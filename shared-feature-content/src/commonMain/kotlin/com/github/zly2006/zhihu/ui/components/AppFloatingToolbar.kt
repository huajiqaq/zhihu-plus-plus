/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.zly2006.zhihu.theme.LocalAppTheme

/** 同一组操作使用当前皮肤的原生浮动工具栏，业务状态和动作由页面持有。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppFloatingToolbar(
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (settingSkinIsMiuix()) {
        MiuixFloatingToolbar(modifier, expanded, trailingContent, content)
        return
    }
    val colors = FloatingToolbarDefaults.standardFloatingToolbarColors()
    HorizontalFloatingToolbar(
        expanded = expanded,
        modifier = modifier.appFloatingGlassSurface(
            shape = FloatingToolbarDefaults.ContainerShape,
            fallbackContainer = colors.toolbarContainerColor,
            tint = colors.toolbarContainerColor.copy(alpha = AppDimens.GlassContainerAlpha),
            borderColor = colors.toolbarContentColor.copy(alpha = 0.12f),
            isDark = LocalAppTheme.current.isDarkTheme,
        ),
        colors = colors.copy(toolbarContainerColor = appGlassContainerColor(colors.toolbarContainerColor)),
        expandedShadowElevation = AppDimens.ElevationNone,
        collapsedShadowElevation = AppDimens.ElevationNone,
        trailingContent = trailingContent,
        content = content,
    )
}

@Composable
internal expect fun MiuixFloatingToolbar(
    modifier: Modifier,
    expanded: Boolean,
    trailingContent: (@Composable RowScope.() -> Unit)?,
    content: @Composable RowScope.() -> Unit,
)
