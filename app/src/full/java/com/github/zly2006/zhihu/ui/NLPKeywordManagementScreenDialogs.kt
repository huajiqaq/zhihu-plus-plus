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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.github.zly2006.zhihu.ui.components.AppAlertDialog
import com.github.zly2006.zhihu.ui.components.AppBottomSheet
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.AppTextField
import com.github.zly2006.zhihu.viewmodel.LoadState
import com.github.zly2006.zhihu.viewmodel.filter.BlockedContentRecord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NLPKeywordManagementRecordsSheet(
    records: LoadState<List<BlockedContentRecord>>,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onClearAll: () -> Unit,
    recordContent: @Composable (BlockedContentRecord) -> Unit,
) {
    AppBottomSheet(onDismissRequest = onDismiss) {
        BlocklistItemList(
            state = records,
            onRetry = onRetry,
            description = "最近 ${BlockedContentRecord.MAX_RECORDS} 条被 NLP 语义屏蔽的内容，点开可查看匹配的短语。",
            emptyTitle = "暂无 NLP 匹配记录",
            clearTitle = { count -> "清空 $count 条 NLP 匹配记录？" },
            clearSummary = "只删除这些记录，不影响屏蔽短语。清空后无法撤销。",
            onClearAll = onClearAll,
            itemKey = { it.id },
            modifier = Modifier.fillMaxHeight(0.8f),
            contentPadding = PaddingValues(bottom = AppDimens.Space16),
        ) { record ->
            recordContent(record)
        }
    }
}

@Composable
internal fun PhraseDialog(
    title: String,
    confirmText: String,
    initialPhrase: String,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val phrase = key(initialPhrase) { rememberTextFieldState(initialPhrase) }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                AppTextField(
                    state = phrase,
                    labelText = "短语",
                    lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2),
                    placeholder = "用空格分隔多个关键词，如：政治 敏感",
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(AppDimens.Space8))
                Text(
                    "提示：用空格分隔多个关键词来表达与逻辑。例如：\"政治 敏感\" 将匹配同时包含这两个主题的内容。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            AppButton(
                onClick = { if (phrase.text.isNotBlank()) onConfirm(phrase.text.toString()) },
                enabled = phrase.text.isNotBlank() && !submitting,
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(AppDimens.Space4))
                Text(confirmText)
            }
        },
        dismissButton = {
            AppTextButton(text = "取消", onClick = onDismiss, enabled = !submitting)
        },
    )
}
