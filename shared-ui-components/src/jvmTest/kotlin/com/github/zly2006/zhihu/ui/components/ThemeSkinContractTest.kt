package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.theme.ThemeColorSpec
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeMode
import com.github.zly2006.zhihu.theme.ThemePaletteStyle
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.ZhihuTheme
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

@OptIn(ExperimentalTestApi::class)
class ThemeSkinContractTest {
    private val originalTheme = ThemeManager.snapshot()
    private val originalUserHome = System.getProperty("user.home")
    private val testDirectory = Files.createTempDirectory("zhihu-theme-contract-").toFile()

    @BeforeTest
    fun isolateSettings() {
        System.setProperty("user.home", testDirectory.absolutePath)
        ThemeManager.load(originalTheme)
    }

    @AfterTest
    fun restoreSettings() {
        if (originalUserHome == null) System.clearProperty("user.home") else System.setProperty("user.home", originalUserHome)
        ThemeManager.load(originalTheme)
        testDirectory.deleteRecursively()
    }

    @Test
    fun changingSkinAndModeKeepsTheContentCompositionAlive() {
        var entered = 0
        var disposed = 0
        var contentIdentity: Any? = null
        runDesktopComposeUiTest(width = 412, height = 300) {
            setContent {
                ZhihuTheme {
                    val identity = remember { Any() }
                    SideEffect { contentIdentity = identity }
                    DisposableEffect(Unit) {
                        entered++
                        onDispose { disposed++ }
                    }
                    Text("主题切换保留页面")
                }
            }
            waitForIdle()
            val originalIdentity = contentIdentity
            repeat(4) { iteration ->
                for (style in ThemeStyle.entries) {
                    runOnIdle {
                        ThemeManager.setThemeStyle(style)
                        ThemeManager.setThemeMode(if (iteration % 2 == 0) ThemeMode.DARK else ThemeMode.LIGHT)
                    }
                    waitForIdle()
                    assertSame(originalIdentity, contentIdentity, "$style 不应重建页面状态")
                    assertEquals(1, entered, "$style 内容只能进入组合一次")
                    assertEquals(0, disposed, "$style 不应销毁导航和浮层宿主")
                }
            }
        }
    }

    @Test
    fun materialColourPreferencesDoNotRecolourMiuix() {
        for ((mode, oled) in listOf(ThemeMode.LIGHT to false, ThemeMode.DARK to false, ThemeMode.DARK to true)) {
            val native = if (mode == ThemeMode.DARK) darkColorScheme() else lightColorScheme()
            ThemeManager.load(
                originalTheme.copy(
                    themeStyle = ThemeStyle.MIUIX,
                    themeMode = mode,
                    amoledTheme = oled,
                    useDynamicColor = false,
                    customColor = 0xFFFF0055.toInt(),
                    backgroundColorLight = 0xFF204030.toInt(),
                    backgroundColorDark = 0xFF403020.toInt(),
                    paletteStyle = ThemePaletteStyle.VIBRANT,
                    colorSpec = ThemeColorSpec.SPEC_2021,
                ),
            )
            runDesktopComposeUiTest(width = 412, height = 620) {
                var primary = Color.Unspecified
                var background = Color.Unspecified
                var container = Color.Unspecified
                var enabled by mutableStateOf(true)
                var expectedCard = Color.Unspecified
                var expectedTitle = Color.Unspecified
                var expectedDescription = Color.Unspecified
                var expectedValue = Color.Unspecified
                var valueSize = TextUnit.Unspecified
                var track = Color.Unspecified
                var indicator = Color.Unspecified
                var selectedText = Color.Unspecified
                var grouped = Color.Unspecified
                setContent {
                    ZhihuTheme {
                        primary = LocalAppSkinTokens.current.primary
                        background = MaterialTheme.colorScheme.background
                        container = LocalAppSkinTokens.current.surfaceContainer
                        expectedCard = if (mode == ThemeMode.DARK) MiuixTheme.colorScheme.surfaceContainerHighest else MiuixTheme.colorScheme.surfaceContainer
                        expectedTitle = if (enabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.disabledOnSurface
                        expectedDescription = if (enabled) MiuixTheme.colorScheme.onSurfaceVariantSummary else MiuixTheme.colorScheme.disabledOnSurface
                        expectedValue = if (enabled) MiuixTheme.colorScheme.onSurfaceVariantSummary else MiuixTheme.colorScheme.disabledOnSecondaryVariant
                        valueSize = MiuixTheme.textStyles.body2.fontSize
                        track = MiuixTheme.colorScheme.surfaceContainerHigh
                        indicator = if (MiuixTheme.colorScheme.surfaceContainer == track) MiuixTheme.colorScheme.surfaceContainerHighest else MiuixTheme.colorScheme.surfaceContainer
                        selectedText = MiuixTheme.colorScheme.onBackground
                        grouped = appGroupedPageBackground()
                        AppScaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) {
                            Column {
                                Text("原生 Miuix 配色")
                                SettingItemGroup {
                                    SettingItem(title = { Text("设置标题") }, description = { Text("设置说明") }, enabled = enabled)
                                    SettingItemSelect(title = { Text("启动页面") }, options = listOf("主页", "关注"), selectedIndex = 1, onSelect = {}, enabled = enabled)
                                    Box(Modifier.fillMaxWidth().height(24.dp).testTag("native_card"))
                                }
                                AppTabRow(listOf("邀请", "推荐"), 1, {}, modifier = Modifier.testTag("native_tabs"))
                                AppSegmentedControl(listOf("默认", "最新"), 0, {}, modifier = Modifier.testTag("native_segments"))
                                Box(Modifier.fillMaxWidth().height(24.dp).testTag("native_page"))
                            }
                        }
                    }
                }
                waitForIdle()
                assertEquals(native.primary, primary, "$mode OLED=$oled 不应被 M3 种子色染色")
                assertEquals(if (oled) Color.Black else native.surface, background, "$mode OLED=$oled 兼容内容使用原生页面表面")
                assertEquals(if (oled) Color(0xFF050505) else native.surfaceContainer, container, "$mode OLED=$oled 保留中性表面")
                assertEquals(background, grouped)
                for ((tag, colour) in listOf("native_card" to expectedCard, "native_page" to background)) {
                    val pixels = onNodeWithTag(tag).captureToImage().toPixelMap()
                    assertEquals(colour.toArgb(), pixels[pixels.width / 2, pixels.height / 2].toArgb())
                }
                for ((tag, label, pad) in listOf(Triple("native_tabs", "推荐", AppDimens.PageHorizontalPadding + 4.dp), Triple("native_segments", "默认", 3.dp))) {
                    val node = onNodeWithTag(tag)
                    val pixels = node.captureToImage().toPixelMap()
                    val trackPixel = pixels[with(density) { pad.roundToPx() }, pixels.height / 2]
                    assertEquals(track.toArgb(), trackPixel.toArgb())
                    val bounds = node.fetchSemanticsNode().boundsInRoot
                    val labelBounds = onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    val selectedPixel = pixels[(labelBounds.center.x - bounds.left).toInt(), (labelBounds.top - bounds.top - with(density) { 3.dp.toPx() }).toInt()]
                    assertEquals(indicator.toArgb(), selectedPixel.toArgb())
                    assertTrue(selectedPixel != trackPixel)
                }
                for (label in listOf("邀请", "推荐", "默认", "最新")) {
                    val layouts = mutableListOf<TextLayoutResult>()
                    onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    assertEquals(if (label in listOf("推荐", "默认")) selectedText else expectedDescription, layouts.single().layoutInput.style.color)
                }
                for (value in listOf(true, false)) {
                    runOnIdle { enabled = value }
                    waitForIdle()
                    for ((label, colour) in listOf("设置标题" to expectedTitle, "设置说明" to expectedDescription, "关注" to expectedValue)) {
                        val layouts = mutableListOf<TextLayoutResult>()
                        onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                        assertEquals(colour, layouts.single().layoutInput.style.color)
                        if (label == "关注") assertEquals(valueSize, layouts.single().layoutInput.style.fontSize)
                    }
                }
            }
        }
    }

    @Test
    fun restoredCustomBackgroundIsOpaqueAndHasAReadableForegroundPair() {
        ThemeManager.load(originalTheme.copy(themeStyle = ThemeStyle.MATERIAL_EXPRESSIVE, themeMode = ThemeMode.LIGHT, backgroundColorLight = 0x40101010, amoledTheme = false))
        var background = Color.Unspecified
        var foreground = Color.Unspecified
        var page = Color.Unspecified
        var surface = Color.Unspecified
        var container = Color.Unspecified
        runDesktopComposeUiTest(width = 412, height = 200) {
            setContent { ZhihuTheme {
                background = MaterialTheme.colorScheme.background
                foreground = MaterialTheme.colorScheme.onBackground
                page = LocalAppSkinTokens.current.background
                surface = MaterialTheme.colorScheme.surface
                container = MaterialTheme.colorScheme.surfaceContainer
                Text("自定义背景")
            } }
            assertEquals(Color(0xFF101010), background)
            assertEquals(1f, background.alpha)
            val rendered = foreground.compositeOver(background)
            val contrast = (maxOf(rendered.luminance(), background.luminance()) + 0.05f) / (minOf(rendered.luminance(), background.luminance()) + 0.05f)
            assertTrue(contrast >= 4.5f)
            runOnIdle { ThemeManager.setBackgroundColor(Color(0x4037474F), isDark = false) }
            waitForIdle()
            assertEquals(0xFF37474F.toInt(), ThemeManager.snapshot().backgroundColorLight)
            assertEquals(Color(0xFF37474F), background)
            assertEquals(background, page)
            runOnIdle { ThemeManager.setThemeMode(ThemeMode.DARK); ThemeManager.setAmoledTheme(true) }
            waitForIdle()
            assertEquals(Color.Black, background)
            assertEquals(background, page)
            assertEquals(Color.Black, surface)
            assertEquals(Color(0xFF050505), container)
        }
    }

    @Test
    fun explicitScaffoldContainerIsNotReplacedBySkinDefaults() {
        val explicit = Color(0xFF37474F)
        for (style in ThemeStyle.entries) {
            ThemeManager.load(originalTheme.copy(themeStyle = style, themeMode = ThemeMode.LIGHT, amoledTheme = false))
            runDesktopComposeUiTest(width = 412, height = 300) {
                setContent {
                    ZhihuTheme {
                        AppScaffold(containerColor = explicit, contentWindowInsets = WindowInsets(0, 0, 0, 0)) {
                            Box(Modifier.fillMaxSize().testTag("explicit_scaffold_content"))
                        }
                    }
                }
                val pixels = onNodeWithTag("explicit_scaffold_content").captureToImage().toPixelMap()
                assertEquals(explicit.toArgb(), pixels[pixels.width / 2, pixels.height / 2].toArgb(), style.name)
            }
        }
    }

    @Test
    fun miuixMaterialProjectionKeepsErrorAndTertiaryContainerPairs() {
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            ThemeManager.load(originalTheme.copy(themeStyle = ThemeStyle.MIUIX, themeMode = mode, useDynamicColor = true, amoledTheme = false))
            val native = mutableListOf<Color>()
            val material = mutableListOf<Color>()
            runDesktopComposeUiTest(width = 412, height = 200) {
                setContent {
                    ZhihuTheme {
                        native.clear()
                        material.clear()
                        native += listOf(
                            MiuixTheme.colorScheme.errorContainer,
                            MiuixTheme.colorScheme.onErrorContainer,
                            MiuixTheme.colorScheme.tertiaryContainer,
                            MiuixTheme.colorScheme.onTertiaryContainer,
                        )
                        material += listOf(
                            MaterialTheme.colorScheme.errorContainer,
                            MaterialTheme.colorScheme.onErrorContainer,
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text("容器配对")
                    }
                }
                waitForIdle()
                assertEquals(native, material, "$mode 原生 error/tertiary 容器色对")
            }
        }
    }
}
