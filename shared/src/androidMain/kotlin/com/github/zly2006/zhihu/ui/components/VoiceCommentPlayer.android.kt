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

package com.github.zly2006.zhihu.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberVoiceCommentPlayer(): VoiceCommentPlayer {
    val context = LocalContext.current.applicationContext
    val player = remember(context) { AndroidVoiceCommentPlayer(context) }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return player
}

private class AndroidVoiceCommentPlayer(
    private val context: Context,
) : VoiceCommentPlayer {
    private var mediaPlayer: MediaPlayer? = null

    override var playingUrl by mutableStateOf<String?>(null)
        private set

    override val isSupported: Boolean = true

    override fun toggle(url: String) {
        if (playingUrl == url) {
            stop()
            return
        }
        stop()
        if (url.isBlank()) return

        val player = MediaPlayer()
        mediaPlayer = player
        player.setAudioAttributes(
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        if (url.startsWith("/") || url.startsWith("file:")) {
            player.setDataSource(url.removePrefix("file:"))
        } else {
            player.setDataSource(context, android.net.Uri.parse(url))
        }
        player.setOnPreparedListener {
            it.start()
            playingUrl = url
        }
        player.setOnCompletionListener { stop() }
        player.setOnErrorListener { _, _, _ ->
            stop()
            true
        }
        player.prepareAsync()
    }

    override fun stop() {
        mediaPlayer?.let { player ->
            player.setOnPreparedListener(null)
            player.setOnCompletionListener(null)
            player.setOnErrorListener(null)
            player.release()
        }
        mediaPlayer = null
        playingUrl = null
    }

    fun release() = stop()
}
