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

@file:OptIn(ExperimentalWasmJsInterop::class)

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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal val recordedWebVoiceUrls = mutableSetOf<String>()

@Composable
actual fun rememberVoiceCommentRecorder(): VoiceCommentRecorder {
    val recorder = remember { WebVoiceCommentRecorder() }
    DisposableEffect(recorder) { onDispose(recorder::dispose) }
    return recorder
}

@Composable
actual fun rememberVoiceRecordPermissionRequester(onLaunched: () -> Unit, onResult: (Boolean) -> Unit): () -> Unit {
    val launched by rememberUpdatedState(onLaunched)
    val result by rememberUpdatedState(onResult)
    return remember {
        {
            // getUserMedia 的授权与设备流必须一起交给 start，不能授权后遗留一条开着的流。
            if (browserRecordingSupported()) {
                launched()
                result(true)
            } else {
                result(false)
            }
        }
    }
}

private class WebVoiceCommentRecorder : VoiceCommentRecorder {
    private val recordings = mutableSetOf<String>()
    private var generation = 0
    private var audioLease: AudioActivityLease? = null
    private val browser = createBrowserVoiceRecorder { reason ->
        audioLease?.close()
        audioLease = null
        isRecording = false
        isBusy = false
        errorMessage = when (reason) {
            "permission" -> CommentsStrings.microphonePermissionRequired
            "interrupted" -> CommentsStrings.recordingInterrupted
            else -> CommentsStrings.recordingFailed(reason)
        }
    }
    override val isSupported: Boolean get() = browserRecordingSupported()
    override var isRecording by mutableStateOf(false)
        private set
    override var isBusy by mutableStateOf(false)
        private set
    override var errorMessage by mutableStateOf<String?>(null)
        private set
    override val elapsedMs: Long get() = if (isRecording) browser.elapsed().toLong() else 0L

    override suspend fun start(): Boolean {
        if (isRecording || isBusy) return false
        errorMessage = null
        isBusy = true
        val operation = ++generation
        try {
            val acquired = AudioActivityCoordinator.acquireRecording {
                cancel()
                errorMessage = CommentsStrings.recordingInterrupted
            }
            if (operation != generation) {
                acquired?.close()
                return false
            }
            audioLease = acquired
            if (audioLease == null) {
                isBusy = false
                return false
            }
        } catch (error: Exception) {
            if (operation == generation) isBusy = false
            throw error
        }
        return suspendCancellableCoroutine { continuation ->
            browser.start { success ->
                if (!success) {
                    audioLease?.close()
                    audioLease = null
                }
                isBusy = false
                if (continuation.isActive) {
                    isRecording = success
                    continuation.resume(success)
                } else {
                    browser.cancel()
                }
            }
            continuation.invokeOnCancellation { cancel() }
        }
    }

    override suspend fun stop(): RecordedVoiceComment? {
        if (!isRecording || isBusy) return null
        isBusy = true
        isRecording = false
        return suspendCancellableCoroutine { continuation ->
            browser.stop { url, duration, size ->
                audioLease?.close()
                audioLease = null
                isBusy = false
                if (continuation.isActive && url.isNotEmpty() && size > 0 && duration > 0) {
                    recordings += url
                    recordedWebVoiceUrls += url
                    continuation.resume(RecordedVoiceComment(url, duration.toLong(), size.toLong())) { _, recorded, _ ->
                        delete(recorded)
                    }
                } else {
                    if (url.isNotEmpty()) browser.delete(url)
                    if (continuation.isActive) continuation.resume(null)
                }
            }
            continuation.invokeOnCancellation { cancel() }
        }
    }

    override fun cancel() {
        generation++
        browser.cancel()
        audioLease?.close()
        audioLease = null
        isBusy = false
        isRecording = false
    }

    fun dispose() {
        cancel()
        recordings.toList().forEach { url ->
            browser.delete(url)
            recordedWebVoiceUrls -= url
        }
        recordings.clear()
        browser.dispose()
    }

    override suspend fun readBytes(recorded: RecordedVoiceComment): ByteArray? {
        if (recorded.filePath !in recordings) return null
        val bytes = browser.read(recorded.filePath) ?: return null
        return ByteArray(bytes.length) { bytes.at(it).toByte() }
    }

    override fun delete(recorded: RecordedVoiceComment) {
        if (recordings.remove(recorded.filePath)) {
            recordedWebVoiceUrls -= recorded.filePath
            browser.delete(recorded.filePath)
        }
    }
}

private external interface BrowserVoiceBytes : JsAny {
    val length: Int
    fun at(index: Int): Int
}

private external interface BrowserVoiceRecorder : JsAny {
    fun start(done: (Boolean) -> Unit)
    fun stop(done: (String, Double, Int) -> Unit)
    fun elapsed(): Double
    fun cancel()
    fun dispose()
    fun read(url: String): BrowserVoiceBytes?
    fun delete(url: String)
}

@JsFun("() => !!(globalThis.isSecureContext && navigator.mediaDevices?.getUserMedia && globalThis.MediaRecorder)")
private external fun browserRecordingSupported(): Boolean

@JsFun(
    """(failed) => {
        let stream = null, recorder = null, chunks = [], started = 0, generation = 0;
        let startDone = null, stopDone = null, worker = null, workerUrl = null, timer = null, rejectEncoding = null;
        const files = new Map();
        const stopTracks = () => {
            if (stream) for (const track of stream.getTracks()) {
                track.onended = track.onmute = null;
                track.stop();
            }
            stream = null;
        };
        const stopWorker = () => {
            if (timer !== null) clearTimeout(timer);
            timer = null;
            if (worker) worker.terminate();
            worker = null;
            if (workerUrl) URL.revokeObjectURL(workerUrl);
            workerUrl = null;
        };
        const cancel = reason => {
            generation++;
            if (recorder) {
                recorder.ondataavailable = recorder.onstop = recorder.onerror = null;
                if (recorder.state !== 'inactive') try { recorder.stop(); } catch {}
            }
            recorder = null;
            stopTracks(); stopWorker(); chunks = []; started = 0;
            const reject = rejectEncoding;
            rejectEncoding = null;
            if (reject) reject(new Error('cancelled'));
            const start = startDone, stop = stopDone;
            startDone = stopDone = null;
            if (reason) failed(reason);
            if (start) start(false);
            if (stop) stop('', 0, 0);
        };
        const fail = reason => cancel(reason);
        const hidden = () => { if (document.hidden && (stream || worker || startDone || stopDone)) fail('interrupted'); };
        const leaving = () => { if (stream || worker || startDone || stopDone) fail('interrupted'); };
        document.addEventListener('visibilitychange', hidden);
        window.addEventListener('pagehide', leaving);
        const transcode = data => new Promise((resolve, reject) => {
            rejectEncoding = reject;
            const base = new URL('voice-codec/', document.baseURI);
            const source = "self.onmessage = async ({data}) => { try { importScripts(data.core); " +
                "const ffmpeg = await createFFmpegCore({mainScriptUrlOrBlob: data.core + '#' + btoa(JSON.stringify({wasmURL:data.wasm}))}); " +
                "ffmpeg.FS.writeFile('input', data.bytes); ffmpeg.setTimeout(120000); " +
                "ffmpeg.exec('-nostdin','-y','-i','input','-vn','-ac','1','-ar','44100','-c:a','aac','-b:a','64k','-movflags','+faststart','output.m4a'); " +
                "if (ffmpeg.ret !== 0) throw new Error('encoding'); const bytes = ffmpeg.FS.readFile('output.m4a'); " +
                "self.postMessage({bytes}, [bytes.buffer]); } catch (_) { self.postMessage({error: true}); } };";
            workerUrl = URL.createObjectURL(new Blob([source], {type:'text/javascript'}));
            worker = new Worker(workerUrl);
            const finish = (bytes, error) => { rejectEncoding = null; stopWorker(); error ? reject(new Error('encoding')) : resolve(bytes); };
            worker.onmessage = event => finish(event.data.bytes, event.data.error);
            worker.onerror = () => finish(null, true);
            timer = setTimeout(() => finish(null, true), 180000);
            worker.postMessage({core: new URL('ffmpeg-core.js', base).href, wasm: new URL('ffmpeg-core.wasm', base).href, bytes: data}, [data.buffer]);
        });
        return {
            async start(done) {
                if (startDone || stopDone || stream || worker) { done(false); return; }
                const operation = ++generation;
                startDone = done;
                try {
                    const input = await navigator.mediaDevices.getUserMedia({audio: {channelCount:1}, video:false});
                    if (operation !== generation) { input.getTracks().forEach(track => track.stop()); return; }
                    stream = input;
                    const types = ['audio/mp4;codecs=mp4a.40.2','audio/mp4','audio/webm;codecs=opus','audio/ogg;codecs=opus'];
                    const type = types.find(value => MediaRecorder.isTypeSupported(value));
                    recorder = new MediaRecorder(stream, type ? {mimeType:type,audioBitsPerSecond:64000} : {audioBitsPerSecond:64000});
                    chunks = [];
                    recorder.ondataavailable = event => { if (event.data.size) chunks.push(event.data); };
                    recorder.onerror = () => { if (operation === generation) fail('encoding'); };
                    recorder.onstop = () => { if (operation === generation) fail('interrupted'); };
                    stream.getAudioTracks().forEach(track => {
                        track.onended = track.onmute = () => { if (operation === generation) fail('interrupted'); };
                    });
                    recorder.start(); started = performance.now();
                    startDone = null; done(true);
                } catch (error) {
                    if (operation === generation) fail(error?.name === 'NotAllowedError' || error?.name === 'SecurityError' ? 'permission' : 'device');
                }
            },
            stop(done) {
                if (!recorder || recorder.state === 'inactive' || stopDone) { done('',0,0); return; }
                const operation = generation, current = recorder;
                const duration = performance.now() - started;
                stopDone = done;
                current.onstop = async () => {
                    if (operation !== generation) return;
                    if (timer !== null) clearTimeout(timer);
                    timer = null;
                    const type = current.mimeType || chunks[0]?.type || '';
                    const blob = new Blob(chunks, {type});
                    current.ondataavailable = current.onerror = current.onstop = null;
                    recorder = null; chunks = []; stopTracks();
                    try {
                        if (!blob.size || duration <= 0) throw new Error('empty');
                        let bytes = new Uint8Array(await blob.arrayBuffer());
                        if (operation !== generation) return;
                        // 容器为 MP4 不保证 AAC；未声明 AAC-LC 的默认编码也须转成上传契约。
                        if (!type.toLowerCase().startsWith('audio/mp4') || !type.toLowerCase().includes('mp4a.40.2')) bytes = await transcode(bytes);
                        if (operation !== generation) return;
                        if (bytes.length < 12 || String.fromCharCode(...bytes.subarray(4,8)) !== 'ftyp') throw new Error('format');
                        const url = URL.createObjectURL(new Blob([bytes], {type:'audio/mp4'}));
                        files.set(url, bytes);
                        stopDone = null; started = 0;
                        done(url, duration, bytes.length);
                    } catch (_) { if (operation === generation) fail('encoding'); }
                };
                timer = setTimeout(() => { if (operation === generation) fail('encoding'); }, 10000);
                try { current.stop(); } catch (_) { fail('encoding'); }
            },
            elapsed() { return started ? performance.now() - started : 0; },
            read(url) { const bytes = files.get(url); return bytes ? {length:bytes.length, at:index => bytes[index]} : null; },
            delete(url) { if (files.delete(url)) URL.revokeObjectURL(url); },
            cancel,
            dispose() {
                cancel();
                document.removeEventListener('visibilitychange', hidden);
                window.removeEventListener('pagehide', leaving);
                for (const url of files.keys()) URL.revokeObjectURL(url);
                files.clear();
            }
        };
    }""",
)
private external fun createBrowserVoiceRecorder(failed: (String) -> Unit): BrowserVoiceRecorder
