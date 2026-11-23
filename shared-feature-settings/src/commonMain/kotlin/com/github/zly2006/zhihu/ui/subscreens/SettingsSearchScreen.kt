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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.github.zly2006.zhihu.data.AIGC_MARKING_ENABLED_PREFERENCE_KEY
import com.github.zly2006.zhihu.markdown.isTiqianMarkdownRendererAvailable
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Notification
import com.github.zly2006.zhihu.notification.NotificationType
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.isAnswerSwipeSupported
import com.github.zly2006.zhihu.platform.isPageTurnSupported
import com.github.zly2006.zhihu.platform.platformName
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.reading.READING_PREFERENCES_KEY
import com.github.zly2006.zhihu.reading.ReadingPublishedTimeMode
import com.github.zly2006.zhihu.reading.ReadingTemplateField
import com.github.zly2006.zhihu.reading.isReadingPlayerSupported
import com.github.zly2006.zhihu.reading.loadReadingPreferences
import com.github.zly2006.zhihu.theme.AMOLED_THEME_PREFERENCE_KEY
import com.github.zly2006.zhihu.theme.MIUIX_STYLE_PREFERENCE_KEY
import com.github.zly2006.zhihu.theme.THEME_COLOR_SPEC_PREFERENCE_KEY
import com.github.zly2006.zhihu.theme.THEME_PALETTE_STYLE_PREFERENCE_KEY
import com.github.zly2006.zhihu.theme.THEME_STYLE_PREFERENCE_KEY
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeMode
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.miuixThemeAvailable
import com.github.zly2006.zhihu.ui.ANSWER_DOUBLE_TAP_ACTION_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.ARTICLE_USE_WEBVIEW_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.AUTO_REFRESH_HOME_ON_STARTUP_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.components.ANSWER_SWITCH_SENSITIVITY_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.components.AppCompactTopBar
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppEmptyState
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.AppSearchField
import com.github.zly2006.zhihu.ui.components.DISABLE_BOTTOM_SHEET_ROUNDED_CORNERS_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingItemGroup
import com.github.zly2006.zhihu.ui.components.SettingsContent
import com.github.zly2006.zhihu.ui.components.appGroupedPageBackground
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import com.github.zly2006.zhihu.ui.isLegacyWebViewSupported
import com.github.zly2006.zhihu.ui.rememberObservedSetting
import com.github.zly2006.zhihu.viewmodel.ANSWER_VOTEUP_THRESHOLD_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.ARTICLE_FOLLOWERS_THRESHOLD_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.ARTICLE_VOTEUP_THRESHOLD_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.QUALITY_FILTER_MODE_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.QUESTION_ANSWER_THRESHOLD_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.QUESTION_FOLLOWERS_THRESHOLD_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.QualityFilterMode
import com.github.zly2006.zhihu.viewmodel.VIDEO_FOLLOWERS_THRESHOLD_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.VIDEO_VOTE_THRESHOLD_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

const val SETTINGS_SEARCH_BACK_BUTTON_TAG = "settings_search_back_button"

const val SETTINGS_SEARCH_INPUT_TAG = "settingsSearch.input"
const val SETTINGS_SEARCH_RESULTS_TAG = "settingsSearch.results"

/** 搜索结果依赖的父项。条件不满足时目标行被隐藏或禁用：结果下方写 [hint]，点击跳到 [parent] 并高亮它。 */
private class SettingsSearchRequirement(
    val hint: @Composable () -> String,
    val parent: @Composable () -> NavDestination,
    val isMet: @Composable (SettingsStore) -> Boolean,
)

private data class SettingsSearchEntry(
    val id: String,
    val title: String,
    val section: String,
    val description: String,
    val destination: NavDestination,
    val keywords: List<String> = emptyList(),
    val requirement: SettingsSearchRequirement? = null,
) {
    fun matches(query: String): Boolean {
        val normalizedTerms = query
            .trim()
            .lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
        if (normalizedTerms.isEmpty()) return true
        val searchableText = buildString {
            append(title)
            append('\n')
            append(section)
            append('\n')
            append(description)
            if (keywords.isNotEmpty()) {
                append('\n')
                append(keywords.joinToString(" "))
            }
        }.lowercase()
        return normalizedTerms.all(searchableText::contains)
    }
}

private val requiresPredictiveBack = SettingsSearchRequirement(
    hint = { "需先开启预测性返回" },
    parent = { Account.NavigationSettings(setting = "enable_predictive_back") },
    isMet = { settings -> settings.getBoolean("enable_predictive_back", true) },
)
private val requiresMiuixSkin = SettingsSearchRequirement(
    hint = { "需先切换到 Miuix 皮肤" },
    parent = { Account.AppearanceSettings(setting = THEME_STYLE_PREFERENCE_KEY) },
    isMet = { ThemeManager.getThemeStyle() == ThemeStyle.MIUIX },
)
private val requiresCustomColor = SettingsSearchRequirement(
    hint = { if (ThemeManager.getThemeStyle() == ThemeStyle.MIUIX) "需先切换到 Material 皮肤" else "需先关闭动态取色" },
    parent = { Account.AppearanceSettings(setting = if (ThemeManager.getThemeStyle() == ThemeStyle.MIUIX) THEME_STYLE_PREFERENCE_KEY else "dynamicColor") },
    isMet = { !ThemeManager.getUseDynamicColor() && ThemeManager.getThemeStyle() != ThemeStyle.MIUIX },
)
private val requiresDarkMode = SettingsSearchRequirement(
    hint = { "仅深色模式可用" },
    parent = { Account.AppearanceSettings(setting = "nightMode") },
    isMet = { ThemeManager.getThemeMode() != ThemeMode.LIGHT },
)
private val requiresTiqianMarkdown = SettingsSearchRequirement(
    hint = { "需先开启提椠 Markdown 渲染器" },
    parent = { Account.ReadingSettings(setting = TIQIAN_MARKDOWN_PREFERENCE_KEY) },
    isMet = { settings -> rememberObservedSetting(settings, TIQIAN_MARKDOWN_PREFERENCE_KEY) { getBoolean(TIQIAN_MARKDOWN_PREFERENCE_KEY, false) }.value },
)
private val requiresAnswerSwitch = SettingsSearchRequirement(
    hint = { "需先开启回答切换手势" },
    parent = { Account.ReadingSettings(setting = "answerSwitchMode") },
    isMet = { settings -> rememberObservedSetting(settings, "answerSwitchMode") { getString("answerSwitchMode", "vertical") }.value != "off" },
)
private val requiresQualityFilter = SettingsSearchRequirement(
    hint = { "需先开启质量过滤" },
    parent = { Account.RecommendSettings(setting = QUALITY_FILTER_MODE_PREFERENCE_KEY) },
    isMet = { settings -> rememberObservedSetting(settings, QUALITY_FILTER_MODE_PREFERENCE_KEY) { getString(QUALITY_FILTER_MODE_PREFERENCE_KEY, QualityFilterMode.RULES.name) }.value != QualityFilterMode.OFF.name },
)
private val requiresTopicBlocking = SettingsSearchRequirement(
    hint = { "需先开启主题屏蔽" },
    parent = { Account.RecommendSettings(setting = "enableTopicBlocking") },
    isMet = { settings -> rememberObservedSetting(settings, "enableTopicBlocking") { getBoolean("enableTopicBlocking", true) }.value },
)
private val requiresPublishedAt = SettingsSearchRequirement(
    hint = { "需先在朗读内容里勾选发布时间" },
    parent = { Account.ReadingPlaybackSettings(setting = READING_PLAYBACK_FIELD_ORDER_KEY) },
    isMet = { settings -> ReadingTemplateField.PublishedAt in rememberObservedSetting(settings, READING_PREFERENCES_KEY) { loadReadingPreferences(this) }.value.enabledFields },
)
private val requiresRelativeTime = SettingsSearchRequirement(
    hint = { "需先勾选发布时间并选择相对时间" },
    parent = { Account.ReadingPlaybackSettings(setting = READING_PLAYBACK_PUBLISHED_TIME_MODE_KEY) },
    isMet = { settings ->
        rememberObservedSetting(settings, READING_PREFERENCES_KEY) { loadReadingPreferences(this) }.value.let {
            ReadingTemplateField.PublishedAt in it.enabledFields && it.publishedTimeMode == ReadingPublishedTimeMode.Relative
        }
    },
)
private val requiresReadingComments = SettingsSearchRequirement(
    hint = { "需先在朗读内容里勾选评论区" },
    parent = { Account.ReadingPlaybackSettings(setting = READING_PLAYBACK_FIELD_ORDER_KEY) },
    isMet = { settings -> ReadingTemplateField.Comments in rememberObservedSetting(settings, READING_PREFERENCES_KEY) { loadReadingPreferences(this) }.value.enabledFields },
)
private val requiresCommentDetails = SettingsSearchRequirement(
    hint = { "需先勾选评论区并把评论数量设为大于 0" },
    parent = { Account.ReadingPlaybackSettings(setting = READING_PLAYBACK_COMMENT_COUNT_KEY) },
    isMet = { settings -> rememberObservedSetting(settings, READING_PREFERENCES_KEY) { loadReadingPreferences(this) }.value.shouldLoadComments },
)

private val settingsSearchEntries = buildList {
    add(SettingsSearchEntry("appearance.bottomSheetCorners", "禁用 popup 圆角", "外观", "评论等 popup 顶部改为直角。", Account.AppearanceSettings(setting = DISABLE_BOTTOM_SHEET_ROUNDED_CORNERS_PREFERENCE_KEY), listOf("评论圆角", "popup", "直角")))

    add(SettingsSearchEntry("appearance.nightMode", "主题模式", "外观", "切换浅色、深色或跟随系统。", Account.AppearanceSettings(setting = "nightMode"), listOf("夜间模式", "深色模式", "暗色模式", "浅色模式", "跟随系统")))
    add(SettingsSearchEntry("appearance.dynamicColor", "动态取色 / 自定义配色", "外观", "M3E 从壁纸提取主题色或使用自选颜色；Miuix 固定原生配色。", Account.AppearanceSettings(setting = "dynamicColor"), listOf("动态颜色", "壁纸取色", "主题色", "Monet")))
    add(SettingsSearchEntry("appearance.paletteStyle", "调色风格", "外观", "选择均衡、中性、鲜明、表现力等 Material 调色板。", Account.AppearanceSettings(setting = THEME_PALETTE_STYLE_PREFERENCE_KEY), listOf("颜色风格", "palette", "expressive")))
    add(SettingsSearchEntry("appearance.colorSpec", "颜色规范", "外观", "切换 2021 或 2025 动态颜色算法；部分调色风格仅支持 2021。", Account.AppearanceSettings(setting = THEME_COLOR_SPEC_PREFERENCE_KEY), listOf("颜色算法", "spec 2025")))
    add(SettingsSearchEntry("appearance.amoled", "AMOLED 纯黑", "外观", "暗色模式使用纯黑背景和更低亮度的表面。", Account.AppearanceSettings(setting = AMOLED_THEME_PREFERENCE_KEY), listOf("纯黑", "OLED"), requiresDarkMode))
    add(SettingsSearchEntry("appearance.landscapeListDetail", "横屏双栏布局", "导航与交互", "开启后，平板和电脑在窗口宽度至少 840dp 且宽大于高时同时显示列表与详情；竖屏、窄窗口和手机始终单栏。侧边导航不受此开关影响。", Account.NavigationSettings(setting = LANDSCAPE_LIST_DETAIL_PREFERENCE_KEY), listOf("双栏", "分栏", "平板", "横屏")))
    add(SettingsSearchEntry("appearance.fontScale", "字号", "阅读与排版", "调整正文阅读字号。", Account.ReadingSettings(setting = "fontScale"), listOf("字体大小", "内容字体", "正文字号")))
    add(SettingsSearchEntry("appearance.feedCardStyle", "信息流样式", "外观", "选择圆角卡片或分割线列表，立即应用于共用信息流。", Account.AppearanceSettings(setting = FEED_CARD_STYLE_PREFERENCE_KEY), listOf("卡片", "分割线", "分隔线", "列表样式")))
    add(SettingsSearchEntry("appearance.showFeedThumbnail", "显示 Feed 卡片缩略图", "推荐与信息流", "控制信息流卡片图片显示。", Account.FeedSettings(setting = "showFeedThumbnail"), listOf("图片", "封面")))
    add(SettingsSearchEntry("appearance.showRefreshFab", "显示刷新悬浮按钮", "推荐与信息流", "首页按钮组固定在页面末端一侧，只能上下拖动；其他列表刷新按钮可上下左右拖动，松手贴靠左右边缘。横竖屏分别记忆位置。", Account.FeedSettings(setting = "showRefreshFab"), listOf("刷新按钮", "浮动按钮")))
    if (isLegacyWebViewSupported) {
        add(
            SettingsSearchEntry(
                "appearance.webviewRender",
                "使用 WebView 显示文章（已弃用）",
                "阅读与排版",
                "仅作兼容性回退，不再更新；开启后正文查找定位与高亮、正文内划线喜欢与句子评论交互等 Compose 专属功能不可用，每次开启都需确认。",
                Account.ReadingSettings(setting = ARTICLE_USE_WEBVIEW_PREFERENCE_KEY),
            ),
        )
    }
    add(SettingsSearchEntry("appearance.titleAutoHide", "自动隐藏回答标题", "阅读与排版", "阅读时自动收起顶部标题。", Account.ReadingSettings(setting = "titleAutoHide"), listOf("标题栏")))
    add(SettingsSearchEntry("appearance.autoHideArticleBottomBar", "自动隐藏回答底部按钮", "阅读与排版", "滚动阅读时自动隐藏底部操作栏。", Account.ReadingSettings(setting = "autoHideArticleBottomBar")))
    add(SettingsSearchEntry("appearance.buttonSkipAnswer", "显示跳转下一个回答按钮", "阅读与排版", "在回答页显示快速跳转按钮，可上下左右拖动，松手贴靠左右边缘。横竖屏分别记忆位置。", Account.ReadingSettings(setting = "buttonSkipAnswer"), listOf("下一个回答")))
    add(SettingsSearchEntry("appearance.pinAnswerDate", "置顶回答日期", "阅读与排版", "调整回答日期在正文中的位置。", Account.ReadingSettings(setting = "pinAnswerDate")))
    if (isAnswerSwipeSupported) {
        add(SettingsSearchEntry("appearance.answerSwitchMode", "回答切换手势", "阅读与排版", "设置回答之间的上下或左右切换。", Account.ReadingSettings(setting = "answerSwitchMode"), listOf("手势", "上下滑动", "左右滑动", "切换回答")))
    }
    if (isPageTurnSupported) {
        add(SettingsSearchEntry("appearance.pageTurnVolume", "音量键翻页", "电纸书翻页", "在支持翻页的可滚动页面使用物理按键翻页。", Account.PageTurnSettings(setting = PREF_VOLUME_KEY_PAGE_TURN), listOf("电纸书", "翻页")))
        add(SettingsSearchEntry("appearance.pageTurnSwitchAnswer", "翻页切换回答", "电纸书翻页", "在回答顶部或底部继续翻页，切换到相邻回答。", Account.PageTurnSettings(setting = PREF_PAGE_TURN_SWITCH_ANSWER), listOf("电纸书", "翻页", "上一个回答", "下一个回答")))
        add(SettingsSearchEntry("appearance.pageTurnFab", "显示翻页悬浮按钮", "电纸书翻页", "只在支持翻页的可滚动页面显示，可上下左右拖动，松手贴靠左右边缘。横竖屏分别记忆位置。", Account.PageTurnSettings(setting = PREF_SHOW_PAGE_TURN_FAB), listOf("电纸书", "翻页")))
        add(SettingsSearchEntry("appearance.pageTurnDistance", "翻页距离", "电纸书翻页", "设置每次滚动占可见区域的比例。", Account.PageTurnSettings(setting = PREF_PAGE_TURN_PERCENT), listOf("电纸书", "翻页", "重叠")))
        add(SettingsSearchEntry("appearance.pageTurnGuide", "显示翻页位置线", "电纸书翻页", "标记相邻两页的重叠位置。", Account.PageTurnSettings(setting = PREF_SHOW_PAGE_TURN_GUIDE), listOf("电纸书", "翻页", "引导线")))
        add(SettingsSearchEntry("appearance.contentEndMarker", "显示内容结束标记", "电纸书翻页", "方便电纸书用户确定内容结束。", Account.PageTurnSettings(setting = PREF_SHOW_CONTENT_END_MARKER), listOf("电纸书", "翻页", "阅读结束", "内容末尾")))
    }
    add(SettingsSearchEntry("appearance.answerDoubleTapAction", "双击回答动作", "阅读与排版", "设置双击正文后的默认动作。", Account.ReadingSettings(setting = ANSWER_DOUBLE_TAP_ACTION_PREFERENCE_KEY), listOf("双击")))
    add(
        SettingsSearchEntry(
            "appearance.bottomBar",
            "底部导航栏",
            "导航与交互",
            "启动页、导航显示页面和顺序；底栏内拖动只切换标签，不移动底栏或改变顺序。",
            Account.NavigationSettings(setting = APPEARANCE_SETTINGS_BOTTOM_BAR_SECTION_KEY),
            listOf(
                "启动默认页面",
                "底部栏",
                "主页",
                "首页",
                "关注",
                "日报",
                "热榜",
                "历史",
                "浏览历史",
                "收藏",
                "收藏夹",
                "账号",
                "回到顶部",
                "双击刷新",
                "自动隐藏底栏",
            ),
        ),
    )
    add(SettingsSearchEntry("appearance.collectionDirectBrowse", "收藏直达浏览", "导航与交互", "从底栏收藏夹直接浏览收藏内容，支持切换收藏夹、顺序浏览与随机浏览。", Account.NavigationSettings(setting = COLLECTION_DIRECT_BROWSE_PREFERENCE_KEY), listOf("收藏夹", "顺序浏览", "随机浏览")))
    add(SettingsSearchEntry("appearance.shareAction", "分享操作", "导航与交互", "设置分享按钮默认复制、分享或询问。", Account.NavigationSettings(setting = "shareAction"), listOf("复制链接", "分享链接", "分享按钮")))
    add(SettingsSearchEntry("appearance.showSearchHotSearch", "搜索界面显示热搜", "导航与交互", "控制搜索页空查询时是否展示热搜。", Account.NavigationSettings(setting = "showSearchHotSearch"), listOf("热搜")))
    add(SettingsSearchEntry("appearance.showSearchHistory", "记录并显示搜索历史", "导航与交互", "控制搜索历史记录和展示。", Account.NavigationSettings(setting = "showSearchHistory"), listOf("搜索记录", "历史记录", "清除搜索历史")))
    add(SettingsSearchEntry("appearance.predictiveBack", "预测性返回", "导航与交互", "控制 Android 预测性返回手势是否驱动页面转场。", Account.NavigationSettings(setting = "enable_predictive_back"), listOf("返回手势", "预测式返回")))
    add(SettingsSearchEntry("navigation.pageTransition", "页面转场", "导航与交互", "选择侧滑或 AOSP 风格；关闭预测性返回即可关闭动画。", Account.NavigationSettings(setting = PAGE_TRANSITION_PREFERENCE_KEY), listOf("动画", "转场", "侧滑", "AOSP"), requiresPredictiveBack))
    add(SettingsSearchEntry("appearance.appBlur", "悬浮玻璃", "外观", "弹层、弹窗打开时模糊底层内容。", Account.AppearanceSettings(setting = APP_BLUR_PREFERENCE_KEY), listOf("模糊", "玻璃", "背景")))
    add(SettingsSearchEntry("appearance.floatingBottomBar", "悬浮底栏", "外观", "底栏以圆角浮层显示；关闭后，足够宽的横屏窗口使用侧边导航，竖屏或窄窗口保持贴底。底栏内拖动仅切换标签，不移动底栏。", Account.AppearanceSettings(setting = FLOATING_BOTTOM_BAR_PREFERENCE_KEY), listOf("底栏", "浮动", "悬浮", "导航栏")))
    add(SettingsSearchEntry("reading.contentAndHome", "排版", "阅读与排版", "调整字号、行高、段落间距与正文排版引擎，或恢复默认排版。", Account.ReadingSettings(setting = "contentAndHome"), listOf("正文排版", "提椠", "Markdown", "排版恢复默认", "重置排版")))
    if (isTiqianMarkdownRendererAvailable) {
        add(SettingsSearchEntry("reading.tiqianMarkdown", "正文：使用提椠 Markdown 渲染器", "阅读与排版", "改进正文两端对齐、中西混排及代码、表格、公式与脚注排版。", Account.ReadingSettings(setting = TIQIAN_MARKDOWN_PREFERENCE_KEY), listOf("提椠", "Markdown", "正文渲染", "排版引擎")))
        add(SettingsSearchEntry("reading.tiqianMathFont", "正文：使用非衬线数学字体", "阅读与排版", "默认使用 Lete Sans Math；关闭后使用 STIX Two Math。", Account.ReadingSettings(setting = TIQIAN_MATH_FONT_PREFERENCE_KEY), listOf("公式", "数学字体", "Lete", "STIX"), requiresTiqianMarkdown))
    }
    if (isReadingPlayerSupported) {
        add(SettingsSearchEntry("playback.preview", "当前朗读模板", "朗读与播放", "预览一条内容会按什么顺序朗读。", Account.ReadingPlaybackSettings(setting = READING_PLAYBACK_TEMPLATE_PREVIEW_KEY), listOf("朗读预览", "模板")))
        add(SettingsSearchEntry("playback.fields", "朗读内容", "朗读与播放", "勾选要朗读的字段并调整顺序。", Account.ReadingPlaybackSettings(setting = READING_PLAYBACK_FIELD_ORDER_KEY), listOf("朗读", "标题", "作者", "时间", "顺序")))
        add(SettingsSearchEntry("playback.publishedTimeMode", "时间形式", "朗读与播放", "朗读绝对发布时间或距今的相对时间。", Account.ReadingPlaybackSettings(setting = READING_PLAYBACK_PUBLISHED_TIME_MODE_KEY), listOf("发布时间", "相对时间", "绝对时间"), requiresPublishedAt))
        add(SettingsSearchEntry("playback.relativeTimePrecision", "相对时间精度", "朗读与播放", "相对时间保留到秒、时或天。", Account.ReadingPlaybackSettings(setting = READING_PLAYBACK_RELATIVE_TIME_PRECISION_KEY), listOf("发布时间", "相对时间", "精度"), requiresRelativeTime))
        add(SettingsSearchEntry("playback.comments", "朗读评论数量", "朗读与播放", "每条内容最多朗读多少条评论，0 表示不加载评论。", Account.ReadingPlaybackSettings(setting = READING_PLAYBACK_COMMENT_COUNT_KEY), listOf("评论", "朗读"), requiresReadingComments))
        add(SettingsSearchEntry("playback.commentAuthor", "朗读评论作者", "朗读与播放", "朗读评论时是否带上作者名。", Account.ReadingPlaybackSettings(setting = READING_PLAYBACK_COMMENT_AUTHOR_KEY), listOf("评论", "作者"), requiresCommentDetails))
        add(SettingsSearchEntry("playback.commentOrder", "评论排序", "朗读与播放", "按热度或发布时间读取评论。", Account.ReadingPlaybackSettings(setting = READING_PLAYBACK_COMMENT_ORDER_KEY), listOf("评论", "排序", "热度"), requiresCommentDetails))
        add(SettingsSearchEntry("playback.queue", "单次队列上限", "朗读与播放", "连续朗读时一次最多排入多少条内容。", Account.ReadingPlaybackSettings(setting = READING_PLAYBACK_QUEUE_LIMIT_KEY), listOf("队列", "连续播放", "朗读")))
        add(SettingsSearchEntry("playback.transition", "内容之间的过渡文本", "朗读与播放", "两条内容之间朗读的提示文字。", Account.ReadingPlaybackSettings(setting = READING_PLAYBACK_TRANSITION_KEY), listOf("过渡", "条目过渡", "朗读")))
    }

    add(SettingsSearchEntry("recommend.recommendationMode", "推荐算法", "推荐与信息流", "选择 Web、Android、本地或混合推荐。", Account.FeedSettings(setting = "recommendationMode"), listOf("推荐来源", "Web 推荐", "Android 推荐", "本地推荐", "混合推荐")))
    add(SettingsSearchEntry("recommend.loginForRecommendation", "推荐内容时登录", "推荐与信息流", "获取推荐内容时是否带登录凭证。", Account.FeedSettings(setting = "loginForRecommendation")))
    add(SettingsSearchEntry("recommend.qualityFilterMode", "质量屏蔽", "内容过滤", "选择不屏蔽、显示屏蔽规则或直接隐藏低质量内容。", Account.RecommendSettings(setting = QUALITY_FILTER_MODE_PREFERENCE_KEY), listOf("低质量", "屏蔽规则", "隐藏")))
    add(SettingsSearchEntry("recommend.enableContentFilter", "启用智能内容过滤", "内容过滤", "过滤重复出现但未点击内容。", Account.RecommendSettings(setting = "enableContentFilter")))
    add(SettingsSearchEntry("recommend.filterFollowedUserContent", "过滤已关注用户内容", "内容过滤", "控制是否过滤已关注用户的内容。", Account.RecommendSettings(setting = "filterFollowedUserContent")))
    add(SettingsSearchEntry("recommend.enableKeywordBlocking", "启用关键词屏蔽", "内容过滤", "按关键词过滤内容。", Account.RecommendSettings(setting = "enableKeywordBlocking"), listOf("关键词", "屏蔽词")))
    add(SettingsSearchEntry("recommend.enableUserBlocking", "启用用户屏蔽", "内容过滤", "按用户过滤内容。", Account.RecommendSettings(setting = "enableUserBlocking"), listOf("作者屏蔽", "屏蔽用户")))
    add(SettingsSearchEntry("recommend.enableTopicBlocking", "启用主题屏蔽", "内容过滤", "按命中主题过滤内容。", Account.RecommendSettings(setting = "enableTopicBlocking"), listOf("话题屏蔽", "屏蔽话题")))
    add(SettingsSearchEntry("recommend.topicBlockingThreshold", "主题屏蔽阈值", "内容过滤", "设置命中多少个被屏蔽主题后才过滤内容。", Account.RecommendSettings(setting = "topicBlockingThreshold"), listOf("主题阈值"), requiresTopicBlocking))
    add(SettingsSearchEntry("recommend.blockZhihuAdPlatform", "屏蔽知乎广告平台内容", "内容过滤", "过滤知乎广告平台推广内容。", Account.RecommendSettings(setting = "blockZhihuAdPlatform"), listOf("广告")))
    add(SettingsSearchEntry("recommend.blockZhihuSchool", "屏蔽知乎学堂内容", "内容过滤", "过滤知乎学堂和教育推广内容。", Account.RecommendSettings(setting = "blockZhihuSchool"), listOf("学堂")))
    add(SettingsSearchEntry("recommend.blockWeChatOfficialAccount", "屏蔽微信公众号文章", "内容过滤", "过滤微信公众号外链内容。", Account.RecommendSettings(setting = "blockWeChatOfficialAccount"), listOf("微信")))
    add(SettingsSearchEntry("recommend.blockPaidContent", "屏蔽知乎盐选付费内容", "内容过滤", "过滤会员付费内容。", Account.RecommendSettings(setting = "blockPaidContent"), listOf("盐选", "付费")))
    add(SettingsSearchEntry("recommend.reverseBlock", "反向屏蔽", "内容过滤", "只保留广告和付费内容。", Account.RecommendSettings(setting = "reverseBlock")))
    add(
        SettingsSearchEntry(
            id = "recommend.blocklist",
            title = "管理屏蔽列表",
            section = "内容过滤",
            description = "分别管理全部内容、问题和回答的关键词，以及用户和主题屏蔽。",
            destination = Account.RecommendSettings.Blocklist,
            keywords = listOf("黑名单", "屏蔽词", "问题关键词", "回答关键词", "作用范围", "屏蔽用户", "屏蔽话题"),
        ),
    )
    add(
        SettingsSearchEntry(
            id = "recommend.blockedHistory",
            title = "屏蔽记录",
            section = "内容过滤",
            description = "查看最近被过滤的内容。",
            destination = Account.RecommendSettings.BlockedFeedHistory,
            keywords = listOf("过滤记录"),
        ),
    )

    add(SettingsSearchEntry("system.githubToken", "GitHub Token", "系统与更新", "配置更新检查时使用的 GitHub API 令牌。", Account.SystemAndUpdateSettings(setting = "githubToken"), listOf("限速", "更新检查", "令牌")))
    add(SettingsSearchEntry("system.checkNightlyUpdates", "检查 Nightly 版本更新", "系统与更新", "检查每日构建版本。", Account.SystemAndUpdateSettings(setting = "checkNightlyUpdates"), listOf("每日构建")))
    add(SettingsSearchEntry("system.allowTelemetry", "允许发送遥测统计数据", "系统与更新", "控制匿名使用统计。", Account.SystemAndUpdateSettings(setting = "allowTelemetry"), listOf("统计", "隐私", "数据收集", "使用数据")))
    add(SettingsSearchEntry("system.aigcMarking", "启用 AIGC 标记", "系统与更新", "开启后可查看其他用户对内容是否疑似 AIGC 的标记。", Account.SystemAndUpdateSettings(setting = AIGC_MARKING_ENABLED_PREFERENCE_KEY), listOf("AI", "AIGC")))
    add(SettingsSearchEntry("system.reminder", "防沉迷提醒", "系统与更新", "设置连续使用提醒的间隔。", Account.SystemAndUpdateSettings(setting = CONTINUOUS_USAGE_REMINDER_INTERVAL_MINUTES_KEY), listOf("连续使用", "休息提醒")))
    if (platformName == "macOS") {
        add(
            SettingsSearchEntry(
                "system.macosQuitOnWindowClose",
                "关闭窗口时退出应用",
                "系统与更新",
                "关闭最后一个窗口时同时退出 macOS 应用；默认关闭。",
                Account.SystemAndUpdateSettings(setting = MACOS_QUIT_ON_WINDOW_CLOSE_PREFERENCE_KEY),
                listOf("macOS", "退出应用", "关闭窗口", "退出程序"),
            ),
        )
    }

    add(SettingsSearchEntry("notification.autoMarkAsRead", "打开通知自动已读", "通知设置", "进入通知页后自动标记当前批次为已读。", Notification.NotificationSettings(setting = "autoMarkAsRead"), listOf("已读", "标记已读")))
    add(SettingsSearchEntry("notification.unreadBadge", "显示未读红点", "通知设置", "控制首页和账号入口的未读角标。", Notification.NotificationSettings(setting = "unreadBadge"), listOf("角标", "红点", "未读数")))
    add(SettingsSearchEntry("notification.resetReadRecords", "重置已读记录", "通知设置", "清除首页公告与作者想法推送的已读记录，执行前需要确认。", Notification.NotificationSettings(setting = "resetReadRecords"), listOf("已读", "清除已读", "重置", "公告", "作者想法")))
    val systemNotificationSummary = if (platformName == "Android") {
        "仅本机当前登录账号，默认关闭；联网时至少每 15 分钟检查，并非实时推送。"
    } else {
        "此平台暂不支持系统通知，请在应用内通知页查看消息。"
    }
    add(SettingsSearchEntry("notification.systemNotifications", "系统消息通知", "通知设置", systemNotificationSummary, Notification.NotificationSettings(setting = "systemNotifications"), listOf("系统通知", "后台通知", "通知权限", "通知通道")))
    NotificationType.entries.forEach { type ->
        add(SettingsSearchEntry("notification.system.${type.name}", type.displayName, "通知设置 / 系统消息通知", systemNotificationSummary, Notification.NotificationSettings(setting = "systemNotification_${type.name}"), listOf("系统通知", "订阅", "后台通知")))
    }
    add(SettingsSearchEntry("notification.displayInAppNotifications", "应用内显示", "通知设置", "控制通知页展示哪些通知类型。", Notification.NotificationSettings(setting = "displayInAppNotifications"), NotificationType.entries.map { it.displayName }))

    if (miuixThemeAvailable) {
        add(SettingsSearchEntry("appearance.uiThemeStyle", "界面皮肤", "外观", "在 Material 3 Expressive 与 Miuix 之间切换。", Account.AppearanceSettings(setting = THEME_STYLE_PREFERENCE_KEY), listOf("皮肤", "Miuix", "HyperOS", "Material", "M3E")))
        add(SettingsSearchEntry("appearance.miuixStyle", "HyperOS 风格", "外观", "在 HyperOS 3 与 HyperOS 4 之间切换；HyperOS 4 的玻璃效果需开启悬浮玻璃且平台支持渲染，不重置其他偏好。", Account.AppearanceSettings(setting = MIUIX_STYLE_PREFERENCE_KEY), listOf("Miuix", "OS3", "OS4", "OS 3", "OS 4", "HyperOS3", "HyperOS4", "HyperOS 3", "HyperOS 4", "子风格"), requiresMiuixSkin))
    }
    add(SettingsSearchEntry("appearance.customColor", "自定义主题色", "外观", "选择自定义配色的基础强调色。", Account.AppearanceSettings(setting = APPEARANCE_SETTINGS_CUSTOM_COLOR_KEY), listOf("主题色", "强调色", "颜色"), requiresCustomColor))
    add(SettingsSearchEntry("appearance.backgroundColor", "自定义背景颜色", "外观", "修改浅色或深色模式的页面背景色。", Account.AppearanceSettings(setting = APPEARANCE_SETTINGS_BACKGROUND_COLOR_KEY), listOf("背景色", "背景")))
    add(SettingsSearchEntry("appearance.browserToolbarColor", "唤起浏览器主题色", "外观", "设置应用内浏览器的工具栏颜色。", Account.AppearanceSettings(setting = APPEARANCE_SETTINGS_BROWSER_COLOR_KEY), listOf("浏览器", "工具栏颜色")))
    add(SettingsSearchEntry("appearance.showCreateFab", "显示发布按钮", "外观", "在主页右下角显示创作/发布按钮。", Account.AppearanceSettings(setting = "showCreateFab"), listOf("发布", "创作", "加号")))
    add(SettingsSearchEntry("appearance.fabOpacity", "悬浮按钮透明度", "外观", "控制所有悬浮按钮的透明度。", Account.AppearanceSettings(setting = PREF_FAB_OPACITY), listOf("悬浮按钮", "透明度", "FAB")))
    add(SettingsSearchEntry("navigation.startDestination", "应用启动默认页面", "导航与交互", "选择启动后先打开的底栏页面。", Account.NavigationSettings(setting = START_DESTINATION_PREFERENCE_KEY), listOf("启动页", "默认页面")))
    add(SettingsSearchEntry("navigation.tapScrollToTop", "点击底部导航栏回到顶部/刷新", "导航与交互", "点当前页的底栏按钮回到顶部，已在顶部时刷新。", Account.NavigationSettings(setting = "bottomBarTapScrollToTop"), listOf("回到顶部", "双击刷新")))
    add(SettingsSearchEntry("navigation.autoHideBottomBar", "滚动时自动隐藏底部导航栏", "导航与交互", "上划时隐藏底部导航栏，下划时重新显示。", Account.NavigationSettings(setting = "autoHideBottomBar"), listOf("隐藏底栏", "自动隐藏")))
    add(SettingsSearchEntry("reading.lineHeight", "行高", "阅读与排版", "调整正文行距。", Account.ReadingSettings(setting = PREF_LINE_HEIGHT), listOf("行距", "排版")))
    add(SettingsSearchEntry("reading.blockSpacing", "段间距", "阅读与排版", "调整正文段落之间的距离。", Account.ReadingSettings(setting = PREF_BLOCK_SPACING), listOf("段落间距", "排版")))
    if (isAnswerSwipeSupported) {
        add(SettingsSearchEntry("reading.answerSwitchSensitivity", "回答切换灵敏度", "阅读与排版", "调整切换回答需要的滑动距离。", Account.ReadingSettings(setting = ANSWER_SWITCH_SENSITIVITY_PREFERENCE_KEY), listOf("灵敏度", "切换回答", "手势"), requiresAnswerSwitch))
    }
    add(SettingsSearchEntry("recommend.autoRefreshHomeOnStartup", "启动时自动刷新首页", "推荐与信息流", "打开应用时自动刷新首页推荐。", Account.FeedSettings(setting = AUTO_REFRESH_HOME_ON_STARTUP_PREFERENCE_KEY), listOf("启动刷新", "首页刷新")))
    listOf(
        ANSWER_VOTEUP_THRESHOLD_PREFERENCE_KEY to "回答最低赞数",
        ARTICLE_VOTEUP_THRESHOLD_PREFERENCE_KEY to "文章最低赞数",
        ARTICLE_FOLLOWERS_THRESHOLD_PREFERENCE_KEY to "文章最低粉丝数",
        VIDEO_VOTE_THRESHOLD_PREFERENCE_KEY to "视频最低赞数",
        VIDEO_FOLLOWERS_THRESHOLD_PREFERENCE_KEY to "视频最低粉丝数",
        QUESTION_ANSWER_THRESHOLD_PREFERENCE_KEY to "问题最低回答数",
        QUESTION_FOLLOWERS_THRESHOLD_PREFERENCE_KEY to "问题最低关注数",
    ).forEach { (key, title) ->
        add(SettingsSearchEntry("recommend.threshold.$key", title, "内容过滤", "质量过滤阈值，低于它的内容会被过滤。", Account.RecommendSettings(setting = key), listOf("质量过滤", "阈值", "低质量"), requiresQualityFilter))
    }
    add(SettingsSearchEntry("recommend.clearExposureRecords", "清除曝光记录", "内容过滤", "清空智能过滤用到的曝光记录。", Account.RecommendSettings(setting = "clearExposureRecords"), listOf("曝光", "智能过滤", "重置")))
    add(
        SettingsSearchEntry(
            id = "account.identity",
            title = "账号与身份",
            section = "账号与安全",
            description = "切换账号、创建身份或移除已保存账号。",
            destination = Account.IdentityManagement,
            keywords = listOf("切换账号", "身份", "多账号", "移除账号"),
        ),
    )

    add(
        SettingsSearchEntry(
            id = "developer.page",
            title = "账号与登录维护",
            section = "账号与安全",
            description = "验证登录、刷新 Token、导入 Cookie 与首次 403 脱敏反馈。",
            destination = Account.DeveloperSettings,
            keywords = listOf("Cookie", "导入 Cookie", "验证登录", "刷新 Token", "刷新Token", "登录维护", "403", "unhuman", "网络诊断", "网络请求诊断", "安全验证", "人工验证"),
        ),
    )
    add(
        SettingsSearchEntry(
            id = "about.webdav",
            title = "WebDAV 同步",
            section = "关于",
            description = "把屏蔽列表和设置备份到自己的 WebDAV，或从中恢复。",
            destination = Account.WebDavSync,
            keywords = listOf("备份", "恢复", "同步", "坚果云"),
        ),
    )
    add(
        SettingsSearchEntry(
            id = "about.webdav.checkConnection",
            title = "检查连接",
            section = "WebDAV 同步",
            description = "检查 WebDAV 目录地址与登录凭据是否可用。",
            destination = Account.WebDavSync,
            keywords = listOf("WebDAV", "连接", "坚果云"),
        ),
    )
    add(
        SettingsSearchEntry(
            id = "about.licenses",
            title = "开源许可",
            section = "关于",
            description = "查看第三方组件许可证。",
            destination = Account.OpenSourceLicenses,
            keywords = listOf("许可证"),
        ),
    )
}

@Composable
fun SettingsSearchScreen() {
    val navigator = LocalNavigator.current
    val settings = rememberSettingsStore()
    val queryState = rememberTextFieldState()
    val query = queryState.text.toString()
    val groups = remember(query) {
        settingsSearchEntries.filter { entry -> entry.matches(query) }.groupBy { it.section }
    }
    val listState = rememberSaveable(query, saver = LazyListState.Saver) { LazyListState() }
    val pageTurnTarget = rememberPageTurnTarget(listState, enabled = true)
    val background = appGroupedPageBackground()

    AppScaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = background,
        topBar = {
            Column(Modifier.background(background)) {
                AppCompactTopBar(
                    title = "搜索设置项",
                    onBack = {
                        queryState.clearText()
                        navigator.onNavigateBack()
                    },
                    backTag = SETTINGS_SEARCH_BACK_BUTTON_TAG,
                    containerColor = background,
                )
                SettingsContent {
                    AppSearchField(
                        state = queryState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space8)
                            .testTag(SETTINGS_SEARCH_INPUT_TAG),
                        placeholder = "搜索设置名称或关键词",
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = if (settingSkinIsMiuix()) Modifier else Modifier.width(AppDimens.SearchBarHeightM3e).wrapContentSize(),
                            )
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .pageTurnViewportWithGuide(pageTurnTarget)
                .testTag(SETTINGS_SEARCH_RESULTS_TAG),
            state = listState,
            contentPadding = innerPadding,
        ) {
            if (groups.isEmpty()) {
                item {
                    AppEmptyState(
                        title = "没有找到相关设置",
                        description = "换个关键词试试，例如“底栏”“字号”“屏蔽”。",
                        icon = Icons.Default.Search,
                    )
                }
            } else {
                groups.forEach { (section, entries) ->
                    item(key = section) {
                        SettingsContent {
                            SettingItemGroup(title = section) {
                                entries.forEach { entry -> SettingsSearchResult(entry, settings, navigator.onNavigate) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSearchResult(
    entry: SettingsSearchEntry,
    settings: SettingsStore,
    onNavigate: (NavDestination) -> Unit,
) {
    val unmet = entry.requirement?.takeUnless { it.isMet(settings) }
    val destination = unmet?.parent?.invoke() ?: entry.destination
    SettingItem(
        modifier = Modifier.testTag("settingsSearch.result.${entry.id}"),
        title = { Text(entry.title) },
        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
        description = {
            Column {
                Text(entry.description)
                if (unmet != null) {
                    Text(text = unmet.hint(), color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        onClick = { onNavigate(destination) },
        endAction = {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
