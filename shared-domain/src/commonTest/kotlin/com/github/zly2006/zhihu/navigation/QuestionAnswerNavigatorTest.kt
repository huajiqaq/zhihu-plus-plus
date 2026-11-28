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

package com.github.zly2006.zhihu.navigation

import com.github.zly2006.zhihu.data.QualityFilterSettings
import com.github.zly2006.zhihu.viewmodel.FeedDisplaySettings
import com.github.zly2006.zhihu.viewmodel.QualityFilterMode
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import io.ktor.client.HttpClient
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class QuestionAnswerNavigatorTest {
    @BeforeTest
    fun ignoreOpenedAnswersByDefault() {
        openedAnswerIdsReaderForTesting = { emptySet() }
    }

    @AfterTest
    fun clearOpenedAnswersReader() {
        openedAnswerIdsReaderForTesting = null
    }

    @Test
    fun remainingAnswerSnapshotKeepsForwardHistoryBeforeSourceQueueWithoutConsumingEither() = runTest {
        val navigator = QuestionAnswerNavigator(
            questionId = 1L,
            initialNextAnswers = listOf(answer(104L), answer(105L)),
            environment = NoopEnvironment,
            displaySettings = { FeedDisplaySettings(QualityFilterMode.OFF) },
        )
        navigator.pushAnswer(answer(101L).toCachedContent())
        navigator.pushAnswer(answer(102L).toCachedContent())
        navigator.pushAnswer(answer(103L).toCachedContent())
        assertEquals(102L, navigator.goToPrevious()?.article?.id)

        val expected = listOf(103L, 104L, 105L)
        assertEquals(expected, navigator.remainingAnswersSnapshot(102L, limit = 8).map(Article::id))
        assertEquals(expected, navigator.remainingAnswersSnapshot(102L, limit = 8).map(Article::id))
        assertEquals(103L, navigator.goToNext()?.article?.id)
        assertEquals(104L, navigator.loadNext()?.id)
        assertEquals(105L, navigator.loadNext()?.id)
    }

    @Test
    fun directArticleScreenReentrySkipsAnswersViewedInPreviousSession() = runTest {
        val environment = FeedEnvironment(listOf(101L, 102L, 103L, 104L, 105L))
        val openedAnswerIds = mutableSetOf(101L, 102L)
        openedAnswerIdsReaderForTesting = { ids -> ids.filterTo(mutableSetOf()) { it in openedAnswerIds } }
        val navigator = QuestionAnswerNavigator(
            questionId = 1L,
            environment = environment,
            displaySettings = { FeedDisplaySettings(QualityFilterMode.OFF) },
        )

        navigator.pushAnswer(answer(101L).toCachedContent())
        assertEquals(103L, navigator.loadNext()?.id)
        openedAnswerIds += 103L
        navigator.pushAnswer(answer(103L).toCachedContent())
        assertEquals(104L, navigator.loadNext()?.id)
        openedAnswerIds += 104L
        navigator.pushAnswer(answer(104L).toCachedContent())

        navigator.pushAnswer(answer(101L).toCachedContent())
        assertEquals(105L, navigator.loadNext()?.id)
        assertNull(navigator.loadNext())
    }

    @Test
    fun loadNextSkipsHiddenAnswersOnTheNextPageAfterAnEmptyPage() = runTest {
        val firstUrl = zhihuQuestionFeedsUrl(1L, limit = 6)
        val nextUrl = "$firstUrl&cursor=opaque"
        val environment = FeedEnvironment(
            answerIds = emptyList(),
            pages = mapOf(
                firstUrl to feedPage(listOf(answerFeed(102L, votes = 25, questionCard = true)), nextUrl),
                nextUrl to feedPage(listOf(answerFeed(104L, votes = 25, questionCard = true), answerFeed(103L, votes = 100, questionCard = true)), firstUrl),
            ),
        )
        val navigator = QuestionAnswerNavigator(
            questionId = 1L,
            environment = environment,
            displaySettings = { FeedDisplaySettings(QualityFilterMode.HIDE, QualityFilterSettings(answerVoteupCount = 100)) },
        )
        navigator.pushAnswer(answer(101L).toCachedContent())
        assertEquals(103L, navigator.loadNext()?.id)
        assertNull(navigator.loadNext())
        assertEquals(listOf(firstUrl, nextUrl), environment.requests)
    }

    private fun feedPage(items: List<JsonObject>, next: String) = buildJsonObject {
        put("data", buildJsonArray { items.forEach(::add) })
        put(
            "paging",
            buildJsonObject {
                put("is_end", false)
                put("next", next)
            },
        )
    }

    private fun answer(id: Long) = Article(id = id, type = ArticleType.Answer)

    private fun Article.toCachedContent() = com.github.zly2006.zhihu.viewmodel.ArticleViewModel.CachedAnswerContent(
        article = this,
        title = "question",
        authorName = "author",
        authorBio = "",
        authorAvatarUrl = "",
        content = "",
        voteUpCount = 0,
        commentCount = 0,
        sourceLabel = "此问题",
    )

    private object NoopEnvironment : ZhihuApiEnvironment {
        override fun httpClient(): HttpClient = error("不应使用此客户端")

        override fun authenticatedCookies(): Map<String, String> = emptyMap()

        override suspend fun handleFetchFailure(
            tag: String?,
            error: Exception,
        ): Unit = throw error
    }

    private inner class FeedEnvironment(
        private val answerIds: List<Long>,
        private val pages: Map<String, JsonObject> = emptyMap(),
    ) : ZhihuApiEnvironment {
        val requests = mutableListOf<String>()

        override fun httpClient(): HttpClient = error("不应使用此客户端")

        override fun authenticatedCookies(): Map<String, String> = emptyMap()

        override suspend fun fetchJson(
            url: String,
            include: String,
        ): JsonObject {
            requests += url
            if (pages.isNotEmpty()) return pages.getValue(url)
            return buildJsonObject {
                put("data", buildJsonArray { answerIds.forEach { add(answerFeed(it)) } })
            }
        }

        override suspend fun handleFetchFailure(
            tag: String?,
            error: Exception,
        ): Unit = throw error
    }

    private fun answerFeed(answerId: Long, votes: Int = -1, questionCard: Boolean = false) = buildJsonObject {
        put("type", if (questionCard) "question_feed_card" else "feed")
        if (questionCard) {
            put("cursor", "answer-$answerId")
            put("target_type", "answer")
        }
        put("target", answerTarget(answerId, votes))
    }

    private fun answerTarget(answerId: Long, votes: Int) = buildJsonObject {
        put("type", JsonPrimitive("answer"))
        put("id", JsonPrimitive(answerId))
        put("url", JsonPrimitive("https://www.zhihu.com/question/1/answer/$answerId"))
        put("question", questionTarget())
        put("voteup_count", votes)
    }

    private fun questionTarget() = buildJsonObject {
        put("type", JsonPrimitive("question"))
        put("id", JsonPrimitive(1L))
        put("title", JsonPrimitive("question"))
        put("url", JsonPrimitive("https://www.zhihu.com/question/1"))
    }
}
