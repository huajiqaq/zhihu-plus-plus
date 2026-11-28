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

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.i18n.ReaderStrings
import com.github.zly2006.zhihu.platform.platformName
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberWebViewUrlOpener
import com.github.zly2006.zhihu.ui.ReaderDisplayFailure
import com.github.zly2006.zhihu.ui.rememberAppVersionInfo
import com.github.zly2006.zhihu.updater.UpdateStep
import com.github.zly2006.zhihu.updater.describeUpdateFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val releasePageUpdateState = MutableStateFlow<SystemUpdateState>(SystemUpdateState.NoUpdate)

@Composable
actual fun rememberSystemUpdateState(): StateFlow<SystemUpdateState> = releasePageUpdateState

@Composable
actual fun rememberSystemUpdateChecker(): suspend () -> Unit {
    val settings = rememberSettingsStore()
    val accountStore = rememberZhihuAccountStore()
    val version = rememberAppVersionInfo()
    return {
        checkReleasePageUpdate(
            engine = accountStore.engine,
            state = releasePageUpdateState,
            currentVersionName = version,
            githubToken = settings.getStringOrNull("githubToken")?.takeIf { it.isNotBlank() },
            checkNightly = settings.getBoolean("checkNightlyUpdates", false),
        )
    }
}

@Composable
actual fun rememberSystemUpdateVersionSkipper(): (String) -> Unit {
    val settings = rememberSettingsStore()
    return { version ->
        settings.putString("skippedVersion", version)
        resetSystemUpdateState()
    }
}

@Composable
actual fun rememberSystemUpdateDownloader(): suspend () -> Unit {
    val openWebUrl = rememberWebViewUrlOpener()
    return download@{
        val update = releasePageUpdateState.value.pendingUpdate ?: return@download
        try {
            if (update.downloadUrl.isBlank()) throw ReaderDisplayFailure("下载链接为空") { ReaderStrings.downloadUrlMissing }
            openWebUrl(update.downloadUrl)
            releasePageUpdateState.value = update
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            releasePageUpdateState.value = SystemUpdateState.Error({ describeUpdateFailure(error) }, UpdateStep.Download, update)
        }
    }
}

@Composable
actual fun rememberDownloadedSystemUpdateInstaller(): suspend () -> Unit = {
    releasePageUpdateState.value = SystemUpdateState.Error(
        { ReaderStrings.apkInstallUnavailable(platformName) },
        UpdateStep.Install,
        releasePageUpdateState.value.pendingUpdate,
    )
}

actual fun resetSystemUpdateState() {
    releasePageUpdateState.value = SystemUpdateState.NoUpdate
}

actual fun setSystemUpdateError(message: String, step: UpdateStep) {
    releasePageUpdateState.value = SystemUpdateState.Error(message, step, releasePageUpdateState.value.pendingUpdate)
}

actual val isApkUpdateInstallSupported: Boolean = false
actual val isWebViewCustomFontSupported: Boolean = false

@Composable
actual fun WebViewCustomFontSettings(customFontName: String?, onCustomFontNameChange: (String?) -> Unit) {
    Text(ReaderStrings.webviewFontUnavailable(platformName))
}
