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

package com.github.zly2006.zhihu.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

actual val miuixThemeAvailable: Boolean = false

@Composable
internal actual fun MiuixSkinIndication(enabled: Boolean, content: @Composable () -> Unit) {
    content()
}

@Composable
internal actual fun ThemeSkinHost(
    darkTheme: Boolean,
    useMiuixSkin: Boolean,
    baseColorScheme: ColorScheme,
    backgroundColor: Color,
    amoledTheme: Boolean,
    content: @Composable (ColorScheme, Typography, AppSkinTokens) -> Unit,
) {
    val colors = baseColorScheme.copy(
        background = backgroundColor,
        onBackground = readableContainerContentColor(backgroundColor, baseColorScheme.onBackground),
        surface = backgroundColor,
        onSurface = readableContainerContentColor(backgroundColor, baseColorScheme.onSurface),
    )
    val typography = remember { Typography() }
    content(colors, typography, AppSkinTokens.material(colors, typography))
}
