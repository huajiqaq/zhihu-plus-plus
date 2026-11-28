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

package com.github.zly2006.zhihu.reading

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.github.zly2006.zhihu.i18n.ReadingStrings
import com.github.zly2006.zhihu.util.AppLog
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class AndroidReadingSpeechEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val acquireFocus: () -> Boolean,
    private val abandonFocus: () -> Unit,
    private val wake: (Boolean) -> Unit,
) : ReadingSpeechEngine {
    override var listener: ReadingSpeechListener? = null
    override var label = ""
        private set
    override var availableLabels = emptyList<String>()
        private set
    private var tts: TextToSpeech? = null
    private var selectedEngine: String? = null
    private var languageTag = ""
    private var speed = DEFAULT_READING_PLAYBACK_SPEED
    private var generation = 0L
    private var closed = false

    override fun initialize(languageTag: String, speed: Float) {
        this.languageTag = languageTag
        this.speed = speed
        initializeEngine(selectedEngine, selectedEngine == null)
    }

    private fun initializeEngine(engineName: String?, allowPreferredSelection: Boolean) {
        if (closed) return
        val attempt = ++generation
        tts?.stop()
        tts?.shutdown()
        tts = null
        val onInit = TextToSpeech.OnInitListener { status ->
            scope.launch {
                if (closed || attempt != generation) return@launch
                val engine = tts ?: return@launch
                if (status != TextToSpeech.SUCCESS) {
                    if (engineName != null) {
                        selectedEngine = null
                        initializeEngine(null, false)
                    } else listener?.onInitializationFailed(ReadingStrings.ttsInitFailed)
                    return@launch
                }
                availableLabels = engine.engines.map { it.name }
                if (engineName == null && allowPreferredSelection) {
                    val preferred = listOf(PICO_ENGINE, SHERPA_ENGINE).firstOrNull { it in availableLabels }
                    if (preferred != null) {
                        selectedEngine = preferred
                        initializeEngine(preferred, false)
                        return@launch
                    }
                }
                val supported = runCatching {
                    val language = engine.setLanguage(Locale.forLanguageTag(languageTag))
                    if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                        val fallback = engine.setLanguage(Locale.getDefault())
                        if (fallback == TextToSpeech.LANG_MISSING_DATA || fallback == TextToSpeech.LANG_NOT_SUPPORTED) return@runCatching false
                    }
                    engine.setSpeechRate(effectiveRate(engineName))
                    engine.setPitch(1f)
                    engine.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    true
                }.getOrElse { AppLog.e("ReadingTts", "Failed to configure TTS", it); false }
                if (!supported) {
                    if (engineName != null) {
                        selectedEngine = null
                        initializeEngine(null, false)
                    } else listener?.onInitializationFailed(ReadingStrings.ttsLanguageUnsupported)
                    return@launch
                }
                selectedEngine = engineName
                label = engineName ?: engine.defaultEngine.orEmpty()
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = dispatch(attempt, utteranceId) { listener?.onStarted(it) }
                    override fun onDone(utteranceId: String?) = dispatch(attempt, utteranceId) { listener?.onFinished(it) }
                    @Suppress("OVERRIDE_DEPRECATION")
                    override fun onError(utteranceId: String?) = onError(utteranceId, TextToSpeech.ERROR)
                    override fun onError(utteranceId: String?, errorCode: Int) = dispatch(attempt, utteranceId) { fail(it, errorCode) }
                })
                listener?.onReady()
            }
        }
        tts = if (engineName == null) TextToSpeech(context, onInit) else TextToSpeech(context, onInit, engineName)
    }

    private fun dispatch(attempt: Long, id: String?, action: (String) -> Unit) {
        scope.launch { if (!closed && generation == attempt && id != null) action(id) }
    }

    override fun speak(text: String, id: String) {
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }, id)
        if (result == null || result == TextToSpeech.ERROR) fail(id, TextToSpeech.ERROR_SERVICE)
    }

    private fun fail(id: String, code: Int) {
        val kind = when (code) {
            TextToSpeech.ERROR_INVALID_REQUEST -> ReadingSpeechFailureKind.Request
            TextToSpeech.ERROR_SYNTHESIS -> ReadingSpeechFailureKind.Synthesis
            else -> ReadingSpeechFailureKind.Engine
        }
        listener?.onFailed(id, ReadingSpeechFailure(kind, if (kind == ReadingSpeechFailureKind.Engine) ReadingStrings.ttsEngineRepeatedFailure(code) else ReadingStrings.ttsSynthesisFailed(code)))
    }

    private fun effectiveRate(engine: String?): Float = (if (engine == SHERPA_ENGINE) 1.1f else 0.9f) * speed
    override fun setSpeed(speed: Float) { this.speed = speed; tts?.setSpeechRate(effectiveRate(selectedEngine)) }
    override fun stop() { tts?.stop() }
    override fun acquireAudio(): Boolean = acquireFocus()
    override fun releaseAudio() = abandonFocus()
    override fun keepAwake(enabled: Boolean) = wake(enabled)
    override fun close() {
        closed = true
        generation++
        tts?.stop()
        tts?.shutdown()
        tts = null
        listener = null
    }

    private companion object {
        const val PICO_ENGINE = "com.svox.pico"
        const val SHERPA_ENGINE = "com.k2fsa.sherpa.onnx.tts.engine"
    }
}
