package com.github.zly2006.zhihu.ui.subscreens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.platform.desktopSettingsStore
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeMode
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.ui.components.appGroupedCardColor
import java.nio.file.Files
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.test.Test
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
            desktopSettingsStore().also { settings ->
                settings.putString("uiThemeStyle", "MATERIAL_EXPRESSIVE")
                settings.putBoolean(DUO3_TIQIAN_MARKDOWN_PREFERENCE_KEY, false)
            }
            runDesktopComposeUiTest(width = 412, height = 1600) {
                var highlight = Color.Unspecified
                var card = Color.Unspecified
                mainClock.autoAdvance = false
                setContent {
                    ZhihuTheme {
                        highlight = MaterialTheme.colorScheme.primaryContainer
                        card = appGroupedCardColor()
                        CompositionLocalProvider(LocalNavigator provides Navigator({}, {})) {
                            ReadingSettingsScreen(setting = DUO3_TIQIAN_MATH_FONT_PREFERENCE_KEY)
                        }
                    }
                }

                fun switchRowBackground(): Color {
                    val pixels = onNodeWithTag(APPEARANCE_SETTINGS_TIQIAN_MARKDOWN_TAG).captureToImage().toPixelMap()
                    return pixels[4, pixels.height / 2]
                }

                val samples = mutableListOf<Color>()
                repeat(40) {
                    mainClock.advanceTimeBy(100)
                    samples += switchRowBackground()
                }
                mainClock.advanceTimeBy(5_000)
                val settled = switchRowBackground()

                val expectedShift = channelDistance(card, blend(highlight, card, 0.4f))
                assertTrue(expectedShift >= 6, "主题里高亮色与卡片色太接近，无法判断：$expectedShift")
                val strongestShift = samples.maxOf { channelDistance(it, settled) }
                assertTrue(
                    strongestShift * 2 >= expectedShift,
                    "提椠开关没有被高亮：背景最大变化 $strongestShift，0.4 透明度高亮应约为 $expectedShift",
                )
            }
        } finally {
            System.setProperty("user.home", originalUserHome)
            ThemeManager.load(originalTheme)
            testDirectory.deleteRecursively()
        }
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
