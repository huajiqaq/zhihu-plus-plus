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
        title = { Text("切换账号") },
        text = {
            Text("将切换到“${account.name}”。之后的推荐、内容和互动行为都属于该账号。")
        },
        confirmButton = {
            AppTextButton(
                enabled = !state.busy && !sessionChangeBlocked,
                onClick = onConfirm,
            ) {
                Text("切换")
            }
        },
        dismissButton = {
            AppTextButton(
                enabled = !state.busy && !sessionChangeBlocked,
                onClick = onCancel,
            ) {
                Text("取消")
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
        title = { Text("新账号使用规则") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "您即将创建的新账号，与当前账号共用同一手机号，为两个相互独立的账号身份。" +
                        "新账号拥有独立的昵称、头像与个人主页，账号之间的内容、数据、互动行为完全隔离。" +
                        "两个账号均需遵守社区规范与用户协议，任一账号存在违规行为，可能影响同一手机号下其他账号的使用权限。",
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
                    Text("我已知悉创建机会有限，创建后将立即切换")
                }
            }
        },
        confirmButton = {
            AppTextButton(
                modifier = Modifier.testTag(IDENTITY_MANAGEMENT_CREATE_CONFIRM_TAG),
                enabled = acceptedCreateRules && !state.busy && !sessionChangeBlocked,
                onClick = onConfirm,
            ) {
                Text("同意并创建")
            }
        },
        dismissButton = {
            AppTextButton(
                enabled = !state.busy && !sessionChangeBlocked,
                onClick = onCancel,
            ) {
                Text("取消")
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
        title = { Text("切换登录账号") },
        text = { Text("将切换到“${account.displayName}”。") },
        confirmButton = {
            AppTextButton(
                enabled = !state.busy && !sessionChangeBlocked,
                onClick = onConfirm,
            ) {
                Text("切换")
            }
        },
        dismissButton = {
            AppTextButton(onClick = onCancel) {
                Text("取消")
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
        title = { Text("移除登录账号") },
        text = { Text("将从本机删除“${account.displayName}”的登录凭据，不会注销知乎账号。") },
        confirmButton = {
            AppTextButton(
                enabled = !state.busy && !sessionChangeBlocked,
                onClick = onConfirm,
            ) {
                Text("移除")
            }
        },
        dismissButton = {
            AppTextButton(onClick = onCancel) {
                Text("取消")
            }
        },
    )
}
