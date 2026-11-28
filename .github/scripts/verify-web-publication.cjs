// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
require('../../webApp/src/wasmJsMain/resources/zhihu-app-cache.js');

const site = 'https://zly2006.github.io/zhihu-plus-plus/';
const store = new globalThis.ZhihuAppCacheStore(site);

function readPointer(bytes) {
    if (!bytes.length || bytes.length > 2048) throw new Error('Invalid web-version.json size');
    return JSON.parse(new TextDecoder('utf-8', {fatal: true}).decode(bytes));
}

function checkPublication(current, published) {
    store.validatePointer(current);
    // The previous publisher emitted only the bundle buildId and had no numbered resource directory.
    if (published === null || (Object.keys(published).length === 1 && /^[0-9a-f]{16,64}$/.test(published.buildId))) return;
    store.validatePointer(published);
    const order = store.compare(current, published);
    if (order > 0) return;
    if (order < 0 || current.versionName !== published.versionName) throw new Error('Refusing a Web release downgrade or unordered version.');
    if (current.sha256 !== published.sha256 || current.buildId !== published.buildId) {
        throw new Error('Published Web version already exists with different resources. Bump the app version before deployment.');
    }
}

async function publishedPointer() {
    const response = await fetch(new URL('web-version.json', site), {
        cache: 'no-store', credentials: 'omit', redirect: 'error', signal: AbortSignal.timeout(15000),
    });
    if (response.status === 404) return null;
    if (response.status !== 200 || !response.body || Number(response.headers.get('content-length')) > 2048) {
        throw new Error(`Could not verify the currently published Web version (HTTP ${response.status}).`);
    }
    const reader = response.body.getReader();
    const bytes = Buffer.alloc(2048);
    let length = 0;
    try {
        for (;;) {
            const part = await reader.read();
            if (part.done) break;
            if (length + part.value.length > bytes.length) throw new Error('Published web-version.json exceeds size limit');
            bytes.set(part.value, length);
            length += part.value.length;
        }
    } finally {
        await reader.cancel();
        reader.releaseLock();
    }
    const result = readPointer(bytes.subarray(0, length));
    if (!result || typeof result !== 'object' || Array.isArray(result)) throw new Error('Invalid published version pointer');
    return result;
}

async function main() {
    const arg = process.argv[2];
    if (arg === '--self-check') {
        const version = n => ({schema: 1, versionCode: n, versionName: `${n}.0`, buildId: 'a'.repeat(20),
            manifest: `app-resources/${n}-${n}.0/app-assets.json`, sha256: 'b'.repeat(64)});
        checkPublication(version(2), null);
        checkPublication(version(2), {buildId: 'c'.repeat(20)});
        checkPublication(version(2), version(1));
        checkPublication(version(2), version(2));
        assert.throws(() => checkPublication(version(2), {...version(2), sha256: 'd'.repeat(64)}));
        assert.throws(() => checkPublication(version(1), version(2)));
        assert.throws(() => checkPublication(version(1), {}));
        assert.throws(() => readPointer(Buffer.alloc(2049)));
        console.log('Web publication checks passed');
        return;
    }
    if (!arg || process.argv.length !== 3) throw new Error('Expected the local web-version.json path');
    checkPublication(readPointer(fs.readFileSync(arg)), await publishedPointer());
}

main().catch(error => { console.error(error.message); process.exitCode = 1; });
