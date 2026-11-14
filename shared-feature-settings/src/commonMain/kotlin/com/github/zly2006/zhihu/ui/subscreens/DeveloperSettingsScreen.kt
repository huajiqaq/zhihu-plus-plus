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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.ZHIHU_ME_URL
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.SentenceSimilarityTest
import com.github.zly2006.zhihu.notification.HOME_NOTIFICATION_READ_UUIDS_PREFERENCE_KEY
import com.github.zly2006.zhihu.platform.isSentenceSimilaritySupported
import com.github.zly2006.zhihu.platform.rememberPlainTextClipboard
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.ui.HOME_PIN_ANNOUNCEMENT_READ_KEY_PREFIX
import com.github.zly2006.zhihu.ui.TtsState
import com.github.zly2006.zhihu.ui.components.AppAlertDialog
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppCard
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppSettingsPage
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.AppTextField
import com.github.zly2006.zhihu.ui.components.SettingItemWithSwitch
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

const val DEVELOPER_SETTINGS_BACK_BUTTON_TAG = "developerSettings/backButton"
const val DEVELOPER_SETTINGS_MODE_TAG = "developerSettings/modeToggle"
const val DEVELOPER_SETTINGS_SENTENCE_SIMILARITY_TAG = "developerSettings/sentenceSimilarity"
const val DEVELOPER_SETTINGS_COLOR_SCHEME_TAG = "developerSettings/colorScheme"

/**
 * 开发者选项页。
 *
 * 页面用于展示网络、电源、连续使用时长、TTS 引擎等诊断信息，并提供验证登录、刷新 Token、手动 Cookie、签名请求、
 * 句子相似度和配色查看器等调试入口。关闭开发者模式会立即返回上一页，因此新增调试项应避免依赖页面长期驻留。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeveloperSettingsScreen() {
    val navigator = LocalNavigator.current
    val environment = rememberPaginationEnvironment(allowGuestAccess = false)
    val accountStore = rememberZhihuAccountStore()
    val accountClient = accountStore.client
    val runtimeInfo = rememberDeveloperInfo()
    val copyPlainText = rememberPlainTextClipboard()
    val userMessages = rememberUserMessageSink()
    val coroutineScope = rememberCoroutineScope()
    val settings = rememberSettingsStore()
    var developerModeEnabled by remember {
        mutableStateOf(settings.getBoolean("developer", false))
    }
    var showCookieDialog by remember { mutableStateOf(false) }
    var showSignedRequestDialog by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    LaunchedEffect(accountClient) {
        showCookieDialog = false
        showSignedRequestDialog = false
        showDiagnostics = false
    }
    AppSettingsPage(
        title = "开发者选项",
        onBack = navigator.onNavigateBack,
        backTag = DEVELOPER_SETTINGS_BACK_BUTTON_TAG,
        pageTurnEnabled = !showCookieDialog && !showSignedRequestDialog && !showDiagnostics,
    ) {
        SettingItemWithSwitch(
            modifier = Modifier.testTag(DEVELOPER_SETTINGS_MODE_TAG),
            title = { Text("开发者模式") },
            checked = developerModeEnabled,
            onCheckedChange = {
                developerModeEnabled = it
                settings.putBoolean("developer", it)
                if (!it) {
                    navigator.onNavigateBack()
                }
            },
        )
        SelectionContainer {
            Column {
                Text(runtimeInfo.networkStatus)
                runtimeInfo.powerSaveModeText?.let { Text(it) }
                Text("连续使用时长：${formatContinuousUsageDuration(runtimeInfo.continuousUsageDurationMs)}")

                Spacer(Modifier.height(16.dp))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton(onClick = {
                coroutineScope.launch {
                    if (accountStore.client.refreshAndSaveProfile() != null) {
                        userMessages.showShortMessage("登录成功")
                    } else {
                        userMessages.showShortMessage("登录失败")
                    }
                }
            }) { Text("验证登录") }

            AppButton(onClick = {
                coroutineScope.launch {
                    check(accountStore.client.credentialRefresher.refresh(force = true)) {
                        "缺少移动端刷新凭据或刚刚尝试过刷新，请稍后重试"
                    }
                    userMessages.showShortMessage("刷新成功")
                }
            }) { Text("刷新Token") }

            AppButton(onClick = { showCookieDialog = true }) { Text("手动设置Cookie") }

            AppButton(onClick = { showSignedRequestDialog = true }) { Text("签名请求") }

            AppButton(
                modifier = Modifier.testTag("network_diagnostics_open"),
                onClick = { showDiagnostics = true },
            ) { Text("403 请求记录") }

            if (isSentenceSimilaritySupported) {
                AppButton(
                    modifier = Modifier.testTag(DEVELOPER_SETTINGS_SENTENCE_SIMILARITY_TAG),
                    onClick = {
                        navigator.onNavigate(SentenceSimilarityTest)
                    },
                ) { Text("句子相似度") }
            }

            AppButton(
                modifier = Modifier.testTag(DEVELOPER_SETTINGS_COLOR_SCHEME_TAG),
                onClick = {
                    navigator.onNavigate(Account.DeveloperSettings.ColorScheme)
                },
            ) { Text("Color Scheme") }

            AppButton(onClick = {
                settings.remove(HOME_NOTIFICATION_READ_UUIDS_PREFERENCE_KEY)
                settings.removeByPrefix(HOME_PIN_ANNOUNCEMENT_READ_KEY_PREFIX)
                userMessages.showShortMessage("已清除 online notification 和作者想法推送的已读记录")
            }) { Text("清除所有 online notification 和作者想法推送已读记录") }
        }

        // TTS引擎信息显示
        AppCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                Text(
                    "语音朗读引擎信息",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "当前引擎",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        runtimeInfo.currentTtsEngineLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "引擎状态",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (runtimeInfo.ttsState.isSpeaking) {
                            "正在朗读"
                        } else if (runtimeInfo.ttsState != TtsState.Uninitialized) {
                            "就绪"
                        } else {
                            "未就绪"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            runtimeInfo.ttsState.isSpeaking -> MaterialTheme.colorScheme.tertiary
                            runtimeInfo.ttsState != TtsState.Uninitialized -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.error
                        },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "引擎列表",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        runtimeInfo.availableTtsEngineLabels.joinToString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            runtimeInfo.ttsState.isSpeaking -> MaterialTheme.colorScheme.tertiary
                            runtimeInfo.ttsState != TtsState.Uninitialized -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
        }
    }

    if (showDiagnostics) {
        NetworkRequestDiagnostics(
            log = accountStore.http403Log,
            client = accountStore.client,
            onDismissRequest = { showDiagnostics = false },
        )
    }

    if (showCookieDialog) {
        var cookieInputText by remember { mutableStateOf("") }
        var showCookieText by remember { mutableStateOf(false) }
        var importing by remember { mutableStateOf(false) }
        var importError by remember { mutableStateOf<String?>(null) }
        AppAlertDialog(
            onDismissRequest = {
                if (!importing) {
                    showCookieDialog = false
                    cookieInputText = ""
                    showCookieText = false
                }
            },
            title = { Text("手动设置Cookie") },
            text = {
                Column {
                    Text(
                        "请输入完整的Cookie字符串，格式类似于document.cookie，使用 \"; \" 分割各个cookie项",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                    AppTextField(
                        value = cookieInputText,
                        onValueChange = { cookieInputText = it; importError = null },
                        enabled = !importing,
                        labelText = "Cookie字符串",
                        placeholder = "name1=value1; name2=value2; name3=value3",
                        visualTransformation = if (showCookieText) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            AppIconButton(enabled = !importing, onClick = { showCookieText = !showCookieText }) {
                                Icon(
                                    imageVector = if (showCookieText) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (showCookieText) "隐藏" else "显示",
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 5,
                    )
                    importError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                AppButton(
                    enabled = !importing && cookieInputText.isNotBlank(),
                    onClick = {
                        if (cookieInputText.isNotBlank() && !importing) {
                            try {
                                // 解析cookie字符串
                                val cookies = mutableMapOf<String, String>()
                                cookieInputText.split("; ").forEach { cookieItem ->
                                    val parts = cookieItem.split("=", limit = 2)
                                    if (parts.size == 2) {
                                        cookies[parts[0].trim()] = parts[1].trim()
                                    }
                                }

                                if (cookies.isNotEmpty()) {
                                    val owner = accountClient
                                    if (accountStore.client !== owner || owner.verificationState.value.blocksSessionChange) return@AppButton
                                    importing = true
                                    importError = null
                                    coroutineScope.launch {
                                        if (accountStore.client !== owner) return@launch
                                        try {
                                            if (accountStore.login(cookies)) {
                                                userMessages.showShortMessage("Cookie设置成功并验证登录状态")
                                                showCookieDialog = false
                                                cookieInputText = ""
                                                showCookieText = false
                                            } else if (accountStore.client === owner) {
                                                importError = "验证登录失败，未导入 Cookie，请检查凭据是否有效"
                                            }
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            currentCoroutineContext().ensureActive()
                                            if (accountStore.client === owner) importError = "验证登录时发生错误：${e.message}"
                                        } finally {
                                            importing = false
                                        }
                                    }
                                } else {
                                    userMessages.showShortMessage("未能解析有效的Cookie数据")
                                }
                            } catch (e: Exception) {
                                userMessages.showShortMessage("解析Cookie时发生错误：${e.message}")
                            }
                        } else {
                            userMessages.showShortMessage("请输入Cookie字符串")
                        }
                    },
                ) {
                    Text(if (importing) "正在验证" else "确认设置")
                }
            },
            dismissButton = {
                AppTextButton(
                    enabled = !importing,
                    onClick = {
                        showCookieDialog = false
                        cookieInputText = ""
                        showCookieText = false
                    },
                ) {
                    Text("取消")
                }
            },
        )
    }

    if (showSignedRequestDialog) {
        var urlInput by remember { mutableStateOf(ZHIHU_ME_URL) }
        var responseText by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }

        AppAlertDialog(
            onDismissRequest = {
                if (!isLoading) {
                    showSignedRequestDialog = false
                    urlInput = ZHIHU_ME_URL
                    responseText = ""
                }
            },
            title = { Text("签名GET请求") },
            text = {
                Column {
                    Text(
                        "输入需要签名的GET请求URL，将自动添加签名头并发送请求",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                    AppTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        labelText = "请求URL",
                        placeholder = ZHIHU_ME_URL,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3,
                        enabled = !isLoading,
                    )
                    if (responseText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "响应内容:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        SelectionContainer {
                            Text(
                                responseText,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp),
                                maxLines = 10,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                AppButton(
                    onClick = {
                        if (urlInput.isNotBlank() && !isLoading) {
                            val owner = accountClient
                            if (accountStore.client !== owner) return@AppButton
                            isLoading = true
                            coroutineScope.launch {
                                try {
                                    if (accountStore.client !== owner) return@launch
                                    val body = environment.signedGetText(urlInput)
                                    currentCoroutineContext().ensureActive()
                                    if (accountStore.client !== owner) return@launch
                                    copyPlainText("Signed Request Response", body)
                                    responseText = body
                                    userMessages.showShortMessage("响应已复制到剪贴板")
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    currentCoroutineContext().ensureActive()
                                    if (accountStore.client !== owner) return@launch
                                    responseText = "错误: ${e.message}"
                                    userMessages.showShortMessage("请求失败: ${e.message}")
                                } finally {
                                    isLoading = false
                                }
                            }
                        } else {
                            userMessages.showShortMessage("请输入有效的URL")
                        }
                    },
                    enabled = !isLoading,
                ) {
                    Text(if (isLoading) "请求中..." else "发送请求")
                }
            },
            dismissButton = {
                AppTextButton(
                    onClick = {
                        showSignedRequestDialog = false
                        urlInput = ZHIHU_ME_URL
                        responseText = ""
                        isLoading = false
                    },
                    enabled = !isLoading,
                ) {
                    Text("关闭")
                }
            },
        )
    }
}

private fun formatContinuousUsageDuration(durationMs: Long): String {
    val safeDurationMs = durationMs.coerceAtLeast(0L)
    val totalSeconds = safeDurationMs / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0 -> "${hours}小时${minutes}分${seconds}秒"
        minutes > 0 -> "${minutes}分${seconds}秒"
        else -> "${seconds}秒"
    }
}
