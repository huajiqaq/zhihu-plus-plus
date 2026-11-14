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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.github.zly2006.zhihu.ui.components.DISABLE_BOTTOM_SHEET_ROUNDED_CORNERS_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.components.ColorPickerDialog
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingItemGroup
import com.github.zly2006.zhihu.ui.components.SettingItemSelect
import com.github.zly2006.zhihu.ui.components.SettingItemWithSwitch
import com.github.zly2006.zhihu.ui.rememberSetting
import com.github.zly2006.zhihu.ui.rememberObservedSetting
import com.github.zly2006.zhihu.navigation.Home
import com.github.zly2006.zhihu.platform.platformBottomBarItemLimit
import com.github.zly2006.zhihu.markdown.isTiqianMarkdownRendererAvailable
import kotlin.math.roundToInt
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

const val APPEARANCE_SETTINGS_BACK_BUTTON_TAG = "appearance_settings_back_button"

const val DUO3_CARD_LARGE_TITLE_PREFERENCE_KEY = "duo3_card_large_title"
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
const val APPEARANCE_SETTINGS_COLLECTION_DIRECT_BROWSE_TAG = "appearanceSettings.collectionDirectBrowse"
const val APPEARANCE_SETTINGS_LANDSCAPE_LIST_DETAIL_TAG = "appearanceSettings.landscapeListDetail"
const val APPEARANCE_SETTINGS_MORE_COLORS_TAG = "appearanceSettings.moreColors"

/** 颜色行没有单一偏好键（背景色按深浅分别存储），故深链使用行键。 */
const val APPEARANCE_SETTINGS_CUSTOM_COLOR_KEY = "customColor"
const val APPEARANCE_SETTINGS_BACKGROUND_COLOR_KEY = "backgroundColor"
const val APPEARANCE_SETTINGS_BROWSER_COLOR_KEY = "browserToolbarColor"

internal val contentFontSizeLevels = (50..120 step 5) + (130..200 step 10)

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
    val isMiuixSkin = currentThemeStyle == ThemeStyle.MIUIX
    /** 固定入页高亮目标，避免切换动态取色时在两行间跳动。 */
    val highlightedKey = remember(requestedKey) {
        when {
            requestedKey == DUO3_CARD_LARGE_TITLE_PREFERENCE_KEY && !settings.getBoolean("duo3_card_layout", false) -> "duo3_card_layout"
            requestedKey == "duo3_article_actions" && !settings.getBoolean("duo3_article_bar", false) -> "duo3_article_bar"
            isMiuixSkin && (requestedKey == APPEARANCE_SETTINGS_CUSTOM_COLOR_KEY || requestedKey in MORE_COLOR_OPTION_KEYS || requestedKey == "dynamicColor") -> THEME_STYLE_PREFERENCE_KEY
            useDynamicColor && requestedKey == APPEARANCE_SETTINGS_CUSTOM_COLOR_KEY -> "dynamicColor"
            else -> requestedKey
        }
    }
    var moreColorsExpanded by rememberSaveable { mutableStateOf(requestedKey in MORE_COLOR_OPTION_KEYS) }

    var showColorPicker by remember { mutableStateOf(false) }
    var showBackgroundColorPicker by remember { mutableStateOf(false) }
    var showBrowserColorPicker by remember { mutableStateOf(false) }
    var browserColorArgb by rememberSetting(settings, "luotianyi_color", 0xff_66CCFF.toInt())

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
                        ThemeManager.setCustomColor(color)
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
                        Color(if (isDark) 0xFF121212.toInt() else 0xFFFFFFFF.toInt()),
                        MaterialTheme.colorScheme.surfaceContainer,
                        if (isDark) Color.Black else null,
                    ),
                    onDismiss = { showBackgroundColorPicker = false },
                    onColorSelected = { color ->
                        ThemeManager.setBackgroundColor(color, isDark)
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
                        Color(0xFF66CCFF),
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
                    ThemeManager.setThemeStyle(themeStyles[index])
                    settings.putString(THEME_STYLE_PREFERENCE_KEY, ThemeManager.currentStyle.name)
                    userMessages.showShortMessage("已切换到${ThemeManager.currentStyle.displayName}皮肤")
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

            val themeModes = listOf(
                ThemeMode.SYSTEM to "自动",
                ThemeMode.LIGHT to "亮色",
                ThemeMode.DARK to "暗色",
            )

            fun selectThemeMode(index: Int) {
                val (mode, label) = themeModes[index]
                ThemeManager.setThemeMode(mode)
                settings.putString("themeMode", mode.name)
                userMessages.showShortMessage("已切换到$label")
            }

            SettingItem(
                title = { Text("主题模式") },
                description = { Text("设置应用的显示主题。") },
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
                    onCheckedChange = { checked ->
                        ThemeManager.setUseDynamicColor(checked)
                        settings.putBoolean("useDynamicColor", checked)
                    },
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
                onCheckedChange = { enabled ->
                    ThemeManager.setAmoledTheme(enabled)
                    settings.putBoolean(AMOLED_THEME_PREFERENCE_KEY, enabled)
                },
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
                        onSelect = { index ->
                            val style = paletteStyles[index]
                            ThemeManager.setPaletteStyle(style)
                            settings.putString(THEME_PALETTE_STYLE_PREFERENCE_KEY, style.name)
                        },
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
                        onSelect = { index ->
                            val spec = colorSpecs[index]
                            ThemeManager.setColorSpec(spec)
                            settings.putString(THEME_COLOR_SPEC_PREFERENCE_KEY, spec.name)
                        },
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
            }
        }

        SettingItemGroup(title = "列表") {
            var feedCardStyle by rememberSetting(settings, FEED_CARD_STYLE_PREFERENCE_KEY, "divider")
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
            var squarePopups by rememberSetting(settings, DISABLE_BOTTOM_SHEET_ROUNDED_CORNERS_PREFERENCE_KEY, false)
            SettingItemWithSwitch(
                modifier = Modifier.testTag(APPEARANCE_SETTINGS_DISABLE_BOTTOM_SHEET_ROUNDED_CORNERS_TAG),
                title = { Text("禁用 popup 圆角") },
                description = { Text("评论等 popup 顶部改为直角。") },
                checked = squarePopups,
                onCheckedChange = { squarePopups = it },
                settingKey = DISABLE_BOTTOM_SHEET_ROUNDED_CORNERS_PREFERENCE_KEY,
            )

            var fabOpacity by rememberSetting(settings, PREF_FAB_OPACITY, DEFAULT_FAB_OPACITY)
            SettingItem(
                title = { Text("悬浮按钮透明度") },
                description = { Text("控制所有悬浮按钮的透明度 ($fabOpacity%)。") },
                settingKey = PREF_FAB_OPACITY,
                bottomAction = {
                    AppSlider(
                        value = fabOpacity.toFloat(),
                        onValueChange = { fabOpacity = (it / 5).roundToInt() * 5 },
                        valueRange = 10f..100f,
                        steps = 17,
                        modifier = Modifier.fillMaxWidth().padding(top = AppDimens.Space8),
                    )
                },
            )
        }
        var duo3All by rememberSetting(settings, "duo3_all", false)
        var duo3HomeAccount by rememberSetting(settings, "duo3_home_account", false)
        var duo3CardAppearance by rememberSetting(settings, "duo3_card_appearance", false)
        var duo3CardLayout by rememberSetting(settings, "duo3_card_layout", false)
        var duo3CardLargeTitle by rememberSetting(settings, DUO3_CARD_LARGE_TITLE_PREFERENCE_KEY, true)
        var duo3ArticleBar by rememberSetting(settings, "duo3_article_bar", false)
        var duo3ArticleActions by rememberSetting(settings, "duo3_article_actions", false)
        val storedBottomBarItems = rememberObservedSetting(settings, BOTTOM_BAR_ITEMS_PREFERENCE_KEY) {
            getStringSet(BOTTOM_BAR_ITEMS_PREFERENCE_KEY, defaultBottomBarSelectionKeys(getBoolean("duo3_home_account", false), platformBottomBarItemLimit))
        }
        val storedBottomBarOrder = rememberObservedSetting(settings, BOTTOM_BAR_ITEM_ORDER_PREFERENCE_KEY) {
            getStringOrNull(BOTTOM_BAR_ITEM_ORDER_PREFERENCE_KEY)
        }
        val selectedBottomBarItemKeys = bottomBarItemOrderFromPreference(
            storedBottomBarOrder.value,
            normalizeBottomBarSelection(storedBottomBarItems.value, duo3HomeAccount, enforceMinimumSelection = true, maximumSelection = platformBottomBarItemLimit),
        )

        fun saveLegacyBottomBarSelection(homeAccountEnabled: Boolean) {
            val currentOrder = selectedBottomBarItemKeys
            val normalized = normalizeBottomBarSelection(currentOrder, homeAccountEnabled, enforceMinimumSelection = true, maximumSelection = platformBottomBarItemLimit)
            val order = normalizeBottomBarItemOrder(currentOrder, normalized)
            settings.putStringSet(BOTTOM_BAR_ITEMS_PREFERENCE_KEY, normalized)
            settings.putString(BOTTOM_BAR_ITEM_ORDER_PREFERENCE_KEY, bottomBarItemOrderPreferenceValue(order))
            settings.putString(START_DESTINATION_PREFERENCE_KEY, resolveValidStartDestinationKey(settings.getString(START_DESTINATION_PREFERENCE_KEY, Home.name), order))
        }

        fun setLegacyLayout(enabled: Boolean) {
            duo3All = enabled
            if (isTiqianMarkdownRendererAvailable) settings.putBoolean(DUO3_TIQIAN_MARKDOWN_PREFERENCE_KEY, enabled)
            duo3HomeAccount = enabled
            duo3CardAppearance = enabled
            duo3CardLayout = enabled
            duo3ArticleBar = enabled
            duo3ArticleActions = enabled
            if (enabled) {
                settings.putBoolean("showRefreshFab", false)
                settings.putBoolean("buttonSkipAnswer", false)
            }
            saveLegacyBottomBarSelection(enabled)
        }

        SettingItemGroup(title = "悬浮控件") {
            var appBlurEnabled by rememberSetting(settings, APP_BLUR_PREFERENCE_KEY, true)
            SettingItemWithSwitch(
                title = { Text("悬浮玻璃") },
                description = { Text("顶栏、底栏和悬浮控件使用背景模糊；关闭后使用不透明背景。") },
                checked = appBlurEnabled,
                onCheckedChange = { appBlurEnabled = it },
                settingKey = APP_BLUR_PREFERENCE_KEY,
            )

            var floatingBottomBar by rememberSetting(settings, FLOATING_BOTTOM_BAR_PREFERENCE_KEY, true)
            SettingItemWithSwitch(
                title = { Text("悬浮底栏") },
                description = { Text("底栏脱离屏幕边缘，以圆角玻璃浮层显示；关闭后大屏改用侧边导航，手机保持贴底。") },
                checked = floatingBottomBar,
                onCheckedChange = { floatingBottomBar = it },
                settingKey = FLOATING_BOTTOM_BAR_PREFERENCE_KEY,
            )

        }

        SettingItemGroup(title = "123Duo3 的 UI/UX 改进（beta）", settingKey = "123Duo3") {
            SettingItemWithSwitch(
                title = { Text("启用所有修改并关闭浮动按钮") },
                checked = duo3All,
                onCheckedChange = ::setLegacyLayout,
                settingKey = "duo3_all",
            )
            SettingItemWithSwitch(
                title = { Text("主页：账号入口迁移至顶部头像") },
                description = { Text("搜索栏样式变更；点击头像弹出账号与设置；历史入口可挪入账号设置页。") },
                checked = duo3HomeAccount,
                onCheckedChange = { duo3HomeAccount = it; saveLegacyBottomBarSelection(it) },
                settingKey = "duo3_home_account",
            )
            SettingItemWithSwitch(
                title = { Text("信息流卡片：外观更改") },
                description = { Text("卡片圆角增大，移除阴影；修改背景与卡片颜色。") },
                checked = duo3CardAppearance,
                onCheckedChange = { duo3CardAppearance = it },
                settingKey = "duo3_card_appearance",
            )
            SettingItemWithSwitch(
                title = { Text("信息流卡片：更改内容排版") },
                description = { Text("作者移至底部；摘要最多显示 4 行。") },
                checked = duo3CardLayout,
                onCheckedChange = { duo3CardLayout = it },
                settingKey = "duo3_card_layout",
            )
            AnimatedVisibility(visible = duo3CardLayout) {
                SettingItemWithSwitch(
                    title = { Text("信息流卡片：使用更大的标题字体") },
                    checked = duo3CardLargeTitle,
                    onCheckedChange = { duo3CardLargeTitle = it },
                    settingKey = DUO3_CARD_LARGE_TITLE_PREFERENCE_KEY,
                )
            }
            SettingItemWithSwitch(
                title = { Text("文章阅读页：更改整体顶/底栏框架") },
                description = { Text("更改标题栏样式；优化顶/底栏隐藏逻辑。") },
                checked = duo3ArticleBar,
                onCheckedChange = { duo3ArticleBar = it },
                settingKey = "duo3_article_bar",
            )
            AnimatedVisibility(visible = duo3ArticleBar) {
                SettingItemWithSwitch(
                    title = { Text("文章阅读页：更改操作栏样式") },
                    checked = duo3ArticleActions,
                    onCheckedChange = { duo3ArticleActions = it },
                    settingKey = "duo3_article_actions",
                )
            }
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
