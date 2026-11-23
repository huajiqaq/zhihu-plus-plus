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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppDialog
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppDropdownMenu
import com.github.zly2006.zhihu.ui.components.AppDropdownMenuItem
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppSectionTitle
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.viewmodel.feed.SearchSuggestItem
import kotlinx.serialization.Serializable
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

@Serializable
internal data class HotSearchItem(
    val query: String,
    val hotShow: String = "",
    val label: String = "",
)

@Composable
internal fun SearchScreenLandingContent(
    suggestItems: List<SearchSuggestItem>,
    showSearchHistory: Boolean,
    searchHistoryItems: List<String>,
    showHotSearch: Boolean,
    hotSearchItems: List<HotSearchItem>,
    suggestionScrollState: ScrollState,
    historyMoreMenuExpanded: Boolean,
    hotSearchMoreMenuExpanded: Boolean,
    historyHeader: @Composable (Boolean) -> Unit,
    isMemberSearch: Boolean,
    memberSearchName: String,
    onHotSearchMoreMenuExpandedChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    onRefreshHotSearch: () -> Unit,
    onDisableHotSearch: () -> Unit,
) {
    if (suggestItems.isNotEmpty()) {
        SearchScreenSuggestions(
            suggestItems = suggestItems,
            onSelect = onSelect,
        )
    } else if (
        showSearchHistory && searchHistoryItems.isNotEmpty() || showHotSearch && hotSearchItems.isNotEmpty()
    ) {
        SearchScreenHistoryAndHotSearch(
            showSearchHistory = showSearchHistory,
            searchHistoryItems = searchHistoryItems,
            showHotSearch = showHotSearch,
            hotSearchItems = hotSearchItems,
            suggestionScrollState = suggestionScrollState,
            historyMoreMenuExpanded = historyMoreMenuExpanded,
            hotSearchMoreMenuExpanded = hotSearchMoreMenuExpanded,
            historyHeader = historyHeader,
            onHotSearchMoreMenuExpandedChange = onHotSearchMoreMenuExpandedChange,
            onSelect = onSelect,
            onRefreshHotSearch = onRefreshHotSearch,
            onDisableHotSearch = onDisableHotSearch,
        )
    } else {
        if (showSearchHistory || isMemberSearch) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                if (showSearchHistory) {
                    historyHeader(false)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Text(
                    text = if (isMemberSearch) {
                        "输入关键词搜索 $memberSearchName 的创作"
                    } else {
                        "暂无搜索历史"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Text(
                text = "请输入搜索内容",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        }
    }
}

@Composable
internal fun SearchScreenHistoryHeader(
    showClearAction: Boolean,
    historyMoreMenuExpanded: Boolean,
    onHistoryMoreMenuExpandedChange: (Boolean) -> Unit,
    showClearHistoryConfirm: Boolean,
    onShowClearHistoryConfirmChange: (Boolean) -> Unit,
    historyCount: Int,
    onConfirmClearHistory: () -> Unit,
    onDisableHistory: () -> Unit,
) {
    AppSectionTitle(text = "搜索历史") {
        AppIconButton(
            onClick = { onHistoryMoreMenuExpandedChange(true) },
            modifier = Modifier
                .size(AppDimens.TouchTarget)
                .testTag("search_history_more_button"),
        ) {
            Icon(Icons.Default.MoreVert, contentDescription = "更多", modifier = Modifier.size(18.dp))
            AppDropdownMenu(
                expanded = historyMoreMenuExpanded,
                onDismissRequest = { onHistoryMoreMenuExpandedChange(false) },
            ) {
                if (showClearAction) {
                    AppDropdownMenuItem(
                        text = { Text("清空搜索历史") },
                        onClick = {
                            onHistoryMoreMenuExpandedChange(false)
                            onShowClearHistoryConfirmChange(true)
                        },
                    )
                }
                AppDropdownMenuItem(
                    text = { Text("关闭搜索历史") },
                    onClick = {
                        onHistoryMoreMenuExpandedChange(false)
                        onDisableHistory()
                    },
                )
            }
        }
    }
    if (showClearHistoryConfirm) {
        AppDialog(
            title = "清空 $historyCount 条搜索历史？",
            summary = "清空后无法恢复。",
            confirmText = "清空",
            onConfirm = {
                onShowClearHistoryConfirmChange(false)
                onConfirmClearHistory()
            },
            dismissText = "取消",
            onDismissRequest = { onShowClearHistoryConfirmChange(false) },
            destructive = true,
        )
    }
}

@Composable
private fun SearchScreenHistoryAndHotSearch(
    showSearchHistory: Boolean,
    searchHistoryItems: List<String>,
    showHotSearch: Boolean,
    hotSearchItems: List<HotSearchItem>,
    suggestionScrollState: ScrollState,
    historyMoreMenuExpanded: Boolean,
    hotSearchMoreMenuExpanded: Boolean,
    historyHeader: @Composable (Boolean) -> Unit,
    onHotSearchMoreMenuExpandedChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    onRefreshHotSearch: () -> Unit,
    onDisableHotSearch: () -> Unit,
) {
    val shouldShowHistory = showSearchHistory && searchHistoryItems.isNotEmpty()
    val shouldShowHotSearch = showHotSearch && hotSearchItems.isNotEmpty()
    val pageTurnTarget = rememberPageTurnTarget(
        scrollState = suggestionScrollState,
        enabled = !historyMoreMenuExpanded && !hotSearchMoreMenuExpanded,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pageTurnViewportWithGuide(pageTurnTarget)
            .verticalScroll(suggestionScrollState)
            .padding(16.dp)
            .testTag("search_hot_list"),
    ) {
        if (shouldShowHistory) {
            historyHeader(true)
            Spacer(modifier = Modifier.height(8.dp))
            searchHistoryItems.forEach { query ->
                SettingItem(
                    title = { Text(query, style = LocalAppSkinTokens.current.main) },
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    onClick = { onSelect(query) },
                    endAction = {},
                )
            }
            if (shouldShowHotSearch) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        if (shouldShowHotSearch) {
            AppSectionTitle(text = "热搜") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIconButton(
                        onClick = onRefreshHotSearch,
                        modifier = Modifier
                            .size(AppDimens.TouchTarget)
                            .testTag("search_hot_refresh_button"),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新热搜", modifier = Modifier.size(18.dp))
                    }
                    AppIconButton(
                        onClick = { onHotSearchMoreMenuExpandedChange(true) },
                        modifier = Modifier
                            .size(AppDimens.TouchTarget)
                            .testTag("search_hot_more_button"),
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多", modifier = Modifier.size(18.dp))
                        AppDropdownMenu(
                            expanded = hotSearchMoreMenuExpanded,
                            onDismissRequest = { onHotSearchMoreMenuExpandedChange(false) },
                        ) {
                            AppDropdownMenuItem(
                                text = { Text("关闭热搜显示") },
                                onClick = {
                                    onHotSearchMoreMenuExpandedChange(false)
                                    onDisableHotSearch()
                                },
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Column {
                hotSearchItems.forEachIndexed { index, item ->
                    SettingItem(
                        title = { Text(item.query, style = LocalAppSkinTokens.current.main) },
                        icon = {
                            Text(
                                "${index + 1}",
                                style = LocalAppSkinTokens.current.footnote1,
                                color = if (index < 3) LocalAppSkinTokens.current.primary else LocalAppSkinTokens.current.onSurfaceVariantSummary,
                            )
                        },
                        onClick = { onSelect(item.query) },
                        endAction = {
                            if (item.hotShow.isNotEmpty()) {
                                Text(item.hotShow, style = LocalAppSkinTokens.current.footnote2, color = LocalAppSkinTokens.current.onSurfaceVariantSummary)
                            }
                        },
                    )
                }
            }
        }
    }
}
