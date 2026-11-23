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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
internal actual fun MiuixFeedCard(
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        onLongPress = onLongClick,
        showIndication = true,
        pressFeedbackType = PressFeedbackType.Sink,
        insideMargin = PaddingValues(
            start = AppDimens.FeedCardContentHorizontalPadding,
            top = AppDimens.FeedCardContentTopPadding,
            end = AppDimens.FeedCardContentHorizontalPadding,
            bottom = AppDimens.FeedCardContentBottomPadding,
        ),
        content = { content() },
    )
}
