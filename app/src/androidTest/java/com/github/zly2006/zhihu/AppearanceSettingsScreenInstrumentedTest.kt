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
import android.content.SharedPreferences
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.Daily
import com.github.zly2006.zhihu.navigation.Follow
import com.github.zly2006.zhihu.navigation.Home
import com.github.zly2006.zhihu.navigation.HotList
import com.github.zly2006.zhihu.navigation.MyCollections
import com.github.zly2006.zhihu.navigation.OnlineHistory
import com.github.zly2006.zhihu.test.performVerticalSwipeCycle
import com.github.zly2006.zhihu.test.resetAppPreferences
import com.github.zly2006.zhihu.test.setScreenContent
import com.github.zly2006.zhihu.ui.PREFERENCE_NAME
import com.github.zly2006.zhihu.ui.subscreens.APPEARANCE_SETTINGS_BOTTOM_BAR_SECTION_KEY
import com.github.zly2006.zhihu.ui.subscreens.BOTTOM_BAR_ITEMS_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.BOTTOM_BAR_ITEM_ORDER_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.COLLECTION_DIRECT_BROWSE_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.NavigationSettingsScreen
import com.github.zly2006.zhihu.ui.subscreens.PREF_SHOW_CONTENT_END_MARKER
import com.github.zly2006.zhihu.ui.subscreens.PREF_SHOW_PAGE_TURN_FAB
import com.github.zly2006.zhihu.ui.subscreens.PREF_VOLUME_KEY_PAGE_TURN
import com.github.zly2006.zhihu.ui.subscreens.PageTurnSettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearanceSettingsScreenInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val preferences: SharedPreferences
        get() = composeRule.activity.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    /**
     * 导航栏编辑项应保持统一触摸高度，调整顺序后应持久保存；行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/354
     */
    @Test
    fun bottomBarRowsKeepUniformHeightAndMoveOrderPersists() {
        // 已选、未选及不可移除的账号行应有相同的触摸高度，同时保持排序操作可持久保存。
        composeRule.resetAppPreferences(forceMock = true)
        val selectedItems = listOf(Follow.name, Daily.name, OnlineHistory.name, MyCollections.name, HotList.name, Account.name)
        preferences
            .edit()
            .putStringSet(BOTTOM_BAR_ITEMS_PREFERENCE_KEY, selectedItems.toSet())
            .putString(BOTTOM_BAR_ITEM_ORDER_PREFERENCE_KEY, selectedItems.joinToString(","))
            .commit()
        composeRule.setScreenContent {
            NavigationSettingsScreen(setting = APPEARANCE_SETTINGS_BOTTOM_BAR_SECTION_KEY, onExit = {})
        }
        clickSettingRow("底部导航栏项目")

        composeRule.onNodeWithTag("appearanceSettings:bottomBar:item:${OnlineHistory.name}").assertDoesNotExist()
        scrollUntilTagDisplayed("appearanceSettings:bottomBar:item:${Home.name}")
        val rows = listOf(Daily.name to "日报", Home.name to "主页", Account.name to "账号设置").map { (key, title) ->
            val tag = "appearanceSettings:bottomBar:item:$key"
            val rowMatcher = hasAnyDescendant(hasText(title)) and (hasTestTag(tag) or hasAnyDescendant(hasTestTag(tag)))
            composeRule
                .onAllNodes(rowMatcher and !hasAnyDescendant(rowMatcher), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .single()
        }
        val metrics = composeRule.activity.resources.displayMetrics
        val rowGeometry = rows.joinToString("; ") { row ->
            val parent = row.parent
            val textNodes = composeRule
                .onAllNodes(
                    SemanticsMatcher.keyIsDefined(SemanticsProperties.Text) and
                        hasAnyAncestor(SemanticsMatcher("完整行 ${row.id}") { it.id == row.id }),
                    useUnmergedTree = true,
                ).fetchSemanticsNodes()
            "row=${row.id} size=${row.size} bounds=${row.boundsInRoot} " +
                "parent=${parent?.id} parentSize=${parent?.size} parentBounds=${parent?.boundsInRoot} " +
                "text=${textNodes.map { "${it.config[SemanticsProperties.Text]} size=${it.size} bounds=${it.boundsInRoot}" }}"
        } + " density=${metrics.density} fontScale=${composeRule.activity.resources.configuration.fontScale}"
        assertTrue("完整行必须有列表父节点: $rowGeometry", rows.all { it.parent != null })
        assertEquals("必须测量同一列表层的完整行: $rowGeometry", 1, rows.map { it.parent?.id }.distinct().size)
        val selectedWidth = rows[0].size.width.toDouble()
        val selectedHeight = rows[0].size.height.toDouble()
        assertEquals("同层完整行必须同宽: $rowGeometry", selectedWidth, rows[1].size.width.toDouble(), 0.5)
        assertEquals("同层完整行必须同宽: $rowGeometry", selectedWidth, rows[2].size.width.toDouble(), 0.5)
        assertEquals("日报与主页必须同高: $rowGeometry", selectedHeight, rows[1].size.height.toDouble(), 0.5)
        assertEquals("日报与账号设置必须同高: $rowGeometry", selectedHeight, rows[2].size.height.toDouble(), 0.5)
        assertTrue("完整行至少48dp: $rowGeometry", selectedHeight >= 48 * metrics.density)
        composeRule.onNodeWithTag("appearanceSettings:bottomBar:item:${Home.name}").assertIsNotEnabled()
        composeRule.onNodeWithTag("appearanceSettings:bottomBar:item:${Account.name}").assertIsNotEnabled().assertIsOn()

        composeRule.onNodeWithTag("appearanceSettings:bottomBar:moveDown:${Daily.name}").performClick()
        waitUntilStringPreference(
            BOTTOM_BAR_ITEM_ORDER_PREFERENCE_KEY,
            expected = listOf(Follow.name, MyCollections.name, Daily.name, HotList.name, Account.name).joinToString(","),
        )
    }

    @Test
    fun legacyCollectionSettingFallsBackToBottomBarItemsWithoutChangingSavedValue() {
        composeRule.resetAppPreferences(forceMock = true)
        preferences.edit().putBoolean(COLLECTION_DIRECT_BROWSE_PREFERENCE_KEY, false).commit()
        composeRule.setScreenContent {
            NavigationSettingsScreen(setting = COLLECTION_DIRECT_BROWSE_PREFERENCE_KEY, onExit = {})
        }

        waitUntilDisplayed(hasText("底部导航栏项目"))
        composeRule.onNodeWithText("收藏直达浏览").assertDoesNotExist()
        assertTrue(preferences.contains(COLLECTION_DIRECT_BROWSE_PREFERENCE_KEY))
        assertFalse(preferences.getBoolean(COLLECTION_DIRECT_BROWSE_PREFERENCE_KEY, true))
    }

    /**
     * Android 应提供翻页设置并保留已保存的开关；行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/630
     */
    @Test
    fun pageTurnSettingsAreAvailableOnAndroid() {
        composeRule.resetAppPreferences(forceMock = true)
        preferences
            .edit()
            .putBoolean(PREF_VOLUME_KEY_PAGE_TURN, true)
            .putBoolean(PREF_SHOW_PAGE_TURN_FAB, true)
            .commit()
        composeRule.setScreenContent { PageTurnSettingsScreen(PREF_SHOW_PAGE_TURN_FAB) }

        waitUntilDisplayed(hasText("显示翻页悬浮按钮"))
        assertTrue(composeRule.onAllNodesWithText("电纸书翻页").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithText("音量键翻页").assertExists()
        composeRule.onNodeWithText("翻页切换回答").assertExists()
        composeRule.onNodeWithText("翻页距离").assertExists()
        composeRule.onNodeWithText("显示翻页位置线").assertExists()
        composeRule.onNodeWithText("显示内容结束标记").assertExists()
        assertFalse(preferences.getBoolean(PREF_SHOW_CONTENT_END_MARKER, false))
        assertTrue(preferences.getBoolean(PREF_VOLUME_KEY_PAGE_TURN, false))
        assertTrue(preferences.getBoolean(PREF_SHOW_PAGE_TURN_FAB, false))
    }

    private fun scrollUntilDisplayed(matcher: SemanticsMatcher, maxSwipes: Int = 12) {
        repeat(maxSwipes) {
            if (isDisplayed(matcher)) {
                return
            }
            composeRule.onRoot().performTouchInput { swipeUp() }
            composeRule.waitForIdle()
        }
        composeRule.onNode(matcher, useUnmergedTree = true).assertIsDisplayed()
    }

    private fun clickSettingRow(title: String) {
        val rowMatcher = hasAnyDescendant(hasText(title)) and hasClickAction()
        scrollUntilDisplayed(rowMatcher)
        composeRule.onNode(rowMatcher, useUnmergedTree = true).performClick()
    }

    private fun scrollUntilTagDisplayed(tag: String, maxSwipes: Int = 12) {
        repeat(maxSwipes) {
            if (isTagDisplayed(tag)) {
                return
            }
            composeRule.onRoot().performTouchInput { swipeUp() }
            composeRule.waitForIdle()
        }
        repeat(maxSwipes) {
            if (isTagDisplayed(tag)) {
                return
            }
            composeRule.onRoot().performVerticalSwipeCycle()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun waitUntilDisplayed(matcher: SemanticsMatcher, timeoutMillis: Long = 5_000) {
        composeRule.waitUntil(timeoutMillis) { isDisplayed(matcher) }
        composeRule.onNode(matcher, useUnmergedTree = true).assertIsDisplayed()
    }

    private fun waitUntilStringPreference(key: String, expected: String, timeoutMillis: Long = 5_000) {
        composeRule.waitUntil(timeoutMillis) { preferences.getString(key, null) == expected }
    }

    private fun isDisplayed(matcher: SemanticsMatcher): Boolean = runCatching {
        composeRule.onNode(matcher, useUnmergedTree = true).assertIsDisplayed()
    }.isSuccess

    private fun isTagDisplayed(tag: String): Boolean = runCatching {
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
    }.isSuccess ||
        runCatching {
            composeRule.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
        }.isSuccess
}
