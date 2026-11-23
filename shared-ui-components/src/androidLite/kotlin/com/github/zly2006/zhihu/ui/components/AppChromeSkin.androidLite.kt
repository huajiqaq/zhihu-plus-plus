/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 only.
 */

package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection

@Composable
actual fun AppOverlayHost(content: @Composable () -> Unit) = content()

@Composable
internal actual fun AppScaffoldSkinHost(
    modifier: Modifier,
    topBar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    floatingActionButton: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
    containerColor: Color,
    contentWindowInsets: WindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
        snackbarHost = snackbarHost,
        containerColor = containerColor,
        contentWindowInsets = contentWindowInsets,
        content = content,
    )
}

@Composable
internal actual fun MiuixTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    centered: Boolean,
    windowInsets: WindowInsets,
): Unit = error("Miuix top bars are not supported by the lite variant")

internal actual class MiuixPageTitleBarScrollState {
    actual val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {}

    actual fun collapse() = Unit
}

@Composable
internal actual fun rememberMiuixPageTitleBarScrollState(): MiuixPageTitleBarScrollState =
    remember { MiuixPageTitleBarScrollState() }

@Composable
internal actual fun MiuixPageTitleBar(
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
): Unit = error("Miuix page title bars are not supported by the lite variant")

@Composable
internal actual fun MiuixCompactTopBar(
    title: String,
    modifier: Modifier,
    subtitle: String?,
    titleTag: String?,
    onBack: (() -> Unit)?,
    backTag: String?,
    containerColor: Color?,
    actions: @Composable RowScope.() -> Unit,
): Unit = error("Miuix compact title bars are not supported by the lite variant")

@Composable
internal actual fun MiuixBackButton(
    onClick: () -> Unit,
    modifier: Modifier,
    image: ImageVector,
    contentDescription: String,
): Unit = error("Miuix back buttons are not supported by the lite variant")
