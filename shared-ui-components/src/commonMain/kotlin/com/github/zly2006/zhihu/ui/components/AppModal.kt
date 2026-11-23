/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.constrainHeight
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens

/** 无 Scaffold 时用窗口宿主接收焦点和返回事件。 */
@Composable
fun AppHostlessAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    if (settingSkinIsMiuix()) {
        RegisterOverlayBlur()
        MiuixHostlessAlertDialog(onDismissRequest, modifier, title, text, confirmButton, dismissButton)
    } else {
        AppAlertDialog(onDismissRequest, modifier, title, text, confirmButton, dismissButton)
    }
}

@Composable
internal expect fun MiuixHostlessAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    title: (@Composable () -> Unit)?,
    text: (@Composable () -> Unit)?,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)?,
)

/** 优先测量操作区，避免长正文挤掉确认按钮。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppModalContent(
    title: (@Composable () -> Unit)?,
    text: (@Composable () -> Unit)?,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)?,
) {
    val tokens = LocalAppSkinTokens.current
    Layout(
        content = {
            if (title != null) {
                Box(Modifier.fillMaxWidth().layoutId("title")) {
                    CompositionLocalProvider(LocalContentColor provides tokens.onSurface) {
                        ProvideTextStyle(tokens.title2, content = title)
                    }
                }
            }
            if (text != null) {
                Column(
                    Modifier.fillMaxWidth().layoutId("body").verticalScroll(rememberScrollState()),
                ) {
                    CompositionLocalProvider(LocalContentColor provides tokens.onSurface) {
                        ProvideTextStyle(tokens.main, content = text)
                    }
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth().layoutId("actions"),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.DialogActionSpacing, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(AppDimens.DialogActionSpacing),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                dismissButton?.invoke()
                confirmButton()
            }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val actions = measurables.first { it.layoutId == "actions" }.measure(loose)
        val titleMeasurable = measurables.firstOrNull { it.layoutId == "title" }
        val bodyMeasurable = measurables.firstOrNull { it.layoutId == "body" }
        val sectionGap = AppDimens.DialogSectionSpacing.roundToPx()
        val titleGap = if (titleMeasurable != null) sectionGap else 0
        val bodyGap = if (bodyMeasurable != null) sectionGap else 0
        val titlePlaceable = titleMeasurable?.measure(
            loose.copy(maxHeight = (constraints.maxHeight - actions.height - titleGap - bodyGap).coerceAtLeast(0)),
        )
        val body = bodyMeasurable?.measure(
            loose.copy(maxHeight = (constraints.maxHeight - actions.height - (titlePlaceable?.height ?: 0) - titleGap - bodyGap).coerceAtLeast(0)),
        )
        val height = (titlePlaceable?.height ?: 0) + (body?.height ?: 0) + actions.height + titleGap + bodyGap
        layout(constraints.maxWidth, constraints.constrainHeight(height)) {
            var y = 0
            titlePlaceable?.let {
                it.placeRelative(0, y)
                y += it.height + titleGap
            }
            body?.let {
                it.placeRelative(0, y)
                y += it.height + bodyGap
            }
            actions.placeRelative(0, y)
        }
    }
}
