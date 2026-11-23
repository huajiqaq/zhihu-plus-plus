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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle

data class AppSkinTokens(
    val primary: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainer: Color,
    val onSurface: Color,
    val onPrimary: Color,
    val background: Color,
    val surfaceVariant: Color,
    val onSurfaceVariantActions: Color,
    val surface: Color,
    val onSurfaceVariantSummary: Color,
    val error: Color,
    val secondaryContainer: Color,
    val secondaryVariant: Color,
    val onSecondaryVariant: Color,
    val disabledOnSurface: Color,
    val disabledOnSecondaryVariant: Color,
    val outline: Color,
    val footnote1: TextStyle,
    val footnote2: TextStyle,
    val title4: TextStyle,
    val main: TextStyle,
    val headline2: TextStyle,
    val title2: TextStyle,
    val title3: TextStyle,
    val body2: TextStyle,
) {
    companion object {
        fun material(colors: ColorScheme, typography: Typography): AppSkinTokens = AppSkinTokens(
            primary = colors.primary,
            surfaceContainerHigh = colors.surfaceContainerHigh,
            surfaceContainer = colors.surfaceContainer,
            onSurface = colors.onSurface,
            onPrimary = colors.onPrimary,
            background = colors.background,
            surfaceVariant = colors.surfaceVariant,
            onSurfaceVariantActions = colors.onSurfaceVariant,
            surface = colors.surface,
            onSurfaceVariantSummary = colors.onSurfaceVariant,
            error = colors.error,
            secondaryContainer = colors.secondaryContainer,
            secondaryVariant = colors.secondaryContainer,
            onSecondaryVariant = colors.onSecondaryContainer,
            disabledOnSurface = colors.onSurface.copy(alpha = 0.38f),
            disabledOnSecondaryVariant = colors.onSecondaryContainer.copy(alpha = 0.38f),
            outline = colors.outline,
            footnote1 = typography.labelLarge,
            footnote2 = typography.bodySmall,
            title4 = typography.titleMedium,
            main = typography.bodyLarge,
            headline2 = typography.headlineMedium,
            title2 = typography.titleLarge,
            title3 = typography.titleMedium,
            body2 = typography.bodyMedium,
        )
    }
}

/** 自定义容器色仍须保证 WCAG AA 文字对比度。 */
internal fun readableContainerContentColor(container: Color, preferred: Color): Color {
    val background = container.copy(alpha = 1f)
    val backgroundLuminance = background.luminance()
    val foregroundLuminance = preferred.compositeOver(background).luminance()
    val contrast = (maxOf(backgroundLuminance, foregroundLuminance) + 0.05f) /
        (minOf(backgroundLuminance, foregroundLuminance) + 0.05f)
    if (contrast >= 4.5f) return preferred
    return if ((1.05f / (backgroundLuminance + 0.05f)) >= ((backgroundLuminance + 0.05f) / 0.05f)) {
        Color.White
    } else {
        Color.Black
    }
}

val LocalAppSkinTokens = staticCompositionLocalOf<AppSkinTokens> {
    error("LocalAppSkinTokens is not provided")
}
