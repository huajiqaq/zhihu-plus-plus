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
import com.github.zly2006.zhihu.i18n.APP_LANGUAGE_KEY
import com.github.zly2006.zhihu.i18n.AppLanguage
import com.github.zly2006.zhihu.i18n.AppLocale
import com.github.zly2006.zhihu.i18n.SettingsSearchStrings
import com.github.zly2006.zhihu.i18n.SettingsStrings
import com.github.zly2006.zhihu.markdown.isTiqianMarkdownRendererAvailable
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Notification
import com.github.zly2006.zhihu.notification.NotificationType
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.defaultAnswerSwitchMode
import com.github.zly2006.zhihu.platform.isAnswerSwipeSupported
import com.github.zly2006.zhihu.platform.isBlocklistNlpSupported
import com.github.zly2006.zhihu.platform.isPageTurnSupported
import com.github.zly2006.zhihu.platform.platformName
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.reading.READING_PREFERENCES_KEY
import com.github.zly2006.zhihu.reading.ReadingPublishedTimeMode
import com.github.zly2006.zhihu.reading.ReadingTemplateField
import com.github.zly2006.zhihu.reading.isReadingPlayerSupported
import com.github.zly2006.zhihu.reading.loadReadingPreferences
import com.github.zly2006.zhihu.theme.AMOLED_THEME_PREFERENCE_KEY
import com.github.zly2006.zhihu.theme.LocalAppTheme
import com.github.zly2006.zhihu.theme.MIUIX_STYLE_PREFERENCE_KEY
import com.github.zly2006.zhihu.theme.THEME_COLOR_SPEC_PREFERENCE_KEY
import com.github.zly2006.zhihu.theme.THEME_PALETTE_STYLE_PREFERENCE_KEY
import com.github.zly2006.zhihu.theme.THEME_STYLE_PREFERENCE_KEY
import com.github.zly2006.zhihu.theme.ThemeMode
import com.github.zly2006.zhihu.theme.ThemeStyle
import com.github.zly2006.zhihu.theme.miuixThemeAvailable
import com.github.zly2006.zhihu.ui.ANSWER_DOUBLE_TAP_ACTION_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.ARTICLE_USE_WEBVIEW_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.AUTO_REFRESH_HOME_ON_STARTUP_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.LocalSharedMainNavigationBarAvailable
import com.github.zly2006.zhihu.ui.components.ANSWER_SWITCH_SENSITIVITY_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.components.AppCompactTopBar
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppEmptyState
import com.github.zly2006.zhihu.ui.components.AppIcon
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

const val SETTINGS_SEARCH_BACK_BUTTON_TAG = "settings_search_back_button"

const val SETTINGS_SEARCH_INPUT_TAG = "settingsSearch.input"
const val SETTINGS_SEARCH_RESULTS_TAG = "settingsSearch.results"

/** 搜索结果依赖的父项。条件不满足时目标行被隐藏或禁用：结果下方写 [hint]，点击跳到 [parent] 并高亮它。 */
private class SettingsSearchRequirement(
    val hint: @Composable () -> String,
    val parent: @Composable () -> NavDestination,
    val isMet: @Composable (SettingsStore) -> Boolean,
)

private enum class SettingsSearchSection(
    val title: () -> String,
) {
    Appearance({ SettingsStrings.appearance }),
    Navigation({ SettingsStrings.navigationInteraction }),
    Reading({ SettingsStrings.readingLayout }),
    Feed({ SettingsStrings.recommendationsFeed }),
    PageTurning({ if (platformName != "Android") SettingsStrings.pageTurning else SettingsStrings.einkPageTurning }),
    Playback({ SettingsStrings.readingPlayback }),
    Filtering({ SettingsStrings.contentFilter }),
    System({ SettingsStrings.systemUpdates }),
    Notifications({ SettingsStrings.notificationSettings }),
    SystemNotifications({ SettingsStrings.systemNotificationSection }),
    AccountSecurity({ SettingsStrings.accountSecurity }),
    About({ SettingsStrings.about }),
    WebDav({ SettingsStrings.webdavSync }),
}

private data class SettingsSearchEntry(
    val id: String,
    val title: String,
    val section: SettingsSearchSection,
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
            append(section.title())
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

private val requiresFloatingBottomBar = SettingsSearchRequirement(
    hint = { SettingsStrings.requiresFloatingBar },
    parent = { Account.AppearanceSettings(setting = FLOATING_BOTTOM_BAR_PREFERENCE_KEY) },
    isMet = { settings -> rememberObservedSetting(settings, FLOATING_BOTTOM_BAR_PREFERENCE_KEY) { getBoolean(FLOATING_BOTTOM_BAR_PREFERENCE_KEY, true) }.value },
)
private val requiresMiuixSkin = SettingsSearchRequirement(
    hint = { SettingsStrings.requiresMiuixSkin },
    parent = { Account.AppearanceSettings(setting = THEME_STYLE_PREFERENCE_KEY) },
    isMet = { LocalAppTheme.current.style == ThemeStyle.MIUIX },
)
private val requiresCustomColor = SettingsSearchRequirement(
    hint = { if (LocalAppTheme.current.style == ThemeStyle.MIUIX) SettingsStrings.requiresMaterialSkin else SettingsStrings.requiresCustomColors },
    parent = { Account.AppearanceSettings(setting = if (LocalAppTheme.current.style == ThemeStyle.MIUIX) THEME_STYLE_PREFERENCE_KEY else "dynamicColor") },
    isMet = { !LocalAppTheme.current.snapshot.useDynamicColor && LocalAppTheme.current.style != ThemeStyle.MIUIX },
)
private val requiresDarkMode = SettingsSearchRequirement(
    hint = { SettingsStrings.darkModeOnly },
    parent = { Account.AppearanceSettings(setting = "nightMode") },
    isMet = { LocalAppTheme.current.snapshot.themeMode != ThemeMode.LIGHT },
)
private val requiresTiqianMarkdown = SettingsSearchRequirement(
    hint = { SettingsStrings.requiresTiqian },
    parent = { Account.ReadingSettings(setting = TIQIAN_MARKDOWN_PREFERENCE_KEY) },
    isMet = { settings -> rememberObservedSetting(settings, TIQIAN_MARKDOWN_PREFERENCE_KEY) { getBoolean(TIQIAN_MARKDOWN_PREFERENCE_KEY, false) }.value },
)
private val requiresAnswerSwitch = SettingsSearchRequirement(
    hint = { SettingsStrings.requiresAnswerSwitch },
    parent = { Account.ReadingSettings(setting = "answerSwitchMode") },
    isMet = { settings -> rememberObservedSetting(settings, "answerSwitchMode") { getString("answerSwitchMode", defaultAnswerSwitchMode) }.value != "off" },
)
private val requiresQualityFilter = SettingsSearchRequirement(
    hint = { SettingsStrings.requiresQualityFilter },
    parent = { Account.RecommendSettings(setting = QUALITY_FILTER_MODE_PREFERENCE_KEY) },
    isMet = { settings -> rememberObservedSetting(settings, QUALITY_FILTER_MODE_PREFERENCE_KEY) { getString(QUALITY_FILTER_MODE_PREFERENCE_KEY, QualityFilterMode.RULES.name) }.value != QualityFilterMode.OFF.name },
)
private val requiresTopicBlocking = SettingsSearchRequirement(
    hint = { SettingsStrings.requiresTopicBlocking },
    parent = { Account.RecommendSettings(setting = "enableTopicBlocking") },
    isMet = { settings -> rememberObservedSetting(settings, "enableTopicBlocking") { getBoolean("enableTopicBlocking", true) }.value },
)
private val requiresPublishedAt = SettingsSearchRequirement(
    hint = { SettingsStrings.requiresPublicationTime },
    parent = { Account.ReadingPlaybackSettings(setting = READING_PLAYBACK_FIELD_ORDER_KEY) },
    isMet = { settings -> ReadingTemplateField.PublishedAt in rememberObservedSetting(settings, READING_PREFERENCES_KEY) { loadReadingPreferences(this) }.value.enabledFields },
)
private val requiresRelativeTime = SettingsSearchRequirement(
    hint = { SettingsStrings.requiresRelativeTime },
    parent = { Account.ReadingPlaybackSettings(setting = READING_PLAYBACK_PUBLISHED_TIME_MODE_KEY) },
    isMet = { settings ->
        rememberObservedSetting(settings, READING_PREFERENCES_KEY) { loadReadingPreferences(this) }.value.let {
            ReadingTemplateField.PublishedAt in it.enabledFields && it.publishedTimeMode == ReadingPublishedTimeMode.Relative
        }
    },
)
private val requiresReadingComments = SettingsSearchRequirement(
    hint = { SettingsStrings.requiresReadComments },
    parent = { Account.ReadingPlaybackSettings(setting = READING_PLAYBACK_FIELD_ORDER_KEY) },
    isMet = { settings -> ReadingTemplateField.Comments in rememberObservedSetting(settings, READING_PREFERENCES_KEY) { loadReadingPreferences(this) }.value.enabledFields },
)
private val requiresCommentDetails = SettingsSearchRequirement(
    hint = { SettingsStrings.requiresCommentDetails },
    parent = { Account.ReadingPlaybackSettings(setting = READING_PLAYBACK_COMMENT_COUNT_KEY) },
    isMet = { settings -> rememberObservedSetting(settings, READING_PREFERENCES_KEY) { loadReadingPreferences(this) }.value.shouldLoadComments },
)

/** 对外链接只能高亮设置目录登记的键，不能把任意参数当成设置值写入地址。 */
val linkableSettingsDestinations: Set<NavDestination> by lazy { buildSettingsSearchEntries().mapTo(mutableSetOf()) { it.destination } }

/** 别名跨语言检索，显示文案仍跟随当前语言，不临时切换全局语言状态。 */
private fun searchAliases(values: (AppLanguage) -> List<String>): List<String> = AppLanguage.entries.flatMap(values).distinct()

private fun NotificationType.searchAliases(): List<String> = when (this) {
    NotificationType.LIKE_ANSWER -> searchAliases(SettingsSearchStrings::notificationLikeAnswer)
    NotificationType.LIKE_COMMENT -> searchAliases(SettingsSearchStrings::notificationLikeComment)
    NotificationType.REPLY_COMMENT -> searchAliases(SettingsSearchStrings::notificationReplyComment)
    NotificationType.INVITE_ANSWER -> searchAliases(SettingsSearchStrings::notificationInviteAnswer)
}

private fun buildSettingsSearchEntries(sharedMainNavigationBarAvailable: Boolean = true) = buildList {
    add(
        SettingsSearchEntry(
            id = "appearance.appLanguage",
            title = SettingsStrings.appLanguage,
            section = SettingsSearchSection.Appearance,
            description = SettingsStrings.appLanguageDescription,
            destination = Account.AppearanceSettings(setting = APP_LANGUAGE_KEY),
            keywords = searchAliases(SettingsSearchStrings::appearanceAppLanguage),
        ),
    )
    add(SettingsSearchEntry("appearance.bottomSheetCorners", SettingsStrings.squarePopups, SettingsSearchSection.Appearance, SettingsStrings.squarePopupsDescription, Account.AppearanceSettings(setting = DISABLE_BOTTOM_SHEET_ROUNDED_CORNERS_PREFERENCE_KEY), searchAliases(SettingsSearchStrings::appearanceBottomSheetCorners)))

    add(SettingsSearchEntry("appearance.nightMode", SettingsStrings.themeMode, SettingsSearchSection.Appearance, SettingsStrings.searchThemeModeDescription, Account.AppearanceSettings(setting = "nightMode"), searchAliases(SettingsSearchStrings::appearanceNightMode)))
    add(SettingsSearchEntry("appearance.dynamicColor", SettingsStrings.searchDynamicColor, SettingsSearchSection.Appearance, SettingsStrings.searchDynamicColorDescription, Account.AppearanceSettings(setting = "dynamicColor"), searchAliases(SettingsSearchStrings::appearanceDynamicColor)))
    add(SettingsSearchEntry("appearance.paletteStyle", SettingsStrings.paletteStyle, SettingsSearchSection.Appearance, SettingsStrings.searchPaletteDescription, Account.AppearanceSettings(setting = THEME_PALETTE_STYLE_PREFERENCE_KEY), searchAliases(SettingsSearchStrings::appearancePaletteStyle)))
    add(SettingsSearchEntry("appearance.colorSpec", SettingsStrings.colorSpecification, SettingsSearchSection.Appearance, SettingsStrings.searchColorSpecDescription, Account.AppearanceSettings(setting = THEME_COLOR_SPEC_PREFERENCE_KEY), searchAliases(SettingsSearchStrings::appearanceColorSpec)))
    add(SettingsSearchEntry("appearance.amoled", SettingsStrings.amoledBlack, SettingsSearchSection.Appearance, SettingsStrings.searchAmoledDescription, Account.AppearanceSettings(setting = AMOLED_THEME_PREFERENCE_KEY), searchAliases(SettingsSearchStrings::appearanceAmoled), requiresDarkMode))
    add(SettingsSearchEntry("appearance.landscapeListDetail", SettingsStrings.landscapeListDetail, SettingsSearchSection.Navigation, landscapeListDetailDescription, Account.NavigationSettings(setting = LANDSCAPE_LIST_DETAIL_PREFERENCE_KEY), searchAliases(SettingsSearchStrings::appearanceLandscapeListDetail)))
    add(SettingsSearchEntry("appearance.fontScale", SettingsStrings.fontSize, SettingsSearchSection.Reading, SettingsStrings.searchFontSizeDescription, Account.ReadingSettings(setting = "fontScale"), searchAliases(SettingsSearchStrings::appearanceFontScale)))
    add(SettingsSearchEntry("appearance.feedCardStyle", SettingsStrings.feedCardStyle, SettingsSearchSection.Appearance, SettingsStrings.searchFeedStyleDescription, Account.AppearanceSettings(setting = FEED_CARD_STYLE_PREFERENCE_KEY), searchAliases(SettingsSearchStrings::appearanceFeedCardStyle)))
    add(SettingsSearchEntry("appearance.showFeedThumbnail", SettingsStrings.showFeedThumbnails, SettingsSearchSection.Feed, SettingsStrings.searchFeedThumbnailsDescription, Account.FeedSettings(setting = "showFeedThumbnail"), searchAliases(SettingsSearchStrings::appearanceShowFeedThumbnail)))
    add(SettingsSearchEntry("appearance.showRefreshFab", SettingsStrings.showRefreshButton, SettingsSearchSection.Feed, SettingsStrings.showRefreshButtonDescription, Account.FeedSettings(setting = "showRefreshFab"), searchAliases(SettingsSearchStrings::appearanceShowRefreshFab)))
    if (isLegacyWebViewSupported) {
        add(
            SettingsSearchEntry(
                "appearance.webviewRender",
                SettingsStrings.legacyWebview,
                SettingsSearchSection.Reading,
                SettingsStrings.searchLegacyWebviewDescription,
                Account.ReadingSettings(setting = ARTICLE_USE_WEBVIEW_PREFERENCE_KEY),
                searchAliases(SettingsSearchStrings::appearanceWebviewRender),
            ),
        )
    }
    add(SettingsSearchEntry("appearance.titleAutoHide", SettingsStrings.autoHideAnswerTitle, SettingsSearchSection.Reading, SettingsStrings.searchHideTitleDescription, Account.ReadingSettings(setting = "titleAutoHide"), searchAliases(SettingsSearchStrings::appearanceTitleAutoHide)))
    add(SettingsSearchEntry("appearance.autoHideArticleBottomBar", SettingsStrings.autoHideAnswerToolbar, SettingsSearchSection.Reading, SettingsStrings.searchHideToolbarDescription, Account.ReadingSettings(setting = "autoHideArticleBottomBar"), searchAliases(SettingsSearchStrings::appearanceAutoHideArticleBottomBar)))
    add(SettingsSearchEntry("appearance.buttonSkipAnswer", SettingsStrings.nextAnswerButton, SettingsSearchSection.Reading, SettingsStrings.nextAnswerButtonDescription, Account.ReadingSettings(setting = "buttonSkipAnswer"), searchAliases(SettingsSearchStrings::appearanceButtonSkipAnswer)))
    add(SettingsSearchEntry("appearance.pinAnswerDate", SettingsStrings.pinAnswerDate, SettingsSearchSection.Reading, SettingsStrings.searchPinDateDescription, Account.ReadingSettings(setting = "pinAnswerDate"), searchAliases(SettingsSearchStrings::appearancePinAnswerDate)))
    if (isAnswerSwipeSupported) {
        add(SettingsSearchEntry("appearance.answerSwitchMode", SettingsStrings.answerSwitchGesture, SettingsSearchSection.Reading, SettingsStrings.searchAnswerSwitchDescription, Account.ReadingSettings(setting = "answerSwitchMode"), searchAliases(SettingsSearchStrings::appearanceAnswerSwitchMode)))
    }
    if (isPageTurnSupported) {
        if (platformName == "Android") {
            add(SettingsSearchEntry("appearance.pageTurnVolume", SettingsStrings.volumeKeyPageTurn, SettingsSearchSection.PageTurning, SettingsStrings.searchVolumePageTurnDescription, Account.PageTurnSettings(setting = PREF_VOLUME_KEY_PAGE_TURN), searchAliases(SettingsSearchStrings::appearancePageTurnVolume)))
        }
        add(SettingsSearchEntry("appearance.pageTurnSwitchAnswer", SettingsStrings.pageTurnSwitchAnswer, SettingsSearchSection.PageTurning, SettingsStrings.searchPageTurnAnswerDescription, Account.PageTurnSettings(setting = PREF_PAGE_TURN_SWITCH_ANSWER), searchAliases(SettingsSearchStrings::appearancePageTurnSwitchAnswer)))
        add(SettingsSearchEntry("appearance.pageTurnFab", SettingsStrings.showPageTurnButton, SettingsSearchSection.PageTurning, SettingsStrings.showPageTurnButtonDescription, Account.PageTurnSettings(setting = PREF_SHOW_PAGE_TURN_FAB), searchAliases(SettingsSearchStrings::appearancePageTurnFab)))
        add(SettingsSearchEntry("appearance.pageTurnDistance", SettingsStrings.pageTurnDistance, SettingsSearchSection.PageTurning, SettingsStrings.searchPageTurnDistanceDescription, Account.PageTurnSettings(setting = PREF_PAGE_TURN_PERCENT), searchAliases(SettingsSearchStrings::appearancePageTurnDistance)))
        add(SettingsSearchEntry("appearance.pageTurnGuide", SettingsStrings.showPageTurnGuide, SettingsSearchSection.PageTurning, SettingsStrings.searchPageTurnGuideDescription, Account.PageTurnSettings(setting = PREF_SHOW_PAGE_TURN_GUIDE), searchAliases(SettingsSearchStrings::appearancePageTurnGuide)))
        add(SettingsSearchEntry("appearance.contentEndMarker", SettingsStrings.showContentEndMarker, SettingsSearchSection.PageTurning, SettingsStrings.searchContentEndDescription, Account.PageTurnSettings(setting = PREF_SHOW_CONTENT_END_MARKER), searchAliases(SettingsSearchStrings::appearanceContentEndMarker)))
    }
    add(SettingsSearchEntry("appearance.answerDoubleTapAction", SettingsStrings.answerDoubleTap, SettingsSearchSection.Reading, SettingsStrings.searchDoubleTapDescription, Account.ReadingSettings(setting = ANSWER_DOUBLE_TAP_ACTION_PREFERENCE_KEY), searchAliases(SettingsSearchStrings::appearanceAnswerDoubleTapAction)))
    add(
        SettingsSearchEntry(
            "appearance.bottomBar",
            SettingsStrings.bottomNavigationBar,
            SettingsSearchSection.Navigation,
            SettingsStrings.searchBottomBarDescription,
            Account.NavigationSettings(setting = APPEARANCE_SETTINGS_BOTTOM_BAR_SECTION_KEY),
            searchAliases(SettingsSearchStrings::appearanceBottomBar),
        ),
    )
    add(SettingsSearchEntry("appearance.shareAction", SettingsStrings.shareAction, SettingsSearchSection.Navigation, SettingsStrings.searchShareActionDescription, Account.NavigationSettings(setting = "shareAction"), searchAliases(SettingsSearchStrings::appearanceShareAction)))
    add(SettingsSearchEntry("appearance.showSearchHotSearch", SettingsStrings.showHotSearches, SettingsSearchSection.Navigation, SettingsStrings.searchHotSearchesDescription, Account.NavigationSettings(setting = "showSearchHotSearch"), searchAliases(SettingsSearchStrings::appearanceShowSearchHotSearch)))
    add(SettingsSearchEntry("appearance.showSearchHistory", SettingsStrings.recordSearchHistory, SettingsSearchSection.Navigation, SettingsStrings.searchHistoryDescription, Account.NavigationSettings(setting = "showSearchHistory"), searchAliases(SettingsSearchStrings::appearanceShowSearchHistory)))
    add(SettingsSearchEntry("navigation.pageTransition", SettingsStrings.pageTransition, SettingsSearchSection.Navigation, SettingsStrings.searchPageTransitionDescription, Account.NavigationSettings(setting = PAGE_TRANSITION_PREFERENCE_KEY), searchAliases(SettingsSearchStrings::navigationPageTransition)))
    add(SettingsSearchEntry("appearance.appBlur", SettingsStrings.floatingGlass, SettingsSearchSection.Appearance, SettingsStrings.searchFloatingGlassDescription, Account.AppearanceSettings(setting = APP_BLUR_PREFERENCE_KEY), searchAliases(SettingsSearchStrings::appearanceAppBlur)))
    if (sharedMainNavigationBarAvailable) {
        add(SettingsSearchEntry("appearance.floatingBottomBar", SettingsStrings.floatingBottomBar, SettingsSearchSection.Appearance, SettingsStrings.searchFloatingBarDescription, Account.AppearanceSettings(setting = FLOATING_BOTTOM_BAR_PREFERENCE_KEY), searchAliases(SettingsSearchStrings::appearanceFloatingBottomBar)))
        add(SettingsSearchEntry("appearance.floatingBottomBarStyle", SettingsStrings.floatingBottomBarStyle, SettingsSearchSection.Appearance, SettingsStrings.searchFloatingBarStyleDescription, Account.AppearanceSettings(setting = FLOATING_BOTTOM_BAR_STYLE_PREFERENCE_KEY), searchAliases(SettingsSearchStrings::appearanceFloatingBottomBarStyle), requiresFloatingBottomBar))
    }
    add(SettingsSearchEntry("reading.contentAndHome", SettingsStrings.typography, SettingsSearchSection.Reading, SettingsStrings.searchTypographyDescription, Account.ReadingSettings(setting = "contentAndHome"), searchAliases(SettingsSearchStrings::readingContentAndHome)))
    if (isTiqianMarkdownRendererAvailable) {
        add(SettingsSearchEntry("reading.tiqianMarkdown", SettingsStrings.tiqianRenderer, SettingsSearchSection.Reading, SettingsStrings.searchTiqianDescription, Account.ReadingSettings(setting = TIQIAN_MARKDOWN_PREFERENCE_KEY), searchAliases(SettingsSearchStrings::readingTiqianMarkdown)))
        add(SettingsSearchEntry("reading.tiqianMathFont", SettingsStrings.sansMathFont, SettingsSearchSection.Reading, SettingsStrings.searchMathFontDescription, Account.ReadingSettings(setting = TIQIAN_MATH_FONT_PREFERENCE_KEY), searchAliases(SettingsSearchStrings::readingTiqianMathFont), requiresTiqianMarkdown))
    }
    if (isReadingPlayerSupported) {
        add(SettingsSearchEntry("playback.preview", SettingsStrings.readingTemplate, SettingsSearchSection.Playback, SettingsStrings.searchReadingPreviewDescription, Account.ReadingPlaybackSettings(setting = READING_PLAYBACK_TEMPLATE_PREVIEW_KEY), searchAliases(SettingsSearchStrings::playbackPreview)))
        add(SettingsSearchEntry("playback.fields", SettingsStrings.readingContent, SettingsSearchSection.Playback, SettingsStrings.searchReadingFieldsDescription, Account.ReadingPlaybackSettings(setting = READING_PLAYBACK_FIELD_ORDER_KEY), searchAliases(SettingsSearchStrings::playbackFields)))
        add(SettingsSearchEntry("playback.publishedTimeMode", SettingsStrings.timeFormat, SettingsSearchSection.Playback, SettingsStrings.searchTimeFormatDescription, Account.ReadingPlaybackSettings(setting = READING_PLAYBACK_PUBLISHED_TIME_MODE_KEY), searchAliases(SettingsSearchStrings::playbackPublishedTimeMode), requiresPublishedAt))
        add(SettingsSearchEntry("playback.relativeTimePrecision", SettingsStrings.relativeTimePrecision, SettingsSearchSection.Playback, SettingsStrings.searchTimePrecisionDescription, Account.ReadingPlaybackSettings(setting = READING_PLAYBACK_RELATIVE_TIME_PRECISION_KEY), searchAliases(SettingsSearchStrings::playbackRelativeTimePrecision), requiresRelativeTime))
        add(SettingsSearchEntry("playback.comments", SettingsStrings.readCommentCount, SettingsSearchSection.Playback, SettingsStrings.searchCommentCountDescription, Account.ReadingPlaybackSettings(setting = READING_PLAYBACK_COMMENT_COUNT_KEY), searchAliases(SettingsSearchStrings::playbackComments), requiresReadingComments))
        add(SettingsSearchEntry("playback.commentAuthor", SettingsStrings.readCommentAuthor, SettingsSearchSection.Playback, SettingsStrings.searchCommentAuthorDescription, Account.ReadingPlaybackSettings(setting = READING_PLAYBACK_COMMENT_AUTHOR_KEY), searchAliases(SettingsSearchStrings::playbackCommentAuthor), requiresCommentDetails))
        add(SettingsSearchEntry("playback.commentOrder", SettingsStrings.commentOrder, SettingsSearchSection.Playback, SettingsStrings.searchCommentOrderDescription, Account.ReadingPlaybackSettings(setting = READING_PLAYBACK_COMMENT_ORDER_KEY), searchAliases(SettingsSearchStrings::playbackCommentOrder), requiresCommentDetails))
        add(SettingsSearchEntry("playback.queue", SettingsStrings.queueLimit, SettingsSearchSection.Playback, SettingsStrings.searchQueueLimitDescription, Account.ReadingPlaybackSettings(setting = READING_PLAYBACK_QUEUE_LIMIT_KEY), searchAliases(SettingsSearchStrings::playbackQueue)))
        add(SettingsSearchEntry("playback.transition", SettingsStrings.transitionBetweenItems, SettingsSearchSection.Playback, SettingsStrings.searchTransitionDescription, Account.ReadingPlaybackSettings(setting = READING_PLAYBACK_TRANSITION_KEY), searchAliases(SettingsSearchStrings::playbackTransition)))
    }

    add(SettingsSearchEntry("recommend.recommendationMode", SettingsStrings.recommendationAlgorithm, SettingsSearchSection.Feed, SettingsStrings.searchRecommendationDescription, Account.FeedSettings(setting = "recommendationMode"), searchAliases(SettingsSearchStrings::recommendRecommendationMode)))
    add(SettingsSearchEntry("recommend.loginForRecommendation", SettingsStrings.recommendationLogin, SettingsSearchSection.Feed, SettingsStrings.searchRecommendationLoginDescription, Account.FeedSettings(setting = "loginForRecommendation"), searchAliases(SettingsSearchStrings::recommendLoginForRecommendation)))
    add(SettingsSearchEntry("recommend.qualityFilterMode", SettingsStrings.qualityBlocking, SettingsSearchSection.Filtering, SettingsStrings.searchQualityFilterDescription, Account.RecommendSettings(setting = QUALITY_FILTER_MODE_PREFERENCE_KEY), searchAliases(SettingsSearchStrings::recommendQualityFilterMode)))
    add(SettingsSearchEntry("recommend.enableContentFilter", SettingsStrings.smartContentFilter, SettingsSearchSection.Filtering, SettingsStrings.searchSmartFilterDescription, Account.RecommendSettings(setting = "enableContentFilter"), searchAliases(SettingsSearchStrings::recommendEnableContentFilter)))
    add(SettingsSearchEntry("recommend.filterFollowedUserContent", SettingsStrings.filterFollowedUsers, SettingsSearchSection.Filtering, SettingsStrings.searchFollowedUsersDescription, Account.RecommendSettings(setting = "filterFollowedUserContent"), searchAliases(SettingsSearchStrings::recommendFilterFollowedUserContent)))
    add(SettingsSearchEntry("recommend.enableKeywordBlocking", SettingsStrings.enableKeywordBlocking, SettingsSearchSection.Filtering, SettingsStrings.searchKeywordBlockingDescription, Account.RecommendSettings(setting = "enableKeywordBlocking"), searchAliases(SettingsSearchStrings::recommendEnableKeywordBlocking)))
    add(SettingsSearchEntry("recommend.enableUserBlocking", SettingsStrings.enableUserBlocking, SettingsSearchSection.Filtering, SettingsStrings.searchUserBlockingDescription, Account.RecommendSettings(setting = "enableUserBlocking"), searchAliases(SettingsSearchStrings::recommendEnableUserBlocking)))
    add(SettingsSearchEntry("recommend.enableTopicBlocking", SettingsStrings.enableTopicBlocking, SettingsSearchSection.Filtering, SettingsStrings.searchTopicBlockingDescription, Account.RecommendSettings(setting = "enableTopicBlocking"), searchAliases(SettingsSearchStrings::recommendEnableTopicBlocking)))
    add(SettingsSearchEntry("recommend.topicBlockingThreshold", SettingsStrings.topicBlockingThreshold, SettingsSearchSection.Filtering, SettingsStrings.searchTopicThresholdDescription, Account.RecommendSettings(setting = "topicBlockingThreshold"), searchAliases(SettingsSearchStrings::recommendTopicBlockingThreshold), requiresTopicBlocking))
    add(SettingsSearchEntry("recommend.blockZhihuAdPlatform", SettingsStrings.blockAdPlatform, SettingsSearchSection.Filtering, SettingsStrings.searchBlockAdsDescription, Account.RecommendSettings(setting = "blockZhihuAdPlatform"), searchAliases(SettingsSearchStrings::recommendBlockZhihuAdPlatform)))
    add(SettingsSearchEntry("recommend.blockZhihuSchool", SettingsStrings.blockZhihuSchool, SettingsSearchSection.Filtering, SettingsStrings.searchBlockSchoolDescription, Account.RecommendSettings(setting = "blockZhihuSchool"), searchAliases(SettingsSearchStrings::recommendBlockZhihuSchool)))
    add(SettingsSearchEntry("recommend.blockWeChatOfficialAccount", SettingsStrings.blockWechatArticles, SettingsSearchSection.Filtering, SettingsStrings.searchBlockWechatDescription, Account.RecommendSettings(setting = "blockWeChatOfficialAccount"), searchAliases(SettingsSearchStrings::recommendBlockWeChatOfficialAccount)))
    add(SettingsSearchEntry("recommend.blockPaidContent", SettingsStrings.blockPaidContent, SettingsSearchSection.Filtering, SettingsStrings.searchBlockPaidDescription, Account.RecommendSettings(setting = "blockPaidContent"), searchAliases(SettingsSearchStrings::recommendBlockPaidContent)))
    add(SettingsSearchEntry("recommend.reverseBlock", SettingsStrings.reverseBlocking, SettingsSearchSection.Filtering, SettingsStrings.searchReverseBlockingDescription, Account.RecommendSettings(setting = "reverseBlock"), searchAliases(SettingsSearchStrings::recommendReverseBlock)))
    add(
        SettingsSearchEntry(
            id = "recommend.blocklist",
            title = SettingsStrings.manageBlocklist,
            section = SettingsSearchSection.Filtering,
            description = SettingsStrings.searchBlocklistDescription,
            destination = Account.RecommendSettings.Blocklist,
            keywords = searchAliases(SettingsSearchStrings::recommendBlocklist) +
                if (isBlocklistNlpSupported && platformName != "Android") searchAliases(SettingsSearchStrings::recommendBlocklistSemantic) else emptyList(),
        ),
    )
    add(
        SettingsSearchEntry(
            id = "recommend.blockedHistory",
            title = SettingsStrings.blockedHistory,
            section = SettingsSearchSection.Filtering,
            description = SettingsStrings.searchBlockedHistoryDescription,
            destination = Account.RecommendSettings.BlockedFeedHistory,
            keywords = searchAliases(SettingsSearchStrings::recommendBlockedHistory),
        ),
    )

    add(SettingsSearchEntry("system.update", SettingsStrings.checkUpdates, SettingsSearchSection.System, SettingsStrings.searchSystemUpdateDescription, Account.SystemAndUpdateSettings(setting = "systemUpdate"), searchAliases(SettingsSearchStrings::systemUpdate)))
    if (platformName == "Web") {
        add(SettingsSearchEntry("system.webAppCache", SettingsStrings.webAppCache, SettingsSearchSection.System, SettingsStrings.webCacheScope, Account.SystemAndUpdateSettings(setting = "webAppCache"), searchAliases(SettingsSearchStrings::systemWebAppCache)))
    }
    add(SettingsSearchEntry("system.githubToken", "GitHub Token", SettingsSearchSection.System, SettingsStrings.searchGithubTokenDescription, Account.SystemAndUpdateSettings(setting = "githubToken"), searchAliases(SettingsSearchStrings::systemGithubToken)))
    add(SettingsSearchEntry("system.checkNightlyUpdates", SettingsStrings.checkNightlyUpdates, SettingsSearchSection.System, SettingsStrings.searchNightlyDescription, Account.SystemAndUpdateSettings(setting = "checkNightlyUpdates"), searchAliases(SettingsSearchStrings::systemCheckNightlyUpdates)))
    add(SettingsSearchEntry("system.allowTelemetry", SettingsStrings.allowTelemetry, SettingsSearchSection.System, SettingsStrings.searchTelemetryDescription, Account.SystemAndUpdateSettings(setting = "allowTelemetry"), searchAliases(SettingsSearchStrings::systemAllowTelemetry)))
    add(SettingsSearchEntry("system.aigcMarking", SettingsStrings.aigcMarking, SettingsSearchSection.System, SettingsStrings.searchAigcDescription, Account.SystemAndUpdateSettings(setting = AIGC_MARKING_ENABLED_PREFERENCE_KEY), searchAliases(SettingsSearchStrings::systemAigcMarking)))
    add(SettingsSearchEntry("system.reminder", SettingsStrings.usageReminder, SettingsSearchSection.System, SettingsStrings.searchReminderDescription, Account.SystemAndUpdateSettings(setting = CONTINUOUS_USAGE_REMINDER_INTERVAL_MINUTES_KEY), searchAliases(SettingsSearchStrings::systemReminder)))
    if (platformName == "macOS") {
        add(
            SettingsSearchEntry(
                "system.macosQuitOnWindowClose",
                SettingsStrings.quitOnWindowClose,
                SettingsSearchSection.System,
                SettingsStrings.searchQuitWindowDescription,
                Account.SystemAndUpdateSettings(setting = MACOS_QUIT_ON_WINDOW_CLOSE_PREFERENCE_KEY),
                searchAliases(SettingsSearchStrings::systemMacosQuitOnWindowClose),
            ),
        )
    }

    add(SettingsSearchEntry("notification.autoMarkAsRead", SettingsStrings.autoMarkNotificationsRead, SettingsSearchSection.Notifications, SettingsStrings.searchAutoMarkReadDescription, Notification.NotificationSettings(setting = "autoMarkAsRead"), searchAliases(SettingsSearchStrings::notificationAutoMarkAsRead)))
    add(SettingsSearchEntry("notification.unreadBadge", SettingsStrings.showUnreadBadge, SettingsSearchSection.Notifications, SettingsStrings.searchUnreadBadgeDescription, Notification.NotificationSettings(setting = "unreadBadge"), searchAliases(SettingsSearchStrings::notificationUnreadBadge)))
    add(SettingsSearchEntry("notification.resetReadRecords", SettingsStrings.resetReadRecords, SettingsSearchSection.Notifications, SettingsStrings.searchResetReadRecordsDescription, Notification.NotificationSettings(setting = "resetReadRecords"), searchAliases(SettingsSearchStrings::notificationResetReadRecords)))
    val systemNotificationSummary = SettingsSearchStrings.systemNotificationsSummary
    add(SettingsSearchEntry("notification.systemNotifications", SettingsStrings.systemNotifications, SettingsSearchSection.Notifications, systemNotificationSummary, Notification.NotificationSettings(setting = "systemNotifications"), searchAliases(SettingsSearchStrings::notificationSystemNotifications)))
    NotificationType.entries.forEach { type ->
        add(SettingsSearchEntry("notification.system.${type.name}", type.displayName, SettingsSearchSection.SystemNotifications, systemNotificationSummary, Notification.NotificationSettings(setting = "systemNotification_${type.name}"), searchAliases(SettingsSearchStrings::notificationSystemCategory) + type.searchAliases()))
    }
    add(SettingsSearchEntry("notification.displayInAppNotifications", SettingsStrings.showInAppNotifications, SettingsSearchSection.Notifications, SettingsStrings.searchInAppNotificationsDescription, Notification.NotificationSettings(setting = "displayInAppNotifications"), searchAliases(SettingsSearchStrings::notificationDisplayInAppNotifications) + NotificationType.entries.flatMap { it.searchAliases() + it.displayName }))

    if (miuixThemeAvailable) {
        add(SettingsSearchEntry("appearance.uiThemeStyle", SettingsStrings.interfaceSkin, SettingsSearchSection.Appearance, SettingsStrings.searchInterfaceSkinDescription, Account.AppearanceSettings(setting = THEME_STYLE_PREFERENCE_KEY), searchAliases(SettingsSearchStrings::appearanceUiThemeStyle)))
        add(SettingsSearchEntry("appearance.miuixStyle", SettingsStrings.hyperosStyle, SettingsSearchSection.Appearance, SettingsStrings.searchHyperosStyleDescription, Account.AppearanceSettings(setting = MIUIX_STYLE_PREFERENCE_KEY), searchAliases(SettingsSearchStrings::appearanceMiuixStyle), requiresMiuixSkin))
    }
    add(SettingsSearchEntry("appearance.customColor", SettingsStrings.customThemeColor, SettingsSearchSection.Appearance, SettingsStrings.searchCustomColorDescription, Account.AppearanceSettings(setting = APPEARANCE_SETTINGS_CUSTOM_COLOR_KEY), searchAliases(SettingsSearchStrings::appearanceCustomColor), requiresCustomColor))
    add(SettingsSearchEntry("appearance.backgroundColor", SettingsStrings.customBackgroundColor, SettingsSearchSection.Appearance, SettingsStrings.searchBackgroundColorDescription, Account.AppearanceSettings(setting = APPEARANCE_SETTINGS_BACKGROUND_COLOR_KEY), searchAliases(SettingsSearchStrings::appearanceBackgroundColor)))
    add(SettingsSearchEntry("appearance.browserToolbarColor", SettingsStrings.openBrowserThemeColor, SettingsSearchSection.Appearance, SettingsStrings.searchBrowserColorDescription, Account.AppearanceSettings(setting = APPEARANCE_SETTINGS_BROWSER_COLOR_KEY), searchAliases(SettingsSearchStrings::appearanceBrowserToolbarColor)))
    add(SettingsSearchEntry("appearance.showCreateFab", SettingsStrings.showCreateButton, SettingsSearchSection.Appearance, SettingsStrings.showCreateButtonDescription, Account.AppearanceSettings(setting = "showCreateFab"), searchAliases(SettingsSearchStrings::appearanceShowCreateFab)))
    add(SettingsSearchEntry("appearance.fabOpacity", SettingsStrings.floatingButtonOpacity, SettingsSearchSection.Appearance, SettingsStrings.searchFloatingOpacityDescription, Account.AppearanceSettings(setting = PREF_FAB_OPACITY), searchAliases(SettingsSearchStrings::appearanceFabOpacity)))
    add(SettingsSearchEntry("navigation.startDestination", SettingsStrings.startupPage, SettingsSearchSection.Navigation, SettingsStrings.searchStartupPageDescription, Account.NavigationSettings(setting = START_DESTINATION_PREFERENCE_KEY), searchAliases(SettingsSearchStrings::navigationStartDestination)))
    add(SettingsSearchEntry("navigation.tapScrollToTop", SettingsStrings.bottomBarTapToTop, SettingsSearchSection.Navigation, SettingsStrings.searchTapToTopDescription, Account.NavigationSettings(setting = "bottomBarTapScrollToTop"), searchAliases(SettingsSearchStrings::navigationTapScrollToTop)))
    if (sharedMainNavigationBarAvailable) {
        add(SettingsSearchEntry("navigation.autoHideBottomBar", SettingsStrings.autoHideBottomBar, SettingsSearchSection.Navigation, SettingsStrings.autoHideBottomBarDescription, Account.NavigationSettings(setting = "autoHideBottomBar"), searchAliases(SettingsSearchStrings::navigationAutoHideBottomBar)))
    }
    add(SettingsSearchEntry("reading.lineHeight", SettingsStrings.lineHeight, SettingsSearchSection.Reading, SettingsStrings.searchLineHeightDescription, Account.ReadingSettings(setting = PREF_LINE_HEIGHT), searchAliases(SettingsSearchStrings::readingLineHeight)))
    add(SettingsSearchEntry("reading.blockSpacing", SettingsStrings.paragraphSpacing, SettingsSearchSection.Reading, SettingsStrings.searchParagraphSpacingDescription, Account.ReadingSettings(setting = PREF_BLOCK_SPACING), searchAliases(SettingsSearchStrings::readingBlockSpacing)))
    if (isAnswerSwipeSupported) {
        add(SettingsSearchEntry("reading.answerSwitchSensitivity", SettingsStrings.answerSwitchSensitivity, SettingsSearchSection.Reading, SettingsStrings.searchAnswerSensitivityDescription, Account.ReadingSettings(setting = ANSWER_SWITCH_SENSITIVITY_PREFERENCE_KEY), searchAliases(SettingsSearchStrings::readingAnswerSwitchSensitivity), requiresAnswerSwitch))
    }
    add(SettingsSearchEntry("recommend.autoRefreshHomeOnStartup", SettingsStrings.autoRefreshHome, SettingsSearchSection.Feed, SettingsStrings.searchAutoRefreshDescription, Account.FeedSettings(setting = AUTO_REFRESH_HOME_ON_STARTUP_PREFERENCE_KEY), searchAliases(SettingsSearchStrings::recommendAutoRefreshHomeOnStartup)))
    listOf(
        ANSWER_VOTEUP_THRESHOLD_PREFERENCE_KEY to SettingsStrings.answerMinimumVotes,
        ARTICLE_VOTEUP_THRESHOLD_PREFERENCE_KEY to SettingsStrings.articleMinimumVotes,
        ARTICLE_FOLLOWERS_THRESHOLD_PREFERENCE_KEY to SettingsStrings.articleMinimumFollowers,
        VIDEO_VOTE_THRESHOLD_PREFERENCE_KEY to SettingsStrings.videoMinimumVotes,
        VIDEO_FOLLOWERS_THRESHOLD_PREFERENCE_KEY to SettingsStrings.videoMinimumFollowers,
        QUESTION_ANSWER_THRESHOLD_PREFERENCE_KEY to SettingsStrings.questionMinimumAnswers,
        QUESTION_FOLLOWERS_THRESHOLD_PREFERENCE_KEY to SettingsStrings.questionMinimumFollowers,
    ).forEach { (key, title) ->
        add(SettingsSearchEntry("recommend.threshold.$key", title, SettingsSearchSection.Filtering, SettingsStrings.searchQualityThresholdDescription, Account.RecommendSettings(setting = key), searchAliases(SettingsSearchStrings::recommendThresholdQuality), requiresQualityFilter))
    }
    add(SettingsSearchEntry("recommend.clearExposureRecords", SettingsStrings.clearExposureRecords, SettingsSearchSection.Filtering, SettingsStrings.searchClearExposureDescription, Account.RecommendSettings(setting = "clearExposureRecords"), searchAliases(SettingsSearchStrings::recommendClearExposureRecords)))
    add(
        SettingsSearchEntry(
            id = "account.identity",
            title = SettingsStrings.accountsIdentities,
            section = SettingsSearchSection.AccountSecurity,
            description = SettingsStrings.searchAccountsDescription,
            destination = Account.IdentityManagement,
            keywords = searchAliases(SettingsSearchStrings::accountIdentity),
        ),
    )

    add(
        SettingsSearchEntry(
            id = "developer.page",
            title = SettingsStrings.accountLoginMaintenance,
            section = SettingsSearchSection.AccountSecurity,
            description = SettingsStrings.searchAccountToolsDescription,
            destination = Account.AccountTools,
            keywords = searchAliases(SettingsSearchStrings::developerPage),
        ),
    )
    add(
        SettingsSearchEntry(
            id = "about.webdav",
            title = SettingsStrings.webdavSync,
            section = SettingsSearchSection.About,
            description = SettingsStrings.searchWebdavDescription,
            destination = Account.WebDavSync,
            keywords = searchAliases(SettingsSearchStrings::aboutWebdav),
        ),
    )
    add(
        SettingsSearchEntry(
            id = "about.webdav.checkConnection",
            title = SettingsStrings.checkConnection,
            section = SettingsSearchSection.WebDav,
            description = SettingsStrings.searchCheckConnectionDescription,
            destination = Account.WebDavSync,
            keywords = searchAliases(SettingsSearchStrings::aboutWebdavCheckConnection),
        ),
    )
    add(
        SettingsSearchEntry(
            id = "about.licenses",
            title = SettingsStrings.openSourceLicenses,
            section = SettingsSearchSection.About,
            description = SettingsStrings.searchLicensesDescription,
            destination = Account.OpenSourceLicenses,
            keywords = searchAliases(SettingsSearchStrings::aboutLicenses),
        ),
    )
}

@Composable
fun SettingsSearchScreen() {
    val navigator = LocalNavigator.current
    val settings = rememberSettingsStore()
    val queryState = rememberTextFieldState()
    val query = queryState.text.toString()
    val language = AppLocale.current
    val sharedMainNavigationBarAvailable = LocalSharedMainNavigationBarAvailable.current
    val entries = remember(language, sharedMainNavigationBarAvailable) { buildSettingsSearchEntries(sharedMainNavigationBarAvailable) }
    val groups = remember(query, entries) {
        entries.filter { entry -> entry.matches(query) }.groupBy { it.section }
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
                    title = SettingsStrings.searchSettings,
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
                        placeholder = SettingsStrings.searchSettingsPlaceholder,
                        leadingIcon = {
                            AppIcon(
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
                        title = SettingsStrings.noSettingsFound,
                        description = SettingsStrings.noSettingsFoundDescription,
                        icon = Icons.Default.Search,
                    )
                }
            } else {
                groups.forEach { (section, entries) ->
                    item(key = section) {
                        SettingsContent {
                            SettingItemGroup(title = section.title()) {
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
        icon = { AppIcon(Icons.Default.Settings, contentDescription = null) },
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
            AppIcon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
