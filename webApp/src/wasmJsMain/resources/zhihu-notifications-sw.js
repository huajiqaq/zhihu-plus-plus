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

// 仅处理已经由页面投递的系统通知及点击；没有 fetch 缓存、定时联网或伪造 push。
const base = new URL('./', self.location.href);
const receiptPattern = /^zhihu-web-v1-[0-9a-f]{64}$/;
const databaseName = 'zhihu-notification-clicks-v1:' + base.pathname;
const readyClients = new Set();

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
