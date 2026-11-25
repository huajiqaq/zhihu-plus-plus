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

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.AppNavDisplay
import com.github.zly2006.zhihu.navigation.AppNavHost
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.MainTabs
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.navigation.Notification
import com.github.zly2006.zhihu.navigation.rememberAppNavHost
import com.github.zly2006.zhihu.test.MainActivityComposeRule
import com.github.zly2006.zhihu.test.RecordingNavigator
import com.github.zly2006.zhihu.test.resetAppPreferences
import com.github.zly2006.zhihu.test.setScreenContent
import com.github.zly2006.zhihu.ui.subscreens.READING_SETTINGS_BACK_BUTTON_TAG
import com.github.zly2006.zhihu.ui.subscreens.ReadingSettingsScreen
import com.github.zly2006.zhihu.ui.subscreens.SETTINGS_SEARCH_BACK_BUTTON_TAG
import com.github.zly2006.zhihu.ui.subscreens.SETTINGS_SEARCH_INPUT_TAG
import com.github.zly2006.zhihu.ui.subscreens.SETTINGS_SEARCH_RESULTS_TAG
import com.github.zly2006.zhihu.ui.subscreens.SettingsSearchScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsSearchScreenInstrumentedTest {
    @get:Rule
    val composeRule: MainActivityComposeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        composeRule.resetAppPreferences(forceMock = true)
    }

    /**
     * 设置搜索的筛选与目标导航行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/262
     */
    @Test
    fun searchScreen_matchesPageNamesConfiguredByGroupedSettings() {
        val navigator = setSearchScreenContent()

        composeRule
            .onNodeWithTag(SETTINGS_SEARCH_INPUT_TAG)
            .performTextInput("热榜")
        composeRule
            .onNodeWithTag("settingsSearch.result.appearance.bottomBar")
            .assertIsDisplayed()
            .performClick()

        assertEquals(
            listOf(Account.NavigationSettings(setting = "appearanceSettings.bottomBarSection")),
            navigator.destinations,
        )
    }

    /**
     * 设置搜索的筛选与目标导航行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/262
     */
    @Test
    fun searchScreen_canJumpToSystemAndNotificationSettingsEntries() {
        val navigator = setSearchScreenContent()

        composeRule
            .onNodeWithTag(SETTINGS_SEARCH_INPUT_TAG)
            .performTextInput("GitHub Token")
        composeRule
            .onNodeWithTag("settingsSearch.result.system.githubToken")
            .assertIsDisplayed()
            .performClick()
        assertEquals(
            listOf(Account.SystemAndUpdateSettings(setting = "githubToken")),
            navigator.destinations,
        )

        composeRule
            .onNodeWithTag(SETTINGS_SEARCH_INPUT_TAG)
            .performTextClearance()
        composeRule
            .onNodeWithTag(SETTINGS_SEARCH_INPUT_TAG)
            .performTextInput("系统通知")
        composeRule
            .onAllNodesWithTag("settingsSearch.result.notification.displayInAppNotifications")
            .assertCountEquals(0)
        composeRule
            .onNodeWithTag("settingsSearch.result.notification.systemNotifications")
            .assertIsDisplayed()
            .performClick()

        assertEquals(
            listOf(
                Account.SystemAndUpdateSettings(setting = "githubToken"),
                Notification.NotificationSettings(setting = "systemNotifications"),
            ),
            navigator.destinations,
        )
    }

    /**
     * 设置搜索的筛选与目标导航行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/262
     */
    @Test
    fun searchScreen_exposesAccountMaintenanceWithoutDeveloperMode() {
        val navigator = setSearchScreenContent()

        composeRule
            .onNodeWithTag(SETTINGS_SEARCH_INPUT_TAG)
            .performTextInput("导入 Cookie")
        composeRule
            .onNodeWithTag("settingsSearch.result.developer.page")
            .assertIsDisplayed()
            .performClick()
        assertEquals(listOf(Account.DeveloperSettings), navigator.destinations)

        composeRule.onNodeWithTag(SETTINGS_SEARCH_INPUT_TAG).performTextClearance()
        composeRule.onNodeWithTag(SETTINGS_SEARCH_INPUT_TAG).performTextInput("签名请求")
        composeRule.onAllNodesWithTag("settingsSearch.result.developer.page").assertCountEquals(0)

        composeRule.onNodeWithTag(SETTINGS_SEARCH_INPUT_TAG).performTextClearance()
        composeRule.onNodeWithTag(SETTINGS_SEARCH_INPUT_TAG).performTextInput("Duo3")
        composeRule.onAllNodesWithTag("settingsSearch.result.appearance.duo3").assertCountEquals(0)
    }

    @Test
    fun searchScreen_queryEditsResetScrollAndOnlyReturningToTheSameVisitRestoresIt() {
        lateinit var host: AppNavHost
        composeRule.setScreenContent {
            host = rememberAppNavHost(MainTabs)
            CompositionLocalProvider(LocalNavigator provides Navigator(host::open, host::onBack)) {
                // “屏蔽”结果的首组就是内容过滤；高屏可能无需滚动就能看到付费内容行。
                AppNavDisplay(
                    navHost = host,
                    modifier = Modifier.wrapContentHeight(Alignment.Top).heightIn(max = 480.dp),
                ) {
                    entry<MainTabs> { }
                    entry<Account.SettingsSearch> { SettingsSearchScreen() }
                    entry<Account.ReadingSettings> { ReadingSettingsScreen(it.setting) }
                }
            }
        }
        composeRule.runOnIdle { host.open(Account.SettingsSearch) }
        val input = composeRule.onNodeWithTag(SETTINGS_SEARCH_INPUT_TAG)
        val results = composeRule.onNodeWithTag(SETTINGS_SEARCH_RESULTS_TAG)

        fun offset() = results.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

        fun query() = input.fetchSemanticsNode().config[SemanticsProperties.EditableText].text

        fun scrollIntoFilters() {
            results.performScrollToKey("内容过滤")
            composeRule.onNodeWithTag("settingsSearch.result.recommend.blockPaidContent").performScrollTo()
            assertTrue(offset() > 0f)
        }
        scrollIntoFilters()
        input.performTextInput("内容")
        composeRule.waitUntil("新查询必须回到首项", 5_000) { offset() == 0f }
        assertEquals(0f, offset(), 0f)
        scrollIntoFilters()
        input.performTextReplacement("屏蔽")
        composeRule.waitUntil("替换查询必须回到首项", 5_000) { offset() == 0f }
        assertEquals(0f, offset(), 0f)
        scrollIntoFilters()
        input.performTextClearance()
        composeRule.waitUntil("清空查询必须回到首项", 5_000) { offset() == 0f }
        assertEquals(0f, offset(), 0f)

        input.performTextInput("阅读")
        Espresso.closeSoftKeyboard()
        results.performScrollToKey("阅读与排版")
        val result = composeRule.onNodeWithTag("settingsSearch.result.reading.lineHeight")
        result.performScrollTo().assertIsDisplayed()
        val before = offset()
        assertTrue(before > 0f)
        result.performClick()
        composeRule.onNodeWithTag(READING_SETTINGS_BACK_BUTTON_TAG).assertIsDisplayed().performClick()
        assertEquals("阅读", query())
        assertEquals(before, offset(), 0f)
        result.assertIsDisplayed()

        composeRule.onNodeWithTag(SETTINGS_SEARCH_BACK_BUTTON_TAG).performClick()
        composeRule.runOnIdle { host.open(Account.SettingsSearch) }
        assertEquals("", query())
        assertEquals(0f, offset(), 0f)
    }

    private fun setSearchScreenContent(): RecordingNavigator =
        composeRule.setScreenContent {
            SettingsSearchScreen()
        }
}
