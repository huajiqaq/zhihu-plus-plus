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

package com.github.zly2006.zhihu.notification

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.i18n.NotificationsStrings
import com.github.zly2006.zhihu.navigation.NavDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 由唯一桌面窗口提供；缺省不宣称具备点击导航能力。 */
val LocalJvmNotificationWindow = staticCompositionLocalOf<(() -> Boolean)?> { null }

@Composable
actual fun rememberSystemNotificationControl(): SystemNotificationControl = rememberRunningSystemNotificationControl()

/** 通知只借用现有账号与导航；只有实际窗口提供唤醒入口时才申请运行租约。 */
@Composable
fun JvmSystemNotificationHost(
    accounts: ZhihuAccountStore,
    onOpen: (NavDestination) -> Unit,
    wakeWindow: (() -> Boolean)?,
    content: @Composable () -> Unit,
) {
    val currentWake = rememberUpdatedState(wakeWindow)
    val currentOpen = rememberUpdatedState(onOpen)
    var adapter by remember { mutableStateOf<RunningSystemNotificationAdapter>(UnavailableJvmNotifications { NotificationsStrings.jvmConnecting }) }
    LaunchedEffect(wakeWindow != null) {
        var prepared: RunningSystemNotificationAdapter? = null
        try {
            withContext(Dispatchers.IO) {
                val os = System.getProperty("os.name")
                prepared = when {
                    wakeWindow == null -> UnavailableJvmNotifications { NotificationsStrings.jvmWindowMissing }
                    os.startsWith("Linux", ignoreCase = true) -> runCatching { LinuxSystemNotifications.connect() }
                        .getOrElse { UnavailableJvmNotifications { NotificationsStrings.jvmLinuxUnavailable } }
                    os.startsWith("Windows", ignoreCase = true) -> runCatching {
                        if (WindowsNotificationBridge.available()) WindowsSystemNotifications.connect() else PortableSystemNotifications.connect()
                    }.getOrElse { UnavailableJvmNotifications { NotificationsStrings.jvmWindowsUnavailable } }
                    os.startsWith("Mac", ignoreCase = true) -> runCatching { PortableSystemNotifications.connect() }
                        .getOrElse { UnavailableJvmNotifications { NotificationsStrings.appleUnavailable } }
                    else -> UnavailableJvmNotifications { NotificationsStrings.jvmUnsupported }
                }
            }
            adapter = checkNotNull(prepared)
            prepared = null
        } finally {
            (prepared as? AutoCloseable)?.close()
        }
    }
    DisposableEffect(adapter) {
        val owned = adapter
        onDispose { (owned as? AutoCloseable)?.close() }
    }
    RunningSystemNotificationHost(
        accounts = accounts,
        adapter = adapter,
        onOpen = { destination ->
            if (currentWake.value?.invoke() == true) currentOpen.value(destination)
        },
        content = content,
    )
}

private class UnavailableJvmNotifications(
    private val description: () -> String,
) : RunningSystemNotificationAdapter {
    override val availabilityDescription: String get() = description()

    override fun activate(onClick: (String) -> Unit): AutoCloseable? = null

    override fun loadState(): DeviceNotificationState = error(availabilityDescription)

    override fun saveState(state: DeviceNotificationState): Nothing = error(availabilityDescription)

    override fun newReceipt(): String = error(availabilityDescription)

    override suspend fun authorization(): RunningSystemNotificationAuthorization = RunningSystemNotificationAuthorization(false, false, supported = false)

    override suspend fun requestAuthorization(): Nothing = error(availabilityDescription)

    override fun publish(accountId: String, event: SystemNotificationEvent, receipt: String, onComplete: (Throwable?) -> Unit) =
        onComplete(IllegalStateException(availabilityDescription))

    override fun withdraw(accountId: String, eventKey: String, receipt: String): Nothing = error(availabilityDescription)

    override fun openSystemSettings(): Nothing = error(availabilityDescription)
}
