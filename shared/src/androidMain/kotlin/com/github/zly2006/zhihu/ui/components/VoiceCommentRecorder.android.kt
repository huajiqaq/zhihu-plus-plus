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

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.io.File

@Composable
actual fun rememberVoiceCommentRecorder(): VoiceCommentRecorder {
    val context = LocalContext.current.applicationContext
    val recorder = remember(context) { AndroidVoiceCommentRecorder(context) }
    DisposableEffect(recorder) {
        onDispose { recorder.release() }
    }
    return recorder
}

@Composable
actual fun rememberVoiceRecordPermissionRequester(onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> onResult(granted) }
    return remember(context, launcher) {
        {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                onResult(true)
            } else {
                launcher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
}

private class AndroidVoiceCommentRecorder(
    private val context: Context,
) : VoiceCommentRecorder {
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAt = 0L

    override val isSupported: Boolean = true

    override var isRecording by mutableStateOf(false)
        private set

    override val elapsedMs: Long
        get() = if (isRecording) SystemClock.elapsedRealtime() - startedAt else 0L

    override fun start(): Boolean {
        if (isRecording) return true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val file = File(context.cacheDir, "voice_comment_${System.currentTimeMillis()}.m4a")
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        return runCatching {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioChannels(1)
            recorder.setAudioSamplingRate(44_100)
            recorder.setAudioEncodingBitRate(64_000)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()
            mediaRecorder = recorder
            outputFile = file
            startedAt = SystemClock.elapsedRealtime()
            isRecording = true
            true
        }.getOrElse {
            runCatching { recorder.release() }
            file.delete()
            false
        }
    }

    override fun stop(): RecordedVoiceComment? {
        if (!isRecording) return null
        val duration = elapsedMs
        val file = outputFile
        val recorder = mediaRecorder
        isRecording = false
        mediaRecorder = null
        outputFile = null
        val stopped = runCatching { recorder?.stop() }.isSuccess
        runCatching { recorder?.release() }
        if (!stopped || file == null || !file.exists() || file.length() <= 0L) {
            file?.delete()
            return null
        }
        return RecordedVoiceComment(
            filePath = file.absolutePath,
            durationMs = duration,
            sizeBytes = file.length(),
        )
    }

    override fun cancel() {
        if (!isRecording) {
            outputFile?.delete()
            outputFile = null
            return
        }
        isRecording = false
        runCatching { mediaRecorder?.stop() }
        runCatching { mediaRecorder?.release() }
        mediaRecorder = null
        outputFile?.delete()
        outputFile = null
    }

    override fun readBytes(recorded: RecordedVoiceComment): ByteArray? =
        runCatching { File(recorded.filePath).readBytes() }.getOrNull()

    override fun delete(recorded: RecordedVoiceComment) {
        runCatching { File(recorded.filePath).delete() }
    }

    fun release() {
        if (isRecording) {
            runCatching { mediaRecorder?.stop() }
        }
        runCatching { mediaRecorder?.release() }
        mediaRecorder = null
        outputFile = null
        isRecording = false
    }
}
