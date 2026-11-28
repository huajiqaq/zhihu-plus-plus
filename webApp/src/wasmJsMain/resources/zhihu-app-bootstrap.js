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
(() => {
    const meta = name => document.querySelector('meta[name="' + name + '"]')?.content || '';
    const base = new URL('./', location.href);
    const mode = meta('zhihu-cache-mode') === 'production' ? 'production' : 'debug';
    const version = {versionName: meta('zhihu-version-name'), versionCode: Number(meta('zhihu-version-code')), buildId: meta('zhihu-build-id')};
    const workerUrl = new URL('zhihu-notifications-sw.js', base).href;
    let state = {schema: 1, mode, available: false, phase: mode === 'debug' ? 'disabled' : 'starting',
        running: version, cached: null, cachedBytes: 0, coreBytes: 0, downloadedBytes: 0, totalBytes: 0,
        candidate: null, suppressed: false, error: null};
    let worker;
    const snapshot = () => structuredClone(state);
    const update = value => {
        state = value;
        window.dispatchEvent(new CustomEvent('zhihu-app-cache-changed', {detail: snapshot()}));
        const detail = document.getElementById('boot-detail');
        if (!detail) return;
        const chinese = /^zh(?:-|$)/i.test(document.documentElement.lang);
        const text = mode === 'debug'
            ? (chinese ? '本地调试版每次访问都从网络重新加载，不保存应用下载缓存；运行仍需要内存。' : 'Local debug builds reload from the network without persistent app downloads; running still requires memory.')
            : (chinese ? '下载占用浏览器本地存储，运行还需要内存。应用资源复用至发布新版本；编解码器和 NLP 运行库按需缓存，模型另行管理。可在设置删除下载。' :
                'Downloads use browser storage; running also requires memory. App files are reused until a newer version is published. Codecs and NLP runtimes load on demand; models are separate. Delete downloads in Settings.');
        const coreBytes = state.coreBytes || (['downloading', 'verifying'].includes(state.phase) ? state.totalBytes : 0);
        const size = mode === 'production' && coreBytes > 0 ? ' ' + (chinese ? '核心资源大小：' : 'Core download size: ') + (coreBytes / 1048576).toFixed(1) + ' MiB.' : '';
        let progress = '';
        if (state.totalBytes && ['downloading', 'verifying'].includes(state.phase)) {
            progress = ' ' + (chinese ? '正在下载并校验 ' : 'Downloading and verifying ') +
                (state.downloadedBytes / 1048576).toFixed(1) + ' / ' + (state.totalBytes / 1048576).toFixed(1) + ' MiB';
        }
        const warning = state.error?.code === 'worker-update-pending'
            ? (chinese ? ' 缓存组件待生效，请关闭本站旧标签后重开；本次只能从网络加载。' : ' The cache component is waiting to activate. Close the older tabs for this site and reopen it; this visit must load from the network.')
            : state.phase === 'unavailable' ? (chinese ? ' 当前浏览器无法保存应用缓存，将直接从网络加载。' : ' App caching is unavailable in this browser; loading directly from the network.') : '';
        detail.textContent = text + size + progress + warning;
    };
    const unavailable = (code = 'unavailable') => update({...state, available: false, phase: 'unavailable', error: {code, message: code}});
    const request = (action, extras = {}, timeout) => new Promise((resolve, reject) => {
        if (!worker) { reject(new Error('unavailable')); return; }
        const channel = new MessageChannel();
        const timer = setTimeout(() => { channel.port1.close(); reject(new Error('unavailable')); }, timeout ?? (action === 'begin' || action === 'download' || action === 'restore' ? 240000 : 45000));
        channel.port1.onmessage = event => {
            clearTimeout(timer); channel.port1.close();
            if (event.data.unavailable || !event.data.snapshot) { reject(new Error('unavailable')); return; }
            update(event.data.snapshot);
            resolve(event.data);
        };
        worker.postMessage({type: 'zhihu-app-cache-v1', action, mode, version: state.running || version, ...extras}, [channel.port2]);
    });
    const probe = () => new Promise(resolve => {
        const channel = new MessageChannel();
        const timer = setTimeout(() => { channel.port1.close(); resolve(false); }, 2000);
        channel.port1.onmessage = event => {
            clearTimeout(timer); channel.port1.close(); resolve(event.data?.protocol === 1);
        };
        try { worker.postMessage({type: 'zhihu-app-cache-probe-v1'}, [channel.port2]); }
        catch { clearTimeout(timer); channel.port1.close(); resolve(false); }
    });
    const command = async action => {
        await started;
        if (mode === 'debug') {
            if (action === 'apply') location.reload();
            return snapshot();
        }
        if (!worker) { unavailable(state.error?.code || 'unavailable'); return snapshot(); }
        try {
            const result = await request(action);
            if (action === 'apply' && !result.snapshot.error && result.snapshot.candidate?.downloaded) location.reload();
        } catch { unavailable(); }
        return snapshot();
    };
    window.zhihuAppCache = Object.freeze({snapshot, check: () => command('check'), download: () => command('download'),
        clear: () => command('clear'), restore: () => command('restore'), apply: () => command('apply'), cancel: () => command('cancel')});
    navigator.serviceWorker?.addEventListener('message', event => {
        if (event.source?.scriptURL === workerUrl && event.data?.type === 'zhihu-app-cache-changed-v1') update(event.data.snapshot);
    });
    const waitControlled = registration => new Promise((resolve, reject) => {
        const check = () => {
            if (navigator.serviceWorker.controller?.scriptURL !== workerUrl) return;
            cleanup(); resolve(navigator.serviceWorker.controller);
        };
        const cleanup = () => { clearTimeout(timer); navigator.serviceWorker.removeEventListener('controllerchange', check); };
        const timer = setTimeout(() => { cleanup(); reject(new Error('unavailable')); }, 20000);
        navigator.serviceWorker.addEventListener('controllerchange', check);
        if (registration.active) check();
    });
    const inject = launch => {
        const resourceBase = new URL(launch?.resourceBase || meta('zhihu-resource-base') || './', base);
        const entry = new URL(launch?.entry || meta('zhihu-app-entry') || 'zhihu.js', resourceBase);
        if (resourceBase.origin !== base.origin || entry.origin !== base.origin || !entry.href.startsWith(resourceBase.href)) throw new Error('manifest');
        window.zhihuAppResourceBase = resourceBase.href;
        const script = document.createElement('script');
        script.id = 'app-script';
        if (mode === 'debug') entry.searchParams.set('visit', String(Date.now()));
        script.src = entry.href;
        if (launch?.entryHash) {
            script.integrity = 'sha256-' + btoa(String.fromCharCode(...launch.entryHash.match(/../g).map(value => parseInt(value, 16))));
            script.crossOrigin = 'anonymous';
        }
        document.body.appendChild(script);
    };
    let started = Promise.resolve();
    started = (async () => {
        update(state);
        if (mode === 'debug') {
            // Do not register/unregister a worker or delete any storage in debug builds.
            worker = navigator.serviceWorker?.controller;
            if (worker?.scriptURL === workerUrl) {
                try { await request('begin', {mode, version}, 2000); } catch { /* An old notification-only worker cannot delay local debug startup. */ }
            } else worker = null;
            inject();
            return;
        }
        if (!isSecureContext || !navigator.serviceWorker || !globalThis.caches || !crypto?.subtle) {
            unavailable(); inject(); return;
        }
        let registration;
        try {
            registration = await navigator.serviceWorker.register(workerUrl, {scope: base.href, updateViaCache: 'none'});
            worker = await waitControlled(registration);
        } catch { unavailable(); inject(); return; }
        if (!await probe()) {
            worker = null;
            unavailable(registration.waiting || registration.installing ? 'worker-update-pending' : 'unavailable');
            inject(); return;
        }
        let result;
        try { result = await request('begin', {mode, version}); }
        catch { unavailable(); inject(); return; }
        if (!result.launch) {
            if (result.snapshot.phase === 'unavailable') { inject(); return; }
            throw new Error(result.snapshot.error?.code || 'cache-missing');
        }
        inject(result.launch);
    })();
    started.then(async () => {
        if (mode !== 'production' || !state.available || !worker) return;
        // A running document stays pinned. Downloading a new version never reloads unsaved input.
        await command('check');
        if (state.candidate && !state.candidate.downloaded && !state.suppressed && !state.error) await command('download');
    }).catch(error => {
        console.error('App bootstrap failed', error);
        window.dispatchEvent(new Event('zhihu-app-boot-failed'));
    });
})();
