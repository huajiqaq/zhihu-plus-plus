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
    var adapter by remember { mutableStateOf<RunningSystemNotificationAdapter>(UnavailableJvmNotifications("正在连接桌面通知服务")) }
    LaunchedEffect(wakeWindow != null) {
        var prepared: RunningSystemNotificationAdapter? = null
        try {
            withContext(Dispatchers.IO) {
                val os = System.getProperty("os.name")
                prepared = when {
                    wakeWindow == null -> UnavailableJvmNotifications("当前宿主没有可唤醒的通知目标窗口")
                    os.startsWith("Linux", ignoreCase = true) -> runCatching { LinuxSystemNotifications.connect() }
                        .getOrElse { UnavailableJvmNotifications("无法连接支持点击的本机 Unix D-Bus 通知服务，请检查桌面会话后重新启动应用；退出后不能从通知启动。") }
                    os.startsWith("Windows", ignoreCase = true) -> runCatching { WindowsSystemNotifications.connect() }
                        .getOrElse {
                            UnavailableJvmNotifications("当前安装方式或窗口不支持系统通知。请使用已签名安装、已启用通知的 Windows MSIX 版本；应用仍可正常使用。")
                        }
                    else -> UnavailableJvmNotifications("此 JVM 宿主暂不支持系统通知")
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
    override val availabilityDescription: String,
) : RunningSystemNotificationAdapter {
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
