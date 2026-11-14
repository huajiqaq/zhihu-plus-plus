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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.zly2006.zhihu.data.HistoryType
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.test.InstrumentedTestEnvironment
import com.github.zly2006.zhihu.test.MainActivityComposeRule
import com.github.zly2006.zhihu.test.RecordingNavigator
import com.github.zly2006.zhihu.test.ZhihuMockApi
import com.github.zly2006.zhihu.test.resetAppPreferences
import com.github.zly2006.zhihu.test.setScreenContent
import com.github.zly2006.zhihu.ui.LEGACY_LOCAL_HISTORY_BACK_BUTTON_TAG
import com.github.zly2006.zhihu.ui.LegacyLocalHistoryScreen
import com.github.zly2006.zhihu.ui.OnlineHistoryScreen
import com.github.zly2006.zhihu.ui.PREFERENCE_NAME
import com.github.zly2006.zhihu.ui.components.HISTORY_DELETE_SELECTED_TAG
import com.github.zly2006.zhihu.ui.components.HISTORY_SELECT_ALL_TAG
import com.github.zly2006.zhihu.ui.components.HISTORY_SELECT_TOGGLE_TAG
import com.github.zly2006.zhihu.viewmodel.feed.OnlineHistoryViewModel
import io.ktor.client.engine.mock.MockEngine
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnlineHistoryScreenInstrumentedTest {
    @get:Rule
    val composeRule: MainActivityComposeRule = createAndroidComposeRule()

    private val onlineHistoryViewModel: OnlineHistoryViewModel
        get() = ViewModelProvider(composeRule.activity)[OnlineHistoryViewModel::class.java]

    @Before
    fun setUp() {
        composeRule.resetAppPreferences(forceMock = true)
        ZhihuMockApi.mockJson(HttpMethod.Get, "https://api.zhihu.com/read_history/total", "{\"disabled\":false}")
        ZhihuMockApi.mockJson(HttpMethod.Post, "https://api.zhihu.com/read_history/batch_del", "{}")
        clearDisplayItems()
    }

    @After
    fun tearDown() {
        clearDisplayItems()
    }

    @Test
    fun historyItemOverflowOffersSingleDeleteAction() {
        setOnlineHistoryScreen()

        val targetKey = onlineHistoryViewModel.displayItems.single { it.title == seedTitle(1) }.stableKey
        assertEquals("online:answer:token-1", targetKey)
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
        val deletion = (InstrumentedTestEnvironment.accountStore.engine as MockEngine).requestHistory.single {
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
        for (duo3 in listOf(false, true)) {
            composeRule.runOnIdle {
                composeRule.activity
                    .getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("duo3_card_layout", duo3)
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
        composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).performClick()
        composeRule.onNodeWithText("删除选中的 12 条浏览记录？").assertIsDisplayed()
        composeRule.onAllNodesWithText("取消")[1].performClick()
        assertEquals(24, onlineHistoryViewModel.displayItems.size)
        composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).assertTextEquals("删除(12)")
        composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).performClick()
        composeRule.onNodeWithText("删除", substring = false).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { onlineHistoryViewModel.displayItems.size == 12 }
        assertTrue(onlineHistoryViewModel.displayItems.all { it.historyType == HistoryType.ANSWER })
    }

    /** 本地历史页点「管理」不能闪退，且官方多选动作条出现。 */
    @Test
    fun localHistoryManagementEntersSelectionWithoutCrash() {
        val navigator = composeRule.setScreenContent { LegacyLocalHistoryScreen() }
        composeRule.onNodeWithTag(HISTORY_SELECT_TOGGLE_TAG).performClick()
        composeRule.onNodeWithTag(HISTORY_SELECT_ALL_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(HISTORY_DELETE_SELECTED_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(HISTORY_SELECT_TOGGLE_TAG).performClick()
        composeRule.onAllNodesWithTag(HISTORY_DELETE_SELECTED_TAG).assertCountEquals(0)
        assertEquals(0, navigator.backCount)
        composeRule.onNodeWithTag(LEGACY_LOCAL_HISTORY_BACK_BUTTON_TAG).performClick()
        assertEquals(1, navigator.backCount)
    }

    private fun setOnlineHistoryScreen(itemCount: Int = 24): RecordingNavigator {
        val items = (1..itemCount).joinToString(",") { index ->
            val type = if (index % 2 == 1) "answer" else "article"
            val route = if (type == "answer") "answers" else "articles"
            """{"card_type":"single_card","data":{"header":{"icon":"","title":"${seedTitle(index)}"},"action":{"type":"router","url":"zhihu://$route/$index"},"extra":{"content_token":"token-$index","content_type":"$type","read_time":$index,"question_token":"fixture"}}}"""
        }
        ZhihuMockApi.mockJsonPrefix(
            HttpMethod.Get,
            "https://api.zhihu.com/unify-consumption/read_history",
            """{"data":[$items],"paging":{"is_end":true,"next":""}}""",
        )
        val navigator = composeRule.setScreenContent { OnlineHistoryScreen() }
        composeRule.waitUntil(timeoutMillis = 5_000) { onlineHistoryViewModel.displayItems.size == itemCount }
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
