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

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.github.zly2006.zhihu.platform.AudioActivityCoordinator

/** Android 主入口仍使用原有 VideoPlayerActivity；共享弹层保持正式平台实现。 */
@Composable
internal actual fun PlatformVideoPlayer(url: String, position: VideoPlaybackPosition, active: Boolean, onFailure: () -> Unit, modifier: Modifier) {
    val failure = rememberUpdatedState(onFailure)
    val currentActive = rememberUpdatedState(active)
    val holder = remember(url) { arrayOfNulls<VideoView>(1) }
    DisposableEffect(url) {
        val lease = AudioActivityCoordinator.acquirePlayback(onInterrupted = { holder[0]?.pause(); position.playing = false })
        if (lease == null) failure.value()
        onDispose { lease?.close() }
    }
    LaunchedEffect(active) { holder[0]?.let { if (active && position.playing) it.start() else it.pause() } }
    AndroidView(
        factory = { context ->
            VideoView(context).apply {
                holder[0] = this
                setMediaController(MediaController(context).also { it.setAnchorView(this) })
                setOnErrorListener { _, _, _ -> failure.value(); true }
                setOnPreparedListener {
                    seekTo((position.seconds * 1_000).toInt())
                    if (currentActive.value && position.playing) start()
                }
                setVideoPath(url)
            }
        },
        modifier = modifier,
        onRelease = { view ->
            position.seconds = view.currentPosition / 1_000.0
            position.playing = view.isPlaying
            view.stopPlayback()
            if (holder[0] === view) holder[0] = null
        },
    )
}
