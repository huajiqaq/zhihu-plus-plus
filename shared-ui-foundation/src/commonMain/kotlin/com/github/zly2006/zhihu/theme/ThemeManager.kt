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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.github.zly2006.zhihu.platform.SettingsStore

private const val THEME_MODE_PREFERENCE_KEY = "themeMode"
private const val USE_DYNAMIC_COLOR_PREFERENCE_KEY = "useDynamicColor"
private const val CUSTOM_THEME_COLOR_PREFERENCE_KEY = "customThemeColor"
private const val BACKGROUND_COLOR_LIGHT_PREFERENCE_KEY = "backgroundColorLight"
private const val BACKGROUND_COLOR_DARK_PREFERENCE_KEY = "backgroundColorDark"

fun readThemeSnapshot(settings: SettingsStore): ThemeSnapshot {
    val themeMode = runCatching {
        ThemeMode.valueOf(settings.getString(THEME_MODE_PREFERENCE_KEY, ThemeMode.SYSTEM.name))
    }.getOrElse { ThemeMode.SYSTEM }
    val requestedStyle = ThemeStyle.fromName(
        settings.getStringOrNull(THEME_STYLE_PREFERENCE_KEY) ?: ThemeStyle.Default.name,
    )
    val effectiveStyle = requestedStyle.takeIf { it != ThemeStyle.MIUIX || miuixThemeAvailable }
        ?: ThemeStyle.MATERIAL_EXPRESSIVE
    return ThemeSnapshot(
        useDynamicColor = settings.getBoolean(USE_DYNAMIC_COLOR_PREFERENCE_KEY, true),
        customColor = settings.getInt(CUSTOM_THEME_COLOR_PREFERENCE_KEY, 0xFF2196F3.toInt()),
        backgroundColorLight = settings.getInt(BACKGROUND_COLOR_LIGHT_PREFERENCE_KEY, 0xFFFFFFFF.toInt()) or 0xFF000000.toInt(),
        backgroundColorDark = settings.getInt(BACKGROUND_COLOR_DARK_PREFERENCE_KEY, 0xFF121212.toInt()) or 0xFF000000.toInt(),
        themeMode = themeMode,
        themeStyle = effectiveStyle,
        paletteStyle = ThemePaletteStyle.fromName(
            settings.getStringOrNull(THEME_PALETTE_STYLE_PREFERENCE_KEY),
        ),
        colorSpec = ThemeColorSpec.fromName(
            settings.getStringOrNull(THEME_COLOR_SPEC_PREFERENCE_KEY),
        ),
        amoledTheme = settings.getBoolean(AMOLED_THEME_PREFERENCE_KEY, false),
    )
}

data class ThemeSnapshot(
    val useDynamicColor: Boolean = true,
    val customColor: Int = 0xFF2196F3.toInt(),
    val backgroundColorLight: Int = 0xFFFFFFFF.toInt(),
    val backgroundColorDark: Int = 0xFF121212.toInt(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val themeStyle: ThemeStyle = ThemeStyle.Default,
    val paletteStyle: ThemePaletteStyle = ThemePaletteStyle.Default,
    val colorSpec: ThemeColorSpec = ThemeColorSpec.Default,
    val amoledTheme: Boolean = false,
)

object ThemeManager {
    private val useDynamicColor = mutableStateOf(true)
    private val customColorInt = mutableIntStateOf(0xFF2196F3.toInt())
    private val backgroundColorLight = mutableIntStateOf(0xFFFFFFFF.toInt())
    private val backgroundColorDark = mutableIntStateOf(0xFF121212.toInt())
    private val themeMode = mutableStateOf(ThemeMode.SYSTEM)
    private val themeStyle = mutableStateOf(ThemeStyle.Default)
    private val paletteStyle = mutableStateOf(ThemePaletteStyle.Default)
    private val colorSpec = mutableStateOf(ThemeColorSpec.Default)
    private val amoledTheme = mutableStateOf(false)
    private var loadedFromSettings = false

    /** 宿主已装载的主题不能被首次组合再次覆盖。 */
    fun loadFromSettings(settings: SettingsStore) {
        if (loadedFromSettings) return
        load(readThemeSnapshot(settings))
    }

    @Composable
    fun getThemeStyle(): ThemeStyle = themeStyle.value

    val currentStyle: ThemeStyle
        get() = themeStyle.value

    @Composable
    fun getUseDynamicColor(): Boolean = useDynamicColor.value

    @Composable
    fun getCustomColor(): Color = Color(customColorInt.intValue)

    @Composable
    fun getBackgroundColor(): Color = if (isDarkTheme()) {
        Color(backgroundColorDark.intValue)
    } else {
        Color(backgroundColorLight.intValue)
    }

    @Composable
    fun getThemeMode(): ThemeMode = themeMode.value

    @Composable
    fun getPaletteStyle(): ThemePaletteStyle = paletteStyle.value

    @Composable
    fun getColorSpec(): ThemeColorSpec = colorSpec.value

    @Composable
    fun getAmoledTheme(): Boolean = amoledTheme.value

    var isDarkTheme: Boolean = false
        private set

    @Composable
    fun isDarkTheme(): Boolean {
        val systemDarkTheme = currentSystemInDarkTheme()
        val isDark = when (themeMode.value) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> systemDarkTheme
        }
        isDarkTheme = isDark
        return isDark
    }

    fun load(snapshot: ThemeSnapshot) {
        loadedFromSettings = true
        useDynamicColor.value = snapshot.useDynamicColor
        customColorInt.intValue = snapshot.customColor
        /** 页面底色必须不透明，浮层材质自行控制透明度。 */
        backgroundColorLight.intValue = snapshot.backgroundColorLight or 0xFF000000.toInt()
        backgroundColorDark.intValue = snapshot.backgroundColorDark or 0xFF000000.toInt()
        themeMode.value = snapshot.themeMode
        themeStyle.value = snapshot.themeStyle.takeIf { it != ThemeStyle.MIUIX || miuixThemeAvailable }
            ?: ThemeStyle.MATERIAL_EXPRESSIVE
        paletteStyle.value = snapshot.paletteStyle
        colorSpec.value = snapshot.colorSpec
        amoledTheme.value = snapshot.amoledTheme
    }

    fun snapshot(): ThemeSnapshot = ThemeSnapshot(
        useDynamicColor = useDynamicColor.value,
        customColor = customColorInt.intValue,
        backgroundColorLight = backgroundColorLight.intValue,
        backgroundColorDark = backgroundColorDark.intValue,
        themeMode = themeMode.value,
        themeStyle = themeStyle.value,
        paletteStyle = paletteStyle.value,
        colorSpec = colorSpec.value,
        amoledTheme = amoledTheme.value,
    )

    fun setUseDynamicColor(useDynamic: Boolean) {
        useDynamicColor.value = useDynamic
    }

    fun setCustomColor(color: Color) {
        customColorInt.intValue = color.toArgb()
    }

    fun setBackgroundColor(color: Color, isDark: Boolean) {
        val colorInt = color.copy(alpha = 1f).toArgb()
        if (isDark) {
            backgroundColorDark.intValue = colorInt
        } else {
            backgroundColorLight.intValue = colorInt
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
    }

    fun setThemeStyle(style: ThemeStyle) {
        themeStyle.value = style.takeIf { it != ThemeStyle.MIUIX || miuixThemeAvailable }
            ?: ThemeStyle.MATERIAL_EXPRESSIVE
    }

    fun setPaletteStyle(style: ThemePaletteStyle) {
        paletteStyle.value = style
    }

    fun setColorSpec(spec: ThemeColorSpec) {
        colorSpec.value = spec
    }

    fun setAmoledTheme(enabled: Boolean) {
        amoledTheme.value = enabled
    }
}
