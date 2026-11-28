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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zly2006.zhihu.account.DEFAULT_ZHIHU_USER_AGENT
import com.github.zly2006.zhihu.account.createDirectHttpClient
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.installZhihuCommonClientConfig
import com.github.zly2006.zhihu.i18n.SettingsStrings
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.notification.rememberNotificationSettingsStore
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.ui.components.AppDialog
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppIcon
import com.github.zly2006.zhihu.ui.components.AppSecureTextField
import com.github.zly2006.zhihu.ui.components.AppSettingsPage
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.AppTextField
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingItemGroup
import com.github.zly2006.zhihu.viewmodel.WebDavSyncViewModel
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import io.ktor.client.HttpClient

const val WEBDAV_SYNC_BACK_BUTTON_TAG = "webdav_sync_back_button"

const val WEBDAV_URL_TAG = "webdav:url"
const val WEBDAV_USERNAME_TAG = "webdav:username"
const val WEBDAV_PASSWORD_TAG = "webdav:password"
const val WEBDAV_TEST_TAG = "webdav:test"
const val WEBDAV_UPLOAD_TAG = "webdav:upload"
const val WEBDAV_RESTORE_TAG = "webdav:restore"
const val WEBDAV_STATUS_TAG = "webdav:status"

/** WebDAV 连接凭据与 GitHub Token 不参与备份，恢复时也不得覆盖。 */
@Composable
fun WebDavSyncScreen() {
    val accountStore = rememberZhihuAccountStore()
    val settings = rememberSettingsStore()
    val notificationSettings = rememberNotificationSettingsStore()
    val database = remember { getContentFilterDatabase() }
    val viewModel: WebDavSyncViewModel = viewModel {
        WebDavSyncViewModel(
            settings = settings,
            database = database,
            notificationSettings = notificationSettings,
            persistConnectionConfig = !accountStore.usesBrowserSession,
            client = if (accountStore.usesBrowserSession) {
                createDirectHttpClient()
            } else {
                HttpClient(accountStore.engine) {
                    installZhihuCommonClientConfig(mutableMapOf(), DEFAULT_ZHIHU_USER_AGENT, enableCookies = false)
                    // WebDAV 的 Basic 凭据不能随 Location 转发到其他地址。
                    followRedirects = false
                }
            },
        )
    }
    var confirmRestore by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        snapshotFlow { viewModel.url.text.toString() }.collect { viewModel.normalizeAddress(it) }
    }
    DisposableEffect(viewModel) {
        onDispose(viewModel::saveConfig)
    }

    AppSettingsPage(
        title = SettingsStrings.webdavSync,
        onBack = LocalNavigator.current.onNavigateBack,
        backTag = WEBDAV_SYNC_BACK_BUTTON_TAG,
        pageTurnEnabled = !confirmRestore,
    ) {
        if (accountStore.usesBrowserSession) {
            SettingItem(
                title = { Text(SettingsStrings.webdavSync) },
                description = { Text(SettingsStrings.browserWebdavDescription) },
            )
        }
        val running = viewModel.runningLabel
        val status = viewModel.status
        AnimatedVisibility(visible = running != null || status != null) {
            val failed = running == null && viewModel.isStatusError
            val statusColor = when {
                running != null -> MaterialTheme.colorScheme.onSurface
                failed -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            }
            SettingItemGroup(title = SettingsStrings.status) {
                SettingItem(
                    title = {
                        Text(
                            text = running ?: status.orEmpty(),
                            color = statusColor,
                            modifier = Modifier
                                .testTag(WEBDAV_STATUS_TAG)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    },
                    icon = {
                        when {
                            running != null -> AppSpinner(size = AppDimens.IconSizeMedium)
                            failed -> AppIcon(Icons.Default.ErrorOutline, null, tint = statusColor)
                            else -> AppIcon(Icons.Default.CheckCircle, null, tint = statusColor)
                        }
                    },
                )
            }
        }

        SettingItemGroup(title = SettingsStrings.connection) {
            SettingItem(
                title = { Text(SettingsStrings.server) },
                description = { Text(SettingsStrings.webdavServerInstructions) },
                icon = { AppIcon(Icons.Default.Dns, null) },
                bottomAction = {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = AppDimens.Space12),
                        verticalArrangement = Arrangement.spacedBy(AppDimens.Space12),
                    ) {
                        AppTextField(
                            state = viewModel.url,
                            modifier = Modifier.fillMaxWidth().testTag(WEBDAV_URL_TAG),
                            labelText = SettingsStrings.webdavDirectoryUrl,
                            placeholder = "https://dav.example.com/dav/",
                        )
                        AppTextField(
                            state = viewModel.username,
                            modifier = Modifier.fillMaxWidth().testTag(WEBDAV_USERNAME_TAG),
                            labelText = SettingsStrings.username,
                        )
                        AppSecureTextField(
                            state = viewModel.password,
                            modifier = Modifier.fillMaxWidth().testTag(WEBDAV_PASSWORD_TAG),
                            labelText = SettingsStrings.passwordOrAppPassword,
                        )
                    }
                },
            )
            SettingItem(
                title = { Text(SettingsStrings.checkConnection) },
                description = { Text(SettingsStrings.checkConnectionDescription) },
                icon = { AppIcon(Icons.Default.NetworkCheck, null) },
                enabled = running == null,
                modifier = Modifier.testTag(WEBDAV_TEST_TAG),
                onClick = viewModel::testConnection,
            )
        }

        SettingItemGroup(
            title = SettingsStrings.backup,
            footer = {
                Text(
                    SettingsStrings.backupIncluded +
                        SettingsStrings.backupExcluded,
                )
            },
        ) {
            SettingItem(
                title = { Text(SettingsStrings.uploadWebdav) },
                description = { Text(SettingsStrings.uploadWebdavDescription) },
                icon = { AppIcon(Icons.Default.CloudUpload, null) },
                enabled = running == null,
                modifier = Modifier.testTag(WEBDAV_UPLOAD_TAG),
                onClick = viewModel::upload,
            )
            SettingItem(
                title = { Text(SettingsStrings.restoreWebdav) },
                description = { Text(SettingsStrings.restoreWebdavDescription) },
                icon = { AppIcon(Icons.Default.CloudDownload, null) },
                enabled = running == null,
                modifier = Modifier.testTag(WEBDAV_RESTORE_TAG),
                onClick = {
                    viewModel.saveConfig()
                    confirmRestore = true
                },
            )
        }
    }

    if (confirmRestore) {
        AppDialog(
            onDismissRequest = { confirmRestore = false },
            title = SettingsStrings.restoreWebdavConfirmation,
            summary = SettingsStrings.restoreWebdavWarning,
            confirmText = SettingsStrings.restore,
            onConfirm = {
                confirmRestore = false
                viewModel.restore()
            },
            dismissText = SettingsStrings.cancel,
            destructive = true,
        )
    }
}
