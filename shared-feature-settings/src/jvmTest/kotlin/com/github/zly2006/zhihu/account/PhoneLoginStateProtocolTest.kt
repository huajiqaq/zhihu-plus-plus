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

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.github.zly2006.zhihu.util.ZhihuMessageBodyEncryptor
import com.github.zly2006.zhihu.util.hmacSha1Hex
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

class PhoneLoginStateProtocolTest {
    private lateinit var directory: java.nio.file.Path
    private var engine: MockEngine? = null
    private var fixtureState: PhoneLoginState? = null

    @BeforeTest
    fun createAccountDirectory() {
        directory = Files.createTempDirectory("phone-login-account-")
    }

    @AfterTest
    fun closeFixture() {
        try {
            fixtureState?.close()
        } finally {
            fixtureState = null
            try {
                engine?.close()
            } finally {
                engine = null
                check(directory.toFile().deleteRecursively())
            }
        }
    }

    @Test
    fun completePhoneLoginKeepsPreinitializedDeviceCookieInReturnedSession() = runTest {
        val cases = listOf(
            Triple(PhoneLoginCountry("+86", "中国", "CN"), "+8613800138000", "%2B8613800138000"),
            Triple(PhoneLoginCountry("+1-242", "巴哈马", "BS"), "5552671", "%2B12425552671"),
        )
        for ((country, phoneNumber, encodedUsername) in cases) {
            val cookies = mutableMapOf<String, String>()
            var requestIndex = 0
            var signingStartedAt = 0L
            var signedAt = 0L
            val client = phoneLoginTestClient(
                MockEngine { request ->
                    requestIndex++
                    when (requestIndex) {
                        1 -> respond(
                            content = "web-device-id",
                            headers = headersOf(HttpHeaders.SetCookie, "d_c0=device-cookie; Path=/; Domain=zhihu.com"),
                        )

                        2 -> respondJson(
                            """
                            {
                              "udid": "device-id",
                              "guest": {
                                "access_token": "guest-access",
                                "token_type": "bearer",
                                "cookie": {"q_c0": "guest-cookie"}
                              }
                            }
                            """.trimIndent(),
                        )

                        3 -> respondJson("""{"show_captcha":false}""")

                        4 -> {
                            assertEncryptedBody(
                                "username=$encodedUsername&client_id=8d5227e0aaaa4797a763ac64e0c3b8",
                                request.body,
                            )
                            respondJson("""{"success":true}""")
                        }

                        5 -> {
                            assertEquals(HttpMethod.Post, request.method)
                            assertEquals("/api/account/prod/sign_in", request.url.encodedPath)
                            assertEquals("bearer guest-access", request.headers[HttpHeaders.Authorization])
                            assertTrue(request.headers[HttpHeaders.Cookie].orEmpty().contains("q_c0=guest-cookie"))
                            assertTrue(
                                request.headers[HttpHeaders.Cookie]
                                    .orEmpty()
                                    .contains("d_c0=")
                                    .not(),
                            )
                            val body = (request.body as TextContent).text
                            signedAt = (signingStartedAt..Clock.System.now().epochSeconds).firstOrNull { timestamp ->
                                val signature = hmacSha1Hex(
                                    "ecbefbf6b17e47ecb9035107866380",
                                    "digits8d5227e0aaaa4797a763ac64e0c3b8com.zhihu.android$timestamp",
                                )
                                val expected = "client_id=8d5227e0aaaa4797a763ac64e0c3b8&digits=123456&grant_type=digits&" +
                                    "signature=$signature&source=com.zhihu.android&timestamp=$timestamp&username=$encodedUsername"
                                body == ZhihuMessageBodyEncryptor.encrypt(expected)
                            } ?: error("登录密文须包含本次请求时间、签名与完整表单字段")
                            respondJson(
                                """
                                {
                                  "access_token": "account-access",
                                  "refresh_token": "account-refresh",
                                  "token_type": "bearer",
                                  "expires_in": 3600,
                                  "cookie": {
                                    "q_c0": "account-q-cookie",
                                    "z_c0": "account-z-cookie"
                                  }
                                }
                                """.trimIndent(),
                            )
                        }

                        6 -> {
                            assertEquals("/api/v4/me", request.url.encodedPath)
                            assertTrue(request.headers[HttpHeaders.Cookie].orEmpty().contains("z_c0=account-z-cookie"))
                            respondJson("""{"id":"phone-account","name":"已验证账号","url_token":"phone-account","user_type":"people"}""")
                        }
                        else -> error("不应发起第 $requestIndex 个请求")
                    }
                },
                cookies,
            )
            client.selectCountry(country)
            val otherNumber = phoneNumber.dropLast(1) + "2"
            client.phoneNumber.setTextAndPlaceCursorAtEnd(otherNumber)
            client.phoneNumber.setTextAndPlaceCursorAtEnd(phoneNumber)
            client.requestDigits()
            assertTrue(client.hasRequestedDigits)
            client.digits.setTextAndPlaceCursorAtEnd("123456")
            val accountDirectory = Files.createDirectory(directory.resolve(country.abbr))
            val repository = ZhihuAccountRepository(Path(accountDirectory.resolve("account.json").toString()))
            val accountStore = ZhihuAccountStore(repository, checkNotNull(engine))
            try {
                signingStartedAt = Clock.System.now().epochSeconds
                assertEquals("已验证账号", client.signIn(accountStore))
                assertEquals(null, client.errorMessage)
                val token = accountStore.session
                assertEquals("account-access", token.mobileAccessToken)
                assertEquals("account-refresh", token.mobileRefreshToken)
                assertEquals("bearer", token.mobileTokenType)
                assertEquals(signedAt + 3_600L, token.mobileTokenExpiresAt)
                assertEquals("device-cookie", token.cookies["d_c0"])
                assertTrue(cookies.isEmpty(), "登录不得修改调用方 Cookie 快照")
                assertEquals("account-z-cookie", token.cookies["z_c0"])
                assertEquals("account-q-cookie", token.cookies["q_c0"])
                assertEquals(accountStore.accountsState.value, repository.loadAccounts())
                client.agreementAccepted = true
                assertTrue(client.canSignIn)
                client.phoneNumber.undoState.undo()
                assertEquals(otherNumber, client.phoneNumber.text.toString())
                assertFalse(client.hasRequestedDigits)
                assertFalse(client.canSignIn)
                assertEquals(null, client.signIn(accountStore), "不能用前一号码的短信码提交")
                assertEquals(6, requestIndex)
            } finally {
                try {
                    client.close()
                } finally {
                    accountStore.close()
                }
            }
        }
    }

    private fun phoneLoginTestClient(
        engine: MockEngine,
        cookies: MutableMap<String, String>,
    ): PhoneLoginState {
        this.engine = engine
        return PhoneLoginState(engine, initialCookies = cookies).also { state ->
            fixtureState = state
        }
    }

    private fun MockRequestHandleScope.respondJson(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(
        content = body,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private fun assertEncryptedBody(
        expectedPlainText: String,
        body: Any,
    ) {
        assertEquals(
            ZhihuMessageBodyEncryptor.encrypt(expectedPlainText),
            (body as TextContent).text,
        )
    }
}
