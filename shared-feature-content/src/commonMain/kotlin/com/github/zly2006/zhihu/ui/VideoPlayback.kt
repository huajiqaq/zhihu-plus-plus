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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.zly2006.zhihu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.fetchHighestQualityZhihuVideoUrl
import com.github.zly2006.zhihu.i18n.ContentStrings
import com.github.zly2006.zhihu.navigation.Video
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppCompactTopBar
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.util.signZhihuFetchRequest
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class VideoPlaybackPosition {
    var seconds = 0.0
    var playing = true
    var rate = 1.0
    var muted = false
}

private class VideoRequest(val video: Video, val contentId: String, val contentType: String) {
    val position = VideoPlaybackPosition()
}

/** 宿主提供内容来源，媒体弹层独立持有解析请求、当前账号和播放恢复状态。 */
@Composable
fun rememberVideoPlaybackOpener(): (Video, String, String) -> Unit {
    val account = rememberZhihuAccountStore()
    val owner = account.client
    var request by remember(owner) { mutableStateOf<VideoRequest?>(null) }
    request?.let { target ->
        key(target) {
            var url by remember { mutableStateOf<String?>(null) }
            var failed by remember { mutableStateOf(false) }
            var retry by remember { mutableIntStateOf(0) }
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
            DisposableEffect(lifecycle) {
                val observer = LifecycleEventObserver { _, _ -> foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }
            LaunchedEffect(target, retry) {
                failed = false
                try {
                    check(account.client === owner)
                    val result = owner.withAuthenticatedClient { client, cookies ->
                        fetchHighestQualityZhihuVideoUrl(client, target.video.id.toString(), target.contentId, target.contentType, cookies["_xsrf"]) {
                            if (!account.usesBrowserSession) signZhihuFetchRequest(cookies)
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    check(account.client === owner)
                    val parsed = result?.let(::Url)
                    check(parsed != null && parsed.protocol.name == "https" && parsed.user == null && parsed.password == null)
                    url = result
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    failed = true
                }
            }
            Dialog(onDismissRequest = { request = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Column(Modifier.fillMaxSize().background(Color.Black)) {
                    AppCompactTopBar(title = ContentStrings.videoPlayerTitle, onBack = { request = null })
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (failed) {
                            Column(Modifier.padding(AppDimens.Space16), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppDimens.Space12)) {
                                Text(ContentStrings.videoPlaybackFailed, color = Color.White)
                                AppButton(text = ContentStrings.retry, onClick = { url = null; retry++ })
                            }
                        } else url?.let { media ->
                            PlatformVideoPlayer(media, target.position, foreground, { failed = true }, Modifier.fillMaxSize())
                        } ?: AppSpinner()
                    }
                }
            }
        }
    }
    return remember(owner) {
        { video, contentId, contentType ->
            if (account.client === owner && video.id > 0 && contentId.toLongOrNull()?.let { it > 0 } == true && contentType in setOf("answer", "article", "question")) {
                request = VideoRequest(video, contentId, contentType)
            }
        }
    }
}

@Composable
internal expect fun PlatformVideoPlayer(
    url: String,
    position: VideoPlaybackPosition,
    active: Boolean,
    onFailure: () -> Unit,
    modifier: Modifier,
)
