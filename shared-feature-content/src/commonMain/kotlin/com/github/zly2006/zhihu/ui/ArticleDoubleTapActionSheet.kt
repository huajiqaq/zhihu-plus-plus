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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.ui.AnswerDoubleTapAction
import com.github.zly2006.zhihu.ui.components.AppBottomSheet
import com.github.zly2006.zhihu.ui.components.AppButton

/** 双击回答的动作设为「询问并记住」时弹出的面板；选中的动作交给 [onSelect] 保存并执行。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArticleDoubleTapActionSheet(
    onDismissRequest: () -> Unit,
    onSelect: (AnswerDoubleTapAction) -> Unit,
) {
    AppBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "设置双击回答动作",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "选择以后双击回答时默认执行的动作。选择后会立即保存到设置，你也可以稍后在设置中修改。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppButton(
                text = "设为无操作",
                onClick = { onSelect(AnswerDoubleTapAction.None) },
                modifier = Modifier.fillMaxWidth(),
            )
            AppButton(
                text = "设为点赞",
                onClick = { onSelect(AnswerDoubleTapAction.VoteUp) },
                modifier = Modifier.fillMaxWidth(),
            )
            AppButton(
                text = "设为打开评论区",
                onClick = { onSelect(AnswerDoubleTapAction.OpenComments) },
                modifier = Modifier.fillMaxWidth(),
            )
            AppButton(
                text = "设为开关沉浸式",
                onClick = { onSelect(AnswerDoubleTapAction.ToggleImmersive) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                listOf(AnswerDoubleTapAction.JumpToTop, AnswerDoubleTapAction.JumpToBottom).forEach { action ->
                    AppButton(
                        text = action.label,
                        onClick = { onSelect(action) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
