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
import com.github.zly2006.zhihu.updater.SemanticVersion
import com.github.zly2006.zhihu.updater.UpdateStep
import com.github.zly2006.zhihu.updater.describeUpdateFailure
import com.github.zly2006.zhihu.updater.extractGithubReleaseNotes
import com.github.zly2006.zhihu.updater.fetchLatestZhihuRelease
import com.github.zly2006.zhihu.updater.fetchNightlyZhihuRelease
import com.github.zly2006.zhihu.updater.withUpdateHttpClient
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

    data class UpdateAvailable(
        val version: String,
        val isNightly: Boolean,
        val releaseNotes: String?,
        val downloadUrl: String,
        val cnDownloadUrl: String?,
        val opensExternally: Boolean,
    ) : SystemUpdateState

    data class Downloading(
        val update: UpdateAvailable,
    ) : SystemUpdateState

    data class Downloaded(
        val update: UpdateAvailable,
    ) : SystemUpdateState

    data class Error(
        val message: String,
        val step: UpdateStep = UpdateStep.Check,
        val update: UpdateAvailable? = null,
    ) : SystemUpdateState
}

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
expect fun rememberSystemUpdateChecker(): suspend () -> Unit

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

internal suspend fun checkReleasePageUpdate(
    engine: HttpClientEngine,
    state: MutableStateFlow<SystemUpdateState>,
    currentVersionName: String,
    githubToken: String?,
    checkNightly: Boolean,
) {
    state.value = SystemUpdateState.Checking
    try {
        val currentVersion = SemanticVersion.fromString(currentVersionName)
        state.value = withUpdateHttpClient(engine) { client ->
            var release = fetchLatestZhihuRelease(client, githubToken)
            var version = release.tagName.takeIf { it.isNotBlank() }?.let(SemanticVersion::fromString)
            var isNightly = false
            if (checkNightly) {
                try {
                    val nightly = fetchNightlyZhihuRelease(client, githubToken)
                    if (nightly.tagName == "nightly") {
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
            } else {
                SystemUpdateState.UpdateAvailable(
                    version = version.toString(),
                    isNightly = isNightly,
                    releaseNotes = release.body?.let(::extractGithubReleaseNotes),
                    downloadUrl = release.htmlUrl?.takeIf(String::isNotBlank) ?: RELEASES_PAGE_URL,
                    cnDownloadUrl = release.assets.firstOrNull()?.cnDownloadUrl,
                    opensExternally = true,
                )
            }
        }
    } catch (error: CancellationException) {
        state.value = SystemUpdateState.NoUpdate
        throw error
    } catch (error: Exception) {
        state.value = SystemUpdateState.Error(describeUpdateFailure(error), UpdateStep.Check)
    }
}
