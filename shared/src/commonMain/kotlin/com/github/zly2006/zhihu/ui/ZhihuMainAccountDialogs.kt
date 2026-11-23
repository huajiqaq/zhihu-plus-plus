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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.github.zly2006.zhihu.account.AccountVerificationDialogs
import com.github.zly2006.zhihu.account.HumanVerificationPhase
import com.github.zly2006.zhihu.account.SessionExpiryPrompt
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.ui.components.AppDialog

/** 人机验证弹窗与登录过期提示；[showExpiryPrompt] 为 false 时（已在登录页）不再提示重新登录。 */
@Composable
internal fun ZhihuMainAccountDialogs(
    showExpiryPrompt: Boolean,
    onRelogin: () -> Unit,
) {
    val accountStore = rememberZhihuAccountStore()
    val accountClient = accountStore.client
    val expiryPrompt by key(accountClient) { accountClient.expiryPrompt.collectAsState() }
    val verification by accountClient.verificationState.collectAsState()
    val http403Feedback by accountStore.http403Log.pendingFeedback.collectAsState()
    AccountVerificationDialogs(client = accountClient)
    if (showExpiryPrompt &&
        expiryPrompt.phase == SessionExpiryPrompt.SHOWING &&
        verification.phase == HumanVerificationPhase.ACTIVE &&
        http403Feedback == null
    ) {
        AppDialog(
            title = "登录已过期",
            summary = listOfNotNull(expiryPrompt.serverMessage, "请重新登录以继续使用完整功能。").joinToString("\n"),
            modifier = Modifier.testTag("session_expiry_dialog"),
            confirmText = "重新登录",
            dismissText = "取消",
            onDismissRequest = accountClient::dismissSessionExpiry,
            onConfirm = {
                if (accountClient.clearExpiredSession()) onRelogin()
            },
        )
    }
}
