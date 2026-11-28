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

import com.github.zly2006.zhihu.data.CommonFeed
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedFilterFacts
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.target
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.util.AppLog
import com.github.zly2006.zhihu.viewmodel.local.LocalReasonPreference
import com.github.zly2006.zhihu.viewmodel.local.applyReasonDiversity
import com.github.zly2006.zhihu.viewmodel.local.buildLocalRecommendationReason
import com.github.zly2006.zhihu.viewmodel.local.parseLocalContentIdentity
import com.github.zly2006.zhihu.viewmodel.local.scoreFeedTarget
import com.github.zly2006.zhihu.viewmodel.local.toLocalContentIdentity
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class LocalRecommendationEngine(
    private val dao: LocalContentDao,
    private val crawlingExecutor: CrawlingExecutor,
    scope: CoroutineScope,
) {
    private val feedGenerator = FeedGenerator(dao)
    private val userBehaviorAnalyzer = UserBehaviorAnalyzer(dao)
    private val contentInitializer = LocalContentInitializer(dao)
    private val taskScheduler = TaskScheduler(dao, crawlingExecutor, scope)

    @kotlin.concurrent.Volatile
    private var initialized = false

    suspend fun initialize() {
        if (initialized) {
            return
        }

        withContext(Dispatchers.Default) {
            if (initialized) {
                return@withContext
            }
            localRecommendationEngineForTesting?.initializeContent?.invoke()
                ?: contentInitializer.initializeIfNeeded()
            localRecommendationEngineForTesting?.startScheduling?.invoke()
                ?: taskScheduler.startScheduling()
            initialized = true
        }
    }

    suspend fun generateRecommendations(limit: Int = 20): List<LocalRecommendationEntry> = withContext(Dispatchers.Default) {
        initialize()

        var candidates = collectCandidateResults(dao, limit)
        if (candidates.size < limit / 2) {
            ensurePendingTasks(dao)
            executeHighPriorityTasks()
            waitForTaskCompletion(dao, 5_000L)
            candidates = collectCandidateResults(dao, limit)
        }

        if (candidates.isEmpty()) {
            return@withContext buildFallbackRecommendations(dao, userBehaviorAnalyzer, feedGenerator, limit)
        }

        val behaviorProfile = userBehaviorAnalyzer.buildBehaviorProfile()
        val ranked = candidates
            .mapNotNull { candidate ->
                rankCandidate(candidate, behaviorProfile)
            }.sortedByDescending { it.finalScore }

        if (ranked.isEmpty()) {
            return@withContext buildFallbackRecommendations(dao, userBehaviorAnalyzer, feedGenerator, limit)
        }

        applyReasonDiversity(
            rankedResults = ranked,
            limit = limit,
            reasonOf = { it.result.reason },
        ).mapNotNull { rankedResult ->
            toRecommendationEntry(
                rankedResult = rankedResult,
                feedGenerator = feedGenerator,
            )
        }
    }

    suspend fun recordContentOpened(contentId: String, reason: CrawlingReason) {
        withContext(Dispatchers.Default) {
            userBehaviorAnalyzer.recordContentOpened(contentId, reason)
        }
    }

    suspend fun recordRecommendationFeedback(
        feedId: String?,
        contentId: String,
        reason: CrawlingReason,
        feedback: Double,
    ) {
        withContext(Dispatchers.Default) {
            feedId?.let { dao.updateFeedFeedback(it, feedback) }
            userBehaviorAnalyzer.recordRecommendationFeedback(contentId, reason, feedback)
        }
    }

    private suspend fun executeHighPriorityTasks() {
        crawlingExecutor.recoverAbandonedTasks()
        dao
            .getTasksByStatus(CrawlingStatus.NotStarted)
            .sortedByDescending { it.priority }
            .take(3)
            .forEach { task ->
                try {
                    localRecommendationEngineForTesting?.executeTask?.invoke(task)
                        ?: crawlingExecutor.executeTask(task)
                    delay(1_000L)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.e("LocalRecommendationEngine", "Task execution failed: ${e.message}", e)
                }
            }
    }
}

data class LocalRecommendationEngineTestOverrides(
    val initializeContent: suspend () -> Unit = {},
    val startScheduling: () -> Unit = {},
    val executeTask: suspend (CrawlingTask) -> Unit = {},
)

var localRecommendationEngineForTesting: LocalRecommendationEngineTestOverrides? = null

data class RankedLocalResult(
    val result: CrawlingResult,
    val finalScore: Double,
    val reasonDisplay: String,
)

data class LocalRecommendationEntry(
    val feed: LocalFeed,
    val result: CrawlingResult,
    val navDestination: NavDestination?,
)

internal fun zhihuFollowingRecommendUrl(offset: Int? = null): String =
    buildString {
        append("https://api.zhihu.com/moments_v3?feed_type=recommend")
        if (offset != null) {
            append("&offset=$offset")
        }
    }

internal fun zhihuTopstoryRecommendUrl(
    limit: Int,
    offset: Int? = null,
): String =
    buildString {
        append("https://www.zhihu.com/api/v3/feed/topstory/recommend?desktop=true&limit=$limit")
        if (offset != null) {
            append("&offset=$offset")
        }
    }

internal fun zhihuFollowingUpvoteRecommendUrl(
    limit: Int,
    offset: Int? = null,
): String =
    buildString {
        append("https://www.zhihu.com/api/v3/feed/topstory/recommend?action_feed=True&limit=$limit")
        if (offset != null) {
            append("&offset=$offset")
        }
    }

internal suspend fun cleanupLocalRecommendationData(
    dao: LocalContentDao,
    nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
) {
    val oneWeekAgo = nowMillis - 7 * 24 * 60 * 60 * 1000L
    val oneMonthAgo = nowMillis - 30 * 24 * 60 * 60 * 1000L

    dao.cleanupOldTasks(CrawlingStatus.Completed, oneWeekAgo)
    dao.cleanupOldTasks(CrawlingStatus.Failed, oneWeekAgo)
    dao.cleanupOldResults(oneMonthAgo)
    dao.cleanupOldFeeds(oneMonthAgo)
    dao.cleanupOldBehaviors(oneMonthAgo)
}

internal suspend fun buildFallbackRecommendations(
    dao: LocalContentDao,
    userBehaviorAnalyzer: UserBehaviorAnalyzer,
    feedGenerator: FeedGenerator,
    limit: Int,
): List<LocalRecommendationEntry> = withContext(Dispatchers.Default) {
    val behaviorProfile = userBehaviorAnalyzer.buildBehaviorProfile()

    dao
        .getRecentResults(limit * 3)
        .mapNotNull { candidate ->
            val identity = parseLocalContentIdentity(candidate.contentId, candidate.url) ?: return@mapNotNull null
            if (candidate.contentId == identity.value) {
                candidate
            } else {
                candidate.copy(contentId = identity.value)
            }
        }.distinctBy { it.contentId }
        .mapNotNull { candidate ->
            rankCandidate(
                candidate = candidate,
                behaviorProfile = behaviorProfile,
            )
        }.sortedByDescending { it.finalScore }
        .take(limit)
        .mapNotNull { rankedResult ->
            toRecommendationEntry(
                rankedResult = rankedResult,
                feedGenerator = feedGenerator,
            )
        }
}

internal suspend fun collectCandidateResults(
    dao: LocalContentDao,
    limit: Int,
): List<CrawlingResult> = withContext(Dispatchers.Default) {
    CrawlingReason.entries
        .flatMap { reason ->
            dao.getResultsByReason(reason).take(limit * 2)
        }.mapNotNull { candidate ->
            val identity = parseLocalContentIdentity(candidate.contentId, candidate.url) ?: return@mapNotNull null
            if (candidate.contentId == identity.value) {
                candidate
            } else {
                candidate.copy(contentId = identity.value)
            }
        }.distinctBy { it.contentId }
}

internal suspend fun ensurePendingTasks(dao: LocalContentDao) {
    val tasks = mutableListOf<CrawlingTask>()

    CrawlingReason.entries.forEach { reason ->
        val pendingCount = dao.getTaskCountByReasonAndStatus(reason, CrawlingStatus.NotStarted)
        val inProgressCount = dao.getTaskCountByReasonAndStatus(reason, CrawlingStatus.InProgress)

        if (pendingCount + inProgressCount < 2) {
            val (url, priority) = when (reason) {
                CrawlingReason.Following -> zhihuFollowingRecommendUrl() to 8
                CrawlingReason.Trending -> zhihuTopstoryRecommendUrl(limit = 20) to 7
                CrawlingReason.UpvotedQuestion -> zhihuTopstoryRecommendUrl(limit = 15) to 6
                CrawlingReason.FollowingUpvote -> zhihuFollowingUpvoteRecommendUrl(limit = 15) to 5
                CrawlingReason.CollaborativeFiltering -> zhihuTopstoryRecommendUrl(limit = 10) to 4
            }
            repeat(3 - pendingCount - inProgressCount) {
                tasks.add(
                    CrawlingTask(
                        url = url,
                        reason = reason,
                        priority = priority,
                    ),
                )
            }
        }
    }

    if (tasks.isNotEmpty()) {
        dao.insertTasks(tasks)
    }
}

internal suspend fun waitForTaskCompletion(
    dao: LocalContentDao,
    maxWaitTimeMs: Long,
) {
    val startTime = Clock.System.now().toEpochMilliseconds()
    val checkInterval = 500L

    while (Clock.System.now().toEpochMilliseconds() - startTime < maxWaitTimeMs) {
        if (dao.getTasksByStatus(CrawlingStatus.InProgress).isEmpty()) {
            break
        }
        delay(checkInterval)
    }
}

internal fun createCrawlingResult(
    element: JsonElement,
    taskId: Long,
    reason: CrawlingReason,
    scoreMultiplier: Double = 1.0,
    accountId: String?,
): CrawlingResult? {
    val feed = ZhihuJson.decodeJson<Feed>(element)
    val target = feed.target ?: return null
    if (reason == CrawlingReason.FollowingUpvote && !isVoteupFeed(feed)) return null
    val source = element.jsonObject["target"] as? JsonObject ?: return null
    val votes = ZhihuJson.decodeJson<LocalVoteCounts>(source)
    val author = (source["author"] as? JsonObject)?.let { ZhihuJson.decodeJson<LocalAuthorCounts>(it) }
    val facts = FeedFilterFacts(
        voteCount = when (target) {
            is Feed.AnswerTarget, is Feed.ArticleTarget -> votes.voteupCount
            is Feed.VideoTarget -> votes.voteCount
            else -> null
        }?.takeIf { it >= 0 },
        authorId = target.author?.id?.takeIf(String::isNotBlank),
        authorIsFollowing = author?.isFollowing?.takeIf { accountId != null },
        authorFollowers = author?.followersCount?.takeIf { it >= 0 },
    )

    return CrawlingResult(
        taskId = taskId,
        contentId = target.toLocalContentIdentity().value,
        title = target.title,
        summary = target.excerpt ?: "",
        url = target.url,
        reason = reason,
        score = scoreFeedTarget(target) * scoreMultiplier,
        filterFactsJson = if (facts == FeedFilterFacts()) null else Json.encodeToString(LocalFilterSnapshot(facts, accountId)),
    )
}

@Serializable
private data class LocalVoteCounts(
    val voteupCount: Int? = null,
    val voteCount: Int? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private data class LocalAuthorCounts(
    val isFollowing: Boolean? = null,
    @JsonNames("followerCount") val followersCount: Int? = null,
)

@Serializable
private data class LocalFilterSnapshot(
    val facts: FeedFilterFacts,
    val accountId: String?,
)

/** 候选库由设备共享，只有采集时的账号可使用当时的关注关系。 */
internal fun CrawlingResult.filterFactsFor(accountId: String?): FeedFilterFacts? = filterFactsJson?.let {
    val snapshot = ZhihuJson.json.decodeFromString<LocalFilterSnapshot>(it)
    if (accountId != null && accountId == snapshot.accountId) snapshot.facts else snapshot.facts.copy(authorIsFollowing = null)
}

internal fun isVoteupFeed(feed: Feed): Boolean =
    feed is CommonFeed && (feed.brief.contains("赞同") || feed.brief.contains("点赞") || feed.attachedInfo.contains("VOTEUP"))

internal fun extractQuestionIdFromUrl(url: String): String? {
    val regex = """question/(\d+)""".toRegex()
    return regex.find(url)?.groupValues?.get(1)
}

internal fun extractQuestionIdFromContentId(contentId: String): String? {
    val identity = parseLocalContentIdentity(contentId, "")
    return when {
        identity?.type == "question" -> identity.id
        ':' !in contentId -> contentId.filter(Char::isDigit).ifBlank { null }
        else -> null
    }
}

internal fun rankCandidate(
    candidate: CrawlingResult,
    behaviorProfile: UserBehaviorAnalyzer.RecommendationBehaviorProfile,
): RankedLocalResult? {
    val identity = parseLocalContentIdentity(candidate.contentId, candidate.url) ?: return null
    val normalizedResult = if (candidate.contentId == identity.value) {
        candidate
    } else {
        candidate.copy(contentId = identity.value)
    }

    val reasonPreference = behaviorProfile.reasonPreferences[normalizedResult.reason] ?: LocalReasonPreference(1.0)
    val contentAffinity = behaviorProfile.contentAffinities[normalizedResult.contentId]
    val reasonWeight = normalizedResult.reason.defaultWeight * reasonPreference.multiplier
    val contentWeight = contentAffinity?.multiplier ?: 1.0
    val freshnessWeight = getFreshnessWeight(normalizedResult.createdAt)
    val finalScore = normalizedResult.score * reasonWeight * contentWeight * freshnessWeight

    return RankedLocalResult(
        result = normalizedResult,
        finalScore = finalScore,
        reasonDisplay = buildLocalRecommendationReason(
            baseReason = normalizedResult.reason.displayText,
            reasonPreference = reasonPreference,
            contentAffinity = contentAffinity,
        ),
    )
}

private suspend fun toRecommendationEntry(
    rankedResult: RankedLocalResult,
    feedGenerator: FeedGenerator,
): LocalRecommendationEntry? {
    val identity = parseLocalContentIdentity(rankedResult.result.contentId, rankedResult.result.url) ?: return null
    val navDestination = identity.toNavDestination(rankedResult.result.title) ?: return null
    val localFeed = feedGenerator.generateFeedFromResult(rankedResult.result, rankedResult.reasonDisplay)
    return LocalRecommendationEntry(
        feed = localFeed,
        result = rankedResult.result,
        navDestination = navDestination,
    )
}

internal fun getFreshnessWeight(
    createdAt: Long,
    nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
): Double {
    val ageMillis = (nowMillis - createdAt).coerceAtLeast(0L)
    val ageHours = ageMillis / (60.0 * 60.0 * 1000.0)
    return when {
        ageHours < 12 -> 1.08
        ageHours < 48 -> 1.0
        ageHours < 168 -> 0.92
        else -> 0.82
    }
}
