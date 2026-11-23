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
import androidx.compose.runtime.staticCompositionLocalOf

/** 同时只播放一条，切换时停止上一条。 */
interface VoiceCommentPlayer {
    /** null 表示未播放。 */
    val playingUrl: String?

    val isSupported: Boolean

    fun toggle(url: String)

    fun stop()
}

internal val LocalVoiceCommentPlayer = staticCompositionLocalOf<VoiceCommentPlayer?> { null }

@Composable
expect fun rememberVoiceCommentPlayer(): VoiceCommentPlayer
