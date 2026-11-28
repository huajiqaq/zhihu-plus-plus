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

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.github.zly2006.zhihu.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.github.zly2006.zhihu.platform.AudioActivityCoordinator
import kotlinx.coroutines.delay
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.currentTime
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVKit.AVPlayerView
import platform.AppKit.NSApplication
import platform.AppKit.NSView
import platform.CoreGraphics.CGRectGetMaxY
import platform.CoreGraphics.CGRectGetMinX
import platform.CoreGraphics.CGRectMake
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSURL

@Composable
internal actual fun PlatformVideoPlayer(url: String, position: VideoPlaybackPosition, active: Boolean, onFailure: () -> Unit, modifier: Modifier) {
    val failure = rememberUpdatedState(onFailure)
    val currentActive = rememberUpdatedState(active)
    val item = remember(url) { AVPlayerItem(uRL = checkNotNull(NSURL.URLWithString(url))) }
    val player = remember(item) { AVPlayer(playerItem = item) }
    val view = remember(player) { AVPlayerView(CGRectMake(0.0, 0.0, 0.0, 0.0)).apply { this.player = player } }
    val parent = remember(view) { arrayOfNulls<NSView>(1) }
    DisposableEffect(player) {
        val lease = AudioActivityCoordinator.acquirePlayback(onInterrupted = { player.pause(); position.playing = false })
        if (lease == null) failure.value()
        player.seekToTime(CMTimeMakeWithSeconds(position.seconds, 600))
        onDispose {
            position.seconds = CMTimeGetSeconds(player.currentTime()).takeIf { it.isFinite() } ?: position.seconds
            player.pause()
            view.player = null
            player.replaceCurrentItemWithPlayerItem(null)
            view.removeFromSuperview()
            parent[0] = null
            lease?.close()
        }
    }
    LaunchedEffect(player, active) { if (active && position.playing) player.play() else player.pause() }
    LaunchedEffect(player) {
        while (true) {
            if (item.status == AVPlayerItemStatusFailed) { failure.value(); break }
            val seconds = CMTimeGetSeconds(player.currentTime())
            if (seconds.isFinite()) position.seconds = seconds
            if (currentActive.value) position.playing = player.rate > 0f
            delay(200)
        }
    }
    Spacer(modifier.onGloballyPositioned { coordinates ->
        val window = parent[0]?.window ?: NSApplication.sharedApplication.keyWindow
        val container = parent[0] ?: window?.contentView
        if (window == null || container == null) {
            failure.value()
        } else {
            if (parent[0] == null) { parent[0] = container; container.addSubview(view) }
            val rect = coordinates.boundsInWindow()
            val scale = window.backingScaleFactor
            view.setFrame(CGRectMake(
                CGRectGetMinX(container.bounds) + rect.left / scale,
                CGRectGetMaxY(container.bounds) - rect.bottom / scale,
                rect.width / scale,
                rect.height / scale,
            ))
        }
    })
}
