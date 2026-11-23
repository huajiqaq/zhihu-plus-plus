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

package com.github.zly2006.zhihu.ui.subscreens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.github.zly2006.zhihu.account.AccountVerificationDialogs
import com.github.zly2006.zhihu.account.HumanVerificationPhase
import com.github.zly2006.zhihu.account.LocalZhihuAccountStore
import com.github.zly2006.zhihu.account.SharedQrLoginPane
import com.github.zly2006.zhihu.account.ZhihuAccountProfileSnapshot
import com.github.zly2006.zhihu.account.ZhihuAccountRepository
import com.github.zly2006.zhihu.account.ZhihuAccountSession
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.account.ZhihuAccounts
import com.github.zly2006.zhihu.account.ZhihuSavedAccount
import com.github.zly2006.zhihu.account.rememberLoginHttpClient
import com.github.zly2006.zhihu.navigation.History
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.ui.ONLINE_HISTORY_OVERFLOW_TAG
import com.github.zly2006.zhihu.ui.OnlineHistoryScreen
import com.github.zly2006.zhihu.ui.components.AppOverlayHost
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.io.files.Path as KotlinxPath

@OptIn(ExperimentalTestApi::class)
class SettingsAccountWorkflowUiTest {
    @Test
    fun first403FeedbackIsAlwaysRedactedAndClearRequiresConsent() {
        for (style in ThemeStyle.entries) {
            withIsolatedAccount(MockEngine { respond("{}", HttpStatusCode.Forbidden, jsonHeaders) }) { store ->
                ThemeManager.setThemeStyle(style)
                runBlocking {
                    store.client
                        .httpClient()
                        .get("https://www.zhihu.com/api/v4/fixture?token=fixture-query") {
                            headers.append(HttpHeaders.Authorization, "Bearer fixture-auth")
                            headers.append("X-Req-Signature", "fixture-signature")
                        }.bodyAsText()
                    withTimeout(5_000) { while (store.http403Log.read() == null) delay(20) }
                }
                val first = runBlocking { checkNotNull(store.http403Log.read()) }
                runDesktopComposeUiTest(width = 720, height = 1000) {
                    setContent { ZhihuTheme { AppOverlayHost { NetworkRequestDiagnostics(store.http403Log, store.client, {}) } } }
                    waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("network_diagnostics_copy").fetchSemanticsNodes().isNotEmpty() }
                    onNodeWithText("Cookie: 已隐藏", substring = true).assertExists()
                    onNodeWithText("Authorization: 已隐藏", substring = true).assertExists()
                    onNodeWithText("X-Req-Signature: 已隐藏", substring = true).assertExists()
                    for (value in arrayOf("fixture-alice", "fixture-auth", "fixture-query", "fixture-signature")) {
                        onAllNodesWithText(value, substring = true).assertCountEquals(0)
                        assertTrue(
                            value !in Files.readString(
                                java.nio.file.Path
                                    .of(store.http403Log.file.toString()),
                            ),
                            "落盘同样不能保留敏感值",
                        )
                    }
                    onNodeWithTag("network_diagnostics_sensitive").assertDoesNotExist()
                    onNodeWithTag("network_diagnostics_copy").performScrollTo().performTouchInput { click() }
                    onNodeWithText("已复制脱敏信息").assertExists()
                    onNodeWithTag("network_diagnostics_feedback").assertExists()
                    onNodeWithTag("network_diagnostics_clear").performTouchInput { click() }
                    onNodeWithText("清空首次 403 记录？").assertIsDisplayed()
                    onNodeWithText("取消").performTouchInput { click() }
                    assertEquals(first, runBlocking { store.http403Log.read() }, "取消不得删除记录")
                    onNodeWithTag("network_diagnostics_clear").performTouchInput { click() }
                    onNode(hasText("清空记录") and hasAnyAncestor(isDialog() and hasAnyDescendant(hasText("清空首次 403 记录？"))))
                        .performTouchInput { click() }
                    waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("暂无 403 请求记录。").fetchSemanticsNodes().isNotEmpty() }
                    assertEquals(null, runBlocking { store.http403Log.read() })
                    assertTrue(
                        !Files.exists(
                            java.nio.file.Path
                                .of(store.http403Log.file.toString()),
                        ),
                    )
                }
            }
        }
    }

    @Test
    fun first403PromptDoesNotRepeatOrCoverVerification() {
        for (style in ThemeStyle.entries) {
            withIsolatedAccount(MockEngine { respond("{}", HttpStatusCode.Forbidden, jsonHeaders) }) { store ->
                ThemeManager.setThemeStyle(style)
                val client = store.client
                runBlocking {
                    client.httpClient().get("https://www.zhihu.com/fixture").bodyAsText()
                    withTimeout(5_000) { while (store.http403Log.pendingFeedback.value == null) delay(20) }
                }
                val first = checkNotNull(store.http403Log.pendingFeedback.value)
                runDesktopComposeUiTest(width = 720, height = 1000) {
                    setContent { ZhihuTheme { AppOverlayHost { AccountVerificationDialogs(client) } } }
                    onNodeWithTag("http403_feedback_prompt").assertIsDisplayed()
                    onNodeWithText("遇到 403 了").assertIsDisplayed()
                    onNodeWithText("复制脱敏信息").assertExists()
                    onNodeWithText("稍后").performTouchInput { click() }
                    runBlocking { store.http403Log.record(first.copy(id = "later")) }
                    waitForIdle()
                    onNodeWithTag("http403_feedback_prompt").assertDoesNotExist()
                    assertEquals(first, runBlocking { store.http403Log.read() })
                    runBlocking {
                        store.http403Log.clear()
                        store.http403Log.record(first)
                    }
                    client.reportHumanVerification(null)
                    waitForIdle()
                    onNodeWithTag("http403_feedback_prompt").assertDoesNotExist()
                    onNodeWithTag("human_verification_prompt").assertIsDisplayed()
                }
            }
        }
    }

    @Test
    fun verificationCannotBeSkippedAndRestartAcknowledgementDoesNotReopenIt() {
        for (style in ThemeStyle.entries) {
            withIsolatedAccount(MockEngine { respond("{}", HttpStatusCode.Forbidden, jsonHeaders) }) { store ->
                ThemeManager.setThemeStyle(style)
                val client = store.client
                runBlocking {
                    client.httpClient().get("https://www.zhihu.com/before-verification").bodyAsText()
                    withTimeout(5_000) { while (store.http403Log.read() == null) delay(20) }
                }
                val visible = mutableStateOf(true)
                client.reportHumanVerification(null)
                runDesktopComposeUiTest(width = 720, height = 1000) {
                    setContent { ZhihuTheme { AppOverlayHost { AccountVerificationDialogs(client, visible.value) } } }
                    onNodeWithTag("human_verification_prompt").assertIsDisplayed()
                    onAllNodesWithText("取消").assertCountEquals(0)
                    client.acknowledgeVerificationRestart()
                    client.save(
                        client.load().copy(
                            cookies = client
                                .load()
                                .cookies
                                .toMutableMap()
                                .apply { put("z_c0", "fixture-rotated") },
                        ),
                    )
                    waitForIdle()
                    assertEquals(HumanVerificationPhase.PROMPT, client.verificationState.value.phase)
                    onNodeWithTag("human_verification_prompt").assertIsDisplayed()
                    // 隐藏真实 WebView，只验证 Cookie 更新和提前确认不能推进完成状态。
                    visible.value = false
                    waitForIdle()
                    client.beginHumanVerification()
                    client.acknowledgeVerificationRestart()
                    client.save(
                        client.load().copy(
                            cookies = client
                                .load()
                                .cookies
                                .toMutableMap()
                                .apply { put("fixtureverification", "changed") },
                        ),
                    )
                    assertEquals(HumanVerificationPhase.VERIFYING, client.verificationState.value.phase)
                    runBlocking { client.completeHumanVerification(mapOf("fixtureverification" to "completed")) }
                    assertEquals(null, runBlocking { store.http403Log.read() })
                    assertEquals(null, store.http403Log.pendingFeedback.value)
                    client.save(
                        client.load().copy(
                            cookies = client
                                .load()
                                .cookies
                                .toMutableMap()
                                .apply { put("z_c0", "fixture-after-verification") },
                        ),
                    )
                    assertEquals(HumanVerificationPhase.RESTART_REQUIRED, client.verificationState.value.phase)
                    visible.value = true
                    waitForIdle()
                    onNodeWithTag("human_verification_restart").assertIsDisplayed()
                    onNodeWithText("知道了").performTouchInput { click() }
                    client.reportHumanVerification(null)
                    client.beginHumanVerification()
                    waitForIdle()
                    onNodeWithTag("human_verification_restart").assertDoesNotExist()
                    onNodeWithTag("human_verification_prompt").assertDoesNotExist()
                    assertEquals(HumanVerificationPhase.DISMISSED, client.verificationState.value.phase)
                }
            }
        }
    }

    @Test
    fun aNewQrAttemptBindsItsOwnCookieMap() = withIsolatedAccount(
        MockEngine {
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.SetCookie, "attempt=from-response; Path=/"),
            )
        },
    ) { store ->
        val firstCookies = mutableMapOf("attempt" to "first")
        val secondCookies = mutableMapOf("attempt" to "second")
        val currentCookies = mutableStateOf(firstCookies)
        var actualClient: HttpClient? = null
        runDesktopComposeUiTest(width = 412, height = 892) {
            setContent {
                CompositionLocalProvider(LocalZhihuAccountStore provides store) {
                    val client = rememberLoginHttpClient(currentCookies.value)
                    SideEffect { actualClient = client }
                }
            }
            waitForIdle()
            currentCookies.value = secondCookies
            waitForIdle()
            runBlocking { checkNotNull(actualClient).get("https://www.zhihu.com/") }
            assertEquals("from-response", secondCookies["attempt"])
            assertEquals("first", firstCookies["attempt"])
        }
    }

    @Test
    fun qrVerificationCannotBeReplacedByRefresh() = withIsolatedAccount(
        MockEngine { request ->
            val body = when {
                request.url.encodedPath.endsWith("/scan_info") -> """{"success":true}"""
                request.url.encodedPath.endsWith("/login/qrcode") ->
                    """{"token":"fixture-qr","link":"https://www.zhihu.com/fixture-qr","expires_at":120}"""
                else -> "{}"
            }
            respond(body, HttpStatusCode.OK, jsonHeaders)
        },
    ) { store ->
        val verificationStarted = CompletableDeferred<Unit>()
        val verificationFinished = CompletableDeferred<Boolean>()
        try {
            runDesktopComposeUiTest(width = 720, height = 892) {
                setContent {
                    CompositionLocalProvider(LocalZhihuAccountStore provides store) {
                        ZhihuTheme {
                            val cookies = remember { mutableMapOf("d_c0" to "fixture-device") }
                            val client = rememberLoginHttpClient(cookies)
                            SharedQrLoginPane(
                                client = client,
                                cookies = cookies,
                                generateQrBitmap = { ImageBitmap(1, 1) },
                                onRiskControl = { url, candidate -> store.client.reportHumanVerification(url, candidate) },
                                onLoginSuccess = {
                                    verificationStarted.complete(Unit)
                                    verificationFinished.await()
                                },
                            )
                        }
                    }
                }
                waitUntil(timeoutMillis = 5_000) { verificationStarted.isCompleted }
                onNodeWithText("正在验证登录").assertExists()
                onNodeWithText("刷新二维码").assertIsNotEnabled()
                verificationFinished.complete(false)
                waitForIdle()
            }
        } finally {
            verificationFinished.complete(false)
        }
    }

    @Test
    fun updateCheckButtonIsDisabledWhileChecking() = withIsolatedDesktopHome {
        runDesktopComposeUiTest(width = 720, height = 900) {
            setContent {
                ZhihuTheme {
                    SystemUpdateStatusCard(
                        state = SystemUpdateState.Checking,
                        currentVersion = "fixture version",
                        installSupported = false,
                        onCheck = {},
                        onDownload = {},
                        onInstall = {},
                        onSkip = {},
                        onOpenCnDownload = {},
                        onOpenUrl = {},
                    )
                }
            }
            onNodeWithText("正在检查更新").assertIsDisplayed()
            onNode(hasText("检查中...") and hasClickAction()).assertIsNotEnabled()
        }
    }

    @Test
    fun externallyChangedAccountDismissesTheOldIdentityConfirmation() = withIsolatedDesktopHome {
        val switchRequests = AtomicInteger()
        val listStarted = CompletableDeferred<Unit>()
        val finishList = CompletableDeferred<Unit>()
        val localSwitchStarted = CompletableDeferred<Unit>()
        val finishLocalSwitch = CompletableDeferred<Unit>()
        val newProfileStarted = CompletableDeferred<Unit>()
        val finishNewProfile = CompletableDeferred<Unit>()
        withIsolatedAccount(
            MockEngine { request ->
                when (request.url.encodedPath) {
                    "/people/account/list" -> {
                        if (!listStarted.isCompleted) {
                            listStarted.complete(Unit)
                            finishList.await()
                        }
                        respond("""{"data":[{"id":"fixture-other","name":"另一个身份","account_type":2}]}""", HttpStatusCode.OK, jsonHeaders)
                    }
                    "/api/v4/me" -> {
                        val cookie = request.headers[HttpHeaders.Cookie].orEmpty()
                        if ("z_c0=new-confirm-session" in cookie) {
                            newProfileStarted.complete(Unit)
                            finishNewProfile.await()
                            respond("""{"id":"fixture-alice","name":"Fixture Alice","url_token":"fixture-alice","user_type":"people"}""", HttpStatusCode.OK, jsonHeaders)
                        } else {
                            if ("z_c0=fixture-bob" in cookie) {
                                localSwitchStarted.complete(Unit)
                                finishLocalSwitch.await()
                            }
                            respond("""{"error":{"message":"fixture expired"}}""", HttpStatusCode.Unauthorized, jsonHeaders)
                        }
                    }
                    "/account/switch" -> {
                        switchRequests.incrementAndGet()
                        error("旧确认不能发送切换请求")
                    }
                    else -> error("身份确认不应请求：${request.url}")
                }
            },
            additionalAccounts = listOf(
                ZhihuSavedAccount("fixture-bob", alice.copy(username = "Fixture Bob", profile = ZhihuAccountProfileSnapshot(id = "fixture-bob", name = " "), cookies = mutableMapOf("z_c0" to "fixture-bob"))),
            ),
        ) { store ->
            runDesktopComposeUiTest(width = 720, height = 1000) {
                setContent {
                    CompositionLocalProvider(LocalZhihuAccountStore provides store, LocalNavigator provides Navigator({}, {})) {
                        ZhihuTheme { IdentityManagementScreen() }
                    }
                }
                try {
                    waitUntil(timeoutMillis = 5_000) { listStarted.isCompleted }
                    onNodeWithText("Fixture Bob").performScrollTo().assertIsEnabled()
                    onNodeWithContentDescription("移除登录账号 Fixture Bob").assertIsEnabled()
                    onNodeWithText("添加其他手机号登录账号").assertIsEnabled()
                    onNodeWithText("Fixture Bob").performClick()
                    onNodeWithText("切换").performClick()
                    waitUntil(timeoutMillis = 5_000) { localSwitchStarted.isCompleted }
                    onNodeWithText("添加其他手机号登录账号").assertIsNotEnabled()
                    finishList.complete(Unit)
                    waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("另一个身份").fetchSemanticsNodes().isNotEmpty() }
                    onNodeWithText("另一个身份").assertIsNotEnabled()
                    onNodeWithContentDescription("移除登录账号 Fixture Bob").assertIsNotEnabled()
                    finishLocalSwitch.complete(Unit)
                    waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("登录凭据已失效", substring = true).fetchSemanticsNodes().isNotEmpty() }
                    onNodeWithText("另一个身份").assertIsEnabled()
                } finally {
                    finishList.complete(Unit)
                    finishLocalSwitch.complete(Unit)
                }
                onNodeWithText("另一个身份").performScrollTo().performClick()
                onNodeWithText("切换账号").assertIsDisplayed()
                runOnIdle { store.replaceSession(store.session.copy(cookies = mutableMapOf("z_c0" to "replacement-session"))) }
                waitForIdle()
                onAllNodesWithText("切换账号").assertCountEquals(0)
                assertEquals(0, switchRequests.get())
                assertEquals("replacement-session", store.session.cookies["z_c0"])
            }
            runDesktopComposeUiTest(width = 720, height = 1000) {
                setContent {
                    CompositionLocalProvider(LocalZhihuAccountStore provides store, LocalNavigator provides Navigator({}, {})) {
                        ZhihuTheme {
                            com.github.zly2006.zhihu.ui.AccountSettingScreen(
                                innerPadding = androidx.compose.foundation.layout
                                    .PaddingValues(0.dp),
                            )
                        }
                    }
                }
                try {
                    waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(com.github.zly2006.zhihu.ui.ACCOUNT_SETTINGS_PROFILE_RETRY_TAG).fetchSemanticsNodes().isNotEmpty() }
                    onNodeWithContentDescription("退出登录").performScrollTo().performClick()
                    onNodeWithText("退出登录").assertIsDisplayed()
                    val accountsBefore = store.accountsState.value
                    val clientBefore = store.client
                    val accountFile = java.nio.file.Path
                        .of(store.http403Log.file.toString())
                        .parent.parent
                        .resolve("fixture-account.json")
                    val persistedBytes = Files.readAllBytes(accountFile)
                    val temporary = Files.createDirectory(accountFile.resolveSibling("fixture-account.json.tmp"))
                    val blocker = Files.writeString(temporary.resolve("blocker"), "keep")
                    try {
                        onNodeWithText("退出", substring = false).performClick()
                        waitForIdle()
                        onNodeWithText("退出失败：", substring = true).assertIsDisplayed()
                        assertTrue(accountsBefore === store.accountsState.value)
                        assertTrue(clientBefore !== store.client)
                        assertContentEquals(persistedBytes, Files.readAllBytes(accountFile))
                    } finally {
                        Files.deleteIfExists(blocker)
                        Files.deleteIfExists(temporary)
                    }
                    runOnIdle { store.replaceSession(store.session.copy(cookies = mutableMapOf("z_c0" to "new-confirm-session"))) }
                    waitUntil(timeoutMillis = 5_000) { newProfileStarted.isCompleted }
                    onNodeWithText("退出登录").assertDoesNotExist()
                    onNodeWithTag(com.github.zly2006.zhihu.ui.ACCOUNT_SETTINGS_PROFILE_RETRY_TAG).assertDoesNotExist()
                    assertEquals("new-confirm-session", store.session.cookies["z_c0"])
                    finishNewProfile.complete(Unit)
                    waitForIdle()
                } finally {
                    finishNewProfile.complete(Unit)
                }
            }
        }
    }

    @Test
    fun successfulRemoteSwitchUsesTheStoreEngineAndVerifiedCandidateCredentials() = withIsolatedDesktopHome {
        val switchRequests = AtomicInteger()
        val profileRequests = AtomicInteger()
        val switchStarted = CompletableDeferred<Unit>()
        val finishRejectedSwitch = CompletableDeferred<Unit>()
        val rejectSwitch = AtomicBoolean(true)
        val backs = AtomicInteger()
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/people/account/list" -> respond(
                    """{"data":[{"id":"fixture-other","name":"另一个身份","account_type":2}]}""",
                    HttpStatusCode.OK,
                    jsonHeaders,
                )
                "/account/switch" -> {
                    switchRequests.incrementAndGet()
                    assertTrue("z_c0=fixture-alice" in request.headers[HttpHeaders.Cookie].orEmpty())
                    assertTrue((request.body as io.ktor.http.content.TextContent).text.contains("fixture-other"))
                    if (rejectSwitch.get()) {
                        switchStarted.complete(Unit)
                        finishRejectedSwitch.await()
                        return@MockEngine respond("""{"error":{"message":"fixture denied"}}""", HttpStatusCode.Forbidden, jsonHeaders)
                    }
                    respond("""{"access_token":"candidate-access","refresh_token":"candidate-refresh","token_type":"bearer","cookie":{"z_c0":"candidate-session","d_c0":"candidate-device"},"expires_at":1700003600}""", HttpStatusCode.OK, jsonHeaders)
                }
                "/people/self" -> {
                    profileRequests.incrementAndGet()
                    assertEquals("bearer candidate-access", request.headers[HttpHeaders.Authorization])
                    assertTrue("z_c0=candidate-session" in request.headers[HttpHeaders.Cookie].orEmpty())
                    assertTrue("d_c0=candidate-device" in request.headers[HttpHeaders.Cookie].orEmpty())
                    respond("""{"id":"fixture-other","name":"另一个身份","url_token":"fixture-other","user_type":"people"}""", HttpStatusCode.OK, jsonHeaders)
                }
                else -> error("身份切换不应请求：${request.url}")
            }
        }
        withIsolatedAccount(
            engine,
            additionalAccounts = listOf(
                ZhihuSavedAccount("fixture-bob", alice.copy(username = "Fixture Bob", profile = ZhihuAccountProfileSnapshot(id = "fixture-bob", name = "Fixture Bob"))),
            ),
        ) { store ->
            val oldClient = store.client
            val oldSession = store.session
            val accountFile = java.nio.file.Path
                .of(store.http403Log.file.toString())
                .parent.parent
                .resolve("fixture-account.json")
            val originalBytes = Files.readAllBytes(accountFile)
            runDesktopComposeUiTest(width = 720, height = 1000) {
                setContent {
                    CompositionLocalProvider(LocalZhihuAccountStore provides store, LocalNavigator provides Navigator({}, { backs.incrementAndGet() })) {
                        ZhihuTheme { IdentityManagementScreen() }
                    }
                }
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("另一个身份").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithText("另一个身份").performClick()
                onNodeWithText("切换").performClick()
                waitUntil(timeoutMillis = 5_000) { switchStarted.isCompleted }
                onNodeWithText("另一个身份").assertIsNotEnabled()
                onNodeWithText("Fixture Bob").assertIsNotEnabled()
                onNodeWithContentDescription("移除登录账号 Fixture Bob").assertIsNotEnabled()
                onNodeWithText("添加其他手机号登录账号").assertIsNotEnabled()
                finishRejectedSwitch.complete(Unit)
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("fixture denied", substring = true).fetchSemanticsNodes().isNotEmpty() }
                onNodeWithText("加载失败").assertDoesNotExist()
                assertTrue(oldClient === store.client)
                assertTrue(oldSession === store.session)
                assertContentEquals(originalBytes, Files.readAllBytes(accountFile))
                assertEquals(0, backs.get())
                rejectSwitch.set(false)
                onNodeWithText("另一个身份").performClick()
                onNodeWithText("切换").performClick()
                waitUntil(timeoutMillis = 5_000) { backs.get() == 1 }
                assertEquals(2, switchRequests.get())
                assertEquals(1, profileRequests.get())
                assertEquals("fixture-other", store.session.profile?.id)
                assertEquals("candidate-session", store.session.cookies["z_c0"])
                assertEquals("candidate-refresh", store.session.mobileRefreshToken)
                assertTrue(oldClient !== store.client)
                assertTrue(engine === store.engine)
                assertTrue(engine === store.client.httpClient().engine)
                assertTrue(engine.coroutineContext[kotlinx.coroutines.Job]!!.isActive)
            }
        }
    }

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Test
    fun profileRetryAndCancelledLogoutKeepTheAccountSheet() = withIsolatedDesktopHome {
        val requests = AtomicInteger()
        val retryStarted = CompletableDeferred<Unit>()
        val finishRetry = CompletableDeferred<Unit>()
        val dismissals = AtomicInteger()
        withIsolatedAccount(
            MockEngine { request ->
                assertEquals("/api/v4/me", request.url.encodedPath)
                if (requests.incrementAndGet() == 1) {
                    respond("{}", HttpStatusCode.OK, jsonHeaders)
                } else {
                    retryStarted.complete(Unit)
                    finishRetry.await()
                    respond(
                        """{"id":"fixture-alice","name":"Updated Fixture Alice","url_token":"fixture-alice","user_type":"people"}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                }
            },
        ) { store ->
            val sheetVisible = mutableStateOf(true)
            val owner = store.client
            val accountFile = java.nio.file.Path
                .of(store.http403Log.file.toString())
                .parent.parent
                .resolve("fixture-account.json")
            try {
                runDesktopComposeUiTest(width = 720, height = 1000) {
                    setContent {
                        CompositionLocalProvider(LocalZhihuAccountStore provides store, LocalNavigator provides Navigator({}, {})) {
                            ZhihuTheme {
                                if (sheetVisible.value) {
                                    com.github.zly2006.zhihu.ui.components.AppBottomSheet(
                                        onDismissRequest = {
                                            dismissals.incrementAndGet()
                                            sheetVisible.value = false
                                        },
                                    ) {
                                        com.github.zly2006.zhihu.ui.AccountSettingScreen(
                                            innerPadding = androidx.compose.foundation.layout
                                                .PaddingValues(0.dp),
                                            onDismissRequest = {
                                                dismissals.incrementAndGet()
                                                sheetVisible.value = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    val retry = com.github.zly2006.zhihu.ui.ACCOUNT_SETTINGS_PROFILE_RETRY_TAG
                    waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(retry).fetchSemanticsNodes().isNotEmpty() }
                    onNodeWithTag(retry).assertIsDisplayed().assertIsEnabled().performClick()
                    waitUntil(timeoutMillis = 5_000) { retryStarted.isCompleted }
                    onNodeWithTag(retry).assertIsNotEnabled()
                    finishRetry.complete(Unit)
                    waitUntil(timeoutMillis = 5_000) {
                        onAllNodesWithText("Updated Fixture Alice").fetchSemanticsNodes().isNotEmpty()
                    }
                    onNodeWithTag(retry).assertDoesNotExist()
                    assertEquals(2, requests.get())
                    assertTrue(owner === store.client)
                    assertEquals("Updated Fixture Alice", store.session.profile?.name)
                    val accountsBefore = store.accountsState.value
                    val persistedBytes = Files.readAllBytes(accountFile)
                    onNodeWithContentDescription("退出登录").performScrollTo().performClick()
                    onNodeWithText("退出登录").assertIsDisplayed()
                    onNode(hasText("取消") and hasAnyAncestor(isDialog())).performClick()
                    onNodeWithText("退出登录").assertDoesNotExist()
                    onNodeWithText("Updated Fixture Alice").assertIsDisplayed()
                    assertEquals(0, dismissals.get())
                    assertTrue(sheetVisible.value)
                    assertTrue(accountsBefore === store.accountsState.value)
                    assertTrue(owner === store.client)
                    assertContentEquals(persistedBytes, Files.readAllBytes(accountFile))
                }
            } finally {
                finishRetry.complete(Unit)
            }
        }
    }

    @Test
    fun guestHistoryOffersLoginAndLocalHistory() = withIsolatedDesktopHome {
        val requestedPaths = CopyOnWriteArrayList<String>()
        withIsolatedAccount(
            MockEngine { request ->
                requestedPaths += request.url.encodedPath
                respond("{}", HttpStatusCode.OK, jsonHeaders)
            },
        ) { store ->
            store.replaceSession(ZhihuAccountSession())
            com.github.zly2006.zhihu.platform
                .desktopSettingsStore()
                .putStringSet(BOTTOM_BAR_ITEMS_PREFERENCE_KEY, setOf("Home", "Follow", "Daily"))
            val destinations = CopyOnWriteArrayList<NavDestination>()
            runDesktopComposeUiTest(width = 412, height = 892) {
                setContent {
                    CompositionLocalProvider(LocalZhihuAccountStore provides store, LocalNavigator provides Navigator({ destinations += it }, {})) {
                        ZhihuTheme {
                            com.github.zly2006.zhihu.ui.AccountSettingScreen(
                                innerPadding = androidx.compose.foundation.layout
                                    .PaddingValues(0.dp),
                                refreshAccountProfileOnEnter = false,
                            )
                        }
                    }
                }
                for (title in listOf("账号与安全", "显示与阅读", "内容与隐私", "系统", "关于")) {
                    onNodeWithText(title).assertExists()
                }
                onNodeWithTag(com.github.zly2006.zhihu.ui.ACCOUNT_SETTINGS_SHORTCUT_HISTORY_TAG).performScrollTo().performClick()
                onNodeWithTag(com.github.zly2006.zhihu.ui.ACCOUNT_SETTINGS_DEVELOPER_TAG).performScrollTo().performClick()
                onNodeWithText("通知设置").performScrollTo().performClick()
                assertEquals(
                    listOf(
                        com.github.zly2006.zhihu.navigation.OnlineHistoryRoute,
                        com.github.zly2006.zhihu.navigation.Account.DeveloperSettings,
                        com.github.zly2006.zhihu.navigation.Notification
                            .NotificationSettings(),
                    ),
                    destinations.toList(),
                )
            }
            destinations.clear()
            val owner = testViewModelStoreOwner()
            try {
                runDesktopComposeUiTest(width = 412, height = 892) {
                    setContent {
                        CompositionLocalProvider(LocalZhihuAccountStore provides store) {
                            ZhihuTheme {
                                CompositionLocalProvider(
                                    LocalNavigator provides Navigator({ destinations += it }, {}),
                                    LocalViewModelStoreOwner provides owner,
                                ) {
                                    OnlineHistoryScreen()
                                }
                            }
                        }
                    }
                    waitUntil(timeoutMillis = 5_000) {
                        onAllNodesWithText("登录后可查看在线历史").fetchSemanticsNodes().isNotEmpty()
                    }
                    onNodeWithText("查看本地历史").performClick()
                    assertEquals(listOf<NavDestination>(History), destinations.toList())
                }
            } finally {
                owner.viewModelStore.clear()
            }
            assertTrue(requestedPaths.none { "read_history" in it }, "游客不应请求在线历史：$requestedPaths")
        }
    }

    @Test
    fun clearingHistoryShowsProgress() = withIsolatedDesktopHome {
        val clearStarted = CompletableDeferred<Unit>()
        val clearGate = CompletableDeferred<Unit>()
        withIsolatedAccount(
            MockEngine { request ->
                if (request.url.encodedPath.endsWith("/read_history/batch_del")) {
                    clearStarted.complete(Unit)
                    clearGate.await()
                    respond("", HttpStatusCode.OK)
                } else if (request.url.encodedPath == "/read_history/total") {
                    respond("""{"disabled":false}""", HttpStatusCode.OK, jsonHeaders)
                } else {
                    respond(onlineHistoryPage("待清除的在线历史"), HttpStatusCode.OK, jsonHeaders)
                }
            },
        ) { store ->
            val owner = testViewModelStoreOwner()
            try {
                runDesktopComposeUiTest(width = 412, height = 892) {
                    setContent {
                        CompositionLocalProvider(LocalZhihuAccountStore provides store) {
                            ZhihuTheme {
                                CompositionLocalProvider(
                                    LocalNavigator provides Navigator({}, {}),
                                    LocalViewModelStoreOwner provides owner,
                                ) {
                                    OnlineHistoryScreen()
                                }
                            }
                        }
                    }
                    waitUntil(timeoutMillis = 5_000) {
                        onAllNodesWithText("待清除的在线历史", substring = true).fetchSemanticsNodes().isNotEmpty()
                    }
                    onNodeWithTag(ONLINE_HISTORY_OVERFLOW_TAG).performClick()
                    onNodeWithText("清空浏览记录").performClick()
                    onNode(hasText("清空吧") and hasClickAction()).performClick()
                    waitUntil(timeoutMillis = 5_000) { clearStarted.isCompleted }
                    waitUntil(timeoutMillis = 5_000) {
                        onAllNodesWithText("正在清除历史记录", substring = true).fetchSemanticsNodes().isNotEmpty()
                    }
                    clearGate.complete(Unit)
                    waitUntil(timeoutMillis = 5_000) {
                        onAllNodesWithText("正在清除历史记录", substring = true).fetchSemanticsNodes().isEmpty()
                    }
                    onAllNodesWithText("待清除的在线历史", substring = true).assertCountEquals(0)
                }
            } finally {
                clearGate.complete(Unit)
                owner.viewModelStore.clear()
            }
        }
    }

    @Test
    fun manualCookieFailureKeepsDraftAndCurrentSession() = withIsolatedDesktopHome {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val rejectCandidate = AtomicBoolean(true)
        withIsolatedAccount(
            MockEngine { request ->
                assertEquals("/api/v4/me", request.url.encodedPath)
                val cookie = request.headers[HttpHeaders.Cookie].orEmpty()
                assertTrue("z_c0=fixture-rejected" in cookie)
                assertTrue("d_c0=fixture-device" in cookie)
                assertTrue("equals=fixture=value" in cookie)
                assertTrue("Path=" !in cookie && "empty=" !in cookie && "ignored" !in cookie)
                started.complete(Unit)
                finish.await()
                if (rejectCandidate.get()) {
                    respond("""{"error":{"message":"fixture rejected"}}""", HttpStatusCode.Unauthorized, jsonHeaders)
                } else {
                    respond("""{"id":"fixture-imported","name":"Imported Fixture","url_token":"fixture-imported"}""", HttpStatusCode.OK, jsonHeaders)
                }
            },
        ) { store ->
            val owner = store.client
            val session = store.session
            try {
                runDesktopComposeUiTest(width = 720, height = 1000) {
                    setContent { CompositionLocalProvider(LocalZhihuAccountStore provides store, LocalNavigator provides Navigator({}, {})) { ZhihuTheme { AccountToolsScreen() } } }
                    onNodeWithText("导入 Cookie").performScrollTo().performClick()
                    onNodeWithText("导入并验证").assertIsNotEnabled()
                    val field = onNode(
                        androidx.compose.ui.test
                            .hasSetTextAction(),
                    )
                    field.performTextInput("z_c0=fixture-rejected;d_c0=fixture-device;equals=fixture=value;Path=/; =ignored;empty=")
                    onNodeWithText("导入并验证").performClick()
                    waitUntil(timeoutMillis = 5_000) { started.isCompleted }
                    onNodeWithText("Cookie 字符串").assertIsNotEnabled()
                    onNodeWithText("正在验证").assertIsNotEnabled()
                    onNodeWithText("取消").assertIsNotEnabled()
                    onNodeWithText("验证登录").assertIsNotEnabled()
                    onNodeWithText("刷新 Token").assertIsNotEnabled()
                    finish.complete(Unit)
                    waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("验证登录失败，未导入 Cookie", substring = true).fetchSemanticsNodes().isNotEmpty() }
                    onNode(
                        androidx.compose.ui.test
                            .hasSetTextAction(),
                    ).assertExists()
                    onNodeWithText("导入并验证").assertExists()
                    assertTrue(owner === store.client)
                    assertTrue(session === store.session)
                    val accountsBefore = store.accountsState.value
                    val accountFile = java.nio.file.Path
                        .of(store.http403Log.file.toString())
                        .parent.parent
                        .resolve("fixture-account.json")
                    val persistedBytes = Files.readAllBytes(accountFile)
                    val temporary = Files.createDirectory(accountFile.resolveSibling("fixture-account.json.tmp"))
                    val blocker = Files.writeString(temporary.resolve("blocker"), "keep")
                    rejectCandidate.set(false)
                    try {
                        onNodeWithText("导入并验证").performClick()
                        waitUntil(timeoutMillis = 5_000) {
                            onAllNodesWithText("验证登录时发生错误", substring = true).fetchSemanticsNodes().isNotEmpty()
                        }
                        assertTrue(accountsBefore === store.accountsState.value)
                        assertTrue(owner !== store.client)
                        assertTrue(session === store.session)
                        assertContentEquals(persistedBytes, Files.readAllBytes(accountFile))
                        onNodeWithText("导入并验证").assertIsEnabled()
                        onNodeWithContentDescription("显示").performClick()
                        onNode(
                            androidx.compose.ui.test
                                .hasSetTextAction(),
                        ).assertTextContains("z_c0=fixture-rejected;d_c0=fixture-device;equals=fixture=value;Path=/; =ignored;empty=")
                    } finally {
                        Files.deleteIfExists(blocker)
                        Files.deleteIfExists(temporary)
                    }
                    onNodeWithText("取消").performClick()
                    onNodeWithText("验证登录时发生错误", substring = true).assertExists()
                    onNodeWithText("验证登录").assertIsEnabled()
                }
            } finally {
                finish.complete(Unit)
            }
        }
    }

    private fun testViewModelStoreOwner(): ViewModelStoreOwner = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    private fun onlineHistoryPage(title: String): String =
        """{"data":[{"card_type":"single_card","data":{"header":{"icon":"","title":"$title"},"action":{"type":"router","url":"zhihu://answers/123"},"extra":{"content_token":"123","content_type":"answer","read_time":1716120000,"question_token":"456"}}}],"paging":{"is_end":true,"is_start":true,"next":""}}"""

    private fun withIsolatedAccount(
        engine: MockEngine,
        additionalAccounts: List<ZhihuSavedAccount> = emptyList(),
        body: (ZhihuAccountStore) -> Unit,
    ) {
        val directory = Files.createTempDirectory("account-verification-ui-")
        val originalTheme = ThemeManager.snapshot()
        var store: ZhihuAccountStore? = null
        try {
            val accountFile = directory.resolve("fixture-account.json")
            val repository = ZhihuAccountRepository(KotlinxPath(accountFile.toString()))
            repository.saveAccounts(ZhihuAccounts("fixture-alice", listOf(ZhihuSavedAccount("fixture-alice", alice)) + additionalAccounts))
            val owner = ZhihuAccountStore(repository, engine)
            store = owner
            body(owner)
        } finally {
            try {
                store?.close()
            } finally {
                engine.close()
                ThemeManager.load(originalTheme)
                check(directory.toFile().deleteRecursively())
            }
        }
    }

    private companion object {
        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
        val alice = ZhihuAccountSession(
            login = true,
            username = "Fixture Alice",
            cookies = mutableMapOf("z_c0" to "fixture-alice", "d_c0" to "fixture-device"),
            profile = ZhihuAccountProfileSnapshot(id = "fixture-alice", name = "Fixture Alice"),
        )
    }
}
