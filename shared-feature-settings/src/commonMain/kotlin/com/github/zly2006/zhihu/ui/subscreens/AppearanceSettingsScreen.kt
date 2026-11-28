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

package com.github.zly2006.zhihu.ui.subscreens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.github.zly2006.zhihu.i18n.APP_LANGUAGE_KEY
import com.github.zly2006.zhihu.i18n.SettingsStrings
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.theme.AMOLED_THEME_PREFERENCE_KEY
import com.github.zly2006.zhihu.theme.DEFAULT_BACKGROUND_COLOR_DARK
import com.github.zly2006.zhihu.theme.DEFAULT_BACKGROUND_COLOR_LIGHT
import com.github.zly2006.zhihu.theme.LocalAppTheme
import com.github.zly2006.zhihu.theme.MIUIX_STYLE_PREFERENCE_KEY
import com.github.zly2006.zhihu.theme.MiuixStyle
import com.github.zly2006.zhihu.theme.THEME_COLOR_SPEC_PREFERENCE_KEY
import com.github.zly2006.zhihu.theme.THEME_PALETTE_STYLE_PREFERENCE_KEY
import com.github.zly2006.zhihu.theme.THEME_STYLE_PREFERENCE_KEY
import com.github.zly2006.zhihu.theme.ThemeMode
import com.github.zly2006.zhihu.theme.ThemePaletteStyle
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.miuixThemeAvailable
import com.github.zly2006.zhihu.theme.resetPaletteAndBackground
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppIcon
import com.github.zly2006.zhihu.ui.components.AppSegmentedControl
import com.github.zly2006.zhihu.ui.components.AppSettingsPage
import com.github.zly2006.zhihu.ui.components.AppSlider
import com.github.zly2006.zhihu.ui.components.ColorPickerDialog
import com.github.zly2006.zhihu.ui.components.DISABLE_BOTTOM_SHEET_ROUNDED_CORNERS_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingItemGroup
import com.github.zly2006.zhihu.ui.components.SettingItemSelect
import com.github.zly2006.zhihu.ui.components.SettingItemWithSwitch
import com.github.zly2006.zhihu.ui.components.appGlassRenderingSupported
import com.github.zly2006.zhihu.ui.rememberSetting
import kotlin.math.roundToInt

const val APPEARANCE_SETTINGS_BACK_BUTTON_TAG = "appearance_settings_back_button"

const val APPEARANCE_SETTINGS_DISABLE_BOTTOM_SHEET_ROUNDED_CORNERS_TAG = "appearanceSettings.disableBottomSheetRoundedCorners"
const val PREF_VOLUME_KEY_PAGE_TURN = "volumeKeyPageTurn"
const val APPEARANCE_SETTINGS_SCROLL_TAG = "appearanceSettings.scroll"
const val APPEARANCE_SETTINGS_START_DESTINATION_TAG = "appearanceSettings.startDestination"
const val APPEARANCE_SETTINGS_ANSWER_DOUBLE_TAP_TAG = "appearanceSettings.answerDoubleTap"
const val APPEARANCE_SETTINGS_ANSWER_SWITCH_SENSITIVITY_TAG = "appearanceSettings.answerSwitchSensitivity"
const val APPEARANCE_SETTINGS_USE_WEBVIEW_TAG = "appearanceSettings.useWebView"
const val APPEARANCE_SETTINGS_TIQIAN_MARKDOWN_TAG = "appearanceSettings.tiqianMarkdown"
const val APPEARANCE_SETTINGS_WEBVIEW_FONT_TAG = "appearanceSettings.webViewFont"
const val APPEARANCE_SETTINGS_WEBVIEW_OPTIONS_TAG = "appearanceSettings.webViewOptions"
const val APPEARANCE_SETTINGS_BOTTOM_BAR_SECTION_KEY = "appearanceSettings.bottomBarSection"
const val APPEARANCE_SETTINGS_LANDSCAPE_LIST_DETAIL_TAG = "appearanceSettings.landscapeListDetail"
const val APPEARANCE_SETTINGS_MORE_COLORS_TAG = "appearanceSettings.moreColors"

/** 颜色行没有单一偏好键（背景色按深浅分别存储），故深链使用行键。 */
const val APPEARANCE_SETTINGS_CUSTOM_COLOR_KEY = "customColor"
const val APPEARANCE_SETTINGS_BACKGROUND_COLOR_KEY = "backgroundColor"
const val APPEARANCE_SETTINGS_BROWSER_COLOR_KEY = "browserToolbarColor"

internal val contentFontSizeLevels = (50..120 step 5) + (130..200 step 10)

private const val BROWSER_TOOLBAR_COLOR_PREFERENCE_KEY = "luotianyi_color"
private const val DEFAULT_BROWSER_TOOLBAR_COLOR = 0xFF66CCFF.toInt()

private val MORE_COLOR_OPTION_KEYS: Set<String> = setOf(
    THEME_PALETTE_STYLE_PREFERENCE_KEY,
    THEME_COLOR_SPEC_PREFERENCE_KEY,
    APPEARANCE_SETTINGS_BACKGROUND_COLOR_KEY,
    APPEARANCE_SETTINGS_BROWSER_COLOR_KEY,
)

/** 退出时只重新读取主壳偏好，不重建返回栈或已加载页面。 */
@Composable
fun AppearanceSettingsScreen(
    setting: String?,
    onExit: () -> Unit,
) {
    val requestedKey = setting.orEmpty()
    val settings = rememberSettingsStore()
    val userMessages = rememberUserMessageSink()

    val useDynamicColor = LocalAppTheme.current.snapshot.useDynamicColor
    val currentThemeMode = LocalAppTheme.current.snapshot.themeMode
    val currentThemeStyle = LocalAppTheme.current.style
    val currentMiuixStyle = LocalAppTheme.current.snapshot.miuixStyle
    val isMiuixSkin = currentThemeStyle == ThemeStyle.MIUIX
    var appBlurEnabled by rememberSetting(settings, APP_BLUR_PREFERENCE_KEY, true)

    /** 固定入页高亮目标，避免切换动态取色时在两行间跳动。 */
    val highlightedKey = remember(requestedKey) {
        when {
            !isMiuixSkin && requestedKey == MIUIX_STYLE_PREFERENCE_KEY -> THEME_STYLE_PREFERENCE_KEY
            isMiuixSkin && (requestedKey == APPEARANCE_SETTINGS_CUSTOM_COLOR_KEY || requestedKey in MORE_COLOR_OPTION_KEYS || requestedKey == "dynamicColor") -> THEME_STYLE_PREFERENCE_KEY
            useDynamicColor && requestedKey == APPEARANCE_SETTINGS_CUSTOM_COLOR_KEY -> "dynamicColor"
            requestedKey == FLOATING_BOTTOM_BAR_STYLE_PREFERENCE_KEY && !settings.getBoolean(FLOATING_BOTTOM_BAR_PREFERENCE_KEY, true) -> FLOATING_BOTTOM_BAR_PREFERENCE_KEY
            else -> requestedKey
        }
    }
    var moreColorsExpanded by rememberSaveable { mutableStateOf(requestedKey in MORE_COLOR_OPTION_KEYS) }

    var showColorPicker by remember { mutableStateOf(false) }
    var showBackgroundColorPicker by remember { mutableStateOf(false) }
    var showBrowserColorPicker by remember { mutableStateOf(false) }
    var browserColorArgb by rememberSetting(settings, BROWSER_TOOLBAR_COLOR_PREFERENCE_KEY, DEFAULT_BROWSER_TOOLBAR_COLOR)

    DisposableEffect(Unit) {
        onDispose {
            onExit()
        }
    }

    AppSettingsPage(
        title = SettingsStrings.appearance,
        onBack = LocalNavigator.current.onNavigateBack,
        backTag = APPEARANCE_SETTINGS_BACK_BUTTON_TAG,
        highlightedKey = highlightedKey,
        scrollTag = APPEARANCE_SETTINGS_SCROLL_TAG,
        overlays = {
            if (showColorPicker) {
                ColorPickerDialog(
                    title = SettingsStrings.themeColorPicker,
                    initialColor = LocalAppTheme.current.customColor,
                    onDismiss = { showColorPicker = false },
                    onColorSelected = { color ->
                        settings.putInt("customThemeColor", color.toArgb())
                        userMessages.showShortMessage(SettingsStrings.themeColorSaved)
                        showColorPicker = false
                    },
                )
            }
            if (showBackgroundColorPicker) {
                val isDark = LocalAppTheme.current.isDarkTheme
                ColorPickerDialog(
                    title = SettingsStrings.backgroundColorPicker,
                    initialColor = LocalAppTheme.current.backgroundColor,
                    presetColors = listOfNotNull(
                        Color(if (isDark) DEFAULT_BACKGROUND_COLOR_DARK else DEFAULT_BACKGROUND_COLOR_LIGHT),
                        MaterialTheme.colorScheme.surfaceContainer,
                        if (isDark) Color.Black else null,
                    ),
                    onDismiss = { showBackgroundColorPicker = false },
                    onColorSelected = { color ->
                        settings.putInt(if (isDark) "backgroundColorDark" else "backgroundColorLight", color.toArgb())
                        userMessages.showShortMessage(SettingsStrings.backgroundColorSaved)
                        showBackgroundColorPicker = false
                    },
                )
            }
            if (showBrowserColorPicker) {
                ColorPickerDialog(
                    title = SettingsStrings.browserColorPicker,
                    initialColor = Color(browserColorArgb),
                    presetColors = listOf(
                        Color(DEFAULT_BROWSER_TOOLBAR_COLOR),
                        Color(0xFF2196F3),
                        Color(0xFF4CAF50),
                        Color(0xFFF44336),
                        Color(0xFFFF9800),
                        Color(0xFF9C27B0),
                    ),
                    onDismiss = { showBrowserColorPicker = false },
                    onColorSelected = { color ->
                        browserColorArgb = color.toArgb()
                        userMessages.showShortMessage(SettingsStrings.browserColorSaved)
                        showBrowserColorPicker = false
                    },
                )
            }
        },
    ) {
        SettingItemGroup(title = SettingsStrings.theme) {
            if (miuixThemeAvailable) {
                val themeStyles = ThemeStyle.entries

                fun selectThemeStyle(index: Int) {
                    val style = themeStyles[index]
                    settings.putString(THEME_STYLE_PREFERENCE_KEY, style.name)
                    userMessages.showShortMessage(SettingsStrings.skinChanged(style.displayName))
                }

                /** 弹层退出与换皮同帧会向已销毁的 NavigationEventDispatcher 注册 BackHandler，故不用弹层选择器。 */
                SettingItem(
                    title = { Text(SettingsStrings.interfaceSkin) },
                    description = {
                        Text(
                            if (isMiuixSkin) {
                                SettingsStrings.miuixSkinDescription
                            } else {
                                SettingsStrings.materialSkinDescription
                            },
                        )
                    },
                    settingKey = THEME_STYLE_PREFERENCE_KEY,
                    bottomAction = {
                        AppSegmentedControl(
                            options = themeStyles.map(ThemeStyle::displayName),
                            selectedIndex = themeStyles.indexOf(currentThemeStyle),
                            onSelect = ::selectThemeStyle,
                            modifier = Modifier
                                .padding(top = AppDimens.Space8)
                                .fillMaxWidth()
                                .testTag("appearance_theme_style_select"),
                        )
                    },
                )
            }

            if (isMiuixSkin) {
                val miuixStyles = MiuixStyle.entries
                SettingItem(
                    title = { Text(SettingsStrings.hyperosStyle) },
                    description = {
                        Text(
                            when {
                                currentMiuixStyle == MiuixStyle.HYPEROS_4 && !appBlurEnabled -> SettingsStrings.hyperosGlassDisabled
                                currentMiuixStyle == MiuixStyle.HYPEROS_4 && !appGlassRenderingSupported() -> SettingsStrings.hyperosGlassUnsupported
                                else -> SettingsStrings.hyperosStyleDescription
                            },
                        )
                    },
                    settingKey = MIUIX_STYLE_PREFERENCE_KEY,
                    bottomAction = {
                        AppSegmentedControl(
                            options = miuixStyles.map(MiuixStyle::displayName),
                            selectedIndex = miuixStyles.indexOf(currentMiuixStyle),
                            onSelect = { index -> settings.putString(MIUIX_STYLE_PREFERENCE_KEY, miuixStyles[index].name) },
                            modifier = Modifier
                                .padding(top = AppDimens.Space8)
                                .fillMaxWidth()
                                .testTag("appearance_miuix_style_select"),
                        )
                    },
                )
            }

            val themeModes = listOf(
                ThemeMode.SYSTEM to SettingsStrings.themeAutomatic,
                ThemeMode.LIGHT to SettingsStrings.themeLight,
                ThemeMode.DARK to SettingsStrings.themeDark,
            )

            fun selectThemeMode(index: Int) {
                val (mode, label) = themeModes[index]
                settings.putString("themeMode", mode.name)
                userMessages.showShortMessage(SettingsStrings.selectionChanged(label))
            }

            SettingItem(
                title = { Text(SettingsStrings.themeMode) },
                settingKey = "nightMode",
                bottomAction = {
                    AppSegmentedControl(
                        options = themeModes.map { it.second },
                        selectedIndex = themeModes.indexOfFirst { it.first == currentThemeMode },
                        onSelect = ::selectThemeMode,
                        modifier = Modifier
                            .padding(top = AppDimens.Space8)
                            .fillMaxWidth()
                            .testTag("appearance_theme_mode_select"),
                    )
                },
            )

            var appLanguage by rememberSetting(settings, APP_LANGUAGE_KEY, "system")
            val languageValues = listOf("system", "zh", "en")
            SettingItemSelect(
                title = { Text(SettingsStrings.appLanguage) },
                options = listOf(SettingsStrings.systemDefault, "简体中文", "English"),
                selectedIndex = languageValues.indexOf(appLanguage).coerceAtLeast(0),
                onSelect = { appLanguage = languageValues[it] },
                settingKey = APP_LANGUAGE_KEY,
                modifier = Modifier.testTag("appearanceSettings.appLanguage"),
            )

            if (!isMiuixSkin) {
                SettingItemWithSwitch(
                    title = { Text(SettingsStrings.dynamicColor) },
                    description = { Text(SettingsStrings.dynamicColorDescription) },
                    checked = useDynamicColor,
                    onCheckedChange = { checked -> settings.putBoolean("useDynamicColor", checked) },
                    settingKey = "dynamicColor",
                )
            }

            /** 分组只为直接子项留间距；包进同一 Column 会使行贴合，故逐行动画。 */
            AnimatedVisibility(visible = !isMiuixSkin && !useDynamicColor) {
                SettingItem(
                    title = { Text(SettingsStrings.customThemeColor) },
                    description = { Text(SettingsStrings.customThemeColorDescription) },
                    onClick = { showColorPicker = true },
                    endAction = { ColorSwatch(LocalAppTheme.current.customColor, MaterialTheme.colorScheme.outline) },
                    settingKey = APPEARANCE_SETTINGS_CUSTOM_COLOR_KEY,
                )
            }

            val amoledAvailable = currentThemeMode != ThemeMode.LIGHT
            SettingItemWithSwitch(
                title = { Text(SettingsStrings.amoledBlack) },
                description = {
                    Text(if (amoledAvailable) SettingsStrings.amoledDescription else SettingsStrings.darkModeOnly)
                },
                checked = LocalAppTheme.current.snapshot.amoledTheme,
                onCheckedChange = { enabled -> settings.putBoolean(AMOLED_THEME_PREFERENCE_KEY, enabled) },
                enabled = amoledAvailable,
                settingKey = AMOLED_THEME_PREFERENCE_KEY,
            )
        }

        if (!isMiuixSkin) {
            SettingItemGroup {
                val moreColorTitles = listOf(SettingsStrings.paletteStyle, SettingsStrings.colorSpecification, SettingsStrings.backgroundColor, SettingsStrings.browserThemeColor)
                SettingItem(
                    title = { Text(SettingsStrings.moreColorOptions) },
                    description = if (moreColorsExpanded) {
                        null
                    } else {
                        { Text(moreColorTitles.joinToString(SettingsStrings.listSeparator)) }
                    },
                    onClick = { moreColorsExpanded = !moreColorsExpanded },
                    endAction = {
                        AppIcon(
                            imageVector = if (moreColorsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier
                        .testTag(APPEARANCE_SETTINGS_MORE_COLORS_TAG)
                        .semantics { stateDescription = if (moreColorsExpanded) SettingsStrings.expanded else SettingsStrings.collapsed },
                )

                val paletteStyles = ThemePaletteStyle.entries
                AnimatedVisibility(visible = moreColorsExpanded) {
                    SettingItemSelect(
                        title = { Text(SettingsStrings.paletteStyle) },
                        description = { Text(SettingsStrings.paletteDescription) },
                        options = paletteStyles.map { it.displayName },
                        selectedIndex = paletteStyles.indexOf(LocalAppTheme.current.snapshot.paletteStyle),
                        onSelect = { index -> settings.putString(THEME_PALETTE_STYLE_PREFERENCE_KEY, paletteStyles[index].name) },
                        settingKey = THEME_PALETTE_STYLE_PREFERENCE_KEY,
                    )
                }
                val currentPalette = LocalAppTheme.current.snapshot.paletteStyle
                val colorSpecs = currentPalette.supportedColorSpecs
                AnimatedVisibility(visible = moreColorsExpanded) {
                    SettingItemSelect(
                        title = { Text(SettingsStrings.colorSpecification) },
                        description = {
                            Text(
                                if (colorSpecs.size == 1) {
                                    SettingsStrings.paletteSupportedSpec(colorSpecs.single().displayName)
                                } else {
                                    SettingsStrings.expressiveColorSpec
                                },
                            )
                        },
                        options = colorSpecs.map { it.displayName },
                        selectedIndex = colorSpecs.indexOf(currentPalette.effectiveColorSpec(LocalAppTheme.current.snapshot.colorSpec)),
                        onSelect = { index -> settings.putString(THEME_COLOR_SPEC_PREFERENCE_KEY, colorSpecs[index].name) },
                        settingKey = THEME_COLOR_SPEC_PREFERENCE_KEY,
                    )
                }
                AnimatedVisibility(visible = moreColorsExpanded) {
                    SettingItem(
                        title = { Text(SettingsStrings.customBackgroundColor) },
                        description = { Text(if (LocalAppTheme.current.isDarkTheme) SettingsStrings.darkBackgroundColor else SettingsStrings.lightBackgroundColor) },
                        onClick = { showBackgroundColorPicker = true },
                        endAction = { ColorSwatch(LocalAppTheme.current.backgroundColor, MaterialTheme.colorScheme.outlineVariant) },
                        settingKey = APPEARANCE_SETTINGS_BACKGROUND_COLOR_KEY,
                    )
                }
                AnimatedVisibility(visible = moreColorsExpanded) {
                    SettingItem(
                        title = { Text(SettingsStrings.openBrowserThemeColor) },
                        description = { Text(SettingsStrings.browserToolbarColorDescription) },
                        onClick = { showBrowserColorPicker = true },
                        endAction = { ColorSwatch(Color(browserColorArgb), MaterialTheme.colorScheme.outlineVariant) },
                        settingKey = APPEARANCE_SETTINGS_BROWSER_COLOR_KEY,
                    )
                }
                AnimatedVisibility(visible = moreColorsExpanded) {
                    SettingItem(
                        title = { Text(SettingsStrings.restoreDefaults) },
                        description = { Text(SettingsStrings.restoreColorsDescription) },
                        endAction = { AppIcon(Icons.Default.RestartAlt, contentDescription = null) },
                        onClick = {
                            resetPaletteAndBackground(settings)
                            settings.remove(BROWSER_TOOLBAR_COLOR_PREFERENCE_KEY)
                            userMessages.showShortMessage(SettingsStrings.colorsRestored)
                        },
                    )
                }
            }
        }

        SettingItemGroup(title = SettingsStrings.listSection) {
            var feedCardStyle by rememberSetting(settings, FEED_CARD_STYLE_PREFERENCE_KEY, "card")
            SettingItemSelect(
                title = { Text(SettingsStrings.feedCardStyle) },
                description = { Text(SettingsStrings.feedCardStyleDescription) },
                options = listOf(SettingsStrings.cardStyle, SettingsStrings.dividerStyle),
                selectedIndex = if (feedCardStyle == "divider") 1 else 0,
                onSelect = { feedCardStyle = if (it == 1) "divider" else "card" },
                settingKey = FEED_CARD_STYLE_PREFERENCE_KEY,
                modifier = Modifier.testTag("appearanceSettings.feedCardStyle"),
            )
        }

        SettingItemGroup(title = SettingsStrings.floatingControls) {
            SettingItemWithSwitch(
                title = { Text(SettingsStrings.floatingGlass) },
                description = { Text(SettingsStrings.floatingGlassDescription) },
                checked = appBlurEnabled,
                onCheckedChange = { appBlurEnabled = it },
                settingKey = APP_BLUR_PREFERENCE_KEY,
            )

            var floatingBottomBar by rememberSetting(settings, FLOATING_BOTTOM_BAR_PREFERENCE_KEY, true)
            SettingItemWithSwitch(
                title = { Text(SettingsStrings.floatingBottomBar) },
                description = { Text(SettingsStrings.floatingBottomBarDescription) },
                checked = floatingBottomBar,
                onCheckedChange = { floatingBottomBar = it },
                settingKey = FLOATING_BOTTOM_BAR_PREFERENCE_KEY,
            )

            var floatingStyle by rememberSetting(settings, FLOATING_BOTTOM_BAR_STYLE_PREFERENCE_KEY, "default")
            SettingItemSelect(
                title = { Text(SettingsStrings.floatingBottomBarStyle) },
                options = listOf(SettingsStrings.defaultOption, "Apple"),
                selectedIndex = if (floatingStyle == "apple") 1 else 0,
                onSelect = { floatingStyle = if (it == 1) "apple" else "default" },
                enabled = floatingBottomBar,
                settingKey = FLOATING_BOTTOM_BAR_STYLE_PREFERENCE_KEY,
                modifier = Modifier.testTag("appearanceSettings.floatingBottomBarStyle"),
            )

            var squarePopups by rememberSetting(settings, DISABLE_BOTTOM_SHEET_ROUNDED_CORNERS_PREFERENCE_KEY, false)
            SettingItemWithSwitch(
                modifier = Modifier.testTag(APPEARANCE_SETTINGS_DISABLE_BOTTOM_SHEET_ROUNDED_CORNERS_TAG),
                title = { Text(SettingsStrings.squarePopups) },
                description = { Text(SettingsStrings.squarePopupsDescription) },
                checked = squarePopups,
                onCheckedChange = { squarePopups = it },
                settingKey = DISABLE_BOTTOM_SHEET_ROUNDED_CORNERS_PREFERENCE_KEY,
            )

            var showCreateFab by rememberSetting(settings, "showCreateFab", true)
            SettingItemWithSwitch(
                title = { Text(SettingsStrings.showCreateButton) },
                description = { Text(SettingsStrings.showCreateButtonDescription) },
                checked = showCreateFab,
                onCheckedChange = { showCreateFab = it },
                settingKey = "showCreateFab",
            )

            var fabOpacity by rememberSetting(settings, PREF_FAB_OPACITY, DEFAULT_FAB_OPACITY)
            SettingItem(
                title = { Text(SettingsStrings.floatingButtonOpacity) },
                description = { Text(SettingsStrings.floatingButtonOpacityDescription(fabOpacity)) },
                settingKey = PREF_FAB_OPACITY,
                bottomAction = {
                    val slider = rememberSliderState(value = fabOpacity.toFloat(), steps = 17, trackRange = 10f..100f)
                    slider.value = fabOpacity.toFloat()
                    AppSlider(
                        state = slider,
                        onValueChange = { fabOpacity = (it / 5).roundToInt() * 5 },
                        modifier = Modifier.fillMaxWidth().padding(top = AppDimens.Space8),
                    )
                },
            )
        }
    }
}

@Composable
private fun ColorSwatch(color: Color, borderColor: Color) {
    Box(
        modifier = Modifier
            .size(AppDimens.IconSizeLarge)
            .clip(CircleShape)
            .background(color)
            .border(AppDimens.DividerThickness, borderColor, CircleShape),
    )
}
