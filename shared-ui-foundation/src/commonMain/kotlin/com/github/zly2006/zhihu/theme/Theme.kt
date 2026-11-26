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
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.material3.rememberDynamicColorScheme

/** 固定换肤组合根；不提供设置存储的独立渲染使用宿主显式装载的快照。 */
@Composable
fun ZhihuTheme(
    settings: SettingsStore? = rememberSettingsStore(),
    content: @Composable () -> Unit,
) {
    if (settings != null) {
        ThemeManager.ObserveSettings(settings)
    }
    val useDynamicColor = ThemeManager.getUseDynamicColor()
    val customBackgroundColor = ThemeManager.getBackgroundColor()
    val darkTheme = ThemeManager.isDarkTheme()
    val themeStyle = ThemeManager.getThemeStyle()
    val paletteStyle = ThemeManager.getPaletteStyle()
    val colorSpec = ThemeManager.getColorSpec()
    val amoledTheme = ThemeManager.getAmoledTheme()
    val seedColor = if (useDynamicColor) platformDynamicThemeSeedColor() ?: ThemeManager.getCustomColor() else ThemeManager.getCustomColor()
    val generatedColorScheme = rememberDynamicColorScheme(
        seedColor = seedColor,
        isDark = darkTheme,
        isAmoled = amoledTheme && darkTheme,
        style = paletteStyle.toMaterialKolor(),
        specVersion = paletteStyle.effectiveColorSpec(colorSpec).toMaterialKolor(),
    )
    val baseColorScheme = generatedColorScheme.withAmoledSurfaces(
        enabled = amoledTheme && darkTheme,
    )

    PlatformSystemBarEffect(darkTheme)

    ThemeSkinHost(
        darkTheme = darkTheme,
        useMiuixSkin = themeStyle == ThemeStyle.MIUIX,
        baseColorScheme = baseColorScheme,
        backgroundColor = if (amoledTheme && darkTheme) Color.Black else customBackgroundColor,
        amoledTheme = amoledTheme && darkTheme,
    ) { colorScheme, typography, skinTokens ->
        CompositionLocalProvider(
            LocalAppSkinTokens provides skinTokens,
            LocalMiuixStyle provides ThemeManager.getMiuixStyle(),
        ) {
            MaterialExpressiveTheme(
                colorScheme = colorScheme.withAmoledSurfaces(amoledTheme && darkTheme),
                typography = typography,
            ) {
                MiuixSkinIndication(enabled = themeStyle == ThemeStyle.MIUIX, content = content)
            }
        }
    }
}

private fun ThemePaletteStyle.toMaterialKolor(): PaletteStyle = when (this) {
    ThemePaletteStyle.TONAL_SPOT -> PaletteStyle.TonalSpot
    ThemePaletteStyle.NEUTRAL -> PaletteStyle.Neutral
    ThemePaletteStyle.VIBRANT -> PaletteStyle.Vibrant
    ThemePaletteStyle.EXPRESSIVE -> PaletteStyle.Expressive
    ThemePaletteStyle.RAINBOW -> PaletteStyle.Rainbow
    ThemePaletteStyle.FRUIT_SALAD -> PaletteStyle.FruitSalad
    ThemePaletteStyle.MONOCHROME -> PaletteStyle.Monochrome
    ThemePaletteStyle.FIDELITY -> PaletteStyle.Fidelity
    ThemePaletteStyle.CONTENT -> PaletteStyle.Content
}

private fun ThemeColorSpec.toMaterialKolor(): ColorSpec.SpecVersion = when (this) {
    ThemeColorSpec.SPEC_2021 -> ColorSpec.SpecVersion.SPEC_2021
    ThemeColorSpec.SPEC_2025 -> ColorSpec.SpecVersion.SPEC_2025
}

private fun ColorScheme.withAmoledSurfaces(enabled: Boolean): ColorScheme {
    if (!enabled) return this
    val black = Color.Black
    val low = Color(0xFF050505)
    val high = Color(0xFF0A0A0A)
    return copy(
        background = black,
        surface = black,
        surfaceDim = black,
        surfaceBright = high,
        surfaceContainerLowest = black,
        surfaceContainerLow = low,
        surfaceContainer = low,
        surfaceContainerHigh = high,
        surfaceContainerHighest = Color(0xFF101010),
    )
}

@Composable
expect fun currentSystemInDarkTheme(): Boolean

@Composable
expect fun platformDynamicThemeSeedColor(): Color?

@Composable
expect fun PlatformSystemBarEffect(darkTheme: Boolean)
