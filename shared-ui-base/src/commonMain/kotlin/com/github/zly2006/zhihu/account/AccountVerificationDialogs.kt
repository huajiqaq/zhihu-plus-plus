/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 * SPDX-License-Identifier: AGPL-3.0-only
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.zly2006.zhihu.data.feedbackText
import com.github.zly2006.zhihu.platform.exportTestTagsForUiAutomation
import com.github.zly2006.zhihu.platform.rememberPlainTextClipboard
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppCompactTopBar
import com.github.zly2006.zhihu.ui.components.AppDialog
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.RegisterOverlayBlur
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountVerificationDialogs(
    client: ZhihuAccountClient,
    visible: Boolean = true,
) {
    val state by client.verificationState.collectAsState()
    val feedback by client.http403Log.pendingFeedback.collectAsState()
    val copyText = rememberPlainTextClipboard()
    val url = state.url
    val initialCookies = remember(client, url) { state.candidateCookies ?: client.load().cookies.toMap() }
    val changedCookies = remember(client, url) { mutableMapOf<String, String>() }
    var completing by remember(client, url) { mutableStateOf(false) }
    var completionError by remember(client, url) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    if (!visible) return

    if (!isLoginRiskControlSupported &&
        (state.phase == HumanVerificationPhase.PROMPT || state.phase == HumanVerificationPhase.VERIFYING)
    ) {
        AppDialog(
            title = "当前平台不支持安全验证",
            summary = "知乎要求完成安全验证后才能继续。当前平台无法打开验证页面，请完全退出应用后使用支持验证的平台。",
            confirmText = "无法在当前平台完成",
            confirmEnabled = false,
            onConfirm = {},
            onDismissRequest = {},
            modifier = Modifier.testTag("human_verification_unsupported"),
        )
        return
    }

    when (state.phase) {
        HumanVerificationPhase.PROMPT -> AppDialog(
            title = "需要安全验证",
            summary = "知乎要求完成人工安全验证，验证前无法继续使用。完成后需完全退出并重新打开应用。",
            confirmText = "去验证",
            confirmEnabled = !url.isNullOrBlank(),
            onConfirm = client::beginHumanVerification,
            onDismissRequest = {},
            modifier = Modifier.testTag("human_verification_prompt"),
        )
        HumanVerificationPhase.VERIFYING -> {
            if (url.isNullOrBlank()) {
                AppDialog(
                    title = "无法打开安全验证",
                    summary = "未取得可用的验证地址，请完全退出应用后重新打开。验证前无法继续使用。",
                    confirmText = "缺少验证地址",
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
                            readLoginRiskControlCookies(url).forEach { (name, value) ->
                                if (initialCookies[name] != value) changedCookies[name] = value else changedCookies.remove(name)
                            }
                            if (automaticallyVerified) {
                                client.completeHumanVerification(changedCookies)
                            } else if (!client.confirmHumanVerification(changedCookies)) {
                                completionError = "知乎尚未确认验证通过，请先完成页面中的验证。"
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            completionError = "未能确认或保存验证信息，请重试。"
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
                    topBar = { AppCompactTopBar(title = "知乎安全验证") },
                    bottomBar = {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(AppDimens.Space12),
                            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8, Alignment.End),
                            verticalArrangement = Arrangement.spacedBy(AppDimens.Space8),
                        ) {
                            AppButton(
                                text = if (completing) "正在确认…" else "我已完成验证",
                                enabled = !completing,
                                onClick = { complete(automaticallyVerified = false) },
                                modifier = Modifier.testTag("human_verification_complete"),
                            )
                        }
                    },
                ) { innerPadding ->
                    Column(Modifier.fillMaxSize().padding(innerPadding)) {
                        Text(
                            "请完成知乎页面中的验证；成功后会提示重启。未自动识别时，可点击“我已完成验证”。",
                            modifier = Modifier.padding(horizontal = AppDimens.Space12, vertical = AppDimens.Space8),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        completionError?.let {
                            Text(
                                it,
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
            title = "请重启应用",
            summary = "安全验证已通过，首次 403 记录已自动清空。请完全退出应用后重新打开，使验证信息生效。",
            confirmText = "知道了",
            onConfirm = client::acknowledgeVerificationRestart,
            onDismissRequest = {},
            modifier = Modifier.testTag("human_verification_restart"),
        )
        HumanVerificationPhase.ACTIVE, HumanVerificationPhase.DISMISSED -> feedback?.let { first ->
            AppDialog(
                title = "遇到 403 了",
                summary = "知乎拒绝了这次请求。请将首次 403 信息反馈给作者，以便检查并更新接口。\n仅保留首次记录，Token、Cookie、签名和地址参数已脱敏。403 也可能与登录或安全验证有关，不一定是接口失效。",
                confirmText = "复制脱敏信息",
                onConfirm = {
                    copyText("首次403反馈", first.feedbackText())
                    client.http403Log.dismissFeedback()
                },
                dismissText = "稍后",
                onDismissRequest = client.http403Log::dismissFeedback,
                modifier = Modifier.testTag("http403_feedback_prompt"),
            )
        }
    }
}
