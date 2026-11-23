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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.ui.components.AppBottomSheet
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ArticleActionsMenuHost(
    showMenu: Boolean,
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    content: @Composable () -> Unit,
) {
    if (!showMenu) return
    if (settingSkinIsMiuix()) {
        MiuixArticleActionsMenuHost(onDismissRequest, content)
    } else {
        AppBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheetState) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                content()
            }
        }
    }
}

@Composable
internal expect fun MiuixArticleActionsMenuHost(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
)
