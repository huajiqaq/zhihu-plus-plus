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

package com.github.zly2006.zhihu

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.zly2006.zhihu.account.LocalZhihuAccountStore
import com.github.zly2006.zhihu.data.HistoryType
import com.github.zly2006.zhihu.data.LocalHistoryStorage
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.currentAndroidApplicationContext
import com.github.zly2006.zhihu.data.initializeAndroidApplicationContext
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.platform.androidSettingsStore
import com.github.zly2006.zhihu.test.InstrumentedTestEnvironment
import com.github.zly2006.zhihu.test.MainActivityComposeRule
import com.github.zly2006.zhihu.test.RecordingNavigator
import com.github.zly2006.zhihu.test.ZhihuMockApi
import com.github.zly2006.zhihu.test.resetAppPreferences
import com.github.zly2006.zhihu.test.setScreenContent
import com.github.zly2006.zhihu.ui.LEGACY_LOCAL_HISTORY_BACK_BUTTON_TAG
import com.github.zly2006.zhihu.ui.LEGACY_LOCAL_HISTORY_LIST_TAG
import com.github.zly2006.zhihu.ui.LegacyLocalHistoryScreen
import com.github.zly2006.zhihu.ui.OnlineHistoryScreen
import com.github.zly2006.zhihu.ui.PREFERENCE_NAME
import com.github.zly2006.zhihu.ui.components.HISTORY_DELETE_SELECTED_TAG
import com.github.zly2006.zhihu.ui.components.HISTORY_SELECT_ALL_TAG
import com.github.zly2006.zhihu.ui.components.HISTORY_SELECT_TOGGLE_TAG
import com.github.zly2006.zhihu.viewmodel.feed.HistoryViewModel
import com.github.zly2006.zhihu.viewmodel.feed.OnlineHistoryViewModel
import io.ktor.client.engine.mock.MockEngine
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OnlineHistoryScreenInstrumentedTest {
    @get:Rule
    val composeRule: MainActivityComposeRule = createAndroidComposeRule()

    private val onlineHistoryViewModel: OnlineHistoryViewModel
        get() = ViewModelProvider(composeRule.activity)[OnlineHistoryViewModel::class.java]
    private lateinit var previousContext: Context
    private lateinit var historyContext: Context
    private lateinit var historyDirectory: File
    private val storage: LocalHistoryStorage
        get() = LocalHistoryStorage(Path(historyDirectory.path, "history.json"), androidSettingsStore(historyContext))

    @Before
    fun setUp() {
        composeRule.resetAppPreferences(forceMock = true)
        composeRule.setScreenContent { }
        composeRule.runOnIdle { composeRule.activity.viewModelStore.clear() }
        previousContext = currentAndroidApplicationContext()
        historyDirectory = File(composeRule.activity.cacheDir, "history-ui-${UUID.randomUUID()}")
        check(historyDirectory.mkdir())
        val preferenceName = "history-ui-${UUID.randomUUID()}"
        historyContext = object : ContextWrapper(composeRule.activity) {
            override fun getApplicationContext(): Context = this

            override fun getFilesDir(): File = historyDirectory

            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences(if (name == PREFERENCE_NAME) preferenceName else name, mode)
        }
        composeRule.runOnIdle { initializeAndroidApplicationContext(historyContext) }
        ZhihuMockApi.mockJson(HttpMethod.Get, "https://api.zhihu.com/read_history/total", "{\"disabled\":false}")
        ZhihuMockApi.mockJson(HttpMethod.Post, "https://api.zhihu.com/read_history/batch_del", "{}")
        clearDisplayItems()
    }

    @After
    fun tearDown() {
        composeRule.setScreenContent { }
        runBlocking {
            onlineHistoryViewModel.viewModelScope.coroutineContext[kotlinx.coroutines.Job]!!.cancelAndJoin()
        }
        composeRule.runOnIdle {
            composeRule.activity.viewModelStore.clear()
            initializeAndroidApplicationContext(previousContext)
        }
        check(historyDirectory.deleteRecursively())
    }

    @Test
    fun historyItemOverflowOffersSingleDeleteAction() {
        setOnlineHistoryScreen()

        val targetKey = onlineHistoryViewModel.displayItems.single { it.title == seedTitle(1) }.stableKey
        assertEquals("online:answer:token-1", targetKey)
        val engine = InstrumentedTestEnvironment.accountStore.engine as MockEngine
        val requestsBeforeDelete = engine.requestHistory.size
        val targetCard = hasClickAction() and hasAnyDescendant(hasTestTag("feed_card_title") and hasText(seedTitle(1)))
        composeRule
            .onNode(
                hasContentDescription("更多选项") and hasAnyAncestor(targetCard),
                useUnmergedTree = true,
            ).assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText("删除此条浏览记录").assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            onlineHistoryViewModel.displayItems.size == 23 &&
                onlineHistoryViewModel.displayItems.none { it.stableKey == targetKey }
        }
        val deletion = engine.requestHistory.drop(requestsBeforeDelete).single {
            it.method == HttpMethod.Post && it.url.toString() == "https://api.zhihu.com/read_history/batch_del"
        }
        assertEquals(
            ZhihuJson.json.parseToJsonElement("""{"pairs":[{"content_token":"token-1","content_type":"answer"}],"clear":false}"""),
            ZhihuJson.json.parseToJsonElement((deletion.body as TextContent).text),
        )
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(seedTitle(1)).fetchSemanticsNodes().isEmpty()
        }
        assertEquals(23, onlineHistoryViewModel.displayItems.size)
        composeRule.onNodeWithText(seedTitle(1)).assertDoesNotExist()
        composeRule.onNodeWithText(seedTitle(2)).assertIsDisplayed()
    }

    @Test
    fun typeFilterAndSelectionModeUseStableTags() {
        setOnlineHistoryScreen()
        assertTrue(composeRule.onAllNodesWithTag("history_badge_" + HistoryType.ANSWER, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithTag("history_type_filter_" + HistoryType.ARTICLE).performClick()
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithTag("history_badge_" + HistoryType.ARTICLE, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        composeRule.onAllNodesWithTag("history_badge_" + HistoryType.ANSWER, useUnmergedTree = true).assertCountEquals(0)
        composeRule.onNodeWithTag(HISTORY_SELECT_TOGGLE_TAG).performClick()
        composeRule.onNodeWithTag(HISTORY_SELECT_ALL_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).assertIsDisplayed()
        for (cardStyle in listOf("card", "divider")) {
            composeRule.runOnIdle {
                historyContext
                    .getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString("feedCardStyle", cardStyle)
                    .commit()
            }
            composeRule.waitForIdle()
            val title = composeRule
                .onNode(hasTestTag("feed_card_title") and hasText(seedTitle(2)), useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
            val badges = composeRule
                .onAllNodesWithTag("history_badge_" + HistoryType.ARTICLE, useUnmergedTree = true)
                .fetchSemanticsNodes()
            assertTrue(badges.any { it.boundsInRoot.left >= title.right && it.boundsInRoot.center.y in title.top..title.bottom })
        }
        composeRule.onNodeWithTag(HISTORY_SELECT_ALL_TAG).performClick()
        composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).assertTextEquals("删除(12)")
        composeRule.onNodeWithText(seedTitle(2)).performClick()
        composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).assertTextEquals("删除(11)")
        composeRule.onNodeWithText(seedTitle(2)).performClick()
        composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).assertTextEquals("删除(12)")
        composeRule.onNodeWithTag("history_type_filter_" + HistoryType.ANSWER).performClick()
        composeRule.onNodeWithText(seedTitle(1)).performClick()
        composeRule.onNodeWithText("共选 13 项").assertIsDisplayed()
        composeRule.onNodeWithTag(HISTORY_SELECT_ALL_TAG).performClick()
        composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).assertTextEquals("删除(24)")
        composeRule.onNodeWithTag(HISTORY_SELECT_ALL_TAG).performClick()
        composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).assertTextEquals("删除(12)")
        composeRule.onNodeWithTag("history_type_filter_" + HistoryType.ARTICLE).performClick()
        composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).performClick()
        composeRule.onNodeWithText("删除选中的 12 条浏览记录？").assertIsDisplayed()
        composeRule.onNodeWithText("会删除所有已选记录（包括其他筛选类型），不影响未选或未加载记录。").assertIsDisplayed()
        composeRule.onNode(hasText("取消") and hasAnyAncestor(isDialog()), useUnmergedTree = true).performClick()
        assertEquals(24, onlineHistoryViewModel.displayItems.size)
        assertEquals(0, ZhihuMockApi.requestCount(HttpMethod.Post, "/read_history/batch_del"))
        composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).assertTextEquals("删除(12)")
        val pendingDelete = CompletableDeferred<Unit>()
        ZhihuMockApi.mockJson(HttpMethod.Post, "https://api.zhihu.com/read_history/batch_del", "{}") { pendingDelete.await() }
        composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).performClick()
        composeRule.onNodeWithText("删除", substring = false).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { ZhihuMockApi.requestCount(HttpMethod.Post, "/read_history/batch_del") == 1 }
        assertEquals(24, onlineHistoryViewModel.displayItems.size)
        composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).assertIsNotEnabled()
        pendingDelete.complete(Unit)
        composeRule.waitUntil(timeoutMillis = 5_000) { onlineHistoryViewModel.displayItems.size == 12 }
        assertTrue(onlineHistoryViewModel.displayItems.all { it.historyType == HistoryType.ANSWER })
        composeRule.onAllNodesWithTag(HISTORY_DELETE_SELECTED_TAG).assertCountEquals(0)
    }

    @Test
    fun batchSelectionAcrossPagesRetainsFailuresAndResetsForReplacementAccount() {
        setOnlineHistoryScreen(itemCount = 40, firstPageCount = 20)
        composeRule.runOnIdle { storage.add(Question(90002, "在线删除不影响本地")) }
        val originalBytes = File(historyDirectory, "history.json").readBytes()
        composeRule.onNodeWithTag(HISTORY_SELECT_TOGGLE_TAG).performClick()
        composeRule.onNodeWithTag(HISTORY_SELECT_ALL_TAG).performClick()
        composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).assertTextEquals("删除(20)")
        composeRule.onNodeWithTag("online_history_list").performScrollToIndex(19)
        composeRule.waitUntil(timeoutMillis = 5_000) { onlineHistoryViewModel.displayItems.size == 40 }
        assertEquals(1, ZhihuMockApi.requestCount(HttpMethod.Get, "read_history?offset=20"))
        composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).assertTextEquals("删除(20)")
        composeRule.onNodeWithTag(HISTORY_SELECT_ALL_TAG).performClick()
        composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).assertTextEquals("删除(40)")
        composeRule.onNodeWithTag(HISTORY_SELECT_ALL_TAG).performClick()
        composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).assertTextEquals("删除(0)")
        composeRule.onNodeWithTag("history_type_filter_" + HistoryType.ARTICLE).performClick()
        composeRule.onNodeWithTag(HISTORY_SELECT_ALL_TAG).performClick()
        composeRule.onNodeWithTag("history_type_filter_" + HistoryType.ANSWER).performClick()
        composeRule.onNodeWithText(seedTitle(1)).performClick()
        composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).assertTextEquals("删除(21)")
        composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).performClick()
        composeRule.onNode(hasText("取消") and hasAnyAncestor(isDialog()), useUnmergedTree = true).performClick()
        assertEquals(0, ZhihuMockApi.requestCount(HttpMethod.Post, "/read_history/batch_del"))
        for ((body, status) in listOf("{}" to HttpStatusCode.ServiceUnavailable, """{"error":{"code":40353,"message":"need_login"}}""" to HttpStatusCode.OK)) {
            val count = ZhihuMockApi.requestCount(HttpMethod.Post, "/read_history/batch_del")
            ZhihuMockApi.mockJson(HttpMethod.Post, "https://api.zhihu.com/read_history/batch_del", body, status)
            composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).performClick()
            composeRule.onNodeWithText("删除", substring = false).performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) { ZhihuMockApi.requestCount(HttpMethod.Post, "/read_history/batch_del") == count + 1 }
            composeRule.waitForIdle()
            assertEquals(40, onlineHistoryViewModel.displayItems.size)
            composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).assertTextEquals("删除(21)")
            assertTrue(originalBytes.contentEquals(File(historyDirectory, "history.json").readBytes()))
        }
        ZhihuMockApi.mockJson(HttpMethod.Post, "https://api.zhihu.com/read_history/batch_del", "{}")
        composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).performClick()
        composeRule.onNodeWithText("删除", substring = false).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { onlineHistoryViewModel.displayItems.size == 19 }
        assertTrue(onlineHistoryViewModel.displayItems.all { it.historyType == HistoryType.ANSWER && it.stableKey != "online:answer:token-1" })
        val deletion = (InstrumentedTestEnvironment.accountStore.engine as MockEngine).requestHistory.last { it.url.encodedPath == "/read_history/batch_del" }
        val pairs = (2..40 step 2).map { """{"content_token":"token-$it","content_type":"article"}""" } + """{"content_token":"token-1","content_type":"answer"}"""
        assertEquals(
            ZhihuJson.json.parseToJsonElement("""{"pairs":[${pairs.joinToString(",")}],"clear":false}"""),
            ZhihuJson.json.parseToJsonElement((deletion.body as TextContent).text),
        )
        assertTrue(originalBytes.contentEquals(File(historyDirectory, "history.json").readBytes()))
        composeRule.onNodeWithTag(HISTORY_SELECT_TOGGLE_TAG).performClick()
        composeRule.onNodeWithTag(HISTORY_SELECT_ALL_TAG).performClick()
        composeRule.runOnIdle {
            val store = InstrumentedTestEnvironment.accountStore
            val session = store.session
            store.replaceSession(session.copy(cookies = session.cookies.toMutableMap().apply { put("z_c0", "synthetic-history-replacement") }))
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(HISTORY_DELETE_SELECTED_TAG).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("history_type_filter_all").assertIsSelected()
        composeRule.onNodeWithTag(HISTORY_SELECT_TOGGLE_TAG).performClick()
        composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).assertTextEquals("删除(0)")
    }

    @Test
    fun localHistoryManagementEntersSelectionWithoutCrash() {
        composeRule.runOnIdle {
            (1..30).forEach { index ->
                storage.add(Article("本地合成历史 $index", if (index % 2 == 0) ArticleType.Article else ArticleType.Answer, index.toLong()))
            }
        }
        val navigator = composeRule.setScreenContent {
            CompositionLocalProvider(
                LocalContext provides historyContext,
                LocalZhihuAccountStore provides InstrumentedTestEnvironment.accountStore,
            ) {
                LegacyLocalHistoryScreen()
            }
        }
        composeRule.onNodeWithTag(HISTORY_SELECT_TOGGLE_TAG).performClick()
        composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag("history_type_filter_" + HistoryType.ARTICLE).performClick()
        composeRule.onNodeWithTag(HISTORY_SELECT_ALL_TAG).performClick()
        composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).assertTextEquals("删除(15)")
        composeRule.onNodeWithTag("history_type_filter_" + HistoryType.ANSWER).performClick()
        composeRule.onNodeWithText("本地合成历史 29").performClick()
        composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).assertTextEquals("删除(16)")
        composeRule.onNodeWithTag(HISTORY_SELECT_ALL_TAG).performClick()
        composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).assertTextEquals("删除(30)")
        composeRule.onNodeWithTag(HISTORY_SELECT_ALL_TAG).performClick()
        composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).assertTextEquals("删除(15)")
        composeRule.onNodeWithTag("history_type_filter_all").performClick()
        composeRule.onNodeWithTag(LEGACY_LOCAL_HISTORY_LIST_TAG).performScrollToIndex(25)
        composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).assertTextEquals("删除(15)")
        composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).performClick()
        composeRule.onNode(hasText("取消") and hasAnyAncestor(isDialog()), useUnmergedTree = true).performClick()
        assertEquals(30, storage.history.size)
        composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).assertTextEquals("删除(15)")
        val file = File(historyDirectory, "history.json")
        val original = file.readText()
        composeRule.runOnIdle { file.writeText("invalid synthetic history") }
        composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).performClick()
        composeRule.onNodeWithText("删除", substring = false).performClick()
        composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).assertTextEquals("删除(15)")
        assertEquals("invalid synthetic history", file.readText())
        val localModel = ViewModelProvider(composeRule.activity)[HistoryViewModel::class.java]
        assertEquals(30, localModel.displayItems.size)
        composeRule.runOnIdle { file.writeText(original) }
        composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).performClick()
        composeRule.onNodeWithText("删除", substring = false).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { localModel.displayItems.size == 15 }
        assertTrue(storage.history.all { it is Article && it.type == ArticleType.Answer })
        composeRule.onAllNodesWithTag(HISTORY_DELETE_SELECTED_TAG).assertCountEquals(0)
        composeRule.runOnIdle { storage.add(Question(90001, "下拉新增本地记录")) }
        composeRule.onNodeWithTag(LEGACY_LOCAL_HISTORY_LIST_TAG).performScrollToIndex(0)
        composeRule.onNodeWithTag(LEGACY_LOCAL_HISTORY_LIST_TAG).performTouchInput { swipeDown() }
        composeRule.waitUntil(timeoutMillis = 5_000) { localModel.displayItems.size == 16 }
        composeRule.onNodeWithTag(LEGACY_LOCAL_HISTORY_LIST_TAG).performScrollToIndex(0)
        composeRule.onNodeWithText("下拉新增本地记录").assertIsDisplayed()
        assertEquals(0, ZhihuMockApi.requestCount(HttpMethod.Post, "/read_history/batch_del"))
        composeRule.onNodeWithTag(HISTORY_SELECT_TOGGLE_TAG).performClick()
        composeRule.onNodeWithTag(LEGACY_LOCAL_HISTORY_BACK_BUTTON_TAG).performClick()
        assertEquals(0, navigator.backCount)
        composeRule.onNodeWithTag(LEGACY_LOCAL_HISTORY_BACK_BUTTON_TAG).performClick()
        assertEquals(1, navigator.backCount)
    }

    private fun setOnlineHistoryScreen(itemCount: Int = 24, firstPageCount: Int = itemCount): RecordingNavigator {
        fun items(range: IntRange) = range.joinToString(",") { index ->
            val type = if (index % 2 == 1) "answer" else "article"
            val route = if (type == "answer") "answers" else "articles"
            """{"card_type":"single_card","data":{"header":{"icon":"","title":"${seedTitle(index)}"},"action":{"type":"router","url":"zhihu://$route/$index"},"extra":{"content_token":"token-$index","content_type":"$type","read_time":$index,"question_token":"fixture"}}}"""
        }
        val paged = firstPageCount < itemCount
        val next = if (paged) "https://api.zhihu.com/unify-consumption/read_history?offset=$firstPageCount&limit=10" else ""
        ZhihuMockApi.mockJsonPrefix(
            HttpMethod.Get,
            "https://api.zhihu.com/unify-consumption/read_history?offset=0",
            """{"data":[${items(1..firstPageCount)}],"paging":{"is_end":${!paged},"next":"$next"}}""",
        )
        if (paged) {
            ZhihuMockApi.mockJsonPrefix(
                HttpMethod.Get,
                next,
                """{"data":[${items(firstPageCount..itemCount)}],"paging":{"is_end":true,"next":""}}""",
            )
        }
        val navigator = composeRule.setScreenContent {
            CompositionLocalProvider(
                LocalContext provides historyContext,
                LocalZhihuAccountStore provides InstrumentedTestEnvironment.accountStore,
            ) {
                OnlineHistoryScreen()
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { onlineHistoryViewModel.displayItems.size == firstPageCount }
        return navigator
    }

    private fun clearDisplayItems() {
        composeRule.activity.runOnUiThread {
            onlineHistoryViewModel.displayItems.clear()
        }
        composeRule.waitForIdle()
    }

    private companion object {
        fun seedTitle(index: Int) = "固定在线历史条目 $index"
    }
}
