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

import com.github.zly2006.zhihu.util.HttpStatusException
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import com.github.zly2006.zhihu.viewmodel.zhihuFetchFailureMessage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZhihuAuthenticatedJsonTest {
    @Test
    fun publicJsonPreservesHttpDecodeAndTransportFailures() = runBlocking {
        val transportFailure = java.io.IOException("synthetic transport failure")
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/missing" -> respond("{}", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))
                "/login" -> respond("""{"error":{"name":"need_login"}}""", HttpStatusCode.Forbidden, headersOf(HttpHeaders.ContentType, "application/json"))
                "/captcha" -> respond("""{"error":{"name":"unhuman"}}""", HttpStatusCode.Forbidden, headersOf(HttpHeaders.ContentType, "application/json"))
                "/login-flag" -> respond("""{"error":{"need_login":true,"redirect":"https://www.zhihu.com/account/unhuman?need_login=true&session=fixture-session&next=%2Fsignin","code":40353,"message":"请您登录后查看更多专业优质内容。"}}""", HttpStatusCode.Forbidden, headersOf(HttpHeaders.ContentType, "application/json"))
                "/login-html" -> respond("<html><body>请您登录后查看更多专业优质内容。</body></html>", headers = headersOf(HttpHeaders.ContentType, "text/html"))
                "/public-json" -> respond("""{"data":[{"title":"请您登录后查看更多专业优质内容。"}]}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                "/quoted-html" -> respond("<html><body><div class='RichText'><p>有人看到“请您登录后查看更多专业优质内容。”</p></div><script>var text='请您登录后查看更多专业优质内容。';</script></body></html>", headers = headersOf(HttpHeaders.ContentType, "text/html"))
                "/unauthorized" -> respond("""{"error":{"message":"unclassified failure"}}""", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.ContentType, "application/json"))
                "/invalid" -> respond("{broken", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                else -> throw transportFailure
            }
        }
        val client = HttpClient(engine)
        val environment = object : ZhihuApiEnvironment {
            override fun httpClient() = client

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
        }
        try {
            val missing = assertFailsWith<HttpStatusException> { environment.fetchJson("https://www.zhihu.com/missing", "") }
            assertEquals(HttpStatusCode.NotFound, missing.status)
            assertEquals("内容或接口不存在（HTTP 404）", zhihuFetchFailureMessage(missing))
            val login = assertFailsWith<HttpStatusException> { environment.fetchJson("https://www.zhihu.com/login", "") }
            assertEquals("此内容需要登录（HTTP 403）", zhihuFetchFailureMessage(login))
            val captcha = assertFailsWith<HttpStatusException> { environment.fetchJson("https://www.zhihu.com/captcha", "") }
            assertEquals("知乎要求完成安全验证（HTTP 403）", zhihuFetchFailureMessage(captcha))
            val loginFlag = assertFailsWith<HttpStatusException> { environment.fetchJson("https://www.zhihu.com/login-flag", "") }
            assertEquals("此内容需要登录（HTTP 403）", zhihuFetchFailureMessage(loginFlag))
            val loginHtml = assertFailsWith<HttpStatusException> { environment.fetchJson("https://www.zhihu.com/login-html", "") }
            assertEquals("此内容需要登录（HTTP 200）", zhihuFetchFailureMessage(loginHtml))
            assertFailsWith<HttpStatusException> { environment.signedGetText("https://www.zhihu.com/login-html") }
            assertTrue(environment.fetchJson("https://www.zhihu.com/public-json", "")!!.containsKey("data"), "正常内容引用登录提示不能被当作登录门")
            assertTrue(environment.signedGetText("https://www.zhihu.com/quoted-html").contains("RichText"), "真实网页的用户正文或脚本引用不能触发登录门")
            val unauthorized = assertFailsWith<HttpStatusException> { environment.fetchJson("https://www.zhihu.com/unauthorized", "") }
            assertEquals("请求失败：HTTP 401 Unauthorized", zhihuFetchFailureMessage(unauthorized))
            val invalid = assertFailsWith<SerializationException> { environment.fetchJson("https://www.zhihu.com/invalid", "") }
            assertTrue("HTTP 200" in invalid.message.orEmpty())
            assertFalse("403" in zhihuFetchFailureMessage(invalid))
            val transport = assertFailsWith<java.io.IOException> { environment.fetchJson("https://www.zhihu.com/transport", "") }
            val rootCause = generateSequence<Throwable>(transport) { it.cause }.last()
            kotlin.test.assertSame(transportFailure, rootCause)
        } finally {
            client.close()
            engine.close()
        }
    }

    @Test
    fun authenticatedJsonFailureDoesNotLogCredentials() = runBlocking {
        val output = ByteArrayOutputStream()
        val originalOutput = System.out
        val engine = MockEngine {
            respond(
                """{"error":{"message":"fixture-body-credential"}}""",
                HttpStatusCode.Forbidden,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            installZhihuCommonClientConfig(mutableMapOf(), "diagnostic-test")
        }
        val environment = object : ZhihuApiEnvironment {
            override fun httpClient() = client

            override fun authenticatedCookies() = emptyMap<String, String>()

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
        }
        try {
            PrintStream(output, true, "UTF-8").use { captured ->
                try {
                    System.setOut(captured)
                    val error = assertFailsWith<HttpStatusException> {
                        environment.fetchJson(
                            "https://fixture-user:fixture-password@www.zhihu.com/private?token=fixture-query#fixture-fragment",
                            "",
                        )
                    }
                    assertEquals(HttpStatusCode.Forbidden, error.status)
                    println(zhihuFetchFailureMessage(error))
                } finally {
                    System.setOut(originalOutput)
                }
            }
            val logged = output.toString("UTF-8")
            assertTrue("403" in logged, "保留状态码以诊断失败")
            assertFalse("fixture-" in logged, "地址凭据、查询参数与响应正文不得进入备用日志")
        } finally {
            System.setOut(originalOutput)
            client.close()
            engine.close()
        }
    }
}
