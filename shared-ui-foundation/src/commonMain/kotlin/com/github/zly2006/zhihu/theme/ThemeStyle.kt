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

import androidx.compose.runtime.compositionLocalOf
import com.github.zly2006.zhihu.i18n.ThemeStrings

/** 皮肤只影响呈现，不承载业务状态。 */
enum class ThemeStyle(
    val displayName: String,
) {
    MATERIAL_EXPRESSIVE("Material 3 Expressive"),
    MIUIX("Miuix"),
    ;

    companion object {
        val Default: ThemeStyle = MATERIAL_EXPRESSIVE

        fun fromName(name: String?): ThemeStyle = entries.firstOrNull { it.name == name } ?: Default
    }
}

const val THEME_STYLE_PREFERENCE_KEY: String = "uiThemeStyle"

/** Miuix 子风格独立于明暗、配色和玻璃偏好。 */
enum class MiuixStyle(
    val displayName: String,
) {
    HYPEROS_3("HyperOS 3"),
    HYPEROS_4("HyperOS 4"),
    ;

    companion object {
        val Default: MiuixStyle = HYPEROS_3

        fun fromName(name: String?): MiuixStyle = entries.firstOrNull { it.name == name } ?: Default
    }
}

const val MIUIX_STYLE_PREFERENCE_KEY: String = "miuixStyle"
val LocalMiuixStyle = compositionLocalOf { MiuixStyle.Default }

/** 仅用于 Material 动态配色，不替换 Miuix 原生色表。 */
enum class ThemePaletteStyle(
    private val paletteName: () -> String,
) {
    TONAL_SPOT(ThemeStrings::paletteTonalSpot),
    NEUTRAL(ThemeStrings::paletteNeutral),
    VIBRANT(ThemeStrings::paletteVibrant),
    EXPRESSIVE(ThemeStrings::paletteExpressive),
    RAINBOW(ThemeStrings::paletteRainbow),
    FRUIT_SALAD(ThemeStrings::paletteFruitSalad),
    MONOCHROME(ThemeStrings::paletteMonochrome),
    FIDELITY(ThemeStrings::paletteFidelity),
    CONTENT(ThemeStrings::paletteContent),
    ;

    val displayName: String get() = paletteName()

    val supportedColorSpecs: List<ThemeColorSpec>
        get() = when (this) {
            TONAL_SPOT, NEUTRAL, VIBRANT, EXPRESSIVE -> ThemeColorSpec.entries
            else -> listOf(ThemeColorSpec.SPEC_2021)
        }

    fun effectiveColorSpec(preferred: ThemeColorSpec): ThemeColorSpec =
        preferred.takeIf { it in supportedColorSpecs } ?: supportedColorSpecs.last()

    companion object {
        val Default: ThemePaletteStyle = EXPRESSIVE

        fun fromName(name: String?): ThemePaletteStyle = entries.firstOrNull { it.name == name } ?: Default
    }
}

enum class ThemeColorSpec(
    val displayName: String,
) {
    SPEC_2021("2021"),
    SPEC_2025("2025"),
    ;

    companion object {
        val Default: ThemeColorSpec = SPEC_2025

        fun fromName(name: String?): ThemeColorSpec = entries.firstOrNull { it.name == name } ?: Default
    }
}

const val THEME_PALETTE_STYLE_PREFERENCE_KEY: String = "themePaletteStyle"
const val THEME_COLOR_SPEC_PREFERENCE_KEY: String = "themeColorSpec"
const val AMOLED_THEME_PREFERENCE_KEY: String = "amoledTheme"

const val NEW_UI_NOTICE_PREFERENCE_KEY: String = "newUiNoticeShown"
