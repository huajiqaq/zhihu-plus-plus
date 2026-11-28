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
import com.github.zly2006.zhihu.i18n.ReaderStrings
import com.github.zly2006.zhihu.i18n.SettingsStrings
import com.github.zly2006.zhihu.platform.platformName
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberWebViewUrlOpener
import com.github.zly2006.zhihu.ui.ReaderDisplayFailure
import com.github.zly2006.zhihu.updater.UpdateStep
import com.github.zly2006.zhihu.updater.assetFor
import com.github.zly2006.zhihu.updater.describeUpdateFailure
import com.github.zly2006.zhihu.updater.downloadReleaseAsset
import com.github.zly2006.zhihu.updater.verifySha256
import com.github.zly2006.zhihu.updater.withUpdateHttpClient
import java.awt.Desktop
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

internal val desktopSystemUpdateState = MutableStateFlow<SystemUpdateState>(SystemUpdateState.NoUpdate)
private val desktopUpdateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private var desktopDownloadJob: Job? = null
private var desktopDownloadedFile: File? = null

@Composable
actual fun rememberSystemUpdateState(): StateFlow<SystemUpdateState> = desktopSystemUpdateState

@Composable
actual fun rememberSystemUpdateChecker(auto: Boolean): suspend () -> Unit {
    val settings = rememberSettingsStore()
    val accountStore = rememberZhihuAccountStore()
    return {
        checkSystemReleaseUpdate(
            engine = accountStore.engine,
            state = desktopSystemUpdateState,
            currentVersionName = desktopVersionName(),
            settings = settings,
            auto = auto,
            selectAsset = { release ->
                val os = System.getProperty("os.name").let {
                    when {
                        it.startsWith("Windows", ignoreCase = true) -> "Windows"
                        it.startsWith("Mac", ignoreCase = true) -> "macOS"
                        it.startsWith("Linux", ignoreCase = true) -> "Linux"
                        else -> it
                    }
                }
                release.assetFor(os, System.getProperty("os.arch"), nativeHost = false)
            },
        )
    }
}

@Composable
actual fun rememberSystemUpdateVersionSkipper(): (String) -> Unit {
    val settings = rememberSettingsStore()
    return { version ->
        if (desktopSystemUpdateState.value is SystemUpdateState.UpdateAvailable) {
            settings.putString(PREF_SKIPPED_VERSION, version)
            resetSystemUpdateState()
        }
    }
}

@Composable
actual fun rememberSystemUpdateDownloader(): suspend () -> Unit {
    val openWebUrl = rememberWebViewUrlOpener()
    val engine = rememberZhihuAccountStore().engine
    return download@{
        val before = desktopSystemUpdateState.value
        if (before !is SystemUpdateState.UpdateAvailable && before !is SystemUpdateState.Error) return@download
        val update = before.pendingUpdate ?: return@download
        if (update.opensExternally) {
            try {
                openWebUrl(update.downloadUrl)
                desktopSystemUpdateState.compareAndSet(before, update)
            } catch (error: Exception) {
                desktopSystemUpdateState.compareAndSet(before, SystemUpdateState.Error({ describeUpdateFailure(error) }, UpdateStep.Download, update))
            }
            return@download
        }
        val asset = update.asset ?: return@download
        val downloading = SystemUpdateState.Downloading(update)
        if (!desktopSystemUpdateState.compareAndSet(before, downloading)) return@download
        desktopDownloadJob = desktopUpdateScope.launch(start = CoroutineStart.UNDISPATCHED) {
            var temporary: File? = null
            try {
                yield()
                val directory = Files.createTempDirectory("zhihu-update-").toFile()
                val output = File(directory, asset.name)
                val partial = File(directory, "download.part")
                temporary = partial
                val hash = MessageDigest.getInstance("SHA-256")
                withUpdateHttpClient(engine) { client ->
                    partial.outputStream().use { stream ->
                        downloadReleaseAsset(client, asset) { bytes, count ->
                            stream.write(bytes, 0, count)
                            hash.update(bytes, 0, count)
                        }
                    }
                }
                asset.verifySha256(hash.digest().toHexString())
                currentCoroutineContext().ensureActive()
                Files.move(partial.toPath(), output.toPath(), StandardCopyOption.ATOMIC_MOVE)
                desktopDownloadedFile = output
                desktopSystemUpdateState.compareAndSet(downloading, SystemUpdateState.Downloaded(update, output.absolutePath))
            } catch (error: CancellationException) {
                desktopSystemUpdateState.compareAndSet(downloading, update)
                throw error
            } catch (error: Exception) {
                desktopSystemUpdateState.compareAndSet(downloading, SystemUpdateState.Error({ describeUpdateFailure(error) }, UpdateStep.Download, update))
            } finally {
                temporary?.delete()
                temporary?.parentFile?.delete()
            }
        }
    }
}

@Composable
actual fun rememberDownloadedSystemUpdateInstaller(): suspend () -> Unit = open@{
    val before = desktopSystemUpdateState.value
    if (before !is SystemUpdateState.Downloaded && !(before is SystemUpdateState.Error && before.step == UpdateStep.Install)) return@open
    val update = before.pendingUpdate ?: return@open
    val file = desktopDownloadedFile?.takeIf { it.isFile }
    if (file == null) {
        desktopSystemUpdateState.compareAndSet(before, SystemUpdateState.Error({ SettingsStrings.updateFileMissing }, UpdateStep.Download, update))
        return@open
    }
    try {
        // 只展示真实下载位置，安装、替换和执行交给用户及系统，不自动运行下载的程序。
        withContext(Dispatchers.IO) { Desktop.getDesktop().open(file.parentFile) }
        desktopSystemUpdateState.compareAndSet(before, SystemUpdateState.Downloaded(update, file.absolutePath))
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        desktopSystemUpdateState.compareAndSet(before, SystemUpdateState.Error({ describeUpdateFailure(error) }, UpdateStep.Install, update))
    }
}

@Composable
actual fun rememberSystemUpdateCanceller(): () -> Unit = { desktopDownloadJob?.cancel() }

@Composable
actual fun rememberWebAppCacheAction(): suspend (WebAppCacheAction) -> Unit = { error("Web cache is unavailable on $platformName") }

actual fun resetSystemUpdateState() {
    val before = desktopSystemUpdateState.value
    if (before !is SystemUpdateState.Downloading && before != SystemUpdateState.Checking) {
        desktopSystemUpdateState.compareAndSet(before, SystemUpdateState.NoUpdate)
    }
}

actual fun setSystemUpdateError(message: String, step: UpdateStep) {
    val before = desktopSystemUpdateState.value
    if (before !is SystemUpdateState.Downloading) {
        desktopSystemUpdateState.compareAndSet(before, SystemUpdateState.Error(message, step, before.pendingUpdate))
    }
}

actual val isApkUpdateInstallSupported: Boolean = false

private const val PREF_SKIPPED_VERSION = "skippedVersion"

internal fun desktopVersionName(): String =
    System.getProperty("zhihu.version")?.takeIf { it.isNotBlank() }
        ?: SystemUpdateState::class.java
            .getResourceAsStream("/zhihu-version.txt")
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText().trim() }
            ?.takeIf { it.isNotBlank() }
        ?: throw ReaderDisplayFailure("没有找到构建时生成的版本信息 zhihu-version.txt") { ReaderStrings.versionMetadataMissing }

@Composable
actual fun rememberLicenseManifestReader(): suspend () -> String = remember {
    {
        withContext(Dispatchers.IO) {
            SystemUpdateState::class.java
                .getResourceAsStream("/aboutlibraries.json")
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                ?.takeIf { it.isNotBlank() }
                ?: throw ReaderDisplayFailure(LICENSE_MANIFEST_MISSING_MESSAGE) { ReaderStrings.licenseManifestMissing }
        }
    }
}

actual val isWebViewCustomFontSupported: Boolean = false

@Composable
actual fun WebViewCustomFontSettings(
    customFontName: String?,
    onCustomFontNameChange: (String?) -> Unit,
) {
    error("$platformName 暂不支持 WebView 自定义字体设置")
}
