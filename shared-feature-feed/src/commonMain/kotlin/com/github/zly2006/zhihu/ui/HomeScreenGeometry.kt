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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import com.github.zly2006.zhihu.ui.components.AppDimens

internal fun homeScreenUtilityBottomAvoidance(
    density: Density,
    homeBounds: Rect?,
    createFabBounds: Rect?,
    createFabVisible: Boolean,
    innerPadding: PaddingValues,
    readingPlayerOverlayPadding: Dp,
): Dp = with(density) {
    val pane = homeBounds
    val fab = createFabBounds.takeIf { createFabVisible }
    if (pane != null && fab != null) {
        (pane.bottom - fab.top).coerceAtLeast(0f).toDp() + AppDimens.Space8
    } else {
        innerPadding.calculateBottomPadding() + readingPlayerOverlayPadding + AppDimens.Space8
    }
}

/** 固定视口高度，避免短列表测量与末端留白互相反馈。 */
internal fun homeScreenFeedBottomPadding(
    density: Density,
    feedListBounds: Rect?,
    utilityFabBounds: Rect?,
    utilityFabsVisible: Boolean,
    innerPadding: PaddingValues,
    readingPlayerOverlayPadding: Dp,
): Dp = maxOf(
    innerPadding.calculateBottomPadding() + readingPlayerOverlayPadding,
    with(density) {
        val list = feedListBounds
        val column = utilityFabBounds.takeIf { utilityFabsVisible }
        if (list != null && column != null && column.top < list.bottom && column.bottom > list.top) {
            /** 按同一 root 坐标系预留整列高度，让末卡菜单能滚出按钮遮挡。 */
            (list.bottom - column.top.coerceAtLeast(list.top)).coerceAtLeast(0f).toDp() + AppDimens.Space8
        } else {
            AppDimens.ElevationNone
        }
    },
)
