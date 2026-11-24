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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection

@Composable
expect fun AppOverlayHost(content: @Composable () -> Unit)

@Composable
internal expect fun AppScaffoldSkinHost(
    modifier: Modifier,
    topBar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    floatingActionButton: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
    containerColor: Color,
    contentWindowInsets: WindowInsets,
    content: @Composable (PaddingValues) -> Unit,
)

@Composable
internal expect fun MiuixTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    centered: Boolean,
    windowInsets: WindowInsets,
)

internal expect class MiuixPageTitleBarScrollState {
    val nestedScrollConnection: NestedScrollConnection

    fun collapse()
}

@Composable
internal expect fun rememberMiuixPageTitleBarScrollState(): MiuixPageTitleBarScrollState

@Composable
internal expect fun MiuixPageTitleBar(
    title: String,
    modifier: Modifier,
    subtitle: String?,
    titleTag: String?,
    scrollState: MiuixPageTitleBarScrollState?,
    onBack: (() -> Unit)?,
    backTag: String?,
    containerColor: Color,
    multilineTitle: Boolean,
    onTitleClick: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
)

@Composable
internal expect fun MiuixCompactTopBar(
    title: String,
    modifier: Modifier,
    subtitle: String?,
    titleTag: String?,
    onBack: (() -> Unit)?,
    backTag: String?,
    containerColor: Color?,
    actions: @Composable RowScope.() -> Unit,
)

@Composable
internal expect fun MiuixBackButton(
    onClick: () -> Unit,
    modifier: Modifier,
    image: ImageVector,
    contentDescription: String,
)
