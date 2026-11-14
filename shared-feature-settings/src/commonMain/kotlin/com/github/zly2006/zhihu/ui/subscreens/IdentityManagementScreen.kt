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

import com.github.zly2006.zhihu.account.ZhihuRequestProfile
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.account.SwitchAccountRequest
import com.github.zly2006.zhihu.account.ZhihuAccountClient
import com.github.zly2006.zhihu.account.ZhihuAccountProfileSnapshot
import com.github.zly2006.zhihu.account.ZhihuAccountSession
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.account.ZhihuIdentityAccount
import com.github.zly2006.zhihu.account.ZhihuIdentityAccountListResponse
import com.github.zly2006.zhihu.account.ZhihuIdentityChangeResult
import com.github.zly2006.zhihu.account.ZhihuIdentityProfile
import com.github.zly2006.zhihu.account.ZhihuIdentityToken
import com.github.zly2006.zhihu.account.ZhihuSavedAccount
import com.github.zly2006.zhihu.account.applyIdentityHeaders
import com.github.zly2006.zhihu.account.createAccountHttpClient
import com.github.zly2006.zhihu.account.identitySuccessBody
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.requestLoginNavigation
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.ui.components.AppAlertDialog
import com.github.zly2006.zhihu.ui.components.AppCheckbox
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppSettingsPage
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingItemGroup
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

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
    val errorMessage: String? = null,
    val operationError: Pair<String, String>? = null,
) {
    val busy: Boolean
        get() = loading || switchingToAccountId != null || creating

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
    val sessionChangeBlocked = verification.blocksSessionChange
    val userMessages = rememberUserMessageSink()
    val coroutineScope = rememberCoroutineScope()
    var state by remember(accountStore, accountClient) {
        mutableStateOf(
            IdentityManagementState(
                currentAccountId = accountStore.session.profile
                    ?.id
                    .orEmpty(),
                loading = true,
            ),
        )
    }

    var switchTarget by remember(accountClient) { mutableStateOf<ZhihuIdentityAccount?>(null) }
    var showCreateDialog by remember(accountClient) { mutableStateOf(false) }
    var acceptedCreateRules by remember(accountClient) { mutableStateOf(false) }
    var switchLoginAccount by remember(accountClient) { mutableStateOf<ZhihuSavedAccount?>(null) }
    var removeLoginAccount by remember(accountClient) { mutableStateOf<ZhihuSavedAccount?>(null) }
    val savedAccounts by accountStore.accountsState.collectAsState()

    suspend fun refresh() {
        if (state.switchingToAccountId != null || state.creating) return
        val owner = accountClient
        if (accountStore.client !== owner) return
        val session = owner.load()
        state = state.copy(loading = true, errorMessage = null)
        val refreshed = try {
            state.copy(
                accounts = ZhihuJson
                    .decodeJson<ZhihuIdentityAccountListResponse>(
                        ZhihuJson.json.parseToJsonElement(
                            owner.get("https://api.zhihu.com/people/account/list", ZhihuRequestProfile.Mobile) {
                                applyIdentityHeaders(owner.load())
                            }.identitySuccessBody("获取身份列表"),
                        ),
                    ).data,
                currentAccountId = session.profile?.id.orEmpty(),
                loading = false,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            state.copy(loading = false, errorMessage = e.message ?: "获取身份列表失败")
        }
        currentCoroutineContext().ensureActive()
        if (accountStore.client === owner) state = refreshed
    }

    LaunchedEffect(accountStore, accountClient) {
        refresh()
    }

    AppSettingsPage(
        title = "身份管理",
        onBack = navigator.onNavigateBack,
        backTag = "identity_management_back_button",
        modifier = Modifier.testTag(IDENTITY_MANAGEMENT_SCREEN_TAG),
        pageTurnEnabled = !showCreateDialog && switchTarget == null && switchLoginAccount == null && removeLoginAccount == null,
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
                    onClick = {
                        coroutineScope.launch {
                            refresh()
                        }
                    },
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
                            { switchTarget = account }
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
                        title = { Text(account.session.profile?.name ?: account.session.username) },
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
                            AppIconButton(enabled = !state.busy && !sessionChangeBlocked, onClick = { removeLoginAccount = account }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "移除登录账号")
                            }
                        },
                        enabled = !state.busy && !sessionChangeBlocked,
                        onClick = { switchLoginAccount = account },
                    )
                }
                SettingItem(
                    title = { Text("添加其他手机号登录账号") },
                    icon = { Icon(Icons.AutoMirrored.Filled.Login, null) },
                    enabled = !state.busy && !sessionChangeBlocked,
                    onClick = ::requestLoginNavigation,
                )
            }

            if (state.accounts.isNotEmpty()) SettingItemGroup(
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
                    onClick = {
                        acceptedCreateRules = false
                        showCreateDialog = true
                    },
                )
            }
        }
    }

    switchTarget?.let { account ->
        AppAlertDialog(
            onDismissRequest = {
                if (!state.busy) switchTarget = null
            },
            title = { Text("切换账号") },
            text = {
                Text("将切换到“${account.name}”。之后的推荐、内容和互动行为都属于该账号。")
            },
            confirmButton = {
                AppTextButton(
                    enabled = !state.busy && !sessionChangeBlocked,
                    onClick = {
                        switchTarget = null
                        coroutineScope.launch {
                            runCatching {
                                check(!state.busy) { "另一个账号操作正在进行" }
                                state = state.copy(switchingToAccountId = account.id, operationError = null)
                                try {
                                    require(account.id.isNotBlank()) { "目标账号不能为空" }
                                    val owner = accountClient
                                    val oldSession = owner.load()
                                    check(accountStore.client === owner && !owner.verificationState.value.blocksSessionChange) { "账号已变更或需要先完成安全验证" }
                                    val response = owner.post(
                                        "https://api.zhihu.com/account/switch",
                                        ZhihuRequestProfile.Mobile,
                                    ) {
                                        applyIdentityHeaders(owner.load())
                                        contentType(ContentType.Application.Json)
                                        setBody(SwitchAccountRequest(account.id))
                                    }
                                    val result = accountStore.completeIdentityChange(
                                        owner,
                                        oldSession,
                                        response.identitySuccessBody("切换账号"),
                                        account.id,
                                    )
                                    state = state.copy(
                                        currentAccountId = result.account.id,
                                        switchingToAccountId = null,
                                    )
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    currentCoroutineContext().ensureActive()
                                    if (accountStore.client !== accountClient) throw CancellationException("账号已切换")
                                    state = state.copy(
                                        switchingToAccountId = null,
                                        operationError = "identity:${account.id}" to (e.message ?: "切换账号失败"),
                                    )
                                    throw e
                                }
                            }.onSuccess {
                                userMessages.showShortMessage("已切换到 ${account.name}")
                                navigator.onNavigateBack()
                            }.onFailure {
                                if (it is CancellationException) throw it
                                currentCoroutineContext().ensureActive()
                                if (accountStore.client !== accountClient) return@onFailure
                                userMessages.showLongMessage(it.message ?: "切换账号失败")
                            }
                        }
                    },
                ) {
                    Text("切换")
                }
            },
            dismissButton = {
                AppTextButton(
                    enabled = !state.busy && !sessionChangeBlocked,
                    onClick = { switchTarget = null },
                ) {
                    Text("取消")
                }
            },
        )
    }

    if (showCreateDialog) {
        AppAlertDialog(
            onDismissRequest = {
                if (!state.busy) showCreateDialog = false
            },
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
                                onValueChange = { acceptedCreateRules = it },
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
                    onClick = {
                        showCreateDialog = false
                        coroutineScope.launch {
                            runCatching {
                                check(!state.busy) { "另一个账号操作正在进行" }
                                check(state.canCreateSubAccount) { "当前账号暂不能创建新账号" }
                                state = state.copy(creating = true, operationError = null)
                                try {
                                    val owner = accountClient
                                    val oldSession = owner.load()
                                    check(accountStore.client === owner && !owner.verificationState.value.blocksSessionChange) { "账号已变更或需要先完成安全验证" }
                                    val response = owner.post(
                                        "https://api.zhihu.com/account/sub/register",
                                        ZhihuRequestProfile.Mobile,
                                    ) {
                                        applyIdentityHeaders(owner.load())
                                    }
                                    val result = accountStore.completeIdentityChange(
                                        owner,
                                        oldSession,
                                        response.identitySuccessBody("创建新账号"),
                                    )
                                    state = state.copy(
                                        accounts = state.accounts + result.account,
                                        currentAccountId = result.account.id,
                                        creating = false,
                                    )
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    currentCoroutineContext().ensureActive()
                                    if (accountStore.client !== accountClient) throw CancellationException("账号已切换")
                                    state = state.copy(
                                        creating = false,
                                        operationError = "create" to (e.message ?: "创建新账号失败"),
                                    )
                                    throw e
                                }
                            }.onSuccess {
                                userMessages.showLongMessage("新账号已创建并初始化，正在重新加载")
                                navigator.onNavigateBack()
                            }.onFailure {
                                if (it is CancellationException) throw it
                                currentCoroutineContext().ensureActive()
                                if (accountStore.client !== accountClient) return@onFailure
                                userMessages.showLongMessage(it.message ?: "创建新账号失败")
                            }
                        }
                    },
                ) {
                    Text("同意并创建")
                }
            },
            dismissButton = {
                AppTextButton(
                    enabled = !state.busy && !sessionChangeBlocked,
                    onClick = { showCreateDialog = false },
                ) {
                    Text("取消")
                }
            },
        )
    }

    switchLoginAccount?.let { account ->
        AppAlertDialog(
            onDismissRequest = { switchLoginAccount = null },
            title = { Text("切换登录账号") },
            text = { Text("将切换到“${account.session.profile?.name ?: account.session.username}”。") },
            confirmButton = {
                AppTextButton(
                    enabled = !state.busy && !sessionChangeBlocked,
                    onClick = {
                        val owner = accountClient
                        if (state.busy || accountStore.client !== owner || owner.verificationState.value.blocksSessionChange) return@AppTextButton
                        switchLoginAccount = null
                        state = state.copy(switchingToAccountId = account.id, operationError = null)
                        coroutineScope.launch {
                            if (accountStore.client !== owner) return@launch
                            try {
                                if (!accountStore.switchAccount(account.id) && accountStore.client === owner) {
                                    state = state.copy(operationError = "saved:${account.id}" to "登录凭据已失效，请重新添加这个账号")
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                currentCoroutineContext().ensureActive()
                                if (accountStore.client === owner) state = state.copy(operationError = "saved:${account.id}" to (e.message ?: "切换登录账号失败"))
                            } finally {
                                if (accountStore.client === owner) state = state.copy(switchingToAccountId = null)
                            }
                        }
                    },
                ) {
                    Text("切换")
                }
            },
            dismissButton = {
                AppTextButton(onClick = { switchLoginAccount = null }) {
                    Text("取消")
                }
            },
        )
    }

    removeLoginAccount?.let { account ->
        AppAlertDialog(
            onDismissRequest = { removeLoginAccount = null },
            title = { Text("移除登录账号") },
            text = { Text("将从本机删除“${account.session.profile?.name ?: account.session.username}”的登录凭据，不会注销知乎账号。") },
            confirmButton = {
                AppTextButton(
                    enabled = !state.busy && !sessionChangeBlocked,
                    onClick = {
                        val owner = accountClient
                        if (state.busy || accountStore.client !== owner || owner.verificationState.value.blocksSessionChange) return@AppTextButton
                        try {
                            accountStore.removeAccount(account.id)
                            state = state.copy(operationError = null)
                            removeLoginAccount = null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            state = state.copy(operationError = "saved:${account.id}" to (e.message ?: "移除登录账号失败"))
                            removeLoginAccount = null
                        }
                    },
                ) {
                    Text("移除")
                }
            },
            dismissButton = {
                AppTextButton(onClick = { removeLoginAccount = null }) {
                    Text("取消")
                }
            },
        )
    }
}
