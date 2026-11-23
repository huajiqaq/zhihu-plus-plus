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

package com.github.zly2006.zhihu.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppChip
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppSegmentedControl
import com.github.zly2006.zhihu.ui.components.AppTextButton
import kotlin.math.roundToInt
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

private const val QUESTION_DETAIL_COLLAPSE_THRESHOLD = 100
private val QUESTION_DETAIL_COLLAPSED_MAX_HEIGHT: Dp = 180.dp
private val QUESTION_DETAIL_MASK_HEIGHT: Dp = 64.dp

@Composable
internal fun QuestionAnimatedBodyHeader(
    questionId: Long,
    questionContent: String,
    questionContentPlainText: String,
    topics: List<DataHolder.Topic>,
    onTopicClick: (DataHolder.Topic) -> Unit,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    answerCount: Int,
    currentSort: String,
    onSortChange: (String) -> Unit,
) {
    val allowDetailCollapse =
        questionContentPlainText.length >= QUESTION_DETAIL_COLLAPSE_THRESHOLD || questionContent.contains("<img")
    val density = LocalDensity.current
    val sectionSpacingPx = with(density) { 16.dp.roundToPx() }
    val collapsedViewportHeightPx = with(density) { QUESTION_DETAIL_COLLAPSED_MAX_HEIGHT.roundToPx() }
    var fullViewportHeightPx by remember(questionId) { mutableIntStateOf(0) }
    var animationInitialized by remember(questionId) { mutableStateOf(false) }
    val animatedViewportHeightPx = remember(questionId) { Animatable(collapsedViewportHeightPx.toFloat()) }
    val animationSpec = remember { tween<Float>(durationMillis = 420, easing = FastOutSlowInEasing) }

    LaunchedEffect(questionId) {
        animationInitialized = false
        animatedViewportHeightPx.snapTo(collapsedViewportHeightPx.toFloat())
    }
    LaunchedEffect(isExpanded, fullViewportHeightPx, collapsedViewportHeightPx) {
        if (!allowDetailCollapse || fullViewportHeightPx <= 0) return@LaunchedEffect
        val collapsedTarget = collapsedViewportHeightPx.coerceAtMost(fullViewportHeightPx).toFloat()
        val targetHeight = if (isExpanded) fullViewportHeightPx.toFloat() else collapsedTarget
        if (!animationInitialized) {
            animationInitialized = true
            animatedViewportHeightPx.snapTo(targetHeight)
            return@LaunchedEffect
        }
        if ((animatedViewportHeightPx.value - targetHeight).let { if (it < 0f) -it else it } < 0.5f) {
            return@LaunchedEffect
        }
        animatedViewportHeightPx.animateTo(targetValue = targetHeight, animationSpec = animationSpec)
    }

    val collapsedTargetPx = collapsedViewportHeightPx.coerceAtMost(fullViewportHeightPx).toFloat()
    val expandedRangePx = (fullViewportHeightPx.toFloat() - collapsedTargetPx).coerceAtLeast(1f)
    val expandProgress =
        if (!allowDetailCollapse || fullViewportHeightPx <= 0 || fullViewportHeightPx <= collapsedViewportHeightPx) {
            1f
        } else {
            ((animatedViewportHeightPx.value - collapsedTargetPx) / expandedRangePx).coerceIn(0f, 1f)
        }
    val viewportHeightPx =
        if (allowDetailCollapse) {
            animatedViewportHeightPx.value.roundToInt()
        } else {
            fullViewportHeightPx
        }

    SubcomposeLayout(modifier = Modifier.fillMaxWidth()) { constraints ->
        val looseConstraints =
            constraints.copy(
                minWidth = 0,
                minHeight = 0,
                maxHeight = Constraints.Infinity,
            )
        val detailPlaceable =
            subcompose("detail") {
                if (questionContent.isEmpty()) return@subcompose
                if (allowDetailCollapse) {
                    QuestionDetailAnimatedViewport(
                        questionId = questionId,
                        questionContent = questionContent,
                        viewportHeightPx = viewportHeightPx,
                        isExpanded = isExpanded,
                        overlayAlpha = 1f - expandProgress,
                        onToggleExpanded = onToggleExpanded,
                        onMeasuredFullHeight = { fullViewportHeightPx = it },
                    )
                } else {
                    QuestionDetailStaticContent(
                        questionId = questionId,
                        questionContent = questionContent,
                        onMeasuredHeight = { fullViewportHeightPx = it },
                    )
                }
            }.singleOrNull()?.measure(looseConstraints)
        val controlsPlaceable =
            subcompose("controls") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.Space12),
                ) {
                    if (topics.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8),
                            verticalArrangement = Arrangement.spacedBy(AppDimens.Space8),
                        ) {
                            topics.forEach { topic ->
                                AppChip(
                                    label = "# ${topic.name}",
                                    selected = false,
                                    onClick = { onTopicClick(topic) },
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12),
                    ) {
                        Text(
                            text = "$answerCount 回答",
                            style = LocalAppSkinTokens.current.title4,
                            color = LocalAppSkinTokens.current.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        AppSegmentedControl(
                            options = listOf("默认", "最新"),
                            selectedIndex = if (currentSort == "updated") 1 else 0,
                            onSelect = { onSortChange(if (it == 0) "default" else "updated") },
                            modifier = Modifier.widthIn(max = AppDimens.SettingControlWidth).testTag(QUESTION_SORT_CONTROL_TAG),
                            testTags = listOf(QUESTION_SORT_DEFAULT_TAG, QUESTION_SORT_UPDATED_TAG),
                        )
                    }
                }
            }.single().measure(looseConstraints)
        val detailHeight = detailPlaceable?.height ?: 0
        val controlsOffset = detailHeight + if (detailPlaceable == null) 0 else sectionSpacingPx
        val totalHeight = controlsOffset + controlsPlaceable.height
        layout(width = constraints.maxWidth, height = totalHeight) {
            detailPlaceable?.place(0, 0)
            controlsPlaceable.place(0, controlsOffset)
        }
    }
}

@Composable
private fun QuestionDetailStaticContent(
    questionId: Long,
    questionContent: String,
    onMeasuredHeight: (Int) -> Unit,
) {
    SubcomposeLayout(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(QUESTION_DETAIL_CONTENT_TAG),
    ) { constraints ->
        val placeable =
            subcompose("static_detail") {
                QuestionDetailContent(questionId = questionId, html = questionContent)
            }.single().measure(
                constraints.copy(
                    minWidth = 0,
                    minHeight = 0,
                    maxHeight = Constraints.Infinity,
                ),
            )
        if (placeable.height > 0) {
            onMeasuredHeight(placeable.height)
        }
        layout(width = constraints.maxWidth, height = placeable.height) {
            placeable.place(0, 0)
        }
    }
}

@Composable
private fun QuestionDetailAnimatedViewport(
    questionId: Long,
    questionContent: String,
    viewportHeightPx: Int,
    isExpanded: Boolean,
    overlayAlpha: Float,
    onToggleExpanded: () -> Unit,
    onMeasuredFullHeight: (Int) -> Unit,
) {
    val maskHeightPx = with(LocalDensity.current) { QUESTION_DETAIL_MASK_HEIGHT.roundToPx() }
    SubcomposeLayout(
        modifier =
            Modifier
                .fillMaxWidth()
                .clipToBounds()
                .testTag(if (isExpanded) QUESTION_DETAIL_CONTENT_TAG else QUESTION_DETAIL_PREVIEW_TAG),
    ) { constraints ->
        val looseConstraints =
            constraints.copy(
                minWidth = 0,
                minHeight = 0,
                maxHeight = Constraints.Infinity,
            )
        val contentPlaceable =
            subcompose("content") {
                Layout(
                    content = { QuestionDetailContent(questionId = questionId, html = questionContent) },
                    modifier = Modifier.fillMaxWidth().clipToBounds(),
                ) { measurables, contentConstraints ->
                    val content = measurables.single().measure(
                        contentConstraints.copy(minHeight = 0, maxHeight = Constraints.Infinity),
                    )
                    if (content.height > 0) onMeasuredFullHeight(content.height)
                    val height = viewportHeightPx.coerceIn(0, content.height)
                    layout(width = content.width, height = height) {
                        content.place(0, 0)
                    }
                }
            }.single().measure(looseConstraints)
        val layoutHeight = contentPlaceable.height
        val buttonPlaceable =
            subcompose("button") {
                QuestionDetailToggleButton(
                    isExpanded = isExpanded,
                    onClick = onToggleExpanded,
                )
            }.single().measure(looseConstraints)
        val overlayPlaceable =
            subcompose("overlay") {
                QuestionDetailOverlayMask(
                    alpha = overlayAlpha,
                    modifier = Modifier.fillMaxWidth(),
                )
            }.single().measure(
                Constraints(
                    minWidth = constraints.maxWidth,
                    maxWidth = constraints.maxWidth,
                    minHeight = 0,
                    maxHeight = layoutHeight.coerceAtLeast(0),
                ),
            )
        layout(width = constraints.maxWidth, height = layoutHeight + buttonPlaceable.height) {
            contentPlaceable.place(0, 0)
            if (overlayAlpha > 0f) {
                overlayPlaceable.place(0, (layoutHeight - minOf(maskHeightPx, overlayPlaceable.height)).coerceAtLeast(0))
            }
            buttonPlaceable.place(constraints.maxWidth - buttonPlaceable.width, layoutHeight)
        }
    }
}

@Composable
private fun QuestionDetailOverlayMask(alpha: Float, modifier: Modifier = Modifier) {
    val surfaceColor = MaterialTheme.colorScheme.background
    Box(
        modifier =
            modifier
                .height(QUESTION_DETAIL_MASK_HEIGHT)
                .graphicsLayer { this.alpha = alpha }
                .blur(12.dp)
                .background(
                    brush =
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                surfaceColor.copy(alpha = 0.7f),
                                surfaceColor,
                            ),
                        ),
                ),
    )
}

@Composable
private fun QuestionDetailToggleButton(
    isExpanded: Boolean,
    onClick: () -> Unit,
) {
    AppTextButton(
        onClick = onClick,
        modifier =
            Modifier
                .padding(end = AppDimens.Space4)
                .testTag(QUESTION_DETAIL_TOGGLE_TAG),
    ) {
        Icon(
            imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
        )
        Spacer(Modifier.width(4.dp))
        Text(if (isExpanded) "收起详情" else "展开详情")
    }
}
