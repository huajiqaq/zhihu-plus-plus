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

@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.github.zly2006.zhihu.viewmodel

import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.navDestination
import com.github.zly2006.zhihu.i18n.AppLocale
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.platform.WebImageOperation
import com.github.zly2006.zhihu.platform.awaitWebImageOperation
import com.github.zly2006.zhihu.platform.webImageRuntime
import com.github.zly2006.zhihu.util.AppLog
import com.github.zly2006.zhihu.util.buildArticleExportFileName
import com.github.zly2006.zhihu.util.buildCollectionExportZipFileName
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.JsAny
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.suspendCancellableCoroutine

internal suspend fun ZhihuApiEnvironment.exportWebCollectionZip(
    collectionTitle: String,
    items: List<CollectionItem>,
    includeImages: Boolean,
    onProgress: suspend (CollectionHtmlExportProgress) -> Unit,
): CollectionHtmlExportResult {
    val archive = createWebCollectionZip(webImageRuntime, AppLocale.languageTag)
    var success = 0
    var skipped = 0
    var failed = 0
    var processed = 0
    var linked = 0
    var template: String? = null
    suspend fun progress(title: String) = onProgress(CollectionHtmlExportProgress(items.size, processed, success, skipped, failed, title))
    try {
        progress("")
        for ((index, item) in items.withIndex()) {
            ensureExportAccountActive()
            val title = item.content.title.ifBlank { item.content.description() }
            progress(title)
            val target = item.content.navDestination as? Article
            if (target == null) {
                skipped++
            } else {
                val resource = template ?: loadWebExportTemplate().also { template = it }
                val entry = try {
                    val content = getOrFetchContentDetail(target) ?: error(DomainStrings.itemDetailError(title))
                    if (content is DataHolder.Answer || content is DataHolder.Article) {
                        val (html, externalImages) = buildWebExportHtml(resource, content, includeAppAttribution = true, includeImages = includeImages)
                        // 加上视图顺序，重复收藏同一条目也不会覆盖同名 HTML。
                        Triple("${index + 1}_${buildArticleExportFileName(content, "html")}", html, externalImages)
                    } else {
                        skipped++
                        null
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    failed++
                    AppLog.e("CollectionContentViewModel", "Failed to prepare collection HTML entry ${index + 1}", error)
                    null
                }
                if (entry != null) {
                    ensureExportAccountActive()
                    // 压缩器失败后不能在损坏的 ZIP 后继续写；这种失败属于整个交付，不当作单篇跳过。
                    suspendCancellableCoroutine<Unit> { continuation ->
                        continuation.invokeOnCancellation { archive.close() }
                        if (continuation.isActive) archive.add(entry.first, entry.second) { error ->
                            if (continuation.isActive) {
                                if (error.isEmpty()) continuation.resume(Unit) else continuation.resumeWithException(IllegalStateException(error))
                            }
                        }
                    }
                    success++
                    linked += entry.third
                }
            }
            processed++
            progress(title)
        }
        ensureExportAccountActive()
        val delivery = if (success > 0) {
            val job = currentCoroutineContext().job
            awaitWebImageOperation { done ->
                archive.deliver(buildCollectionExportZipFileName(collectionTitle), includeImages, linked, { job.isActive }, done)
            }
        } else {
            null
        }
        return CollectionHtmlExportResult(items.size, success, skipped, failed, zipFilePath = null, delivery = delivery)
    } finally {
        archive.close()
    }
}

private external interface WebCollectionZip : JsAny {
    fun add(name: String, html: String, done: (String) -> Unit)

    fun deliver(name: String, includeImages: Boolean, linked: Int, isActive: () -> Boolean, done: (Int, String) -> Unit): WebImageOperation

    fun close()
}

@JsFun(
    """(runtime, language) => {
        const control = new AbortController();
        const signal = control.signal;
        const t = (zh, en) => runtime.text(language, zh, en);
        const parts = [];
        const encoder = new TextEncoder();
        let module, zip, failure, finished = false, size = 0, entries = 0;
        const close = () => {
            control.abort();
            if (zip) { zip.terminate(); zip = null; }
            parts.length = 0;
        };
        const add = (name, html, done) => {
            (async () => {
                try {
                    signal.throwIfAborted();
                    if (!module) {
                        try { module = await runtime.wait(import('fflate'), signal); } catch { throw new Error('assets'); }
                        zip = new module.Zip((error, bytes, final) => {
                            if (error) { failure = error; return; }
                            if (failure || signal.aborted) return;
                            size += bytes.byteLength;
                            if (size > 256 * 1024 * 1024) { failure = new Error('size'); return; }
                            if (bytes.byteLength) parts.push(new Blob([bytes]));
                            finished = final;
                        });
                    }
                    // fflate streaming writer emits classic ZIP, not ZIP64. Never wrap entry counts or publish partial archives.
                    if (entries >= 65534 || html.length > 96 * 1024 * 1024) throw new Error('size');
                    if (/[\/\u0000-\u001f\u007f]/.test(name) || encoder.encode(name).length > 65535) throw new Error('name');
                    const compressor = new module.ZipDeflate(name, {level: 4});
                    // Zip retains its input metadata until end(); do not retain a Deflate buffer for every completed article.
                    const metadata = {filename: name, crc: 0, size: 0, compression: compressor.compression, flag: compressor.flag};
                    zip.add(metadata);
                    compressor.ondata = (error, bytes, final) => {
                        if (final) { metadata.crc = compressor.crc; metadata.size = compressor.size; }
                        metadata.ondata(error, bytes, final);
                    };
                    for (let start = 0; start < html.length;) {
                        signal.throwIfAborted();
                        let end = Math.min(start + 65536, html.length);
                        const last = html.charCodeAt(end - 1);
                        if (end < html.length && last >= 0xd800 && last <= 0xdbff) end--;
                        compressor.push(encoder.encode(html.slice(start, end)), end === html.length);
                        if (failure) throw failure;
                        start = end;
                        if (start < html.length) await runtime.wait(new Promise(resolve => setTimeout(resolve, 0)), signal);
                    }
                    if (!html.length) compressor.push(new Uint8Array(0), true);
                    if (failure) throw failure;
                    entries++;
                    done('');
                } catch (error) {
                    close();
                    done(error && error.message === 'size' ? t('ZIP 超出浏览器安全归档预算；未下载不完整文件。',
                        'The ZIP exceeds the safe browser archive budget. No incomplete file was downloaded.') :
                        t('ZIP 压缩组件或条目写入失败；未下载不完整文件。', 'ZIP compression or entry writing failed. No incomplete file was downloaded.'));
                }
            })();
        };
        const deliver = (name, includeImages, linked, isActive, done) => {
            const panel = runtime.panel(language, control, 'zip', isActive);
            (async () => {
                try {
                    signal.throwIfAborted();
                    zip.end();
                    if (failure || !entries || !finished || !size) throw new Error('encode');
                    zip.terminate(); zip = null;
                    const blob = new Blob(parts, {type: 'application/zip'});
                    parts.length = 0;
                    const note = !includeImages ? t('按所选设置，文中图片保留在线链接。', 'As selected, images are kept as online links.') :
                        linked ? t('仍需联网的图片链接：', 'Image links that still require internet: ') + linked : '';
                    panel.ready(blob, name, note);
                    const result = await panel.result;
                    done(result.cancelled ? 0 : 1, result.message);
                } catch {
                    if (signal.aborted) done(0, '');
                    else done(-1, t('ZIP 未能完整生成，未发起下载。', 'The ZIP could not be finalized. No download was requested.'));
                } finally { panel.dispose(); }
            })();
            return {abort: close};
        };
        return {add, deliver, close};
    }""",
)
private external fun createWebCollectionZip(runtime: JsAny, language: String): WebCollectionZip
