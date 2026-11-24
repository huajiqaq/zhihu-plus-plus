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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.github.zly2006.zhihu.ui.components.AppAlertDialog
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppCheckbox
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.AppTextField

@Composable
internal fun BlocklistSettingsAddDialog(
    dialogTab: BlocklistTab,
    submitting: Boolean,
    dismiss: () -> Unit,
    onConfirmKeyword: (String, Boolean, Boolean) -> Unit,
    onConfirmUser: (String, String) -> Unit,
    onConfirmQuestionAuthor: (String, String) -> Unit,
    onConfirmTopic: (String, String) -> Unit,
) {
    when (dialogTab) {
        BlocklistTab.Keywords -> AddKeywordDialog(
            submitting = submitting,
            onDismiss = dismiss,
            onConfirm = onConfirmKeyword,
        )

        BlocklistTab.Users -> AddUserDialog(
            submitting = submitting,
            onDismiss = dismiss,
            onConfirm = onConfirmUser,
        )

        BlocklistTab.QuestionAuthors -> AddUserDialog(
            title = "添加屏蔽提问者",
            hint = "添加后会屏蔽该用户提出的问题，不影响该用户回答别人问题的内容",
            submitting = submitting,
            onDismiss = dismiss,
            onConfirm = onConfirmQuestionAuthor,
        )

        BlocklistTab.Topics -> AddTopicDialog(
            submitting = submitting,
            onDismiss = dismiss,
            onConfirm = onConfirmTopic,
        )

        BlocklistTab.Nlp -> Unit
    }
}

@Composable
private fun AddKeywordDialog(
    submitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, Boolean, Boolean) -> Unit,
) {
    val keyword = rememberTextFieldState()
    var caseSensitive by rememberSaveable { mutableStateOf(false) }
    var isRegex by rememberSaveable { mutableStateOf(false) }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加屏蔽关键词") },
        text = {
            Column {
                AppTextField(
                    state = keyword,
                    labelText = "关键词",
                    placeholder = "输入要屏蔽的关键词",
                    enabled = !submitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(BlocklistSettingsTestTags.KEYWORD_DIALOG_INPUT),
                )
                Spacer(modifier = Modifier.height(AppDimens.Space8))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(BlocklistSettingsTestTags.KEYWORD_DIALOG_CASE_SENSITIVE)
                        .toggleable(value = caseSensitive, enabled = !submitting, role = Role.Checkbox, onValueChange = { caseSensitive = it }),
                ) {
                    AppCheckbox(
                        modifier = Modifier.clearAndSetSemantics {},
                        checked = caseSensitive,
                        onCheckedChange = null,
                        enabled = !submitting,
                    )
                    Spacer(modifier = Modifier.width(AppDimens.Space8))
                    Text("区分大小写")
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(BlocklistSettingsTestTags.KEYWORD_DIALOG_REGEX)
                        .toggleable(value = isRegex, enabled = !submitting, role = Role.Checkbox, onValueChange = { isRegex = it }),
                ) {
                    AppCheckbox(
                        modifier = Modifier.clearAndSetSemantics {},
                        checked = isRegex,
                        onCheckedChange = null,
                        enabled = !submitting,
                    )
                    Spacer(modifier = Modifier.width(AppDimens.Space8))
                    Text("正则表达式")
                }
                if (isRegex) {
                    Text(
                        "提示：使用正则表达式可以实现更灵活的匹配，但语法错误会导致该关键词无效",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = AppDimens.Space8),
                    )
                }
            }
        },
        confirmButton = {
            AppButton(
                text = "添加",
                onClick = {
                    val text = keyword.text.toString()
                    if (text.isNotBlank()) onConfirm(text, caseSensitive, isRegex)
                },
                modifier = Modifier.testTag(BlocklistSettingsTestTags.KEYWORD_DIALOG_CONFIRM),
                enabled = keyword.text.isNotBlank() && !submitting,
            )
        },
        dismissButton = {
            AppTextButton(
                text = "取消",
                onClick = onDismiss,
                modifier = Modifier.testTag(BlocklistSettingsTestTags.KEYWORD_DIALOG_DISMISS),
                enabled = !submitting,
            )
        },
    )
}

@Composable
private fun AddUserDialog(
    submitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
    title: String = "添加屏蔽用户",
    hint: String = "提示：可以从用户主页的URL中获取用户ID",
) {
    val userId = rememberTextFieldState()
    val userName = rememberTextFieldState()

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                AppTextField(
                    state = userId,
                    labelText = "用户ID",
                    placeholder = "输入用户ID",
                    enabled = !submitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(BlocklistSettingsTestTags.USER_DIALOG_ID_INPUT),
                )
                Spacer(modifier = Modifier.height(AppDimens.Space8))
                AppTextField(
                    state = userName,
                    labelText = "用户名",
                    placeholder = "输入用户名（可选）",
                    enabled = !submitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(BlocklistSettingsTestTags.USER_DIALOG_NAME_INPUT),
                )
                Spacer(modifier = Modifier.height(AppDimens.Space8))
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            AppButton(
                text = "添加",
                onClick = {
                    val id = userId.text.toString()
                    if (id.isNotBlank()) onConfirm(id, userName.text.toString().ifBlank { id })
                },
                modifier = Modifier.testTag(BlocklistSettingsTestTags.USER_DIALOG_CONFIRM),
                enabled = userId.text.isNotBlank() && !submitting,
            )
        },
        dismissButton = {
            AppTextButton(
                text = "取消",
                onClick = onDismiss,
                modifier = Modifier.testTag(BlocklistSettingsTestTags.USER_DIALOG_DISMISS),
                enabled = !submitting,
            )
        },
    )
}

@Composable
private fun AddTopicDialog(
    submitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    val topicId = rememberTextFieldState()
    val topicName = rememberTextFieldState()

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加屏蔽主题") },
        text = {
            Column {
                Text(
                    "输入要屏蔽的主题ID和名称。主题ID可以从知乎网页版主题链接中获取。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(AppDimens.Space16))
                AppTextField(
                    state = topicId,
                    labelText = "主题ID",
                    placeholder = "例如: 19550517",
                    enabled = !submitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(BlocklistSettingsTestTags.TOPIC_DIALOG_ID_INPUT),
                )
                Spacer(modifier = Modifier.height(AppDimens.Space8))
                AppTextField(
                    state = topicName,
                    labelText = "主题名称",
                    placeholder = "例如: 娱乐",
                    enabled = !submitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(BlocklistSettingsTestTags.TOPIC_DIALOG_NAME_INPUT),
                )
            }
        },
        confirmButton = {
            AppButton(
                text = "添加",
                onClick = {
                    val id = topicId.text.toString()
                    if (id.isNotBlank()) onConfirm(id, topicName.text.toString().ifBlank { id })
                },
                modifier = Modifier.testTag(BlocklistSettingsTestTags.TOPIC_DIALOG_CONFIRM),
                enabled = topicId.text.isNotBlank() && !submitting,
            )
        },
        dismissButton = {
            AppTextButton(
                text = "取消",
                onClick = onDismiss,
                modifier = Modifier.testTag(BlocklistSettingsTestTags.TOPIC_DIALOG_DISMISS),
                enabled = !submitting,
            )
        },
    )
}
