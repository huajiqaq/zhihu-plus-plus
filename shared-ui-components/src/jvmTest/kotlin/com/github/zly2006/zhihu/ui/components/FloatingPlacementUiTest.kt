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

package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
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
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.desktopSettingsStore
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.theme.MIUIX_STYLE_PREFERENCE_KEY
import com.github.zly2006.zhihu.theme.MiuixStyle
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeMode
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.ZhihuTheme
import top.yukonga.miuix.kmp.basic.NavigationBarDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.nio.file.Files
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import top.yukonga.miuix.kmp.basic.FloatingActionButton as NativeMiuixFab

@OptIn(ExperimentalTestApi::class)
class FloatingPlacementUiTest {
    /** 原生尺寸、end 留白和首帧稳定性在旋转、短 pane 与底部避让后仍保持。 */
    @Test
    fun fabColumnKeepsNativeMeasurementAndStableAppearance() {
        for (style in ThemeStyle.entries) {
            withIsolatedSettings(style) { settings ->
                runDesktopComposeUiTest(width = 640, height = 260) {
                    var generation by mutableStateOf(0)
                    var windowSize by mutableStateOf(IntSize(640, 260))
                    var paneHeight by mutableStateOf(120.dp)
                    var avoidance by mutableStateOf(20.dp)
                    var clicks = 0
                    val placedY = mutableListOf<Float>()
                    val window = object : WindowInfo {
                        override val isWindowFocused = true
                        override val containerSize get() = windowSize
                    }
                    settings.putFloat("boundedColumn-y", Float.MAX_VALUE)
                    setContent {
                        ZhihuTheme {
                            CompositionLocalProvider(LocalWindowInfo provides window) {
                                Box(Modifier.fillMaxSize()) {
                                    Row(Modifier.padding(top = 28.dp)) {
                                        Spacer(Modifier.width(180.dp))
                                        Box(Modifier.width(240.dp).height(paneHeight).testTag("column_pane")) {
                                            key(generation) {
                                                DraggableFabColumn(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(end = 16.dp)
                                                        .testTag("native_fab_column")
                                                        .onGloballyPositioned { placedY += it.positionInRoot().y },
                                                    preferenceName = "boundedColumn",
                                                    bottomAvoidance = avoidance,
                                                ) {
                                                    AppFloatingActionButton(onClick = { clicks++ }, modifier = Modifier.testTag("column_native_fab")) {
                                                        Icon(Icons.Default.Refresh, "刷新")
                                                    }
                                                }
                                            }
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
                        runOnIdle {
                            placedY.clear()
                            generation++
                        }
                    }
                    onNodeWithTag("column_native_fab").performClick()
                    assertEquals(1, clicks)
                    onNodeWithTag("native_fab_column").performTouchInput {
                        swipe(center, center + Offset(-80f, -18f), durationMillis = 400)
                    }
                    waitForIdle()
                    val landscapeFraction = settings.getFloat("boundedColumn-landscape-yFraction", Float.NaN)
                    assertTrue(landscapeFraction in 0f..0.95f, "必须保存非默认纵向位置：$landscapeFraction")
                    assertEquals(null, settings.getStringOrNull("boundedColumn-landscape-edge"), "按钮组仍只允许纵向拖动")
                    val writes = mutableListOf<String>()
                    val subscription = settings.observeKeyChanges { writes += it }
                    try {
                        runOnIdle {
                            windowSize = IntSize(260, 640)
                            paneHeight = 180.dp
                        }
                        waitForIdle()
                        assertEquals(
                            onNodeWithTag("column_pane").getUnclippedBoundsInRoot().bottom.value - 20f,
                            onNodeWithTag("column_native_fab").getUnclippedBoundsInRoot().bottom.value,
                            1f,
                        )
                        assertTrue(writes.isEmpty(), "旋转不得迁移或重写偏好：$writes")
                        onNodeWithTag("native_fab_column").performTouchInput {
                            swipe(center, center + Offset(80f, -65f), durationMillis = 400)
                        }
                        waitForIdle()
                        val portraitFraction = settings.getFloat("boundedColumn-portrait-yFraction", Float.NaN)
                        assertTrue(portraitFraction in 0f..0.95f && abs(portraitFraction - landscapeFraction) > 0.05f)
                        runOnIdle {
                            writes.clear()
                            windowSize = IntSize(640, 260)
                            paneHeight = 120.dp
                        }
                        waitForIdle()
                        for ((height, bottom) in listOf(120.dp to 20.dp, 150.dp to 35.dp)) {
                            runOnIdle {
                                paneHeight = height
                                avoidance = bottom
                            }
                            waitForIdle()
                            val currentPane = onNodeWithTag("column_pane").getUnclippedBoundsInRoot()
                            val currentFab = onNodeWithTag("column_native_fab").getUnclippedBoundsInRoot()
                            val availableY = (height.value - (native.bottom - native.top).value - bottom.value).coerceAtLeast(0f)
                            assertEquals(currentPane.top.value + landscapeFraction * availableY, currentFab.top.value, 1f)
                            assertEquals(currentPane.right.value - 16f, currentFab.right.value, 1f)
                            assertEquals((native.bottom - native.top).value, (currentFab.bottom - currentFab.top).value, 1f)
                        }
                        assertTrue(writes.isEmpty(), "返回与 resize/避让不得持久写位置：$writes")
                        assertEquals(Float.MAX_VALUE, settings.getFloat("boundedColumn-y", 0f))
                    } finally {
                        subscription.close()
                    }
                }
            }
        }
    }

    /** 同一组件横竖分别记边和高度比例；旧 px 回退只读，resize 与取消拖动不保存。 */
    @OptIn(InternalComposeUiApi::class)
    @Test
    fun draggableControlsRestoreOrientationEdgesWithinActualPane() = withIsolatedSettings(ThemeStyle.MATERIAL_EXPRESSIVE) { settings ->
        runDesktopComposeUiTest(width = 640, height = 520) {
            var windowSize by mutableStateOf(IntSize(640, 520))
            var paneHeight by mutableStateOf(320.dp)
            var paneWidth by mutableStateOf(180.dp)
            var avoidance by mutableStateOf(20.dp)
            var generation by mutableStateOf(0)
            var focused by mutableStateOf(true)
            val window = object : WindowInfo {
                override val isWindowFocused get() = focused
                override val containerSize get() = windowSize
            }
            val names = listOf("boundedRefresh", "boundedCompact", "boundedPageTurn")
            var preferenceSuffix by mutableStateOf("")
            names.forEach { name ->
                settings.putFloat("$name-x", 0f)
                settings.putFloat("$name-y", 70f)
            }
            var refreshClicks = 0
            var compactClicks = 0
            var pageClicks = 0
            var pageLongClicks = 0
            setContent {
                ZhihuTheme {
                    CompositionLocalProvider(LocalWindowInfo provides window) {
                        Row(Modifier.padding(top = 28.dp)) {
                            Spacer(Modifier.width(20.dp))
                            names.forEachIndexed { index, name ->
                                Box(Modifier.width(paneWidth).height(paneHeight).testTag("${name}_pane")) {
                                    key(generation) {
                                        if (index == 2) {
                                            DraggablePageTurnButtons(
                                                onPageUp = { pageClicks++ },
                                                onPageDown = { pageClicks++ },
                                                onLongPressUp = { pageLongClicks++ },
                                                onLongPressDown = { pageLongClicks++ },
                                                modifier = Modifier.testTag(name),
                                                preferenceName = name + preferenceSuffix,
                                            )
                                        } else {
                                            DraggableRefreshButton(
                                                modifier = Modifier.testTag(name),
                                                preferenceName = name + preferenceSuffix,
                                                bottomAvoidance = avoidance,
                                                initiallyOnLeft = index == 1,
                                                edgeMargin = 0.dp,
                                                compact = false,
                                                onClick = { if (index == 0) refreshClicks++ else compactClicks++ },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            names.forEach { name ->
                val pane = onNodeWithTag("${name}_pane").fetchSemanticsNode().boundsInRoot
                val control = onNodeWithTag(name).fetchSemanticsNode().boundsInRoot
                assertEquals(pane.left, control.left, 1f, "旧 x=0 保持左边")
                assertEquals(pane.top + 70f, control.top, 1f, "旧 y 按当前 px 边界回退")
            }
            onNodeWithTag(names[0]).performClick()
            onNodeWithTag(names[1]).performClick()
            onNodeWithContentDescription("上翻页").performTouchInput { click() }
            onNodeWithContentDescription("下翻页").performTouchInput { longClick() }
            assertEquals(1, refreshClicks)
            assertEquals(1, compactClicks)
            assertEquals(1, pageClicks)
            assertEquals(1, pageLongClicks)
            names.forEachIndexed { index, name ->
                val node = onNodeWithTag(name)
                val pane = onNodeWithTag("${name}_pane").fetchSemanticsNode().boundsInRoot
                val control = node.fetchSemanticsNode().boundsInRoot
                val bottom = if (index == 2) 0f else with(density) { avoidance.toPx() }
                val target = Offset(pane.right - control.width / 2f, pane.top + (pane.height - control.height - bottom) * 0.2f + control.height / 2f)
                assertTrue(pane.contains(control.center) && pane.contains(target), "$name 横屏拖动端点须在 pane 内：pane=$pane control=$control target=$target")
                node.performTouchInput { swipe(center, center + target - control.center, durationMillis = 400) }
            }
            waitForIdle()
            val landscapeFractions = names.associateWith { settings.getFloat("$it-landscape-yFraction", Float.NaN) }
            val landscapeGeometry = names.associateWith { name ->
                "pane=${onNodeWithTag("${name}_pane").fetchSemanticsNode().boundsInRoot} control=${onNodeWithTag(name).fetchSemanticsNode().boundsInRoot}"
            }
            names.forEach { name ->
                assertEquals("right", settings.getString("$name-landscape-edge", ""))
                assertTrue(landscapeFractions.getValue(name) in 0.1f..0.9f, "$name landscapeFraction=${landscapeFractions.getValue(name)} targetFraction=0.2 ${landscapeGeometry.getValue(name)}")
            }
            val writes = mutableListOf<String>()
            val subscription = settings.observeKeyChanges { writes += it }
            try {
                runOnIdle {
                    windowSize = IntSize(520, 640)
                    paneHeight = 420.dp
                }
                waitForIdle()
                names.forEach { name ->
                    val pane = onNodeWithTag("${name}_pane").fetchSemanticsNode().boundsInRoot
                    val control = onNodeWithTag(name).fetchSemanticsNode().boundsInRoot
                    assertEquals(pane.left, control.left, 1f, "另一方向仍用独立旧键回退")
                    assertEquals(pane.top + 70f, control.top, 1f)
                }
                assertTrue(writes.isEmpty(), "旋转不得写入新键：$writes")
                names.forEachIndexed { index, name ->
                    val node = onNodeWithTag(name)
                    val pane = onNodeWithTag("${name}_pane").fetchSemanticsNode().boundsInRoot
                    val control = node.fetchSemanticsNode().boundsInRoot
                    val bottom = if (index == 2) 0f else with(density) { avoidance.toPx() }
                    val target = Offset(pane.left + control.width / 2f, pane.top + (pane.height - control.height - bottom) * 0.7f + control.height / 2f)
                    assertTrue(pane.contains(control.center) && pane.contains(target), "$name 竖屏拖动端点须在 pane 内：pane=$pane control=$control target=$target")
                    node.performTouchInput { swipe(center, center + target - control.center, durationMillis = 400) }
                }
                waitForIdle()
                names.forEach { name ->
                    assertEquals("left", settings.getString("$name-portrait-edge", ""))
                    val portraitFraction = settings.getFloat("$name-portrait-yFraction", Float.NaN)
                    val landscapeFraction = landscapeFractions.getValue(name)
                    val pane = onNodeWithTag("${name}_pane").fetchSemanticsNode().boundsInRoot
                    val control = onNodeWithTag(name).fetchSemanticsNode().boundsInRoot
                    assertTrue(
                        abs(portraitFraction - landscapeFraction) > 0.05f,
                        "$name portraitFraction=$portraitFraction landscapeFraction=$landscapeFraction targets=0.7/0.2 " +
                            "portraitPane=$pane portraitControl=$control landscape=${landscapeGeometry.getValue(name)}",
                    )
                }
                runOnIdle {
                    writes.clear()
                    windowSize = IntSize(640, 520)
                    paneHeight = 320.dp
                }
                waitForIdle()
                for (height in listOf(320.dp, 180.dp, 380.dp)) {
                    runOnIdle {
                        paneHeight = height
                        avoidance = 35.dp
                    }
                    waitForIdle()
                    names.forEachIndexed { index, name ->
                        val pane = onNodeWithTag("${name}_pane").fetchSemanticsNode().boundsInRoot
                        val control = onNodeWithTag(name).fetchSemanticsNode().boundsInRoot
                        val bottom = if (index == 2) 0f else with(density) { avoidance.toPx() }
                        assertEquals(pane.right, control.right, 1f, "恢复的是 pane 右边而非窗口右边")
                        assertEquals(pane.top + landscapeFractions.getValue(name) * (pane.height - control.height - bottom), control.top, 1f)
                        assertTrue(control.top >= pane.top && control.bottom <= pane.bottom - bottom + 1f)
                    }
                }
                val savedPositions = settings.allEntries()

                fun assertSavedProjection() {
                    waitUntil("中断拖动后的动画须回到保存边位和比例投影", timeoutMillis = 1_000) {
                        names.withIndex().all { (index, name) ->
                            val pane = onNodeWithTag("${name}_pane").fetchSemanticsNode().boundsInRoot
                            val control = onNodeWithTag(name).fetchSemanticsNode().boundsInRoot
                            val bottom = if (index == 2) 0f else with(density) { avoidance.toPx() }
                            val savedTop = pane.top + landscapeFractions.getValue(name) * (pane.height - control.height - bottom)
                            abs(pane.right - control.right) <= 1f && abs(savedTop - control.top) <= 1f
                        }
                    }
                    names.forEachIndexed { index, name ->
                        val pane = onNodeWithTag("${name}_pane").fetchSemanticsNode().boundsInRoot
                        val control = onNodeWithTag(name).fetchSemanticsNode().boundsInRoot
                        val bottom = if (index == 2) 0f else with(density) { avoidance.toPx() }
                        assertEquals(pane.right, control.right, 1f, "中断拖动后恢复保存的右边")
                        assertEquals(
                            pane.top + landscapeFractions.getValue(name) * (pane.height - control.height - bottom),
                            control.top,
                            1f,
                            "中断拖动后按新边界投影保存的比例",
                        )
                    }
                    assertTrue(writes.isEmpty(), "重启或取消不得保存：$writes")
                    assertEquals(savedPositions, settings.allEntries(), "中断不得改写任何位置偏好")
                }
                onNodeWithTag(names[0]).performTouchInput {
                    down(center)
                    moveBy(Offset(-60f, -45f), delayMillis = 320)
                    cancel()
                }
                // Skiko 的触摸注入 cancel 不派发事件，须由真实场景取消当前指针输入。
                runOnIdle { scene.cancelPointerInput() }
                waitForIdle()
                assertSavedProjection()
                for ((name, restart) in names.zip(listOf("pane", "avoidance", "focus"))) {
                    val before = onNodeWithTag(name).fetchSemanticsNode().boundsInRoot
                    onNodeWithTag(name).performTouchInput {
                        down(center)
                        moveBy(Offset(-60f, -45f), delayMillis = 320)
                    }
                    waitForIdle()
                    val during = onNodeWithTag(name).fetchSemanticsNode().boundsInRoot
                    assertTrue(during.left < before.left - 10f && during.top < before.top - 10f, "须先超过 slop 并实际拖动：$name $restart before=$before during=$during")
                    runOnIdle {
                        when (restart) {
                            "pane" -> paneHeight = 330.dp
                            "avoidance" -> avoidance = 55.dp
                            else -> focused = false
                        }
                    }
                    waitForIdle()
                    assertSavedProjection()
                    onNodeWithTag(name).performTouchInput { up() }
                    waitForIdle()
                    assertSavedProjection()
                    if (restart == "focus") {
                        runOnIdle { focused = true }
                        waitForIdle()
                        assertSavedProjection()
                    }
                }
            } finally {
                subscription.close()
            }
            names.forEach { name -> assertEquals(70f, settings.getFloat("$name-y", 0f)) }
            runOnIdle {
                names.forEach { name ->
                    settings.putFloat("$name-x", 1f)
                    settings.putFloat("$name-y", Float.MAX_VALUE)
                }
                windowSize = IntSize(400, 640)
                generation++
            }
            waitForIdle()
            names.forEachIndexed { index, name ->
                val pane = onNodeWithTag("${name}_pane").fetchSemanticsNode().boundsInRoot
                val control = onNodeWithTag(name).fetchSemanticsNode().boundsInRoot
                /** 已保存的 portrait 键优先于后来改变的旧 px 键。 */
                assertEquals(pane.left, control.left, 1f)
                val fraction = settings.getFloat("$name-portrait-yFraction", Float.NaN)
                val bottom = if (index == 2) 0f else with(density) { avoidance.toPx() }
                assertEquals(pane.top + fraction * (pane.height - control.height - bottom), control.top, 1f)
            }
            runOnIdle {
                names.forEach { name ->
                    settings.putFloat("${name}Legacy-x", 1f)
                    settings.putFloat("${name}Legacy-y", Float.MAX_VALUE)
                }
                preferenceSuffix = "Legacy"
            }
            waitForIdle()
            names.forEachIndexed { index, name ->
                val pane = onNodeWithTag("${name}_pane").fetchSemanticsNode().boundsInRoot
                val control = onNodeWithTag(name).fetchSemanticsNode().boundsInRoot
                val bottom = with(density) { if (index == 2) 80.dp.toPx() else avoidance.toPx() }
                assertEquals(pane.left + 1f, control.left, 1f, "有限旧像素保留，不把正数当右边")
                assertEquals(pane.bottom - bottom, control.bottom, 1f, "Float.MAX_VALUE 使用当前 pane 默认位置")
                assertTrue(settings.getFloat("${name}Legacy-portrait-yFraction", Float.NaN).isNaN())
            }
            for (legacyX in listOf(0f, 16f, 80f, 10_000f, Float.MAX_VALUE)) {
                for (width in listOf(180.dp, 130.dp)) {
                    runOnIdle {
                        names.forEach { name -> settings.putFloat("${name}Legacy-x", legacyX) }
                        paneWidth = width
                        generation++
                    }
                    waitForIdle()
                    names.forEach { name ->
                        val pane = onNodeWithTag("${name}_pane").fetchSemanticsNode().boundsInRoot
                        val control = onNodeWithTag(name).fetchSemanticsNode().boundsInRoot
                        val availableX = (pane.width - control.width).coerceAtLeast(0f)
                        val expectedX = if (legacyX == Float.MAX_VALUE) availableX else legacyX.coerceIn(0f, availableX)
                        assertEquals(pane.left + expectedX, control.left, 1f, "旧像素只按实测pane夹取：$name x=$legacyX width=$width")
                        assertEquals(legacyX, settings.getFloat("${name}Legacy-x", Float.NaN))
                        assertEquals(null, settings.getStringOrNull("${name}Legacy-portrait-edge"))
                        assertTrue(settings.getFloat("${name}Legacy-portrait-yFraction", Float.NaN).isNaN())
                    }
                }
            }
            runOnIdle {
                names.forEach { name -> settings.putFloat("${name}Legacy-y", 10_000f) }
                generation++
            }
            waitForIdle()
            names.forEachIndexed { index, name ->
                val pane = onNodeWithTag("${name}_pane").fetchSemanticsNode().boundsInRoot
                val control = onNodeWithTag(name).fetchSemanticsNode().boundsInRoot
                val bottom = if (index == 2) 0f else with(density) { avoidance.toPx() }
                assertEquals(pane.bottom - bottom, control.bottom, 1f, "旧超界 y 只按当前 bound clamp")
                assertEquals(10_000f, settings.getFloat("${name}Legacy-y", 0f))
            }
        }
    }

    @Test
    fun bottomBarKeepsNativeLabelsSelectionAndReselectionInNarrowPane() {
        for (style in ThemeStyle.entries) {
            withIsolatedSettings(style) {
                runDesktopComposeUiTest(width = 640, height = 260) {
                    var selected by mutableStateOf(0)
                    var count by mutableStateOf(2)
                    var direction by mutableStateOf(LayoutDirection.Ltr)
                    val labels = listOf("主页", "关注", "热榜", "日报", "账号")
                    val clicks = mutableListOf<Int>()
                    setContent {
                        ZhihuTheme {
                            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                                Row(Modifier.fillMaxSize().padding(top = 28.dp)) {
                                    Spacer(Modifier.width(180.dp))
                                    Box(Modifier.width(240.dp).height(160.dp).testTag("bar_pane")) {
                                        AppBottomBar(
                                            modifier = Modifier.fillMaxWidth(),
                                            items = labels.take(count).mapIndexed { index, label ->
                                                BottomBarItem(label, Icons.Default.Home, "bounded_tab_$index", selected == index) {
                                                    clicks += index
                                                    selected = index
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    for (layoutDirection in LayoutDirection.entries) {
                        runOnIdle { direction = layoutDirection }
                        for (itemCount in 2..5) {
                            runOnIdle {
                                count = itemCount
                                selected = 0
                                clicks.clear()
                            }
                            waitForIdle()
                            val pane = onNodeWithTag("bar_pane").fetchSemanticsNode().boundsInRoot
                            val bounds = (0 until itemCount).map { index ->
                                onNodeWithText(labels[index], useUnmergedTree = true).assertExists()
                                val tab = onNodeWithTag("bounded_tab_$index")
                                tab.assertHasClickAction().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
                                val item = tab.fetchSemanticsNode().boundsInRoot
                                assertTrue(item.width >= 48f && item.height >= 48f, "$style/$direction 触摸区域不能缩成图标")
                                assertTrue(item.left >= pane.left && item.right <= pane.right && item.bottom <= pane.bottom, "pane=$pane item=$item")
                                tab.performTouchInput { click(center) }
                                tab.assertIsSelected()
                                item
                            }
                            assertTrue(bounds.zipWithNext().all { (a, b) -> if (direction == LayoutDirection.Ltr) a.right <= b.left + 1f else b.right <= a.left + 1f })
                            onNodeWithTag("bounded_tab_${itemCount - 1}").performTouchInput { click(center) }
                            assertEquals((0 until itemCount).toList() + (itemCount - 1), clicks, "切页和重选各提交一次；重选仍交给宿主处理")
                        }
                    }
                }
            }
        }
    }

    /** 贴底导航使用原生选中、未选中和按压颜色，不能被外层 Material 内容色覆盖。 */
    @Test
    fun miuixBottomBarLabelsUseNativeForeground() = withIsolatedSettings(ThemeStyle.MIUIX) {
        for ((mode, oled) in listOf(ThemeMode.LIGHT to false, ThemeMode.DARK to false, ThemeMode.DARK to true)) {
            val current = ThemeManager.snapshot()
            ThemeManager.load(current.copy(themeStyle = ThemeStyle.MIUIX, themeMode = mode, amoledTheme = oled))
            runDesktopComposeUiTest(width = 640, height = 240) {
                var foreground = Color.Unspecified
                setContent {
                    ZhihuTheme(settings = null) {
                        foreground = MiuixTheme.colorScheme.onSurfaceContainer
                        AppBottomBar(
                            items = listOf("主页", "关注").mapIndexed { index, label ->
                                BottomBarItem(label, Icons.Default.Home, "contrast_tab_$index", index == 0) {}
                            },
                        )
                    }
                }
                for (label in listOf("主页", "关注")) {
                    val results = mutableListOf<TextLayoutResult>()
                    onNodeWithText(label, useUnmergedTree = true)
                        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
                    assertEquals(
                        if (label == "主页") foreground else foreground.copy(alpha = foreground.alpha * NavigationBarDefaults.UnselectedAlpha),
                        results
                            .single()
                            .layoutInput.style.color,
                        "$mode OLED=$oled $label",
                    )
                }
                val tab = onNodeWithTag("contrast_tab_1")
                val before = tab.captureToImage().toPixelMap()
                tab.performTouchInput { down(center) }
                val pressed = tab.captureToImage().toPixelMap()
                val changed = (0 until before.height / 2).sumOf { y ->
                    (0 until before.width).count { x -> before[x, y].toArgb() != pressed[x, y].toArgb() }
                }
                assertTrue(changed > 0, "普通底栏按下时图标应有原生反馈")
                tab.performTouchInput { cancel() }
                onNodeWithTag("contrast_tab_0").assertIsSelected()
            }
        }
    }

    /** 系统字号仍完整生效；五个标签的实际文字布局和可见边界都要留在导航栏内。 */
    @Test
    fun bottomBarLabelsFitAtSystemFontScale() {
        for (style in ThemeStyle.entries) {
            withIsolatedSettings(style) {
                runDesktopComposeUiTest(width = 412, height = 360) {
                    var fontScale by mutableStateOf(1f)
                    val labels = listOf("主页", "关注", "热榜", "日报", "账号设置")
                    setContent {
                        ZhihuTheme {
                            val currentDensity = LocalDensity.current
                            CompositionLocalProvider(LocalDensity provides Density(currentDensity.density, fontScale)) {
                                AppBottomBar(
                                    items = labels.mapIndexed { index, label ->
                                        BottomBarItem(label, Icons.Default.Home, "font_tab_$index", index == 0) {}
                                    },
                                    modifier = Modifier.testTag("font_bar"),
                                    floating = true,
                                    bottomPadding = 12.dp,
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
                            assertTrue(full.top >= bar.top && full.bottom <= bar.bottom, "$style $scale $label 不在导航栏内")
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
                                    AppSegmentedControl(listOf("手机号登录", "扫码登录"), selected, {
                                        selected = it
                                        changes++
                                    }, Modifier.width(280.dp), listOf("phone_choice", "qr_choice"), enabled)
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
                        assertEquals(
                            2f,
                            layouts
                                .single()
                                .layoutInput.density.fontScale,
                        )
                        assertEquals(1, layouts.single().lineCount)
                        val layout = layouts.single()
                        assertTrue(
                            !layout.hasVisualOverflow && !layout.isLineEllipsized(0),
                            "$style scale=2 $label 字形必须完整：size=${layout.size} paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height} " +
                                "overflowWidth=${layout.didOverflowWidth} overflowHeight=${layout.didOverflowHeight} ellipsized=${layout.isLineEllipsized(0)} " +
                                "constraints=${layout.layoutInput.constraints} bounds=${node.fetchSemanticsNode().boundsInRoot} style=${layout.layoutInput.style}",
                        )
                        assertTrue(node.fetchSemanticsNode().boundsInRoot.height >= layouts.single().size.height - 1f)
                    }
                    onNodeWithTag("phone_choice").assertIsSelected()
                    if (style == ThemeStyle.MIUIX) onNodeWithTag("phone_choice").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
                    onNodeWithTag("qr_choice").performClick().assertIsSelected()
                    assertEquals(1, changes)
                    runOnIdle {
                        enabled = false
                        scale = 3f
                    }
                    onNodeWithTag("phone_choice").assertIsNotEnabled().performClick()
                    assertEquals(1, changes)
                    onNodeWithTag("qr_choice").assertIsSelected()
                    if (style == ThemeStyle.MIUIX) {
                        for (label in listOf("短", "长")) {
                            val layouts = mutableListOf<TextLayoutResult>()
                            val node = onNodeWithText(label, useUnmergedTree = true)
                            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                            assertEquals(
                                3f,
                                layouts
                                    .single()
                                    .layoutInput.density.fontScale,
                            )
                            assertTrue(!layouts.single().didOverflowHeight)
                            assertTrue(node.fetchSemanticsNode().boundsInRoot.height >= layouts.single().size.height - 1f)
                        }
                        val disabled = mutableListOf<TextLayoutResult>()
                        onNode(hasText("手机号登录") and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
                            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(disabled) }
                        assertEquals(
                            disabledColour,
                            disabled
                                .single()
                                .layoutInput.style.color,
                        )
                        runOnIdle { scale = 2f }
                        for (label in listOf("回答", "文章", "关注订阅")) {
                            onNode(SemanticsMatcher("native tabs") { SemanticsActions.ScrollToIndex in it.config && SemanticsProperties.HorizontalScrollAxisRange in it.config } and hasAnyAncestor(hasTestTag("native_font_tabs")), useUnmergedTree = true).performScrollToNode(hasText(label))
                            val layouts = mutableListOf<TextLayoutResult>()
                            onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                            assertEquals(
                                2f,
                                layouts
                                    .single()
                                    .layoutInput.density.fontScale,
                            )
                            assertTrue(!layouts.single().didOverflowHeight)
                            assertTrue((0 until layouts.single().lineCount).none(layouts.single()::isLineEllipsized))
                        }
                    }
                    runOnIdle {
                        scale = 1f
                        enabled = true
                    }
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

    @Test
    fun bottomBarDragBlendsBeforeCommittingAndDiscardsInterruptedGestures() {
        for (style in ThemeStyle.entries) {
            withIsolatedSettings(style) { settings ->
                runSkikoComposeUiTest(size = Size(360f, 220f), density = Density(1f)) {
                    var selected by mutableStateOf(0)
                    var order by mutableStateOf(listOf(0, 1, 2))
                    var floating by mutableStateOf(false)
                    var direction by mutableStateOf(LayoutDirection.Ltr)
                    val labels = listOf("主页", "收藏夹", "日报")
                    val clicks = mutableListOf<Int>()
                    setContent {
                        ZhihuTheme {
                            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                                AppBottomBar(
                                    order.map { id ->
                                        BottomBarItem(labels[id], Icons.Default.Home, "drag_$id", selected == id) {
                                            clicks += id
                                            selected = id
                                        }
                                    },
                                    floating = floating,
                                )
                            }
                        }
                    }

                    fun tab(id: Int) = onNodeWithTag("drag_$id")

                    fun labelColor(id: Int): Color {
                        val layouts = mutableListOf<TextLayoutResult>()
                        onNodeWithText(labels[id], useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                        return layouts
                            .single()
                            .layoutInput.style.color
                    }
                    val bars = if (style == ThemeStyle.MIUIX) listOf("docked", "default", "os4", "apple") else listOf("docked", "default", "apple")
                    for (bar in bars) {
                        runOnIdle {
                            order = listOf(0, 1, 2)
                            selected = 0
                            clicks.clear()
                            floating = bar != "docked"
                            direction = if (floating) LayoutDirection.Rtl else LayoutDirection.Ltr
                            settings.putString("floatingBottomBarStyle", if (bar == "apple") "apple" else "default")
                            val miuixStyle = if (bar == "os4" || bar == "apple") MiuixStyle.HYPEROS_4 else MiuixStyle.HYPEROS_3
                            settings.putString(MIUIX_STYLE_PREFERENCE_KEY, miuixStyle.name)
                        }
                        val source = tab(0).fetchSemanticsNode().boundsInRoot
                        val start = source.center
                        val end = tab(1).fetchSemanticsNode().boundsInRoot.center
                        val active = labelColor(0)
                        val inactive = labelColor(1)
                        val before = tab(1).captureToImage().toPixelMap()
                        val root = onRoot()
                        root.performTouchInput {
                            down(start)
                            moveTo((start + end) / 2f)
                        }
                        for (id in 0..1) {
                            val color = labelColor(id)
                            assertTrue(color.toArgb() != active.toArgb() && color.toArgb() != inactive.toArgb(), "$style/$bar $id 按住半程未交接")
                        }
                        val during = tab(1).captureToImage().toPixelMap()
                        val iconChanged = (0 until before.height / 2).any { y -> (0 until before.width).any { x -> before[x, y] != during[x, y] } }
                        assertTrue(iconChanged, "$style/$bar 图标区域未交接")
                        tab(0).assertIsSelected()
                        assertTrue(clicks.isEmpty())
                        root.performTouchInput {
                            moveTo(Offset(end.x, source.bottom + 8f))
                            up()
                        }
                        assertEquals(active.toArgb(), labelColor(0).toArgb())
                        assertEquals(inactive.toArgb(), labelColor(1).toArgb())
                        root.performTouchInput {
                            down(start)
                            moveTo(start + Offset(0f, source.height * 0.4f))
                            up()
                        }
                        assertTrue(clicks.isEmpty())
                        root.performTouchInput {
                            down(start)
                            moveTo(end)
                            up()
                        }
                        tab(1).assertIsSelected()
                        assertEquals(listOf(1), clicks)
                        root.performTouchInput {
                            down(end)
                            moveTo(start)
                        }
                        runOnIdle {
                            selected = 2
                            order = order.reversed()
                        }
                        tab(2).assertIsSelected()
                        root.performTouchInput { up() }
                        assertEquals(listOf(1), clicks, "换页和重排不能提交旧目标")
                        assertEquals(active.toArgb(), labelColor(2).toArgb())
                    }
                }
            }
        }
    }

    @Test
    fun appleBarKeepsFullLabelsTouchTargetsAndExternalSelection() =
        withIsolatedSettings(ThemeStyle.MATERIAL_EXPRESSIVE) { settings ->
            settings.putString("floatingBottomBarStyle", "apple")
            runDesktopComposeUiTest(width = 412, height = 360) {
                var selected by mutableStateOf(0)
                var direction by mutableStateOf(LayoutDirection.Ltr)
                var fontScale by mutableStateOf(1f)
                val clicks = mutableListOf<Int>()
                val labels = listOf("主页", "关注", "热榜", "日报", "账号设置")
                setContent {
                    ZhihuTheme {
                        val currentDensity = LocalDensity.current
                        CompositionLocalProvider(
                            LocalLayoutDirection provides direction,
                            LocalDensity provides Density(currentDensity.density, fontScale),
                        ) {
                            AppBottomBar(
                                labels.mapIndexed { index, label ->
                                    BottomBarItem(label, Icons.Default.Home, "apple_tab_$index", selected == index) {
                                        clicks += index
                                        selected = index
                                    }
                                },
                                Modifier.testTag("apple_bar"),
                                floating = true,
                                bottomPadding = 12.dp,
                            )
                        }
                    }
                }
                for (layoutDirection in LayoutDirection.entries) {
                    runOnIdle { direction = layoutDirection }
                    for (index in labels.indices) {
                        onNodeWithTag("apple_tab_$index").assertHasClickAction().performClick().assertIsSelected()
                        val bounds = onNodeWithTag("apple_tab_$index").fetchSemanticsNode().boundsInRoot
                        assertTrue(bounds.width >= 48f && bounds.height >= 48f)
                    }
                }
                onNodeWithTag("apple_tab_4").performClick()
                assertEquals(labels.indices.toList() + labels.indices.toList() + 4, clicks)
                runOnIdle {
                    selected = 1
                    fontScale = 2f
                }
                onNodeWithTag("apple_tab_1").assertIsSelected()
                for (label in labels) {
                    val layouts = mutableListOf<TextLayoutResult>()
                    val node = onNodeWithText(label, useUnmergedTree = true)
                    node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    val layout = layouts.single()
                    assertEquals(2f, layout.layoutInput.density.fontScale)
                    assertTrue(!layout.hasVisualOverflow && (0 until layout.lineCount).none(layout::isLineEllipsized))
                    assertTrue(node.fetchSemanticsNode().boundsInRoot.height >= layout.size.height - 1f)
                }
                runOnIdle { settings.putString("floatingBottomBarStyle", "default") }
                onNodeWithTag("apple_tab_1").assertIsSelected()
                labels.forEach { onNodeWithText(it, useUnmergedTree = true).assertExists() }
            }
        }

    @Test
    fun appleBarKeepsSevenNarrowWindowItemsReachableAtFullTouchSize() =
        withIsolatedSettings(ThemeStyle.MATERIAL_EXPRESSIVE) { settings ->
            settings.putString("floatingBottomBarStyle", "apple")
            runDesktopComposeUiTest(width = 320, height = 360) {
                var selected by mutableStateOf(6)
                setContent {
                    ZhihuTheme {
                        AppBottomBar(
                            (0..6).map { index ->
                                BottomBarItem("项目 $index", Icons.Default.Home, "narrow_apple_$index", selected == index) { selected = index }
                            },
                            floating = true,
                        )
                    }
                }
                onNodeWithTag("narrow_apple_6").assertIsSelected()
                for (index in 0..6) {
                    val node = onNodeWithTag("narrow_apple_$index").performScrollTo()
                    val bounds = node.fetchSemanticsNode().boundsInRoot
                    assertTrue(bounds.width >= 48f && bounds.height >= 48f, "Each item must have its own 48dp target: $bounds")
                    node.performClick().assertIsSelected()
                }
                val viewport = onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)).fetchSemanticsNode().boundsInRoot
                val root = onRoot()
                val last = onNodeWithTag("narrow_apple_6").fetchSemanticsNode().boundsInRoot.center
                root.performTouchInput {
                    down(last)
                    moveTo(Offset(viewport.left + 1f, last.y))
                }
                val first = onNodeWithTag("narrow_apple_0")
                assertTrue(first.fetchSemanticsNode().boundsInRoot.width >= 48f, "按住边缘应能抵达先前在屏外的项目")
                assertEquals(6, selected)
                root.performTouchInput { up() }
                first.assertIsSelected()
                val middle = onNodeWithTag("narrow_apple_3").fetchSemanticsNode().boundsInRoot.center
                root.performTouchInput { swipe(middle, Offset(viewport.left + 1f, middle.y)) }
                assertTrue(onNodeWithTag("narrow_apple_6").fetchSemanticsNode().boundsInRoot.width >= 48f)
                assertEquals(0, selected, "从未选中项横滑只滚动，不导航")
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
