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

import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {test} from 'node:test';
import {runInNewContext} from 'node:vm';
import {MessageChannel} from 'node:worker_threads';

const userscript = await readFile(new URL('./zhihu-web-bridge.user.js', import.meta.url), 'utf8');
const engine = await readFile(new URL('../../shared-domain/src/wasmJsMain/kotlin/com/github/zly2006/zhihu/account/WebBridgeEngine.wasmJs.kt', import.meta.url), 'utf8');
const pageAdapter = engine.match(/"""(\(channel, op, owner, id, query, body, bytes, done\) => \{[\s\S]*?)"""/u)[1];
const openAdapter = engine.match(/"""(\(\) => \{[^]*?window\.dispatchEvent\(new Event\('zhihu\+\+\/verification\/open\/1'[^]*?)"""/u)[1];
const local = 'http://127.0.0.1:8080';
const pages = 'https://zly2006.github.io/zhihu-plus-plus/';
const id = '9007199254740993123';
const me = 'https://www.zhihu.com/api/v4/me';
const apiSelf = 'https://api.zhihu.com/people/self';
const profileA = '{"id":"account-A","name":"A"}';

function fixture({href = pages, confirm = true, cookies, clock = Date, top = true} = {}) {
    const calls = [], menus = [], prompts = [], alerts = [], listeners = new Map(), waiters = new Map();
    const navigator = {userActivation: {isActive: true}, locks: {request: (_name, _options, run) => run({})}};
    function fire(type, event) { for (const callback of listeners.get(type) || []) callback(event); }
    const window = {
        alert: text => { alerts.push(text); },
        confirm: text => { prompts.push(text); return typeof confirm === 'function' ? confirm(text) : confirm; },
        open: () => ({location: {replace() {}}}),
        dispatchEvent(e) { fire(e.type, e); return !e.defaultPrevented; },
        addEventListener(type, fn) { if (!listeners.has(type)) listeners.set(type, new Set()); listeners.get(type).add(fn); },
        removeEventListener(type, fn) { listeners.get(type)?.delete(fn); },
        postMessage(data, origin, ports) {
            assert.equal(origin, context.location.origin);
            assert.deepEqual(Object.keys(data), ['type']);
            assert.equal(data.type, 'zhihu++/bridge/1');
            assert.equal(ports.length, 1);
            const message = structuredClone({data, ports}, {transfer: ports});
            fire('message', {source: window, origin, isTrusted: true, ...message});
        },
    };
    window.top = top ? window : {};
    const context = {window, URL, navigator, Event, location: new URL(href), Date: clock,
        setTimeout: (fn, delay) => setTimeout(fn, delay).unref(), clearTimeout};
    runInNewContext(userscript, {
        ...context, URLSearchParams, TextEncoder, Uint8Array, Int32Array, ArrayBuffer, DataView, Blob, document: {addEventListener() {}}, GM_cookie: cookies, crypto: globalThis.crypto, atob, btoa,
        GM_info: {scriptHandler: 'Tampermonkey', version: '5.4'},
        GM_registerMenuCommand: (_label, fn) => menus.push(fn) - 1,
        GM_xmlhttpRequest(details) {
            const call = {details, load(body, status = 200) { details.onload({status, finalUrl: details.url, responseText: body}); }};
            calls.push(call);
            waiters.get(calls.length - 1)?.(call);
            waiters.delete(calls.length - 1);
            return {abort: () => details.onabort()};
        },
    });
    const start = runInNewContext('(' + pageAdapter + ')', {...context, MessageChannel});
    return {
        calls, menus, prompts, alerts, openVerification: runInNewContext('(' + openAdapter + ')', context),
        navigate(href) { context.location.href = new URL(href, context.location.href).href; },
        message(event) { fire('message', {source: window, origin: context.location.origin, isTrusted: true, ...event}); },
        activate(value) { navigator.userActivation.isActive = value; },
        callAt(index, pending) {
            return calls[index] ?? Promise.race([new Promise(resolve => waiters.set(index, resolve)),
                pending.result.then(({kind}) => assert.fail('Expected another request before ' + kind))]);
        },
        business(op, resource = '', body = '', query = '', bytes = null, owner = 'account-A') {
            const {promise, resolve} = Promise.withResolvers();
            const handle = start('business', op, owner, resource, query, body, bytes, (kind, status, response) => resolve({kind, status, body: response}));
            return {result: promise, cancel: () => handle.abort()};
        },
        call(op = 'profile', query = '', resource = '') {
            const {promise, resolve} = Promise.withResolvers();
            const history = op === 'history' || op === 'historySetting';
            const handle = start('read', op, history ? resource : '', history ? 'account-bound' : resource, query, '', null,
                (kind, status, body) => resolve({kind, status, body}));
            return {result: promise, cancel: () => handle.abort()};
        },
    };
}

async function verifyProfile(h, fromPage = false) {
    const index = h.calls.length, alerts = h.alerts.length;
    const grant = fromPage ? h.call('authorizeProfile') : (h.menus[0](), null);
    const proof = fromPage ? await h.callAt(index, grant) : h.calls[index];
    assert.equal(proof.details.url, me);
    assert.equal(proof.details.anonymous, false);
    assert.equal(JSON.parse((await h.call('bridgeStatus').result).body).permission.profile, 'checking');
    assert.equal((await h.call().result).kind, 'denied');
    assert.equal(h.alerts.length, alerts, 'authorization must wait for /me');
    proof.load(profileA);
    if (grant) assert.deepEqual(await grant.result, {kind: 'http', status: 200, body: '{"authorized":true}'});
    else assert.match(h.alerts.at(-1), /^Profile verified\./u);
    assert.equal(h.alerts.length, alerts + (fromPage ? 0 : 1));
    const status = await h.call('bridgeStatus').result;
    assert.equal(status.kind, 'http');
    assert.equal(JSON.parse(status.body).permission.profile, 'granted');
}

async function proveHistoryOwner(h, pending, index) {
    const proof = await h.callAt(index, pending);
    assert.equal(proof.details.url, me);
    assert.equal(proof.details.anonymous, false);
    proof.load(profileA);
    const api = await h.callAt(index + 1, pending);
    assert.equal(api.details.url, apiSelf);
    api.load(profileA);
}

async function startHistory(h, op, query) {
    const index = h.calls.length;
    const pending = h.call(op, query, 'account-A');
    await proveHistoryOwner(h, pending, index);
    await h.callAt(index + 2, pending);
    return pending;
}

function syntheticBusinessCookies() {
    return {
        list({url}, callback) { callback(['z_c0', 'd_c0', '_xsrf'].map(name => ({name, value: 'fixture-' + name, path: '/', domain: new URL(url).hostname, hostOnly: true, httpOnly: name === 'z_c0'}))); },
        set(_details, callback) { callback(); },
        delete(_details, callback) { callback(); },
    };
}

async function grantBusiness(h, operation) {
    const index = h.calls.length;
    const grant = h.call(operation);
    const proof = await h.callAt(index, grant);
    assert.equal(proof.details.url, me);
    proof.load(profileA);
    assert.equal((await grant.result).body, '{"authorized":true}');
}

async function proveBusiness(h, pending, index) {
    const website = await h.callAt(index, pending);
    assert.equal(website.details.url, me);
    assert.equal(website.details.anonymous, true);
    assert.match(website.details.cookie, /z_c0=fixture-z_c0/u);
    assert.equal(website.details.headers.Cookie, undefined);
    website.load(profileA);
    const api = await h.callAt(index + 1, pending);
    assert.equal(api.details.url, apiSelf);
    assert.equal(api.details.anonymous, true);
    api.load(profileA);
    return h.callAt(index + 2, pending);
}

test('full bridge capacity rejects both channels before acceptance without transferring private bytes', async () => {
    const h = fixture(), reservations = [];
    try {
        for (let index = 0; index < 20; index++) {
            const ports = new MessageChannel();
            reservations.push(ports);
            h.message({data: {type: 'zhihu++/bridge/1'}, ports: [ports.port2]});
        }
        assert.deepEqual(await h.call('history', '', 'fixture-owner').result, {kind: 'unavailable', status: 0, body: ''});
        const bytes = new ArrayBuffer(16);
        assert.deepEqual(await h.business('uploadVoice', '', '{"mime":"audio/mp4","durationMs":100}', '', bytes).result,
            {kind: 'unavailable', status: 0, body: ''});
        assert.equal(bytes.byteLength, 16, 'a rejected handshake must not transfer the request payload');
        assert.equal(h.calls.length, 0);
    } finally {
        for (const {port1, port2} of reservations) {
            port1.postMessage('cancel');
            port1.close();
            port2.close();
        }
    }
});

test('business writes require their own grant and never accept URL or header forwarding', async () => {
    const h = fixture({cookies: syntheticBusinessCookies()}); await verifyProfile(h);
    assert.equal((await h.business('vote', 'answers/42', '{"type":"up"}').result).kind, 'denied');
    await grantBusiness(h, 'authorizeInteractions');
    for (const [op, id, body] of [
        ['request', 'https://example.invalid', '{}'], ['vote', 'answers/42', '{"type":"up","headers":{"Cookie":"synthetic"}}'],
        ['commentDelete', '../42', ''], ['collectionCreate', '', '{"title":"x","description":"y","is_public":true,"url":"https://example.invalid"}'],
    ]) {
        const count = h.calls.length;
        assert.equal((await h.business(op, id, body).result).kind, 'denied');
        assert.equal(h.calls.length, count);
    }
    const index = h.calls.length, pending = h.business('vote', 'answers/42', '{"type":"up"}');
    const write = await proveBusiness(h, pending, index);
    assert.equal(write.details.url, 'https://www.zhihu.com/api/v4/answers/42/voters');
    assert.equal(write.details.method, 'POST');
    assert.equal(write.details.data, '{"type":"up"}');
    assert.equal(write.details.anonymous, true);
    assert.match(write.details.cookie, /z_c0=fixture-z_c0/u);
    assert.equal(write.details.headers.Cookie, undefined);
    assert.equal(write.details.headers['x-xsrftoken'], 'fixture-_xsrf');
    assert.match(write.details.headers['x-zse-96'], /^2[.]0_/u);
    write.load('{"voteup_count":3}');
    assert.deepEqual(await pending.result, {kind: 'http', status: 200, body: '{"voteup_count":3}'});
});

test('business ownership mismatch stops before writes and revoke cancels pending replies', async () => {
    const h = fixture({cookies: syntheticBusinessCookies()}); await verifyProfile(h); await grantBusiness(h, 'authorizeInteractions');
    const index = h.calls.length, pending = h.business('vote', 'answers/42', '{"type":"up"}');
    (await h.callAt(index, pending)).load('{"id":"account-B","name":"B"}');
    assert.equal((await pending.result).kind, 'denied');
    assert.equal(h.calls.length, index + 1);
    const nextIndex = h.calls.length, next = h.business('vote', 'answers/42', '{"type":"up"}');
    const write = await proveBusiness(h, next, nextIndex);
    h.menus[4]();
    write.load('{"voteup_count":5}');
    assert.equal((await next.result).kind, 'uncertain');
});

test('revoking a checking business grant prevents its late profile response from restoring permission', async () => {
    const h = fixture({cookies: syntheticBusinessCookies()}); await verifyProfile(h);
    const index = h.calls.length, pending = h.call('authorizeCreation');
    const proof = await h.callAt(index, pending);
    h.menus[4]();
    proof.load(profileA);
    assert.equal((await pending.result).kind, 'denied');
    const count = h.calls.length;
    assert.equal((await h.business('articleCreate', '', 'title=x&content=y&copyright=public').result).kind, 'denied');
    assert.equal(h.calls.length, count);
});

test('business response and upload errors never expose authentication fields', async () => {
    const h = fixture({cookies: syntheticBusinessCookies()}); await verifyProfile(h); await grantBusiness(h, 'authorizePrivate');
    for (const key of ['token', 'security_token', 'access_token', 'session', 'session_id', 'headers', 'response_headers']) {
        const index = h.calls.length, pending = h.business('messagesRead', '', '', 'limit=20');
        const read = await proveBusiness(h, pending, index);
        read.load(JSON.stringify({error: {nested: {[key]: 'synthetic'}}}), 400);
        assert.equal((await pending.result).kind, 'denied', key);
    }
});

test('a sent non-idempotent create that loses its response is uncertain, not safe to retry', async () => {
    const h = fixture({cookies: syntheticBusinessCookies()}); await verifyProfile(h); await grantBusiness(h, 'authorizeCreation');
    const index = h.calls.length, pending = h.business('articleCreate', '', 'title=x&content=y&copyright=public');
    const write = await proveBusiness(h, pending, index);
    assert.equal(write.details.method, 'POST');
    write.details.onerror();
    assert.equal((await pending.result).kind, 'uncertain');
    assert.equal(h.calls.length, index + 3, 'the bridge does not retry a write');
});

test('a submitted write remains uncertain after the entry path changes', async () => {
    const h = fixture({cookies: syntheticBusinessCookies()}); await verifyProfile(h); await grantBusiness(h, 'authorizeCreation');
    const index = h.calls.length, pending = h.business('articleCreate', '', 'title=x&content=y&copyright=public');
    const write = await proveBusiness(h, pending, index);
    h.navigate(pages + 'other/');
    write.load('{"id":42}');
    assert.equal((await pending.result).kind, 'uncertain');
});

test('a failed media upload cannot disclose its temporary credentials', async () => {
    const h = fixture({cookies: syntheticBusinessCookies()}); await verifyProfile(h); await grantBusiness(h, 'authorizeCreation');
    const index = h.calls.length, pending = h.business('uploadVoice', '', '{"mime":"audio/mp4","durationMs":100}', '', new ArrayBuffer(16));
    const upload = await proveBusiness(h, pending, index);
    assert.equal(upload.details.url, 'https://lens.zhihu.com/api/v4/videos');
    assert.equal(upload.details.headers['X-Upload-Conent-Type'], 'audio/mp4');
    upload.load('{"error":{"access_key":"synthetic","security_token":"synthetic"}}', 400);
    assert.equal((await pending.result).kind, 'uncertain');
});

test('notification reads use the mobile contract without marking notifications read', async () => {
    const h = fixture({cookies: syntheticBusinessCookies()}); await verifyProfile(h); await grantBusiness(h, 'authorizePrivate');
    const index = h.calls.length, pending = h.business('notificationTimeline', 'invite', '', 'limit=20&invite_with_time_slice=1');
    const read = await proveBusiness(h, pending, index);
    assert.equal(read.details.url, 'https://api.zhihu.com/notifications/v3/timeline/entry/invite?limit=20&invite_with_time_slice=1');
    assert.equal(read.details.method, 'GET');
    assert.equal(read.details.headers['x-api-version'], '3.1.8');
    read.load('{"data":[],"paging":{"is_end":true,"next":""}}');
    assert.equal((await pending.result).kind, 'http');
    assert.equal(h.calls.filter(call => call.details.method !== 'GET').length, 0);
});

test('upload byte budgets and scopes are checked before any request', async () => {
    const h = fixture({cookies: syntheticBusinessCookies()}); await verifyProfile(h); await grantBusiness(h, 'authorizeCreation');
    for (const bytes of [null, new ArrayBuffer(0), new ArrayBuffer(32 * 1024 * 1024 + 1)]) {
        const count = h.calls.length;
        assert.equal((await h.business('uploadVoice', '', '{"mime":"audio/mp4","durationMs":100}', '', bytes).result).kind, 'denied');
        assert.equal(h.calls.length, count);
    }
});

test('only exact top-level entries are trusted, including during consent and pending replies', async () => {
    for (const href of [local, local + '/index.html', pages, pages + 'index.html',
        local + '/?entry=local#/settings', pages + '?entry=pages#/answer/42']) {
        const h = fixture({href}), page = new URL(href);
        assert.equal(h.menus.length, 9, href);
        assert.equal((await h.call().result).kind, 'denied', href);
        assert.equal(h.calls.length, 0);
        await verifyProfile(h, true);
        assert.ok(h.prompts[0].includes(page.origin + page.pathname));
        const read = h.call();
        const request = await h.callAt(1, read);
        h.navigate('#/settings/appearance');
        request.load(profileA);
        assert.equal((await read.result).body, profileA, 'hash navigation keeps the same entry');
    }
    for (const options of [
        {href: 'http://127.0.0.1:8081'}, {href: 'http://localhost:8080'}, {href: local + '/other'},
        {href: 'http://user@127.0.0.1:8080/'}, {href: 'https://user:password@zly2006.github.io/zhihu-plus-plus/'},
        {href: 'http://zly2006.github.io/zhihu-plus-plus/'}, {href: 'https://zly2006.github.io:8443/zhihu-plus-plus/'},
        {href: 'https://zly2006.github.io/'}, {href: 'https://zly2006.github.io/other-project/'},
        {href: pages.slice(0, -1)}, {href: pages + 'nested/'}, {href: pages + 'index.html/extra'},
        {href: pages, top: false}, {href: local, top: false},
    ]) {
        const h = fixture(options);
        assert.equal(h.menus.length, 0, options.href);
        assert.equal((await h.call().result).kind, 'denied', options.href);
        assert.equal(h.openVerification(), false);
        assert.equal(h.calls.length, 0);
    }
    for (const href of [local + '/', pages]) {
        const h = fixture({href}), port = {postMessage() { assert.fail('untrusted event accepted'); }};
        const data = {type: 'zhihu++/bridge/1'};
        for (const event of [{origin: 'https://other.invalid'}, {source: {}}, {isTrusted: false}, {ports: []}, {ports: [port, port]}]) {
            h.message({data, ports: [port], ...event});
        }
        assert.equal(h.calls.length, 0);
        for (const next of [href + 'index.html', href + 'other/']) {
            const grant = fixture({href, confirm: () => { grant.navigate(next); return true; }});
            assert.deepEqual(await grant.call('authorizeProfile').result, {kind: 'denied', status: 0, body: ''});
            grant.navigate(href);
            assert.equal((await grant.call().result).kind, 'denied', 'changed-path consent must not grant profile access');
            assert.equal(grant.calls.length, 0);

            const pending = fixture({href}); await verifyProfile(pending);
            const read = pending.call();
            const request = await pending.callAt(1, read);
            pending.navigate(next);
            request.load(profileA);
            assert.deepEqual(await read.result, {kind: 'denied', status: 0, body: ''});

            const queued = fixture({href}); await verifyProfile(queued);
            const reply = queued.call();
            (await queued.callAt(1, reply)).load(profileA);
            queued.navigate(next);
            assert.deepEqual(await reply.result, {kind: 'denied', status: 0, body: ''}, 'page adapter rechecks already queued replies');
        }
    }
});

test('a refused profile grant releases nothing; a granted read keeps credentials off the port', async () => {
    const h = fixture({confirm: false});
    assert.deepEqual(JSON.parse((await h.call('authorizeProfile').result).body), {authorized: false});
    assert.equal((await h.call().result).kind, 'denied');
    assert.equal(h.calls.length, 0);
    await verifyProfile(h);
    const pending = h.call();
    const {details, load} = await h.callAt(1, pending);
    assert.equal(details.url, me);
    assert.equal(details.method, 'GET');
    assert.equal(details.anonymous, false);
    assert.equal(details.redirect, 'manual');
    load('{"id":"fixture","name":"测试","email":"private","access_token":"synthetic"}');
    const result = await pending.result;
    assert.equal(result.kind, 'http');
    assert.deepEqual(JSON.parse(result.body), {id: 'fixture', name: '测试'});
});

test('a public answer stays anonymous under a profile grant and cannot return a nested refresh token', async () => {
    const h = fixture(); await verifyProfile(h);
    const answer = h.call('answer', '', id);
    const call = await h.callAt(1, answer);
    assert.equal(call.details.anonymous, true);
    assert.equal(call.details.url, 'https://www.zhihu.com/api/v4/answers/' + id);
    call.load('{"nested":{"refresh_token":"synthetic"}}');
    assert.deepEqual(await answer.result, {kind: 'denied', status: 0, body: ''});
});

test('opening a challenge cannot clear it until the official appeal confirms it', async () => {
    const h = fixture(), challengeUrl = 'https://www.zhihu.com/account/unhuman?session=fixture';
    const answer = h.call('answer', '', '42');
    (await h.callAt(0, answer)).load(JSON.stringify({error: {code: 40352, redirect: challengeUrl}}), 403);
    assert.deepEqual(await answer.result, {kind: 'challenge', status: 0, body: ''});
    assert.ok(h.openVerification());
    const check = h.call('verificationCheck'), appeal = await h.callAt(1, check);
    assert.equal(appeal.details.url, 'https://www.zhihu.com/api/v4/anticrawl/new_captcha_appeal?session=fixture');
    assert.equal(appeal.details.headers.Referer, challengeUrl);
    appeal.load('{"block_level":1}');
    assert.deepEqual(JSON.parse((await check.result).body), {verified: false});
    const retry = h.call('verificationCheck');
    (await h.callAt(2, retry)).load('{"block_level":0}');
    assert.deepEqual(JSON.parse((await retry.result).body), {verified: true});
    assert.equal(h.calls.length, 3, 'verification cannot replay the failed request');
});

test('a verified profile alone cannot read history; a separate grant is required', async () => {
    const h = fixture(); await verifyProfile(h);
    assert.equal((await h.call('historySetting', '', 'account-A').result).kind, 'denied');
    assert.equal(h.calls.length, 1);
    h.menus[2]();
    const read = await startHistory(h, 'historySetting', '');
    const proofIndex = h.calls.length;
    h.calls.at(-1).load('{"disabled":false,"internal":"no"}');
    await proveHistoryOwner(h, read, proofIndex);
    assert.deepEqual(JSON.parse((await read.result).body), {disabled: false});
});

test('switching from A to B during a history read expires A’s grant without releasing B’s data', async () => {
    const h = fixture();
    await verifyProfile(h); h.menus[2]();
    const p = await startHistory(h, 'history', 'offset=0&limit=10');
    assert.equal(h.calls.at(-1).details.url, 'https://api.zhihu.com/unify-consumption/read_history?offset=0&limit=10');
    const proofIndex = h.calls.length;
    h.calls.at(-1).load(JSON.stringify({data: [{card_type: 'read_history', data: {header: {title: 'B private history'},
        action: {type: 'router', url: `zhihu://answers/${id}`}, extra: {content_token: id, content_type: 'answer', read_time: 1}}}],
        paging: {is_end: true, next: ''}}));
    const proof = await h.callAt(proofIndex, p);
    assert.equal(proof.details.url, me);
    const profileB = '{"id":"account-B","name":"B private profile"}';
    proof.load(profileB);
    assert.deepEqual(await p.result, {kind: 'expired', status: 0, body: ''});
    const count = h.calls.length;
    h.menus[2]();
    assert.equal((await h.call('historySetting', '', 'account-A').result).kind, 'denied');
    assert.equal(h.calls.length, count, 'a new menu click cannot reuse A’s stale profile');
    const refresh = h.call(); (await h.callAt(count, refresh)).load(profileB); await refresh.result;
    assert.equal((await h.call('historySetting', '', 'account-B').result).kind, 'denied');
    h.menus[2]();
    assert.equal((await h.call('historySetting', '', 'account-A').result).kind, 'expired');
    assert.equal(h.calls.length, count + 1, 'A’s old client cannot use B’s new grant');
});

test('the API host must authenticate the same history owner as the website', async () => {
    const h = fixture(); await verifyProfile(h); h.menus[2]();
    const pending = await startHistory(h, 'historySetting', '');
    const proofIndex = h.calls.length;
    h.calls.at(-1).load('{"disabled":false}');
    const website = await h.callAt(proofIndex, pending);
    assert.equal(website.details.url, me); website.load(profileA);
    const api = await h.callAt(proofIndex + 1, pending);
    assert.equal(api.details.url, apiSelf); api.load('{"id":"other-account","name":"B"}');
    assert.deepEqual(await pending.result, {kind:'expired',status:0,body:''});
    assert.equal((await h.call('historySetting', '', 'account-A').result).kind, 'denied');
});

const qrEndpoint = 'https://www.zhihu.com/api/v3/account/api/login/qrcode';
const signin = 'https://www.zhihu.com/signin';
const pollKey = 'fixture-private-poll-123', display = 'https://www.zhihu.com/account/scan/login/fixture-phone-456';
const qrCreated = {link: display, token: pollKey, expires_at: 90};
const issued = 'fixture.browser.ticket';
const confirmedQr = {success: true, user_id: 'account-A', cookie: `ignored=a; z_c0=${issued}; Domain=bad.invalid; Path=/private`, access_token: 'not-for-page'};

async function qrRequest(h, pending, index, method, url, anonymous) {
    const call = await h.callAt(index, pending);
    assert.equal(call.details.method, method); assert.equal(call.details.url, url); assert.equal(call.details.anonymous, anonymous);
    return call;
}
async function qrMe(h, pending, index, profile) {
    (await qrRequest(h, pending, index, 'GET', me, false)).load(profile ?? '{"error":{"code":100,"message":"ERR_TICKET_NOT_EXIST"}}', profile ? 200 : 401);
}
async function startQr(h, data = qrCreated) {
    const index = h.calls.length, start = h.call('loginQrStart');
    await qrMe(h, start, index, null);
    (await qrRequest(h, start, index + 1, 'POST', qrEndpoint, true)).load(JSON.stringify(data));
    return start.result;
}
async function pollQr(h, info) {
    const index = h.calls.length, poll = h.call('loginQrPoll');
    (await qrRequest(h, poll, index, 'GET', qrEndpoint + '/' + pollKey + '/scan_info', true)).load(JSON.stringify(info));
    return poll.result;
}
async function confirmed(h) {
    assert.deepEqual(JSON.parse((await startQr(h)).body), {state: 'ready', link: display});
    assert.deepEqual(JSON.parse((await pollQr(h, confirmedQr)).body), {state: 'confirmed'});
}
function qrCookieJar({existing = [], attributes = {}, realAttributes = {}, set} = {}) {
    const writes = [], records = new Map();
    return {writes, api: {
        list({url, name}, done) {
            assert.equal(url, signin);
            done(records.has(name) ? [records.get(name)] : name === 'z_c0' ? existing : []);
        },
        set(details, done) {
            const real = details.name === 'z_c0';
            records.set(details.name, {...details, domain: 'www.zhihu.com', hostOnly: true, ...(real ? realAttributes : attributes)});
            if (real) {
                writes.push({...details});
                if (set) return set(done);
            }
            done();
        },
        delete({name}, done) {
            assert.notEqual(name, 'z_c0');
            records.delete(name); done();
        },
    }};
}

test('QR: scanning is not confirmation, even if the scan response contains a ticket', async () => {
    const h = fixture();
    assert.deepEqual(JSON.parse((await startQr(h)).body), {state: 'ready', link: display});
    assert.deepEqual(JSON.parse((await pollQr(h, {...confirmedQr, status: 1})).body), {state: 'scanned'});
    const count = h.calls.length;
    assert.equal((await h.call('cookieSignIn').result).kind, 'denied');
    assert.equal((await h.call('profile').result).kind, 'denied');
    assert.equal(h.calls.length, count);
});

test('QR: an official display link containing the private polling token never reaches the page', async () => {
    const h = fixture();
    const link = display.replace('fixture-phone-456', 'prefix_' + pollKey + '_suffix');
    assert.deepEqual(JSON.parse((await startQr(h, {...qrCreated, link})).body), {state: 'official'});
    assert.equal((await h.call('loginQrPoll').result).kind, 'denied');
    assert.equal(h.calls.length, 2);
});

test('QR: only a scoped HttpOnly write followed by a matching official profile authorizes the account', async () => {
    const jar = qrCookieJar(), h = fixture({cookies: jar.api});
    await confirmed(h);
    h.activate(false);
    assert.deepEqual(await h.call('cookieSignIn').result, {kind: 'unsupported', status: 0, body: 'gesture-required'});
    h.activate(true);
    const index = h.calls.length, commit = h.call('cookieSignIn');
    await qrMe(h, commit, index, null);
    await h.callAt(index + 1, commit);
    assert.deepEqual(jar.writes, [{url: signin, name: 'z_c0', value: issued, path: '/', secure: true, httpOnly: true}]);
    assert.equal((await h.call('profile').result).kind, 'denied');
    await qrMe(h, commit, index + 1, profileA);
    assert.deepEqual(await commit.result, {kind: 'http', status: 200, body: '{"authorized":true}'});
    const profileIndex = h.calls.length, profile = h.call();
    await qrMe(h, profile, profileIndex, profileA);
    assert.equal((await profile.result).body, profileA);
    const switchIndex = h.calls.length, switched = h.call();
    await qrMe(h, switched, switchIndex, '{"id":"account-B","name":"B"}');
    assert.equal((await switched.result).kind, 'expired');
});

test('QR: an existing session cookie is never overwritten by the confirmed ticket', async () => {
    const jar = qrCookieJar({existing: [{name: 'z_c0', value: 'other-sign-in'}]}), h = fixture({cookies: jar.api});
    await confirmed(h);
    const index = h.calls.length, commit = h.call('cookieSignIn');
    await qrMe(h, commit, index, null);
    assert.equal((await commit.result).kind, 'denied');
    assert.equal(jar.writes.length, 0);
    assert.equal((await h.call('profile').result).kind, 'denied');
});

test('QR: a manager that silently drops HttpOnly on the probe never receives the real ticket', async () => {
    const jar = qrCookieJar({attributes: {httpOnly: false}}), h = fixture({cookies: jar.api});
    await confirmed(h);
    const index = h.calls.length, commit = h.call('cookieSignIn');
    await qrMe(h, commit, index, null);
    assert.deepEqual(await commit.result, {kind: 'unsupported', status: 0, body: 'cookie-attributes-unsupported'});
    assert.equal(jar.writes.length, 0);
    assert.equal((await h.call('profile').result).kind, 'denied');
});

test('QR: an insecure real cookie readback cannot authorize a profile after a successful probe', async () => {
    const jar = qrCookieJar({realAttributes: {httpOnly: false}}), h = fixture({cookies: jar.api});
    await confirmed(h);
    const index = h.calls.length, commit = h.call('cookieSignIn');
    await qrMe(h, commit, index, null);
    assert.equal((await commit.result).kind, 'unavailable');
    assert.equal(jar.writes.length, 1);
    assert.equal(h.calls.length, index + 1);
    assert.equal((await h.call('profile').result).kind, 'denied');
});

test('QR: a different account returned by the post-write /me does not get profile access', async () => {
    const jar = qrCookieJar(), h = fixture({cookies: jar.api});
    await confirmed(h);
    const index = h.calls.length, commit = h.call('cookieSignIn');
    await qrMe(h, commit, index, null);
    await qrMe(h, commit, index + 1, '{"id":"account-B","name":"B"}');
    assert.equal((await commit.result).kind, 'denied');
    assert.equal(jar.writes.length, 1);
    assert.equal((await h.call('profile').result).kind, 'denied');
});

test('QR: an expired write callback cannot publish a candidate through an earlier profile grant', async () => {
    let now = Date.now();
    const write = Promise.withResolvers();
    const jar = qrCookieJar({set: write.resolve}), h = fixture({cookies: jar.api, clock: {now: () => now}});
    await verifyProfile(h);
    await confirmed(h);
    const index = h.calls.length, commit = h.call('cookieSignIn');
    await qrMe(h, commit, index, null);
    const release = await Promise.race([write.promise, commit.result.then(({kind}) => assert.fail('Expected cookie write before ' + kind))]);
    const racingIndex = h.calls.length, racing = h.call('profile');
    await qrMe(h, racing, racingIndex, profileA);
    assert.equal((await racing.result).kind, 'expired');
    const count = h.calls.length;
    now += qrCreated.expires_at * 1000;
    release();
    assert.deepEqual(await commit.result, {kind: 'expired', status: 0, body: ''});
    assert.equal(h.calls.length, count);
    const late = h.call('profile'); await qrMe(h, late, count, profileA);
    assert.equal((await late.result).kind, 'expired');
});
