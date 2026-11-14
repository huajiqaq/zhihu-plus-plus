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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.Daily
import com.github.zly2006.zhihu.navigation.Follow
import com.github.zly2006.zhihu.navigation.Home
import com.github.zly2006.zhihu.navigation.HotList
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.MyCollections
import com.github.zly2006.zhihu.navigation.OnlineHistory
import com.github.zly2006.zhihu.platform.platformBottomBarItemLimit
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppSettingsPage
import com.github.zly2006.zhihu.ui.components.AppSortableCheckList
import com.github.zly2006.zhihu.ui.components.MyModalBottomSheet
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingItemGroup
import com.github.zly2006.zhihu.ui.components.SettingItemSelect
import com.github.zly2006.zhihu.ui.components.SettingItemWithSwitch
import com.github.zly2006.zhihu.ui.components.SortableCheckItem
import com.github.zly2006.zhihu.ui.rememberObservedSetting
import com.github.zly2006.zhihu.ui.rememberSetting

const val NAVIGATION_SETTINGS_BACK_BUTTON_TAG = "navigation_settings_back_button"

val NAVIGATION_SETTINGS_KEYS: Set<String> = setOf(
    APPEARANCE_SETTINGS_BOTTOM_BAR_SECTION_KEY,
    START_DESTINATION_PREFERENCE_KEY,
    BOTTOM_BAR_ITEMS_PREFERENCE_KEY,
    "bottomBarTapScrollToTop",
    "autoHideBottomBar",
    LANDSCAPE_LIST_DETAIL_PREFERENCE_KEY,
    COLLECTION_DIRECT_BROWSE_PREFERENCE_KEY,
    "enable_predictive_back",
    PAGE_TRANSITION_PREFERENCE_KEY,
    BOTTOM_BAR_ITEM_ORDER_PREFERENCE_KEY,
    "shareAction",
    "showSearchHotSearch",
    "showSearchHistory",
)

private val bottomBarItemLabels: Map<String, String> = linkedMapOf(
    Home.name to "主页",
    Follow.name to "关注",
    HotList.name to "热榜",
    Daily.name to "日报",
    OnlineHistory.name to "历史",
    MyCollections.name to "收藏夹",
    Account.name to "账号设置",
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

    val duo3HomeAccount by rememberSetting(settings, "duo3_home_account", false)
    var showBottomBarItemsSheet by remember { mutableStateOf(false) }
    val bottomBarSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    /** 选择与顺序分别存储，须同时观察外部修改。 */
    val storedBottomBarItems = rememberObservedSetting(settings, BOTTOM_BAR_ITEMS_PREFERENCE_KEY) {
        getStringSet(BOTTOM_BAR_ITEMS_PREFERENCE_KEY, defaultBottomBarSelectionKeys(duo3HomeAccount, platformBottomBarItemLimit))
    }
    val storedBottomBarOrder = rememberObservedSetting(settings, BOTTOM_BAR_ITEM_ORDER_PREFERENCE_KEY) {
        getStringOrNull(BOTTOM_BAR_ITEM_ORDER_PREFERENCE_KEY)
    }

    fun currentBottomBarItemKeys(): List<String> = bottomBarItemOrderFromPreference(
        storedBottomBarOrder.value,
        normalizeBottomBarSelection(
            storedBottomBarItems.value,
            duo3HomeAccount,
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
            duo3HomeAccount,
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
    val highlightedKey = if (requestedKey == BOTTOM_BAR_ITEM_ORDER_PREFERENCE_KEY) BOTTOM_BAR_ITEMS_PREFERENCE_KEY else requestedKey

    DisposableEffect(Unit) {
        onDispose {
            onExit()
        }
    }

    AppSettingsPage(
        title = "导航与交互",
        onBack = LocalNavigator.current.onNavigateBack,
        backTag = NAVIGATION_SETTINGS_BACK_BUTTON_TAG,
        highlightedKey = highlightedKey,
        scrollTag = APPEARANCE_SETTINGS_SCROLL_TAG,
        pageTurnEnabled = !showBottomBarItemsSheet,
        overlays = {
            if (showBottomBarItemsSheet) {
                MyModalBottomSheet(
                    onDismissRequest = { showBottomBarItemsSheet = false },
                    sheetState = bottomBarSheetState,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        Text(
                            text = "底部导航栏项目",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = AppDimens.PageHorizontalPadding).padding(top = AppDimens.Space8),
                        )
                        Text(
                            text = "建议选择 3-5 项，可用箭头调整显示和滑动顺序。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(horizontal = AppDimens.PageHorizontalPadding)
                                .padding(top = AppDimens.Space4, bottom = AppDimens.Space12),
                        )
                        SettingItemGroup {
                            AppSortableCheckList(
                                items = (selectedBottomBarItemKeys + bottomBarItemLabels.keys.filter { it !in selectedBottomBarItemKeys })
                                    .mapNotNull { key ->
                                        bottomBarItemLabels[key]?.let { label ->
                                            SortableCheckItem(
                                                key = key,
                                                title = label,
                                                lockedReason = if (key == Account.name && (!duo3HomeAccount || Home.name !in selectedBottomBarItemKeys)) "底栏需要账号入口以保留设置出口" else null,
                                            )
                                        }
                                    },
                                checkedKeys = selectedBottomBarItemKeys,
                                onCheckedChange = ::toggleBottomBarItem,
                                onMove = ::moveBottomBarItem,
                                minChecked = 3,
                                maxChecked = platformBottomBarItemLimit,
                                minCheckedReason = "至少保留 3 项",
                                testTagPrefix = "appearanceSettings:bottomBar",
                            )
                        }
                        AppButton(
                            text = "完成",
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
            title = "导航",
            settingKey = APPEARANCE_SETTINGS_BOTTOM_BAR_SECTION_KEY,
        ) {
            val selectedBottomBarItems = selectedBottomBarItemKeys.mapNotNull { key ->
                bottomBarItemLabels[key]?.let { label -> key to label }
            }
            SettingItemSelect(
                title = { Text("应用启动默认页面") },
                description = { Text("仅可选择已在底部导航栏中显示的页面。") },
                options = selectedBottomBarItems.map { it.second },
                selectedIndex = selectedBottomBarItems.indexOfFirst { it.first == startDestinationKey },
                onSelect = { index ->
                    val (key, label) = selectedBottomBarItems[index]
                    storedStartDestination = key
                    userMessages.showShortMessage("已设置启动页：$label，重启后生效")
                },
                enabled = selectedBottomBarItems.isNotEmpty(),
                fieldModifier = Modifier.testTag(APPEARANCE_SETTINGS_START_DESTINATION_TAG),
                settingKey = START_DESTINATION_PREFERENCE_KEY,
            )

            SettingItem(
                title = { Text("底部导航栏项目") },
                description = { Text(selectedBottomBarItems.joinToString("、") { it.second }) },
                onClick = { showBottomBarItemsSheet = true },
                settingKey = BOTTOM_BAR_ITEMS_PREFERENCE_KEY,
            )

            var tapToRefresh by rememberSetting(settings, "bottomBarTapScrollToTop", true)
            SettingItemWithSwitch(
                title = { Text("点击底部导航栏回到顶部/刷新") },
                description = { Text("点击底部导航栏当前页面按钮回到顶部，已在顶部时则刷新页面。双击可直接刷新。") },
                checked = tapToRefresh,
                onCheckedChange = { tapToRefresh = it },
                settingKey = "bottomBarTapScrollToTop",
            )

            var autoHideBottomBar by rememberSetting(settings, "autoHideBottomBar", false)
            SettingItemWithSwitch(
                title = { Text("滚动时自动隐藏底部导航栏") },
                description = { Text("上划时隐藏底部导航栏，下划时重新显示。") },
                checked = autoHideBottomBar,
                onCheckedChange = { autoHideBottomBar = it },
                settingKey = "autoHideBottomBar",
            )

            var landscapeListDetail by rememberSetting(settings, LANDSCAPE_LIST_DETAIL_PREFERENCE_KEY, true)
            SettingItemWithSwitch(
                modifier = Modifier.testTag(APPEARANCE_SETTINGS_LANDSCAPE_LIST_DETAIL_TAG),
                title = { Text("横屏双栏布局") },
                description = { Text("在平板和电脑横屏时同时显示列表与详情。手机横屏始终使用单栏。") },
                checked = landscapeListDetail,
                onCheckedChange = { landscapeListDetail = it },
                settingKey = LANDSCAPE_LIST_DETAIL_PREFERENCE_KEY,
            )

            var collectionDirectBrowse by rememberSetting(settings, COLLECTION_DIRECT_BROWSE_PREFERENCE_KEY, false)
            SettingItemWithSwitch(
                modifier = Modifier.testTag(APPEARANCE_SETTINGS_COLLECTION_DIRECT_BROWSE_TAG),
                title = { Text("收藏直达浏览（测试）") },
                description = { Text("从底栏收藏夹直接浏览收藏内容，支持切换收藏夹、顺序浏览与随机浏览。此功能仍在测试，请谨慎开启。") },
                checked = collectionDirectBrowse,
                onCheckedChange = { collectionDirectBrowse = it },
                settingKey = COLLECTION_DIRECT_BROWSE_PREFERENCE_KEY,
            )
        }

        SettingItemGroup(title = "返回与分享") {
            SettingItemWithSwitch(
                title = { Text("预测性返回") },
                description = { Text("开启后页面使用所选转场；关闭后页面直接切换，不播放转场动画。") },
                checked = predictiveBackEnabled,
                onCheckedChange = { predictiveBackEnabled = it },
                settingKey = "enable_predictive_back",
            )

            var pageTransition by rememberSetting(settings, PAGE_TRANSITION_PREFERENCE_KEY, "miuix")
            val pageTransitionOptions = arrayOf("miuix" to "侧滑", "aosp" to "AOSP")
            SettingItemSelect(
                modifier = Modifier.testTag("navigation_settings_page_transition"),
                title = { Text("页面转场") },
                description = { Text("选择侧滑或 AOSP 风格。") },
                options = pageTransitionOptions.map { it.second },
                selectedIndex = pageTransitionOptions.indexOfFirst { it.first == pageTransition }.coerceAtLeast(0),
                onSelect = { index -> pageTransition = pageTransitionOptions[index].first },
                enabled = predictiveBackEnabled,
                settingKey = PAGE_TRANSITION_PREFERENCE_KEY,
            )

            var shareActionMode by rememberSetting(settings, "shareActionMode", "ask")
            val shareActionOptions = listOf(
                "ask" to "询问",
                "copy" to "复制链接",
                "share" to "Android分享",
            )
            SettingItemSelect(
                title = { Text("分享操作") },
                description = { Text("点击分享按钮时的默认行为。") },
                options = shareActionOptions.map { it.second },
                selectedIndex = shareActionOptions.indexOfFirst { it.first == shareActionMode },
                onSelect = { index ->
                    val (mode, label) = shareActionOptions[index]
                    shareActionMode = mode
                    userMessages.showShortMessage("已设置为：$label")
                },
                settingKey = "shareAction",
            )
        }

        SettingItemGroup(title = "搜索") {
            var showSearchHotSearch by rememberSetting(settings, "showSearchHotSearch", true)
            SettingItemWithSwitch(
                title = { Text("搜索界面显示热搜") },
                description = { Text("在搜索界面空白时显示知乎热搜关键词。") },
                checked = showSearchHotSearch,
                onCheckedChange = { showSearchHotSearch = it },
                settingKey = "showSearchHotSearch",
            )
            var showSearchHistory by rememberSetting(settings, "showSearchHistory", true)
            SettingItemWithSwitch(
                title = { Text("记录并显示搜索历史") },
                description = { Text("在搜索界面显示最近搜索过的关键词，关闭后不再记录新的搜索。") },
                checked = showSearchHistory,
                onCheckedChange = { showSearchHistory = it },
                settingKey = "showSearchHistory",
            )
        }
    }
}
