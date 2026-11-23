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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.github.zly2006.zhihu.editor.uploadVoiceComment
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.platform.UserMessageSink
import com.github.zly2006.zhihu.ui.components.LocalVoiceCommentPlayer
import com.github.zly2006.zhihu.ui.components.RecordedVoiceComment
import com.github.zly2006.zhihu.ui.components.VoiceCommentPlayer
import com.github.zly2006.zhihu.ui.components.VoiceCommentRecorder
import com.github.zly2006.zhihu.ui.components.rememberVoiceCommentPlayer
import com.github.zly2006.zhihu.ui.components.rememberVoiceCommentRecorder
import com.github.zly2006.zhihu.ui.components.rememberVoiceRecordPermissionRequester
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 语音评论在一次重组内的状态与操作入口。
 * 每次重组新建实例；状态对象由 [rememberCommentVoiceState] 按各自的 key 记住，读取发生在使用处。
 */
internal class CommentVoiceState(
    val recorder: VoiceCommentRecorder,
    val previewPlayer: VoiceCommentPlayer,
    /** 已发送的本地录音，按 voiceId 索引；转码响应暂缺 URL 时供气泡立即播放。 */
    val submittedVoices: SnapshotStateMap<String, RecordedVoiceComment>,
    recordedVoiceState: MutableState<RecordedVoiceComment?>,
    uploadedVoiceIdState: State<String?>,
    isUploadingVoiceState: State<Boolean>,
    recordingElapsedMsState: State<Long>,
    voiceUploadErrorState: State<String?>,
    val discardRecordedVoice: () -> Unit,
    val uploadRecordedVoice: () -> Unit,
    val stopVoiceRecording: () -> Unit,
    val requestVoicePermission: () -> Unit,
) {
    var recordedVoice by recordedVoiceState
    val uploadedVoiceId by uploadedVoiceIdState
    val isUploadingVoice by isUploadingVoiceState
    val recordingElapsedMs by recordingElapsedMsState
    val voiceUploadError by voiceUploadErrorState
}

/** 语音评论的录制、试听和上传；录音与上传结果随 [resolvedContent] 重建。 */
@Composable
internal fun rememberCommentVoiceState(
    resolvedContent: NavDestination,
    isSendingState: State<Boolean>,
    paginationEnvironment: PaginationEnvironment,
    coroutineScope: CoroutineScope,
    userMessages: UserMessageSink,
    /** 权限窗口启动前通知 owner，避免窗口失焦卸载注册。 */
    onPickerLaunched: () -> Unit,
): CommentVoiceState {
    val isSending by isSendingState
    val voiceRecorder = rememberVoiceCommentRecorder()
    val voicePreviewPlayer = LocalVoiceCommentPlayer.current ?: rememberVoiceCommentPlayer()
    val recordedVoiceState = remember(resolvedContent) { mutableStateOf<RecordedVoiceComment?>(null) }
    var recordedVoice by recordedVoiceState
    val uploadedVoiceIdState = remember(resolvedContent) { mutableStateOf<String?>(null) }
    var uploadedVoiceId by uploadedVoiceIdState
    val submittedVoices = remember(resolvedContent) { mutableStateMapOf<String, RecordedVoiceComment>() }
    DisposableEffect(submittedVoices, voiceRecorder, recordedVoiceState) {
        onDispose {
            voiceRecorder.cancel()
            recordedVoiceState.value?.let(voiceRecorder::delete)
            submittedVoices.values.forEach(voiceRecorder::delete)
        }
    }
    val isUploadingVoiceState = remember { mutableStateOf(false) }
    var isUploadingVoice by isUploadingVoiceState
    val recordingElapsedMsState = remember { mutableStateOf(0L) }
    var recordingElapsedMs by recordingElapsedMsState
    var voiceErrorMessage by remember { mutableStateOf<String?>(null) }
    val voiceUploadErrorState = remember(resolvedContent) { mutableStateOf<String?>(null) }
    var voiceUploadError by voiceUploadErrorState
    var isVoiceScreenActive by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        onDispose {
            isVoiceScreenActive = false
            voicePreviewPlayer.stop()
        }
    }

    fun discardRecordedVoice() {
        voicePreviewPlayer.stop()
        recordedVoice?.let { voiceRecorder.delete(it) }
        recordedVoice = null
        uploadedVoiceId = null
        voiceUploadError = null
    }

    fun uploadRecordedVoice() {
        if (isUploadingVoice || isSending) return
        val recorded = recordedVoice ?: return
        val bytes = voiceRecorder.readBytes(recorded)
        if (bytes == null || bytes.isEmpty()) {
            voiceUploadError = "读取录音失败"
            voiceErrorMessage = voiceUploadError
            return
        }
        voiceUploadError = null
        isUploadingVoice = true
        coroutineScope.launch {
            try {
                val uploaded = uploadVoiceComment(
                    environment = paginationEnvironment,
                    bytes = bytes,
                    durationMs = recorded.durationMs,
                    mimeType = recorded.mimeType,
                )
                uploadedVoiceId = uploaded.voiceId
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                voiceUploadError = "语音上传失败：${e.message}"
                voiceErrorMessage = voiceUploadError
            } finally {
                isUploadingVoice = false
            }
        }
    }

    fun stopVoiceRecording() {
        val recorded = voiceRecorder.stop()
        if (recorded == null || recorded.sizeBytes <= 0L) {
            voiceErrorMessage = "录音时间太短"
            return
        }
        recordedVoice?.let { voiceRecorder.delete(it) }
        recordedVoice = recorded
        uploadedVoiceId = null
        recordingElapsedMs = recorded.durationMs
        voicePreviewPlayer.toggle(recorded.filePath)
        uploadRecordedVoice()
    }

    val requestVoicePermission = rememberVoiceRecordPermissionRequester(onLaunched = onPickerLaunched) { granted ->
        if (!isVoiceScreenActive) return@rememberVoiceRecordPermissionRequester
        if (granted) {
            discardRecordedVoice()
            if (voiceRecorder.start()) {
                recordingElapsedMs = 0L
            } else {
                voiceErrorMessage = "无法开始录音"
            }
        } else {
            voiceErrorMessage = "需要麦克风权限才能录制语音评论"
        }
    }

    LaunchedEffect(voiceRecorder.isRecording) {
        while (voiceRecorder.isRecording) {
            recordingElapsedMs = voiceRecorder.elapsedMs
            delay(100)
        }
    }

    LaunchedEffect(voiceErrorMessage) {
        voiceErrorMessage?.let { message ->
            userMessages.showShortMessage(message)
            voiceErrorMessage = null
        }
    }

    return CommentVoiceState(
        recorder = voiceRecorder,
        previewPlayer = voicePreviewPlayer,
        submittedVoices = submittedVoices,
        recordedVoiceState = recordedVoiceState,
        uploadedVoiceIdState = uploadedVoiceIdState,
        isUploadingVoiceState = isUploadingVoiceState,
        recordingElapsedMsState = recordingElapsedMsState,
        voiceUploadErrorState = voiceUploadErrorState,
        discardRecordedVoice = ::discardRecordedVoice,
        uploadRecordedVoice = ::uploadRecordedVoice,
        stopVoiceRecording = ::stopVoiceRecording,
        requestVoicePermission = requestVoicePermission,
    )
}
