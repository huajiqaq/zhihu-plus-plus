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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.github.zly2006.zhihu.crossdevice.CrossDevice
import com.github.zly2006.zhihu.crossdevice.crossDeviceId
import com.github.zly2006.zhihu.crossdevice.crossDeviceInboxInitialized
import com.github.zly2006.zhihu.crossdevice.crossDeviceLastSeen
import com.github.zly2006.zhihu.crossdevice.crossDeviceName
import com.github.zly2006.zhihu.crossdevice.ensureCrossDevice
import com.github.zly2006.zhihu.crossdevice.fetchCrossDeviceInbox
import com.github.zly2006.zhihu.crossdevice.markCrossDeviceInboxInitialized
import com.github.zly2006.zhihu.crossdevice.setCrossDeviceLastSeen
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.ui.rememberAccountSettingsAccountState
import com.github.zly2006.zhihu.util.Log
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val CROSS_DEVICE_POLL_INTERVAL_MS = 15_000L

/**
 * 前台轮询本机的跨设备收件箱：其他设备投放内容后，自动打开最新一条。
 * 首次轮询只记录基线，避免把历史收藏当成新投放。
 */
@Composable
fun CrossDeviceInboxEffect() {
    val settings = rememberSettingsStore()
    val environment = rememberPaginationEnvironment(allowGuestAccess = false)
    val account = rememberAccountSettingsAccountState().value
    val navigator = LocalNavigator.current
    val userMessages = rememberUserMessageSink()
    val lifecycleOwner = LocalLifecycleOwner.current
    val deviceId = remember { settings.crossDeviceId() }
    val deviceName = remember { settings.crossDeviceName() }

    LaunchedEffect(account.login, account.urlToken) {
        val token = account.urlToken
        if (!account.login || token.isNullOrBlank()) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var device: CrossDevice? = null
            while (isActive) {
                try {
                    val resolved = device
                        ?: environment.ensureCrossDevice(token, deviceId, deviceName)?.also { device = it }
                    if (resolved != null) {
                        val inbox = environment.fetchCrossDeviceInbox(resolved)
                        val keys = inbox.map { "${it.type}:${it.id}" }
                        if (!settings.crossDeviceInboxInitialized(deviceId)) {
                            settings.markCrossDeviceInboxInitialized(deviceId)
                            settings.setCrossDeviceLastSeen(deviceId, keys)
                        } else {
                            val lastSeen = settings.crossDeviceLastSeen(deviceId)
                            val newItems = inbox.filter { "${it.type}:${it.id}" !in lastSeen }
                            if (newItems.isNotEmpty()) {
                                settings.setCrossDeviceLastSeen(deviceId, keys)
                                val newest = newItems.first()
                                userMessages.showShortMessage(
                                    "已从其他设备打开：${newest.title.ifBlank { "内容" }}",
                                )
                                navigator.onNavigate(newest)
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("CrossDeviceInbox", "Failed to poll cross-device inbox", e)
                }
                delay(CROSS_DEVICE_POLL_INTERVAL_MS)
            }
        }
    }
}
