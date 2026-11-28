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

// Only verified, version-pinned local runtime URLs are supplied by the Wasm host.
let runtime;
let sha256;
let session;
let sessionKey;
let queue = Promise.resolve();
const operations = new Map();
const CHUNK_BYTES = 1024 * 1024;
const DATABASE = 'zhihu++:offline-nlp:v1';
const DIRECTORY = 'zhihu-offline-nlp-v1';

const failure = (code, parameters = {}) => ({code, ...parameters});
const fail = (code, parameters) => { throw failure(code, parameters); };
const hex = bytes => Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('');
const tick = () => new Promise(resolve => setTimeout(resolve, 0));
const identity = spec => JSON.stringify([spec.id, spec.revision, spec.files.map(file => [file.name, file.expectedBytes, file.sha256])]);

function validateSpec(spec) {
    if (!spec || !/^[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}$/.test(spec.id) || !/^[a-f0-9]{40}$/.test(spec.revision)) {
        fail('INVALID_MODEL');
    }
    if (!Array.isArray(spec.files) || spec.files.length !== 2 ||
        !spec.files.some(file => file.name === 'model.onnx') || !spec.files.some(file => file.name === 'tokenizer.json')) {
        fail('INVALID_MODEL');
    }
    for (const file of spec.files) {
        const url = new URL(file.url);
        if (url.protocol !== 'https:' || url.username || url.password || !url.pathname.includes('/' + spec.revision + '/')) {
            fail('INVALID_SOURCE');
        }
        if (!Number.isSafeInteger(file.expectedBytes) || file.expectedBytes <= 0 ||
            file.expectedBytes > (file.name === 'model.onnx' ? 512 * 1024 * 1024 : 16 * 1024 * 1024) ||
            !/^[a-f0-9]{64}$/.test(file.sha256)) fail('INVALID_MODEL');
    }
    return spec;
}

async function database() {
    return new Promise((resolve, reject) => {
        const request = indexedDB.open(DATABASE, 1);
        let blocked = false;
        request.onupgradeneeded = () => request.result.createObjectStore('models');
        request.onerror = () => reject(request.error);
        request.onblocked = () => {
            blocked = true;
            reject(failure('STORAGE_BUSY'));
        };
        request.onsuccess = () => {
            if (blocked) { request.result.close(); return; }
            request.result.onversionchange = () => request.result.close();
            resolve(request.result);
        };
    });
}

async function manifest(id, value, write = false, signal) {
    const db = await database();
    try {
        return await new Promise((resolve, reject) => {
            const transaction = db.transaction('models', write ? 'readwrite' : 'readonly');
            const store = transaction.objectStore('models');
            const request = !write ? store.get(id) : value ? store.put(value, id) : store.delete(id);
            const abort = () => { try { transaction.abort(); } catch {} };
            signal?.addEventListener('abort', abort, {once: true});
            transaction.oncomplete = () => {
                signal?.removeEventListener('abort', abort);
                resolve(request.result);
            };
            transaction.onabort = transaction.onerror = () => {
                signal?.removeEventListener('abort', abort);
                reject(transaction.error || failure('STORAGE_FAILED'));
            };
            if (signal?.aborted) abort();
        });
    } finally { db.close(); }
}

async function modelRoot(spec) {
    const root = await navigator.storage.getDirectory();
    const directory = await root.getDirectoryHandle(DIRECTORY, {create: true});
    return directory.getDirectoryHandle(spec.id, {create: true});
}

async function cleanOrphans(root, keep) {
    for await (const [name, handle] of root.entries()) {
        if (handle.kind === 'directory' && name.startsWith('candidate-') && name !== keep) {
            await root.removeEntry(name, {recursive: true});
        }
    }
}

async function verifyFile(directory, spec, signal, capture = false) {
    const fileHandle = await directory.getFileHandle(spec.name);
    if (typeof fileHandle.createSyncAccessHandle !== 'function') fail('WEB_SYNC_STORAGE_UNSUPPORTED');
    const handle = await fileHandle.createSyncAccessHandle();
    const hash = sha256.create();
    try {
        if (handle.getSize() !== spec.expectedBytes) fail('CACHE_SIZE', {fileName: spec.name});
        // Only session creation allocates the complete model; verification uses one 1 MiB buffer.
        const bytes = new Uint8Array(capture ? spec.expectedBytes : Math.min(CHUNK_BYTES, spec.expectedBytes));
        for (let offset = 0; offset < spec.expectedBytes;) {
            signal.throwIfAborted();
            const length = Math.min(CHUNK_BYTES, spec.expectedBytes - offset);
            const chunk = capture ? bytes.subarray(offset, offset + length) : bytes.subarray(0, length);
            let read = 0;
            while (read < length) {
                const count = handle.read(chunk.subarray(read), {at: offset + read});
                if (count <= 0) fail('FILE_READ', {fileName: spec.name});
                read += count;
            }
            hash.update(chunk);
            offset += length;
            if (offset % (16 * CHUNK_BYTES) === 0) await tick();
        }
        if (hex(hash.digest()) !== spec.sha256) fail('CACHE_HASH', {fileName: spec.name});
        signal.throwIfAborted();
        return capture ? bytes : undefined;
    } finally {
        hash.destroy();
        handle.close();
    }
}

async function download(directory, file, signal, progress) {
    signal.throwIfAborted();
    const response = await fetch(file.url, {
        credentials: 'omit', referrerPolicy: 'no-referrer', cache: 'no-store', signal,
    });
    if (!response.ok || !response.body) {
        await response.body?.cancel();
        fail('DOWNLOAD_HTTP', {fileName: file.name, httpStatus: response.status});
    }
    // Validate decoded stream bytes, not Content-Length: CORS can hide Content-Encoding.
    const hash = sha256.create();
    let handle;
    let reader;
    let bytes = 0;
    let reported = 0;
    try {
        const fileHandle = await directory.getFileHandle(file.name, {create: true});
        handle = await fileHandle.createSyncAccessHandle();
        reader = response.body.getReader();
        handle.truncate(0);
        progress('downloading', file.name, 0, file.expectedBytes);
        for (;;) {
            signal.throwIfAborted();
            const {done, value} = await reader.read();
            if (done) break;
            if (bytes + value.byteLength > file.expectedBytes) fail('DOWNLOAD_SIZE', {fileName: file.name});
            let written = 0;
            while (written < value.byteLength) {
                const count = handle.write(value.subarray(written), {at: bytes + written});
                if (count <= 0) fail('FILE_WRITE', {fileName: file.name});
                written += count;
            }
            hash.update(value);
            bytes += value.byteLength;
            if (performance.now() - reported >= 100) {
                progress('downloading', file.name, bytes, file.expectedBytes);
                reported = performance.now();
            }
        }
        progress('downloading', file.name, bytes, file.expectedBytes);
        progress('verifying', file.name, bytes, file.expectedBytes);
        if (bytes !== file.expectedBytes || hex(hash.digest()) !== file.sha256) fail('DOWNLOAD_HASH', {fileName: file.name});
        signal.throwIfAborted();
        handle.flush();
    } finally {
        hash.destroy();
        handle?.close();
        if (reader) {
            await reader.cancel().catch(() => {});
            reader.releaseLock();
        } else await response.body.cancel().catch(() => {});
    }
}

async function prepare(spec, signal, progress) {
    return navigator.locks.request(DATABASE + ':' + spec.id, {signal}, async () => {
        const root = await modelRoot(spec);
        const current = await manifest(spec.id);
        await cleanOrphans(root, current?.directory);
        if (current?.identity === identity(spec)) {
            try {
                const directory = await root.getDirectoryHandle(current.directory);
                for (const file of spec.files) {
                    progress('verifying', file.name, 0, file.expectedBytes);
                    await verifyFile(directory, file, signal);
                }
                const tokenizer = await (await directory.getFileHandle('tokenizer.json')).getFile();
                return {tokenizerJson: await tokenizer.text()};
            } catch (error) {
                signal.throwIfAborted();
                if (['SecurityError', 'NotAllowedError', 'NoModificationAllowedError'].includes(error.name)) throw error;
                // A bad cache is not usable. Keep its files until a replacement has fully verified.
            }
        }
        const required = spec.files.reduce((sum, file) => sum + file.expectedBytes, 8 * CHUNK_BYTES);
        const estimate = await navigator.storage.estimate();
        if (Number.isFinite(estimate.quota) && Number.isFinite(estimate.usage) && estimate.quota - estimate.usage < required) {
            fail('QUOTA_REQUIRED', {requiredMiB: Math.ceil(required / CHUNK_BYTES)});
        }
        const name = 'candidate-' + crypto.randomUUID();
        const directory = await root.getDirectoryHandle(name, {create: true});
        let committed = false;
        try {
            for (const file of spec.files) await download(directory, file, signal, progress);
            const tokenizer = await (await directory.getFileHandle('tokenizer.json')).getFile();
            const tokenizerJson = await tokenizer.text();
            signal.throwIfAborted();
            // Publication is one IDB transaction; no existing file is overwritten by a failed download.
            await manifest(spec.id, {identity: identity(spec), directory: name}, true, signal);
            committed = true;
            await cleanOrphans(root, name).catch(() => {});
            return {tokenizerJson};
        } finally {
            if (!committed) await root.removeEntry(name, {recursive: true}).catch(() => {});
        }
    });
}

async function open(spec, signal) {
    const key = identity(spec);
    if (session) {
        if (sessionKey === key) return {};
        fail('SESSION_BUSY');
    }
    let bytes = await navigator.locks.request(DATABASE + ':' + spec.id, {signal}, async () => {
        const current = await manifest(spec.id);
        if (current?.identity !== key) fail('CACHE_MISSING');
        const root = await modelRoot(spec);
        const directory = await root.getDirectoryHandle(current.directory);
        return verifyFile(directory, spec.files.find(file => file.name === 'model.onnx'), signal, true);
    });
    try {
        // Estimated peak >= 3 model copies (~1.2 GiB), plus ORT activations/browser memory; not a measured device requirement.
        const ort = await import(runtime.ort);
        ort.env.wasm.numThreads = 1;
        ort.env.wasm.proxy = false;
        ort.env.wasm.wasmPaths = {mjs: runtime.glue, wasm: runtime.wasm};
        session = await ort.InferenceSession.create(bytes, {executionProviders: ['wasm'], graphOptimizationLevel: 'all'});
        signal.throwIfAborted();
        if (!['input_ids', 'attention_mask', 'token_type_ids'].every(name => session.inputNames.includes(name)) ||
            !session.outputNames.includes('last_hidden_state')) fail('INVALID_MODEL');
        sessionKey = key;
        return {};
    } catch (error) {
        if (session) await session.release().catch(() => {});
        session = undefined;
        sessionKey = undefined;
        throw error;
    } finally { bytes = undefined; }
}

async function encode(payload, signal) {
    if (!session) fail('SESSION_CLOSED');
    const values = [payload.inputIds, payload.attentionMask, payload.tokenTypeIds];
    const count = values[0]?.length;
    if (!Number.isInteger(count) || count < 1 || count > 512 || values.some(array => !Array.isArray(array) || array.length !== count)) {
        fail('INVALID_INPUT');
    }
    const inputs = values.map((array, index) => BigInt64Array.from(array.map(value => {
        if (typeof value !== 'string' || !/^\d{1,19}$/.test(value)) fail('INVALID_INPUT');
        const integer = BigInt(value);
        if (integer > 9223372036854775807n || (index > 0 && integer > 1n)) fail('INVALID_INPUT');
        return integer;
    })));
    if (!inputs[1].some(value => value === 1n)) fail('INVALID_INPUT');
    const ort = await import(runtime.ort);
    const feeds = {};
    let outputs;
    try {
        ['input_ids', 'attention_mask', 'token_type_ids'].forEach((name, index) => {
            feeds[name] = new ort.Tensor('int64', inputs[index], [1, count]);
        });
        signal.throwIfAborted();
        outputs = await session.run(feeds, ['last_hidden_state']);
        const output = outputs.last_hidden_state;
        if (output.type !== 'float32' || output.dims.length !== 3 || output.dims[0] !== 1 || output.dims[1] !== count || output.dims[2] !== 768) {
            fail('INVALID_OUTPUT');
        }
        // Never transfer ORT's heap. The host uses the shared Kotlin pooling/normalization implementation.
        const data = new Float32Array(output.data);
        signal.throwIfAborted();
        return {data};
    } finally {
        for (const tensor of Object.values(feeds)) tensor.dispose();
        if (outputs) for (const tensor of Object.values(outputs)) tensor.dispose();
    }
}

function errorFailure(error, operation) {
    if (error && typeof error.code === 'string' && /^[A-Z_]{1,48}$/.test(error.code)) return error;
    if (error?.name === 'QuotaExceededError') return failure('QUOTA_EXCEEDED');
    if (error?.name === 'SecurityError' || error?.name === 'NotAllowedError') return failure('STORAGE_DENIED');
    if (error?.name === 'NoModificationAllowedError') return failure('STORAGE_BUSY');
    if (error?.name === 'AbortError') return failure('CANCELLED');
    if (error instanceof RangeError || /out of memory|memory access out of bounds|allocation failed/i.test(String(error))) {
        return failure('OUT_OF_MEMORY');
    }
    return failure(operation === 'encode' ? 'INFERENCE_FAILED' : operation === 'close' ? 'CLOSE_FAILED' : 'LOAD_FAILED');
}

self.onmessage = event => {
    const {id, operation, payload} = event.data;
    if (operation === 'cancel') {
        operations.get(id)?.abort();
        return;
    }
    const controller = new AbortController();
    operations.set(id, controller);
    queue = queue.catch(() => {}).then(async () => {
        const progress = (stage, fileName, downloadedBytes, totalBytes) => self.postMessage({id, progress: {stage, fileName, downloadedBytes, totalBytes}});
        try {
            controller.signal.throwIfAborted();
            let result;
            if (operation === 'initialize') {
                runtime = payload;
                ({sha256} = await import(runtime.hash));
                result = {};
            } else if (!runtime) fail('RUNTIME_LOAD');
            else if (operation === 'prepare') result = await prepare(validateSpec(payload), controller.signal, progress);
            else if (operation === 'open') result = await open(validateSpec(payload), controller.signal);
            else if (operation === 'encode') result = await encode(payload, controller.signal);
            else if (operation === 'close') {
                if (session) await session.release();
                session = undefined;
                sessionKey = undefined;
                result = {};
            } else fail('INVALID_INPUT');
            self.postMessage({id, result}, result?.data ? [result.data.buffer] : []);
        } catch (error) {
            self.postMessage({id, error: errorFailure(error, operation)});
        } finally { operations.delete(id); }
    });
};
