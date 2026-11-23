package com.github.zly2006.zhihu.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.AtomicFile
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.github.zly2006.zhihu.account.ZhihuAccountClient
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.account.ZhihuRequestProfile
import com.github.zly2006.zhihu.account.androidZhihuAccountStore
import com.github.zly2006.zhihu.navigation.NavDestination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val WORK_NAME = "zhihu-system-notifications"
private const val WORK_EPOCH = "subscription_epoch"
private const val NOTIFICATION_TAG_PREFIX = "zhihu-system:"
private const val CLICK_ACTION = "com.github.zly2006.zhihu.SYSTEM_NOTIFICATION"
private const val CLICK_SCHEME = "zhihu-system-notification"
private const val MAIN_ACTIVITY = "com.github.zly2006.zhihu.MainActivity"
private const val NOTIFICATION_ID = 0
private const val CHANNEL_GROUP_ID = "zhihu_system_messages"

private var runtime: AndroidSystemNotifications? = null

/** Application、设置和 Worker 共用一个所有者；账号 store 及客户端始终只借用。 */
@Synchronized
fun androidSystemNotifications(context: Context): AndroidSystemNotifications = runtime ?: AndroidSystemNotifications(
    context.applicationContext,
    androidZhihuAccountStore(context),
).also {
    runtime = it
    it.start()
}

private data class PollOwner(
    val client: ZhihuAccountClient,
    val state: DeviceNotificationState,
    val effectiveTypes: Set<NotificationType>,
    val revision: Long,
)

class AndroidSystemNotifications internal constructor(
    private val context: Context,
    private val accounts: ZhihuAccountStore,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val file = AtomicFile(File(context.noBackupFilesDir, "system-notifications/state.json"))
    private val json = Json { ignoreUnknownKeys = true }
    private var device = runCatching {
        file.openRead().use { json.decodeFromString<DeviceNotificationState>(it.readBytes().decodeToString()) }
    }.getOrDefault(DeviceNotificationState())
    private var owner = accounts.client
    private var scheduledEpoch: String? = null

    /** 标读只使在途轮询失效，不改变持久订阅或后台任务的 epoch。 */
    private var pollRevision = 0L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableStatus = MutableStateFlow(SystemNotificationStatus(supported = true, backgroundPollingAvailable = true))
    val status = mutableStatus.asStateFlow()

    internal fun start() {
        accounts.observeNotificationReadAll(::beginReadAll)
        refresh()
        /** 分组上线前已订阅的设备不会再走开关，启动时给既有通道补上归类。 */
        accounts.withSessionLock { createChannels() }
        scope.launch {
            accounts.accountsState.collect { refresh() }
        }
    }

    /** 同一会话锁覆盖最终投递和点击消费，账号切换不能穿过校验与副作用之间。 */
    fun refresh() = accounts.withSessionLock {
        try {
            reconcileAccount()
            updateStatus()
            schedule()
        } catch (_: Exception) {
            updateStatus("无法更新本机通知任务，请稍后重试")
        }
    }

    private fun beginReadAll(client: ZhihuAccountClient, entryName: String): (() -> Unit)? = accounts.withSessionLock {
        if (accounts.client !== client || owner !== client || !client.isCurrent || device.accountId != currentAccountId()) return@withSessionLock null
        val snapshot = device.readSnapshot(entryName) ?: return@withSessionLock null
        return@withSessionLock {
            accounts.withSessionLock {
                if (accounts.client === client && owner === client && client.isCurrent && currentAccountId() == snapshot.accountId) {
                    pollRevision++
                    try {
                        val change = device.markRead(snapshot)
                        save(change.state)
                        change.removed.forEach { cancel(it, snapshot.accountId) }
                        updateStatus()
                    } catch (_: Exception) {
                        updateStatus("标记已读成功，但无法更新本机通知，请稍后重试")
                    }
                }
            }
        }
    }

    internal fun setTypeEnabled(type: NotificationType, enabled: Boolean, expectedClient: ZhihuAccountClient): Boolean = accounts.withSessionLock {
        try {
            reconcileAccount()
            if (accounts.client !== expectedClient || !expectedClient.isCurrent) {
                updateStatus()
                return@withSessionLock false
            }
            val accountId = currentAccountId() ?: return@withSessionLock false
            if (!accounts.client.isCurrent) return@withSessionLock false
            if ((type in device.enabledTypes) == enabled) return@withSessionLock true
            val change = device.withTypeEnabled(type, enabled, accountId, UUID.randomUUID().toString())
            save(change.state)
            change.removed.forEach { cancel(it, accountId) }
            owner = accounts.client
            createChannels()
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            scheduledEpoch = null
            updateStatus()
            schedule()
            true
        } catch (_: Exception) {
            updateStatus("无法保存本机通知订阅，请稍后重试")
            false
        }
    }

    private fun currentAccountId(): String? = accounts.accountsState.value
        .takeIf { it.session.login }
        ?.activeAccountId

    private fun reconcileAccount() {
        val client = accounts.client
        if (device.accountId != null &&
            (device.accountId != currentAccountId() || owner !== client || !owner.isCurrent)
        ) {
            /** 退出或换号撤销本机授权，不在切回时自动重新启用。 */
            try {
                save(DeviceNotificationState())
            } finally {
                device = DeviceNotificationState()
                file.delete()
                cancelPosted()
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                scheduledEpoch = null
            }
        }
        owner = client
    }

    private fun createChannels() {
        if (device.enabledTypes.isEmpty()) return
        /** 分组只影响系统设置里的归类，把消息通道与朗读前台服务的通道分开列出。 */
        manager.createNotificationChannelGroup(NotificationChannelGroup(CHANNEL_GROUP_ID, "消息通知"))
        device.enabledTypes.forEach { type ->
            manager.createNotificationChannel(
                NotificationChannel(channelId(type), type.displayName, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    group = CHANNEL_GROUP_ID
                    description = "后台联网检查，间隔至少 15 分钟，并非实时推送"
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                },
            )
        }
    }

    private fun updateStatus(error: String? = null) {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        mutableStatus.value = SystemNotificationStatus(
            supported = true,
            signedIn = currentAccountId() != null,
            permissionGranted = granted,
            appNotificationsEnabled = manager.areNotificationsEnabled(),
            enabledTypes = device.enabledTypes,
            blockedChannels = NotificationType.entries.filterTo(mutableSetOf()) {
                manager.getNotificationChannel(channelId(it))?.importance == NotificationManager.IMPORTANCE_NONE
            },
            error = error,
            backgroundPollingAvailable = true,
        )
    }

    private fun effectiveTypes(): Set<NotificationType> = status.value.let {
        if (it.permissionGranted && it.appNotificationsEnabled) it.enabledTypes - it.blockedChannels else emptySet()
    }

    private fun schedule() {
        if (device.enabledTypes.isEmpty() || device.accountId == null) {
            if (scheduledEpoch != "") {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                cancelPosted()
                scheduledEpoch = ""
            }
            return
        }
        if (scheduledEpoch == device.epoch) return
        val work = PeriodicWorkRequestBuilder<SystemNotificationWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(WORK_EPOCH to device.epoch))
            .addTag(WORK_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, work)
        scheduledEpoch = device.epoch
    }

    internal suspend fun poll(epoch: String) {
        val snapshot = accounts.withSessionLock {
            reconcileAccount()
            updateStatus()
            if (epoch != device.epoch || device.accountId == null || effectiveTypes().isEmpty()) {
                if (effectiveTypes().isEmpty()) cancelPosted()
                return@withSessionLock null
            }
            PollOwner(accounts.client, device, effectiveTypes(), pollRevision)
        } ?: return
        try {
            val client = snapshot.client.httpClient(ZhihuRequestProfile.Mobile)
            val result = pollSystemNotifications(snapshot.state.checkpoint, snapshot.effectiveTypes) { url ->
                currentCoroutineContext().ensureActive()
                check(isCurrent(snapshot)) { "通知订阅已变更" }
                loadSystemNotificationPage(client, url).also {
                    currentCoroutineContext().ensureActive()
                    check(isCurrent(snapshot)) { "通知订阅已变更" }
                }
            }
            currentCoroutineContext().ensureActive()
            accounts.withSessionLock {
                if (!isCurrent(snapshot)) return@withSessionLock
                updateStatus()
                if (effectiveTypes() != snapshot.effectiveTypes) return@withSessionLock
                result.events.forEach { event ->
                    val receipt = UUID.randomUUID().toString()
                    device.reserve(event, receipt)?.let { next ->
                        /** 先落盘投递凭据，进程重试不重复响铃；系统通知与文件无法原子提交。 */
                        save(next)
                        post(event, receipt, checkNotNull(device.accountId))
                    }
                }
                save(device.copy(checkpoint = result.checkpoint, reserved = emptySet()))
                updateStatus()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            accounts.withSessionLock {
                if (isCurrent(snapshot)) {
                    updateStatus("后台检查失败，将稍后重试")
                    throw error
                }
            }
        }
    }

    private fun isCurrent(snapshot: PollOwner): Boolean = accounts.withSessionLock {
        accounts.client === snapshot.client &&
            snapshot.client.isCurrent &&
            currentAccountId() == snapshot.state.accountId &&
            device.epoch == snapshot.state.epoch &&
            pollRevision == snapshot.revision
    }

    /** 保留 action 作为已处理标记；不把来自 exported Activity 的任意 extras/URL 当作可信导航。 */
    fun handles(intent: Intent): Boolean = intent.action == CLICK_ACTION || intent.data?.scheme == CLICK_SCHEME

    fun consumeClick(intent: Intent, navigate: (NavDestination) -> Unit): Boolean = accounts.withSessionLock {
        val data = intent.data
        intent.data = null
        intent.action = CLICK_ACTION
        reconcileAccount()
        if (data?.scheme != CLICK_SCHEME || data.authority != context.packageName || data.pathSegments.size != 3) return@withSessionLock false
        val (accountId, eventKey, receipt) = data.pathSegments
        val consumed = device.consumeClick(SystemNotificationClick(accountId, eventKey, receipt), currentAccountId()) ?: return@withSessionLock false
        save(consumed.state)
        cancel(consumed.event, accountId)
        navigate(consumed.event.destination)
        true
    }

    private fun clickIntent(event: SystemNotificationEvent, receipt: String, accountId: String): Intent = Intent(CLICK_ACTION)
        .setClassName(context.packageName, MAIN_ACTIVITY)
        .setData(
            Uri
                .Builder()
                .scheme(CLICK_SCHEME)
                .authority(context.packageName)
                .appendPath(accountId)
                .appendPath(event.key)
                .appendPath(receipt)
                .build(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    @SuppressLint("MissingPermission")
    private fun post(event: SystemNotificationEvent, receipt: String, accountId: String) {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            clickIntent(event, receipt, accountId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val publicVersion = NotificationCompat
            .Builder(context, channelId(event.type))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("知乎++")
            .setContentText("你有新的通知")
            .build()
        val notification = NotificationCompat
            .Builder(context, channelId(event.type))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(event.type.displayName)
            .setContentText("点按查看详情")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(notificationTag(accountId, event.key), NOTIFICATION_ID, notification)
    }

    private fun cancel(event: SystemNotificationEvent, accountId: String) {
        manager.cancel(notificationTag(accountId, event.key), NOTIFICATION_ID)
    }

    private fun cancelPosted() {
        manager.activeNotifications.filter { it.tag?.startsWith(NOTIFICATION_TAG_PREFIX) == true }.forEach {
            manager.cancel(it.tag, it.id)
        }
    }

    private fun save(next: DeviceNotificationState) {
        file.baseFile.parentFile?.mkdirs()
        val stream = file.startWrite()
        try {
            stream.write(json.encodeToString(next).encodeToByteArray())
            file.finishWrite(stream)
            device = next
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }
}

private fun channelId(type: NotificationType): String = "zhihu_system_${type.name.lowercase()}"

private fun notificationTag(accountId: String, eventKey: String): String = "$NOTIFICATION_TAG_PREFIX$accountId:$eventKey"

class SystemNotificationWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val epoch = inputData.getString(WORK_EPOCH) ?: return Result.success()
        return try {
            androidSystemNotifications(applicationContext).poll(epoch)
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
