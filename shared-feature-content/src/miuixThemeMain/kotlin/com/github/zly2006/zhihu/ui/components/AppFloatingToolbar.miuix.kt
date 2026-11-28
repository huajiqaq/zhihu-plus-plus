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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.zly2006.zhihu.theme.LocalAppTheme
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal actual fun MiuixFloatingToolbar(
    modifier: Modifier,
    expanded: Boolean,
    trailingContent: (@Composable RowScope.() -> Unit)?,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    FloatingToolbar(
        modifier = modifier.appFloatingGlassSurface(
            shape = CircleShape,
            fallbackContainer = colors.surfaceContainer,
            tint = colors.surfaceContainer.copy(alpha = AppDimens.GlassContainerAlpha),
            borderColor = colors.dividerLine,
            isDark = LocalAppTheme.current.isDarkTheme,
        ),
        color = appGlassContainerColor(colors.surfaceContainer),
        outSidePadding = PaddingValues(AppDimens.ElevationNone),
        shadowElevation = AppDimens.ElevationNone,
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.onSurfaceContainer) {
            Row(
                modifier = Modifier.padding(AppDimens.Space4),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Space4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                content()
                if (trailingContent != null) {
                    AnimatedVisibility(expanded) {
                        Row(verticalAlignment = Alignment.CenterVertically, content = trailingContent)
                    }
                }
            }
        }
    }
}
