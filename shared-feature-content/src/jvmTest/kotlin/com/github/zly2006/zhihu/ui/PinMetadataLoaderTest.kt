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

package com.github.zly2006.zhihu.ui

import androidx.lifecycle.viewModelScope
import com.github.zly2006.zhihu.account.ZhihuAccountProfileSnapshot
import com.github.zly2006.zhihu.account.ZhihuAccountRepository
import com.github.zly2006.zhihu.account.ZhihuAccountSession
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.LocalHistoryStorage
import com.github.zly2006.zhihu.data.installZhihuCommonClientConfig
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.viewmodel.ContentLoadEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
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
import kotlinx.io.files.Path
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PinMetadataLoaderTest {
    @Test
    fun realLoaderPreservesIdentityThroughHttpAndDecodeFailureThenRetries() = runTest {
        withIsolatedOwner {
            val destination = Pin(42, "路由作者")
            var body = pinJson("完整想法标题 <不可截断>", "first-author")
            var status = HttpStatusCode.OK
            val requests = mutableListOf<Url>()
            val client = own(
                HttpClient(
                    MockEngine { request ->
                        assertEquals(HttpMethod.Get, request.method)
                        requests += request.url
                        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                    },
                ) {
                    installZhihuCommonClientConfig(mutableMapOf(), "synthetic-test")
                },
            )
            val environment = RecordingEnvironment(client)
            val model = PinScreenData().also(models::add)
            assertSame(client, environment.httpClient())
            assertSame(client, environment.httpClient())

            model.load(environment, destination)
            val cached = assertNotNull(model.content.value)
            assertEquals("42", cached.id)
            assertEquals("完整想法标题 <不可截断>", cached.excerptTitle)
            assertEquals("first-author", cached.author.id)
            assertEquals("完整作者名称 first-author", cached.author.name)
            assertEquals("<p>完整想法正文</p>", (cached.content.single() as DataHolder.Pin.ContentText).content)
            assertEquals("IP 属地合成属地", cached.ipInfo)
            assertEquals(7, model.likeCount.intValue)
            assertTrue(model.isLiked.value)
            assertFalse(model.isLoading.value)
            assertNull(model.errorMessage.value)
            assertEquals(listOf<Pair<NavDestination, Long?>>(destination to null), environment.opens)
            assertEquals(cached.author.name, (LocalHistoryStorage().history.single() as Pin).authorName)

            status = HttpStatusCode.InternalServerError
            body = """{"error":{"message":"server unavailable"}}"""
            model.load(environment, destination)
            assertSame(cached, model.content.value)
            assertFalse(model.isLoading.value)
            assertTrue(assertNotNull(model.errorMessage.value).isNotBlank())
            assertEquals(7, model.likeCount.intValue)
            assertTrue(model.isLiked.value)
            assertEquals(1, environment.opens.size)

            status = HttpStatusCode.OK
            body = """{"id":"42","excerpt_title":"缺少必需作者的响应"}"""
            model.load(environment, destination)
            assertSame(cached, model.content.value)
            assertFalse(model.isLoading.value)
            assertTrue(assertNotNull(model.errorMessage.value).isNotBlank())
            assertEquals(1, environment.opens.size)

            body = pinJson("重试后的完整标题", "retry-author", ipInfo = null)
            model.load(environment, destination)
            val retried = assertNotNull(model.content.value)
            assertEquals("42", retried.id)
            assertEquals("重试后的完整标题", retried.excerptTitle)
            assertEquals("retry-author", retried.author.id)
            assertEquals("完整作者名称 retry-author", retried.author.name)
            assertNull(retried.ipInfo)
            assertNull(model.errorMessage.value)
            assertFalse(model.isLoading.value)
            assertEquals(listOf<Pair<NavDestination, Long?>>(destination to null, destination to null), environment.opens)
            assertEquals(retried.author.name, (LocalHistoryStorage().history.single() as Pin).authorName)
            assertEquals(4, requests.size)
            requests.forEach { url ->
                assertEquals(URLProtocol.HTTPS, url.protocol)
                assertEquals("api.zhihu.com", url.host)
                assertEquals("/v2/pins/42", url.encodedPath)
                assertTrue(url.parameters.isEmpty())
            }
        }
    }

    @Test
    fun clientSwitchDuringRealLoadCannotPublishLateContentAndNewClientCanRetry() = runTest {
        withIsolatedOwner {
            val destination = Pin(42, "路由作者")
            var oldBody = pinJson("已有完整标题", "cached-author")
            val oldRequests = mutableListOf<Url>()
            val oldClient = own(
                HttpClient(
                    MockEngine { request ->
                        oldRequests += request.url
                        respond(oldBody, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    },
                ) {
                    installZhihuCommonClientConfig(mutableMapOf(), "synthetic-test")
                },
            )
            val newRequests = mutableListOf<Url>()
            val newClient = own(
                HttpClient(
                    MockEngine { request ->
                        newRequests += request.url
                        respond(pinJson("最新客户端标题", "latest-author"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    },
                ) {
                    installZhihuCommonClientConfig(mutableMapOf(), "synthetic-test")
                },
            )
            val environment = RecordingEnvironment(oldClient)
            val model = PinScreenData().also(models::add)
            model.load(environment, destination)
            val cached = assertNotNull(model.content.value)

            val reachedOpen = CompletableDeferred<Unit>()
            val releaseOpen = CompletableDeferred<Unit>()
            environment.beforeOpen = {
                reachedOpen.complete(Unit)
                releaseOpen.await()
            }
            oldBody = pinJson("旧客户端晚到标题", "stale-author")
            val staleLoad = launch { model.load(environment, destination) }
            try {
                reachedOpen.await()
                assertTrue(model.isLoading.value)
                assertSame(cached, model.content.value)
                val requestsWhileLoading = oldRequests.size
                model.load(environment, destination)
                assertEquals(requestsWhileLoading, oldRequests.size)

                environment.client = newClient
                assertSame(newClient, environment.httpClient())
                releaseOpen.complete(Unit)
                staleLoad.join()
                assertTrue(staleLoad.isCancelled, "旧客户端加载应传播取消，不能作为成功回写")
                assertSame(cached, model.content.value)
                assertEquals(
                    "cached-author",
                    model.content.value
                        ?.author
                        ?.id,
                )
                assertNull(model.errorMessage.value)
                assertFalse(model.isLoading.value)

                environment.beforeOpen = {}
                model.load(environment, destination)
                val latest = assertNotNull(model.content.value)
                assertEquals("42", latest.id)
                assertEquals("最新客户端标题", latest.excerptTitle)
                assertEquals("latest-author", latest.author.id)
                assertEquals(2, oldRequests.size)
                assertEquals(1, newRequests.size)
                assertNull(model.errorMessage.value)
                assertFalse(model.isLoading.value)
                assertEquals(latest.author.name, (LocalHistoryStorage().history.single() as Pin).authorName)
            } finally {
                releaseOpen.complete(Unit)
                staleLoad.cancel()
                staleLoad.join()
            }
        }
    }

    @Test
    fun authorFollowUsesSignedProtocolRejectsDuplicatesAndPreservesRelationshipOnFailure() = runTest {
        withIsolatedOwner {
            val reachedFollow = CompletableDeferred<Unit>()
            val releaseFollow = CompletableDeferred<Unit>()
            val requests = mutableListOf<HttpRequestData>()
            var status = HttpStatusCode.OK
            val store = accountStore(
                MockEngine { request ->
                    if (request.url.encodedPath.endsWith("/followers")) {
                        requests += request
                        reachedFollow.complete(Unit)
                        releaseFollow.await()
                        respond("{}", status, headersOf(HttpHeaders.ContentType, "application/json"))
                    } else {
                        respond(pinJson("想法标题", "other-author"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                },
            )
            val environment = AccountEnvironment(store)
            val model = PinScreenData().also(models::add)
            model.load(environment, Pin(42))
            assertTrue(model.canFollowAuthor(environment))
            val following = launch { model.toggleAuthorFollow(environment) }
            try {
                reachedFollow.await()
                model.toggleAuthorFollow(environment)
                assertEquals(1, requests.size)
                assertTrue(model.authorFollowChanging.value)
                assertFalse(assertNotNull(model.content.value).author.isFollowing)
                releaseFollow.complete(Unit)
                following.join()
                assertTrue(assertNotNull(model.content.value).author.isFollowing)
                assertFalse(model.authorFollowChanging.value)

                status = HttpStatusCode.ServiceUnavailable
                model.toggleAuthorFollow(environment)
                assertTrue(assertNotNull(model.content.value).author.isFollowing)
                assertEquals("取消关注失败，请重试", model.authorFollowError.value)
                assertFalse(model.authorFollowChanging.value)

                status = HttpStatusCode.OK
                model.toggleAuthorFollow(environment)
                assertFalse(assertNotNull(model.content.value).author.isFollowing)
                assertNull(model.authorFollowError.value)
                assertEquals(listOf(HttpMethod.Post, HttpMethod.Delete, HttpMethod.Delete), requests.map { it.method })
                requests.forEach { request ->
                    assertEquals("https://www.zhihu.com/api/v4/members/other-author/followers", request.url.toString())
                    assertEquals("101_3_3.0", request.headers["x-zse-93"])
                    assertTrue(assertNotNull(request.headers["x-zse-96"]).isNotBlank())
                    assertTrue(assertNotNull(request.headers[HttpHeaders.Cookie]).contains("z_c0=synthetic-login"))
                }
            } finally {
                releaseFollow.complete(Unit)
                following.cancel()
                following.join()
            }
        }
    }

    @Test
    fun authorFollowCannotWriteBackAfterRefreshLeaveOrAccountSwitch() = runTest {
        withIsolatedOwner {
            var reachedFollow = CompletableDeferred<Unit>()
            var releaseFollow = CompletableDeferred<Unit>()
            val store = accountStore(
                MockEngine { request ->
                    if (request.url.encodedPath.endsWith("/followers")) {
                        reachedFollow.complete(Unit)
                        releaseFollow.await()
                        respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    } else {
                        respond(pinJson("想法标题", "other-author"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                },
            )
            val environment = AccountEnvironment(store)
            val model = PinScreenData().also(models::add)
            model.load(environment, Pin(42))
            for (invalidate in listOf("refresh", "leave", "account")) {
                reachedFollow = CompletableDeferred()
                releaseFollow = CompletableDeferred()
                val changing = launch { model.toggleAuthorFollow(environment) }
                try {
                    reachedFollow.await()
                    when (invalidate) {
                        "refresh" -> model.load(environment, Pin(42))
                        "leave" -> model.cancelAuthorFollow()
                        "account" -> {
                            store.replaceSession(store.session.copy(profile = ZhihuAccountProfileSnapshot(id = "account-b")))
                            assertFalse(model.canFollowAuthor(environment))
                        }
                    }
                    releaseFollow.complete(Unit)
                    changing.join()
                    assertFalse(assertNotNull(model.content.value).author.isFollowing, invalidate)
                    assertFalse(model.authorFollowChanging.value, invalidate)
                    assertNull(model.authorFollowError.value, invalidate)
                } finally {
                    releaseFollow.complete(Unit)
                    changing.cancel()
                    changing.join()
                }
            }
            model.load(environment, Pin(42))
            assertTrue(model.canFollowAuthor(environment))
            model.toggleAuthorFollow(environment)
            assertTrue(assertNotNull(model.content.value).author.isFollowing)
        }
    }

    @Test
    fun authorFollowIsUnavailableForSelfMissingIdentityOrGuest() = runTest {
        withIsolatedOwner {
            var followRequests = 0
            val store = accountStore(
                MockEngine { request ->
                    if (request.url.encodedPath.endsWith("/followers")) followRequests++
                    respond(pinJson("想法标题", "other-author"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            )
            val environment = AccountEnvironment(store)
            val model = PinScreenData().also(models::add)
            model.load(environment, Pin(42))
            val pin = assertNotNull(model.content.value)
            val unavailablePins = listOf(
                pin.copy(selfCreate = true),
                pin.copy(author = pin.author.copy(id = "account-a")),
                pin.copy(author = pin.author.copy(id = "")),
                pin.copy(author = pin.author.copy(urlToken = " ")),
            )
            for (unavailable in unavailablePins) {
                model.content.value = unavailable
                assertFalse(model.canFollowAuthor(environment))
                model.toggleAuthorFollow(environment)
            }
            model.content.value = pin
            store.replaceSession(store.session.copy(login = false))
            assertFalse(model.canFollowAuthor(environment))
            model.toggleAuthorFollow(environment)
            assertEquals(0, followRequests)
            assertFalse(assertNotNull(model.content.value).author.isFollowing)
        }
    }

    private suspend fun TestScope.withIsolatedOwner(block: suspend Fixture.() -> Unit) {
        val home = Files.createTempDirectory("pin-metadata-loader-")
        val previousHome = System.getProperty("user.home")
        val fixture = Fixture(home.toString())
        System.setProperty("user.home", home.toString())
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            fixture.block()
        } finally {
            fixture.models.forEach {
                it.cancelAuthorFollow()
                it.viewModelScope.cancel()
            }
            fixture.stores.forEach(ZhihuAccountStore::close)
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

    private class Fixture(
        private val home: String,
    ) {
        val clients = mutableListOf<HttpClient>()
        val stores = mutableListOf<ZhihuAccountStore>()
        val models = mutableListOf<PinScreenData>()

        fun own(client: HttpClient): HttpClient = client.also(clients::add)

        fun accountStore(engine: MockEngine): ZhihuAccountStore = ZhihuAccountStore(
            ZhihuAccountRepository(Path(home, "synthetic-account-${stores.size}.json")),
            engine,
        ).also { store ->
            stores += store
            store.save(
                ZhihuAccountSession(
                    login = true,
                    profile = ZhihuAccountProfileSnapshot(id = "account-a"),
                    cookies = mutableMapOf("z_c0" to "synthetic-login"),
                ),
            )
        }
    }

    private class AccountEnvironment(
        private val store: ZhihuAccountStore,
    ) : ContentLoadEnvironment {
        override val accountClient get() = store.client

        override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit

        override suspend fun recordContentOpenEvent(destination: NavDestination, questionId: Long?, openFrom: String) = Unit
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

    private fun pinJson(
        title: String,
        authorId: String,
        ipInfo: String? = "IP 属地合成属地",
    ): String =
        """
        {
          "id":"42","url":"https://www.zhihu.com/pin/42","excerpt_title":"$title",
          "content":[{"type":"text","content":"<p>完整想法正文</p>","title":"$title"}],
          "like_count":7,"virtuals":{"is_liked":true},${ipInfo?.let { "\"ip_info\":\"$it\"," }.orEmpty()}
          "author":{"avatar_url":"https://example.invalid/avatar.png","gender":0,"headline":"完整作者简介",
            "id":"$authorId","is_advertiser":false,"is_org":false,"name":"完整作者名称 $authorId",
            "type":"people","url":"https://www.zhihu.com/people/$authorId","url_token":"$authorId","user_type":"people"}
        }
        """.trimIndent()
}
