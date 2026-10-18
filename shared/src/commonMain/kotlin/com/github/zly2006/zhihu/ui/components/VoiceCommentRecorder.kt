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

/** 录制完成的语音评论临时文件。 */
data class RecordedVoiceComment(
    val filePath: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val mimeType: String = "audio/mp4",
)

/**
 * 语音评论录制器。同一时刻只录制一条。
 *
 * 语音评论沿用知乎客户端的音频上传格式：MPEG-4 / AAC（`.m4a`）。
 */
interface VoiceCommentRecorder {
    val isSupported: Boolean
    val isRecording: Boolean

    /** 已录制时长（毫秒），未录制时为 0。 */
    val elapsedMs: Long

    /** 开始录制；没有录音权限或平台不支持时返回 false。 */
    fun start(): Boolean

    /** 停止并返回录制结果；没有有效录音时返回 null。 */
    fun stop(): RecordedVoiceComment?

    /** 停止并删除临时文件。 */
    fun cancel()

    fun readBytes(recorded: RecordedVoiceComment): ByteArray?

    fun delete(recorded: RecordedVoiceComment)
}

@Composable
expect fun rememberVoiceCommentRecorder(): VoiceCommentRecorder

/**
 * 请求录音权限。返回一个触发函数；已经授权时会直接回调 true。
 */
@Composable
expect fun rememberVoiceRecordPermissionRequester(onResult: (Boolean) -> Unit): () -> Unit
