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
import com.github.zly2006.zhihu.data.RecommendationMode
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
    AppSettingsPage(
        title = "推荐与信息流",
        onBack = LocalNavigator.current.onNavigateBack,
        backTag = "feed_settings_back_button",
        highlightedKey = setting.orEmpty(),
        scrollTag = "feedSettings:scroll",
    ) {
        SettingItemGroup(title = "推荐来源") {
            val modes = RecommendationMode.entries
            var modeKey by rememberSetting(settings, "recommendationMode", RecommendationMode.MIXED.key)
            val mode = modes.find { it.key == modeKey } ?: RecommendationMode.MIXED
            SettingItemSelect(
                title = { Text("推荐算法") },
                description = { Text(mode.description) },
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
                title = { Text("推荐内容时登录") },
                description = { Text("获取推荐内容时携带登录凭证") },
                checked = loginForRecommendation,
                onCheckedChange = { loginForRecommendation = it },
                settingKey = "loginForRecommendation",
            )
        }

        SettingItemGroup(title = "首页信息流") {
            var showFeedThumbnail by rememberSetting(settings, "showFeedThumbnail", true)
            SettingItemWithSwitch(
                title = { Text("显示 Feed 卡片缩略图") },
                description = { Text("在信息流卡片中显示文章缩略图") },
                checked = showFeedThumbnail,
                onCheckedChange = { showFeedThumbnail = it },
                settingKey = "showFeedThumbnail",
            )
            var showRefreshFab by rememberSetting(settings, "showRefreshFab", true)
            SettingItemWithSwitch(
                title = { Text("显示刷新悬浮按钮") },
                description = { Text("在首页和列表页显示可拖动的刷新按钮") },
                checked = showRefreshFab,
                onCheckedChange = { showRefreshFab = it },
                settingKey = "showRefreshFab",
            )
            var autoRefreshHomeOnStartup by rememberSetting(settings, AUTO_REFRESH_HOME_ON_STARTUP_PREFERENCE_KEY, true)
            SettingItemWithSwitch(
                modifier = Modifier.testTag("contentFilterSettings:autoRefreshHomeOnStartup"),
                title = { Text("启动时自动刷新首页") },
                description = { Text("关闭后优先显示上次获取的一批首页推荐；没有缓存时仍会加载新推荐") },
                checked = autoRefreshHomeOnStartup,
                onCheckedChange = { autoRefreshHomeOnStartup = it },
                settingKey = AUTO_REFRESH_HOME_ON_STARTUP_PREFERENCE_KEY,
            )
        }
    }
}
