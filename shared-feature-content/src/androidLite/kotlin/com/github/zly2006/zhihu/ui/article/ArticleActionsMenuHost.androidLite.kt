/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 */

package com.github.zly2006.zhihu.ui.article

import androidx.compose.runtime.Composable

@Composable
internal actual fun MiuixArticleActionsMenuHost(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
): Unit = error("Miuix article actions are not supported by the lite variant")
