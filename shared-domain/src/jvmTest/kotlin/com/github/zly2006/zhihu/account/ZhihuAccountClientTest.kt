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

package com.github.zly2006.zhihu.account

import androidx.lifecycle.viewModelScope
import com.github.zly2006.zhihu.notification.NotificationSettingsStore
import com.github.zly2006.zhihu.notification.NotificationType
import com.github.zly2006.zhihu.util.ZHIHU_WEB_ZSE93
import com.github.zly2006.zhihu.util.ZhihuFetchSignature
import com.github.zly2006.zhihu.viewmodel.MobileNotificationCategory
import com.github.zly2006.zhihu.viewmodel.NotificationEnvironment
import com.github.zly2006.zhihu.viewmodel.NotificationTimelineViewModel
import com.github.zly2006.zhihu.viewmodel.NotificationViewModel
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.io.files.Path
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ZhihuAccountClientTest {
    private lateinit var directory: java.nio.file.Path
    private lateinit var file: java.nio.file.Path

    @BeforeTest
    fun createAccountDirectory() {
        directory = Files.createTempDirectory("account-client-contract-")
        file = directory.resolve("account.json")
    }

    @AfterTest
    fun deleteAccountDirectory() {
        check(directory.toFile().deleteRecursively())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun notificationReadAllCapturesBeforePostAndConfirmsOnlyTheCurrentSuccessfulOwner() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        var status = HttpStatusCode.ServiceUnavailable
        var release = CompletableDeferred<Unit>().apply { complete(Unit) }
        val calls = mutableListOf<String>()
        val confirmations = mutableListOf<Pair<ZhihuAccountClient, String>>()
        val engine = MockEngine.create {
            this.dispatcher = dispatcher
            addHandler { request ->
                if (request.method == HttpMethod.Post) {
                    val entry = request.url.encodedPath
                        .substringAfter("/entry/")
                        .substringBefore('/')
                    calls += "post:$entry"
                    release.await()
                    respond("", status)
                } else {
                    respond("""{"data":[{"id":"fixture-notification","is_read":false}],"paging":{"is_end":true,"next":""}}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
        }
        val store = ZhihuAccountStore(ZhihuAccountRepository(Path(file.toString())), engine)
        val settings = object : NotificationSettingsStore {
            override fun getSystemNotificationEnabled(type: NotificationType) = false

            override fun setSystemNotificationEnabled(type: NotificationType, enabled: Boolean) = Unit

            override fun getDisplayInAppEnabled(type: NotificationType) = true

            override fun setDisplayInAppEnabled(type: NotificationType, enabled: Boolean) = Unit

            override fun getAutoMarkAsReadEnabled() = true

            override fun setAutoMarkAsReadEnabled(enabled: Boolean) = Unit

            override fun getUnreadBadgeEnabled() = true

            override fun setUnreadBadgeEnabled(enabled: Boolean) = Unit
        }
        val environment = object : NotificationEnvironment {
            override val accountClient get() = store.client
            override val notificationSettingsStore = settings

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = throw error
        }
        val observer = store.observeNotificationReadAll { owner, entry ->
            calls += "begin:$entry"
            val confirm: () -> Unit = {
                calls += "confirm:$entry"
                confirmations += owner to entry
            }
            confirm
        }
        val overview = NotificationViewModel()
        val timeline = NotificationTimelineViewModel("like")
        try {
            store.replaceSession(ZhihuAccountSession(login = true, profile = ZhihuAccountProfileSnapshot(id = "fixture-a", name = "A")))
            val firstOwner = store.client
            overview.categoryUnreadCounts[MobileNotificationCategory.Comment] = 1
            assertFalse(overview.markAllAsRead(environment))
            assertEquals(listOf("begin:comment", "post:comment"), calls)
            assertTrue(confirmations.isEmpty())
            calls.clear()
            status = HttpStatusCode.NoContent
            assertTrue(overview.markAllAsRead(environment))
            assertEquals(listOf("begin:comment", "post:comment", "confirm:comment"), calls)
            assertEquals(listOf(firstOwner to "comment"), confirmations)
            calls.clear()
            status = HttpStatusCode.ServiceUnavailable
            timeline.refresh(environment)
            advanceUntilIdle()
            assertTrue(timeline.allData.single().isUnread)
            assertEquals(listOf("begin:like", "post:like"), calls)
            assertEquals(1, confirmations.size)
            calls.clear()
            status = HttpStatusCode.NoContent
            timeline.refresh(environment)
            advanceUntilIdle()
            assertFalse(timeline.allData.single().isUnread)
            assertEquals(listOf("begin:like", "post:like", "confirm:like"), calls)
            assertEquals(listOf(firstOwner to "comment", firstOwner to "like"), confirmations)

            release = CompletableDeferred()
            overview.categoryUnreadCounts[MobileNotificationCategory.Comment] = 1
            val pending = async { overview.markAllAsRead(environment) }
            runCurrent()
            assertFalse(pending.isCompleted)
            val staleConfirmation = firstOwner.beginNotificationReadAll("like")
            store.replaceSession(ZhihuAccountSession(login = true, profile = ZhihuAccountProfileSnapshot(id = "fixture-b", name = "B")))
            release.complete(Unit)
            pending.cancelAndJoin()
            staleConfirmation()
            assertEquals(2, confirmations.size, "换号期间旧 POST 不能撤销新账号通知")
            val confirm = store.client.beginNotificationReadAll("like")
            confirm()
            confirm()
            assertEquals(3, confirmations.size, "成功回调只执行一次")
            val disconnected = store.client.beginNotificationReadAll("like")
            observer.close()
            disconnected()
            assertEquals(3, confirmations.size, "关闭的 runtime 不应收到迟到回调")
        } finally {
            release.complete(Unit)
            observer.close()
            overview.viewModelScope.cancel()
            timeline.viewModelScope.cancel()
            store.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun legacySingleAndSavedAccountFormatsKeepCredentialsAcrossUpgrade() {
        val session = """{"login":true,"username":"Legacy","cookies":{"z_c0":"fixture-old-cookie","d_c0":"fixture-device"},"self":{"id":"fixture-legacy","name":"Legacy","url_token":"legacy"},"mobileAccessToken":"fixture-old-access","mobileRefreshToken":"fixture-old-refresh","mobileTokenType":"bearer","mobileTokenExpiresAt":2000000000}"""
        val inputs = listOf(session, """{"activeAccountId":"fixture-legacy","accounts":[{"id":"fixture-legacy","session":$session}]}""")
        for (raw in inputs) {
            Files.writeString(file, raw)
            MockEngine { error("读取本地旧版会话不应发起请求") }.use { engine ->
                val repository = ZhihuAccountRepository(Path(file.toString()))
                val store = ZhihuAccountStore(repository, engine)
                try {
                    val owner = store.client
                    assertTrue(store.session.login)
                    assertEquals("fixture-legacy", store.session.profile?.id)
                    assertEquals("fixture-old-refresh", store.session.mobileRefreshToken)
                    assertEquals("fixture-old-access", store.session.mobileAccessToken)
                    assertEquals("fixture-old-cookie", store.session.cookies["z_c0"])
                    assertEquals(raw, Files.readString(file), "升级读取不应覆盖原账号文件")
                    store.save(store.session.copy(username = "Updated"))
                    assertSame(owner, store.client)
                    assertEquals(store.session, repository.loadAccounts().session)
                    assertEquals("fixture-old-refresh", repository.loadAccounts().session.mobileRefreshToken)
                } finally {
                    store.close()
                }
            }
        }
    }

    @Test
    fun expiredGetRenewsOnceAndPersistsAllReplacementCredentials() = runTest {
        val requests = mutableListOf<String>()
        val session = ZhihuAccountSession(
            login = true,
            username = "Captured User",
            profile = ZhihuAccountProfileSnapshot(id = "captured-person", name = "Captured User"),
            cookies = mutableMapOf("z_c0" to "expired-cookie", "d_c0" to "captured-device-cookie"),
            mobileAccessToken = "expired-access-token",
            mobileRefreshToken = "saved-refresh-token",
        )
        val repository = ZhihuAccountRepository(Path(file.toString())).apply {
            saveAccounts(ZhihuAccounts("captured-person", listOf(ZhihuSavedAccount("captured-person", session))))
        }
        MockEngine { request ->
            requests += "${request.method.value} ${request.url.host}${request.url.encodedPath}"
            when (request.url.encodedPath) {
                "/api/account/prod/sign_in" -> respond(
                    """{"uid":"captured-person","token_type":"bearer","access_token":"captured-access-token","refresh_token":"captured-refresh-token","expires_in":2591999,"cookie":{"q_c0":"captured-q-c0","z_c0":"captured-z-c0"},"verification":null}""",
                    headers = headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.SetCookie to listOf("_xsrf=captured-xsrf; Path=/; Domain=zhihu.com")),
                )
                "/api/v4/me" -> if (request.headers[HttpHeaders.Cookie].orEmpty().contains("z_c0=captured-z-c0")) {
                    respond("""{"id":"captured-person","name":"Captured User"}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                } else {
                    respond("""{"error":{"code":100,"message":"ERR_LOGIN_TICKET_EXPIRED"}}""", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.ContentType, "application/json"))
                }
                else -> error("不应发起请求 ${request.url.encodedPath}")
            }
        }.use { engine ->
            val store = ZhihuAccountStore(repository, engine)
            try {
                val owner = store.client
                assertEquals(HttpStatusCode.OK, owner.get("https://www.zhihu.com/api/v4/me").status)
                assertEquals(listOf("GET www.zhihu.com/api/v4/me", "POST api.zhihu.com/api/account/prod/sign_in", "GET www.zhihu.com/api/v4/me"), requests)
                val restored = repository.loadAccounts().session
                assertEquals("captured-access-token", restored.mobileAccessToken)
                assertEquals("captured-refresh-token", restored.mobileRefreshToken)
                assertEquals("captured-device-cookie", restored.cookies["d_c0"])
                assertEquals("captured-z-c0", restored.cookies["z_c0"])
                assertEquals("captured-q-c0", restored.cookies["q_c0"])
                assertEquals("captured-xsrf", restored.cookies["_xsrf"])
                assertSame(owner, store.client)
                assertEquals(SessionExpiryState(), owner.expiryPrompt.value, "续期已成功，中间 401 不能要求退出账号")
                assertEquals(store.accountsState.value, repository.loadAccounts())
            } finally {
                store.close()
            }
        }
    }

    @Test
    fun rejectedRefreshCookiesCannotOverwriteSavedCredentials() = runTest {
        val original = ZhihuAccountSession(
            login = true,
            username = "Captured User",
            profile = ZhihuAccountProfileSnapshot(id = "captured-person", name = "Captured User"),
            cookies = mutableMapOf("z_c0" to "expired-cookie", "d_c0" to "captured-device-cookie"),
            mobileAccessToken = "expired-access-token",
            mobileRefreshToken = "rejected-refresh-token",
        )
        val accounts = ZhihuAccounts("captured-person", listOf(ZhihuSavedAccount("captured-person", original)))
        val repository = ZhihuAccountRepository(Path(file.toString())).apply { saveAccounts(accounts) }
        MockEngine {
            respond(
                """{"error":{"code":100008,"name":"ERR_INVALID_REFRESH_TOKEN","message":"refresh_token 已失效，请重新登录（rejected-refresh-token）"}}""",
                HttpStatusCode.BadRequest,
                headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.SetCookie to listOf("z_c0=must-not-persist; Path=/; Domain=zhihu.com")),
            )
        }.use { engine ->
            val store = ZhihuAccountStore(repository, engine)
            try {
                val failure = assertFailsWith<InvalidRefreshTokenException> { store.client.refreshCredentials(force = true) }
                assertEquals("refresh_token 已失效，请重新登录（[已隐藏]）", failure.serverMessage)
                assertFalse(failure.toString().contains("rejected-refresh-token"))
                assertTrue(failure.message.orEmpty().contains("请重新登录"))
                assertEquals(SessionExpiryState(SessionExpiryPrompt.SHOWING, failure.serverMessage), store.client.expiryPrompt.value)
                assertEquals(accounts, repository.loadAccounts())
                assertEquals(original, store.session)
            } finally {
                store.close()
            }
        }
    }

    @Test
    fun temporaryRefreshFailureKeepsAccountAndDoesNotLeakTheResponse() = runTest {
        val original = ZhihuAccountSession(
            login = true,
            profile = ZhihuAccountProfileSnapshot(id = "fixture-a", name = "A"),
            cookies = mutableMapOf("z_c0" to "fixture-cookie"),
            mobileRefreshToken = "fixture-refresh-secret",
        )
        val repository = ZhihuAccountRepository(Path(file.toString())).apply {
            saveAccounts(ZhihuAccounts("fixture-a", listOf(ZhihuSavedAccount("fixture-a", original))))
        }
        MockEngine { throw IOException("network failed; token=fixture-refresh-secret") }.use { engine ->
            val store = ZhihuAccountStore(repository, engine)
            try {
                val owner = store.client
                val failure = assertFailsWith<IllegalStateException> { owner.refreshCredentials(force = true) }
                assertFalse(failure is InvalidRefreshTokenException)
                assertFalse(failure.toString().contains("fixture-refresh-secret"))
                assertNull(failure.cause)
                assertEquals(SessionExpiryState(), owner.expiryPrompt.value)
                assertEquals(original, repository.loadAccounts().session)
                assertSame(owner, store.client)
            } finally {
                store.close()
            }
        }
    }

    @Test
    fun rejectedRefreshFromOldOwnerCannotExpireNewAccount() = runTest {
        val original = ZhihuAccountSession(
            login = true,
            profile = ZhihuAccountProfileSnapshot(id = "fixture-a", name = "A"),
            cookies = mutableMapOf("z_c0" to "fixture-a-cookie"),
            mobileRefreshToken = "fixture-a-refresh",
        )
        val repository = ZhihuAccountRepository(Path(file.toString())).apply {
            saveAccounts(ZhihuAccounts("fixture-a", listOf(ZhihuSavedAccount("fixture-a", original))))
        }
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        MockEngine {
            started.complete(Unit)
            release.await()
            respond("""{"error":{"code":100008,"name":"ERR_INVALID_REFRESH_TOKEN","message":"refresh_token 已失效"}}""", HttpStatusCode.BadRequest, headersOf(HttpHeaders.ContentType, "application/json"))
        }.use { engine ->
            val store = ZhihuAccountStore(repository, engine)
            try {
                val previous = store.client
                val pending = async { previous.refreshCredentials(force = true) }
                started.await()
                val next = original.copy(profile = original.profile!!.copy(id = "fixture-b", name = "B"), mobileRefreshToken = "fixture-b-refresh")
                store.replaceSession(next)
                val current = store.client
                release.complete(Unit)
                assertFalse(pending.await())
                assertEquals(SessionExpiryState(), current.expiryPrompt.value)
                assertEquals(next, store.session)
                assertSame(current, store.client)
                assertFalse(previous.clearExpiredSession())
                assertEquals(next, repository.loadAccounts().session)
            } finally {
                release.complete(Unit)
                store.close()
            }
        }
    }

    @Test
    fun expiredWritesNeverRetryAndUnsuccessfulGetRetriesOnlyOnce() = runTest {
        val requests = mutableListOf<String>()
        val session = ZhihuAccountSession(
            login = true,
            username = "Captured User",
            profile = ZhihuAccountProfileSnapshot(id = "captured-person", name = "Captured User"),
            cookies = mutableMapOf("z_c0" to "expired-cookie"),
            mobileRefreshToken = "saved-refresh-token",
        )
        val repository = ZhihuAccountRepository(Path(file.toString())).apply {
            saveAccounts(ZhihuAccounts("captured-person", listOf(ZhihuSavedAccount("captured-person", session))))
        }
        MockEngine { request ->
            requests += "${request.method.value} ${request.url.encodedPath}"
            if (request.url.encodedPath == "/api/account/prod/sign_in") {
                respond("""{"uid":"captured-person","access_token":"new-access","refresh_token":"new-refresh","token_type":"bearer","expires_in":2591999,"verification":null,"cookie":{"z_c0":"new-cookie"}}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond("""{"error":{"code":100,"message":"ERR_TICKET_NOT_EXIST"}}""", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }.use { engine ->
            val store = ZhihuAccountStore(repository, engine)
            try {
                val owner = store.client
                for (method in listOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch, HttpMethod.Delete)) {
                    assertEquals(HttpStatusCode.Unauthorized, owner.request("https://www.zhihu.com/write", method).status)
                }
                assertEquals(HttpStatusCode.Unauthorized, owner.get("https://www.zhihu.com/overridden-write") { method = HttpMethod.Post }.status)
                assertEquals(listOf("POST /write", "PUT /write", "PATCH /write", "DELETE /write", "POST /overridden-write"), requests)
                assertFailsWith<com.github.zly2006.zhihu.util.HttpStatusException> { owner.get("https://www.zhihu.com/still-expired") }
                assertEquals(listOf("GET /still-expired", "POST /api/account/prod/sign_in", "GET /still-expired"), requests.takeLast(3))
            } finally {
                store.close()
            }
        }
    }

    @Test
    fun sameIdentitySessionRefreshKeepsTheBoundClient() = runTest {
        emptyMockEngine().use { engine ->
            val session = ZhihuAccountSession(
                login = true,
                username = "alice",
                cookies = mutableMapOf("d_c0" to "old-dc0"),
            )
            val repository = ZhihuAccountRepository(Path(file.toString())).apply {
                saveAccounts(ZhihuAccounts("alice", listOf(ZhihuSavedAccount("alice", session))))
            }
            val accountData = ZhihuAccountStore(repository, engine)
            try {
                val firstClient = accountData.client
                val nextSession = ZhihuAccountSession(
                    login = true,
                    username = "alice",
                    cookies = mutableMapOf("d_c0" to "new-dc0"),
                )

                accountData.save(nextSession)

                assertEquals(nextSession.username, accountData.session.username)
                assertEquals(nextSession.username, accountData.client.load().username)
                assertSame(firstClient, accountData.client)
                assertEquals(accountData.accountsState.value, repository.loadAccounts())
            } finally {
                accountData.close()
            }
        }
    }

    @Test
    fun cookieChangesPersistTheBoundSessionWithoutCreatingAnotherState() = runTest {
        val repository = ZhihuAccountRepository(Path(file.toString()))
        val session = ZhihuAccountSession(
            login = true,
            username = "alice",
            userAgent = "bound-agent",
            cookies = mutableMapOf("z_c0" to "old-token", "d_c0" to "alice-dc0"),
        )
        repository.saveAccounts(ZhihuAccounts("alice", listOf(ZhihuSavedAccount("alice", session))))
        MockEngine { request ->
            val expectedAgent = if (request.url.encodedPath in setOf("/mobile", "/guest-mobile", "/signed", "/post")) com.github.zly2006.zhihu.data.ZHIHU_ANDROID_USER_AGENT else "bound-agent"
            assertEquals(expectedAgent, request.headers[HttpHeaders.UserAgent])
            when (request.url.encodedPath) {
                "/guest", "/guest-mobile" -> assertNull(request.headers[HttpHeaders.Cookie])
                "/mobile" -> {
                    assertTrue(request.headers[HttpHeaders.Cookie].orEmpty().contains("z_c0=new-token"))
                    val signature = request.headers["x-zse-96"].orEmpty()
                    assertTrue(
                        (0..126).any { seed ->
                            signature == ZhihuFetchSignature.createZse96Header(
                                ZHIHU_WEB_ZSE93,
                                request.url.toString(),
                                "alice-dc0",
                                seed = seed,
                            )
                        },
                    )
                }
                "/signed" -> {
                    assertEquals("caller-zse93", request.headers["x-zse-93"])
                    assertEquals("caller-signature", request.headers["x-zse-96"])
                }
                else -> assertNull(request.headers["x-zse-96"])
            }
            val token = if (request.url.encodedPath in setOf("/guest", "/guest-mobile")) "guest-token" else "new-token"
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.SetCookie, "z_c0=$token; Path=/"),
            )
        }.use { engine ->
            val accountData = ZhihuAccountStore(repository, engine)
            try {
                val initialSession = accountData.session
                val owner = accountData.client
                val web = owner.httpClient()
                val guest = owner.httpClient(useCookies = false)
                val mobile = owner.mobileHomeFeedHttpClient()
                val guestMobile = owner.mobileHomeFeedHttpClient(useCookies = false)
                web.get("https://www.zhihu.com/")
                assertNotSame(initialSession, accountData.session)
                assertSame(owner, accountData.client)
                assertEquals("new-token", accountData.session.cookies["z_c0"])
                assertEquals("new-token", repository.loadAccounts().session.cookies["z_c0"])
                val persistedBytes = Files.readAllBytes(file)
                val persistedState = accountData.accountsState.value
                Files.setLastModifiedTime(
                    file,
                    java.nio.file.attribute.FileTime
                        .fromMillis(1_000_000),
                )
                val persistedAt = Files.getLastModifiedTime(file)
                web.get("https://www.zhihu.com/")
                assertEquals(persistedAt, Files.getLastModifiedTime(file), "相同 Cookie 不应重写账号文件")
                repeat(2) {
                    guest.get("https://www.zhihu.com/guest")
                    guestMobile.get("https://www.zhihu.com/guest-mobile")
                }
                assertContentEquals(persistedBytes, Files.readAllBytes(file))
                assertSame(persistedState, accountData.accountsState.value)
                mobile.get("https://api.zhihu.com/mobile")
                mobile.get("https://api.zhihu.com/signed") {
                    headers["x-zse-93"] = "caller-zse93"
                    headers["x-zse-96"] = "caller-signature"
                }
                mobile.post("https://api.zhihu.com/post")
                assertSame(guest, owner.httpClient(useCookies = false))
                assertSame(mobile, owner.mobileHomeFeedHttpClient())
                assertSame(guestMobile, owner.mobileHomeFeedHttpClient(useCookies = false))
                guest.close()
                assertEquals(HttpStatusCode.OK, mobile.get("https://api.zhihu.com/mobile").status)
                accountData.close()
                assertTrue(web.coroutineContext.job.isCompleted)
                assertTrue(guest.coroutineContext.job.isCompleted)
                assertTrue(mobile.coroutineContext.job.isCompleted)
                assertTrue(guestMobile.coroutineContext.job.isCompleted)
            } finally {
                accountData.close()
            }
        }
    }

    @Test
    fun profileRefreshKeepsClientAndPreservesMobileTokens() = runTest {
        val repository = ZhihuAccountRepository(Path(file.toString()))
        val session = ZhihuAccountSession(
            login = true,
            cookies = mutableMapOf("z_c0" to "token"),
            mobileAccessToken = "access",
            mobileRefreshToken = "refresh",
            mobileTokenType = "bearer",
            mobileTokenExpiresAt = 1234,
        )
        repository.saveAccounts(ZhihuAccounts("account", listOf(ZhihuSavedAccount("account", session))))
        MockEngine {
            respond(
                content = """{"id":"1","name":"alice","url_token":"alice","user_type":"people"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }.use { engine ->
            val accountData = ZhihuAccountStore(repository, engine)
            try {
                val oldClient = accountData.client

                val refreshed = accountData.client.refreshAndSaveProfile()

                assertEquals("access", refreshed?.mobileAccessToken)
                assertEquals("refresh", accountData.session.mobileRefreshToken)
                assertSame(oldClient, accountData.client)
                assertEquals(accountData.accountsState.value, repository.loadAccounts())
            } finally {
                accountData.close()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun mobileLoginVerifiesProfileAndAtomicallyReplacesSessionAndClient() = runTest {
        val repository = ZhihuAccountRepository(Path(file.toString()))
        MockEngine { request ->
            assertEquals("/api/v4/me", request.url.encodedPath)
            respond(
                content = """{"id":"1","name":"alice","url_token":"alice","user_type":"people"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }.use { engine ->
            val accountData = ZhihuAccountStore(repository, engine)
            val states = mutableListOf<ZhihuAccounts>()
            val observation = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                accountData.accountsState.collect {
                    assertEquals(it, repository.loadAccounts())
                    states += it
                }
            }
            try {
                val oldClient = accountData.client

                val verified = accountData.login(
                    ZhihuMobileLoginToken(
                        accessToken = "mobile-access",
                        refreshToken = "mobile-refresh",
                        tokenType = "bearer",
                        expiresAt = 1_700_003_600L,
                        cookies = mapOf(
                            "q_c0" to "q-cookie",
                            "z_c0" to "z-cookie",
                            "d_c0" to "device-cookie",
                        ),
                    ),
                )

                assertEquals(true, verified)
                assertEquals(listOf(ZhihuAccounts(), accountData.accountsState.value), states)
                assertEquals("alice", accountData.session.username)
                assertEquals("z-cookie", accountData.session.cookies["z_c0"])
                assertEquals("device-cookie", accountData.session.cookies["d_c0"])
                assertEquals("mobile-access", accountData.session.mobileAccessToken)
                assertEquals("mobile-refresh", accountData.session.mobileRefreshToken)
                assertNotSame(oldClient, accountData.client)
                assertFailsWith<IllegalStateException> { oldClient.httpClient() }
                assertSame(accountData.session, accountData.client.load())
            } finally {
                observation.cancel()
                accountData.close()
            }
        }
    }

    @Test
    fun logoutDestroysTheBoundClientAndPublishesOneGuestSession() = runTest {
        MockEngine {
            respond(
                content = """{"id":"bob","name":"Bob","url_token":"bob","user_type":"people"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }.use { engine ->
            val session = ZhihuAccountSession(
                login = true,
                username = "alice",
                cookies = mutableMapOf("z_c0" to "token"),
            )
            val repository = ZhihuAccountRepository(Path(file.toString())).apply {
                saveAccounts(ZhihuAccounts("alice", listOf(ZhihuSavedAccount("alice", session))))
            }
            val accountData = ZhihuAccountStore(repository, engine)
            try {
                val signedInClient = accountData.client

                accountData.clear()

                assertEquals(false, accountData.session.login)
                assertSame(accountData.session, accountData.client.load())
                assertNotSame(signedInClient, accountData.client)
                assertFailsWith<IllegalStateException> { signedInClient.httpClient() }
                assertFalse(Files.exists(file))
                assertEquals(ZhihuAccounts(), repository.loadAccounts())
            } finally {
                accountData.close()
            }
        }
    }

    @Test
    fun savedCommentSenderAndPendingAliasKeepTheViewerAndDoNotReplayPosts() = runTest {
        val viewer = ZhihuAccountProfileSnapshot("viewer", "Viewer")
        val other = ZhihuAccountProfileSnapshot("other", "Other")
        val alias = ZhihuAccountProfileSnapshot("alias", "Alias")

        fun saved(profile: ZhihuAccountProfileSnapshot) = ZhihuSavedAccount(
            profile.id,
            ZhihuAccountSession(
                login = true,
                username = profile.name,
                profile = profile,
                cookies = mutableMapOf("z_c0" to profile.id),
                mobileAccessToken = profile.id,
            ),
        )
        val repository = ZhihuAccountRepository(Path(file.toString()))
        repository.saveAccounts(ZhihuAccounts(viewer.id, listOf(saved(viewer), saved(other))))
        val posts = mutableListOf<String>()
        val switches = mutableListOf<String>()
        var offline = true

        fun engine() = MockEngine { request ->
            val cookie = request.headers[HttpHeaders.Cookie]
                .orEmpty()
                .substringAfter("z_c0=")
                .substringBefore(';')
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            when (request.url.encodedPath) {
                "/people/account/list" -> {
                    assertEquals("viewer", cookie)
                    respond("""{"data":[{"id":"alias","name":"Alias"}]}""", headers = json)
                }
                "/account/switch" -> {
                    switches += cookie
                    if (cookie == "rotated-alias" && offline) {
                        respond("{}", status = HttpStatusCode.ServiceUnavailable, headers = json)
                    } else {
                        val target = if (cookie == "viewer") "alias" else "viewer"
                        val next = if (target == "viewer") "renewed-viewer" else target
                        respond("""{"uid":"$target","access_token":"$next","refresh_token":"refresh-$next","cookie":{"z_c0":"$next"}}""", headers = json)
                    }
                }
                "/comment" -> {
                    posts += cookie
                    val headers = if (cookie == "alias") {
                        headersOf(HttpHeaders.SetCookie, "z_c0=rotated-alias; Path=/")
                    } else {
                        json
                    }
                    respond("{}", headers = headers)
                }
                "/before-switch" -> {
                    assertEquals("viewer", cookie)
                    respond("{}", headers = json)
                }
                "/ordinary" -> {
                    assertEquals("renewed-viewer", cookie)
                    respond("{}", headers = json)
                }
                else -> error("意外请求：${request.url.encodedPath}")
            }
        }
        ZhihuAccountStore(repository, engine()).use { store ->
            val owner = store.client
            val target = store.commentIdentities.list(owner, viewer.id).single()
            assertEquals(CommentSender(viewer.id, alias), target)
            assertTrue(switches.isEmpty())
            store.commentIdentities.sendAs(owner, CommentSender(other.id, other)) { client, _ -> client.post("https://www.zhihu.com/comment") }
            assertTrue(switches.isEmpty())
            val reading = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val reader = async {
                owner.withAuthenticatedClient { client, _ ->
                    reading.complete(Unit)
                    release.await()
                    client.get("https://www.zhihu.com/before-switch")
                }
            }
            reading.await()
            val writer = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                assertFailsWith<ZhihuIdentityApiException> {
                    store.commentIdentities.sendAs(owner, target) { client, _ ->
                        assertEquals(viewer, owner.load().profile)
                        assertEquals("viewer", owner.load().cookies["z_c0"])
                        assertEquals(
                            alias,
                            repository
                                .loadAccounts()
                                .accounts
                                .first()
                                .pendingCommentIdentity
                                ?.profile,
                        )
                        client.post("https://www.zhihu.com/comment")
                    }
                }
            }
            assertTrue(switches.isEmpty())
            release.complete(Unit)
            reader.await()
            writer.await()
            assertSame(owner, store.client)
            assertEquals(listOf("other", "alias"), posts)
            assertEquals(listOf("viewer", "rotated-alias"), switches)
        }
        offline = false
        ZhihuAccountStore(repository, engine()).use { store ->
            store.client.httpClient().get("https://www.zhihu.com/ordinary")
            assertEquals(viewer.id, store.accountsState.value.activeAccountId)
            assertEquals("refresh-renewed-viewer", store.session.mobileRefreshToken)
            assertNull(
                repository
                    .loadAccounts()
                    .accounts
                    .first()
                    .pendingCommentIdentity,
            )
            assertEquals(listOf("viewer", "rotated-alias", "rotated-alias"), switches)
            assertEquals(listOf("other", "alias"), posts)
        }
    }

    @Test
    fun switchingSavedAccountsReplacesClientAndPreservesBothSessions() = runTest {
        val alice = ZhihuAccountSession(login = true, username = "alice")
        val bob = ZhihuAccountSession(login = true, username = "bob")
        val repository = ZhihuAccountRepository(Path(file.toString())).apply {
            saveAccounts(
                ZhihuAccounts(
                    activeAccountId = "alice",
                    accounts = listOf(ZhihuSavedAccount("alice", alice), ZhihuSavedAccount("bob", bob)),
                ),
            )
        }
        MockEngine {
            respond(
                content = """{"id":"bob","name":"Bob","url_token":"bob","user_type":"people"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }.use { engine ->
            val accountStore = ZhihuAccountStore(repository, engine)
            try {
                val aliceClient = accountStore.client

                assertTrue(accountStore.switchAccount("bob"))

                assertEquals("bob", accountStore.session.profile?.id)
                assertEquals(alice, accountStore.accounts.first().session)
                assertEquals(
                    "bob",
                    accountStore.accounts
                        .last()
                        .session.profile
                        ?.id,
                )
                assertNotSame(aliceClient, accountStore.client)
                assertFailsWith<IllegalStateException> { aliceClient.httpClient() }
                assertSame(accountStore.session, accountStore.client.load())
                assertEquals(accountStore.accountsState.value, repository.loadAccounts())
            } finally {
                accountStore.close()
            }
        }
    }

    @Test
    fun expiredSavedAccountDoesNotReplaceTheCurrentSessionOrClient() = runTest {
        val alice = ZhihuAccountSession(login = true, username = "alice")
        val bob = ZhihuAccountSession(login = true, username = "bob")
        val repository = ZhihuAccountRepository(Path(file.toString())).apply {
            saveAccounts(
                ZhihuAccounts(
                    activeAccountId = "alice",
                    accounts = listOf(ZhihuSavedAccount("alice", alice), ZhihuSavedAccount("bob", bob)),
                ),
            )
        }
        MockEngine {
            respond(
                content = "unauthorized",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "text/plain"),
            )
        }.use { engine ->
            val accountStore = ZhihuAccountStore(repository, engine)
            try {
                val client = accountStore.client
                val persistedBytes = Files.readAllBytes(file)

                assertEquals(false, accountStore.switchAccount("bob"))
                assertEquals(alice, accountStore.session)
                assertSame(client, accountStore.client)
                assertContentEquals(persistedBytes, Files.readAllBytes(file))
            } finally {
                accountStore.close()
            }
        }
    }

    @Test
    fun logoutCurrentSavedAccountSelectsTheNextOne() = runTest {
        val alice = ZhihuAccountSession(login = true, username = "alice")
        val bob = ZhihuAccountSession(login = true, username = "bob")
        val repository = ZhihuAccountRepository(Path(file.toString())).apply {
            saveAccounts(
                ZhihuAccounts(
                    activeAccountId = "alice",
                    accounts = listOf(ZhihuSavedAccount("alice", alice), ZhihuSavedAccount("bob", bob)),
                ),
            )
        }
        emptyMockEngine().use { engine ->
            val accountStore = ZhihuAccountStore(repository, engine)
            try {
                val initialState = accountStore.accountsState.value
                val initialClient = accountStore.client
                val persistedBytes = Files.readAllBytes(file)
                val temporary = Files.createDirectory(directory.resolve("account.json.tmp"))
                val blocker = Files.writeString(temporary.resolve("blocker"), "keep")

                assertFailsWith<IOException> { accountStore.clear() }
                assertSame(initialState, accountStore.accountsState.value)
                assertNotSame(initialClient, accountStore.client)
                assertFailsWith<IllegalStateException> { initialClient.httpClient() }
                assertContentEquals(persistedBytes, Files.readAllBytes(file))
                assertEquals(
                    HttpStatusCode.OK,
                    accountStore.client
                        .httpClient()
                        .get("https://www.zhihu.com/")
                        .status,
                )
                Files.delete(blocker)
                Files.delete(temporary)

                accountStore.clear()

                assertEquals(bob, accountStore.session)
                assertEquals(listOf("bob"), accountStore.accounts.map { it.id })
                assertEquals(accountStore.accountsState.value, repository.loadAccounts())
                assertFalse(Files.exists(temporary))
            } finally {
                accountStore.close()
            }
        }
    }

    @Test
    fun loginAddsAndSelectsAnIndependentAccountWithoutOverwritingCurrent() = runTest {
        val alice = ZhihuAccountSession(
            login = true,
            username = "alice",
            profile = ZhihuAccountProfileSnapshot(id = "alice", name = "Alice"),
        )
        val repository = ZhihuAccountRepository(Path(file.toString())).apply {
            saveAccounts(ZhihuAccounts("alice", listOf(ZhihuSavedAccount("alice", alice))))
        }
        MockEngine {
            respond(
                content = """{"id":"bob","name":"Bob","url_token":"bob","user_type":"people"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }.use { engine ->
            val accountStore = ZhihuAccountStore(repository, engine)
            try {
                val loggedIn = accountStore.login(mutableMapOf("z_c0" to "bob-token"))

                assertEquals(true, loggedIn)
                assertEquals("bob", accountStore.session.profile?.id)
                assertEquals(listOf("alice", "bob"), accountStore.accounts.map { it.id })
                assertEquals(
                    "alice",
                    accountStore.accounts
                        .first()
                        .session.profile
                        ?.id,
                )
                assertEquals(accountStore.accountsState.value, repository.loadAccounts())
            } finally {
                accountStore.close()
            }
        }
    }

    @Test
    fun profileRefreshFinishingAfterLogoutCannotRestoreTheOldAccount() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val mobileStarted = CompletableDeferred<Unit>()
        val finishResponse = CompletableDeferred<Unit>()
        val session = ZhihuAccountSession(
            login = true,
            username = "alice",
            cookies = mutableMapOf("z_c0" to "alice-token", "d_c0" to "alice-dc0"),
        )
        val repository = ZhihuAccountRepository(Path(file.toString())).apply {
            saveAccounts(ZhihuAccounts("alice", listOf(ZhihuSavedAccount("alice", session))))
        }
        MockEngine { request ->
            if (request.url.encodedPath == "/mobile") mobileStarted.complete(Unit) else requestStarted.complete(Unit)
            finishResponse.await()
            respond(
                content = """{"id":"alice","name":"Alice","url_token":"alice","user_type":"people"}""",
                status = HttpStatusCode.OK,
                headers = Headers.build {
                    append(HttpHeaders.ContentType, "application/json")
                    append(HttpHeaders.SetCookie, "z_c0=late-alice-token; Path=/")
                },
            )
        }.use { engine ->
            val accountStore = ZhihuAccountStore(repository, engine)
            try {
                val oldClient = accountStore.client
                val oldMobile = oldClient.mobileHomeFeedHttpClient()
                val refresh = async { oldClient.refreshAndSaveProfile() }
                val mobile = async { oldMobile.get("https://api.zhihu.com/mobile") }
                requestStarted.await()
                mobileStarted.await()
                accountStore.clear()
                val guestClient = accountStore.client
                assertEquals(false, accountStore.session.login)
                assertTrue(accountStore.accounts.isEmpty())
                accountStore.save(
                    ZhihuAccountSession(
                        login = true,
                        username = "bob",
                        cookies = mutableMapOf("z_c0" to "bob-token", "d_c0" to "bob-dc0"),
                    ),
                )
                val bobClient = accountStore.client
                val persistedBytes = Files.readAllBytes(file)
                finishResponse.complete(Unit)
                assertNull(refresh.await())
                mobile.await()

                assertEquals("bob", accountStore.session.username)
                assertEquals("bob-token", accountStore.session.cookies["z_c0"])
                assertEquals("bob-token", repository.loadAccounts().session.cookies["z_c0"])
                assertContentEquals(persistedBytes, Files.readAllBytes(file))
                assertSame(bobClient, accountStore.client)
                assertNotSame(guestClient, bobClient)
                assertTrue(oldMobile.coroutineContext.job.isCompleted)
            } finally {
                finishResponse.complete(Unit)
                accountStore.close()
            }
        }
    }

    @Test
    fun failedAccountDeletionKeepsTheCurrentSessionUsable() = runTest {
        val session = ZhihuAccountSession(login = true, username = "alice")
        val repository = ZhihuAccountRepository(Path(file.toString())).apply {
            saveAccounts(ZhihuAccounts("alice", listOf(ZhihuSavedAccount("alice", session))))
        }
        emptyMockEngine().use { engine ->
            val accountStore = ZhihuAccountStore(repository, engine)
            try {
                val initialState = accountStore.accountsState.value
                val oldClient = accountStore.client
                val persistedBytes = Files.readAllBytes(file)
                Files.delete(file)
                Files.createDirectory(file)
                val preserved = Files.write(file.resolve("preserved.json"), persistedBytes)

                assertFailsWith<IOException> { accountStore.clear() }
                assertSame(initialState, accountStore.accountsState.value)
                assertEquals("alice", accountStore.session.username)
                assertNotSame(oldClient, accountStore.client)
                assertFailsWith<IllegalStateException> { oldClient.httpClient() }
                assertContentEquals(persistedBytes, Files.readAllBytes(preserved))
                assertEquals(
                    HttpStatusCode.OK,
                    accountStore.client
                        .httpClient()
                        .get("https://www.zhihu.com/")
                        .status,
                )
                Files.delete(preserved)
                Files.delete(file)
                accountStore.save(accountStore.session)
                assertEquals("alice", repository.loadAccounts().session.username)
                assertEquals(accountStore.accountsState.value, repository.loadAccounts())
            } finally {
                accountStore.close()
            }
        }
    }

    @Test
    fun expiryPromptIsOncePerClientSession() = runTest {
        val session = ZhihuAccountSession(login = true, username = "alice", cookies = mutableMapOf("z_c0" to "old"))
        val repository = ZhihuAccountRepository(Path(file.toString())).apply {
            saveAccounts(ZhihuAccounts("alice", listOf(ZhihuSavedAccount("alice", session))))
        }
        emptyMockEngine().use { engine ->
            val store = ZhihuAccountStore(repository, engine)
            try {
                val old = store.client
                repeat(10) { old.reportSessionExpired() }
                assertEquals(SessionExpiryPrompt.SHOWING, old.expiryPrompt.value.phase)
                old.dismissSessionExpiry()
                repeat(10) { old.reportSessionExpired() }
                assertEquals(SessionExpiryPrompt.DISMISSED, old.expiryPrompt.value.phase)
                store.save(store.session.copy(cookies = mutableMapOf("z_c0" to "renewed")))
                assertEquals(SessionExpiryPrompt.ACTIVE, old.expiryPrompt.value.phase)
                old.reportSessionExpired()
                assertEquals(SessionExpiryPrompt.SHOWING, old.expiryPrompt.value.phase)
                store.replaceSession(store.session)
                old.reportSessionExpired()
                assertEquals(SessionExpiryPrompt.ACTIVE, store.client.expiryPrompt.value.phase)
                store.client.reportSessionExpired()
                assertEquals(SessionExpiryPrompt.SHOWING, store.client.expiryPrompt.value.phase)
            } finally {
                store.close()
            }
        }
    }

    @Test
    fun independentStoresKeepTheirEngineAcrossSessionReplacementAndValidation() = runTest {
        val firstEngine = MockEngine { request ->
            assertEquals("/api/v4/me", request.url.encodedPath)
            respond("""{"id":"alice","name":"Alice","url_token":"alice","user_type":"people"}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val secondEngine = MockEngine { error("另一个store不能接收到当前store的请求") }
        var first: ZhihuAccountStore? = null
        var second: ZhihuAccountStore? = null
        try {
            val firstStore = ZhihuAccountStore(ZhihuAccountRepository(Path(file.toString())), firstEngine)
            first = firstStore
            assertSame(firstEngine, firstStore.client.httpClient().engine)
            second = ZhihuAccountStore(ZhihuAccountRepository(Path(directory.resolve("second-account.json").toString())), secondEngine)
            firstStore.replaceSession(firstStore.session)
            assertSame(firstEngine, firstStore.client.httpClient().engine, "其它store创建不能改变原store替换会话时的engine")
            assertTrue(firstStore.login(mutableMapOf("z_c0" to "synthetic-alice")))
            assertSame(firstEngine, firstStore.client.httpClient().engine)
            assertSame(secondEngine, second.client.httpClient().engine)
            assertTrue(secondEngine.requestHistory.isEmpty())
            firstStore.close()
            firstEngine.coroutineContext.job.join()
            assertTrue(firstEngine.coroutineContext.job.isCompleted, "store关闭必须释放自己的engine")
            assertTrue(secondEngine.coroutineContext.job.isActive, "关闭一个store不能关闭另一个store的engine")
        } finally {
            first?.close()
            second?.close()
            firstEngine.close()
            secondEngine.close()
        }
    }

    @Test
    fun failedInitialClientConstructionReleasesEngineWithoutReplacingOriginalError() {
        val delegate = MockEngine { error("构造失败不能发请求") }
        val originalError = IllegalStateException("client init failed")
        val cleanupError = IllegalStateException("engine close failed")
        var closed = 0
        val engine = object : HttpClientEngine by delegate {
            override val coroutineContext: kotlin.coroutines.CoroutineContext
                get() = throw originalError

            override fun close() {
                closed++
                delegate.close()
                throw cleanupError
            }
        }
        try {
            val error = assertFailsWith<IllegalStateException> {
                ZhihuAccountStore(ZhihuAccountRepository(Path(file.toString())), engine)
            }
            assertSame(originalError, error)
            assertEquals(1, closed, "store构造失败后也必须释放已接收的engine")
            assertSame(cleanupError, error.suppressedExceptions.single())
            assertTrue(delegate.requestHistory.isEmpty())
            Files.createDirectory(file)
            var transferredClosed = 0
            val transferred = object : HttpClientEngine by delegate {
                override fun close() {
                    transferredClosed++
                    throw cleanupError
                }
            }
            val loadingError = assertFailsWith<IOException> {
                ZhihuAccountStore(ZhihuAccountRepository(Path(file.toString())), transferred)
            }
            assertEquals(1, transferredClosed, "读取账户失败也要释放移交的engine")
            assertSame(cleanupError, loadingError.suppressedExceptions.single())
        } finally {
            delegate.close()
        }
    }

    @Test
    fun anonymousWebBootstrapsOnceAndSendsOnlyItsSignedDeviceSnapshot() = runTest {
        val bootstrapStarted = CompletableDeferred<Unit>()
        val finishBootstrap = CompletableDeferred<Unit>()
        val requests = mutableListOf<String>()
        val guest = ZhihuAccountSession(cookies = mutableMapOf("z_c0" to "must-not-read-auth", "d_c0" to "must-not-read-device", "_xsrf" to "must-not-read-xsrf"))
        val repository = ZhihuAccountRepository(Path(file.toString())).apply { saveAccounts(ZhihuAccounts(guestCookies = guest.cookies)) }
        val engine = MockEngine { request ->
            requests += request.url.encodedPath
            assertNull(request.headers[HttpHeaders.Authorization])
            assertNull(request.headers["x-app-za"])
            assertFalse(request.headers[HttpHeaders.Cookie].orEmpty().contains("must-not-read"))
            when (request.url.encodedPath) {
                "/signin" -> {
                    assertNull(request.headers[HttpHeaders.Cookie])
                    bootstrapStarted.complete(Unit)
                    finishBootstrap.await()
                    respond("<html></html>", headers = headersOf(HttpHeaders.SetCookie, "_xsrf=anonymous-xsrf; Domain=zhihu.com; Path=/"))
                }
                "/udid" -> {
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals("anonymous-xsrf", request.headers["x-xsrftoken"])
                    respond("", headers = headersOf(HttpHeaders.SetCookie, "d_c0=anonymous-device; Domain=zhihu.com; Path=/"))
                }
                "/public" -> {
                    assertTrue(request.headers[HttpHeaders.Cookie].orEmpty().contains("d_c0=anonymous-device"))
                    assertTrue((0..126).any { seed -> request.headers["x-zse-96"] == ZhihuFetchSignature.createZse96Header(ZHIHU_WEB_ZSE93, request.url.toString(), "anonymous-device", seed = seed) })
                    respond("{}", headers = headersOf(HttpHeaders.SetCookie, "z_c0=must-not-save-auth; Domain=zhihu.com; Path=/"))
                }
                "/external" -> {
                    assertNull(request.headers[HttpHeaders.Cookie])
                    assertNull(request.headers["x-zse-96"])
                    respond("{}")
                }
                else -> error("不应访问 ${request.url.encodedPath}")
            }
        }
        val store = ZhihuAccountStore(repository, engine)
        try {
            val persisted = Files.readAllBytes(file)
            val owner = store.client
            val first = async { owner.request("https://www.zhihu.com/public", useCookies = false) }
            bootstrapStarted.await()
            val second = async { owner.request("https://www.zhihu.com/public", useCookies = false) }
            finishBootstrap.complete(Unit)
            assertEquals(HttpStatusCode.OK, first.await().status)
            assertEquals(HttpStatusCode.OK, second.await().status)
            assertEquals(1, requests.count { it == "/signin" })
            assertEquals(1, requests.count { it == "/udid" })
            assertFailsWith<ZhihuLoginRequiredException> { owner.request("https://api.zhihu.com/mobile", profile = ZhihuRequestProfile.Mobile, useCookies = false) }
            owner.httpClient(useCookies = false).get("https://example.com/external")
            owner.withAuthenticatedClient { _, cookies -> assertTrue(cookies.isEmpty()) }
            assertContentEquals(persisted, Files.readAllBytes(file))
        } finally {
            finishBootstrap.complete(Unit)
            store.close()
            engine.close()
        }
    }

    @Test
    fun anonymousInitializationCanRetryAndOwnerReplacementCancelsTheOldRequest() = runTest {
        var attempts = 0
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val repository = ZhihuAccountRepository(Path(file.toString()))
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/signin" -> {
                    attempts++
                    respond("<html></html>")
                }
                "/udid" -> if (attempts == 1) respond("") else respond("", headers = headersOf(HttpHeaders.SetCookie, "d_c0=anonymous-device; Domain=zhihu.com; Path=/"))
                "/pending" -> {
                    started.complete(Unit)
                    finish.await()
                    respond("{}", headers = headersOf(HttpHeaders.SetCookie, "d_c0=late-device; Domain=zhihu.com; Path=/"))
                }
                else -> error("不应访问 ${request.url.encodedPath}")
            }
        }
        val store = ZhihuAccountStore(repository, engine)
        try {
            val old = store.client
            assertFailsWith<IllegalStateException> { old.request("https://www.zhihu.com/pending", useCookies = false) }
            val pending = async { old.request("https://www.zhihu.com/pending", useCookies = false) }
            started.await()
            assertEquals(2, attempts)
            store.replaceSession(ZhihuAccountSession())
            assertFailsWith<kotlinx.coroutines.CancellationException> { pending.await() }
            finish.complete(Unit)
            assertTrue(store.session.cookies.isEmpty())
            assertFalse(Files.exists(file))
            assertNotSame(old, store.client)
        } finally {
            finish.complete(Unit)
            store.close()
            engine.close()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun anonymousWebInitializationAndCookieBoundariesFailClosed() = runTest {
        val deviceStarted = CompletableDeferred<Unit>()
        val finishFailedDevice = CompletableDeferred<Unit>()
        var signinCount = 0
        var deviceCount = 0
        var businessCount = 0
        var targetCount = 0
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/signin" -> {
                    signinCount++
                    assertNull(request.headers[HttpHeaders.Cookie], "失败 jar 必须在下一次初始化前清空")
                    respond("<html></html>", headers = headersOf(HttpHeaders.SetCookie, "d_c0=early-device; Domain=zhihu.com; Path=/"))
                }
                "/udid" -> {
                    deviceCount++
                    if (deviceCount == 1) {
                        deviceStarted.complete(Unit)
                        finishFailedDevice.await()
                        respond("{}", HttpStatusCode.Forbidden, headersOf(HttpHeaders.SetCookie, "d_c0=failed-device; Domain=zhihu.com; Path=/"))
                    } else {
                        respond("", headers = headersOf(HttpHeaders.SetCookie, "d_c0=ready-device; Domain=zhihu.com; Path=/"))
                    }
                }
                "/business" -> {
                    businessCount++
                    respond("{}")
                }
                "/redirect" -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, request.url.parameters["target"]!!))
                "/set-host-only" -> respond("{}", headers = headersOf(HttpHeaders.SetCookie, "_xsrf=host-only; Path=/"))
                "/set-domain" -> respond("{}", headers = headersOf(HttpHeaders.SetCookie, "_xsrf=domain-path; Domain=zhihu.com; Path=/scoped"))
                "/probe", "/scoped/probe" -> {
                    val cookies = request.headers[HttpHeaders.Cookie].orEmpty()
                    if (request.url.host == "child.www.zhihu.com") {
                        assertFalse("host-only" in cookies)
                        assertEquals(request.url.encodedPath.startsWith("/scoped/"), "_xsrf=domain-path" in cookies)
                    } else {
                        assertTrue("_xsrf=host-only" in cookies, "同名不同 domain/path Cookie 不得相互覆盖")
                    }
                    respond("{}")
                }
                else -> {
                    targetCount++
                    respond("{}")
                }
            }
        }
        val store = ZhihuAccountStore(ZhihuAccountRepository(Path(file.toString())), engine)
        try {
            val owner = store.client
            val first = async { runCatching { owner.request("https://www.zhihu.com/business", useCookies = false) } }
            deviceStarted.await()
            val second = async { runCatching { owner.request("https://www.zhihu.com/business", useCookies = false) } }
            runCurrent()
            assertEquals(0, businessCount, "提前收到 d_c0 不能绕过正在初始化的 Deferred")
            finishFailedDevice.complete(Unit)
            assertTrue(first.await().exceptionOrNull() is com.github.zly2006.zhihu.util.HttpStatusException)
            assertTrue(second.await().exceptionOrNull() is com.github.zly2006.zhihu.util.HttpStatusException)
            assertEquals(0, businessCount)
            assertEquals(HttpStatusCode.OK, owner.request("https://www.zhihu.com/business", useCookies = false).status)
            assertEquals(2, signinCount)
            assertEquals(2, deviceCount)
            assertEquals(1, businessCount)
            for (target in listOf("https://api.zhihu.com/target", "https://www.zhihu.com/target", "https://example.com/target")) {
                assertEquals(HttpStatusCode.Found, owner.request("https://www.zhihu.com/redirect", useCookies = false) { url { parameters["target"] = target } }.status)
            }
            assertEquals(0, targetCount, "匿名自动重定向必须禁用，不能发送源 Cookie 或旧签名")
            owner.request("https://www.zhihu.com/set-host-only", useCookies = false)
            owner.request("https://www.zhihu.com/set-domain", useCookies = false)
            owner.request("https://www.zhihu.com/probe", useCookies = false)
            owner.request("https://child.www.zhihu.com/probe", useCookies = false)
            owner.request("https://child.www.zhihu.com/scoped/probe", useCookies = false)
        } finally {
            finishFailedDevice.complete(Unit)
            store.close()
            engine.close()
        }
    }

    private fun emptyMockEngine(): HttpClientEngine =
        MockEngine {
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
}
