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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.input.TextFieldLineLimits
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
import com.github.zly2006.zhihu.i18n.ContentStrings

@Composable
fun CreateCollectionDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String, Boolean) -> Unit,
    isSubmitting: Boolean = false,
    errorMessage: String? = null,
) {
    if (showDialog) {
        val title = rememberTextFieldState()
        val description = rememberTextFieldState()
        var isPublic by rememberSaveable { mutableStateOf(false) }
        AppAlertDialog(
            onDismissRequest = {
                if (!isSubmitting) onDismiss()
            },
            modifier = Modifier.testTag(CREATE_COLLECTION_DIALOG_TAG),
            title = { Text(ContentStrings.newCollection) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Space16)) {
                    AppTextField(
                        state = title,
                        labelText = ContentStrings.collectionName,
                        placeholder = ContentStrings.collectionNameHint,
                        modifier = Modifier.fillMaxWidth().testTag(CREATE_COLLECTION_TITLE_INPUT_TAG),
                        enabled = !isSubmitting,
                    )
                    AppTextField(
                        state = description,
                        labelText = ContentStrings.optionalDescription,
                        placeholder = ContentStrings.collectionDescriptionHint,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSubmitting,
                        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 3),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().toggleable(
                            value = isPublic,
                            enabled = !isSubmitting,
                            role = Role.Checkbox,
                            onValueChange = { isPublic = it },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppCheckbox(
                            checked = isPublic,
                            onCheckedChange = null,
                            modifier = Modifier.clearAndSetSemantics { },
                            enabled = !isSubmitting,
                        )
                        Text(
                            text = ContentStrings.publicCollection,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    errorMessage?.let {
                        Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                AppButton(
                    text = if (isSubmitting) ContentStrings.creating else ContentStrings.create,
                    onClick = {
                        if (title.text.isNotBlank()) onConfirm(title.text.toString().trim(), description.text.toString().trim(), isPublic)
                    },
                    modifier = Modifier.testTag(CREATE_COLLECTION_CONFIRM_TAG),
                    enabled = title.text.isNotBlank() && !isSubmitting,
                )
            },
            dismissButton = {
                AppTextButton(text = ContentStrings.cancel, onClick = onDismiss, enabled = !isSubmitting)
            },
        )
    }
}

private const val CREATE_COLLECTION_DIALOG_TAG = "create_collection_dialog"
private const val CREATE_COLLECTION_TITLE_INPUT_TAG = "create_collection_title_input"
private const val CREATE_COLLECTION_CONFIRM_TAG = "create_collection_confirm"
