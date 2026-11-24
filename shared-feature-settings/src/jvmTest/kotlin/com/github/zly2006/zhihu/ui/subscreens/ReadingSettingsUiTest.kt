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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.github.zly2006.zhihu.desktop.desktopZhihuDataFile
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.platform.desktopSettingsStore
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeMode
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.ui.ARTICLE_USE_WEBVIEW_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.components.appGroupedCardColor
import java.nio.file.Files
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ReadingSettingsUiTest {
    /** 数学字体行被隐藏时深链须高亮提椠开关；高亮无语义，使用行背景像素验证。 */
    @Test
    fun hiddenMathFontDeepLinkHighlightsTiqianSwitch() {
        val originalUserHome = System.getProperty("user.home")
        val originalTheme = ThemeManager.snapshot()
        val testDirectory = Files.createTempDirectory("zhihu-reading-layout-ui-").toFile()
        try {
            System.setProperty("user.home", testDirectory.absolutePath)
            ThemeManager.load(originalTheme.copy(themeStyle = ThemeStyle.MATERIAL_EXPRESSIVE, themeMode = ThemeMode.LIGHT))
            val settings = desktopSettingsStore()
            settings.putString("uiThemeStyle", "MATERIAL_EXPRESSIVE")
            settings.putBoolean(TIQIAN_MARKDOWN_PREFERENCE_KEY, false)
            runDesktopComposeUiTest(width = 412, height = 500) {
                val visit = mutableIntStateOf(0)
                var highlight = Color.Unspecified
                var card = Color.Unspecified
                mainClock.autoAdvance = false
                setContent {
                    ZhihuTheme {
                        highlight = MaterialTheme.colorScheme.primaryContainer
                        card = appGroupedCardColor()
                        CompositionLocalProvider(LocalNavigator provides Navigator({}, {})) {
                            key(visit.intValue) { ReadingSettingsScreen(setting = TIQIAN_MATH_FONT_PREFERENCE_KEY) }
                        }
                    }
                }

                val parent = onNodeWithTag(APPEARANCE_SETTINGS_TIQIAN_MARKDOWN_TAG)
                val child = onNode(hasText("正文：使用非衬线数学字体") and hasClickAction())
                val scroll = onNodeWithTag(READING_LAYOUT_SETTINGS_SCROLL_TAG)

                fun scrollOffset() = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

                fun background(row: SemanticsNodeInteraction): Color {
                    onRoot().performMouseInput { moveTo(Offset.Zero) }
                    mainClock.advanceTimeBy(300)
                    val pixels = row.assertIsDisplayed().captureToImage().toPixelMap()
                    return pixels[4, pixels.height / 2]
                }

                fun reveal(row: SemanticsNodeInteraction) {
                    // 语义滚动自身会等待滚动动画，期间必须让测试时钟前进。
                    mainClock.autoAdvance = true
                    row.performScrollTo()
                    waitForIdle()
                    mainClock.autoAdvance = false
                }

                fun assertPrompt(row: SemanticsNodeInteraction) {
                    mainClock.advanceTimeBy(2_000)
                    assertTrue(scrollOffset() > 0f, "短视口没有自动滚动到目标：row=${row.getUnclippedBoundsInRoot()} root=${onRoot().getUnclippedBoundsInRoot()}")
                    val bounds = row.getUnclippedBoundsInRoot()
                    assertTrue(bounds.top >= onNodeWithTag(READING_SETTINGS_BACK_BUTTON_TAG).getUnclippedBoundsInRoot().bottom)
                    assertTrue(bounds.bottom <= onRoot().getUnclippedBoundsInRoot().bottom)
                    val lit = background(row)
                    mainClock.advanceTimeBy(5_000)
                    val settled = background(row)
                    val expectedShift = channelDistance(card, blend(highlight, card, 0.4f))
                    assertTrue(expectedShift >= 6 && channelDistance(lit, settled) * 2 >= expectedShift, "目标行没有高亮：$lit → $settled")
                }

                fun assertUnlit(row: SemanticsNodeInteraction) {
                    val actual = background(row)
                    assertTrue(channelDistance(card, actual) <= 2, "同次进入不应重新高亮：actual=$actual card=$card bounds=${row.getUnclippedBoundsInRoot()}")
                }

                child.assertDoesNotExist()
                assertPrompt(parent)
                parent.performClick()
                mainClock.advanceTimeBy(2_000)
                reveal(child)
                assertUnlit(child)

                runOnIdle { visit.intValue++ }
                assertPrompt(child)
                assertUnlit(parent)
                runOnIdle { settings.putInt(PREF_LINE_HEIGHT, 150) }
                mainClock.advanceTimeBy(1_500)
                assertUnlit(child)
                parent.performClick()
                mainClock.advanceTimeBy(2_000)
                child.assertDoesNotExist()
                reveal(parent)
                assertUnlit(parent)
                parent.performClick()
                mainClock.advanceTimeBy(2_000)
                reveal(child)
                assertUnlit(child)

                reveal(onNodeWithTag(READING_LAYOUT_SETTINGS_PREVIEW_TAG))
                val before = scrollOffset()
                runOnIdle { settings.putBoolean(TIQIAN_MARKDOWN_PREFERENCE_KEY, false) }
                mainClock.advanceTimeBy(1_000)
                runOnIdle { settings.putBoolean(TIQIAN_MARKDOWN_PREFERENCE_KEY, true) }
                mainClock.advanceTimeBy(2_000)
                assertEquals(before, scrollOffset(), "子行重挂不应抢走用户的滚动位置")
                val bottomSetting = onNodeWithText("自动隐藏回答底部按钮")
                reveal(bottomSetting)
                bottomSetting.assertIsOn()
            }
        } finally {
            System.setProperty("user.home", originalUserHome)
            ThemeManager.load(originalTheme)
            testDirectory.deleteRecursively()
        }
    }

    @Test
    fun resetTypographyRestoresOnlyGroupDefaultsAndPersists() = withIsolatedDesktopHome {
        val settings = desktopSettingsStore().apply {
            putInt(PREF_FONT_SIZE, 130)
            putInt(PREF_LINE_HEIGHT, 200)
            putInt(PREF_BLOCK_SPACING, 150)
            putBoolean(TIQIAN_MARKDOWN_PREFERENCE_KEY, true)
            putString(TIQIAN_MATH_FONT_PREFERENCE_KEY, "stix")
            putBoolean("duo3_tiqian_markdown", true)
            putString("duo3_tiqian_math_font", "stix")
            putBoolean(ARTICLE_USE_WEBVIEW_PREFERENCE_KEY, true)
            putBoolean("webviewHardwareAcceleration", false)
            putString("webviewCustomFontName", "fixture.otf")
            putString("answerSwitchMode", "horizontal")
        }
        val expected = settings.allEntries() + mapOf(
            PREF_FONT_SIZE to "100",
            PREF_LINE_HEIGHT to "160",
            PREF_BLOCK_SPACING to "100",
            TIQIAN_MARKDOWN_PREFERENCE_KEY to "false",
            TIQIAN_MATH_FONT_PREFERENCE_KEY to "lete",
        )
        runDesktopComposeUiTest(width = 540, height = 1000) {
            setContent {
                ZhihuTheme {
                    CompositionLocalProvider(LocalNavigator provides Navigator({}, {})) { ReadingSettingsScreen() }
                }
            }
            val tiqian = onNodeWithTag(APPEARANCE_SETTINGS_TIQIAN_MARKDOWN_TAG)
            tiqian.assertIsOn()
            onNodeWithText("200%", useUnmergedTree = true).assertExists()
            onNodeWithText("恢复默认").performScrollTo().performClick()
            waitForIdle()
            onAllNodesWithText("100%", useUnmergedTree = true).assertCountEquals(2)
            onNodeWithText("160%", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            tiqian.assertIsOff()
            onNodeWithText("正文：使用非衬线数学字体").assertDoesNotExist()
        }

        // 桌面设置实例按路径缓存；新目录只带上次写入的文件。
        val saved = desktopZhihuDataFile("settings.properties")
        System.setProperty("user.home", saved.resolveSibling("reopened").absolutePath)
        saved.copyTo(desktopZhihuDataFile("settings.properties"))
        assertEquals(expected, desktopSettingsStore().allEntries())
    }

    private fun blend(top: Color, bottom: Color, alpha: Float): Color = Color(
        red = top.red * alpha + bottom.red * (1 - alpha),
        green = top.green * alpha + bottom.green * (1 - alpha),
        blue = top.blue * alpha + bottom.blue * (1 - alpha),
    )

    private fun channelDistance(a: Color, b: Color): Int = max(
        abs(((a.red - b.red) * 255).roundToInt()),
        max(abs(((a.green - b.green) * 255).roundToInt()), abs(((a.blue - b.blue) * 255).roundToInt())),
    )
}
