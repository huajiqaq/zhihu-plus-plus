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

import com.github.zly2006.zhihu.account.ZhihuAccountRepository
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalRecommendationSupportTest {
    private val testAccountStore = lazy {
        ZhihuAccountStore(
            ZhihuAccountRepository(Path(createTempDirectory("local-recommendation-account").resolve("account.json").toString())),
            MockEngine { error("本地推荐测试不得请求真实网络") },
        )
    }

    @AfterTest
    fun clearTestOverrides() {
        crawlingFeedArrayForTesting = null
        if (testAccountStore.isInitialized()) testAccountStore.value.close()
    }

    @Test
    fun schedulingStopsWithItsOwnerBeforeDatabaseCloses() = runTest {
        val database = testLocalContentDatabase()
        val dao = database.contentDao()
        dao.insertTasks(listOf(CrawlingTask(url = "https://api.zhihu.com/moments_v3?feed_type=recommend", reason = CrawlingReason.Following)))
        val task = dao.getTasksByStatus(CrawlingStatus.NotStarted).single()
        val started = CompletableDeferred<Job>()
        val release = CompletableDeferred<Unit>()
        crawlingFeedArrayForTesting = {
            started.complete(checkNotNull(currentCoroutineContext()[Job]))
            release.await()
            throw CancellationException("owner stopped")
        }
        val owner = SupervisorJob()
        val ownerScope = CoroutineScope(owner + Dispatchers.Default)
        val scheduler = TaskScheduler(dao, CrawlingExecutor(dao, testAccountStore.value), ownerScope)
        try {
            scheduler.startScheduling()
            val execution = started.await()
            owner.cancel()
            owner.join()
            assertTrue(execution.isCancelled, "调度任务必须随真实owner取消，不能后台继续使用已关闭数据库")
            assertEquals(task.id, dao.getTasksByStatus(CrawlingStatus.NotStarted).single().id)
            assertTrue(dao.getTasksByStatus(CrawlingStatus.Failed).isEmpty())
            assertTrue(dao.getTasksByStatus(CrawlingStatus.Completed).isEmpty())
        } finally {
            ownerScope.cancel()
            val execution = if (started.isCompleted) started.await() else null
            execution?.parent?.cancel()
            release.complete(Unit)
            execution?.parent?.join()
            execution?.join()
            database.close()
        }
    }

    private fun testLocalContentDatabase(): LocalContentDatabase =
        getLocalContentDatabase(
            createTempDirectory("local-recommendation-support-room").resolve("local-content.db").toFile(),
        )
}
