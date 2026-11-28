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
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
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

/** 存储快照保留用户请求的皮肤；Lite 的有效皮肤只在渲染根解析。 */
fun readThemeSnapshot(settings: SettingsStore): ThemeSnapshot = ThemeSnapshot(
    useDynamicColor = settings.getBoolean(USE_DYNAMIC_COLOR_PREFERENCE_KEY, true),
    customColor = settings.getInt(CUSTOM_THEME_COLOR_PREFERENCE_KEY, 0xFF2196F3.toInt()),
    backgroundColorLight = settings.getInt(BACKGROUND_COLOR_LIGHT_PREFERENCE_KEY, DEFAULT_BACKGROUND_COLOR_LIGHT),
    backgroundColorDark = settings.getInt(BACKGROUND_COLOR_DARK_PREFERENCE_KEY, DEFAULT_BACKGROUND_COLOR_DARK),
    themeMode = ThemeMode.entries.firstOrNull { it.name == settings.getStringOrNull(THEME_MODE_PREFERENCE_KEY) } ?: ThemeMode.SYSTEM,
    themeStyle = ThemeStyle.fromName(settings.getStringOrNull(THEME_STYLE_PREFERENCE_KEY)),
    paletteStyle = ThemePaletteStyle.fromName(settings.getStringOrNull(THEME_PALETTE_STYLE_PREFERENCE_KEY)),
    colorSpec = ThemeColorSpec.fromName(settings.getStringOrNull(THEME_COLOR_SPEC_PREFERENCE_KEY)),
    amoledTheme = settings.getBoolean(AMOLED_THEME_PREFERENCE_KEY, false),
    miuixStyle = MiuixStyle.fromName(settings.getStringOrNull(MIUIX_STYLE_PREFERENCE_KEY)),
)

/** 每个主题根观察自己的设置；注册后立即重读，覆盖首次组合期间的变动。 */
@Composable
fun rememberThemeSnapshot(settings: SettingsStore): State<ThemeSnapshot> {
    val snapshot = remember(settings) { mutableStateOf(readThemeSnapshot(settings)) }
    DisposableEffect(settings) {
        val subscription = settings.observeKeyChanges { key ->
            if (key in themePreferenceKeys) snapshot.value = readThemeSnapshot(settings)
        }
        snapshot.value = readThemeSnapshot(settings)
        onDispose(subscription::close)
    }
    return snapshot
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
) {
    fun resolve(systemDark: Boolean, styleOverride: ThemeStyle? = null): AppTheme = AppTheme(
        snapshot = this,
        isDarkTheme = when (themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> systemDark
        },
        style = (styleOverride ?: themeStyle).takeIf { it != ThemeStyle.MIUIX || miuixThemeAvailable } ?: ThemeStyle.MATERIAL_EXPRESSIVE,
    )
}

/** 当前根的只读偏好及解析结果；不回写设置，也不缓存上次组合的系统暗色。 */
data class AppTheme(
    val snapshot: ThemeSnapshot,
    val isDarkTheme: Boolean,
    val style: ThemeStyle,
) {
    val customColor: Color get() = Color(snapshot.customColor)
    val backgroundColor: Color
        get() = Color((if (isDarkTheme) snapshot.backgroundColorDark else snapshot.backgroundColorLight) or 0xFF000000.toInt())
}

val LocalAppTheme = staticCompositionLocalOf<AppTheme> { error("ZhihuTheme is not provided") }

/** 调试换肤只覆盖所在主题根；null 恢复持久偏好。 */
val LocalAppThemeOverride = staticCompositionLocalOf<(ThemeStyle?) -> Unit> { error("ZhihuTheme is not provided") }

fun resetPaletteAndBackground(settings: SettingsStore) {
    settings.remove(THEME_PALETTE_STYLE_PREFERENCE_KEY)
    settings.remove(THEME_COLOR_SPEC_PREFERENCE_KEY)
    settings.remove(BACKGROUND_COLOR_LIGHT_PREFERENCE_KEY)
    settings.remove(BACKGROUND_COLOR_DARK_PREFERENCE_KEY)
}
