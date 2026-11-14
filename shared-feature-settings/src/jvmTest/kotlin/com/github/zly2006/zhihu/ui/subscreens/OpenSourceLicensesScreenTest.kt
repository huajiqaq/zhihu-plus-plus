package com.github.zly2006.zhihu.ui.subscreens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.platform.desktopSettingsStore
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeMode
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.ZhihuTheme
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 许可证清单缺失必须显示失败原因与可读的重试按钮，不能当成空列表。 */
@OptIn(ExperimentalTestApi::class)
class OpenSourceLicensesScreenTest {
    @Test
    fun missingManifestIsReportedAsAFailureWithRetry() = withIsolatedDesktopHome {
        assertNull(
            javaClass.classLoader.getResource("aboutlibraries.json"),
            "测试前提：classpath 上不能有许可证清单",
        )
        for (skin in ThemeStyle.entries) {
            desktopSettingsStore().putString("uiThemeStyle", skin.name)
            ThemeManager.load(ThemeManager.snapshot().copy(themeStyle = skin, themeMode = ThemeMode.LIGHT, amoledTheme = false))
            runSkikoComposeUiTest(size = Size(320f, 640f)) {
                var background = Color.Unspecified
                setContent {
                    ZhihuTheme {
                        background = LocalAppSkinTokens.current.surface
                        CompositionLocalProvider(LocalNavigator provides Navigator({}, {})) {
                            OpenSourceLicensesScreen()
                        }
                    }
                }
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithText("aboutlibraries.json", substring = true).fetchSemanticsNodes().isNotEmpty()
                }
                onNode(hasText("重试") and hasClickAction()).assertIsEnabled()
                val layouts = mutableListOf<TextLayoutResult>()
                onNodeWithText("重试", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val foreground = layouts
                    .single()
                    .layoutInput.style.color

                fun luminance(color: Color): Double {
                    fun linear(channel: Float): Double = if (channel <= 0.04045f) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
                    return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
                }
                val first = luminance(foreground)
                val second = luminance(background)
                val contrast = (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
                assertTrue(contrast >= 4.5, "$skin 启用重试文字对比度=$contrast")
            }
        }
    }
}
