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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.github.zly2006.zhihu.account.ZhihuAccountClient
import com.github.zly2006.zhihu.account.parseCookieAssignments
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.ui.components.AppAlertDialog
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppSecureTextField
import com.github.zly2006.zhihu.ui.components.AppSettingsPage
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingItemGroup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

const val DEVELOPER_SETTINGS_BACK_BUTTON_TAG = "developerSettings/backButton"

private enum class AccountTool { Verify, RefreshToken, ImportCookie }

private data class AccountToolResult(
    val message: String,
    val failed: Boolean,
)

/** 操作与结果属于同一个客户端，旧账号的异步返回不能修改新账号的维护状态。 */
private data class AccountToolsState(
    val owner: ZhihuAccountClient,
    val running: AccountTool? = null,
    val results: Map<AccountTool, AccountToolResult> = emptyMap(),
)

/** 公开的账号维护入口；三项操作串行执行，结果留在对应行，Cookie 候选先验证再替换会话。 */
@Composable
fun AccountToolsScreen() {
    val navigator = LocalNavigator.current
    val accountStore = rememberZhihuAccountStore()
    val accountClient = accountStore.client
    val accounts by accountStore.accountsState.collectAsState()
    val session = accounts.session
    val verification by accountClient.verificationState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var cookieDialogOwner by remember(accountStore) { mutableStateOf<ZhihuAccountClient?>(null) }
    val showCookieDialog = cookieDialogOwner === accountClient
    var showDiagnostics by remember(accountClient) { mutableStateOf(false) }
    var toolState by remember(accountStore) { mutableStateOf(AccountToolsState(accountClient)) }
    val state = toolState.takeIf { it.owner === accountClient } ?: AccountToolsState(accountClient)
    val canRun = state.running == null && !verification.blocksSessionChange

    LaunchedEffect(accountClient) {
        if (accountStore.client === accountClient) {
            if (toolState.owner !== accountClient) toolState = AccountToolsState(accountClient)
            if (cookieDialogOwner !== accountClient) cookieDialogOwner = null
        }
    }

    fun runTool(
        tool: AccountTool,
        failurePrefix: String,
        onResult: (AccountToolResult) -> Unit = {},
        action: suspend (ZhihuAccountClient) -> AccountToolResult,
    ) {
        val owner = accountClient
        if (accountStore.client !== owner || owner.verificationState.value.blocksSessionChange) return
        if (toolState.owner === owner && toolState.running != null) return
        toolState = (toolState.takeIf { it.owner === owner } ?: AccountToolsState(owner)).copy(running = tool)
        coroutineScope.launch {
            var resultOwner = owner
            try {
                if (accountStore.client !== owner || owner.verificationState.value.blocksSessionChange) return@launch
                val accountsBefore = accountStore.accountsState.value
                val result = try {
                    action(owner)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (tool == AccountTool.ImportCookie && accountStore.accountsState.value === accountsBefore && toolState.owner === owner) {
                        /** 持久化失败与重建客户端同步完成；仅原账号快照未发布变化时保留草稿及失败反馈。 */
                        resultOwner = accountStore.client
                        toolState = toolState.copy(owner = resultOwner)
                        if (cookieDialogOwner === owner) cookieDialogOwner = resultOwner
                    }
                    AccountToolResult("$failurePrefix：${e.message}", failed = true)
                }
                currentCoroutineContext().ensureActive()
                if (accountStore.client === resultOwner && toolState.owner === resultOwner) {
                    toolState = toolState.copy(results = toolState.results + (tool to result))
                    onResult(result)
                }
            } finally {
                if (toolState.owner === resultOwner) toolState = toolState.copy(running = null)
            }
        }
    }

    AppSettingsPage(
        title = "账号与登录维护",
        onBack = navigator.onNavigateBack,
        backTag = DEVELOPER_SETTINGS_BACK_BUTTON_TAG,
        pageTurnEnabled = !showCookieDialog && !showDiagnostics,
    ) {
        SettingItemGroup(title = "当前登录") {
            SettingItem(
                title = {
                    Text(
                        if (session.login) {
                            session.profile?.name?.takeIf { it.isNotBlank() } ?: session.username.ifBlank { "已登录" }
                        } else {
                            "未登录"
                        },
                    )
                },
                description = {
                    Text(
                        when {
                            verification.blocksSessionChange -> "请先完成安全验证，再进行账号维护"
                            session.login ->
                                session.profile
                                    ?.urlToken
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { "已登录 · @$it" } ?: "已登录"
                            else -> "登录或导入 Cookie 后可使用全部维护操作"
                        },
                    )
                },
                icon = { Icon(if (session.login) Icons.Default.AccountCircle else Icons.Default.PersonOff, null) },
            )
        }

        SettingItemGroup(
            title = "维护",
            footer = { Text("Cookie 属于账号凭据，请勿向他人提供。") },
        ) {
            AccountToolRow(
                title = "验证登录",
                idleSummary = "检查当前保存的登录是否仍然有效",
                runningSummary = "正在验证…",
                icon = Icons.Default.VerifiedUser,
                running = state.running == AccountTool.Verify,
                result = state.results[AccountTool.Verify],
                enabled = canRun,
                onClick = {
                    runTool(AccountTool.Verify, "验证登录失败") { owner ->
                        if (owner.refreshAndSaveProfile() != null) {
                            AccountToolResult("登录有效", failed = false)
                        } else {
                            AccountToolResult("登录无效，请重新登录或导入 Cookie", failed = true)
                        }
                    }
                },
            )
            AccountToolRow(
                title = "刷新 Token",
                idleSummary = if (session.login) "重新签发当前账号的登录凭据" else "登录或导入 Cookie 后才能刷新",
                runningSummary = "正在刷新…",
                icon = Icons.Default.Sync,
                running = state.running == AccountTool.RefreshToken,
                result = state.results[AccountTool.RefreshToken].takeIf { session.login },
                enabled = session.login && canRun,
                onClick = {
                    runTool(AccountTool.RefreshToken, "刷新失败") { owner ->
                        if (owner.refreshCredentials(force = true)) {
                            AccountToolResult("刷新成功", failed = false)
                        } else {
                            AccountToolResult("缺少移动端刷新凭据时请重新登录；刚刚尝试过刷新时请稍后重试", failed = true)
                        }
                    }
                },
            )
            AccountToolRow(
                title = "导入 Cookie",
                idleSummary = "使用自己的 Cookie 登录，导入前会先验证",
                runningSummary = "正在验证 Cookie…",
                icon = Icons.Default.Cookie,
                running = state.running == AccountTool.ImportCookie,
                result = state.results[AccountTool.ImportCookie],
                enabled = canRun,
                onClick = { cookieDialogOwner = accountClient },
            )
        }

        SettingItemGroup(title = "网络请求诊断") {
            SettingItem(
                title = { Text("403 请求记录") },
                description = { Text("仅保留首次 403 的脱敏信息，供反馈作者；验证通过后自动清空") },
                icon = { Icon(Icons.Default.BugReport, null) },
                endAction = {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                modifier = Modifier.testTag("network_diagnostics_open"),
                onClick = { showDiagnostics = true },
            )
        }
    }

    if (showDiagnostics) {
        NetworkRequestDiagnostics(
            log = accountStore.http403Log,
            client = accountClient,
            onDismissRequest = { showDiagnostics = false },
        )
    }

    if (showCookieDialog) {
        val cookieInput = remember { TextFieldState() }
        var showCookieText by remember { mutableStateOf(false) }
        var importError by remember(cookieInput.text) { mutableStateOf<String?>(null) }
        val importing = state.running == AccountTool.ImportCookie
        val closeDialog = {
            cookieDialogOwner = null
            cookieInput.clearText()
            showCookieText = false
        }
        AppAlertDialog(
            onDismissRequest = { if (!importing) closeDialog() },
            title = { Text("导入 Cookie") },
            text = {
                Column {
                    Text(
                        "请输入自己的完整 Cookie 字符串，使用分号分隔各项（空格可省略）。导入前会验证登录状态。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = AppDimens.Space16),
                    )
                    AppSecureTextField(
                        state = cookieInput,
                        enabled = !importing,
                        labelText = "Cookie 字符串",
                        placeholder = "name1=value1; name2=value2; name3=value3",
                        textObfuscationMode = if (showCookieText) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
                        trailingIcon = {
                            AppIconButton(enabled = !importing, onClick = { showCookieText = !showCookieText }) {
                                Icon(
                                    imageVector = if (showCookieText) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (showCookieText) "隐藏" else "显示",
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    importError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = AppDimens.Space8))
                    }
                }
            },
            confirmButton = {
                AppButton(
                    text = if (importing) "正在验证" else "导入并验证",
                    enabled = cookieInput.text.isNotBlank() && canRun,
                    onClick = {
                        val cookies = parseCookieAssignments(cookieInput.text.toString())
                        if (cookies.isEmpty()) {
                            importError = "未能解析有效的 Cookie 数据"
                        } else {
                            importError = null
                            runTool(
                                AccountTool.ImportCookie,
                                "验证登录时发生错误",
                                onResult = { result -> if (result.failed) importError = result.message },
                            ) {
                                if (accountStore.login(cookies.toMutableMap())) {
                                    currentCoroutineContext().ensureActive()
                                    val result = AccountToolResult("已导入，登录有效", failed = false)
                                    /** 仅成功提交的候选可以把导入结果交给新客户端；旧验证、刷新结果不再适用。 */
                                    toolState = AccountToolsState(accountStore.client, results = mapOf(AccountTool.ImportCookie to result))
                                    closeDialog()
                                    result
                                } else {
                                    AccountToolResult("验证登录失败，未导入 Cookie，请检查凭据是否有效", failed = true)
                                }
                            }
                        }
                    },
                )
            },
            dismissButton = {
                AppTextButton(text = "取消", enabled = !importing, onClick = closeDialog)
            },
        )
    }
}

@Composable
private fun AccountToolRow(
    title: String,
    idleSummary: String,
    runningSummary: String,
    icon: ImageVector,
    running: Boolean,
    result: AccountToolResult?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    SettingItem(
        title = { Text(title) },
        description = {
            when {
                running -> Text(runningSummary)
                result != null -> Text(
                    result.message,
                    color = if (result.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                else -> Text(idleSummary)
            }
        },
        icon = { Icon(icon, null) },
        endAction = { if (running) AppSpinner(size = AppDimens.InlineSpinnerSize) },
        enabled = enabled,
        onClick = onClick,
    )
}
