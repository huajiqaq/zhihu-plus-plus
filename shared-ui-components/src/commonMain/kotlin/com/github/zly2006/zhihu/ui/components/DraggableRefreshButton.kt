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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.i18n.WidgetsStrings
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.ui.rememberObservedSetting
import com.github.zly2006.zhihu.ui.subscreens.DEFAULT_FAB_OPACITY
import com.github.zly2006.zhihu.ui.subscreens.PREF_FAB_OPACITY
import kotlin.math.roundToInt

/** 未保存位置以 [Float.MAX_VALUE] 作贴边哨兵，避免绑定初始窗口尺寸。 */
@Composable
fun DraggableRefreshButton(
    modifier: Modifier = Modifier,
    preferenceName: String = "fabRefresh",
    bottomAvoidance: Dp = 0.dp,
    initiallyOnLeft: Boolean = false,
    edgeMargin: Dp = 16.dp,
    compact: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit = {
        AppIcon(Icons.Default.Refresh, contentDescription = WidgetsStrings.refresh)
    },
) {
    val settings = rememberSettingsStore()
    val opacityFraction by rememberObservedSetting(settings, PREF_FAB_OPACITY) {
        getInt(PREF_FAB_OPACITY, DEFAULT_FAB_OPACITY).coerceIn(10, 100) / 100f
    }
    DraggableFloatingPlacement(
        modifier = modifier,
        preferenceName = preferenceName,
        bottomAvoidance = bottomAvoidance,
        initiallyOnLeft = initiallyOnLeft,
        edgeMargin = edgeMargin,
    ) { draggableModifier ->
        if (settingSkinIsMiuix()) {
            MiuixDraggableRefreshButton(onClick, draggableModifier, opacityFraction, compact, content)
        } else {
            val elevation = if (opacityFraction < 1f) {
                FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
            } else {
                FloatingActionButtonDefaults.elevation()
            }
            val containerColor = FloatingActionButtonDefaults.containerColor.copy(alpha = opacityFraction)
            val contentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = opacityFraction)
            if (compact) {
                SmallFloatingActionButton(
                    onClick = onClick,
                    modifier = draggableModifier,
                    shape = FloatingActionButtonDefaults.smallShape,
                    containerColor = containerColor,
                    contentColor = contentColor,
                    elevation = elevation,
                    content = content,
                )
            } else {
                FloatingActionButton(
                    onClick = onClick,
                    modifier = draggableModifier,
                    containerColor = containerColor,
                    contentColor = contentColor,
                    elevation = elevation,
                    content = content,
                )
            }
        }
    }
}

@Composable
fun DraggableFabColumn(
    modifier: Modifier = Modifier,
    preferenceName: String = "fabColumn",
    bottomAvoidance: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    DraggableFloatingPlacement(modifier, preferenceName, bottomAvoidance, onlyVertical = true) { draggableModifier ->
        Column(
            modifier = draggableModifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
            content = content,
        )
    }
}

/** 窗口仅用于横竖分键；位置、手势边界和首帧放置共用所在 pane 的实测尺寸。 */
@Composable
internal fun DraggableFloatingPlacement(
    modifier: Modifier,
    preferenceName: String,
    bottomAvoidance: Dp = 0.dp,
    initiallyOnLeft: Boolean = false,
    onlyVertical: Boolean = false,
    animateSnap: Boolean = true,
    defaultBottomGap: Dp = 0.dp,
    edgeMargin: Dp = 0.dp,
    content: @Composable (Modifier) -> Unit,
) {
    val windowInfo = LocalWindowInfo.current
    val windowSize = windowInfo.containerSize
    val orientation = if (windowSize.width > windowSize.height) "landscape" else "portrait"
    val placementKey = "$preferenceName-$orientation"
    val settings = rememberSettingsStore()
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    var onRight by remember(settings, placementKey, initiallyOnLeft) {
        mutableStateOf<Boolean?>(
            when (settings.getString("$placementKey-edge", "")) {
                "left" -> false
                "right" -> true
                else -> null
            },
        )
    }
    var yFraction by remember(settings, placementKey) {
        mutableFloatStateOf(settings.getFloat("$placementKey-yFraction", Float.NaN))
    }
    val legacyX = remember(settings, placementKey, initiallyOnLeft) {
        settings.getFloat("$preferenceName-x", if (initiallyOnLeft) 0f else Float.MAX_VALUE)
    }
    val legacyY = remember(settings, placementKey) { settings.getFloat("$preferenceName-y", Float.MAX_VALUE) }
    var maxOffset by remember(placementKey) { mutableStateOf<Offset?>(null) }
    var dragOffset by remember(placementKey) { mutableStateOf<Offset?>(null) }
    var moved by remember(placementKey) { mutableStateOf(false) }
    val bounds = maxOffset
    val defaultGapPx = with(density) { defaultBottomGap.toPx() }
    val edgeMarginPx = with(density) { edgeMargin.toPx().coerceAtLeast(0f) }

    fun minX(max: Offset): Float = edgeMarginPx.coerceAtMost(max.x)

    fun position(max: Offset): Offset {
        val dragged = dragOffset
        return Offset(
            x = when {
                onlyVertical -> max.x
                dragged != null -> dragged.x.coerceIn(minX(max), max.x)
                onRight == true -> max.x
                onRight == false -> minX(max)
                legacyX == Float.MAX_VALUE -> max.x
                !legacyX.isFinite() -> if (initiallyOnLeft) minX(max) else max.x
                /** 旧值只有像素，未保存历史宽度和密度；保留有限坐标，不猜左右边。 */
                else -> legacyX.coerceIn(minX(max), max.x)
            },
            y = when {
                dragged != null -> dragged.y.coerceIn(0f, max.y)
                yFraction.isFinite() -> yFraction.coerceIn(0f, 1f) * max.y
                legacyY == Float.MAX_VALUE || !legacyY.isFinite() -> (max.y - defaultGapPx).coerceAtLeast(0f)
                else -> legacyY.coerceIn(0f, max.y)
            },
        )
    }

    val target = bounds?.let(::position) ?: Offset.Zero
    val animatedX by key(placementKey, bounds) {
        animateFloatAsState(target.x, tween(if (dragOffset != null || !animateSnap) 0 else 300), label = "floatingX")
    }
    val animatedY by key(placementKey, bounds) {
        animateFloatAsState(target.y, tween(if (dragOffset != null || !animateSnap) 0 else 300), label = "floatingY")
    }
    val displayed = if (dragOffset != null) target else Offset(animatedX, animatedY)
    val currentDisplayed by rememberUpdatedState(displayed)
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            content(
                modifier.pointerInput(placementKey, bounds, onlyVertical, windowInfo.isWindowFocused) {
                    try {
                        detectDragGestures(
                            onDragStart = {
                                if (bounds != null) {
                                    dragOffset = Offset(currentDisplayed.x.coerceIn(minX(bounds), bounds.x), currentDisplayed.y.coerceIn(0f, bounds.y))
                                    moved = false
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            },
                            onDragCancel = {
                                dragOffset = null
                                moved = false
                            },
                            onDragEnd = {
                                val dropped = dragOffset
                                if (moved && dropped != null && bounds != null) {
                                    onRight = onlyVertical || dropped.x > (minX(bounds) + bounds.x) / 2f
                                    yFraction = if (bounds.y > 0f) dropped.y.coerceIn(0f, bounds.y) / bounds.y else 0f
                                    if (!onlyVertical) settings.putString("$placementKey-edge", if (onRight == true) "right" else "left")
                                    settings.putFloat("$placementKey-yFraction", yFraction)
                                }
                                dragOffset = null
                                moved = false
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                val previous = dragOffset
                                if (previous != null && bounds != null) {
                                    val next = Offset(
                                        if (onlyVertical) bounds.x else (previous.x + amount.x).coerceIn(minX(bounds), bounds.x),
                                        (previous.y + amount.y).coerceIn(0f, bounds.y),
                                    )
                                    moved = moved || next != previous
                                    dragOffset = next
                                }
                            },
                        )
                    } finally {
                        /** 边界或焦点重启会取消协程，不保证触发 onDragCancel。 */
                        dragOffset = null
                        moved = false
                    }
                },
            )
        },
    ) { measurables, constraints ->
        val control = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else control.width
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else control.height
        val freeWidth = (width - control.width).coerceAtLeast(0).toFloat()
        val margin = edgeMarginPx.coerceAtMost(freeWidth / 2f)
        val measuredMax = Offset(
            freeWidth - margin,
            (height - control.height - bottomAvoidance.toPx() - edgeMarginPx).coerceAtLeast(0f),
        )
        maxOffset = measuredMax
        /** 新 pane 直接按比例投影，动画只用于同一边界内的拖放吸附，避免从 top=0 滑入。 */
        val placed = if (bounds == measuredMax) displayed else position(measuredMax)
        layout(width, height) {
            val x = placed.x.coerceIn(0f, measuredMax.x).roundToInt()
            val y = placed.y.coerceIn(0f, measuredMax.y).roundToInt()
            if (onlyVertical) control.placeRelative(x, y) else control.place(x, y)
        }
    }
}
