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

package com.github.zly2006.zhihu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.i18n.CommentsStrings
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppIcon
import com.github.zly2006.zhihu.ui.components.RecordedVoiceComment
import com.github.zly2006.zhihu.ui.components.VoiceCommentPlayer

@Composable
internal fun VoiceCommentBubble(voice: DataHolder.Comment.CommentVoiceInfo, localVoice: RecordedVoiceComment?, player: VoiceCommentPlayer) {
    val playbackUrl = voice.url.ifBlank { localVoice?.filePath.orEmpty() }
    val durationMs = voice.durationMs.takeIf { it > 0 } ?: localVoice?.durationMs ?: 0
    val isPlaying = player.playingUrl == playbackUrl && playbackUrl.isNotBlank()
    val canPlay = player.isSupported && playbackUrl.isNotBlank()
    val skin = LocalAppSkinTokens.current
    var showTranscription by remember(voice.voiceId) { mutableStateOf(voice.asrText.isNotBlank()) }

    Column(modifier = Modifier.padding(top = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(enabled = canPlay) {
                    player.toggle(playbackUrl)
                }.padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("comment_voice_${voice.voiceId}"),
        ) {
            AppIcon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) CommentsStrings.pauseVoice else CommentsStrings.playVoice,
                tint = if (canPlay) skin.onSurface else skin.disabledOnSurface,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${(durationMs / 1000).coerceAtLeast(0)}″",
                style = MaterialTheme.typography.bodyMedium,
                color = if (canPlay) skin.onSurface else skin.disabledOnSurface,
            )
            if (!player.isSupported) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = CommentsStrings.voicePlaybackUnavailable,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (voice.asrText.isNotBlank()) {
            Text(
                text = if (showTranscription) voice.asrText else CommentsStrings.showTranscription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { showTranscription = !showTranscription }
                    .testTag("comment_voice_text_${voice.voiceId}"),
            )
        }
    }
}
