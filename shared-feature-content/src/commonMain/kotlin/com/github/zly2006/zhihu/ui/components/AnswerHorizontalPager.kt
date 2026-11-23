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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

/**
 * 左右 overscroll 切换回答容器。
 *
 * 主内容水平偏移，相邻回答的预览内容由调用方通过 [previousContent] / [nextContent] 提供，并从侧边露出。
 * 使用 tanh 阻尼、震动反馈、spring 回弹。
 */
@Composable
fun AnswerHorizontalOverscroll(
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onNavigatePrevious: () -> Unit,
    onNavigateNext: () -> Unit,
    previousContent: (@Composable () -> Unit)?,
    nextContent: (@Composable () -> Unit)?,
    answerSwitchSensitivity: Float = DEFAULT_ANSWER_SWITCH_SENSITIVITY,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val tokens = LocalAppSkinTokens.current

    val maxOverscrollPx = with(density) { MAX_HORIZONTAL_OVERSCROLL_DP.dp.toPx() }
    val triggerThresholdPx = with(density) {
        (HORIZONTAL_TRIGGER_THRESHOLD_DP / normalizedAnswerSwitchSensitivity(answerSwitchSensitivity)).dp.toPx()
    }

    val overscrollOffset = remember { Animatable(0f) }
    var hasTriggeredHaptic by remember { mutableStateOf(false) }
    var rawDragAccumulator by remember { mutableFloatStateOf(0f) }
    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("answer_horizontal_overscroll")
            .clipToBounds()
            .onSizeChanged { containerWidthPx = it.width.toFloat() }
            .pointerInput(canGoPrevious, canGoNext, triggerThresholdPx) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        isDragging = true
                        rawDragAccumulator = 0f
                        hasTriggeredHaptic = false
                    },
                    onDragEnd = {
                        isDragging = false
                        val currentOffset = overscrollOffset.value
                        if (abs(currentOffset) >= triggerThresholdPx) {
                            if (currentOffset > 0 && canGoPrevious) {
                                onNavigatePrevious()
                            } else if (currentOffset < 0 && canGoNext) {
                                onNavigateNext()
                            }
                        }
                        rawDragAccumulator = 0f
                        hasTriggeredHaptic = false
                        /** Unit 回调不能证明已换页；加载失败或重复请求被拒绝时也必须回到原位。 */
                        coroutineScope.launch {
                            overscrollOffset.animateTo(
                                0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                            )
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                        rawDragAccumulator = 0f
                        hasTriggeredHaptic = false
                        coroutineScope.launch { overscrollOffset.snapTo(0f) }
                    },
                ) { _, dragAmount ->
                    val canDragRight = dragAmount > 0 && canGoPrevious
                    val canDragLeft = dragAmount < 0 && canGoNext
                    val continuing = (overscrollOffset.value > 0 && dragAmount > 0) ||
                        (overscrollOffset.value < 0 && dragAmount < 0)
                    val reversing = (overscrollOffset.value > 0 && dragAmount < 0) ||
                        (overscrollOffset.value < 0 && dragAmount > 0)

                    if (canDragRight || canDragLeft || continuing || reversing) {
                        rawDragAccumulator += dragAmount
                        if ((rawDragAccumulator > 0 && !canGoPrevious) ||
                            (rawDragAccumulator < 0 && !canGoNext)
                        ) {
                            rawDragAccumulator = 0f
                        }
                        val newOffset = dampedOverscrollOffset(
                            rawDelta = rawDragAccumulator,
                            maxOverscrollPx = maxOverscrollPx,
                            dampingFactor = HORIZONTAL_DAMPING_FACTOR,
                        )
                        coroutineScope.launch { overscrollOffset.snapTo(newOffset) }
                        if (!hasTriggeredHaptic && abs(newOffset) >= triggerThresholdPx) {
                            hasTriggeredHaptic = true
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        if (hasTriggeredHaptic && abs(newOffset) < triggerThresholdPx) {
                            hasTriggeredHaptic = false
                        }
                    }
                }
            },
    ) {
        // 左侧相邻回答（上一个），始终存活以避免预览重建闪动。
        if (previousContent != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clearAndSetSemantics { }
                    .offset {
                        IntOffset(
                            (overscrollOffset.value - containerWidthPx).roundToInt(),
                            0,
                        )
                    },
            ) {
                previousContent()
            }
        }

        // 右侧相邻回答（下一个），始终存活以避免预览重建闪动。
        if (nextContent != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clearAndSetSemantics { }
                    .offset {
                        IntOffset(
                            (overscrollOffset.value + containerWidthPx).roundToInt(),
                            0,
                        )
                    },
            ) {
                nextContent()
            }
        }

        // 主内容，跟随水平 overscroll 偏移。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(overscrollOffset.value.roundToInt(), 0) },
        ) {
            content()
        }

        if (abs(overscrollOffset.value) > 0.5f) {
            val previous = overscrollOffset.value > 0f
            val readyToSwitch = isDragging && abs(overscrollOffset.value) >= triggerThresholdPx
            val directionCaption = if (previous) "上一个回答" else "下一个回答"
            val gestureCaption = if (readyToSwitch) "松开切换" else "继续滑动查看"
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + AppDimens.Space16)
                    .background(tokens.secondaryVariant, MaterialTheme.shapes.extraLarge)
                    .testTag("answer_horizontal_preview_caption")
                    .semantics(mergeDescendants = true) {
                        stateDescription = "$directionCaption，$gestureCaption"
                    }.padding(horizontal = AppDimens.Space16, vertical = AppDimens.Space12),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (previous) Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = tokens.onSecondaryVariant,
                    modifier = Modifier.size(AppDimens.IconSizeMedium),
                )
                Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Space2)) {
                    Text(
                        text = directionCaption,
                        color = tokens.onSecondaryVariant,
                        style = tokens.footnote1,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(text = gestureCaption, color = tokens.onSecondaryVariant, style = tokens.footnote2)
                }
            }
            Box(
                modifier = Modifier
                    .align(if (previous) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + AppDimens.Space96,
                        bottom = AppDimens.Space96,
                        start = AppDimens.Space4,
                        end = AppDimens.Space4,
                    ).fillMaxHeight()
                    .width(AppDimens.Space4)
                    .testTag("answer_horizontal_edge_progress")
                    .semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(
                            (abs(overscrollOffset.value) / triggerThresholdPx).coerceIn(0f, 1f),
                            0f..1f,
                        )
                    }.drawBehind {
                        val fraction = (abs(overscrollOffset.value) / triggerThresholdPx).coerceIn(0f, 1f)
                        val radius = CornerRadius(size.width / 2f)
                        drawRoundRect(tokens.outline.copy(alpha = 0.2f), cornerRadius = radius)
                        drawRoundRect(
                            color = tokens.primary,
                            size = Size(size.width, size.height * fraction),
                            cornerRadius = radius,
                        )
                    },
            )
        }
    }
}

private const val MAX_HORIZONTAL_OVERSCROLL_DP = 300f
private const val HORIZONTAL_TRIGGER_THRESHOLD_DP = 46f
private const val HORIZONTAL_DAMPING_FACTOR = 1.2f
