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

@file:OptIn(ExperimentalWasmJsInterop::class)

package com.github.zly2006.zhihu.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.DialogProperties
import com.github.zly2006.zhihu.i18n.CommentsStrings
import com.github.zly2006.zhihu.navigation.isWebPageUrl
import com.github.zly2006.zhihu.platform.AudioActivityCoordinator
import com.github.zly2006.zhihu.platform.AudioActivityLease
import com.github.zly2006.zhihu.platform.UserMessageSink
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import org.w3c.dom.Audio

internal actual val commentDialogProperties = DialogProperties(usePlatformDefaultWidth = false)

@Composable
actual fun rememberVoiceCommentPlayer(): VoiceCommentPlayer {
    val messages = rememberUserMessageSink()
    val player = remember(messages) { WebVoicePlayer(messages) }
    DisposableEffect(player) { onDispose(player::stop) }
    return player
}

private class WebVoicePlayer(
    private val messages: UserMessageSink,
) : VoiceCommentPlayer {
    private var audio: Audio? = null
    private var audioLease: AudioActivityLease? = null
    override val isSupported: Boolean = true
    override var playingUrl by mutableStateOf<String?>(null)
        private set

    override fun toggle(url: String) {
        val wasPlaying = playingUrl == url
        stop()
        if (wasPlaying) return
        if (!isWebPageUrl(url) && url !in recordedWebVoiceUrls) {
            messages.showShortMessage(CommentsStrings.invalidVoiceUrl)
            return
        }
        audioLease = AudioActivityCoordinator.acquirePlayback(onInterrupted = { stop() })
        if (audioLease == null) {
            messages.showShortMessage(CommentsStrings.voicePlaybackFailed)
            return
        }
        val player = Audio(url)
        audio = player
        playingUrl = url
        val failed = {
            if (audio === player) {
                stop()
                messages.showShortMessage(CommentsStrings.browserVoiceFailed)
            }
        }
        player.onended = { if (audio === player) stop() }
        player.onerror = { _, _, _, _, _ ->
            failed()
            null
        }
        player.play().catch {
            failed()
            null
        }
    }

    override fun stop() {
        audio?.let {
            it.onended = null
            it.onerror = null
            it.pause()
            it.removeAttribute("src")
            it.load()
        }
        audio = null
        playingUrl = null
        audioLease?.close()
        audioLease = null
    }
}
