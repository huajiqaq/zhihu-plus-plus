package com.github.zly2006.zhihu.account

import com.github.zly2006.zhihu.data.Http403Log
import com.github.zly2006.zhihu.data.HttpFailureKind
import com.github.zly2006.zhihu.data.HttpFailureRecord
import com.github.zly2006.zhihu.data.installZhihuCommonClientConfig
import com.github.zly2006.zhihu.data.redacted
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headers
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.files.Path
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpFailureDiagnosticsTest {
    @Test
    fun forbiddenResponseKeepsStreamingBodyAndFinalHeaders() = runBlocking {
        val directory = Files.createTempDirectory("http403-record-").toFile()
        val target = directory.resolve("http403.json")
        target.writeText("{broken-fixture-history")
        val log = Http403Log(Path(target.path))
        val payload = """{"error":{"code":40352,"name":"need_login","message":"need_login"}}"""
        val engine = MockEngine { respond(payload, HttpStatusCode.Forbidden, jsonHeaders) }
        val seen = CompletableDeferred<HttpFailureRecord>()
        val client = HttpClient(engine) {
            installZhihuCommonClientConfig(
                mutableMapOf(),
                "diagnostic-test",
                loggedIn = { false },
                onFailure = { record ->
                    log.record(record)
                    seen.complete(record)
                },
            )
        }
        try {
            assertFailsWith<SerializationException> { log.read() }
            client
                .prepareGet("https://www.zhihu.com/api/v4/me?token=fixture-query") {
                    header("X-Duplicate", "one")
                    header("X-Duplicate", "two")
                    header(HttpHeaders.Cookie, "z_c0=fixture-token")
                }.execute { response ->
                    assertEquals(HttpStatusCode.Forbidden, response.status)
                    assertEquals(payload, response.bodyAsText(), "流式响应不能被诊断读取吞掉")
                }
            val record = withTimeout(3_000) { seen.await() }
            assertEquals(HttpFailureKind.LOGIN_REQUIRED, record.kind)
            assertEquals(false, record.loggedIn)
            assertTrue(record.authenticatedRequest)
            assertEquals("https://www.zhihu.com/api/v4/me?token=fixture-query", record.url)
            assertEquals(listOf("one", "two"), record.headers["X-Duplicate"])
            assertEquals(listOf("z_c0=fixture-token"), record.headers[HttpHeaders.Cookie])
            val saved = target.readText()
            assertTrue(Json.parseToJsonElement(saved) is JsonObject, "首次403必须直接保存为JSON对象")
            assertFalse("fixture-token" in saved)
            assertFalse("fixture-query" in saved)
            assertEquals(record.redacted(), Http403Log(log.file).read(), "落盘必须脱敏，原始诊断回调不能被修改")
            assertEquals(record.redacted(), log.pendingFeedback.value)
            assertFalse(log.writeFailed.value)
            val later = record.copy(id = "second-fixture")
            log.record(later)
            assertEquals(record.redacted(), log.read(), "损坏JSON恢复后仍只保留首条")
            assertEquals(record.redacted(), log.pendingFeedback.value)
            log.dismissFeedback()
            assertNull(log.pendingFeedback.value)
            assertEquals(record.redacted(), log.read(), "关闭提示不能删除记录")
            log.record(later)
            val reopened = Http403Log(log.file)
            reopened.record(later)
            assertEquals(record.redacted(), reopened.read())
            assertNull(log.pendingFeedback.value, "重复403不能让已关闭提示复活")
            assertNull(reopened.pendingFeedback.value, "已有文件不能重新触发首次反馈")
            assertEquals(saved, target.readText(), "后续403不得追加或覆盖首条文件")
            log.clear()
            assertFalse(target.exists())
            assertNull(log.read())
            assertNull(log.pendingFeedback.value)
            log.record(later)
            assertEquals(later.redacted(), log.read(), "手动清理后可保存新的首次403")
            assertEquals(later.redacted(), log.pendingFeedback.value)

            assertTrue(target.delete())
            assertTrue(target.mkdir())
            val blocker = target.resolve("blocker").apply { writeText("fixture-keep") }
            val temporary = directory.resolve("http403.json.tmp").apply { writeText("fixture-temporary") }
            assertFailsWith<IOException> { log.read() }
            log.record(record.copy(id = "must-not-write"))
            assertTrue(log.writeFailed.value, "历史读取IO失败必须保留写入失败状态")
            assertEquals("fixture-keep", blocker.readText())
            assertEquals("fixture-temporary", temporary.readText(), "只有损坏JSON可以兜底，IO失败不得覆盖或清理暂存文件")
        } finally {
            client.close()
            engine.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun nonJsonAndUnreadableForbiddenBodiesStillRecordKnownResponseOnce() = runBlocking {
        val directory = Files.createTempDirectory("http403-body-").toFile()
        val log = Http403Log(Path(directory.resolve("http403.json").path))
        val urls = listOf(
            "http://www.zhihu.com/private",
            "https://www.zhihu.com/private",
            "http://api.zhihu.com/private",
            "https://api.zhihu.com/private",
        )
        val brokenUrl = "https://api.zhihu.com/broken"
        val verificationUrl = "https://www.zhihu.com/account/unhuman?next=%2Fprivate"
        val bodyFailure = IOException("fixture-body-read-failed")
        val brokenResponse = CompletableDeferred<HttpFailureRecord>()
        val callbacks = AtomicInteger()
        val requests = AtomicInteger()
        val engine = MockEngine { request ->
            requests.incrementAndGet()
            if (request.url.encodedPath == "/broken") {
                respond(
                    ByteChannel().apply { cancel(bodyFailure) },
                    HttpStatusCode.Forbidden,
                    headersOf(
                        HttpHeaders.ContentType to listOf("text/plain"),
                        HttpHeaders.Location to listOf(verificationUrl),
                    ),
                )
            } else {
                respond("fixture non-JSON forbidden", HttpStatusCode.Forbidden, headersOf(HttpHeaders.ContentType, "text/plain"))
            }
        }
        val client = HttpClient(engine) {
            installZhihuCommonClientConfig(
                mutableMapOf(),
                "diagnostic-test",
                loggedIn = { false },
                onFailure = {
                    callbacks.incrementAndGet()
                    log.record(it)
                    if (it.url == brokenUrl) brokenResponse.complete(it)
                },
            )
        }
        try {
            urls.forEach { url ->
                log.clear()
                assertNull(log.pendingFeedback.value, "手动清理必须消除上一条首次403的反馈")
                client.prepareGet(url).execute { response ->
                    assertEquals(HttpStatusCode.Forbidden, response.status)
                    assertEquals("fixture non-JSON forbidden", response.bodyAsText())
                }
                withTimeout(3_000) { while (log.read() == null) delay(10) }
                val record = assertNotNull(Http403Log(log.file).read())
                assertEquals(url, record.url)
                assertEquals(403, record.status)
                assertEquals(HttpFailureKind.FORBIDDEN, record.kind)
                assertNull(record.verificationUrl)
            }
            log.clear()
            val error = assertFailsWith<IOException> {
                client.prepareGet(brokenUrl).execute { response ->
                    assertEquals(HttpStatusCode.Forbidden, response.status)
                    response.bodyAsText()
                }
            }
            assertTrue(generateSequence<Throwable>(error) { it.cause }.any { it === bodyFailure }, "诊断不能遮蔽原始响应体读取异常")
            val broken = withTimeout(3_000) { brokenResponse.await() }
            assertEquals(brokenUrl, broken.url)
            assertEquals(403, broken.status)
            assertEquals(HttpFailureKind.HUMAN_VERIFICATION, broken.kind)
            assertEquals(verificationUrl, broken.verificationUrl, "响应体损坏仍保留已知Location")
            assertEquals(broken.redacted(), Http403Log(log.file).read(), "损坏响应体仍须记录已知响应，但落盘地址需脱敏")
            client.close()
            withTimeout(3_000) { client.coroutineContext.job.join() }
            assertEquals(urls.size + 1, callbacks.get(), "读取失败也只能发布一次诊断")
            assertEquals(urls.size + 1, requests.get(), "诊断不能重试业务请求")
            assertFalse(log.writeFailed.value)
        } finally {
            client.close()
            engine.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun redirectsAndErrorBodiesReportVerificationWithoutChangingRequests() = runBlocking {
        val directory = Files.createTempDirectory("http403-routes-").toFile()
        val payload = """{"error":{"code":40352,"message":"请完成安全验证","redirect":"/account/risk_control/"}}"""
        val requests = mutableListOf<String>()
        val engine = MockEngine { request ->
            requests += request.url.encodedPath
            when (request.url.encodedPath) {
                "/signin" -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/account/unhuman?next=%2Fsignin"))
                "/account/unhuman" -> respond("<html>challenge</html>", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html"))
                else -> respond(payload, HttpStatusCode.Forbidden, jsonHeaders)
            }
        }
        val store = ZhihuAccountStore(ZhihuAccountRepository(Path(directory.resolve("account.json").path)), engine)
        try {
            val client = store.client
            assertEquals("<html>challenge</html>", client.httpClient().get("https://www.zhihu.com/signin").bodyAsText())
            withTimeout(3_000) { client.verificationState.awaitPhase(HumanVerificationPhase.PROMPT) }
            assertEquals("https://www.zhihu.com/account/unhuman?next=%2Fsignin", client.verificationState.value.url)
            assertNull(store.http403Log.read(), "302与200不伪装成403记录")
            assertEquals(listOf("/signin", "/account/unhuman"), requests, "不额外探测或重试")
            client.acknowledgeVerificationRestart()
            assertEquals(HumanVerificationPhase.PROMPT, client.verificationState.value.phase, "验证前不能确认重启提示来跳过验证")
            assertEquals(payload, client.httpClient().get("https://www.zhihu.com/api/v4/me").bodyAsText())
            withTimeout(3_000) { while (store.http403Log.read() == null) delay(10) }
            assertEquals(HumanVerificationPhase.PROMPT, client.verificationState.value.phase, "并发验证请求不得绕过或重复打开验证")
            val record = assertNotNull(store.http403Log.read())
            assertEquals(HttpFailureKind.HUMAN_VERIFICATION, record.kind)
            assertEquals("https://www.zhihu.com/account/risk_control/", record.verificationUrl)
            assertEquals(false, record.loggedIn)
            assertEquals(3, requests.size)
        } finally {
            store.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun expectSuccessAndNoRedirectClientsKeepTheirOriginalSemantics() = runBlocking {
        val seen = CompletableDeferred<HttpFailureRecord>()
        val engine = MockEngine { respond("""{"error":{"name":"need_login"}}""", HttpStatusCode.Forbidden, jsonHeaders) }
        val client = HttpClient(engine) {
            expectSuccess = true
            installZhihuCommonClientConfig(mutableMapOf(), "diagnostic-test", onFailure = { seen.complete(it) })
        }
        try {
            assertFailsWith<ClientRequestException> { client.get("https://www.zhihu.com/private") }
            assertEquals(HttpFailureKind.LOGIN_REQUIRED, withTimeout(3_000) { seen.await() }.kind)
        } finally {
            client.close()
            engine.close()
        }
        val redirected = CompletableDeferred<HttpFailureRecord>()
        val redirects = MockEngine { respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/account/unhuman")) }
        val noRedirect = HttpClient(redirects) {
            followRedirects = false
            installZhihuCommonClientConfig(mutableMapOf(), "diagnostic-test", onFailure = { redirected.complete(it) })
        }
        try {
            assertEquals(HttpStatusCode.Found, noRedirect.get("https://www.zhihu.com/signin").status)
            assertEquals(HttpFailureKind.HUMAN_VERIFICATION, withTimeout(3_000) { redirected.await() }.kind)
        } finally {
            noRedirect.close()
            redirects.close()
        }
    }

    @Test
    fun nonZhihuForbiddenResponsesBypassDiagnostics() = runBlocking {
        val payload = """{"error":{"name":"unhuman","redirect":"https://www.zhihu.com/account/unhuman"}}"""
        val engine = MockEngine { respond(payload, HttpStatusCode.Forbidden, jsonHeaders) }
        val seen = mutableListOf<HttpFailureRecord>()
        val client = HttpClient(engine) {
            installZhihuCommonClientConfig(mutableMapOf(), "diagnostic-test", onFailure = { seen.add(it) })
        }
        try {
            arrayOf("example.com", "notzhihu.com", "zhihu.com.example.com", "zhihu.com", "zhimg.com", "github.com").forEach { host ->
                val response = client.get("https://$host/private")
                assertEquals(HttpStatusCode.Forbidden, response.status)
                assertEquals(payload, response.bodyAsText())
            }
            client.close()
            withTimeout(3_000) { client.coroutineContext.job.join() }
            assertTrue(seen.isEmpty(), "非知乎403不得记录，也不能借响应中的知乎地址触发验证")
        } finally {
            client.close()
            engine.close()
        }
    }

    @Test
    fun guestVerificationPersistsExactCookieNamesAndClearStillRemovesCredentials() = runBlocking {
        val directory = Files.createTempDirectory("http403-guest-").toFile()
        val file = directory.resolve("account.json")
        val repository = ZhihuAccountRepository(Path(file.path))
        val cookies = mapOf("d_c0" to "fixture-device", "x-zse-96" to "fixture-hyphen", "__zse_ck" to "fixture-proof")
        val engine = MockEngine { request ->
            val header = request.headers[HttpHeaders.Cookie].orEmpty()
            cookies.forEach { (key, value) -> assertTrue("$key=$value" in header) }
            respond("{}", HttpStatusCode.OK, jsonHeaders)
        }
        val store = ZhihuAccountStore(repository, engine)
        try {
            val seed = HttpFailureRecord(
                id = "fixture-first",
                timestampMillis = 1,
                method = "GET",
                url = "https://www.zhihu.com/private",
                status = 403,
                headers = emptyMap(),
                loggedIn = false,
                authenticatedRequest = false,
                kind = HttpFailureKind.HUMAN_VERIFICATION,
            )
            store.http403Log.record(seed)
            assertEquals(seed.redacted(), store.http403Log.pendingFeedback.value)
            val client = store.client
            client.completeHumanVerification(cookies)
            assertFalse(file.exists(), "未进入验证流程不能接受完成回调")
            assertEquals(seed.redacted(), store.http403Log.read(), "无效完成回调不能清理首次403")
            client.reportHumanVerification(null)
            client.beginHumanVerification()
            client.acknowledgeVerificationRestart()
            assertEquals(HumanVerificationPhase.VERIFYING, client.verificationState.value.phase, "验证进行中不可跳过")
            client.completeHumanVerification(cookies)
            assertTrue(file.isFile, "游客凭据不为空时不得把账号文件删除")
            val restored = repository.loadAccounts().session
            assertFalse(restored.login)
            assertEquals(cookies, restored.cookies, "Cookie 名按原样保存")
            assertNull(store.http403Log.read())
            assertNull(store.http403Log.pendingFeedback.value)
            store.http403Log.record(seed.copy(id = "fixture-late"))
            assertNull(store.http403Log.read(), "验证成功后的晚到403不能复活记录")
            assertNull(store.http403Log.pendingFeedback.value)
            client.httpClient().get("https://www.zhihu.com/after-verification").bodyAsText()
            assertEquals(HumanVerificationPhase.RESTART_REQUIRED, client.verificationState.value.phase)
            client.acknowledgeVerificationRestart()
            client.reportHumanVerification(null)
            client.beginHumanVerification()
            assertEquals(HumanVerificationPhase.DISMISSED, client.verificationState.value.phase, "已完成的验证不能重新打开")
            store.clear()
            assertFalse(file.exists())
            assertTrue(store.session.cookies.isEmpty())
            assertFalse(store.session.login)
            client.completeHumanVerification(mapOf("late" to "must-not-return"))
            assertFalse(file.exists(), "旧客户端不能写回游客凭据")
        } finally {
            store.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun candidateVerificationPersistsGuestCookiesWithoutChangingActiveAccount() = runBlocking {
        val directory = Files.createTempDirectory("http403-candidate-").toFile()
        val repository = ZhihuAccountRepository(Path(directory.resolve("account.json").path))
        val active = ZhihuAccountSession(
            login = true,
            username = "fixture-active",
            cookies = mutableMapOf("z_c0" to "fixture-active-token", "d_c0" to "fixture-active-device"),
        )
        val before = ZhihuAccounts(
            activeAccountId = "fixture-active",
            accounts = listOf(ZhihuSavedAccount("fixture-active", active)),
            guestCookies = mapOf("guest-existing" to "fixture-existing"),
        )
        repository.saveAccounts(before)
        val store = ZhihuAccountStore(repository, MockEngine { error("候选验证不应发请求") })
        try {
            val client = store.client
            client.beginHumanVerification()
            assertEquals(HumanVerificationPhase.ACTIVE, client.verificationState.value.phase)
            val candidate = mapOf("z_c0" to "fixture-candidate-token", "d_c0" to "fixture-candidate-device")
            val verified = mapOf("z_c0" to "fixture-untrusted-token", "x-zse-96" to "fixture-hyphen", "__zse_ck" to "fixture-proof")
            client.reportHumanVerification("https://www.zhihu.com/account/risk_control/", candidate)
            client.completeHumanVerification(verified)
            assertEquals(before, repository.loadAccounts(), "PROMPT阶段不能接受候选验证完成回调")
            client.beginHumanVerification()
            client.completeHumanVerification(verified)
            val restored = repository.loadAccounts()
            assertEquals(
                before.copy(
                    guestCookies = before.guestCookies + mapOf(
                        "d_c0" to "fixture-candidate-device",
                        "x-zse-96" to "fixture-hyphen",
                        "__zse_ck" to "fixture-proof",
                    ),
                ),
                restored,
                "候选验证只能更新guest凭据，不能替换活跃账号或改动其Cookie",
            )
            assertFalse(restored.guestCookies.containsKey("z_c0"))
            assertEquals(active, store.session)
            assertEquals(before.accounts, store.accounts)
            assertEquals(HumanVerificationPhase.RESTART_REQUIRED, client.verificationState.value.phase)
        } finally {
            store.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun lateFailureIsRecordedForOldRequestWithoutPromptingReplacementAccount() = runBlocking {
        val directory = Files.createTempDirectory("http403-late-").toFile()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val engine = MockEngine {
            started.complete(Unit)
            release.await()
            respond("""{"error":{"name":"unhuman"}}""", HttpStatusCode.Forbidden, jsonHeaders)
        }
        val store = ZhihuAccountStore(ZhihuAccountRepository(Path(directory.resolve("account.json").path)), engine)
        val old = store.client
        try {
            val request = async { old.httpClient().get("https://www.zhihu.com/late").bodyAsText() }
            started.await()
            store.replaceSession(ZhihuAccountSession(login = true, username = "replacement", cookies = mutableMapOf("z_c0" to "fixture-replacement")))
            release.complete(Unit)
            request.await()
            withTimeout(3_000) { while (store.http403Log.read() == null) delay(10) }
            assertEquals(
                false,
                assertNotNull(store.http403Log.read()).loggedIn,
                "登录状态取请求发送时，不取响应完成时",
            )
            assertEquals(HumanVerificationPhase.ACTIVE, store.client.verificationState.value.phase)
            assertEquals(HumanVerificationPhase.DISMISSED, old.verificationState.value.phase)
            old.reportHumanVerification(null)
            old.beginHumanVerification()
            assertEquals(HumanVerificationPhase.DISMISSED, old.verificationState.value.phase)
            assertEquals("replacement", store.session.username)
        } finally {
            release.complete(Unit)
            store.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun failedGuestCredentialWriteDoesNotReportVerificationSuccess() = runBlocking {
        val directory = Files.createTempDirectory("http403-guest-failed-").toFile()
        val target = directory.resolve("account.json")
        val repository = ZhihuAccountRepository(Path(target.path))
        val store = ZhihuAccountStore(repository, MockEngine { error("不应发请求") })
        try {
            val seed = HttpFailureRecord(
                id = "fixture-first",
                timestampMillis = 1,
                method = "GET",
                url = "https://www.zhihu.com/private",
                status = 403,
                headers = emptyMap(),
                loggedIn = false,
                authenticatedRequest = false,
                kind = HttpFailureKind.HUMAN_VERIFICATION,
            )
            store.http403Log.record(seed)
            assertEquals(seed.redacted(), store.http403Log.read())
            assertEquals(seed.redacted(), store.http403Log.pendingFeedback.value)
            target.mkdir()
            target.resolve("not-empty").writeText("fixture")
            val client = store.client
            client.reportHumanVerification(null)
            client.beginHumanVerification()
            assertFailsWith<IOException> { client.completeHumanVerification(mapOf("d_c0" to "fixture-new")) }
            assertEquals(HumanVerificationPhase.VERIFYING, client.verificationState.value.phase)
            assertTrue(store.session.cookies.isEmpty(), "持久化失败不能先发布新Cookie")
            assertEquals(seed.redacted(), store.http403Log.read(), "凭据保存失败必须保留首次403")
            assertEquals(seed.redacted(), store.http403Log.pendingFeedback.value)

            assertTrue(target.deleteRecursively())
            val diagnostic = directory.resolve("network-diagnostics/http403.json")
            assertTrue(diagnostic.delete())
            assertTrue(diagnostic.mkdir())
            val blocker = diagnostic.resolve("not-empty").apply { writeText("fixture-keep") }
            assertFailsWith<IOException> { client.completeHumanVerification(mapOf("d_c0" to "fixture-new")) }
            assertEquals("fixture-new", repository.loadAccounts().session.cookies["d_c0"], "先保存凭据，再尝试清理日志")
            assertEquals(HumanVerificationPhase.VERIFYING, client.verificationState.value.phase, "日志清理失败不能宣布需要重启")
            assertEquals(seed.redacted(), store.http403Log.pendingFeedback.value)
            assertEquals("fixture-keep", blocker.readText())
        } finally {
            store.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun manualCompletionRequiresServerConfirmationAndKeepsCandidateIsolated() = runBlocking {
        val directory = Files.createTempDirectory("http403-manual-confirm-").toFile()
        val repository = ZhihuAccountRepository(Path(directory.resolve("account.json").path))
        var body = """{"block_level":1}"""
        var status = HttpStatusCode.OK
        val attempts = AtomicInteger()
        val store = ZhihuAccountStore(
            repository,
            MockEngine { request ->
                attempts.incrementAndGet()
                assertEquals("https://www.zhihu.com/api/v4/anticrawl/new_captcha_appeal?session=fixture-session", request.url.toString())
                assertEquals("https://www.zhihu.com/account/unhuman?session=fixture-session", request.headers[HttpHeaders.Referrer])
                assertTrue("z_c0=fixture-candidate" in request.headers[HttpHeaders.Cookie].orEmpty())
                assertFalse("fixture-active" in request.headers[HttpHeaders.Cookie].orEmpty())
                respond(
                    body,
                    status,
                    headers {
                        append(HttpHeaders.ContentType, "application/json")
                        append(HttpHeaders.SetCookie, "__zse_ck=fixture-proof; Path=/; Domain=.zhihu.com")
                    },
                )
            },
        )
        try {
            val active = ZhihuAccountSession(login = true, username = "fixture-owner", cookies = mutableMapOf("z_c0" to "fixture-active"))
            store.replaceSession(active)
            val seed = HttpFailureRecord(
                id = "fixture-first",
                timestampMillis = 1,
                method = "GET",
                url = "https://www.zhihu.com/private",
                status = 403,
                headers = emptyMap(),
                loggedIn = true,
                authenticatedRequest = true,
                kind = HttpFailureKind.HUMAN_VERIFICATION,
            )
            store.http403Log.record(seed)
            assertEquals(seed.redacted(), store.http403Log.pendingFeedback.value)
            val client = store.client
            val candidate = mapOf("z_c0" to "fixture-candidate", "d_c0" to "fixture-device")
            client.reportHumanVerification("https://www.zhihu.com/account/unhuman?session=fixture-session", candidate)
            assertFalse(client.confirmHumanVerification(emptyMap()))
            assertEquals(0, attempts.get(), "未进入验证页不发送状态请求")
            client.beginHumanVerification()
            assertFalse(client.confirmHumanVerification(emptyMap()))
            body = """{"block_level":"0","code":"0"}"""
            assertFalse(client.confirmHumanVerification(emptyMap()), "数字字符串不能冒充官方数字成功字段")
            body = """{"code":0,"error":{"code":40352}}"""
            assertFalse(client.confirmHumanVerification(emptyMap()), "带错误的响应不能宣布成功")
            body = """{"block_level":0}"""
            status = HttpStatusCode.Forbidden
            assertFalse(client.confirmHumanVerification(emptyMap()), "失败状态码不能被成功字段覆盖")
            assertEquals(HumanVerificationPhase.VERIFYING, client.verificationState.value.phase)
            assertEquals(active, store.session)
            assertTrue(repository.loadAccounts().guestCookies.isEmpty(), "未通过前不能发布候选验证凭据")
            assertEquals(seed.redacted(), store.http403Log.read(), "未通过验证不能清理或替换首次403")
            status = HttpStatusCode.OK
            assertTrue(client.confirmHumanVerification(emptyMap()))
            assertEquals(HumanVerificationPhase.RESTART_REQUIRED, client.verificationState.value.phase)
            assertEquals(active, store.session)
            assertEquals("fixture-proof", repository.loadAccounts().guestCookies["__zse_ck"])
            assertFalse(repository.loadAccounts().guestCookies.containsKey("z_c0"))
            assertNull(store.http403Log.read())
            assertNull(store.http403Log.pendingFeedback.value)
            store.http403Log.record(seed.copy(id = "fixture-late"))
            assertNull(store.http403Log.read(), "手动验证成功后的晚到403不能复活记录")
            assertNull(store.http403Log.pendingFeedback.value)
            assertEquals(5, attempts.get(), "每次手动确认只发一次请求，不自动轮询")
        } finally {
            store.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun inFlightLoginCannotReplaceAccountAfterVerificationStarts() = runBlocking {
        val directory = Files.createTempDirectory("http403-login-race-").toFile()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val store = ZhihuAccountStore(
            ZhihuAccountRepository(Path(directory.resolve("account.json").path)),
            MockEngine {
                started.complete(Unit)
                release.await()
                respond("""{"id":"fixture-new","name":"New account"}""", HttpStatusCode.OK, jsonHeaders)
            },
        )
        try {
            val owner = store.client
            val login = async { store.login(mutableMapOf("z_c0" to "fixture-new")) }
            started.await()
            owner.reportHumanVerification(null)
            assertFailsWith<IllegalStateException> { store.clear() }
            assertFailsWith<IllegalStateException> { store.replaceSession(ZhihuAccountSession(login = true, username = "bypass")) }
            release.complete(Unit)
            assertFalse(login.await())
            assertTrue(store.client === owner, "在途登录不能清除验证所属会话")
            assertFalse(store.session.login)
            assertEquals(HumanVerificationPhase.PROMPT, owner.verificationState.value.phase)
        } finally {
            release.complete(Unit)
            store.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun concurrentFirstRecordsAndLegacyArrayKeepOneRedactedSnapshot() = runBlocking {
        val directory = Files.createTempDirectory("http403-first-").toFile()
        val target = directory.resolve("http403.json")
        val logs = listOf(Http403Log(Path(target.path)), Http403Log(Path(target.path)))
        val first = HttpFailureRecord(
            id = "first",
            timestampMillis = 1,
            method = "GET",
            url = "https://fixture-user:fixture-password@www.zhihu.com/private?token=fixture-query#fixture-fragment",
            status = 403,
            headers = mapOf(
                HttpHeaders.Cookie to listOf("z_c0=fixture-token"),
                HttpHeaders.Authorization to listOf("Bearer fixture-token"),
                "X-Token" to listOf("fixture-token"),
                "X-Signature" to listOf("fixture-signature"),
                HttpHeaders.Referrer to listOf("https://www.zhihu.com/?token=fixture-query"),
            ),
            loggedIn = true,
            authenticatedRequest = true,
            kind = HttpFailureKind.HUMAN_VERIFICATION,
            errorName = "fixture-error-name",
            errorMessage = "fixture-error-message",
            verificationUrl = "https://www.zhihu.com/account/unhuman?session=fixture-verification#fixture-fragment",
        )
        val records = listOf(first, first.copy(id = "second", timestampMillis = 2))
        try {
            val start = CompletableDeferred<Unit>()
            val writes = logs.mapIndexed { index, log ->
                async(Dispatchers.Default) {
                    start.await()
                    log.record(records[index])
                }
            }
            start.complete(Unit)
            withTimeout(3_000) { writes.awaitAll() }
            val feedback = logs.mapNotNull { it.pendingFeedback.value }
            assertEquals(1, feedback.size, "同文件并发403只能有一次首次写入和反馈")
            val winner = feedback.single()
            assertTrue(winner in records.map { it.redacted() })
            val saved = target.readText()
            assertEquals(winner, Json.decodeFromString(HttpFailureRecord.serializer(), saved))
            assertFalse("fixture-" in saved, "URL、头、错误文本和验证地址中的凭据均不得落盘")
            logs.forEach { log ->
                log.dismissFeedback()
                log.record(first.copy(id = "later"))
                assertEquals(winner, log.read())
                assertNull(log.pendingFeedback.value)
            }
            assertEquals(saved, target.readText())

            target.writeText(Json.encodeToString(records))
            val migrated = Http403Log(Path(target.path))
            assertEquals(first.redacted(), migrated.read(), "旧数组仅保留最早一条并脱敏")
            val migratedJson = target.readText()
            assertEquals(first.redacted(), Json.decodeFromString(HttpFailureRecord.serializer(), migratedJson))
            assertFalse("fixture-" in migratedJson)
            migrated.record(records.last())
            assertEquals(migratedJson, target.readText(), "迁移后的记录不能被新403覆盖")
            assertNull(migrated.pendingFeedback.value, "迁移旧记录不能重新弹出首次反馈")
        } finally {
            directory.deleteRecursively()
        }
    }

    private suspend fun kotlinx.coroutines.flow.StateFlow<HumanVerificationState>.awaitPhase(phase: HumanVerificationPhase) {
        while (value.phase != phase) delay(10)
    }

    private companion object {
        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
