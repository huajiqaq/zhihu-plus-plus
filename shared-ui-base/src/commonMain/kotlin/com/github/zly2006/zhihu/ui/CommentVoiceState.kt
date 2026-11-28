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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.zly2006.zhihu.account.ZhihuAccountClient
import com.github.zly2006.zhihu.editor.uploadVoiceComment
import com.github.zly2006.zhihu.i18n.CommentsStrings
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.platform.UserMessageSink
import com.github.zly2006.zhihu.ui.components.LocalVoiceCommentPlayer
import com.github.zly2006.zhihu.ui.components.RecordedVoiceComment
import com.github.zly2006.zhihu.ui.components.VoiceCommentPlayer
import com.github.zly2006.zhihu.ui.components.VoiceCommentRecorder
import com.github.zly2006.zhihu.ui.components.rememberVoiceCommentPlayer
import com.github.zly2006.zhihu.ui.components.rememberVoiceCommentRecorder
import com.github.zly2006.zhihu.ui.components.rememberVoiceRecordPermissionRequester
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
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
    voiceUploadErrorState: State<(() -> String)?>,
    val discardRecordedVoice: () -> Unit,
    val uploadRecordedVoice: () -> Unit,
    val stopVoiceRecording: () -> Unit,
    val requestVoicePermission: () -> Unit,
) {
    var recordedVoice by recordedVoiceState
    val uploadedVoiceId by uploadedVoiceIdState
    val isUploadingVoice by isUploadingVoiceState
    val recordingElapsedMs by recordingElapsedMsState
    private val voiceUploadErrorText by voiceUploadErrorState
    val voiceUploadError: String? get() = voiceUploadErrorText?.invoke()
}

/** 只在调用方按内容与账号客户端建立的子组合内使用；切 owner 销毁录音和上传，不清除文字草稿。 */
@Composable
internal fun rememberCommentVoiceState(
    resolvedContent: NavDestination,
    accountClient: ZhihuAccountClient,
    paginationEnvironment: ZhihuApiEnvironment,
    isSendingState: State<Boolean>,
    isCurrentAccount: () -> Boolean,
    coroutineScope: CoroutineScope,
    userMessages: UserMessageSink,
    /** 权限窗口启动前通知 owner，避免窗口失焦卸载注册。 */
    onPickerLaunched: () -> Unit,
): CommentVoiceState {
    val isSending by isSendingState
    val uploadEnvironment = remember(accountClient, paginationEnvironment) {
        val owner = accountClient
        object : ZhihuApiEnvironment {
            override val accountClient = owner

            override suspend fun handleFetchFailure(tag: String?, error: Exception) =
                paginationEnvironment.handleFetchFailure(tag, error)
        }
    }
    val voiceRecorder = key(resolvedContent) { rememberVoiceCommentRecorder() }
    val voicePreviewPlayer = LocalVoiceCommentPlayer.current ?: rememberVoiceCommentPlayer()
    val recordedVoiceState = remember(resolvedContent) { mutableStateOf<RecordedVoiceComment?>(null) }
    var recordedVoice by recordedVoiceState
    val uploadedVoiceIdState = remember(resolvedContent) { mutableStateOf<String?>(null) }
    var uploadedVoiceId by uploadedVoiceIdState
    val submittedVoices = remember(resolvedContent) { mutableStateMapOf<String, RecordedVoiceComment>() }
    val isUploadingVoiceState = remember(resolvedContent) { mutableStateOf(false) }
    var isUploadingVoice by isUploadingVoiceState
    val recordingElapsedMsState = remember { mutableStateOf(0L) }
    var recordingElapsedMs by recordingElapsedMsState
    var voiceErrorMessage by remember { mutableStateOf<String?>(null) }
    val voiceUploadErrorState = remember(resolvedContent) { mutableStateOf<(() -> String)?>(null) }
    var voiceUploadError by voiceUploadErrorState
    var isVoiceScreenActive by remember(resolvedContent) { mutableStateOf(true) }
    var generation by remember(resolvedContent) { mutableStateOf(0) }
    var recordingJob by remember(resolvedContent) { mutableStateOf<Job?>(null) }
    var uploadJob by remember(resolvedContent) { mutableStateOf<Job?>(null) }
    val operationBusy = remember(resolvedContent) { mutableStateOf(false) }
    val exposedRecorder = remember(voiceRecorder, operationBusy) {
        object : VoiceCommentRecorder by voiceRecorder {
            override val isBusy: Boolean get() = operationBusy.value || voiceRecorder.isBusy

            override fun cancel() {
                generation++
                recordingJob?.cancel()
                voiceRecorder.cancel()
                operationBusy.value = false
            }
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(resolvedContent, lifecycle, voiceRecorder) {
        isVoiceScreenActive = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                voicePreviewPlayer.stop()
                if (voiceRecorder.isRecording || voiceRecorder.isBusy) {
                    generation++
                    recordingJob?.cancel()
                    voiceRecorder.cancel()
                    operationBusy.value = false
                    voiceErrorMessage = CommentsStrings.recordingInterrupted
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            isVoiceScreenActive = false
            generation++
            recordingJob?.cancel()
            uploadJob?.cancel()
            voicePreviewPlayer.stop()
            voiceRecorder.cancel()
            recordedVoiceState.value?.let(voiceRecorder::delete)
            submittedVoices.values.forEach(voiceRecorder::delete)
        }
    }

    fun discardRecordedVoice() {
        generation++
        uploadJob?.cancel()
        uploadJob = null
        isUploadingVoice = false
        voicePreviewPlayer.stop()
        recordedVoice?.let { voiceRecorder.delete(it) }
        recordedVoice = null
        uploadedVoiceId = null
        voiceUploadError = null
    }

    fun uploadRecordedVoice() {
        if (isUploadingVoice || isSending || !isCurrentAccount()) return
        val recorded = recordedVoice ?: return
        val operation = generation
        voiceUploadError = null
        isUploadingVoice = true
        uploadJob = coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val bytes = voiceRecorder.readBytes(recorded)
                if (bytes == null || bytes.isEmpty()) {
                    if (operation == generation && isVoiceScreenActive && isCurrentAccount()) {
                        voiceUploadError = { CommentsStrings.recordingReadFailed }
                        voiceErrorMessage = voiceUploadError?.invoke()
                    }
                    return@launch
                }
                if (operation != generation || !isVoiceScreenActive || !isCurrentAccount()) return@launch
                val uploaded = uploadVoiceComment(
                    environment = uploadEnvironment,
                    bytes = bytes,
                    durationMs = recorded.durationMs,
                    mimeType = recorded.mimeType,
                )
                if (operation == generation && isVoiceScreenActive && isCurrentAccount()) uploadedVoiceId = uploaded.voiceId
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (operation == generation && isVoiceScreenActive && isCurrentAccount()) {
                    voiceUploadError = { CommentsStrings.voiceUploadFailed(e.message.orEmpty()) }
                    voiceErrorMessage = voiceUploadError?.invoke()
                }
            } finally {
                if (operation == generation) isUploadingVoice = false
            }
        }
    }

    fun stopVoiceRecording() {
        if (!voiceRecorder.isRecording || exposedRecorder.isBusy) return
        operationBusy.value = true
        val operation = generation
        recordingJob = coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val recorded = voiceRecorder.stop()
                if (operation != generation || !isVoiceScreenActive || !isCurrentAccount()) {
                    recorded?.let(voiceRecorder::delete)
                    return@launch
                }
                if (recorded == null || recorded.sizeBytes <= 0L || recorded.durationMs <= 0L) {
                    recorded?.let(voiceRecorder::delete)
                    if (voiceRecorder.errorMessage == null) voiceErrorMessage = CommentsStrings.recordingTooShort
                    return@launch
                }
                recordedVoice?.let(voiceRecorder::delete)
                recordedVoice = recorded
                uploadedVoiceId = null
                recordingElapsedMs = recorded.durationMs
                voicePreviewPlayer.toggle(recorded.filePath)
                uploadRecordedVoice()
            } catch (e: CancellationException) {
                if (operation == generation) voiceRecorder.cancel()
                throw e
            } catch (_: Exception) {
                if (operation == generation) {
                    voiceRecorder.cancel()
                    voiceErrorMessage = CommentsStrings.recordingFailed("")
                }
            } finally {
                if (operation == generation) operationBusy.value = false
            }
        }
    }

    val launchVoicePermission = key(resolvedContent, generation) {
        val requestGeneration = generation
        rememberVoiceRecordPermissionRequester(onLaunched = onPickerLaunched) { granted ->
            if (requestGeneration == generation && isVoiceScreenActive && operationBusy.value && isCurrentAccount()) {
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    operationBusy.value = false
                } else if (granted) {
                    discardRecordedVoice()
                    val operation = generation
                    recordingJob = coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                        try {
                            if (voiceRecorder.start()) {
                                if (operation == generation && isVoiceScreenActive && isCurrentAccount()) {
                                    recordingElapsedMs = 0L
                                } else {
                                    voiceRecorder.cancel()
                                }
                            } else if (operation == generation) {
                                if (voiceRecorder.errorMessage == null) voiceErrorMessage = CommentsStrings.recordingStartFailed
                            }
                        } catch (e: CancellationException) {
                            if (operation == generation) voiceRecorder.cancel()
                            throw e
                        } catch (_: Exception) {
                            if (operation == generation) {
                                voiceRecorder.cancel()
                                voiceErrorMessage = CommentsStrings.recordingStartFailed
                            }
                        } finally {
                            if (operation == generation) operationBusy.value = false
                        }
                    }
                } else {
                    operationBusy.value = false
                    voiceErrorMessage = CommentsStrings.microphonePermissionRequired
                }
            }
        }
    }
    val requestVoicePermission: () -> Unit = {
        if (!exposedRecorder.isBusy && !voiceRecorder.isRecording && !isSending && !isUploadingVoice && isCurrentAccount()) {
            operationBusy.value = true
            launchVoicePermission()
        }
    }

    LaunchedEffect(voiceRecorder.errorMessage, generation) {
        voiceRecorder.errorMessage?.let { voiceErrorMessage = it }
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
        recorder = exposedRecorder,
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
