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

// The worker owns all app-cache writes. No account, notification, OPFS or NLP model storage is opened here.
globalThis.ZhihuAppCacheStore = class {
    constructor(base, notify = () => {}) {
        this.base = new URL(base);
        this.prefix = 'zhihu++:app:v1:' + encodeURIComponent(this.base.pathname) + ':';
        this.metaKey = new URL('__zhihu_app_cache_state__', this.base).href;
        this.notify = notify;
        this.records = {};
        this.pins = {};
        this.states = {};
        this.queue = Promise.resolve();
        this.saveQueue = Promise.resolve();
        this.pending = new Set();
        this.controllers = new Set();
        this.epoch = 0;
        this.clearing = false;
        this.loaded = false;
    }

    fail(code) { return Object.assign(new Error(code), {code}); }
    error(error) {
        const code = error?.name === 'QuotaExceededError' ? 'quota' : error?.name === 'AbortError' ? 'cancelled' :
            ['unavailable', 'network', 'manifest', 'integrity', 'immutable-version', 'quota', 'cancelled', 'cache-missing', 'cache-metadata-invalid'].includes(error?.code) ? error.code : 'unavailable';
        // Never expose URLs, response bodies, browser storage exceptions or credentials to the UI.
        return {code, message: code};
    }
    key(version) { return version.versionCode + '-' + version.versionName; }
    version(record) {
        if (!record) return null;
        const {versionName, versionCode, buildId} = record.pointer;
        return {versionName, versionCode, buildId};
    }
    compare(a, b) {
        if (a.versionCode !== b.versionCode) return Math.sign(a.versionCode - b.versionCode);
        if (a.versionName === b.versionName) return 0;
        // versionCode is authoritative; equal-code numeric version names may advance, never arbitrary hashes.
        const parse = name => /^(\d+(?:\.\d+)*)(?:-([A-Za-z0-9.-]+))?$/.exec(name);
        const x = parse(a.versionName), y = parse(b.versionName);
        if (!x || !y) return 0;
        const left = x[1].split('.').map(Number), right = y[1].split('.').map(Number);
        for (let i = 0; i < Math.max(left.length, right.length); i++) {
            if ((left[i] || 0) !== (right[i] || 0)) return Math.sign((left[i] || 0) - (right[i] || 0));
        }
        if (!x[2] && y[2]) return 1;
        if (x[2] && !y[2]) return -1;
        const preLeft = (x[2] || '').split('.'), preRight = (y[2] || '').split('.');
        for (let i = 0; i < Math.max(preLeft.length, preRight.length); i++) {
            const l = preLeft[i], r = preRight[i];
            if (l === r) continue;
            if (l === undefined || r === undefined) return l === undefined ? -1 : 1;
            const ln = /^\d+$/.test(l), rn = /^\d+$/.test(r);
            if (ln && rn) return Math.sign(Number(l) - Number(r));
            if (ln !== rn) return ln ? -1 : 1;
            return l < r ? -1 : 1;
        }
        return 0;
    }
    validPath(path) {
        return typeof path === 'string' && path.length > 0 && path.length < 1024 &&
            !/[\\%?#:\u0000-\u001f\u007f]/.test(path) && path.split('/').every(part => part && part !== '.' && part !== '..');
    }
    validatePointer(value) {
        if (!value || value.schema !== 1 || !Number.isSafeInteger(value.versionCode) || value.versionCode < 1 ||
            typeof value.versionName !== 'string' || !/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/.test(value.versionName) ||
            typeof value.buildId !== 'string' || !/^[a-f0-9]{16,64}$/.test(value.buildId) ||
            typeof value.sha256 !== 'string' || !/^[a-f0-9]{64}$/.test(value.sha256) ||
            value.manifest !== 'app-resources/' + this.key(value) + '/app-assets.json') throw this.fail('manifest');
        return value;
    }
    validateManifest(value, pointer) {
        if (!value || value.schema !== 1 || value.versionCode !== pointer.versionCode || value.versionName !== pointer.versionName ||
            value.buildId !== pointer.buildId || value.base !== 'app-resources/' + this.key(pointer) + '/' ||
            !Array.isArray(value.assets) || !value.assets.length || value.assets.length > 10000 || !this.validPath(value.entry)) throw this.fail('manifest');
        const paths = new Set();
        let bytes = 0;
        for (const asset of value.assets) {
            if (!asset || !this.validPath(asset.path) || paths.has(asset.path) || /\.(?:onnx|onnx_data)$/i.test(asset.path) ||
                !Number.isSafeInteger(asset.bytes) || asset.bytes < 0 || asset.bytes > 536870912 ||
                !/^[a-f0-9]{64}$/.test(asset.sha256) || !['core', 'lazy'].includes(asset.group)) throw this.fail('manifest');
            paths.add(asset.path);
            bytes += asset.bytes;
        }
        if (bytes > 2147483648 || !value.assets.some(asset => asset.path === value.entry && asset.group === 'core') ||
            !value.assets.some(asset => asset.path === 'index.html' && asset.group === 'core')) throw this.fail('manifest');
        return value;
    }
    assetUrl(record, asset) { return new URL(record.manifest.base + asset.path, this.base).href; }
    totals(record) {
        return record.manifest.assets.reduce((sum, asset) => {
            sum.total += asset.bytes;
            if (asset.group === 'core') sum.core += asset.bytes;
            if (record.stored.includes(asset.path)) sum.stored += asset.bytes;
            return sum;
        }, {total: 0, core: 0, stored: 0});
    }
    state(id) {
        return this.states[id] ||= {phase: 'idle', error: null, candidate: null, downloadedBytes: 0, totalBytes: 0};
    }
    snapshot(id) {
        const pin = this.pins[id];
        const state = this.state(id);
        const running = this.records[pin?.key];
        const candidate = this.records[state.candidate || pin?.candidate];
        const cached = running?.complete ? running : Object.values(this.records).filter(record => record.complete)
            .sort((a, b) => this.compare(b.pointer, a.pointer))[0];
        const debug = pin?.mode === 'debug';
        return {
            schema: 1, mode: debug ? 'debug' : 'production', available: !debug && this.available !== false,
            phase: debug ? 'disabled' : this.available === false ? 'unavailable' : state.phase,
            running: this.version(running) || pin?.version || null,
            cached: debug ? null : this.version(cached),
            cachedBytes: debug ? 0 : Object.values(this.records).reduce((sum, record) => sum + this.totals(record).stored, 0),
            coreBytes: running ? this.totals(running).core : 0,
            downloadedBytes: state.downloadedBytes, totalBytes: state.totalBytes,
            candidate: candidate ? {...this.version(candidate), coreBytes: this.totals(candidate).core,
                totalBytes: this.totals(candidate).total, downloaded: candidate.complete} : null,
            suppressed: !!pin?.suppressed, error: state.error
        };
    }
    emit(id) { this.notify(id, this.snapshot(id)); }
    async load() {
        if (this.loaded) return;
        if (!this.loading) this.loading = this.loadState().finally(() => { this.loading = null; });
        return this.loading;
    }
    async loadState() {
        try {
            const meta = await caches.open(this.prefix + 'meta');
            const response = await meta.match(this.metaKey);
            if (response) {
                try {
                    const saved = await response.json();
                    if (saved.schema !== 1 || !saved.records || !saved.pins || typeof saved.records !== 'object' || typeof saved.pins !== 'object' ||
                        Array.isArray(saved.records) || Array.isArray(saved.pins)) throw this.fail('manifest');
                    for (const [key, record] of Object.entries(saved.records)) {
                        this.validatePointer(record.pointer);
                        this.validateManifest(record.manifest, record.pointer);
                        if (key !== this.key(record.pointer) || !Array.isArray(record.stored)) throw this.fail('manifest');
                    }
                    for (const pin of Object.values(saved.pins)) {
                        if (!pin || !['debug', 'production'].includes(pin.mode) || typeof pin.suppressed !== 'boolean') throw this.fail('manifest');
                    }
                    this.records = saved.records;
                    this.pins = {...saved.pins, ...this.pins};
                } catch {
                    this.metadataInvalid = true;
                    throw this.fail('cache-metadata-invalid');
                }
            }
            this.metadataInvalid = false;
            this.available = true;
            this.loaded = true;
        } catch (error) { this.available = false; throw error; }
    }
    save() {
        const job = this.saveQueue.then(async () => {
            const meta = await caches.open(this.prefix + 'meta');
            await meta.put(this.metaKey, new Response(JSON.stringify({schema: 1, records: this.records, pins: this.pins}),
                {headers: {'Content-Type': 'application/json'}}));
        });
        this.saveQueue = job.catch(() => {});
        return job;
    }
    async prunePins(liveIds) {
        await this.load();
        for (const id of Object.keys(this.pins)) if (!liveIds.includes(id)) { delete this.pins[id]; delete this.states[id]; }
    }
    checkEpoch(epoch) { if (epoch !== this.epoch) throw this.fail('cancelled'); }
    async network(url, limit, controller, progress = () => {}) {
        let response;
        try {
            response = await fetch(url, {mode: 'same-origin', credentials: 'omit', cache: 'no-store', redirect: 'error',
                referrerPolicy: 'no-referrer', signal: controller.signal});
        } catch (error) { if (controller.signal.aborted) throw this.fail('cancelled'); throw this.fail('network'); }
        if (response.status !== 200 || !response.body || Number(response.headers.get('Content-Length')) > limit) throw this.fail('network');
        const reader = response.body.getReader(), parts = [];
        let length = 0;
        try {
            for (;;) {
                controller.signal.throwIfAborted();
                const {done, value} = await reader.read();
                if (done) break;
                length += value.byteLength;
                if (length > limit) throw this.fail('integrity');
                parts.push(value);
                progress(length);
            }
        } finally { await reader.cancel().catch(() => {}); reader.releaseLock(); }
        const bytes = new Uint8Array(length);
        let offset = 0;
        for (const part of parts) { bytes.set(part, offset); offset += part.byteLength; }
        return {bytes, type: response.headers.get('Content-Type') || 'application/octet-stream'};
    }
    async digest(bytes) {
        return Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', bytes)), byte => byte.toString(16).padStart(2, '0')).join('');
    }
    async withNetwork(action, timeout = 180000) {
        const controller = new AbortController();
        this.controllers.add(controller);
        const timer = setTimeout(() => controller.abort(), timeout);
        try { return await action(controller); }
        finally { clearTimeout(timer); this.controllers.delete(controller); }
    }
    async published(id, epoch, controller) {
        const state = this.state(id);
        state.phase = 'checking'; state.error = null; state.downloadedBytes = 0; state.totalBytes = 0; this.emit(id);
        const data = await this.network(new URL('web-version.json', this.base).href, 2048, controller);
        let pointer;
        try { pointer = this.validatePointer(JSON.parse(new TextDecoder('utf-8', {fatal: true}).decode(data.bytes))); }
        catch { throw this.fail('manifest'); }
        this.checkEpoch(epoch);
        const known = this.records[this.key(pointer)];
        if (known) {
            if (known.pointer.sha256 !== pointer.sha256 || known.pointer.buildId !== pointer.buildId) throw this.fail('immutable-version');
            return known;
        }
        const running = this.records[this.pins[id]?.key];
        if (running && this.compare(pointer, running.pointer) <= 0) return running;
        const inventory = await this.network(new URL(pointer.manifest, this.base).href, 4194304, controller);
        state.phase = 'verifying'; this.emit(id);
        if (await this.digest(inventory.bytes) !== pointer.sha256) throw this.fail('integrity');
        let manifest;
        try { manifest = this.validateManifest(JSON.parse(new TextDecoder('utf-8', {fatal: true}).decode(inventory.bytes)), pointer); }
        catch { throw this.fail('manifest'); }
        this.checkEpoch(epoch);
        const record = {pointer, manifest, complete: false, stored: []};
        this.records[this.key(pointer)] = record;
        await this.save();
        return record;
    }
    async validCached(response, asset, verifyHash = true) {
        if (!response || response.status !== 200 || !response.body || Number(response.headers.get('Content-Length')) !== asset.bytes) return false;
        const reader = response.body.getReader();
        const bytes = verifyHash ? new Uint8Array(asset.bytes) : null;
        let length = 0;
        try {
            for (;;) {
                const {done, value} = await reader.read();
                if (done) break;
                if (length + value.byteLength > asset.bytes) return false;
                if (bytes) bytes.set(value, length);
                length += value.byteLength;
            }
            return length === asset.bytes && (!verifyHash || await this.digest(bytes) === asset.sha256);
        } catch { return false; }
        finally { await reader.cancel().catch(() => {}); reader.releaseLock(); }
    }
    async reconcileRecord(record, names) {
        const epoch = this.epoch;
        const name = this.prefix + this.key(record.pointer);
        const stored = [];
        if (names.includes(name)) {
            const cache = await caches.open(name);
            const assets = new Map(record.manifest.assets.map(asset => [this.assetUrl(record, asset), asset]));
            for (const request of await cache.keys()) {
                const asset = assets.get(request.url);
                const response = asset ? await cache.match(request.url) : null;
                // Audit core hashes locally at startup; lazy payloads are hashed when requested, not downloaded for accounting.
                if (asset && await this.validCached(response, asset, asset.group === 'core')) stored.push(asset.path);
                else {
                    this.checkEpoch(epoch);
                    await cache.delete(request);
                }
            }
        }
        this.checkEpoch(epoch);
        const complete = record.complete && record.manifest.assets.filter(asset => asset.group === 'core').every(asset => stored.includes(asset.path));
        const changed = record.complete !== complete || record.stored.length !== stored.length || record.stored.some(path => !stored.includes(path));
        record.complete = complete;
        record.stored = stored;
        return changed;
    }
    async reconcileCaches() {
        const names = await caches.keys();
        let changed = false;
        for (const record of Object.values(this.records)) changed = await this.reconcileRecord(record, names) || changed;
        if (changed) await this.save();
    }
    async hasCore(record) {
        if (await this.reconcileRecord(record, await caches.keys())) await this.save();
        return record.complete;
    }
    async asset(record, asset, epoch, controller, id) {
        const before = id ? this.state(id).downloadedBytes : 0;
        let lastProgress = 0;
        const result = await this.network(this.assetUrl(record, asset), asset.bytes, controller, received => {
            if (!id) return;
            this.state(id).downloadedBytes = before + received;
            if (Date.now() - lastProgress > 100 || received === asset.bytes) { lastProgress = Date.now(); this.emit(id); }
        });
        this.checkEpoch(epoch);
        if (id) { this.state(id).phase = 'verifying'; this.emit(id); }
        if (result.bytes.byteLength !== asset.bytes || await this.digest(result.bytes) !== asset.sha256) throw this.fail('integrity');
        this.checkEpoch(epoch);
        return new Response(result.bytes, {headers: {'Content-Type': result.type, 'Content-Length': String(asset.bytes), 'Cache-Control': 'no-store'}});
    }
    async download(id, record, epoch, controller) {
        if (this.pins[id]?.suppressed || this.clearing) throw this.fail('cancelled');
        if (await this.hasCore(record)) return;
        const state = this.state(id);
        state.totalBytes = this.totals(record).core; state.downloadedBytes = 0;
        const name = this.prefix + this.key(record.pointer);
        // A cache is selectable only after its final, atomic metadata commit. Previous complete versions are untouched.
        await caches.delete(name);
        const cache = await caches.open(name);
        record.stored = [];
        try {
            for (const asset of record.manifest.assets.filter(item => item.group === 'core')) {
                this.checkEpoch(epoch);
                state.phase = 'downloading'; this.emit(id);
                const response = await this.asset(record, asset, epoch, controller, id);
                this.checkEpoch(epoch);
                await cache.put(this.assetUrl(record, asset), response);
                this.checkEpoch(epoch);
                record.stored.push(asset.path);
                this.emit(id);
            }
            record.complete = true;
            await this.save();
            this.checkEpoch(epoch);
        } catch (error) {
            record.complete = false; record.stored = [];
            await caches.delete(name);
            await this.save().catch(() => {});
            throw error;
        }
    }
    abort() {
        this.epoch++;
        for (const controller of this.controllers) controller.abort();
    }
    command(id, action, options = {}) {
        if (this.pins[id]?.mode === 'debug' && action !== 'begin') return Promise.resolve({snapshot: this.snapshot(id)});
        if (action === 'cancel' || action === 'clear') this.abort();
        if (action === 'clear') {
            this.clearing = true;
            for (const pin of Object.values(this.pins)) if (pin.mode !== 'debug') pin.suppressed = true;
        }
        const epoch = this.epoch;
        const job = this.queue.then(() => this.perform(id, action, options, epoch));
        this.queue = job.catch(() => {});
        return job;
    }
    async perform(id, action, options, epoch) {
        const state = this.state(id);
        let launch = null;
        try {
            if (action === 'begin' && options.mode === 'debug') {
                // No CacheStorage calls in an explicit local debug build.
                this.pins[id] = {mode: 'debug', suppressed: true, version: options.version};
                return {snapshot: this.snapshot(id)};
            }
            if (action === 'begin') this.pins[id] = {mode: 'production', suppressed: false, version: options.version};
            if (action === 'clear') {
                // A corrupt metadata document must not prevent the only operation that can remove it safely.
                try { await this.load(); } catch { /* Scope ownership comes from the worker URL, not stored JSON. */ }
            } else await this.load();
            if (action !== 'clear') this.checkEpoch(epoch);
            if (action === 'begin' || action === 'snapshot') await this.reconcileCaches();
            if (action === 'begin') {
                await this.prunePins(options.liveIds || [id]);
                this.pins[id] = {mode: 'production', suppressed: false, version: options.version};
                const versions = Object.values(this.records).filter(record => record.complete)
                    .sort((a, b) => this.compare(b.pointer, a.pointer));
                let record = versions[0];
                if (!record) {
                    record = await this.withNetwork(async controller => {
                        const published = await this.published(id, epoch, controller);
                        try { await this.download(id, published, epoch, controller); }
                        catch (error) { if (this.error(error).code !== 'quota') throw error; state.error = this.error(error); }
                        return published;
                    });
                }
                this.pins[id].key = this.key(record.pointer);
                launch = {resourceBase: new URL(record.manifest.base, this.base).href, entry: record.manifest.entry,
                    entryHash: record.manifest.assets.find(asset => asset.path === record.manifest.entry).sha256};
                await this.save();
            } else if (this.pins[id]?.mode === 'debug') {
                return {snapshot: this.snapshot(id)};
            } else if (action === 'check') {
                await this.withNetwork(async controller => {
                    const published = await this.published(id, epoch, controller);
                    const running = this.records[this.pins[id]?.key];
                    state.candidate = !running || this.compare(published.pointer, running.pointer) > 0 ? this.key(published.pointer) : null;
                    this.pins[id].candidate = state.candidate;
                    if (published.complete) await this.hasCore(published);
                    await this.save();
                }, 30000);
            } else if (action === 'download' || action === 'restore') {
                const pin = this.pins[id];
                if (!pin) throw this.fail('cache-missing');
                if (action === 'restore') { pin.suppressed = false; await this.save(); }
                let record = this.records[action === 'restore' ? pin.key : state.candidate || pin.candidate || pin.key];
                state.error = null;
                await this.withNetwork(async controller => {
                    if (!record && action === 'restore') {
                        const running = pin.version || options.version;
                        const published = await this.published(id, epoch, controller);
                        if (!running || running.versionCode !== published.pointer.versionCode || running.versionName !== published.pointer.versionName ||
                            running.buildId !== published.pointer.buildId) throw this.fail('cache-missing');
                        record = published;
                        pin.key = this.key(record.pointer);
                    }
                    if (!record) throw this.fail('cache-missing');
                    await this.download(id, record, epoch, controller);
                });
            } else if (action === 'clear') {
                state.phase = 'clearing'; this.emit(id);
                // Wait for already-started writes before deleting; late fetches can never recreate a deleted cache.
                await Promise.allSettled([...this.pending]);
                await this.saveQueue;
                for (const name of await caches.keys()) if (name.startsWith(this.prefix)) await caches.delete(name);
                for (const record of Object.values(this.records)) { record.complete = false; record.stored = []; }
                for (const clientId of new Set([id, ...(options.liveIds || [])])) {
                    this.pins[clientId] ||= {mode: 'production', suppressed: true};
                }
                if (!this.pins[id].version && options.version) this.pins[id].version = options.version;
                for (const [clientId, pin] of Object.entries(this.pins)) {
                    if (pin.mode !== 'debug') pin.suppressed = true;
                    pin.candidate = null;
                    Object.assign(this.state(clientId), {candidate: null, error: null, phase: 'idle', downloadedBytes: 0, totalBytes: 0});
                }
                // Only a scope-local control tombstone survives: it prevents recaching by still-open tabs after worker suspension.
                await this.save();
                this.available = true;
                this.loaded = true;
                this.metadataInvalid = false;
            } else if (action === 'apply') {
                state.error = null;
                const record = this.records[state.candidate || this.pins[id]?.candidate];
                if (!record || !await this.hasCore(record)) throw this.fail('cache-missing');
            } else if (action === 'cancel') {
                state.error = {code: 'cancelled', message: 'cancelled'};
            } else if (action !== 'snapshot') throw this.fail('manifest');
            if (state.phase !== 'idle') state.phase = 'idle';
        } catch (error) {
            state.error = this.metadataInvalid ? this.error(this.fail('cache-metadata-invalid')) : this.error(error);
            state.phase = this.available === false ? 'unavailable' : 'idle';
        } finally {
            if (action === 'clear') this.clearing = false;
        }
        for (const clientId of Object.keys(this.pins)) this.emit(clientId);
        return {snapshot: this.snapshot(id), launch};
    }
    serve(request, clientId) {
        const job = this.serveRequest(request, clientId);
        this.pending.add(job);
        job.finally(() => this.pending.delete(job)).catch(() => {});
        return job;
    }
    async serveRequest(request, clientId) {
        if (request.method !== 'GET') return null;
        const url = new URL(request.url);
        if (url.origin !== this.base.origin || url.search || url.username || url.password) return null;
        if (!url.pathname.startsWith(this.base.pathname)) return null;
        const relative = url.pathname.slice(this.base.pathname.length);
        if (!relative.startsWith('app-resources/') && !['', 'index.html', 'zhihu-app-bootstrap.js', 'manifest.webmanifest', 'icon-192.png', 'icon-512.png'].includes(relative)) return null;
        if (this.pins[clientId]?.mode === 'debug') return fetch(new Request(request, {cache: 'no-store'}));
        if (!this.loaded) await this.load().catch(() => {});
        const pin = this.pins[clientId];
        const records = Object.values(this.records);
        let record = records.find(item => url.href.startsWith(new URL(item.manifest.base, this.base).href));
        let asset = record?.manifest.assets.find(item => this.assetUrl(record, item) === url.href);
        const rootPath = url.pathname.slice(this.base.pathname.length);
        const shell = ['', 'index.html', 'zhihu-app-bootstrap.js', 'manifest.webmanifest', 'icon-192.png', 'icon-512.png'].includes(rootPath) &&
            url.pathname.startsWith(this.base.pathname);
        if (!asset && shell) {
            // Shell files are declared in the same complete inventory, never API or arbitrary same-origin traffic.
            record = records.filter(item => item.complete).sort((a, b) => this.compare(b.pointer, a.pointer))[0];
            asset = record?.manifest.assets.find(item => item.path === (rootPath || 'index.html'));
        }
        if (shell && (rootPath === '' || rootPath === 'index.html')) {
            // Always observe the build's explicit debug/production marker; an offline visit may use the verified old shell.
            try {
                const response = await this.withNetwork(controller => fetch(new Request(request, {cache: 'no-store', signal: controller.signal})), 15000);
                if (response.status === 200) return response;
            } catch { /* Keep the last complete shell when the network is unavailable. */ }
        }
        if (!record || !asset) return null;
        const epoch = this.epoch;
        const name = this.prefix + this.key(record.pointer);
        const wasComplete = record.complete;
        if (record.complete && !pin?.suppressed && !this.clearing) {
            const cache = await caches.open(name);
            const cached = await cache.match(this.assetUrl(record, asset));
            if (cached && await this.validCached(cached.clone(), asset)) return cached;
            this.checkEpoch(epoch);
            await cache.delete(this.assetUrl(record, asset));
            record.stored = record.stored.filter(path => path !== asset.path);
            if (asset.group === 'core') record.complete = false;
            await this.save();
            for (const id of Object.keys(this.pins)) this.emit(id);
        }
        // A clear keeps running code alive; uncached lazy files may still be fetched, but not written again by that visit.
        const canWrite = !this.clearing && pin && !pin.suppressed && wasComplete;
        try {
            return await this.withNetwork(async controller => {
                const response = await this.asset(record, asset, epoch, controller);
                if (canWrite && !this.clearing && !pin.suppressed && epoch === this.epoch) {
                    try {
                        const cache = await caches.open(name);
                        await cache.put(this.assetUrl(record, asset), response.clone());
                        this.checkEpoch(epoch);
                        if (!record.stored.includes(asset.path)) record.stored.push(asset.path);
                        if (wasComplete) record.complete = record.manifest.assets.filter(item => item.group === 'core').every(item => record.stored.includes(item.path));
                        await this.save();
                    } catch (error) {
                        // A verified runtime can still run when the browser refuses to persist another file.
                        this.state(clientId).error = this.error(error);
                    }
                    for (const id of Object.keys(this.pins)) this.emit(id);
                }
                return response;
            });
        } catch (error) {
            if (pin) { this.state(clientId).error = this.error(error); this.emit(clientId); }
            return new Response('App resource unavailable', {status: 503, headers: {'Content-Type': 'text/plain'}});
        }
    }
};
