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

import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    ) : ZhihuApiEnvironment {
        override fun httpClient(): HttpClient = error("不应使用此客户端")

        override fun authenticatedCookies(): Map<String, String> = emptyMap()

        override suspend fun fetchJson(
            url: String,
            include: String,
        ): JsonObject = buildJsonObject {
            put(
                "data",
                buildJsonArray {
                    answerIds.forEach { answerId ->
                        add(answerFeed(answerId))
                    }
                },
            )
        }

        override suspend fun handleFetchFailure(
            tag: String?,
            error: Exception,
        ): Unit = throw error
    }

    private fun answerFeed(answerId: Long) = buildJsonObject {
        put("type", JsonPrimitive("feed"))
        put("target", answerTarget(answerId))
    }

    private fun answerTarget(answerId: Long) = buildJsonObject {
        put("type", JsonPrimitive("answer"))
        put("id", JsonPrimitive(answerId))
        put("url", JsonPrimitive("https://www.zhihu.com/question/1/answer/$answerId"))
        put("question", questionTarget())
    }

    private fun questionTarget() = buildJsonObject {
        put("type", JsonPrimitive("question"))
        put("id", JsonPrimitive(1L))
        put("title", JsonPrimitive("question"))
        put("url", JsonPrimitive("https://www.zhihu.com/question/1"))
    }
}
