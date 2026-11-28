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

import com.fleeksoft.ksoup.Ksoup
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.util.buildArticleExportHtml
import kotlin.js.JsAny

internal suspend fun buildWebRenderHtml(
    content: DataHolder.Content,
    includeAppAttribution: Boolean,
    extraSectionsHtml: String = "",
): String {
    val template = loadWebExportTemplate()
    val document = Ksoup.parse(
        buildArticleExportHtml(
            loadAssetText = { template },
            content = content,
            includeAppAttribution = includeAppAttribution,
            extraSectionsHtml = extraSectionsHtml,
        ),
    )
    // 在进入浏览器解析器前移走所有远端资源；DOMParser 的“惰性文档”也不能作为网络隔离边界。
    document.select("img").forEach { image ->
        image.attr("data-zhihu-export-source", image.attr("src"))
        image.removeAttr("src")
        image.removeAttr("srcset")
        image.removeAttr("sizes")
    }
    document.select("script, link, base, iframe, object, embed, video, audio").remove()
    document.head().prependElement("meta")
        .attr("http-equiv", "Content-Security-Policy")
        .attr(
            "content",
            "default-src 'none'; img-src data: blob:; style-src 'unsafe-inline'; font-src data: blob:; " +
                "script-src 'none'; connect-src 'none'; base-uri 'none'; form-action 'none'; frame-src 'self' about:",
        )
    return document.outerHtml()
}

/** PNG 和打印共用同一安全文档准备器；字体、图片及 iframe 由返回对象统一释放。 */
internal val webExportDocumentFactory: JsAny = createWebExportDocumentFactory()

@JsFun(
    """() => async (runtime, html, language, signal, report, forPrint) => {
        const t = (zh, en) => runtime.text(language, zh, en);
        let frame;
        const objectUrls = new Set();
        const fontFaces = [];
        const localUrl = blob => { const url = URL.createObjectURL(blob); objectUrls.add(url); return url; };
        const resourceBudget = {bytes: 0, pixels: 0};
        let missingImages = 0;
        let missingMath = 0;
        const budget = (bytes, pixels) => {
            resourceBudget.bytes += bytes;
            resourceBudget.pixels += pixels;
            if (resourceBudget.bytes > 134217728 || resourceBudget.pixels > 32000000) throw new Error('size');
        };
        const check = () => signal.throwIfAborted();
        const yieldToBrowser = () => runtime.wait(new Promise(resolve => setTimeout(resolve, 0)), signal);
        const placeholder = (node, formula = '') => {
            const replacement = node.ownerDocument.createElement('span');
            replacement.className = 'export-image-placeholder';
            replacement.style.cssText = 'display:inline-block;max-width:100%;overflow-wrap:anywhere;color:#9f1239;background:#fff1f2;padding:4px';
            if (formula) {
                missingMath++;
                replacement.textContent = t('[公式未能绘制，保留 LaTeX] ', '[Formula could not be rendered; LaTeX preserved] ') + formula;
            } else {
                missingImages++;
                replacement.textContent = t('[图片无法匿名读取或解码]', '[Image could not be read anonymously or decoded]') +
                    (node.getAttribute('alt') ? ' ' + node.getAttribute('alt') : '');
            }
            node.replaceWith(replacement);
        };
        const addFootnotes = doc => {
            if (doc.getElementById('zhihu-plus-footnotes')) return;
            const references = Array.from(doc.querySelectorAll('sup[data-draft-type="reference"]'));
            if (!references.length) return;
            const container = doc.createElement('div');
            container.id = 'zhihu-plus-footnotes';
            const heading = doc.createElement('h4');
            heading.textContent = t('参考资料', 'References');
            const list = doc.createElement('ol');
            references.forEach((reference, index) => {
                const number = reference.getAttribute('data-numero') || String(index + 1);
                const item = doc.createElement('li');
                if (/^[1-9][0-9]*$/.test(number)) item.value = Number(number);
                const label = reference.getAttribute('data-text') || '';
                const url = reference.getAttribute('data-url') || '';
                // common 已校验链接；这里只创建静态文本节点，不执行脚注脚本或拼入 HTML。
                item.textContent = label + (url ? (label ? ' — ' : '') + url : '');
                list.appendChild(item);
            });
            container.append(heading, list);
            (doc.querySelector('.RichContent-inner') || doc.body).appendChild(container);
        };
        const loadMath = async doc => {
            let module;
            try { module = await runtime.wait(import('katex'), signal); } catch { throw new Error('assets'); }
            const katex = module.default || module;
            const cssUrl = new URL('export/katex/katex.min.css', window.zhihuAppResourceBase || new URL('./', location.href));
            const css = await (await runtime.read(cssUrl.href, 'text', signal)).text();
            const definitions = Array.from(css.matchAll(/@font-face\s*\{([^}]+)\}/g));
            if (!definitions.length) throw new Error('assets');
            for (const definition of definitions) {
                check();
                const property = name => {
                    const match = new RegExp('(?:^|;)\\s*' + name + '\\s*:\\s*([^;]+)').exec(definition[1]);
                    return match ? match[1].trim().replace(/^['"]|['"]$/g, '') : '';
                };
                const source = /url\(\s*['"]?([^)'"\s]+\.woff2)['"]?\s*\)/.exec(definition[1]);
                if (!source || !property('font-family')) throw new Error('assets');
                const fontUrl = new URL(source[1], cssUrl);
                const blob = await runtime.read(fontUrl.href, 'font', signal);
                const face = new FontFace(property('font-family'), await blob.arrayBuffer(), {
                    style: property('font-style') || 'normal', weight: property('font-weight') || 'normal'
                });
                try { await runtime.wait(face.load(), signal); } catch { throw new Error('fonts'); }
                // 输出 Canvas 位于主 document，离屏布局和库内资源位于 frame；两处均使用同一字体。
                document.fonts.add(face);
                doc.fonts.add(face);
                fontFaces.push(face);
            }
            const remainingCss = css.replace(/@font-face\s*\{[^}]+\}/g, '');
            if (/url\s*\(|@import/i.test(remainingCss)) throw new Error('assets');
            const style = doc.createElement('style');
            style.textContent = remainingCss;
            doc.head.appendChild(style);
            return katex;
        };
        const normalizeImage = async (node, blob) => {
            const decoded = await runtime.decode(blob, signal);
            try {
                const original = decoded.image;
                const avatar = node.classList.contains('author-avatar');
                const comment = node.classList.contains('comment-image');
                const formula = node.hasAttribute('eeimg') || node.classList.contains('ee_img');
                // 仅限制解码后内存，保留原图纵横比；头像裁剪由模板的 object-fit:cover 和渲染器负责。
                const resize = avatar
                    ? Math.min(1, 112 / Math.min(original.naturalWidth, original.naturalHeight))
                    : Math.min(1, (comment ? 480 : 1360) / original.naturalWidth, comment ? 480 / original.naturalHeight : 1);
                const width = Math.max(1, Math.round(original.naturalWidth * resize));
                const height = Math.max(1, Math.round(original.naturalHeight * resize));
                // 超长原图不先展平为一张巨幅 Canvas；保留已解码的 img，由正文条带绘制连续捕获。
                if (width > 4096 || height > 4096 || width * height > 2000000) {
                    budget(blob.size, original.naturalWidth * original.naturalHeight);
                    node.src = localUrl(blob);
                } else {
                    const canvas = document.createElement('canvas');
                    try {
                        canvas.width = width; canvas.height = height;
                        const context = canvas.getContext('2d');
                        if (!context) throw new Error('canvas');
                        context.drawImage(original, 0, 0, width, height);
                        const normalized = await runtime.canvasBlob(canvas, signal);
                        budget(normalized.size, width * height);
                        node.src = localUrl(normalized);
                    } finally { canvas.width = canvas.height = 0; }
                }
                node.loading = 'eager';
                if (formula) {
                    node.style.display = node.getAttribute('eeimg') === '2' ? 'block' : 'inline-block';
                    node.style.margin = node.getAttribute('eeimg') === '2' ? '16px auto' : '0 0.12em';
                    node.style.maxWidth = '100%';
                }
                await runtime.wait(node.decode(), signal);
            } finally { decoded.release(); }
        };
        const prepareResources = async doc => {
            const images = Array.from(doc.querySelectorAll('img[data-zhihu-export-source]'));
            const formulaOf = node => {
                const source = node.getAttribute('data-zhihu-export-source') || '';
                let url;
                try { url = new URL(source); } catch {}
                const equation = url && url.hostname === 'www.zhihu.com' && url.pathname === '/equation';
                if (!node.hasAttribute('eeimg') && !node.classList.contains('ee_img') && !equation) return '';
                return (equation ? url.searchParams.get('tex') : '') || node.getAttribute('alt') || '';
            };
            const hasMath = images.some(node => formulaOf(node));
            const katex = hasMath ? await loadMath(doc) : null;
            for (let index = 0; index < images.length; index++) {
                check();
                const node = images[index];
                const source = node.getAttribute('data-zhihu-export-source') || '';
                const formula = formulaOf(node);
                report(t('正在准备图片和公式 ', 'Preparing images and formulas ') + (index + 1) + '/' + images.length);
                if (formula && formula.length <= 65536) {
                    const math = doc.createElement(node.getAttribute('eeimg') === '2' ? 'div' : 'span');
                    try {
                        katex.render(formula, math, {
                            displayMode: node.getAttribute('eeimg') === '2', output: 'html',
                            throwOnError: true, trust: false, strict: 'warn', maxExpand: 1000, maxSize: 20
                        });
                        math.setAttribute('aria-label', formula);
                        node.replaceWith(math);
                        continue;
                    } catch { /* 不支持的 TeX 先尝试原公式图片，仍失败则保留完整原文并明示。 */ }
                }
                try {
                    // 顺序解码且立即释放原始 Blob，不为相距很远的重复 URL 保留所有原图。
                    const blob = await runtime.read(source, 'image', signal);
                    await normalizeImage(node, blob);
                } catch (error) {
                    check();
                    if (['size', 'image-size', 'canvas', 'encode'].includes(error && error.message)) throw error;
                    placeholder(node, formula);
                }
                await yieldToBrowser();
            }
            try { await runtime.wait(doc.fonts.ready, signal); await runtime.wait(document.fonts.ready, signal); }
            catch { throw new Error('fonts'); }
            for (const formula of doc.querySelectorAll('.katex-display')) {
                const inner = formula.querySelector('.katex');
                if (inner && inner.getBoundingClientRect().width > formula.clientWidth && formula.clientWidth > 0) {
                    const size = parseFloat(doc.defaultView.getComputedStyle(inner).fontSize);
                    inner.style.fontSize = (size * formula.clientWidth / inner.getBoundingClientRect().width) + 'px';
                }
            }
        };
        const dispose = () => {
                for (const face of fontFaces) {
                    document.fonts.delete(face);
                    if (frame && frame.contentDocument) frame.contentDocument.fonts.delete(face);
                }
                if (frame) { frame.onload = frame.onerror = null; frame.remove(); }
                for (const url of objectUrls) URL.revokeObjectURL(url);
        };
        try {
                frame = document.createElement('iframe');
                frame.setAttribute('sandbox', forPrint ? 'allow-same-origin allow-modals' : 'allow-same-origin');
                frame.setAttribute('aria-hidden', 'true'); frame.tabIndex = -1;
                frame.style.cssText = 'position:fixed;left:-100000px;top:0;width:752px;height:1024px;border:0;pointer-events:none';
                const loaded = new Promise((resolve, reject) => {
                    frame.onload = resolve; frame.onerror = () => reject(new Error('assets'));
                });
                frame.srcdoc = html;
                document.body.appendChild(frame);
                await runtime.wait(loaded, signal);
                const doc = frame.contentDocument;
                if (!doc || !doc.body || !doc.querySelector('.page')) throw new Error('assets');
                const layout = doc.createElement('style');
                layout.textContent = 'html{overflow:hidden}body{width:752px;min-height:0}' +
                    '.content{overflow-wrap:anywhere}.content table{max-width:100%;table-layout:fixed}' +
                    '.content pre{white-space:pre-wrap;overflow-wrap:anywhere}.content img{max-width:100%}' +
                    'img[eeimg="1"]{display:inline-block;margin:0 .12em}img[eeimg="2"]{display:block;margin:16px auto}';
                doc.head.appendChild(layout);
                if (forPrint) {
                    const printStyle = doc.createElement('style');
                    printStyle.textContent = '@page{size:A4;margin:12mm 10mm}' +
                        '@media print{' +
                        'html,body{overflow:visible!important;width:auto!important;height:auto!important;min-height:0!important}' +
                        '.page{width:auto!important;max-width:none!important}' +
                        'h1,h2,h3,h4,h5,h6{break-after:avoid-page;page-break-after:avoid}' +
                        'p,li{orphans:2;widows:2}' +
                        '.content figure,.comment{break-inside:auto;page-break-inside:auto}' +
                        '.content img{max-width:100%;max-height:250mm;width:auto;height:auto;object-fit:contain;break-inside:avoid}' +
                        'table{width:100%;max-width:100%;border-collapse:collapse;table-layout:fixed}' +
                        'thead{display:table-header-group}tfoot{display:table-footer-group}td,th{overflow-wrap:anywhere}' +
                        'pre,code{white-space:pre-wrap;overflow-wrap:anywhere}' +
                        '.katex-display{max-width:100%;overflow:visible;break-inside:avoid}' +
                        '*{-webkit-print-color-adjust:exact;print-color-adjust:exact}' +
                        '}';
                    doc.head.appendChild(printStyle);
                }
                addFootnotes(doc);
                await prepareResources(doc);
                const warning = missingImages || missingMath
                    ? t('注意：', 'Notice: ') + missingImages + t(' 张图片和 ', ' images and ') + missingMath +
                        t(' 个公式未能读取或绘制，文档内已标记并保留公式原文；不是完整资源副本。',
                            ' formulas could not be loaded or rendered. They are marked in the document; formula source is preserved. This is not a complete resource copy.')
                    : '';
            return {doc, frame, fontFaces, warning, dispose};
        } catch (error) { dispose(); throw error; }
    }""",
)
private external fun createWebExportDocumentFactory(): JsAny
