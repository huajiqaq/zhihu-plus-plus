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

package com.github.zly2006.zhihu.reading

import platform.MediaPlayer.MPMediaItemPropertyAlbumTitle
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommand
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusCommandFailed
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess

/** 系统拥有命令中心；仅会话存活期间注册自身 target，不接管其它音频的回调。 */
internal class AppleReadingMediaSession(private val command: (ReadingMediaCommand) -> Unit) {
    private val center = MPRemoteCommandCenter.sharedCommandCenter()
    private val targets = mutableListOf<Pair<MPRemoteCommand, Any>>()
    private var state = ReadingPlayerState()

    private fun install() {
        if (targets.isNotEmpty()) return
        fun register(remote: MPRemoteCommand, action: () -> ReadingMediaCommand) {
            val target = remote.addTargetWithHandler {
                if (!state.hasSession) MPRemoteCommandHandlerStatusCommandFailed else {
                    command(action())
                    MPRemoteCommandHandlerStatusSuccess
                }
            }
            targets += remote to target
        }
        register(center.playCommand) { ReadingMediaCommand.Play }
        register(center.pauseCommand) { ReadingMediaCommand.Pause }
        register(center.togglePlayPauseCommand) { if (state.isActivelyPlaying) ReadingMediaCommand.Pause else ReadingMediaCommand.Play }
        register(center.previousTrackCommand) { ReadingMediaCommand.Previous }
        register(center.nextTrackCommand) { ReadingMediaCommand.Next }
        register(center.stopCommand) { ReadingMediaCommand.Stop }
    }

    fun publish(state: ReadingPlayerState) {
        this.state = state
        if (!state.hasSession) { close(); return }
        install()
        center.playCommand.enabled = true
        center.pauseCommand.enabled = true
        center.togglePlayPauseCommand.enabled = true
        center.stopCommand.enabled = true
        center.previousTrackCommand.enabled = state.canPlayPrevious
        center.nextTrackCommand.enabled = state.canPlayNext
        val item = state.currentItem ?: return
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = mapOf(
            MPMediaItemPropertyTitle to item.displayTitle,
            MPMediaItemPropertyArtist to item.author,
            MPMediaItemPropertyAlbumTitle to item.contentType.displayName,
            MPNowPlayingInfoPropertyPlaybackRate to if (state.status == ReadingPlaybackStatus.Playing) state.playbackSpeed.toDouble() else 0.0,
        )
    }

    fun close() {
        if (targets.isEmpty()) return
        targets.forEach { (remote, target) -> remote.removeTarget(target) }
        targets.clear()
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
    }
}
