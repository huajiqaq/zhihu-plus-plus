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

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun Float.tabWeight(index: Int): Float = (1f - abs(this - index)).coerceIn(0f, 1f)

/** 两个原生项可以同时着色，无障碍服务仍只报告宿主真正选中的一项。 */
internal fun Modifier.bottomBarTab(item: BottomBarItem): Modifier = testTag(item.testTag).semantics { selected = item.selected }

/** 只持有手指在可见条带内的位置；松手、取消、换页或重排后由宿主的选中项决定外观。 */
@Composable
internal fun BottomBarDragStrip(
    items: List<BottomBarItem>,
    modifier: Modifier = Modifier,
    content: @Composable (itemWidth: Dp, preview: Float?) -> Unit,
) {
    val order = items.map { it.testTag }
    val selected = items.indexOfFirst { it.selected }
    val currentItems by rememberUpdatedState(items)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = maxOf(maxWidth, AppDimens.TouchTarget * items.size)
        val itemWidth = width / items.size
        val itemPx = with(LocalDensity.current) { width.roundToPx() } / items.size.toFloat()
        val viewportPx = constraints.maxWidth
        val overflow = width > maxWidth
        val scroll = rememberScrollState()
        var finger by remember(order, selected, rtl, viewportPx) { mutableStateOf<Float?>(null) }
        fun position(x: Float) = ((x + scroll.value) / itemPx - 0.5f).coerceIn(0f, items.lastIndex.toFloat())

        LaunchedEffect(selected, order, viewportPx, scroll.maxValue) {
            if (overflow && selected >= 0) {
                scroll.animateScrollTo(((selected + 0.5f) * itemPx - viewportPx / 2f).roundToInt().coerceIn(0, scroll.maxValue))
            }
        }
        val edge = finger?.let { x ->
            when {
                x < itemPx / 2 -> -1
                x > viewportPx - itemPx / 2 -> 1
                else -> 0
            }
        } ?: 0
        LaunchedEffect(edge) {
            if (overflow && edge != 0) scroll.animateScrollTo(if (edge < 0) 0 else scroll.maxValue)
        }
        Box(
            Modifier
                .pointerInput(order, selected, rtl, viewportPx, itemPx) {
                    fun logicalX(x: Float) = if (rtl) size.width - x else x
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Initial)
                        // 窄窗从当前项拖选，从其他项滑动则交给原生滚动；无选中项时也可长按起拖。
                        val scrollFromHere = overflow && position(logicalX(down.position.x)).roundToInt() != selected
                        try {
                            while (true) {
                                val change = awaitPointerEvent(PointerEventPass.Initial).changes.singleOrNull() ?: break
                                if (change.id != down.id || change.isConsumed) break
                                if (change.position.y !in 0f..size.height.toFloat()) {
                                    change.consume()
                                    break
                                }
                                val x = logicalX(change.position.x)
                                if (!change.pressed) {
                                    if (finger != null && change.changedToUp()) {
                                        change.consume()
                                        if (x in 0f..size.width.toFloat()) {
                                            val target = currentItems[position(x).roundToInt()]
                                            if (!target.selected) target.onClick()
                                        }
                                    }
                                    break
                                }
                                if (finger == null) {
                                    val delta = change.position - down.position
                                    if (abs(delta.y) > viewConfiguration.touchSlop && abs(delta.y) >= abs(delta.x)) {
                                        change.consume()
                                        break
                                    }
                                    if (abs(delta.x) <= viewConfiguration.touchSlop) continue
                                    if (scrollFromHere && change.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) break
                                }
                                change.consume()
                                finger = x
                            }
                        } finally {
                            finger = null
                        }
                    }
                }.then(if (overflow) Modifier.horizontalScroll(scroll) else Modifier)
                .width(width),
        ) { content(itemWidth, finger?.let(::position)) }
    }
}
