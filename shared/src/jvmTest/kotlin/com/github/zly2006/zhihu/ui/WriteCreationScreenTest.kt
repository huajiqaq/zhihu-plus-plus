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

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.account.LocalZhihuAccountStore
import com.github.zly2006.zhihu.account.ZhihuAccountProfileSnapshot
import com.github.zly2006.zhihu.account.ZhihuAccountRepository
import com.github.zly2006.zhihu.account.ZhihuAccountSession
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.account.ZhihuAccounts
import com.github.zly2006.zhihu.account.ZhihuSavedAccount
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.editor.compileMdToZhihuHtml
import com.github.zly2006.zhihu.editor.zhihuHtmlToEditorMarkdown
import com.github.zly2006.zhihu.navigation.AppNavHost
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.Daily
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.MainTabs
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.navigation.WriteAnswer
import com.github.zly2006.zhihu.navigation.WriteArticle
import com.github.zly2006.zhihu.navigation.rememberAppNavHost
import com.github.zly2006.zhihu.platform.desktopSettingsStore
import com.github.zly2006.zhihu.theme.ThemeMode
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.ZhihuTheme
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.AfterClass
import org.junit.BeforeClass
import kotlinx.io.files.Path as KotlinxPath

@OptIn(ExperimentalTestApi::class)
class WriteCreationScreenTest {
    @Test
    fun failedRemoteAnswerLoadsKeepLocalEditingButBlockEveryWriteUntilRetry() {
        val answerFixture = checkNotNull(javaClass.getResource("/real-api/answer-detail.json")).readText()
        for ((index, failure) in listOf("relationship", "draft", "answer").withIndex()) {
            requests.clear()
            var recover = false
            val questionId = 9300L + index
            val localText = if (failure == "answer") "" else "本机未同步的回答 😀 $index"
            val key = "creation.answer.$PROFILE_ID.$questionId"
            desktopSettingsStore().apply {
                putBoolean("$key.rich", false)
                putString(key, localText)
            }
            responder = { request ->
                check(request.method == HttpMethod.Get) { "加载失败后不允许写入：${request.method} ${request.url.encodedPath}" }
                val path = request.url.encodedPath
                val fails = !recover &&
                    when (failure) {
                        "relationship" -> path == "/questions/$questionId"
                        "draft" -> path.endsWith("/draft")
                        else -> path == "/api/v4/answers/42"
                    }
                respond(
                    if (fails) {
                        ""
                    } else {
                        when {
                            path == "/questions/$questionId" -> if (failure == "answer") {
                                """{"relationship":{"my_answer":{"answer_id":"42","is_deleted":false}}}"""
                            } else {
                                """{"relationship":{"my_answer":null}}"""
                            }
                            path.endsWith("/draft") -> if (failure == "answer") """{"content":"","settings":{}}""" else """{"content":"<p>远端旧正文</p>","settings":{}}"""
                            path == "/api/v4/answers/42" -> answerFixture
                            else -> error("未授权的加载请求：$path")
                        }
                    },
                    if (fails) HttpStatusCode.ServiceUnavailable else HttpStatusCode.OK,
                    JSON_HEADERS,
                )
            }
            runDesktopComposeUiTest(width = 720, height = 800) {
                setContent {
                    ZhihuTheme {
                        CompositionLocalProvider(
                            LocalZhihuAccountStore provides checkNotNull(store),
                            LocalNavigator provides Navigator({}, {}),
                        ) { WriteAnswerScreen(WriteAnswer(questionId, "加载失败保护")) }
                    }
                }
                waitUntil(timeoutMillis = 5_000) { requests.any { it.url.encodedPath == "/questions/$questionId" } }
                waitForIdle()
                onNodeWithTag(WRITE_ANSWER_FAB_SAVE_TAG).assertIsNotEnabled()
                onNodeWithText("发布").assertIsNotEnabled()
                onNodeWithTag(WRITE_ANSWER_CONTENT_TAG).assertIsEnabled().assert(hasText(localText))
                mainClock.advanceTimeBy(9_000)
                waitForIdle()
                assertTrue(requests.all { it.method == HttpMethod.Get })
                recover = true
                onNodeWithText("重试加载草稿").performClick()
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("重试加载草稿").fetchSemanticsNodes().isEmpty() }
                onNodeWithTag(WRITE_ANSWER_FAB_SAVE_TAG).assertIsEnabled()
                val expected = if (failure == "answer") {
                    val answer = ZhihuJson.decodeJson<DataHolder.Answer>(ZhihuJson.json.parseToJsonElement(answerFixture))
                    zhihuHtmlToEditorMarkdown(answer.editableContent ?: answer.content)
                } else {
                    localText
                }
                onNodeWithTag(WRITE_ANSWER_CONTENT_TAG).assert(hasText(expected))
            }
        }
    }

    @Test
    fun articleKeepsDraftOnPublishFailureThenClosesEditorAndUsesLiveLandscapeOwner() {
        requests.clear()
        val title = "文章 & + %😀"
        val markdown = "长文 **正文** & + 😀"
        val expectedHtml = compileMdToZhihuHtml(markdown)
        var failPublish = true
        responder = { request ->
            when {
                request.method == HttpMethod.Post && request.url.encodedPath in listOf("/articles/write", "/articles/9501/draft") -> respond("""{"id":9501}""", HttpStatusCode.OK, JSON_HEADERS)
                request.method == HttpMethod.Put && request.url.encodedPath == "/articles/9501/draft" -> if (failPublish) {
                    respond("""{"message":"synthetic article failure"}""", HttpStatusCode.BadRequest, JSON_HEADERS)
                } else {
                    respond("""{"id":9501}""", HttpStatusCode.OK, JSON_HEADERS)
                }
                request.method == HttpMethod.Get -> respond("""{"date":"20261104","stories":[],"data":[],"paging":{"is_end":true,"next":""}}""", HttpStatusCode.OK, JSON_HEADERS)
                else -> error("未授权的文章请求：${request.method} ${request.url.encodedPath}")
            }
        }
        var host: AppNavHost? = null
        var detailHost: AppNavHost? = null
        val key = "creation.article.$PROFILE_ID.draft"
        runDesktopComposeUiTest(width = 1200, height = 800) {
            setContent {
                CompositionLocalProvider(LocalZhihuAccountStore provides checkNotNull(store)) {
                    ZhihuTheme {
                        val mainHost = rememberAppNavHost(MainTabs)
                        host = mainHost
                        ZhihuMain(
                            modifier = Modifier.requiredSize(1200.dp, 800.dp),
                            navHost = mainHost,
                            navigate = mainHost::open,
                            navigateContent = { destination, target ->
                                target.open(destination)
                            },
                            enableLandscapeListDetail = true,
                            tabsState = rememberZhihuMainTabsState(remember {
                                ZhihuMainPreferenceState {
                                    ZhihuMainPreferenceSnapshot(
                                        tapToScrollToTopEnabled = false,
                                        autoHideBottomBar = false,
                                        landscapeListDetailEnabled = true,
                                        selectedBottomBarItemKeys = listOf(Daily.name),
                                        startDestination = Daily,
                                    )
                                }
                            }),
                            articleContent = { article ->
                                val articleHost = checkNotNull(LocalArticleNavHost.current)
                                SideEffect { detailHost = articleHost }
                                Text("已发表文章 ${article.id}")
                            },
                        )
                    }
                }
            }
            runOnIdle { host!!.open(WriteArticle) }
            onNodeWithTag(WRITE_ARTICLE_CONTENT_TAG).performTextInput("<p></p>")
            onNodeWithTag(WRITE_ARTICLE_SAVE_TAG).performClick()
            waitForIdle()
            assertTrue(requests.none { it.method == HttpMethod.Post }, "空 HTML 结构不是正文，不能创建空云稿")
            onNodeWithTag(WRITE_ARTICLE_TITLE_TAG).performTextInput(title)
            onNodeWithTag(WRITE_ARTICLE_CONTENT_TAG).performTextReplacement(markdown)
            onNodeWithTag(WRITE_ARTICLE_SAVE_TAG).performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("草稿已保存").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(listOf(MainTabs, WriteArticle), host!!.backStack.toList())
            assertEquals(mapOf("title" to title, "content" to expectedHtml, "copyright" to "public"), form(requests.single { it.url.encodedPath == "/articles/write" }))
            onNodeWithTag(WRITE_ARTICLE_PUBLISH_TAG).performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("操作失败").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(WriteArticle, host.top)
            assertTrue(desktopSettingsStore().getStringOrNull(key) != null)
            onNodeWithText("确定").performClick()
            onNodeWithTag(WRITE_ARTICLE_TITLE_TAG).assert(hasText(title))
            onNodeWithTag(WRITE_ARTICLE_CONTENT_TAG).assert(hasText(markdown))
            failPublish = false
            onNodeWithTag(WRITE_ARTICLE_PUBLISH_TAG).performClick()
            waitUntil(timeoutMillis = 5_000) { detailHost?.top is Article }
            waitForIdle()
            assertEquals(listOf(MainTabs), host.backStack.toList())
            assertEquals(Article(title = title, type = ArticleType.Article, id = 9501), detailHost!!.top)
            onNodeWithText("已发表文章 9501").assertExists()
            val publish = requests.last { it.method == HttpMethod.Put }
            assertEquals(mapOf("title" to title, "content" to expectedHtml, "copyright_permission" to "need_review"), form(publish))
            assertEquals(null, desktopSettingsStore().getStringOrNull(key))
            runOnIdle {
                detailHost.back()
                host.open(WriteArticle)
            }
            onNodeWithTag(WRITE_ARTICLE_TITLE_TAG).assert(hasText(""))
            onNodeWithTag(WRITE_ARTICLE_CONTENT_TAG).assert(hasText(""))
        }
    }

    @Test
    fun answerPublishStopsWhenTheAccountChangesAfterDraftSave() {
        requests.clear()
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val events = CopyOnWriteArrayList<Any>()
        val account = checkNotNull(store)
        val original = account.session
        val key = "creation.answer.$PROFILE_ID.9410"
        desktopSettingsStore().putBoolean("$key.rich", false)
        responder = { request ->
            when {
                request.method == HttpMethod.Get && request.url.encodedPath == "/questions/9410" -> respond("""{"relationship":{"my_answer":null}}""", HttpStatusCode.OK, JSON_HEADERS)
                request.method == HttpMethod.Get && request.url.encodedPath.endsWith("/draft") -> respond("""{"content":"","settings":{}}""", HttpStatusCode.OK, JSON_HEADERS)
                request.method == HttpMethod.Post && request.url.encodedPath.endsWith("/draft") -> {
                    started.complete(Unit)
                    release.await()
                    respond("{}", HttpStatusCode.OK, JSON_HEADERS)
                }
                else -> error("账号变化后不能继续发布：${request.method} ${request.url.encodedPath}")
            }
        }
        try {
            runDesktopComposeUiTest(width = 720, height = 800) {
                setContent {
                    ZhihuTheme {
                        CompositionLocalProvider(
                            LocalZhihuAccountStore provides account,
                            LocalNavigator provides Navigator({ events += it }, { events += "back" }),
                        ) { WriteAnswerScreen(WriteAnswer(9410, "原账号问题")) }
                    }
                }
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("云端草稿已加载").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag(WRITE_ANSWER_CONTENT_TAG).performTextInput("原账号未同步正文")
                onNodeWithText("发布").performClick()
                waitUntil(timeoutMillis = 5_000) { started.isCompleted }
                runOnIdle {
                    account.replaceSession(original.copy(username = "replacement-answer", profile = original.profile!!.copy(id = "replacement-answer")))
                    release.complete(Unit)
                }
                waitForIdle()
                assertTrue(requests.none { it.url.encodedPath == "/api/v4/content/publish" })
                assertTrue(events.isEmpty())
                assertEquals("原账号未同步正文", desktopSettingsStore().getString(key, ""))
                assertEquals(null, desktopSettingsStore().getStringOrNull("creation.answer.replacement-answer.9410"))
            }
        } finally {
            release.complete(Unit)
            account.replaceSession(original)
        }
    }

    private fun form(request: HttpRequestData): Map<String, String> = (request.body as TextContent).text.split('&').associate { field ->
        val (key, value) = field.split('=', limit = 2)
        URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
    }

    companion object {
        private const val PROFILE_ID = "creation-ui-fixture"
        private val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")
        private val requests = CopyOnWriteArrayList<HttpRequestData>()
        private var responder: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { error("未配置创作请求") }
        private var originalHome: String? = null
        private var directory: Path? = null
        private var store: ZhihuAccountStore? = null

        @BeforeClass
        @JvmStatic
        fun setUpIsolatedAccount() {
            originalHome = System.getProperty("user.home")
            val home = Files.createTempDirectory("zhihu-creation-ui-")
            directory = home
            System.setProperty("user.home", home.toString())
            val account = home.resolve("fixture-account.json")
            val repository = ZhihuAccountRepository(KotlinxPath(account.toString()))
            val session = ZhihuAccountSession(
                login = true,
                username = PROFILE_ID,
                cookies = mutableMapOf("d_c0" to "synthetic-device", "z_c0" to "synthetic-session", "_xsrf" to "synthetic-xsrf"),
                profile = ZhihuAccountProfileSnapshot(PROFILE_ID, "创作测试用户", PROFILE_ID, "people", ""),
            )
            repository.saveAccounts(ZhihuAccounts(PROFILE_ID, listOf(ZhihuSavedAccount(PROFILE_ID, session))))
            val mock = MockEngine { request ->
                requests += request
                responder(request)
            }
            val fixtureStore = ZhihuAccountStore(repository, engine = mock)
            store = fixtureStore
            assertEquals(PROFILE_ID, fixtureStore.session.profile?.id)
            assertSame(mock, fixtureStore.client.httpClient().engine)
            desktopSettingsStore().apply {
                putBoolean("enableContentFilter", false)
                putBoolean("allowTelemetry", false)
                putBoolean("articleUseWebView", false)
                putBoolean("floatingBottomBar", false)
                putString("uiThemeStyle", ThemeStyle.MATERIAL_EXPRESSIVE.name)
                putString("themeMode", ThemeMode.LIGHT.name)
            }
        }

        @AfterClass
        @JvmStatic
        fun tearDownIsolatedAccount() {
            store?.close()
            if (originalHome == null) System.clearProperty("user.home") else System.setProperty("user.home", originalHome)
            directory?.toFile()?.deleteRecursively()
        }
    }
}
