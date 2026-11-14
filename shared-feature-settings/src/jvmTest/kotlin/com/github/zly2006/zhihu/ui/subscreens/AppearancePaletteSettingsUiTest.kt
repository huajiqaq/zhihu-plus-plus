package com.github.zly2006.zhihu.ui.subscreens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.platform.desktopSettingsStore
import com.github.zly2006.zhihu.theme.THEME_PALETTE_STYLE_PREFERENCE_KEY
import com.github.zly2006.zhihu.theme.THEME_STYLE_PREFERENCE_KEY
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeMode
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.ZhihuTheme
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
            ThemeManager.load(originalTheme.copy(themeStyle = ThemeStyle.MIUIX, themeMode = ThemeMode.LIGHT, useDynamicColor = false, customColor = 0xFFCC4455.toInt()))
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
