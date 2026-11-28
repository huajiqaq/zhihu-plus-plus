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

import com.github.zly2006.zhihu.data.Http403Log
import com.github.zly2006.zhihu.data.HttpFailureKind
import com.github.zly2006.zhihu.data.HttpFailureRecord
import com.github.zly2006.zhihu.data.installZhihuCommonClientConfig
import com.github.zly2006.zhihu.data.redactDiagnosticText
import com.github.zly2006.zhihu.data.redacted
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headers
import io.ktor.http.headersOf
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.files.Path
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class HttpFailureDiagnosticsTest {
    @Test
    fun errorTextRedactsCredentialsAndKeepsCompleteStack() {
        val text =
            """
            IllegalStateException: 合成错误
            Authorization: Bearer fixture-auth
            Cookie: d_c0=fixture-device; z_c0=fixture-session
            {"access_token":"fixture-token","password":"fixture-password"}
            {"auth":{"access_token":"fixture-nested-token"},"items":[{"password":"fixture-nested-password"}]}
            {"access_token":
                "fixture-multiline-token"}
            failed URL https://www.zhihu.com/api/v4/fixture?token=fixture-query&cursor=fixture-cursor
            failed URL HTTPS://www.zhihu.com/fixture?cursor=fixture-upper-query
                at fixture.Frame.run(Test.kt:123)
                at fixture.Frame.run(ZhihuMobileLoginToken.kt:456)
            STACK_END_MARKER
            """.trimIndent()
        val redacted = redactDiagnosticText(text)
        for (secret in listOf("fixture-auth", "fixture-device", "fixture-session", "fixture-token", "fixture-password", "fixture-nested-token", "fixture-nested-password", "fixture-multiline-token", "fixture-query", "fixture-cursor", "fixture-upper-query")) {
            assertFalse(secret in redacted, "诊断正文和复制信息不能保留 $secret")
        }
        assertTrue("IllegalStateException: 合成错误" in redacted)
        assertTrue("at fixture.Frame.run(Test.kt:123)" in redacted)
        assertTrue("at fixture.Frame.run(ZhihuMobileLoginToken.kt:456)" in redacted)
        assertTrue(redacted.endsWith("STACK_END_MARKER"))
        assertEquals(redacted, redactDiagnosticText(redacted))
    }

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

    private companion object {
        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
