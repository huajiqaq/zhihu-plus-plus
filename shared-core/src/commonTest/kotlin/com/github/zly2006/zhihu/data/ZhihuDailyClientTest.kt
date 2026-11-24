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

package com.github.zly2006.zhihu.data

import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Question
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ZhihuDailyClientTest {
    private val story = DailyStory(100, "日报卡片标题", "https://daily.zhihu.com/story/100", "编辑推荐 · 3 分钟阅读", listOf("cover"), 0)

    @Test
    fun dailyStoryNavigationResolvesOriginalContentWithoutAdditionalRequests() = runTest {
        val cases: List<Pair<String, NavDestination?>> = listOf(
            "<a class='originUrl' href='https://www.zhihu.com/answer/123'>原文</a>" to Article(type = ArticleType.Answer, id = 123),
            "<a class='originUrl' href='https://example.com/unknown'>未知</a><div class='view-more'><a href='https://zhuanlan.zhihu.com/p/123'>更多</a></div>" to Article(type = ArticleType.Article, id = 123),
            "<div class='view-more'><a href='https://www.zhihu.com/question/456'>更多</a></div>" to Question(456),
            "<a class='originUrl' href='https://zhuanlan.zhihu.com/p/789'>专栏原文</a>" to Article(type = ArticleType.Article, id = 789),
            """
            <a class='originUrl' href='https://www.zhihu.com/answer/invalid'>失效链接</a>
            <a class='originUrl' href='https://www.zhihu.com/question/456/answer/123'>原文</a>
            <div class='view-more'><a href='https://www.zhihu.com/question/456'>更多</a></div>
            """.trimIndent() to Article(type = ArticleType.Answer, id = 123),
            """
            <a class='originUrl' href='https://www.zhihu.com/question/456'>问题</a>
            <div class='view-more'>
              <a href='https://zhuanlan.zhihu.com/p/0'>无效文章</a>
              <a href='https://zhuanlan.zhihu.com/p/789'>专栏原文</a>
            </div>
            """.trimIndent() to Article(type = ArticleType.Article, id = 789),
            """
            <p><a href='https://www.zhihu.com/answer/123'>正文引用</a></p>
            <a class='originUrl' href='https://www.zhihu.com/people/author'>作者</a>
            <div class='view-more'><a href='${story.url}'>日报网页</a></div>
            """.trimIndent() to null,
            "" to null,
        )
        cases.forEach { (body, expected) ->
            var requestCount = 0
            val client = HttpClient(
                MockEngine { request ->
                    requestCount++
                    assertEquals(HttpMethod.Get, request.method)
                    assertEquals("https://daily.zhihu.com/api/7/story/100", request.url.toString())
                    val detail = buildJsonObject {
                        put("url", story.url)
                        put("body", body)
                    }
                    respond(detail.toString(), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                },
            ) {
                installZhihuCommonClientConfig(cookies = mutableMapOf(), userAgent = "test-agent")
            }
            try {
                val destination = client.fetchDailyStoryDestination(story)
                if (expected == null) {
                    assertNull(destination, body)
                } else {
                    checkNotNull(destination)
                    assertEquals(expected.toFeedDisplayItemNavDestinationJson(), destination.toFeedDisplayItemNavDestinationJson())
                }
                assertEquals(1, requestCount)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun dailyStoryNavigationPropagatesDetailRequestFailures() = runTest {
        var requestCount = 0
        val client = HttpClient(
            MockEngine {
                when (++requestCount) {
                    1 -> respond("{}", HttpStatusCode.ServiceUnavailable, headersOf(HttpHeaders.ContentType, "application/json"))
                    2 -> throw UnresolvedAddressException()
                    else -> throw CancellationException("request cancelled")
                }
            },
        ) {
            installZhihuCommonClientConfig(cookies = mutableMapOf(), userAgent = "test-agent")
        }
        try {
            assertFailsWith<ServerResponseException> { client.fetchDailyStoryDestination(story) }
            assertFailsWith<UnresolvedAddressException> { client.fetchDailyStoryDestination(story) }
            assertFailsWith<CancellationException> { client.fetchDailyStoryDestination(story) }
            assertEquals(3, requestCount)
        } finally {
            client.close()
        }
    }

    @Test
    fun fetchDailyStoriesForDateRequestsNextDayBeforeEndpoint() = runTest {
        val client = dailyMockClient { url ->
            assertEquals("https://news-at.zhihu.com/api/4/stories/before/20260521", url)
        }

        val response = client.fetchDailyStoriesBefore("20260521")

        assertEquals("20260520", response.date)
        assertEquals(1L, response.stories.single().id)
    }

    @Test
    fun fetchLatestDailyStoriesRequestsLatestEndpoint() = runTest {
        val client = dailyMockClient { url ->
            assertEquals("https://news-at.zhihu.com/api/4/stories/latest", url)
        }

        val response = client.fetchLatestDailyStories()

        assertEquals("20260520", response.date)
    }

    @Test
    fun fetchLatestDailyStoriesFallsBackToDailyHostWhenPrimaryHostCannotResolve() = runTest {
        val requestedUrls = mutableListOf<String>()
        val client = HttpClient(
            MockEngine { request ->
                requestedUrls += request.url.toString()
                if (request.url.host == "news-at.zhihu.com") {
                    throw UnresolvedAddressException()
                }
                respondDailyStories()
            },
        ) {
            installZhihuCommonClientConfig(
                cookies = mutableMapOf(),
                userAgent = "test-agent",
            )
        }

        val response = client.fetchLatestDailyStories()

        assertEquals("20260520", response.date)
        assertEquals(
            listOf(
                "https://news-at.zhihu.com/api/4/stories/latest",
                "https://daily.zhihu.com/api/4/stories/latest",
            ),
            requestedUrls,
        )
    }

    @Test
    fun fetchLatestDailyStoriesFallsBackForTopLevelHostResolutionMessage() = runTest {
        val requestedUrls = mutableListOf<String>()
        val client = HttpClient(
            MockEngine { request ->
                requestedUrls += request.url.toString()
                if (request.url.host == "news-at.zhihu.com") {
                    throw Exception("Unable to resolve host news-at.zhihu.com")
                }
                respondDailyStories()
            },
        ) {
            installZhihuCommonClientConfig(
                cookies = mutableMapOf(),
                userAgent = "test-agent",
            )
        }

        val response = client.fetchLatestDailyStories()

        assertEquals("20260520", response.date)
        assertEquals(
            listOf(
                "https://news-at.zhihu.com/api/4/stories/latest",
                "https://daily.zhihu.com/api/4/stories/latest",
            ),
            requestedUrls,
        )
    }

    @Test
    fun fetchDailyStoriesBeforeFallsBackToDailyHostWhenPrimaryHostCannotResolve() = runTest {
        val requestedUrls = mutableListOf<String>()
        val client = HttpClient(
            MockEngine { request ->
                requestedUrls += request.url.toString()
                if (request.url.host == "news-at.zhihu.com") {
                    throw UnresolvedAddressException()
                }
                respondDailyStories()
            },
        ) {
            installZhihuCommonClientConfig(
                cookies = mutableMapOf(),
                userAgent = "test-agent",
            )
        }

        val response = client.fetchDailyStoriesBefore("20260521")

        assertEquals("20260520", response.date)
        assertEquals(
            listOf(
                "https://news-at.zhihu.com/api/4/stories/before/20260521",
                "https://daily.zhihu.com/api/4/stories/before/20260521",
            ),
            requestedUrls,
        )
    }

    @Test
    fun fetchDailyStoriesBeforeDoesNotFallbackForNestedHostResolutionMessageOnly() = runTest {
        val requestedUrls = mutableListOf<String>()
        val client = HttpClient(
            MockEngine { request ->
                requestedUrls += request.url.toString()
                throw Exception(
                    "daily api unavailable",
                    Exception("Unable to resolve host news-at.zhihu.com"),
                )
            },
        ) {
            installZhihuCommonClientConfig(
                cookies = mutableMapOf(),
                userAgent = "test-agent",
            )
        }

        assertFailsWith<Exception> {
            client.fetchDailyStoriesBefore("20260521")
        }
        assertEquals(
            listOf("https://news-at.zhihu.com/api/4/stories/before/20260521"),
            requestedUrls,
        )
    }

    @Test
    fun fetchLatestDailyStoriesDoesNotFallbackForNonHostFailures() = runTest {
        val requestedUrls = mutableListOf<String>()
        val client = HttpClient(
            MockEngine { request ->
                requestedUrls += request.url.toString()
                throw IllegalStateException("daily api unavailable")
            },
        ) {
            installZhihuCommonClientConfig(
                cookies = mutableMapOf(),
                userAgent = "test-agent",
            )
        }

        assertFailsWith<IllegalStateException> {
            client.fetchLatestDailyStories()
        }
        assertEquals(
            listOf("https://news-at.zhihu.com/api/4/stories/latest"),
            requestedUrls,
        )
    }

    @Test
    fun fetchLatestDailyStoriesPropagatesCancellationWithoutFallback() = runTest {
        val requestedUrls = mutableListOf<String>()
        val client = HttpClient(
            MockEngine { request ->
                requestedUrls += request.url.toString()
                throw CancellationException("request cancelled")
            },
        ) {
            installZhihuCommonClientConfig(
                cookies = mutableMapOf(),
                userAgent = "test-agent",
            )
        }

        assertFailsWith<CancellationException> {
            client.fetchLatestDailyStories()
        }
        assertEquals(
            listOf("https://news-at.zhihu.com/api/4/stories/latest"),
            requestedUrls,
        )
    }

    @Test
    fun fetchLatestDailyStoriesPropagatesFallbackFailureWhenBothHostsCannotResolve() = runTest {
        val requestedUrls = mutableListOf<String>()
        val client = HttpClient(
            MockEngine { request ->
                requestedUrls += request.url.toString()
                throw UnresolvedAddressException()
            },
        ) {
            installZhihuCommonClientConfig(
                cookies = mutableMapOf(),
                userAgent = "test-agent",
            )
        }

        assertFailsWith<UnresolvedAddressException> {
            client.fetchLatestDailyStories()
        }
        assertEquals(
            listOf(
                "https://news-at.zhihu.com/api/4/stories/latest",
                "https://daily.zhihu.com/api/4/stories/latest",
            ),
            requestedUrls,
        )
    }

    private fun dailyMockClient(assertUrl: (String) -> Unit): HttpClient = HttpClient(
        MockEngine { request ->
            assertUrl(request.url.toString())
            respondDailyStories()
        },
    ) {
        installZhihuCommonClientConfig(
            cookies = mutableMapOf(),
            userAgent = "test-agent",
        )
    }

    private fun MockRequestHandleScope.respondDailyStories() = respond(
        content =
            """
            {
              "date": "20260520",
              "stories": [
                {
                  "id": 1,
                  "title": "story",
                  "url": "https://example.com/story",
                  "hint": "hint",
                  "images": [],
                  "type": 0
                }
              ]
            }
            """.trimIndent(),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )
}
