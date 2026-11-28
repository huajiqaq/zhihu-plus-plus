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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.zly2006.zhihu.notification

import com.github.zly2006.zhihu.i18n.NotificationsStrings
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** 宿主尽早调用；只加载打包目录的原生库，不运行安装注册或请求权限。 */
fun installPortableSystemNotifications(): Boolean = PortableNotificationBridge.install()

/** 与可选MSIX桥分离；Windows必须已由安装器登记COM/AUMID，macOS必须是真实.app。 */
object PortableNotificationBridge {
    private var loaded = false

    @Synchronized
    fun install(): Boolean {
        return try {
            if (!loaded) {
                val os = System.getProperty("os.name")
                val name = when {
                    os.startsWith("Windows", ignoreCase = true) -> "ZhihuPortableNotifications.dll"
                    os.startsWith("Mac", ignoreCase = true) -> "libZhihuPortableNotifications.dylib"
                    else -> return false
                }
                val resources = System.getProperty("compose.application.resources.dir") ?: return false
                val library = Path.of(resources, "native-notifications", name).toAbsolutePath().normalize()
                if (!Files.isRegularFile(library, NOFOLLOW_LINKS) || library.toRealPath() != library) return false
                System.load(library.toString())
                loaded = true
            }
            connect()
        } catch (_: Exception) {
            false
        } catch (_: LinkageError) {
            false
        }
    }

    private external fun connect(): Boolean

    external fun authorization(): Int

    external fun requestAuthorization(): Boolean

    external fun loadState(): String?

    external fun saveState(state: String)

    external fun takeReceipt(): String?

    external fun confirmReceipt(receipt: String)

    external fun publish(receipt: String, title: String, body: String): Boolean

    external fun withdraw(receipt: String)

    external fun disconnect()
}

internal class PortableSystemNotifications private constructor() : RunningSystemNotificationAdapter, AutoCloseable {
    private val claimed = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val seenClicks = mutableSetOf<String>()
    override val availabilityDescription: String
        get() = if (System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) {
            NotificationsStrings.appleAvailability
        } else {
            NotificationsStrings.windowsAvailability
        }

    override fun activate(onClick: (String) -> Unit): AutoCloseable? {
        if (closed.get() || !claimed.compareAndSet(false, true)) return null
        scope.launch {
            while (isActive) {
                try {
                    val receipt = PortableNotificationBridge.takeReceipt()
                    if (receipt != null && RECEIPT.matches(receipt) && seenClicks.add(receipt)) onClick(receipt)
                } catch (_: Exception) {
                    close()
                }
                delay(500)
            }
        }
        return AutoCloseable { close() }
    }

    override fun loadState(): DeviceNotificationState = PortableNotificationBridge.loadState()
        ?.let { Json.decodeFromString(DeviceNotificationState.serializer(), it) } ?: DeviceNotificationState()

    override fun saveState(state: DeviceNotificationState) = PortableNotificationBridge.saveState(Json.encodeToString(DeviceNotificationState.serializer(), state))

    override fun newReceipt(): String = UUID.randomUUID().toString().replace("-", "")

    override suspend fun authorization(): RunningSystemNotificationAuthorization = withContext(Dispatchers.IO) {
        val value = if (closed.get()) -1 else PortableNotificationBridge.authorization()
        RunningSystemNotificationAuthorization(value == 1, value == 1, supported = value >= 0)
    }

    override suspend fun requestAuthorization() = withContext(Dispatchers.IO) {
        check(PortableNotificationBridge.requestAuthorization()) { NotificationsStrings.permissionRequestFailed }
    }

    override fun publish(accountId: String, event: SystemNotificationEvent, receipt: String, onComplete: (Throwable?) -> Unit) {
        require(RECEIPT.matches(receipt))
        val completed = AtomicBoolean(false)
        fun complete(error: Throwable?) {
            if (completed.compareAndSet(false, true)) onComplete(error)
        }
        scope.launch {
            val result = runCatching {
                check(!closed.get()) { NotificationsStrings.hostClosed }
                check(PortableNotificationBridge.publish(receipt, NotificationsStrings.appName, NotificationsStrings.newMessage)) {
                    NotificationsStrings.deliveryRejected
                }
            }
            complete(result.exceptionOrNull())
        }.invokeOnCompletion { error ->
            if (!completed.get()) complete(error ?: CancellationException(NotificationsStrings.hostClosed))
        }
    }

    override fun withdraw(accountId: String, eventKey: String, receipt: String) {
        require(RECEIPT.matches(receipt))
        PortableNotificationBridge.withdraw(receipt)
    }

    override fun confirmClick(receipt: String) {
        require(RECEIPT.matches(receipt))
        PortableNotificationBridge.confirmReceipt(receipt)
    }

    override fun openSystemSettings(): Nothing = throw SystemNotificationUnavailableException {
        if (System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) NotificationsStrings.macosSettingsHint else NotificationsStrings.windowsSettingsHint
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        scope.cancel()
        runCatching { PortableNotificationBridge.disconnect() }
    }

    companion object {
        private val RECEIPT = Regex("[0-9a-f]{32}")

        fun connect(): PortableSystemNotifications {
            check(PortableNotificationBridge.install()) { NotificationsStrings.hostNotConnected }
            return PortableSystemNotifications()
        }
    }
}
