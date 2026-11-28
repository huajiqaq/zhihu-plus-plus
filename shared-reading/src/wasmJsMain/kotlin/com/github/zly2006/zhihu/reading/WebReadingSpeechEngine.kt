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

@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.github.zly2006.zhihu.reading

import com.github.zly2006.zhihu.i18n.ReadingStrings
import kotlin.js.JsAny
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

actual val isReadingPlayerSupported: Boolean get() = browserSpeechAvailable()
internal actual fun createReadingSpeechEngine(scope: CoroutineScope): ReadingSpeechEngine = WebReadingSpeechEngine(scope)

private class WebReadingSpeechEngine(private val scope: CoroutineScope) : ReadingSpeechEngine {
    override var listener: ReadingSpeechListener? = null
    override val label: String get() = "Web Speech"
    override val availableLabels: List<String> get() = listOf(label)
    private var handle: BrowserSpeech? = null
    private var language = ""
    private var speed = DEFAULT_READING_PLAYBACK_SPEED
    private var closed = false
    private var activeId: String? = null
    private var suspension: Pair<String, CompletableDeferred<Unit>>? = null
    private var audioReleaseGeneration = 0L
    private val audio = ReadingAudioActivity(listener = { listener }, awaitSuspended = ::awaitSuspended)

    override fun acquireAudio(): Boolean { audioReleaseGeneration++; return audio.acquire() }
    override fun releaseAudio() {
        val releaseGeneration = ++audioReleaseGeneration
        if (suspension?.second?.isCompleted != false) audio.release() else scope.launch {
            try { awaitSuspended() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) {
                // 失败已经留给播放器和录音申请方处理。
            } finally { if (releaseGeneration == audioReleaseGeneration) audio.release() }
        }
    }

    private suspend fun awaitSuspended() {
        val pending = suspension ?: return
        try { withTimeout(2000) { pending.second.await() } } catch (error: Exception) {
            pending.second.completeExceptionally(error)
            if (suspension === pending) {
                listener?.onInterrupted(false)
                listener?.onInitializationFailed(ReadingStrings.ttsInitFailed)
            }
            throw error
        }
    }

    override fun initialize(languageTag: String, speed: Float) {
        if (closed) return
        handle?.close()
        language = languageTag
        this.speed = speed
        if (!browserSpeechAvailable()) { listener?.onInitializationFailed(ReadingStrings.ttsInitFailed); return }
        handle = createBrowserSpeech { type, id, detail ->
            scope.launch {
                if (closed) return@launch
                if (type in setOf("pause", "end", "error", "cancel")) suspension?.takeIf { it.first == id }?.second?.complete(Unit)
                when (type) {
                    "start" -> listener?.onStarted(id)
                    "end" -> { if (activeId == id) activeId = null; listener?.onFinished(id) }
                    "error" -> listener?.onFailed(id, ReadingSpeechFailure(
                        if (detail == "invalid-argument" || detail == "text-too-long" || detail == "not-allowed") ReadingSpeechFailureKind.Request else ReadingSpeechFailureKind.Engine,
                        detail,
                    ))
                    "command" -> ReadingMediaCommand.entries.firstOrNull { it.name == detail }?.let { listener?.onCommand(it) }
                    "pagehide" -> listener?.onInterrupted(false)
                }
            }
        }
        listener?.onReady()
    }

    override fun speak(text: String, id: String) {
        val engine = handle
        if (engine == null) listener?.onFailed(id, ReadingSpeechFailure(ReadingSpeechFailureKind.Engine, ReadingStrings.ttsInitFailed))
        else { suspension = null; activeId = id; engine.speak(text, id, language, speed.toDouble()) }
    }
    override fun pause(): Boolean {
        val id = activeId ?: return false
        suspension = id to CompletableDeferred()
        return handle?.pause() == true
    }
    override fun resume(): Boolean { suspension = null; return handle?.resume() == true }
    override fun setSpeed(speed: Float) { this.speed = speed }
    override fun stop() {
        val id = activeId
        if (id != null && suspension?.first != id) suspension = id to CompletableDeferred()
        activeId = null
        handle?.stop()
    }
    override fun publish(state: ReadingPlayerState) {
        val item = state.currentItem
        handle?.publish(item?.displayTitle.orEmpty(), item?.author.orEmpty(), item?.contentType?.displayName.orEmpty(), state.hasSession,
            state.status == ReadingPlaybackStatus.Playing, state.canPlayPrevious, state.canPlayNext)
    }
    override fun close() { closed = true; audio.release(); handle?.close(); handle = null; listener = null }
}

private external interface BrowserSpeech : JsAny {
    fun speak(text: String, id: String, language: String, rate: Double)
    fun pause(): Boolean
    fun resume(): Boolean
    fun stop()
    fun publish(title: String, author: String, album: String, active: Boolean, playing: Boolean, previous: Boolean, next: Boolean)
    fun close()
}

@JsFun("() => typeof window !== 'undefined' && !!window.speechSynthesis && typeof SpeechSynthesisUtterance === 'function'")
private external fun browserSpeechAvailable(): Boolean

@JsFun("""(event) => {
    const synth = window.speechSynthesis;
    const media = navigator.mediaSession;
    let current = null, currentId = '', voices = synth.getVoices(), serial = 0, closed = false, registered = false, playing = false;
    const actions = new Set();
    const updateVoices = () => { voices = synth.getVoices(); };
    const stop = () => {
        ++serial;
        const cancelled = current, cancelledId = currentId;
        if (cancelled) {
            cancelled.onstart = cancelled.onpause = null;
            cancelled.onend = cancelled.onerror = () => event('cancel', cancelledId, '');
        }
        current = null; currentId = '';
        synth.cancel();
        // cancel does not reset the browser's global paused flag.
        if (synth.paused) synth.resume();
        if (cancelled && !synth.speaking && !synth.pending) event('cancel', cancelledId, '');
    };
    const clearMedia = () => {
        if (!media || !registered) return;
        for (const action of actions) { try { media.setActionHandler(action, null); } catch (_) {} }
        actions.clear();
        media.metadata = null;
        media.playbackState = 'none';
        registered = false;
    };
    const setAction = (action, command, enabled = true) => {
        try { media.setActionHandler(action, enabled ? () => event('command', '', command) : null); actions.add(action); }
        catch (_) { /* This browser does not support this individual MediaSession action. */ }
    };
    const pagehide = () => { stop(); clearMedia(); event('pagehide', '', ''); };
    synth.addEventListener('voiceschanged', updateVoices);
    window.addEventListener('pagehide', pagehide);
    return {
        speak(text, id, language, rate) {
            stop();
            if (closed) return;
            const generation = serial;
            const utterance = new SpeechSynthesisUtterance(text);
            current = utterance; currentId = id;
            const exact = voices.find(v => v.lang.toLowerCase() === language.toLowerCase());
            const base = voices.find(v => v.lang.split('-')[0] === language.split('-')[0]);
            if (exact || base) utterance.voice = exact || base;
            utterance.lang = language;
            utterance.rate = rate;
            utterance.onstart = () => { if (generation === serial && !closed) event('start', id, ''); };
            utterance.onpause = () => { if (generation === serial && !closed) event('pause', id, ''); };
            utterance.onend = () => { if (generation === serial && !closed) { current = null; event('end', id, ''); } };
            utterance.onerror = e => {
                if (generation === serial && !closed) { current = null; event('error', id, String(e.error || 'synthesis-failed')); }
            };
            try { synth.speak(utterance); }
            catch (e) { current = null; event('error', id, String(e.message || 'synthesis-failed')); }
        },
        pause() {
            if (!current) return false;
            if (synth.paused) { event('pause', currentId, ''); return true; }
            synth.pause();
            return true;
        },
        resume() { if (!current || !synth.paused) return false; synth.resume(); return true; },
        stop,
        publish(title, author, album, active, isPlaying, previous, next) {
            playing = isPlaying;
            if (!media) return;
            if (!active) { clearMedia(); return; }
            if (typeof MediaMetadata === 'function') media.metadata = new MediaMetadata({title, artist: author, album});
            media.playbackState = playing ? 'playing' : 'paused';
            registered = true;
            setAction('play', 'Play'); setAction('pause', 'Pause'); setAction('stop', 'Stop');
            setAction('previoustrack', 'Previous', previous); setAction('nexttrack', 'Next', next);
        },
        close() {
            closed = true; stop(); clearMedia();
            synth.removeEventListener('voiceschanged', updateVoices);
            window.removeEventListener('pagehide', pagehide);
        }
    };
}""")
private external fun createBrowserSpeech(event: (String, String, String) -> Unit): BrowserSpeech
