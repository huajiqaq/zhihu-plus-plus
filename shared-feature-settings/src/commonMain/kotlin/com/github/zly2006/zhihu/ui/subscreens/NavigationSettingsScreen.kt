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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.github.zly2006.zhihu.i18n.SettingsStrings
import com.github.zly2006.zhihu.i18n.PlatformStrings
import com.github.zly2006.zhihu.ui.LocalSharedMainNavigationBarAvailable
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.Daily
import com.github.zly2006.zhihu.navigation.Follow
import com.github.zly2006.zhihu.navigation.Home
import com.github.zly2006.zhihu.navigation.HotList
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.MyCollections
import com.github.zly2006.zhihu.navigation.OnlineHistory
import com.github.zly2006.zhihu.platform.platformBottomBarItemLimit
import com.github.zly2006.zhihu.platform.platformName
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.ui.components.AppBottomSheet
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppSettingsPage
import com.github.zly2006.zhihu.ui.components.AppSortableCheckList
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingItemGroup
import com.github.zly2006.zhihu.ui.components.SettingItemSelect
import com.github.zly2006.zhihu.ui.components.SettingItemWithSwitch
import com.github.zly2006.zhihu.ui.components.SortableCheckItem
import com.github.zly2006.zhihu.ui.rememberObservedSetting
import com.github.zly2006.zhihu.ui.rememberSetting

const val NAVIGATION_SETTINGS_BACK_BUTTON_TAG = "navigation_settings_back_button"
internal val landscapeListDetailDescription: String
    get() = SettingsStrings.landscapeListDetailDescription

val NAVIGATION_SETTINGS_KEYS: Set<String> = setOf(
    APPEARANCE_SETTINGS_BOTTOM_BAR_SECTION_KEY,
    START_DESTINATION_PREFERENCE_KEY,
    BOTTOM_BAR_ITEMS_PREFERENCE_KEY,
    "bottomBarTapScrollToTop",
    "autoHideBottomBar",
    LANDSCAPE_LIST_DETAIL_PREFERENCE_KEY,
    "enable_predictive_back",
    PAGE_TRANSITION_PREFERENCE_KEY,
    BOTTOM_BAR_ITEM_ORDER_PREFERENCE_KEY,
    "shareAction",
    "showSearchHotSearch",
    "showSearchHistory",
)

private val bottomBarItemLabels: Map<String, String>
    get() = linkedMapOf(
        Home.name to SettingsStrings.home,
        Follow.name to SettingsStrings.following,
        HotList.name to SettingsStrings.trending,
        Daily.name to SettingsStrings.daily,
        OnlineHistory.name to SettingsStrings.history,
        MyCollections.name to SettingsStrings.collections,
        Account.name to SettingsStrings.accountSettings,
    )

/** 退出时只重新读取主壳偏好，不重建返回栈或已加载页面。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationSettingsScreen(
    setting: String?,
    onExit: () -> Unit,
) {
    val requestedKey = setting.orEmpty()
    val settings = rememberSettingsStore()
    val userMessages = rememberUserMessageSink()

    var showBottomBarItemsSheet by remember { mutableStateOf(false) }
    val bottomBarSheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )

    /** 选择与顺序分别存储，须同时观察外部修改。 */
    val storedBottomBarItems = rememberObservedSetting(settings, BOTTOM_BAR_ITEMS_PREFERENCE_KEY) {
        getStringSet(BOTTOM_BAR_ITEMS_PREFERENCE_KEY, defaultBottomBarSelectionKeys(platformBottomBarItemLimit))
    }
    val storedBottomBarOrder = rememberObservedSetting(settings, BOTTOM_BAR_ITEM_ORDER_PREFERENCE_KEY) {
        getStringOrNull(BOTTOM_BAR_ITEM_ORDER_PREFERENCE_KEY)
    }

    fun currentBottomBarItemKeys(): List<String> = bottomBarItemOrderFromPreference(
        storedBottomBarOrder.value,
        normalizeBottomBarSelection(
            storedBottomBarItems.value,
            enforceMinimumSelection = true,
            maximumSelection = platformBottomBarItemLimit,
        ),
    )
    val selectedBottomBarItemKeys = currentBottomBarItemKeys()
    var storedStartDestination by rememberSetting(settings, START_DESTINATION_PREFERENCE_KEY, Home.name)
    val startDestinationKey = resolveValidStartDestinationKey(storedStartDestination, selectedBottomBarItemKeys)

    fun persistBottomBarSelection(currentOrderKeys: List<String>) {
        val normalizedSet = normalizeBottomBarSelection(
            currentOrderKeys,
            enforceMinimumSelection = true,
            maximumSelection = platformBottomBarItemLimit,
        )
        val normalizedOrderKeys = normalizeBottomBarItemOrder(currentOrderKeys, normalizedSet)
        val normalizedOrder = bottomBarItemOrderPreferenceValue(normalizedOrderKeys)
        settings.putStringSet(BOTTOM_BAR_ITEMS_PREFERENCE_KEY, normalizedSet)
        settings.putString(BOTTOM_BAR_ITEM_ORDER_PREFERENCE_KEY, normalizedOrder)
        storedBottomBarItems.value = normalizedSet
        storedBottomBarOrder.value = normalizedOrder
        storedStartDestination = resolveValidStartDestinationKey(storedStartDestination, normalizedOrderKeys)
    }

    fun moveBottomBarItem(key: String, offset: Int) {
        val currentOrderKeys = currentBottomBarItemKeys()
        val fromIndex = currentOrderKeys.indexOf(key)
        val toIndex = fromIndex + offset
        if (fromIndex < 0 || toIndex !in currentOrderKeys.indices) {
            return
        }
        val reorderedKeys = currentOrderKeys.toMutableList()
        reorderedKeys.removeAt(fromIndex)
        reorderedKeys.add(toIndex, key)
        persistBottomBarSelection(reorderedKeys)
    }

    fun toggleBottomBarItem(key: String, checked: Boolean) {
        val currentOrderKeys = currentBottomBarItemKeys()
        persistBottomBarSelection(if (checked) currentOrderKeys + key else currentOrderKeys - key)
    }

    var predictiveBackEnabled by rememberSetting(settings, "enable_predictive_back", true)
    val highlightedKey = when (requestedKey) {
        BOTTOM_BAR_ITEM_ORDER_PREFERENCE_KEY, COLLECTION_DIRECT_BROWSE_PREFERENCE_KEY -> BOTTOM_BAR_ITEMS_PREFERENCE_KEY
        "enable_predictive_back" -> PAGE_TRANSITION_PREFERENCE_KEY
        else -> requestedKey
    }

    DisposableEffect(Unit) {
        onDispose {
            onExit()
        }
    }

    AppSettingsPage(
        title = SettingsStrings.navigationInteraction,
        onBack = LocalNavigator.current.onNavigateBack,
        backTag = NAVIGATION_SETTINGS_BACK_BUTTON_TAG,
        highlightedKey = highlightedKey,
        scrollTag = APPEARANCE_SETTINGS_SCROLL_TAG,
        pageTurnEnabled = !showBottomBarItemsSheet,
        overlays = {
            if (showBottomBarItemsSheet) {
                AppBottomSheet(
                    onDismissRequest = { showBottomBarItemsSheet = false },
                    sheetState = bottomBarSheetState,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        Text(
                            text = SettingsStrings.bottomBarItems,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = AppDimens.PageHorizontalPadding).padding(top = AppDimens.Space8),
                        )
                        Text(
                            text = SettingsStrings.bottomBarItemsDescription,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(horizontal = AppDimens.PageHorizontalPadding)
                                .padding(top = AppDimens.Space4, bottom = AppDimens.Space12),
                        )
                        SettingItemGroup {
                            AppSortableCheckList(
                                items = (selectedBottomBarItemKeys + availableBottomBarItemKeys(platformBottomBarItemLimit).filter { it !in selectedBottomBarItemKeys })
                                    .mapNotNull { key ->
                                        bottomBarItemLabels[key]?.let { label ->
                                            SortableCheckItem(
                                                key = key,
                                                title = label,
                                                lockedReason = if (key == Account.name && Home.name !in selectedBottomBarItemKeys) SettingsStrings.accountEntryRequired else null,
                                            )
                                        }
                                    },
                                checkedKeys = selectedBottomBarItemKeys,
                                onCheckedChange = ::toggleBottomBarItem,
                                onMove = ::moveBottomBarItem,
                                minChecked = 3,
                                maxChecked = platformBottomBarItemLimit,
                                minCheckedReason = SettingsStrings.minimumNavigationItems,
                                testTagPrefix = "appearanceSettings:bottomBar",
                            )
                        }
                        AppButton(
                            text = SettingsStrings.done,
                            onClick = { showBottomBarItemsSheet = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space16),
                        )
                    }
                }
            }
        },
    ) {
        SettingItemGroup(
            title = SettingsStrings.navigation,
            settingKey = APPEARANCE_SETTINGS_BOTTOM_BAR_SECTION_KEY,
        ) {
            val selectedBottomBarItems = selectedBottomBarItemKeys.mapNotNull { key ->
                bottomBarItemLabels[key]?.let { label -> key to label }
            }
            SettingItemSelect(
                title = { Text(SettingsStrings.startupPage) },
                description = { Text(SettingsStrings.startupPageDescription) },
                options = selectedBottomBarItems.map { it.second },
                selectedIndex = selectedBottomBarItems.indexOfFirst { it.first == startDestinationKey },
                onSelect = { index ->
                    val (key, label) = selectedBottomBarItems[index]
                    storedStartDestination = key
                    userMessages.showShortMessage(SettingsStrings.startupPageChanged(label))
                },
                enabled = selectedBottomBarItems.isNotEmpty(),
                fieldModifier = Modifier.testTag(APPEARANCE_SETTINGS_START_DESTINATION_TAG),
                settingKey = START_DESTINATION_PREFERENCE_KEY,
            )

            SettingItem(
                title = { Text(SettingsStrings.bottomBarItems) },
                description = { Text(selectedBottomBarItems.joinToString(SettingsStrings.listSeparator) { it.second }) },
                onClick = { showBottomBarItemsSheet = true },
                settingKey = BOTTOM_BAR_ITEMS_PREFERENCE_KEY,
            )

            var tapToRefresh by rememberSetting(settings, "bottomBarTapScrollToTop", true)
            SettingItemWithSwitch(
                title = { Text(SettingsStrings.bottomBarTapToTop) },
                description = { Text(SettingsStrings.bottomBarTapToTopDescription) },
                checked = tapToRefresh,
                onCheckedChange = { tapToRefresh = it },
                settingKey = "bottomBarTapScrollToTop",
            )

            var autoHideBottomBar by rememberSetting(settings, "autoHideBottomBar", false)
            SettingItemWithSwitch(
                title = { Text(SettingsStrings.autoHideBottomBar) },
                description = { Text(if (LocalSharedMainNavigationBarAvailable.current) SettingsStrings.autoHideBottomBarDescription else PlatformStrings.sharedNavigationOnly) },
                checked = autoHideBottomBar && LocalSharedMainNavigationBarAvailable.current,
                enabled = LocalSharedMainNavigationBarAvailable.current,
                onCheckedChange = { autoHideBottomBar = it },
                settingKey = "autoHideBottomBar",
            )

            var landscapeListDetail by rememberSetting(settings, LANDSCAPE_LIST_DETAIL_PREFERENCE_KEY, true)
            SettingItemWithSwitch(
                modifier = Modifier.testTag(APPEARANCE_SETTINGS_LANDSCAPE_LIST_DETAIL_TAG),
                title = { Text(SettingsStrings.landscapeListDetail) },
                description = { Text(landscapeListDetailDescription) },
                checked = landscapeListDetail,
                onCheckedChange = { landscapeListDetail = it },
                settingKey = LANDSCAPE_LIST_DETAIL_PREFERENCE_KEY,
            )
        }

        SettingItemGroup(title = SettingsStrings.backAndSharing) {
            var pageTransition by rememberSetting(settings, PAGE_TRANSITION_PREFERENCE_KEY, "miuix")
            val pageTransitionOptions = arrayOf("none" to SettingsStrings.noAnimation, "miuix" to SettingsStrings.slideTransition, "aosp" to "AOSP")
            SettingItemSelect(
                modifier = Modifier.testTag("navigation_settings_page_transition"),
                title = { Text(SettingsStrings.pageTransition) },
                options = pageTransitionOptions.map { it.second },
                selectedIndex = if (predictiveBackEnabled) pageTransitionOptions.indexOfFirst { it.first == pageTransition }.coerceAtLeast(1) else 0,
                onSelect = { index ->
                    if (index > 0) pageTransition = pageTransitionOptions[index].first
                    predictiveBackEnabled = index > 0
                },
                settingKey = PAGE_TRANSITION_PREFERENCE_KEY,
            )

            var shareActionMode by rememberSetting(settings, "shareActionMode", "ask")
            val shareActionOptions = listOf(
                "ask" to SettingsStrings.ask,
                "copy" to SettingsStrings.copyLink,
                "share" to if (platformName == "Android") SettingsStrings.androidShare else SettingsStrings.systemShare,
            )
            SettingItemSelect(
                title = { Text(SettingsStrings.shareAction) },
                options = shareActionOptions.map { it.second },
                selectedIndex = shareActionOptions.indexOfFirst { it.first == shareActionMode },
                onSelect = { index ->
                    val (mode, label) = shareActionOptions[index]
                    shareActionMode = mode
                    userMessages.showShortMessage(SettingsStrings.settingChanged(label))
                },
                settingKey = "shareAction",
            )
        }

        SettingItemGroup(title = SettingsStrings.search) {
            var showSearchHotSearch by rememberSetting(settings, "showSearchHotSearch", true)
            SettingItemWithSwitch(
                title = { Text(SettingsStrings.showHotSearches) },
                description = { Text(SettingsStrings.showHotSearchesDescription) },
                checked = showSearchHotSearch,
                onCheckedChange = { showSearchHotSearch = it },
                settingKey = "showSearchHotSearch",
            )
            var showSearchHistory by rememberSetting(settings, "showSearchHistory", true)
            SettingItemWithSwitch(
                title = { Text(SettingsStrings.recordSearchHistory) },
                description = { Text(SettingsStrings.recordSearchHistoryDescription) },
                checked = showSearchHistory,
                onCheckedChange = { showSearchHistory = it },
                settingKey = "showSearchHistory",
            )
        }
    }
}
