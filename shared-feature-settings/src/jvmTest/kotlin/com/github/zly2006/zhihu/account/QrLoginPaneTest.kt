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

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.io.files.Path
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class QrLoginPaneTest {
    @Test
    fun malformedLoginResponseDoesNotExposeCredentialsInStatus() {
        val client = HttpClient(
            MockEngine { request ->
                respond(
                    if (request.url.encodedPath.endsWith("/qrcode")) {
                        """{"token":{},"cookie":"z_c0=private-test-value","link":"https://www.zhihu.com/test-qr"}"""
                    } else {
                        "{}"
                    },
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
        try {
            runDesktopComposeUiTest(width = 480, height = 320) {
                setContent {
                    MaterialTheme {
                        SharedQrLoginPane(
                            client = client,
                            cookies = mutableMapOf(),
                            onLoginSuccess = { false },
                            onRiskControl = { _, _ -> error("非风控夹具不能触发验证") },
                        )
                    }
                }
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodesWithText("知乎登录响应格式异常，请重试").fetchSemanticsNodes().size == 1
                }
                onNodeWithTag("qr_login_status").assertTextEquals("知乎登录响应格式异常，请重试")
            }
        } finally {
            client.close()
            client.engine.close()
        }
    }

    @Test
    fun riskControlStopsQrRequestsAndOffersNoContinueOrRefresh() {
        val directory = Files.createTempDirectory("qr-risk-control-").toFile()
        val requests = AtomicInteger()
        val callbacks = AtomicInteger()
        val cookies = mutableMapOf("d_c0" to "fixture-device")
        val engine = MockEngine { request ->
            requests.incrementAndGet()
            if (request.url.encodedPath.endsWith("/qrcode")) {
                respond(
                    """{"error":{"message":"fixture-risk","redirect":"/account/risk_control/"}}""",
                    HttpStatusCode.Forbidden,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val store = ZhihuAccountStore(ZhihuAccountRepository(Path(directory.resolve("account.json").path)), engine)
        val client = createAccountHttpClient(cookies, ZHIHU_DESKTOP_USER_AGENT, store.engine)
        try {
            runDesktopComposeUiTest(width = 480, height = 320) {
                setContent {
                    MaterialTheme {
                        SharedQrLoginPane(
                            client = client,
                            cookies = cookies,
                            generateQrBitmap = { error("拒绝获取二维码后不能生成图像") },
                            onLoginSuccess = { error("风控响应不能验证为登录成功") },
                            onRiskControl = { url, candidate ->
                                callbacks.incrementAndGet()
                                store.client.reportHumanVerification(url, candidate)
                            },
                        )
                    }
                }
                waitUntil(timeoutMillis = 10_000) {
                    callbacks.get() == 1 &&
                        onAllNodesWithText("请完成安全验证并重启应用后再扫码。").fetchSemanticsNodes().size == 1
                }
                onNodeWithTag("qr_risk_control_content").assertTextEquals("请完成安全验证并重启应用后再扫码。")
                onNodeWithTag("qr_risk_control_continue").assertDoesNotExist()
                onNodeWithTag("qr_login_retry").assertDoesNotExist()
                assertEquals(HumanVerificationPhase.PROMPT, store.client.verificationState.value.phase)
                assertEquals(ZHIHU_RISK_CONTROL_URL, store.client.verificationState.value.url)
                assertEquals(cookies, store.client.verificationState.value.candidateCookies)
                mainClock.advanceTimeBy(1_000)
                waitForIdle()
                assertEquals(1, callbacks.get(), "风控回调不能因重组重复打开验证")
                assertEquals(4, requests.get(), "预热及一次二维码拒绝后不能自动重取码或轮询")
                onNodeWithTag("qr_risk_control_continue").assertDoesNotExist()
                onNodeWithTag("qr_login_retry").assertDoesNotExist()
            }
        } finally {
            client.close()
            store.close()
            directory.deleteRecursively()
        }
    }
}
