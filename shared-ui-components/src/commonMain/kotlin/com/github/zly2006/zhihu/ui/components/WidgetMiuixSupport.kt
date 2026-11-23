/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 */

package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

@Composable
internal expect fun MiuixBottomBar(
    items: List<BottomBarItem>,
    modifier: Modifier,
)

@Composable
internal expect fun MiuixNavigationRail(
    items: List<BottomBarItem>,
    modifier: Modifier,
)

@Composable
internal expect fun MiuixDraggableRefreshButton(
    onClick: () -> Unit,
    modifier: Modifier,
    opacityFraction: Float,
    compact: Boolean,
    content: @Composable () -> Unit,
)

@Composable
internal expect fun RowScope.MiuixFloatingNavigationItem(
    item: BottomBarItem,
    modifier: Modifier,
    itemWidth: Dp,
    highlightSelected: Boolean = true,
    contentColor: Color? = null,
)
