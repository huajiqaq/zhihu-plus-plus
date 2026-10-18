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

import androidx.compose.runtime.Composable

/**
 * 评论语音播放器。同一时刻只播放一条；切换时自动停止上一条。
 */
interface VoiceCommentPlayer {
    /** 当前正在播放的语音 URL，null 表示未播放。 */
    val playingUrl: String?

    /** 当前平台是否支持播放语音评论。 */
    val isSupported: Boolean

    fun toggle(url: String)

    fun stop()
}

@Composable
expect fun rememberVoiceCommentPlayer(): VoiceCommentPlayer
