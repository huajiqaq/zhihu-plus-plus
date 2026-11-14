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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

/** [AppSortableCheckList] 的一行。[lockedReason] 非空时这一行不能改勾选状态，摘要显示原因。 */
data class SortableCheckItem(
    val key: String,
    val title: String,
    val description: String? = null,
    val lockedReason: String? = null,
)

/** 最小/最大选择数限制勾选；排序仅在已选项间移动，不受勾选禁用影响。 */
@Composable
fun AppSortableCheckList(
    items: List<SortableCheckItem>,
    checkedKeys: List<String>,
    onCheckedChange: (key: String, checked: Boolean) -> Unit,
    onMove: (key: String, offset: Int) -> Unit,
    minChecked: Int = 1,
    maxChecked: Int? = null,
    minCheckedReason: String = if (minChecked <= 1) "至少保留一项" else "至少保留 $minChecked 项",
    maxCheckedReason: String = "最多选择 ${maxChecked ?: 0} 项",
    testTagPrefix: String = "sortable_check",
) {
    items.forEach { item ->
        val checked = item.key in checkedKeys
        val position = checkedKeys.indexOf(item.key)
        val blockedReason = when {
            item.lockedReason != null -> item.lockedReason
            checked && checkedKeys.size <= minChecked -> minCheckedReason
            !checked && maxChecked != null && checkedKeys.size >= maxChecked -> maxCheckedReason
            else -> null
        }
        val toggleable = blockedReason == null
        SettingItem(
            title = { Text(item.title) },
            description = (blockedReason ?: item.description)?.let { text -> { Text(text) } },
            icon = {
                AppCheckbox(
                    checked = checked,
                    onCheckedChange = null,
                    enabled = toggleable,
                    modifier = if (toggleable) {
                        Modifier.clearAndSetSemantics { }
                    } else {
                        // 只禁用原生复选目标；整行仍可承载独立的排序按钮。
                        Modifier
                            .testTag("$testTagPrefix:item:${item.key}")
                            .semantics {
                                role = Role.Checkbox
                                toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                                disabled()
                                contentDescription = "${item.title}，$blockedReason"
                            }
                    },
                )
            },
            onClick = if (toggleable) ({ onCheckedChange(item.key, !checked) }) else null,
            modifier = if (toggleable) {
                Modifier
                    .testTag("$testTagPrefix:item:${item.key}")
                    .semantics {
                        role = Role.Checkbox
                        toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                    }
            } else {
                Modifier
            },
            endAction = if (checked) {
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIconButton(
                            onClick = { onMove(item.key, -1) },
                            enabled = position > 0,
                            modifier = Modifier.size(AppDimens.TouchTarget).testTag("$testTagPrefix:moveUp:${item.key}"),
                        ) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上移${item.title}")
                        }
                        AppIconButton(
                            onClick = { onMove(item.key, 1) },
                            enabled = position in 0 until checkedKeys.lastIndex,
                            modifier = Modifier.size(AppDimens.TouchTarget).testTag("$testTagPrefix:moveDown:${item.key}"),
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下移${item.title}")
                        }
                    }
                }
            } else {
                null
            },
        )
    }
}
