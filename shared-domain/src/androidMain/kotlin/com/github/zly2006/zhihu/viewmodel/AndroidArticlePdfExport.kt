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

package com.github.zly2006.zhihu.viewmodel

import android.app.Activity
import android.app.Application
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintJob
import android.print.PrintManager
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.i18n.WidgetsStrings
import com.github.zly2006.zhihu.util.AppLog
import com.github.zly2006.zhihu.util.ArticleExportBudgetExceeded
import com.github.zly2006.zhihu.util.buildArticleExportFileName
import com.github.zly2006.zhihu.util.buildArticleExportHtml
import com.github.zly2006.zhihu.util.inlineArticleExportImagesForOfflineHtml
import io.ktor.client.HttpClient
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive

/** 官方打印服务拥有 layout/write 回调，不能反射构造其包私有 callback 来绕开系统生命周期。 */
internal suspend fun exportAndroidArticlePdf(
    activity: Activity,
    content: DataHolder.Content,
    includeAppAttribution: Boolean,
    httpClient: HttpClient,
    renderer: AndroidArticleExportRenderer,
): ArticleExportResult {
    val html = withContext(Dispatchers.Default) {
        val source = buildArticleExportHtml(
            loadAssetText = { activity.assets.open(it).bufferedReader().use { reader -> reader.readText() } },
            content = content,
            includeAppAttribution = includeAppAttribution,
        )
        check(source.length <= 64 * 1024 * 1024) { DomainStrings.exportTooLarge }
        val inlined = withTimeoutOrNull(18_000L) {
            inlineArticleExportImagesForOfflineHtml(
                source, httpClient, replaceFailedImagesWithPlaceholder = true,
                maxTotalImageBytes = 128L * 1024 * 1024,
            )
        } ?: error(DomainStrings.renderTimedOut)
        prepareAndroidPdfImages(inlined)
    }
    var view: WebView? = null
    var session: AndroidPdfPrintSession? = null
    var result: ArticleExportResult? = null
    var primary: Throwable? = null
    try {
        val ownerContext = currentCoroutineContext()
        withContext(Dispatchers.Main) {
            check(!activity.isFinishing && !activity.isDestroyed) { DomainStrings.exportHostUnavailable }
            ensureExportAccountActive()
            val webView = renderer.createExportWebView().also { view = it }
            val parent = checkNotNull(activity.findViewById<ViewGroup>(android.R.id.content)) { DomainStrings.exportHostUnavailable }
            val width = (720 * activity.resources.displayMetrics.density).toInt().coerceAtLeast(1)
            val height = (900 * activity.resources.displayMetrics.density).toInt().coerceAtLeast(1)
            parent.addView(webView, 0, ViewGroup.LayoutParams(width, height))
            renderer.measureAndLayoutExportWebView(webView, width, height)
            prepareAndroidPdfView(webView, html, renderer)
            ensureExportAccountActive()
            val manager = checkNotNull(activity.getSystemService(PrintManager::class.java)) { DomainStrings.exportHostUnavailable }
            val name = buildArticleExportFileName(content, "pdf")
            val printSession = AndroidPdfPrintSession(activity, webView, webView.createPrintDocumentAdapter(name), ownerContext)
            session = printSession
            val attributes = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asPortrait())
                .setResolution(PrintAttributes.Resolution("pdf", "PDF", 300, 300))
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .build()
            printSession.start(manager, name, attributes)
        }
        result = withContext(Dispatchers.Main) { checkNotNull(session).awaitResult() }
    } catch (error: Throwable) {
        primary = error
        throw error
    } finally {
        withContext(NonCancellable + Dispatchers.Main) {
            val cleanup = runCatching {
                val printSession = session
                if (printSession == null) {
                    view?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy() }
                } else {
                    printSession.releaseOrCancel()
                    printSession.cleanupFailure?.let { throw it }
                }
            }.exceptionOrNull()
            if (cleanup != null) {
                if (primary != null) primary.addSuppressed(cleanup)
                else if (result is ArticleExportResult.Completed) {
                    AppLog.e("ArticlePdfExport", "Print completed but cleanup failed", cleanup)
                    result = ArticleExportResult.Completed("${(result as ArticleExportResult.Completed).completionMessage}\n${DomainStrings.exportCleanupFailed}")
                } else throw cleanup
            }
        }
    }
    return checkNotNull(result)
}

private suspend fun prepareAndroidPdfImages(html: String): String {
    if (html.length > 192 * 1024 * 1024) throw ArticleExportBudgetExceeded()
    val document = Ksoup.parse(html)
    var bytesTotal = 0L
    var pixelsTotal = 0L
    for (image in document.select("img")) {
        currentCoroutineContext().ensureActive()
        try {
            val source = image.attr("src")
            require(source.startsWith("data:image/") && source.substringBefore(',').endsWith(";base64"))
            val encoded = source.substringAfter(',')
            require(encoded.length <= 44 * 1024 * 1024)
            val bytes = Base64.Default.decode(encoded)
            bytesTotal += bytes.size
            if (bytesTotal > 128L * 1024 * 1024) throw ArticleExportBudgetExceeded()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0)
            val pixels = bounds.outWidth.toLong() * bounds.outHeight
            require(pixels <= 64_000_000L)
            pixelsTotal += pixels
            if (pixelsTotal > 128_000_000L) throw ArticleExportBudgetExceeded()
        } catch (error: ArticleExportBudgetExceeded) {
            throw error
        } catch (_: Exception) {
            image.replaceWith(Element("span").addClass("export-image-placeholder").text(
                "[${WidgetsStrings.imageLoadFailed}]" + image.attr("alt").takeIf(String::isNotBlank)?.let { " $it" }.orEmpty(),
            ))
        }
    }
    return document.outerHtml()
}

private suspend fun prepareAndroidPdfView(view: WebView, html: String, renderer: AndroidArticleExportRenderer) {
    val loaded = CompletableDeferred<Unit>()
    view.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
        override fun onPageFinished(view: WebView, url: String) { loaded.complete(Unit) }
        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) loaded.completeExceptionally(IllegalStateException(DomainStrings.renderLoadError))
        }
    }
    val csp = "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src data:; style-src 'unsafe-inline'; script-src 'unsafe-inline'; font-src data:; base-uri 'none'; form-action 'none'\">"
    view.loadDataWithBaseURL("about:blank", html.replaceFirst("<head>", "<head>$csp"), "text/html", "UTF-8", null)
    withTimeoutOrNull(18_000L) {
        loaded.await()
        suspendCancellableCoroutine { continuation -> renderer.injectExportFootnoteScript(view) { if (continuation.isActive) continuation.resume(Unit) } }
        view.evaluatePdfScript(
            """
            (function() {
              window.__zhihuPdfReady = false;
              var failed = ${JsonPrimitive(WidgetsStrings.imageLoadFailed)};
              function placeholder(i) { var s=document.createElement('span'); s.className='export-image-placeholder'; s.textContent='['+failed+']'+(i.alt?' '+i.alt:''); i.replaceWith(s); }
              Promise.all([document.fonts.ready].concat(Array.from(document.images).map(function(i) {
                i.loading='eager';
                return (i.decode?i.decode():Promise.resolve()).then(function() { if (!i.naturalWidth || !i.naturalHeight) placeholder(i); },function(){placeholder(i);});
              }))).then(function() {
                var previous=-1, stable=0;
                function ready() { var height=Math.max(document.body.scrollHeight,document.documentElement.scrollHeight); stable=height===previous?stable+1:0; previous=height;
                  if(stable>=3) window.__zhihuPdfReady=true; else requestAnimationFrame(ready);
                }
                requestAnimationFrame(ready);
              });
            })();
            """.trimIndent(),
        )
        while (view.evaluatePdfScript("window.__zhihuPdfReady === true") != "true") delay(40)
        val fits = view.evaluatePdfScript(
            "(function(){var limit=document.documentElement.clientWidth+1;return document.documentElement.scrollWidth<=limit&&Array.from(document.body.querySelectorAll('*')).every(function(e){var r=e.getBoundingClientRect(),s=getComputedStyle(e);if(!r.width||!r.height||s.display==='none')return true;return r.left>=-1&&r.right<=limit&&(!(s.overflowX==='auto'||s.overflowX==='scroll'||s.overflowX==='hidden')||e.scrollWidth<=e.clientWidth+1);});})()",
        )
        check(fits == "true") { DomainStrings.exportRenderFailed }
    } ?: error(DomainStrings.renderTimedOut)
}

private suspend fun WebView.evaluatePdfScript(script: String): String = suspendCancellableCoroutine { continuation ->
    evaluateJavascript(script) { value -> if (continuation.isActive) continuation.resume(value.orEmpty()) }
}

private class AndroidPdfPrintSession(
    private val activity: Activity,
    private val view: WebView,
    private val delegate: PrintDocumentAdapter,
    private val ownerContext: CoroutineContext,
) : PrintDocumentAdapter(), Application.ActivityLifecycleCallbacks {
    private var printJob: PrintJob? = null
    private var activeSignal: CancellationSignal? = null
    private var adapterFinished = false
    private var destroyed = false
    private var acceptingCallbacks = true
    private var callbackFailure: Throwable? = null
    var cleanupFailure: Throwable? = null
        private set

    fun start(manager: PrintManager, name: String, attributes: PrintAttributes) {
        activity.application.registerActivityLifecycleCallbacks(this)
        try {
            printJob = checkNotNull(manager.print(name, this, attributes)) { DomainStrings.exportRenderFailed }
        } catch (error: Throwable) {
            activity.application.unregisterActivityLifecycleCallbacks(this)
            throw error
        }
    }

    override fun onStart() {
        try { delegate.onStart() } catch (error: Exception) {
            callbackFailure = error
            acceptingCallbacks = false
        }
    }

    override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes, cancellationSignal: CancellationSignal, callback: LayoutResultCallback, extras: Bundle?) {
        if (!acceptingCallbacks || !ownerContext.isActive) { callback.onLayoutCancelled(); return }
        activeSignal = cancellationSignal
        CoroutineScope(ownerContext + Dispatchers.Main.immediate).launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                ensureExportAccountActive()
                delegate.onLayout(oldAttributes, newAttributes, cancellationSignal, callback, extras)
            } catch (_: CancellationException) {
                cancellationSignal.cancel()
                callback.onLayoutCancelled()
            } catch (error: Exception) {
                callbackFailure = error
                callback.onLayoutFailed(error.message)
            }
        }
    }

    override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal, callback: WriteResultCallback) {
        if (!acceptingCallbacks || !ownerContext.isActive) { destination.close(); callback.onWriteCancelled(); return }
        activeSignal = cancellationSignal
        CoroutineScope(ownerContext + Dispatchers.Main.immediate).launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                ensureExportAccountActive()
                delegate.onWrite(pages, destination, cancellationSignal, callback)
            } catch (_: CancellationException) {
                cancellationSignal.cancel()
                destination.close()
                callback.onWriteCancelled()
            } catch (error: Exception) {
                callbackFailure = error
                destination.close()
                callback.onWriteFailed(error.message)
            }
        }
    }

    override fun onFinish() {
        if (adapterFinished) return
        adapterFinished = true
        val finishError = runCatching { delegate.onFinish() }.exceptionOrNull()
        val destroyError = runCatching { destroyView() }.exceptionOrNull()
        cleanupFailure = finishError ?: destroyError
        if (finishError != null && destroyError != null && finishError !== destroyError) finishError.addSuppressed(destroyError)
        cleanupFailure?.let { AppLog.e("ArticlePdfExport", "Print adapter cleanup failed", it) }
    }

    suspend fun awaitResult(): ArticleExportResult {
        val job = checkNotNull(printJob)
        while (true) {
            ensureExportAccountActive()
            callbackFailure?.let { throw it }
            when {
                job.isCompleted -> return ArticleExportResult.Completed(DomainStrings.pdfPrintCompleted)
                job.isCancelled || activity.isDestroyed -> return ArticleExportResult.Cancelled
                job.isFailed -> error(DomainStrings.exportRenderFailed)
            }
            // blocked 是可恢复状态，继续观察真实终态，账号或页面取消仍立即中断等待。
            delay(250)
        }
    }

    fun releaseOrCancel() {
        acceptingCallbacks = false
        val job = printJob ?: run { destroyView(); return }
        val terminal = job.isCompleted || job.isCancelled || job.isFailed
        if (!terminal) {
            // adapter onFinish 只结束内容生成，排队或阻塞中的系统任务仍须随 owner 取消。
            try {
                activeSignal?.cancel()
            } finally {
                job.cancel()
            }
        }
        if (terminal && !adapterFinished) onFinish()
        // 非终态时系统仍可能回调适配器，由 onFinish 或宿主销毁释放 WebView。
    }

    private fun destroyView() {
        if (destroyed) return
        destroyed = true
        activity.application.unregisterActivityLifecycleCallbacks(this)
        try { view.stopLoading() } finally { (view.parent as? ViewGroup)?.removeView(view); view.destroy() }
    }

    override fun onActivityDestroyed(target: Activity) {
        if (target !== activity) return
        acceptingCallbacks = false
        activeSignal?.cancel()
        printJob?.cancel()
        onFinish()
    }
    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
}
