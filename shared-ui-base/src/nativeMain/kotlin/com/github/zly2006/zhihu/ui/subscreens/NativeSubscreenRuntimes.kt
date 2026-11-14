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

package com.github.zly2006.zhihu.ui.subscreens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.platform.nativeIsDesktop
import com.github.zly2006.zhihu.platform.platformName
import com.github.zly2006.zhihu.platform.rememberExternalUrlOpener
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.ui.NativeArticleSpeechController
import com.github.zly2006.zhihu.ui.TtsState
import com.github.zly2006.zhihu.updater.UpdateStep
import com.github.zly2006.zhihu.updater.describeUpdateFailure
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager

private const val PREF_SKIPPED_VERSION = "skippedVersion"

private val nativeSystemUpdateState = MutableStateFlow<SystemUpdateState>(SystemUpdateState.NoUpdate)

@Composable
actual fun rememberSystemUpdateState(): StateFlow<SystemUpdateState> = nativeSystemUpdateState

@Composable
actual fun rememberSystemUpdateChecker(): suspend () -> Unit {
    val settings = rememberSettingsStore()
    val accountStore = rememberZhihuAccountStore()
    return {
        checkReleasePageUpdate(
            engine = accountStore.engine,
            state = nativeSystemUpdateState,
            currentVersionName = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "0.0.0",
            githubToken = settings.getStringOrNull("githubToken")?.takeIf { it.isNotBlank() },
            checkNightly = settings.getBoolean("checkNightlyUpdates", false),
        )
    }
}

@Composable
actual fun rememberSystemUpdateVersionSkipper(): (String) -> Unit {
    val settings = rememberSettingsStore()
    return { version ->
        settings.putString(PREF_SKIPPED_VERSION, version)
        nativeSystemUpdateState.value = SystemUpdateState.NoUpdate
    }
}

@Composable
actual fun rememberSystemUpdateDownloader(): suspend () -> Unit {
    val openExternalUrl = rememberExternalUrlOpener()
    return download@{
        val update = nativeSystemUpdateState.value.pendingUpdate ?: return@download
        try {
            require(update.downloadUrl.isNotBlank()) { "下载链接为空" }
            openExternalUrl(update.downloadUrl)
            nativeSystemUpdateState.value = update
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            nativeSystemUpdateState.value = SystemUpdateState.Error(describeUpdateFailure(error), UpdateStep.Download, update)
        }
    }
}

@Composable
actual fun rememberDownloadedSystemUpdateInstaller(): suspend () -> Unit = {
    nativeSystemUpdateState.value = SystemUpdateState.Error(
        "$platformName 暂不支持 APK 更新安装",
        UpdateStep.Install,
        nativeSystemUpdateState.value.pendingUpdate,
    )
}

actual fun resetSystemUpdateState() {
    nativeSystemUpdateState.value = SystemUpdateState.NoUpdate
}

actual fun setSystemUpdateError(message: String, step: UpdateStep) {
    nativeSystemUpdateState.value = SystemUpdateState.Error(message, step, nativeSystemUpdateState.value.pendingUpdate)
}

actual val isApkUpdateInstallSupported: Boolean = false

@Composable
actual fun rememberDeveloperInfo(): DeveloperInfoSnapshot =
    DeveloperInfoSnapshot(
        networkStatus = if (nativeIsDesktop) {
            "网络状态：桌面端使用系统网络"
        } else {
            "网络状态：iOS 端使用系统网络"
        },
        ttsState = if (nativeIsDesktop) NativeArticleSpeechController.currentState else TtsState.Uninitialized,
        currentTtsEngineLabel = if (nativeIsDesktop) "macOS 系统语音" else "未初始化",
        availableTtsEngineLabels = if (nativeIsDesktop) listOf("NSSpeechSynthesizer") else emptyList(),
    )

@Composable
actual fun rememberLicenseManifestReader(): suspend () -> String = remember {
    { loadNativeAboutLibrariesJson() ?: error(LICENSE_MANIFEST_MISSING_MESSAGE) }
}

@OptIn(ExperimentalForeignApi::class)
private fun loadNativeAboutLibrariesJson(): String? {
    val resourcePath = NSBundle.mainBundle.resourcePath?.let { "$it/aboutlibraries.json" } ?: return null
    val data = NSFileManager.defaultManager.contentsAtPath(resourcePath) ?: return null
    val bytes = data.bytes?.reinterpret<ByteVar>()?.readBytes(data.length.toInt()) ?: return null
    return bytes.decodeToString().takeIf { it.isNotBlank() }
}

actual val isWebViewCustomFontSupported: Boolean = false

@Composable
actual fun WebViewCustomFontSettings(
    customFontName: String?,
    onCustomFontNameChange: (String?) -> Unit,
) {
    error("$platformName 暂不支持 WebView 自定义字体设置")
}
