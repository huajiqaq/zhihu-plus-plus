/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 */

package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.miuixThemeAvailable

@Composable
fun settingSkinIsMiuix(): Boolean =
    miuixThemeAvailable && ThemeManager.getThemeStyle() == ThemeStyle.MIUIX

@Composable
internal expect fun MiuixSettingSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier,
    enabled: Boolean,
)

internal expect object MiuixSettingsRenderer {
    @Composable
    fun group(
        modifier: Modifier,
        title: String?,
        header: (@Composable () -> Unit)?,
        footer: (@Composable () -> Unit)?,
        content: @Composable () -> Unit,
    )

    @Composable
    fun row(
        modifier: Modifier,
        title: @Composable () -> Unit,
        description: (@Composable () -> Unit)?,
        icon: (@Composable () -> Unit)?,
        onClick: (() -> Unit)?,
        endAction: (@Composable () -> Unit)?,
        bottomAction: (@Composable () -> Unit)?,
        rowModifier: Modifier,
        enabled: Boolean,
        iconShape: Shape,
    )

    @Composable
    fun select(
        modifier: Modifier,
        title: @Composable () -> Unit,
        description: (@Composable () -> Unit)?,
        icon: (@Composable () -> Unit)?,
        options: List<String>,
        selectedIndex: Int,
        summaries: List<String?>?,
        enabled: Boolean,
        fieldModifier: Modifier,
        settingKey: String?,
        highlightedKey: String,
        onPositioned: ((Int) -> Unit)?,
        bringIntoViewRequester: BringIntoViewRequester?,
        expanded: Boolean,
        onExpandedChange: (Boolean) -> Unit,
        onSelect: (Int) -> Unit,
    )
}
