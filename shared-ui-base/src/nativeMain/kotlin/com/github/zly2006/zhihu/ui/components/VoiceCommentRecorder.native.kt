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

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.github.zly2006.zhihu.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.github.zly2006.zhihu.i18n.CommentsStrings
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioRecorderDelegateProtocol
import platform.AVFAudio.AVEncoderBitRateKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceWasDisconnectedNotification
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.dataWithContentsOfFile
import platform.darwin.NSObject
import platform.darwin.NSObjectProtocol
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

/** iOS 的音频会话由应用级协调器管理；macOS 不使用 AVAudioSession。 */
internal expect suspend fun acquireCommentRecordingSession(onInterrupted: () -> Unit): (() -> Unit)?

internal expect fun acquireCommentPlaybackSession(onInterrupted: () -> Unit): (() -> Unit)?

@Composable
actual fun rememberVoiceCommentRecorder(): VoiceCommentRecorder {
    val recorder = remember { AppleVoiceCommentRecorder() }
    DisposableEffect(recorder) { onDispose(recorder::cancel) }
    return recorder
}

@Composable
actual fun rememberVoiceRecordPermissionRequester(onLaunched: () -> Unit, onResult: (Boolean) -> Unit): () -> Unit {
    val launched by rememberUpdatedState(onLaunched)
    val result by rememberUpdatedState(onResult)
    var active by remember { mutableStateOf(true) }
    DisposableEffect(Unit) { onDispose { active = false } }
    return remember {
        {
            when {
                !hasMicrophoneUsageDescription() -> result(false)
                AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeAudio) == AVAuthorizationStatusAuthorized -> result(true)
                AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeAudio) == AVAuthorizationStatusNotDetermined -> {
                    launched()
                    AVCaptureDevice.requestAccessForMediaType(AVMediaTypeAudio) { granted ->
                        dispatch_async(dispatch_get_main_queue()) { if (active) result(granted) }
                    }
                }
                else -> result(false)
            }
        }
    }
}

private fun hasMicrophoneUsageDescription(): Boolean =
    (NSBundle.mainBundle.objectForInfoDictionaryKey("NSMicrophoneUsageDescription") as? String)?.isNotBlank() == true

private class AppleVoiceCommentRecorder : VoiceCommentRecorder {
    private var recorder: AVAudioRecorder? = null
    private var generation = 0
    private var outputPath: String? = null
    private var releaseSession: (() -> Unit)? = null
    private var finished: CompletableDeferred<Boolean>? = null
    private var disconnectedObserver: NSObjectProtocol? = null
    private val recordings = mutableSetOf<String>()
    private val delegate = object : NSObject(), AVAudioRecorderDelegateProtocol {
        override fun audioRecorderDidFinishRecording(recorder: AVAudioRecorder, successfully: Boolean) {
            if (this@AppleVoiceCommentRecorder.recorder !== recorder) return
            val waiting = finished
            if (waiting != null) {
                waiting.complete(successfully)
            } else {
                fail(CommentsStrings.recordingInterrupted)
            }
        }

        override fun audioRecorderEncodeErrorDidOccur(recorder: AVAudioRecorder, error: NSError?) {
            if (this@AppleVoiceCommentRecorder.recorder === recorder) fail(CommentsStrings.recordingFailed(""))
        }
    }

    override val isSupported: Boolean get() = hasMicrophoneUsageDescription()
    override var isRecording by mutableStateOf(false)
        private set
    override var isBusy by mutableStateOf(false)
        private set
    override var errorMessage by mutableStateOf<String?>(null)
        private set
    override val elapsedMs: Long get() = if (isRecording) ((recorder?.currentTime ?: 0.0) * 1000).toLong() else 0L

    override suspend fun start(): Boolean {
        if (isRecording || isBusy) return false
        errorMessage = null
        if (!isSupported || AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeAudio) != AVAuthorizationStatusAuthorized) return false
        isBusy = true
        val operation = ++generation
        try {
            val release = acquireCommentRecordingSession { fail(CommentsStrings.recordingInterrupted) } ?: return false
            if (operation != generation) {
                release()
                return false
            }
            releaseSession = release
            val path = NSTemporaryDirectory() + "voice_comment_${NSUUID().UUIDString}.m4a"
            outputPath = path
            val audio = AVAudioRecorder(
                uRL = NSURL.fileURLWithPath(path),
                settings = mapOf<Any?, Any>(
                    AVFormatIDKey to kAudioFormatMPEG4AAC.toInt(),
                    AVSampleRateKey to 44_100.0,
                    AVNumberOfChannelsKey to 1,
                    AVEncoderBitRateKey to 64_000,
                ),
                error = null,
            )
            recorder = audio
            val input = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeAudio)
            disconnectedObserver = NSNotificationCenter.defaultCenter.addObserverForName(
                AVCaptureDeviceWasDisconnectedNotification,
                input,
                NSOperationQueue.mainQueue,
            ) {
                if (recorder === audio) fail(CommentsStrings.recordingInterrupted)
            }
            audio.delegate = delegate
            if (!audio.prepareToRecord() || !audio.record()) {
                cancel()
                return false
            }
            isRecording = true
            return true
        } catch (cancelled: CancellationException) {
            if (operation == generation) cancel()
            throw cancelled
        } catch (_: Exception) {
            if (operation == generation) cancel()
            return false
        } finally {
            if (operation == generation) isBusy = false
        }
    }

    override suspend fun stop(): RecordedVoiceComment? {
        val audio = recorder ?: return null
        val path = outputPath ?: return null
        if (!isRecording || isBusy) return null
        val duration = elapsedMs
        isBusy = true
        isRecording = false
        val completion = CompletableDeferred<Boolean>()
        finished = completion
        try {
            audio.stop()
            val succeeded = withTimeoutOrNull(10_000) { completion.await() }
            if (recorder !== audio) return null
            if (succeeded != true) {
                fail(CommentsStrings.recordingFailed(""))
                return null
            }
            val size = (NSFileManager.defaultManager.attributesOfItemAtPath(path, null)?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L
            if (size <= 0L || duration <= 0L) return null
            recordings += path
            outputPath = null
            return RecordedVoiceComment(path, duration, size)
        } finally {
            audio.delegate = null
            if (recorder === audio) {
                finished = null
                recorder = null
                disconnectedObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
                disconnectedObserver = null
                isBusy = false
                outputPath?.let { NSFileManager.defaultManager.removeItemAtPath(it, null) }
                outputPath = null
                val release = releaseSession
                releaseSession = null
                release?.invoke()
            }
        }
    }

    private fun fail(message: String) {
        errorMessage = message
        cancel()
    }

    override fun cancel() {
        generation++
        val completion = finished
        finished = null
        val audio = recorder
        recorder = null
        audio?.delegate = null
        audio?.stop()
        disconnectedObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        disconnectedObserver = null
        isRecording = false
        isBusy = false
        outputPath?.let { NSFileManager.defaultManager.removeItemAtPath(it, null) }
        outputPath = null
        val release = releaseSession
        releaseSession = null
        release?.invoke()
        completion?.complete(false)
    }

    override suspend fun readBytes(recorded: RecordedVoiceComment): ByteArray? {
        if (recorded.filePath !in recordings) return null
        return withContext(Dispatchers.Default) {
            val data = NSData.dataWithContentsOfFile(recorded.filePath) ?: return@withContext null
            if (data.length == 0uL || data.length > Int.MAX_VALUE.toULong()) return@withContext null
            ByteArray(data.length.toInt()).also { bytes ->
                bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
            }
        }
    }

    override fun delete(recorded: RecordedVoiceComment) {
        if (recordings.remove(recorded.filePath)) NSFileManager.defaultManager.removeItemAtPath(recorded.filePath, null)
    }
}
