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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.desktopSettingsStore
import com.github.zly2006.zhihu.theme.THEME_PALETTE_STYLE_PREFERENCE_KEY
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeMode
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.ZhihuTheme
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AppearanceNavigationSettingsUiTest {
    @Test
    fun deepLinkToColourRowHiddenByDynamicColourHighlightsTheDynamicColourSwitch() =
        withSettingsFixture(useDynamicColor = true) {
            runDesktopComposeUiTest(width = 412, height = 892) {
                mainClock.autoAdvance = false
                setContent {
                    TestScreen { AppearanceSettingsScreen(setting = APPEARANCE_SETTINGS_CUSTOM_COLOR_KEY, onExit = {}) }
                }
                assertRowHighlightedOnEntry("使用 Material You 动态取色")
            }
        }

    @Test
    fun deepLinkIntoCollapsedColourOptionsExpandsTheGroupAndHighlightsTheRow() =
        withSettingsFixture(useDynamicColor = false) {
            runDesktopComposeUiTest(width = 412, height = 892) {
                mainClock.autoAdvance = false
                setContent {
                    TestScreen { AppearanceSettingsScreen(setting = THEME_PALETTE_STYLE_PREFERENCE_KEY, onExit = {}) }
                }
                assertRowHighlightedOnEntry("调色风格")
            }
        }

    @Test
    fun leavingNavigationSettingsAsksTheShellToReloadPreferences() =
        withSettingsFixture(useDynamicColor = true) {
            var exits = 0
            val shown = mutableStateOf(true)
            runDesktopComposeUiTest(width = 412, height = 892) {
                setContent {
                    TestScreen {
                        if (shown.value) NavigationSettingsScreen(setting = null, onExit = { exits++ })
                    }
                }
                assertEquals(0, exits)
                runOnIdle { shown.value = false }
                waitForIdle()
                assertEquals(1, exits)
            }
        }

    @Test
    fun appearanceKeepsSavedNavigationAndIndependentButtonPreferences() =
        withSettingsFixture(useDynamicColor = false, prepare = {
            putBoolean("duo3_all", true)
            putBoolean("duo3_home_account", true)
            putStringSet(BOTTOM_BAR_ITEMS_PREFERENCE_KEY, setOf("Home", "Follow", "Daily", "OnlineHistory", "Account"))
            putString(BOTTOM_BAR_ITEM_ORDER_PREFERENCE_KEY, "Home,Follow,Daily,OnlineHistory,Account")
            putString(START_DESTINATION_PREFERENCE_KEY, "Account")
            putBoolean("showRefreshFab", true)
            putBoolean("buttonSkipAnswer", true)
        }) {
            runDesktopComposeUiTest(width = 720, height = 1000) {
                setContent { TestScreen { AppearanceSettingsScreen(null, {}) } }
                onNodeWithText("123Duo3 的 UI/UX 改进（beta）").assertDoesNotExist()
                onNodeWithText("启用所有修改并关闭浮动按钮").assertDoesNotExist()
                onNodeWithTag("appearanceSettings.feedCardStyle").performScrollTo().assert(hasAnyDescendant(hasText("卡片")))
                onNode(hasText("显示发布按钮") and hasClickAction()).performScrollTo().performClick()
                waitForIdle()
                val settings = desktopSettingsStore()
                assertEquals(false, settings.getBoolean("showCreateFab", true))
                assertEquals(true, settings.getBoolean("showRefreshFab", false))
                assertEquals(true, settings.getBoolean("buttonSkipAnswer", false))
                assertEquals(setOf("Home", "Follow", "Daily", "OnlineHistory", "Account"), settings.getStringSet(BOTTOM_BAR_ITEMS_PREFERENCE_KEY, emptySet()))
                assertEquals("Home,Follow,Daily,OnlineHistory,Account", settings.getString(BOTTOM_BAR_ITEM_ORDER_PREFERENCE_KEY, ""))
                assertEquals("Account", settings.getString(START_DESTINATION_PREFERENCE_KEY, ""))
            }
        }

    @Test
    fun pageTransitionSelectionPreservesDisabledPreferenceAndReenablesChosenStyle() =
        withSettingsFixture(useDynamicColor = true, prepare = {
            putBoolean("enable_predictive_back", false)
            putString(PAGE_TRANSITION_PREFERENCE_KEY, "aosp")
        }) {
            runDesktopComposeUiTest(width = 412, height = 892) {
                setContent { TestScreen { NavigationSettingsScreen(setting = "enable_predictive_back", onExit = {}) } }
                val settings = desktopSettingsStore()
                val row = onNode(hasText("页面转场") and hasClickAction())
                row.performScrollTo()
                onNodeWithText("无动画").assertExists()
                assertEquals(false, settings.getBoolean("enable_predictive_back", true))
                assertEquals("aosp", settings.getString(PAGE_TRANSITION_PREFERENCE_KEY, ""))

                row.performClick()
                onNodeWithText("侧滑").performClick()
                mainClock.advanceTimeBy(200)
                waitForIdle()
                assertEquals(true, settings.getBoolean("enable_predictive_back", false))
                assertEquals("miuix", settings.getString(PAGE_TRANSITION_PREFERENCE_KEY, ""))

                row.performClick()
                onNodeWithText("无动画").performClick()
                mainClock.advanceTimeBy(200)
                waitForIdle()
                assertEquals(false, settings.getBoolean("enable_predictive_back", true))
                assertEquals("miuix", settings.getString(PAGE_TRANSITION_PREFERENCE_KEY, ""))

                row.performClick()
                onNodeWithText("AOSP").performClick()
                mainClock.advanceTimeBy(200)
                waitForIdle()
                assertEquals(true, settings.getBoolean("enable_predictive_back", false))
                assertEquals("aosp", settings.getString(PAGE_TRANSITION_PREFERENCE_KEY, ""))
                runOnIdle { settings.putBoolean("enable_predictive_back", false) }
                onNodeWithText("无动画").assertExists()
            }
        }

    @Test
    fun floatingStyleKeepsSkinAndLegacyToggleWhileObservingExternalChanges() =
        withSettingsFixture(useDynamicColor = true, prepare = {
            putBoolean(FLOATING_BOTTOM_BAR_PREFERENCE_KEY, false)
            putString(FLOATING_BOTTOM_BAR_STYLE_PREFERENCE_KEY, "apple")
        }) {
            runDesktopComposeUiTest(width = 412, height = 892) {
                setContent { TestScreen { AppearanceSettingsScreen(FLOATING_BOTTOM_BAR_STYLE_PREFERENCE_KEY, {}) } }
                val settings = desktopSettingsStore()
                val originalTheme = ThemeManager.snapshot()
                val row = onNode(hasText("悬浮底栏样式") and hasClickAction())
                onNodeWithTag("appearanceSettings.floatingBottomBarStyle").performScrollTo()
                onNodeWithText("Apple").assertExists()
                assertEquals(false, settings.getBoolean(FLOATING_BOTTOM_BAR_PREFERENCE_KEY, true))
                runOnIdle { settings.putBoolean(FLOATING_BOTTOM_BAR_PREFERENCE_KEY, true) }
                row.performClick()
                onNodeWithText("默认").performClick()
                mainClock.advanceTimeBy(200)
                waitForIdle()
                assertEquals("default", settings.getString(FLOATING_BOTTOM_BAR_STYLE_PREFERENCE_KEY, ""))
                runOnIdle { settings.putString(FLOATING_BOTTOM_BAR_STYLE_PREFERENCE_KEY, "apple") }
                onNodeWithText("Apple").assertExists()
                assertEquals(originalTheme, ThemeManager.snapshot())
                assertEquals(true, settings.getBoolean(FLOATING_BOTTOM_BAR_PREFERENCE_KEY, false))
            }
        }

    @Composable
    private fun TestScreen(content: @Composable () -> Unit) {
        ZhihuTheme {
            CompositionLocalProvider(LocalNavigator provides Navigator({}, {}), content = content)
        }
    }

    /** 高亮未暴露语义状态，故比较同一行高亮与淡出后的像素。 */
    private fun ComposeUiTest.assertRowHighlightedOnEntry(title: String) {
        val row = onNode(hasText(title) and hasClickAction())
        mainClock.advanceTimeBy(2_500)
        waitForIdle()
        val highlighted = row.captureToImage().toPixelMap()
        mainClock.advanceTimeBy(6_000)
        waitForIdle()
        val settled = row.captureToImage().toPixelMap()
        val width = minOf(highlighted.width, settled.width)
        val height = minOf(highlighted.height, settled.height)
        var changed = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (highlighted[x, y] != settled[x, y]) changed++
            }
        }
        assertTrue(changed > width * height / 4, "“$title”行进入页面后没有高亮：${width}x$height 中只有 $changed 个像素变化")
    }

    private fun withSettingsFixture(
        useDynamicColor: Boolean,
        prepare: SettingsStore.() -> Unit = {},
        body: () -> Unit,
    ) {
        val originalUserHome = System.getProperty("user.home")
        val originalTheme = ThemeManager.snapshot()
        val testDirectory = Files.createTempDirectory("zhihu-appearance-ui-").toFile()
        try {
            System.setProperty("user.home", testDirectory.absolutePath)
            desktopSettingsStore().apply {
                putString("uiThemeStyle", ThemeStyle.MATERIAL_EXPRESSIVE.name)
                putString("themeMode", ThemeMode.LIGHT.name)
                putBoolean("useDynamicColor", useDynamicColor)
                prepare()
            }
            body()
        } finally {
            System.setProperty("user.home", originalUserHome)
            ThemeManager.load(originalTheme)
            testDirectory.deleteRecursively()
        }
    }
}
