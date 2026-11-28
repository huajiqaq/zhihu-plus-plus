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

package com.github.zly2006.zhihu.viewmodel.local

import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal actual suspend fun tryAcquireCrawlingLock(databasePath: String): AutoCloseable? = suspendCancellableCoroutine { continuation ->
    var request: BrowserCrawlingLock? = null
    request = acquireBrowserCrawlingLock(databasePath) { result ->
        when (result) {
            1 -> {
                val lease = AutoCloseable { request?.release() }
                continuation.resume(lease) { _, value, _ -> value.close() }
            }
            0 -> continuation.resume(null) { _, _, _ -> }
            else -> continuation.resumeWithException(IllegalStateException("浏览器未能取得本地推荐锁，请使用支持 Web Locks 的安全页面"))
        }
    }
    continuation.invokeOnCancellation { request.release() }
}

private external interface BrowserCrawlingLock : JsAny {
    fun release()
}

/** Web Locks 随页面销毁释放，异步取得的租约在取消时也必须归还。 */
@JsFun(
    """(database, done) => {
        let closed = false, unlock = null;
        const handle = {release() { closed = true; if (unlock) { unlock(); unlock = null; } }};
        queueMicrotask(() => {
            if (closed) return;
            if (!globalThis.isSecureContext || !navigator.locks) { done(-1); return; }
            navigator.locks.request('zhihu++:crawling:' + database, {ifAvailable: true}, lock => {
                if (closed) return;
                if (!lock) { done(0); return; }
                return new Promise(resolve => {
                    unlock = resolve;
                    done(1);
                });
            }).catch(() => { if (!closed) done(-1); });
        });
        return handle;
    }""",
)
private external fun acquireBrowserCrawlingLock(database: String, done: (Int) -> Unit): BrowserCrawlingLock
