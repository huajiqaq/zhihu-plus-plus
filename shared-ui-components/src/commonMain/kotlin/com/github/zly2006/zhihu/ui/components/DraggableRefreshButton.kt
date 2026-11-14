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

import androidx.compose.ui.layout.Layout
import androidx.compose.ui.Alignment
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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
    onClick: () -> Unit,
    content: @Composable () -> Unit = {
        Icon(Icons.Default.Refresh, contentDescription = "刷新")
    },
) {
    val density = LocalDensity.current
    val screenSize = LocalWindowInfo.current.containerSize
    val settings = rememberSettingsStore()

    var offsetX by remember(preferenceName, initiallyOnLeft) {
        mutableFloatStateOf(
            settings.getFloat(
                "$preferenceName-x",
                if (initiallyOnLeft) 0f else Float.MAX_VALUE,
            ),
        )
    }
    var offsetY by remember { mutableFloatStateOf(settings.getFloat("$preferenceName-y", Float.MAX_VALUE)) }
    var pressing by remember { mutableStateOf(false) }
    val maxStoredOffsetY = with(density) {
        (screenSize.height - 250.dp.toPx()).coerceAtLeast(0f)
    }
    val maxVisibleOffsetY = with(density) {
        (maxStoredOffsetY - bottomAvoidance.toPx()).coerceAtLeast(0f)
    }

    fun adjustFabPosition() {
        with(density) {
            offsetX = offsetX.coerceIn(0f, screenSize.width - 56.dp.toPx())
            offsetY = offsetY.coerceIn(0f, maxStoredOffsetY)
        }
    }

    adjustFabPosition()
    val visibleOffsetY = offsetY.coerceAtMost(maxVisibleOffsetY)

    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = tween(if (pressing) 1 else 300),
        label = "offsetX",
    )
    val animatedOffsetY by animateFloatAsState(
        targetValue = visibleOffsetY,
        animationSpec = tween(if (pressing) 1 else 300),
        label = "offsetY",
    )
    val displayedOffsetX = if (pressing) offsetX else animatedOffsetX
    val displayedOffsetY = if (pressing) visibleOffsetY else animatedOffsetY
    val hapticFeedback = LocalHapticFeedback.current

    val opacityFraction by rememberObservedSetting(settings, PREF_FAB_OPACITY) {
        getInt(PREF_FAB_OPACITY, DEFAULT_FAB_OPACITY).coerceIn(10, 100) / 100f
    }
    FloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = FloatingActionButtonDefaults.containerColor.copy(alpha = opacityFraction),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = opacityFraction),
        elevation = if (opacityFraction < 1f) {
            FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
        } else {
            FloatingActionButtonDefaults.elevation()
        },
        modifier = modifier
            .offset { IntOffset(displayedOffsetX.roundToInt(), displayedOffsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        offsetX = animatedOffsetX
                        offsetY = animatedOffsetY
                        pressing = true
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragCancel = { pressing = false },
                    onDragEnd = {
                        pressing = false
                        adjustFabPosition()
                        val screenWidth = screenSize.width.toFloat()
                        with(density) {
                            offsetX =
                                if (offsetX < screenWidth / 2) {
                                    0f
                                } else {
                                    screenWidth - 56.dp.toPx()
                                }
                        }
                        settings.putFloat("$preferenceName-x", offsetX)
                        settings.putFloat("$preferenceName-y", offsetY)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (pressing) {
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                            adjustFabPosition()
                        }
                    },
                )
            },
        content = content,
    )
}

@Composable
fun DraggableFabColumn(
    modifier: Modifier = Modifier,
    preferenceName: String = "fabColumn",
    bottomAvoidance: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val settings = rememberSettingsStore()
    val hapticFeedback = LocalHapticFeedback.current
    /** 首帧按实测边界直接放置，边界确定后重建动画，避免按钮组出现时滑入。 */
    var maxOffsetY by remember { mutableFloatStateOf(Float.NaN) }
    var offsetY by remember(preferenceName) {
        mutableFloatStateOf(settings.getFloat("$preferenceName-y", Float.MAX_VALUE))
    }
    var pressing by remember { mutableStateOf(false) }
    val boundsKnown = !maxOffsetY.isNaN()
    val resolvedY = if (boundsKnown) resolveDragOffset(offsetY, 0f, maxOffsetY) else 0f
    val animatedY = key(boundsKnown) {
        animateFloatAsState(
            targetValue = resolvedY,
            animationSpec = tween(if (pressing) 1 else 300),
            label = "fabColumnY",
        )
    }
    val displayedY = if (pressing) resolvedY else animatedY.value
    val currentDisplayedY by rememberUpdatedState(displayedY)
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            Column(
                modifier = modifier.pointerInput(preferenceName) {
                    detectDragGestures(
                        onDragStart = {
                            offsetY = currentDisplayedY.coerceIn(0f, maxOffsetY)
                            pressing = true
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragCancel = { pressing = false },
                        onDragEnd = {
                            pressing = false
                            settings.putFloat("$preferenceName-y", offsetY.coerceIn(0f, maxOffsetY))
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offsetY = (offsetY + dragAmount.y).coerceIn(0f, maxOffsetY)
                        },
                    )
                },
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End,
                content = content,
            )
        },
    ) { measurables, constraints ->
        val column = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else column.width
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else column.height
        val bound = (height - column.height - bottomAvoidance.toPx()).coerceAtLeast(0f)
        maxOffsetY = bound
        val y = if (boundsKnown) displayedY else resolveDragOffset(offsetY, 0f, bound)
        layout(width, height) {
            column.placeRelative((width - column.width).coerceAtLeast(0), y.coerceIn(0f, bound).roundToInt())
        }
    }
}

private fun resolveDragOffset(saved: Float, min: Float, max: Float): Float =
    if (saved == Float.MAX_VALUE) max else saved.coerceIn(min, max)
