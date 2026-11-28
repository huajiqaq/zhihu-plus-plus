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

(() => {
    'use strict';

    const COLLECTION_PREFIX = 'com.github.zly2006.zhplus.openinbrowser';
    const statusEl = document.getElementById('status');
    const hintEl = document.getElementById('hint');
    const loginButton = document.getElementById('login');

    const message = key => chrome.i18n.getMessage(key);
    document.documentElement.lang = message('htmlLang');
    document.querySelectorAll('[data-i18n]').forEach(el => { el.textContent = message(el.dataset.i18n); });

    function show(status, hint) {
        statusEl.textContent = message(status);
        hintEl.textContent = hint ? message(hint) : '';
        loginButton.hidden = true;
    }

    function showLogin() {
        show('loginRequired', 'loginHint');
        loginButton.hidden = false;
    }

    async function request(path) {
        const response = await fetch(`https://www.zhihu.com${path}`, {
            credentials: 'include',
            signal: AbortSignal.timeout(10000),
        });
        if (!response.ok) {
            const error = new Error(`HTTP ${response.status}`);
            error.status = response.status;
            throw error;
        }
        return response.json();
    }

    async function findCollection() {
        let token = null;
        try {
            const me = await request('/api/v4/me');
            token = me.url_token || me.urlToken || null;
        } catch (_) {
            // 退回使用 self 别名。
        }
        const paths = token
            ? [`/api/v4/people/${token}/collections?limit=50`]
            : ['/api/v4/people/self/collections?limit=50'];
        let lastError = null;
        for (const path of paths) {
            try {
                const payload = await request(path);
                const collections = Array.isArray(payload.data) ? payload.data : [];
                const found = collections.find((collection) =>
                    (collection.description || '').startsWith(COLLECTION_PREFIX),
                );
                if (found) return found;
            } catch (error) {
                lastError = error;
            }
        }
        if (lastError) throw lastError;
        return null;
    }

    function contentHref(content) {
        if (!content) return null;
        if (content.type === 'answer') {
            if (content.question?.id) {
                return `https://www.zhihu.com/question/${content.question.id}/answer/${content.id}`;
            }
            return `https://www.zhihu.com/answer/${content.id}`;
        }
        if (content.type === 'article') {
            return `https://zhuanlan.zhihu.com/p/${content.id}`;
        }
        if (content.type === 'pin') {
            return `https://www.zhihu.com/pin/${content.id}`;
        }
        if (content.type === 'zvideo') {
            return `https://www.zhihu.com/zvideo/${content.id}`;
        }
        if (typeof content.url === 'string' && /^https:\/\/(www|zhuanlan)\.zhihu\.com\//.test(content.url)) {
            return content.url;
        }
        return null;
    }

    function contentTime(item) {
        const value = Number(item.created);
        if (Number.isFinite(value)) return value;
        const parsed = Date.parse(item.created);
        return Number.isFinite(parsed) ? parsed : 0;
    }

    async function openLatest() {
        try {
            const collection = await findCollection();
            if (!collection) {
                show('noCollection', 'noCollectionHint');
                return;
            }
            const payload = await request(`/api/v4/collections/${collection.id}/items?offset=0&limit=20`);
            const items = Array.isArray(payload.data) ? payload.data : [];
            const newest = items
                .filter((item) => item && item.content)
                .sort((left, right) => contentTime(right) - contentTime(left))[0];
            if (!newest) {
                show('emptyCollection', 'emptyCollectionHint');
                return;
            }
            const href = contentHref(newest.content);
            if (!href) {
                show('cannotOpen');
                return;
            }
            chrome.tabs.create({ url: href }, () => window.close());
        } catch (error) {
            if (error.status === 401 || error.status === 403) {
                showLogin();
            } else if (error.name === 'TimeoutError' || error.name === 'AbortError') {
                show('timedOut', 'retryHint');
            } else {
                show('readFailed', 'retryHint');
            }
        }
    }

    loginButton.addEventListener('click', () => {
        chrome.tabs.create({ url: 'https://www.zhihu.com/signin' }, () => window.close());
    });

    openLatest();
})();
