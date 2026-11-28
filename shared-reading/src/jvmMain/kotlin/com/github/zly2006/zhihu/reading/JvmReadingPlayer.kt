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

import com.github.zly2006.zhihu.i18n.ReadingStrings
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private fun speechHelperFile(): File? {
    val os = System.getProperty("os.name").lowercase()
    val name = when {
        os.startsWith("windows") -> "zhihu-reading-host.exe"
        os.contains("mac") || os.startsWith("linux") -> "zhihu-reading-host"
        else -> return null
    }
    val explicit = System.getProperty("zhihu.reading.helper")?.let(::File)
    if (explicit != null) return explicit.takeIf { it.isAbsolute && it.isFile && (os.startsWith("windows") || it.canExecute()) }
    val resources = System.getProperty("compose.application.resources.dir")?.let(::File) ?: return null
    return File(resources, "reading/$name").takeIf { it.isFile && (os.startsWith("windows") || it.canExecute()) }
}

actual val isReadingPlayerSupported: Boolean get() = speechHelperFile() != null
internal actual fun createReadingSpeechEngine(scope: CoroutineScope): ReadingSpeechEngine = JvmReadingSpeechEngine(scope)

/** 固定系统 helper 的 JSON 行协议；正文永不进入 shell 或命令参数。 */
private class JvmReadingSpeechEngine(private val scope: CoroutineScope) : ReadingSpeechEngine {
    override var listener: ReadingSpeechListener? = null
    override var label = ""
        private set
    override var availableLabels = emptyList<String>()
        private set
    private val audio = ReadingAudioActivity({ listener }, ::awaitSuspended)
    private var process: Process? = null
    private var commands: Channel<String>? = null
    private var reader: Job? = null
    private var writer: Job? = null
    private var errors: Job? = null
    private var generation = 0L
    private var activeId: String? = null
    private var ready = false
    private var closed = false
    private var speed = DEFAULT_READING_PLAYBACK_SPEED
    private val requestSequence = AtomicLong()
    private val acknowledgements = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private data class PauseCheckpoint(val acknowledgement: CompletableDeferred<Unit>, val child: Process?, val generation: Long)
    private var pauseCheckpoint: PauseCheckpoint? = null
    private var audioReleaseGeneration = 0L
    private var termination: CompletableDeferred<Unit>? = null

    override fun initialize(languageTag: String, speed: Float) {
        if (closed) return
        shutdownProcess()
        this.speed = speed
        val token = ++generation
        scope.launch {
            var launched: Process? = null
            try {
                val executable = speechHelperFile() ?: error(ReadingStrings.ttsInitFailed)
                val child = withContext(Dispatchers.IO) { ProcessBuilder(executable.absolutePath).start().also { launched = it } }
                if (closed || token != generation) { disposeProcess(child); return@launch }
                process = child
                termination = null
                val output = Channel<String>(Channel.UNLIMITED)
                commands = output
                writer = scope.launch(Dispatchers.IO) {
                    try {
                        child.outputStream.bufferedWriter(Charsets.UTF_8).use { stream ->
                            for (line in output) { stream.write(line); stream.newLine(); stream.flush() }
                        }
                    } catch (failure: Exception) {
                        withContext(Dispatchers.Main) { if (!closed && token == generation) failedProcess(failure.message.orEmpty()) }
                    }
                }
                errors = scope.launch(Dispatchers.IO) {
                    // 系统引擎 stderr 可能含正文或本机路径，只排空，不复制到应用日志。
                    child.errorStream.use { stream ->
                        val buffer = ByteArray(4096)
                        while (stream.read(buffer) >= 0) Unit
                    }
                }
                reader = scope.launch(Dispatchers.IO) {
                    try {
                        child.inputStream.bufferedReader(Charsets.UTF_8).use { stream ->
                            while (true) {
                                val line = stream.readLine() ?: break
                                check(line.length <= 65536) { "Invalid speech helper response" }
                                val event = Json.parseToJsonElement(line) as? JsonObject ?: error("Invalid speech helper event")
                                if (event["event"]?.jsonPrimitive?.contentOrNull == "ack") {
                                    acknowledgements.remove(event["request"]?.jsonPrimitive?.contentOrNull)?.complete(Unit)
                                } else scope.launch(Dispatchers.Main) { if (!closed && token == generation) receive(event) }
                            }
                        }
                        withContext(Dispatchers.Main) { if (!closed && token == generation) failedProcess(ReadingStrings.ttsInitFailed) }
                    } catch (failure: Exception) {
                        withContext(Dispatchers.Main) { if (!closed && token == generation) failedProcess(failure.message.orEmpty()) }
                    }
                }
                send(buildJsonObject { put("command", "initialize"); put("version", 1); put("language", languageTag); put("speed", speed) })
            } catch (cancelled: CancellationException) {
                launched?.takeUnless { it === process }?.let(::disposeProcess)
                throw cancelled
            } catch (failure: Exception) {
                launched?.takeUnless { it === process }?.let(::disposeProcess)
                if (!closed && token == generation) listener?.onInitializationFailed(failure.message ?: ReadingStrings.ttsInitFailed)
            }
        }
    }

    private fun receive(event: JsonObject) {
        fun text(key: String) = event[key]?.jsonPrimitive?.contentOrNull.orEmpty()
        val id = text("id")
        when (text("event")) {
            "ready" -> { ready = true; label = text("label"); availableLabels = listOf(label); listener?.onReady() }
            "start" -> listener?.onStarted(id)
            "end" -> { if (activeId == id) activeId = null; listener?.onFinished(id) }
            "error" -> {
                val message = text("message").ifBlank { ReadingStrings.ttsInitFailed }
                if (id.isBlank()) listener?.onInitializationFailed(message) else listener?.onFailed(id, ReadingSpeechFailure(ReadingSpeechFailureKind.Engine, message))
            }
            "command" -> ReadingMediaCommand.entries.firstOrNull { it.name == text("action") }?.let { listener?.onCommand(it) }
        }
    }

    private fun failedProcess(message: String) {
        val id = activeId
        shutdownProcess()
        if (id != null) listener?.onFailed(id, ReadingSpeechFailure(ReadingSpeechFailureKind.Engine, message)) else listener?.onInitializationFailed(message)
    }

    private fun send(value: JsonObject): Boolean = commands?.trySend(value.toString())?.isSuccess == true
    private fun command(name: String): Boolean = send(buildJsonObject { put("command", name) })
    override fun speak(text: String, id: String) {
        pauseCheckpoint = null
        activeId = id
        if (!send(buildJsonObject { put("command", "speak"); put("id", id); put("text", text); put("speed", speed) })) {
            listener?.onFailed(id, ReadingSpeechFailure(ReadingSpeechFailureKind.Engine, ReadingStrings.ttsInitFailed))
        }
    }
    override fun pause(): Boolean {
        if (activeId == null || !ready) return false
        val request = requestSequence.incrementAndGet().toString()
        val acknowledgement = CompletableDeferred<Unit>()
        acknowledgements[request] = acknowledgement
        pauseCheckpoint = PauseCheckpoint(acknowledgement, process, generation)
        if (!send(buildJsonObject { put("command", "pause"); put("request", request) })) {
            acknowledgements.remove(request)
            acknowledgement.completeExceptionally(IllegalStateException(ReadingStrings.ttsInitFailed))
            return false
        }
        return true
    }

    private suspend fun awaitSuspended() {
        termination?.let { withTimeout(3000) { it.await() }; return }
        val checkpoint = pauseCheckpoint ?: return
        try {
            withTimeout(2000) { checkpoint.acknowledgement.await() }
        } catch (failure: Exception) {
            // 旧暂停等待绝不能终止已替换的 helper 或新语句。
            if (checkpoint !== pauseCheckpoint || checkpoint.generation != generation || checkpoint.child !== process) throw failure
            val child = checkpoint.child
            shutdownProcess()
            val shutdownGeneration = generation
            val pendingTermination = termination
            withContext(NonCancellable + Dispatchers.IO) {
                if (child != null && child.isAlive) {
                    child.destroyForcibly()
                    check(child.waitFor(2, TimeUnit.SECONDS)) { "Speech process did not stop" }
                }
            }
            // IO 等待期间可能已启动新引擎；旧失败只能结束自身等待，不能回写新会话。
            if (closed || generation != shutdownGeneration || process != null ||
                termination !== pendingTermination || pauseCheckpoint !== checkpoint
            ) throw failure
            listener?.onInterrupted(false)
            listener?.onInitializationFailed(ReadingStrings.ttsInitFailed)
            throw failure
        } finally {
            if (pauseCheckpoint === checkpoint) pauseCheckpoint = null
        }
    }
    override fun resume(): Boolean { pauseCheckpoint = null; return activeId != null && ready && command("resume") }
    override fun setSpeed(speed: Float) { this.speed = speed; send(buildJsonObject { put("command", "speed"); put("speed", speed) }) }
    override fun stop() {
        if (activeId != null && ready) pause()
        activeId = null
        command("stop")
    }
    override fun acquireAudio(): Boolean { audioReleaseGeneration++; return audio.acquire() }
    override fun releaseAudio() {
        val releaseGeneration = ++audioReleaseGeneration
        val pending = pauseCheckpoint?.acknowledgement
        if (pending == null || pending.isCompleted) {
            audio.release()
        } else {
            scope.launch {
                try {
                    awaitSuspended()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // awaitSuspended 已发布失败并确保子进程不再播放。
                } finally {
                    if (releaseGeneration == audioReleaseGeneration) audio.release()
                }
            }
        }
    }
    override fun publish(state: ReadingPlayerState) {
        send(buildJsonObject {
            put("command", "metadata"); put("active", state.hasSession); put("playing", state.status == ReadingPlaybackStatus.Playing)
            put("previous", state.canPlayPrevious); put("next", state.canPlayNext)
            put("title", state.currentItem?.displayTitle.orEmpty()); put("author", state.currentItem?.author.orEmpty())
            put("album", state.currentItem?.contentType?.displayName.orEmpty())
        })
    }
    override fun close() {
        if (closed) return
        closed = true
        shutdownProcess()
        listener = null
        val completion = termination
        if (completion == null) audio.release() else {
            val cleanup = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            cleanup.launch {
                try { completion.await() } finally { audio.release(); cleanup.cancel() }
            }
        }
    }

    private fun shutdownProcess() {
        generation++
        ready = false
        activeId = null
        val child = process
        process = null
        commands?.close(); commands = null
        acknowledgements.values.forEach { it.completeExceptionally(IllegalStateException(ReadingStrings.ttsInitFailed)) }
        acknowledgements.clear()
        writer?.cancel(); writer = null
        reader?.cancel(); reader = null
        errors?.cancel(); errors = null
        if (child != null) termination = disposeProcess(child)
    }

    private fun disposeProcess(child: Process): CompletableDeferred<Unit> {
        val completion = CompletableDeferred<Unit>()
        // 独立关闭任务必须活过宿主 scope 的取消；仅终止本引擎创建的进程。
        Thread({
            try {
                runCatching { child.outputStream.close() }
                if (!child.waitFor(750, TimeUnit.MILLISECONDS)) {
                    child.destroy()
                    if (!child.waitFor(2, TimeUnit.SECONDS)) child.destroyForcibly().waitFor()
                }
            } finally {
                if (child.isAlive) child.destroyForcibly()
                runCatching { child.inputStream.close() }
                runCatching { child.errorStream.close() }
                if (!child.isAlive) completion.complete(Unit) else completion.completeExceptionally(IllegalStateException("Speech process did not stop"))
            }
        }, "reading-helper-close").apply { isDaemon = true; start() }
        return completion
    }
}
