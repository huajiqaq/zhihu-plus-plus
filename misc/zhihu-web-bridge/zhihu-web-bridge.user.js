// ==UserScript==
// @name              Zhihu++ 浏览器桥
// @name:en           Zhihu++ Browser Bridge
// @namespace         https://github.com/zly2006/zhihu-plus-plus
// @version           1.0.0
// @description       Named Zhihu reading, interaction, publishing and media operations with separate per-tab grants. Credentials remain inside the bridge.
// @description:zh-CN 为知乎++提供具名读取、互动、创作与媒体上传；按标签页独立授权，凭据不进入应用页面。
// @license           AGPL-3.0-only
// @homepageURL       https://github.com/zly2006/zhihu-plus-plus
// @supportURL        https://github.com/zly2006/zhihu-plus-plus/issues
// @downloadURL       https://zly2006.github.io/zhihu-plus-plus/zhihu-web-bridge.user.js
// @updateURL         https://zly2006.github.io/zhihu-plus-plus/zhihu-web-bridge.user.js
// @match             http://127.0.0.1/*
// @match             https://zly2006.github.io/zhihu-plus-plus/*
// @match             https://www.zhihu.com/signin
// @match             https://www.zhihu.com/api/v4/me
// @match             https://api.zhihu.com/people/self
// @match             https://lens.zhihu.com/api/v4/videos
// @noframes
// @run-at            document-start
// @sandbox           DOM
// @grant             GM_info
// @grant             GM_registerMenuCommand
// @grant             GM_xmlhttpRequest
// @grant             GM_cookie
// @connect           www.zhihu.com
// @connect           api.zhihu.com
// @connect           lens.zhihu.com
// @connect           zhihu-pics-upload.zhimg.com
// @connect           upload-oss.vzuu.com
// ==/UserScript==

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
    const entryPage = new URL(location.href);
    const trustedPage = () => {
        const page = new URL(location.href);
        return window.top === window && !page.username && !page.password &&
            page.origin === entryPage.origin && page.pathname === entryPage.pathname &&
            ((page.origin === 'http://127.0.0.1:8080' && ['/', '/index.html'].includes(page.pathname)) ||
                (page.origin === 'https://zly2006.github.io' && ['/zhihu-plus-plus/', '/zhihu-plus-plus/index.html'].includes(page.pathname)));
    };
    if (!trustedPage()) return;
    const site = entryPage.origin + entryPage.pathname;

    const WWW = 'https://www.zhihu.com';
    const ME = `${WWW}/api/v4/me`;
    const VERIFY = `${WWW}/api/v4/anticrawl/new_captcha_appeal`;
    const FORWARD = 'https://api.zhihu.com/unify-consumption/forward/list';
    const HISTORY = 'https://api.zhihu.com/unify-consumption/read_history';
    const HISTORY_SELF = 'https://api.zhihu.com/people/self';
    const TAG = 'zhihu++/bridge/1';
    const MAX_BODY = 4 * 1024 * 1024;
    const controls = /[\x00-\x1f\x7f-\x9f]/u;
    const numberId = /^[1-9][0-9]{0,18}$/u;
    const pageNumber = /^(0|[1-9][0-9]{0,6})$/u;
    const cursor = /^[A-Za-z0-9_+.\/=:,-]{0,512}$/u;
    const pageLimit = /^(?:[1-9]|[1-4][0-9]|50)$/u;
    const feedInclude = 'data[*].content,excerpt,headline,target.author.badge_v2';
    const commentInclude = [feedInclude, 'data[*].content,excerpt,headline'];
    const includes = {
        answer: '.settings,content,editable_content,paid_info,can_comment,excerpt,thanks_count,voteup_count,comment_count,visited_count,attachment,reaction,ip_info,pagination_info,endorsements,question.topics,question.author,reaction.relation.voting,author.badge_v2,settings.table_of_contents.enabled',
        article: 'content,topics,paid_info,can_comment,excerpt,thanks_count,voteup_count,comment_count,visited_count,relationship,ip_info,relationship.vote,author.badge_v2',
        question: 'read_count,visit_count,answer_count,voteup_count,comment_count,follower_count,detail,excerpt,author,relationship.is_following,topics',
        pin: 'topics',
    };
    function check(ok) {
        if (!ok) throw new Error('denied');
    }
    const userActivated = () => navigator.userActivation?.isActive === true;
    const chooseText = (en, zh) => menuLanguage ? zh : en;

    // 缺少管理器信息不能证明安全支持 manual redirect，也不能使诊断监听器安装失败。
    const manager = typeof GM_info === 'object' && GM_info !== null ? GM_info : null;
    const managerVersion = typeof manager?.version === 'string'
        ? /^(\d+)\.(\d+)(?:\.\d+)*(?:[-+ ][A-Za-z0-9][A-Za-z0-9.+ -]*)?$/u.exec(manager.version.trim())
        : null;
    const readIssue = !manager ? 'manager-info-missing'
        : manager.scriptHandler !== 'Tampermonkey' ? 'manager-unsupported'
        : !managerVersion ? 'manager-version-unknown'
        : (+managerVersion[1] < 5 || (+managerVersion[1] === 5 && +managerVersion[2] < 4)) ? 'redirect-unsupported'
        : typeof GM_xmlhttpRequest !== 'function' ? 'request-api-missing'
        : null;
    const cookieIssue = typeof GM_cookie === 'undefined' ||
        ['list', 'set', 'delete'].some(method => typeof GM_cookie?.[method] !== 'function') ? 'cookie-api-missing'
        : typeof navigator.locks?.request !== 'function' ? 'web-locks-missing'
        : typeof globalThis.crypto?.randomUUID !== 'function' ? 'secure-random-missing'
        : null;
    function bridgeStatus() {
        return {
            version: '1.0.0',
            capabilities: {read: readIssue === null, cookie: cookieIssue === null, readIssue, cookieIssue},
            permission: {profile: profileChecking ? 'checking' : profileAllowed ? 'granted' : 'denied', business: [...businessScopes]},
        };
    }
    function notifyPermission() {
        window.dispatchEvent(new Event('zhihu++/permission/changed/1'));
    }

    // 只保留官方验证路径和会话参数，不接受 challenge 的外跳地址或页面提供的 URL。
    function verificationUrl(raw) {
        if (typeof raw !== 'string' || !raw || raw.length > 2048 || /[\s\x5c]/u.test(raw) || controls.test(raw)) return null;
        let url;
        try { url = new URL(raw, WWW); } catch { return null; }
        if (url.origin !== WWW || url.username || url.password || url.hash ||
            !/^\/account\/(?:unhuman|risk_control)\/?$/u.test(url.pathname) || url.searchParams.get('need_login') === 'true') return null;
        const session = url.searchParams.getAll('session');
        if (session.length > 1 || (session.length && !/^[A-Za-z0-9_.~+/=-]{1,1024}$/u.test(session[0]))) return null;
        const clean = new URL(url.pathname, WWW);
        if (session.length) clean.searchParams.set('session', session[0]);
        return clean.href;
    }

    function challengeFromBody(status, raw) {
        if ((status !== 403 && status !== 200) || !raw.trimStart().startsWith('{')) return null;
        let error;
        try { error = JSON.parse(raw).error; } catch { return null; }
        if (!error || error.need_login === true || error.code === 40353 || error.name === 'need_login' || error.message === 'need_login') return null;
        if (typeof error.redirect === 'string') return verificationUrl(error.redirect);
        return error === 'unhuman' || error.code === 40352 || error.name === 'unhuman' || error.message === 'unhuman' ? `${WWW}/account/unhuman` : null;
    }
    function only(query, rules, required = []) {
        for (const [key, value] of query) {
            check(Object.hasOwn(rules, key));
            const rule = rules[key];
            check(rule instanceof RegExp ? rule.test(value) : rule.includes(value));
        }
        check(required.every(key => query.has(key)));
    }
    function parseQuery(raw) {
        check(typeof raw === 'string' && raw.length <= 4096 && !/[?#\\]/u.test(raw) && !controls.test(raw));
        const query = new URLSearchParams();
        if (!raw) return query;
        for (const part of raw.split('&')) {
            const equals = part.indexOf('=');
            check(equals > 0);
            const key = part.slice(0, equals);
            const value = decodeURIComponent(part.slice(equals + 1).replace(/\+/gu, ' '));
            check(/^[a-z_]+$/u.test(key) && !query.has(key) && !controls.test(value));
            query.append(key, value);
        }
        return query;
    }
    function destination(op, id, query) {
        const comment = /^(root|segments|segment)-(answer|article|question|pin)$/u.exec(op);
        if (comment) {
            check(typeof id === 'string' && numberId.test(id));
            const mode = comment[1];
            const type = comment[2];
            check(mode === 'root' || ['answer', 'article'].includes(type));
            const rules = {
                order_by: ['score', 'ts'],
                order: ['score'],
                limit: pageLimit,
                offset: cursor,
                cursor,
                include: commentInclude,
            };
            if (mode === 'segment') rules.segment_id = /^[A-Za-z0-9_-]{1,128}$/u;
            only(query, rules);
            if (mode === 'segment') check(query.has('segment_id') || query.has('cursor'));
            const suffix = mode === 'root' ? 'root_comment' : mode === 'segments' ? 'segment_comment' : 'segment/root_comment';
            return `${WWW}/api/v4/comment_v5/${type}s/${id}/${suffix}`;
        }
        if (['comment', 'childComments', 'pinV2', 'questionFeeds'].includes(op)) {
            check(typeof id === 'string' && numberId.test(id));
            if (op === 'pinV2') {
                only(query, {});
                return `https://api.zhihu.com/v2/pins/${id}`;
            }
            if (op === 'questionFeeds') {
                only(query, {limit: pageLimit, order: ['default', 'updated'], offset: pageNumber, cursor, include: [feedInclude]});
                return `${WWW}/api/v4/questions/${id}/feeds`;
            }
            only(query, op === 'comment' ? {} : {limit: pageLimit, offset: cursor, cursor, order_by: ['ts', 'score'], include: commentInclude});
            return `${WWW}/api/v4/comment_v5/comment/${id}${op === 'childComments' ? '/child_comment' : ''}`;
        }
        if (Object.hasOwn(includes, op)) {
            check(typeof id === 'string' && numberId.test(id));
            only(query, {include: [includes[op]]});
            return `${WWW}/api/v4/${op}s/${id}`;
        }
        check(id === '');
        switch (op) {
            case 'profile':
                only(query, {});
                return ME;
            case 'hotList':
                only(query, {limit: ['50'], mobile: ['true'], include: [feedInclude]}, ['limit', 'mobile']);
                return `${WWW}/api/v3/feed/topstory/hot-lists/total`;
            case 'recommend':
                only(query, {desktop: ['true'], limit: ['10', '15', '20'], offset: pageNumber, include: [feedInclude]}, ['desktop', 'limit']);
                return `${WWW}/api/v3/feed/topstory/recommend`;
            case 'homeFeed':
                only(query, {include: [feedInclude]});
                return 'https://api.zhihu.com/topstory/recommend';
            case 'history':
                only(query, {offset: pageNumber, limit: ['10', '50'], include: [feedInclude]}, ['offset', 'limit']);
                return HISTORY;
            case 'historySetting':
                only(query, {});
                return 'https://api.zhihu.com/read_history/total';
            case 'search':
                only(query, {
                    gk_version: ['gz-gaokao'], t: ['general', 'topic', 'people'], q: /^.{1,128}$/u,
                    correction: ['1'], offset: pageNumber, limit: ['20'], search_source: ['Normal', 'Filter'],
                    show_all_topics: ['0', '1'], include: ['data[*].highlight,object,type'],
                    vertical: ['answer', 'article', 'zvideo'], vertical_info: ['0,0,0,0,0,0,0,0,0,0,0,0'],
                    sort: ['created_time', 'upvoted_count'], time_interval: ['a_day', 'a_week', 'a_month', 'three_months', 'half_a_year', 'a_year'],
                }, ['q', 't']);
                return `${WWW}/api/v4/search_v3`;
            case 'forwardCount':
                only(query, {content_type: ['answer', 'article', 'pin'], content_token: numberId}, ['content_type', 'content_token']);
                return 'https://api.zhihu.com/unify-consumption/forward/count';
            case 'forwardList':
                only(query, {
                    content_type: ['answer', 'article', 'pin'], content_token: numberId,
                    cursor: /^[A-Za-z0-9_+/=-]{0,512}$/u, delivered_count: pageNumber,
                }, ['content_type', 'content_token']);
                return FORWARD;
            default: throw new Error('denied');
        }
    }

    function profileId(json) {
        check(json && typeof json === 'object' && !json.error);
        if (json.is_anonymous === true || /^0+$/u.test(String(json.id))) return null;
        check(typeof json.id === 'string' && /^[\w-]{1,128}$/u.test(json.id));
        return json.id;
    }
    function profileBody(json) {
        const id = profileId(json);
        if (id === null) return null;
        check(typeof json.name === 'string' && json.name.trim() && json.name.length <= 128 && !controls.test(json.name));
        const profile = {id, name: json.name};
        if (typeof json.url_token === 'string' && /^[\w-]{1,128}$/u.test(json.url_token)) profile.url_token = json.url_token;
        if (['people', 'organization'].includes(json.user_type)) profile.user_type = json.user_type;
        if (typeof json.avatar_url === 'string' && /^https:\/\/pic[1-4ax]\.zhimg\.com\/[\w./-]{1,512}$/u.test(json.avatar_url)) profile.avatar_url = json.avatar_url;
        return profile;
    }
    /** 仅资源标识可以使用 token 后缀；认证字段连错误响应也不得离开脚本。 */
    function safeResponseField(key, value) {
        const name = key.toLowerCase().replace(/[-_]/gu, '');
        const imageToken = key === 'token' && typeof value === 'string' && /^v2-[a-f0-9]{32}$/u.test(value) && this.type === 'image' &&
            typeof this.url === 'string' && /^https:\/\/pic[1-4ax][.]zhimg[.]com\//u.test(this.url) && new URL(this.url).pathname.includes(value);
        const resourceToken = ['urltoken', 'contenttoken', 'questiontoken'].includes(name) &&
            (value === null || typeof value === 'string' && /^[A-Za-z0-9_-]{0,128}$/u.test(value));
        check(!/cookie|authorization|password|secret|credential/u.test(name) &&
            !['accesskey', 'accessid', 'apikey', 'headers', 'requestheaders', 'responseheaders', 'session', 'sessionid', 'xsrf', 'csrf', 'zc0', 'dc0'].includes(name) &&
            (!name.endsWith('token') || resourceToken || imageToken));
        return value;
    }
    function publicBody(raw, op, id, query) {
        // 返回原文而非 JSON.stringify：不能让 JS Number 截断业务的 64 位 ID。
        const json = JSON.parse(raw, safeResponseField);
        check(json && typeof json === 'object' && !Array.isArray(json) && !json.error);
        if (json.paging) {
            const prefix = destination(op, id, query);
            for (const link of [json.paging.next, json.paging.previous, json.paging.prev]) {
                if (link === '' || link == null) continue;
                check(typeof link === 'string' && link.startsWith(prefix + '?'));
                const next = parseQuery(link.slice(prefix.length + 1));
                destination(op, id, next);
                for (const key of ['q', 't', 'segment_id', 'content_type', 'content_token']) {
                    if (next.has(key)) check(next.get(key) === query.get(key));
                }
            }
        }
        if (op === 'forwardCount') {
            check(json.data && typeof json.data.is_show_tab === 'boolean' &&
                Number.isSafeInteger(json.data.count) && json.data.count >= 0);
        }
        if (op === 'forwardList') check(Array.isArray(json.data) && json.paging && typeof json.paging.is_end === 'boolean');
        return raw;
    }

    function historyBody(raw, query) {
        const json = JSON.parse(publicBody(raw, 'history', '', query));
        check(Array.isArray(json.data) && json.paging && typeof json.paging.is_end === 'boolean');
        const text = value => typeof value === 'string' ? value : '';
        const image = value => typeof value === 'string' && /^https:\/\/pic[1-4ax]\.zhimg\.com\/[\w./-]{1,512}$/u.test(value) ? value : '';
        const action = value => {
            const url = text(value?.url);
            check(/^zhihu:\/\/(?:answers|articles|questions|pins|zvideo)\/[1-9][0-9]{0,18}$/u.test(url) ||
                /^https:\/\/www\.zhihu\.com\/(?:question\/[1-9][0-9]{0,18}(?:\/answer\/[1-9][0-9]{0,18})?|pin\/[1-9][0-9]{0,18}|zvideo\/[1-9][0-9]{0,18})$/u.test(url) ||
                /^https:\/\/zhuanlan\.zhihu\.com\/p\/[1-9][0-9]{0,18}$/u.test(url));
            return {type: text(value.type), url};
        };
        const data = json.data.map(item => {
            const d = item.data, extra = d?.extra;
            check(d && extra && typeof extra.content_token === 'string' && numberId.test(extra.content_token) &&
                ['answer', 'article', 'question', 'pin', 'zvideo'].includes(extra.content_type) &&
                Number.isSafeInteger(extra.read_time) && extra.read_time >= 0);
            return {
                card_type: text(item.card_type),
                data: {
                    header: {
                        icon: image(d.header?.icon),
                        title: text(d.header?.title),
                        badges: (d.header?.badges || []).map(image).filter(Boolean),
                    },
                    content: {
                        author_name: text(d.content?.author_name),
                        summary: text(d.content?.summary),
                        cover_image: image(d.content?.cover_image),
                    },
                    action: action(d.action),
                    extra: {
                        content_token: extra.content_token,
                        content_type: extra.content_type,
                        read_time: extra.read_time,
                        question_token: typeof extra.question_token === 'string' && numberId.test(extra.question_token)
                            ? extra.question_token : '',
                    },
                    matrix: (d.matrix || []).map(it => ({type: text(it.type), data: {text: text(it.data?.text)}})),
                },
            };
        });
        return JSON.stringify({
            data,
            paging: {
                is_end: json.paging.is_end,
                is_start: json.paging.is_start === true,
                next: json.paging.next || '',
            },
        });
    }

    const active = new Set();
    function createSession(port) {
        let received = false, timer;
        const entry = {settled: false, channel: null, mutation: false, submitted: false, finish, stop, send, receive,
            timeout(ms) { clearTimeout(timer); timer = setTimeout(() => stop('unavailable'), ms); }};
        function send(kind, status = 0, body = '') {
            try { port.postMessage(['http', 'unsupported'].includes(kind) ? {kind, status, body} : {kind}); }
            catch { stop('cancelled'); }
        }
        function finish(kind, status = 0, body = '') {
            if (entry.settled) return;
            const cancelled = kind === 'cancelled';
            if (!trustedPage()) kind = 'denied';
            if (kind !== 'http' && (entry.submitted || cancelled && entry.mutation)) kind = 'uncertain';
            entry.settled = true;
            entry.request = entry.handle = null;
            clearTimeout(timer);
            active.delete(entry);
            port.onmessage = null;
            try { entry.onFinish?.(kind); send(kind, status, body); }
            finally { port.close(); }
        }
        function stop(kind) {
            if (entry.settled) return;
            const handle = entry.handle;
            try { finish(kind); } finally { handle?.abort(); }
        }
        function receive(data, menuConsent = false) {
            if (entry.settled) return;
            if (data === 'cancel') return stop('cancelled');
            if (received) return stop('denied');
            received = true;
            try {
                exactObject(data, ['channel', 'op', 'owner', 'id', 'query', 'body', 'bytes']);
                check(trustedPage() && ['read', 'business'].includes(data.channel) && typeof data.op === 'string');
                const limit = data.channel === 'read' ? 12 : 8;
                if ([...active].filter(request => request.channel === data.channel).length >= limit) return finish('unavailable');
                entry.channel = data.channel;
                if (data.channel === 'read') {
                    check(data.body === '' && data.bytes === null);
                    entry.timeout(15000);
                    handleRead(data, entry, menuConsent);
                } else {
                    if (readIssue || cookieIssue) return finish('unsupported', 0, readIssue || cookieIssue);
                    handleBusiness(data, entry);
                }
            } catch { stop('denied'); }
        }
        if (active.size >= 20) { finish('unavailable'); return null; }
        active.add(entry);
        entry.timeout(5000);
        port.onmessage = ({data}) => receive(data);
        send('accepted');
        return entry;
    }
    function transport(entry, {url, method, headers = {}, body = '', cookie = '', anonymous = true}, receive, fail) {
        check(!entry.settled);
        const token = entry.request = Symbol();
        entry.handle = null;
        let completed = false;
        const current = () => !entry.settled && entry.request === token;
        const complete = () => {
            if (completed) return false;
            completed = true;
            if (entry.settled) { fail('cancelled'); return false; }
            if (entry.request !== token) return false;
            entry.request = entry.handle = null;
            return true;
        };
        const failure = kind => { if (complete()) fail(kind); };
        const handle = GM_xmlhttpRequest({
            url, method, headers, ...(body === '' ? {} : {data: body}), ...(cookie ? {cookie} : {}),
            anonymous, redirect: 'manual', nocache: true,
            onprogress: event => { if (current() && (event.loaded > MAX_BODY || event.total > MAX_BODY)) entry.stop('denied'); },
            onabort: () => failure('cancelled'),
            onerror: () => failure('unavailable'),
            ontimeout: () => failure('unavailable'),
            onload: response => {
                if (!complete()) return;
                try {
                    const {status, responseText: raw} = response;
                    check(response.finalUrl === url && Number.isInteger(status) && status >= 200 && status <= 599);
                    const redirect = status >= 300 && status < 400;
                    check(raw == null ? redirect : typeof raw === 'string' && raw.length <= MAX_BODY);
                    receive({status, body: raw, headers: response.responseHeaders || ''});
                } catch { fail('denied'); }
            },
        });
        // 同步回调可能已启动下一请求；旧调用返回的句柄不能覆盖其归属。
        if (current()) entry.handle = handle;
        else if (!completed) handle?.abort();
    }

    let profileAllowed = false, profileChecking = false;
    const gestures = new Map();
    let verifiedId = null, historyOwner = null;
    let challenge = null;
    function recordChallenge(url) {
        challenge = {url, owner: verifiedId, opened: false, checking: null};
        resetBrowserQr();
    }
    window.addEventListener('zhihu++/verification/open/1', event => {
        if (!trustedPage() || !userActivated() || !event.cancelable || !challenge || challenge.owner !== verifiedId) return;
        let popup;
        try {
            popup = window.open('about:blank', '_blank');
            if (!popup || popup.closed) return;
            popup.opener = null;
            if (popup.opener !== null) { popup.close(); return; }
            popup.location.replace(challenge.url);
            challenge.opened = true;
            event.preventDefault();
        } catch { popup?.close(); }
    });
    function revokeHistory(kind) {
        historyOwner = null;
        for (const request of active) if (request.history) request.stop(kind);
    }
    function rememberProfile(id) {
        if (verifiedId !== id) { challenge = null; revokeBusiness(); }
        verifiedId = id;
        if (historyOwner !== null && historyOwner !== id) revokeHistory('expired');
    }
    function revoke() {
        revokeBusiness();
        profileAllowed = false;
        verifiedId = null;
        challenge = null;
        gestures.clear();
        resetBrowserQr(true);
        revokeHistory('denied');
        for (const request of active) if (request.profile) request.stop('denied');
        notifyPermission();
    }
    const QR_NEW = `${WWW}/api/v3/account/api/login/qrcode`;
    const QR_SIGNIN = `${WWW}/signin`;
    const qrPart = /^[A-Za-z0-9_-]{1,256}$/u;
    const qrDisplay = /^https:\/\/www\.zhihu\.com\/account\/scan\/login\/([A-Za-z0-9_-]{1,256})$/u;
    const QR_TTL = 120000;
    const QR_UNVERIFIED = Symbol();
    let browserQr = null, qrProfileOwner = null, qrExpired = false, qrWriting = false;
    function browserQrProfileMatches(id) {
        return !qrWriting && browserQr?.state !== 'committing' && (qrProfileOwner === null || id === qrProfileOwner);
    }
    function resetBrowserQr(clearOwner = false) {
        if (browserQr) {
            clearTimeout(browserQr.timer);
            browserQr.token = browserQr.cookie = browserQr.user = browserQr.before = null;
        }
        browserQr = null;
        qrExpired = false;
        if (clearOwner) qrProfileOwner = null;
    }
    function expireBrowserQr(tx) {
        if (browserQr !== tx) return;
        resetBrowserQr();
        qrExpired = true;
    }
    function qrDeadline(tx, expires) {
        const now = Date.now();
        if (expires != null) {
            check(Number.isSafeInteger(expires) && expires >= 0);
            const end = expires <= 86400 ? now + expires * 1000 : expires <= 86400000 ? now + expires :
                expires < 10000000000 ? expires * 1000 : expires;
            tx.deadline = Math.min(tx.deadline, end);
        }
        clearTimeout(tx.timer);
        tx.timer = setTimeout(() => expireBrowserQr(tx), Math.max(0, tx.deadline - now));
    }
    function separateQrKeys(display, token) {
        if (typeof atob !== 'function' || typeof btoa !== 'function') return false;
        const forms = key => {
            const hex = Array.from(key, c => c.charCodeAt(0).toString(16).padStart(2, '0')).join('');
            const base64url = btoa(key).replaceAll('+', '-').replaceAll('/', '_').replace(/=+$/u, '');
            const values = [key, hex, base64url], compact = key.replace(/[-_]/gu, '');
            if (/^(?:[0-9a-f]{2})+$/iu.test(compact)) values.push(String.fromCharCode(...compact.match(/../gu).map(pair => parseInt(pair, 16))));
            try { values.push(atob(key.replaceAll('-', '+').replaceAll('_', '/'))); } catch { /* 不是 Base64 形态。 */ }
            return values.map(v => v.toLowerCase().replace(/[-_]/gu, ''));
        };
        const scan = forms(display), poll = forms(token);
        return !scan.some(a => poll.some(b => a.includes(b) || b.includes(a)));
    }
    function qrResponse(raw) {
        const data = JSON.parse(raw);
        check(data && typeof data === 'object' && !Array.isArray(data) && data.error == null);
        return data;
    }
    function issuedQrCookie(data) {
        const values = new Set();
        const add = value => {
            // RFC 6265 的 cookie-octet，可带一对外层引号；不接受属性、换行或多个不同票据。
            check(typeof value === 'string' && value.length > 0 && value.length <= 4096 &&
                /^(?:[\x21\x23-\x2b\x2d-\x3a\x3c-\x5b\x5d-\x7e]+|"[\x21\x23-\x2b\x2d-\x3a\x3c-\x5b\x5d-\x7e]+")$/u.test(value));
            values.add(value);
        };
        if (data.z_c0 != null) add(data.z_c0);
        for (const field of [data.cookie, data.cookies]) {
            if (field == null) continue;
            check(typeof field === 'string' && field.length <= 16384 && !controls.test(field));
            for (const part of field.split(';')) {
                const pair = part.trim();
                if (pair.startsWith('z_c0=')) add(pair.slice(5));
            }
        }
        check(values.size <= 1);
        return values.values().next().value ?? null;
    }

    function handleBrowserQr(op, {request, finish, live, entry, consent}) {
        const reply = data => finish('http', 200, JSON.stringify(data));
        let tx = browserQr;
        function expired() {
            if (op === 'loginQrPoll') reply({state: 'expired'}); else finish('expired');
        }
        function current() {
            if (tx && tx === browserQr && Date.now() >= tx.deadline) expireBrowserQr(tx);
            if (tx && tx === browserQr && live()) return true;
            if (!browserQr && qrExpired) expired(); else finish('denied');
            return false;
        }
        function bind() {
            tx.busy = true;
            entry.onQrFinish = kind => {
                if (tx !== browserQr) return;
                tx.busy = false;
                if (kind !== 'http') resetBrowserQr();
            };
        }
        // GM_cookie 的回调不经过共享请求的 onload；撤销、换码和超时后同样不可发布身份。
        const guarded = next => (...args) => {
            if (!current()) return;
            try { next(...args); } catch { finish('denied'); }
        };
        const readMe = next => request('GET', ME, guarded((status, raw) => {
            if (status === 401) {
                const failure = JSON.parse(raw)?.error;
                check(failure?.message === 'ERR_TICKET_NOT_EXIST' ||
                    (failure?.code === 100 && failure.message === 'ERR_LOGIN_TICKET_EXPIRED'));
                return next(null);
            }
            if (status !== 200) return finish('unavailable');
            next(profileBody(JSON.parse(raw))?.id ?? null);
        }), {anonymous: false});
        if (op === 'loginQrStart') {
            check(live() && !qrWriting);
            resetBrowserQr();
            if (!consent) return reply({state: 'cancelled'});
            check(live());
            tx = browserQr = {state: 'starting', deadline: Date.now() + QR_TTL, nextPoll: 0};
            bind();
            qrDeadline(tx, null);
            return readMe(before => {
                tx.before = before;
                request('POST', QR_NEW, guarded((status, raw) => {
                    if (status !== 200 && status !== 201) return finish('unavailable');
                    const data = qrResponse(raw), token = data.token ?? data.qrcode_token;
                    check(typeof token === 'string' && qrPart.test(token) && (data.qrcode_token == null || data.qrcode_token === token));
                    const display = typeof data.link === 'string' && data.link.length <= 512 && qrDisplay.exec(data.link);
                    if (!display || Object.keys(data).some(key => !['link', 'token', 'qrcode_token', 'expires_at'].includes(key)) ||
                        !separateQrKeys(display[1], token)) {
                        resetBrowserQr();
                        return reply({state: 'official'});
                    }
                    qrDeadline(tx, data.expires_at);
                    if (!current()) return;
                    tx.state = 'waiting';
                    tx.token = token;
                    reply({state: 'ready', link: data.link});
                }), {anonymous: true});
            });
        }
        if (!current()) return;
        if (op === 'loginQrPoll') {
            if (tx.state === 'confirmed') return reply({state: 'confirmed'});
            check(tx.state === 'waiting' || tx.state === 'scanned');
            if (tx.busy || Date.now() < tx.nextPoll) return reply({state: tx.state});
            bind();
            tx.nextPoll = Date.now() + 1000;
            return request('GET', `${QR_NEW}/${tx.token}/scan_info`, guarded((status, raw) => {
                if (status !== 200) return finish('unavailable');
                const data = qrResponse(raw), label = data.login_status?.toUpperCase();
                check(data.status == null || Number.isSafeInteger(data.status));
                if (data.status === 2 || ['EXPIRED', 'QR_CODE_EXPIRED', 'LOGIN_EXPIRED'].includes(label)) {
                    expireBrowserQr(tx);
                    return expired();
                }
                // status=1 只表示已扫描；即使响应附有 cookie 或身份字段也不能提交。
                if (data.status === 0 || data.status === 1) {
                    if (data.status === 1) tx.state = 'scanned';
                    return reply({state: tx.state});
                }
                check(data.success !== false && data.logged_in !== false &&
                    (label == null || ['CONFIRMED', 'LOGIN_SUCCESS', 'SUCCESS', 'OK', 'LOGGED_IN'].includes(label)) &&
                    (label != null || data.success === true || data.logged_in === true || data.user_id != null ||
                        (typeof data.access_token === 'string' && data.access_token.length > 0)));
                if (data.user_id != null) check(typeof data.user_id === 'string' && /^[\w-]{1,128}$/u.test(data.user_id) && !/^0+$/u.test(data.user_id));
                tx.cookie = issuedQrCookie(data);
                tx.user = data.user_id ?? null;
                check(tx.cookie !== null || tx.user !== null);
                tx.token = null;
                tx.state = 'confirmed';
                reply({state: 'confirmed'});
            }), {anonymous: true});
        }
        check(op === 'cookieSignIn' && !tx.busy && tx.state === 'confirmed');
        bind();
        if (!consent) {
            resetBrowserQr();
            return reply({authorized: false});
        }
        if (!current()) return;
        tx.state = 'committing';
        function publish(id) {
            check(id !== null && (tx.user === null || tx.user === id));
            resetBrowserQr();
            qrProfileOwner = id;
            rememberProfile(id);
            profileAllowed = true;
            notifyPermission();
            reply({authorized: true});
        }
        return readMe(before => {
            check(tx.before === null || tx.before === before);
            if (before !== null) {
                check(tx.user === before);
                return readMe(after => { check(after === before); publish(after); });
            }
            if (!tx.cookie) return finish('unavailable');
            if (cookieIssue) return finish('unsupported', 0, cookieIssue);
            const list = name => new Promise((resolve, reject) => GM_cookie.list({url: QR_SIGNIN, name}, (records, error) => {
                if (error != null || !Array.isArray(records)) reject(); else resolve(records);
            }));
            const done = invoke => new Promise((resolve, reject) => invoke(error => { if (error != null) reject(); else resolve(); }));
            const matches = (records, name, value, path) => {
                const c = records[0];
                return records.length === 1 && c?.name === name && c.value === value && c.path === path &&
                    c.domain === 'www.zhihu.com' && c.hostOnly === true && c.secure === true && c.httpOnly === true;
            };
            // Web Locks 仅协调同 origin 标签页；本地、Pages 与官网互不共享，临写前检查也不能原子阻止异源竞争。
            navigator.locks.request('zhihu++/qr-cookie/1', {ifAvailable: true}, async lock => {
                if (!current()) return;
                if (!lock) return finish('unavailable');
                if ((await list('z_c0')).length) return finish('denied');
                const probe = `zhpp_qr_probe_${crypto.randomUUID()}`;
                if (!current() || (await list(probe)).length) return finish('denied');
                if (!current()) return;
                const attrs = {url: QR_SIGNIN, secure: true, httpOnly: true};
                const expirationDate = Math.floor(Date.now() / 1000) + 60;
                let capable = false;
                try {
                    await done(cb => GM_cookie.set({...attrs, name: probe, value: '1', path: '/signin', expirationDate}, cb));
                    if (!current()) return;
                    const records = await list(probe);
                    capable = matches(records, probe, '1', '/signin') && records[0].expirationDate === expirationDate;
                } finally {
                    // 仅清理随机、无凭据的自有探针；GM_cookie.delete 无法按值原子删除真实票据。
                    await done(cb => GM_cookie.delete({url: QR_SIGNIN, name: probe}, cb));
                }
                if (!current()) return;
                if (!capable || (await list(probe)).length) return finish('unsupported', 0, 'cookie-attributes-unsupported');
                if (!current()) return;
                if ((await list('z_c0')).length) return finish('denied');
                if (!current()) return;
                // 原有的广义资料授权不能抢在写入后的身份校验之前发布候选账号。
                qrProfileOwner = QR_UNVERIFIED;
                qrWriting = true;
                try { await done(cb => GM_cookie.set({...attrs, name: 'z_c0', value: tx.cookie, path: '/'}, cb)); }
                finally { qrWriting = false; }
                if (!current()) return;
                if (!matches(await list('z_c0'), 'z_c0', tx.cookie, '/')) return finish('unavailable');
                if (!current()) return;
                readMe(after => {
                    check(after !== null && (tx.user === null || tx.user === after));
                    list('z_c0').then(guarded(records => {
                        if (!matches(records, 'z_c0', tx.cookie, '/')) return finish('unavailable');
                        publish(after);
                    })).catch(() => finish('unavailable'));
                });
            }).catch(() => finish('unavailable'));
        });
    }

    // 业务能力必须显式授予，不能从既有 profile/history 读取许可推导。
    let businessOwner = null, businessEpoch = 0;
    const businessScopes = new Set();
    const answerDraftSettings = new Map();
    function revokeBusiness() {
        businessOwner = null;
        businessEpoch++;
        businessScopes.clear();
        answerDraftSettings.clear();
        for (const [op, action] of Object.entries(actions)) if (action.scope) gestures.delete(op);
        for (const request of active) if (request.businessGrant || request.channel === 'business') request.stop('denied');
        notifyPermission();
    }
    const actions = {
        authorizeProfile: {
            menu: ['Allow Zhihu++ to read my basic profile in this tab', '允许本标签页的 Zhihu++ 读取我的基本资料'],
            prompt: () => chooseText(`Allow Zhihu++ at ${site} to verify your current Zhihu profile in this tab?`,
                `允许 ${site} 上的知乎++在此标签页核对当前知乎账号的基本资料？`),
        },
        revokeProfile: {menu: ['Revoke profile access in this tab', '撤销本标签页的账号资料授权'], run: revoke},
        authorizeHistory: {
            menu: ['Allow this tab to read the verified account’s history (refresh the account first)', '允许本标签页读取已验证账号的浏览历史（请先刷新账号）'],
            run() { if (profileAllowed && verifiedId !== null) historyOwner = verifiedId; },
        },
        revokeHistory: {menu: ['Revoke history access in this tab', '撤销本标签页的浏览历史授权'], run: () => revokeHistory('denied')},
        revokeBusiness: {menu: ['Revoke all business permissions in this tab', '撤销本标签页的所有业务权限'], run: revokeBusiness},
        authorizePrivate: {scope: 'private', description: ['read personal lists, follows, collections, drafts, stickers, notifications and private messages', '读取个人列表、关注、收藏、草稿、表情、通知与私信']},
        authorizeInteractions: {scope: 'interact', description: ['vote, collect, follow, block, answer polls, send or delete comments, send private messages and mark notifications read', '点赞、收藏、关注、屏蔽、投票、发送或删除评论、发送私信、标记通知已读']},
        authorizeCreation: {scope: 'create', description: ['create, edit, save and publish answers, articles, questions and pins, and upload images and voice comments', '创建、编辑、保存及发布回答、文章、提问、想法，以及上传图片和语音']},
        authorizeHistoryWrite: {scope: 'history', description: ['add, delete or clear reading history and change history recording', '添加、删除、清空浏览历史或改变历史记录开关']},
        loginQrStart: {
            prompt: () => chooseText(`Allow Zhihu++ at ${site} to request a Zhihu sign-in QR code and check whether this browser is already signed in?`,
                `允许 ${site} 上的 Zhihu++ 向知乎获取登录二维码，并检查浏览器是否已登录知乎？`),
        },
        cookieSignIn: {
            prompt: () => chooseText(
                `Allow Zhihu++ at ${site} to use the phone-confirmed account in this tab and read its basic profile? If signed out, this will sign the browser in to Zhihu. A different account stops sign-in. Do not sign in simultaneously from local Zhihu++, Pages or Zhihu itself; concurrent sign-ins could be replaced.`,
                `允许 ${site} 上的 Zhihu++ 在本标签页使用手机确认的账号，并读取其基本资料？若浏览器未登录，将同时登录知乎官网。检测到其他账号将停止；请勿同时在本地版、Pages 版或官网登录或切换账号，以免覆盖同时发生的登录。`),
        },
        verificationCheck: {},
    };
    function consentFor(op) {
        const action = actions[op];
        if (readIssue) return false;
        if (action.scope) {
            if (!profileAllowed || verifiedId === null || cookieIssue) return false;
            return window.confirm(chooseText(
                `Allow ${site} to ${action.description[0]} as the verified account in this tab? This grant lasts until revoked, the account changes or the page closes. Writes may be irreversible; no per-action permission prompt follows.`,
                `允许 ${site} 在本标签页以当前已验证的知乎账号${action.description[1]}？此授权持续到撤销、换账号或关闭页面。写入可能不可撤销；不会逐次弹出授权。`));
        }
        return op === 'verificationCheck' || (op === 'authorizeProfile' && profileAllowed && browserQrProfileMatches(null)) || window.confirm(action.prompt());
    }
    function authorizeFromMenu(op) {
        if (!trustedPage() || (actions[op].scope && !consentFor(op)) || !trustedPage()) return;
        // 管理器菜单是用户操作；授权仍经过共享会话上限和 /me 核验。
        createSession({
            postMessage(result) {
                if (result.kind === 'accepted') return;
                const success = result.kind === 'http' && result.body === '{"authorized":true}';
                if (actions[op].scope) {
                    window.alert(success ? chooseText('Business permissions granted.', '业务权限已授权。') :
                        chooseText('Permission was not granted. Verify the account and retry.', '授权未完成，请核对账号后重试。'));
                    return;
                }
                const detail = result.kind === 'unsupported' ? ` (${result.body})` : '';
                window.alert(success ? chooseText(
                    'Profile verified. Return to Zhihu++ to use this browser session.',
                    '账号资料核验成功。返回知乎++即可使用此浏览器会话。',
                ) : chooseText(
                    `Profile verification did not complete${detail}. Return to Zhihu++ and retry; open the official sign-in page if signed out.`,
                    `账号资料未完成核验${detail}。请返回知乎++重试；若未登录，请先打开知乎官方登录页。`,
                ));
            },
            close() {},
        })?.receive({channel: 'read', op, owner: '', id: '', query: '', body: '', bytes: null}, true);
    }
    for (const [op, action] of Object.entries(actions)) {
        if (action.run) continue;
        // 在真正的点击栈内确认；结果只存在闭包，且只能消费一次。
        window.addEventListener(`zhihu++/gesture/${op}/1`, event => {
            if (!trustedPage() || !event.cancelable || !userActivated()) return;
            const granted = consentFor(op);
            if (!trustedPage()) return;
            gestures.set(op, {granted, deadline: Date.now() + 5000});
            event.preventDefault();
        });
    }
    let menuLanguage;
    function updateMenuLabels() {
        const lang = document.documentElement?.lang || (navigator.languages || [navigator.language]).find(tag => /^(zh|en)(?:-|$)/i.test(tag));
        const chinese = /^zh(?:-|$)/i.test(lang);
        if (chinese === menuLanguage) return;
        menuLanguage = chinese;
        if (typeof GM_registerMenuCommand !== 'function') return;
        for (const [op, action] of Object.entries(actions)) {
            const label = action.menu ? chooseText(...action.menu) : action.scope ? chooseText('Allow ' + action.description[0], '授权' + action.description[1]) : null;
            if (label) action.menuId = GM_registerMenuCommand(`${label} — ${site}`, () => {
                if (!trustedPage()) return;
                if (action.run) action.run(); else authorizeFromMenu(op);
            }, {id: action.menuId});
        }
    }
    function observeMenuLanguage() {
        updateMenuLabels();
        new MutationObserver(updateMenuLabels).observe(document.documentElement, {attributeFilter: ['lang']});
    }
    if (document.documentElement) observeMenuLanguage();
    else {
        updateMenuLabels();
        document.addEventListener('DOMContentLoaded', observeMenuLanguage, {once: true});
    }
    window.addEventListener('languagechange', updateMenuLabels);
    window.addEventListener('pagehide', () => {
        profileAllowed = false;
        gestures.clear();
        verifiedId = historyOwner = null;
        challenge = null;
        resetBrowserQr(true);
        for (const request of active) request.stop('cancelled');
        revokeBusiness();
    });
    window.addEventListener('message', event => {
        if (!trustedPage() || !event.isTrusted || event.source !== window || event.origin !== location.origin ||
            event.data?.type !== TAG || Array.isArray(event.data) || !Object.hasOwn(event.data, 'type') ||
            Object.keys(event.data).length !== 1 || event.ports.length !== 1) return;
        createSession(event.ports[0]);
    });

    const API = 'https://api.zhihu.com';
    const LENS = 'https://lens.zhihu.com';
    const textBudget = 1024 * 1024, binaryBudget = 32 * 1024 * 1024;
    const businessText = value => typeof value === 'string' && value.length <= textBudget && !/[\u0000]/u.test(value);
    const keyId = value => typeof value === 'string' && /^[A-Za-z0-9_-]{1,128}$/u.test(value);
    const exactObject = (value, keys, required = keys) => {
        check(value !== null && typeof value === 'object' && !Array.isArray(value));
        check(Object.keys(value).every(key => keys.includes(key)) && required.every(key => Object.hasOwn(value, key)));
    };
    const jsonBody = (raw, keys, required = keys) => {
        const value = JSON.parse(raw);
        exactObject(value, keys, required);
        const visit = (entry, depth = 0) => {
            check(depth <= 24);
            if (entry === null || typeof entry !== 'object') return;
            for (const [key, child] of Object.entries(entry)) {
                check(!['__proto__', 'prototype', 'constructor'].includes(key));
                visit(child, depth + 1);
            }
        };
        visit(value);
        return value;
    };
    const emptyBody = raw => check(raw === '');
    const formBody = (raw, keys, required = keys) => {
        const value = new URLSearchParams(raw);
        check([...value.keys()].length === new Set(value.keys()).size);
        check([...value.keys()].every(key => keys.includes(key)) && required.every(key => value.has(key)));
        return value;
    };
    const businessIncludes = [feedInclude, 'data[*].content,excerpt,headline',
        'allow_message,is_followed,is_following,is_org,is_blocking,badge_v2,answer_count,follower_count,following_count,articles_count,question_count,pins_count',
        'name,excerpt,avatar_url,followers_count,questions_count,is_following,topic_id,total_pv,discuss_count',
        'data[*].is_normal,admin_closed_comment,reward_info,is_collapsed,annotation_action,annotation_detail,collapse_reason,collapsed_by,suggest_edit,comment_count,thanks_count,can_comment,content,editable_content,attachment,voteup_count,reshipment_settings,comment_permission,created_time,updated_time,review_info,excerpt,paid_info,reaction_instruction,is_labeled,label_info,relationship.is_authorized,voting,is_author,is_thanked,is_nothelp,author.badge_v2',
        'data[*].comment_count,suggest_edit,is_normal,thumbnail_extra_info,thumbnail,can_comment,comment_permission,admin_closed_comment,content,voteup_count,created,updated,upvoted_followees,voting,review_info,reaction_instruction,is_labeled,label_info,author.badge_v2;data[*].vessay_info;data[*].author.badge[?(type=best_answerer)].topics;',
        'data[*].answer_count,articles_count,gender,follower_count,is_followed,is_following,badge_v2,badge[?(type=best_answerer)].topics',
        'data[*].updated_time,answer_count,follower_count,creator', 'data[*].created,answer_count,follower_count,author,visit_count,comment_count,detail,relationship,topics,voteup_count',
        'data[*].like_count,comment_count,created,updated,content', 'data[*].column.articles_count', 'data[*].articles_count,followers,author'];
    const listQuery = {limit: /^(?:[1-9][0-9]?|100)$/u, offset: cursor, cursor, after_id: cursor, before_id: cursor, include: businessIncludes};
    /** 每个具名操作固定网络主机、路径和方法；通配符只匹配已知资源 ID。 */
    function businessDestination(op, id, query, raw) {
        const parts = id.split('/');
        let method = 'GET', path, host = WWW, scope = 'private', body = raw, contentType = 'application/json', profile = 'web';
        const noQuery = () => only(query, {});
        const mutation = (verb = 'POST', permission = 'interact') => { method = verb; scope = permission; noQuery(); };
        const resource = value => check(numberId.test(value));
        const person = value => check(keyId(value));
        const verb = op.substring(op.lastIndexOf('-') + 1);
        switch (op) {
            case 'existingRead': {
                check(parts.length === 2 && !['profile', 'history', 'historySetting'].includes(parts[0])); emptyBody(raw);
                const url = new URL(destination(parts[0], parts[1], query)); url.search = query.toString();
                return {url: url.href, method, scope, body, contentType, profile: parts[0] === 'pinV2' ? 'mobile' : profile};
            }
            case 'vote': {
                mutation(); check(parts.length === 2 && ['answers', 'articles'].includes(parts[0])); resource(parts[1]);
                const value = jsonBody(raw, parts[0] === 'answers' ? ['type'] : ['voting']);
                check(parts[0] === 'answers' ? ['up', 'down', 'neutral'].includes(value.type) : [0, 1].includes(value.voting));
                path = `/api/v4/${parts[0]}/${parts[1]}/voters`; break;
            }
            case 'pinVote-POST': case 'pinVote-DELETE':
                mutation(verb); resource(id); emptyBody(raw); path = `/api/v4/pins/${id}/voters/up`; break;
            case 'follow-POST': case 'follow-DELETE':
                mutation(verb); check(parts.length === 2 && ['members', 'questions', 'topics'].includes(parts[0])); person(parts[1]);
                if (parts[0] !== 'members') resource(parts[1]);
                emptyBody(raw); path = `/api/v4/${parts[0]}/${parts[1]}/followers`; break;
            case 'block-POST': case 'block-DELETE':
                mutation(verb); person(id); emptyBody(raw); path = `/api/v4/members/${id}/actions/block`; break;
            case 'commentLike-POST': case 'commentLike-DELETE':
                mutation(verb); resource(id); emptyBody(raw); path = `/api/v4/comments/${id}/like`; break;
            case 'notificationLike-POST': case 'notificationLike-DELETE':
                mutation(verb); resource(id); emptyBody(raw); host = API; profile = 'mobile'; path = `/reaction/comments/${id}/like`; break;
            case 'commentSend': case 'voiceCommentSend': {
                mutation(); check(['answers', 'articles', 'questions', 'pins'].includes(parts[0])); resource(parts[1]);
                check(parts.slice(2).join('/') === 'comment' || (['answers', 'articles'].includes(parts[0]) && parts.slice(2).join('/') === 'segment/comment'));
                const value = jsonBody(raw, ['comment_id', 'content', 'extra_params', 'has_img', 'reply_comment_id', 'score', 'selected_settings', 'unfriendly_check', 'sticker_type', 'voice_info', 'segment'],
                    ['comment_id', 'content', 'extra_params', 'has_img', 'reply_comment_id', 'score', 'sticker_type', 'voice_info', 'segment']);
                check(value.comment_id === '' && businessText(value.content) && value.extra_params === '' && typeof value.has_img === 'boolean' &&
                    (value.reply_comment_id === '' || numberId.test(value.reply_comment_id)) && value.score === 0);
                if (value.selected_settings !== undefined) check(Array.isArray(value.selected_settings) && value.selected_settings.length <= 1 && value.selected_settings.every(x => x === 'publish_sync_pin'));
                if (value.unfriendly_check !== undefined) check(value.unfriendly_check === 'strict');
                check(value.sticker_type === null || (Array.isArray(value.sticker_type) && value.sticker_type.length === 1 && ['vip', 'normal'].includes(value.sticker_type[0])));
                if (value.voice_info !== null) { exactObject(value.voice_info, ['voice_id']); person(value.voice_info.voice_id); }
                if (value.segment !== null) {
                    exactObject(value.segment, ['segment_id', 'content', 'position'], ['segment_id']);
                    check(typeof value.segment.segment_id === 'string' && /^[A-Za-z0-9_-]{0,128}$/u.test(value.segment.segment_id));
                    if (value.segment.segment_id === '') {
                        check(businessText(value.segment.content)); exactObject(value.segment.position, ['start', 'end']);
                        for (const at of [value.segment.position.start, value.segment.position.end]) {
                            exactObject(at, ['offset', 'paragraph_id']); check(Number.isSafeInteger(at.offset) && at.offset >= 0 && typeof at.paragraph_id === 'string' && at.paragraph_id.length <= 256);
                        }
                    }
                }
                if (op === 'voiceCommentSend') { host = API; profile = 'voice'; check(value.voice_info !== null); }
                path = `${host === WWW ? '/api/v4' : ''}/comment_v5/${id}`; break;
            }
            case 'commentDelete': mutation('DELETE'); resource(id); emptyBody(raw); path = `/api/v4/comment_v5/comment/${id}`; break;
            case 'collectionCreate': {
                mutation(); check(id === ''); const value = jsonBody(raw, ['title', 'description', 'is_public']);
                check(businessText(value.title) && businessText(value.description) && typeof value.is_public === 'boolean');
                path = '/api/v4/collections'; break;
            }
            case 'collectionDelete': mutation('DELETE'); resource(id); emptyBody(raw); path = `/api/v4/collections/${id}`; break;
            case 'contentCollections-GET': case 'contentCollections-PUT': {
                check(parts.length === 2 && ['answer', 'article'].includes(parts[0])); resource(parts[1]); host = API; path = `/collections/contents/${id}`;
                if (verb === 'PUT') { mutation('PUT'); contentType = 'application/x-www-form-urlencoded';
                    const value = formBody(raw, ['add_collections', 'remove_collections'], []);
                    check([...value.keys()].length === 1); resource([...value.values()][0]);
                } else { only(query, listQuery); emptyBody(raw); } break;
            }
            case 'pollVote': mutation(); resource(id); path = `/api/v4/polls/${id}`;
                { const value = jsonBody(raw, ['options']); check(Array.isArray(value.options) && value.options.length === 1); person(String(value.options[0])); } break;
            case 'emojiWrite': {
                mutation(); host = API; check(['collect', 'user/remove', 'upload'].includes(id)); path = `/emoji/${id}`;
                const value = jsonBody(raw, id === 'upload' ? ['image_tokens'] : ['emoji_id', 'source']);
                const ids = value.image_tokens ?? value.emoji_id; check(Array.isArray(ids) && ids.length === 1 && ids.every(keyId));
                if (id !== 'upload') check(value.source === 'comment'); break;
            }
            case 'emojiList': host = API; check(['user', 'library'].includes(id)); only(query, listQuery); emptyBody(raw); path = `/emoji/${id}/list`; break;
            case 'stickerGroups': check(id === ''); noQuery(); emptyBody(raw); path = '/api/v4/me/sticker-groups'; break;
            case 'stickerGroup': person(id); noQuery(); emptyBody(raw); path = `/api/v4/sticker-groups/${id}`; break;
            case 'mentions': check(id === ''); host = API; emptyBody(raw); only(query, {...listQuery, scene: ['comment_editor'], q: /^.{0,256}$/u}); path = '/people/ats'; break;
            case 'parseLinks': check(id === ''); host = API; emptyBody(raw); only(query, {url: /^.{1,4096}$/u, scene: ['editor']}, ['url', 'scene']); path = '/content/publish/parse_url'; break;
            case 'answerDraft-GET': case 'answerDraft-POST': {
                resource(id); path = `/api/v4/questions/${id}/draft`; noQuery();
                if (verb === 'GET') emptyBody(raw); else { mutation('POST', 'create');
                    const value = jsonBody(raw, ['content', 'draft_type', 'delta_time', 'settings']);
                    check(businessText(value.content) && value.draft_type === 'normal' && value.delta_time === 30);
                    validateDraftSettings(value.settings, answerDraftSettings.get(id));
                } break;
            }
            case 'publish': case 'pinDraft': {
                mutation('POST', 'create'); check(id === ''); const value = jsonBody(raw, ['action', 'data']);
                check(['answer', 'pin'].includes(value.action) && (op !== 'pinDraft' || value.action === 'pin'));
                validatePublish(value);
                if (op === 'pinDraft') { host = API; path = '/content/drafts'; } else path = '/api/v4/content/publish'; break;
            }
            case 'pinTopicSuggest': {
                method = 'POST'; scope = 'create'; host = API; check(id === '');
                only(query, {recommend_type: ['pin'], key_word: /^.{1,256}$/u}, ['recommend_type', 'key_word']);
                const value = jsonBody(raw, ['title', 'content']); check(businessText(value.title) && businessText(value.content)); path = '/content/publish/topics/recommend'; break;
            }
            case 'questionCreate': {
                mutation('POST', 'create'); host = API; check(id === ''); contentType = 'application/x-www-form-urlencoded';
                const value = formBody(raw, ['title', 'detail', 'is_anonymous']); check(value.get('is_anonymous') === 'false'); path = '/questions'; break;
            }
            case 'articleCreate': case 'articleDraft-POST': case 'articleDraft-PUT': {
                mutation(op.endsWith('-PUT') ? 'PUT' : 'POST', 'create'); host = API; profile = 'editor'; contentType = 'application/x-www-form-urlencoded';
                if (op === 'articleCreate') { check(id === ''); path = '/articles/write'; } else { resource(id); path = `/articles/${id}/draft`; }
                const fields = method === 'PUT' ? ['title', 'content', 'copyright_permission'] : op === 'articleCreate' ? ['title', 'content', 'copyright'] : ['title', 'content', 'copyright', 'delta_time', 'topic_ids'];
                const value = formBody(raw, fields);
                if (method === 'PUT') check(value.get('copyright_permission') === 'need_review');
                else { check(value.get('copyright') === (op === 'articleCreate' ? 'public' : 'need_review'));
                    if (op !== 'articleCreate') check(value.get('delta_time') === '30000' && value.get('topic_ids') === ''); }
                break;
            }
            case 'questionRelationship': host = API; resource(id); emptyBody(raw); only(query, {include: ['relationship,relationship.my_answer']}); path = `/questions/${id}`; break;
            case 'collectionRead': resource(parts[0]); check(parts.length === 2 && ['', 'items'].includes(parts[1])); only(query, listQuery); emptyBody(raw); path = `/api/v4/collections/${parts[0]}${parts[1] ? '/items' : ''}`; break;
            case 'peopleCollections': person(id); only(query, listQuery); emptyBody(raw); path = `/api/v4/people/${id}/collections`; break;
            case 'memberList':
                person(parts[0]); check(parts.length === 2 && ['answers', 'articles', 'followees', 'favlists', 'questions', 'column-contributions', 'following-favlists', 'following-questions', 'following-topic-contributions', 'following-columns'].includes(parts[1]));
                only(query, {...listQuery, sort_by: ['created', 'voteups', 'updated'], order_by: ['created', 'voteups', 'updated']}); emptyBody(raw); path = `/api/v4/members/${id}`; break;
            case 'memberPins': person(id); only(query, listQuery); emptyBody(raw); path = `/api/v4/v2/pins/${id}/moments`; break;
            case 'moments': check(id === '' || keyId(id)); only(query, {...listQuery, desktop: ['true']}); emptyBody(raw); path = id ? `/api/v3/moments/${id}/activities` : '/api/v3/moments'; break;
            case 'voters': check(parts.length === 2 && ['answers', 'pins'].includes(parts[0])); resource(parts[1]); only(query, listQuery); emptyBody(raw); path = `/api/v4/${id}/upvoters`; break;
            case 'answerRelationship': resource(id); only(query, {desktop: ['true']}); emptyBody(raw); path = `/api/v4/answers/${id}/relationship`; break;
            case 'personRead': person(parts[0]); check(['', 'followers', 'profile/detail'].includes(parts.slice(1).join('/'))); host = API; only(query, listQuery); emptyBody(raw); path = `/people/${id.replace(/\/$/u, '')}`; break;
            case 'mobileMoments': check(id === ''); host = API; profile = 'mobile'; only(query, {...listQuery, feed_type: ['recommend', 'pin']}); emptyBody(raw); path = '/moments_v3'; break;
            case 'recentFollow': check(id === ''); host = API; only(query, {type: ['raw']}); emptyBody(raw); path = '/moments/recent'; break;
            case 'recentFollowRead': check(id === ''); host = API; method = 'POST'; scope = 'interact'; only(query, {brief: /^.{1,4096}$/u}, ['brief']); emptyBody(raw); path = '/moments/recent/read'; break;
            case 'notificationMessages': check(id === ''); host = API; profile = 'mobile'; only(query, listQuery); emptyBody(raw); path = '/notifications/v3/message/v3'; break;
            case 'notificationTimeline': case 'notificationReadAll':
                check(['like', 'comment', 'invite', 'favlist_me', 'follow'].includes(id)); host = API; profile = 'mobile'; emptyBody(raw);
                path = `/notifications/v3/timeline/entry/${id}`;
                if (op === 'notificationReadAll') { mutation(); path += '/actions/readall'; }
                else only(query, {...listQuery, invite_with_time_slice: ['1']}); break;
            case 'messageSend': {
                mutation(); check(id === ''); host = API; profile = 'message'; contentType = 'application/x-www-form-urlencoded';
                const value = jsonBody(raw, ['receiver_id', 'content']); person(value.receiver_id); check(businessText(value.content) && value.content.trim().length > 0);
                body = encryptMessageBody(new URLSearchParams({receiver_id: value.receiver_id, content: value.content, content_type: '0', source_type: 'message_list'}).toString());
                path = '/messages'; break;
            }
            case 'messagesRead': check(id === '' || keyId(id)); host = API; profile = 'mobile'; only(query, {...listQuery, sender_id: /^[A-Za-z0-9_-]{1,128}$/u}); emptyBody(raw); path = id ? `/messages/user/${id}` : '/messages'; break;
            case 'answerSuggestions': check(['recommended', 'hot'].includes(id)); host = API; profile = 'mobile'; only(query, listQuery); emptyBody(raw); path = `/creators/question_route/notification_related/${id}`; break;
            case 'historyWrite': {
                mutation('POST', 'history'); host = API; check(['set_switch', 'batch_del'].includes(id)); path = `/read_history/${id}`;
                const value = jsonBody(raw, id === 'set_switch' ? ['enabled'] : ['pairs', 'clear']);
                if (id === 'set_switch') check(typeof value.enabled === 'boolean');
                else { check(typeof value.clear === 'boolean' && Array.isArray(value.pairs) && value.pairs.length <= 100 && (!value.clear || value.pairs.length === 0));
                    for (const pair of value.pairs) { exactObject(pair, ['content_token', 'content_type']); person(pair.content_token); check(['answer', 'article', 'pin', 'question', 'zvideo'].includes(pair.content_type)); } }
                break;
            }
            case 'historyAdd': {
                mutation('POST', 'history'); check(id === ''); const value = jsonBody(raw, ['content_token', 'content_type']);
                person(value.content_token); check(['answer', 'article', 'pin', 'question', 'zvideo', 'profile'].includes(value.content_type)); path = '/api/v4/read_history/add'; break;
            }
            case 'contentDetail': {
                check(parts.length === 2 && ['answers', 'articles', 'questions', 'pins'].includes(parts[0])); resource(parts[1]);
                only(query, {include: [includes[parts[0].slice(0, -1)]]}); emptyBody(raw); path = `/api/v4/${id}`; break;
            }
            case 'topstoryRead': check(id === ''); only(query, {desktop: ['true'], action_feed: ['True'], limit: ['10', '15', '20'], offset: pageNumber, include: [feedInclude]}, ['limit']);
                check(query.has('desktop') !== query.has('action_feed')); emptyBody(raw); path = '/api/v3/feed/topstory/recommend'; break;
            case 'questionFeedsRead': resource(id); only(query, {limit: pageLimit, order: ['default', 'updated'], offset: pageNumber, cursor, include: [feedInclude]}); emptyBody(raw); path = `/api/v4/questions/${id}/feeds`; break;
            case 'topicRead': resource(parts[0]); check(parts.length >= 2 && ['', 'top_activity/v2', 'essence/v2', 'timeline_activity/v2', 'top_question/v2', 'pin-hot', 'pin-new'].includes(parts.slice(1).join('/')));
                only(query, listQuery); emptyBody(raw); path = `/api/v5.1/topics/${parts[0]}${parts[1] ? '/feeds/' + parts.slice(1).join('/') : ''}`; break;
            case 'searchAssist': check(['hot_search', 'suggest'].includes(id)); only(query, {q: /^.{0,256}$/u}); emptyBody(raw); path = `/api/v4/search/${id}`; break;
            default: throw new Error('denied');
        }
        const url = new URL(path, host); url.search = query.toString();
        return {url: url.href, method, scope, body, contentType, profile};
    }
    function validateDraftSettings(value, saved) {
        const keys = ['reshipment_settings', 'comment_permission', 'can_reward', 'tagline', 'disclaimer_status', 'disclaimer_type',
            'commercial_report_info', 'push_activity', 'table_of_contents_enabled', 'thank_inviter_status', 'thank_inviter'];
        exactObject(value, [...keys, ...Object.keys(saved || {})], []);
        for (const [key, entry] of Object.entries(value)) {
            // 共享编辑器原样保留服务端扩展设置；只接受本账号此前读取的相同值，不放行页面自造扩展。
            if (key !== 'table_of_contents_enabled' && saved && Object.hasOwn(saved, key) && JSON.stringify(saved[key]) === JSON.stringify(entry)) continue;
            check(keys.includes(key));
            if (['can_reward', 'push_activity', 'table_of_contents_enabled'].includes(key)) check(typeof entry === 'boolean');
            else if (key === 'commercial_report_info') { exactObject(entry, ['is_report']); check(typeof entry.is_report === 'boolean'); }
            else check(typeof entry === 'string' && entry.length <= 1024);
        }
        if (saved) check(Object.keys(saved).every(key => Object.hasOwn(value, key)));
    }
    function validatePublish(value) {
        const fields = value.action === 'pin' ? ['publish', 'commentsPermission', 'extra_info', 'draft', 'title', 'hybrid', 'media', 'topic'] :
            ['publish', 'hybridInfo', 'draft', 'extra_info', 'hybrid', 'reprint', 'commentsPermission', 'appreciate', 'publishSwitch', 'creationStatement', 'commercialReportInfo', 'toFollower', 'contentsTables', 'thanksInvitation'];
        exactObject(value.data, fields, ['publish', 'draft', 'extra_info']);
        // 深度和总量有界；每个已知顶层字段的嵌套键仍按共享出站模型检查。
        const schemas = {
            publish: ['traceId'], commentsPermission: ['comment_permission'], draft: ['disabled', 'isPublished', 'contentId'],
            extra_info: value.action === 'pin' ? ['view_permission', 'publisher'] : ['question_id', 'publisher', 'include', 'pc_business_params'],
            title: ['title'], hybrid: ['html', 'textLength'], media: ['medias'], topic: ['topics'], hybridInfo: [], toFollower: [],
            reprint: ['reshipment_settings'], appreciate: ['can_reward'], publishSwitch: ['draft_type'], creationStatement: ['disclaimer_status', 'disclaimer_type'],
            commercialReportInfo: ['isReport'], contentsTables: ['table_of_contents_enabled'], thanksInvitation: ['thank_inviter_status', 'thank_inviter'],
        };
        for (const [key, entry] of Object.entries(value.data)) exactObject(entry, schemas[key], []);
        const data = value.data;
        check(typeof data.publish.traceId === 'string' && /^[0-9]{1,16},[0-9a-f-]{36}$/iu.test(data.publish.traceId));
        check(data.extra_info.publisher === 'pc' && data.draft.disabled === 1);
        if (data.commentsPermission) check(data.commentsPermission.comment_permission === 'all');
        if (data.draft.isPublished !== undefined) check(typeof data.draft.isPublished === 'boolean');
        if (data.reprint) check(data.reprint.reshipment_settings === 'allowed');
        if (data.appreciate) check(data.appreciate.can_reward === false);
        if (data.publishSwitch) check(data.publishSwitch.draft_type === 'normal');
        if (data.creationStatement) check(data.creationStatement.disclaimer_status === 'close' && data.creationStatement.disclaimer_type === 'none');
        if (data.commercialReportInfo) check(data.commercialReportInfo.isReport === 0);
        if (data.contentsTables) check(typeof data.contentsTables.table_of_contents_enabled === 'boolean');
        if (data.thanksInvitation) check(data.thanksInvitation.thank_inviter_status === 'close' && data.thanksInvitation.thank_inviter === '');
        if (value.action === 'answer') { check(numberId.test(data.extra_info.question_id)); check(businessText(data.hybrid?.html));
            check(data.extra_info.include === 'is_visible,paid_info,paid_info_content,has_column,admin_closed_comment,reward_info,annotation_action,annotation_detail,collapse_reason,is_normal,is_sticky,collapsed_by,suggest_edit,comment_count,thanks_count,favlists_count,can_comment,content,editable_content,voteup_count,reshipment_settings,comment_permission,created_time,updated_time,review_info,relevant_info,question,excerpt,attachment,content_source,is_labeled,endorsements,reaction_instruction,ip_info,relationship.is_authorized,voting,is_thanked,is_author,is_nothelp,is_favorited;author.vip_info,kvip_info,badge[*].topics;settings.table_of_contents.enabled');
            if (data.draft.contentId != null) check(numberId.test(data.draft.contentId));
            if (data.extra_info.pc_business_params !== undefined) { const extra = JSON.parse(data.extra_info.pc_business_params); exactObject(extra,
                ['reshipment_settings', 'comment_permission', 'reward_setting', 'disclaimer_status', 'disclaimer_type', 'commercial_report_info', 'commercial_zhitask_bind_info', 'is_report', 'table_of_contents_enabled', 'thank_inviter_status', 'thank_inviter']);
                exactObject(extra.reward_setting, ['can_reward']); exactObject(extra.commercial_report_info, ['is_report']);
                check(extra.reshipment_settings === 'allowed' && extra.comment_permission === 'all' && extra.reward_setting.can_reward === false &&
                    extra.disclaimer_status === 'close' && extra.disclaimer_type === 'none' && extra.commercial_report_info.is_report === false &&
                    extra.commercial_zhitask_bind_info === null && extra.is_report === false && typeof extra.table_of_contents_enabled === 'boolean' &&
                    extra.thank_inviter_status === 'close' && extra.thank_inviter === ''); }
        } else {
            check(data.extra_info.view_permission === 'all');
            if (data.hybrid) check(businessText(data.hybrid.html) && Number.isSafeInteger(data.hybrid.textLength) && data.hybrid.textLength >= 0);
            if (data.title) check(businessText(data.title.title));
            if (data.media) { check(Array.isArray(data.media.medias) && data.media.medias.length <= 100);
                for (const item of data.media.medias) { exactObject(item, ['image']); exactObject(item.image, ['height', 'width', 'url', 'originalUrl', 'watermark', 'watermarkUrl'], ['height', 'width', 'url', 'originalUrl']);
                    for (const key of ['url', 'originalUrl', 'watermarkUrl']) if (item.image[key] != null) safeUploadedImageUrl(item.image[key]);
                    check(Number.isSafeInteger(item.image.height) && item.image.height >= 0 && Number.isSafeInteger(item.image.width) && item.image.width >= 0);
                } }
            if (data.topic) { check(Array.isArray(data.topic.topics) && data.topic.topics.length <= 100);
                for (const item of data.topic.topics) { exactObject(item, ['topic_id', 'topic_name']); check(numberId.test(String(item.topic_id)) && businessText(item.topic_name)); } }
        }
    }
    function safeUploadedImageUrl(value) {
        check(typeof value === 'string' && value.length <= 2048); const url = new URL(value);
        check(url.protocol === 'https:' && /^pic[1-4ax][.]zhimg[.]com$/u.test(url.host) && !url.username && !url.password && !url.hash);
        return value;
    }

    // 与共享 ZhihuFetchSignature/ZseSigner 同一算法；所有 Cookie 输入留在闭包内。
    function md5(bytes) {
        if (typeof bytes === 'string') bytes = new TextEncoder().encode(bytes);
        const padded = new Uint8Array(Math.ceil((bytes.length + 9) / 64) * 64); padded.set(bytes); padded[bytes.length] = 128;
        const view = new DataView(padded.buffer), bits = BigInt(bytes.length) * 8n;
        view.setUint32(padded.length - 8, Number(bits & 0xffffffffn), true); view.setUint32(padded.length - 4, Number(bits >> 32n), true);
        const shifts = [7,12,17,22,5,9,14,20,4,11,16,23,6,10,15,21], rotate = (x, n) => (x << n) | (x >>> (32 - n));
        let a0 = 0x67452301, b0 = 0xefcdab89, c0 = 0x98badcfe, d0 = 0x10325476;
        for (let offset = 0; offset < padded.length; offset += 64) {
            let a = a0, b = b0, c = c0, d = d0;
            for (let i = 0; i < 64; i++) {
                let f, g;
                if (i < 16) { f = (b & c) | (~b & d); g = i; }
                else if (i < 32) { f = (d & b) | (~d & c); g = (5 * i + 1) % 16; }
                else if (i < 48) { f = b ^ c ^ d; g = (3 * i + 5) % 16; }
                else { f = c ^ (b | ~d); g = (7 * i) % 16; }
                const k = Math.floor(Math.abs(Math.sin(i + 1)) * 4294967296) >>> 0;
                const next = (b + rotate((a + f + k + view.getUint32(offset + g * 4, true)) | 0, shifts[Math.floor(i / 16) * 4 + i % 4])) | 0;
                a = d; d = c; c = b; b = next;
            }
            a0 = (a0 + a) | 0; b0 = (b0 + b) | 0; c0 = (c0 + c) | 0; d0 = (d0 + d) | 0;
        }
        return [a0,b0,c0,d0].flatMap(x => [0,8,16,24].map(n => ((x >>> n) & 255).toString(16).padStart(2, '0'))).join('');
    }
    const zseKeys = [1170614578,1024848638,1413669199,3951632832,3528873006,2921909214,4151847688,3997739139,1933479194,3323781115,3888513386,460404854,3747539722,2403641034,2615871395,2119585428,2265697227,2035090028,2773447226,4289380121,4217216195,2200601443,3051914490,1579901135,1321810770,456816404,2903323407,4065664991,330002838,3506006750,363569021,2347096187];
    const zseBox = [20,223,245,7,248,2,194,209,87,6,227,253,240,128,222,91,237,9,125,157,230,93,252,205,90,79,144,199,159,197,186,167,39,37,156,198,38,42,43,168,217,153,15,103,80,189,71,191,97,84,247,95,36,69,14,35,12,171,28,114,178,148,86,182,32,83,158,109,22,255,94,238,151,85,77,124,254,18,4,26,123,176,232,193,131,172,143,142,150,30,10,146,162,62,224,218,196,229,1,192,213,27,110,56,231,180,138,107,242,187,54,120,19,44,117,228,215,203,53,239,251,127,81,11,133,96,204,132,41,115,73,55,249,147,102,48,122,145,106,118,74,190,29,16,174,5,177,129,63,113,99,31,161,76,246,34,211,13,60,68,207,160,65,111,82,165,67,169,225,57,112,244,155,51,236,200,233,58,61,47,100,137,185,64,17,70,234,163,219,108,170,166,59,149,52,105,24,212,78,173,45,0,116,226,119,136,206,135,175,195,25,92,121,208,126,139,3,75,141,21,130,98,241,40,154,66,184,49,181,46,243,88,101,183,8,23,72,188,104,179,210,134,250,201,164,89,216,202,220,50,221,152,140,33,235,214];
    function zseSign(source) {
        const seed = crypto.getRandomValues(new Uint8Array(1))[0] % 127;
        const input = new TextEncoder().encode(md5(source)), plain = new Uint8Array(48);
        plain[0] = seed; plain.set(input, 2); plain.fill(14, 34);
        const key = new TextEncoder().encode('059053f7d15e01d7'), cipher = new Uint8Array(48);
        const rotate = (v, n) => (v << n) | (v >>> (32 - n));
        let previous = key.map(x => x ^ 42);
        for (let offset = 0; offset < plain.length; offset += 16) {
            const block = plain.slice(offset, offset + 16).map((x, i) => x ^ previous[i]);
            const view = new DataView(block.buffer), tr = new Int32Array(36);
            for (let i = 0; i < 4; i++) tr[i] = view.getInt32(i * 4);
            for (let i = 0; i < 32; i++) {
                const t = tr[i + 1] ^ tr[i + 2] ^ tr[i + 3] ^ zseKeys[i];
                const v = (zseBox[(t >>> 24) & 255] << 24) | (zseBox[(t >>> 16) & 255] << 16) | (zseBox[(t >>> 8) & 255] << 8) | zseBox[t & 255];
                tr[i + 4] = tr[i] ^ v ^ rotate(v, 2) ^ rotate(v, 10) ^ rotate(v, 18) ^ rotate(v, 24);
            }
            for (let i = 0; i < 4; i++) view.setInt32(i * 4, tr[35 - i]);
            previous = block; cipher.set(block, offset);
        }
        const alphabet = '6fpLRqJO8M/c3jnYxFkUVC4ZIG12SiH=5v0mXDazWBTsuw7QetbKdoPyAl+hN9rgE';
        let result = '', index = 0;
        for (let p = cipher.length - 1; p >= 0; p -= 3) {
            let value = 0;
            for (let i = 0; i < 3; i++) value |= (cipher[p - i] ^ ((58 >>> (8 * (index++ % 4))) & 255)) << (8 * i);
            for (let i = 0; i < 4; i++) result += alphabet[(value >>> (6 * i)) & 63];
        }
        return '2.0_' + result;
    }
    // 协议查表数据来自 shared-domain 的 ZhihuMessageBodyEncryptor，非账号密钥。
    const messageProtocol = 'jMG7yWvFZrgFKLB3cESv6Edbt7jX9WO7HZBsd4F/BfHuDEthz0/TYZp0QDiksPiHA23oZTjaz7LZUYQ991089fsOrTmIFXo/KiIRupwY6f5z0hIkHwGDoF11fqZugPDYJgslEFGx1EuwclIWaRfHDuzdlvmp0D0JrldI1QArgRo1RXBkJmhhpziR1/3F8Shvh6UqaNMAxAGf7elAHHBK1O3ZCV3JHjeAEjwUy7ZjoeThxvzXcWFdSKy5hZ8QNQ8r/qHnVbA83pfEdhJMhm4vD58+SRoprI74ZQhb4dTLun5Ca5eHAc0Q5z0t2/xQqXi4NJtvAoj2L6lGH3jGtuXWWGlFPSMc7wvGkIO+rH30W9DO6aS8XUB6YfHfKD0Elh6BpvXLeN6TtzDqUQljKEyNGXleuaTlE8IF2/sxJWyPTZK623bJ/IarIFvgag02HpxAJ4EJZZrdPLUWQMFzrlj64wIYKjhFVGJzjZqpucrX5vKLLB1EObKW1QRo5V39d6HEFAaBlm5yQlssOfnR6bnJo1d53/7CAu0etqeWiUElajzVsF/qqiD6hHjISxefCD5su9t2wfqHoyBS7WMAPhmVSdG9UuOtLfqEds9FG5UCOWV6V7+m4BLFDd72Ni9kj0Obx+mvulBMf2Tw3yU8CpIWh/uv5V+8P9aex3wSTYBjIQqIKR1DML+Y3Alk51b0f6vFoPPCcdqQsj7pUwplLUmGHiuPDGuX1Tm0GUfMeaZS/+BYfdj/zQPrEbOnnYdPIGI16c3+3X5oWUepv4ScHD4CK21DPicY5QbHkIW8rXjwUtQKHy0+RVVrcoqeqLbF1Ob9OZJmBYL3L6BOF3jCvOvUUBkHjZBkeU9cJT723OS7yameNUAWKaSA9WMCWunfxL9yT2aUjQLHFuoxLtvzXah1tAluEfyMU+g7dJXCKNJGs6ttCHWX6ziHUxb0rU+8JtLNHnoO7pdM8Chpgtw1wFuntvyd5gJ5qxvEhWA/1S2yS1qL7JB9BNdisvAeS6hXyTAqUTJKpdAOu2gszp92gBro/uOC9hRiswvRlHYkwjOiW0k/VCzLsWLRC0uv+xridIGUdRdohfYqlUkH4L9fqzTF1pP8g24cxnSi6A1YvkfaIjHJqtk3Q5Qu+L5ZCuYUiHloIkU63aN8zxFXsegD/WyeiNy1zSlWhjztqkob+w2XZXJMKF2wzxOgdjXRgmSVDvvos96mQDniUIbLL3CcafMCGq/AtFUg/kmT3jhhhHbnHgHaFxcMNoqK8Bmamsjel5c/NU1NVziMjPRc1tZy+21tHcqHh0y1y8vFyY+PQvRoaBxFa2vV6a+vUm35+RMEyMg8D7a2AUlvb9J8NjZidQ0NJ4fZ2ST5KiqYhu/vYSQCAn4Xk5PGLQkJczlKSlj2amoQ1JKSPseJiURm//8RMExMU0dpadS9ubmzQ2Ji2mpXV5yA3t4pKjc3rH41NWrkcnL+1RsbBVTY2HzteXnztr+/sdyUlDguBwd/vLS0uHEBASsCwcE1XtXVeiIAAHeRcXFLm62tjXs9PW39Y2MZUtHRddcZGQSJ398izEREiCEwMK2YrKyEdwMDJh0TE7lV7e2n4aCgXVPo6K92OjpgMUFBWwzGxjL1LS2Xo/j437u7u7utU1PpOkVFXq/29tHEQkKO+GxsFP5lZRphUFCd63t7+/JhYRVf5uahWerqqD2Dg/nvpKRQdDg4bIzk5GhNKSnjuc/PwjuNjf0TmJjPJzk5pAGxsQvITk6Gp/Pz1hYaGrARkZHLcjExZZdzc0YeFRW6wI6OSXAMDCOq9fXe7HR0+NafnzFAbm7Zplpa4KRYWOwUGBi8lqqqgGlfX5J/BgYhBsrKMEYvL+Hofn72QiAg5/AsLJOxwMDN0pCQN4PS0iovNDSg/GZmEpKhoYVnWVmULAQEeM+EhEC3ycnESC4u5k4nJ+9KZ2fcA7i4Dyk/P6K4vr62jufnb4HQ0C3zKCifPIaG8j9GRlGP1NQgJTs7pRWdncfjoqJa5n9/8aX9/dfDgoJKrFZW4hgcHLSzwsLKVtracOJwcPcrCwt7a/v7G5SoqIy/xMTACMzMNMJAQIfqp6dcYF5emfEhIZuK19cs9yMjloLg4GeI7u5meQoKKLSysr6rXV3t0B4eCW9UVJDNSUmDB7OzBkFgYN3bm5s7XdPTeUwkJOiwzs7JofHx2x+WlsFLKyvrGpWVzj6Fhfpa5eWumnV1Tg3DwzndmZkzjenpY09kZNDnqalUhOLibtienjaysLC3UOzso7rHx8zBgIBNYvDwFwm6ugiuVVXqID4+qQC8vANlW1uVGx0dvZl6ekhR4eGrxk9PgRwWFrIoDg52W93dfW739x+oXFzk0RAQDWj+/hacpqaCCrW1DpN4eE8mDw9xnaOjiaD8/NOiUVHlEhERtTdDQ1aL6+trnqWlin0zM2mF29sl7nd3/3g8PGRs9PQYDsXFOljc3HSp+vrYEJycw8tLS4s0iIj8vre3v98UFAD/JiaR5aurVTKBgfULzc09RCIi7mTy8h7TEhIKzkdHj3MICC+QfHxDV+PjpmNSUpqVfX1Hn3Z2QeCurlnFi4tFIzIyqgW9vQfZHx8C+iUlnjNISF96BQUuFxcM2oqK8DaamsgZl5c/3k1NVzWMjPQ41tZyXG1tHfuHh0zKy8vFtY+PQsloaBz0a2vVRa+vUun5+RNtyMg8BLa2AQ9vb9JJNjZifA0NJ3XZ2SSHKiqY+e/vYYYCAn4kk5PGFwkJcy1KSlg5amoQ9pKSPtSJiUTH//8RZkxMUzBpadRHubmzvWJi2kNXV5xq3t4pgDc3rCo1NWp+cnL+5BsbBdXY2HxUeXnz7b+/sbaUlDjcBwd/LrS0uLwBAStxwcE1AtXVel4AAHcicXFLka2tjZs9PW17Y2MZ/dHRdVIZGQTX398iiUREiMwwMK0hrKyEmAMDJncTE7kd7e2nVaCgXeHo6K9TOjpgdkFBWzHGxjIMLS2X9fj436O7u7u7U1PprUVFXjr29tGvQkKOxGxsFPhlZRr+UFCdYXt7++thYRXy5uahX+rqqFmDg/k9pKRQ7zg4bHTk5GiMKSnjTc/PwrmNjf07mJjPEzk5pCexsQsBTk6GyPPz1qcaGrAWkZHLETExZXJzc0aXFRW6Ho6OScAMDCNw9fXeqnR0+OyfnzHWbm7ZQFpa4KZYWOykGBi8FKqqgJZfX5JpBgYhf8rKMAYvL+FGfn726CAg50IsLJPwwMDNsZCQN9LS0iqDNDSgL2ZmEvyhoYWSWVmUZwQEeCyEhEDPycnEty4u5kgnJ+9OZ2fcSri4DwM/P6Ipvr62uOfnb47Q0C2BKCif84aG8jxGRlE/1NQgjzs7pSWdnccVoqJa439/8eb9/delgoJKw1ZW4qwcHLQYwsLKs9racFZwcPfiCwt7K/v7G2uoqIyUxMTAv8zMNAhAQIfCp6dc6l5emWAhIZvx19csiiMjlvfg4GeC7u5miAoKKHmysr60XV3tqx4eCdBUVJBvSUmDzbOzBgdgYN1Bm5s729PTeV0kJOhMzs7JsPHx26GWlsEfKyvrS5WVzhqFhfo+5eWuWnV1TprDwzkNmZkz3enpY41kZNBPqalU5+LiboSenjbYsLC3suzso1DHx8y6gIBNwfDwF2K6uggJVVXqrj4+qSC8vAMAW1uVZR0dvRt6ekiZ4eGrUU9PgcYWFrIcDg52KN3dfVv39x9uXFzkqBAQDdH+/hZopqaCnLW1Dgp4eE+TDw9xJqOjiZ38/NOgUVHlohERtRJDQ1Y36+tri6Wlip4zM2l929slhXd3/+48PGR49PQYbMXFOg7c3HRY+vrYqZycwxBLS4vLiIj8NLe3v74UFADfJiaR/6urVeWBgfUyzc09CyIi7kTy8h5kEhIK00dHj84ICC9zfHxDkOPjpldSUppjfX1HlXZ2QZ+urlngi4tFxTIyqiO9vQcFHx8C2SUlnvpISF8zBQUuehcM2heK8DaKmsgZmpc/3pdNVzVNjPQ4jNZyXNZtHftth0zKh8vFtcuPQsmPaBz0aGvVRWuvUumv+RNt+cg8BMi2AQ+2b9JJbzZifDYNJ3UN2SSH2SqY+SrvYYbvAn4kApPGF5MJcy0JSlg5SmoQ9mqSPtSSiUTHif8RZv9MUzBMadRHabmzvbli2kNiV5xqV94pgN43rCo3NWp+NXL+5HIbBdUb2HxU2Hnz7Xm/sba/lDjclAd/Lge0uLy0AStxAcE1AsHVel7VAHciAHFLkXGtjZutPW17PWMZ/WPRdVLRGQTXGd8iid9EiMxEMK0hMKyEmKwDJncDE7kdE+2nVe2gXeGg6K9T6DpgdjpBWzFBxjIMxi2X9S3436P4u7u7u1PprVNFXjpF9tGv9kKOxEJsFPhsZRr+ZVCdYVB7++t7YRXyYeahX+bqqFnqg/k9g6RQ76Q4bHQ45GiM5CnjTSnPwrnPjf07jZjPE5g5pCc5sQsBsU6GyE7z1qfzGrAWGpHLEZExZXIxc0aXcxW6HhWOScCODCNwDPXeqvV0+Ox0nzHWn27ZQG5a4KZaWOykWBi8FBiqgJaqX5JpXwYhfwbKMAbKL+FGL3726H4g50IgLJPwLMDNscCQN9KQ0iqD0jSgLzRmEvxmoYWSoVmUZ1kEeCwEhEDPhMnEt8ku5kguJ+9OJ2fcSme4DwO4P6IpP762uL7nb47n0C2B0Cif8yiG8jyGRlE/RtQgj9Q7pSU7nccVnaJa46J/8eZ//del/YJKw4JW4qxWHLQYHMLKs8LacFbacPficAt7Kwv7G2v7qIyUqMTAv8TMNAjMQIfCQKdc6qdemWBeIZvxIdcsitcjlvcj4GeC4O5miO4KKHkKsr60sl3tq10eCdAeVJBvVEmDzUmzBgezYN1BYJs725vTeV3TJOhMJM7JsM7x26HxlsEflivrSyuVzhqVhfo+heWuWuV1Tpp1wzkNw5kz3ZnpY43pZNBPZKlU56niboTinjbYnrC3srDso1Dsx8y6x4BNwYDwF2LwuggJulXqrlU+qSA+vAMAvFuVZVsdvRsdekiZeuGrUeFPgcZPFrIcFg52KA7dfVvd9x9u91zkqFwQDdEQ/hZo/qaCnKa1Dgq1eE+TeA9xJg+jiZ2j/NOg/FHlolERtRIRQ1Y3Q+tri+ulip6lM2l9M9slhdt3/+53PGR4PPQYbPTFOg7F3HRY3PrYqfqcwxCcS4vLS4j8NIi3v763FADfFCaR/yarVeWrgfUygc09C80i7kQi8h5k8hIK0xJHj85HCC9zCHxDkHzjplfjUppjUn1HlX12QZ92rlngrotFxYsyqiMyvQcFvR8C2R8lnvolSF8zSAUuegUM2hcX8DaKisgZmpo/3peXVzVNTfQ4jIxyXNbWHfttbUzKh4fFtcvLQsmPjxz0aGjVRWtrUumvrxNt+fk8BMjIAQ+2ttJJb29ifDY2J3UNDSSH2dmY+SoqYYbv734kAgLGF5OTcy0JCVg5SkoQ9mpqPtSSkkTHiYkRZv//UzBMTNRHaWmzvbm52kNiYpxqV1cpgN7erCo3N2p+NTX+5HJyBdUbG3xU2Njz7Xl5sba/vzjclJR/LgcHuLy0tCtxAQE1AsHBel7V1XciAABLkXFxjZutrW17PT0Z/WNjdVLR0QTXGRkiid/fiMxERK0hMDCEmKysJncDA7kdExOnVe3tXeGgoK9T6Ohgdjo6WzFBQTIMxsaX9S0t36P4+Lu7u7vprVNTXjpFRdGv9vaOxEJCFPhsbBr+ZWWdYVBQ++t7exXyYWGhX+bmqFnq6vk9g4NQ76SkbHQ4OGiM5OTjTSkpwrnPz/07jY3PE5iYpCc5OQsBsbGGyE5O1qfz87AWGhrLEZGRZXIxMUaXc3O6HhUVScCOjiNwDAzeqvX1+Ox0dDHWn5/ZQG5u4KZaWuykWFi8FBgYgJaqqpJpX18hfwYGMAbKyuFGLy/26H5+50IgIJPwLCzNscDAN9KQkCqD0tKgLzQ0EvxmZoWSoaGUZ1lZeCwEBEDPhITEt8nJ5kguLu9OJyfcSmdnDwO4uKIpPz+2uL6+b47n5y2B0NCf8ygo8jyGhlE/RkYgj9TUpSU7O8cVnZ1a46Ki8eZ/f9el/f1Kw4KC4qxWVrQYHBzKs8LCcFba2vficHB7KwsLG2v7+4yUqKjAv8TENAjMzIfCQEBc6qenmWBeXpvxISEsitfXlvcjI2eC4OBmiO7uKHkKCr60srLtq11dCdAeHpBvVFSDzUlJBgezs91BYGA725ubeV3T0+hMJCTJsM7O26Hx8cEflpbrSysrzhqVlfo+hYWuWuXlTpp1dTkNw8Mz3ZmZY43p6dBPZGRU56mpboTi4jbYnp63srCwo1Ds7My6x8dNwYCAF2Lw8AgJurrqrlVVqSA+PgMAvLyVZVtbvRsdHUiZenqrUeHhgcZPT7IcFhZ2KA4OfVvd3R9u9/fkqFxcDdEQEBZo/v6CnKamDgq1tU+TeHhxJg8PiZ2jo9Og/PzlolFRtRIREVY3Q0Nri+vrip6lpWl9MzMlhdvb/+53d2R4PDwYbPT0Og7FxXRY3NzYqfr6wxCcnIvLS0v8NIiIv763twDfFBSR/yYmVeWrq/UygYE9C83N7kQiIh5k8vIK0xISj85HRy9zCAhDkHx8plfj45pjUlJHlX19QZ92dlngrq5FxYuLqiMyMgcFvb0C2R8fnvolJV8zSEguegUFF4qal02M1m2Hy49oa6/5yLZvNg3ZKu8CkwlKapKJ/0xpuWJX3jc1chvYeb+UB7QBwdUAca09Y9EZ30QwrAMT7aDoOkHGLfi7U0X2QmxlUHth5uqDpDjkKc+NmDmxTvMakTFzFY4M9XSfblpYGKpfBsovfiAswJDSNGahWQSEyS4nZ7g/vufQKIZG1Dudon/9glYcwtpwC/uoxMxAp14h1yPg7gqyXR5USbNgm9MkzvGWK5WF5XXDmelkqeKesOzHgPC6VT68Wx164U8WDt33XBD+prV4D6P8URFD66Uz23c89MXc+pxLiLcUJquBzSLyEkcIfONSfXauizK9HyVIBQ==';
    function encryptMessageBody(form) {
        const protocol = Uint8Array.from(atob(messageProtocol), ch => ch.charCodeAt(0));
        const roundKeys = protocol.slice(0, 176), x0 = protocol.slice(176, 432), xr = protocol.slice(432, 688), xf = protocol.slice(688, 944);
        const tables = [protocol.slice(944, 1968), protocol.slice(1968, 2992), protocol.slice(2992, 4016), protocol.slice(4016, 5040)], sbox = protocol.slice(5040, 5296);
        const swap = value => ((value & 0x55) << 1) | ((value & 0xaa) >>> 1);
        const xor = (table, a, b) => (table[((a >>> 4) << 4) ^ (b >>> 4)] & 0xf0) ^ (table[((a & 15) << 4) ^ (b & 15)] >>> 4);
        const plain = new TextEncoder().encode(form), padding = 16 - plain.length % 16, input = new Uint8Array(plain.length + padding);
        input.set(plain.map(x => swap(x) ^ 0xbb)); input.fill(swap(padding) ^ 0xbb, plain.length);
        let previous = new TextEncoder().encode('f0551856aa575faa').map(swap);
        const output = new Uint8Array(input.length), positions = [[0,4,8,12],[5,9,13,1],[10,14,2,6],[15,3,7,11]], shift = [0,5,10,15,4,9,14,3,8,13,2,7,12,1,6,11];
        for (let offset = 0; offset < input.length; offset += 16) {
            let state = input.slice(offset, offset + 16).map((value, i) => xor(x0, value ^ previous[i], roundKeys[i]));
            for (let round = 1; round < 10; round++) {
                const mixed = new Uint8Array(16);
                positions.forEach((group, tableIndex) => group.forEach((position, word) => {
                    for (let byte = 0; byte < 4; byte++) {
                        const index = word * 4 + byte, value = tables[tableIndex][state[position] * 4 + 3 - byte];
                        mixed[index] = tableIndex === 0 ? value : xor(xr, mixed[index], value);
                    }
                }));
                state = mixed.map((value, i) => xor(xr, value, roundKeys[round * 16 + i]));
            }
            previous = Uint8Array.from(shift, (position, i) => xor(xf, sbox[state[position]], roundKeys[160 + i]));
            output.set(previous.map(swap), offset);
        }
        let binary = '';
        for (let i = 0; i < output.length; i += 16384) binary += String.fromCharCode(...output.subarray(i, i + 16384));
        return btoa(binary);
    }
    async function businessCookies(host) {
        const url = host === API ? HISTORY_SELF : host === LENS ? `${LENS}/api/v4/videos` : ME;
        const records = await new Promise((resolve, reject) => GM_cookie.list({url}, (cookies, error) => error ? reject(new Error('unavailable')) : resolve(cookies)));
        check(Array.isArray(records));
        const entries = new Map();
        for (const cookie of records) {
            if (!['z_c0', 'd_c0', '_xsrf', '__zse_ck'].includes(cookie.name)) continue;
            check(cookie.path === '/' && !cookie.partitionKey && (!cookie.expirationDate || cookie.expirationDate * 1000 > Date.now()));
            check(cookie.domain === new URL(host).hostname || (!cookie.hostOnly && cookie.domain.replace(/^\./u, '') === 'zhihu.com'));
            check(typeof cookie.value === 'string' && cookie.value.length <= 16384 && !/[\x00-\x20\x7f;,]/u.test(cookie.value));
            check(!entries.has(cookie.name) || entries.get(cookie.name) === cookie.value); entries.set(cookie.name, cookie.value);
        }
        check(entries.get('z_c0'));
        return {header: [...entries].map(([name, value]) => `${name}=${value}`).join('; '), identity: entries.get('z_c0'), dc0: entries.get('d_c0'), xsrf: entries.get('_xsrf')};
    }
    function businessResponse(raw) {
        if (raw === '') return raw;
        JSON.parse(raw, safeResponseField);
        return raw;
    }
    async function handleBusiness(data, entry) {
        const {finish} = entry;
        let epoch, owner;
        const live = () => !entry.settled && trustedPage() && profileAllowed && verifiedId === owner && businessOwner === owner && businessEpoch === epoch;
        const request = (url, method, headers = {}, body = '', cookie = '') => new Promise((resolve, reject) => {
            if (!live()) return reject(new Error('expired'));
            if (method !== 'GET' && !entry.submitted) { entry.submitted = true; entry.send('submitted'); }
            // 官方专用 cookie 参数与 anonymous 一起固定快照，绝不合入浏览器当前会话。
            transport(entry, {url, method, headers, body, cookie, anonymous: true}, response => {
                if (!live()) return reject(new Error('expired'));
                if (response.status >= 300 && response.status < 400) return reject(new Error('denied'));
                resolve(response);
            }, kind => reject(new Error(kind)));
        });
        try {
            check(keyId(data.owner) && typeof data.id === 'string' && data.id.length <= 512 && businessText(data.body));
            check(new TextEncoder().encode(data.body).length <= textBudget);
            owner = data.owner; epoch = businessEpoch; check(live());
            const query = parseQuery(data.query);
            const upload = data.op === 'uploadImage' || data.op === 'uploadVoice';
            if (upload) check(data.bytes instanceof ArrayBuffer && data.bytes.byteLength > 0 && data.bytes.byteLength <= binaryBudget && data.id === '' && data.query === '');
            else check(data.bytes === null);
            const destination = upload ? {scope: 'create'} : businessDestination(data.op, data.id, query, data.body);
            check(businessScopes.has(destination.scope));
            entry.mutation = upload || destination.method !== 'GET';
            entry.timeout(upload ? 175000 : 60000);
            const website = await businessCookies(WWW); check(live());
            const proof = await request(ME, 'GET', {}, '', website.header);
            check(proof.status === 200 && profileId(JSON.parse(proof.body)) === owner);
            const api = await businessCookies(API); check(live());
            const apiProof = await request(HISTORY_SELF, 'GET', {}, '', api.header);
            check(apiProof.status === 200 && profileId(JSON.parse(apiProof.body)) === owner);
            const signed = async (url, method, body = '', contentType = 'application/json', profile = 'web') => {
                const target = new URL(url); check([WWW, API, LENS].includes(target.origin));
                const snapshot = target.origin === WWW ? website : target.origin === API ? api : await businessCookies(LENS);
                if (target.origin === LENS) check(snapshot.identity === api.identity);
                check(live() && snapshot.dc0 && (method === 'GET' || snapshot.xsrf));
                const zse93 = profile === 'message' ? '101_1_1.0' : '101_3_3.0';
                const headers = {Accept: 'application/json', 'x-requested-with': 'fetch',
                    'x-zse-93': zse93, 'x-zse-96': zseSign([zse93, target.pathname + target.search, snapshot.dc0, body].filter(x => x !== '').join('+'))};
                if (snapshot.xsrf) headers['x-xsrftoken'] = snapshot.xsrf;
                if (profile === 'uploadVoice') headers['X-Upload-Conent-Type'] = 'audio/mp4';
                if (target.origin === WWW && /^\/api\/v4\/questions\/[1-9][0-9]{0,18}\/draft$/u.test(target.pathname)) {
                    headers.Referer = `${WWW}/question/${target.pathname.split('/')[4]}/answer/`;
                } else if (target.pathname === '/api/v4/content/publish' || target.pathname === '/content/drafts') headers.Referer = WWW + '/';
                if (body !== '') headers['Content-Type'] = contentType;
                if (profile === 'editor') { headers['User-Agent'] = 'com.zhihu.android/Futureve/11.12.0 Mozilla/5.0 (Linux; Android 15)'; headers['x-api-version'] = '3.0.89'; }
                else if (profile === 'voice') { headers['User-Agent'] = 'com.zhihu.android/Futureve/11.12.0 Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Mobile Safari/537.36'; headers['x-api-version'] = '3.1.8'; headers['x-app-version'] = '11.12.0'; headers['x-app-za'] = 'OS=Android&Release=15&VersionName=11.12.0&VersionCode=41208&Product=com.zhihu.android&DeviceType=AndroidPhone'; }
                else if (profile === 'mobile' || profile === 'message') { headers['User-Agent'] = 'com.zhihu.android/Futureve/10.61.0 Mozilla/5.0 (Linux; Android 12; sdk_gphone64_arm64 Build/SE1A.220630.001.A1; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/57.0.1000.10 Mobile Safari/537.36'; headers['x-api-version'] = '3.1.8'; headers['x-app-version'] = '10.61.0'; headers['x-app-za'] = 'OS=Android&Release=12&Model=sdk_gphone64_arm64&VersionName=10.61.0&VersionCode=26107&Product=com.zhihu.android&Width=1440&Height=2952&Installer=%E7%81%B0%E5%BA%A6&DeviceType=AndroidPhone&Brand=google'; }
                const response = await request(url, method, headers, body, snapshot.header);
                const challengeUrl = challengeFromBody(response.status, response.body);
                if (challengeUrl) { recordChallenge(challengeUrl); throw new Error(entry.submitted ? 'unavailable' : 'challenge'); }
                return response;
            };
            const response = upload ? await businessUpload(data, signed, request, live) : await signed(destination.url, destination.method, destination.body, destination.contentType, destination.profile);
            check(live());
            const safeBody = businessResponse(response.body);
            if (destination.scope === 'private' && response.status === 200 && safeBody) {
                const paging = JSON.parse(safeBody).paging;
                if (paging?.next) {
                    const next = new URL(paging.next), current = new URL(destination.url);
                    if (next.protocol === 'http:' && !next.port) next.protocol = 'https:';
                    check(next.origin === current.origin && next.pathname === current.pathname && !next.username && !next.password && !next.hash);
                    businessDestination(data.op, data.id, parseQuery(next.search.slice(1)), '');
                }
            }
            if (data.op === 'answerDraft-GET' && response.status === 200) {
                const settings = JSON.parse(safeBody).settings;
                if (settings && typeof settings === 'object' && !Array.isArray(settings)) {
                    if (answerDraftSettings.size >= 32 && !answerDraftSettings.has(data.id)) answerDraftSettings.delete(answerDraftSettings.keys().next().value);
                    answerDraftSettings.set(data.id, settings);
                }
            }
            finish('http', response.status, safeBody);
        } catch (error) {
            finish(['cancelled', 'expired', 'unavailable', 'challenge'].includes(error?.message) ? error.message : 'denied');
        }
    }

    /** 上传凭据、OSS 地址和分片 ID 均由已授权操作自身取得，不接受页面传入。 */
    async function businessUpload(data, signed, request, live) {
        const bytes = new Uint8Array(data.bytes), meta = JSON.parse(data.body);
        const voice = data.op === 'uploadVoice';
        exactObject(meta, voice ? ['mime', 'durationMs'] : ['mime', 'source', 'width', 'height']);
        check(voice ? meta.mime === 'audio/mp4' : ['image/png', 'image/jpeg', 'image/gif', 'image/webp'].includes(meta.mime));
        if (voice) check(Number.isSafeInteger(meta.durationMs) && meta.durationMs > 0);
        else check(['answer', 'article', 'pin', 'comment', 'zim'].includes(meta.source) && Number.isSafeInteger(meta.width) && meta.width >= 0 && Number.isSafeInteger(meta.height) && meta.height >= 0);
        const hash = md5(bytes);
        const response = await signed(voice ? `${LENS}/api/v4/videos` : `${API}/images`, 'POST', JSON.stringify(voice ?
            {file_md5: hash, file_size: bytes.length, source: 'comment', duration_ms: meta.durationMs} : {image_hash: hash, source: meta.source}), 'application/json', voice ? 'uploadVoice' : 'web');
        if (response.status < 200 || response.status >= 300) return response;
        const session = JSON.parse(response.body), file = session.upload_file || session.uploadfile;
        check(file && typeof file === 'object');
        const id = voice ? file.video_id : file.image_id;
        check(keyId(id));
        const mustUpload = voice ? file.state !== 1 : file.state === 2;
        const key = voice ? file.object_key : `v2-${hash}`;
        const endpoint = voice ? 'https://upload-oss.vzuu.com' : 'https://zhihu-pics-upload.zhimg.com';
        const bucket = voice ? 'zhihu-video-input' : 'zhihu-pics';
        const userAgent = voice ? 'aliyun-sdk-android/2.9.5' : 'aliyun-sdk-js/6.8.0 Chrome 99.0.4844.84 on Windows 10 64-bit';
        const successful = response => { check(response.status >= 200 && response.status < 300); return response; };
        const voiceEvent = async event => {
            try { await signed(`${LENS}/api/v4/videos/${id}/uploading_status`, 'PUT', JSON.stringify({video_id: id, object_key: key, upload_id: session.upload_id || '', video_source: 'comment', upload_event: event})); }
            catch (error) { if (!live()) throw error; }
        };
        if (mustUpload) {
            check(typeof key === 'string' && /^[A-Za-z0-9_./-]{1,512}$/u.test(key) && !key.startsWith('/') && !key.split('/').some(x => x === '..' || x === '.'));
            const token = session.upload_token || session.uploadtoken;
            check(token && ['access_id', 'access_key', 'access_token'].every(k => typeof token[k] === 'string' && token[k].length > 0 && token[k].length <= 16384 && !controls.test(token[k])));
            const oss = async (method, query, contentType, payload) => {
                check(live());
                const date = new Date().toUTCString();
                const headers = {'x-oss-date': date, 'x-oss-security-token': token.access_token, 'x-oss-user-agent': userAgent};
                const canonical = `${method}\n\n${contentType}\n${date}\nx-oss-date:${date}\nx-oss-security-token:${token.access_token}\nx-oss-user-agent:${userAgent}\n/${bucket}/${key}${query ? '?' + query : ''}`;
                const cryptoKey = await crypto.subtle.importKey('raw', new TextEncoder().encode(token.access_key), {name: 'HMAC', hash: 'SHA-1'}, false, ['sign']);
                const signature = new Uint8Array(await crypto.subtle.sign('HMAC', cryptoKey, new TextEncoder().encode(canonical)));
                headers.Authorization = `OSS ${token.access_id}:${btoa(String.fromCharCode(...signature))}`;
                if (contentType) headers['Content-Type'] = contentType;
                check(live());
                return successful(await request(`${endpoint}/${key}${query ? '?' + query : ''}`, method, headers, payload));
            };
            if (voice) await voiceEvent('UPLOADING_START');
            if (!voice && meta.mime === 'image/gif') {
                const initiated = await oss('POST', 'uploads', '', '');
                const uploadId = /<UploadId>([^<]+)<\/UploadId>/u.exec(initiated.body)?.[1];
                check(typeof uploadId === 'string' && /^[A-Za-z0-9_-]{1,256}$/u.test(uploadId));
                const parts = [];
                for (let offset = 0, part = 1; offset < bytes.length; offset += 1024 * 1024, part++) {
                    const uploaded = await oss('PUT', `partNumber=${part}&uploadId=${uploadId}`, 'application/octet-stream', new Blob([bytes.slice(offset, offset + 1024 * 1024)]));
                    const etag = /^etag:[\t ]*([^\r\n]+)$/imu.exec(uploaded.headers)?.[1]?.trim();
                    check(typeof etag === 'string' && /^"?[A-Za-z0-9-]{1,128}"?$/u.test(etag));
                    parts.push(`<Part><PartNumber>${part}</PartNumber><ETag>${etag.replace(/"/gu, '&quot;')}</ETag></Part>`);
                }
                await oss('POST', `uploadId=${uploadId}`, 'application/xml', `<CompleteMultipartUpload>${parts.join('')}</CompleteMultipartUpload>`);
            } else await oss('PUT', '', meta.mime, new Blob([bytes], {type: meta.mime}));
            if (voice) await voiceEvent('UPLOADING_SUCCESS');
            else successful(await signed(`${API}/images/${id}/uploading_status`, 'PUT', '{"upload_result":"success"}'));
        }
        if (voice) return {status: 200, body: JSON.stringify({voiceId: id})};
        for (let attempt = 0; attempt < 10; attempt++) {
            check(live());
            const result = successful(await signed(`${API}/images/${id}`, 'GET'));
            const image = JSON.parse(result.body);
            if (image.status === 'success') {
                const format = image.format || { 'image/png': 'png', 'image/jpeg': 'jpg', 'image/gif': 'gif', 'image/webp': 'webp' }[meta.mime];
                check(['png', 'jpg', 'jpeg', 'gif', 'webp'].includes(format));
                const normalize = value => {
                    check(typeof value === 'string'); const url = new URL(value.replace(/\.$/u, ''));
                    if (!url.pathname.substring(url.pathname.lastIndexOf('/') + 1).includes('.')) url.pathname += '.' + format;
                    return safeUploadedImageUrl(url.href);
                };
                const url = normalize(image.original_src || image.src), originalUrl = normalize(image.original_src || image.src);
                check(typeof image.original_hash === 'string' && /^v2-[a-f0-9]{32}$/u.test(image.original_hash));
                const watermark = ['watermark', 'true', '1'].includes(String(image.watermark).toLowerCase()) ? true : ['original', 'false', '0'].includes(String(image.watermark).toLowerCase()) ? false : null;
                return {status: 200, body: JSON.stringify({url, originalUrl, watermark, watermarkMode: image.watermark || null,
                    watermarkUrl: image.watermark_src ? normalize(image.watermark_src) : null, rawWidth: image.width > 0 ? image.width : meta.width,
                    rawHeight: image.height > 0 ? image.height : meta.height, imageId: id, hash: image.original_hash})};
            }
            check(!['process_fail', 'upload_fail'].includes(image.status));
            if (attempt < 9) await new Promise(resolve => setTimeout(resolve, 2000));
        }
        throw new Error('unavailable');
    }

    function handleRead(data, entry, menuConsent) {
        const {op, id, query: rawQuery} = data, {finish} = entry;
        const profile = op === 'profile';
        const history = op === 'history' || op === 'historySetting';
        const qr = op === 'loginQrStart' || op === 'loginQrPoll' || op === 'cookieSignIn';
        const businessGrant = Object.hasOwn(actions, op) && Boolean(actions[op].scope);
        const login = op === 'authorizeProfile' || qr || businessGrant;
        const verification = op === 'verificationCheck';
        const owner = historyOwner;
        const pending = verification ? challenge : null;
        entry.profile = profile || login;
        entry.history = history;
        entry.businessGrant = businessGrant;
        entry.onFinish = kind => {
            entry.onQrFinish?.(kind);
            if (entry.authorizing) {
                profileChecking = false;
                notifyPermission();
            }
            if (pending?.checking === entry) pending.checking = null;
        };
        const allowed = () => trustedPage() && (!profile || profileAllowed) && (!history || (owner !== null && historyOwner === owner && verifiedId === owner)) &&
            (!verification || (pending && pending === challenge && pending.owner === verifiedId));
        const live = () => !entry.settled && allowed();
        function request(method, href, receive, options = {}) {
            check(live());
            const target = new URL(href);
            check((method === 'GET' || (qr && method === 'POST')) && [WWW, API].includes(target.origin) && !target.username && !target.password && !target.hash);
            if (login) entry.timeout(15000);
            transport(entry, {
                method, url: href,
                anonymous: options.anonymous ?? (!profile && !history && !verification && !businessGrant && op !== 'authorizeProfile'),
                ...(verification && href.startsWith(VERIFY) ? {headers: {Referer: pending.url}} : {}),
            }, ({status, body: raw, headers}) => {
                check(allowed());
                if (status >= 300 && status < 400) {
                    const location = /^location:[\t ]*([^\r\n]+)$/imu.exec(headers)?.[1]?.trim();
                    const url = verificationUrl(location);
                    if (!url || verification) return finish('denied');
                    recordChallenge(url);
                    return finish('challenge');
                }
                const url = !verification && (status === 403 || raw.includes('"error"')) && challengeFromBody(status, raw);
                if (url) { recordChallenge(url); return finish('challenge'); }
                if ((profile || history || op === 'authorizeProfile') && (status === 401 || status === 403)) {
                    let expired = false;
                    try {
                        const error = JSON.parse(raw).error;
                        expired = error?.code === 100 && error.message === 'ERR_LOGIN_TICKET_EXPIRED';
                    } catch { /* 非 JSON 错误不构成失效证据。 */ }
                    if (status === 401 || expired) {
                        if (history && target.origin !== WWW) return revokeHistory('expired');
                        rememberProfile(null);
                    }
                    if (!entry.settled) finish(expired ? 'expired' : status === 401 ? 'guest' : 'denied');
                } else receive(status, raw);
            }, finish);
        }
        const get = (href, receive) => request('GET', href, receive);
        // 登录 Cookie 可以只属于 www；读取历史前后还须核对 API 主机自己的身份。
        function withHistoryOwner(next) {
            get(ME, (status, raw) => {
                if (status !== 200) return finish('unavailable');
                if (profileId(JSON.parse(raw)) !== owner) return rememberProfile(null);
                get(HISTORY_SELF, (apiStatus, apiBody) => {
                    if (apiStatus !== 200) return finish('unavailable');
                    if (profileId(JSON.parse(apiBody)) !== owner) return revokeHistory('expired');
                    next();
                });
            });
        }
        try {
            check(history ? keyId(data.owner) && id === 'account-bound' : data.owner === '');
            if (op === 'bridgeStatus') {
                check(trustedPage() && id === '' && rawQuery === '');
                return finish('http', 200, JSON.stringify(bridgeStatus()));
            }
            // 不支持 manual redirect 的管理器会先泄露请求；finalUrl 不能替代发送前的能力门槛。
            if (readIssue) return finish('unsupported', 0, readIssue);
            check(allowed());
            if (history && data.owner !== owner) return finish('expired');
            const query = parseQuery(rawQuery);
            if (login || verification) {
                check(id === '' && rawQuery === '');
                let consent = menuConsent;
                if (op !== 'loginQrPoll' && !menuConsent) {
                    const gesture = gestures.get(op);
                    gestures.delete(op);
                    if (!gesture || Date.now() > gesture.deadline) return finish('unsupported', 0, 'gesture-required');
                    consent = gesture.granted;
                }
                if (businessGrant) {
                    if (!consent || !profileAllowed || verifiedId === null) return finish('http', 200, '{"authorized":false}');
                    const owner = verifiedId, grantEpoch = businessEpoch;
                    return get(ME, (status, raw) => {
                        if (!live() || grantEpoch !== businessEpoch || !profileAllowed || verifiedId !== owner) return finish('denied');
                        if (status !== 200 || profileId(JSON.parse(raw)) !== owner) { revokeBusiness(); return finish('expired'); }
                        check(businessOwner === null || businessOwner === owner);
                        businessOwner = owner;
                        businessScopes.add(actions[op].scope);
                        finish('http', 200, '{"authorized":true}');
                        notifyPermission();
                    });
                }
                if (op === 'authorizeProfile') {
                    if (!consent) return finish('http', 200, '{"authorized":false}');
                    check(!profileChecking && !qrWriting && browserQr?.state !== 'committing');
                    resetBrowserQr(true);
                    profileAllowed = false;
                    profileChecking = true;
                    entry.authorizing = true;
                    return get(ME, (status, raw) => {
                        if (status !== 200) return finish('unavailable');
                        const data = profileBody(JSON.parse(raw));
                        rememberProfile(data?.id ?? null);
                        if (data === null) return finish('guest');
                        profileAllowed = true;
                        finish('http', 200, '{"authorized":true}');
                    });
                }
                if (qr) return handleBrowserQr(op, {request, finish, live, entry, consent});
                check(consent && pending.opened && !pending.checking);
                pending.checking = entry;
                const appeal = new URL(VERIFY), session = new URL(pending.url).searchParams.get('session');
                if (session) appeal.searchParams.set('session', session);
                const withOwner = next => {
                    if (pending.owner === null) return next();
                    get(ME, (status, raw) => {
                        if (status !== 200 || profileId(JSON.parse(raw)) !== pending.owner) return finish('denied');
                        next();
                    });
                };
                return withOwner(() => get(appeal.href, (status, raw) => {
                    if (status !== 200) return finish('unavailable');
                    const json = JSON.parse(raw);
                    const verified = Boolean(json && typeof json === 'object' && !Array.isArray(json) && json.error == null && (json.code === 0 || json.block_level === 0));
                    if (!verified) return finish('http', 200, '{"verified":false}');
                    withOwner(() => {
                        if (!live()) return finish('denied');
                        challenge = null;
                        finish('http', 200, '{"verified":true}');
                    });
                }));
            }
            const url = new URL(destination(op, history ? '' : id, query));
            url.search = query.toString();
            const read = () => get(url.href, (status, raw) => {
                if (history) {
                    return withHistoryOwner(() => {
                        if (status !== 200) return finish('http', status);
                        if (op === 'historySetting') {
                            const json = JSON.parse(raw);
                            check(!json.error && typeof json.disabled === 'boolean');
                            finish('http', status, JSON.stringify({disabled: json.disabled}));
                        } else {
                            finish('http', status, historyBody(raw, query));
                        }
                    });
                }
                if (status !== 200) return finish('http', status);
                if (!profile) return finish('http', status, publicBody(raw, op, id, query));
                const data = profileBody(JSON.parse(raw));
                if (!browserQrProfileMatches(data?.id ?? null)) { rememberProfile(null); return finish('expired'); }
                rememberProfile(data?.id ?? null);
                if (data === null) finish('guest'); else finish('http', status, JSON.stringify(data));
            });
            if (history) withHistoryOwner(read); else read();
        } catch { finish('denied'); }
    }
})();
