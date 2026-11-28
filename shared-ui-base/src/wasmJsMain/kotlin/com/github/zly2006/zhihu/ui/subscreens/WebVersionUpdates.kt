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

@file:OptIn(ExperimentalWasmJsInterop::class)

package com.github.zly2006.zhihu.ui.subscreens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.github.zly2006.zhihu.i18n.ReaderStrings
import com.github.zly2006.zhihu.platform.platformName
import com.github.zly2006.zhihu.updater.UpdateStep
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json

private val webCacheJson = Json { ignoreUnknownKeys = true }
private val webUpdateState = MutableStateFlow<SystemUpdateState>(SystemUpdateState.Web(WebAppCacheSnapshot()))

/** JS 缓存宿主拥有任务和缓存；Kotlin 仅订阅快照，离开设置页不取消下载。 */
private val webCacheSubscription by lazy {
    subscribeWebAppCache(::acceptWebCacheSnapshot)
    true
}

private fun acceptWebCacheSnapshot(json: String) {
    val snapshot = runCatching {
        webCacheJson.decodeFromString<WebAppCacheSnapshot>(json).also {
            require(it.schema == 1 && it.cachedBytes >= 0 && it.downloadedBytes >= 0 && it.totalBytes >= 0)
        }
    }.getOrElse {
        WebAppCacheSnapshot(phase = "unavailable", error = WebCacheError("unavailable"))
    }
    webUpdateState.value = SystemUpdateState.Web(snapshot)
}

private suspend fun runWebCacheAction(action: String) {
    webCacheSubscription
    suspendCancellableCoroutine { continuation ->
        invokeWebAppCache(action) { snapshot ->
            acceptWebCacheSnapshot(snapshot)
            if (continuation.isActive) continuation.resume(Unit)
        }
    }
}

@Composable
actual fun rememberSystemUpdateState(): StateFlow<SystemUpdateState> {
    webCacheSubscription
    return webUpdateState
}

@Composable
actual fun rememberSystemUpdateChecker(auto: Boolean): suspend () -> Unit = { runWebCacheAction("check") }

@Composable
actual fun rememberSystemUpdateVersionSkipper(): (String) -> Unit = {
    error("Web updates do not support skipping versions")
}

@Composable
actual fun rememberSystemUpdateDownloader(): suspend () -> Unit = { runWebCacheAction("download") }

/** 只由保存输入确认框调用；应用候选与调试刷新均由同一 JS 宿主保留 hash。 */
@Composable
actual fun rememberDownloadedSystemUpdateInstaller(): suspend () -> Unit = { runWebCacheAction("apply") }

@Composable
actual fun rememberWebAppCacheAction(): suspend (WebAppCacheAction) -> Unit = { runWebCacheAction(it.name.lowercase()) }

@Composable
actual fun rememberSystemUpdateCanceller(): () -> Unit = { invokeWebAppCache("cancel", ::acceptWebCacheSnapshot) }

actual fun resetSystemUpdateState() {
    // 关闭提示不撤销已验证候选，也不改变活动下载的所有权。
    webCacheSubscription
}

actual fun setSystemUpdateError(message: String, step: UpdateStep) {
    webUpdateState.value = SystemUpdateState.Error(message, step)
}

actual val isApkUpdateInstallSupported: Boolean = false
actual val isWebViewCustomFontSupported: Boolean = false

@Composable
actual fun WebViewCustomFontSettings(customFontName: String?, onCustomFontNameChange: (String?) -> Unit) {
    Text(ReaderStrings.webviewFontUnavailable(platformName))
}

@JsFun(
    """(done) => {
        const snapshot = () => JSON.stringify(window.zhihuAppCache?.snapshot() || {
            schema: 1, available: false, phase: 'unavailable', error: {code: 'unavailable'}
        });
        window.addEventListener('zhihu-app-cache-changed', () => done(snapshot()));
        done(snapshot());
    }""",
)
private external fun subscribeWebAppCache(done: (String) -> Unit)

@JsFun(
    """(action, done) => {
        const cache = window.zhihuAppCache;
        const snapshot = () => JSON.stringify(cache?.snapshot() || {
            schema: 1, available: false, phase: 'unavailable', error: {code: 'unavailable'}
        });
        if (!cache || !['check', 'download', 'apply', 'clear', 'restore', 'cancel'].includes(action)) {
            done(snapshot()); return;
        }
        Promise.resolve().then(() => cache[action]()).then(() => done(snapshot())).catch(() => {
            done(JSON.stringify({...cache.snapshot(), error: {code: 'unavailable'}}));
        });
    }""",
)
private external fun invokeWebAppCache(action: String, done: (String) -> Unit)
