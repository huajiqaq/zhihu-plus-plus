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
'use strict';

// 通知只处理页面投递及点击；应用静态资源缓存独立于通知收据，不轮询 API 或伪造 push。
const base = new URL('./', self.location.href);
const receiptPattern = /^zhihu-web-v1-[0-9a-f]{64}$/;
const databaseName = 'zhihu-notification-clicks-v1:' + base.pathname;
const readyClients = new Set();

// Keep this worker URL and scope stable: notification opt-in and receipt recovery use this exact registration.
let appCache;
try {
    importScripts('./zhihu-app-cache.js');
    appCache = new ZhihuAppCacheStore(base.href, (id, snapshot) => {
        self.clients.get(id).then(client => {
            if (trustedClient(client)) client.postMessage({type: 'zhihu-app-cache-changed-v1', snapshot});
        }).catch(() => {});
    });
} catch (error) { console.error('App cache unavailable', error); }
// Use normal activation: an old worker must finish its clients/writes before a new cache owner can run.
self.addEventListener('activate', event => event.waitUntil(self.clients.claim()));
self.addEventListener('message', event => {
    if (!trustedClient(event.source) || !event.ports[0]) return;
    if (event.data?.type === 'zhihu-app-cache-probe-v1') {
        event.ports[0].postMessage({protocol: appCache ? 1 : 0});
        event.ports[0].close();
        return;
    }
    if (event.data?.type !== 'zhihu-app-cache-v1') return;
    const {action, mode, version} = event.data;
    if (!['begin', 'snapshot', 'check', 'download', 'clear', 'restore', 'apply', 'cancel'].includes(action)) return;
    event.waitUntil((async () => {
        if (!appCache) { event.ports[0].postMessage({unavailable: true}); event.ports[0].close(); return; }
        const liveIds = (await self.clients.matchAll({type: 'window', includeUncontrolled: true})).filter(trustedClient).map(client => client.id);
        const result = await appCache.command(event.source.id, action, {mode, version, liveIds});
        event.ports[0].postMessage(result);
        event.ports[0].close();
    })());
});
self.addEventListener('fetch', event => {
    const url = new URL(event.request.url);
    if (!appCache || event.request.method !== 'GET' || url.origin !== base.origin || url.search || !url.pathname.startsWith(base.pathname)) return;
    const path = url.pathname.slice(base.pathname.length);
    if (!path.startsWith('app-resources/') && !['', 'index.html', 'zhihu-app-bootstrap.js', 'manifest.webmanifest', 'icon-192.png', 'icon-512.png'].includes(path)) return;
    event.respondWith(appCache.serve(event.request, event.clientId).then(response => response || fetch(event.request)));
});

function trustedClient(client) {
    if (!client || client.type !== 'window' || client.frameType !== 'top-level') return false;
    const url = new URL(client.url);
    return url.origin === base.origin && !url.username && !url.password &&
        (url.pathname === base.pathname || url.pathname === base.pathname + 'index.html');
}

function withClicks(mode, action) {
    return new Promise((resolve, reject) => {
        const request = indexedDB.open(databaseName, 1);
        request.onupgradeneeded = () => request.result.createObjectStore('clicks');
        request.onerror = () => reject(request.error);
        request.onsuccess = () => {
            const database = request.result;
            const transaction = database.transaction('clicks', mode);
            const result = action(transaction.objectStore('clicks'));
            transaction.oncomplete = () => { database.close(); resolve(result.result); };
            transaction.onabort = transaction.onerror = () => { database.close(); reject(transaction.error); };
        };
    });
}

async function deliver(client) {
    if (!trustedClient(client)) return;
    const receipts = await withClicks('readonly', store => store.getAllKeys());
    for (const receipt of receipts) {
        if (typeof receipt === 'string' && receiptPattern.test(receipt)) {
            client.postMessage({type: 'zhihu-notification-click-v1', receipt});
        }
    }
}

self.addEventListener('message', event => {
    const client = event.source;
    const data = event.data;
    if (!trustedClient(client) || !data || typeof data !== 'object') return;
    if (data.type === 'zhihu-notifications-ready-v1' && Object.keys(data).length === 1) {
        readyClients.add(client.id);
        event.waitUntil((async () => {
            await deliver(client);
            client.postMessage({type: 'zhihu-notifications-ready-v1'});
        })());
    } else if (data.type === 'zhihu-notification-consumed-v1' && Object.keys(data).length === 2 &&
        typeof data.receipt === 'string' && receiptPattern.test(data.receipt)) {
        // 网页必须已经原子消费本机回执；SW 不从外部消息接受导航目标。
        event.waitUntil(withClicks('readwrite', store => store.delete(data.receipt)));
    }
});

self.addEventListener('notificationclick', event => {
    const receipt = event.notification.data?.receipt;
    if (event.action || typeof receipt !== 'string' || !receiptPattern.test(receipt) ||
        event.notification.tag !== receipt) return;
    event.notification.close();
    event.waitUntil((async () => {
        // 先持久化点击，应用下载/登录前 SW 可被停止；不把回执写进 URL 或服务器日志。
        await withClicks('readwrite', store => store.put(true, receipt));
        const clients = (await self.clients.matchAll({type: 'window', includeUncontrolled: true})).filter(trustedClient);
        const target = clients.find(client => readyClients.has(client.id)) || clients[0];
        if (target) {
            await target.focus();
            await deliver(target);
        } else {
            // 冷启动只开固定应用入口；账号核验后由共用运行时从本机回执恢复实际目标。
            await self.clients.openWindow(base.href);
        }
    })());
});
