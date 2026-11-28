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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.github.zly2006.zhihu.i18n.SettingsStrings
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.platform.isPageTurnSupported
import com.github.zly2006.zhihu.platform.platformName
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppEmptyState
import com.github.zly2006.zhihu.ui.components.AppSettingsPage
import com.github.zly2006.zhihu.ui.components.AppSlider
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingItemGroup
import com.github.zly2006.zhihu.ui.components.SettingItemWithSwitch
import com.github.zly2006.zhihu.ui.rememberSetting
import kotlin.math.roundToInt

const val PAGE_TURN_SETTINGS_BACK_BUTTON_TAG = "page_turn_settings_back_button"

val PAGE_TURN_SETTINGS_KEYS: Set<String> = setOf(
    PREF_VOLUME_KEY_PAGE_TURN,
    PREF_PAGE_TURN_SWITCH_ANSWER,
    PREF_SHOW_PAGE_TURN_FAB,
    PREF_PAGE_TURN_PERCENT,
    PREF_SHOW_PAGE_TURN_GUIDE,
    PREF_SHOW_CONTENT_END_MARKER,
)

@Composable
fun PageTurnSettingsScreen(setting: String?) {
    val settings = rememberSettingsStore()
    AppSettingsPage(
        title = if (platformName != "Android") SettingsStrings.pageTurning else SettingsStrings.einkPageTurning,
        onBack = LocalNavigator.current.onNavigateBack,
        backTag = PAGE_TURN_SETTINGS_BACK_BUTTON_TAG,
        highlightedKey = if (platformName != "Android" && setting == PREF_VOLUME_KEY_PAGE_TURN) PREF_SHOW_PAGE_TURN_FAB else setting.orEmpty(),
    ) {
        if (!isPageTurnSupported) {
            /** 不支持的平台已隐藏入口；此分支防止旧导航状态误示可用。 */
            AppEmptyState(
                title = SettingsStrings.pageTurnUnsupported,
                description = SettingsStrings.pageTurnPlatformUnsupported,
            )
        } else {
            SettingItemGroup(
                title = SettingsStrings.pageTurning,
                footer = { Text(SettingsStrings.pageTurnScope) },
            ) {
                if (platformName == "Android") {
                    var volumeKeyPageTurn by rememberSetting(settings, PREF_VOLUME_KEY_PAGE_TURN, false)
                    SettingItemWithSwitch(
                        title = { Text(SettingsStrings.volumeKeyPageTurn) },
                        description = { Text(SettingsStrings.volumeKeyPageTurnDescription) },
                        checked = volumeKeyPageTurn,
                        onCheckedChange = { volumeKeyPageTurn = it },
                        settingKey = PREF_VOLUME_KEY_PAGE_TURN,
                    )
                }

                var pageTurnSwitchAnswer by rememberSetting(
                    settings,
                    PREF_PAGE_TURN_SWITCH_ANSWER,
                    DEFAULT_PAGE_TURN_SWITCH_ANSWER,
                )
                SettingItemWithSwitch(
                    title = { Text(SettingsStrings.pageTurnSwitchAnswer) },
                    description = { Text(SettingsStrings.pageTurnSwitchAnswerDescription) },
                    checked = pageTurnSwitchAnswer,
                    onCheckedChange = { pageTurnSwitchAnswer = it },
                    settingKey = PREF_PAGE_TURN_SWITCH_ANSWER,
                )

                var showPageTurnFab by rememberSetting(settings, PREF_SHOW_PAGE_TURN_FAB, false)
                SettingItemWithSwitch(
                    title = { Text(SettingsStrings.showPageTurnButton) },
                    description = { Text(SettingsStrings.showPageTurnButtonDescription) },
                    checked = showPageTurnFab,
                    onCheckedChange = { showPageTurnFab = it },
                    settingKey = PREF_SHOW_PAGE_TURN_FAB,
                )

                var pageTurnPercent by rememberSetting(settings, PREF_PAGE_TURN_PERCENT, DEFAULT_PAGE_TURN_PERCENT)
                SettingItem(
                    title = { Text(SettingsStrings.pageTurnDistance) },
                    description = { Text(SettingsStrings.pageTurnDistanceDescription(pageTurnPercent)) },
                    settingKey = PREF_PAGE_TURN_PERCENT,
                    bottomAction = {
                        val slider = rememberSliderState(value = pageTurnPercent.toFloat(), steps = 9, trackRange = 50f..100f)
                        slider.value = pageTurnPercent.toFloat()
                        AppSlider(
                            state = slider,
                            onValueChange = {
                                val percent = ((it / 5).roundToInt() * 5).coerceIn(50, 100)
                                if (percent != pageTurnPercent) pageTurnPercent = percent
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = AppDimens.Space8),
                        )
                    },
                )

                var showGuide by rememberSetting(settings, PREF_SHOW_PAGE_TURN_GUIDE, DEFAULT_SHOW_PAGE_TURN_GUIDE)
                SettingItemWithSwitch(
                    title = { Text(SettingsStrings.showPageTurnGuide) },
                    description = { Text(SettingsStrings.showPageTurnGuideDescription) },
                    checked = showGuide,
                    onCheckedChange = { showGuide = it },
                    settingKey = PREF_SHOW_PAGE_TURN_GUIDE,
                )

                var showContentEndMarker by rememberSetting(
                    settings,
                    PREF_SHOW_CONTENT_END_MARKER,
                    DEFAULT_SHOW_CONTENT_END_MARKER,
                )
                SettingItemWithSwitch(
                    title = { Text(SettingsStrings.showContentEndMarker) },
                    description = { Text(SettingsStrings.showContentEndMarkerDescription) },
                    checked = showContentEndMarker,
                    onCheckedChange = { showContentEndMarker = it },
                    settingKey = PREF_SHOW_CONTENT_END_MARKER,
                )
            }
        }
    }
}
