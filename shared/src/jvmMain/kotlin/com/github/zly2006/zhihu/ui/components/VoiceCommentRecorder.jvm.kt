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
import androidx.compose.runtime.remember

@Composable
actual fun rememberVoiceCommentRecorder(): VoiceCommentRecorder = remember { UnsupportedVoiceCommentRecorder }

@Composable
actual fun rememberVoiceRecordPermissionRequester(onResult: (Boolean) -> Unit): () -> Unit =
    remember(onResult) { { onResult(false) } }

private object UnsupportedVoiceCommentRecorder : VoiceCommentRecorder {
    override val isSupported: Boolean = false
    override val isRecording: Boolean = false
    override val elapsedMs: Long = 0L

    override fun start(): Boolean = false

    override fun stop(): RecordedVoiceComment? = null

    override fun cancel() = Unit

    override fun readBytes(recorded: RecordedVoiceComment): ByteArray? = null

    override fun delete(recorded: RecordedVoiceComment) = Unit
}
