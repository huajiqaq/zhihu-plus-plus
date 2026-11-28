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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import com.github.zly2006.zhihu.account.HumanVerificationPhase
import com.github.zly2006.zhihu.account.ZhihuAccountClient
import com.github.zly2006.zhihu.data.Http403Log
import com.github.zly2006.zhihu.data.HttpFailureRecord
import com.github.zly2006.zhihu.data.feedbackText
import com.github.zly2006.zhihu.i18n.SettingsStrings
import com.github.zly2006.zhihu.platform.rememberPlainTextClipboard
import com.github.zly2006.zhihu.platform.rememberWebViewUrlOpener
import com.github.zly2006.zhihu.ui.components.AppAlertDialog
import com.github.zly2006.zhihu.ui.components.AppDialog
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.util.formatFullDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun NetworkRequestDiagnostics(
    log: Http403Log,
    client: ZhihuAccountClient,
    onDismissRequest: () -> Unit,
) {
    val revision by log.revision.collectAsState()
    val writeFailed by log.writeFailed.collectAsState()
    val verification by client.verificationState.collectAsState()
    val scope = rememberCoroutineScope()
    val copyText = rememberPlainTextClipboard()
    val openUrl = rememberWebViewUrlOpener()
    var first by remember(log) { mutableStateOf<HttpFailureRecord?>(null) }
    var loading by remember(log) { mutableStateOf(true) }
    var readFailed by remember(log) { mutableStateOf(false) }
    var clearFailed by remember(log) { mutableStateOf(false) }
    var clearing by remember(log) { mutableStateOf(false) }
    var reload by remember(log) { mutableIntStateOf(0) }
    var confirmClear by remember(log) { mutableStateOf(false) }
    var copied by remember(log, first?.id) { mutableStateOf(false) }

    LaunchedEffect(log, revision, reload) {
        loading = true
        readFailed = false
        try {
            first = log.read()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            first = null
            readFailed = true
        } finally {
            loading = false
        }
    }

    AppAlertDialog(
        modifier = Modifier.testTag("network_diagnostics_dialog"),
        onDismissRequest = { if (!clearing) onDismissRequest() },
        title = { Text(SettingsStrings.firstForbiddenFeedback) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Space12)) {
                Text(SettingsStrings.firstForbiddenPrivacy)
                Text(SettingsStrings.savedPath(log.file), style = MaterialTheme.typography.bodySmall)
                if (verification.phase == HumanVerificationPhase.RESTART_REQUIRED || verification.phase == HumanVerificationPhase.DISMISSED) {
                    Text(SettingsStrings.verificationRestartNeeded)
                }
                if (writeFailed) Text(SettingsStrings.forbiddenSaveFailed, color = MaterialTheme.colorScheme.error)
                if (clearFailed) Text(SettingsStrings.clearRecordsFailed, color = MaterialTheme.colorScheme.error)
                if (readFailed) {
                    Text(SettingsStrings.readRecordsFailed, color = MaterialTheme.colorScheme.error)
                    AppTextButton(text = SettingsStrings.readAgain, onClick = { reload++ })
                }
                if (loading) {
                    AppSpinner(size = AppDimens.InlineSpinnerSize)
                } else if (first == null && !readFailed) {
                    Text(SettingsStrings.noForbiddenRecords)
                }
                first?.let { record ->
                    Text(formatFullDateTime(record.timestampMillis / 1_000), style = MaterialTheme.typography.bodySmall)
                    Text(record.feedbackText(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    if (record.source == "WebView") Text(SettingsStrings.webviewHeaderLimit)
                    AppTextButton(
                        text = if (copied) SettingsStrings.redactedInfoCopied else SettingsStrings.copyRedactedInfo,
                        modifier = Modifier.testTag("network_diagnostics_copy"),
                        onClick = {
                            copyText(SettingsStrings.forbiddenClipboardLabel, record.feedbackText())
                            copied = true
                        },
                    )
                    AppTextButton(
                        text = SettingsStrings.reportToDeveloper,
                        modifier = Modifier.testTag("network_diagnostics_feedback"),
                        onClick = { openUrl("https://github.com/zly2006/zhihu-plus-plus/issues") },
                    )
                    Text(SettingsStrings.feedbackInstructions, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { AppTextButton(text = SettingsStrings.close, enabled = !clearing, onClick = onDismissRequest) },
        dismissButton = {
            AppTextButton(
                text = if (clearing) SettingsStrings.clearingRecords else SettingsStrings.clearRecords,
                enabled = !loading && !clearing && (first != null || readFailed),
                modifier = Modifier.testTag("network_diagnostics_clear"),
                onClick = { confirmClear = true },
            )
        },
    )
    if (confirmClear) {
        AppDialog(
            title = SettingsStrings.clearForbiddenConfirmation,
            summary = SettingsStrings.clearForbiddenWarning,
            confirmText = SettingsStrings.clearRecords,
            dismissText = SettingsStrings.cancel,
            destructive = true,
            onDismissRequest = { confirmClear = false },
            onConfirm = {
                confirmClear = false
                clearing = true
                clearFailed = false
                scope.launch {
                    try {
                        log.clear()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        clearFailed = true
                    } finally {
                        clearing = false
                    }
                }
            },
        )
    }
}
