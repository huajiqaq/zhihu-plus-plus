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
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.theme.AMOLED_THEME_PREFERENCE_KEY
import com.github.zly2006.zhihu.theme.DEFAULT_BACKGROUND_COLOR_DARK
import com.github.zly2006.zhihu.theme.DEFAULT_BACKGROUND_COLOR_LIGHT
import com.github.zly2006.zhihu.theme.MIUIX_STYLE_PREFERENCE_KEY
import com.github.zly2006.zhihu.theme.MiuixStyle
import com.github.zly2006.zhihu.theme.THEME_COLOR_SPEC_PREFERENCE_KEY
import com.github.zly2006.zhihu.theme.THEME_PALETTE_STYLE_PREFERENCE_KEY
import com.github.zly2006.zhihu.theme.THEME_STYLE_PREFERENCE_KEY
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeMode
import com.github.zly2006.zhihu.theme.ThemePaletteStyle
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.miuixThemeAvailable
import com.github.zly2006.zhihu.ui.components.AppDimens
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
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

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

    val useDynamicColor = ThemeManager.getUseDynamicColor()
    val currentThemeMode = ThemeManager.getThemeMode()
    val currentThemeStyle = ThemeManager.getThemeStyle()
    val currentMiuixStyle = ThemeManager.getMiuixStyle()
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
        title = "外观",
        onBack = LocalNavigator.current.onNavigateBack,
        backTag = APPEARANCE_SETTINGS_BACK_BUTTON_TAG,
        highlightedKey = highlightedKey,
        scrollTag = APPEARANCE_SETTINGS_SCROLL_TAG,
        overlays = {
            if (showColorPicker) {
                ColorPickerDialog(
                    title = "选择主题色",
                    initialColor = ThemeManager.getCustomColor(),
                    onDismiss = { showColorPicker = false },
                    onColorSelected = { color ->
                        settings.putInt("customThemeColor", color.toArgb())
                        userMessages.showShortMessage("主题色已保存")
                        showColorPicker = false
                    },
                )
            }
            if (showBackgroundColorPicker) {
                val isDark = ThemeManager.isDarkTheme()
                ColorPickerDialog(
                    title = "选择背景颜色",
                    initialColor = ThemeManager.getBackgroundColor(),
                    presetColors = listOfNotNull(
                        Color(if (isDark) DEFAULT_BACKGROUND_COLOR_DARK else DEFAULT_BACKGROUND_COLOR_LIGHT),
                        MaterialTheme.colorScheme.surfaceContainer,
                        if (isDark) Color.Black else null,
                    ),
                    onDismiss = { showBackgroundColorPicker = false },
                    onColorSelected = { color ->
                        settings.putInt(if (isDark) "backgroundColorDark" else "backgroundColorLight", color.toArgb())
                        userMessages.showShortMessage("背景颜色已保存")
                        showBackgroundColorPicker = false
                    },
                )
            }
            if (showBrowserColorPicker) {
                ColorPickerDialog(
                    title = "选择浏览器主题色",
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
                        userMessages.showShortMessage("浏览器主题色已保存")
                        showBrowserColorPicker = false
                    },
                )
            }
        },
    ) {
        SettingItemGroup(title = "主题") {
            if (miuixThemeAvailable) {
                val themeStyles = ThemeStyle.entries

                fun selectThemeStyle(index: Int) {
                    val style = themeStyles[index]
                    settings.putString(THEME_STYLE_PREFERENCE_KEY, style.name)
                    userMessages.showShortMessage("已切换到${style.displayName}皮肤")
                }

                /** 弹层退出与换皮同帧会向已销毁的 NavigationEventDispatcher 注册 BackHandler，故不用弹层选择器。 */
                SettingItem(
                    title = { Text("界面皮肤") },
                    description = {
                        Text(
                            if (isMiuixSkin) {
                                "使用 HyperOS 原生中性色与蓝色强调色，不跟随 Material 配色。"
                            } else {
                                "使用 Material 3 Expressive 的形状、动态取色与强调动效。"
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
                    title = { Text("HyperOS 风格") },
                    description = {
                        Text(
                            when {
                                currentMiuixStyle == MiuixStyle.HYPEROS_4 && !appBlurEnabled -> "已保存 HyperOS 4；悬浮玻璃已关闭，当前使用实心效果。"
                                currentMiuixStyle == MiuixStyle.HYPEROS_4 && !appGlassRenderingSupported() -> "已保存 HyperOS 4；当前平台不支持玻璃渲染，使用实心效果。"
                                else -> "HyperOS 4 的玻璃效果需开启悬浮玻璃且平台支持渲染；切换不重置配色、AMOLED 或悬浮控件偏好。"
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
                ThemeMode.SYSTEM to "自动",
                ThemeMode.LIGHT to "亮色",
                ThemeMode.DARK to "暗色",
            )

            fun selectThemeMode(index: Int) {
                val (mode, label) = themeModes[index]
                settings.putString("themeMode", mode.name)
                userMessages.showShortMessage("已切换到$label")
            }

            SettingItem(
                title = { Text("主题模式") },
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

            if (!isMiuixSkin) {
                SettingItemWithSwitch(
                    title = { Text("使用 Material You 动态取色") },
                    description = { Text("从系统壁纸提取主题色，并应用所选调色风格；不支持壁纸取色的平台使用保存的主题色。") },
                    checked = useDynamicColor,
                    onCheckedChange = { checked -> settings.putBoolean("useDynamicColor", checked) },
                    settingKey = "dynamicColor",
                )
            }

            /** 分组只为直接子项留间距；包进同一 Column 会使行贴合，故逐行动画。 */
            AnimatedVisibility(visible = !isMiuixSkin && !useDynamicColor) {
                SettingItem(
                    title = { Text("自定义主题色") },
                    description = { Text("选择自定义配色的基础强调色") },
                    onClick = { showColorPicker = true },
                    endAction = { ColorSwatch(ThemeManager.getCustomColor(), MaterialTheme.colorScheme.outline) },
                    settingKey = APPEARANCE_SETTINGS_CUSTOM_COLOR_KEY,
                )
            }

            val amoledAvailable = currentThemeMode != ThemeMode.LIGHT
            SettingItemWithSwitch(
                title = { Text("AMOLED 纯黑") },
                description = {
                    Text(if (amoledAvailable) "暗色模式下使用纯黑背景和更低亮度的表面层级" else "仅深色模式可用")
                },
                checked = ThemeManager.getAmoledTheme(),
                onCheckedChange = { enabled -> settings.putBoolean(AMOLED_THEME_PREFERENCE_KEY, enabled) },
                enabled = amoledAvailable,
                settingKey = AMOLED_THEME_PREFERENCE_KEY,
            )
        }

        if (!isMiuixSkin) {
            SettingItemGroup {
                val moreColorTitles = listOf("调色风格", "颜色规范", "背景颜色", "浏览器主题色")
                SettingItem(
                    title = { Text("更多颜色选项") },
                    description = if (moreColorsExpanded) {
                        null
                    } else {
                        { Text(moreColorTitles.joinToString("、")) }
                    },
                    onClick = { moreColorsExpanded = !moreColorsExpanded },
                    endAction = {
                        Icon(
                            imageVector = if (moreColorsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier
                        .testTag(APPEARANCE_SETTINGS_MORE_COLORS_TAG)
                        .semantics { stateDescription = if (moreColorsExpanded) "已展开" else "已收起" },
                )

                val paletteStyles = ThemePaletteStyle.entries
                AnimatedVisibility(visible = moreColorsExpanded) {
                    SettingItemSelect(
                        title = { Text("调色风格") },
                        description = { Text("决定强调色与中性色之间的关系") },
                        options = paletteStyles.map { it.displayName },
                        selectedIndex = paletteStyles.indexOf(ThemeManager.getPaletteStyle()),
                        onSelect = { index -> settings.putString(THEME_PALETTE_STYLE_PREFERENCE_KEY, paletteStyles[index].name) },
                        settingKey = THEME_PALETTE_STYLE_PREFERENCE_KEY,
                    )
                }
                val currentPalette = ThemeManager.getPaletteStyle()
                val colorSpecs = currentPalette.supportedColorSpecs
                AnimatedVisibility(visible = moreColorsExpanded) {
                    SettingItemSelect(
                        title = { Text("颜色规范") },
                        description = {
                            Text(
                                if (colorSpecs.size == 1) {
                                    "当前调色风格仅支持 ${colorSpecs.single().displayName} 规范"
                                } else {
                                    "2025 规范提供更强的明暗层次和 Expressive 色彩关系"
                                },
                            )
                        },
                        options = colorSpecs.map { it.displayName },
                        selectedIndex = colorSpecs.indexOf(currentPalette.effectiveColorSpec(ThemeManager.getColorSpec())),
                        onSelect = { index -> settings.putString(THEME_COLOR_SPEC_PREFERENCE_KEY, colorSpecs[index].name) },
                        settingKey = THEME_COLOR_SPEC_PREFERENCE_KEY,
                    )
                }
                AnimatedVisibility(visible = moreColorsExpanded) {
                    SettingItem(
                        title = { Text("自定义背景颜色") },
                        description = { Text(if (ThemeManager.isDarkTheme()) "深色模式背景色" else "浅色模式背景色") },
                        onClick = { showBackgroundColorPicker = true },
                        endAction = { ColorSwatch(ThemeManager.getBackgroundColor(), MaterialTheme.colorScheme.outlineVariant) },
                        settingKey = APPEARANCE_SETTINGS_BACKGROUND_COLOR_KEY,
                    )
                }
                AnimatedVisibility(visible = moreColorsExpanded) {
                    SettingItem(
                        title = { Text("唤起浏览器主题色") },
                        description = { Text("应用内浏览器的工具栏颜色") },
                        onClick = { showBrowserColorPicker = true },
                        endAction = { ColorSwatch(Color(browserColorArgb), MaterialTheme.colorScheme.outlineVariant) },
                        settingKey = APPEARANCE_SETTINGS_BROWSER_COLOR_KEY,
                    )
                }
                AnimatedVisibility(visible = moreColorsExpanded) {
                    SettingItem(
                        title = { Text("恢复默认") },
                        description = { Text("仅恢复调色风格、颜色规范、深浅背景色和浏览器工具栏颜色。") },
                        endAction = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
                        onClick = {
                            ThemeManager.resetPaletteAndBackground(settings)
                            settings.remove(BROWSER_TOOLBAR_COLOR_PREFERENCE_KEY)
                            userMessages.showShortMessage("已恢复本组默认颜色")
                        },
                    )
                }
            }
        }

        SettingItemGroup(title = "列表") {
            var feedCardStyle by rememberSetting(settings, FEED_CARD_STYLE_PREFERENCE_KEY, "card")
            SettingItemSelect(
                title = { Text("信息流样式") },
                description = { Text("卡片保留圆角与间距；分割线使用无底色的连续列表。") },
                options = listOf("卡片", "分割线"),
                selectedIndex = if (feedCardStyle == "divider") 1 else 0,
                onSelect = { feedCardStyle = if (it == 1) "divider" else "card" },
                settingKey = FEED_CARD_STYLE_PREFERENCE_KEY,
                modifier = Modifier.testTag("appearanceSettings.feedCardStyle"),
            )
        }

        SettingItemGroup(title = "悬浮控件") {
            SettingItemWithSwitch(
                title = { Text("悬浮玻璃") },
                description = { Text("顶栏、悬浮底栏和悬浮控件使用背景模糊；关闭后使用不透明背景。") },
                checked = appBlurEnabled,
                onCheckedChange = { appBlurEnabled = it },
                settingKey = APP_BLUR_PREFERENCE_KEY,
            )

            var floatingBottomBar by rememberSetting(settings, FLOATING_BOTTOM_BAR_PREFERENCE_KEY, true)
            SettingItemWithSwitch(
                title = { Text("悬浮底栏") },
                description = { Text("底栏脱离屏幕边缘，以圆角浮层显示；关闭后，足够宽的横屏窗口使用侧边导航，竖屏或窄窗口保持贴底。") },
                checked = floatingBottomBar,
                onCheckedChange = { floatingBottomBar = it },
                settingKey = FLOATING_BOTTOM_BAR_PREFERENCE_KEY,
            )

            var floatingStyle by rememberSetting(settings, FLOATING_BOTTOM_BAR_STYLE_PREFERENCE_KEY, "default")
            SettingItemSelect(
                title = { Text("悬浮底栏样式") },
                options = listOf("默认", "Apple"),
                selectedIndex = if (floatingStyle == "apple") 1 else 0,
                onSelect = { floatingStyle = if (it == 1) "apple" else "default" },
                enabled = floatingBottomBar,
                settingKey = FLOATING_BOTTOM_BAR_STYLE_PREFERENCE_KEY,
                modifier = Modifier.testTag("appearanceSettings.floatingBottomBarStyle"),
            )

            var squarePopups by rememberSetting(settings, DISABLE_BOTTOM_SHEET_ROUNDED_CORNERS_PREFERENCE_KEY, false)
            SettingItemWithSwitch(
                modifier = Modifier.testTag(APPEARANCE_SETTINGS_DISABLE_BOTTOM_SHEET_ROUNDED_CORNERS_TAG),
                title = { Text("禁用 popup 圆角") },
                description = { Text("评论等 popup 顶部改为直角。") },
                checked = squarePopups,
                onCheckedChange = { squarePopups = it },
                settingKey = DISABLE_BOTTOM_SHEET_ROUNDED_CORNERS_PREFERENCE_KEY,
            )

            var showCreateFab by rememberSetting(settings, "showCreateFab", true)
            SettingItemWithSwitch(
                title = { Text("显示发布按钮") },
                description = { Text("在主页右下角显示创作/发布按钮。") },
                checked = showCreateFab,
                onCheckedChange = { showCreateFab = it },
                settingKey = "showCreateFab",
            )

            var fabOpacity by rememberSetting(settings, PREF_FAB_OPACITY, DEFAULT_FAB_OPACITY)
            SettingItem(
                title = { Text("悬浮按钮透明度") },
                description = { Text("控制所有悬浮按钮的透明度 ($fabOpacity%)。") },
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
