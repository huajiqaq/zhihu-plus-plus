/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 only.
 */

package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun MiuixAppBadgedBox(
    label: String?,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
): Unit = error("Miuix badges are not supported by the lite variant")

@Composable
internal actual fun MiuixAppTabRow(
    titles: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
    scrollable: Boolean,
    secondary: Boolean,
    enabled: Boolean,
): Unit = error("Miuix tab rows are not supported by the lite variant")

@Composable
internal actual fun MiuixAppSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
): Unit = error("Miuix segmented controls are not supported by the lite variant")
