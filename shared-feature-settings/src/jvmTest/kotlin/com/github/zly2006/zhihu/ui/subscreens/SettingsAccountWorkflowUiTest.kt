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
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.github.zly2006.zhihu.account.LocalZhihuAccountStore
import com.github.zly2006.zhihu.account.ZhihuAccountProfileSnapshot
import com.github.zly2006.zhihu.account.ZhihuAccountRepository
import com.github.zly2006.zhihu.account.ZhihuAccountSession
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.account.ZhihuAccounts
import com.github.zly2006.zhihu.account.ZhihuSavedAccount
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ZhihuTheme
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import java.nio.file.Files
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
