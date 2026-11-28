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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.github.zly2006.zhihu.reading

import com.github.zly2006.zhihu.i18n.ReadingStrings
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesizerDelegateProtocol
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.AVSpeechUtteranceDefaultSpeechRate
import platform.AVFAudio.AVSpeechUtteranceMaximumSpeechRate
import platform.AVFAudio.AVSpeechUtteranceMinimumSpeechRate
import platform.darwin.NSObject

actual val isReadingPlayerSupported: Boolean = true
internal actual fun createReadingSpeechEngine(scope: CoroutineScope): ReadingSpeechEngine = AppleReadingSpeechEngine(scope)

internal interface AppleReadingAudioSession {
    fun acquire(): Boolean
    fun release()
    fun close() = release()
    fun configure(synthesizer: AVSpeechSynthesizer)
}

internal expect fun appleReadingAudioSession(
    listener: () -> ReadingSpeechListener?,
    onServicesReset: () -> Unit,
    awaitSuspended: suspend () -> Unit,
): AppleReadingAudioSession

internal class AppleReadingSpeechEngine(private val scope: CoroutineScope) : ReadingSpeechEngine {
    override var listener: ReadingSpeechListener? = null
    override var label = ""
        private set
    override var availableLabels = emptyList<String>()
        private set
    private var synthesizer: AVSpeechSynthesizer? = null
    private var voice: AVSpeechSynthesisVoice? = null
    private var speed = DEFAULT_READING_PLAYBACK_SPEED
    private var active: Pair<AVSpeechUtterance, String>? = null
    private var closed = false
    private var suspension: Pair<AVSpeechUtterance, CompletableDeferred<Unit>>? = null
    private var audioReleaseGeneration = 0L
    // AVSpeechSynthesizer 的 delegate 为弱引用，必须由引擎持有独立的 ObjC 对象。
    private val speechDelegate = object : NSObject(), AVSpeechSynthesizerDelegateProtocol {
        @ObjCSignatureOverride
        override fun speechSynthesizer(synthesizer: AVSpeechSynthesizer, didStartSpeechUtterance: AVSpeechUtterance) {
            dispatch(didStartSpeechUtterance) { listener?.onStarted(it) }
        }

        @ObjCSignatureOverride
        override fun speechSynthesizer(synthesizer: AVSpeechSynthesizer, didPauseSpeechUtterance: AVSpeechUtterance) {
            confirmSuspended(didPauseSpeechUtterance)
        }

        @ObjCSignatureOverride
        override fun speechSynthesizer(synthesizer: AVSpeechSynthesizer, didFinishSpeechUtterance: AVSpeechUtterance) {
            confirmSuspended(didFinishSpeechUtterance)
            dispatch(didFinishSpeechUtterance) { active = null; listener?.onFinished(it) }
        }

        @ObjCSignatureOverride
        override fun speechSynthesizer(synthesizer: AVSpeechSynthesizer, didCancelSpeechUtterance: AVSpeechUtterance) {
            confirmSuspended(didCancelSpeechUtterance)
            dispatch(didCancelSpeechUtterance) { active = null; listener?.onInterrupted(false) }
        }
    }
    private val audio = appleReadingAudioSession({ listener }, ::resetSynthesizer, ::awaitSuspended)
    private val media = AppleReadingMediaSession { command -> scope.launch { if (!closed) listener?.onCommand(command) } }

    override fun initialize(languageTag: String, speed: Float) {
        if (closed) return
        stop()
        synthesizer?.delegate = null
        this.speed = speed
        voice = AVSpeechSynthesisVoice.voiceWithLanguage(languageTag)
        availableLabels = AVSpeechSynthesisVoice.speechVoices().filterIsInstance<AVSpeechSynthesisVoice>().map { it.name }
        label = voice?.name ?: "AVSpeechSynthesizer"
        resetSynthesizer()
        listener?.onReady()
    }

    private fun resetSynthesizer() {
        suspension?.second?.completeExceptionally(IllegalStateException(ReadingStrings.ttsInitFailed))
        suspension = null
        active = null
        synthesizer?.delegate = null
        synthesizer?.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        synthesizer = if (closed) null else AVSpeechSynthesizer().also {
            audio.configure(it)
            it.delegate = speechDelegate
        }
    }

    override fun speak(text: String, id: String) {
        val engine = synthesizer
        if (closed || engine == null) {
            listener?.onFailed(id, ReadingSpeechFailure(ReadingSpeechFailureKind.Engine, ReadingStrings.ttsInitFailed))
            return
        }
        val utterance = AVSpeechUtterance(string = text).apply {
            voice = this@AppleReadingSpeechEngine.voice
            rate = (AVSpeechUtteranceDefaultSpeechRate * speed).coerceIn(AVSpeechUtteranceMinimumSpeechRate, AVSpeechUtteranceMaximumSpeechRate)
        }
        suspension = null
        active = utterance to id
        engine.speakUtterance(utterance)
    }

    override fun pause(): Boolean {
        val utterance = active?.first ?: return false
        val engine = synthesizer ?: return false
        val completion = CompletableDeferred<Unit>()
        suspension = utterance to completion
        if (engine.paused) { completion.complete(Unit); return true }
        val accepted = engine.pauseSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        if (!accepted && !engine.speaking) completion.complete(Unit)
        return accepted
    }

    private suspend fun awaitSuspended() {
        val pending = suspension ?: return
        try {
            withTimeout(2000) { pending.second.await() }
        } catch (error: Exception) {
            pending.second.completeExceptionally(error)
            if (suspension === pending) {
                listener?.onInterrupted(false)
                listener?.onInitializationFailed(ReadingStrings.ttsInitFailed)
            }
            throw error
        }
    }

    override fun resume(): Boolean { suspension = null; return synthesizer?.continueSpeaking() == true }
    override fun setSpeed(speed: Float) { this.speed = speed }
    override fun stop() {
        val previous = active?.first
        if (previous != null && suspension?.first != previous) suspension = previous to CompletableDeferred()
        active = null
        val engine = synthesizer
        engine?.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        if (engine == null || !engine.speaking) suspension?.second?.complete(Unit)
    }
    override fun acquireAudio(): Boolean { audioReleaseGeneration++; return audio.acquire() }
    override fun releaseAudio() {
        val releaseGeneration = ++audioReleaseGeneration
        if (suspension?.second?.isCompleted != false) audio.release() else scope.launch {
            try { awaitSuspended() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) {
                // 录音仍需等待同一失败的暂停屏障，不能把受理暂停误报为已静音。
            } finally { if (releaseGeneration == audioReleaseGeneration) audio.release() }
        }
    }
    override fun publish(state: ReadingPlayerState) = media.publish(state)
    override fun close() {
        if (closed) return
        closed = true
        stop()
        audio.close()
        media.close()
        synthesizer?.delegate = null
        synthesizer = null
        listener = null
    }

    private fun confirmSuspended(utterance: AVSpeechUtterance) {
        scope.launch { suspension?.takeIf { it.first == utterance }?.second?.complete(Unit) }
    }

    private fun dispatch(utterance: AVSpeechUtterance, event: (String) -> Unit) {
        scope.launch {
            val current = active
            if (!closed && current?.first == utterance) event(checkNotNull(current).second)
        }
    }
}
