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

import androidx.lifecycle.viewModelScope
import com.github.zly2006.zhihu.account.ZhihuAccountRepository
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.data.CommonFeed
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.local.LocalContentAffinity
import com.github.zly2006.zhihu.viewmodel.local.LocalReasonPreference
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.serialization.json.JsonArray
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
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
        localRecommendationEngineForTesting = null
        crawlingFeedArrayForTesting = null
        crawlingNowMillisForTesting = null
        if (testAccountStore.isInitialized()) testAccountStore.value.close()
    }

    @Test
    fun buildFallbackRecommendationsRanksRecentResultsAndCreatesEntries() = runTest {
        val database = testLocalContentDatabase()
        val dao = database.contentDao()
        dao.insertResults(
            listOf(
                CrawlingResult(
                    taskId = 1L,
                    contentId = "42",
                    title = "回答标题",
                    summary = "摘要",
                    url = "https://www.zhihu.com/answer/42",
                    reason = CrawlingReason.Trending,
                    score = 10.0,
                ),
            ),
        )

        val entries = buildFallbackRecommendations(
            dao = dao,
            userBehaviorAnalyzer = UserBehaviorAnalyzer(dao),
            feedGenerator = FeedGenerator(dao),
            limit = 5,
        )

        assertEquals(1, entries.size)
        assertEquals("回答标题", entries.single().feed.title)
        assertEquals("answer:42", entries.single().result.contentId)
        assertEquals(
            Article(type = ArticleType.Answer, id = 42L, title = "回答标题"),
            entries.single().navDestination,
        )
        database.close()
    }

    @Test
    fun cleanupLocalRecommendationDataUsesWeekAndMonthRetention() = runTest {
        val database = testLocalContentDatabase()
        val dao = database.contentDao()
        val now = 100L * 24L * 60L * 60L * 1000L
        val olderThanWeek = now - 8L * 24L * 60L * 60L * 1000L
        val olderThanMonth = now - 31L * 24L * 60L * 60L * 1000L
        dao.insertTasks(
            listOf(
                CrawlingTask(
                    url = "completed",
                    reason = CrawlingReason.Trending,
                    status = CrawlingStatus.Completed,
                    createdAt = olderThanWeek,
                ),
                CrawlingTask(
                    url = "not-started",
                    reason = CrawlingReason.Trending,
                    status = CrawlingStatus.NotStarted,
                    createdAt = olderThanWeek,
                ),
            ),
        )
        dao.insertResults(
            listOf(
                CrawlingResult(
                    taskId = 1L,
                    contentId = "answer:1",
                    title = "标题",
                    summary = "摘要",
                    url = "https://www.zhihu.com/answer/1",
                    reason = CrawlingReason.Trending,
                    createdAt = olderThanMonth,
                ),
            ),
        )
        dao.insertFeed(
            LocalFeed(
                id = "local_feed_answer_1",
                resultId = 1L,
                title = "标题",
                summary = "摘要",
                reasonDisplay = "热门推荐",
                navDestination = "answer:1",
                createdAt = olderThanMonth,
            ),
        )
        dao.insertBehavior(
            UserBehavior(
                contentId = "answer:1",
                action = "click",
                timestamp = olderThanMonth,
            ),
        )

        cleanupLocalRecommendationData(dao, nowMillis = now)

        assertEquals(emptyList(), dao.getTasksByStatus(CrawlingStatus.Completed))
        assertEquals(1, dao.getTasksByStatus(CrawlingStatus.NotStarted).size)
        assertEquals(emptyList(), dao.getRecentResults(10))
        assertEquals(null, dao.getFeedById("local_feed_answer_1"))
        assertEquals(emptyList(), dao.getBehaviorsSince(0L))
        database.close()
    }

    @Test
    fun collectCandidateResultsNormalizesAndDeduplicatesCandidates() = runTest {
        val database = testLocalContentDatabase()
        val dao = database.contentDao()
        dao.insertResults(
            listOf(
                CrawlingResult(
                    taskId = 1L,
                    contentId = "42",
                    title = "标题 1",
                    summary = "摘要",
                    url = "https://www.zhihu.com/answer/42",
                    reason = CrawlingReason.Trending,
                    score = 10.0,
                ),
                CrawlingResult(
                    taskId = 1L,
                    contentId = "answer:42",
                    title = "标题 2",
                    summary = "摘要",
                    url = "https://www.zhihu.com/answer/42",
                    reason = CrawlingReason.Following,
                    score = 9.0,
                ),
                CrawlingResult(
                    taskId = 1L,
                    contentId = "bad",
                    title = "标题 3",
                    summary = "摘要",
                    url = "https://www.zhihu.com/not-content",
                    reason = CrawlingReason.Trending,
                    score = 8.0,
                ),
            ),
        )

        val candidates = collectCandidateResults(dao, limit = 10)

        assertEquals(listOf("answer:42"), candidates.map { it.contentId })
        database.close()
    }

    @Test
    fun ensurePendingTasksAddsTasksForReasonsBelowThreshold() = runTest {
        val database = testLocalContentDatabase()
        val dao = database.contentDao()
        dao.insertTasks(
            listOf(
                CrawlingTask(
                    url = "existing",
                    reason = CrawlingReason.Trending,
                ),
            ),
        )

        ensurePendingTasks(dao)

        val trendingTasks = dao.getTasksByStatus(CrawlingStatus.NotStarted).filter { it.reason == CrawlingReason.Trending }
        assertEquals(3, trendingTasks.size)
        assertEquals(
            setOf("existing", "https://www.zhihu.com/api/v3/feed/topstory/recommend?desktop=true&limit=20"),
            trendingTasks.map { it.url }.toSet(),
        )
        assertEquals(setOf(0, 7), trendingTasks.map { it.priority }.toSet())
        val followingTasks = dao.getTasksByStatus(CrawlingStatus.NotStarted).filter { it.reason == CrawlingReason.Following }
        assertEquals(3, followingTasks.size)
        assertEquals(setOf("https://api.zhihu.com/moments_v3?feed_type=recommend"), followingTasks.map { it.url }.toSet())
        assertEquals(setOf(8), followingTasks.map { it.priority }.toSet())
        database.close()
    }

    @Test
    fun waitForTaskCompletionReturnsWhenNoTaskInProgress() = runTest {
        val database = testLocalContentDatabase()

        waitForTaskCompletion(database.contentDao(), maxWaitTimeMs = 5_000L)

        database.close()
    }

    @Test
    fun createCrawlingResultKeepsFeedTargetMappingAndMultiplier() {
        val feed = CommonFeed(
            target = Feed.AnswerTarget(
                id = 42L,
                url = "https://www.zhihu.com/question/1/answer/42",
                question = Feed.QuestionTarget(
                    id = 1L,
                    _title = "问题标题",
                    url = "https://www.zhihu.com/question/1",
                    type = "question",
                ),
                excerpt = "回答摘要",
                voteupCount = 100,
                commentCount = 0,
            ),
        )

        val result = createCrawlingResult(feed, taskId = 7L, reason = CrawlingReason.Trending, scoreMultiplier = 1.2)

        assertNotNull(result)
        assertEquals(7L, result.taskId)
        assertEquals("answer:42", result.contentId)
        assertEquals("问题标题", result.title)
        assertEquals("回答摘要", result.summary)
        assertEquals("https://www.zhihu.com/question/1/answer/42", result.url)
        assertEquals(CrawlingReason.Trending, result.reason)
        assertEquals(2.4, result.score)
    }

    @Test
    fun isVoteupFeedMatchesBriefOrAttachedInfo() {
        assertTrue(isVoteupFeed(CommonFeed(brief = "某人赞同了回答")))
        assertTrue(isVoteupFeed(CommonFeed(attachedInfo = "ACTION_VOTEUP")))
        assertEquals(false, isVoteupFeed(CommonFeed(brief = "某人关注了问题")))
    }

    @Test
    fun extractQuestionIdFromUrlKeepsQuestionRegex() {
        assertEquals("123", extractQuestionIdFromUrl("https://www.zhihu.com/question/123/answer/456"))
        assertEquals(null, extractQuestionIdFromUrl("https://www.zhihu.com/answer/456"))
    }

    @Test
    fun extractQuestionIdFromContentIdKeepsLegacyFallback() {
        assertEquals("123", extractQuestionIdFromContentId("question:123"))
        assertEquals("123", extractQuestionIdFromContentId("q123"))
        assertEquals(null, extractQuestionIdFromContentId("answer:456"))
    }

    @Test
    fun localContentInitializerCreatesColdStartTasks() = runTest {
        val database = testLocalContentDatabase()
        val dao = database.contentDao()

        LocalContentInitializer(dao).initializeIfNeeded()

        assertEquals(3, dao.getTaskCountByReasonAndStatus(CrawlingReason.Following, CrawlingStatus.NotStarted))
        assertEquals(3, dao.getTaskCountByReasonAndStatus(CrawlingReason.Trending, CrawlingStatus.NotStarted))
        assertEquals(2, dao.getTaskCountByReasonAndStatus(CrawlingReason.UpvotedQuestion, CrawlingStatus.NotStarted))
        assertEquals(2, dao.getTaskCountByReasonAndStatus(CrawlingReason.FollowingUpvote, CrawlingStatus.NotStarted))
        assertEquals(2, dao.getTaskCountByReasonAndStatus(CrawlingReason.CollaborativeFiltering, CrawlingStatus.NotStarted))
        database.close()
    }

    @Test
    fun getFreshnessWeightUsesSameAgeBuckets() {
        val now = 10_000L * 60L * 60L * 1000L

        assertEquals(1.08, getFreshnessWeight(now - 11L * 60L * 60L * 1000L, now))
        assertEquals(1.0, getFreshnessWeight(now - 24L * 60L * 60L * 1000L, now))
        assertEquals(0.92, getFreshnessWeight(now - 72L * 60L * 60L * 1000L, now))
        assertEquals(0.82, getFreshnessWeight(now - 200L * 60L * 60L * 1000L, now))
    }

    @Test
    fun rankCandidateNormalizesContentIdAndAppliesWeights() {
        val ranked = rankCandidate(
            candidate = CrawlingResult(
                id = 1L,
                taskId = 1L,
                contentId = "42",
                title = "标题",
                summary = "摘要",
                url = "https://www.zhihu.com/answer/42",
                reason = CrawlingReason.Trending,
                score = 10.0,
                createdAt = 0L,
            ),
            behaviorProfile = UserBehaviorAnalyzer.RecommendationBehaviorProfile(
                reasonPreferences = mapOf(
                    CrawlingReason.Trending to LocalReasonPreference(
                        multiplier = 1.2,
                        explanation = "你最近更偏好这类来源",
                    ),
                ),
                contentAffinities = mapOf(
                    "answer:42" to LocalContentAffinity(
                        multiplier = 1.5,
                        explanation = "你明确喜欢过类似内容",
                    ),
                ),
            ),
        )

        assertNotNull(ranked)
        assertEquals("answer:42", ranked.result.contentId)
        assertTrue(ranked.finalScore > 0.0)
        assertEquals("热门推荐 · 你明确喜欢过类似内容 · 你最近更偏好这类来源", ranked.reasonDisplay)
    }

    @Test
    fun localRecommendationEngineInitializesOnlyOnce() = runTest {
        val database = testLocalContentDatabase()
        var initializeCount = 0
        var startCount = 0
        val engine = localRecommendationEngineForTest(
            dao = database.contentDao(),
            scope = this,
            initializeContentIfNeeded = { initializeCount++ },
            startScheduling = { startCount++ },
        )

        engine.initialize()
        engine.initialize()

        assertEquals(1, initializeCount)
        assertEquals(1, startCount)
        database.close()
    }

    @Test
    fun crawlingExecutorMarksTaskCompletedAfterEmptyFetch() = runTest {
        val database = testLocalContentDatabase()
        val dao = database.contentDao()
        dao.insertTasks(
            listOf(
                CrawlingTask(
                    url = "https://api.zhihu.com/moments_v3?feed_type=recommend",
                    reason = CrawlingReason.Following,
                ),
            ),
        )
        val taskId = dao.getTasksByStatus(CrawlingStatus.NotStarted).single().id
        val requestedUrls = mutableListOf<String>()
        crawlingFeedArrayForTesting = { url ->
            requestedUrls.add(url)
            JsonArray(emptyList())
        }
        crawlingNowMillisForTesting = { 42L }
        val executor = CrawlingExecutor(
            dao = dao,
            accountStore = testAccountStore.value,
        )

        executor.executeTask(
            CrawlingTask(
                id = taskId,
                url = "https://api.zhihu.com/moments_v3?feed_type=recommend",
                reason = CrawlingReason.Following,
            ),
        )

        assertEquals(listOf("https://api.zhihu.com/moments_v3?feed_type=recommend"), requestedUrls)
        assertEquals(emptyList(), dao.getTasksByStatus(CrawlingStatus.InProgress))
        assertEquals(42L, dao.getTasksByStatus(CrawlingStatus.Completed).single().executedAt)
        assertEquals(emptyList(), dao.getRecentResults(10))
        database.close()
    }

    @Test
    fun concurrentExecutorsDoNotRepeatPendingSnapshots() = runTest {
        val database = testLocalContentDatabase()
        val dao = database.contentDao()
        dao.insertTasks(listOf(CrawlingTask(url = "https://api.zhihu.com/moments_v3?feed_type=recommend", reason = CrawlingReason.Following)))
        val task = dao.getTasksByStatus(CrawlingStatus.NotStarted).single()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val fetches = AtomicInteger()
        crawlingFeedArrayForTesting = {
            if (fetches.incrementAndGet() == 1) started.complete(Unit)
            release.await()
            JsonArray(emptyList())
        }
        crawlingNowMillisForTesting = { 42L }
        val first = CrawlingExecutor(dao, testAccountStore.value)
        val second = CrawlingExecutor(dao, testAccountStore.value)
        val winner = launch(Dispatchers.Default) { first.executeTask(task) }
        var duplicate: Job? = null
        try {
            started.await()
            duplicate = launch(Dispatchers.Default) { second.executeTask(task) }
            release.complete(Unit)
            winner.join()
            duplicate.join()
            assertEquals(1, fetches.get(), "两个真实executor不能重复请求同一份待处理快照")
            assertEquals(1, dao.getTasksByStatus(CrawlingStatus.Completed).size)
            assertTrue(dao.getTasksByStatus(CrawlingStatus.NotStarted).isEmpty())
            assertTrue(dao.getTasksByStatus(CrawlingStatus.InProgress).isEmpty())
            first.executeTask(task)
            second.executeTask(task)
            assertEquals(1, fetches.get(), "已完成任务不能被批次中的旧快照重新执行")
        } finally {
            release.complete(Unit)
            duplicate?.cancelAndJoin()
            winner.cancelAndJoin()
            database.close()
        }
    }

    @Test
    fun cancelledCrawlingAndRecommendationDoNotBecomeFailureOrContinueOtherTasks() = runTest {
        val database = testLocalContentDatabase()
        try {
            val dao = database.contentDao()
            dao.insertTasks(listOf(CrawlingTask(url = "https://api.zhihu.com/moments_v3?feed_type=recommend", reason = CrawlingReason.Following)))
            val task = dao.getTasksByStatus(CrawlingStatus.NotStarted).single()
            crawlingFeedArrayForTesting = { throw CancellationException("cancelled crawl") }
            val executor = CrawlingExecutor(dao, testAccountStore.value)
            assertFailsWith<CancellationException> { executor.executeTask(task) }
            assertTrue(dao.getTasksByStatus(CrawlingStatus.Failed).isEmpty())
            assertTrue(dao.getTasksByStatus(CrawlingStatus.Completed).isEmpty())
            assertEquals(task.id, dao.getTasksByStatus(CrawlingStatus.NotStarted).single().id)
            assertTrue(dao.getRecentResults(10).isEmpty())

            val cancellation = Job()
            val requestsAfterCancellation = AtomicInteger()
            val claimingDao = object : LocalContentDao by dao {
                override suspend fun claimTask(id: Long, previousExecution: Long?, executedAt: Long): Int =
                    dao.claimTask(id, previousExecution, executedAt).also { if (it == 1) cancellation.cancel() }
            }
            crawlingFeedArrayForTesting = {
                requestsAfterCancellation.incrementAndGet()
                JsonArray(emptyList())
            }
            try {
                assertFailsWith<CancellationException> {
                    withContext(cancellation + Dispatchers.Default) {
                        CrawlingExecutor(claimingDao, testAccountStore.value).executeTask(dao.getTasksByStatus(CrawlingStatus.NotStarted).single())
                    }
                }
                assertEquals(0, requestsAfterCancellation.get(), "领取已提交后的取消不能开始抓取")
                assertEquals(task.id, dao.getTasksByStatus(CrawlingStatus.NotStarted).single().id)
            } finally {
                cancellation.cancelAndJoin()
            }

            val attempted = AtomicInteger()
            val engine = localRecommendationEngineForTest(dao, this, executeTask = {
                attempted.incrementAndGet()
                throw CancellationException("cancelled recommendations")
            })
            assertFailsWith<CancellationException> { engine.generateRecommendations(20) }
            assertEquals(1, attempted.get(), "取消后不能继续执行其它高优先级任务")
        } finally {
            database.close()
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun abandonedLocalRoundCannotShowColdStartOrFinishNewLoading() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val failures = AtomicInteger()
        localRecommendationEngineForTesting = LocalRecommendationEngineTestOverrides(
            initializeContent = {
                started.complete(Unit)
                release.await()
                throw CancellationException("cancelled local round")
            },
        )
        val client = HttpClient(
            io.ktor.client.engine.mock
                .MockEngine { error("本地推荐不应请求网络") },
        )
        val environment = object : PaginationEnvironment {
            override fun httpClient() = client

            override fun authenticatedCookies(): Map<String, String> = emptyMap()

            override suspend fun handleFetchFailure(tag: String?, error: Exception) {
                failures.incrementAndGet()
            }
        }
        val model = LocalHomeFeedViewModel(testAccountStore.value)
        val original = FeedDisplayItem(title = "保留旧内容", summary = null, details = "", feed = null)
        model.displayItems.add(original)
        var cancellationPropagated = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                model.pullToRefresh(environment)
            } catch (_: CancellationException) {
                cancellationPropagated = true
            }
        }
        val newStarted = CompletableDeferred<Unit>()
        val newRelease = CompletableDeferred<Unit>()
        var newJob: kotlinx.coroutines.Job? = null
        try {
            started.await()
            localRecommendationEngineForTesting = LocalRecommendationEngineTestOverrides(
                initializeContent = {
                    newStarted.complete(Unit)
                    newRelease.await()
                    throw CancellationException("finish current test round")
                },
            )
            model.resetForRefresh()
            newJob = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    model.pullToRefresh(environment)
                } catch (_: CancellationException) {
                }
            }
            newStarted.await()
            release.complete(Unit)
            job.join()
            assertTrue(cancellationPropagated)
            assertEquals(0, failures.get(), "取消不是加载错误")
            assertEquals(original, model.displayItems.single(), "旧轮取消不能追加冷启动占位")
            assertTrue(model.latestLoadedDisplayItems.value.isEmpty())
            assertTrue(model.isLoading, "旧轮 finally 不能结束新轮加载")
            assertTrue(model.isPullToRefresh)
        } finally {
            release.complete(Unit)
            newRelease.complete(Unit)
            job.cancel()
            job.join()
            newJob?.cancel()
            newJob?.join()
            model.viewModelScope.cancel()
            client.close()
            Dispatchers.resetMain()
        }
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

    private fun localRecommendationEngineForTest(
        dao: LocalContentDao,
        scope: CoroutineScope,
        initializeContentIfNeeded: suspend () -> Unit = {},
        startScheduling: () -> Unit = {},
        executeTask: suspend (CrawlingTask) -> Unit = {},
    ): LocalRecommendationEngine {
        localRecommendationEngineForTesting = LocalRecommendationEngineTestOverrides(
            initializeContent = initializeContentIfNeeded,
            startScheduling = startScheduling,
            executeTask = executeTask,
        )
        return LocalRecommendationEngine(dao, CrawlingExecutor(dao, testAccountStore.value), scope)
    }

    private fun testLocalContentDatabase(): LocalContentDatabase =
        getLocalContentDatabase(
            createTempDirectory("local-recommendation-support-room").resolve("local-content.db").toFile(),
        )
}
