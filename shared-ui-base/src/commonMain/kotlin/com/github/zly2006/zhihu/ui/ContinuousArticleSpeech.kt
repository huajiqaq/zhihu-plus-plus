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

import androidx.compose.runtime.Composable
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.reading.ReadingContentType
import com.github.zly2006.zhihu.reading.ReadingPlaybackStatus
import com.github.zly2006.zhihu.reading.ReadingPreferences
import com.github.zly2006.zhihu.reading.ReadingQueueItem
import com.github.zly2006.zhihu.reading.ReadingStartRequest
import com.github.zly2006.zhihu.reading.ReadingTemplateField
import com.github.zly2006.zhihu.reading.loadReadingPlaybackSpeed
import com.github.zly2006.zhihu.reading.rememberReadingPlayerController

@Composable
internal fun rememberContinuousArticleTtsState(): TtsState {
    val state = rememberReadingPlayerController()?.state?.value ?: return TtsState.Error
    return when (state.status) {
        ReadingPlaybackStatus.Idle -> TtsState.Ready
        ReadingPlaybackStatus.Initializing -> TtsState.Initializing
        ReadingPlaybackStatus.Loading -> TtsState.LoadingText
        ReadingPlaybackStatus.Playing -> TtsState.Speaking
        ReadingPlaybackStatus.Paused -> TtsState.Paused
        ReadingPlaybackStatus.Error -> TtsState.Error
    }
}

/** 旧文章工具栏也控制宿主唯一播放器，不再另建单篇合成器。 */
@Composable
internal fun rememberContinuousArticleSpeechToggler(): (String, String) -> Unit {
    val player = rememberReadingPlayerController()
    val settings = rememberSettingsStore()
    return { title, content ->
        if (player?.state?.value?.hasSession == true) {
            player.stop()
        } else if (title.isNotBlank() || content.isNotBlank()) {
            player?.start(ReadingStartRequest(
                queue = listOf(ReadingQueueItem(ReadingContentType.Article, title.hashCode().toLong() and 0xffffffffL, title = title, bodyHtml = content)),
                preferences = ReadingPreferences(
                    fieldOrder = listOf(ReadingTemplateField.Title, ReadingTemplateField.Body),
                    enabledFields = setOf(ReadingTemplateField.Title, ReadingTemplateField.Body),
                    queueLimit = 1,
                    transitionText = "",
                ),
                playbackSpeed = loadReadingPlaybackSpeed(settings),
            ))
        }
    }
}
