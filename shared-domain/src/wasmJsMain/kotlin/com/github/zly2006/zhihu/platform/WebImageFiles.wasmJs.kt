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

package com.github.zly2006.zhihu.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.github.zly2006.zhihu.i18n.AppLocale
import com.github.zly2006.zhihu.viewmodel.ArticleExportResult
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.JsAny
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

internal external interface WebImageOperation : JsAny {
    fun abort()
}

/** 只封装浏览器文件和匿名资源操作；不持有账号或借用请求桥。 */
internal val webImageRuntime: JsAny = createWebImageRuntime()

internal suspend fun readWebExportResource(source: String, image: Boolean = false): String = suspendCancellableCoroutine { continuation ->
    val operation = readWebExportResource(webImageRuntime, source, image) { success, value ->
        if (continuation.isActive) {
            if (success) continuation.resume(value) else continuation.resumeWithException(IllegalStateException(value))
        }
    }
    continuation.invokeOnCancellation { operation.abort() }
}

internal suspend fun awaitWebImageOperation(start: ((Int, String) -> Unit) -> WebImageOperation): ArticleExportResult =
    suspendCancellableCoroutine { continuation ->
        val operation = start { status, message ->
            if (continuation.isActive) {
                when (status) {
                    0 -> continuation.resume(ArticleExportResult.Cancelled)
                    1 -> continuation.resume(ArticleExportResult.Completed(message))
                    else -> continuation.resumeWithException(IllegalStateException(message))
                }
            }
        }
        continuation.invokeOnCancellation { operation.abort() }
    }

@Composable
actual fun rememberImageSaver(): ImageSaver {
    val action = rememberWebImageAction(share = false)
    return remember(action) {
        object : ImageSaver {
            override fun invoke(url: String) = action(url)
        }
    }
}

@Composable
actual fun rememberImageSharer(): ImageSharer {
    val action = rememberWebImageAction(share = true)
    return remember(action) {
        object : ImageSharer {
            override fun invoke(url: String) = action(url)
        }
    }
}

@Composable
private fun rememberWebImageAction(share: Boolean): (String) -> Unit {
    val scope = rememberCoroutineScope()
    val messages = rememberUserMessageSink()
    return remember(scope, messages, share) {
        { source ->
            scope.launch {
                try {
                    val result = awaitWebImageOperation { done ->
                        deliverWebImage(webImageRuntime, source, share, AppLocale.languageTag, done)
                    }
                    if (result is ArticleExportResult.Completed) messages.showLongMessage(result.completionMessage)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    messages.showLongMessage(error.message.orEmpty())
                }
            }
        }
    }
}

@JsFun(
    """(runtime, source, image, done) => {
        const controller = new AbortController();
        (async () => {
            try {
                const blob = await runtime.read(source, image ? 'image' : 'text', controller.signal);
                const value = image ? await runtime.dataUrl(blob, controller.signal) : await blob.text();
                controller.signal.throwIfAborted();
                done(true, value);
            } catch (error) {
                done(false, runtime.errorText(error, document.documentElement.lang));
            }
        })();
        return {abort: () => controller.abort()};
    }""",
)
private external fun readWebExportResource(runtime: JsAny, source: String, image: Boolean, done: (Boolean, String) -> Unit): WebImageOperation

@JsFun(
    """(runtime, source, share, language, done) => {
        const controller = new AbortController();
        const panel = runtime.panel(language, controller);
        (async () => {
            try {
                let blob = await runtime.read(source, 'image', controller.signal);
                if (blob.type === 'image/svg+xml') {
                    const decoded = await runtime.decode(blob, controller.signal);
                    try {
                        if (decoded.image.naturalWidth * decoded.image.naturalHeight > 4000000)
                            throw new Error('image-size');
                        const canvas = document.createElement('canvas');
                        try {
                            canvas.width = decoded.image.naturalWidth;
                            canvas.height = decoded.image.naturalHeight;
                            const context = canvas.getContext('2d');
                            if (!context) throw new Error('canvas');
                            context.drawImage(decoded.image, 0, 0);
                            blob = await runtime.canvasBlob(canvas, controller.signal);
                        } finally { canvas.width = canvas.height = 0; }
                    } finally { decoded.release(); }
                }
                const extensions = {
                    'image/jpeg': 'jpg', 'image/png': 'png', 'image/gif': 'gif', 'image/webp': 'webp',
                    'image/avif': 'avif', 'image/bmp': 'bmp', 'image/heic': 'heic', 'image/heif': 'heif'
                };
                const name = 'zhihu-image-' + Date.now() + '.' + extensions[blob.type];
                panel.ready(blob, name, '', share);
                const result = await panel.result;
                done(result.cancelled ? 0 : 1, result.message);
            } catch (error) {
                if (controller.signal.aborted) done(0, '');
                else done(-1, runtime.errorText(error, language));
            } finally { controller.abort(); panel.dispose(); }
        })();
        return {abort: () => controller.abort()};
    }""",
)
private external fun deliverWebImage(runtime: JsAny, source: String, share: Boolean, language: String, done: (Int, String) -> Unit): WebImageOperation

@JsFun(
    """() => {
        const imageTypes = new Set(['image/jpeg', 'image/png', 'image/gif', 'image/webp',
            'image/avif', 'image/bmp', 'image/svg+xml', 'image/heic', 'image/heif']);
        const text = (language, zh, en) => /^zh(?:-|$)/i.test(language) ? zh : en;
        const errorText = (error, language) => {
            const messages = {
                'url': ['只允许不含凭据的 HTTPS 图片地址。', 'Only HTTPS image URLs without credentials are allowed.'],
                'network': ['图片或导出资源读取失败（可能被跨域策略阻止）；没有使用账号凭据或请求代理。',
                    'An image or export resource could not be read, possibly due to CORS. No account credentials or proxy were used.'],
                'timeout': ['读取或绘制超时，未导出截断的图片。请重试或导出 HTML。',
                    'Reading or rendering timed out. No truncated image was exported. Retry or export HTML.'],
                'type': ['服务器没有返回受支持的图片文件。', 'The server did not return a supported image file.'],
                'size': ['资源超过安全内存预算，未生成不完整文件。请使用 HTML 导出。',
                    'The resource exceeds the memory budget. No incomplete file was created. Export HTML instead.'],
                'image-size': ['图片解码尺寸过大，未生成不完整文件。', 'The image is too large to decode safely. No incomplete file was created.'],
                'decode': ['浏览器无法解码这张图片。', 'This browser cannot decode the image.'],
                'canvas': ['浏览器无法创建或读取绘图表面。未导出空白或截断的图片。',
                    'The browser could not create or read the drawing surface. No blank or truncated image was exported.'],
                'encode': ['PNG 编码失败，未创建下载文件。', 'PNG encoding failed. No download file was created.'],
                'compression': ['当前浏览器不支持长图流式编码，请升级浏览器或导出 HTML。',
                    'This browser cannot stream-encode long images. Update the browser or export HTML.'],
                'fonts': ['导出字体未能加载，未用缺字图片冒充完整导出。请重试。',
                    'Export fonts could not be loaded. An image with missing glyphs was not exported. Please retry.'],
                'layout': ['正文超出可安全绘制的尺寸，未裁切内容。请使用 HTML 导出。',
                    'The content exceeds safe rendering dimensions. Nothing was cropped. Export HTML instead.'],
                'assets': ['导出组件或本地字体资源缺失，请检查站点分发文件。',
                    'An export component or local font resource is missing. Check the site distribution.'],
                'operation': ['浏览器未能完成图片处理，未创建不完整文件。请重试或导出 HTML。',
                    'The browser could not finish processing the image. No incomplete file was created. Retry or export HTML.']
            };
            const pair = messages[error && error.message] || messages.operation;
            return text(language, pair[0], pair[1]);
        };
        const wait = (promise, signal, timeout = 15000) => new Promise((resolve, reject) => {
            let timer;
            const cleanup = () => { clearTimeout(timer); signal.removeEventListener('abort', aborted); };
            const aborted = () => { cleanup(); reject(signal.reason || new DOMException('Aborted', 'AbortError')); };
            if (signal.aborted) { aborted(); return; }
            signal.addEventListener('abort', aborted, {once: true});
            timer = setTimeout(() => { cleanup(); reject(new Error('timeout')); }, timeout);
            Promise.resolve(promise).then(value => { cleanup(); resolve(value); }, error => { cleanup(); reject(error); });
        });
        const read = async (source, kind, signal) => {
            signal.throwIfAborted();
            const image = kind === 'image';
            const limit = image ? 33554432 : kind === 'font' ? 8388608 : 2097152;
            if (image && /^data:/i.test(source)) {
                if (source.length > Math.ceil(limit * 4 / 3) + 128) throw new Error('size');
                const match = /^data:(image\/(?:png|jpeg|gif|webp|avif|bmp));base64,([A-Za-z0-9+/]+={0,2})$/i.exec(source);
                if (!match) throw new Error('type');
                const binary = atob(match[2]);
                return new Blob([Uint8Array.from(binary, char => char.charCodeAt(0))], {type: match[1].toLowerCase()});
            }
            let url;
            try { url = new URL(source, document.baseURI); } catch { throw new Error('url'); }
            if (url.username || url.password || (image ? url.protocol !== 'https:' :
                url.origin !== location.origin || !['http:', 'https:'].includes(url.protocol))) throw new Error('url');
            const controller = new AbortController();
            const aborted = () => controller.abort();
            signal.addEventListener('abort', aborted, {once: true});
            let timedOut = false;
            const timer = setTimeout(() => { timedOut = true; controller.abort(); }, 15000);
            try {
                const response = await fetch(url.href, {
                    mode: image ? 'cors' : 'same-origin', credentials: 'omit', redirect: 'error',
                    referrerPolicy: 'no-referrer', signal: controller.signal
                });
                if (!response.ok || !response.body) throw new Error('network');
                const mime = (response.headers.get('content-type') || '').split(';')[0].trim().toLowerCase();
                if (image && !imageTypes.has(mime)) throw new Error('type');
                const declared = Number(response.headers.get('content-length'));
                if (Number.isFinite(declared) && declared > limit) throw new Error('size');
                const reader = response.body.getReader();
                const parts = [];
                let size = 0;
                try {
                    while (true) {
                        const part = await reader.read();
                        if (part.done) break;
                        size += part.value.byteLength;
                        if (size > limit) throw new Error('size');
                        parts.push(part.value);
                    }
                } finally { await reader.cancel().catch(() => {}); reader.releaseLock(); }
                if (!size) throw new Error('network');
                signal.throwIfAborted();
                return new Blob(parts, {type: mime});
            } catch (error) {
                if (signal.aborted) throw signal.reason;
                if (timedOut) throw new Error('timeout');
                if (!error || !['size', 'type', 'network'].includes(error.message)) throw new Error('network');
                throw error;
            } finally { clearTimeout(timer); signal.removeEventListener('abort', aborted); controller.abort(); }
        };
        const dataUrl = (blob, signal) => new Promise((resolve, reject) => {
            const reader = new FileReader();
            const cleanup = () => { signal.removeEventListener('abort', aborted); reader.onload = reader.onerror = reader.onabort = null; };
            const aborted = () => { cleanup(); if (reader.readyState === 1) reader.abort(); reject(signal.reason); };
            if (signal.aborted) { aborted(); return; }
            signal.addEventListener('abort', aborted, {once: true});
            reader.onload = () => { const value = reader.result; cleanup(); resolve(value); };
            reader.onerror = reader.onabort = () => { cleanup(); reject(new Error('decode')); };
            reader.readAsDataURL(blob);
        });
        const decode = async (blob, signal) => {
            const url = URL.createObjectURL(blob);
            const image = new Image();
            const release = () => { image.removeAttribute('src'); URL.revokeObjectURL(url); };
            try {
                image.src = url;
                await wait(image.decode(), signal);
                if (!image.naturalWidth || !image.naturalHeight) throw new Error('decode');
                if (image.naturalWidth * image.naturalHeight > 32000000) throw new Error('image-size');
                return {image, release};
            } catch (error) { release(); throw error.message === 'image-size' ? error : new Error('decode'); }
        };
        const canvasBlob = async (canvas, signal) => {
            const blob = await wait(new Promise(resolve => canvas.toBlob(resolve, 'image/png')), signal);
            if (!blob || !blob.size || blob.type !== 'image/png') throw new Error('encode');
            return blob;
        };
        const download = (name, blob) => {
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            try {
                link.href = url;
                link.download = name;
                link.hidden = true;
                document.body.appendChild(link);
                link.click();
            } finally { link.remove(); setTimeout(() => URL.revokeObjectURL(url), 60000); }
        };
        // 原生文件交付面板提供新的用户手势，异步取图后不能假定 Web Share 仍有 transient activation。
        const panel = (language, controller, kind = 'image', isActive = () => true) => {
            const archive = kind === 'zip';
            const t = (zh, en) => text(language, zh, en);
            const previousFocus = document.activeElement;
            const dialog = document.createElement('dialog');
            dialog.style.cssText = 'max-width:min(36rem,calc(100vw - 48px));max-height:80vh;overflow:auto;padding:24px;border:1px solid GrayText;border-radius:16px;color-scheme:light dark;background:Canvas;color:CanvasText;font:16px/1.6 system-ui,sans-serif';
            const title = document.createElement('h2');
            title.textContent = archive ? t('收藏夹 ZIP 文件', 'Collection ZIP file') : t('图片文件', 'Image file');
            title.style.cssText = 'margin:0 0 12px;font-size:22px';
            const status = document.createElement('p');
            status.setAttribute('role', 'status');
            status.setAttribute('aria-live', 'polite');
            status.style.cssText = 'white-space:pre-wrap;overflow-wrap:anywhere';
            status.textContent = archive ? t('正在准备 ZIP…', 'Preparing the ZIP…') : t('正在准备图片…', 'Preparing the image…');
            const controls = document.createElement('div');
            controls.style.cssText = 'display:flex;flex-wrap:wrap;gap:12px';
            const button = label => {
                const element = document.createElement('button');
                element.type = 'button'; element.textContent = label;
                element.style.cssText = 'font:inherit;padding:8px 16px;cursor:pointer';
                controls.appendChild(element); return element;
            };
            const save = button(archive ? t('下载 ZIP', 'Download ZIP') : t('下载图片', 'Download image'));
            const share = button(t('分享图片', 'Share image'));
            const cancel = button(t('取消', 'Cancel'));
            save.hidden = share.hidden = true;
            dialog.setAttribute('aria-label', title.textContent);
            dialog.append(title, status, controls);
            document.body.appendChild(dialog);
            let settled = false;
            let finish;
            const result = new Promise(resolve => { finish = resolve; });
            const resolve = value => { if (!settled) { settled = true; finish(value); } };
            const aborted = () => { resolve({cancelled: true, message: ''}); dialog.close(); };
            controller.signal.addEventListener('abort', aborted, {once: true});
            cancel.onclick = () => controller.abort();
            dialog.oncancel = event => { event.preventDefault(); controller.abort(); };
            dialog.showModal();
            const dispose = () => {
                controller.signal.removeEventListener('abort', aborted);
                dialog.close(); dialog.remove();
                if (previousFocus && previousFocus.isConnected) previousFocus.focus({preventScroll: true});
            };
            const ready = (blob, name, warning, preferShare = false) => {
                controller.signal.throwIfAborted();
                const file = archive ? null : new File([blob], name, {type: blob.type});
                const summary = name + '\n' + (blob.size / 1048576).toFixed(2) + ' MiB' + (warning ? '\n' + warning : '');
                let canShare = false;
                try { canShare = !archive && isSecureContext && !!navigator.share && !!navigator.canShare && navigator.canShare({files: [file]}); } catch {}
                status.textContent = summary + (archive || canShare ? '' : '\n' + t('当前浏览器不支持分享这个图片文件，请下载后分享。',
                    'This browser cannot share this image file. Download it and share the file instead.'));
                save.hidden = false;
                share.hidden = archive;
                share.disabled = !canShare;
                cancel.textContent = t('关闭', 'Close');
                const suffix = warning ? '\n' + warning : '';
                save.onclick = () => {
                    if (!isActive()) { controller.abort(); return; }
                    if (settled || controller.signal.aborted) return;
                    try {
                        download(name, blob);
                        resolve({cancelled: false, message: t('已向浏览器请求下载：', 'Download requested in the browser: ') + name + suffix});
                    } catch { status.textContent = t('无法发起下载，请重试。', 'The download could not be started. Please retry.'); }
                };
                share.onclick = () => {
                    if (!isActive()) { controller.abort(); return; }
                    if (settled || controller.signal.aborted || !canShare) return;
                    save.disabled = share.disabled = true;
                    // share() 必须在本次 click 内调用，前面不能再 await 文件读取。
                    let request;
                    try { request = navigator.share({files: [file], title: name}); }
                    catch (error) { request = Promise.reject(error); }
                    Promise.resolve(request).then(() => {
                        resolve({cancelled: false, message: t('图片文件已交给系统分享；是否送达取决于目标应用。',
                            'The image file was handed to system sharing; delivery depends on the target app.') + suffix});
                    }, error => {
                        if (controller.signal.aborted) return;
                        const explanation = error && error.name === 'AbortError'
                            ? t('分享已取消或没有可用目标。可以重试或下载图片。', 'Sharing was cancelled or no target is available. Retry or download the image.')
                            : t('系统未能分享图片（权限、文件限制或目标应用错误）。可以重试或下载图片。',
                                'System sharing failed (permission, file restrictions, or target-app error). Retry or download the image.');
                        status.textContent = summary + '\n' + explanation;
                        save.disabled = false; share.disabled = !canShare;
                    });
                };
                (preferShare && canShare ? share : save).focus();
            };
            return {result, ready, dispose, status: value => { if (!settled) status.textContent = value; }};
        };
        return {read, dataUrl, decode, canvasBlob, download, panel, wait, text, errorText};
    }""",
)
private external fun createWebImageRuntime(): JsAny
