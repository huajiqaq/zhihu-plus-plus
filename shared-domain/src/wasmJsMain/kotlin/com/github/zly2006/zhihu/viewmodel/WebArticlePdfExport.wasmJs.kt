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
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.platform.WebImageOperation
import com.github.zly2006.zhihu.platform.awaitWebImageOperation
import com.github.zly2006.zhihu.platform.webImageRuntime
import com.github.zly2006.zhihu.util.buildArticleExportFileName
import kotlin.js.JsAny
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job

internal suspend fun exportWebArticlePdf(content: DataHolder.Content, includeAppAttribution: Boolean): ArticleExportResult {
    val html = buildWebRenderHtml(content, includeAppAttribution)
    ensureExportAccountActive()
    val job = currentCoroutineContext().job
    return awaitWebImageOperation { done ->
        printWebArticle(
            runtime = webImageRuntime,
            prepareDocument = webExportDocumentFactory,
            html = html,
            title = buildArticleExportFileName(content, "pdf").removeSuffix(".pdf"),
            language = AppLocale.languageTag,
            printOpened = DomainStrings.pdfPrintOpened,
            isActive = { job.isActive },
            done = done,
        )
    }
}

/** 打印只确认调用系统流程；afterprint 无法区分保存、打印或取消，不能用作文件交付凭据。 */
@JsFun(
    """(runtime, prepareDocument, html, title, language, printOpened, isActive, done) => {
        const controller = new AbortController();
        const signal = controller.signal;
        const t = (zh, en) => runtime.text(language, zh, en);
        const previousFocus = document.activeElement;
        const dialog = document.createElement('dialog');
        dialog.style.cssText = 'max-width:min(38rem,calc(100vw - 48px));max-height:80vh;overflow:auto;padding:24px;border:1px solid GrayText;border-radius:16px;color-scheme:light dark;background:Canvas;color:CanvasText;font:16px/1.6 system-ui,sans-serif';
        const heading = document.createElement('h2');
        heading.textContent = t('打印／另存为 PDF', 'Print / Save as PDF');
        heading.style.cssText = 'margin:0 0 12px;font-size:22px';
        const status = document.createElement('p');
        status.setAttribute('role', 'status');
        status.setAttribute('aria-live', 'polite');
        status.style.cssText = 'white-space:pre-wrap;overflow-wrap:anywhere';
        const explanation = document.createElement('p');
        explanation.textContent = t(
            '使用浏览器打印界面，在支持时选择“另存为 PDF”。正文保持文字排版，不会转成整页截图。应用不能判断最终是否保存或取消，也不能读取或直接分享另存后的文件。',
            'Use the browser print interface and choose Save as PDF where available. Body text is not turned into a page screenshot. The app cannot tell whether saving was completed or cancelled, or read or directly share the saved file.'
        );
        const controls = document.createElement('div');
        controls.style.cssText = 'display:flex;flex-wrap:wrap;gap:12px';
        const button = label => {
            const element = document.createElement('button');
            element.type = 'button'; element.textContent = label;
            element.style.cssText = 'font:inherit;padding:8px 16px;cursor:pointer';
            controls.appendChild(element); return element;
        };
        const print = button(t('打印／另存为 PDF', 'Print / Save as PDF'));
        const complete = button(t('返回应用', 'Return to app'));
        const close = button(t('取消', 'Cancel'));
        print.disabled = true;
        complete.hidden = true;
        dialog.setAttribute('aria-label', heading.textContent);
        dialog.append(heading, status, explanation, controls);
        document.body.appendChild(dialog);
        let prepared;
        let printWindow;
        let afterPrint;
        let settled = false;
        let requested = false;
        let resolveResult;
        const result = new Promise(resolve => { resolveResult = resolve; });
        const finish = (code, message) => {
            if (settled) return;
            settled = true;
            resolveResult({code, message});
        };
        const releaseDocument = () => {
            if (printWindow && afterPrint) printWindow.removeEventListener('afterprint', afterPrint);
            printWindow = null;
            if (prepared) { prepared.dispose(); prepared = null; }
        };
        const aborted = () => { releaseDocument(); finish(0, ''); };
        signal.addEventListener('abort', aborted, {once: true});
        close.onclick = () => controller.abort();
        dialog.oncancel = event => { event.preventDefault(); controller.abort(); };
        complete.onclick = () => {
            if (!isActive()) { controller.abort(); return; }
            if (!signal.aborted && requested) finish(1, printOpened);
        };
        status.textContent = t('正在准备安全打印文档…', 'Preparing the safe print document…');
        dialog.showModal();
        (async () => {
            try {
                prepared = await prepareDocument(runtime, html, language, signal, value => {
                    if (!settled) status.textContent = value;
                }, true);
                signal.throwIfAborted();
                if (!isActive()) { controller.abort(); done(0, ''); return; }
                prepared.doc.title = title;
                printWindow = prepared.frame.contentWindow;
                if (!printWindow || typeof printWindow.print !== 'function') {
                    throw new Error('print-unavailable');
                }
                const warning = prepared.warning;
                status.textContent = t('文档已就绪。点击按钮后选择 PDF 目标。', 'The document is ready. Click the button, then select a PDF destination.') +
                    (warning ? '\n' + warning : '');
                afterPrint = () => {
                    // 这里只回收文档；原生打印窗口关闭既可能是保存，也可能是用户取消。
                    releaseDocument();
                };
                printWindow.addEventListener('afterprint', afterPrint, {once: true});
                print.disabled = false;
                print.onclick = () => {
                    if (!isActive()) { controller.abort(); return; }
                    if (settled || signal.aborted || requested || !printWindow) return;
                    print.disabled = true;
                    try {
                        // 在用户这次点击中直接调用，不再 await；不选择打印机，不自动提交任何打印任务。
                        printWindow.focus();
                        printWindow.print();
                        requested = true;
                        complete.hidden = false;
                        close.textContent = t('关闭', 'Close');
                        status.textContent = printOpened + (warning ? '\n' + warning : '');
                        complete.focus();
                    } catch {
                        print.disabled = !printWindow;
                        status.textContent = t('浏览器未能打开打印界面。请允许打印后重试，或关闭后导出 HTML。',
                            'The browser could not open printing. Allow printing and retry, or close this dialog and export HTML.');
                    }
                };
                print.focus();
                const completion = await result;
                done(completion.code, completion.message);
            } catch (error) {
                if (signal.aborted) done(0, '');
                else done(-1, error && error.message === 'print-unavailable'
                    ? t('当前浏览器没有提供打印／另存为 PDF 功能。请改用支持打印的浏览器或导出 HTML。',
                        'This browser does not provide Print / Save as PDF. Use a browser with printing support or export HTML.')
                    : t('无法准备打印文档：', 'Unable to prepare the print document: ') + runtime.errorText(error, language));
            } finally {
                signal.removeEventListener('abort', aborted);
                controller.abort();
                releaseDocument();
                dialog.close(); dialog.remove();
                if (previousFocus && previousFocus.isConnected) previousFocus.focus({preventScroll: true});
            }
        })();
        return {abort: () => controller.abort()};
    }""",
)
private external fun printWebArticle(
    runtime: JsAny,
    prepareDocument: JsAny,
    html: String,
    title: String,
    language: String,
    printOpened: String,
    isActive: () -> Boolean,
    done: (Int, String) -> Unit,
): WebImageOperation
