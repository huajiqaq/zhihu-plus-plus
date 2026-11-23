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

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

@Composable
internal actual fun MiuixBottomBar(items: List<BottomBarItem>, modifier: Modifier): Unit =
    error("Miuix theme widgets are not supported by the lite variant")

@Composable
internal actual fun MiuixNavigationRail(items: List<BottomBarItem>, modifier: Modifier): Unit =
    error("Miuix theme widgets are not supported by the lite variant")

@Composable
internal actual fun MiuixDraggableRefreshButton(
    onClick: () -> Unit,
    modifier: Modifier,
    opacityFraction: Float,
    compact: Boolean,
    content: @Composable () -> Unit,
): Unit = error("Miuix theme widgets are not supported by the lite variant")

@Composable
internal actual fun RowScope.MiuixFloatingNavigationItem(item: BottomBarItem, modifier: Modifier, itemWidth: Dp, highlightSelected: Boolean, contentColor: Color?): Unit =
    error("Miuix theme widgets are not supported by the lite variant")
