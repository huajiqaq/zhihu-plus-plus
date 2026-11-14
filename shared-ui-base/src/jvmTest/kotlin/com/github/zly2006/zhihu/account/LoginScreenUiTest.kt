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

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeSnapshot
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.ZhihuTheme
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.io.files.Path
import java.nio.file.Files
import kotlin.test.Test

/** 独立 store/MockEngine 覆盖公开登录入口，禁止回落到默认桌面账户或真实联网。 */
@OptIn(ExperimentalTestApi::class)
class LoginScreenUiTest {
    @Test
    fun switchingLoginMethodKeepsPhoneNumberAndAgreement() = withLoginFixture(loginEngine()) { store ->
        runDesktopComposeUiTest(width = 720, height = 1280) {
            setContent { CompositionLocalProvider(LocalZhihuAccountStore provides store) { ZhihuTheme { LoginScreen(onLoginComplete = {}, onOpenTelemetrySettings = {}) } } }
            passWelcomeStage()
            onNodeWithTag("phone_login_country").performClick()
            waitUntil(timeoutMillis = 5_000) {
                onAllNodes(hasTestTag("phone_login_country_GB")).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag("phone_login_country_GB").performClick()
            onNodeWithTag("phone_login_phone").performTextInput("2079460018")
            onNodeWithTag("phone_login_agreement").performClick()

            onNodeWithTag("login_mode_qr").performClick()
            onNodeWithTag("login_mode_phone").performClick()

            onNodeWithTag("phone_login_country").assert(hasText("国家或地区：英国 +44 ▾"))
            onNodeWithTag("phone_login_phone").assert(hasText("2079460018"))
            onNodeWithTag("phone_login_agreement").assertIsOn()
            onNodeWithTag("phone_login_send_digits").assertIsEnabled()
        }
    }

    @Test
    fun switchingLoginMethodKeepsSmsCooldown() = withLoginFixture(loginEngine()) { store ->
        runDesktopComposeUiTest(width = 720, height = 1280) {
            setContent { CompositionLocalProvider(LocalZhihuAccountStore provides store) { ZhihuTheme { LoginScreen(onLoginComplete = {}, onOpenTelemetrySettings = {}) } } }
            passWelcomeStage()
            onNodeWithTag("phone_login_phone").performTextInput(VALID_PHONE)
            onNodeWithTag("phone_login_agreement").performClick()
            onNodeWithTag("phone_login_send_digits").performClick()
            waitUntil(timeoutMillis = 5_000) {
                onAllNodes(hasTestTag("phone_login_send_digits") and hasCooldownText())
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }

            onNodeWithTag("login_mode_qr").performClick()
            onNodeWithTag("login_mode_phone").performClick()

            onNodeWithTag("phone_login_send_digits")
                .assert(hasCooldownText())
                .assertIsNotEnabled()
        }
    }

    @Test
    fun phoneNumberNotStartingWithOneCannotRequestSms() = withLoginFixture(loginEngine()) { store ->
        runDesktopComposeUiTest(width = 720, height = 1280) {
            setContent { CompositionLocalProvider(LocalZhihuAccountStore provides store) { ZhihuTheme { PhoneLoginPane(onLoginSuccess = {}) } } }
            onNodeWithTag("phone_login_agreement").performClick()
            onNodeWithTag("phone_login_phone").performTextInput("23800138000")

            onNodeWithTag("phone_login_send_digits").assertIsNotEnabled()
            onNodeWithText("请输入正确的中国大陆手机号").assertExists()

            onNodeWithTag("phone_login_phone").performTextReplacement(VALID_PHONE)
            onNodeWithTag("phone_login_send_digits").assertIsEnabled()
        }
    }

    @Test
    fun captchaActionsRequireAgreement() = withLoginFixture(loginEngine(captchaImages = mutableListOf(VALID_PNG_BASE64))) { store ->
        runDesktopComposeUiTest(width = 720, height = 1280) {
            setContent { CompositionLocalProvider(LocalZhihuAccountStore provides store) { ZhihuTheme { PhoneLoginPane(onLoginSuccess = {}) } } }
            requestCaptcha()
            onNodeWithTag("phone_login_captcha_input").performTextInput("a7Bc")

            onNodeWithTag("phone_login_agreement").performClick()
            onNode(hasText("换一张") and hasClickAction()).assertIsNotEnabled()
            onNodeWithTag("phone_login_verify_captcha").assertIsNotEnabled()

            onNodeWithTag("phone_login_agreement").performClick()
            onNode(hasText("换一张") and hasClickAction()).assertIsEnabled()
            onNodeWithTag("phone_login_verify_captcha").assertIsEnabled()
        }
    }

    @Test
    fun undecodableCaptchaImageExplainsWhyAndCanRetry() = withLoginFixture(
        loginEngine(captchaImages = mutableListOf("not-an-image", VALID_PNG_BASE64)),
    ) { store ->
        runDesktopComposeUiTest(width = 720, height = 1280) {
            setContent { CompositionLocalProvider(LocalZhihuAccountStore provides store) { ZhihuTheme { PhoneLoginPane(onLoginSuccess = {}) } } }
            requestCaptcha()

            onNodeWithText("图形验证码图片无法解析").assertExists()
            onNode(hasText("重新获取") and hasClickAction()).performClick()
            waitUntil(timeoutMillis = 5_000) {
                onAllNodes(hasTestTag("phone_login_captcha_image")).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test
    fun inputsAreLockedWhileSmsRequestRuns() {
        val udidGate = CompletableDeferred<Unit>()
        withLoginFixture(loginEngine(onUdid = { udidGate.await() })) { store ->
            try {
                runDesktopComposeUiTest(width = 720, height = 1280) {
                    setContent { CompositionLocalProvider(LocalZhihuAccountStore provides store) { ZhihuTheme { PhoneLoginPane(onLoginSuccess = {}) } } }
                    onNodeWithTag("phone_login_phone").performTextInput(VALID_PHONE)
                    onNodeWithTag("phone_login_agreement").performClick()
                    onNodeWithTag("phone_login_send_digits").performClick()

                    waitUntil(timeoutMillis = 5_000) {
                        onAllNodes(hasTestTag("phone_login_phone") and isNotEnabled()).fetchSemanticsNodes().isNotEmpty()
                    }
                    onNodeWithTag("phone_login_agreement").assertIsNotEnabled()

                    udidGate.complete(Unit)
                    waitUntil(timeoutMillis = 5_000) {
                        onAllNodes(hasTestTag("phone_login_phone") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
                    }
                }
            } finally {
                udidGate.complete(Unit)
            }
        }
    }

    @Test
    fun agreementNamesAreLinks() = withLoginFixture(loginEngine()) { store ->
        runDesktopComposeUiTest(width = 720, height = 1280) {
            setContent { CompositionLocalProvider(LocalZhihuAccountStore provides store) { ZhihuTheme { PhoneLoginPane(onLoginSuccess = {}) } } }
            onNode(hasText("《知乎协议》") and hasClickAction()).assertExists()
            onNode(hasText("《个人信息保护指引》") and hasClickAction()).assertExists()
        }
    }

    private fun ComposeUiTest.passWelcomeStage() {
        onNodeWithTag("login_notice_confirm").assertIsNotEnabled()
        onNodeWithTag("login_notice_agreement").assertDoesNotExist()
        for (step in 1..3) {
            onNodeWithTag("login_notice_step_$step").performScrollTo().performClick()
            if (step < 3) {
                onNodeWithTag("login_notice_confirm").assertIsNotEnabled()
                onNodeWithTag("login_notice_agreement").assertDoesNotExist()
            }
        }
        onNodeWithTag("login_notice_agreement").assertExists()
        onNodeWithTag("login_notice_confirm").assertIsNotEnabled()
        onNodeWithText("我已阅读并同意以上说明").performScrollTo().performClick()
        onNodeWithTag("login_notice_confirm").assertIsEnabled().performScrollTo().performClick()
    }

    private fun ComposeUiTest.requestCaptcha() {
        onNodeWithTag("phone_login_phone").performTextInput(VALID_PHONE)
        onNodeWithTag("phone_login_agreement").performClick()
        onNodeWithTag("phone_login_send_digits").performClick()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodes(hasTestTag("phone_login_captcha_input") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun hasCooldownText() = SemanticsMatcher("短信冷却倒计时") { node ->
        node.config
            .getOrNull(SemanticsProperties.Text)
            .orEmpty()
            .any { COOLDOWN_TEXT.matches(it.text) }
    }

    /** 扫码请求失败后停在错误态，切换方式测试不进入持续轮询。 */
    private fun loginEngine(
        captchaImages: MutableList<String> = mutableListOf(),
        onUdid: suspend () -> Unit = {},
    ) = MockEngine { request ->
        val path = request.url.encodedPath
        when {
            path == "/api/v3/oauth/sms/supported_countries" -> respondJson(
                """{"data":[{"code":"+86","name":"中国","abbr":"CN"},{"code":"+44","name":"英国","abbr":"GB"}]}""",
            )

            request.url.host == "www.zhihu.com" && path == "/udid" -> {
                onUdid()
                respond(
                    content = "fixture-udid",
                    headers = headersOf(HttpHeaders.SetCookie, "d_c0=fixture-device; Path=/; Domain=zhihu.com"),
                )
            }

            path == "/api/account/prod/init/udid_guest" -> respondJson(INITIALIZATION_RESPONSE)

            path == "/captcha" && request.method == HttpMethod.Get ->
                respondJson("""{"show_captcha":${captchaImages.isNotEmpty()}}""")

            path == "/captcha" && request.method == HttpMethod.Put ->
                respondJson("""{"show_captcha":true,"img_base64":"${captchaImages.removeAt(0)}"}""")

            path == "/api/account/prod/auth/digits" -> respondJson("""{"success":true}""")

            else -> respondJson("""{"error":{"message":"fixture unavailable"}}""", HttpStatusCode.InternalServerError)
        }
    }

    private fun MockRequestHandleScope.respondJson(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun withLoginFixture(engine: MockEngine, body: (ZhihuAccountStore) -> Unit) {
        val originalTheme = ThemeManager.snapshot()
        val directory = Files.createTempDirectory("zhihu-login-ui-")
        val store = ZhihuAccountStore(ZhihuAccountRepository(Path(directory.resolve("account.json").toString())), engine)
        try {
            ThemeManager.load(ThemeSnapshot(themeStyle = ThemeStyle.MATERIAL_EXPRESSIVE))
            body(store)
        } finally {
            ThemeManager.load(originalTheme)
            store.close()
            directory.toFile().deleteRecursively()
        }
    }

    private companion object {
        const val VALID_PHONE = "13800138000"
        val COOLDOWN_TEXT = Regex("""\d+s""")
        const val VALID_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGP4//8/AAX+Av4N70a4AAAAAElFTkSuQmCC"
        const val INITIALIZATION_RESPONSE =
            """{"udid":"device-id","guest":{"access_token":"guest-access","token_type":"bearer"}}"""
    }
}
