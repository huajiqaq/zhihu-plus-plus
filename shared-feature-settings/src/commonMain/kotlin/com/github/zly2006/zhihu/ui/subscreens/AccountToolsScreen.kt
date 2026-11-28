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
import com.github.zly2006.zhihu.i18n.AccountStrings
import com.github.zly2006.zhihu.i18n.SettingsStrings
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Login
import com.github.zly2006.zhihu.ui.components.AppAlertDialog
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppIcon
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppSecureTextField
import com.github.zly2006.zhihu.ui.components.AppSettingsPage
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingItemGroup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

const val DEVELOPER_SETTINGS_BACK_BUTTON_TAG = "developerSettings/backButton"

private enum class AccountTool { Verify, RefreshToken, ImportCookie, RestoreIdentity }

private data class AccountToolResult(
    private val text: () -> String,
    val failed: Boolean,
) {
    val message: String get() = text()
}

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
    val restoreRequired by accountClient.browserCommentRestoreRequired.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var cookieDialogOwner by remember(accountStore) { mutableStateOf<ZhihuAccountClient?>(null) }
    val showCookieDialog = cookieDialogOwner === accountClient
    var showDiagnostics by remember(accountClient) { mutableStateOf(false) }
    var toolState by remember(accountStore) { mutableStateOf(AccountToolsState(accountClient)) }
    val state = toolState.takeIf { it.owner === accountClient } ?: AccountToolsState(accountClient)
    val canRestore = state.running == null && !verification.blocksSessionChange
    val canRun = canRestore && !restoreRequired

    LaunchedEffect(accountClient) {
        if (accountStore.client === accountClient) {
            if (toolState.owner !== accountClient) toolState = AccountToolsState(accountClient)
            if (cookieDialogOwner !== accountClient) cookieDialogOwner = null
        }
    }

    fun runTool(
        tool: AccountTool,
        failurePrefix: () -> String,
        onResult: (AccountToolResult) -> Unit = {},
        action: suspend (ZhihuAccountClient) -> AccountToolResult,
    ) {
        val owner = accountClient
        if (accountStore.client !== owner || owner.verificationState.value.blocksSessionChange) return
        if (owner.browserCommentRestoreRequired.value && tool != AccountTool.RestoreIdentity) return
        if (toolState.owner === owner && toolState.running != null) return
        toolState = (toolState.takeIf { it.owner === owner } ?: AccountToolsState(owner)).copy(running = tool)
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
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
                    AccountToolResult({ SettingsStrings.failureWithReason(failurePrefix(), e.message) }, failed = true)
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
        title = SettingsStrings.accountLoginMaintenance,
        onBack = navigator.onNavigateBack,
        backTag = DEVELOPER_SETTINGS_BACK_BUTTON_TAG,
        pageTurnEnabled = !showCookieDialog && !showDiagnostics,
    ) {
        SettingItemGroup(title = SettingsStrings.currentLogin) {
            SettingItem(
                title = {
                    Text(
                        if (session.login) {
                            session.profile?.name?.takeIf { it.isNotBlank() } ?: session.username.ifBlank { SettingsStrings.loggedIn }
                        } else {
                            SettingsStrings.notLoggedIn
                        },
                    )
                },
                description = {
                    Text(
                        when {
                            verification.blocksSessionChange -> SettingsStrings.finishVerificationFirst
                            session.login ->
                                session.profile
                                    ?.urlToken
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { SettingsStrings.loggedInAs(it) } ?: SettingsStrings.loggedIn
                            accountStore.usesBrowserSession -> SettingsStrings.browserLoginPrompt
                            else -> SettingsStrings.loginForMaintenance
                        },
                    )
                },
                icon = { AppIcon(if (session.login) Icons.Default.AccountCircle else Icons.Default.PersonOff, null) },
            )
        }

        SettingItemGroup(
            title = SettingsStrings.maintenance,
            footer = { Text(if (accountStore.usesBrowserSession) SettingsStrings.browserLoginPrivacy else SettingsStrings.cookieSecurityWarning) },
        ) {
            if (restoreRequired) {
                AccountToolRow(
                    title = AccountStrings.webRestoreIdentity,
                    idleSummary = AccountStrings.webRestoreIdentitySummary,
                    runningSummary = AccountStrings.verificationConfirming,
                    icon = Icons.Default.Sync,
                    running = state.running == AccountTool.RestoreIdentity,
                    result = state.results[AccountTool.RestoreIdentity],
                    enabled = canRestore,
                    onClick = {
                        runTool(AccountTool.RestoreIdentity, { AccountStrings.webRestoreIdentityFailed }) { owner ->
                            if (accountStore.restoreBrowserCommentIdentity(owner)) {
                                AccountToolResult({ AccountStrings.webRestoreIdentitySucceeded }, failed = false)
                            } else {
                                AccountToolResult({ AccountStrings.webRestoreIdentityFailed }, failed = true)
                            }
                        }
                    },
                )
            }
            AccountToolRow(
                title = SettingsStrings.verifyLogin,
                idleSummary = SettingsStrings.verifyLoginDescription,
                runningSummary = SettingsStrings.verifyingLogin,
                icon = Icons.Default.VerifiedUser,
                running = state.running == AccountTool.Verify,
                result = state.results[AccountTool.Verify],
                enabled = canRun,
                onClick = {
                    runTool(AccountTool.Verify, { SettingsStrings.verifyLoginFailed }) { owner ->
                        if (owner.refreshAndSaveProfile() != null) {
                            AccountToolResult({ SettingsStrings.loginValid }, failed = false)
                        } else {
                            AccountToolResult({ if (accountStore.usesBrowserSession) SettingsStrings.browserLoginUnverified else SettingsStrings.loginInvalid }, failed = true)
                        }
                    }
                },
            )
            AccountToolRow(
                title = SettingsStrings.refreshToken,
                idleSummary = if (accountStore.usesBrowserSession) {
                    SettingsStrings.browserNoMobileToken
                } else if (session.login) {
                    SettingsStrings.refreshTokenDescription
                } else {
                    SettingsStrings.refreshTokenRequiresLogin
                },
                runningSummary = SettingsStrings.refreshingToken,
                icon = Icons.Default.Sync,
                running = state.running == AccountTool.RefreshToken,
                result = state.results[AccountTool.RefreshToken].takeIf { session.login },
                enabled = !accountStore.usesBrowserSession && session.login && canRun,
                onClick = {
                    runTool(AccountTool.RefreshToken, { SettingsStrings.refreshFailed }) { owner ->
                        if (owner.refreshCredentials(force = true)) {
                            AccountToolResult({ SettingsStrings.refreshSucceeded }, failed = false)
                        } else {
                            AccountToolResult({ SettingsStrings.refreshCredentialsMissing }, failed = true)
                        }
                    }
                },
            )
            AccountToolRow(
                title = if (accountStore.usesBrowserSession) SettingsStrings.browserManageSession else SettingsStrings.importCookie,
                idleSummary = if (accountStore.usesBrowserSession) SettingsStrings.browserSessionCookieDescription else SettingsStrings.importCookieDescription,
                runningSummary = SettingsStrings.verifyingCookie,
                icon = Icons.Default.Cookie,
                running = state.running == AccountTool.ImportCookie,
                result = state.results[AccountTool.ImportCookie],
                enabled = canRun,
                onClick = { if (accountStore.usesBrowserSession) navigator.onNavigate(Login) else cookieDialogOwner = accountClient },
            )
        }

        SettingItemGroup(title = SettingsStrings.networkDiagnostics) {
            SettingItem(
                title = { Text(SettingsStrings.forbiddenRequestRecord) },
                description = { Text(SettingsStrings.forbiddenRequestDescription) },
                icon = { AppIcon(Icons.Default.BugReport, null) },
                endAction = {
                    AppIcon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
        var importError by remember(cookieInput.text) { mutableStateOf<(() -> String)?>(null) }
        val importing = state.running == AccountTool.ImportCookie
        val closeDialog = {
            cookieDialogOwner = null
            cookieInput.clearText()
            showCookieText = false
        }
        AppAlertDialog(
            onDismissRequest = { if (!importing) closeDialog() },
            title = { Text(SettingsStrings.importCookie) },
            text = {
                Column {
                    Text(
                        SettingsStrings.cookieInputInstructions,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = AppDimens.Space16),
                    )
                    AppSecureTextField(
                        state = cookieInput,
                        enabled = !importing,
                        labelText = SettingsStrings.cookieString,
                        placeholder = "name1=value1; name2=value2; name3=value3",
                        textObfuscationMode = if (showCookieText) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
                        trailingIcon = {
                            AppIconButton(enabled = !importing, onClick = { showCookieText = !showCookieText }) {
                                AppIcon(
                                    imageVector = if (showCookieText) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (showCookieText) SettingsStrings.hide else SettingsStrings.show,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    importError?.let {
                        Text(it(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = AppDimens.Space8))
                    }
                }
            },
            confirmButton = {
                AppButton(
                    text = if (importing) SettingsStrings.verifying else SettingsStrings.importAndVerify,
                    enabled = cookieInput.text.isNotBlank() && canRun,
                    onClick = {
                        val cookies = parseCookieAssignments(cookieInput.text.toString())
                        if (cookies.isEmpty()) {
                            importError = { SettingsStrings.invalidCookieData }
                        } else {
                            importError = null
                            runTool(
                                AccountTool.ImportCookie,
                                { SettingsStrings.loginVerificationError },
                                onResult = { result -> if (result.failed) importError = { result.message } },
                            ) { owner ->
                                if (accountStore.login(owner, cookies.toMutableMap())) {
                                    currentCoroutineContext().ensureActive()
                                    val result = AccountToolResult({ SettingsStrings.cookieImported }, failed = false)
                                    /** 仅成功提交的候选可以把导入结果交给新客户端；旧验证、刷新结果不再适用。 */
                                    toolState = AccountToolsState(accountStore.client, results = mapOf(AccountTool.ImportCookie to result))
                                    closeDialog()
                                    result
                                } else {
                                    AccountToolResult({ SettingsStrings.cookieImportRejected }, failed = true)
                                }
                            }
                        }
                    },
                )
            },
            dismissButton = {
                AppTextButton(text = SettingsStrings.cancel, enabled = !importing, onClick = closeDialog)
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
        icon = { AppIcon(icon, null) },
        endAction = { if (running) AppSpinner(size = AppDimens.InlineSpinnerSize) },
        enabled = enabled,
        onClick = onClick,
    )
}
