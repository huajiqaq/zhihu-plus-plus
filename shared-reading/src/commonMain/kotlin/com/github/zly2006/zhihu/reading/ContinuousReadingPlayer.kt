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

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.github.zly2006.zhihu.i18n.AppLocale
import com.github.zly2006.zhihu.i18n.ReadingStrings
import com.github.zly2006.zhihu.i18n.WidgetsStrings
import com.github.zly2006.zhihu.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 所有端共用的连续朗读状态机；平台只管理系统音频、语音和宿主生命周期。 */
internal class ContinuousReadingPlayer(
    private val scope: CoroutineScope,
    private val resolver: ReadingContentResolver,
    private val engine: ReadingSpeechEngine,
    private val showMessage: (String) -> Unit,
    private val onState: (ReadingPlayerState) -> Unit = {},
    private val onStopped: () -> Unit = {},
) : ReadingPlayerController, ReadingSpeechListener {
    private val mutableState = mutableStateOf(ReadingPlayerState())
    override val state: State<ReadingPlayerState> get() = mutableState
    private var queue = emptyList<ReadingQueueItem>()
    private var sourceId: String? = null
    private var preferences = ReadingPreferences()
    private var speed = DEFAULT_READING_PLAYBACK_SPEED
    private var index = -1
    private var chunks = emptyList<String>()
    private var chunkIndex = 0
    private var includeTransition = false
    private var status = ReadingPlaybackStatus.Idle
    private var errorMessage: String? = null
    private var loading: Job? = null
    private var generation = 0L
    private var sequence = 0L
    private var activeUtterance: String? = null
    private var playWhenReady = false
    private var resumeAfterInterruption = false
    private var retainedUtterance = false
    private var ready = false
    private var initializing = false
    private var initializationFailed = false
    private var languageTag: String? = null
    private var engineRecoveryAttempts = 0
    private var synthesisRecoveryAttempts = 0
    private var closed = false

    init { engine.listener = this }

    fun initialize() {
        if (!closed && !initializing && !ready) restartEngine()
    }

    override fun start(request: ReadingStartRequest) {
        if (closed || request.queue.isEmpty()) return
        invalidateSpeech()
        playWhenReady = true
        resumeAfterInterruption = false
        engineRecoveryAttempts = 0
        synthesisRecoveryAttempts = 0
        preferences = request.preferences.normalized()
        speed = normalizeReadingPlaybackSpeed(request.playbackSpeed)
        if (ready) engine.setSpeed(speed)
        sourceId = request.sourceId
        queue = request.queue.distinctBy(ReadingQueueItem::key).take(preferences.queueLimit)
        index = request.startIndex.coerceIn(queue.indices)
        chunks = emptyList()
        chunkIndex = 0
        includeTransition = false
        errorMessage = null
        status = if (ready) ReadingPlaybackStatus.Loading else ReadingPlaybackStatus.Initializing
        publish()
        if (ready) loadCurrent(false) else if (initializationFailed || !initializing) restartEngine()
    }

    override fun togglePlayPause() {
        when (status) {
            ReadingPlaybackStatus.Playing, ReadingPlaybackStatus.Loading, ReadingPlaybackStatus.Initializing -> pause()
            ReadingPlaybackStatus.Paused, ReadingPlaybackStatus.Error -> play()
            ReadingPlaybackStatus.Idle -> Unit
        }
    }

    fun play() {
        if (closed || index !in queue.indices || (playWhenReady && state.value.isActivelyPlaying)) return
        if (initializationFailed) {
            engineRecoveryAttempts = 0
            synthesisRecoveryAttempts = 0
        }
        playWhenReady = true
        resumeAfterInterruption = false
        errorMessage = null
        if (!ready) {
            status = ReadingPlaybackStatus.Initializing
            publish()
            if (!initializing || initializationFailed) restartEngine()
            return
        }
        engine.keepAwake(true)
        if (chunks.isEmpty()) {
            loadCurrent(includeTransition)
            return
        }
        if (!acquireAudio()) return
        if (languageTag != AppLocale.languageTag) { restartEngine(); return }
        if (retainedUtterance) {
            retainedUtterance = false
            if (engine.resume()) {
                status = ReadingPlaybackStatus.Playing
                publish()
                return
            }
            activeUtterance = null
            engine.stop()
        }
        speakCurrent()
    }

    fun pause(releaseAudio: Boolean = true, resumeAutomatically: Boolean = false) {
        if (closed || queue.isEmpty()) return
        generation++
        playWhenReady = false
        resumeAfterInterruption = resumeAutomatically
        loading?.cancel()
        retainedUtterance = activeUtterance != null && (retainedUtterance || engine.pause())
        if (!retainedUtterance) {
            activeUtterance = null
            engine.stop()
        }
        status = ReadingPlaybackStatus.Paused
        if (releaseAudio) engine.releaseAudio()
        engine.keepAwake(false)
        publish()
    }

    override fun playPrevious() { if (index > 0) playAt(index - 1) }
    override fun playNext() { if (index in 0 until queue.lastIndex) playAt(index + 1) }

    override fun playAt(index: Int) {
        if (closed || index !in queue.indices) return
        invalidateSpeech()
        this.index = index
        chunks = emptyList()
        chunkIndex = 0
        includeTransition = false
        playWhenReady = true
        resumeAfterInterruption = false
        engineRecoveryAttempts = 0
        synthesisRecoveryAttempts = 0
        errorMessage = null
        if (ready) loadCurrent(false) else {
            status = ReadingPlaybackStatus.Initializing
            publish()
            if (!initializing || initializationFailed) restartEngine()
        }
    }

    override fun setPlaybackSpeed(speed: Float) {
        if (closed) return
        val normalized = normalizeReadingPlaybackSpeed(speed)
        if (normalized == this.speed) return
        this.speed = normalized
        if (ready) engine.setSpeed(normalized)
        if (retainedUtterance || (status == ReadingPlaybackStatus.Playing && playWhenReady)) {
            activeUtterance = null
            retainedUtterance = false
            engine.stop()
            if (playWhenReady && chunkIndex in chunks.indices) speakCurrent() else publish()
        } else publish()
    }

    private fun loadCurrent(transition: Boolean) {
        includeTransition = transition
        if (!playWhenReady) {
            status = ReadingPlaybackStatus.Paused
            engine.keepAwake(false)
            publish()
            return
        }
        if (index !in queue.indices) { stop(); return }
        invalidateSpeech()
        engine.keepAwake(true)
        val loadGeneration = generation
        val item = queue[index]
        chunks = emptyList()
        chunkIndex = 0
        status = ReadingPlaybackStatus.Loading
        errorMessage = null
        publish()
        loading = scope.launch {
            try {
                val (resolvedItem, content) = withContext(Dispatchers.Default) { resolver.resolve(item, preferences) }
                if (closed || loadGeneration != generation) return@launch
                queue = queue.toMutableList().also { it[index] = resolvedItem.withoutBody() }
                if (languageTag != AppLocale.languageTag) { restartEngine(); return@launch }
                val speech = buildReadingSpeechText(content, preferences)
                check(speech.isNotBlank()) { ReadingStrings.nothingToRead }
                val prefix = if (transition && index > 0) {
                    renderReadingTransition(preferences.effectiveTransitionText, resolvedItem, index, queue.size)
                } else ""
                chunks = splitReadingSpeechIntoChunks(listOf(prefix, speech).filter(String::isNotBlank).joinToString("\n"))
                includeTransition = false
                if (acquireAudio()) speakCurrent()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (!closed && loadGeneration == generation && playWhenReady) skipFailedItem(failure)
            }
        }
    }

    private fun acquireAudio(): Boolean {
        if (engine.acquireAudio()) return true
        playWhenReady = false
        status = ReadingPlaybackStatus.Paused
        errorMessage = ReadingStrings.audioFocusUnavailable
        engine.keepAwake(false)
        publish()
        return false
    }

    private fun speakCurrent() {
        if (closed || !playWhenReady) return
        if (chunkIndex !in chunks.indices || index !in queue.indices) { finishItem(); return }
        val id = "reading_${generation}_${index}_${chunkIndex}_${++sequence}"
        activeUtterance = id
        retainedUtterance = false
        status = ReadingPlaybackStatus.Playing
        errorMessage = null
        publish()
        engine.speak(chunks[chunkIndex], id)
    }

    private fun finishItem() {
        if (closed || !playWhenReady) return
        if (index < queue.lastIndex) {
            index++
            engineRecoveryAttempts = 0
            synthesisRecoveryAttempts = 0
            loadCurrent(true)
        } else stop()
    }

    private fun skipFailedItem(failure: Exception) {
        if (closed || !playWhenReady) return
        AppLog.e("ReadingPlayer", "Failed to read ${queue.getOrNull(index)?.key}", failure)
        showMessage(ReadingStrings.skippedItem(failure.message ?: WidgetsStrings.unknownError))
        if (index < queue.lastIndex) {
            index++
            engineRecoveryAttempts = 0
            synthesisRecoveryAttempts = 0
            loadCurrent(false)
        } else stop()
    }

    private fun invalidateSpeech() {
        generation++
        loading?.cancel()
        loading = null
        activeUtterance = null
        retainedUtterance = false
        engine.stop()
    }

    override fun stop() {
        if (closed) return
        invalidateSpeech()
        playWhenReady = false
        resumeAfterInterruption = false
        engineRecoveryAttempts = 0
        synthesisRecoveryAttempts = 0
        engine.releaseAudio()
        engine.keepAwake(false)
        queue = emptyList()
        sourceId = null
        index = -1
        chunks = emptyList()
        chunkIndex = 0
        includeTransition = false
        status = ReadingPlaybackStatus.Idle
        errorMessage = null
        publish()
        onStopped()
    }

    fun close() {
        if (closed) return
        stop()
        closed = true
        engine.listener = null
        engine.close()
    }

    private fun restartEngine() {
        activeUtterance = null
        retainedUtterance = false
        ready = false
        initializing = true
        initializationFailed = false
        languageTag = AppLocale.languageTag
        engine.initialize(checkNotNull(languageTag), speed)
    }

    override fun onReady() {
        if (closed) return
        ready = true
        initializing = false
        initializationFailed = false
        errorMessage = null
        when {
            queue.isEmpty() -> { status = ReadingPlaybackStatus.Idle; publish() }
            playWhenReady -> {
                if (chunks.isEmpty()) loadCurrent(includeTransition) else {
                    engine.keepAwake(true)
                    if (acquireAudio()) speakCurrent()
                }
            }
            else -> { status = ReadingPlaybackStatus.Paused; publish() }
        }
    }

    override fun onInitializationFailed(message: String) {
        if (closed) return
        ready = false
        initializing = false
        initializationFailed = true
        playWhenReady = false
        status = if (queue.isEmpty()) ReadingPlaybackStatus.Idle else ReadingPlaybackStatus.Error
        errorMessage = message.takeIf { queue.isNotEmpty() }
        engine.releaseAudio()
        engine.keepAwake(false)
        publish()
    }

    private fun isCurrent(id: String): Boolean = !closed && playWhenReady && activeUtterance == id
    override fun onStarted(id: String) {
        if (!isCurrent(id)) return
        status = ReadingPlaybackStatus.Playing
        publish()
    }

    override fun onFinished(id: String) {
        if (closed || activeUtterance != id) return
        activeUtterance = null
        retainedUtterance = false
        engineRecoveryAttempts = 0
        synthesisRecoveryAttempts = 0
        chunkIndex++
        // pause 请求与系统结束事件可以交错；记住真实完成，不在用户暂停后播下一句。
        if (!playWhenReady) { publish(); return }
        if (chunkIndex < chunks.size) speakCurrent() else finishItem()
    }

    override fun onFailed(id: String, failure: ReadingSpeechFailure) {
        if (closed || activeUtterance != id) return
        activeUtterance = null
        retainedUtterance = false
        if (!playWhenReady) {
            // 异步 pause 失败也必须停止发声；保留分块，等待用户决定重试。
            engine.stop()
            resumeAfterInterruption = false
            onInitializationFailed(failure.message)
            return
        }
        when (failure.kind) {
            ReadingSpeechFailureKind.Request -> { skipFailedItem(IllegalStateException(failure.message)); return }
            ReadingSpeechFailureKind.Synthesis -> {
                if (synthesisRecoveryAttempts++ >= 1) { skipFailedItem(IllegalStateException(failure.message)); return }
            }
            ReadingSpeechFailureKind.Engine -> {
                if (engineRecoveryAttempts++ >= 1) { onInitializationFailed(failure.message); return }
            }
        }
        generation++
        loading?.cancel()
        status = ReadingPlaybackStatus.Initializing
        errorMessage = null
        engine.releaseAudio()
        engine.keepAwake(false)
        publish()
        restartEngine()
    }

    override fun onInterrupted(resumeAutomatically: Boolean) {
        val shouldResume = resumeAutomatically && (playWhenReady || resumeAfterInterruption)
        if (!resumeAutomatically) {
            // 系统对象失效或路由变化后不能继续旧 utterance，保留分块供用户重新开始。
            activeUtterance = null
            retainedUtterance = false
            engine.stop()
        }
        pause(releaseAudio = !resumeAutomatically, resumeAutomatically = shouldResume)
    }

    override fun onInterruptionEnded() {
        if (resumeAfterInterruption && !closed) { resumeAfterInterruption = false; play() }
    }

    override fun onCommand(command: ReadingMediaCommand) {
        when (command) {
            ReadingMediaCommand.Play -> play()
            ReadingMediaCommand.Pause -> pause()
            ReadingMediaCommand.Previous -> playPrevious()
            ReadingMediaCommand.Next -> playNext()
            ReadingMediaCommand.Stop -> stop()
        }
    }

    fun publish() {
        if (closed) return
        val next = ReadingPlayerState(
            status = status, queue = queue.map(ReadingQueueItem::withoutBody), currentIndex = index,
            currentChunkIndex = chunkIndex, totalChunks = chunks.size, errorMessage = errorMessage,
            engineLabel = engine.label, availableEngineLabels = engine.availableLabels,
            sourceId = sourceId, playbackSpeed = speed,
        )
        mutableState.value = next
        engine.publish(next)
        onState(next)
    }
}
