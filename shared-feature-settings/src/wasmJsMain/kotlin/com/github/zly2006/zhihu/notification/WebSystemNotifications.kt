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

@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.github.zly2006.zhihu.notification

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.github.zly2006.zhihu.account.BrowserSessionStatus
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.i18n.NotificationsStrings
import com.github.zly2006.zhihu.navigation.NavDestination
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.JsAny
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json

/** 只借用当前账号与共用只读消息源；SW 不持有凭据、不轮询、不缓存应用资源。 */
@Composable
fun WebSystemNotificationHost(
    accountStore: ZhihuAccountStore,
    onOpen: (NavDestination) -> Unit,
    content: @Composable () -> Unit,
) {
    val adapter = remember(accountStore) { WebSystemNotificationAdapter() }
    LaunchedEffect(accountStore, adapter) {
        accountStore.browserSessionStatus.collect { adapter.refreshAccount() }
    }
    RunningSystemNotificationHost(accountStore, adapter, onOpen, content)
}

private class WebSystemNotificationAdapter : RunningSystemNotificationAdapter {
    private var bridge: WebNotificationBridge? = null
    private var changed: (() -> Unit)? = null
    private var permissionRequest: CompletableDeferred<Boolean>? = null
    private var stored: String? = null
    private var readRevision: String? = null
    override val sharedState: Boolean = true
    override val canPoll: Boolean get() = bridge?.visible() == true
    override val availabilityDescription: String
        get() = if (bridge?.available() == true) NotificationsStrings.webAvailability else NotificationsStrings.webUnavailable

    override fun activate(onClick: (String) -> Unit): AutoCloseable? {
        if (bridge != null) return null
        val connected = connectWebNotifications(onClick) { changed?.invoke() } ?: return null
        bridge = connected
        return AutoCloseable {
            connected.close()
            if (bridge === connected) bridge = null
        }
    }

    fun refreshAccount() { changed?.invoke() }

    override fun observeChanges(onChange: () -> Unit): AutoCloseable {
        changed = onChange
        return AutoCloseable { if (changed === onChange) changed = null }
    }

    override fun accountStateKnown(accounts: ZhihuAccountStore): Boolean =
        accounts.session.login || accounts.browserSessionStatus.value in setOf(BrowserSessionStatus.Guest, BrowserSessionStatus.Expired)

    override suspend fun withStateLock(block: suspend () -> Unit) {
        val connection = checkNotNull(bridge)
        val lease = suspendCancellableCoroutine<WebNotificationLease> { continuation ->
            val pending = connection.lock { acquired ->
                if (acquired == null) {
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException(NotificationsStrings.webUnavailable))
                } else if (continuation.isActive) {
                    continuation.resume(acquired, onCancellation = { _, value, _ -> value.release() })
                } else {
                    acquired.release()
                }
            }
            continuation.invokeOnCancellation { pending.close() }
        }
        try {
            block()
        } finally {
            lease.release()
        }
    }

    override fun loadState(): DeviceNotificationState {
        stored = checkNotNull(bridge).readState()
        readRevision = checkNotNull(bridge).readRevision()
        return stored?.let { Json.decodeFromString(DeviceNotificationState.serializer(), it) } ?: DeviceNotificationState()
    }

    override fun readSnapshot(): DeviceNotificationState = checkNotNull(bridge).readState()
        ?.let { Json.decodeFromString(DeviceNotificationState.serializer(), it) } ?: DeviceNotificationState()

    override fun saveState(state: DeviceNotificationState) {
        val text = Json.encodeToString(DeviceNotificationState.serializer(), state)
        check(checkNotNull(bridge).writeState(stored, text)) { NotificationsStrings.storageFailedStopped }
        stored = text
    }

    override fun invalidateReadAll() = checkNotNull(bridge).invalidateReadAll()

    override fun isStateCurrent(state: DeviceNotificationState): Boolean {
        if (bridge?.readRevision() != readRevision) return false
        val text = bridge?.readState() ?: return state == DeviceNotificationState()
        return text == Json.encodeToString(DeviceNotificationState.serializer(), state)
    }

    override fun newReceipt(): String = checkNotNull(bridge).newReceipt()

    override suspend fun authorization(): RunningSystemNotificationAuthorization {
        val connection = bridge
        val available = connection?.available() == true
        val granted = available && connection.permission() == "granted"
        return RunningSystemNotificationAuthorization(granted, granted, supported = available)
    }

    override fun prepareAuthorizationRequest() {
        val connection = checkNotNull(bridge)
        if (connection.permission() == "granted") return
        val result = CompletableDeferred<Boolean>()
        permissionRequest = result
        // requestPermission 在这个同步调用内执行；回调才是异步的。
        connection.requestPermission { result.complete(it) }
    }

    override suspend fun requestAuthorization() {
        check(permissionRequest?.await() == true) { NotificationsStrings.permissionRequestFailed }
    }

    override fun publish(accountId: String, event: SystemNotificationEvent, receipt: String, onComplete: (Throwable?) -> Unit) {
        checkNotNull(bridge).publish(receipt, NotificationsStrings.appName, NotificationsStrings.newMessage) { accepted ->
            onComplete(if (accepted) null else IllegalStateException(NotificationsStrings.deliveryRejected))
        }
    }

    override fun withdraw(accountId: String, eventKey: String, receipt: String) {
        check(checkNotNull(bridge).withdraw(receipt)) { NotificationsStrings.withdrawPending }
    }

    override fun confirmClick(receipt: String) = checkNotNull(bridge).confirmClick(receipt)

    override fun openSystemSettings(): Nothing = throw SystemNotificationUnavailableException { NotificationsStrings.webSettingsHint }
}

private external interface WebNotificationHandle : JsAny {
    fun close()
}

private external interface WebNotificationLease : JsAny {
    fun release()
}

private external interface WebNotificationBridge : JsAny {
    fun available(): Boolean
    fun visible(): Boolean
    fun permission(): String
    fun requestPermission(done: (Boolean) -> Unit)
    fun lock(done: (WebNotificationLease?) -> Unit): WebNotificationHandle
    fun readState(): String?
    fun readRevision(): String?
    fun invalidateReadAll()
    fun writeState(expected: String?, next: String): Boolean
    fun newReceipt(): String
    fun publish(receipt: String, title: String, body: String, done: (Boolean) -> Unit)
    fun withdraw(receipt: String): Boolean
    fun confirmClick(receipt: String)
    fun close()
}

/** 只有同目录的专属 SW 可投递点击；通知 payload 只放不可猜测回执，不放账号或导航 URL。 */
@JsFun(
    """(clicked, changed) => {
        if (!isSecureContext || window.top !== window || !('Notification' in window) || !('indexedDB' in window) ||
            !('ServiceWorkerRegistration' in window) || !('showNotification' in ServiceWorkerRegistration.prototype) ||
            !('getNotifications' in ServiceWorkerRegistration.prototype) ||
            !navigator.serviceWorker || !navigator.locks || !crypto?.getRandomValues) return null;
        const base = new URL('./', document.baseURI);
        if (base.origin !== location.origin) return null;
        const workerUrl = new URL('zhihu-notifications-sw.js', base).href;
        const namespace = 'zhihu-system-notifications:v1:' + base.pathname;
        const stateKey = namespace + ':state';
        const readRevisionKey = namespace + ':read-revision';
        const lockName = namespace + ':transaction';
        const receiptPattern = /^zhihu-web-v1-[0-9a-f]{64}$/;
        const probe = namespace + ':probe:' + crypto.getRandomValues(new Uint32Array(4)).join('-');
        try { localStorage.setItem(probe, '1'); localStorage.removeItem(probe); } catch { return null; }
        let registration = null, connected = false, closed = false, suspended = false;
        const outstanding = new Map();
        const withdrawals = new Map();
        const activeLocks = new Set();
        const validWorker = worker => worker && worker.scriptURL === workerUrl;
        const workerReady = () => !closed && registration?.active?.state === 'activated' && validWorker(registration.active);
        const available = () => connected && workerReady();
        const tellWorker = message => { if (workerReady()) registration.active.postMessage(message); };
        const handshake = () => tellWorker({type: 'zhihu-notifications-ready-v1'});
        const onMessage = event => {
            const data = event.data;
            if (closed || !validWorker(event.source)) return;
            if (data?.type === 'zhihu-notifications-ready-v1' && Object.keys(data).length === 1) {
                if (!connected) { connected = true; changed(); }
                return;
            }
            if (data?.type !== 'zhihu-notification-click-v1' || typeof data.receipt !== 'string' ||
                !receiptPattern.test(data.receipt) || Object.keys(data).length !== 2) return;
            clicked(data.receipt);
        };
        const releaseLocks = () => { for (const release of [...activeLocks]) release(); };
        const onVisibility = () => {
            // 隐藏先同步取消Kotlin任务，再释放锁；冻结标签页不能长期阻塞活跃标签。
            changed();
            if (document.visibilityState !== 'visible') releaseLocks();
            else handshake();
        };
        const onPageHide = () => { suspended = true; changed(); releaseLocks(); };
        const onPageShow = () => { suspended = false; onVisibility(); };
        const onStorage = event => {
            if (event.storageArea === localStorage && (event.key === stateKey || event.key === readRevisionKey || event.key === null)) changed();
        };
        navigator.serviceWorker.addEventListener('message', onMessage);
        document.addEventListener('visibilitychange', onVisibility);
        window.addEventListener('pagehide', onPageHide);
        window.addEventListener('pageshow', onPageShow);
        document.addEventListener('freeze', onPageHide);
        document.addEventListener('resume', onPageShow);
        window.addEventListener('storage', onStorage);
        navigator.serviceWorker.register(workerUrl, {scope: base.pathname, updateViaCache: 'none'}).then(reg => {
            if (closed) return;
            registration = reg;
            const worker = reg.installing || reg.waiting || reg.active;
            if (!validWorker(worker)) throw new Error('Unexpected notification worker');
            const ready = () => {
                if (workerReady()) handshake();
            };
            ready();
            worker.addEventListener('statechange', ready);
        }).catch(() => { if (!closed) changed(); });
        const withdraw = receipt => {
            if (!receiptPattern.test(receipt)) return false;
            const previous = withdrawals.get(receipt);
            if (previous === 'done') return true;
            if (previous === 'pending') return false;
            if (!available()) return false;
            const pending = outstanding.get(receipt);
            if (pending) pending.cancelled = true;
            withdrawals.set(receipt, 'pending');
            Promise.resolve(pending?.settled).then(() => registration.getNotifications({tag: receipt})).then(items => {
                for (const item of items) if (item.tag === receipt) item.close();
                withdrawals.set(receipt, 'done');
                if (!closed) changed();
            }, () => { withdrawals.delete(receipt); if (!closed) changed(); });
            return false;
        };
        return {
            available,
            visible: () => !closed && !suspended && document.visibilityState === 'visible',
            permission: () => Notification.permission,
            requestPermission(done) {
                if (!available() || !navigator.userActivation?.isActive) { done(false); return; }
                try { Notification.requestPermission().then(value => done(value === 'granted'), () => done(false)); }
                catch { done(false); }
            },
            lock(done) {
                const abort = new AbortController();
                let release = null, settled = false;
                navigator.locks.request(lockName, {mode: 'exclusive', signal: abort.signal}, () => new Promise(resolve => {
                    let released = false;
                    release = () => {
                        if (released) return;
                        released = true;
                        activeLocks.delete(release);
                        resolve();
                    };
                    activeLocks.add(release);
                    settled = true;
                    done({release});
                })).catch(() => { if (!settled) done(null); });
                return {close() { abort.abort(); release?.(); }};
            },
            readState: () => localStorage.getItem(stateKey),
            readRevision: () => localStorage.getItem(readRevisionKey),
            invalidateReadAll() {
                localStorage.setItem(readRevisionKey, crypto.getRandomValues(new Uint32Array(4)).join('-'));
            },
            writeState(expected, next) {
                if (localStorage.getItem(stateKey) !== expected) return false;
                localStorage.setItem(stateKey, next);
                return true;
            },
            newReceipt() {
                const bytes = crypto.getRandomValues(new Uint8Array(32));
                return 'zhihu-web-v1-' + [...bytes].map(value => value.toString(16).padStart(2, '0')).join('');
            },
            publish(receipt, title, body, done) {
                if (!available() || Notification.permission !== 'granted' || !receiptPattern.test(receipt) || outstanding.has(receipt)) {
                    done(false); return;
                }
                const item = {cancelled: false, settled: null};
                outstanding.set(receipt, item);
                withdrawals.delete(receipt);
                item.settled = registration.showNotification(title, {body, tag: receipt, data: {receipt}}).then(async () => {
                    if (item.cancelled || closed) {
                        const visible = await registration.getNotifications({tag: receipt});
                        for (const notification of visible) if (notification.tag === receipt) notification.close();
                        done(false);
                    } else done(true);
                }, () => done(false)).finally(() => { outstanding.delete(receipt); });
            },
            withdraw,
            confirmClick(receipt) {
                if (receiptPattern.test(receipt)) tellWorker({type: 'zhihu-notification-consumed-v1', receipt});
            },
            close() {
                if (closed) return;
                closed = true;
                releaseLocks();
                navigator.serviceWorker.removeEventListener('message', onMessage);
                document.removeEventListener('visibilitychange', onVisibility);
                window.removeEventListener('pagehide', onPageHide);
                window.removeEventListener('pageshow', onPageShow);
                document.removeEventListener('freeze', onPageHide);
                document.removeEventListener('resume', onPageShow);
                window.removeEventListener('storage', onStorage);
                for (const item of outstanding.values()) item.cancelled = true;
            }
        };
    }""",
)
private external fun connectWebNotifications(clicked: (String) -> Unit, changed: () -> Unit): WebNotificationBridge?
