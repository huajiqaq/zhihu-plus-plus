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

import com.github.zly2006.zhihu.util.HttpStatusException
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
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.nio.file.Path as JavaPath

class ZhihuAccountClientTest {
    private lateinit var directory: JavaPath
    private lateinit var file: JavaPath

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
    fun browserAccountRequiresVerifiedProfileAndRetiresTheSignedOutClient() = runTest {
        var signedIn = true
        val mock = MockEngine { request ->
            assertEquals("/api/v4/me", request.url.encodedPath)
            if (!signedIn) throw BrowserBridgeException(BrowserSessionStatus.Guest)
            respond("""{"id":"browser-person","name":"浏览器用户"}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val store = ZhihuAccountStore(null, object : HttpClientEngine by mock, BrowserAccountEngine {})
        try {
            val guest = store.client
            assertFailsWith<BrowserBridgeException> { store.login(guest, mutableMapOf("z_c0" to "synthetic-rejected")) }
            assertFailsWith<BrowserBridgeException> { store.save(ZhihuAccountSession(login = true, username = "unverified")) }
            assertSame(guest, store.client)
            assertFalse(store.session.login)
            assertEquals(BrowserSessionStatus.Authenticated, store.refreshBrowserSession())
            assertFalse(guest.isCurrent)
            assertEquals("browser-person", store.session.profile?.id)
            val owner = store.client
            signedIn = false
            assertEquals(BrowserSessionStatus.Expired, store.refreshBrowserSession())
            assertFalse(owner.isCurrent)
            assertFalse(store.session.login)
            assertNull(store.session.profile)
        } finally {
            store.close()
        }
    }

    @Test
    fun browserSignOutRevokesOnlyTheCurrentTabBeforeRetiringItsClient() = runTest {
        var requests = 0
        var rejectRevocation = true
        var revocations = 0
        val mock = MockEngine {
            requests++
            respond("""{"id":"fixture-person","name":"Fixture"}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val store = ZhihuAccountStore(null, object : HttpClientEngine by mock, BrowserAccountEngine {
            override suspend fun revokeSession(owner: ZhihuAccountClient) {
                check(owner.isCurrent)
                revocations++
                check(!rejectRevocation) { "synthetic revocation failure" }
            }
        })
        try {
            assertEquals(BrowserSessionStatus.Authenticated, store.refreshBrowserSession())
            val owner = store.client
            val session = store.session
            assertFailsWith<IllegalStateException> { store.signOut(owner) }
            assertSame(owner, store.client)
            assertEquals(session, store.session)
            rejectRevocation = false
            assertTrue(store.signOut(owner))
            assertFalse(owner.isCurrent)
            assertFalse(store.session.login)
            assertNull(store.session.profile)
            assertEquals(BrowserSessionStatus.Guest, store.browserSessionStatus.value)
            assertFalse(store.signOut(owner))
            assertEquals(2, revocations)
            assertEquals(1, requests, "Sign-out must not call a website logout or cookie endpoint")
        } finally {
            store.close()
        }
    }

    @Test
    fun loginReceiptSurvivesPageRecreationAndCannotOutliveItsAccount() = runTest {
        var id = "fixture-A"
        val mock = MockEngine {
            respond("""{"id":"$id","name":"Fixture"}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val store = ZhihuAccountStore(null, object : HttpClientEngine by mock, BrowserAccountEngine {
            override suspend fun revokeSession(owner: ZhihuAccountClient) = Unit
        })
        try {
            val guest = store.client
            store.refreshBrowserSession()
            val owner = store.client
            assertNull(store.loginSuccessReceipt.value, "Automatic profile refresh is not an explicit login")
            assertFalse(store.reportLoginSuccess(guest, "Fixture"))
            assertTrue(store.reportLoginSuccess(owner, "Fixture"))
            val receipt = assertNotNull(store.loginSuccessReceipt.value)
            assertSame(receipt, store.loginSuccessReceipt.value, "A new page reads the same store-owned receipt")
            assertFalse(store.reportLoginSuccess(guest, "Fixture"))
            assertTrue(store.consumeLoginSuccess(receipt))
            assertFalse(store.consumeLoginSuccess(receipt))
            store.reportLoginSuccess(owner, "Fixture")
            id = "fixture-B"
            store.refreshBrowserSession()
            assertNull(store.loginSuccessReceipt.value)
            assertFalse(store.reportLoginSuccess(owner, "Fixture"))
            val current = store.client
            assertTrue(store.reportLoginSuccess(current, "Fixture"))
            store.signOut(current)
            assertNull(store.loginSuccessReceipt.value)
        } finally {
            store.close()
        }
    }

    @Test
    fun browserIdentitySwitchRequiresTheRequestedVerifiedProfile() = runTest {
        var profileId = "fixture-A"
        var wrongAccount = true
        val mock = MockEngine { request ->
            assertEquals("/api/v4/me", request.url.encodedPath)
            respond("""{"id":"$profileId","name":"Fixture"}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val store = ZhihuAccountStore(null, object : HttpClientEngine by mock, BrowserAccountEngine {
            override suspend fun identityAccounts(owner: ZhihuAccountClient) = listOf(ZhihuIdentityAccount("fixture-B", name = "Other"))
            override suspend fun changeIdentity(owner: ZhihuAccountClient, targetUserId: String?): Boolean {
                check(owner.isCurrent)
                profileId = if (wrongAccount) "fixture-C" else checkNotNull(targetUserId)
                return true
            }
        })
        try {
            assertEquals(BrowserSessionStatus.Authenticated, store.refreshBrowserSession())
            val owner = store.client
            assertEquals("fixture-B", store.browserIdentityAccounts(owner).single().id)
            assertFailsWith<Exception> { store.changeBrowserIdentity(owner, "fixture-B") }
            assertSame(owner, store.client)
            assertEquals("fixture-A", store.session.profile?.id, "A mismatched candidate must not replace the visible account")
            wrongAccount = false
            val result = store.changeBrowserIdentity(owner, "fixture-B")
            assertFalse(owner.isCurrent)
            assertEquals("fixture-B", result.account.id)
            assertEquals("fixture-B", store.session.profile?.id)
            assertTrue(store.session.cookies.isEmpty(), "Browser identity results contain no credentials")
        } finally {
            store.close()
        }
    }

    @Test
    fun browserSenderDeniedByEngineDoesNotRequireIdentityRecovery() = runTest {
        var sendAttempts = 0
        var restores = 0
        var requests = 0
        val mock = MockEngine { request ->
            requests++
            assertEquals("/api/v4/me", request.url.encodedPath)
            respond("""{"id":"viewer","name":"Viewer"}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        // 仅验证 store 对引擎拒绝的契约，不执行 Wasm 对 authorized=false 的实际解码。
        val store = ZhihuAccountStore(null, object : HttpClientEngine by mock, BrowserAccountEngine {
            override suspend fun sendCommentAs(owner: ZhihuAccountClient, sender: CommentSender, url: String, body: JsonObject): BrowserCommentSendResult {
                sendAttempts++
                throw BrowserBridgeException(BrowserSessionStatus.Denied)
            }
            override suspend fun restoreCommentIdentity(owner: ZhihuAccountClient): Boolean {
                restores++
                return true
            }
        })
        try {
            assertEquals(BrowserSessionStatus.Authenticated, store.refreshBrowserSession())
            val owner = store.client
            val session = store.session
            val sender = CommentSender("viewer", ZhihuAccountProfileSnapshot("alias", "Alias"))
            val body = Json.parseToJsonElement("""{"content":"<p>Fixture draft</p>"}""") as JsonObject
            val denied = assertFailsWith<BrowserBridgeException> {
                store.sendBrowserCommentAs(owner, sender, "https://www.zhihu.com/api/v4/comment_v5/answers/1/comment", body)
            }
            assertEquals(BrowserSessionStatus.Denied, denied.status)
            assertFalse(owner.browserCommentRestoreRequired.value)
            assertSame(owner, store.client)
            assertTrue(owner.isCurrent)
            assertEquals(session, store.session)
            assertTrue(store.restoreBrowserCommentIdentity(owner), "A refused send needs no identity recovery")
            assertEquals(0, restores, "Do not start a recovery transaction after user refusal")
            assertEquals(1, sendAttempts)
            assertEquals(1, requests, "Only the synthetic initial profile lookup was requested")
        } finally {
            store.close()
        }
    }

    @Test
    fun browserSenderRecoveryRetainsTheViewerAndNeverResends() = runTest {
        var sends = 0
        var restores = 0
        val mock = MockEngine {
            respond("""{"id":"viewer","name":"Viewer"}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val store = ZhihuAccountStore(null, object : HttpClientEngine by mock, BrowserAccountEngine {
            override suspend fun sendCommentAs(owner: ZhihuAccountClient, sender: CommentSender, url: String, body: JsonObject): BrowserCommentSendResult {
                sends++
                return BrowserCommentSendResult(Json.parseToJsonElement("""{"id":"fixture-comment"}""") as JsonObject, restored = false)
            }
            override suspend fun restoreCommentIdentity(owner: ZhihuAccountClient): Boolean = ++restores > 1
        })
        try {
            store.refreshBrowserSession()
            val owner = store.client
            val sender = CommentSender("viewer", ZhihuAccountProfileSnapshot("alias", "Alias"))
            val body = JsonObject(emptyMap())
            val result = store.sendBrowserCommentAs(owner, sender, "https://www.zhihu.com/api/v4/comment_v5/answers/1/comment", body)
            assertFalse(result.restored)
            assertTrue(owner.browserCommentRestoreRequired.value)
            assertSame(owner, store.client)
            assertEquals("viewer", store.session.profile?.id)
            assertFailsWith<Exception> { store.sendBrowserCommentAs(owner, sender, "https://www.zhihu.com/api/v4/comment_v5/answers/1/comment", body) }
            assertFalse(store.restoreBrowserCommentIdentity(owner))
            assertTrue(owner.browserCommentRestoreRequired.value)
            assertTrue(store.restoreBrowserCommentIdentity(owner))
            assertFalse(owner.browserCommentRestoreRequired.value)
            assertEquals(1, sends)
            assertEquals(2, restores)
        } finally {
            store.close()
        }
    }

    @Test
    fun challengedBrowserRefreshKeepsTheVerifiedAccount() = runTest {
        var challenged = false
        val mock = MockEngine {
            if (challenged) throw BrowserVerificationRequired()
            respond("""{"id":"fixture-person","name":"Fixture"}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val store = ZhihuAccountStore(null, object : HttpClientEngine by mock, BrowserAccountEngine {})
        try {
            assertEquals(BrowserSessionStatus.Authenticated, store.refreshBrowserSession())
            val owner = store.client
            val session = store.session
            challenged = true
            assertEquals(BrowserSessionStatus.Unavailable, store.refreshBrowserSession())
            assertSame(owner, store.client)
            assertEquals(session, store.session)
            assertEquals(BrowserSessionStatus.Authenticated, store.browserSessionStatus.value)
        } finally {
            store.close()
        }
    }

    @Test
    fun oldBrowserVerificationCannotDismissOrConfirmANewerChallenge() = runTest {
        val mock = MockEngine { error("Unexpected request") }
        val store = ZhihuAccountStore(null, object : HttpClientEngine by mock, BrowserAccountEngine {})
        try {
            val owner = store.client
            val pending = assertNotNull(owner.browserVerification)
            val firstResult = CompletableDeferred<Boolean>()
            owner.reportBrowserVerification({ true }, firstResult::await)
            val first = assertNotNull(pending.value)
            assertTrue(first.open())
            val late = async(start = CoroutineStart.UNDISPATCHED) { first.check() }
            owner.reportBrowserVerification({ true }) { true }
            val second = assertNotNull(pending.value)
            first.dismiss()
            firstResult.complete(true)
            assertFalse(late.await())
            assertSame(second, pending.value)
        } finally {
            store.close()
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
                assertFailsWith<HttpStatusException> { owner.get("https://www.zhihu.com/still-expired") }
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
            val writer = async(start = CoroutineStart.UNDISPATCHED) {
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
        val loginStarted = CompletableDeferred<Unit>()
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
            when {
                request.url.encodedPath == "/mobile" -> mobileStarted.complete(Unit)
                request.headers[HttpHeaders.Cookie].orEmpty().contains("z_c0=candidate-alice") -> loginStarted.complete(Unit)
                else -> requestStarted.complete(Unit)
            }
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
                val login = async { accountStore.login(oldClient, mutableMapOf("z_c0" to "candidate-alice")) }
                requestStarted.await()
                mobileStarted.await()
                loginStarted.await()
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
                assertFalse(login.await(), "验证在途时换账号，旧登录不能提交")
                val requestsBeforeLateLogin = engine.requestHistory.size
                assertFalse(accountStore.login(oldClient, mutableMapOf("z_c0" to "candidate-alice")), "旧扫码回调不能借用新 owner")
                assertFalse(
                    accountStore.login(
                        oldClient,
                        ZhihuMobileLoginToken(
                            accessToken = "synthetic-access",
                            refreshToken = null,
                            tokenType = "bearer",
                            expiresAt = null,
                            cookies = mapOf("z_c0" to "candidate-alice"),
                        ),
                    ),
                    "旧手机登录同样不能借用新 owner",
                )
                assertEquals(requestsBeforeLateLogin, engine.requestHistory.size, "已失效 owner 不应开始候选验证")

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
            assertTrue(first.await().exceptionOrNull() is HttpStatusException)
            assertTrue(second.await().exceptionOrNull() is HttpStatusException)
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
