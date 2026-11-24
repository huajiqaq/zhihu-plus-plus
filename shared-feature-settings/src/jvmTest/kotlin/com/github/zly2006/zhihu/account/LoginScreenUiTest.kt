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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.github.zly2006.zhihu.desktop.desktopZhihuDataFile
import com.github.zly2006.zhihu.platform.desktopSettingsStore
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeMode
import com.github.zly2006.zhihu.theme.ThemeSnapshot
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.ui.components.LocalAppGlassEnabled
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.io.files.Path
import java.io.File
import java.nio.file.Files
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 独立 store/MockEngine 覆盖公开登录入口，禁止回落到默认桌面账户或真实联网。 */
@OptIn(ExperimentalTestApi::class)
class LoginScreenUiTest {
    @Test
    fun welcomeBackScrollsWithContentAndNoticePressKeepsSquareCorners() = ThemeStyle.entries.forEach { skin ->
        withLoginFixture(loginEngine()) { store ->
            desktopSettingsStore().apply {
                putString("uiThemeStyle", skin.name)
                putString("themeMode", ThemeMode.LIGHT.name)
            }
            runDesktopComposeUiTest(width = 720, height = 420) {
                val glassEnabled = mutableStateOf(false)
                var backCount = 0
                setContent {
                    CompositionLocalProvider(LocalZhihuAccountStore provides store, LocalAppGlassEnabled provides glassEnabled.value) {
                        ZhihuTheme {
                            LoginScreen(onLoginComplete = {}, onOpenTelemetrySettings = {}, onBack = { backCount++ })
                        }
                    }
                }
                val scrollingContent = SemanticsMatcher("引导正文滚动区域") { node ->
                    node.config.contains(SemanticsProperties.VerticalScrollAxisRange)
                }
                onNodeWithTag("login_back").assertIsDisplayed().assert(hasAnyAncestor(scrollingContent)).performClick()
                assertEquals(1, backCount)
                onNodeWithText("开始之前").assert(hasAnyAncestor(scrollingContent))
                runOnIdle { glassEnabled.value = true }
                onNodeWithTag("login_back").assert(hasAnyAncestor(scrollingContent))

                val row = onNodeWithTag("login_notice_step_2").performScrollTo()
                val viewport = onNode(scrollingContent).fetchSemanticsNode().boundsInRoot
                val backBottom = onNodeWithTag("login_back").fetchSemanticsNode().boundsInRoot.bottom
                onNode(scrollingContent).performSemanticsAction(SemanticsActions.ScrollBy) { scroll ->
                    assertTrue(scroll(0f, backBottom - viewport.top + 1f))
                }
                onNodeWithTag("login_back").assertIsNotDisplayed()
                row.assertIsDisplayed()
                val resting = row.captureToImage().toPixelMap()
                mainClock.autoAdvance = false
                row.performTouchInput { down(center) }
                mainClock.advanceTimeBy(400)
                waitForIdle()
                val pressed = row.captureToImage().toPixelMap()
                assertEquals(resting.width, pressed.width)
                assertEquals(resting.height, pressed.height)
                for ((x, y) in listOf(2 to 2, pressed.width - 3 to 2, 2 to pressed.height - 3, pressed.width - 3 to pressed.height - 3)) {
                    val before = resting[x, y]
                    val after = pressed[x, y]
                    val difference = abs(before.red - after.red) + abs(before.green - after.green) + abs(before.blue - after.blue)
                    assertTrue(difference > 0.01f, "$skin 按压须覆盖说明行方形角落 ($x,$y)：before=$before after=$after")
                }
                row.performTouchInput { up() }
                mainClock.autoAdvance = true
                row.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已展开"))
                passWelcomeStage()
                onNodeWithTag("phone_login_phone").assertExists()
                onNodeWithText("登录后同步收藏、关注与阅读记录").assertDoesNotExist()
                val titleBounds = onNodeWithText("知乎++登录").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val backBounds = onNodeWithTag("login_back").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertTrue(titleBounds.height <= backBounds.height, "$skin 登录标题应保持紧凑行高度")
                assertTrue(titleBounds.center.y in backBounds.top..backBounds.bottom, "$skin 登录标题应与返回按钮同一行")
            }
        }
    }

    @Test
    fun guestReopensLoginWithoutWelcomeOnlyAfterExplicitConfirmation() = withLoginFixture(loginEngine()) { store ->
        val settings = desktopSettingsStore()
        settings.putBoolean("loginForRecommendation", true)
        runDesktopComposeUiTest(width = 720, height = 800) {
            val open = mutableStateOf(true)
            setContent {
                CompositionLocalProvider(LocalZhihuAccountStore provides store) {
                    ZhihuTheme {
                        if (open.value) {
                            LoginScreen(onLoginComplete = { open.value = false }, onOpenTelemetrySettings = {}, onBack = { open.value = false })
                        }
                    }
                }
            }
            readNoticesAndCheckAgreement()
            onNodeWithTag("login_notice_confirm").assertIsEnabled()
            onNodeWithTag("login_back").performScrollTo().performClick()
            onNodeWithTag("login_back").assertDoesNotExist()
            runOnIdle { open.value = true }
            onNodeWithText("已读 0 / 3 项").assertExists()
            passWelcomeStage()
            onNodeWithTag("phone_login_agreement").assertIsOff()
            onNodeWithTag("login_back").performClick()
            onNodeWithTag("login_back").assertDoesNotExist()
        }
        assertFalse(store.session.login)
        assertTrue(settings.getBoolean("loginForRecommendation", false), "普通返回不改推荐偏好")

        withLoginFixture(loginEngine(), savedSettings = desktopZhihuDataFile("settings.properties")) { restartedStore ->
            assertFalse(restartedStore.session.login)
            runDesktopComposeUiTest(width = 720, height = 800) {
                var guestExits = 0
                setContent {
                    CompositionLocalProvider(LocalZhihuAccountStore provides restartedStore) {
                        ZhihuTheme { LoginScreen(onLoginComplete = { guestExits++ }, onOpenTelemetrySettings = {}) }
                    }
                }
                onNodeWithText("开始之前").assertDoesNotExist()
                onNodeWithTag("phone_login_agreement").assertIsOff()
                onNodeWithTag("login_guest").performClick()
                assertEquals(1, guestExits)
            }
            assertFalse(desktopSettingsStore().getBoolean("loginForRecommendation", true), "显式选择先逛逛仍会关闭推荐时登录")
        }
    }

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
        readNoticesAndCheckAgreement()
        onNodeWithTag("login_notice_confirm").assertIsEnabled().performScrollTo().performClick()
    }

    private fun ComposeUiTest.readNoticesAndCheckAgreement() {
        onNodeWithTag("login_notice_confirm").assertIsNotEnabled()
        onNodeWithTag("login_notice_agreement").assertDoesNotExist()
        for (step in 1..3) {
            onNodeWithTag("login_notice_step_$step").performScrollTo().performClick()
            if (step < 3) {
                onNodeWithTag("login_notice_confirm").assertIsNotEnabled()
                onNodeWithTag("login_notice_agreement").assertDoesNotExist()
            }
        }
        onNodeWithText("已读 3 / 3 项").assertExists()
        onNodeWithTag("login_notice_agreement").assertExists()
        onNodeWithTag("login_notice_confirm").assertIsNotEnabled()
        onNodeWithText("我已阅读并同意以上说明").performScrollTo().performClick()
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

    private fun withLoginFixture(engine: MockEngine, savedSettings: File? = null, body: (ZhihuAccountStore) -> Unit) {
        val originalTheme = ThemeManager.snapshot()
        val originalHome = System.getProperty("user.home")
        val directory = Files.createTempDirectory("zhihu-login-ui-")
        try {
            System.setProperty("user.home", directory.toString())
            savedSettings?.copyTo(desktopZhihuDataFile("settings.properties"))
            ZhihuAccountStore(ZhihuAccountRepository(Path(directory.resolve("account.json").toString())), engine).use { store ->
                ThemeManager.load(ThemeSnapshot(themeStyle = ThemeStyle.MATERIAL_EXPRESSIVE))
                body(store)
            }
        } finally {
            ThemeManager.load(originalTheme)
            if (originalHome == null) System.clearProperty("user.home") else System.setProperty("user.home", originalHome)
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
