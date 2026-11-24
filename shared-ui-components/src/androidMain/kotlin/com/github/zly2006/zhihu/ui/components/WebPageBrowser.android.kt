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

package com.github.zly2006.zhihu.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.github.zly2006.zhihu.navigation.isWebPageUrl

@Composable
internal actual fun rememberWebPageBrowser(initialUrl: String): WebPageBrowser = remember(initialUrl) { AndroidWebPageBrowser(initialUrl) }

private class AndroidWebPageBrowser(
    initialUrl: String,
) : WebPageBrowser(initialUrl) {
    private var view: WebView? = null

    override fun goBack() {
        view?.takeIf { it.canGoBack() }?.goBack()
    }

    override fun goForward() {
        view?.takeIf { it.canGoForward() }?.goForward()
    }

    override fun reload() {
        view?.reload()
    }

    private fun updateHistory(view: WebView, location: String?) {
        location?.takeIf(::isWebPageUrl)?.let { currentUrl = it }
        canGoBack = view.canGoBack()
        canGoForward = view.canGoForward()
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    override fun Content(modifier: Modifier) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                WebView(context).apply {
                    view = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    /** target=_blank 与用户点击触发的 window.open 在当前视图导航。 */
                    settings.setSupportMultipleWindows(false)
                    webChromeClient = object : WebChromeClient() {
                        override fun onReceivedTitle(view: WebView, title: String?) {
                            this@AndroidWebPageBrowser.title = title.orEmpty()
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            if (!request.isForMainFrame || isWebPageUrl(request.url.toString())) return false
                            errorMessage = "此链接不是 HTTP 或 HTTPS 网页"
                            return true
                        }

                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            errorMessage = null
                            this@AndroidWebPageBrowser.title = ""
                            updateHistory(view, url)
                        }

                        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) = updateHistory(view, url)

                        override fun onPageFinished(view: WebView, url: String) = updateHistory(view, view.url)

                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) {
                                updateHistory(view, request.url.toString())
                                errorMessage = "网页加载失败：${error.description}"
                            }
                        }

                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                            if (request.isForMainFrame) errorMessage = "网页返回 HTTP ${errorResponse.statusCode}"
                        }
                    }
                    loadUrl(initialUrl)
                }
            },
            onReset = null,
            onRelease = {
                view = null
                it.stopLoading()
                it.webChromeClient = null
                it.webViewClient = WebViewClient()
                it.destroy()
            },
        )
    }
}
