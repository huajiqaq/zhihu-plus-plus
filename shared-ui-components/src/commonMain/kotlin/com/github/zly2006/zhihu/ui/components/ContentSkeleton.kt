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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.i18n.WidgetsStrings
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens

enum class SkeletonLayout { Comment, Feed, Paragraph }

@Composable
fun ContentSkeleton(
    layout: SkeletonLayout,
    modifier: Modifier = Modifier,
    description: String = WidgetsStrings.loading,
) {
    val fill = LocalAppSkinTokens.current.onSurface.copy(alpha = 0.14f)
    val inset = when (layout) {
        SkeletonLayout.Comment -> AppDimens.PageHorizontalPadding
        SkeletonLayout.Feed -> AppDimens.FeedCardContentHorizontalPadding +
            if (settingSkinIsMiuix()) AppDimens.FeedCardHorizontalPaddingMiuix else AppDimens.FeedCardHorizontalPaddingM3e
        SkeletonLayout.Paragraph -> 0.dp
    }
    Column(
        modifier.fillMaxWidth().semantics {
            contentDescription = description
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        }.padding(horizontal = inset, vertical = AppDimens.Space12),
        verticalArrangement = Arrangement.spacedBy(if (layout == SkeletonLayout.Paragraph) AppDimens.Space12 else AppDimens.Space28),
    ) {
        if (layout == SkeletonLayout.Paragraph) {
            repeat(4) { line -> SkeletonLine(fill, if (line == 3) 0.58f else 1f) }
        } else {
            repeat(3) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12)) {
                    val comment = layout == SkeletonLayout.Comment
                    if (comment) Spacer(Modifier.size(AppDimens.AvatarSizeMedium).background(fill, CircleShape))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppDimens.Space8)) {
                        SkeletonLine(fill, if (comment) 0.28f else 0.76f, if (comment) AppDimens.Space10 else AppDimens.Space16)
                        SkeletonLine(fill, 0.92f)
                        SkeletonLine(fill, if (row == 1) 0.53f else 0.68f)
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonLine(fill: Color, fraction: Float, height: Dp = AppDimens.Space12) {
    Spacer(Modifier.fillMaxWidth(fraction).height(height).background(fill, CircleShape))
}
