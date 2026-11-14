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

package com.github.zly2006.zhihu.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zly2006.zhihu.data.HISTORY_RECORDING_ENABLED_KEY
import com.github.zly2006.zhihu.data.LocalHistoryStorage
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.platform.PlatformBackHandler
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.reading.RegisterReadingQueueSource
import com.github.zly2006.zhihu.ui.components.AppDialog
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppDropdownMenuItem
import com.github.zly2006.zhihu.ui.components.AppEmptyState
import com.github.zly2006.zhihu.ui.components.AppPageTitleBar
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.FeedCard
import com.github.zly2006.zhihu.ui.components.FeedPullToRefresh
import com.github.zly2006.zhihu.ui.components.HistorySelectAction
import com.github.zly2006.zhihu.ui.components.HistorySelectableRow
import com.github.zly2006.zhihu.ui.components.HistorySelectionBar
import com.github.zly2006.zhihu.ui.components.HistoryTypeBadge
import com.github.zly2006.zhihu.ui.components.HistoryTypeFilterRow
import com.github.zly2006.zhihu.ui.components.PaginatedList
import com.github.zly2006.zhihu.ui.components.appPageTitleBarScroll
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.ui.components.rememberAppPageTitleBarScrollState
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.viewmodel.feed.HistoryViewModel
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment

const val LEGACY_LOCAL_HISTORY_BACK_BUTTON_TAG = "legacy_local_history_back_button"
const val LEGACY_LOCAL_HISTORY_SCREEN_TAG = "legacy_local_history_screen"
const val LEGACY_LOCAL_HISTORY_LIST_TAG = "legacy_local_history_list"

@Composable
fun LegacyLocalHistoryScreen() {
    val navigator = LocalNavigator.current
    val model: HistoryViewModel = viewModel { HistoryViewModel() }
    val environment = rememberPaginationEnvironment(allowGuestAccess = true)
    val messages = rememberUserMessageSink()
    val settings = rememberSettingsStore()
    val storage = remember { LocalHistoryStorage() }
    var recordingEnabled by rememberObservedSetting(settings, HISTORY_RECORDING_ENABLED_KEY) { getBoolean(HISTORY_RECORDING_ENABLED_KEY, true) }
    val listState = rememberLazyListState()
    var selectedType by rememberSaveable { mutableStateOf<String?>(null) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedKeys by rememberSaveable(
        stateSaver = Saver<Set<String>, Array<String>>(
            save = { it.toTypedArray() },
            restore = { it.toSet() },
        ),
    ) { mutableStateOf(emptySet()) }
    var showDelete by remember { mutableStateOf(false) }
    val items = if (selectedType == null) model.displayItems else model.displayItems.filter { it.historyType == selectedType }
    val visibleKeys = items.mapTo(linkedSetOf()) { it.stableKey }
    val allSelected = visibleKeys.isNotEmpty() && visibleKeys.all { it in selectedKeys }
    val pageTurnTarget = rememberPageTurnTarget(listState, enabled = !selectionMode && recordingEnabled && !showDelete)
    val titleBarState = rememberAppPageTitleBarScrollState()

    if (recordingEnabled) RegisterReadingQueueSource(sourceId = "history:local", items = model.displayItems)
    LaunchedEffect(recordingEnabled) { model.refresh(environment) }
    val loadedKeys = model.displayItems.mapTo(hashSetOf()) { it.stableKey }
    LaunchedEffect(loadedKeys, model.isLoading) {
        if (!model.isLoading) selectedKeys = selectedKeys.intersect(loadedKeys)
    }
    PlatformBackHandler(enabled = selectionMode && !showDelete) {
        selectionMode = false
        selectedKeys = emptySet()
    }

    AppScaffold(
        modifier = Modifier.fillMaxSize().appPageTitleBarScroll(titleBarState).testTag(LEGACY_LOCAL_HISTORY_SCREEN_TAG),
        topBar = {
            AppPageTitleBar(
                title = if (selectionMode) "已选 ${selectedKeys.size} 项" else "本地浏览历史",
                scrollState = titleBarState,
                onBack = if (selectionMode) {
                    (
                        {
                            selectionMode = false
                            selectedKeys = emptySet()
                        }
                    )
                } else {
                    navigator.onNavigateBack
                },
                backTag = LEGACY_LOCAL_HISTORY_BACK_BUTTON_TAG,
                actions = {
                    AppTextButton(text = if (recordingEnabled) "停止记录" else "开始记录", onClick = {
                        val enabled = !recordingEnabled
                        storage.setRecordingEnabled(enabled)
                        recordingEnabled = enabled
                    }, modifier = Modifier.testTag("local_history_recording_toggle"))
                    HistorySelectAction(selectionMode, {
                        selectionMode = !selectionMode
                        selectedKeys = emptySet()
                    })
                },
            )
        },
        bottomBar = {
            if (selectionMode) {
                HistorySelectionBar(
                    selectedKeys.size,
                    visibleKeys.size,
                    allSelected,
                    onSelectAll = { selectedKeys = if (allSelected) selectedKeys - visibleKeys else selectedKeys + visibleKeys },
                    onDelete = { showDelete = true },
                )
            }
        },
    ) { padding ->
        FeedPullToRefresh(model, environment, padding = padding) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (!recordingEnabled) Text("已停止记录新浏览，本地已有记录保留。", modifier = Modifier.padding(AppDimens.PageHorizontalPadding))
                HistoryTypeFilterRow(selectedType, model.displayItems.mapNotNullTo(linkedSetOf()) { it.historyType }, {
                    selectedType = it
                    listState.requestScrollToItem(0)
                })
                if (items.isEmpty() && !model.isLoading && model.errorMessage == null) {
                    AppEmptyState("暂无本地历史", modifier = Modifier.weight(1f), icon = Icons.Default.History)
                } else {
                    PaginatedList(
                        items = items,
                        listState = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .pageTurnViewportWithGuide(pageTurnTarget)
                            .testTag(LEGACY_LOCAL_HISTORY_LIST_TAG),
                        onLoadMore = {},
                        isEnd = { true },
                        isLoading = { model.isLoading },
                        loadError = { model.errorMessage },
                        onRetry = { model.refresh(environment) },
                        key = { it.stableKey },
                    ) { item ->
                        val toggle = { selectedKeys = if (item.stableKey in selectedKeys) selectedKeys - item.stableKey else selectedKeys + item.stableKey }
                        val card: @Composable () -> Unit = {
                            FeedCard(
                                item,
                                readingQueueSourceId = "history:local".takeUnless { selectionMode },
                                titleTrailingContent = { HistoryTypeBadge(item.historyType, item.titleBadges) },
                                onClick = if (selectionMode) ({ _, _ -> toggle() }) else null,
                                menuItems = { dismiss ->
                                    AppDropdownMenuItem(text = { Text("删除此条浏览记录") }, onClick = {
                                        dismiss()
                                        try {
                                            model.removeItems(setOf(item.stableKey))
                                        } catch (e: Exception) {
                                            messages.showShortMessage("删除失败：${e.message}")
                                        }
                                    })
                                },
                            )
                        }
                        if (selectionMode) HistorySelectableRow(item.stableKey in selectedKeys, toggle, card) else card()
                    }
                }
            }
        }
    }
    if (showDelete) {
        AppDialog(
            title = "删除选中的 ${selectedKeys.size} 条本地历史？",
            summary = "只删除本机选中记录，不影响知乎账号的在线历史。",
            confirmText = "删除",
            dismissText = "取消",
            destructive = true,
            onDismissRequest = { showDelete = false },
            onDismiss = { showDelete = false },
            onConfirm = {
                showDelete = false
                try {
                    model.removeItems(selectedKeys)
                    selectionMode = false
                    selectedKeys = emptySet()
                } catch (e: Exception) {
                    messages.showShortMessage("删除失败：${e.message}")
                }
            },
        )
    }
}
