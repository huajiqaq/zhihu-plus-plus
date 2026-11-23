/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.github.zly2006.zhihu.ui

import com.github.zly2006.zhihu.util.HttpStatusException
import com.github.zly2006.zhihu.util.raiseForStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WriteOperationErrorMessageTest {
    /** 创作错误的异常和复制文案不得暴露请求头，但须保留请求方法、状态及响应原因。 */
    @Test
    fun failedRequestNeverExposesHeadersInExceptionsOrCopyableDiagnostics() = runTest {
        val secrets = listOf("fixture-cookie-secret", "fixture-bearer-secret", "fixture-xsrf-secret", "fixture-private-header")
        val client = HttpClient(
            MockEngine {
                respond(
                    content = """{"error":{"code":500,"message":"fixture upload failed"}}""",
                    status = HttpStatusCode.InternalServerError,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
        try {
            val response = client.post("https://api.zhihu.com/content/drafts?limit=20") {
                header(HttpHeaders.Cookie, "z_c0=${secrets[0]}")
                header(HttpHeaders.Authorization, "Bearer ${secrets[1]}")
                header("x-xsrftoken", secrets[2])
                header("x-private-fixture", secrets[3])
                header(HttpHeaders.UserAgent, "fixture-user-agent")
                setBody("fixture draft body")
            }
            val failure = try {
                response.raiseForStatus()
                error("夹具响应必须判为失败")
            } catch (error: HttpStatusException) {
                error
            }
            assertIs<HttpStatusException>(failure)
            secrets.forEach { secret ->
                assertFalse(failure.message.orEmpty().contains(secret), "异常消息不能包含凭据")
                assertFalse(failure.toString().contains(secret), "异常字符串不能包含凭据")
            }

            val diagnostic = buildWriteOperationErrorMessage("保存草稿失败", IllegalStateException("fixture outer failure", failure))
            assertTrue(diagnostic.contains("保存草稿失败"))
            assertTrue(diagnostic.contains("500 Internal Server Error"))
            assertTrue(diagnostic.contains("请求方法: POST"))
            assertTrue(diagnostic.contains("https://api.zhihu.com/content/drafts?limit=20"))
            assertTrue(diagnostic.contains("fixture upload failed"))
            secrets.forEach { secret -> assertFalse(diagnostic.contains(secret), "可复制的诊断文案不能包含任何请求头") }
            assertFalse(diagnostic.contains("fixture-user-agent"), "公开诊断文案也不能包含非认证请求头")
            assertFalse(diagnostic.contains("curl -X"), "不能提供携带认证信息的请求重现命令")
        } finally {
            client.close()
        }
    }

    /** 同一生产失败链中的 URL 凭据与服务端回显凭据也不能绕过公开请求地址/响应体的脱敏边界。 */
    @Test
    fun publicUrlAndResponseBodyRedactCredentialFieldsButKeepUsefulContext() = runTest {
        val secrets = listOf("fixture-url-user", "fixture-url-password", "fixture-query-token", "fixture-echo-cookie", "fixture-echo-bearer")
        val client = HttpClient(
            MockEngine {
                respond(
                    content = """{"error":{"message":"fixture rejected"},"cookie":"${secrets[3]}","nested":{"Authorization":"Bearer ${secrets[4]}"}}""",
                    status = HttpStatusCode.Forbidden,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
        try {
            val response = client.post("https://${secrets[0]}:${secrets[1]}@api.zhihu.com/content/drafts?access_token=${secrets[2]}&limit=20")
            val failure = try {
                response.raiseForStatus()
                error("夹具响应必须判为失败")
            } catch (error: HttpStatusException) {
                error
            }
            secrets.forEach { secret ->
                assertFalse(failure.message.orEmpty().contains(secret), "异常消息不能包含凭据")
                assertFalse(failure.toString().contains(secret), "异常字符串不能包含凭据")
            }
            val diagnostic = buildWriteOperationErrorMessage("保存草稿失败", failure)
            assertTrue(diagnostic.contains("api.zhihu.com/content/drafts"))
            assertTrue(diagnostic.contains("请求方法: POST"))
            assertTrue(diagnostic.contains("limit=20"))
            assertTrue(diagnostic.contains("fixture rejected"))
            secrets.forEach { secret -> assertFalse(diagnostic.contains(secret), "公开地址和响应诊断文案必须对凭据脱敏") }
        } finally {
            client.close()
        }
    }
}
