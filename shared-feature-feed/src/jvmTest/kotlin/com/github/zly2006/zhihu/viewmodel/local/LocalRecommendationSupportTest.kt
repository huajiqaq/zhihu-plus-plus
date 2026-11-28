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
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.QualityFilterSettings
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.viewmodel.FeedDisplaySettings
import com.github.zly2006.zhihu.viewmodel.QualityFilterMode
import com.github.zly2006.zhihu.viewmodel.feed.parseMobileHomeFeedDisplayItem
import com.github.zly2006.zhihu.viewmodel.filter.BlockedKeyword
import com.github.zly2006.zhihu.viewmodel.filter.ContentDetailProvider
import com.github.zly2006.zhihu.viewmodel.filter.FeedDisplayFilterPipeline
import com.github.zly2006.zhihu.viewmodel.filter.FeedFilterSettings
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import io.ktor.client.engine.mock.MockEngine
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
import kotlinx.serialization.json.jsonObject

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
            release.complete(Unit)
            owner.join()
            database.closeDatabase()
        }
    }

    private fun testLocalContentDatabase(): LocalContentDatabase =
        getLocalContentDatabase(
            createTempDirectory("local-recommendation-support-room").resolve("local-content.db").toFile(),
        )

    @Test
    fun mobileAndLocalVotesRespectTheBoundaryAndExplicitKeywords() = runTest {
        data class Sample(
            val title: String,
            val votes: Int,
            val summary: String = "clean",
        )
        val samples = listOf(
            Sample("low", 999),
            Sample("edge", 1000),
            Sample("edge-hit", 1000, "blocked-term"),
        )
        val local = testLocalContentDatabase()
        val filters = getContentFilterDatabase(createTempDirectory("recommendation-filters").resolve("filters.db").toFile())
        try {
            val dao = local.contentDao()
            val mobile = mutableListOf<FeedDisplayItem>()
            val rows = samples.mapIndexed { index, (title, votes, summary) ->
                val id = index + 1
                val card = ZhihuJson.json
                    .parseToJsonElement(
                        """{"type":"ComponentCard","action":{"parameter":"route_url=https://www.zhihu.com/answer/$id"},"children":[
                        {"id":"Text","style":"Text","type":"Text","text":"$title"},{"id":"text_pin_summary","style":"Text","type":"Text","text":"$summary"},
                        {"style":"RecommendAuthorLine_default","type":"Line","elements":[{"type":"Avatar","style":"Avatar_default","image":{"url":""}},{"type":"Text","text":"author"}]},
                        {"style":"Footer","type":"Line","elements":[{"reaction":"Vote","count":$votes},{"type":"Text","text":"999 赞同"}]}]}""",
                    ).jsonObject
                mobile += checkNotNull(parseMobileHomeFeedDisplayItem(card))
                val raw = ZhihuJson.json.parseToJsonElement(
                    """{"type":"feed","target":{"type":"answer","id":$id,"url":"https://www.zhihu.com/answer/$id","voteup_count":$votes,"excerpt":"$summary",
                        "question":{"id":$id,"type":"question","title":"$title","url":""}}}""",
                )
                checkNotNull(createCrawlingResult(raw, 1, CrawlingReason.Trending, accountId = "owner"))
            }
            dao.insertResults(rows)
            val entries = buildFallbackRecommendations(dao, UserBehaviorAnalyzer(dao), FeedGenerator(dao), samples.size)
            val localItems = dao.toLocalDisplayItems(entries, "owner")
            filters.blockedKeywordDao().insertKeyword(BlockedKeyword(keyword = "blocked-term"))
            val pipeline = FeedDisplayFilterPipeline(
                settings = FeedFilterSettings(enableNlpBlocking = false),
                contentDetailProvider = ContentDetailProvider { null },
                database = filters,
                semanticMatcher = null,
                displaySettings = FeedDisplaySettings(QualityFilterMode.HIDE, QualityFilterSettings(answerVoteupCount = 1000)),
            )
            val expected = listOf("edge" to false)
            assertEquals(expected, pipeline.filter(mobile).map { it.title to it.isQualityFiltered }, "mobile")
            assertEquals(expected, pipeline.filter(localItems).map { it.title to it.isQualityFiltered }, "local")
        } finally {
            filters.closeDatabase()
            local.closeDatabase()
        }
    }
}
