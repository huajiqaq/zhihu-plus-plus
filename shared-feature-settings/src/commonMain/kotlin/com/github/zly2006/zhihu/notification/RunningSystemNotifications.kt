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
import com.github.zly2006.zhihu.i18n.NotificationsStrings
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

    /** 原生文件须受本机账号保护且排除备份；Web使用独立站点状态并披露浏览器备份限制。仅不存在时返回默认状态，损坏须抛错。 */
    fun loadState(): DeviceNotificationState

    /** 标读请求发出前只拍快照，不替换当前事务的存储版本。 */
    fun readSnapshot(): DeviceNotificationState = loadState()

    /** 原子替换且持久化成功后才返回；失败保留旧文件并抛错，不由平台镜像另一份订阅。 */
    fun saveState(state: DeviceNotificationState)

    /** 使用平台的密码学安全随机源，同时用于订阅 epoch；不得复用旧值。 */
    fun newReceipt(): String

    suspend fun authorization(): RunningSystemNotificationAuthorization

    /** 浏览器权限必须在点击调用栈中发起，不能等异步站点锁或系统授权查询。 */
    fun prepareAuthorizationRequest() = Unit

    /** 仅由明确启用操作触发，拒绝或失败不能被当作已授权。 */
    suspend fun requestAuthorization()

    /** 同源多标签共享持久状态时，每个事务重读；原生文件仍由唯一宿主持有。 */
    val sharedState: Boolean get() = false

    fun isStateCurrent(state: DeviceNotificationState): Boolean = true

    /** 标读成功立即作废其他标签页尚未完成的只读轮询，不改变订阅。 */
    fun invalidateReadAll() = Unit

    suspend fun withStateLock(block: suspend () -> Unit) = block()

    /** 页面隐藏时不承诺后台检查；权限状态与可运行状态分开。 */
    val canPoll: Boolean get() = true

    /** 浏览器初次核身未完成不等于退出，不能清空另一标签页保存的订阅。 */
    fun accountStateKnown(accounts: ZhihuAccountStore): Boolean = true

    fun observeChanges(onChange: () -> Unit): AutoCloseable? = null

    /** 只有本机回执处理完毕后，平台才删除冷启动点击队列。 */
    fun confirmClick(receipt: String) = Unit

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
    private var ownerAccountId = accounts.accountsState.value.activeAccountId
    private var authorization = RunningSystemNotificationAuthorization(false, false, supported = false)
    private var activation: AutoCloseable? = null
    private var readObserver: AutoCloseable? = null
    private var changeObserver: AutoCloseable? = null
    private val pendingClicks = linkedSetOf<String>()
    private var pollJob: Job? = null
    private var permissionJob: Job? = null
    private var revision = 0L
    private var started = false
    private var active = false
    private var closed = false
    private var storageFailed = false
    private var errorMessage: (() -> String)? = null
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
                            if (ready.await()) {
                                val added = accounts.withSessionLock { pendingClicks.add(receipt) }
                                if (added) refresh()
                            }
                        }
                    }
                }
                if (activation == null) {
                    errorMessage = if (adapter.availabilityDescription.isNullOrBlank()) ({ NotificationsStrings.hostNotConnected }) else null
                    updateStatus()
                    ready.complete(false)
                    return@withSessionLock false
                }
                true
            } catch (_: Exception) {
                initializationFailed()
                false
            }
        }
        if (!shouldStart) return false
        /** Web Locks 可挂起；绝不把账号的可重入锁跨越异步站点锁。 */
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                withDeviceLock {
                    accounts.withSessionLock {
                        if (closed) return@withSessionLock
                        if (!adapter.sharedState) device = adapter.loadState().also(::validateState)
                        reconcileAccount()
                        active = true
                        readObserver = accounts.observeNotificationReadAll(::beginRead)
                        changeObserver = adapter.observeChanges(::refresh)
                        updateStatus()
                    }
                }
                ready.complete(active)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                accounts.withSessionLock { initializationFailed() }
            }
        }
        scope.launch {
            if (!ready.await()) return@launch
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
            if (!ready.await()) return@launch
            while (isActive) {
                val cycle = launch(start = CoroutineStart.LAZY) {
                    try {
                        withDeviceLock { runCycle() }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        accounts.withSessionLock { reportError { NotificationsStrings.runningPollFailed } }
                    }
                }
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
        return true
    }

    private fun initializationFailed() {
        active = false
        errorMessage = { NotificationsStrings.initFailed }
        runCatching { activation?.close() }
        activation = null
        updateStatus()
        ready.complete(false)
    }

    private suspend fun withDeviceLock(block: suspend () -> Unit) = adapter.withStateLock {
        currentCoroutineContext().ensureActive()
        if (adapter.sharedState) {
            accounts.withSessionLock {
                val latest = adapter.loadState().also(::validateState)
                if (latest != device) {
                    device = latest
                    revision++
                }
            }
        }
        block()
    }

    fun refresh(): Unit = accounts.withSessionLock {
        if (!active || closed) return@withSessionLock
        if (adapter.sharedState) invalidatePoll()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                withDeviceLock {
                    accounts.withSessionLock {
                        if (!operational()) return@withSessionLock
                        reconcileAccount()
                        if (authorization.supported && adapter.accountStateKnown(accounts)) pendingClicks.toList().forEach(::consumeClick)
                        updateStatus()
                        wake.trySend(Unit)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                accounts.withSessionLock { reportError { NotificationsStrings.updateFailed } }
            }
        }
    }

    fun setTypeEnabled(type: NotificationType, enabled: Boolean, expectedClient: ZhihuAccountClient) {
        val allowed = accounts.withSessionLock {
            if (!operational() || accounts.client !== expectedClient || !expectedClient.isCurrent || currentAccountId() == null) {
                return@withSessionLock false
            }
            try {
                if (enabled) adapter.prepareAuthorizationRequest()
                invalidatePoll()
                true
            } catch (_: Exception) {
                reportError { NotificationsStrings.permissionRequestFailed }
                false
            }
        }
        if (!allowed) return
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var request: Job? = null
            try {
                withDeviceLock {
                    accounts.withSessionLock {
                        if (!operational()) return@withSessionLock
                        reconcileAccount()
                        if (accounts.client !== expectedClient || !expectedClient.isCurrent) return@withSessionLock
                        val accountId = currentAccountId() ?: return@withSessionLock
                        if (adapter.sharedState && device.accountId != null && device.accountId != accountId) {
                            if (!enabled) return@withSessionLock
                            saveAndWithdraw(DeviceNotificationState())
                        }
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
                    }
                }
                request?.start()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                accounts.withSessionLock { reportError { NotificationsStrings.subscriptionSaveFailed } }
            }
        }
    }

    fun openSystemSettings() = accounts.withSessionLock {
        if (!operational()) return@withSessionLock
        try {
            adapter.openSystemSettings()
        } catch (error: UnsupportedOperationException) {
            reportError { error.message?.takeIf { it.isNotBlank() } ?: NotificationsStrings.systemSettingsHint }
        } catch (_: Exception) {
            reportError { NotificationsStrings.systemSettingsFailed }
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
                if (matchesSession(expected)) reportError { NotificationsStrings.permissionRequestFailed }
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
                if (permission.supported && adapter.accountStateKnown(accounts)) pendingClicks.toList().forEach(::consumeClick)
                updateStatus()
                val types = effectiveTypes()
                if (!adapter.canPoll || !adapter.accountStateKnown(accounts) || device.accountId == null || types.isEmpty() || pendingWithdrawals.isNotEmpty()) {
                    return@withSessionLock null
                }
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
                if (expected?.let(::isCurrent) == true) reportError { NotificationsStrings.deliveryPending }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            accounts.withSessionLock {
                if (expected?.let(::isCurrent) == true) reportError { NotificationsStrings.runningPollFailed }
            }
        }
    }

    private suspend fun publish(expected: RunningNotificationOwner, event: SystemNotificationEvent) {
        val completion = CompletableDeferred<Throwable?>()
        var receipt: String? = null
        var accepted = false
        try {
            val submitted = accounts.withSessionLock {
                if (!isCurrent(expected)) throw CancellationException(NotificationsStrings.subscriptionChanged)
                if (event.key in device.reserved) return@withSessionLock false
                val token = newReceipt()
                val next = checkNotNull(device.reserve(event, token)) { NotificationsStrings.receiptReserveFailed }
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
            if (deliveryError != null) throw IllegalStateException(NotificationsStrings.deliveryRejected, deliveryError)
            currentCoroutineContext().ensureActive()
            accounts.withSessionLock {
                if (!matchesSession(expected) || device.clicks[token] != event) throw CancellationException(NotificationsStrings.receiptExpired)
                accepted = true
            }
        } finally {
            val token = receipt
            if (!accepted && token != null) {
                accounts.withSessionLock {
                    try {
                        if (operational() && (!adapter.sharedState || revision == expected.revision) && adapter.isStateCurrent(device) &&
                            device.accountId == expected.accountId && device.clicks[token] == event
                        ) {
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
        val snapshot = (if (adapter.sharedState) adapter.readSnapshot() else device).readSnapshot(entryName) ?: return@withSessionLock null
        var completed = false
        val onSuccess: () -> Unit = {
            accounts.withSessionLock {
                if (!completed && operational() && accounts.client === client && client.isCurrent && currentAccountId() == snapshot.accountId) {
                    completed = true
                    try {
                        adapter.invalidateReadAll()
                    } catch (_: Exception) {
                        reportError { NotificationsStrings.readWithdrawFailed }
                    }
                    /** 成功标读即使没有旧回执，也必须阻止请求开始前抓取的未读结果继续投递。 */
                    invalidatePoll(cancel = adapter.sharedState)
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        try {
                            withDeviceLock {
                                accounts.withSessionLock {
                                    if (operational() && accounts.client === client && client.isCurrent && currentAccountId() == snapshot.accountId) {
                                        saveAndWithdraw(device.markRead(snapshot).state)
                                        updateStatus()
                                    }
                                }
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            accounts.withSessionLock { reportError { NotificationsStrings.readWithdrawFailed } }
                        }
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
            val accountId = device.accountId
            if (adapter.sharedState && accountId != null && accountId != currentAccountId()) return@withSessionLock
            val event = device.clicks[receipt]
            val result = if (accountId != null && event != null) {
                device.consumeClick(SystemNotificationClick(accountId, event.key, receipt), currentAccountId())
            } else {
                null
            }
            if (result != null) {
                save(result.state)
                invalidatePoll(cancel = false)
                withdraw(checkNotNull(accountId), checkNotNull(event), receipt)
            }
            adapter.confirmClick(receipt)
            pendingClicks -= receipt
            updateStatus()
            if (result != null) onNavigate(result.event.destination)
        } catch (_: Exception) {
            reportError { NotificationsStrings.openFailed }
        }
    }

    private fun currentAccountId(): String? = accounts.accountsState.value
        .takeIf { it.session.login && accounts.client.isCurrent }
        ?.activeAccountId

    private fun reconcileAccount() {
        val client = accounts.client
        if (!adapter.accountStateKnown(accounts)) return
        val revoked = if (adapter.sharedState) {
            /** 另一标签页切号后，旧标签页不能凭过期身份反向清空新账号的订阅。 */
            ownerAccountId != null && device.accountId == ownerAccountId &&
                (owner !== client || !owner.isCurrent) && device.accountId != currentAccountId()
        } else {
            device.accountId != currentAccountId() || owner !== client || !owner.isCurrent
        }
        if (device.accountId != null && revoked) {
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
        ownerAccountId = currentAccountId()
    }

    private fun applyAuthorization(next: RunningSystemNotificationAuthorization) {
        if (authorization != next) revision++
        authorization = next
        if (adapter.sharedState && (!next.supported || !adapter.accountStateKnown(accounts) || device.accountId != currentAccountId())) return
        val allowed = effectiveTypes()
        saveAndWithdraw(device.copy(clicks = device.clicks.filterValues { it.type in allowed }))
    }

    private fun effectiveTypes(): Set<NotificationType> = if (
        device.accountId == currentAccountId() && owner === accounts.client &&
        authorization.supported && authorization.permissionGranted && authorization.appNotificationsEnabled
    ) {
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
        device.epoch == expected.state.epoch &&
        adapter.isStateCurrent(device)

    private fun isCurrent(expected: RunningNotificationOwner): Boolean = matchesSession(expected) &&
        revision == expected.revision && (!adapter.sharedState || adapter.canPoll)

    private fun checkCurrent(expected: RunningNotificationOwner) = accounts.withSessionLock {
        if (!isCurrent(expected)) throw CancellationException(NotificationsStrings.subscriptionChanged)
    }

    private fun invalidatePoll(cancel: Boolean = true) {
        revision++
        if (cancel) pollJob?.cancel(CancellationException(NotificationsStrings.subscriptionOrReadChanged))
        wake.trySend(Unit)
    }

    private fun newReceipt(): String = adapter.newReceipt().also {
        check(it.isNotBlank() && it != device.epoch && it !in device.clicks) { NotificationsStrings.receiptRandomMissing }
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
            errorMessage = { NotificationsStrings.storageFailedStopped }
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
        ) { NotificationsStrings.stateIncomplete }
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

    private fun reportError(message: () -> String) {
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
            errorText = errorMessage ?: if (pendingWithdrawals.isNotEmpty()) ({ NotificationsStrings.withdrawPending }) else null,
            backgroundPollingAvailable = false,
            availabilityText = { adapter.availabilityDescription },
        )
    }

    override fun close() {
        accounts.withSessionLock {
            if (closed) return@withSessionLock
            closed = true
            active = false
            revision++
            ready.complete(false)
            runCatching { readObserver?.close() }.onFailure { reportError { NotificationsStrings.readObserverReleaseFailed } }
            readObserver = null
            runCatching { changeObserver?.close() }.onFailure { reportError { NotificationsStrings.listenerReleaseFailed } }
            changeObserver = null
            runCatching { activation?.close() }.onFailure { reportError { NotificationsStrings.listenerReleaseFailed } }
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
