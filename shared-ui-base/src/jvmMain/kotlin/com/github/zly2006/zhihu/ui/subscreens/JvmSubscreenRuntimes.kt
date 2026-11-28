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
import com.github.zly2006.zhihu.platform.platformName
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberWebViewUrlOpener
import com.github.zly2006.zhihu.ui.ReaderDisplayFailure
import com.github.zly2006.zhihu.updater.UpdateStep
import com.github.zly2006.zhihu.updater.describeUpdateFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

internal val desktopSystemUpdateState = MutableStateFlow<SystemUpdateState>(SystemUpdateState.NoUpdate)

@Composable
actual fun rememberSystemUpdateState(): StateFlow<SystemUpdateState> = desktopSystemUpdateState

@Composable
actual fun rememberSystemUpdateChecker(): suspend () -> Unit {
    val settings = rememberSettingsStore()
    val accountStore = rememberZhihuAccountStore()
    return {
        checkReleasePageUpdate(
            engine = accountStore.engine,
            state = desktopSystemUpdateState,
            currentVersionName = desktopVersionName(),
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
        desktopSystemUpdateState.value = SystemUpdateState.NoUpdate
    }
}

@Composable
actual fun rememberSystemUpdateDownloader(): suspend () -> Unit {
    val openWebUrl = rememberWebViewUrlOpener()
    return download@{
        val update = desktopSystemUpdateState.value.pendingUpdate ?: return@download
        try {
            if (update.downloadUrl.isBlank()) throw ReaderDisplayFailure("下载链接为空") { ReaderStrings.downloadUrlMissing }
            openWebUrl(update.downloadUrl)
            desktopSystemUpdateState.value = update
        } catch (error: Exception) {
            desktopSystemUpdateState.value = SystemUpdateState.Error({ describeUpdateFailure(error) }, UpdateStep.Download, update)
        }
    }
}

@Composable
actual fun rememberDownloadedSystemUpdateInstaller(): suspend () -> Unit = {
    desktopSystemUpdateState.value = SystemUpdateState.Error(
        { ReaderStrings.apkInstallUnavailable(platformName) },
        UpdateStep.Install,
        desktopSystemUpdateState.value.pendingUpdate,
    )
}

actual fun resetSystemUpdateState() {
    desktopSystemUpdateState.value = SystemUpdateState.NoUpdate
}

actual fun setSystemUpdateError(message: String, step: UpdateStep) {
    desktopSystemUpdateState.value = SystemUpdateState.Error(message, step, desktopSystemUpdateState.value.pendingUpdate)
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
