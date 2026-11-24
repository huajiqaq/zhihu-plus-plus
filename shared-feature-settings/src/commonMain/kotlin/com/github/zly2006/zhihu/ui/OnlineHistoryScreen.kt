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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zly2006.zhihu.navigation.History
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.requestLoginNavigation
import com.github.zly2006.zhihu.platform.PlatformBackHandler
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.reading.RegisterReadingQueueSource
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppDialog
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppDropdownMenu
import com.github.zly2006.zhihu.ui.components.AppDropdownMenuItem
import com.github.zly2006.zhihu.ui.components.AppEmptyState
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppOutlinedButton
import com.github.zly2006.zhihu.ui.components.AppPageTitleBar
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.AppSpinner
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
import com.github.zly2006.zhihu.viewmodel.feed.OnlineHistoryViewModel
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

const val ONLINE_HISTORY_OVERFLOW_TAG = "online_history_overflow"
const val ONLINE_HISTORY_BACK_TAG = "online_history_back"
const val ONLINE_HISTORY_GUEST_TAG = "online_history_guest"
const val ONLINE_HISTORY_CLEARING_TAG = "online_history_clearing"
const val HISTORY_RECORDING_TOGGLE_TAG = "history_recording_toggle"

@Composable
fun OnlineHistoryScreen(scrollToTopTrigger: Int = 0, isActive: Boolean = true, onBack: (() -> Unit)? = null) {
    val navigator = LocalNavigator.current
    val model: OnlineHistoryViewModel = viewModel { OnlineHistoryViewModel() }
    val account by rememberAccountSettingsAccountState()
    val environment = rememberPaginationEnvironment(allowGuestAccess = false)
    val accountClient = environment.httpClient()
    val userMessages = rememberUserMessageSink()
    key(account.login, account.id, accountClient) {
        val scope = rememberCoroutineScope()
        val listState = rememberLazyListState()
        var cachedTrigger by remember { mutableIntStateOf(scrollToTopTrigger) }
        var selectedType by rememberSaveable(account.login, account.id, accountClient) { mutableStateOf<String?>(null) }
        var selectionMode by rememberSaveable(account.login, account.id, accountClient) { mutableStateOf(false) }
        var selectedKeys by rememberSaveable(
            account.login,
            account.id,
            accountClient,
            stateSaver = Saver<Set<String>, Array<String>>(
                save = { it.toTypedArray() },
                restore = { it.toSet() },
            ),
        ) { mutableStateOf(emptySet()) }
        var showActions by remember(account.login, account.id, accountClient) { mutableStateOf(false) }
        var showClear by remember(account.login, account.id, accountClient) { mutableStateOf(false) }
        var showDelete by remember(account.login, account.id, accountClient) { mutableStateOf(false) }
        var showDisable by remember(account.login, account.id, accountClient) { mutableStateOf(false) }
        var deleting by remember(account.login, account.id, accountClient) { mutableStateOf(false) }
        var clearing by remember(account.login, account.id, accountClient) { mutableStateOf(false) }
        val busy = deleting || clearing || model.isChangingRecording
        val items = if (selectedType == null) model.displayItems else model.displayItems.filter { it.historyType == selectedType }
        val visibleKeys = items.mapTo(linkedSetOf()) { it.stableKey }
        val allSelected = visibleKeys.isNotEmpty() && visibleKeys.all { it in selectedKeys }
        val pageTurnTarget = rememberPageTurnTarget(
            listState,
            enabled = isActive &&
                account.login &&
                model.recordingEnabled &&
                !selectionMode &&
                !busy &&
                !showActions &&
                !showClear &&
                !showDelete &&
                !showDisable,
        )
        val titleBarState = rememberAppPageTitleBarScrollState()

        if (isActive && account.login && model.recordingEnabled) {
            RegisterReadingQueueSource(sourceId = "history:online", items = model.displayItems)
        }
        LaunchedEffect(account.login, account.id, accountClient) {
            showActions = false
            showClear = false
            showDelete = false
            showDisable = false
            if (account.login) {
                model.loadFor(environment, account.id)
                try {
                    model.loadRecordingSetting(environment, account.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    userMessages.showShortMessage("记录状态获取失败：${e.message}")
                }
            } else {
                model.clearForGuest()
            }
        }
        val loadedKeys = model.displayItems.mapTo(hashSetOf()) { it.stableKey }
        LaunchedEffect(loadedKeys, model.isLoading) {
            if (!model.isLoading) selectedKeys = selectedKeys.intersect(loadedKeys)
        }
        LaunchedEffect(scrollToTopTrigger, isActive) {
            if (isActive && account.login && model.recordingEnabled && !busy) {
                when (topLevelReselectAction(scrollToTopTrigger - cachedTrigger, listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0)) {
                    TopLevelReselectAction.Refresh -> model.refresh(environment)
                    TopLevelReselectAction.ScrollToTop -> listState.animateScrollToItem(0)
                    null -> Unit
                }
            }
            cachedTrigger = scrollToTopTrigger
        }
        PlatformBackHandler(enabled = isActive && selectionMode && !showActions && !showClear && !showDelete && !showDisable && !busy) {
            selectionMode = false
            selectedKeys = emptySet()
        }

        AppScaffold(
            modifier = Modifier.fillMaxSize().appPageTitleBarScroll(titleBarState),
            topBar = {
                AppPageTitleBar(
                    title = if (selectionMode) "已选 ${selectedKeys.size} 项" else "在线浏览历史",
                    scrollState = titleBarState,
                    onBack = if (selectionMode) {
                        (
                            {
                                selectionMode = false
                                selectedKeys = emptySet()
                            }
                        )
                    } else {
                        onBack
                    },
                    backTag = ONLINE_HISTORY_BACK_TAG,
                    actions = {
                        if (account.login) {
                            HistorySelectAction(selectionMode, {
                                selectionMode = !selectionMode
                                selectedKeys = emptySet()
                            }, enabled = !busy && model.recordingEnabled)
                            AppIconButton(onClick = { showActions = true }, modifier = Modifier.testTag(ONLINE_HISTORY_OVERFLOW_TAG), enabled = !busy) {
                                Icon(Icons.Default.MoreVert, "更多选项")
                                AppDropdownMenu(expanded = isActive && showActions, onDismissRequest = { showActions = false }) {
                                    AppDropdownMenuItem(text = { Text("查看本地历史记录") }, onClick = {
                                        showActions = false
                                        navigator.onNavigate(History)
                                    })
                                    AppDropdownMenuItem(text = { Text("批量删除") }, enabled = !busy && model.recordingEnabled, onClick = {
                                        showActions = false
                                        selectedKeys = emptySet()
                                        selectionMode = true
                                    })
                                    AppDropdownMenuItem(text = { Text("清空浏览记录") }, enabled = model.recordingEnabled, onClick = {
                                        showActions = false
                                        showClear = true
                                    })
                                    AppDropdownMenuItem(
                                        modifier = Modifier.testTag(HISTORY_RECORDING_TOGGLE_TAG),
                                        text = { Text(if (model.recordingEnabled) "关闭最近浏览" else "开启最近浏览") },
                                        onClick = {
                                            showActions = false
                                            if (model.recordingEnabled) {
                                                showDisable = true
                                            } else {
                                                scope.launch {
                                                    try {
                                                        model.setRecordingEnabled(environment, true)
                                                    } catch (e: CancellationException) {
                                                        throw e
                                                    } catch (e: Exception) {
                                                        userMessages.showShortMessage("开启失败：${e.message}")
                                                    }
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
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
                        enabled = !busy,
                    )
                }
            },
        ) { padding ->
            when {
                !account.login -> OnlineHistoryGuestContent(::requestLoginNavigation, { navigator.onNavigate(History) }, Modifier.padding(padding))
                !model.recordingEnabled -> AppEmptyState(
                    title = "最近浏览已关闭",
                    modifier = Modifier.fillMaxSize().padding(padding),
                    icon = Icons.Default.History,
                    action = {
                        AppButton(text = "点击开启", enabled = !busy, onClick = {
                            scope.launch {
                                try {
                                    model.setRecordingEnabled(environment, true)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    userMessages.showShortMessage("开启失败：${e.message}")
                                }
                            }
                        })
                    },
                )
                else -> FeedPullToRefresh(model, environment, padding = padding) {
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        if (clearing) OnlineHistoryClearingRow()
                        HistoryTypeFilterRow(selectedType, model.displayItems.mapNotNullTo(linkedSetOf()) { it.historyType }, {
                            selectedType = it
                            listState.requestScrollToItem(0)
                        }, enabled = !busy)
                        PaginatedList(
                            items = items,
                            listState = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .pageTurnViewportWithGuide(pageTurnTarget)
                                .testTag("online_history_list"),
                            onLoadMore = { if (!busy) model.loadMore(environment) },
                            isEnd = { model.isEnd },
                            isLoading = { model.isLoading },
                            loadError = { model.errorMessage },
                            onRetry = { if (!busy) model.retry(environment) },
                            key = { it.stableKey },
                        ) { item ->
                            val toggle = { selectedKeys = if (item.stableKey in selectedKeys) selectedKeys - item.stableKey else selectedKeys + item.stableKey }
                            val card: @Composable () -> Unit = {
                                FeedCard(
                                    item,
                                    readingQueueSourceId = "history:online".takeIf { isActive && !selectionMode },
                                    titleTrailingContent = { HistoryTypeBadge(item.historyType, item.titleBadges) },
                                    onClick = if (selectionMode) ({ _, _ -> if (!busy) toggle() }) else null,
                                    menuItems = { dismiss ->
                                        AppDropdownMenuItem(text = { Text("删除此条浏览记录") }, enabled = !busy, onClick = {
                                            dismiss()
                                            scope.launch {
                                                deleting = true
                                                try {
                                                    if (model.deleteItems(environment, setOf(item.stableKey))) userMessages.showShortMessage("已删除")
                                                } catch (e: CancellationException) {
                                                    throw e
                                                } catch (e: Exception) {
                                                    userMessages.showShortMessage("删除失败：${e.message}")
                                                } finally {
                                                    if (environment.httpClient() === accountClient && model.accountId == account.id) deleting = false
                                                }
                                            }
                                        })
                                    },
                                )
                            }
                            if (selectionMode) HistorySelectableRow(item.stableKey in selectedKeys, { if (!busy) toggle() }, card, enabled = !busy) else card()
                        }
                    }
                }
            }
        }
        if (isActive && showDisable) {
            AppDialog(
                title = "确认关闭",
                summary = "关闭后当前账号的浏览记录及本机历史将被清空，且不会再被记录。",
                confirmText = "确认关闭",
                dismissText = "取消",
                destructive = true,
                confirmEnabled = !busy,
                onDismissRequest = { showDisable = false },
                onDismiss = { showDisable = false },
                onConfirm = {
                    showDisable = false
                    scope.launch {
                        try {
                            if (model.setRecordingEnabled(environment, false)) {
                                selectionMode = false
                                selectedKeys = emptySet()
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            userMessages.showShortMessage("关闭失败：${e.message}")
                        }
                    }
                },
            )
        }
        if (isActive && showClear) {
            AppDialog(
                title = "确认清空吗？",
                summary = "会清空当前账号的在线历史和本机全部历史，清空后无法恢复。",
                confirmText = "清空吧",
                dismissText = "先不了",
                destructive = true,
                confirmEnabled = !busy,
                onDismissRequest = { showClear = false },
                onDismiss = { showClear = false },
                onConfirm = {
                    showClear = false
                    scope.launch {
                        clearing = true
                        try {
                            if (model.clearAllHistory(environment)) selectedKeys = emptySet()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            userMessages.showShortMessage("清空失败：${e.message}")
                        } finally {
                            if (environment.httpClient() === accountClient && model.accountId == account.id) clearing = false
                        }
                    }
                },
            )
        }
        if (isActive && showDelete) {
            AppDialog(
                title = "删除选中的 ${selectedKeys.size} 条浏览记录？",
                summary = "会删除所有已选记录（包括其他筛选类型），不影响未选或未加载记录。",
                confirmText = "删除",
                dismissText = "取消",
                destructive = true,
                confirmEnabled = !busy && selectedKeys.isNotEmpty(),
                onDismissRequest = { showDelete = false },
                onDismiss = { showDelete = false },
                onConfirm = {
                    showDelete = false
                    val keys = selectedKeys
                    scope.launch {
                        deleting = true
                        try {
                            if (model.deleteItems(environment, keys)) {
                                selectedKeys = emptySet()
                                selectionMode = false
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            userMessages.showShortMessage("删除失败：${e.message}")
                        } finally {
                            if (environment.httpClient() === accountClient && model.accountId == account.id) deleting = false
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun OnlineHistoryClearingRow() {
    val skin = LocalAppSkinTokens.current
    Row(Modifier.fillMaxWidth().padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space8).testTag(ONLINE_HISTORY_CLEARING_TAG), horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12), verticalAlignment = Alignment.CenterVertically) {
        AppSpinner(size = AppDimens.InlineSpinnerSize)
        Text("正在清除历史记录…", style = skin.body2, color = skin.onSurfaceVariantSummary)
    }
}

@Composable
private fun OnlineHistoryGuestContent(onLogin: () -> Unit, onOpenLocalHistory: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag(ONLINE_HISTORY_GUEST_TAG)) {
        AppEmptyState(
            "登录后可查看在线历史",
            description = "在线历史保存在知乎账号里。本机打开过的内容记在本地历史中，不需要登录。",
            icon = Icons.Default.History,
            action = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppDimens.Space8)) {
                    AppButton(text = "登录知乎", onClick = onLogin)
                    AppOutlinedButton(text = "查看本地历史", onClick = onOpenLocalHistory)
                }
            },
        )
    }
}
