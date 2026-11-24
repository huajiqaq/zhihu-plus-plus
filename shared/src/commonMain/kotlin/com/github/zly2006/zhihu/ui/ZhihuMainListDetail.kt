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

import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.ui.components.AppDimens
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope

/** 列表栏的最小内容宽度：放得下一行常见的收藏夹标题，字号放大时按比例加宽。 */
@Composable
internal fun minimumListContentWidth(fontScale: Float): Dp {
    val textMeasurer = rememberTextMeasurer()
    return maxOf(
        MIN_LIST_PANE_WIDTH * fontScale,
        with(LocalDensity.current) {
            textMeasurer
                .measure("值得反复阅读的收藏内容", style = MaterialTheme.typography.titleMedium, softWrap = false)
                .size.width
                .toDp()
        } + AppDimens.PageHorizontalPadding * 2,
    )
}

/** 列表栏当前实际占可用宽度的比例；拖动和按键微调都从夹取后的值起算。 */
internal fun visibleListPaneRatio(
    containerWidth: Dp,
    requestedRatio: Float,
    minimumListWidth: Dp,
    minimumDetailWidth: Dp,
): Float = normalizedListPaneWidth(containerWidth, requestedRatio, minimumListWidth, minimumDetailWidth).value /
    (containerWidth - LIST_DETAIL_DIVIDER_WIDTH).value

/**
 * 两栏共用一个 NavDisplay 时，条目按所在栏的宽度测量，右栏从 [detailPaneStart] 起摆放。
 * [isLeft] 的来源栏只改布局，不叠加转场、压暗和圆角。
 */
internal fun listDetailEntryTransition(
    base: NavTransition,
    isLeft: Boolean,
    isRight: Boolean,
    paneWidth: Dp,
    detailPaneStart: Dp,
    effects: NavDisplayEffects,
    layoutDirection: LayoutDirection,
): NavTransition {
    val radius = effects.cornerClipRadius
    val allCorners = effects.cornerClipMode == NavCornerClipMode.All
    val roundLeft = allCorners || layoutDirection == LayoutDirection.Ltr
    val roundRight = allCorners || !roundLeft
    val paneShape = AbsoluteRoundedCornerShape(
        topLeft = if (roundLeft) radius else 0.dp,
        bottomLeft = if (roundLeft) radius else 0.dp,
        topRight = if (roundRight) radius else 0.dp,
        bottomRight = if (roundRight) radius else 0.dp,
    )
    return object : NavTransition by base {
        override fun Modifier.transformEntry(scope: NavTransitionScope): Modifier {
            val width = with(scope.density) { paneWidth.roundToPx() }.coerceAtLeast(0)
            val paneScope = object : NavTransitionScope by scope {
                override val layoutSize: IntSize get() = IntSize(width, scope.layoutSize.height)
            }
            val coveredScope = object : NavTransitionScope by paneScope {
                override val relativeDepth: Float get() = scope.relativeDepth + 1f
            }
            val placement = layout { measurable, constraints ->
                val placeable = measurable.measure(Constraints.fixed(width, constraints.maxHeight))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val x = if (isRight) with(scope.density) { detailPaneStart.roundToPx() } else 0
                    placeable.placeRelative(x, 0)
                }
            }.clipToBounds()
            if (isLeft) return placement
            /** 共用 NavDisplay 的全屏效果会影响另一栏；在条目自身的栏宽内绘制。 */
            val pane = placement.drawWithContent {
                if (scope.relativeDepth <= 0f) {
                    drawRect(Color.Black, alpha = effects.dimAmount * base.scrimFraction(coveredScope).coerceIn(0f, 1f))
                }
                drawContent()
            }
            return with(base) { pane.transformEntry(paneScope) }.graphicsLayer {
                shape = paneShape
                clip = effects.shouldClipCornersAt(scope.relativeDepth)
            }
        }
    }
}
