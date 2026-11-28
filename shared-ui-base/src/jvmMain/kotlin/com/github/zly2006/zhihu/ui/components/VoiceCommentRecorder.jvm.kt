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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.github.zly2006.zhihu.i18n.CommentsStrings
import com.github.zly2006.zhihu.platform.AudioActivityCoordinator
import com.github.zly2006.zhihu.platform.AudioActivityLease
import com.github.zly2006.zhihu.ui.ReaderDisplayFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC
import org.bytedeco.javacv.FFmpegFrameRecorder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

@Composable
actual fun rememberVoiceCommentRecorder(): VoiceCommentRecorder {
    val recorder = remember { DesktopVoiceCommentRecorder() }
    DisposableEffect(recorder) { onDispose(recorder::dispose) }
    return recorder
}

@Composable
actual fun rememberVoiceRecordPermissionRequester(onLaunched: () -> Unit, onResult: (Boolean) -> Unit): () -> Unit {
    val launched by rememberUpdatedState(onLaunched)
    val result by rememberUpdatedState(onResult)
    return remember {
        {
            // 桌面隐私授权由系统在线路 open 时处理；start 的设备异常必须反馈，不能假定已有授权。
            launched()
            result(true)
        }
    }
}

private class DesktopVoiceCommentRecorder : VoiceCommentRecorder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var capture: DesktopVoiceCapture? = null
    private var generation = 0
    private var lease: AudioActivityLease? = null
    private val recordings = mutableSetOf<String>()
    private var stoppingCapture: DesktopVoiceCapture? = null
    private var startedAt = 0L
    override val isSupported = true
    override var isRecording by mutableStateOf(false)
        private set
    override var isBusy by mutableStateOf(false)
        private set
    private var errorText by mutableStateOf<(() -> String)?>(null)
    override val errorMessage: String? get() = errorText?.invoke()
    override val elapsedMs: Long get() = if (isRecording) (System.nanoTime() - startedAt) / 1_000_000 else 0L

    override suspend fun start(): Boolean {
        if (isRecording || isBusy) return false
        errorText = null
        isBusy = true
        val operation = ++generation
        try {
            stoppingCapture?.completed?.await()
            if (operation != generation) return false
            stoppingCapture = null
            val acquired = AudioActivityCoordinator.acquireRecording {
                cancel()
                errorText = { CommentsStrings.recordingInterrupted }
            }
            if (operation != generation) {
                acquired?.close()
                return false
            }
            lease = acquired
            if (lease == null) {
                isBusy = false
                return false
            }
        } catch (error: Exception) {
            if (operation == generation) isBusy = false
            throw error
        }
        val session = DesktopVoiceCapture()
        capture = session
        scope.launch(Dispatchers.IO) { session.run() }
        scope.launch {
            val result = session.completed.await()
            if (capture === session && !isBusy && result.isFailure) {
                val failure = result.exceptionOrNull()
                errorText = { CommentsStrings.recordingFailed((failure as? ReaderDisplayFailure)?.displayMessage?.invoke() ?: failure?.message.orEmpty()) }
                cancel()
            }
        }
        try {
            val started = session.started.await()
            if (capture !== session || !started) {
                if (capture === session) {
                    val failure = session.completed.await().exceptionOrNull()
                    if (failure != null) errorText = { CommentsStrings.recordingFailed((failure as? ReaderDisplayFailure)?.displayMessage?.invoke() ?: failure.message.orEmpty()) }
                    cancel()
                }
                return false
            }
            isRecording = true
            startedAt = System.nanoTime()
            return true
        } catch (cancelled: CancellationException) {
            if (capture === session) cancel() else session.cancel()
            throw cancelled
        } finally {
            if (capture === session) isBusy = false
        }
    }

    override suspend fun stop(): RecordedVoiceComment? {
        val session = capture ?: return null
        if (!isRecording || isBusy) return null
        isBusy = true
        isRecording = false
        try {
            withContext(Dispatchers.IO) { session.requestStop() }
            val result = session.completed.await().getOrElse { failure ->
                errorText = { CommentsStrings.recordingFailed((failure as? ReaderDisplayFailure)?.displayMessage?.invoke() ?: failure.message.orEmpty()) }
                return null
            }
            if (capture !== session) {
                result?.let { File(it.filePath).delete() }
                return null
            }
            result?.let { recordings += it.filePath }
            return result
        } catch (cancelled: CancellationException) {
            session.cancel()
            throw cancelled
        } finally {
            if (capture === session) {
                capture = null
                isBusy = false
                lease?.close()
                lease = null
            }
        }
    }

    override fun cancel() {
        generation++
        val session = capture
        capture = null
        if (session != null) {
            stoppingCapture = session
            session.cancel()
        }
        isRecording = false
        isBusy = false
        lease?.close()
        lease = null
    }

    fun dispose() {
        cancel()
        recordings.forEach { File(it).delete() }
        recordings.clear()
        scope.cancel()
    }

    override suspend fun readBytes(recorded: RecordedVoiceComment): ByteArray? {
        if (recorded.filePath !in recordings) return null
        return withContext(Dispatchers.IO) { runCatching { File(recorded.filePath).readBytes() }.getOrNull() }
    }

    override fun delete(recorded: RecordedVoiceComment) {
        if (recordings.remove(recorded.filePath)) File(recorded.filePath).delete()
    }
}

/** 捕获、AAC 编码与 MP4 收尾归同一工作线程所有；关闭输入线路会唤醒阻塞中的 read。 */
private class DesktopVoiceCapture {
    val started = CompletableDeferred<Boolean>()
    val completed = CompletableDeferred<Result<RecordedVoiceComment?>>()
    private val stopping = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)

    @Volatile
    private var line: TargetDataLine? = null

    @Volatile
    private var output: File? = null

    fun requestStop() {
        stopping.set(true)
        runCatching { line?.stop() }
        runCatching { line?.close() }
    }

    fun cancel() {
        cancelled.set(true)
        requestStop()
        if (completed.isCompleted) output?.delete()
    }

    fun run() {
        var encoder: FFmpegFrameRecorder? = null
        var retained = false
        val result = runCatching {
            val file = File.createTempFile("voice_comment_", ".m4a")
            output = file
            check(!cancelled.get())
            val formats = listOf(
                AudioFormat(44_100f, 16, 1, true, false),
                AudioFormat(48_000f, 16, 1, true, false),
                AudioFormat(44_100f, 16, 2, true, false),
                AudioFormat(48_000f, 16, 2, true, false),
            )
            val format = formats.firstOrNull { AudioSystem.isLineSupported(DataLine.Info(TargetDataLine::class.java, it)) }
                ?: throw ReaderDisplayFailure("No supported microphone input") { CommentsStrings.microphoneInputUnsupported }
            val input = AudioSystem.getLine(DataLine.Info(TargetDataLine::class.java, format)) as TargetDataLine
            line = input
            input.open(format)
            check(!cancelled.get())
            val recorder = FFmpegFrameRecorder(file, 1).apply {
                setFormat("mp4")
                setAudioCodec(AV_CODEC_ID_AAC)
                setSampleRate(44_100)
                setAudioChannels(1)
                setAudioBitrate(64_000)
            }
            encoder = recorder
            recorder.start()
            check(!cancelled.get())
            input.start()
            started.complete(true)
            val bytes = ByteArray(4096 * format.frameSize)
            var frames = 0L
            while (!stopping.get()) {
                val count = input.read(bytes, 0, bytes.size)
                if (count <= 0) {
                    if (!stopping.get()) throw ReaderDisplayFailure("Microphone input disconnected") { CommentsStrings.microphoneInputDisconnected }
                    break
                }
                check(count % format.frameSize == 0)
                recorder.recordSamples(
                    format.sampleRate.toInt(),
                    format.channels,
                    ByteBuffer.wrap(bytes, 0, count).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer(),
                )
                frames += count / format.frameSize
            }
            recorder.stop()
            val duration = frames * 1000 / format.sampleRate.toLong()
            if (cancelled.get() || duration <= 0 || file.length() <= 0) {
                null
            } else {
                retained = true
                RecordedVoiceComment(file.absolutePath, duration, file.length())
            }
        }
        requestStop()
        runCatching { encoder?.release() }
        if (!retained || cancelled.get()) output?.delete()
        completed.complete(result)
        // 与 cancel 在结果发布处交错时，最后一方仍负责删除已封装但未交付的文件。
        if (cancelled.get()) output?.delete()
        started.complete(false)
    }
}
