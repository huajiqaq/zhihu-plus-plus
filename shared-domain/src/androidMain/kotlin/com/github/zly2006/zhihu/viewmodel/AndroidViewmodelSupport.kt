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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.account.webViewFailureObserver
import com.github.zly2006.zhihu.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

data class AndroidPreparedExportWebView(
    val webView: WebView,
    val viewportWidthPx: Int,
    val contentHeightPx: Int,
) : PreparedArticleExportContent

private const val ARTICLE_EXPORT_DPI = 200f

class AndroidArticleExportRenderer(
    private val context: Context,
    private val accountStore: ZhihuAccountStore,
) : ArticleImageExportRenderer {
    override suspend fun prepareExportWebView(
        htmlContent: String,
        timeoutMs: Long,
    ): PreparedArticleExportContent {
        val mainHandler = Handler(Looper.getMainLooper())
        var pendingWebView: WebView? = null
        var isFinished = false
        try {
            return withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    val webView = createExportWebView()
                    pendingWebView = webView
                    val account = accountStore.client
                    val observeHttpFailure = account.webViewFailureObserver()
                    val viewportWidthPx = context.resources.displayMetrics.widthPixels
                        .coerceAtLeast(1)
                    var timeoutRunnable = Runnable {}

                    fun fail(error: Throwable) {
                        if (isFinished) return
                        isFinished = true
                        mainHandler.removeCallbacks(timeoutRunnable)
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }

                    fun finish(contentHeightPx: Int) {
                        if (isFinished) return
                        measureAndLayoutExportWebView(
                            webView = webView,
                            widthPx = viewportWidthPx,
                            heightPx = contentHeightPx.coerceAtLeast(1),
                        )
                        isFinished = true
                        mainHandler.removeCallbacks(timeoutRunnable)
                        if (continuation.isActive) {
                            continuation.resume(
                                AndroidPreparedExportWebView(
                                    webView = webView,
                                    viewportWidthPx = viewportWidthPx,
                                    contentHeightPx = contentHeightPx,
                                ),
                            )
                        }
                    }

                    fun scheduleReadinessCheck(
                        attempt: Int = 0,
                        lastHeightPx: Int = -1,
                        stablePasses: Int = 0,
                    ) {
                        mainHandler.postDelayed({
                            if (isFinished) return@postDelayed

                            val density = webView.resources.displayMetrics.density
                            val contentHeightPx = maxOf(
                                (webView.contentHeight * density).roundToInt(),
                                webView.measuredHeight,
                                webView.height,
                                1,
                            )
                            if (contentHeightPx <= 1 && attempt >= 24) {
                                fail(IllegalStateException("内容为空"))
                                return@postDelayed
                            }

                            measureAndLayoutExportWebView(
                                webView = webView,
                                widthPx = viewportWidthPx,
                                heightPx = contentHeightPx.coerceAtLeast(1),
                            )

                            val nextStablePasses = if (contentHeightPx == lastHeightPx) stablePasses + 1 else 0
                            if (contentHeightPx > 1 && (nextStablePasses >= 2 || attempt >= 24)) {
                                finish(contentHeightPx)
                            } else {
                                scheduleReadinessCheck(
                                    attempt = attempt + 1,
                                    lastHeightPx = contentHeightPx,
                                    stablePasses = nextStablePasses,
                                )
                            }
                        }, if (attempt == 0) 450L else 180L)
                    }

                    timeoutRunnable = Runnable { fail(IllegalStateException("超时")) }
                    continuation.invokeOnCancellation {
                        mainHandler.post {
                            isFinished = true
                            mainHandler.removeCallbacksAndMessages(null)
                        }
                    }
                    webView.webViewClient = object : android.webkit.WebViewClient() {
                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                            super.onReceivedHttpError(view, request, errorResponse)
                            observeHttpFailure(request, errorResponse)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (!isFinished) {
                                injectExportFootnoteScript(webView) {
                                    if (!isFinished) scheduleReadinessCheck()
                                }
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: android.webkit.WebResourceError?,
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame != false) fail(IllegalStateException("加载错误"))
                        }
                    }
                    measureAndLayoutExportWebView(webView, viewportWidthPx, 1)
                    mainHandler.postDelayed(timeoutRunnable, timeoutMs)
                    webView.loadDataWithBaseURL("https://www.zhihu.com", htmlContent, "text/html", "UTF-8", null)
                }
            }
        } catch (error: Throwable) {
            /** resume 和 withContext 返回可取消；未移交的 WebView 仍须回收。 */
            withContext(NonCancellable) {
                runCatching {
                    withContext(Dispatchers.Main) {
                        isFinished = true
                        mainHandler.removeCallbacksAndMessages(null)
                        pendingWebView?.let { destroyExportWebView(it) }
                    }
                }.exceptionOrNull()?.let { cleanupError ->
                    if (cleanupError !== error && error.suppressedExceptions.none { it === cleanupError }) error.addSuppressed(cleanupError)
                }
            }
            throw error
        }
    }

    fun createExportWebView(): WebView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = false
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        setBackgroundColor(android.graphics.Color.WHITE)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
    }

    fun injectExportFootnoteScript(webView: WebView, onInjected: () -> Unit) {
        val jsCode = context.assets
            .open("footnotes.js")
            .bufferedReader()
            .use { it.readText() }
        if (jsCode.isBlank()) {
            onInjected()
            return
        }

        runCatching {
            webView.evaluateJavascript(jsCode) {
                onInjected()
            }
        }.onFailure { error ->
            Log.e("ArticleViewModel", "Failed to inject export footnotes", error)
            onInjected()
        }
    }

    fun measureAndLayoutExportWebView(webView: WebView, widthPx: Int, heightPx: Int) {
        val safeHeight = heightPx.coerceAtLeast(1)
        webView.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx.coerceAtLeast(1), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(safeHeight, View.MeasureSpec.EXACTLY),
        )
        webView.layout(0, 0, widthPx.coerceAtLeast(1), safeHeight)
    }

    override suspend fun captureExportBitmap(preparedWebView: PreparedArticleExportContent): Bitmap {
        var pendingBitmap: Bitmap? = null
        try {
            return withContext(Dispatchers.Main) {
                preparedWebView as AndroidPreparedExportWebView
                val rawWidth = preparedWebView.viewportWidthPx.coerceAtLeast(1)
                val rawHeight = preparedWebView.contentHeightPx.coerceAtLeast(1)
                val scale = ARTICLE_EXPORT_DPI / context.resources.displayMetrics.densityDpi
                    .coerceAtLeast(1)
                    .toFloat()
                val bitmapWidth = (rawWidth * scale).roundToInt().coerceAtLeast(1)
                val bitmapHeight = (rawHeight * scale).roundToInt().coerceAtLeast(1)

                Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
                    pendingBitmap = bitmap
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    canvas.scale(
                        bitmapWidth.toFloat() / rawWidth.toFloat(),
                        bitmapHeight.toFloat() / rawHeight.toFloat(),
                    )
                    preparedWebView.webView.draw(canvas)
                }
            }
        } catch (error: Throwable) {
            runCatching { pendingBitmap?.recycle() }.exceptionOrNull()?.let { cleanupError ->
                if (cleanupError !== error && error.suppressedExceptions.none { it === cleanupError }) error.addSuppressed(cleanupError)
            }
            throw error
        }
    }

    suspend fun destroyExportWebView(webView: WebView) {
        withContext(Dispatchers.Main) {
            val stopError = runCatching { webView.stopLoading() }.exceptionOrNull()
            val destroyed = runCatching { webView.destroy() }
            if (stopError != null) {
                destroyed.exceptionOrNull()?.let { destroyError ->
                    if (destroyError !== stopError && stopError.suppressedExceptions.none { it === destroyError }) stopError.addSuppressed(destroyError)
                }
                throw stopError
            }
            destroyed.getOrThrow()
        }
    }

    override suspend fun destroyExportWebView(preparedWebView: PreparedArticleExportContent) {
        preparedWebView as AndroidPreparedExportWebView
        destroyExportWebView(preparedWebView.webView)
    }

    override fun recycleExportBitmap(bitmap: Any) {
        (bitmap as Bitmap).recycle()
    }
}
