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

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.github.zly2006.zhihu.account.SwitchAccountRequest
import com.github.zly2006.zhihu.account.ZhihuIdentityAccount
import com.github.zly2006.zhihu.account.ZhihuIdentityAccountListResponse
import com.github.zly2006.zhihu.account.ZhihuRequestProfile
import com.github.zly2006.zhihu.account.ZhihuSavedAccount
import com.github.zly2006.zhihu.account.applyIdentityHeaders
import com.github.zly2006.zhihu.account.identitySuccessBody
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.i18n.SettingsStrings
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.ui.components.AppSettingsPage
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

const val IDENTITY_MANAGEMENT_SCREEN_TAG = "identityManagement.screen"
const val IDENTITY_MANAGEMENT_CREATE_TAG = "identityManagement.create"
const val IDENTITY_MANAGEMENT_RETRY_TAG = "identityManagement.retry"
const val IDENTITY_MANAGEMENT_CREATE_CONFIRM_TAG = "identityManagement.createConfirm"

data class IdentityManagementState(
    val accounts: List<ZhihuIdentityAccount> = emptyList(),
    val currentAccountId: String = "",
    val loading: Boolean = false,
    val switchingToAccountId: String? = null,
    val creating: Boolean = false,
    val errorMessage: (() -> String)? = null,
    val operationError: Pair<String, () -> String>? = null,
) {
    /** 列表加载不占账号操作锁，本地切换、移除和添加入口不必等待远端列表。 */
    val busy: Boolean
        get() = switchingToAccountId != null || creating

    val canCreateSubAccount: Boolean
        get() = accounts.size < 2 &&
            accounts.any {
                it.canCreateSubAccount &&
                    it.subAccountControlStatus == 0
            }
}

/** 切换和创建身份都会签发新凭据，须原子替换会话及其客户端。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityManagementScreen() {
    val navigator = LocalNavigator.current
    val accountStore = rememberZhihuAccountStore()
    val accountClient = accountStore.client
    val verification by accountClient.verificationState.collectAsState()
    val restoreRequired by accountClient.browserCommentRestoreRequired.collectAsState()
    val sessionChangeBlocked = verification.blocksSessionChange || restoreRequired
    val userMessages = rememberUserMessageSink()
    val coroutineScope = rememberCoroutineScope()
    var state by remember(accountStore, accountClient) {
        mutableStateOf(
            IdentityManagementState(
                currentAccountId = accountStore.session.profile
                    ?.id
                    .orEmpty(),
                loading = !accountStore.usesBrowserSession,
            ),
        )
    }

    var browserListRequested by remember(accountClient) { mutableStateOf(false) }
    var switchTarget by remember(accountClient) { mutableStateOf<ZhihuIdentityAccount?>(null) }
    var showCreateDialog by remember(accountClient) { mutableStateOf(false) }
    var acceptedCreateRules by remember(accountClient) { mutableStateOf(false) }
    var switchLoginAccount by remember(accountClient) { mutableStateOf<ZhihuSavedAccount?>(null) }
    var removeLoginAccount by remember(accountClient) { mutableStateOf<ZhihuSavedAccount?>(null) }
    val savedAccounts by accountStore.accountsState.collectAsState()

    suspend fun refresh() {
        if (state.busy) return
        val owner = accountClient
        if (accountStore.client !== owner) return
        val session = owner.load()
        state = state.copy(loading = true, errorMessage = null)
        val accounts = try {
            if (accountStore.usesBrowserSession) {
                browserListRequested = true
                accountStore.browserIdentityAccounts(owner)
            } else {
                ZhihuJson
                    .decodeJson<ZhihuIdentityAccountListResponse>(
                        ZhihuJson.json.parseToJsonElement(
                            owner
                                .get("https://api.zhihu.com/people/account/list", ZhihuRequestProfile.Mobile) {
                                    applyIdentityHeaders(owner.load())
                                }.identitySuccessBody { SettingsStrings.fetchIdentities },
                        ),
                    ).data
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            if (accountStore.client === owner) {
                state = state.copy(loading = false, errorMessage = { e.message ?: SettingsStrings.fetchIdentitiesFailed })
            }
            return
        }
        currentCoroutineContext().ensureActive()
        if (accountStore.client === owner) {
            /** 列表加载可以与本地账号操作并行，不能用请求前的快照覆盖操作状态。 */
            state = state.copy(accounts = accounts, currentAccountId = session.profile?.id.orEmpty(), loading = false)
        }
    }

    LaunchedEffect(accountStore, accountClient) {
        if (!accountStore.usesBrowserSession) refresh()
    }

    AppSettingsPage(
        title = SettingsStrings.identityManagement,
        onBack = navigator.onNavigateBack,
        backTag = "identity_management_back_button",
        modifier = Modifier.testTag(IDENTITY_MANAGEMENT_SCREEN_TAG),
        pageTurnEnabled = !showCreateDialog && switchTarget == null && switchLoginAccount == null && removeLoginAccount == null,
    ) {
        IdentityManagementContent(
            state = state,
            savedAccounts = savedAccounts,
            browserSession = accountStore.usesBrowserSession,
            browserListRequested = browserListRequested,
            sessionChangeBlocked = sessionChangeBlocked,
            onRetry = { coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) { refresh() } },
            onSwitchTargetChange = { switchTarget = it },
            onRemoveLoginAccountChange = { removeLoginAccount = it },
            onSwitchLoginAccountChange = { switchLoginAccount = it },
            onCreate = {
                acceptedCreateRules = false
                showCreateDialog = true
            },
        )
    }

    switchTarget?.let { account ->
        IdentityManagementSwitchDialog(
            account = account,
            state = state,
            sessionChangeBlocked = sessionChangeBlocked,
            onDismissRequest = {
                if (!state.busy) switchTarget = null
            },
            onConfirm = {
                switchTarget = null
                coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    runCatching {
                        check(!state.busy) { SettingsStrings.accountOperationBusy }
                        state = state.copy(switchingToAccountId = account.id, operationError = null)
                        try {
                            require(account.id.isNotBlank()) { SettingsStrings.accountTargetEmpty }
                            val owner = accountClient
                            val oldSession = owner.load()
                            check(accountStore.client === owner && !owner.verificationState.value.blocksSessionChange) { SettingsStrings.accountChangedOrVerificationNeeded }
                            val result = if (accountStore.usesBrowserSession) {
                                accountStore.changeBrowserIdentity(owner, account.id)
                            } else {
                                val response = owner.post(
                                    "https://api.zhihu.com/account/switch",
                                    ZhihuRequestProfile.Mobile,
                                ) {
                                    applyIdentityHeaders(owner.load())
                                    contentType(ContentType.Application.Json)
                                    setBody(SwitchAccountRequest(account.id))
                                }
                                accountStore.completeIdentityChange(
                                    owner,
                                    oldSession,
                                    response.identitySuccessBody { SettingsStrings.switchAccount },
                                    account.id,
                                )
                            }
                            state = state.copy(
                                currentAccountId = result.account.id,
                                switchingToAccountId = null,
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            currentCoroutineContext().ensureActive()
                            if (accountStore.client !== accountClient) throw CancellationException(SettingsStrings.accountChanged)
                            state = state.copy(
                                switchingToAccountId = null,
                                operationError = "identity:${account.id}" to { e.message ?: SettingsStrings.switchAccountFailed },
                            )
                            throw e
                        }
                    }.onSuccess {
                        userMessages.showShortMessage(SettingsStrings.switchedToAccount(account.name))
                        navigator.onNavigateBack()
                    }.onFailure {
                        if (it is CancellationException) throw it
                        currentCoroutineContext().ensureActive()
                        if (accountStore.client !== accountClient) return@onFailure
                        userMessages.showLongMessage(it.message ?: SettingsStrings.switchAccountFailed)
                    }
                }
            },
            onCancel = { switchTarget = null },
        )
    }

    if (showCreateDialog) {
        IdentityManagementCreateDialog(
            acceptedCreateRules = acceptedCreateRules,
            state = state,
            sessionChangeBlocked = sessionChangeBlocked,
            onAcceptedCreateRulesChange = { acceptedCreateRules = it },
            onDismissRequest = {
                if (!state.busy) showCreateDialog = false
            },
            onConfirm = {
                showCreateDialog = false
                coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    runCatching {
                        check(!state.busy) { SettingsStrings.accountOperationBusy }
                        check(state.canCreateSubAccount) { SettingsStrings.cannotCreateAccount }
                        state = state.copy(creating = true, operationError = null)
                        try {
                            val owner = accountClient
                            val oldSession = owner.load()
                            check(accountStore.client === owner && !owner.verificationState.value.blocksSessionChange) { SettingsStrings.accountChangedOrVerificationNeeded }
                            val result = if (accountStore.usesBrowserSession) {
                                accountStore.changeBrowserIdentity(owner, null)
                            } else {
                                val response = owner.post(
                                    "https://api.zhihu.com/account/sub/register",
                                    ZhihuRequestProfile.Mobile,
                                ) {
                                    applyIdentityHeaders(owner.load())
                                }
                                accountStore.completeIdentityChange(
                                    owner,
                                    oldSession,
                                    response.identitySuccessBody { SettingsStrings.createAccount },
                                )
                            }
                            state = state.copy(
                                accounts = state.accounts + result.account,
                                currentAccountId = result.account.id,
                                creating = false,
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            currentCoroutineContext().ensureActive()
                            if (accountStore.client !== accountClient) throw CancellationException(SettingsStrings.accountChanged)
                            state = state.copy(
                                creating = false,
                                operationError = "create" to { e.message ?: SettingsStrings.createAccountFailed },
                            )
                            throw e
                        }
                    }.onSuccess {
                        userMessages.showLongMessage(SettingsStrings.accountCreatedReloading)
                        navigator.onNavigateBack()
                    }.onFailure {
                        if (it is CancellationException) throw it
                        currentCoroutineContext().ensureActive()
                        if (accountStore.client !== accountClient) return@onFailure
                        userMessages.showLongMessage(it.message ?: SettingsStrings.createAccountFailed)
                    }
                }
            },
            onCancel = { showCreateDialog = false },
        )
    }

    switchLoginAccount?.let { account ->
        IdentityManagementSavedSwitchDialog(
            account = account,
            state = state,
            sessionChangeBlocked = sessionChangeBlocked,
            onDismissRequest = { switchLoginAccount = null },
            onConfirm = confirm@{
                val owner = accountClient
                if (state.busy || accountStore.client !== owner || owner.verificationState.value.blocksSessionChange) return@confirm
                switchLoginAccount = null
                state = state.copy(switchingToAccountId = account.id, operationError = null)
                coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    if (accountStore.client !== owner) return@launch
                    try {
                        if (!accountStore.switchAccount(account.id) && accountStore.client === owner) {
                            state = state.copy(operationError = "saved:${account.id}" to { SettingsStrings.savedLoginExpired })
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        currentCoroutineContext().ensureActive()
                        if (accountStore.client === owner) state = state.copy(operationError = "saved:${account.id}" to { e.message ?: SettingsStrings.switchSavedAccountFailed })
                    } finally {
                        if (accountStore.client === owner) state = state.copy(switchingToAccountId = null)
                    }
                }
            },
            onCancel = { switchLoginAccount = null },
        )
    }

    removeLoginAccount?.let { account ->
        IdentityManagementRemoveDialog(
            account = account,
            state = state,
            sessionChangeBlocked = sessionChangeBlocked,
            onDismissRequest = { removeLoginAccount = null },
            onConfirm = confirm@{
                val owner = accountClient
                if (state.busy || accountStore.client !== owner || owner.verificationState.value.blocksSessionChange) return@confirm
                try {
                    accountStore.removeAccount(account.id)
                    state = state.copy(operationError = null)
                    removeLoginAccount = null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    state = state.copy(operationError = "saved:${account.id}" to { e.message ?: SettingsStrings.removeSavedAccountFailed })
                    removeLoginAccount = null
                }
            },
            onCancel = { removeLoginAccount = null },
        )
    }
}
