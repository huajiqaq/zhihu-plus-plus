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

package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.crossdevice.CrossDevice
import com.github.zly2006.zhihu.crossdevice.castToDevice
import com.github.zly2006.zhihu.crossdevice.crossDeviceId
import com.github.zly2006.zhihu.crossdevice.crossDeviceName
import com.github.zly2006.zhihu.crossdevice.ensureCrossDevice
import com.github.zly2006.zhihu.crossdevice.listCrossDevices
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.ui.rememberAccountSettingsAccountState
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 跨设备打开：选择目标设备并把当前内容投放过去。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrossDeviceOpenSheet(
    show: Boolean,
    article: Article,
    onDismiss: () -> Unit,
) {
    if (!show) return

    val settings = rememberSettingsStore()
    val environment = rememberPaginationEnvironment(allowGuestAccess = false)
    val account = rememberAccountSettingsAccountState().value
    val userMessages = rememberUserMessageSink()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val localDeviceId = remember { settings.crossDeviceId() }
    var devices by remember { mutableStateOf<List<CrossDevice>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var castingTo by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(show, account.urlToken, reloadKey) {
        val token = account.urlToken ?: run {
            error = "请先登录"
            loading = false
            return@LaunchedEffect
        }
        loading = true
        error = null
        try {
            environment.ensureCrossDevice(token, localDeviceId, settings.crossDeviceName())
            devices = environment.listCrossDevices(token)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "加载设备列表失败"
        } finally {
            loading = false
        }
    }

    MyModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = "跨设备打开",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "选择设备后，内容会立即在对应设备上打开",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))

            when {
                loading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("正在获取设备列表…")
                    }
                }

                error != null -> {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = { reloadKey++ }) { Text("重试") }
                }

                devices.isNullOrEmpty() -> {
                    Text(
                        text = "暂无其他设备。请先在其他设备上打开知乎++，并登录同一账号。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { reloadKey++ }) { Text("刷新") }
                }

                else -> {
                    devices!!.forEach { device ->
                        val isLocal = device.id == localDeviceId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isLocal && castingTo == null) {
                                    scope.launch {
                                        castingTo = device.id
                                        try {
                                            if (environment.castToDevice(device, article)) {
                                                userMessages.showShortMessage("已投放到 ${device.name}")
                                                onDismiss()
                                            } else {
                                                userMessages.showShortMessage("投放失败，请稍后重试")
                                            }
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            userMessages.showShortMessage("投放失败：${e.message}")
                                        } finally {
                                            castingTo = null
                                        }
                                    }
                                }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = device.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = if (isLocal) "本机" else "点击投放",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (castingTo == device.id) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}
