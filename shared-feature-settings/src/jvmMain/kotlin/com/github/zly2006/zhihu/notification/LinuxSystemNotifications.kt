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

import com.github.zly2006.zhihu.i18n.NotificationsStrings
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.freedesktop.dbus.Marshalling
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.DBusSigHandler
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant

/** freedesktop 的公开协议模型；方法与嵌套信号名称由 D-Bus 反射使用，不可混淆。 */
@Suppress("FunctionName")
@DBusInterfaceName("org.freedesktop.Notifications")
interface FreedesktopNotifications : DBusInterface {
    fun GetCapabilities(): List<String>

    fun Notify(
        appName: String,
        replacesId: UInt32,
        appIcon: String,
        summary: String,
        body: String,
        actions: List<String>,
        hints: Map<String, Variant<*>>,
        expireTimeout: Int,
    ): UInt32

    fun CloseNotification(id: UInt32)

    class ActionInvoked(
        path: String,
        val id: UInt32,
        val action: String,
    ) : DBusSignal(path, id, action)

    class NotificationClosed(
        path: String,
        val id: UInt32,
        val reason: UInt32,
    ) : DBusSignal(path, id, reason)
}

/** 仅连接当前桌面会话，不启动服务、不打开 TCP、不执行外部命令。 */
internal class LinuxSystemNotifications private constructor(
    private val connection: DBusConnection,
    private val service: FreedesktopNotifications,
    private val serviceOwner: String,
) : RunningSystemNotificationAdapter,
    AutoCloseable {
    private data class Delivery(
        val accountId: String,
        val eventKey: String,
        val receipt: String,
        var id: UInt32? = null,
    )

    private val store by lazy { JvmNotificationStateStore() }
    private val monitor = Any()
    private val deliveries = mutableMapOf<String, Delivery>()
    private val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "zhihu-notifications-dbus").apply { isDaemon = true } }
    private val claimed = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    @Volatile
    private var onClick: ((String) -> Unit)? = null

    @Volatile
    private var withdrawalFailed = false

    @Volatile
    private var serviceAvailable = true
    private var actionListener: AutoCloseable? = null
    private var closeListener: AutoCloseable? = null

    override val availabilityDescription: String
        get() = if (!serviceAvailable) {
            NotificationsStrings.linuxServiceLost
        } else if (withdrawalFailed) {
            NotificationsStrings.linuxWithdrawFailed
        } else {
            NotificationsStrings.linuxAvailability
        }

    private fun listen() {
        actionListener = connection.addSigHandler(
            FreedesktopNotifications.ActionInvoked::class.java,
            serviceOwner,
            service,
            DBusSigHandler { signal ->
                if (signal.source != serviceOwner || signal.path != OBJECT_PATH || signal.action != DEFAULT_ACTION) return@DBusSigHandler
                if (!closed.get()) {
                    runCatching {
                        executor.execute {
                            val receipt = synchronized(monitor) { deliveries.values.firstOrNull { it.id == signal.id }?.receipt }
                            val trusted = runCatching { connection.getDBusOwnerName(SERVICE_NAME) == serviceOwner }.getOrDefault(false)
                            if (!trusted) serviceAvailable = false
                            if (receipt != null && !closed.get() && trusted) onClick?.invoke(receipt)
                        }
                    }
                }
            },
        )
        closeListener = connection.addSigHandler(
            FreedesktopNotifications.NotificationClosed::class.java,
            serviceOwner,
            service,
            DBusSigHandler { signal ->
                if (signal.source == serviceOwner && signal.path == OBJECT_PATH && !closed.get()) {
                    runCatching {
                        executor.execute { synchronized(monitor) { deliveries.entries.removeAll { it.value.id == signal.id } } }
                    }
                }
            },
        )
    }

    override fun activate(onClick: (String) -> Unit): AutoCloseable? {
        if (closed.get() || !connection.isConnected || !claimed.compareAndSet(false, true)) return null
        this.onClick = onClick
        return AutoCloseable { close() }
    }

    override fun loadState(): DeviceNotificationState = store.load()

    override fun saveState(state: DeviceNotificationState) = store.save(state)

    override fun newReceipt(): String = UUID.randomUUID().toString()

    override suspend fun authorization(): RunningSystemNotificationAuthorization = withContext(Dispatchers.IO) {
        val available = runCatching {
            !closed.get() &&
                connection.isConnected &&
                connection.getDBusOwnerName(SERVICE_NAME) == serviceOwner &&
                "actions" in service.GetCapabilities()
        }.getOrDefault(false)
        serviceAvailable = available
        RunningSystemNotificationAuthorization(available, available, supported = available)
    }

    override suspend fun requestAuthorization() {
        check(authorization().appNotificationsEnabled) { NotificationsStrings.linuxPermissionHint }
    }

    override fun publish(accountId: String, event: SystemNotificationEvent, receipt: String, onComplete: (Throwable?) -> Unit) {
        val completed = AtomicBoolean(false)

        fun complete(error: Throwable?) {
            if (completed.compareAndSet(false, true)) onComplete(error)
        }
        val delivery = Delivery(accountId, event.key, receipt)
        try {
            check(claimed.get() && !closed.get()) { NotificationsStrings.hostClosed }
            synchronized(monitor) { check(deliveries.putIfAbsent(receipt, delivery) == null) { NotificationsStrings.receiptExists } }
            executor.execute {
                try {
                    check(synchronized(monitor) { !closed.get() && deliveries[receipt] === delivery }) { NotificationsStrings.withdrawn }
                    check(connection.getDBusOwnerName(SERVICE_NAME) == serviceOwner) { NotificationsStrings.linuxServiceChanged }
                    val id = service.Notify(
                        appName = "Zhihu++",
                        replacesId = UInt32(0),
                        appIcon = "",
                        summary = NotificationsStrings.linuxNotificationTitle(event.type.displayName),
                        body = NotificationsStrings.newMessage,
                        actions = listOf(DEFAULT_ACTION, NotificationsStrings.view),
                        hints = mapOf("transient" to Variant(true)),
                        expireTimeout = -1,
                    )
                    check(id.toLong() != 0L) { NotificationsStrings.linuxDeliveryRejected }
                    synchronized(monitor) { delivery.id = id }
                    complete(null)
                } catch (error: Exception) {
                    synchronized(monitor) { deliveries.remove(receipt, delivery) }
                    complete(error)
                }
            }
        } catch (error: Exception) {
            synchronized(monitor) { deliveries.remove(receipt, delivery) }
            complete(error)
        }
    }

    override fun withdraw(accountId: String, eventKey: String, receipt: String) {
        val delivery = synchronized(monitor) {
            deliveries[receipt]?.takeIf { it.accountId == accountId && it.eventKey == eventKey }?.also { deliveries.remove(receipt) }
        } ?: return
        executor.execute {
            try {
                delivery.id?.let { service.CloseNotification(it) }
            } catch (_: Exception) {
                withdrawalFailed = true
            }
        }
    }

    override fun openSystemSettings(): Nothing = throw SystemNotificationUnavailableException { NotificationsStrings.linuxSettingsHint }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        onClick = null
        val outstanding = synchronized(monitor) { deliveries.values.toList().also { deliveries.clear() } }
        executor.execute {
            try {
                outstanding.mapNotNull { it.id }.forEach { id -> runCatching { service.CloseNotification(id) } }
            } finally {
                runCatching { actionListener?.close() }
                runCatching { closeListener?.close() }
                connection.close()
            }
        }
        executor.shutdown()
    }

    companion object {
        private const val SERVICE_NAME = "org.freedesktop.Notifications"
        private const val OBJECT_PATH = "/org/freedesktop/Notifications"
        private const val DEFAULT_ACTION = "default"

        fun connect(): LinuxSystemNotifications {
            /** 正式包自检反射签名；R8/ProGuard 丢失泛型时在接通能力前失败，不能发送畸形协议。 */
            val notify = FreedesktopNotifications::class.java.methods.single { it.name == "Notify" }
            check(Marshalling.getDBusType(notify.genericParameterTypes) == "susssasa{sv}i") { NotificationsStrings.dbusSignatureInvalid }
            val address = System.getenv("DBUS_SESSION_BUS_ADDRESS") ?: error(NotificationsStrings.dbusSessionMissing)
            require(address.length <= 4096 && !address.contains(';')) { NotificationsStrings.dbusSingleUnixOnly }
            require(address.startsWith("unix:path=")) { NotificationsStrings.dbusSocketUnsupported }
            val options = address.removePrefix("unix:").split(',')
            require(options.size in 1..2 && options.drop(1).all { it.matches(Regex("guid=[0-9a-fA-F]{32}")) }) { NotificationsStrings.dbusParametersUnsupported }
            val path = options.first().removePrefix("path=")
            require(path.isNotBlank() && Path.of(path).isAbsolute && path.none { it == '%' || it == '\u0000' }) { NotificationsStrings.dbusSocketInvalid }
            val connection = DBusConnectionBuilder.forAddress(address).withShared(false).build()
            try {
                val owner = connection.getDBusOwnerName(SERVICE_NAME)
                check(!owner.isNullOrBlank() && owner.startsWith(':')) { NotificationsStrings.dbusServiceMissing }
                val service = connection.getRemoteObject(owner, OBJECT_PATH, FreedesktopNotifications::class.java, false)
                check("actions" in service.GetCapabilities()) { NotificationsStrings.dbusActionsUnsupported }
                return LinuxSystemNotifications(connection, service, owner).also { it.listen() }
            } catch (error: Exception) {
                connection.close()
                throw error
            }
        }
    }
}
