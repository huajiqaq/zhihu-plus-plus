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

package com.github.zly2006.zhihu.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.github.zly2006.zhihu.account.LocalZhihuAccountStore
import com.github.zly2006.zhihu.account.ZhihuAccountRepository
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.WriteQuestion
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.util.ZHIHU_WEB_ZSE93
import com.github.zly2006.zhihu.util.ZhihuFetchSignature
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import java.net.URLDecoder
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.io.files.Path as KotlinxPath

@OptIn(ExperimentalTestApi::class)
class WriteQuestionScreenTest {
    @Test
    fun publishesOfficialFormAndRetainsDraftAfterFailure() {
        val oldHome = System.getProperty("user.home")
        val oldTheme = ThemeManager.snapshot()
        val home = Files.createTempDirectory("zhihu-question-ui-")
        val requests = CopyOnWriteArrayList<HttpRequestData>()
        val navigations = CopyOnWriteArrayList<NavDestination>()
        val responseGate = CompletableDeferred<Unit>()
        var store: ZhihuAccountStore? = null
        val title = "  提问 & + %😀  "
        val detail = "  说明 & + %😀\n第二行  "
        val engine = MockEngine { request ->
            check(request.method == HttpMethod.Post && request.url.toString() == "https://api.zhihu.com/questions")
            requests += request
            responseGate.await()
            respond(
                if (requests.size == 1) """{"error":{"message":"synthetic question failure"}}""" else """{"id":42}""",
                if (requests.size == 1) HttpStatusCode.BadRequest else HttpStatusCode.Created,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        try {
            System.setProperty("user.home", home.toString())
            val account = home.resolve("fixture-account.json")
            Files.createDirectories(account.parent)
            Files.writeString(account, """{"login":true,"username":"question-ui-fixture","cookies":{"d_c0":"synthetic-device","z_c0":"synthetic-session"}}""")
            val accountStore = ZhihuAccountStore(ZhihuAccountRepository(KotlinxPath(account.toString())), engine = engine)
            store = accountStore
            assertEquals("question-ui-fixture", accountStore.session.username)
            assertSame(engine, accountStore.client.httpClient().engine)
            val showEditor = mutableStateOf(true)
            runDesktopComposeUiTest(width = 720, height = 800) {
                setContent {
                    ZhihuTheme {
                        CompositionLocalProvider(
                            LocalZhihuAccountStore provides accountStore,
                            LocalNavigator provides Navigator({ navigations += it }, { showEditor.value = false }),
                        ) {
                            if (showEditor.value) WriteQuestionScreen(WriteQuestion)
                        }
                    }
                }
                onNodeWithTag(WRITE_QUESTION_TITLE_TAG).performTextInput(title)
                onNodeWithTag(WRITE_QUESTION_DETAIL_TAG).performTextInput(detail)
                onNodeWithTag(WRITE_QUESTION_PUBLISH_TAG).performClick()
                waitUntil(timeoutMillis = 5_000) { requests.isNotEmpty() }
                onNodeWithTag(WRITE_QUESTION_TITLE_TAG).assertIsNotEnabled()
                onNodeWithTag(WRITE_QUESTION_DETAIL_TAG).assertIsNotEnabled()
                onNodeWithTag(WRITE_QUESTION_PUBLISH_TAG).assertIsNotEnabled()
                val request = requests.single()
                val body = request.body as TextContent
                assertEquals(ContentType.Application.FormUrlEncoded, body.contentType.withoutParameters())
                val fields = body.text.split('&').map { entry ->
                    val (key, value) = entry.split('=', limit = 2)
                    URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
                }
                assertEquals(
                    listOf("title" to "提问 & + %😀？", "detail" to "说明 & + %😀\n第二行", "is_anonymous" to "false"),
                    fields,
                )
                assertEquals(ZHIHU_WEB_ZSE93, request.headers["x-zse-93"])
                assertTrue(
                    (0..126).any { seed ->
                        request.headers["x-zse-96"] == ZhihuFetchSignature.createZse96Header(
                            ZHIHU_WEB_ZSE93,
                            request.url.toString(),
                            "synthetic-device",
                            body.text,
                            seed,
                        )
                    },
                )
                responseGate.complete(Unit)
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("操作失败").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithText("synthetic question failure", substring = true).assertExists()
                assertTrue(navigations.isEmpty())
                onNodeWithText("确定").performClick()
                runOnIdle { showEditor.value = false }
                waitForIdle()
                runOnIdle { showEditor.value = true }
                onNodeWithTag(WRITE_QUESTION_TITLE_TAG).assert(hasText(title))
                onNodeWithTag(WRITE_QUESTION_DETAIL_TAG).assert(hasText(detail))
                onNodeWithTag(WRITE_QUESTION_PUBLISH_TAG).performClick()
                waitUntil(timeoutMillis = 5_000) { navigations.isNotEmpty() }
                val question = navigations.single() as Question
                assertEquals(42L, question.questionId)
                assertEquals("提问 & + %😀？", question.title)
                assertEquals(2, requests.size)
                assertEquals(false, showEditor.value, "成功后必须退出编辑页")
                waitForIdle()
                runOnIdle { showEditor.value = true }
                onNodeWithTag(WRITE_QUESTION_TITLE_TAG).assert(hasText(""))
                onNodeWithTag(WRITE_QUESTION_DETAIL_TAG).assert(hasText(""))
            }
        } finally {
            responseGate.complete(Unit)
            store?.close()
            ThemeManager.load(oldTheme)
            if (oldHome == null) System.clearProperty("user.home") else System.setProperty("user.home", oldHome)
            home.toFile().deleteRecursively()
        }
    }
}
