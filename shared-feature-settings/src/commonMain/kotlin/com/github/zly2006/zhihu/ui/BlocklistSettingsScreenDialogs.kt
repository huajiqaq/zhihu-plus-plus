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
import com.github.zly2006.zhihu.i18n.AccountStrings
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
            title = AccountStrings.blockQuestionAuthorTitle,
            hint = AccountStrings.blockQuestionAuthorHint,
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
        title = { Text(AccountStrings.blockKeywordTitle) },
        text = {
            Column {
                AppTextField(
                    state = keyword,
                    labelText = AccountStrings.keyword,
                    placeholder = AccountStrings.blockKeywordPlaceholder,
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
                    Text(AccountStrings.caseSensitive)
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
                    Text(AccountStrings.regularExpression)
                }
                if (isRegex) {
                    Text(
                        AccountStrings.regexHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = AppDimens.Space8),
                    )
                }
            }
        },
        confirmButton = {
            AppButton(
                text = AccountStrings.add,
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
                text = AccountStrings.cancel,
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
    title: String = AccountStrings.blockUserTitle,
    hint: String = AccountStrings.blockUserHint,
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
                    labelText = AccountStrings.userId,
                    placeholder = AccountStrings.userIdPlaceholder,
                    enabled = !submitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(BlocklistSettingsTestTags.USER_DIALOG_ID_INPUT),
                )
                Spacer(modifier = Modifier.height(AppDimens.Space8))
                AppTextField(
                    state = userName,
                    labelText = AccountStrings.username,
                    placeholder = AccountStrings.usernamePlaceholder,
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
                text = AccountStrings.add,
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
                text = AccountStrings.cancel,
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
        title = { Text(AccountStrings.blockTopicTitle) },
        text = {
            Column {
                Text(
                    AccountStrings.blockTopicHint,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(AppDimens.Space16))
                AppTextField(
                    state = topicId,
                    labelText = AccountStrings.topicId,
                    placeholder = AccountStrings.topicIdExample,
                    enabled = !submitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(BlocklistSettingsTestTags.TOPIC_DIALOG_ID_INPUT),
                )
                Spacer(modifier = Modifier.height(AppDimens.Space8))
                AppTextField(
                    state = topicName,
                    labelText = AccountStrings.topicName,
                    placeholder = AccountStrings.topicNameExample,
                    enabled = !submitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(BlocklistSettingsTestTags.TOPIC_DIALOG_NAME_INPUT),
                )
            }
        },
        confirmButton = {
            AppButton(
                text = AccountStrings.add,
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
                text = AccountStrings.cancel,
                onClick = onDismiss,
                modifier = Modifier.testTag(BlocklistSettingsTestTags.TOPIC_DIALOG_DISMISS),
                enabled = !submitting,
            )
        },
    )
}
