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

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.i18n.ReaderStrings
import com.github.zly2006.zhihu.platform.androidUserMessageSink
import com.github.zly2006.zhihu.platform.rememberWebViewUrlOpener
import com.github.zly2006.zhihu.ui.ReaderDisplayFailure
import com.github.zly2006.zhihu.ui.components.AppIcon
import com.github.zly2006.zhihu.ui.components.AppOutlinedButton
import com.github.zly2006.zhihu.updater.UpdateManager
import com.github.zly2006.zhihu.updater.UpdateManager.UpdateState
import com.github.zly2006.zhihu.updater.UpdateStep
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

actual val isWebViewCustomFontSupported: Boolean = true

@Composable
actual fun WebViewCustomFontSettings(
    customFontName: String?,
    onCustomFontNameChange: (String?) -> Unit,
) {
    val context = LocalContext.current
    val userMessages = androidUserMessageSink(context)
    val fontFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString()
        val destFile = File(context.filesDir, "custom_font")
        val tempFile = File(context.filesDir, "custom_font.tmp")
        runCatching {
            val input = context.contentResolver.openInputStream(uri) ?: error(ReaderStrings.selectedFileUnreadable)
            input.use { source -> tempFile.outputStream().use { output -> source.copyTo(output) } }
            check(tempFile.renameTo(destFile)) { ReaderStrings.fontFileSaveFailed }
        }.onSuccess {
            onCustomFontNameChange(name)
            userMessages.showShortMessage(ReaderStrings.fontSelected)
        }.onFailure {
            tempFile.delete()
            userMessages.showLongMessage(ReaderStrings.fontSelectionFailed(it.message ?: it::class.simpleName.orEmpty()))
        }
    }
    Column(
        modifier = Modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            AppOutlinedButton(
                onClick = {
                    fontFilePicker.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream"))
                },
                modifier = Modifier.weight(1f),
            ) {
                AppIcon(Icons.Default.FolderOpen, contentDescription = null)
                Text(ReaderStrings.choose, modifier = Modifier.padding(start = 4.dp))
            }
            if (customFontName != null) {
                AppOutlinedButton(
                    onClick = {
                        File(context.filesDir, "custom_font").delete()
                        onCustomFontNameChange(null)
                        userMessages.showShortMessage(ReaderStrings.fontCleared)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    AppIcon(Icons.Default.Clear, contentDescription = null)
                    Text(ReaderStrings.clear, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

@Composable
actual fun rememberSystemUpdateState(): StateFlow<SystemUpdateState> {
    val scope = rememberCoroutineScope()
    return remember(scope) {
        UpdateManager.updateState.map { it.toSystemUpdateState() }.stateIn(
            scope,
            SharingStarted.Eagerly,
            UpdateManager.updateState.value.toSystemUpdateState(),
        )
    }
}

@Composable
actual fun rememberSystemUpdateChecker(auto: Boolean): suspend () -> Unit {
    val context = LocalContext.current
    val accountStore = rememberZhihuAccountStore()
    return {
        if (auto) UpdateManager.autoCheckForUpdate(context, accountStore.engine) else UpdateManager.checkForUpdate(context, accountStore.engine)
    }
}

@Composable
actual fun rememberSystemUpdateVersionSkipper(): (String) -> Unit {
    val context = LocalContext.current
    return { version -> UpdateManager.skipVersion(context, version) }
}

@Composable
actual fun rememberSystemUpdateDownloader(): suspend () -> Unit {
    val context = LocalContext.current
    val accountStore = rememberZhihuAccountStore()
    val openWebUrl = rememberWebViewUrlOpener()
    return { UpdateManager.downloadUpdate(context, accountStore.engine, openWebUrl::invoke) }
}

@Composable
actual fun rememberDownloadedSystemUpdateInstaller(): suspend () -> Unit {
    val context = LocalContext.current
    return { UpdateManager.installUpdate(context) }
}

actual fun resetSystemUpdateState() = UpdateManager.resetState()

@Composable
actual fun rememberSystemUpdateCanceller(): () -> Unit = UpdateManager::cancelDownload

@Composable
actual fun rememberWebAppCacheAction(): suspend (WebAppCacheAction) -> Unit = { error("Web cache is unavailable on Android") }

actual fun setSystemUpdateError(message: String, step: UpdateStep) = UpdateManager.reportFailure(message, step)

actual val isApkUpdateInstallSupported: Boolean = true

private fun UpdateState.toSystemUpdateState(): SystemUpdateState = when (this) {
    UpdateState.NoUpdate -> SystemUpdateState.NoUpdate
    UpdateState.Checking -> SystemUpdateState.Checking
    UpdateState.Latest -> SystemUpdateState.Latest
    is UpdateState.UpdateAvailable -> toSystemUpdate()
    is UpdateState.Downloading -> SystemUpdateState.Downloading(update.toSystemUpdate())
    is UpdateState.Downloaded -> SystemUpdateState.Downloaded(update.toSystemUpdate())
    is UpdateState.Error -> SystemUpdateState.Error({ message }, step, update?.toSystemUpdate())
}

private fun UpdateState.UpdateAvailable.toSystemUpdate() = SystemUpdateState.UpdateAvailable(
    version = version.toString(),
    isNightly = isNightly,
    releaseNotes = releaseNotes,
    downloadUrl = downloadUrl,
    cnDownloadUrl = cnDownloadUrl,
    opensExternally = opensExternally,
    asset = asset,
)

@Composable
actual fun rememberLicenseManifestReader(): suspend () -> String {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        {
            withContext(Dispatchers.IO) {
                /** 清单属于 app 变体资源，此模块没有其 R 类，需按资源名查找。 */
                @SuppressLint("DiscouragedApi")
                val id = context.resources.getIdentifier("aboutlibraries", "raw", context.packageName)
                if (id == 0) throw ReaderDisplayFailure(LICENSE_MANIFEST_MISSING_MESSAGE) { ReaderStrings.licenseManifestMissing }
                context.resources
                    .openRawResource(id)
                    .bufferedReader()
                    .use { it.readText() }
            }
        }
    }
}
