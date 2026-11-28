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

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package com.github.zly2006.zhihu.ui.subscreens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.i18n.ReaderStrings
import com.github.zly2006.zhihu.i18n.SettingsStrings
import com.github.zly2006.zhihu.platform.LocalNativeFileDelivery
import com.github.zly2006.zhihu.platform.isLiteVariant
import com.github.zly2006.zhihu.platform.nativeIsDesktop
import com.github.zly2006.zhihu.platform.platformName
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberWebViewUrlOpener
import com.github.zly2006.zhihu.ui.rememberAppVersionInfo
import com.github.zly2006.zhihu.updater.UpdateStep
import com.github.zly2006.zhihu.updater.assetFor
import com.github.zly2006.zhihu.updater.describeUpdateFailure
import com.github.zly2006.zhihu.updater.downloadReleaseAsset
import com.github.zly2006.zhihu.updater.verifySha256
import com.github.zly2006.zhihu.updater.withUpdateHttpClient
import com.github.zly2006.zhihu.viewmodel.ArticleExportResult
import kotlin.native.Platform
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.CoreCrypto.CC_SHA256_CTX
import platform.CoreCrypto.CC_SHA256_Final
import platform.CoreCrypto.CC_SHA256_Init
import platform.CoreCrypto.CC_SHA256_Update
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

private val releasePageUpdateState = MutableStateFlow<SystemUpdateState>(SystemUpdateState.NoUpdate)
private val nativeUpdateScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
private var nativeDownloadJob: Job? = null
private var nativeDownloadedFile: String? = null

@Composable
actual fun rememberSystemUpdateState(): StateFlow<SystemUpdateState> = releasePageUpdateState

@Composable
actual fun rememberSystemUpdateChecker(auto: Boolean): suspend () -> Unit {
    val settings = rememberSettingsStore()
    val accountStore = rememberZhihuAccountStore()
    val version = rememberAppVersionInfo()
    return {
        checkSystemReleaseUpdate(
            engine = accountStore.engine,
            state = releasePageUpdateState,
            currentVersionName = version,
            settings = settings,
            auto = auto,
            selectAsset = { release ->
                val deviceBuild = nativeIsDesktop ||
                    (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleSupportedPlatforms") as? List<*>)?.contains("iPhoneOS") == true
                if (deviceBuild) release.assetFor(platformName, Platform.cpuArchitecture.name, nativeHost = true, lite = isLiteVariant) else null
            },
            unsignedIos = !nativeIsDesktop,
        )
    }
}

@Composable
actual fun rememberSystemUpdateVersionSkipper(): (String) -> Unit {
    val settings = rememberSettingsStore()
    return { version ->
        if (releasePageUpdateState.value is SystemUpdateState.UpdateAvailable) {
            settings.putString("skippedVersion", version)
            resetSystemUpdateState()
        }
    }
}

@Composable
actual fun rememberSystemUpdateDownloader(): suspend () -> Unit {
    val openWebUrl = rememberWebViewUrlOpener()
    val engine = rememberZhihuAccountStore().engine
    return download@{
        val before = releasePageUpdateState.value
        if (before !is SystemUpdateState.UpdateAvailable && before !is SystemUpdateState.Error) return@download
        val update = before.pendingUpdate ?: return@download
        if (update.opensExternally) {
            try {
                openWebUrl(update.downloadUrl)
                releasePageUpdateState.compareAndSet(before, update)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                releasePageUpdateState.compareAndSet(before, SystemUpdateState.Error({ describeUpdateFailure(error) }, UpdateStep.Download, update))
            }
            return@download
        }
        val asset = update.asset ?: return@download
        val downloading = SystemUpdateState.Downloading(update)
        if (!releasePageUpdateState.compareAndSet(before, downloading)) return@download
        nativeDownloadJob = nativeUpdateScope.launch(start = CoroutineStart.UNDISPATCHED) {
            var partial: String? = null
            try {
                val output = withContext(Dispatchers.Default) {
                    val directory = "${NSTemporaryDirectory().trimEnd('/')}/zhihu-update-${NSUUID().UUIDString}"
                    check(NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)) { SettingsStrings.updateFileInvalid }
                    val temporary = "$directory/download.part"
                    partial = temporary
                    withUpdateHttpClient(engine) { client ->
                        SystemFileSystem.sink(Path(temporary)).buffered().use { sink ->
                            downloadReleaseAsset(client, asset) { bytes, count -> sink.write(bytes, 0, count) }
                        }
                    }
                    if (asset.digest != null) asset.verifySha256(nativeUpdateSha256(temporary))
                    currentCoroutineContext().ensureActive()
                    "$directory/${asset.name}".also { SystemFileSystem.atomicMove(Path(temporary), Path(it)) }
                }
                nativeDownloadedFile = output
                releasePageUpdateState.compareAndSet(downloading, SystemUpdateState.Downloaded(update, output))
            } catch (error: CancellationException) {
                releasePageUpdateState.compareAndSet(downloading, update)
                throw error
            } catch (error: Exception) {
                releasePageUpdateState.compareAndSet(downloading, SystemUpdateState.Error({ describeUpdateFailure(error) }, UpdateStep.Download, update))
            } finally {
                partial?.let { NSFileManager.defaultManager.removeItemAtPath(it, null) }
            }
        }
    }
}

@Composable
actual fun rememberDownloadedSystemUpdateInstaller(): suspend () -> Unit {
    val delivery = LocalNativeFileDelivery.current
    return save@{
        val before = releasePageUpdateState.value
        if (before !is SystemUpdateState.Downloaded && !(before is SystemUpdateState.Error && before.step == UpdateStep.Install)) return@save
        val update = before.pendingUpdate ?: return@save
        val file = nativeDownloadedFile?.takeIf { SystemFileSystem.exists(Path(it)) }
        if (file == null) {
            releasePageUpdateState.compareAndSet(before, SystemUpdateState.Error({ SettingsStrings.updateFileMissing }, UpdateStep.Download, update))
            return@save
        }
        try {
            val host = checkNotNull(delivery) { SettingsStrings.updateDeliveryUnavailable }
            when (host.saveFile(file, image = false)) {
                is ArticleExportResult.Completed -> releasePageUpdateState.compareAndSet(before, SystemUpdateState.Downloaded(update, file, delivered = true))
                ArticleExportResult.Cancelled -> Unit
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            releasePageUpdateState.compareAndSet(before, SystemUpdateState.Error({ describeUpdateFailure(error) }, UpdateStep.Install, update))
        }
    }
}

private suspend fun nativeUpdateSha256(path: String): String = memScoped {
    val hash = alloc<CC_SHA256_CTX>()
    check(CC_SHA256_Init(hash.ptr) == 1)
    SystemFileSystem.source(Path(path)).buffered().use { source ->
        val bytes = ByteArray(64 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = source.readAtMostTo(bytes)
            if (count < 0) break
            if (count > 0) bytes.usePinned { check(CC_SHA256_Update(hash.ptr, it.addressOf(0), count.convert()) == 1) }
        }
    }
    val digest = ByteArray(32)
    digest.usePinned { check(CC_SHA256_Final(it.addressOf(0).reinterpret(), hash.ptr) == 1) }
    digest.toHexString()
}

@Composable
actual fun rememberSystemUpdateCanceller(): () -> Unit = { nativeDownloadJob?.cancel() }

@Composable
actual fun rememberWebAppCacheAction(): suspend (WebAppCacheAction) -> Unit = { error("Web cache is unavailable on $platformName") }

actual fun resetSystemUpdateState() {
    val before = releasePageUpdateState.value
    if (before !is SystemUpdateState.Downloading && before != SystemUpdateState.Checking) {
        releasePageUpdateState.compareAndSet(before, SystemUpdateState.NoUpdate)
    }
}

actual fun setSystemUpdateError(message: String, step: UpdateStep) {
    val before = releasePageUpdateState.value
    if (before !is SystemUpdateState.Downloading) {
        releasePageUpdateState.compareAndSet(before, SystemUpdateState.Error(message, step, before.pendingUpdate))
    }
}

actual val isApkUpdateInstallSupported: Boolean = false
actual val isWebViewCustomFontSupported: Boolean = false

@Composable
actual fun WebViewCustomFontSettings(customFontName: String?, onCustomFontNameChange: (String?) -> Unit) {
    Text(ReaderStrings.webviewFontUnavailable(platformName))
}
