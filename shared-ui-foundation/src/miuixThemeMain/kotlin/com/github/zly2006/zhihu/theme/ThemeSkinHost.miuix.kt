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

import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.utils.MiuixIndication

actual val miuixThemeAvailable: Boolean = true

@Composable
internal actual fun MiuixSkinIndication(enabled: Boolean, content: @Composable () -> Unit) {
    val color = MiuixTheme.colorScheme.onBackground
    val miuixIndication = remember(color) { MiuixIndication(color = color) }
    val indication = if (enabled) miuixIndication else LocalIndication.current
    /** 只替换 Indication 值，不能移动 content 的组合位置。 */
    CompositionLocalProvider(LocalIndication provides indication, content = content)
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
    val controller = remember(darkTheme) {
        ThemeController(
            colorSchemeMode = if (darkTheme) ColorSchemeMode.Dark else ColorSchemeMode.Light,
            isDark = darkTheme,
        )
    }
    MiuixThemeHost(controller, baseColorScheme, amoledTheme) {
        val textStyles = MiuixTheme.textStyles
        val typography = if (useMiuixSkin) {
            remember(textStyles) {
                Typography(
                    displayLarge = textStyles.title1,
                    displayMedium = textStyles.title1,
                    displaySmall = textStyles.title1,
                    headlineLarge = textStyles.title1,
                    headlineMedium = textStyles.title2,
                    headlineSmall = textStyles.title3,
                    titleLarge = textStyles.title3,
                    titleMedium = textStyles.title4,
                    titleSmall = textStyles.headline1,
                    bodyLarge = textStyles.main,
                    bodyMedium = textStyles.body2,
                    bodySmall = textStyles.footnote1,
                    labelLarge = textStyles.button,
                    labelMedium = textStyles.footnote1,
                    labelSmall = textStyles.footnote2,
                )
            }
        } else {
            remember { Typography() }
        }
        val colors = zhihuColorScheme(baseColorScheme, if (useMiuixSkin) MiuixTheme.colorScheme.surface else backgroundColor, useMiuixSkin)
        val tokens = if (useMiuixSkin) {
            AppSkinTokens(
                primary = MiuixTheme.colorScheme.primary,
                surfaceContainerHigh = MiuixTheme.colorScheme.surfaceContainerHigh,
                surfaceContainer = MiuixTheme.colorScheme.surfaceContainer,
                onSurface = MiuixTheme.colorScheme.onSurface,
                onPrimary = MiuixTheme.colorScheme.onPrimary,
                background = colors.background,
                surfaceVariant = MiuixTheme.colorScheme.surfaceVariant,
                onSurfaceVariantActions = MiuixTheme.colorScheme.onSurfaceVariantActions,
                surface = MiuixTheme.colorScheme.surface,
                onSurfaceVariantSummary = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                error = MiuixTheme.colorScheme.error,
                secondaryContainer = MiuixTheme.colorScheme.secondaryContainer,
                secondaryVariant = MiuixTheme.colorScheme.secondaryVariant,
                onSecondaryVariant = MiuixTheme.colorScheme.onSecondaryVariant,
                disabledOnSurface = MiuixTheme.colorScheme.disabledOnSurface,
                disabledOnSecondaryVariant = MiuixTheme.colorScheme.disabledOnSecondaryVariant,
                outline = MiuixTheme.colorScheme.outline,
                footnote1 = MiuixTheme.textStyles.footnote1,
                footnote2 = MiuixTheme.textStyles.footnote2,
                title4 = MiuixTheme.textStyles.title4,
                main = MiuixTheme.textStyles.main,
                headline2 = MiuixTheme.textStyles.headline2,
                title2 = MiuixTheme.textStyles.title2,
                title3 = MiuixTheme.textStyles.title3,
                body2 = MiuixTheme.textStyles.body2,
            )
        } else {
            AppSkinTokens.material(colors, typography)
        }
        content(colors, typography, tokens)
    }
}
