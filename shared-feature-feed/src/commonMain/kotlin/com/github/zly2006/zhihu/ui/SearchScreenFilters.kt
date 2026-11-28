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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.github.zly2006.zhihu.i18n.FeedStrings
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppBottomSheet
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppCard
import com.github.zly2006.zhihu.ui.components.AppChip
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppIcon
import com.github.zly2006.zhihu.ui.components.AppOutlinedButton
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.viewmodel.feed.SearchContentType
import com.github.zly2006.zhihu.viewmodel.feed.SearchFilters
import com.github.zly2006.zhihu.viewmodel.feed.SearchSortOption
import com.github.zly2006.zhihu.viewmodel.feed.SearchTimeRange

@Composable
internal fun SearchFilterEntry(filters: SearchFilters, onClick: () -> Unit) {
    val skin = LocalAppSkinTokens.current
    val activeCount = listOf(
        filters.sort != SearchSortOption.Default,
        filters.contentType != SearchContentType.All,
        filters.timeRange != SearchTimeRange.All,
    ).count { it }
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space8)
            .testTag("search_filter_button"),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.Space12)
                .testTag("search_filter_context"),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(Icons.Default.FilterList, contentDescription = null, tint = if (activeCount > 0) skin.primary else skin.onSurface)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppDimens.Space2)) {
                Text(if (activeCount > 0) FeedStrings.activeFilters(activeCount) else FeedStrings.filters, style = skin.main)
                Text("${filters.sort.label} · ${filters.contentType.label} · ${filters.timeRange.label}", style = skin.footnote1, color = skin.onSurfaceVariantSummary)
            }
            AppIcon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = skin.onSurfaceVariantActions)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchFilterMenu(
    filters: SearchFilters,
    onFiltersChange: (SearchFilters) -> Unit,
    onDismissRequest: () -> Unit,
    onApply: () -> Unit,
) {
    val skin = LocalAppSkinTokens.current
    AppBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
        modifier = Modifier.testTag("search_filter_panel"),
        showMiuixCloseButton = true,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.PageHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Space16),
        ) {
            Text(FeedStrings.filterResults, style = skin.title3)
            SearchFilterGroup(FeedStrings.sortOrder) {
                SearchSortOption.entries.forEach { option ->
                    AppChip(option.label, filters.sort == option, { onFiltersChange(filters.copy(sort = option)) }, modifier = Modifier.heightIn(min = AppDimens.TouchTarget).testTag("search_filter_sort_${option.name}"))
                }
            }
            SearchFilterGroup(FeedStrings.contentType) {
                SearchContentType.entries.forEach { type ->
                    AppChip(type.label, filters.contentType == type, { onFiltersChange(filters.copy(contentType = type)) }, modifier = Modifier.heightIn(min = AppDimens.TouchTarget).testTag("search_filter_type_${type.name}"))
                }
            }
            SearchFilterGroup(FeedStrings.timeRange) {
                SearchTimeRange.entries.forEach { range ->
                    AppChip(range.label, filters.timeRange == range, { onFiltersChange(filters.copy(timeRange = range)) }, modifier = Modifier.heightIn(min = AppDimens.TouchTarget).testTag("search_filter_time_${range.name}"))
                }
            }
            Spacer(Modifier.height(AppDimens.Space4))
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(AppDimens.PageHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Space8),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            AppTextButton(FeedStrings.reset, { onFiltersChange(SearchFilters()) }, enabled = filters != SearchFilters(), modifier = Modifier.width(IntrinsicSize.Max).testTag("search_filter_reset"))
            AppOutlinedButton(FeedStrings.cancel, onDismissRequest, modifier = Modifier.width(IntrinsicSize.Max).testTag("search_filter_cancel"))
            AppButton(FeedStrings.applyFilters, onApply, modifier = Modifier.width(IntrinsicSize.Max).testTag("search_filter_apply"))
        }
    }
}

@Composable
private fun SearchFilterGroup(title: String, content: @Composable FlowRowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Space8)) {
        Text(title, style = LocalAppSkinTokens.current.main)
        FlowRow(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Space8),
            content = content,
        )
    }
}
