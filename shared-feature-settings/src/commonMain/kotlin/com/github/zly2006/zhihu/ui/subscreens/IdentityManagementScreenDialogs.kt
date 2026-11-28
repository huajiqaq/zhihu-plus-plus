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

package com.github.zly2006.zhihu.ui.subscreens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.account.ZhihuIdentityAccount
import com.github.zly2006.zhihu.account.ZhihuSavedAccount
import com.github.zly2006.zhihu.i18n.SettingsStrings
import com.github.zly2006.zhihu.ui.components.AppAlertDialog
import com.github.zly2006.zhihu.ui.components.AppCheckbox
import com.github.zly2006.zhihu.ui.components.AppTextButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IdentityManagementSwitchDialog(
    account: ZhihuIdentityAccount,
    state: IdentityManagementState,
    sessionChangeBlocked: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(SettingsStrings.switchAccount) },
        text = {
            Text(SettingsStrings.switchAccountConfirmation(account.name))
        },
        confirmButton = {
            AppTextButton(
                enabled = !state.busy && !sessionChangeBlocked,
                onClick = onConfirm,
            ) {
                Text(SettingsStrings.switch)
            }
        },
        dismissButton = {
            AppTextButton(
                enabled = !state.busy && !sessionChangeBlocked,
                onClick = onCancel,
            ) {
                Text(SettingsStrings.cancel)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IdentityManagementCreateDialog(
    acceptedCreateRules: Boolean,
    state: IdentityManagementState,
    sessionChangeBlocked: Boolean,
    onAcceptedCreateRulesChange: (Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(SettingsStrings.newAccountRules) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    SettingsStrings.newAccountRulesPhone +
                        SettingsStrings.newAccountRulesData +
                        SettingsStrings.newAccountRulesConduct,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = acceptedCreateRules,
                            enabled = !state.busy && !sessionChangeBlocked,
                            role = Role.Checkbox,
                            onValueChange = onAcceptedCreateRulesChange,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppCheckbox(
                        checked = acceptedCreateRules,
                        enabled = !state.busy && !sessionChangeBlocked,
                        onCheckedChange = null,
                    )
                    Text(SettingsStrings.acceptAccountRules)
                }
            }
        },
        confirmButton = {
            AppTextButton(
                modifier = Modifier.testTag(IDENTITY_MANAGEMENT_CREATE_CONFIRM_TAG),
                enabled = acceptedCreateRules && !state.busy && !sessionChangeBlocked,
                onClick = onConfirm,
            ) {
                Text(SettingsStrings.agreeAndCreate)
            }
        },
        dismissButton = {
            AppTextButton(
                enabled = !state.busy && !sessionChangeBlocked,
                onClick = onCancel,
            ) {
                Text(SettingsStrings.cancel)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IdentityManagementSavedSwitchDialog(
    account: ZhihuSavedAccount,
    state: IdentityManagementState,
    sessionChangeBlocked: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(SettingsStrings.switchSavedAccount) },
        text = { Text(SettingsStrings.switchSavedAccountConfirmation(account.displayName)) },
        confirmButton = {
            AppTextButton(
                enabled = !state.busy && !sessionChangeBlocked,
                onClick = onConfirm,
            ) {
                Text(SettingsStrings.switch)
            }
        },
        dismissButton = {
            AppTextButton(onClick = onCancel) {
                Text(SettingsStrings.cancel)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IdentityManagementRemoveDialog(
    account: ZhihuSavedAccount,
    state: IdentityManagementState,
    sessionChangeBlocked: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(SettingsStrings.removeSavedAccount) },
        text = { Text(SettingsStrings.removeSavedAccountConfirmation(account.displayName)) },
        confirmButton = {
            AppTextButton(
                enabled = !state.busy && !sessionChangeBlocked,
                onClick = onConfirm,
            ) {
                Text(SettingsStrings.remove)
            }
        },
        dismissButton = {
            AppTextButton(onClick = onCancel) {
                Text(SettingsStrings.cancel)
            }
        },
    )
}
