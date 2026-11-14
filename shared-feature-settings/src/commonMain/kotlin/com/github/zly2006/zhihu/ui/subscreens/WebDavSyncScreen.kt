/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 only.
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zly2006.zhihu.account.DEFAULT_ZHIHU_USER_AGENT
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.installZhihuCommonClientConfig
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.notification.HOME_NOTIFICATION_READ_UUIDS_PREFERENCE_KEY
import com.github.zly2006.zhihu.notification.rememberNotificationSettingsStore
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.ui.components.AppDialog
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppSettingsPage
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.AppTextField
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingItemGroup
import com.github.zly2006.zhihu.viewmodel.WebDavConfig
import com.github.zly2006.zhihu.viewmodel.WebDavSyncViewModel
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import com.github.zly2006.zhihu.viewmodel.parseWebDavAddress
import io.ktor.client.HttpClient
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

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
            stringSetKeys = setOf(BOTTOM_BAR_ITEMS_PREFERENCE_KEY, HOME_NOTIFICATION_READ_UUIDS_PREFERENCE_KEY),
            client = HttpClient(accountStore.engine) {
                installZhihuCommonClientConfig(mutableMapOf(), DEFAULT_ZHIHU_USER_AGENT, enableCookies = false)
            },
        )
    }
    var url by remember { mutableStateOf(viewModel.config.url) }
    var username by remember { mutableStateOf(viewModel.config.username) }
    var password by remember { mutableStateOf(viewModel.config.password) }
    var confirmRestore by remember { mutableStateOf(false) }

    fun syncConfig() {
        viewModel.config = WebDavConfig(url = url, username = username, password = password)
        viewModel.saveConfig()
    }

    DisposableEffect(viewModel) {
        onDispose { syncConfig() }
    }

    AppSettingsPage(
        title = "WebDAV 同步",
        onBack = LocalNavigator.current.onNavigateBack,
        backTag = WEBDAV_SYNC_BACK_BUTTON_TAG,
        pageTurnEnabled = !confirmRestore,
    ) {
        val running = viewModel.runningLabel
        val status = viewModel.status
        AnimatedVisibility(visible = running != null || status != null) {
            val failed = running == null && viewModel.isStatusError
            val statusColor = when {
                running != null -> MaterialTheme.colorScheme.onSurface
                failed -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            }
            SettingItemGroup(title = "状态") {
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
                            failed -> Icon(Icons.Default.ErrorOutline, null, tint = statusColor)
                            else -> Icon(Icons.Default.CheckCircle, null, tint = statusColor)
                        }
                    },
                )
            }
        }

        SettingItemGroup(title = "连接") {
            SettingItem(
                title = { Text("服务器") },
                description = { Text("可以直接粘贴带账号密码的完整链接（如 dav://用户名:密码@example.com/dav/），会自动拆分填入。") },
                icon = { Icon(Icons.Default.Dns, null) },
                bottomAction = {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = AppDimens.Space12),
                        verticalArrangement = Arrangement.spacedBy(AppDimens.Space12),
                    ) {
                        AppTextField(
                            value = url,
                            onValueChange = { raw ->
                                val parsed = parseWebDavAddress(raw)
                                url = parsed.url
                                // 只在地址里真的带了凭据时才覆盖，避免干扰手动逐字段填写。
                                if (parsed.username.isNotEmpty()) username = parsed.username
                                if (parsed.password.isNotEmpty()) password = parsed.password
                            },
                            modifier = Modifier.fillMaxWidth().testTag(WEBDAV_URL_TAG),
                            labelText = "WebDAV 目录地址",
                            placeholder = "https://dav.example.com/dav/",
                        )
                        AppTextField(
                            value = username,
                            onValueChange = { username = it },
                            modifier = Modifier.fillMaxWidth().testTag(WEBDAV_USERNAME_TAG),
                            labelText = "用户名",
                        )
                        AppTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth().testTag(WEBDAV_PASSWORD_TAG),
                            labelText = "密码或应用密码",
                            visualTransformation = PasswordVisualTransformation(),
                        )
                    }
                },
            )
            SettingItem(
                title = { Text("检查连接") },
                description = { Text("确认目录地址和登录凭据可用") },
                icon = { Icon(Icons.Default.NetworkCheck, null) },
                enabled = running == null,
                modifier = Modifier.testTag(WEBDAV_TEST_TAG),
                onClick = {
                    syncConfig()
                    viewModel.testConnection()
                },
            )
        }

        SettingItemGroup(
            title = "备份",
            footer = {
                Text(
                    "会备份：屏蔽列表、应用设置（包括底栏项目）和通知设置。\n" +
                        "不会备份：WebDAV 连接信息和 GitHub Token。它们只保存在本机，恢复时也不会被覆盖。",
                )
            },
        ) {
            SettingItem(
                title = { Text("上传到 WebDAV") },
                description = { Text("用本机的数据覆盖 WebDAV 上的备份") },
                icon = { Icon(Icons.Default.CloudUpload, null) },
                enabled = running == null,
                modifier = Modifier.testTag(WEBDAV_UPLOAD_TAG),
                onClick = {
                    syncConfig()
                    viewModel.upload()
                },
            )
            SettingItem(
                title = { Text("从 WebDAV 恢复") },
                description = { Text("合并屏蔽列表，覆盖本机设置") },
                icon = { Icon(Icons.Default.CloudDownload, null) },
                enabled = running == null,
                modifier = Modifier.testTag(WEBDAV_RESTORE_TAG),
                onClick = {
                    syncConfig()
                    confirmRestore = true
                },
            )
        }
    }

    if (confirmRestore) {
        AppDialog(
            onDismissRequest = { confirmRestore = false },
            title = "从 WebDAV 恢复备份？",
            summary = "屏蔽列表会与备份合并；应用设置和通知设置会被备份覆盖，无法撤销。WebDAV 连接信息和 GitHub Token 不受影响。部分设置需要重启后生效。",
            confirmText = "恢复",
            onConfirm = {
                confirmRestore = false
                viewModel.restore()
            },
            dismissText = "取消",
            destructive = true,
        )
    }
}
