/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
'use strict';
const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const {resolve} = require('node:path');
const {createHash, webcrypto} = require('node:crypto');
const vm = require('node:vm');
const source = readFileSync(resolve(__dirname, '../../wasmJsMain/resources/zhihu-app-cache.js'), 'utf8');
const hash = data => createHash('sha256').update(data).digest('hex');
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done; }); return {promise, resolve}; };

function environment(base = 'https://example.test/zhihu-plus-plus/') {
    const storage = new Map(), routes = new Map(), requests = [], events = [];
    let putHook = async () => {}, accesses = 0;
    const caches = {
        async open(name) {
            accesses++;
            if (!storage.has(name)) storage.set(name, new Map());
            const data = storage.get(name);
            return {
                async match(key) { return data.get(String(key))?.clone(); },
                async delete(key) { return data.delete(typeof key === 'string' ? key : key.url); },
                async put(key, response) { await putHook(name, String(key)); data.set(String(key), response.clone()); },
                async keys() { return [...data.keys()].map(key => new Request(key)); }
            };
        },
        async delete(name) { accesses++; return storage.delete(name); },
        async keys() { accesses++; return [...storage.keys()]; }
    };
    const fetch = async (input, options = {}) => {
        const url = typeof input === 'string' ? input : input.url;
        requests.push(url);
        const route = routes.get(url);
        if (!route) throw new TypeError('Offline');
        return typeof route === 'function' ? route(options) : new Response(route, {headers: {'Content-Type': url.endsWith('.wasm') ? 'application/wasm' : 'application/json'}});
    };
    const context = vm.createContext({URL, Request, Response, AbortController, TextDecoder, Uint8Array, crypto: webcrypto,
        caches, fetch, setTimeout, clearTimeout, console});
    vm.runInContext(source, context);
    const makeStore = () => new context.ZhihuAppCacheStore(base, (id, value) => events.push({id, value}));
    const store = makeStore();
    function publish(code, name = String(code), contents = {}) {
        const prefix = 'app-resources/' + code + '-' + name + '/';
        const files = {'index.html': '<!DOCTYPE html>fixture', 'main.js': 'main version ' + code,
            'lazy-chunk.js': 'chunk version ' + code, 'engine.wasm': 'engine version ' + code,
            'nlp/ort/runtime.wasm': 'NLP runtime', 'voice-codec/core.wasm': 'codec runtime', ...contents};
        const assets = Object.entries(files).map(([path, text]) => ({path, bytes: Buffer.byteLength(text), sha256: hash(text),
            group: /^(nlp|voice-codec)\//.test(path) ? 'lazy' : 'core'}));
        const buildId = hash(files['main.js']).slice(0, 20);
        const manifest = {schema: 1, versionCode: code, versionName: name, buildId, base: prefix, entry: 'main.js', assets};
        const bytes = JSON.stringify(manifest);
        const pointer = {schema: 1, versionCode: code, versionName: name, buildId, manifest: prefix + 'app-assets.json', sha256: hash(bytes)};
        routes.set(base + 'web-version.json', JSON.stringify(pointer));
        routes.set(base + pointer.manifest, bytes);
        for (const [path, text] of Object.entries(files)) routes.set(base + prefix + path, text);
        return {pointer, manifest, files, url: path => base + prefix + path};
    }
    return {base, store, makeStore, publish, caches, storage, routes, requests, events,
        accessCount: () => accesses, setPutHook: hook => { putHook = hook; }};
}

async function checkCacheRecovery() {
    const damaged = environment();
    const first = damaged.publish(1);
    await damaged.store.command('first', 'begin', {liveIds: ['first']});
    const cache = damaged.storage.get(damaged.store.prefix + damaged.store.key(first.pointer));
    const corrupt = path => cache.set(first.url(path), new Response('x'.repeat(first.files[path].length),
        {headers: {'Content-Length': String(first.files[path].length)}}));
    corrupt('main.js');
    const restart = damaged.makeStore();
    const before = damaged.requests.length;
    const recovered = await restart.command('second', 'begin', {liveIds: ['first', 'second']});
    assert.equal(recovered.snapshot.cached.versionCode, 1);
    assert.ok(damaged.requests.length > before, 'equal-length corrupt core is detected locally and repaired');
    assert.equal(await (await restart.serve(new Request(first.url('main.js')), 'second')).text(), first.files['main.js']);
    const repairedCache = damaged.storage.get(restart.prefix + restart.key(first.pointer));
    repairedCache.set(first.url('main.js'), new Response('x'.repeat(first.files['main.js'].length),
        {headers: {'Content-Length': String(first.files['main.js'].length)}}));
    assert.equal(await (await restart.serve(new Request(first.url('main.js')), 'second')).text(), first.files['main.js'], 'serve never returns post-boot corrupt bytes');
    await restart.serve(new Request(first.url('voice-codec/core.wasm')), 'second');
    const bytesWithLazy = restart.snapshot('second').cachedBytes;
    repairedCache.delete(first.url('voice-codec/core.wasm'));
    const noNetwork = damaged.requests.length;
    const evicted = await damaged.makeStore().command('third', 'begin', {liveIds: ['first', 'second', 'third']});
    assert.equal(evicted.snapshot.cachedBytes, bytesWithLazy - Buffer.byteLength(first.files['voice-codec/core.wasm']), 'evicted lazy bytes are not reported as stored');
    assert.equal(damaged.requests.length, noNetwork, 'reconciling cached keys/bytes does not refetch the same valid version');
    const newer = damaged.publish(2);
    await restart.command('second', 'check');
    await restart.command('second', 'download');
    damaged.storage.get(restart.prefix + restart.key(newer.pointer)).set(newer.url('main.js'), new Response('corrupt', {headers: {'Content-Length': '7'}}));
    damaged.routes.delete(damaged.base + 'web-version.json');
    const fallback = await damaged.makeStore().command('offline', 'begin', {liveIds: ['second', 'offline']});
    assert.equal(fallback.snapshot.running.versionCode, 1, 'an intact old version survives a corrupted newer cache while offline');

    const broken = environment();
    const release = broken.publish(1);
    await broken.store.command('old', 'begin', {liveIds: ['old']});
    broken.storage.get(broken.store.prefix + 'meta').set(broken.store.metaKey, new Response('{broken'));
    await (await broken.caches.open('unrelated-user-data')).put(broken.base + 'draft', new Response('keep'));
    const recovery = broken.makeStore();
    const version = {versionCode: 1, versionName: '1', buildId: release.pointer.buildId};
    assert.equal((await recovery.command('fallback', 'begin', {liveIds: ['old', 'fallback'], version})).snapshot.error.code, 'cache-metadata-invalid');
    const originalDelete = broken.caches.delete;
    broken.caches.delete = async () => { throw new DOMException('Denied', 'SecurityError'); };
    const failedClear = await recovery.command('fallback', 'clear', {liveIds: ['old', 'fallback'], version});
    assert.equal(failedClear.snapshot.error.code, 'cache-metadata-invalid', 'failed cleanup remains visibly retryable');
    assert.ok(broken.storage.has(broken.store.prefix + broken.store.key(release.pointer)));
    broken.caches.delete = originalDelete;
    const cleared = await recovery.command('fallback', 'clear', {liveIds: ['old', 'fallback'], version});
    assert.equal(cleared.snapshot.error, null);
    assert.equal(cleared.snapshot.available, true);
    assert.equal(cleared.snapshot.cached, null);
    assert.equal(cleared.snapshot.cachedBytes, 0);
    assert.equal(cleared.snapshot.suppressed, true);
    assert.ok(broken.storage.has('unrelated-user-data'));
    const tombstone = broken.makeStore();
    assert.equal((await tombstone.command('old', 'snapshot')).snapshot.suppressed, true, 'lost old-client pins are suppressed conservatively');
    const restored = await tombstone.command('fallback', 'restore', {version});
    assert.equal(restored.snapshot.error, null);
    assert.equal(restored.snapshot.cached.versionCode, 1);
    assert.equal(restored.snapshot.suppressed, false);
    assert.equal(tombstone.snapshot('old').suppressed, true);
}

async function checkVoicePreload(cancel) {
    const kotlin = readFileSync(resolve(__dirname, '../../../../shared-ui-base/src/wasmJsMain/kotlin/com/github/zly2006/zhihu/ui/components/VoiceCommentRecorder.wasmJs.kt'), 'utf8');
    const code = /"""(\(failed\) => \{[\s\S]*?)"""/.exec(kotlin)[1];
    const window = new EventTarget(), document = new EventTarget();
    const version = {versionCode: 1, versionName: '1'};
    window.zhihuAppResourceBase = 'https://example.test/project/app-resources/1-1/';
    window.zhihuAppCache = {snapshot: () => ({mode: 'production', available: true, suppressed: false, cached: version, running: version})};
    let workers = 0, now = 1;
    const requests = [], fetching = deferred(), finished = deferred();
    class MediaRecorder {
        static isTypeSupported(type) { return type.startsWith('audio/webm'); }
        constructor() { this.mimeType = 'audio/webm'; this.state = 'inactive'; }
        start() { this.state = 'recording'; }
        stop() { this.state = 'inactive'; this.ondataavailable({data: new Blob(['recording'])}); this.onstop(); }
    }
    class Worker {
        constructor() { workers++; assert.equal(requests.length, 2, 'page-owned preloads finish before starting the blob worker'); }
        postMessage() { queueMicrotask(() => this.onmessage({data: {bytes: Uint8Array.from([0, 0, 0, 0, 102, 116, 121, 112, 0, 0, 0, 0])}})); }
        terminate() {}
    }
    const context = vm.createContext({window, document, MediaRecorder, Worker, AbortController, Uint8Array, Blob, URL,
        navigator: {mediaDevices: {getUserMedia: async () => ({getTracks: () => [{stop() {}}], getAudioTracks: () => [{stop() {}}]})}},
        location: {href: 'https://example.test/project/index.html'}, performance: {now: () => now++},
        setTimeout, clearTimeout, queueMicrotask,
        fetch: async (url, options) => {
            requests.push(url);
            fetching.resolve();
            if (cancel) return new Promise((resolve, reject) => options.signal.addEventListener('abort', () => reject(new DOMException('Cancelled', 'AbortError'))));
            assert.equal(options.credentials, 'omit');
            return new Response('verified fixture');
        }});
    const recorder = vm.runInContext('(' + code + ')', context)(() => {});
    await new Promise(resolve => recorder.start(success => { assert.equal(success, true); resolve(); }));
    recorder.stop((url, duration, bytes) => finished.resolve({url, duration, bytes}));
    await fetching.promise;
    if (cancel) recorder.cancel();
    const result = await finished.promise;
    assert.equal(workers, cancel ? 0 : 1);
    if (!cancel) {
        assert.equal(result.bytes, 12);
        assert.equal(requests[0], window.zhihuAppResourceBase + 'voice-codec/ffmpeg-core.js');
        assert.equal(requests[1], window.zhihuAppResourceBase + 'voice-codec/ffmpeg-core.wasm');
    }
    recorder.dispose();
}

function checkDebugShell() {
    const html = readFileSync(resolve(__dirname, '../../wasmJsMain/resources/index.html'), 'utf8');
    const source = [...html.matchAll(/<script>\s*([\s\S]*?)<\/script>/g)].at(-1)[1];
    for (const mode of ['debug', 'production']) {
        let script;
        const document = {querySelector: () => ({content: mode}), createElement: () => ({}), body: {appendChild: value => { script = value; }}};
        vm.runInNewContext(source, {document, URL, location: {href: 'http://127.0.0.1:8080/project/index.html#route'}});
        assert.equal(script.id, 'app-cache-bootstrap');
        const url = new URL(script.src);
        assert.equal(url.pathname, '/project/zhihu-app-bootstrap.js');
        assert.equal(url.searchParams.has('visit'), mode === 'debug', 'debug bootstrap bypasses a previously registered production worker');
    }
    const config = {output: {}, devServer: {}};
    vm.runInNewContext(readFileSync(resolve(__dirname, '../../../webpack.config.d/localhost.js'), 'utf8'), {config});
    assert.equal(config.devServer.headers['Cache-Control'], 'no-store', 'all development responses, including Wasm and chunks, use no-store');
    assert.equal(config.output.publicPath, 'auto');
}

async function checkBootstrap(href, mode, supported = true, existingController = false, notificationOnly = false) {
    const base = new URL('./', href).href;
    const injected = deferred(), calls = [];
    const window = new EventTarget();
    let reloads = 0;
    const location = {href, origin: new URL(href).origin, reload: () => { reloads++; }};
    const metas = {'zhihu-cache-mode': mode, 'zhihu-version-name': '2', 'zhihu-version-code': '2',
        'zhihu-build-id': 'b'.repeat(20), 'zhihu-resource-base': mode === 'debug' ? './' : 'app-resources/2-2/',
        'zhihu-app-entry': 'zhihu.js'};
    const detail = {textContent: ''};
    const document = {baseURI: href, documentElement: {lang: 'en'},
        querySelector: selector => ({content: metas[/name="([^"]+)"/.exec(selector)[1]]}),
        getElementById: id => id === 'boot-detail' ? detail : null, createElement: () => ({}), body: {appendChild: script => injected.resolve(script)}};
    const snapshot = {schema: 1, mode: 'production', available: true, phase: 'idle',
        running: {versionCode: 1, versionName: '1', buildId: 'a'.repeat(20)}, cached: {versionCode: 1, versionName: '1', buildId: 'a'.repeat(20)},
        cachedBytes: 100, coreBytes: 100, downloadedBytes: 0, totalBytes: 0, candidate: null, suppressed: false, error: null};
    const serviceWorker = new EventTarget();
    serviceWorker.controller = {scriptURL: base + 'zhihu-notifications-sw.js', postMessage: (data, ports) => {
        if (data.type === 'zhihu-app-cache-probe-v1') {
            if (!notificationOnly) ports[0].postMessage({protocol: 1});
            ports[0].close();
            return;
        }
        calls.push(data.action);
        if (notificationOnly) { ports[0].close(); return; }
        ports[0].postMessage({snapshot: data.mode === 'debug' ? {...snapshot, mode: 'debug', phase: 'disabled', available: false, cached: null, candidate: null} : snapshot,
            launch: data.action === 'begin' ? {resourceBase: base + 'app-resources/1-1/', entry: 'old.js', entryHash: 'a'.repeat(64)} : null});
        ports[0].close();
    }};
    serviceWorker.register = async (url, options) => {
        assert.equal(url, base + 'zhihu-notifications-sw.js');
        assert.equal(options.scope, base);
        return {active: serviceWorker.controller, waiting: notificationOnly ? {} : null};
    };
    const navigator = {serviceWorker: supported && (mode !== 'debug' || existingController) ? serviceWorker : undefined};
    const context = vm.createContext({window, location, document, navigator, URL, crypto: webcrypto, isSecureContext: supported,
        caches: supported ? {} : undefined, MessageChannel, structuredClone, CustomEvent, Event, setTimeout, clearTimeout, console, btoa});
    vm.runInContext(readFileSync(resolve(__dirname, '../../wasmJsMain/resources/zhihu-app-bootstrap.js'), 'utf8'), context);
    const script = await injected.promise;
    assert.ok(detail.textContent.includes('memory'), 'startup distinguishes memory from download storage');
    if (mode === 'production' && supported && !notificationOnly) assert.ok(detail.textContent.includes('Core download size:'));
    assert.equal(document.baseURI, href);
    assert.equal(script.id, 'app-script');
    if (mode === 'debug') {
        assert.ok(script.src.startsWith(base + 'zhihu.js?visit='));
        assert.equal(window.zhihuAppCache.snapshot().phase, 'disabled');
        await window.zhihuAppCache.apply();
        assert.equal(reloads, 1);
        assert.equal(calls.length, existingController ? 1 : 0);
        assert.equal(window.zhihuAppResourceBase, base, 'debug never restores the production resource base');
        assert.equal(window.zhihuAppCache.snapshot().cached, null);
    } else if (notificationOnly) {
        assert.equal(window.zhihuAppCache.snapshot().error.code, 'worker-update-pending');
        assert.equal(window.zhihuAppCache.snapshot().available, false);
        assert.equal(window.zhihuAppCache.snapshot().cached, null);
        assert.equal(calls.length, 0, 'notification-only workers never receive a long-running cache operation');
        assert.equal(script.src, base + 'app-resources/2-2/zhihu.js');
        assert.equal((await window.zhihuAppCache.restore()).error.code, 'worker-update-pending');
    } else if (supported) {
        assert.equal(calls[0], 'begin', 'main is selected only after the worker completed bootstrap');
        assert.equal(script.src, base + 'app-resources/1-1/old.js');
        assert.ok(script.integrity.startsWith('sha256-'));
        assert.equal(window.zhihuAppResourceBase, base + 'app-resources/1-1/');
        assert.equal(window.zhihuAppCache.snapshot().running.versionCode, 1);
    } else {
        assert.equal(window.zhihuAppCache.snapshot().phase, 'unavailable');
        assert.equal(window.zhihuAppCache.snapshot().available, false);
        assert.equal(script.src, base + 'app-resources/2-2/zhihu.js');
    }
}

(async () => {
    const e = environment();
    const first = e.publish(1);
    const begin = await e.store.command('tab1', 'begin', {liveIds: ['tab1']});
    assert.equal(begin.snapshot.cached.versionCode, 1);
    assert.equal(begin.launch.resourceBase, e.base + 'app-resources/1-1/');
    assert.equal(begin.snapshot.cachedBytes, first.manifest.assets.filter(asset => asset.group === 'core').reduce((sum, asset) => sum + asset.bytes, 0));
    assert.ok(!e.requests.some(url => url.endsWith('runtime.wasm')));
    const before = e.requests.length;
    const repeat = await e.store.command('tab2', 'begin', {liveIds: ['tab1', 'tab2']});
    assert.equal(repeat.snapshot.running.versionCode, 1);
    assert.equal(e.requests.length, before, 'same-version startup uses complete cache before checking network');
    await e.store.command('tab2', 'check');
    assert.equal(e.requests.length, before + 1, 'same version checks only the tiny pointer');
    assert.equal(e.store.snapshot('tab2').candidate, null);
    assert.equal(await e.store.serve(new Request(e.base + 'api/me'), 'tab1'), null);
    assert.equal(await e.store.serve(new Request('https://other.test/app-resources/1-1/main.js'), 'tab1'), null);

    const lazy = await e.store.serve(new Request(first.url('voice-codec/core.wasm')), 'tab1');
    assert.equal(await lazy.text(), first.files['voice-codec/core.wasm']);
    const afterLazy = e.requests.length;
    await e.store.serve(new Request(first.url('voice-codec/core.wasm')), 'tab1');
    assert.equal(e.requests.length, afterLazy, 'lazy runtime is reused');

    e.publish(1, '1', {'main.js': 'tampered same version'});
    assert.equal((await e.store.command('tab1', 'check')).snapshot.error.code, 'immutable-version');
    assert.equal(await (await e.store.serve(new Request(first.url('main.js')), 'tab1')).text(), first.files['main.js']);

    const second = e.publish(2);
    assert.equal((await e.store.command('tab1', 'check')).snapshot.candidate.versionCode, 2);
    e.routes.set(second.url('engine.wasm'), 'broken');
    assert.equal((await e.store.command('tab1', 'download')).snapshot.error.code, 'integrity');
    assert.equal(e.store.snapshot('tab1').cached.versionCode, 1);
    assert.equal(e.store.snapshot('tab1').candidate.downloaded, false);
    assert.equal(await (await e.store.serve(new Request(first.url('main.js')), 'tab2')).text(), first.files['main.js']);
    e.routes.set(second.url('engine.wasm'), second.files['engine.wasm']);
    e.setPutHook(async (name, key) => { if (key === second.url('main.js')) throw new DOMException('Quota', 'QuotaExceededError'); });
    assert.equal((await e.store.command('tab1', 'download')).snapshot.error.code, 'quota');
    assert.equal(e.store.snapshot('tab1').cached.versionCode, 1);
    e.setPutHook(async () => {});
    assert.equal((await e.store.command('tab1', 'download')).snapshot.candidate.downloaded, true);
    assert.equal(e.store.snapshot('tab1').running.versionCode, 1, 'download never swaps running code');
    const appliedAfterSleep = (await e.makeStore().command('tab1', 'apply')).snapshot;
    assert.equal(appliedAfterSleep.error, null);
    assert.equal(appliedAfterSleep.candidate.downloaded, true, 'downloaded candidate survives worker suspension');
    assert.equal((await e.store.command('tab3', 'begin', {liveIds: ['tab1', 'tab2', 'tab3']})).snapshot.running.versionCode, 2);
    assert.equal(e.store.snapshot('tab2').running.versionCode, 1, 'other clients keep their version pin');

    e.routes.delete(e.base + 'web-version.json');
    assert.equal((await e.store.command('tab1', 'check')).snapshot.error.code, 'network');
    assert.equal((await e.makeStore().command('restart', 'begin', {liveIds: ['tab1', 'tab2', 'tab3', 'restart']})).snapshot.running.versionCode, 2);

    const third = e.publish(3);
    await e.store.command('tab1', 'check');
    const writing = deferred(), release = deferred();
    e.setPutHook(async (name, key) => { if (key === third.url('main.js')) { writing.resolve(); await release.promise; } });
    const download = e.store.command('tab1', 'download');
    await writing.promise;
    await (await e.caches.open('zhihu++:offline-nlp-runtime:keep')).put(e.base + 'model', new Response('keep'));
    await (await e.caches.open('zhihu++:app:v1:%2Fother%2F:keep')).put(e.base + 'other', new Response('keep'));
    const clearing = e.store.command('tab1', 'clear');
    release.resolve();
    await download;
    assert.equal((await clearing).snapshot.cachedBytes, 0);
    assert.ok(e.storage.has('zhihu++:offline-nlp-runtime:keep'));
    assert.ok(e.storage.has('zhihu++:app:v1:%2Fother%2F:keep'));
    assert.equal(e.store.snapshot('tab2').suppressed, true);
    assert.equal(e.store.snapshot('tab1').candidate, null);
    assert.equal([...e.storage.keys()].filter(name => name.startsWith(e.store.prefix) && !name.endsWith(':meta')).length, 0);
    const restart = e.makeStore();
    assert.equal((await restart.command('tab2', 'snapshot')).snapshot.suppressed, true, 'clear survives worker restart for old tabs');
    await restart.serve(new Request(first.url('voice-codec/core.wasm')), 'tab2');
    assert.equal(restart.snapshot('tab2').cachedBytes, 0, 'old tabs cannot silently recache after clear');
    for (const [path, text] of Object.entries(first.files)) e.routes.set(first.url(path), text);
    assert.equal((await restart.command('tab2', 'restore')).snapshot.cached.versionCode, 1);
    assert.equal(restart.snapshot('tab2').suppressed, false);
    assert.equal((await restart.command('new-visit', 'begin', {liveIds: ['tab1', 'tab2', 'new-visit']})).snapshot.suppressed, false);

    // Clearing while a lazy write is in-flight must also wait for and remove that write.
    const lazyWriting = deferred(), lazyRelease = deferred();
    e.setPutHook(async (name, key) => { if (key === first.url('nlp/ort/runtime.wasm')) { lazyWriting.resolve(); await lazyRelease.promise; } });
    const pendingLazy = restart.serve(new Request(first.url('nlp/ort/runtime.wasm')), 'tab2');
    await lazyWriting.promise;
    const clearLazy = restart.command('tab2', 'clear');
    lazyRelease.resolve();
    await Promise.all([pendingLazy, clearLazy]);
    assert.equal(restart.snapshot('tab2').cachedBytes, 0);
    assert.equal([...e.storage.keys()].filter(name => name.startsWith(restart.prefix) && !name.endsWith(':meta')).length, 0);

    const debug = environment();
    const count = debug.accessCount();
    assert.equal((await debug.store.command('debug', 'begin', {mode: 'debug', version: {versionName: '1', versionCode: 1, buildId: 'development'}})).snapshot.phase, 'disabled');
    for (const action of ['check', 'download', 'clear', 'restore', 'cancel']) await debug.store.command('debug', action);
    assert.equal(debug.accessCount(), count, 'debug visits must not touch persistent app caches');
    const visits = environment('https://example.test/subpath/');
    const visitVersion = visits.publish(1);
    await visits.store.command('old', 'begin', {liveIds: ['old']});
    visits.setPutHook(async (name, key) => { if (key === visitVersion.url('voice-codec/core.wasm')) throw new DOMException('Quota', 'QuotaExceededError'); });
    assert.equal(await (await visits.store.serve(new Request(visitVersion.url('voice-codec/core.wasm')), 'old')).text(), 'codec runtime', 'quota must not prevent a verified lazy runtime from running');
    assert.equal(visits.store.snapshot('old').error.code, 'quota');
    visits.setPutHook(async () => {});
    await visits.store.command('old', 'clear');
    assert.equal((await visits.store.command('next', 'begin', {liveIds: ['old', 'next']})).snapshot.cached.versionCode, 1);
    assert.equal(visits.store.snapshot('old').suppressed, true);
    assert.equal(visits.store.snapshot('next').suppressed, false);
    const unavailable = environment();
    unavailable.caches.open = async () => { throw new DOMException('Denied', 'SecurityError'); };
    const unavailableState = (await unavailable.store.command('tab', 'begin')).snapshot;
    assert.equal(unavailableState.available, false);
    assert.equal(unavailableState.phase, 'unavailable');
    assert.equal(unavailableState.error.code, 'unavailable');
    const invalid = environment();
    const bad = invalid.publish(1);
    bad.manifest.assets[0].path = '../api/me';
    const bytes = JSON.stringify(bad.manifest);
    bad.pointer.sha256 = hash(bytes);
    invalid.routes.set(invalid.base + bad.pointer.manifest, bytes);
    invalid.routes.set(invalid.base + 'web-version.json', JSON.stringify(bad.pointer));
    assert.equal((await invalid.store.command('tab', 'begin')).snapshot.error.code, 'manifest');
    assert.equal(invalid.store.compare({versionCode: 1, versionName: '0.33'}, {versionCode: 1, versionName: '0.32'}), 1);
    assert.equal(invalid.store.compare({versionCode: 1, versionName: '2.0'}, {versionCode: 2, versionName: '1.0'}), -1);
    checkDebugShell();
    await checkCacheRecovery();
    await checkVoicePreload(false);
    await checkVoicePreload(true);
    for (const href of ['https://example.test/zhihu-plus-plus/', 'https://example.test/zhihu-plus-plus/index.html#route']) {
        await checkBootstrap(href, 'production');
        await checkBootstrap(href, 'debug');
        await checkBootstrap(href, 'debug', true, true);
        await checkBootstrap(href, 'production', false);
        await checkBootstrap(href, 'production', true, false, true);
    }
    console.log('App cache: same/new version, immutable release, atomic failure/quota fallback, pinning, lazy reuse, concurrent clear, restart suppression, scope isolation, subpath/index bootstrap and debug/unavailable checks passed');
})().catch(error => { console.error(error); process.exitCode = 1; });
