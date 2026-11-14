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

@Composable
internal actual fun MiuixFloatingToolbar(
    modifier: Modifier,
    expanded: Boolean,
    trailingContent: (@Composable RowScope.() -> Unit)?,
    content: @Composable RowScope.() -> Unit,
): Unit = error("Miuix floating toolbars are not supported by the lite variant")
