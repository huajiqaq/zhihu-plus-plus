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

package com.github.zly2006.zhihu.viewmodel.feed

import androidx.lifecycle.viewModelScope
import com.github.zly2006.zhihu.data.LocalHistoryStorage
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.viewmodel.ContentLoadEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class QuestionMetadataLoaderTest {
    @Test
    fun realLoaderPreservesIdentityThroughHttpAndDecodeFailureThenRetries() = runTest {
        withIsolatedOwner {
            val destination = Question(42, "路由原始标题")
            var body = questionJson("完整问题标题 <不可截断>", "first-author")
            var status = HttpStatusCode.OK
            val requests = mutableListOf<Url>()
            val client = own(
                HttpClient(
                    MockEngine { request ->
                        assertEquals(HttpMethod.Get, request.method)
                        requests += request.url
                        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                    },
                ),
            )
            val environment = RecordingEnvironment(client)
            val model = QuestionFeedViewModel(42).also(models::add)
            assertSame(client, environment.httpClient())
            assertSame(client, environment.httpClient())

            model.loadQuestionDetail(environment, destination)
            val cached = assertNotNull(model.questionDetail)
            assertEquals(42L, cached.id)
            assertEquals("完整问题标题 <不可截断>", cached.title)
            assertEquals("first-author", cached.author.id)
            assertEquals("完整作者名称 first-author", cached.author.name)
            assertEquals("<p>完整问题正文</p>", cached.detail)
            assertFalse(model.isQuestionDetailLoading)
            assertNull(model.questionDetailError)
            assertEquals(listOf<Pair<NavDestination, Long?>>(destination to 42L), environment.opens)
            assertEquals(cached.title, (LocalHistoryStorage().history.single() as Question).title)

            status = HttpStatusCode.InternalServerError
            body = """{"error":{"message":"server unavailable"}}"""
            model.loadQuestionDetail(environment, destination)
            assertSame(cached, model.questionDetail)
            assertFalse(model.isQuestionDetailLoading)
            assertTrue(assertNotNull(model.questionDetailError).isNotBlank())
            assertEquals(1, environment.opens.size)

            status = HttpStatusCode.OK
            body = """{"id":42,"title":"缺少必需作者的响应"}"""
            model.loadQuestionDetail(environment, destination)
            assertSame(cached, model.questionDetail)
            assertFalse(model.isQuestionDetailLoading)
            assertTrue(assertNotNull(model.questionDetailError).isNotBlank())
            assertEquals(1, environment.opens.size)

            body = questionJson("重试后的完整标题", "retry-author")
            model.loadQuestionDetail(environment, destination)
            val retried = assertNotNull(model.questionDetail)
            assertEquals(42L, retried.id)
            assertEquals("重试后的完整标题", retried.title)
            assertEquals("retry-author", retried.author.id)
            assertEquals("完整作者名称 retry-author", retried.author.name)
            assertNull(model.questionDetailError)
            assertFalse(model.isQuestionDetailLoading)
            assertEquals(listOf<Pair<NavDestination, Long?>>(destination to 42L, destination to 42L), environment.opens)
            assertEquals(retried.title, (LocalHistoryStorage().history.single() as Question).title)
            assertEquals(4, requests.size)
            requests.forEach { url ->
                assertEquals(URLProtocol.HTTPS, url.protocol)
                assertEquals("www.zhihu.com", url.host)
                assertEquals("/api/v4/questions/42", url.encodedPath)
                assertEquals(setOf("include"), url.parameters.names())
                assertEquals(
                    "read_count,visit_count,answer_count,voteup_count,comment_count,follower_count,detail,excerpt,author,relationship.is_following,topics",
                    url.parameters["include"],
                )
            }
        }
    }

    @Test
    fun clientSwitchDuringRealLoadCannotPublishLateContentAndNewClientCanRetry() = runTest {
        withIsolatedOwner {
            val destination = Question(42, "路由标题")
            var oldBody = questionJson("已有完整标题", "cached-author")
            val oldRequests = mutableListOf<Url>()
            val oldClient = own(
                HttpClient(
                    MockEngine { request ->
                        oldRequests += request.url
                        respond(oldBody, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    },
                ),
            )
            val newRequests = mutableListOf<Url>()
            val newClient = own(
                HttpClient(
                    MockEngine { request ->
                        newRequests += request.url
                        respond(questionJson("最新客户端标题", "latest-author"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    },
                ),
            )
            val environment = RecordingEnvironment(oldClient)
            val model = QuestionFeedViewModel(42).also(models::add)
            model.loadQuestionDetail(environment, destination)
            val cached = assertNotNull(model.questionDetail)

            val reachedOpen = CompletableDeferred<Unit>()
            val releaseOpen = CompletableDeferred<Unit>()
            environment.beforeOpen = {
                reachedOpen.complete(Unit)
                releaseOpen.await()
            }
            oldBody = questionJson("旧客户端晚到标题", "stale-author")
            val staleLoad = launch { model.loadQuestionDetail(environment, destination) }
            try {
                reachedOpen.await()
                assertTrue(model.isQuestionDetailLoading)
                assertSame(cached, model.questionDetail)
                val requestsWhileLoading = oldRequests.size
                model.loadQuestionDetail(environment, destination)
                assertEquals(requestsWhileLoading, oldRequests.size)

                environment.client = newClient
                assertSame(newClient, environment.httpClient())
                releaseOpen.complete(Unit)
                staleLoad.join()
                assertTrue(staleLoad.isCancelled, "旧客户端加载应传播取消，不能作为成功回写")
                assertSame(cached, model.questionDetail)
                assertEquals("cached-author", model.questionDetail?.author?.id)
                assertNull(model.questionDetailError)
                assertFalse(model.isQuestionDetailLoading)

                environment.beforeOpen = {}
                model.loadQuestionDetail(environment, destination)
                val latest = assertNotNull(model.questionDetail)
                assertEquals(42L, latest.id)
                assertEquals("最新客户端标题", latest.title)
                assertEquals("latest-author", latest.author.id)
                assertEquals(2, oldRequests.size)
                assertEquals(1, newRequests.size)
                assertNull(model.questionDetailError)
                assertFalse(model.isQuestionDetailLoading)
                assertEquals(latest.title, (LocalHistoryStorage().history.single() as Question).title)
            } finally {
                releaseOpen.complete(Unit)
                staleLoad.cancel()
                staleLoad.join()
            }
        }
    }

    private suspend fun TestScope.withIsolatedOwner(block: suspend Fixture.() -> Unit) {
        val home = Files.createTempDirectory("question-metadata-loader-")
        val previousHome = System.getProperty("user.home")
        val fixture = Fixture()
        System.setProperty("user.home", home.toString())
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            fixture.block()
        } finally {
            fixture.models.forEach { it.viewModelScope.cancel() }
            fixture.clients.forEach { client ->
                client.close()
                client.engine.close()
            }
            runCurrent()
            Dispatchers.resetMain()
            if (previousHome == null) System.clearProperty("user.home") else System.setProperty("user.home", previousHome)
            check(home.toFile().deleteRecursively())
        }
    }

    private class Fixture {
        val clients = mutableListOf<HttpClient>()
        val models = mutableListOf<QuestionFeedViewModel>()

        fun own(client: HttpClient): HttpClient = client.also(clients::add)
    }

    private class RecordingEnvironment(
        var client: HttpClient,
    ) : ContentLoadEnvironment {
        val opens = mutableListOf<Pair<NavDestination, Long?>>()
        var beforeOpen: suspend () -> Unit = {}

        override fun httpClient(): HttpClient = client

        override fun authenticatedCookies(): Map<String, String> = emptyMap()

        override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit

        override suspend fun recordContentOpenEvent(destination: NavDestination, questionId: Long?, openFrom: String) {
            beforeOpen()
            opens += destination to questionId
        }
    }

    private fun questionJson(title: String, authorId: String): String =
        """
        {
          "type":"question","id":42,"title":"$title","question_type":"normal",
          "created":1,"updated_time":2,"url":"https://www.zhihu.com/question/42",
          "answer_count":3,"visit_count":4,"comment_count":5,"follower_count":6,
          "detail":"<p>完整问题正文</p>","relationship":{"is_following":false},"topics":[],"voteup_count":7,
          "author":{"avatar_url":"https://example.invalid/avatar.png","gender":0,"headline":"完整作者简介",
            "id":"$authorId","is_advertiser":false,"is_org":false,"name":"完整作者名称 $authorId",
            "type":"people","url":"https://www.zhihu.com/people/$authorId","url_token":"$authorId","user_type":"people"}
        }
        """.trimIndent()
}
