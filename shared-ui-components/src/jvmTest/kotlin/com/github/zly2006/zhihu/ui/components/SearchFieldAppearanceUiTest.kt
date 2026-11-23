package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeMode
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.ZhihuTheme
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 搜索与表单输入的原生外观、焦点过渡及大字布局契约。 */
@OptIn(ExperimentalTestApi::class)
class SearchFieldAppearanceUiTest {
    @Test
    fun miuixSearchFieldKeepsTheSameOutlineThroughoutFocusChanges() {
        val original = ThemeManager.snapshot()
        try {
            for (mode in arrayOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                ThemeManager.load(original.copy(themeStyle = ThemeStyle.MIUIX, themeMode = mode, amoledTheme = false))
                runDesktopComposeUiTest(width = 480, height = 180) {
                    mainClock.autoAdvance = false
                    val requester = FocusRequester()
                    lateinit var focusManager: FocusManager
                    setContent {
                        ZhihuTheme {
                            focusManager = LocalFocusManager.current
                            Box(Modifier.background(MaterialTheme.colorScheme.background).padding(12.dp).testTag("search_surface")) {
                                AppSearchField(
                                    rememberTextFieldState(),
                                    placeholder = "搜索",
                                    modifier = Modifier.width(360.dp).focusRequester(requester).testTag("search_field"),
                                )
                            }
                        }
                    }
                    runOnIdle { focusManager.clearFocus(force = true) }
                    mainClock.advanceTimeBy(800)
                    waitForIdle()
                    val input = onNode(hasSetTextAction())
                    input.assertIsNotFocused()
                    val resting = onNodeWithTag("search_field").captureToImage().toPixelMap()
                    val edge = with(density) { 8.dp.roundToPx() }
                    for (focused in booleanArrayOf(true, false)) {
                        runOnIdle {
                            if (focused) requester.requestFocus() else focusManager.clearFocus(force = true)
                        }
                        repeat(14) { frame ->
                            mainClock.advanceTimeBy(32)
                            waitForIdle()
                            if (focused) input.assertIsFocused() else input.assertIsNotFocused()
                            val pixels = onNodeWithTag("search_field").captureToImage().toPixelMap()
                            assertEquals(resting.width, pixels.width)
                            assertEquals(resting.height, pixels.height)
                            var altered = 0
                            var minX = pixels.width
                            var minY = pixels.height
                            var maxX = 0
                            var maxY = 0
                            for (y in 0 until pixels.height) {
                                for (x in 0 until pixels.width) {
                                    val perimeter = y < edge || y >= pixels.height - edge || x < edge || x >= pixels.width - edge
                                    if (perimeter && pixels[x, y] != resting[x, y]) {
                                        altered++
                                        minX = minOf(minX, x)
                                        minY = minOf(minY, y)
                                        maxX = maxOf(maxX, x)
                                        maxY = maxOf(maxY, y)
                                    }
                                }
                            }
                            assertEquals(0, altered, "$mode focus=$focused frame=$frame bounds=$minX,$minY-$maxX,$maxY 搜索轮廓不能因焦点动画增加描边")
                        }
                    }
                    mainClock.autoAdvance = true
                }
            }
        } finally {
            ThemeManager.load(original)
        }
    }

    /** 首页搜索入口虽不是输入态，提示文字仍必须在实际胶囊上可辨认。 */
    @Test
    fun miuixSearchBarHintHasReadableContrast() {
        val original = ThemeManager.snapshot()
        try {
            for ((mode, oled) in listOf(ThemeMode.LIGHT to false, ThemeMode.DARK to false, ThemeMode.DARK to true)) {
                ThemeManager.load(original.copy(themeStyle = ThemeStyle.MIUIX, themeMode = mode, amoledTheme = oled))
                runDesktopComposeUiTest(width = 412, height = 160) {
                    setContent {
                        ZhihuTheme {
                            AppSearchBar("搜索设置名称或关键词", {}, modifier = Modifier.testTag("search_entry"))
                        }
                    }
                    val entry = onNodeWithTag("search_entry")
                    val pixels = entry.captureToImage().toPixelMap()
                    val hintNode = onNode(
                        hasText("搜索设置名称或关键词") and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
                        useUnmergedTree = true,
                    )
                    val hintLayouts = mutableListOf<TextLayoutResult>()
                    hintNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(hintLayouts) }
                    val hintLayout = hintLayouts.single()
                    val background = pixels[pixels.width - 12, pixels.height / 2]
                    val bright = background.luminance()
                    val entryBounds = entry.fetchSemanticsNode().boundsInRoot
                    val textBounds = hintNode.fetchSemanticsNode().boundsInRoot
                    val left = floor(textBounds.left - entryBounds.left).toInt().coerceIn(0, pixels.width)
                    val right = ceil(textBounds.right - entryBounds.left).toInt().coerceIn(left, pixels.width)
                    val top = floor(textBounds.top - entryBounds.top).toInt().coerceIn(0, pixels.height)
                    val bottom = ceil(textBounds.bottom - entryBounds.top).toInt().coerceIn(top, pixels.height)
                    assertTrue(right > left && bottom > top, "MIUIX $mode OLED=$oled 提示必须有真实可见文字边界：$textBounds")
                    var readablePixels = 0
                    var strongestContrast = 1f
                    var strongestPixel = background
                    for (y in top until bottom) {
                        for (x in left until right) {
                            val value = pixels[x, y].luminance()
                            val contrast = (maxOf(value, bright) + 0.05f) / (minOf(value, bright) + 0.05f)
                            if (contrast > strongestContrast) {
                                strongestContrast = contrast
                                strongestPixel = pixels[x, y]
                            }
                            if (contrast >= 4.5f) readablePixels++
                        }
                    }
                    assertTrue(
                        readablePixels >= 10,
                        "MIUIX $mode OLED=$oled fontScale=${hintLayout.layoutInput.density.fontScale} 搜索入口提示对比不足：$readablePixels " +
                            "pixels=${pixels.width}x${pixels.height} scan=$left,$top-$right,$bottom background=$background " +
                            "foreground=${hintLayout.layoutInput.style.color} strongestPixel=$strongestPixel maxContrast=$strongestContrast textBounds=$textBounds " +
                            "entryBounds=$entryBounds size=${hintLayout.size} " +
                            "paragraphHeight=${hintLayout.multiParagraph.height} constraints=${hintLayout.layoutInput.constraints} " +
                            "os=${System.getProperty("os.name")} java=${System.getProperty("java.version")}",
                    )
                }
            }
        } finally {
            ThemeManager.load(original)
        }
    }

    @Test
    fun searchInputKeepsPlaceholderTypographyInsideTopBar() {
        val original = ThemeManager.snapshot()
        try {
            for (themeStyle in ThemeStyle.entries) {
                ThemeManager.load(original.copy(themeStyle = themeStyle, themeMode = ThemeMode.LIGHT))
                for (fontScale in floatArrayOf(1f, 2f)) {
                    runDesktopComposeUiTest(width = 1067, height = 400) {
                        val query = TextFieldState()
                        setContent {
                            ZhihuTheme {
                                val density = LocalDensity.current
                                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                                    AppTopBar(title = {
                                        AppSearchField(query, placeholder = "搜索内容", modifier = Modifier.testTag("search_probe"))
                                    }, onBack = {})
                                }
                            }
                        }
                        val placeholderLayouts = mutableListOf<TextLayoutResult>()
                        val placeholderNode = onNodeWithText("搜索内容", useUnmergedTree = true)
                        placeholderNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(placeholderLayouts) }
                        assertEquals(
                            fontScale,
                            placeholderLayouts
                                .single()
                                .layoutInput.density.fontScale,
                            0.01f,
                            "提示必须保留系统字号比例",
                        )
                        val placeholderLayout = placeholderLayouts.single()
                        assertTrue(
                            !placeholderLayout.didOverflowHeight,
                            "$themeStyle fontScale=$fontScale 提示字形不能被裁切：size=${placeholderLayout.size} " +
                                "paragraphHeight=${placeholderLayout.multiParagraph.height} constraints=${placeholderLayout.layoutInput.constraints} " +
                                "lineBottom=${placeholderLayout.getLineBottom(0)} bounds=${placeholderNode.fetchSemanticsNode().boundsInRoot}",
                        )
                        assertTrue(
                            placeholderNode.fetchSemanticsNode().boundsInRoot.height >= placeholderLayouts.single().size.height - 1f,
                            "$themeStyle fontScale=$fontScale 提示可见区须包含全部字高",
                        )
                        runOnIdle { query.setTextAndPlaceCursorAtEnd("搜索内容") }
                        val inputLayouts = mutableListOf<TextLayoutResult>()
                        val inputNode = onNodeWithText("搜索内容", useUnmergedTree = true)
                        inputNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(inputLayouts) }
                        val placeholder = placeholderLayouts.single().layoutInput.style
                        val input = inputLayouts.single().layoutInput.style
                        assertEquals(placeholder.fontSize, input.fontSize, "$themeStyle fontScale=$fontScale 输入不应继承顶栏的大标题字号")
                        val inputLayout = inputLayouts.single()
                        assertEquals(fontScale, inputLayout.layoutInput.density.fontScale, 0.01f, "输入必须保留系统字号比例")
                        assertTrue(
                            !inputLayout.didOverflowHeight,
                            "$themeStyle fontScale=$fontScale 输入字形不能被裁切：size=${inputLayout.size} " +
                                "paragraphHeight=${inputLayout.multiParagraph.height} exceededMaxLines=${inputLayout.multiParagraph.didExceedMaxLines} " +
                                "density=${inputLayout.layoutInput.density} constraints=${inputLayout.layoutInput.constraints} " +
                                "lineBottom=${inputLayout.getLineBottom(0)} textBounds=${inputNode.fetchSemanticsNode().boundsInRoot} " +
                                "fieldBounds=${onNodeWithTag("search_probe").fetchSemanticsNode().boundsInRoot} style=${inputLayout.layoutInput.style} " +
                                "os=${System.getProperty("os.name")} java=${System.getProperty("java.version")}",
                        )
                        assertTrue(
                            inputNode.fetchSemanticsNode().boundsInRoot.height >= inputLayout.size.height - 1f,
                            "$themeStyle fontScale=$fontScale 输入可见区须包含全部字高",
                        )
                    }
                }
            }
        } finally {
            ThemeManager.load(original)
        }
    }

    @Test
    fun miuixServerFieldLabelHasReadableContrast() {
        val original = ThemeManager.snapshot()
        try {
            for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                ThemeManager.load(original.copy(themeStyle = ThemeStyle.MIUIX, themeMode = mode, useDynamicColor = true))
                runDesktopComposeUiTest(width = 412, height = 240) {
                    setContent {
                        ZhihuTheme {
                            AppTextField(rememberTextFieldState(), labelText = "WebDAV目录地址", modifier = Modifier.testTag("server_probe"))
                        }
                    }
                    val layouts = mutableListOf<TextLayoutResult>()
                    onNodeWithText("WebDAV目录地址", useUnmergedTree = true)
                        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    val pixels = onNodeWithTag("server_probe").captureToImage().toPixelMap()
                    val background = pixels[pixels.width / 2, 4]
                    val foreground = layouts
                        .single()
                        .layoutInput.style.color
                    val a = foreground.alpha
                    val blended = androidx.compose.ui.graphics.Color(
                        foreground.red * a + background.red * (1f - a),
                        foreground.green * a + background.green * (1f - a),
                        foreground.blue * a + background.blue * (1f - a),
                    )
                    val contrast = (maxOf(blended.luminance(), background.luminance()) + 0.05f) /
                        (minOf(blended.luminance(), background.luminance()) + 0.05f)
                    assertTrue(contrast >= 4.5f, "$mode 服务器字段唯一标签对比不足：$contrast")
                }
            }
        } finally {
            ThemeManager.load(original)
        }
    }

    @Test
    fun miuixSearchPlaceholderHasReadableContrast() {
        val original = ThemeManager.snapshot()
        try {
            for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                ThemeManager.load(original.copy(themeStyle = ThemeStyle.MIUIX, themeMode = mode, useDynamicColor = true))
                runDesktopComposeUiTest(width = 412, height = 240) {
                    setContent {
                        ZhihuTheme {
                            AppSearchField(rememberTextFieldState(), placeholder = "搜索设置名称或关键词", modifier = Modifier.testTag("search_probe"))
                        }
                    }
                    val layouts = mutableListOf<TextLayoutResult>()
                    onNodeWithText("搜索设置名称或关键词", useUnmergedTree = true)
                        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    val pixels = onNodeWithTag("search_probe").captureToImage().toPixelMap()
                    val background = pixels[pixels.width / 2, 4]
                    val foreground = layouts
                        .single()
                        .layoutInput.style.color
                    val a = foreground.alpha
                    val blended = androidx.compose.ui.graphics.Color(
                        foreground.red * a + background.red * (1f - a),
                        foreground.green * a + background.green * (1f - a),
                        foreground.blue * a + background.blue * (1f - a),
                    )
                    val contrast = (maxOf(blended.luminance(), background.luminance()) + 0.05f) /
                        (minOf(blended.luminance(), background.luminance()) + 0.05f)
                    assertTrue(contrast >= 4.5f, "$mode 启用搜索框提示对比不足：$contrast")
                }
            }
        } finally {
            ThemeManager.load(original)
        }
    }

    @Test
    fun draggableUtilityGroupStaysAboveTheActualBottomAvoidance() = runDesktopComposeUiTest(width = 412, height = 300) {
        setContent {
            ZhihuTheme {
                Box(Modifier.fillMaxSize().testTag("utility_pane")) {
                    DraggableFabColumn(bottomAvoidance = 80.dp) {
                        AppButton("刷新", {}, modifier = Modifier.testTag("utility_button"))
                    }
                }
            }
        }
        val pane = onNodeWithTag("utility_pane").fetchSemanticsNode().boundsInRoot
        val button = onNodeWithTag("utility_button").fetchSemanticsNode().boundsInRoot
        val avoidance = with(density) { 80.dp.toPx() }
        assertTrue(button.bottom <= pane.bottom - avoidance + 1f)
        assertTrue(button.left >= pane.left && button.right <= pane.right)
    }

    @Test
    fun pageHeaderKeepsFullTitleAndSearchEntryIsClickable() = runDesktopComposeUiTest(width = 412, height = 300) {
        val title = "热榜完整标题与已有分页结果不能被缩略或裁切"
        var opens = 0
        setContent {
            ZhihuTheme {
                Column {
                    AppPageHeader(title = title, subtitle = "实际列表标题")
                    AppSearchBar("搜索内容", { opens++ }, modifier = Modifier.testTag("search_entry_click"))
                }
            }
        }
        onNodeWithText(title).assertIsDisplayed()
        onNodeWithTag("search_entry_click").performClick()
        assertEquals(1, opens)
    }
}
