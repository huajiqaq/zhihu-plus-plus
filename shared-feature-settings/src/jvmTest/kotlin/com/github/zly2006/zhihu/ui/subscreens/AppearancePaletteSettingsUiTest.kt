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
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.github.zly2006.zhihu.desktop.desktopZhihuDataFile
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.platform.desktopSettingsStore
import com.github.zly2006.zhihu.theme.AMOLED_THEME_PREFERENCE_KEY
import com.github.zly2006.zhihu.theme.MIUIX_STYLE_PREFERENCE_KEY
import com.github.zly2006.zhihu.theme.MiuixStyle
import com.github.zly2006.zhihu.theme.THEME_COLOR_SPEC_PREFERENCE_KEY
import com.github.zly2006.zhihu.theme.THEME_PALETTE_STYLE_PREFERENCE_KEY
import com.github.zly2006.zhihu.theme.THEME_STYLE_PREFERENCE_KEY
import com.github.zly2006.zhihu.theme.ThemeColorSpec
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeMode
import com.github.zly2006.zhihu.theme.ThemePaletteStyle
import com.github.zly2006.zhihu.theme.ThemeSnapshot
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.theme.readThemeSnapshot
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class AppearancePaletteSettingsUiTest {
    @Test
    fun miuixUsesNativeColoursAndPreservesMaterialChoicesAcrossSkinSwitches() {
        val originalHome = System.getProperty("user.home")
        val originalTheme = ThemeManager.snapshot()
        val directory = Files.createTempDirectory("zhihu-native-palette-ui-").toFile()
        try {
            System.setProperty("user.home", directory.absolutePath)
            val settings = desktopSettingsStore()
            settings.putBoolean("useDynamicColor", false)
            settings.putInt("customThemeColor", 0xFFCC4455.toInt())
            settings.putBoolean(APP_BLUR_PREFERENCE_KEY, false)
            settings.putBoolean(FLOATING_BOTTOM_BAR_PREFERENCE_KEY, false)
            assertEquals(MiuixStyle.HYPEROS_3, readThemeSnapshot(settings).miuixStyle)
            assertEquals(MiuixStyle.HYPEROS_3, MiuixStyle.fromName("unknown"))
            val otherEntries = settings.allEntries()
            ThemeManager.load(originalTheme.copy(themeStyle = ThemeStyle.MIUIX, themeMode = ThemeMode.LIGHT, useDynamicColor = false, customColor = 0xFFCC4455.toInt(), miuixStyle = MiuixStyle.Default))
            val nativeTheme = ThemeManager.snapshot()
            runDesktopComposeUiTest(width = 540, height = 1200) {
                setContent {
                    ZhihuTheme {
                        CompositionLocalProvider(LocalNavigator provides Navigator({}, {})) {
                            AppearanceSettingsScreen(setting = null, onExit = {})
                        }
                    }
                }
                onNodeWithText("启用自定义配色", useUnmergedTree = true).assertDoesNotExist()
                onNodeWithText("自定义主题色", useUnmergedTree = true).assertDoesNotExist()
                onNodeWithTag(APPEARANCE_SETTINGS_MORE_COLORS_TAG, useUnmergedTree = true).assertDoesNotExist()
                onNodeWithText("HyperOS 3", useUnmergedTree = true).assertExists()
                onNodeWithText("HyperOS 4", useUnmergedTree = true).performClick()
                waitForIdle()
                assertEquals(MiuixStyle.HYPEROS_4.name, settings.getString(MIUIX_STYLE_PREFERENCE_KEY, ""))
                assertEquals(nativeTheme.copy(miuixStyle = MiuixStyle.HYPEROS_4), ThemeManager.snapshot())
                assertEquals(otherEntries, settings.allEntries() - MIUIX_STYLE_PREFERENCE_KEY)
                onNodeWithText("已保存 HyperOS 4；悬浮玻璃已关闭，当前使用实心效果。", useUnmergedTree = true).assertExists()
                onNodeWithText("Material 3 Expressive", useUnmergedTree = true).performClick()
                waitForIdle()
                onNodeWithText("使用 Material You 动态取色", useUnmergedTree = true).assertExists()
                onNodeWithText("自定义主题色", useUnmergedTree = true).assertExists()
                assertFalse(settings.getBoolean("useDynamicColor", true))
                assertEquals(0xFFCC4455.toInt(), ThemeManager.snapshot().customColor)
                assertEquals(ThemeStyle.MATERIAL_EXPRESSIVE.name, settings.getString(THEME_STYLE_PREFERENCE_KEY, ""))
            }
        } finally {
            System.setProperty("user.home", originalHome)
            ThemeManager.load(originalTheme)
            directory.deleteRecursively()
        }
    }

    @Test
    fun resetMoreColorsUpdatesOnlyItsFivePreferencesAndSurvivesRestart() {
        val originalHome = System.getProperty("user.home")
        val originalTheme = ThemeManager.snapshot()
        val directory = Files.createTempDirectory("zhihu-reset-more-colors-").toFile()
        try {
            System.setProperty("user.home", directory.absolutePath)
            val settings = desktopSettingsStore()
            settings.putBoolean("useDynamicColor", false)
            settings.putInt("customThemeColor", 0xFFCC4455.toInt())
            settings.putString(THEME_STYLE_PREFERENCE_KEY, ThemeStyle.MATERIAL_EXPRESSIVE.name)
            settings.putString("themeMode", ThemeMode.DARK.name)
            settings.putBoolean(AMOLED_THEME_PREFERENCE_KEY, true)
            settings.putString(THEME_PALETTE_STYLE_PREFERENCE_KEY, ThemePaletteStyle.NEUTRAL.name)
            settings.putString(THEME_COLOR_SPEC_PREFERENCE_KEY, ThemeColorSpec.SPEC_2021.name)
            settings.putInt("backgroundColorLight", 0xFFFFCCAA.toInt())
            settings.putInt("backgroundColorDark", 0xFF112233.toInt())
            settings.putInt("luotianyi_color", 0xFF556677.toInt())
            val groupKeys = setOf(
                THEME_PALETTE_STYLE_PREFERENCE_KEY,
                THEME_COLOR_SPEC_PREFERENCE_KEY,
                "backgroundColorLight",
                "backgroundColorDark",
                "luotianyi_color",
            )
            val otherEntries = settings.allEntries() - groupKeys
            val defaults = ThemeSnapshot()
            val expected = readThemeSnapshot(settings).copy(
                paletteStyle = defaults.paletteStyle,
                colorSpec = defaults.colorSpec,
                backgroundColorLight = defaults.backgroundColorLight,
                backgroundColorDark = defaults.backgroundColorDark,
            )
            val savedFile = desktopZhihuDataFile("settings.properties")
            for (reopened in listOf(false, true)) {
                if (reopened) {
                    // 桌面按路径缓存设置实例；新目录只带上次写到磁盘的文件。
                    System.setProperty("user.home", directory.resolve("reopened").absolutePath)
                    savedFile.copyTo(desktopZhihuDataFile("settings.properties"))
                }
                val store = desktopSettingsStore()
                ThemeManager.load(readThemeSnapshot(store))
                runDesktopComposeUiTest(width = 540, height = 1200) {
                    setContent {
                        ZhihuTheme {
                            CompositionLocalProvider(LocalNavigator provides Navigator({}, {})) {
                                AppearanceSettingsScreen(setting = null, onExit = {})
                            }
                        }
                    }
                    onNodeWithText("恢复默认").assertDoesNotExist()
                    onNodeWithTag(APPEARANCE_SETTINGS_MORE_COLORS_TAG).performScrollTo().performClick()
                    if (!reopened) onNodeWithText("恢复默认").performScrollTo().performClick()
                    waitForIdle()
                    assertEquals(expected, ThemeManager.snapshot())
                    onNodeWithText(defaults.paletteStyle.displayName, useUnmergedTree = true).assertExists()
                    onNodeWithText(defaults.colorSpec.displayName, useUnmergedTree = true).assertExists()
                    onNodeWithText("自定义背景颜色").performScrollTo().performClick()
                    onNodeWithContentDescription("预设颜色 #121212").assertIsSelected()
                    onNodeWithText("取消").performClick()
                    onNodeWithText("唤起浏览器主题色").performScrollTo().performClick()
                    onNodeWithContentDescription("预设颜色 #66CCFF").assertIsSelected()
                    onNodeWithText("取消").performClick()
                }
                assertEquals(otherEntries, store.allEntries() - groupKeys)
            }
        } finally {
            System.setProperty("user.home", originalHome)
            ThemeManager.load(originalTheme)
            directory.deleteRecursively()
        }
    }

    @Test
    fun materialDynamicColourOffersAndPersistsExtendedPalettes() {
        val originalHome = System.getProperty("user.home")
        val originalTheme = ThemeManager.snapshot()
        val directory = Files.createTempDirectory("zhihu-material-palette-ui-").toFile()
        try {
            System.setProperty("user.home", directory.absolutePath)
            val settings = desktopSettingsStore()
            settings.putBoolean("useDynamicColor", true)
            ThemeManager.load(originalTheme.copy(themeStyle = ThemeStyle.MATERIAL_EXPRESSIVE, themeMode = ThemeMode.LIGHT, useDynamicColor = true))
            runDesktopComposeUiTest(width = 540, height = 1200) {
                setContent {
                    ZhihuTheme {
                        CompositionLocalProvider(LocalNavigator provides Navigator({}, {})) {
                            AppearanceSettingsScreen(setting = null, onExit = {})
                        }
                    }
                }
                onNodeWithTag(APPEARANCE_SETTINGS_MORE_COLORS_TAG, useUnmergedTree = true).performScrollTo().performClick()
                onNodeWithText("调色风格", useUnmergedTree = true).performScrollTo().performClick()
                onNodeWithText("单色", useUnmergedTree = true).performScrollTo().performClick()
                waitForIdle()
                assertEquals("MONOCHROME", settings.getString(THEME_PALETTE_STYLE_PREFERENCE_KEY, ""))
                onNodeWithText("2021", useUnmergedTree = true).assertExists()
            }
        } finally {
            System.setProperty("user.home", originalHome)
            ThemeManager.load(originalTheme)
            directory.deleteRecursively()
        }
    }
}
