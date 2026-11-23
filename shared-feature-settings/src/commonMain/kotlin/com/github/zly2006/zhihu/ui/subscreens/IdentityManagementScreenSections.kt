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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.account.ZhihuAccounts
import com.github.zly2006.zhihu.account.ZhihuIdentityAccount
import com.github.zly2006.zhihu.account.ZhihuSavedAccount
import com.github.zly2006.zhihu.navigation.requestLoginNavigation
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingItemGroup
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

internal val ZhihuSavedAccount.displayName: String
    get() = session.profile?.name?.takeIf { it.isNotBlank() } ?: session.username

@Composable
internal fun IdentityManagementContent(
    state: IdentityManagementState,
    savedAccounts: ZhihuAccounts,
    sessionChangeBlocked: Boolean,
    onRetry: () -> Unit,
    onSwitchTargetChange: (ZhihuIdentityAccount) -> Unit,
    onRemoveLoginAccountChange: (ZhihuSavedAccount) -> Unit,
    onSwitchLoginAccountChange: (ZhihuSavedAccount) -> Unit,
    onCreate: () -> Unit,
) {
    if (state.loading && state.accounts.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            contentAlignment = Alignment.Center,
        ) {
            AppSpinner()
        }
    }

    state.errorMessage?.let { errorMessage ->
        SettingItemGroup {
            SettingItem(
                title = { Text("加载失败") },
                description = { Text(errorMessage) },
                icon = { Icon(Icons.Default.ErrorOutline, null) },
                endAction = { Icon(Icons.Default.Refresh, contentDescription = "重试") },
                modifier = Modifier.testTag(IDENTITY_MANAGEMENT_RETRY_TAG),
                enabled = !state.busy && !sessionChangeBlocked,
                onClick = onRetry,
            )
        }
    }

    if (state.accounts.isNotEmpty()) {
        SettingItemGroup(
            title = "当前手机号下的账号",
        ) {
            state.accounts.forEachIndexed { index, account ->
                val isCurrent = account.id == state.currentAccountId
                val isSwitching = account.id == state.switchingToAccountId
                SettingItem(
                    title = { Text(account.name) },
                    description = {
                        Column {
                            Text(
                                when (account.accountType) {
                                    1 -> "主账号"
                                    2 -> "马甲号"
                                    else -> "知乎账号"
                                },
                            )
                            state.operationError?.takeIf { it.first == "identity:${account.id}" }?.second?.let {
                                Text(it, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                    icon = {
                        AsyncImage(
                            model = account.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                        )
                    },
                    endAction = {
                        when {
                            isSwitching -> AppSpinner(size = 20.dp)
                            isCurrent -> Text(
                                "当前登录",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            else -> Icon(Icons.Default.SwitchAccount, contentDescription = "切换")
                        }
                    },
                    modifier = Modifier.testTag("identityManagement.account.$index"),
                    enabled = !state.busy && !sessionChangeBlocked,
                    onClick = if (isCurrent) {
                        null
                    } else {
                        { onSwitchTargetChange(account) }
                    },
                )
            }
        }
    } else if (!state.loading && state.errorMessage == null) {
        SettingItemGroup {
            SettingItem(
                title = { Text("未找到可管理的账号") },
                description = { Text("请确认当前登录状态后重试") },
                icon = { Icon(Icons.Default.ErrorOutline, null) },
                endAction = { Icon(Icons.Default.Refresh, contentDescription = "重试") },
                modifier = Modifier.testTag(IDENTITY_MANAGEMENT_RETRY_TAG),
                enabled = !state.busy && !sessionChangeBlocked,
                onClick = onRetry,
            )
        }
    }

    run {
        val otherAccounts = savedAccounts.accounts.filterNot { it.id == savedAccounts.activeAccountId }
        SettingItemGroup(
            title = "其他登录账号",
        ) {
            otherAccounts.forEach { account ->
                SettingItem(
                    title = { Text(account.displayName) },
                    description = {
                        Column {
                            Text("切换到这个登录账号")
                            state.operationError?.takeIf { it.first == "saved:${account.id}" }?.second?.let {
                                Text(it, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.SwitchAccount, null) },
                    endAction = {
                        AppIconButton(enabled = !state.busy && !sessionChangeBlocked, onClick = { onRemoveLoginAccountChange(account) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "移除登录账号 ${account.displayName}")
                        }
                    },
                    enabled = !state.busy && !sessionChangeBlocked,
                    onClick = { onSwitchLoginAccountChange(account) },
                )
            }
            SettingItem(
                title = { Text("添加其他手机号登录账号") },
                icon = { Icon(Icons.AutoMirrored.Filled.Login, null) },
                enabled = !state.busy && !sessionChangeBlocked,
                onClick = ::requestLoginNavigation,
            )
        }

        if (state.accounts.isNotEmpty()) {
            SettingItemGroup(
                title = "新账号",
                footer = {
                    Text(
                        if (state.canCreateSubAccount) {
                            "新账号会先使用系统昵称完成初始化。昵称修改受知乎次数限制，本客户端不会自动改名。"
                        } else if (state.accounts.size >= 2) {
                            "当前手机号下已经存在主账号和马甲号。"
                        } else {
                            "当前登录账号暂不满足创建新账号的条件。"
                        },
                    )
                },
            ) {
                SettingItem(
                    title = { Text("创建新账号") },
                    description = state.operationError?.takeIf { it.first == "create" }?.second?.let { message ->
                        { Text(message, color = MaterialTheme.colorScheme.error) }
                    },
                    icon = { Icon(Icons.Default.Add, null) },
                    modifier = Modifier.testTag(IDENTITY_MANAGEMENT_CREATE_TAG),
                    enabled = state.canCreateSubAccount && !state.busy && !sessionChangeBlocked,
                    endAction = {
                        if (state.creating) {
                            AppSpinner(size = 20.dp)
                        }
                    },
                    onClick = onCreate,
                )
            }
        }
    }
}
