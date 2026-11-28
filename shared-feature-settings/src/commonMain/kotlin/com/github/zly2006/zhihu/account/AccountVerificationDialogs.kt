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

package com.github.zly2006.zhihu.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.zly2006.zhihu.data.feedbackText
import com.github.zly2006.zhihu.i18n.AccountStrings
import com.github.zly2006.zhihu.navigation.requestLoginNavigation
import com.github.zly2006.zhihu.platform.exportTestTagsForUiAutomation
import com.github.zly2006.zhihu.platform.rememberPlainTextClipboard
import com.github.zly2006.zhihu.ui.components.AppAlertDialog
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppCompactTopBar
import com.github.zly2006.zhihu.ui.components.AppDialog
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.RegisterOverlayBlur
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountVerificationDialogs(
    client: ZhihuAccountClient,
    visible: Boolean = true,
    onLogin: () -> Unit = ::requestLoginNavigation,
) {
    val browser = client.browserVerification
    if (browser != null) {
        if (!visible) return
        val verification by key(client) { browser.collectAsState() }
        verification?.let {
            key(it) { BrowserVerificationDialog(it) }
            return
        }
        val restoreRequired by client.browserCommentRestoreRequired.collectAsState()
        if (restoreRequired) {
            key(client) { BrowserCommentRecoveryDialog(client) }
            return
        }
    }
    val state by client.verificationState.collectAsState()
    val loginPrompt by client.loginPrompt.collectAsState()
    if (visible && loginPrompt == SessionExpiryPrompt.SHOWING && state.phase == HumanVerificationPhase.ACTIVE) {
        AppDialog(
            title = AccountStrings.loginRequiredTitle,
            summary = AccountStrings.loginRequiredSummary,
            confirmText = AccountStrings.loginGo,
            dismissText = AccountStrings.later,
            onConfirm = {
                client.dismissLoginRequirement()
                onLogin()
            },
            onDismissRequest = client::dismissLoginRequirement,
            modifier = Modifier.testTag("login_requirement_prompt"),
        )
        return
    }
    if (browser != null) return

    val feedback by client.http403Log.pendingFeedback.collectAsState()
    val copyText = rememberPlainTextClipboard()
    val url = state.url
    val initialCookies = remember(client, url) { state.candidateCookies ?: client.load().cookies.toMap() }
    val changedCookies = remember(client, url) { mutableMapOf<String, String>() }
    var readCookies by remember(client, url) { mutableStateOf<(suspend () -> Map<String, String>)?>(null) }
    var completing by remember(client, url) { mutableStateOf(false) }
    var completionError by remember(client, url) { mutableStateOf<(() -> String)?>(null) }
    val scope = rememberCoroutineScope()
    if (!visible) return

    if (!isLoginRiskControlSupported &&
        (state.phase == HumanVerificationPhase.PROMPT || state.phase == HumanVerificationPhase.VERIFYING)
    ) {
        AppDialog(
            title = AccountStrings.verificationUnsupportedTitle,
            summary = AccountStrings.verificationUnsupportedSummary,
            confirmText = AccountStrings.verificationUnsupportedConfirm,
            confirmEnabled = false,
            onConfirm = {},
            onDismissRequest = {},
            modifier = Modifier.testTag("human_verification_unsupported"),
        )
        return
    }

    when (state.phase) {
        HumanVerificationPhase.PROMPT -> AppDialog(
            title = AccountStrings.verificationRequiredTitle,
            summary = AccountStrings.verificationRequiredSummary,
            confirmText = AccountStrings.verificationGo,
            confirmEnabled = !url.isNullOrBlank(),
            onConfirm = client::beginHumanVerification,
            onDismissRequest = {},
            modifier = Modifier.testTag("human_verification_prompt"),
        )
        HumanVerificationPhase.VERIFYING -> {
            if (url.isNullOrBlank()) {
                AppDialog(
                    title = AccountStrings.verificationOpenFailed,
                    summary = AccountStrings.verificationUrlMissingSummary,
                    confirmText = AccountStrings.verificationUrlMissing,
                    confirmEnabled = false,
                    onConfirm = {},
                    onDismissRequest = {},
                )
                return
            }

            fun complete(automaticallyVerified: Boolean) {
                if (!completing && client.verificationState.value.phase == HumanVerificationPhase.VERIFYING) {
                    completing = true
                    completionError = null
                    scope.launch {
                        try {
                            checkNotNull(readCookies).invoke().forEach { (name, value) ->
                                if (initialCookies[name] != value) changedCookies[name] = value else changedCookies.remove(name)
                            }
                            if (automaticallyVerified) {
                                client.completeHumanVerification(changedCookies)
                            } else if (!client.confirmHumanVerification(changedCookies)) {
                                completionError = { AccountStrings.verificationNotConfirmed }
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            completionError = { AccountStrings.verificationSaveFailed }
                        } finally {
                            completing = false
                        }
                    }
                }
            }
            RegisterOverlayBlur()
            Dialog(
                onDismissRequest = {},
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                ),
            ) {
                AppScaffold(
                    modifier = Modifier.fillMaxSize().exportTestTagsForUiAutomation().testTag("human_verification_page"),
                    topBar = { AppCompactTopBar(title = AccountStrings.verificationTitle) },
                    bottomBar = {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(AppDimens.Space12),
                            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8, Alignment.End),
                            verticalArrangement = Arrangement.spacedBy(AppDimens.Space8),
                        ) {
                            AppButton(
                                text = if (completing) AccountStrings.verificationConfirming else AccountStrings.verificationDone,
                                enabled = !completing && readCookies != null,
                                onClick = { complete(automaticallyVerified = false) },
                                modifier = Modifier.testTag("human_verification_complete"),
                            )
                        }
                    },
                ) { innerPadding ->
                    Column(Modifier.fillMaxSize().padding(innerPadding)) {
                        Text(
                            AccountStrings.verificationInstructions,
                            modifier = Modifier.padding(horizontal = AppDimens.Space12, vertical = AppDimens.Space8),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        completionError?.let {
                            Text(
                                it(),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = AppDimens.Space12),
                            )
                        }
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                            Box(Modifier.fillMaxWidth().height(maxHeight)) {
                                key(client, url) {
                                    LoginRiskControlPane(
                                        url = url,
                                        cookies = initialCookies,
                                        onCookiesChanged = { updated ->
                                            // 仅合并本次验证改变的凭据，避免旧快照覆盖并发请求更新的 Cookie。
                                            updated.forEach { (name, value) ->
                                                if (initialCookies[name] != value) changedCookies[name] = value else changedCookies.remove(name)
                                            }
                                        },
                                        onCookieReaderReady = { readCookies = it },
                                        onVerificationComplete = { complete(automaticallyVerified = true) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        HumanVerificationPhase.RESTART_REQUIRED -> AppDialog(
            title = AccountStrings.restartTitle,
            summary = AccountStrings.verificationRestartSummary,
            confirmText = AccountStrings.acknowledge,
            onConfirm = client::acknowledgeVerificationRestart,
            onDismissRequest = {},
            modifier = Modifier.testTag("human_verification_restart"),
        )
        HumanVerificationPhase.ACTIVE, HumanVerificationPhase.DISMISSED -> feedback?.let { first ->
            AppDialog(
                title = AccountStrings.http403Title,
                summary = AccountStrings.http403Summary,
                confirmText = AccountStrings.http403Copy,
                onConfirm = {
                    copyText(AccountStrings.http403Clipboard, first.feedbackText())
                    client.http403Log.dismissFeedback()
                },
                dismissText = AccountStrings.later,
                onDismissRequest = client.http403Log::dismissFeedback,
                modifier = Modifier.testTag("http403_feedback_prompt"),
            )
        }
    }
}

@Composable
private fun BrowserCommentRecoveryDialog(client: ZhihuAccountClient) {
    val store = rememberZhihuAccountStore()
    val scope = rememberCoroutineScope()
    var dismissed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    if (dismissed) return
    AppDialog(
        title = AccountStrings.webRestoreIdentity,
        summary = if (failed) AccountStrings.webRestoreIdentityFailed else AccountStrings.webRestoreIdentitySummary,
        confirmText = if (busy) AccountStrings.verificationConfirming else AccountStrings.webRestoreIdentity,
        confirmEnabled = !busy,
        dismissText = AccountStrings.later,
        onDismissRequest = { if (!busy) dismissed = true },
        onConfirm = {
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                busy = true
                failed = false
                try {
                    failed = !store.restoreBrowserCommentIdentity(client)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    failed = true
                } finally {
                    busy = false
                }
            }
        },
        modifier = Modifier.testTag("web_comment_identity_restore"),
    )
}

@Composable
private fun BrowserVerificationDialog(verification: ZhihuAccountClient.BrowserVerification) {
    val scope = rememberCoroutineScope()
    var failure by remember { mutableStateOf<(() -> String)?>(null) }

    fun dismiss() {
        scope.cancel()
        verification.dismiss()
    }

    AppAlertDialog(
        onDismissRequest = ::dismiss,
        modifier = Modifier.testTag("web_verification_prompt"),
        title = { Text(AccountStrings.verificationRequiredTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Space8)) {
                Text(AccountStrings.webChallengeInstructions)
                AppButton(
                    text = AccountStrings.webChallengeOpen,
                    onClick = {
                        if (verification.open()) failure = null else failure = { AccountStrings.webChallengeOpenFailed }
                    },
                    enabled = !verification.checking,
                    modifier = Modifier.testTag("web_verification_open"),
                )
                failure?.let {
                    Text(it(), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            AppTextButton(
                text = if (verification.checking) AccountStrings.verificationConfirming else AccountStrings.webChallengeAuthorized,
                enabled = verification.opened && !verification.checking,
                onClick = {
                    failure = null
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        try {
                            if (!verification.check()) failure = { AccountStrings.verificationNotConfirmed }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            failure = { AccountStrings.webChallengeCheckFailed }
                        }
                    }
                },
                modifier = Modifier.testTag("web_verification_check"),
            )
        },
        dismissButton = {
            AppTextButton(
                text = AccountStrings.cancel,
                onClick = ::dismiss,
                modifier = Modifier.testTag("web_verification_cancel"),
            )
        },
    )
}
