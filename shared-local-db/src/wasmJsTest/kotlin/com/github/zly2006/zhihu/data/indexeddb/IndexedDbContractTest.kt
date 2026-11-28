/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

@file:OptIn(ExperimentalWasmJsInterop::class)

package com.github.zly2006.zhihu.data.indexeddb

import com.github.zly2006.zhihu.viewmodel.filter.BlockedKeyword
import com.github.zly2006.zhihu.viewmodel.filter.IndexedFilterDatabase
import com.github.zly2006.zhihu.viewmodel.filter.KeywordType
import com.github.zly2006.zhihu.viewmodel.local.CrawlingReason
import com.github.zly2006.zhihu.viewmodel.local.CrawlingResult
import com.github.zly2006.zhihu.viewmodel.local.CrawlingStatus
import com.github.zly2006.zhihu.viewmodel.local.CrawlingTask
import com.github.zly2006.zhihu.viewmodel.local.IndexedLocalContentDatabase
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.test.runTest

class IndexedDbContractTest {
    @Test
    fun observesCommittedRowsAcrossConnectionsAndReopensExactIds() = runTest {
        withTestName { name ->
            val largeId = 9_007_199_254_740_993L
            IndexedFilterDatabase(name).use { writer ->
                IndexedFilterDatabase(name).use { reader ->
                    val keywords = writer.blockedKeywordDao()
                    val observed = reader.blockedKeywordDao().observeKeywordsByType(KeywordType.EXACT_MATCH.name).produceIn(this)
                    try {
                        assertEquals(emptyList(), observed.receive())
                        val first = BlockedKeyword(id = largeId, keyword = "先", createdTime = 1)
                        assertEquals(largeId, keywords.insertKeyword(first))
                        assertEquals(listOf(first), observed.receive())
                        val second = BlockedKeyword(keyword = "后", createdTime = 2)
                        assertEquals(largeId + 1, keywords.insertKeyword(second))
                        assertEquals(listOf(second.copy(id = largeId + 1), first), observed.receive())
                    } finally {
                        observed.cancel()
                    }
                }
            }
            IndexedFilterDatabase(name).use { reopened ->
                assertEquals(listOf(largeId + 1, largeId), reopened.blockedKeywordDao().getAllKeywords().map { it.id })
            }
        }
    }

    @Test
    fun claimsOnceAndCommitsOrRollsBackBothStores() = runTest {
        withTestName { name ->
            IndexedLocalContentDatabase(name).use { first ->
                IndexedLocalContentDatabase(name).use { second ->
                    val task = CrawlingTask(url = "fixture", reason = CrawlingReason.Trending, createdAt = 1)
                    first.insertTasks(listOf(task))
                    val id = second.getTasksByStatus(CrawlingStatus.NotStarted).single().id
                    assertFailsWith<IllegalStateException> { first.insertTasks(listOf(task, task.copy(id = id))) }
                    assertEquals(1, first.getTaskCountByReasonAndStatus(task.reason, CrawlingStatus.NotStarted))
                    assertEquals(1, listOf(first, second).map { async { it.claimTask(id, null, 41) } }.awaitAll().sum())
                    val result = CrawlingResult(taskId = id, contentId = "answer:1", title = "", summary = "", url = "fixture", reason = task.reason)
                    assertFailsWith<IllegalArgumentException> { first.completeTask(id, 41, listOf(result, result.copy(score = Double.NaN))) }
                    assertEquals(id, second.getTasksByStatus(CrawlingStatus.InProgress).single().id)
                    assertTrue(second.getRecentResults(-1).isEmpty())
                    assertEquals(1, second.recoverAbandonedTasks())
                    assertEquals(0, first.claimTask(id, 41, 41))
                    assertEquals(1, first.claimTask(id, 41, 42))
                    second.completeTask(id, 41, listOf(result))
                    first.completeTask(id, 42, listOf(result))
                    assertEquals(0, second.finishTask(id, 41, CrawlingStatus.Failed))
                    assertEquals(id, second.getTasksByStatus(CrawlingStatus.Completed).single().id)
                    assertEquals(listOf("answer:1"), second.getRecentResults(-1).map { it.contentId })
                }
            }
        }
    }
}

private suspend fun withTestName(block: suspend (String) -> Unit) {
    val name = "zhihu-pp-indexeddb-test-${Random.nextLong().toULong().toString(16)}"
    try {
        block(name)
    } finally {
        suspendCoroutine<Unit> { done ->
            val request = deleteTestDatabase(name)
            request.onsuccess = { done.resume(Unit) }
            request.onerror = { done.resumeWithException(IllegalStateException(request.error?.message)) }
        }
    }
}

private fun deleteTestDatabase(name: String): IdbRequest<JsAny> = js("globalThis.indexedDB.deleteDatabase(name)")
