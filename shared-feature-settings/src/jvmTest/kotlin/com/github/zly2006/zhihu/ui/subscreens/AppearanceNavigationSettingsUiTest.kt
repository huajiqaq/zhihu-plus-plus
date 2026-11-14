package com.github.zly2006.zhihu.ui.subscreens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
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
    fun legacyMasterPreservesOtherDefaultsAndNormalizesBottomBarSelection() =
        withSettingsFixture(useDynamicColor = false, prepare = {
            putBoolean("duo3_home_account", false)
            putStringSet(BOTTOM_BAR_ITEMS_PREFERENCE_KEY, setOf("Home", "Follow", "Daily", "OnlineHistory", "Account"))
            putString(BOTTOM_BAR_ITEM_ORDER_PREFERENCE_KEY, "Home,Follow,Daily,OnlineHistory,Account")
            putString(START_DESTINATION_PREFERENCE_KEY, "Account")
        }) {
            runDesktopComposeUiTest(width = 720, height = 1000) {
                setContent { TestScreen { AppearanceSettingsScreen("123Duo3", {}) } }
                onNode(hasText("启用所有修改并关闭浮动按钮") and hasClickAction()).performScrollTo().performClick()
                waitForIdle()
                val settings = desktopSettingsStore()
                assertEquals(true, settings.getBoolean("duo3_home_account", false))
                assertEquals(true, settings.getBoolean("duo3_card_layout", false))
                assertEquals(false, settings.getBoolean("showRefreshFab", true))
                assertEquals(false, settings.getBoolean("buttonSkipAnswer", true))
                assertTrue("Account" !in settings.getStringSet(BOTTOM_BAR_ITEMS_PREFERENCE_KEY, emptySet()))
                assertTrue(settings.getString(START_DESTINATION_PREFERENCE_KEY, "") in settings.getStringSet(BOTTOM_BAR_ITEMS_PREFERENCE_KEY, emptySet()))
                onNode(hasText("启用所有修改并关闭浮动按钮") and hasClickAction()).performClick()
                waitForIdle()
                assertEquals(false, settings.getBoolean("duo3_home_account", true))
                assertTrue("Account" in settings.getStringSet(BOTTOM_BAR_ITEMS_PREFERENCE_KEY, emptySet()))
                assertEquals(false, settings.getBoolean("showRefreshFab", true))
                assertEquals(false, settings.getBoolean("buttonSkipAnswer", true))
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
            ThemeManager.load(
                originalTheme.copy(
                    themeStyle = ThemeStyle.MATERIAL_EXPRESSIVE,
                    themeMode = ThemeMode.LIGHT,
                    useDynamicColor = useDynamicColor,
                ),
            )
            desktopSettingsStore().prepare()
            body()
        } finally {
            System.setProperty("user.home", originalUserHome)
            ThemeManager.load(originalTheme)
            testDirectory.deleteRecursively()
        }
    }
}
