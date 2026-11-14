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

import com.github.zly2006.zhihu.util.ZhihuMessageBodyEncryptor
import com.github.zly2006.zhihu.util.hmacSha1Hex
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.content.TextContent
import io.ktor.http.formUrlEncode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

class PhoneLoginStateProtocolTest {
    private lateinit var directory: java.nio.file.Path
    private lateinit var expectedDeviceForm: String
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
    fun guestCaptchaCheckAndDigitsRequestMatchOfficialProtocol() = runTest {
        val cookies = mutableMapOf<String, String>()
        var requestIndex = 0
        val client = phoneLoginTestClient(
            MockEngine { request ->
                requestIndex++
                when (requestIndex) {
                    1 -> {
                        assertEquals(HttpMethod.Post, request.method)
                        assertEquals("www.zhihu.com", request.url.host)
                        assertEquals("/udid", request.url.encodedPath)
                        assertEquals("101_3_3.0", request.headers["x-zse-93"])
                        assertTrue(request.headers["x-zse-96"].orEmpty().startsWith("2.0_"))
                        assertEquals(0, request.body.toByteArray().size)
                        respond(
                            content = "web-device-id",
                            headers = headersOf(
                                HttpHeaders.SetCookie,
                                "d_c0=web-device-cookie; Path=/; Domain=zhihu.com",
                            ),
                        )
                    }

                    2 -> {
                        assertEquals(HttpMethod.Post, request.method)
                        assertEquals("/api/account/prod/init/udid_guest", request.url.encodedPath)
                        assertTrue(
                            request.headers[HttpHeaders.Cookie]
                                .orEmpty()
                                .contains("d_c0=")
                                .not(),
                        )
                        assertTrue(request.headers[HttpHeaders.Authorization].orEmpty().startsWith("oauth "))
                        assertEquals("1355", request.headers["x-app-id"])
                        assertEquals("2", request.headers["x-sign-version"])
                        val timestamp = checkNotNull(request.headers["x-req-ts"])
                        assertTrue(timestamp.toLong() > 0)
                        assertEquals(
                            hmacSha1Hex("dd49a835-56e7-4a0f-95b5-efd51ea5397f", "13552$expectedDeviceForm$timestamp"),
                            request.headers["x-req-signature"],
                        )
                        assertEquals("11.4.0", request.headers["x-app-version"])
                        assertEquals("44", request.headers["x-page-id"])
                        assertEquals("101_1_1.0", request.headers["x-zse-93"])
                        assertTrue(
                            request.headers[HttpHeaders.UserAgent]
                                .orEmpty()
                                .contains("Android SDK built for arm64"),
                        )
                        assertTrue(request.headers["x-app-za"].orEmpty().contains("Width=1080&Height=2274"))
                        assertEncryptedBody(expectedDeviceForm, request.body)
                        respondJson(
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
                    }

                    3 -> {
                        assertEquals(HttpMethod.Get, request.method)
                        assertEquals("/captcha", request.url.encodedPath)
                        assertEquals("bearer guest-access", request.headers[HttpHeaders.Authorization])
                        assertEquals("device-id", request.headers["x-udid"])
                        assertTrue(request.headers[HttpHeaders.Cookie].orEmpty().contains("q_c0=guest-cookie"))
                        respondJson("""{"show_captcha":false}""")
                    }

                    4 -> {
                        assertEquals(HttpMethod.Post, request.method)
                        assertEquals("/api/account/prod/auth/digits", request.url.encodedPath)
                        assertEquals("bearer guest-access", request.headers[HttpHeaders.Authorization])
                        assertEncryptedBody(
                            "username=%2B8613800138000&client_id=8d5227e0aaaa4797a763ac64e0c3b8",
                            request.body,
                        )
                        respondJson("""{"success":true}""")
                    }

                    else -> error("Unexpected request #$requestIndex")
                }
            },
            cookies,
        )
        client.updatePhoneNumber("138 0013 8000")
        client.requestDigits()

        assertTrue(client.hasRequestedDigits)
        assertEquals(null, client.errorMessage)
        assertTrue(cookies.isEmpty(), "初始化不得修改调用方的 Cookie 快照")
        assertEquals(4, requestIndex)
        client.close()
    }

    @Test
    fun restoredVerificationCookiesReachMobileRequestsWithoutWebDeviceCookie() = runTest {
        val initialCookies = mapOf("d_c0" to "fixture-device", "__zse_ck" to "fixture-proof")
        var requests = 0
        val mock = MockEngine { request ->
            requests++
            assertTrue("__zse_ck=fixture-proof" in request.headers[HttpHeaders.Cookie].orEmpty())
            assertTrue("d_c0=" !in request.headers[HttpHeaders.Cookie].orEmpty(), "网页设备Cookie仍不得混入移动初始化")
            when (requests) {
                1 -> {
                    assertEquals("/api/account/prod/init/udid_guest", request.url.encodedPath)
                    respondJson(INITIALIZATION_RESPONSE)
                }
                2 -> respondJson("""{"show_captcha":false}""")
                3 -> respondJson("""{"success":true}""")
                else -> error("不应自动重试")
            }
        }
        engine = mock
        val state = PhoneLoginState(mock, initialCookies = initialCookies)
        try {
            state.updatePhoneNumber("13800138000")
            state.requestDigits()
            assertEquals(null, state.errorMessage)
            assertTrue(state.hasRequestedDigits)
            assertEquals(3, requests)
            assertEquals("fixture-device", initialCookies["d_c0"], "不能修改权威持久化快照")
        } finally {
            state.close()
        }
    }

    @Test
    fun captchaBranchLoadsImageAndVerifiesUserInputBeforeSendingDigits() = runTest {
        val cookies = mutableMapOf("d_c0" to "device-cookie")
        var requestIndex = 0
        val client = phoneLoginTestClient(
            MockEngine { request ->
                requestIndex++
                when (requestIndex) {
                    1 -> respondJson(INITIALIZATION_RESPONSE)

                    2 -> {
                        assertEquals(HttpMethod.Get, request.method)
                        assertEquals("/captcha", request.url.encodedPath)
                        respondJson("""{"show_captcha":true}""")
                    }

                    3 -> {
                        assertEquals(HttpMethod.Put, request.method)
                        assertEquals("/captcha", request.url.encodedPath)
                        respondJson("""{"show_captcha":true,"img_base64":"image-data"}""")
                    }

                    4 -> {
                        assertEquals(HttpMethod.Post, request.method)
                        assertEquals("/captcha", request.url.encodedPath)
                        assertEncryptedBody("input_text=a7Bc", request.body)
                        respondJson("""{"success":true}""")
                    }
                    5 -> respondJson("""{"show_captcha":false}""")
                    6 -> {
                        assertEquals("/api/account/prod/auth/digits", request.url.encodedPath)
                        respondJson("""{"success":true}""")
                    }

                    else -> error("Unexpected request #$requestIndex")
                }
            },
            cookies,
        )
        client.updatePhoneNumber("13800138000")
        client.requestDigits()
        assertEquals("image-data", client.captchaImageBase64)
        assertTrue(client.captchaRequired)
        client.captchaInput = " a7Bc "
        client.verifyCaptchaAndRequestDigits()
        assertTrue(client.hasRequestedDigits)
        assertEquals(null, client.errorMessage)

        assertEquals(6, requestIndex)
        client.close()
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
                        else -> error("Unexpected request #$requestIndex")
                    }
                },
                cookies,
            )
            client.selectCountry(country)
            client.updatePhoneNumber(phoneNumber)
            client.requestDigits()
            assertTrue(client.hasRequestedDigits)
            client.updateDigits("123456")
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

    @Test
    fun loginStopsBeforeSmsWhenUdidEndpointDoesNotSetDeviceCookie() = runTest {
        val cookies = mutableMapOf<String, String>()
        var requestIndex = 0
        val client = phoneLoginTestClient(
            MockEngine { request ->
                requestIndex++
                assertEquals(HttpMethod.Post, request.method)
                assertEquals("/udid", request.url.encodedPath)
                respond(content = "web-device-id")
            },
            cookies,
        )
        client.updatePhoneNumber("13800138000")
        client.requestDigits()

        assertEquals("服务器未返回必要的 Cookie d_c0", client.errorMessage)
        assertEquals(1, requestIndex)
        client.close()
    }

    @Test
    fun exposesServerMessageForInvalidPhoneNumber() = runTest {
        val cookies = mutableMapOf("d_c0" to "device-cookie")
        var requestIndex = 0
        val client = phoneLoginTestClient(
            MockEngine { request ->
                requestIndex++
                when (requestIndex) {
                    1 -> respondJson(INITIALIZATION_RESPONSE)
                    2 -> respondJson("""{"show_captcha":false}""")
                    3 -> respondJson(
                        body =
                            """
                            {
                              "error": {
                                "code": 100030,
                                "name": "ERR_BAD_PHONE_NO_FORMAT",
                                "message": "手机号格式错误"
                              }
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.BadRequest,
                    )

                    else -> error("Unexpected request #$requestIndex")
                }
            },
            cookies,
        )
        client.updatePhoneNumber("13800138000")
        client.requestDigits()

        assertTrue(client.errorMessage.orEmpty().contains("手机号格式错误"))
        client.close()
    }

    @Test
    fun ticketNeededErrorRechecksCaptchaAndRetriesDigitsOnce() = runTest {
        val cookies = mutableMapOf("d_c0" to "device-cookie")
        var requestIndex = 0
        val client = phoneLoginTestClient(
            MockEngine { request ->
                requestIndex++
                when (requestIndex) {
                    1 -> respondJson(INITIALIZATION_RESPONSE)
                    2 -> respondJson("""{"show_captcha":false}""")
                    3, 5 -> {
                        assertEquals("/api/account/prod/auth/digits", request.url.encodedPath)
                        assertEncryptedBody(
                            "username=%2B442079460018&client_id=8d5227e0aaaa4797a763ac64e0c3b8",
                            request.body,
                        )
                        if (requestIndex == 3) {
                            respondJson(
                                body = """{"error":{"code":120005,"message":"需要重新检查验证码"}}""",
                                status = HttpStatusCode.BadRequest,
                            )
                        } else {
                            respondJson("""{"success":true}""")
                        }
                    }

                    4 -> {
                        assertEquals(HttpMethod.Get, request.method)
                        assertEquals("/captcha", request.url.encodedPath)
                        respondJson("""{"show_captcha":false}""")
                    }

                    else -> error("Unexpected request #$requestIndex")
                }
            },
            cookies,
        )
        client.selectCountry(PhoneLoginCountry("+44", "英国", "GB"))
        client.updatePhoneNumber("2079460018")
        client.requestDigits()
        assertTrue(client.hasRequestedDigits)
        assertEquals(null, client.errorMessage)
        assertEquals(5, requestIndex)
        client.close()
    }

    private fun phoneLoginTestClient(
        engine: MockEngine,
        cookies: MutableMap<String, String>,
    ): PhoneLoginState {
        this.engine = engine
        return PhoneLoginState(engine, initialCookies = cookies).also { state ->
            fixtureState = state
            /** 密文包含设备快照；读取本实例以校验真实请求，不覆盖平台能力或生产时钟。 */
            val device = PhoneLoginState::class.java
                .getDeclaredMethod("getDeviceInfo")
                .apply { isAccessible = true }
                .invoke(state) as ZhihuPhoneLoginDeviceInfo
            expectedDeviceForm = Parameters
                .build {
                    append("app_build", "40408")
                    append("app_install_time", device.appInstallTimeMillis.toString())
                    append("app_ticket", "interface is empty")
                    append("app_version", "11.4.0")
                    append("bt_ck", if (device.bluetoothAvailable) "1" else "0")
                    append("bundle_id", "com.zhihu.android")
                    append("cp_ct", device.cpuCount.toString())
                    append("cp_tp", device.cpuType)
                    append("cp_us", device.cpuUsage)
                    append("d_n", device.phoneModel)
                    append("fr_mem", device.freeMemoryMegabytes.toString())
                    append("fr_st", device.freeStorageMegabytes.toString())
                    append("latitude", "0.0")
                    append("longitude", "0.0")
                    append("nt_st", if (device.notificationEnabled) "1" else "0")
                    append("ph_br", device.phoneBrand)
                    append("ph_md", device.phoneModel)
                    append("ph_os", "Android ${device.androidRelease}")
                    append("pre_install", "InterfaceIsNull")
                    append("tt_mem", device.totalMemoryMegabytes.toString())
                    append("tt_st", device.totalStorageMegabytes.toString())
                    append("tz_of", device.timezoneOffsetSeconds.toString())
                    append("zx_expired", "0")
                }.formUrlEncode()
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

    private companion object {
        const val INITIALIZATION_RESPONSE =
            """{"udid":"device-id","guest":{"access_token":"guest-access","token_type":"bearer"}}"""
    }
}
