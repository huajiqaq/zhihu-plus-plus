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

package com.github.zly2006.zhihu.viewmodel.filter

import com.github.zly2006.zhihu.filter.asListLoadState
import com.github.zly2006.zhihu.viewmodel.LoadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ContentFilterDatabaseTest {
    @Test
    fun createsJvmRoomDatabaseAndStoresKeyword() = runTest {
        val database = getContentFilterDatabase(
            createTempDirectory("content-filter-room").resolve("content-filter.db").toFile(),
        )

        val keywordId = database.blockedKeywordDao().insertKeyword(
            BlockedKeyword(keyword = "noise"),
        )

        val keywords = database.blockedKeywordDao().getAllKeywords()
        assertEquals(1L, keywordId)
        assertEquals(listOf("noise"), keywords.map { it.keyword })
        database.close()
    }

    @Test
    fun storesBlockedQuestionAuthor() = runTest {
        val database = getContentFilterDatabase(
            createTempDirectory("content-filter-room-question-author").resolve("content-filter.db").toFile(),
        )

        database.blockedQuestionAuthorDao().insertUser(
            BlockedQuestionAuthor(
                userId = "asker-id",
                userName = "提问者",
                urlToken = "asker-token",
            ),
        )

        val authors = database.blockedQuestionAuthorDao().getAllUsers()
        assertEquals(listOf("asker-id"), authors.map { it.userId })
        assertEquals(listOf("提问者"), authors.map { it.userName })
        database.close()
    }

    @Test
    fun categoryClearingAndObservedQueriesKeepOtherRules() = runTest {
        val database = getContentFilterDatabase(
            createTempDirectory("content-filter-room-categories").resolve("content-filter.db").toFile(),
        )
        try {
            val keywords = database.blockedKeywordDao()
            keywords.insertKeyword(BlockedKeyword(keyword = "exact", keywordType = KeywordType.EXACT_MATCH.name))
            keywords.insertKeyword(BlockedKeyword(keyword = "semantic", keywordType = KeywordType.NLP_SEMANTIC.name))
            assertEquals(listOf("exact"), keywords.observeKeywordsByType(KeywordType.EXACT_MATCH.name).first().map { it.keyword })
            assertEquals(listOf("semantic"), keywords.observeKeywordsByType(KeywordType.NLP_SEMANTIC.name).first().map { it.keyword })
            keywords.deleteKeywordsByType(KeywordType.EXACT_MATCH.name)
            assertEquals(emptyList(), keywords.observeKeywordsByType(KeywordType.EXACT_MATCH.name).first())
            assertEquals(listOf("semantic"), keywords.getAllKeywords().map { it.keyword })
            keywords.insertKeyword(BlockedKeyword(keyword = "exact-again", keywordType = KeywordType.EXACT_MATCH.name))
            keywords.deleteKeywordsByType(KeywordType.NLP_SEMANTIC.name)
            assertEquals(listOf("exact-again"), keywords.getAllKeywords().map { it.keyword })

            database.blockedUserDao().insertUser(BlockedUser(userId = "user", userName = "User"))
            database.blockedQuestionAuthorDao().insertUser(BlockedQuestionAuthor(userId = "asker", userName = "Asker"))
            database.blockedTopicDao().insertTopic(BlockedTopic(topicId = "topic", topicName = "Topic"))
            assertEquals(
                listOf("user"),
                database
                    .blockedUserDao()
                    .observeAllUsers()
                    .first()
                    .map { it.userId },
            )
            assertEquals(
                listOf("asker"),
                database
                    .blockedQuestionAuthorDao()
                    .observeAllUsers()
                    .first()
                    .map { it.userId },
            )
            assertEquals(
                listOf("topic"),
                database
                    .blockedTopicDao()
                    .observeAllTopics()
                    .first()
                    .map { it.topicId },
            )
            database.blockedQuestionAuthorDao().clearAllUsers()
            assertEquals(emptyList(), database.blockedQuestionAuthorDao().observeAllUsers().first())
            assertEquals(listOf("user"), database.blockedUserDao().getAllUsers().map { it.userId })
            assertEquals(listOf("topic"), database.blockedTopicDao().getAllTopics().map { it.topicId })
        } finally {
            database.close()
        }
    }

    @Test
    fun observedListDistinguishesLoadingEmptyDataFailureAndCancellation() = runTest {
        assertEquals(listOf(LoadState.Loading, LoadState.Ready(emptyList<Int>())), emptyFlow<List<Int>>().asListLoadState().toList())
        assertEquals(listOf(LoadState.Loading, LoadState.Ready(listOf(7))), flowOf(listOf(7)).asListLoadState().toList())
        val failure = flow<List<Int>> { error("query failed") }.asListLoadState().toList()
        assertEquals(listOf(LoadState.Loading, LoadState.Failed("query failed")), failure)
        val states = mutableListOf<LoadState<List<Int>>>()
        var cancelled = false
        try {
            flow<List<Int>> { throw CancellationException("old observer") }.asListLoadState().collect { states.add(it) }
        } catch (error: CancellationException) {
            cancelled = true
            assertEquals("old observer", error.message)
        }
        assertEquals(true, cancelled)
        assertEquals(listOf<LoadState<List<Int>>>(LoadState.Loading), states)
    }

    @Test
    fun cancelledBlockedRecordWriteStopsBeforePublishingSuccess() = runTest {
        for (phase in listOf("insert", "limit")) {
            val directory = createTempDirectory("blocked-record-cancellation-")
            val database = getContentFilterDatabase(directory.resolve("content-filter.db").toFile())
            try {
                val records = database.blockedContentRecordDao()
                val calls = mutableListOf<String>()
                val cancellation = CancellationException("cancel $phase")
                val recordDao = object : BlockedContentRecordDao by records {
                    override suspend fun insertRecord(record: BlockedContentRecord): Long {
                        calls += "insert"
                        if (phase == "insert") throw cancellation
                        return records.insertRecord(record)
                    }

                    override suspend fun maintainRecordLimit() {
                        calls += "limit"
                        throw cancellation
                    }
                }
                val service = BlockedKeywordService(database.blockedKeywordDao(), recordDao, null)
                val thrown = assertFailsWith<CancellationException> {
                    service.recordBlockedContent(
                        contentId = "synthetic-answer",
                        contentType = "answer",
                        title = "合成内容",
                        excerpt = "合成摘要",
                        authorName = null,
                        authorId = null,
                        matchedKeywords = listOf(MatchedKeywordInfo("合成关键词", 0.9)),
                    )
                }
                assertTrue(generateSequence<Throwable>(thrown) { it.cause }.any { it === cancellation })
                assertEquals(if (phase == "insert") listOf("insert") else listOf("insert", "limit"), calls)
                assertEquals(if (phase == "insert") 0 else 1, records.getRecordCount())
            } finally {
                database.close()
                check(directory.toFile().deleteRecursively())
            }
        }
    }
}
