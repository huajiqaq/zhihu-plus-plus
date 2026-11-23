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

package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.data.HistoryType
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens

const val HISTORY_TYPE_FILTER_TAG_PREFIX = "history_type_filter_"
const val HISTORY_SELECT_TOGGLE_TAG = "history_select_toggle"
const val HISTORY_SELECT_ALL_TAG = "history_select_all"
const val HISTORY_DELETE_SELECTED_TAG = "history_delete_selected"
const val HISTORY_BADGE_TAG_PREFIX = "history_badge_"

@Composable
fun HistoryTypeFilterRow(
    selectedType: String?,
    availableTypes: Set<String>,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8),
        contentPadding = PaddingValues(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space8),
    ) {
        item(key = "all") {
            AppChip(
                label = "全部",
                enabled = enabled,
                selected = selectedType == null,
                onClick = { onSelect(null) },
                modifier = Modifier.testTag(HISTORY_TYPE_FILTER_TAG_PREFIX + "all"),
            )
        }
        for (type in HistoryType.filters) {
            if (type !in availableTypes) continue
            item(key = type) {
                AppChip(
                    label = type,
                    enabled = enabled,
                    selected = selectedType == type,
                    onClick = { onSelect(type) },
                    modifier = Modifier.testTag(HISTORY_TYPE_FILTER_TAG_PREFIX + type),
                )
            }
        }
    }
}

/** 与标题同行；在线徽标优先使用服务端 header.badges 图片，缺省才显示类型文字。 */
@Composable
fun HistoryTypeBadge(type: String?, images: Array<String> = emptyArray()) {
    val label = HistoryType.badgeType(type)
    val skin = LocalAppSkinTokens.current
    Row(
        modifier = Modifier.testTag(HISTORY_BADGE_TAG_PREFIX + label),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.Space4),
    ) {
        if (images.isEmpty()) {
            Text(
                text = label,
                style = skin.footnote2,
                color = skin.onSurfaceVariantSummary,
                maxLines = 1,
            )
        } else {
            for (image in images) {
                AsyncImage(
                    model = image,
                    contentDescription = label,
                    modifier = Modifier.size(AppDimens.IconSizeSmall),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

@Composable
fun HistorySelectableRow(
    selected: Boolean,
    onToggle: () -> Unit,
    card: @Composable () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().toggleable(
            value = selected,
            role = Role.Checkbox,
            enabled = enabled,
            onValueChange = { onToggle() },
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppCheckbox(checked = selected, onCheckedChange = null, enabled = enabled)
        Box(Modifier.weight(1f)) { card() }
    }
}

@Composable
fun HistorySelectionBar(
    selectedCount: Int,
    totalCount: Int,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val skin = LocalAppSkinTokens.current
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = AppDimens.Space12, vertical = AppDimens.Space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8),
    ) {
        Text(
            text = "共选 $selectedCount 项",
            style = skin.body2,
            color = skin.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        AppTextButton(
            text = if (allSelected) "取消全选" else "全选",
            onClick = onSelectAll,
            enabled = enabled && totalCount > 0,
            modifier = Modifier.testTag(HISTORY_SELECT_ALL_TAG),
        )
        AppTextButton(
            text = "删除($selectedCount)",
            onClick = onDelete,
            enabled = enabled && selectedCount > 0,
            contentColor = skin.error,
            modifier = Modifier.testTag(HISTORY_DELETE_SELECTED_TAG),
        )
    }
}

@Composable
fun HistorySelectAction(selectionMode: Boolean, onEnter: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    AppTextButton(
        text = if (selectionMode) "取消" else "管理",
        onClick = onEnter,
        enabled = enabled,
        modifier = modifier.testTag(HISTORY_SELECT_TOGGLE_TAG),
    )
}
