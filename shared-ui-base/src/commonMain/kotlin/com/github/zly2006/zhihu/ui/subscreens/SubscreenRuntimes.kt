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
import androidx.compose.runtime.LaunchedEffect
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.isJvm
import com.github.zly2006.zhihu.platform.isNative
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.updater.GithubAsset
import com.github.zly2006.zhihu.updater.GithubRelease
import com.github.zly2006.zhihu.updater.SYSTEM_UPDATE_AUTO_CHECK_INTERVAL_MILLIS
import com.github.zly2006.zhihu.updater.SemanticVersion
import com.github.zly2006.zhihu.updater.UpdateStep
import com.github.zly2006.zhihu.updater.describeUpdateFailure
import com.github.zly2006.zhihu.updater.extractGithubReleaseNotes
import com.github.zly2006.zhihu.updater.fetchLatestZhihuRelease
import com.github.zly2006.zhihu.updater.fetchNightlyZhihuRelease
import com.github.zly2006.zhihu.updater.officialReleasePageUrl
import com.github.zly2006.zhihu.updater.withUpdateHttpClient
import io.ktor.client.engine.HttpClientEngine
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

expect val isWebViewCustomFontSupported: Boolean

@Composable
expect fun WebViewCustomFontSettings(
    customFontName: String?,
    onCustomFontNameChange: (String?) -> Unit,
)

sealed interface SystemUpdateState {
    data object NoUpdate : SystemUpdateState

    data object Checking : SystemUpdateState

    data object Latest : SystemUpdateState

    data object Unpublished : SystemUpdateState

    /** Web 缓存宿主的只读快照；更新和磁盘占用不再维护两套状态。 */
    data class Web(val snapshot: WebAppCacheSnapshot) : SystemUpdateState

    data class UpdateAvailable(
        val version: String,
        val isNightly: Boolean,
        val releaseNotes: String?,
        val downloadUrl: String,
        val cnDownloadUrl: String?,
        val opensExternally: Boolean,
        val asset: GithubAsset? = null,
        val releasePageUrl: String = RELEASES_PAGE_URL,
        /** iOS 的公开 IPA 未签名，只能交付文件，不能自动安装。 */
        val unsignedIos: Boolean = false,
    ) : SystemUpdateState

    data class Downloading(
        val update: UpdateAvailable,
    ) : SystemUpdateState

    data class Downloaded(
        val update: UpdateAvailable,
        val filePath: String? = null,
        val delivered: Boolean = false,
    ) : SystemUpdateState

    data class Error(
        private val messageProvider: () -> String,
        val step: UpdateStep = UpdateStep.Check,
        val update: UpdateAvailable? = null,
    ) : SystemUpdateState {
        constructor(message: String, step: UpdateStep = UpdateStep.Check, update: UpdateAvailable? = null) :
            this({ message }, step, update)

        val message: String get() = messageProvider()
    }
}

/** 字节数仅属于应用静态缓存，不是内存、业务数据或 NLP 模型占用。 */
@Serializable
data class WebAppCacheSnapshot(
    val schema: Int = 1,
    val mode: String = "production",
    val available: Boolean = false,
    val phase: String = "starting",
    val running: WebCachedVersion? = null,
    val cached: WebCachedVersion? = null,
    val cachedBytes: Long = 0,
    val coreBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val candidate: WebCachedVersion? = null,
    val suppressed: Boolean = false,
    val error: WebCacheError? = null,
) {
    val busy: Boolean get() = phase in setOf("starting", "checking", "downloading", "verifying", "clearing", "applying")
}

@Serializable
data class WebCachedVersion(
    val versionName: String,
    val versionCode: Long,
    val buildId: String,
    val coreBytes: Long = 0,
    val totalBytes: Long = 0,
    val downloaded: Boolean = false,
)

@Serializable
data class WebCacheError(val code: String)

enum class WebAppCacheAction { Clear, Restore, Apply, Cancel }

@Composable
expect fun rememberWebAppCacheAction(): suspend (WebAppCacheAction) -> Unit

@Composable
expect fun rememberSystemUpdateCanceller(): () -> Unit

val SystemUpdateState.pendingUpdate: SystemUpdateState.UpdateAvailable?
    get() = when (this) {
        is SystemUpdateState.UpdateAvailable -> this
        is SystemUpdateState.Downloading -> update
        is SystemUpdateState.Downloaded -> update
        is SystemUpdateState.Error -> update
        else -> null
    }

@Composable
expect fun rememberSystemUpdateState(): StateFlow<SystemUpdateState>

@Composable
expect fun rememberSystemUpdateChecker(auto: Boolean = false): suspend () -> Unit

/** 桌面及 Apple 宿主在 store provider 内挂一次；Android 与 Web 继续使用已有启动检查。 */
@Composable
fun SystemUpdateAutoCheck() {
    if (!isJvm && !isNative) return
    val accountStore = rememberZhihuAccountStore()
    val settings = rememberSettingsStore()
    val check = rememberSystemUpdateChecker(auto = true)
    LaunchedEffect(accountStore, settings) {
        try {
            check()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            setSystemUpdateError(describeUpdateFailure(error), UpdateStep.Check)
        }
    }
}

@Composable
expect fun rememberSystemUpdateVersionSkipper(): (String) -> Unit

@Composable
expect fun rememberSystemUpdateDownloader(): suspend () -> Unit

@Composable
expect fun rememberDownloadedSystemUpdateInstaller(): suspend () -> Unit

expect fun resetSystemUpdateState()

expect fun setSystemUpdateError(message: String, step: UpdateStep)

expect val isApkUpdateInstallSupported: Boolean

const val RELEASES_PAGE_URL = "https://github.com/zly2006/zhihu-plus-plus/releases"

internal suspend fun checkSystemReleaseUpdate(
    engine: HttpClientEngine,
    state: MutableStateFlow<SystemUpdateState>,
    currentVersionName: String,
    settings: SettingsStore,
    auto: Boolean = false,
    selectAsset: (GithubRelease) -> GithubAsset?,
    unsignedIos: Boolean = false,
) {
    val githubToken = settings.getStringOrNull("githubToken")?.takeIf { it.isNotBlank() }
    val now = Clock.System.now().toEpochMilliseconds()
    if (auto && now - settings.getLong("lastUpdateCheck", 0) < SYSTEM_UPDATE_AUTO_CHECK_INTERVAL_MILLIS && githubToken == null) return
    val before = state.value
    if (before != SystemUpdateState.NoUpdate && before != SystemUpdateState.Latest &&
        !(before is SystemUpdateState.Error && before.update == null)
    ) return
    if (!state.compareAndSet(before, SystemUpdateState.Checking)) return
    try {
        settings.putLong("lastUpdateCheck", now)
        val skippedVersion = if (auto) settings.getStringOrNull("skippedVersion") else null
        val checkNightly = !auto && settings.getBoolean("checkNightlyUpdates", false)
        val currentVersion = SemanticVersion.fromString(currentVersionName)
        val result = withUpdateHttpClient(engine) { client ->
            var release = fetchLatestZhihuRelease(client, githubToken)
            var version = release.tagName.takeIf { it.isNotBlank() }?.let(SemanticVersion::fromString)
            var isNightly = false
            if (checkNightly) {
                try {
                    val nightly = fetchNightlyZhihuRelease(client, githubToken)
                    if (nightly.tagName == "nightly" && selectAsset(nightly) != null) {
                        release = nightly
                        version = SemanticVersion(allComponents = listOf(999, 0, 0), preRelease = "nightly", build = "")
                        isNightly = true
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                }
            }
            if (version == null || version <= currentVersion) {
                SystemUpdateState.Latest
            } else if (version.toString() == skippedVersion) {
                SystemUpdateState.NoUpdate
            } else {
                val asset = selectAsset(release)
                val releasePage = release.officialReleasePageUrl()
                SystemUpdateState.UpdateAvailable(
                    version = version.toString(),
                    isNightly = isNightly,
                    releaseNotes = release.body?.let(::extractGithubReleaseNotes),
                    downloadUrl = asset?.browserDownloadUrl ?: releasePage,
                    cnDownloadUrl = asset?.cnDownloadUrl,
                    opensExternally = asset == null,
                    asset = asset,
                    releasePageUrl = releasePage,
                    unsignedIos = unsignedIos,
                )
            }
        }
        state.compareAndSet(SystemUpdateState.Checking, result)
    } catch (error: CancellationException) {
        state.compareAndSet(SystemUpdateState.Checking, SystemUpdateState.NoUpdate)
        throw error
    } catch (error: Exception) {
        state.compareAndSet(SystemUpdateState.Checking, SystemUpdateState.Error({ describeUpdateFailure(error) }, UpdateStep.Check))
    }
}
