/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 only.
 */

package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal actual object MiuixPageRenderer {
    @Composable
    actual fun SearchBar(
        hint: String,
        onClick: () -> Unit,
        modifier: Modifier,
        trailing: (@Composable () -> Unit)?,
    ) {
        val colors = MiuixTheme.colorScheme
        val textStyle = MiuixTheme.textStyles.main.copy(fontWeight = FontWeight.Normal)
        val container = LocalAppSkinTokens.current.surfaceContainerHigh

        /** 输入仅为启用搜索入口的外观；按实际胶囊底色预合成同一提示色，只在局部桥接不透明前景。 */
        val hintColor = colors.onSurfaceVariantSummary.compositeOver(container)
        Box(modifier = modifier.fillMaxWidth()) {
            SearchBar(
                inputField = {
                    MiuixTheme(colors = colors.copy(onSurfaceContainerHigh = hintColor)) {
                        InputField(
                            query = "",
                            onQueryChange = {},
                            onSearch = {},
                            expanded = false,
                            onExpandedChange = {},
                            enabled = false,
                            label = hint,
                            textStyle = textStyle,
                            modifier = Modifier.fillMaxWidth().heightIn(min = AppDimens.SearchBarHeightMiuix),
                            color = container,
                            trailingIcon = trailing?.let { { Spacer(Modifier.width(AppDimens.TouchTarget)) } },
                        )
                    }
                },
                onExpandedChange = {},
                expanded = false,
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
                insideMargin = DpSize(0.dp, 0.dp),
                content = {},
            )
            Box(Modifier.matchParentSize()) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .semantics { contentDescription = hint }
                        .clickable(role = Role.Button, onClickLabel = hint, onClick = onClick),
                )
                trailing?.let {
                    Box(
                        modifier = Modifier.align(Alignment.CenterEnd).width(AppDimens.TouchTarget).fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        it()
                    }
                }
            }
        }
    }
}
