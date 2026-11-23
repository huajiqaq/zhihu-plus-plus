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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** 只由 MSIX 的本应用原生宿主通过 RegisterNatives 接通；不搜索或解压任何本机库。 */
object WindowsNotificationBridge {
    fun available(): Boolean = try {
        connected()
    } catch (_: LinkageError) {
        false
    }

    private external fun connected(): Boolean

    external fun storageDirectory(): String

    external fun verifyStorage(path: String, directory: Boolean)

    external fun newReceipt(): String

    external fun permissionGranted(): Boolean

    external fun takeReceipt(): String?

    external fun publish(receipt: String)

    external fun withdraw(receipt: String)

    external fun disconnect()
}

/** Windows 不接受命令行、URL 或文件中的点击；唯一来源是 SDK COM callback 的内存队列。 */
internal class WindowsSystemNotifications private constructor() :
    RunningSystemNotificationAdapter,
    AutoCloseable {
        private val store by lazy { JvmNotificationStateStore() }
        private val claimed = AtomicBoolean(false)
        private val closed = AtomicBoolean(false)
        private var clickThread: Thread? = null

        override val availabilityDescription: String =
            "仅应用运行期间检查新消息；已投递通知可在退出后启动应用。是否显示由 Windows 通知设置和勿扰模式决定。"

        override fun activate(onClick: (String) -> Unit): AutoCloseable? {
            if (closed.get() || !WindowsNotificationBridge.available() || !claimed.compareAndSet(false, true)) return null
            clickThread = Thread({
                try {
                    while (!closed.get() && WindowsNotificationBridge.available()) {
                        val receipt = WindowsNotificationBridge.takeReceipt()
                        if (receipt != null && !closed.get() && RECEIPT.matches(receipt)) onClick(receipt)
                    }
                } catch (_: Exception) {
                    close()
                }
            }, "zhihu-windows-notification-click").apply {
                isDaemon = true
                start()
            }
            return AutoCloseable { close() }
        }

        override fun loadState(): DeviceNotificationState = store.load()

        override fun saveState(state: DeviceNotificationState) = store.save(state)

        override fun newReceipt(): String = WindowsNotificationBridge.newReceipt().also { check(RECEIPT.matches(it)) }

        override suspend fun authorization(): RunningSystemNotificationAuthorization = withContext(Dispatchers.IO) {
            val permission = if (!closed.get() && WindowsNotificationBridge.available()) {
                runCatching { WindowsNotificationBridge.permissionGranted() }.getOrNull()
            } else {
                null
            }
            RunningSystemNotificationAuthorization(permission == true, permission == true, supported = permission != null)
        }

        override suspend fun requestAuthorization() {
            check(authorization().permissionGranted) { "请在 Windows 系统设置中允许知乎++发送通知" }
        }

        override fun publish(accountId: String, event: SystemNotificationEvent, receipt: String, onComplete: (Throwable?) -> Unit) {
            val result = runCatching {
                check(!closed.get() && claimed.get()) { "通知宿主已关闭" }
                require(RECEIPT.matches(receipt))
                WindowsNotificationBridge.publish(receipt)
            }
            onComplete(result.exceptionOrNull())
        }

        override fun withdraw(accountId: String, eventKey: String, receipt: String) {
            require(RECEIPT.matches(receipt))
            WindowsNotificationBridge.withdraw(receipt)
        }

        override fun openSystemSettings(): Nothing =
            throw UnsupportedOperationException("请打开 Windows 设置 → 系统 → 通知，找到知乎++并允许通知")

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            runCatching { WindowsNotificationBridge.disconnect() }
        }

        companion object {
            private val RECEIPT = Regex("[0-9a-f]{32}")

            fun connect(): WindowsSystemNotifications {
                check(WindowsNotificationBridge.available()) { "当前安装方式或窗口尚未接通 Windows 通知" }
                return WindowsSystemNotifications()
            }
        }
    }
