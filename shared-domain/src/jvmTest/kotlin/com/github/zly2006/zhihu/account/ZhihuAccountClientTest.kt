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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
