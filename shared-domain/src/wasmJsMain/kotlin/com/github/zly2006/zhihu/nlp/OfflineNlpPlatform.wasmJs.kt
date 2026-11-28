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

package com.github.zly2006.zhihu.nlp

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.JsAny
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

actual fun createOfflineNlpPlatform(): OfflineNlpPlatform = WasmOfflineNlpPlatform()

private class WasmOfflineNlpPlatform : OfflineNlpPlatform {
    private val mutex = Mutex()
    private val runtime by lazy { createWebNlpRuntime() }
    private var session: WasmOfflineNlpSession? = null

    override val unsupportedReason: OfflineNlpFailure?
        get() = webNlpUnsupportedReason().takeIf { it.isNotEmpty() }?.let { OfflineNlpFailure(OfflineNlpErrorCode.valueOf(it)) }

    override fun normalizeNfd(text: String): String = webNlpNormalizeNfd(text)

    override fun unicodeCategory(codePoint: Int): OfflineNlpUnicodeCategory = OfflineNlpUnicodeCategory.entries[webNlpUnicodeCategory(codePoint)]

    override suspend fun prepareModel(
        spec: OfflineNlpModelSpec,
        onProgress: (OfflineNlpModelState) -> Unit,
    ): OfflineNlpModelAssets = mutex.withLock {
        unsupportedReason?.let { throw OfflineNlpException(it) }
        val result = runtime.call("prepare", spec.toWebJson(), onProgress)
        WasmOfflineNlpAssets(this, spec, checkNotNull(result.tokenizerJson))
    }

    override suspend fun openSession(
        assets: OfflineNlpModelAssets,
        onInvalidated: (failure: OfflineNlpFailure?) -> Unit,
    ): OfflineNlpSession = mutex.withLock {
        require(assets is WasmOfflineNlpAssets && assets.owner === this) { offlineNlpError(OfflineNlpErrorCode.ASSETS_MISMATCH) }
        session?.takeUnless { it.closed }?.let { existing ->
            require(existing.spec == assets.spec) { offlineNlpError(OfflineNlpErrorCode.SESSION_BUSY) }
            return@withLock existing
        }
        val result = runtime.call("open", assets.spec.toWebJson())
        WasmOfflineNlpSession(runtime, assets.spec, result.generation, onInvalidated).also { session = it }
    }

    override suspend fun deleteModel(spec: OfflineNlpModelSpec) = mutex.withLock {
        session?.close()
        session = null
        runtime.shutdown()
        runtime.call("delete", spec.toWebJson())
        Unit
    }
}

private data class WasmOfflineNlpAssets(
    val owner: WasmOfflineNlpPlatform,
    val spec: OfflineNlpModelSpec,
    override val tokenizerJson: String,
) : OfflineNlpModelAssets

private class WasmOfflineNlpSession(
    private val runtime: WebNlpRuntime,
    val spec: OfflineNlpModelSpec,
    private val generation: Int,
    onInvalidated: (failure: OfflineNlpFailure?) -> Unit,
) : OfflineNlpSession {
    private val mutex = Mutex()
    var closed: Boolean = false
        private set

    init {
        runtime.onInvalidated(generation) { message ->
            if (!closed) {
                closed = true
                onInvalidated(message?.let(::decodeWebNlpFailure))
            }
        }
    }

    override suspend fun encode(inputIds: LongArray, attentionMask: LongArray, tokenTypeIds: LongArray): FloatArray = mutex.withLock {
        check(!closed) { offlineNlpError(OfflineNlpErrorCode.SESSION_CLOSED) }
        require(inputIds.size in 1..spec.maxTokens && attentionMask.size == inputIds.size && tokenTypeIds.size == inputIds.size) {
            offlineNlpError(OfflineNlpErrorCode.INVALID_INPUT)
        }
        require(inputIds.all { it >= 0 } && attentionMask.all { it == 0L || it == 1L } && tokenTypeIds.all { it == 0L || it == 1L }) {
            offlineNlpError(OfflineNlpErrorCode.INVALID_INPUT)
        }
        val payload = buildJsonObject {
            put("generation", generation)
            put("inputIds", inputIds.toWebLongs())
            put("attentionMask", attentionMask.toWebLongs())
            put("tokenTypeIds", tokenTypeIds.toWebLongs())
        }.toString()
        val result = runtime.call("encode", payload)
        val data = checkNotNull(result.data)
        check(webNlpArrayLength(data) == inputIds.size * spec.embeddingDimension) { offlineNlpError(OfflineNlpErrorCode.INVALID_OUTPUT) }
        val flat = FloatArray(webNlpArrayLength(data)) { webNlpArrayValue(data, it).toFloat() }
        poolAndNormalizeTokenEmbeddings(flat, inputIds.size, spec.embeddingDimension, attentionMask)
    }

    override suspend fun close() = mutex.withLock {
        if (closed) return@withLock
        closed = true
        runtime.clearInvalidation(generation)
        try {
            runtime.call("close", buildJsonObject { put("generation", generation) }.toString())
        } finally {
            // Releasing a session does not shrink the Emscripten heap; terminate its dedicated worker as well.
            runtime.terminate(generation)
        }
        Unit
    }
}

private fun LongArray.toWebLongs(): JsonArray = JsonArray(map { JsonPrimitive(it.toString()) })

private fun OfflineNlpModelSpec.toWebJson(): String = buildJsonObject {
    put("id", id)
    put("revision", revision)
    put(
        "files",
        JsonArray(files.map { file ->
            buildJsonObject {
                put("name", file.name)
                put("url", file.url)
                put("expectedBytes", file.expectedBytes)
                put("sha256", file.sha256)
            }
        }),
    )
}.toString()

private external interface WebNlpReply : JsAny {
    val tokenizerJson: String?
    val generation: Int
    val data: JsAny?
}

private external interface WebNlpOperation : JsAny {
    fun cancel()
}

private external interface WebNlpRuntime : JsAny {
    fun request(
        operation: String,
        payload: String,
        progress: (String, String, Double, Double) -> Unit,
        done: (Boolean, WebNlpReply?, String) -> Unit,
    ): WebNlpOperation

    fun onInvalidated(generation: Int, callback: (String?) -> Unit)

    fun clearInvalidation(generation: Int)

    fun terminate(generation: Int)

    fun shutdown()
}

private suspend fun WebNlpRuntime.call(
    operation: String,
    payload: String,
    onProgress: (OfflineNlpModelState) -> Unit = {},
): WebNlpReply = suspendCancellableCoroutine { continuation ->
    val request = request(
        operation,
        payload,
        { stage, file, downloaded, total ->
            if (continuation.isActive) {
                when (stage) {
                    "downloading" -> onProgress(OfflineNlpModelState.Downloading(file, downloaded.toLong(), total.toLong()))
                    "verifying" -> onProgress(OfflineNlpModelState.Verifying(file))
                }
            }
        },
    ) { success, value, error ->
        if (continuation.isActive) {
            if (success && value != null) continuation.resume(value) else continuation.resumeWithException(OfflineNlpException(decodeWebNlpFailure(error)))
        }
    }
    continuation.invokeOnCancellation { request.cancel() }
}

/** Worker 错误只允许短文件名、HTTP 状态与配额数值；拒绝携带正文或任意异常字符串。 */
private fun decodeWebNlpFailure(encoded: String): OfflineNlpFailure {
    if (encoded.length > 1024) return OfflineNlpFailure(OfflineNlpErrorCode.OPERATION_FAILED)
    return runCatching {
        Json.decodeFromString<OfflineNlpFailure>(encoded).also {
            require(it.fileName.length <= 160 && it.fileName.all { character -> character.isLetterOrDigit() || character in "._/-" })
            require(it.httpStatus in 0..599 && it.requiredMiB in 0..1_048_576 && it.currentVersion.isEmpty())
        }
    }.getOrElse { OfflineNlpFailure(OfflineNlpErrorCode.OPERATION_FAILED) }
}

@JsFun("(text) => text.normalize('NFD')")
private external fun webNlpNormalizeNfd(text: String): String

@JsFun("""(codePoint) => { const value = String.fromCodePoint(codePoint); return /\p{C}/u.test(value) ? 0 : /\p{Mn}/u.test(value) ? 1 : /\p{P}/u.test(value) ? 2 : 3; }""")
private external fun webNlpUnicodeCategory(codePoint: Int): Int

@JsFun("(data) => data.length")
private external fun webNlpArrayLength(data: JsAny): Int

@JsFun("(data, index) => data[index]")
private external fun webNlpArrayValue(data: JsAny, index: Int): Double

@JsFun(
    """() => {
        if (!globalThis.isSecureContext) return 'HTTPS_REQUIRED';
        if (!globalThis.Worker || !globalThis.WebAssembly || !globalThis.BigInt64Array) return 'WEB_RUNTIME_UNSUPPORTED';
        if (!navigator.storage?.getDirectory || !navigator.storage?.estimate || !globalThis.indexedDB || !navigator.locks || !globalThis.caches || !globalThis.crypto?.subtle) {
            return 'WEB_STORAGE_UNSUPPORTED';
        }
        return '';
    }""",
)
private external fun webNlpUnsupportedReason(): String

@JsFun(
    """() => {
        const version = 'ort-1.31.1-hashes-2.4.0-v1';
        const workerHash = '0993988a98e00d8ef29a35129ceb809d4683164f70bc20ce66d45bab54e70cc4';
        const workerSize = 16611;
        const cacheName = 'zhihu++:offline-nlp-runtime:' + version + ':' + workerHash;
        const base = new URL('nlp/', window.zhihuAppResourceBase || new URL('./', location.href));
        const debug = window.zhihuAppCache?.snapshot().mode === 'debug';
        const artifacts = [
            ['offline-nlp-worker.mjs', workerSize, workerHash],
            ['ort/ort.wasm.min.mjs', 53725, 'c21d9c326d453a4a61398270c804d5979e118540597cb0c07f7b3d64e6098234'],
            ['ort/ort-wasm-simd-threaded.mjs', 24738, '30ed0b5cd1182ede931fea448afb567d4dc705ec957af09f108a2be5f770dac3'],
            ['ort/ort-wasm-simd-threaded.wasm', 14848852, '4b884c203b0fa48d057be3332c65513ff85aa3c5bddd6946acc0fab7ecda001d'],
            ['hashes/sha2.js', 18997, '471746bba6ec4c6238ca41358d1d3b40b6ff31cf3363f0b4d550c649c1a8e83b'],
            ['hashes/_md.js', 9175, '60cf3010fda89e3e4d3f0e7ff1ce249e9c467f34b4fd41b6fd6101d9f69be763'],
            ['hashes/_u64.js', 4794, 'b09da8c07fe8187c07649494cdb7cd0bcf13df90b506a9473d19e4d5f8c2e102'],
            ['hashes/utils.js', 26035, '037ad49adb78168b6b699598fd33f85f7877456b1d28b4d032e2a1a16807947c']
        ];
        let worker = null, generation = 0, opening = null, nextId = 1;
        let urls = [], init = null, invalidation = null, lastInvalidation = null;
        const pending = new Map(), cancellations = new Map();
        const failure = (code, parameters = {}) => ({code, ...parameters});
        const errorFailure = error => {
            if (error && typeof error.code === 'string' && /^[A-Z_]{1,48}$/.test(error.code)) {
                const fileName = typeof error.fileName === 'string' && /^[A-Za-z0-9._/-]{0,160}$/.test(error.fileName) ? error.fileName : '';
                const httpStatus = Number.isInteger(error.httpStatus) && error.httpStatus >= 0 && error.httpStatus <= 599 ? error.httpStatus : 0;
                const requiredMiB = Number.isInteger(error.requiredMiB) && error.requiredMiB >= 0 && error.requiredMiB <= 1048576 ? error.requiredMiB : 0;
                return failure(error.code, {fileName, httpStatus, requiredMiB});
            }
            if (error?.name === 'QuotaExceededError') return failure('QUOTA_EXCEEDED');
            if (error?.name === 'SecurityError' || error?.name === 'NotAllowedError') return failure('STORAGE_DENIED');
            if (error?.name === 'NoModificationAllowedError') return failure('STORAGE_BUSY');
            if (error?.name === 'AbortError') return failure('CANCELLED');
            return failure('WORKER_FAILED');
        };
        function settle(id, error, result) {
            const job = pending.get(id);
            if (!job) return;
            pending.delete(id);
            if (job.timer) clearTimeout(job.timer);
            job.done(!error, result || null, error ? JSON.stringify(errorFailure(error)) : '');
        }
        function stop(error, expected, invalidatedMessage = errorFailure(error)) {
            if (expected !== undefined && expected !== generation) return;
            const stoppedGeneration = generation;
            const listener = invalidation?.generation === stoppedGeneration ? invalidation : null;
            invalidation = null;
            lastInvalidation = {generation: stoppedGeneration, message: invalidatedMessage};
            generation++;
            opening?.controller.abort();
            opening = null;
            if (init) { init.reject(error); init = null; }
            worker?.terminate(); worker = null;
            urls.forEach(url => URL.revokeObjectURL(url)); urls = [];
            cancellations.forEach(timer => clearTimeout(timer)); cancellations.clear();
            window.removeEventListener('pagehide', onPageHide);
            const abandoned = Array.from(pending.keys());
            try { listener?.callback(invalidatedMessage == null ? null : JSON.stringify(invalidatedMessage)); }
            finally { for (const id of abandoned) settle(id, error); }
        }
        const onPageHide = () => stop(failure('SESSION_CLOSED'), generation, null);
        async function artifact(cache, item, allowNetwork, signal) {
            const [path, size, hash] = item;
            const url = new URL(path, base).href;
            const key = url + '?sha256=' + hash;
            const valid = async response => {
                if (!response || !response.ok || !response.body) return null;
                const capacity = path === 'offline-nlp-worker.mjs' ? size * 2 : size;
                const bytes = new Uint8Array(capacity);
                const reader = response.body.getReader();
                let length = 0;
                try {
                    for (;;) {
                        signal.throwIfAborted();
                        const {done, value} = await reader.read();
                        if (done) break;
                        if (length + value.byteLength > capacity) return null;
                        bytes.set(value, length); length += value.byteLength;
                    }
                } finally { await reader.cancel().catch(() => {}); reader.releaseLock(); }
                let buffer = bytes.buffer.slice(0, length);
                if (path === 'offline-nlp-worker.mjs') {
                    // Git on Windows can check out the app-owned worker with CRLF; pin its canonical LF source.
                    buffer = new TextEncoder().encode(new TextDecoder().decode(buffer).replace(/\r\n/g, '\n')).buffer;
                }
                if (buffer.byteLength !== size) return null;
                const digest = await crypto.subtle.digest('SHA-256', buffer);
                const value = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('');
                return value === hash ? buffer : null;
            };
            let buffer = debug ? null : await valid(await cache.match(key));
            signal.throwIfAborted();
            if (buffer) return buffer;
            if (!allowNetwork) throw failure('RUNTIME_MISSING');
            const response = await fetch(url, {credentials: 'omit', cache: debug ? 'no-store' : 'no-cache', referrerPolicy: 'no-referrer', signal});
            buffer = await valid(response);
            if (!buffer) throw failure('RUNTIME_INTEGRITY', {fileName: path});
            signal.throwIfAborted();
            if (!debug) await cache.put(key, new Response(buffer, {headers: {'Content-Type': path.endsWith('.wasm') ? 'application/wasm' : 'text/javascript'}}));
            return buffer;
        }
        async function boot(allowNetwork) {
            if (worker && !opening) return;
            if (opening) return opening.promise;
            const current = ++generation;
            const controller = new AbortController();
            const holder = {controller, promise: null};
            opening = holder;
            window.addEventListener('pagehide', onPageHide);
            holder.promise = (async () => {
                const cache = debug ? null : await caches.open(cacheName);
                const contents = new Map();
                for (const item of artifacts) contents.set(item[0], await artifact(cache, item, allowNetwork, controller.signal));
                controller.signal.throwIfAborted();
                if (current !== generation) throw failure('CANCELLED');
                const local = new Map();
                const make = (path, dependencies = []) => {
                    let source = new TextDecoder().decode(contents.get(path));
                    for (const dependency of dependencies) {
                        const target = local.get('hashes/' + dependency);
                        source = source.replaceAll('"./' + dependency + '"', JSON.stringify(target));
                        source = source.replaceAll("'./" + dependency + "'", JSON.stringify(target));
                    }
                    const url = URL.createObjectURL(new Blob([source], {type: 'text/javascript'}));
                    urls.push(url); local.set(path, url); return url;
                };
                make('hashes/utils.js'); make('hashes/_u64.js');
                make('hashes/_md.js', ['_u64.js', 'utils.js']);
                const hash = make('hashes/sha2.js', ['_md.js', '_u64.js', 'utils.js']);
                const ort = make('ort/ort.wasm.min.mjs');
                const glue = make('ort/ort-wasm-simd-threaded.mjs');
                const wasm = URL.createObjectURL(new Blob([contents.get('ort/ort-wasm-simd-threaded.wasm')], {type: 'application/wasm'}));
                urls.push(wasm);
                const source = make('offline-nlp-worker.mjs');
                contents.clear();
                const instance = new Worker(source, {type: 'module', name: 'zhihu-offline-nlp'});
                worker = instance;
                instance.onerror = event => { event.preventDefault(); stop(failure('WORKER_FAILED'), current); };
                instance.onmessageerror = () => stop(failure('WORKER_MESSAGE'), current);
                instance.onmessage = event => {
                    if (current !== generation) return;
                    const {id, result, error, progress} = event.data;
                    if (id === 0) {
                        const request = init; init = null;
                        if (error) request?.reject(error); else request?.resolve();
                        return;
                    }
                    if (cancellations.has(id)) {
                        if (!progress) {
                            clearTimeout(cancellations.get(id)); cancellations.delete(id);
                            if (pending.size === 0) stop(failure('CANCELLED'), current);
                        }
                        return;
                    }
                    const job = pending.get(id);
                    if (!job) return;
                    if (progress) { job.progress(progress.stage, progress.fileName, progress.downloadedBytes, progress.totalBytes); return; }
                    if (error && ['prepare', 'open', 'encode'].includes(job.operation)) { stop(error, current); return; }
                    settle(id, error, result ? {...result, generation: current} : null);
                };
                await new Promise((resolve, reject) => {
                    const timer = setTimeout(() => reject(failure('WORKER_TIMEOUT')), 30000);
                    init = {resolve: () => { clearTimeout(timer); resolve(); }, reject: error => { clearTimeout(timer); reject(error); }};
                    instance.postMessage({id: 0, operation: 'initialize', payload: {hash, ort, glue, wasm}});
                });
            })();
            try { await holder.promise; }
            catch (error) { stop(errorFailure(error), current); throw error; }
            finally { if (opening === holder) opening = null; }
        }
        async function deleteLocal(spec, signal) {
            if (!/^[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}$/.test(spec.id)) throw failure('INVALID_MODEL');
            await navigator.locks.request('zhihu++:offline-nlp:v1:' + spec.id, {signal}, async () => {
                const db = await new Promise((resolve, reject) => {
                    const request = indexedDB.open('zhihu++:offline-nlp:v1', 1);
                    let blocked = false;
                    request.onupgradeneeded = () => request.result.createObjectStore('models');
                    request.onerror = () => reject(request.error);
                    request.onblocked = () => { blocked = true; reject(failure('STORAGE_BUSY')); };
                    request.onsuccess = () => {
                        if (blocked) request.result.close(); else resolve(request.result);
                    };
                });
                try {
                    signal.throwIfAborted();
                    await new Promise((resolve, reject) => {
                        const transaction = db.transaction('models', 'readwrite');
                        transaction.objectStore('models').delete(spec.id);
                        transaction.oncomplete = resolve;
                        transaction.onabort = transaction.onerror = () => reject(transaction.error || failure('DELETE_FAILED'));
                    });
                    try {
                        const root = await navigator.storage.getDirectory();
                        const directory = await root.getDirectoryHandle('zhihu-offline-nlp-v1');
                        await directory.removeEntry(spec.id, {recursive: true});
                    } catch (error) { if (error.name !== 'NotFoundError') throw error; }
                } finally { db.close(); }
            });
            await caches.delete(cacheName);
        }
        return {
            request(operation, json, progress, done) {
                const id = nextId++;
                const controller = new AbortController();
                const job = {operation, progress, done, dispatched: false};
                pending.set(id, job);
                (async () => {
                    try {
                        const payload = JSON.parse(json);
                        if (operation === 'close' && payload.generation !== generation) {
                            settle(id, null, {generation}); return;
                        }
                        if (operation === 'delete') {
                            if (worker || opening) throw failure('SESSION_BUSY');
                            await deleteLocal(payload, controller.signal);
                            settle(id, null, {generation}); return;
                        }
                        if (operation === 'encode' && (payload.generation !== generation || !worker)) throw failure('SESSION_CLOSED');
                        await boot(operation === 'prepare');
                        controller.signal.throwIfAborted();
                        if (!pending.has(id)) return;
                        job.dispatched = true;
                        job.generation = generation;
                        if (operation === 'close') job.timer = setTimeout(() => stop(failure('CLOSE_TIMEOUT'), job.generation), 10000);
                        worker.postMessage({id, operation, payload});
                    } catch (error) { settle(id, errorFailure(error)); }
                })();
                return {cancel() {
                    controller.abort();
                    if (!pending.delete(id)) {
                        // Kotlin can cancel after the open result was delivered but before it owns the session.
                        if (operation === 'open' && job.dispatched) stop(failure('CANCELLED'), job.generation, null);
                        return;
                    }
                    if (job.timer) clearTimeout(job.timer);
                    if (operation === 'prepare' && job.dispatched && worker) {
                        worker.postMessage({id, operation: 'cancel'});
                        cancellations.set(id, setTimeout(() => stop(failure('CANCEL_TIMEOUT'), job.generation), 15000));
                    } else if (operation !== 'delete') stop(failure('CANCELLED'), job.generation, null);
                }};
            },
            onInvalidated(expected, callback) {
                if (expected !== generation || !worker) {
                    const reason = lastInvalidation?.generation === expected ? lastInvalidation.message : failure('SESSION_CLOSED');
                    callback(reason == null ? null : JSON.stringify(reason));
                } else invalidation = {generation: expected, callback};
            },
            clearInvalidation(expected) {
                if (invalidation?.generation === expected) invalidation = null;
            },
            terminate(expected) {
                if (invalidation?.generation === expected) invalidation = null;
                stop(failure('SESSION_CLOSED'), expected, null);
            },
            shutdown() { invalidation = null; stop(failure('SESSION_CLOSED'), undefined, null); }
        };
    }""",
)
private external fun createWebNlpRuntime(): WebNlpRuntime
