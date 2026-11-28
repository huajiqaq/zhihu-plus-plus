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
import com.github.zly2006.zhihu.i18n.AppLocale
import com.github.zly2006.zhihu.platform.WebImageOperation
import com.github.zly2006.zhihu.platform.awaitWebImageOperation
import com.github.zly2006.zhihu.platform.webImageRuntime
import com.github.zly2006.zhihu.util.buildArticleExportFileName
import kotlin.js.JsAny
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job

internal suspend fun exportWebArticleImage(
    content: DataHolder.Content,
    includeAppAttribution: Boolean,
    extraSectionsHtml: String,
): ArticleExportResult {
    val html = buildWebRenderHtml(content, includeAppAttribution, extraSectionsHtml)
    ensureExportAccountActive()
    val job = currentCoroutineContext().job
    return awaitWebImageOperation { done ->
        renderWebArticleImage(
            runtime = webImageRuntime,
            prepareDocument = webExportDocumentFactory,
            html = html,
            fileName = buildArticleExportFileName(content, "png"),
            language = AppLocale.languageTag,
            isActive = { job.isActive },
            done = done,
        )
    }
}

/** 分条 Canvas 绘制及连续 zlib/IDAT 编码；从未创建正文全高 Canvas，也不把长图悄悄拆成多张。 */
@JsFun(
    """(runtime, prepareDocument, html, fileName, language, isActive, done) => {
        const controller = new AbortController();
        const signal = controller.signal;
        const panel = runtime.panel(language, controller, 'image', isActive);
        const t = (zh, en) => runtime.text(language, zh, en);
        let prepared;
        let writer;
        let drain;
        const check = () => signal.throwIfAborted();
        const yieldToBrowser = () => runtime.wait(new Promise(resolve => setTimeout(resolve, 0)), signal);
        const crcTable = new Uint32Array(256);
        for (let n = 0; n < 256; n++) {
            let c = n;
            for (let bit = 0; bit < 8; bit++) c = (c & 1) ? (0xedb88320 ^ (c >>> 1)) : (c >>> 1);
            crcTable[n] = c >>> 0;
        }
        const pngChunk = (type, bytes) => {
            const chunk = new Uint8Array(bytes.byteLength + 12);
            const view = new DataView(chunk.buffer);
            view.setUint32(0, bytes.byteLength);
            for (let index = 0; index < 4; index++) chunk[4 + index] = type.charCodeAt(index);
            chunk.set(bytes, 8);
            let crc = 0xffffffff;
            for (let index = 4; index < chunk.length - 4; index++) crc = crcTable[(crc ^ chunk[index]) & 255] ^ (crc >>> 8);
            view.setUint32(chunk.length - 4, (crc ^ 0xffffffff) >>> 0);
            return chunk;
        };
        const encode = async (doc, render) => {
            if (typeof CompressionStream !== 'function') throw new Error('compression');
            const scale = 2;
            const page = doc.querySelector('.page');
            const pageBounds = page.getBoundingClientRect();
            const view = doc.defaultView;
            const bodyStyle = view.getComputedStyle(doc.body);
            // 根 scrollHeight 至少是视口高；按 .page 边界及其实际滚动溢出测量，body 的外圈 padding 只计一次。
            const widthCss = Math.ceil(Math.max(752, view.scrollX + Math.max(
                pageBounds.right + parseFloat(bodyStyle.paddingRight), pageBounds.left + page.clientLeft + page.scrollWidth
            )));
            const heightCss = Math.ceil(view.scrollY + Math.max(
                pageBounds.bottom + parseFloat(bodyStyle.paddingBottom), pageBounds.top + page.clientTop + page.scrollHeight
            ));
            const width = Math.ceil(widthCss * scale);
            const height = Math.ceil(heightCss * scale);
            if (!Number.isSafeInteger(width) || !Number.isSafeInteger(height) || width < 1 || height < 1 ||
                width > 8192 || heightCss >= 16000000 || height > 0x7fffffff) throw new Error('layout');
            const header = new Uint8Array(13);
            const headerView = new DataView(header.buffer);
            headerView.setUint32(0, width); headerView.setUint32(4, height);
            header[8] = 8; header[9] = 6;
            // CSS 96 dpi 的 2 倍绘制，pHYs 与真实像素比例一致。
            const physical = new Uint8Array(9);
            const physicalView = new DataView(physical.buffer);
            physicalView.setUint32(0, 7559); physicalView.setUint32(4, 7559); physical[8] = 1;
            const chunks = [new Uint8Array([137,80,78,71,13,10,26,10]), pngChunk('IHDR', header), pngChunk('pHYs', physical)];
            let compressedSize = 0;
            let compression;
            try { compression = new CompressionStream('deflate'); } catch { throw new Error('compression'); }
            writer = compression.writable.getWriter();
            const reader = compression.readable.getReader();
            let drainError;
            // 所有条带共用一个 zlib 流；IDAT 分块不是独立 PNG 或独立 deflate 流。
            drain = (async () => {
                try {
                    while (true) {
                        const value = await reader.read();
                        if (value.done) break;
                        compressedSize += value.value.byteLength + 12;
                        if (compressedSize > 268435456) throw new Error('size');
                        // 及时移交不可变 Blob，让最终文件组合不再复制所有压缩字节数组。
                        chunks.push(new Blob([pngChunk('IDAT', value.value)]));
                    }
                } catch (error) { drainError = error; await reader.cancel(error).catch(() => {}); }
                finally { reader.releaseLock(); }
            })();
            const rowsPerTile = Math.max(1, Math.min(1024, Math.floor(2000000 / width)));
            let writtenRows = 0;
            for (let y = 0; y < height; y += rowsPerTile) {
                check();
                if (drainError) throw drainError;
                const rows = Math.min(rowsPerTile, height - y);
                panel.status(t('正在绘制完整长图 ', 'Rendering the complete image ') + Math.floor(y * 100 / height) + '%');
                const canvas = document.createElement('canvas');
                try {
                    canvas.width = width; canvas.height = rows;
                    if (!canvas.getContext('2d')) throw new Error('canvas');
                    const rendering = render(doc.body, {
                        canvas, width: widthCss, height: rows / scale, x: 0, y: y / scale, scale,
                        windowWidth: 752, windowHeight: 1024, scrollX: 0, scrollY: 0,
                        foreignObjectRendering: false, allowTaint: false, useCORS: false, proxy: null, signal,
                        imageTimeout: 15000, removeContainer: true, logging: false, backgroundColor: '#f5f7fb',
                        onclone: async clone => {
                            for (const face of prepared.fontFaces) clone.fonts.add(face);
                            await runtime.wait(clone.fonts.ready, signal);
                            await Promise.all(Array.from(clone.images).map(image => runtime.wait(image.decode(), signal)));
                        }
                    });
                    await runtime.wait(rendering.then(value => {
                        if (signal.aborted) { value.width = value.height = 0; check(); }
                        return value;
                    }), signal, 60000);
                    check();
                    if (canvas.width !== width || canvas.height !== rows) throw new Error('canvas');
                    const pixels = canvas.getContext('2d', {willReadFrequently: true}).getImageData(0, 0, width, rows).data;
                    const stride = width * 4;
                    // 每批只保留少量滤波行；PNG Sub 滤波避免纯色/文字长图产生巨量输出。
                    for (let row = 0; row < rows; row += 32) {
                        check();
                        const count = Math.min(32, rows - row);
                        const filtered = new Uint8Array((stride + 1) * count);
                        for (let r = 0; r < count; r++) {
                            const offset = (row + r) * stride;
                            const target = r * (stride + 1);
                            filtered[target] = 1;
                            for (let column = 0; column < stride; column++)
                                filtered[target + 1 + column] = (pixels[offset + column] - (column < 4 ? 0 : pixels[offset + column - 4])) & 255;
                        }
                        await runtime.wait(writer.write(filtered), signal, 60000);
                        writtenRows += count;
                    }
                } finally { canvas.width = canvas.height = 0; }
                await yieldToBrowser();
            }
            if (writtenRows !== height) throw new Error('encode');
            await runtime.wait(writer.close(), signal, 60000);
            await runtime.wait(drain, signal, 60000);
            if (drainError) throw drainError;
            chunks.push(pngChunk('IEND', new Uint8Array(0)));
            const blob = new Blob(chunks, {type: 'image/png'});
            if (!blob.size || !compressedSize) throw new Error('encode');
            return blob;
        };
        (async () => {
            try {
                let module;
                try { module = await runtime.wait(import('html2canvas-pro'), signal); } catch { throw new Error('assets'); }
                const render = module.default;
                if (typeof render !== 'function') throw new Error('assets');
                prepared = await prepareDocument(runtime, html, language, signal, panel.status, false);
                const blob = await encode(prepared.doc, render);
                check();
                panel.ready(blob, fileName, prepared.warning);
                const result = await panel.result;
                done(result.cancelled ? 0 : 1, result.message);
            } catch (error) {
                if (signal.aborted) done(0, '');
                else done(-1, runtime.errorText(error, language));
            } finally {
                controller.abort();
                if (writer) { await writer.abort().catch(() => {}); writer.releaseLock(); }
                if (drain) await drain;
                if (prepared) prepared.dispose();
                panel.dispose();
            }
        })();
        return {abort: () => controller.abort()};
    }""",
)
private external fun renderWebArticleImage(
    runtime: JsAny,
    prepareDocument: JsAny,
    html: String,
    fileName: String,
    language: String,
    isActive: () -> Boolean,
    done: (Int, String) -> Unit,
): WebImageOperation
