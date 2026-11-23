package com.github.zly2006.zhihu.notification

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.github.zly2006.zhihu.account.LocalZhihuAccountStore
import com.github.zly2006.zhihu.account.ZhihuAccountClient
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.account.ZhihuRequestProfile
import com.github.zly2006.zhihu.navigation.NavDestination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

internal data class RunningSystemNotificationAuthorization(
    val permissionGranted: Boolean,
    val appNotificationsEnabled: Boolean,
    val blockedTypes: Set<NotificationType> = emptySet(),
    val supported: Boolean = true,
)

/** 平台只拥有系统副作用；账号、检查点、订阅和一次性点击均由共同运行时决定。 */
internal interface RunningSystemNotificationAdapter {
    /** 固定、无敏感数据的能力说明；未接通或被系统阻止时展示，不能声称通知一定会显示。 */
    val availabilityDescription: String? get() = null

    /** 真正接通点击唤醒后授予唯一租约；未接宿主或已有所有者时返回 null，不能仅凭 OS 名称宣布支持。 */
    fun activate(onClick: (receipt: String) -> Unit): AutoCloseable?

    /** 文件须受本机账号保护且排除备份/同步；仅文件不存在时返回默认状态，损坏或保护失败须抛错。 */
    fun loadState(): DeviceNotificationState

    /** 原子替换且持久化成功后才返回；失败保留旧文件并抛错，不由平台镜像另一份订阅。 */
    fun saveState(state: DeviceNotificationState)

    /** 使用平台的密码学安全随机源，同时用于订阅 epoch；不得复用旧值。 */
    fun newReceipt(): String

    suspend fun authorization(): RunningSystemNotificationAuthorization

    /** 仅由明确启用操作触发，拒绝或失败不能被当作已授权。 */
    suspend fun requestAuthorization()

    /**
     * 同步提交但不等待原生回调；系统明确接受或拒绝后恰好调用一次 onComplete。
     * null 仅表示接受，不保证已显示。系统标识须含唯一 receipt，不能覆盖同 key 的另一回执。
     */
    fun publish(
        accountId: String,
        event: SystemNotificationEvent,
        receipt: String,
        onComplete: (Throwable?) -> Unit,
    )

    /** 精确撤回这一回执，包含待投递请求；幂等，不能误撤同账号同 key 的新回执。 */
    fun withdraw(accountId: String, eventKey: String, receipt: String)

    fun openSystemSettings()
}

private const val RUNNING_POLL_INTERVAL_MS = 15L * 60 * 1_000
private const val DELIVERY_CONFIRMATION_TIMEOUT_MS = 30_000L

private data class RunningNotificationOwner(
    val client: ZhihuAccountClient,
    val accountId: String?,
    val state: DeviceNotificationState,
    val revision: Long,
)

/**
 * 只借用账号 store 和宿主主线程作用域；关闭后释放事件租约、观察者和全部子协程。
 * 这是进程内周期检查，不是 Android WorkManager：退出或系统挂起进程后不会继续联网。
 */
internal class RunningSystemNotifications(
    private val accounts: ZhihuAccountStore,
    private val adapter: RunningSystemNotificationAdapter,
    parentScope: CoroutineScope,
    private val onNavigate: (NavDestination) -> Unit,
) : AutoCloseable {
    private val lifetime = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + lifetime)
    private val ready = CompletableDeferred<Boolean>()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val mutableStatus = MutableStateFlow(SystemNotificationStatus())
    val status = mutableStatus.asStateFlow()
    private var device = DeviceNotificationState()
    private var owner = accounts.client
    private var authorization = RunningSystemNotificationAuthorization(false, false, supported = false)
    private var activation: AutoCloseable? = null
    private var readObserver: AutoCloseable? = null
    private var pollJob: Job? = null
    private var permissionJob: Job? = null
    private var revision = 0L
    private var started = false
    private var active = false
    private var closed = false
    private var storageFailed = false
    private var errorMessage: String? = null
    private val pendingWithdrawals = mutableSetOf<SystemNotificationClick>()

    init {
        lifetime.invokeOnCompletion { close() }
    }

    fun owns(store: ZhihuAccountStore): Boolean = accounts === store

    fun start(): Boolean {
        val shouldStart = accounts.withSessionLock {
            if (started || closed || !lifetime.isActive) return@withSessionLock false
            started = true
            try {
                activation = adapter.activate { receipt ->
                    if (receipt.isNotBlank()) {
                        scope.launch {
                            if (ready.await()) consumeClick(receipt)
                        }
                    }
                }
                if (activation == null) {
                    errorMessage = if (adapter.availabilityDescription.isNullOrBlank()) "此宿主尚未接通系统通知" else null
                    updateStatus()
                    ready.complete(false)
                    return@withSessionLock false
                }
                device = adapter.loadState().also(::validateState)
                reconcileAccount()
                active = true
                readObserver = accounts.observeNotificationReadAll(::beginRead)
                updateStatus()
                true
            } catch (_: Exception) {
                active = false
                errorMessage = "无法初始化本机通知，请检查本机存储和系统支持"
                runCatching { activation?.close() }
                activation = null
                updateStatus()
                ready.complete(false)
                false
            }
        }
        if (!shouldStart) return false
        /** 启动协程在锁外，异步授权查询不能借主线程的可重入锁跨越挂起点。 */
        scope.launch {
            accounts.accountsState.collect {
                accounts.withSessionLock {
                    /** Cookie 刷新也会发出 accountsState；不能让每次网络响应重新启动一次轮询。 */
                    if (owner !== accounts.client || !owner.isCurrent || (device.accountId != null && device.accountId != currentAccountId())) {
                        refresh()
                    } else {
                        updateStatus()
                    }
                }
            }
        }
        scope.launch {
            while (isActive) {
                val cycle = launch(start = CoroutineStart.LAZY) { runCycle() }
                accounts.withSessionLock {
                    pollJob = cycle
                    if (closed || storageFailed) cycle.cancel()
                }
                cycle.start()
                cycle.join()
                accounts.withSessionLock { if (pollJob === cycle) pollJob = null }
                withTimeoutOrNull(RUNNING_POLL_INTERVAL_MS) { wake.receive() }
            }
        }
        ready.complete(true)
        return true
    }

    fun refresh(): Unit = accounts.withSessionLock {
        if (!active || closed) return@withSessionLock
        try {
            reconcileAccount()
            updateStatus()
            wake.trySend(Unit)
        } catch (_: Exception) {
            reportError("无法更新本机通知，请稍后重试")
        }
    }

    fun setTypeEnabled(type: NotificationType, enabled: Boolean, expectedClient: ZhihuAccountClient) {
        var request: Job? = null
        accounts.withSessionLock {
            if (!operational()) return@withSessionLock
            try {
                reconcileAccount()
                if (accounts.client !== expectedClient || !expectedClient.isCurrent) return@withSessionLock
                val accountId = currentAccountId() ?: return@withSessionLock
                if ((type in device.enabledTypes) == enabled) return@withSessionLock
                val epoch = if (!enabled && device.enabledTypes == setOf(type)) "" else newReceipt()
                val change = device.withTypeEnabled(type, enabled, accountId, epoch)
                saveAndWithdraw(change.state)
                invalidatePoll()
                permissionJob?.cancel()
                errorMessage = null
                updateStatus()
                if (enabled) {
                    val expected = snapshot()
                    request = scope.launch(start = CoroutineStart.LAZY) { requestPermission(expected) }
                    permissionJob = request
                }
            } catch (_: Exception) {
                reportError("无法保存本机通知订阅，请稍后重试")
            }
        }
        request?.start()
    }

    fun openSystemSettings() = accounts.withSessionLock {
        if (!operational()) return@withSessionLock
        try {
            adapter.openSystemSettings()
        } catch (error: UnsupportedOperationException) {
            reportError(error.message?.takeIf { it.isNotBlank() } ?: "请在系统设置中找到知乎++的通知选项")
        } catch (_: Exception) {
            reportError("无法打开系统通知设置，请在系统设置中找到知乎++的通知选项")
        }
    }

    private suspend fun requestPermission(expected: RunningNotificationOwner) {
        try {
            val current = adapter.authorization()
            currentCoroutineContext().ensureActive()
            if (!accounts.withSessionLock { matchesSession(expected) }) return
            if (current.supported && !current.permissionGranted) adapter.requestAuthorization()
            currentCoroutineContext().ensureActive()
            refresh()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            accounts.withSessionLock {
                if (matchesSession(expected)) reportError("无法请求系统通知权限，请在系统设置中检查")
            }
        }
    }

    private suspend fun runCycle() {
        var expected: RunningNotificationOwner? = null
        try {
            val beforeAuthorization = accounts.withSessionLock {
                if (!operational()) return@withSessionLock null
                reconcileAccount()
                pendingWithdrawals.toList().forEach(::withdraw)
                snapshot()
            } ?: return
            expected = beforeAuthorization
            val permission = adapter.authorization()
            currentCoroutineContext().ensureActive()
            val poll = accounts.withSessionLock {
                if (!isCurrent(beforeAuthorization)) return@withSessionLock null
                applyAuthorization(permission)
                updateStatus()
                val types = effectiveTypes()
                if (device.accountId == null || types.isEmpty() || pendingWithdrawals.isNotEmpty()) return@withSessionLock null
                snapshot() to types
            } ?: return
            val (pollOwner, types) = poll
            expected = pollOwner
            val client = accounts.withSessionLock {
                if (!isCurrent(pollOwner)) return@withSessionLock null
                pollOwner.client.httpClient(ZhihuRequestProfile.Mobile)
            } ?: return
            val result = pollSystemNotifications(pollOwner.state.checkpoint, types) { url ->
                currentCoroutineContext().ensureActive()
                checkCurrent(pollOwner)
                loadSystemNotificationPage(client, url).also {
                    currentCoroutineContext().ensureActive()
                    checkCurrent(pollOwner)
                }
            }
            val latestPermission = adapter.authorization()
            currentCoroutineContext().ensureActive()
            val mayPublish = accounts.withSessionLock {
                if (!isCurrent(pollOwner)) return@withSessionLock false
                applyAuthorization(latestPermission)
                updateStatus()
                isCurrent(pollOwner) && effectiveTypes() == types
            }
            if (!mayPublish) return
            for (event in result.events) publish(pollOwner, event)
            currentCoroutineContext().ensureActive()
            accounts.withSessionLock {
                if (!isCurrent(pollOwner) || effectiveTypes() != types) return@withSessionLock
                save(device.copy(checkpoint = result.checkpoint, reserved = emptySet()))
                errorMessage = null
                updateStatus()
            }
        } catch (_: TimeoutCancellationException) {
            accounts.withSessionLock {
                if (expected?.let(::isCurrent) == true) reportError("系统尚未确认通知提交，将稍后检查")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            accounts.withSessionLock {
                if (expected?.let(::isCurrent) == true) reportError("进程内通知检查失败，将稍后重试")
            }
        }
    }

    private suspend fun publish(expected: RunningNotificationOwner, event: SystemNotificationEvent) {
        val completion = CompletableDeferred<Throwable?>()
        var receipt: String? = null
        var accepted = false
        try {
            val submitted = accounts.withSessionLock {
                if (!isCurrent(expected)) throw CancellationException("通知订阅已变更")
                if (event.key in device.reserved) return@withSessionLock false
                val token = newReceipt()
                val next = checkNotNull(device.reserve(event, token)) { "通知回执无法预留" }
                save(next)
                receipt = token
                val accountId = checkNotNull(expected.accountId)
                adapter.publish(accountId, event, token) { error ->
                    accounts.withSessionLock {
                        /** 原生投递可晚于注销、标读或 close 完成；只能撤它自己的回执。 */
                        if (error != null || !matchesSession(expected) || device.clicks[token] != event) {
                            withdraw(accountId, event, token)
                        }
                        completion.complete(error)
                    }
                }
                true
            }
            if (!submitted) return
            val token = checkNotNull(receipt)
            val deliveryError = withTimeout(DELIVERY_CONFIRMATION_TIMEOUT_MS) { completion.await() }
            if (deliveryError != null) throw IllegalStateException("系统未接受通知", deliveryError)
            currentCoroutineContext().ensureActive()
            accounts.withSessionLock {
                if (!matchesSession(expected) || device.clicks[token] != event) throw CancellationException("通知回执已失效")
                accepted = true
            }
        } finally {
            val token = receipt
            if (!accepted && token != null) {
                accounts.withSessionLock {
                    try {
                        if (operational() && device.accountId == expected.accountId && device.clicks[token] == event) {
                            save(device.copy(clicks = device.clicks - token))
                        }
                    } finally {
                        withdraw(checkNotNull(expected.accountId), event, token)
                    }
                }
            }
        }
    }

    private fun beginRead(client: ZhihuAccountClient, entryName: String): (() -> Unit)? = accounts.withSessionLock {
        if (!operational() || accounts.client !== client || !client.isCurrent || owner !== client) return@withSessionLock null
        val snapshot = device.readSnapshot(entryName) ?: return@withSessionLock null
        var completed = false
        val onSuccess: () -> Unit = {
            accounts.withSessionLock {
                if (!completed && operational() && accounts.client === client && client.isCurrent && currentAccountId() == snapshot.accountId) {
                    completed = true
                    /** 成功标读即使没有旧回执，也必须阻止请求开始前抓取的未读结果继续投递。 */
                    invalidatePoll(cancel = false)
                    try {
                        saveAndWithdraw(device.markRead(snapshot).state)
                        updateStatus()
                    } catch (_: Exception) {
                        reportError("标读已成功，但本机通知未能完全撤回")
                    }
                }
            }
        }
        onSuccess
    }

    private fun consumeClick(receipt: String) = accounts.withSessionLock {
        if (!operational()) return@withSessionLock
        try {
            reconcileAccount()
            val accountId = device.accountId ?: return@withSessionLock
            val event = device.clicks[receipt] ?: return@withSessionLock
            val result = device.consumeClick(SystemNotificationClick(accountId, event.key, receipt), currentAccountId())
                ?: return@withSessionLock
            save(result.state)
            invalidatePoll(cancel = false)
            withdraw(accountId, event, receipt)
            updateStatus()
            onNavigate(result.event.destination)
        } catch (_: Exception) {
            reportError("无法打开这条系统通知，请在应用内查看")
        }
    }

    private fun currentAccountId(): String? = accounts.accountsState.value
        .takeIf { it.session.login && accounts.client.isCurrent }
        ?.activeAccountId

    private fun reconcileAccount() {
        val client = accounts.client
        if (device.accountId != null && (device.accountId != currentAccountId() || owner !== client || !owner.isCurrent)) {
            invalidatePoll()
            permissionJob?.cancel()
            val previous = device
            try {
                save(DeviceNotificationState())
            } finally {
                previous.accountId?.let { accountId -> previous.clicks.forEach { (receipt, event) -> withdraw(accountId, event, receipt) } }
                owner = client
            }
        }
        owner = client
    }

    private fun applyAuthorization(next: RunningSystemNotificationAuthorization) {
        if (authorization != next) revision++
        authorization = next
        val allowed = effectiveTypes()
        saveAndWithdraw(device.copy(clicks = device.clicks.filterValues { it.type in allowed }))
    }

    private fun effectiveTypes(): Set<NotificationType> = if (authorization.supported && authorization.permissionGranted && authorization.appNotificationsEnabled) {
        device.enabledTypes - authorization.blockedTypes
    } else {
        emptySet()
    }

    private fun snapshot(): RunningNotificationOwner = RunningNotificationOwner(accounts.client, currentAccountId(), device, revision)

    private fun operational(): Boolean = active && !closed && !storageFailed && lifetime.isActive

    private fun matchesSession(expected: RunningNotificationOwner): Boolean = operational() &&
        accounts.client === expected.client &&
        expected.client.isCurrent &&
        owner === expected.client &&
        currentAccountId() == expected.accountId &&
        device.accountId == expected.state.accountId &&
        device.epoch == expected.state.epoch

    private fun isCurrent(expected: RunningNotificationOwner): Boolean = matchesSession(expected) && revision == expected.revision

    private fun checkCurrent(expected: RunningNotificationOwner) = accounts.withSessionLock {
        if (!isCurrent(expected)) throw CancellationException("通知订阅已变更")
    }

    private fun invalidatePoll(cancel: Boolean = true) {
        revision++
        if (cancel) pollJob?.cancel(CancellationException("通知订阅或已读状态已变更"))
        wake.trySend(Unit)
    }

    private fun newReceipt(): String = adapter.newReceipt().also {
        check(it.isNotBlank() && it != device.epoch && it !in device.clicks) { "平台未提供新的随机通知回执" }
    }

    private fun saveAndWithdraw(next: DeviceNotificationState) {
        val previous = device
        save(next)
        previous.accountId?.let { accountId ->
            previous.clicks.filterKeys { it !in next.clicks }.forEach { (receipt, event) -> withdraw(accountId, event, receipt) }
        }
    }

    private fun save(next: DeviceNotificationState) {
        if (device == next) return
        try {
            validateState(next)
            adapter.saveState(next)
            device = next
        } catch (error: Exception) {
            /** 不把内存改成“已保存”；关闭能力并保留错误，避免后续查询掩盖磁盘失败。 */
            storageFailed = true
            errorMessage = "本机通知状态保存失败，已停止检查；请修复存储后重新启动"
            invalidatePoll()
            device.accountId?.let { accountId -> device.clicks.forEach { (receipt, event) -> withdraw(accountId, event, receipt) } }
            updateStatus()
            throw error
        }
    }

    private fun validateState(state: DeviceNotificationState) {
        require(
            if (state.enabledTypes.isEmpty()) {
                state.accountId == null && state.epoch.isEmpty() && state.clicks.isEmpty() && state.reserved.isEmpty()
            } else {
                !state.accountId.isNullOrBlank() && state.epoch.isNotBlank()
            },
        ) { "本机通知状态不完整" }
        require(state.clicks.all { (receipt, event) -> receipt.isNotBlank() && event.key.isNotBlank() && event.type in state.enabledTypes })
    }

    private fun withdraw(accountId: String, event: SystemNotificationEvent, receipt: String) =
        withdraw(SystemNotificationClick(accountId, event.key, receipt))

    private fun withdraw(click: SystemNotificationClick) {
        try {
            adapter.withdraw(click.accountId, click.eventKey, click.receipt)
            pendingWithdrawals -= click
        } catch (_: Exception) {
            pendingWithdrawals += click
        }
        updateStatus()
    }

    private fun reportError(message: String) {
        if (!storageFailed) errorMessage = message
        updateStatus()
    }

    private fun updateStatus() {
        mutableStatus.value = SystemNotificationStatus(
            supported = operational() && authorization.supported,
            signedIn = currentAccountId() != null,
            permissionGranted = authorization.permissionGranted,
            appNotificationsEnabled = authorization.appNotificationsEnabled,
            enabledTypes = if (device.accountId == currentAccountId() && owner === accounts.client) device.enabledTypes else emptySet(),
            blockedChannels = authorization.blockedTypes,
            error = errorMessage ?: if (pendingWithdrawals.isNotEmpty()) "部分系统通知未能撤回，将重试；也可在系统通知中心检查" else null,
            backgroundPollingAvailable = false,
            availabilityDescription = adapter.availabilityDescription,
        )
    }

    override fun close() {
        accounts.withSessionLock {
            if (closed) return@withSessionLock
            closed = true
            active = false
            revision++
            ready.complete(false)
            runCatching { readObserver?.close() }.onFailure { reportError("无法释放本机通知观察者") }
            readObserver = null
            runCatching { activation?.close() }.onFailure { reportError("无法释放系统通知事件监听") }
            activation = null
            updateStatus()
        }
        scope.cancel()
    }
}

private val LocalRunningSystemNotifications = staticCompositionLocalOf<RunningSystemNotifications?> { null }

/**
 * 安装在应用/accountStore 的唯一根宿主，而不是设置页或每个窗口；多窗口须在窗口之外共享这个宿主。
 * 没有成功的原生事件租约时仍为 Unsupported；此组合不自行创建账号、数据库或后台服务。
 */
@Composable
internal fun RunningSystemNotificationHost(
    accounts: ZhihuAccountStore,
    adapter: RunningSystemNotificationAdapter,
    onOpen: (NavDestination) -> Unit,
    content: @Composable () -> Unit,
) {
    val parentScope = rememberCoroutineScope()
    val latestOnOpen = rememberUpdatedState(onOpen)
    val runtime = remember(accounts, adapter) {
        RunningSystemNotifications(accounts, adapter, parentScope) { latestOnOpen.value(it) }
    }
    DisposableEffect(runtime) {
        runtime.start()
        onDispose { runtime.close() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { runtime.refresh() }
    CompositionLocalProvider(
        LocalRunningSystemNotifications provides runtime,
        LocalZhihuAccountStore provides accounts,
        content = content,
    )
}

/** 设置页只借用根宿主，旧页面捕获的客户端不能替新账号改变订阅。 */
@Composable
internal fun rememberRunningSystemNotificationControl(): SystemNotificationControl {
    val runtime = LocalRunningSystemNotifications.current ?: return UnsupportedSystemNotifications
    val accounts = LocalZhihuAccountStore.current ?: return UnsupportedSystemNotifications
    if (!runtime.owns(accounts)) return UnsupportedSystemNotifications
    val client = accounts.client
    return remember(runtime, client) {
        object : SystemNotificationControl {
            override val status = runtime.status

            override fun setTypeEnabled(type: NotificationType, enabled: Boolean) = runtime.setTypeEnabled(type, enabled, client)

            override fun openSystemSettings() = runtime.openSystemSettings()
        }
    }
}
