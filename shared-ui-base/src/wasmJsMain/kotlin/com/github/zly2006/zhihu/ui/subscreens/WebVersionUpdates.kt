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
import com.github.zly2006.zhihu.i18n.SettingsStrings
import com.github.zly2006.zhihu.platform.platformName
import com.github.zly2006.zhihu.updater.UpdateStep
import kotlin.coroutines.resume
import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

private val loadedBuildId = readWebBuildId().takeIf { Regex("[a-f0-9]{16,64}").matches(it) }
private val webUpdateState = MutableStateFlow<SystemUpdateState>(
    if (loadedBuildId == null) SystemUpdateState.Unpublished else SystemUpdateState.NoUpdate,
)

@Composable
actual fun rememberSystemUpdateState(): StateFlow<SystemUpdateState> = webUpdateState

@Composable
actual fun rememberSystemUpdateChecker(): suspend () -> Unit = check@{
    if (loadedBuildId == null) {
        webUpdateState.value = SystemUpdateState.Unpublished
        return@check
    }
    if (webUpdateState.value == SystemUpdateState.Checking) return@check
    webUpdateState.value = SystemUpdateState.Checking
    try {
        val publishedBuildId = suspendCancellableCoroutine<String?> { continuation ->
            val request = requestWebBuildId { success, buildId ->
                if (continuation.isActive) continuation.resume(buildId.takeIf { success })
            }
            continuation.invokeOnCancellation { request.abort() }
        }
        webUpdateState.value = when (publishedBuildId) {
            null -> SystemUpdateState.Error({ SettingsStrings.webUpdateCheckFailed })
            loadedBuildId -> SystemUpdateState.Latest
            else -> SystemUpdateState.WebUpdateAvailable(publishedBuildId)
        }
    } catch (error: CancellationException) {
        resetSystemUpdateState()
        throw error
    } catch (_: Exception) {
        webUpdateState.value = SystemUpdateState.Error({ SettingsStrings.webUpdateCheckFailed })
    }
}

@Composable
actual fun rememberSystemUpdateVersionSkipper(): (String) -> Unit = {
    error("Web updates do not support skipping versions")
}

/** 由更新卡的保存输入确认框调用；普通 reload 保留当前 hash 和浏览器存储。 */
@Composable
actual fun rememberSystemUpdateDownloader(): suspend () -> Unit = {
    if (webUpdateState.value is SystemUpdateState.WebUpdateAvailable) window.location.reload()
}

@Composable
actual fun rememberDownloadedSystemUpdateInstaller(): suspend () -> Unit = {
    webUpdateState.value = SystemUpdateState.Error({ ReaderStrings.apkInstallUnavailable(platformName) }, UpdateStep.Install)
}

actual fun resetSystemUpdateState() {
    webUpdateState.value = if (loadedBuildId == null) SystemUpdateState.Unpublished else SystemUpdateState.NoUpdate
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

@JsFun("() => document.querySelector('meta[name=\"zhihu-build-id\"]')?.content || ''")
private external fun readWebBuildId(): String

private external interface WebVersionRequest : JsAny {
    fun abort()
}

/** 只读取当前静态站点的发布标识，不使用账号请求桥、Cookie 或 GitHub 凭据。 */
@JsFun(
    """(done) => {
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), 15000);
        (async () => {
            try {
                const url = new URL('web-version.json', document.baseURI);
                if (url.origin !== location.origin || !['http:', 'https:'].includes(url.protocol) ||
                    url.username || url.password) throw new Error();
                const response = await fetch(url.href, {
                    mode: 'same-origin', credentials: 'omit', cache: 'no-store', redirect: 'error',
                    referrerPolicy: 'no-referrer', signal: controller.signal
                });
                if (response.status !== 200 || !response.body ||
                    (response.headers.get('content-type') || '').split(';')[0].trim().toLowerCase() !== 'application/json' ||
                    Number(response.headers.get('content-length')) > 2048) throw new Error();
                const reader = response.body.getReader();
                const bytes = new Uint8Array(2048);
                let size = 0;
                try {
                    while (true) {
                        const part = await reader.read();
                        if (part.done) break;
                        if (size + part.value.byteLength > bytes.length) throw new Error();
                        bytes.set(part.value, size);
                        size += part.value.byteLength;
                    }
                } finally { await reader.cancel(); reader.releaseLock(); }
                const manifest = JSON.parse(new TextDecoder('utf-8', {fatal: true}).decode(bytes.subarray(0, size)));
                if (!manifest || typeof manifest !== 'object' || Array.isArray(manifest) ||
                    typeof manifest.buildId !== 'string' || !/^[a-f0-9]{16,64}$/.test(manifest.buildId) ||
                    controller.signal.aborted) throw new Error();
                done(true, manifest.buildId);
            } catch { done(false, ''); }
            finally { clearTimeout(timer); controller.abort(); }
        })();
        return {abort: () => controller.abort()};
    }""",
)
private external fun requestWebBuildId(done: (Boolean, String) -> Unit): WebVersionRequest
