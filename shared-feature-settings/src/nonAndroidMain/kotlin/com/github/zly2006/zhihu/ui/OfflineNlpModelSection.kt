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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.github.zly2006.zhihu.i18n.AccountStrings
import com.github.zly2006.zhihu.i18n.SettingsStrings
import com.github.zly2006.zhihu.i18n.WidgetsStrings
import com.github.zly2006.zhihu.nlp.OfflineNlpController
import com.github.zly2006.zhihu.nlp.OfflineNlpFailure
import com.github.zly2006.zhihu.nlp.OfflineNlpModelState
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppDialog
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppLinearProgress
import com.github.zly2006.zhihu.ui.components.AppLinearProgressIndeterminate
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.SettingItem

/** 模型生命周期由页面持有，滚动使此行离开视口时不取消下载。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OfflineNlpModelSection(
    state: OfflineNlpModelState,
    busy: Boolean,
    loading: Boolean,
    operationError: OfflineNlpFailure?,
    onLoad: () -> Unit,
    onCancel: () -> Unit,
    onUnload: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val preparing = state is OfflineNlpModelState.Downloading || state is OfflineNlpModelState.Verifying || state == OfflineNlpModelState.Loading
    val canAct = !busy && !preparing && OfflineNlpController.isSupported

    SettingItem(
        modifier = Modifier.testTag("nlpSettings:model"),
        title = { Text(SettingsStrings.nlpModel) },
        description = { Text(SettingsStrings.nlpModelNotice) },
        bottomAction = {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Space8)) {
                Text(
                    when (state) {
                        is OfflineNlpModelState.Unsupported -> state.reason.displayMessage
                        OfflineNlpModelState.NotDownloaded -> SettingsStrings.nlpNotDownloaded
                        OfflineNlpModelState.Unloaded -> SettingsStrings.nlpUnloaded
                        is OfflineNlpModelState.Downloading -> SettingsStrings.nlpDownloadProgress(
                            state.fileName,
                            modelMegabytesText(state.downloadedBytes),
                            modelMegabytesText(state.totalBytes),
                        )
                        is OfflineNlpModelState.Verifying -> SettingsStrings.nlpVerifying(state.fileName)
                        OfflineNlpModelState.Loading -> WidgetsStrings.loading
                        OfflineNlpModelState.Ready -> SettingsStrings.nlpReady
                        is OfflineNlpModelState.Error -> SettingsStrings.nlpOperationFailed(state.failure.displayMessage)
                    },
                    modifier = Modifier.testTag("nlpSettings:modelState"),
                )
                if (state is OfflineNlpModelState.Downloading && state.totalBytes > 0) {
                    AppLinearProgress(
                        progress = (state.downloadedBytes.toDouble() / state.totalBytes).toFloat().coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (preparing || loading) {
                    AppLinearProgressIndeterminate(Modifier.fillMaxWidth())
                }
                operationError?.let { Text(SettingsStrings.nlpOperationFailed(it.displayMessage), color = MaterialTheme.colorScheme.error) }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.Space4),
                ) {
                    AppButton(
                        text = when (state) {
                            OfflineNlpModelState.Unloaded -> SettingsStrings.nlpLoad
                            is OfflineNlpModelState.Error -> AccountStrings.retry
                            else -> SettingsStrings.nlpDownloadLoad
                        },
                        onClick = onLoad,
                        enabled = canAct && state != OfflineNlpModelState.Ready,
                        modifier = Modifier.testTag("nlpSettings:load"),
                    )
                    if (loading) {
                        AppTextButton(
                            text = AccountStrings.cancel,
                            onClick = onCancel,
                            modifier = Modifier.testTag("nlpSettings:cancelLoad"),
                        )
                    }
                    AppTextButton(
                        text = SettingsStrings.nlpUnload,
                        onClick = onUnload,
                        enabled = canAct && state == OfflineNlpModelState.Ready,
                        modifier = Modifier.testTag("nlpSettings:unload"),
                    )
                    AppTextButton(
                        text = SettingsStrings.nlpDeleteModel,
                        onClick = { confirmDelete = true },
                        enabled = canAct,
                        modifier = Modifier.testTag("nlpSettings:deleteModel"),
                    )
                }
            }
        },
    )
    if (confirmDelete) {
        AppDialog(
            title = SettingsStrings.nlpDeleteModel,
            summary = SettingsStrings.nlpDeleteModelConfirm,
            confirmText = AccountStrings.delete,
            confirmEnabled = canAct,
            dismissText = AccountStrings.cancel,
            destructive = true,
            onDismissRequest = { confirmDelete = false },
            onConfirm = {
                if (canAct) {
                    confirmDelete = false
                    onDelete()
                }
            },
            modifier = Modifier.testTag("nlpSettings:deleteModelConfirm"),
        )
    }
}

private fun modelMegabytesText(bytes: Long): String =
    "${bytes / 1_000_000}.${(bytes % 1_000_000 / 1_000).toString().padStart(3, '0')}"
