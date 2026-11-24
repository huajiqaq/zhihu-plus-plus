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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.github.zly2006.zhihu.platform.SettingsStore

private const val THEME_MODE_PREFERENCE_KEY = "themeMode"
private const val USE_DYNAMIC_COLOR_PREFERENCE_KEY = "useDynamicColor"
private const val CUSTOM_THEME_COLOR_PREFERENCE_KEY = "customThemeColor"
private const val BACKGROUND_COLOR_LIGHT_PREFERENCE_KEY = "backgroundColorLight"
private const val BACKGROUND_COLOR_DARK_PREFERENCE_KEY = "backgroundColorDark"
const val DEFAULT_BACKGROUND_COLOR_LIGHT = 0xFFFFFFFF.toInt()
const val DEFAULT_BACKGROUND_COLOR_DARK = 0xFF121212.toInt()

private val themePreferenceKeys = setOf(
    THEME_MODE_PREFERENCE_KEY,
    USE_DYNAMIC_COLOR_PREFERENCE_KEY,
    CUSTOM_THEME_COLOR_PREFERENCE_KEY,
    BACKGROUND_COLOR_LIGHT_PREFERENCE_KEY,
    BACKGROUND_COLOR_DARK_PREFERENCE_KEY,
    THEME_STYLE_PREFERENCE_KEY,
    THEME_PALETTE_STYLE_PREFERENCE_KEY,
    THEME_COLOR_SPEC_PREFERENCE_KEY,
    AMOLED_THEME_PREFERENCE_KEY,
    MIUIX_STYLE_PREFERENCE_KEY,
)

fun readThemeSnapshot(settings: SettingsStore): ThemeSnapshot {
    val themeMode = runCatching {
        ThemeMode.valueOf(settings.themeString(THEME_MODE_PREFERENCE_KEY) ?: ThemeMode.SYSTEM.name)
    }.getOrElse { ThemeMode.SYSTEM }
    val requestedStyle = ThemeStyle.fromName(settings.themeString(THEME_STYLE_PREFERENCE_KEY))
    val effectiveStyle = requestedStyle.takeIf { it != ThemeStyle.MIUIX || miuixThemeAvailable }
        ?: ThemeStyle.MATERIAL_EXPRESSIVE
    return ThemeSnapshot(
        useDynamicColor = settings.themeBoolean(USE_DYNAMIC_COLOR_PREFERENCE_KEY, true),
        customColor = settings.themeInt(CUSTOM_THEME_COLOR_PREFERENCE_KEY, 0xFF2196F3.toInt()),
        backgroundColorLight = settings.themeInt(BACKGROUND_COLOR_LIGHT_PREFERENCE_KEY, DEFAULT_BACKGROUND_COLOR_LIGHT) or 0xFF000000.toInt(),
        backgroundColorDark = settings.themeInt(BACKGROUND_COLOR_DARK_PREFERENCE_KEY, DEFAULT_BACKGROUND_COLOR_DARK) or 0xFF000000.toInt(),
        themeMode = themeMode,
        themeStyle = effectiveStyle,
        paletteStyle = ThemePaletteStyle.fromName(settings.themeString(THEME_PALETTE_STYLE_PREFERENCE_KEY)),
        colorSpec = ThemeColorSpec.fromName(settings.themeString(THEME_COLOR_SPEC_PREFERENCE_KEY)),
        amoledTheme = settings.themeBoolean(AMOLED_THEME_PREFERENCE_KEY, false),
        miuixStyle = MiuixStyle.fromName(settings.themeString(MIUIX_STYLE_PREFERENCE_KEY)),
    )
}

/** 旧桌面备份迁到 Android 后，布尔和整数偏好可能仍用字符串存储。 */
private fun SettingsStore.themeBoolean(key: String, defaultValue: Boolean): Boolean = try {
    getBoolean(key, defaultValue)
} catch (_: ClassCastException) {
    themeString(key)?.toBooleanStrictOrNull() ?: defaultValue
}

private fun SettingsStore.themeInt(key: String, defaultValue: Int): Int = try {
    getInt(key, defaultValue)
} catch (_: ClassCastException) {
    themeString(key)?.toIntOrNull() ?: defaultValue
}

private fun SettingsStore.themeString(key: String): String? = try {
    getStringOrNull(key)
} catch (_: ClassCastException) {
    null
}

data class ThemeSnapshot(
    val useDynamicColor: Boolean = true,
    val customColor: Int = 0xFF2196F3.toInt(),
    val backgroundColorLight: Int = DEFAULT_BACKGROUND_COLOR_LIGHT,
    val backgroundColorDark: Int = DEFAULT_BACKGROUND_COLOR_DARK,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val themeStyle: ThemeStyle = ThemeStyle.Default,
    val paletteStyle: ThemePaletteStyle = ThemePaletteStyle.Default,
    val colorSpec: ThemeColorSpec = ThemeColorSpec.Default,
    val amoledTheme: Boolean = false,
    val miuixStyle: MiuixStyle = MiuixStyle.Default,
)

object ThemeManager {
    private var theme by mutableStateOf(ThemeSnapshot())

    /** 首帧使用已保存的皮肤；注册后重读，覆盖首次组合期间的设置变动。 */
    @Composable
    fun ObserveSettings(settings: SettingsStore) {
        val initial = remember(settings) { readThemeSnapshot(settings).also { theme = it } }
        DisposableEffect(settings) {
            val subscription = settings.observeKeyChanges { key ->
                if (key in themePreferenceKeys) theme = readThemeSnapshot(settings)
            }
            val latest = readThemeSnapshot(settings)
            if (latest != initial) theme = latest
            onDispose(subscription::close)
        }
    }

    @Composable
    fun getThemeStyle(): ThemeStyle = theme.themeStyle

    @Composable
    fun getMiuixStyle(): MiuixStyle = theme.miuixStyle

    val currentStyle: ThemeStyle
        get() = theme.themeStyle

    @Composable
    fun getUseDynamicColor(): Boolean = theme.useDynamicColor

    @Composable
    fun getCustomColor(): Color = Color(theme.customColor)

    @Composable
    fun getBackgroundColor(): Color = Color(if (isDarkTheme()) theme.backgroundColorDark else theme.backgroundColorLight)

    @Composable
    fun getThemeMode(): ThemeMode = theme.themeMode

    @Composable
    fun getPaletteStyle(): ThemePaletteStyle = theme.paletteStyle

    @Composable
    fun getColorSpec(): ThemeColorSpec = theme.colorSpec

    @Composable
    fun getAmoledTheme(): Boolean = theme.amoledTheme

    var isDarkTheme: Boolean = false
        private set

    @Composable
    fun isDarkTheme(): Boolean {
        val systemDarkTheme = currentSystemInDarkTheme()
        val isDark = when (theme.themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> systemDarkTheme
        }
        isDarkTheme = isDark
        return isDark
    }

    fun load(snapshot: ThemeSnapshot) {
        /** 页面底色必须不透明，浮层材质自行控制透明度。 */
        theme = snapshot.copy(
            backgroundColorLight = snapshot.backgroundColorLight or 0xFF000000.toInt(),
            backgroundColorDark = snapshot.backgroundColorDark or 0xFF000000.toInt(),
            themeStyle = snapshot.themeStyle.takeIf { it != ThemeStyle.MIUIX || miuixThemeAvailable } ?: ThemeStyle.MATERIAL_EXPRESSIVE,
        )
    }

    fun snapshot(): ThemeSnapshot = theme

    fun setBackgroundColor(color: Color, isDark: Boolean) {
        val colorInt = color.copy(alpha = 1f).toArgb()
        theme = if (isDark) {
            theme.copy(backgroundColorDark = colorInt)
        } else {
            theme.copy(backgroundColorLight = colorInt)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        theme = theme.copy(themeMode = mode)
    }

    fun setThemeStyle(style: ThemeStyle) {
        load(theme.copy(themeStyle = style))
    }

    fun resetPaletteAndBackground(settings: SettingsStore) {
        settings.remove(THEME_PALETTE_STYLE_PREFERENCE_KEY)
        settings.remove(THEME_COLOR_SPEC_PREFERENCE_KEY)
        settings.remove(BACKGROUND_COLOR_LIGHT_PREFERENCE_KEY)
        settings.remove(BACKGROUND_COLOR_DARK_PREFERENCE_KEY)
    }

    fun setAmoledTheme(enabled: Boolean) {
        theme = theme.copy(amoledTheme = enabled)
    }
}
