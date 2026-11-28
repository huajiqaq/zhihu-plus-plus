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

data class RecordedVoiceComment(
    /** 可试听的本地文件路径或由录音器持有的浏览器 Blob URL；删除后失效。 */
    val filePath: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val mimeType: String = "audio/mp4",
)

/** 上传格式为 MPEG-4/AAC（.m4a）。 */
interface VoiceCommentRecorder {
    val isSupported: Boolean
    val isRecording: Boolean

    /** 设备启动、封装或转码尚未完成；期间不得启动下一次录音。 */
    val isBusy: Boolean

    /** 设备断开、编码或系统中断错误；下一次 start 时清除。 */
    val errorMessage: String?

    /** 未录制时为 0。 */
    val elapsedMs: Long

    /** 未授权或平台不支持时返回 false；取消必须释放迟到的设备授权结果。 */
    suspend fun start(): Boolean

    /** 等待容器封装完成后返回；无有效录音时返回 null。 */
    suspend fun stop(): RecordedVoiceComment?

    /** 同时删除临时文件。 */
    fun cancel()

    suspend fun readBytes(recorded: RecordedVoiceComment): ByteArray?

    fun delete(recorded: RecordedVoiceComment)
}

@Composable
expect fun rememberVoiceCommentRecorder(): VoiceCommentRecorder

/** 仅实际启动权限窗口前通知调用方保持组合；已授权时直接回调 true，不支持的平台回调 false。 */
@Composable
expect fun rememberVoiceRecordPermissionRequester(
    onLaunched: () -> Unit = {},
    onResult: (Boolean) -> Unit,
): () -> Unit
