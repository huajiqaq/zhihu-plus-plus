/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.desktopSettingsStore
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeMode
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.ZhihuTheme
import java.nio.file.Files
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import top.yukonga.miuix.kmp.basic.FloatingActionButton as NativeMiuixFab

@OptIn(ExperimentalTestApi::class)
class FloatingPlacementUiTest {
    /** 浮动导航保持原生 label/selected/click 行为；拖到新项只提交一次，原地拖动不触发重选。 */
    @Test
    fun fabColumnKeepsNativeMeasurementAndStableAppearance() {
        for (style in ThemeStyle.entries) withIsolatedSettings(style) {
        runDesktopComposeUiTest(width = 640, height = 260) {
            var generation by mutableStateOf(0)
            val placedY = mutableListOf<Float>()
            setContent {
                ZhihuTheme {
                    Box(Modifier.fillMaxSize()) {
                        Row(Modifier.padding(top = 28.dp)) {
                            Spacer(Modifier.width(180.dp))
                            Box(Modifier.width(240.dp).height(120.dp).testTag("column_pane")) {
                                key(generation) { DraggableFabColumn(
                                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 16.dp).testTag("native_fab_column").onGloballyPositioned { placedY += it.positionInRoot().y },
                                    preferenceName = "boundedColumn",
                                    bottomAvoidance = 20.dp,
                                ) {
                                    AppFloatingActionButton(onClick = {}, modifier = Modifier.testTag("column_native_fab")) {
                                        Icon(Icons.Default.Refresh, "刷新")
                                    }
                                } }
                            }
                        }
                        Box(Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                            if (style == ThemeStyle.MIUIX) {
                                NativeMiuixFab(onClick = {}, modifier = Modifier.testTag("reference_native_fab")) {
                                    Icon(Icons.Default.Refresh, "原生尺寸对照")
                                }
                            } else {
                                FloatingActionButton(onClick = {}, modifier = Modifier.testTag("reference_native_fab")) {
                                    Icon(Icons.Default.Refresh, "原生尺寸对照")
                                }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            val pane = onNodeWithTag("column_pane").getUnclippedBoundsInRoot()
            val column = onNodeWithTag("native_fab_column").getUnclippedBoundsInRoot()
            val fab = onNodeWithTag("column_native_fab").getUnclippedBoundsInRoot()
            val native = onNodeWithTag("reference_native_fab").getUnclippedBoundsInRoot()
            assertEquals((native.right - native.left).value, (fab.right - fab.left).value, 1f, "$style 不得把原生 FAB 裁进固定 56dp 列")
            assertEquals((native.bottom - native.top).value, (fab.bottom - fab.top).value, 1f, "$style 使用原生高度")
            assertEquals(pane.right.value - 16f, fab.right.value, 1f, "$style 保持调用侧 end 留白")
            assertEquals(pane.bottom.value - 20f, fab.bottom.value, 1f, "$style 按所在短 pane 的实际高度贴底")
            assertTrue(column.top >= pane.top && fab.left >= pane.left, "pane=$pane column=$column fab=$fab")
            repeat(2) {
                waitForIdle()
                val positions = runOnIdle { placedY.toList() }
                assertTrue(positions.isNotEmpty())
                assertTrue(positions.all { abs(it - positions.last()) <= 1f }, "$style 初现位置移动：$positions")
                runOnIdle { placedY.clear(); generation++ }
            }
        }
    }

    }

    @Test
    fun floatingBarKeepsNativeLabelsSelectionAndDragInNarrowPane() {
        for (style in ThemeStyle.entries) {
            withIsolatedSettings(style) {
                runDesktopComposeUiTest(width = 640, height = 260) {
                    var selected by mutableStateOf(0)
                    val clicks = mutableListOf<Int>()
                    var activeColor = Color.Unspecified
                    var idleColor = Color.Unspecified
                    setContent {
                        ZhihuTheme {
                            activeColor = if (style == ThemeStyle.MIUIX) LocalAppSkinTokens.current.primary else MaterialTheme.colorScheme.primary
                            idleColor = if (style == ThemeStyle.MIUIX) LocalAppSkinTokens.current.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            Row(Modifier.fillMaxSize().padding(top = 28.dp)) {
                                Spacer(Modifier.width(180.dp))
                                Box(Modifier.width(240.dp).height(120.dp).testTag("bar_pane")) {
                                    AppFloatingBar(
                                        items = listOf("主页", "关注").mapIndexed { index, label ->
                                            BottomBarItem(label, Icons.Default.Home, "bounded_tab_$index", selected == index) {
                                                clicks += index
                                                selected = index
                                            }
                                        },
                                        bottomPadding = 12.dp,
                                    )
                                }
                            }
                        }
                    }
                    waitForIdle()
                    onNodeWithText("主页", useUnmergedTree = true).assertExists()
                    onNodeWithText("关注", useUnmergedTree = true).assertExists()
                    onNodeWithTag("bounded_tab_0").assertIsSelected().assertHasClickAction()
                    val pane = onNodeWithTag("bar_pane").getUnclippedBoundsInRoot()
                    for (index in 0..1) {
                        val item = onNodeWithTag("bounded_tab_$index").getUnclippedBoundsInRoot()
                        assertTrue(item.left >= pane.left && item.right <= pane.right && item.bottom <= pane.bottom, "pane=$pane item=$item")
                    }
                    onNodeWithTag("bounded_tab_0").performTouchInput {
                        down(center)
                        moveTo(center + Offset(68f, 0f), delayMillis = 480)
                    }
                    waitForIdle()
                    onNodeWithTag("bounded_tab_0").assertIsSelected()
                    assertTrue(clicks.isEmpty(), "拖动中不能提前导航")
                    val fromLayout = mutableListOf<TextLayoutResult>()
                    val toLayout = mutableListOf<TextLayoutResult>()
                    onNodeWithText("主页", useUnmergedTree = true)
                        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(fromLayout) }
                    onNodeWithText("关注", useUnmergedTree = true)
                        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(toLayout) }
                    val tabBounds = onNodeWithTag("bounded_tab_0").getUnclippedBoundsInRoot()
                    val fraction = 68f / with(density) { (tabBounds.right - tabBounds.left).toPx() }
                    assertEquals(
                        lerp(activeColor, idleColor, fraction).toArgb(),
                        fromLayout
                            .single()
                            .layoutInput.style.color
                            .toArgb(),
                        "$style 旧标签应随胶囊移走而淡出蓝色",
                    )
                    assertEquals(
                        lerp(idleColor, activeColor, fraction).toArgb(),
                        toLayout
                            .single()
                            .layoutInput.style.color
                            .toArgb(),
                        "$style 目标标签应与图标共享连续跟手色",
                    )
                    onNodeWithTag("bounded_tab_0").performTouchInput { up() }
                    waitForIdle()
                    onNodeWithTag("bounded_tab_1").assertIsSelected()
                    assertEquals(listOf(1), clicks, "拖动提交目标项一次")
                    onNodeWithTag("bounded_tab_1").performTouchInput {
                        swipe(center, center + Offset(32f, 0f), durationMillis = 320)
                    }
                    waitForIdle()
                    assertEquals(listOf(1), clicks, "原地拖动不调用当前 tab 的 onClick")
                    onNodeWithTag("bounded_tab_0").performClick()
                    onNodeWithTag("bounded_tab_0").assertIsSelected()
                }
            }
        }
    }

    /** 贴底和悬浮导航的文字应在三种外观下使用完整的主题前景色。 */
    @Test
    fun miuixBottomBarLabelsUseReadableForeground() = withIsolatedSettings(ThemeStyle.MIUIX) {
        for ((mode, oled) in listOf(ThemeMode.LIGHT to false, ThemeMode.DARK to false, ThemeMode.DARK to true)) {
            for (floating in listOf(false, true)) {
                val current = ThemeManager.snapshot()
                ThemeManager.load(current.copy(themeStyle = ThemeStyle.MIUIX, themeMode = mode, amoledTheme = oled))
                runDesktopComposeUiTest(width = 640, height = 240) {
                    var foreground = Color.Unspecified
                    var selectedColor = Color.Unspecified
                    setContent {
                        ZhihuTheme {
                            foreground = LocalAppSkinTokens.current.onSurface
                            selectedColor = LocalAppSkinTokens.current.primary
                            AppBottomBar(
                                items = listOf("主页", "关注").mapIndexed { index, label ->
                                    BottomBarItem(label, Icons.Default.Home, "contrast_tab_$index", index == 0) {}
                                },
                                bottomPadding = 0.dp,
                                floating = floating,
                            )
                        }
                    }
                    for (label in listOf("主页", "关注")) {
                        val results = mutableListOf<TextLayoutResult>()
                        onNodeWithText(label, useUnmergedTree = true)
                            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
                        assertEquals(
                            if (label == "主页") selectedColor else foreground,
                            results
                                .single()
                                .layoutInput.style.color,
                            "$mode OLED=$oled 悬浮=$floating $label",
                        )
                    }
                    if (!floating && mode == ThemeMode.LIGHT) {
                        val tab = onNodeWithTag("contrast_tab_1")
                        val before = tab.captureToImage().toPixelMap()
                        tab.performTouchInput { down(center) }
                        val pressed = tab.captureToImage().toPixelMap()
                        val changed = (0 until before.height / 2).sumOf { y ->
                            (0 until before.width).count { x -> before[x, y].toArgb() != pressed[x, y].toArgb() }
                        }
                        assertTrue(changed > 0, "普通底栏按下时图标应有反馈")
                        tab.performTouchInput { cancel() }
                        onNodeWithTag("contrast_tab_0").assertIsSelected()
                    }
                }
            }
        }
    }

    /** 系统字号仍完整生效；五个标签的实际文字布局和可见边界都要留在胶囊内。 */
    @Test
    fun floatingBarLabelsFitAtSystemFontScale() {
        for (style in ThemeStyle.entries) {
            withIsolatedSettings(style) {
                runDesktopComposeUiTest(width = 600, height = 360) {
                    var fontScale by mutableStateOf(1f)
                    val labels = listOf("主页", "关注", "热榜", "日报", "账号设置")
                    setContent {
                        ZhihuTheme {
                            val currentDensity = LocalDensity.current
                            CompositionLocalProvider(LocalDensity provides Density(currentDensity.density, fontScale)) {
                                AppFloatingBar(
                                    items = labels.mapIndexed { index, label ->
                                        BottomBarItem(label, Icons.Default.Home, "font_tab_$index", index == 0) {}
                                    },
                                    bottomPadding = 0.dp,
                                    modifier = Modifier.testTag("font_bar"),
                                )
                            }
                        }
                    }
                    for (scale in listOf(1f, 2f)) {
                        runOnIdle { fontScale = scale }
                        waitForIdle()
                        val bar = onNodeWithTag("font_bar").getUnclippedBoundsInRoot()
                        for (label in labels) {
                            val results = mutableListOf<TextLayoutResult>()
                            val node = onNodeWithText(label, useUnmergedTree = true)
                            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
                            assertTrue(results.isNotEmpty(), "$style $scale $label 缺少文字布局")
                            val result = results.first()
                            assertEquals(scale, result.layoutInput.density.fontScale, 0.01f, "不能缩小系统字号来隐藏裁切")
                            assertTrue(!result.didOverflowHeight, "$style $scale $label 高度被裁切")
                            assertTrue((0 until result.lineCount).none(result::isLineEllipsized), "$style $scale $label 被省略")
                            val full = node.getUnclippedBoundsInRoot()
                            val visible = node.fetchSemanticsNode().boundsInRoot
                            assertEquals(with(density) { (full.bottom - full.top).toPx() }, visible.height, 1f, "$style $scale $label 部分文字在容器外")
                            assertTrue(full.top >= bar.top && full.bottom <= bar.bottom, "$style $scale $label 不在胶囊内")
                        }
                    }
                }
                runSkikoComposeUiTest(size = Size(360f, 700f), density = Density(1f)) {
                    var scale by mutableStateOf(1f)
                    var enabled by mutableStateOf(true)
                    var selected by mutableStateOf(0)
                    var changes = 0
                    var disabledColour = Color.Unspecified
                    setContent {
                        ZhihuTheme {
                            disabledColour = LocalAppSkinTokens.current.disabledOnSurface
                            CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                                Column {
                                    AppTabRow(listOf("回答", "文章", "关注订阅"), 0, {}, scrollable = true, modifier = Modifier.testTag("native_font_tabs"))
                                    AppSegmentedControl(listOf("手机号登录", "扫码登录"), selected, { selected = it; changes++ }, Modifier.width(280.dp), listOf("phone_choice", "qr_choice"), enabled)
                                    if (style == ThemeStyle.MIUIX) AppSegmentedControl(listOf("短", "长"), 0, {}, Modifier.width(320.dp))
                                }
                            }
                        }
                    }
                    runOnIdle { scale = 2f }
                    for (label in listOf("手机号登录", "扫码登录")) {
                        val layouts = mutableListOf<TextLayoutResult>()
                        val node = onNode(hasText(label) and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
                        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                        assertEquals(2f, layouts.single().layoutInput.density.fontScale)
                        assertEquals(1, layouts.single().lineCount)
                        assertTrue(!layouts.single().hasVisualOverflow && !layouts.single().isLineEllipsized(0))
                        assertTrue(node.fetchSemanticsNode().boundsInRoot.height >= layouts.single().size.height - 1f)
                    }
                    onNodeWithTag("phone_choice").assertIsSelected()
                    if (style == ThemeStyle.MIUIX) onNodeWithTag("phone_choice").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
                    onNodeWithTag("qr_choice").performClick().assertIsSelected()
                    assertEquals(1, changes)
                    runOnIdle { enabled = false; scale = 3f }
                    onNodeWithTag("phone_choice").assertIsNotEnabled().performClick()
                    assertEquals(1, changes)
                    onNodeWithTag("qr_choice").assertIsSelected()
                    if (style == ThemeStyle.MIUIX) {
                        for (label in listOf("短", "长")) {
                            val layouts = mutableListOf<TextLayoutResult>()
                            val node = onNodeWithText(label, useUnmergedTree = true)
                            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                            assertEquals(3f, layouts.single().layoutInput.density.fontScale)
                            assertTrue(!layouts.single().didOverflowHeight)
                            assertTrue(node.fetchSemanticsNode().boundsInRoot.height >= layouts.single().size.height - 1f)
                        }
                        val disabled = mutableListOf<TextLayoutResult>()
                        onNode(hasText("手机号登录") and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
                            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(disabled) }
                        assertEquals(disabledColour, disabled.single().layoutInput.style.color)
                        runOnIdle { scale = 2f }
                        for (label in listOf("回答", "文章", "关注订阅")) {
                            onNode(SemanticsMatcher("native tabs") { SemanticsActions.ScrollToIndex in it.config && SemanticsProperties.HorizontalScrollAxisRange in it.config } and hasAnyAncestor(hasTestTag("native_font_tabs")), useUnmergedTree = true).performScrollToNode(hasText(label))
                            val layouts = mutableListOf<TextLayoutResult>()
                            onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                            assertEquals(2f, layouts.single().layoutInput.density.fontScale)
                            assertTrue(!layouts.single().didOverflowHeight)
                            assertTrue((0 until layouts.single().lineCount).none(layouts.single()::isLineEllipsized))
                        }
                    }
                    runOnIdle { scale = 1f; enabled = true }
                    onNodeWithText("扫码登录").assertIsSelected()
                    if (style == ThemeStyle.MIUIX) onNodeWithText("扫码登录").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
                }
            }
        }
    }

    @Test
    fun bottomBarKeepsEveryConfiguredDestinationReachable() {
        val labels = listOf("主页", "关注", "热榜", "日报", "历史", "收藏夹", "账号")
        for (style in ThemeStyle.entries) {
            withIsolatedSettings(style) {
                runSkikoComposeUiTest(size = Size(412f, 360f), density = Density(1f)) {
                    val clicks = mutableListOf<Int>()
                    setContent {
                        ZhihuTheme {
                            AppScaffold(
                                bottomBar = {
                                    Box(Modifier.width(412.dp)) {
                                        AppBottomBar(
                                            items = labels.mapIndexed { index, label ->
                                                BottomBarItem(label, Icons.Default.Home, "all_nav_$index", index == 0) { clicks += index }
                                            },
                                            bottomPadding = 0.dp,
                                        )
                                    }
                                },
                            ) {}
                        }
                    }
                    labels.indices.forEach { index ->
                        val item = onNodeWithTag("all_nav_$index")
                        val bounds = item.fetchSemanticsNode().boundsInRoot
                        assertTrue(bounds.width > 0 && bounds.height > 0, "$style ${labels[index]}必须在窗口内")
                        item.assertHasClickAction().performClick()
                    }
                    assertEquals(labels.indices.toList(), clicks)
                }
            }
        }
    }

    private fun withIsolatedSettings(style: ThemeStyle, block: (SettingsStore) -> Unit) {
        val originalHome = System.getProperty("user.home")
        val originalTheme = ThemeManager.snapshot()
        val directory = Files.createTempDirectory("zhihu-floating-ui-").toFile()
        try {
            System.setProperty("user.home", directory.absolutePath)
            val settings = desktopSettingsStore()
            settings.putString("uiThemeStyle", style.name)
            settings.putString("themeMode", ThemeMode.LIGHT.name)
            settings.putBoolean("amoledTheme", false)
            ThemeManager.load(originalTheme.copy(themeStyle = style, themeMode = ThemeMode.LIGHT, amoledTheme = false))
            block(settings)
        } finally {
            if (originalHome == null) System.clearProperty("user.home") else System.setProperty("user.home", originalHome)
            ThemeManager.load(originalTheme)
            directory.deleteRecursively()
        }
    }
}
