/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 */

package com.github.zly2006.zhihu.ui.article

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.overlay.OverlayListPopup

@Composable
internal actual fun MiuixArticleActionsMenuHost(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    OverlayListPopup(
        show = true,
        alignment = PopupPositionProvider.Align.End,
        enableWindowDim = false,
        onDismissRequest = onDismissRequest,
        maxHeight = 560.dp,
        minWidth = 240.dp,
    ) {
        ListPopupColumn {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
            ) {
                content()
            }
        }
    }
}
