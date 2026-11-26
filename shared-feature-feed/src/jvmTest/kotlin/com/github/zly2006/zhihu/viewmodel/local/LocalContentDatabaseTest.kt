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

package com.github.zly2006.zhihu.viewmodel.local

import androidx.room.useWriterConnection
import com.github.zly2006.zhihu.account.ZhihuAccountRepository
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.serialization.json.JsonArray
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LocalContentDatabaseTest {
    @Test
    fun recoveryPreservesLiveExecutorAndReleasesOnlyAfterCancellationCleanup() = runTest {
        val file = createTempDirectory("local-content-recovery-room").resolve("local-content.db").toFile()
        val database = getLocalContentDatabase(file)
        val otherDatabase = getLocalContentDatabase(file)
        val store = ZhihuAccountStore(
            ZhihuAccountRepository(Path(file.parent, "account.json")),
            MockEngine { error("抓取测试不得请求真实网络") },
        )
        val started = CompletableDeferred<Unit>()
        val cleanupStarted = CompletableDeferred<Unit>()
        val cleanupRelease = CompletableDeferred<Unit>()
        val previousFeed = crawlingFeedArrayForTesting
        val requests = AtomicInteger()
        var execution: Job? = null
        try {
            val dao = database.contentDao()
            val other = otherDatabase.contentDao()
            assertEquals(file.canonicalPath, java.io.File(dao.databaseFilePath()).canonicalPath)
            dao.insertTasks(listOf(CrawlingTask(url = "https://www.zhihu.com/question/1", reason = CrawlingReason.Trending)))
            val task = dao.getTasksByStatus(CrawlingStatus.NotStarted).single()
            assertEquals("https://www.zhihu.com/question/1", task.url)
            val delayedDao = object : LocalContentDao by dao {
                override suspend fun finishTask(id: Long, executedAt: Long, status: CrawlingStatus, errorMessage: String?): Int {
                    if (status == CrawlingStatus.NotStarted) {
                        cleanupStarted.complete(Unit)
                        cleanupRelease.await()
                    }
                    return dao.finishTask(id, executedAt, status, errorMessage)
                }
            }
            crawlingFeedArrayForTesting = {
                requests.incrementAndGet()
                started.complete(Unit)
                awaitCancellation()
            }
            execution = launch(Dispatchers.Default) { CrawlingExecutor(delayedDao, store).executeTask(task) }
            started.await()
            val recovery = CrawlingExecutor(other, store)
            recovery.recoverAbandonedTasks()
            assertEquals(task.id, other.getTasksByStatus(CrawlingStatus.InProgress).single().id, "活执行器不能被另一个恢复入口重置")
            recovery.executeTask(task)
            assertEquals(1, requests.get(), "活锁期间不能开始另一个抓取")
            execution.cancel()
            cleanupStarted.await()
            recovery.recoverAbandonedTasks()
            assertEquals(task.id, other.getTasksByStatus(CrawlingStatus.InProgress).single().id, "取消清理结束前不能释放OS锁")
            cleanupRelease.complete(Unit)
            execution.join()
            val released = other.getTasksByStatus(CrawlingStatus.NotStarted).single()
            assertEquals(task.id, released.id)
            val nextExecution = checkNotNull(released.executedAt) + 1L
            assertEquals(1, other.claimTask(task.id, released.executedAt, nextExecution))
            assertEquals(task.id, other.getTasksByStatus(CrawlingStatus.InProgress).single().id)
            crawlingFeedArrayForTesting = {
                requests.incrementAndGet()
                JsonArray(emptyList())
            }
            recovery.recoverAbandonedTasks()
            assertEquals(nextExecution, other.getTasksByStatus(CrawlingStatus.NotStarted).single().executedAt, "孤立恢复仍要保留领取代次")
            recovery.executeTask(other.getTasksByStatus(CrawlingStatus.NotStarted).single())
            assertEquals(2, requests.get())
            assertEquals(task.id, other.getTasksByStatus(CrawlingStatus.Completed).single().id)
            assertTrue(other.getTasksByStatus(CrawlingStatus.InProgress).isEmpty())
        } finally {
            cleanupRelease.complete(Unit)
            execution?.cancelAndJoin()
            crawlingFeedArrayForTesting = previousFeed
            store.close()
            otherDatabase.close()
            database.close()
        }
    }

    @Test
    fun taskClaimsKeepOwnershipAcrossConnectionsAndAtomicResultCommit() = runTest {
        val file = createTempDirectory("local-content-claim-room").resolve("local-content.db").toFile()
        val database = getLocalContentDatabase(file)
        val otherDatabase = getLocalContentDatabase(file)
        try {
            val dao = database.contentDao()
            val other = otherDatabase.contentDao()
            dao.insertTasks(listOf(CrawlingTask(url = "https://www.zhihu.com/question/1", reason = CrawlingReason.Trending)))
            val task = dao.getTasksByStatus(CrawlingStatus.NotStarted).single()
            val firstClaim = async(Dispatchers.Default) { dao.claimTask(task.id, null, 42L) }
            val secondClaim = async(Dispatchers.Default) { other.claimTask(task.id, null, 42L) }
            assertEquals(1, firstClaim.await() + secondClaim.await(), "同文件的两个Room连接必须只有一次领取成功")
            assertEquals(42L, dao.getTasksByStatus(CrawlingStatus.InProgress).single().executedAt)
            assertEquals(1, dao.finishTask(task.id, 42L, CrawlingStatus.NotStarted))
            assertEquals(42L, other.getTasksByStatus(CrawlingStatus.NotStarted).single().executedAt)
            assertEquals(0, other.claimTask(task.id, null, 43L), "释放前的快照不能跨代次重领")
            assertEquals(0, other.claimTask(task.id, 42L, 42L), "同毫秒重领仍须推进代次")
            assertEquals(1, other.claimTask(task.id, 42L, 43L))
            assertEquals(0, dao.finishTask(task.id, 42L, CrawlingStatus.NotStarted))
            assertEquals(0, dao.finishTask(task.id, 42L, CrawlingStatus.Failed, "stale failure"))
            val result = CrawlingResult(taskId = task.id, contentId = "answer:42", title = "回答", summary = "摘要", url = "https://www.zhihu.com/answer/42", reason = CrawlingReason.Trending)
            dao.completeTask(task.id, 42L, listOf(result))
            assertTrue(dao.getRecentResults(10).isEmpty(), "旧代次不能提交结果")
            database.useWriterConnection { connection ->
                connection.usePrepared("CREATE TRIGGER reject_crawling_result BEFORE INSERT ON crawling_results BEGIN SELECT RAISE(ABORT, 'injected result failure'); END") { it.step() }
            }
            try {
                assertFailsWith<Exception> { dao.completeTask(task.id, 43L, listOf(result)) }
                assertTrue(other.getTasksByStatus(CrawlingStatus.Completed).isEmpty(), "结果插入失败必须回滚完成状态")
                assertEquals(43L, other.getTasksByStatus(CrawlingStatus.InProgress).single().executedAt)
                assertTrue(dao.getRecentResults(10).isEmpty())
            } finally {
                database.useWriterConnection { connection ->
                    connection.usePrepared("DROP TRIGGER reject_crawling_result") { it.step() }
                }
            }
            dao.completeTask(task.id, 43L, listOf(result))
            assertEquals(43L, other.getTasksByStatus(CrawlingStatus.Completed).single().executedAt)
            assertEquals("answer:42", other.getRecentResults(10).single().contentId)
            assertEquals(0, other.finishTask(task.id, 43L, CrawlingStatus.NotStarted), "完成提交后不能因迟到取消复活任务")
            assertEquals(0, other.claimTask(task.id, 43L, 44L))
            dao.completeTask(task.id, 43L, listOf(result))
            assertEquals(1, other.getRecentResults(10).size)
        } finally {
            otherDatabase.close()
            database.close()
        }
    }
}
