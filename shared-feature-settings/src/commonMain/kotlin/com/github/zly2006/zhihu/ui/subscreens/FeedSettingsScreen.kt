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

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.RecommendationMode
import com.github.zly2006.zhihu.i18n.SettingsStrings
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.ui.AUTO_REFRESH_HOME_ON_STARTUP_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.components.AppSettingsPage
import com.github.zly2006.zhihu.ui.components.SettingItemGroup
import com.github.zly2006.zhihu.ui.components.SettingItemSelect
import com.github.zly2006.zhihu.ui.components.SettingItemWithSwitch
import com.github.zly2006.zhihu.ui.rememberSetting

val FEED_SETTINGS_KEYS: Set<String> = setOf(
    "recommendationMode",
    "loginForRecommendation",
    "showFeedThumbnail",
    "showRefreshFab",
    AUTO_REFRESH_HOME_ON_STARTUP_PREFERENCE_KEY,
)

@Composable
fun FeedSettingsScreen(setting: String?) {
    val settings = rememberSettingsStore()
    val browserSession = rememberZhihuAccountStore().usesBrowserSession
    AppSettingsPage(
        title = SettingsStrings.recommendationsFeed,
        onBack = LocalNavigator.current.onNavigateBack,
        backTag = "feed_settings_back_button",
        highlightedKey = setting.orEmpty(),
        scrollTag = "feedSettings:scroll",
    ) {
        SettingItemGroup(title = SettingsStrings.recommendationSource) {
            val modes = if (browserSession) listOf(RecommendationMode.WEB) else RecommendationMode.entries
            var modeKey by rememberSetting(settings, "recommendationMode", RecommendationMode.MIXED.key)
            val mode = if (browserSession) RecommendationMode.WEB else modes.find { it.key == modeKey } ?: RecommendationMode.MIXED
            SettingItemSelect(
                title = { Text(SettingsStrings.recommendationAlgorithm) },
                description = { Text(if (browserSession) SettingsStrings.browserRecommendations else mode.description) },
                enabled = !browserSession,
                options = modes.map { it.displayName },
                summaries = modes.map { it.description },
                selectedIndex = modes.indexOf(mode),
                onSelect = { index -> modeKey = modes[index].key },
                fieldModifier = Modifier.testTag("contentFilterSettings:recommendationModeField"),
                settingKey = "recommendationMode",
            )
            var loginForRecommendation by rememberSetting(settings, "loginForRecommendation", true)
            SettingItemWithSwitch(
                modifier = Modifier.testTag("contentFilterSettings:loginForRecommendation"),
                title = { Text(SettingsStrings.recommendationLogin) },
                description = { Text(SettingsStrings.recommendationLoginDescription) },
                checked = loginForRecommendation,
                onCheckedChange = { loginForRecommendation = it },
                settingKey = "loginForRecommendation",
            )
        }

        SettingItemGroup(title = SettingsStrings.homeFeed) {
            var showFeedThumbnail by rememberSetting(settings, "showFeedThumbnail", true)
            SettingItemWithSwitch(
                title = { Text(SettingsStrings.showFeedThumbnails) },
                checked = showFeedThumbnail,
                onCheckedChange = { showFeedThumbnail = it },
                settingKey = "showFeedThumbnail",
            )
            var showRefreshFab by rememberSetting(settings, "showRefreshFab", true)
            SettingItemWithSwitch(
                title = { Text(SettingsStrings.showRefreshButton) },
                description = { Text(SettingsStrings.showRefreshButtonDescription) },
                checked = showRefreshFab,
                onCheckedChange = { showRefreshFab = it },
                settingKey = "showRefreshFab",
            )
            var autoRefreshHomeOnStartup by rememberSetting(settings, AUTO_REFRESH_HOME_ON_STARTUP_PREFERENCE_KEY, true)
            SettingItemWithSwitch(
                modifier = Modifier.testTag("contentFilterSettings:autoRefreshHomeOnStartup"),
                title = { Text(SettingsStrings.autoRefreshHome) },
                description = { Text(SettingsStrings.autoRefreshHomeDescription) },
                checked = autoRefreshHomeOnStartup,
                onCheckedChange = { autoRefreshHomeOnStartup = it },
                settingKey = AUTO_REFRESH_HOME_ON_STARTUP_PREFERENCE_KEY,
            )
        }
    }
}
