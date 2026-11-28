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
import androidx.compose.runtime.setValue
import com.github.zly2006.zhihu.i18n.CommentsStrings
import com.github.zly2006.zhihu.navigation.isWebPageUrl
import com.github.zly2006.zhihu.platform.AudioActivityCoordinator
import com.github.zly2006.zhihu.platform.AudioActivityLease
import com.github.zly2006.zhihu.platform.UserMessageSink
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.io.File
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

@Composable
actual fun rememberVoiceCommentPlayer(): VoiceCommentPlayer {
    val messages = rememberUserMessageSink()
    val player = remember(messages) { DesktopVoiceCommentPlayer(messages) }
    DisposableEffect(player) { onDispose(player::dispose) }
    return player
}

private class DesktopVoiceCommentPlayer(private val messages: UserMessageSink) : VoiceCommentPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var playback: DesktopVoicePlayback? = null
    private var lease: AudioActivityLease? = null
    override val isSupported = true
    override var playingUrl by mutableStateOf<String?>(null)
        private set

    override fun toggle(url: String) {
        val same = playingUrl == url
        stop()
        if (same) return
        val source = runCatching {
            when {
                File(url).isAbsolute -> File(url).absolutePath
                url.startsWith("file:") -> File(URI(url)).absolutePath
                isWebPageUrl(url) -> URI(url).takeIf { it.userInfo == null }?.toString()
                else -> null
            }
        }.getOrNull()
        if (source == null) {
            messages.showShortMessage(CommentsStrings.invalidVoiceUrl)
            return
        }
        val session = DesktopVoicePlayback(source)
        lease = AudioActivityCoordinator.acquirePlayback(
            onInterrupted = { stop() },
            awaitSuspended = { session.completed.await() },
        )
        if (lease == null) {
            messages.showShortMessage(CommentsStrings.voicePlaybackFailed)
            return
        }
        playback = session
        playingUrl = url
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val result = withContext(Dispatchers.IO) { runCatching { session.play() } }
                if (playback === session) {
                    stop()
                    if (result.isFailure) messages.showShortMessage(CommentsStrings.voicePlaybackFailed)
                }
            } finally {
                session.completed.complete(Unit)
            }
        }
    }

    override fun stop() {
        playback?.cancel()
        playback = null
        playingUrl = null
        lease?.close()
        lease = null
    }

    fun dispose() {
        stop()
        scope.cancel()
    }
}

/** 使用随包的 AAC 解码器，不依赖 Linux 发行版是否额外安装 JavaFX 媒体 codec。 */
private class DesktopVoicePlayback(private val source: String) {
    val completed = CompletableDeferred<Unit>()
    private val cancelled = AtomicBoolean(false)

    @Volatile
    private var line: SourceDataLine? = null

    fun cancel() {
        cancelled.set(true)
        runCatching { line?.stop() }
        runCatching { line?.flush() }
        runCatching { line?.close() }
    }

    fun play() {
        val decoder = FFmpegFrameGrabber(source)
        try {
            // 网络输入允许正常 HTTPS 重定向和媒体分片，但不能解析 file/concat 等嵌套本地协议。
            if (isWebPageUrl(source)) {
                decoder.setOption("protocol_whitelist", "http,https,tcp,tls,crypto")
                decoder.setOption("rw_timeout", "10000000")
            }
            decoder.setSampleRate(44_100)
            decoder.setAudioChannels(1)
            decoder.setSampleFormat(AV_SAMPLE_FMT_S16)
            decoder.start()
            if (cancelled.get()) return
            val format = AudioFormat(44_100f, 16, 1, true, false)
            val output = AudioSystem.getLine(DataLine.Info(SourceDataLine::class.java, format)) as SourceDataLine
            line = output
            output.open(format)
            if (cancelled.get()) return
            output.start()
            var played = false
            while (!cancelled.get()) {
                val frame = decoder.grabSamples() ?: break
                val samples = frame.samples?.firstOrNull() as? ShortBuffer ?: error("Unsupported decoded audio format")
                val buffer = samples.duplicate()
                val bytes = ByteArray(buffer.remaining() * 2)
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(buffer)
                var offset = 0
                while (offset < bytes.size && !cancelled.get()) {
                    val written = output.write(bytes, offset, bytes.size - offset)
                    check(written > 0 || cancelled.get()) { "Audio output disconnected" }
                    offset += written
                }
                played = played || bytes.isNotEmpty()
            }
            if (!cancelled.get()) {
                check(played) { "Audio stream is empty" }
                output.drain()
            }
        } finally {
            cancel()
            runCatching { decoder.release() }
            completed.complete(Unit)
        }
    }
}
