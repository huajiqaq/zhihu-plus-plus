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
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.LocalContentColor as MiuixContentColor

/** 保持 Miuix 宿主结构稳定，切肤不重建子树。 */
@Composable
fun MiuixThemeHost(
    controller: ThemeController,
    baseColorScheme: ColorScheme,
    amoledTheme: Boolean,
    content: @Composable () -> Unit,
) {
    MiuixTheme(controller = controller) {
        val originalColors = MiuixTheme.colorScheme
        /** AMOLED 只覆盖中性表面，Miuix 彩色角色不继承 Material 种子色。 */
        val colors = originalColors.copy(
            background = if (amoledTheme) Color.Black else originalColors.background,
            surface = if (amoledTheme) baseColorScheme.surface else originalColors.surface,
            surfaceContainer = if (amoledTheme) baseColorScheme.surfaceContainer else originalColors.surfaceContainer,
            surfaceContainerHigh = if (amoledTheme) baseColorScheme.surfaceContainerHigh else originalColors.surfaceContainerHigh,
            surfaceContainerHighest = if (amoledTheme) baseColorScheme.surfaceContainerHighest else originalColors.surfaceContainerHighest,
            surfaceVariant = if (amoledTheme) baseColorScheme.surfaceContainer else originalColors.surfaceVariant,
            secondaryContainer = if (amoledTheme) baseColorScheme.surfaceContainerHigh else originalColors.secondaryContainer,
        )
        MiuixTheme(colors = colors) {
            /** Material Text/Icon 不读取 Miuix 内容色，须在容器作用域桥接。 */
            CompositionLocalProvider(LocalContentColor provides MiuixContentColor.current) {
                content()
            }
        }
    }
}

/** 为仍使用 Material 的消费者投影 Miuix 颜色角色。 */
@Composable
fun zhihuColorScheme(
    baseColorScheme: ColorScheme,
    backgroundColor: Color,
    useMiuixColors: Boolean,
): ColorScheme {
    val base = baseColorScheme.copy(
        background = backgroundColor,
        onBackground = readableContainerContentColor(backgroundColor, baseColorScheme.onBackground),
        surface = backgroundColor,
        onSurface = readableContainerContentColor(backgroundColor, baseColorScheme.onSurface),
    )
    if (!useMiuixColors) {
        return base
    }

    val miuix = MiuixTheme.colorScheme
    return base.copy(
        background = backgroundColor,
        onBackground = miuix.onBackground,
        surface = miuix.surface,
        onSurface = miuix.onSurface,
        surfaceVariant = miuix.surfaceVariant,
        onSurfaceVariant = miuix.onSurface.copy(alpha = 0.7f),
        surfaceContainer = miuix.surfaceContainer,
        surfaceContainerHigh = miuix.surfaceContainerHigh,
        surfaceContainerHighest = miuix.surfaceContainerHighest,
        /** Miuix 缺少 Low/Lowest/Bright/Dim，用中性表面避免混入壁纸色。 */
        surfaceContainerLow = miuix.surfaceContainer,
        surfaceContainerLowest = miuix.surface,
        surfaceBright = miuix.surface,
        surfaceDim = miuix.background,
        outlineVariant = miuix.outline,
        primary = miuix.primary,
        onPrimary = miuix.onPrimary,
        primaryContainer = miuix.primaryContainer,
        onPrimaryContainer = miuix.onPrimaryContainer,
        /** 原生 secondary 文字对比不足，使用达标的 secondaryVariant 色对。 */
        secondary = miuix.secondaryVariant,
        onSecondary = miuix.onSecondaryVariant,
        secondaryContainer = miuix.secondaryVariant,
        onSecondaryContainer = miuix.onSecondaryVariant,
        error = miuix.error,
        onError = miuix.onError,
        errorContainer = miuix.errorContainer,
        onErrorContainer = miuix.onErrorContainer,
        tertiaryContainer = miuix.tertiaryContainer,
        onTertiaryContainer = miuix.onTertiaryContainer,
        outline = miuix.outline,
    )
}
