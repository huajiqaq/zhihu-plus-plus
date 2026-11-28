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

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.github.zly2006.zhihu.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.github.zly2006.zhihu.i18n.CommentsStrings
import com.github.zly2006.zhihu.navigation.isWebPageUrl
import com.github.zly2006.zhihu.platform.UserMessageSink
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemFailedToPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.darwin.NSObjectProtocol

@Composable
actual fun rememberVoiceCommentPlayer(): VoiceCommentPlayer {
    val messages = rememberUserMessageSink()
    val scope = rememberCoroutineScope()
    val player = remember(messages, scope) { AppleVoiceCommentPlayer(messages, scope) }
    DisposableEffect(player) { onDispose(player::stop) }
    return player
}

private class AppleVoiceCommentPlayer(
    private val messages: UserMessageSink,
    private val scope: CoroutineScope,
) : VoiceCommentPlayer {
    private var player: AVPlayer? = null
    private var releaseSession: (() -> Unit)? = null
    private val observers = mutableListOf<NSObjectProtocol>()
    private var statusJob: Job? = null
    override val isSupported = true
    override var playingUrl by mutableStateOf<String?>(null)
        private set

    override fun toggle(url: String) {
        val same = playingUrl == url
        stop()
        if (same) return
        val source = when {
            url.startsWith("/") -> NSURL.fileURLWithPath(url)
            isWebPageUrl(url) || url.startsWith("file://") -> NSURL.URLWithString(url)
            else -> null
        }
        if (source == null || source.user != null || source.password != null) {
            messages.showShortMessage(CommentsStrings.invalidVoiceUrl)
            return
        }
        releaseSession = acquireCommentPlaybackSession { stop() }
        if (releaseSession == null) {
            failed()
            return
        }
        val item = AVPlayerItem(uRL = source)
        val audio = AVPlayer(playerItem = item)
        player = audio
        playingUrl = url
        val notifications = NSNotificationCenter.defaultCenter
        observers += notifications.addObserverForName(AVPlayerItemDidPlayToEndTimeNotification, item, NSOperationQueue.mainQueue) {
            if (player === audio) stop()
        }
        observers += notifications.addObserverForName(AVPlayerItemFailedToPlayToEndTimeNotification, item, NSOperationQueue.mainQueue) {
            if (player === audio) failed()
        }
        statusJob = scope.launch {
            while (player === audio) {
                if (item.status == AVPlayerItemStatusFailed) {
                    failed()
                    break
                }
                delay(200)
            }
        }
        audio.play()
    }

    private fun failed() {
        stop()
        messages.showShortMessage(CommentsStrings.voicePlaybackFailed)
    }

    override fun stop() {
        statusJob?.cancel()
        statusJob = null
        observers.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
        observers.clear()
        player?.pause()
        player?.replaceCurrentItemWithPlayerItem(null)
        player = null
        playingUrl = null
        val release = releaseSession
        releaseSession = null
        release?.invoke()
    }
}
