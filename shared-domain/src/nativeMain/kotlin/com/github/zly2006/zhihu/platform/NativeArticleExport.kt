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

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.github.zly2006.zhihu.platform

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.i18n.WidgetsStrings
import com.github.zly2006.zhihu.util.ArticleExportBudgetExceeded
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.util.buildArticleExportFileName
import com.github.zly2006.zhihu.util.buildArticleExportHtml
import com.github.zly2006.zhihu.util.buildOfflineArticleExportHtml
import com.github.zly2006.zhihu.util.inlineArticleExportImagesForOfflineHtml
import com.github.zly2006.zhihu.viewmodel.ArticleExportResult
import io.ktor.client.HttpClient
import kotlin.io.encoding.Base64
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.cinterop.ObjCSignatureOverride
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.NSObject

internal suspend fun exportNativeArticleHtml(
    content: DataHolder.Content,
    includeAppAttribution: Boolean,
    httpClient: HttpClient,
    delivery: NativeFileDelivery,
): ArticleExportResult = withNativeExportDirectory { directory ->
    val path = "$directory/${buildArticleExportFileName(content, "html")}"
    withContext(Dispatchers.Default) {
        val html = buildOfflineArticleExportHtml(
            ::readNativeExportAsset, content, includeAppAttribution, httpClient,
        )
        writeNativeExportText(path, html)
    }
    delivery.saveFile(path, image = false)
}

internal suspend fun exportNativeArticleImage(
    content: DataHolder.Content,
    includeAppAttribution: Boolean,
    extraSectionsHtml: String,
    includeComments: Boolean,
    httpClient: HttpClient,
    delivery: NativeFileDelivery,
): ArticleExportResult = withNativeExportDirectory { directory ->
    val path = "$directory/${buildArticleExportFileName(content, "png")}"
    val html = prepareNativeArticleForRendering(content, includeAppAttribution, extraSectionsHtml, includeComments, httpClient, directory)
    renderNativeArticlePng(html, path, delivery)
    delivery.saveFile(path, image = true)
}

internal suspend fun prepareNativeArticleForRendering(
    content: DataHolder.Content,
    includeAppAttribution: Boolean,
    extraSectionsHtml: String,
    includeComments: Boolean,
    httpClient: HttpClient,
    directory: String,
): String = withContext(Dispatchers.Default) {
    val source = buildArticleExportHtml(::readNativeExportAsset, content, includeAppAttribution, extraSectionsHtml)
    if (source.length > 64 * 1024 * 1024) throw ArticleExportBudgetExceeded()
    val inlined = withTimeoutOrNull(if (includeComments) 18_000L else 15_000L) {
        inlineArticleExportImagesForOfflineHtml(
            html = source,
            httpClient = httpClient,
            replaceFailedImagesWithPlaceholder = true,
            maxTotalImageBytes = 128L * 1024 * 1024,
        )
    } ?: error(DomainStrings.renderTimedOut)
    prepareNativeExportImages(inlined, directory)
}

/** 在 WebKit 解码前检查原图像素与所有内嵌数据，坏图保留说明，预算不足整体失败。 */
private suspend fun prepareNativeExportImages(html: String, directory: String): String {
    if (html.length > 192 * 1024 * 1024) throw ArticleExportBudgetExceeded()
    val document = Ksoup.parse(html)
    var totalBytes = 0L
    var totalPixels = 0L
    val probe = Path("$directory/image-probe")
    for (image in document.select("img")) {
        currentCoroutineContext().ensureActive()
        try {
            val src = image.attr("src")
            check(src.startsWith("data:image/") && src.substringBefore(',').endsWith(";base64"))
            val encodedLength = src.length - src.indexOf(',') - 1
            if (encodedLength > 44 * 1024 * 1024) throw ArticleExportBudgetExceeded()
            val bytes = Base64.Default.decode(src.substringAfter(','))
            totalBytes += bytes.size
            if (totalBytes > 128L * 1024 * 1024) throw ArticleExportBudgetExceeded()
            SystemFileSystem.sink(probe).buffered().use { it.write(bytes) }
            totalPixels += validateNativeImageFile(probe.toString())
            if (totalPixels > 128_000_000L) throw ArticleExportBudgetExceeded()
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: ArticleExportBudgetExceeded) {
            throw error
        } catch (_: Exception) {
            image.replaceWith(Element("span").addClass("export-image-placeholder").text(
                "[${WidgetsStrings.imageLoadFailed}]" + image.attr("alt").takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty(),
            ))
        } finally {
            if (SystemFileSystem.exists(probe)) SystemFileSystem.delete(probe)
        }
    }
    return document.outerHtml()
}

private const val EXPORT_CSS_WIDTH = 720
private const val EXPORT_TILE_HEIGHT = 1024
private const val EXPORT_PIXEL_SCALE = 2

internal suspend fun <T> withNativeArticleExportView(
    html: String,
    delivery: NativeFileDelivery,
    render: suspend (webView: WKWebView, contentHeight: Int) -> T,
): T {
    var webView: WKWebView? = null
    var detach: (() -> Unit)? = null
    var navigationDelegate: WKNavigationDelegateProtocol? = null
    var primary: Throwable? = null
    try {
        val height = withContext(Dispatchers.Main) {
            val loaded = CompletableDeferred<Unit>()
            val configuration = WKWebViewConfiguration().apply {
                websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore()
            }
            val view = WKWebView(
                frame = CGRectMake(0.0, 0.0, EXPORT_CSS_WIDTH.toDouble(), EXPORT_TILE_HEIGHT.toDouble()),
                configuration = configuration,
            )
            webView = view
            val delegate = object : NSObject(), WKNavigationDelegateProtocol {
                override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
                    loaded.complete(Unit)
                }

                @ObjCSignatureOverride
                override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) {
                    loaded.completeExceptionally(IllegalStateException(withError.localizedDescription))
                }

                @ObjCSignatureOverride
                override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) {
                    loaded.completeExceptionally(IllegalStateException(withError.localizedDescription))
                }

                override fun webViewWebContentProcessDidTerminate(webView: WKWebView) {
                    loaded.completeExceptionally(IllegalStateException(DomainStrings.exportRenderFailed))
                }

                override fun webView(
                    webView: WKWebView,
                    decidePolicyForNavigationAction: WKNavigationAction,
                    decisionHandler: (WKNavigationActionPolicy) -> Unit,
                ) {
                    val url = decidePolicyForNavigationAction.request.URL?.absoluteString.orEmpty()
                    decisionHandler(
                        if (url == "about:blank") WKNavigationActionPolicy.WKNavigationActionPolicyAllow
                        else WKNavigationActionPolicy.WKNavigationActionPolicyCancel,
                    )
                }
            }
            navigationDelegate = delegate
            view.navigationDelegate = delegate
            detach = delivery.attachExportView(view)
            // 独立 origin 与 CSP 限制 CSS/图片外链；不把页面当成已登录知乎 WebView。
            val csp = "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src data:; style-src 'unsafe-inline'; script-src 'unsafe-inline'; font-src data:; base-uri 'none'; form-action 'none'\">"
            checkNotNull(view.loadHTMLString(html.replaceFirst("<head>", "<head>$csp"), baseURL = null)) {
                DomainStrings.exportRenderFailed
            }
            withTimeoutOrNull(18_000L) { loaded.await() } ?: error(DomainStrings.renderTimedOut)
            view.evaluateExportScript(readNativeExportAsset("footnotes.js"))
            // Promise 不依赖 evaluateJavaScript 自动等待；显式轮询业务就绪状态并受总超时约束。
            view.evaluateExportScript(
                """
                (function() {
                  window.__zhihuExportReady = false;
                  var failureText = ${JsonPrimitive(WidgetsStrings.imageLoadFailed)};
                  function placeholder(i) {
                    var note = document.createElement('span');
                    note.className = 'export-image-placeholder';
                    note.textContent = '[' + failureText + ']' + (i.alt ? ' ' + i.alt : '');
                    i.replaceWith(note);
                  }
                  document.querySelectorAll('img').forEach(function(i) { i.loading = 'eager'; });
                  Promise.all([document.fonts.ready].concat(Array.from(document.images).map(function(i) {
                    return (i.decode ? i.decode() : Promise.resolve()).then(function() {
                      if (!i.complete || i.naturalWidth <= 0 || i.naturalHeight <= 0) placeholder(i);
                    }, function() { placeholder(i); });
                  }))).then(function() {
                    var previous = -1, stable = 0;
                    function check() {
                      var height = Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);
                      stable = height === previous ? stable + 1 : 0; previous = height;
                      if (stable >= 3) { window.__zhihuExportReady = true; }
                      else requestAnimationFrame(check);
                    }
                    requestAnimationFrame(check);
                  });
                })();
                """.trimIndent(),
            )
            withTimeoutOrNull(18_000L) {
                while (view.evaluateExportScript("window.__zhihuExportReady === true") != true) {
                    kotlinx.coroutines.delay(40)
                }
            } ?: error(DomainStrings.renderTimedOut)
            val fitsWidth = view.evaluateExportScript(
                """
                (function() {
                  var limit = document.documentElement.clientWidth + 1;
                  return document.documentElement.scrollWidth <= limit && Array.from(document.body.querySelectorAll('*')).every(function(e) {
                    var r = e.getBoundingClientRect(), s = getComputedStyle(e);
                    if (!r.width || !r.height || s.display === 'none' || s.visibility === 'hidden') return true;
                    return r.left >= -1 && r.right <= limit && (!(s.overflowX === 'auto' || s.overflowX === 'scroll' || s.overflowX === 'hidden') || e.scrollWidth <= e.clientWidth + 1);
                  });
                })()
                """.trimIndent(),
            )
            check(fitsWidth == true) { DomainStrings.exportRenderFailed }
            val measured = (view.evaluateExportScript("Math.max(document.body.scrollHeight, document.documentElement.scrollHeight)") as? Number)?.toDouble()
                ?: error(DomainStrings.renderEmpty)
            check(measured.isFinite() && measured > 0 && measured <= 500_000) { DomainStrings.exportTooLarge }
            // delegate 保持强引用到导航与布局完成。
            check(view.navigationDelegate === delegate)
            ceil(measured).toInt()
        }
        return render(checkNotNull(webView), height)
    } catch (error: Throwable) {
        primary = error
        throw error
    } finally {
        withContext(NonCancellable + Dispatchers.Main) {
            val cleanup = runCatching {
                try {
                    webView?.stopLoading()
                } finally {
                    webView?.navigationDelegate = null
                    navigationDelegate = null
                    detach?.invoke()
                }
            }.exceptionOrNull()
            if (cleanup != null) {
                if (primary != null) primary.addSuppressed(cleanup) else throw cleanup
            }
        }
    }
}

private suspend fun renderNativeArticlePng(html: String, filePath: String, delivery: NativeFileDelivery) =
    withNativeArticleExportView(html, delivery) { view, height ->
        var writer: NativePngStreamWriter? = null
        var pendingSnapshot: NativeExportSnapshot? = null
        var primary: Throwable? = null
        try {
            val outputWidth = EXPORT_CSS_WIDTH * EXPORT_PIXEL_SCALE
            val outputHeight = height * EXPORT_PIXEL_SCALE
            check(outputWidth.toLong() * outputHeight <= 400_000_000L) { DomainStrings.exportTooLarge }
            val freeSpace = NSFileManager.defaultManager.attributesOfFileSystemForPath(filePath.substringBeforeLast('/'), null)
                ?.get(NSFileSystemFreeSize) as? Number
            check(freeSpace == null || freeSpace.toLong() >= minOf(outputWidth.toLong() * outputHeight * 3 + 1_048_576, 513L * 1024 * 1024)) {
                DomainStrings.exportStorageInsufficient
            }
            val output = withContext(Dispatchers.Default) {
                NativePngStreamWriter(filePath, outputWidth, outputHeight).also { writer = it }
            }
            var top = 0
            while (top < height) {
                currentCoroutineContext().ensureActive()
                val tileHeight = minOf(EXPORT_TILE_HEIGHT, height - top)
                val image = withContext(Dispatchers.Main) {
                    resizeNativeExportView(view, EXPORT_CSS_WIDTH, tileHeight)
                    view.evaluateExportScript("window.scrollTo(0, $top); window.__zhihuExportScrolled = false; requestAnimationFrame(function() { requestAnimationFrame(function() { window.__zhihuExportScrolled = true; }); });")
                    withTimeoutOrNull(5_000L) {
                        while (view.evaluateExportScript("window.__zhihuExportScrolled === true") != true) kotlinx.coroutines.delay(16)
                    } ?: error(DomainStrings.renderTimedOut)
                    val actualTop = (view.evaluateExportScript("window.scrollY") as? Number)?.toDouble() ?: -1.0
                    check(kotlin.math.abs(actualTop - top) < 1.0) { DomainStrings.exportRenderFailed }
                    withTimeoutOrNull(15_000L) {
                        snapshotNativeExportView(view, EXPORT_CSS_WIDTH, tileHeight, outputWidth).also { pendingSnapshot = it }
                    } ?: error(DomainStrings.renderTimedOut)
                }
                try {
                    withContext(Dispatchers.Default) { output.append(image.image, tileHeight * EXPORT_PIXEL_SCALE) }
                } finally {
                    image.close()
                    pendingSnapshot = null
                }
                top += tileHeight
            }
            withContext(Dispatchers.Default) { output.finish() }
        } catch (error: Throwable) {
            primary = error
            throw error
        } finally {
            withContext(NonCancellable) {
                val snapshotError = runCatching { pendingSnapshot?.close() }.exceptionOrNull()
                val outputError = runCatching { withContext(Dispatchers.Default) { writer?.close() } }.exceptionOrNull()
                val first = primary ?: snapshotError ?: outputError
                for (error in arrayOf(snapshotError, outputError)) {
                    if (first != null && error != null && first !== error) first.addSuppressed(error)
                }
                if (primary == null && first != null) throw first
            }
        }
    }

private suspend fun WKWebView.evaluateExportScript(script: String): Any? = suspendCancellableCoroutine { continuation ->
    evaluateJavaScript(script) { value, error ->
        if (continuation.isActive) {
            if (error != null) continuation.resumeWithException(IllegalStateException(error.localizedDescription))
            else continuation.resume(value)
        }
    }
}

internal expect fun resizeNativeExportView(webView: WKWebView, width: Int, height: Int)
