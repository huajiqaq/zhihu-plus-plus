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

package com.github.zly2006.zhihu.updater

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.github.zly2006.zhihu.i18n.ReaderStrings
import com.github.zly2006.zhihu.platform.androidSettingsStore
import com.github.zly2006.zhihu.platform.isLiteVariant
import com.github.zly2006.zhihu.ui.ReaderDisplayFailure
import com.github.zly2006.zhihu.util.raiseForStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

object UpdateManager {
    private const val AUTO_CHECK_INTERVAL_MILLIS = 3 * 60 * 60 * 1000L

    private const val PREF_SKIPPED_VERSION = "skippedVersion"
    private const val PREF_LAST_UPDATE_CHECK = "lastUpdateCheck"

    sealed class UpdateState {
        object NoUpdate : UpdateState()

        object Checking : UpdateState()

        object Latest : UpdateState()

        data class UpdateAvailable(
            val version: SemanticVersion,
            val isNightly: Boolean = false,
            val releaseNotes: String?,
            val downloadUrl: String,
            val cnDownloadUrl: String?,
            val opensExternally: Boolean = false,
        ) : UpdateState()

        data class Downloading(
            val update: UpdateAvailable,
        ) : UpdateState()

        data class Downloaded(
            val update: UpdateAvailable,
        ) : UpdateState()

        data class Error(
            private val messageProvider: () -> String,
            val step: UpdateStep = UpdateStep.Check,
            val update: UpdateAvailable? = null,
        ) : UpdateState() {
            constructor(message: String, step: UpdateStep = UpdateStep.Check, update: UpdateAvailable? = null) :
                this({ message }, step, update)

            val message: String get() = messageProvider()
        }
    }

    val updateState = MutableStateFlow<UpdateState>(UpdateState.NoUpdate)

    /** 下载跟随进程而不是页面：离开更新页不会丢掉下了一半的安装包。 */
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadInProgress = AtomicBoolean(false)

    private fun getGitHubToken(context: Context): String? = androidSettingsStore(context).getStringOrNull("githubToken")?.takeIf { it.isNotBlank() }

    private fun Context.versionName(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrNull() ?: "0.0.0"

    private fun Context.updateApkFile() = File(applicationContext.cacheDir, "update.apk")

    fun skipVersion(context: Context, version: String) {
        androidSettingsStore(context).putString(PREF_SKIPPED_VERSION, version)
        updateState.value = UpdateState.NoUpdate
    }

    private fun shouldPerformAutoCheck(context: Context): Boolean {
        val settings = androidSettingsStore(context)
        return (System.currentTimeMillis() - settings.getLong(PREF_LAST_UPDATE_CHECK, 0)) >= AUTO_CHECK_INTERVAL_MILLIS
    }

    suspend fun autoCheckForUpdate(context: Context, engine: HttpClientEngine): Boolean {
        if (!shouldPerformAutoCheck(context) && getGitHubToken(context) == null) return false
        Log.i("UpdateManager", "Performing auto update check")
        runUpdateCheck(
            context,
            engine,
            checkNightly = false,
            skippedVersion = androidSettingsStore(context).getStringOrNull(PREF_SKIPPED_VERSION),
        )
        return updateState.value is UpdateState.UpdateAvailable
    }

    suspend fun checkForUpdate(context: Context, engine: HttpClientEngine) = runUpdateCheck(
        context,
        engine,
        checkNightly = androidSettingsStore(context).getBoolean("checkNightlyUpdates", false),
        skippedVersion = null,
    )

    private suspend fun runUpdateCheck(
        context: Context,
        engine: HttpClientEngine,
        checkNightly: Boolean,
        skippedVersion: String?,
    ) {
        val before = updateState.value
        if (before != UpdateState.NoUpdate &&
            before != UpdateState.Latest &&
            !(before is UpdateState.Error && before.update == null)
        ) {
            return
        }
        if (!updateState.compareAndSet(before, UpdateState.Checking)) return
        androidSettingsStore(context).putLong(PREF_LAST_UPDATE_CHECK, System.currentTimeMillis())
        try {
            val update = withUpdateHttpClient(engine) { client -> findUpdate(context, client, checkNightly) }
            val result = when {
                update == null -> UpdateState.Latest
                update.version.toString() == skippedVersion -> UpdateState.NoUpdate
                else -> update
            }
            updateState.compareAndSet(UpdateState.Checking, result)
        } catch (e: CancellationException) {
            updateState.compareAndSet(UpdateState.Checking, UpdateState.NoUpdate)
            throw e
        } catch (e: Exception) {
            Log.e("UpdateManager", "Error checking for updates", e)
            updateState.compareAndSet(UpdateState.Checking, UpdateState.Error({ describeUpdateFailure(e) }, UpdateStep.Check))
        }
    }

    private suspend fun findUpdate(context: Context, client: HttpClient, checkNightly: Boolean): UpdateState.UpdateAvailable? {
        val currentVersion = SemanticVersion.fromString(context.versionName())
        val token = getGitHubToken(context)
        var release = fetchLatestZhihuRelease(client, token)
        var version = release.tagName.takeIf { it.isNotBlank() }?.let(SemanticVersion::fromString)
        var isNightly = false
        var downloadInfo: AndroidReleaseDownloadInfo? = null
        if (checkNightly) {
            try {
                val nightly = fetchNightlyZhihuRelease(client, token)
                if (nightly.tagName == "nightly") {
                    downloadInfo = nightly.extractAndroidDownloadInfo(isLiteVariant)
                    release = nightly
                    version = SemanticVersion(allComponents = listOf(999, 0, 0), preRelease = "nightly", build = "")
                    isNightly = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Nightly 查不到或没有安装包时，整体沿用正式版的结果，不把 Nightly 版本号和正式版链接拼在一起。
                Log.e("UpdateManager", "Failed to check nightly release", e)
            }
        }
        if (version == null || version <= currentVersion) return null
        val info = downloadInfo ?: release.extractAndroidDownloadInfo(isLiteVariant)
        return UpdateState.UpdateAvailable(
            version = version,
            isNightly = isNightly,
            releaseNotes = release.body?.let(::extractGithubReleaseNotes),
            downloadUrl = info.browserDownloadUrl,
            cnDownloadUrl = info.cnDownloadUrl,
            opensExternally = info.opensExternally,
        )
    }

    fun downloadUpdate(context: Context, engine: HttpClientEngine, openDownloadPage: (String) -> Unit) {
        val update = when (val state = updateState.value) {
            is UpdateState.UpdateAvailable -> state
            is UpdateState.Error -> state.update
            else -> null
        } ?: return
        if (update.opensExternally) {
            try {
                openDownloadPage(update.downloadUrl)
                updateState.value = update
            } catch (e: Exception) {
                updateState.value = UpdateState.Error({ describeUpdateFailure(e) }, UpdateStep.Download, update)
            }
            return
        }
        val apkFile = context.updateApkFile()
        val tempFile = File(apkFile.parentFile, "${apkFile.name}.tmp")
        if (!downloadInProgress.compareAndSet(false, true)) return
        updateState.value = UpdateState.Downloading(update)
        downloadScope.launch {
            updateState.value = try {
                withUpdateHttpClient(engine) { client ->
                    client.prepareGet(update.downloadUrl).execute { response ->
                        response.raiseForStatus()
                        val channel = response.bodyAsChannel()
                        tempFile.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val bytesRead = channel.readAvailable(buffer)
                                if (bytesRead == -1) break
                                output.write(buffer, 0, bytesRead)
                            }
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                if (!tempFile.renameTo(apkFile)) throw ReaderDisplayFailure("无法保存安装包") { ReaderStrings.apkSaveFailed }
                UpdateState.Downloaded(update)
            } catch (e: CancellationException) {
                updateState.value = update
                throw e
            } catch (e: Exception) {
                Log.e("UpdateManager", "Failed to download update", e)
                UpdateState.Error({ describeUpdateFailure(e) }, UpdateStep.Download, update)
            } finally {
                try {
                    tempFile.delete()
                } finally {
                    downloadInProgress.set(false)
                }
            }
        }
    }

    fun installUpdate(context: Context) {
        val update = when (val state = updateState.value) {
            is UpdateState.Downloaded -> state.update
            is UpdateState.Error if (state.step == UpdateStep.Install) -> state.update
            else -> null
        } ?: return
        val apkFile = context.updateApkFile()
        if (!apkFile.isFile) {
            updateState.value = UpdateState.Error({ ReaderStrings.apkMissing }, UpdateStep.Download, update)
            return
        }
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", apkFile)
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                },
            )
            updateState.value = UpdateState.Downloaded(update)
        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to start installer", e)
            updateState.value = UpdateState.Error({ describeUpdateFailure(e) }, UpdateStep.Install, update)
        }
    }

    fun reportFailure(message: String, step: UpdateStep) {
        val update = when (val state = updateState.value) {
            is UpdateState.UpdateAvailable -> state
            is UpdateState.Downloading -> state.update
            is UpdateState.Downloaded -> state.update
            is UpdateState.Error -> state.update
            else -> null
        }
        updateState.value = UpdateState.Error(message, step, update)
    }
}
