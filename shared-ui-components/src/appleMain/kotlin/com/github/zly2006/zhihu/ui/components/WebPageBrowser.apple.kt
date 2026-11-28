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

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.github.zly2006.zhihu.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.github.zly2006.zhihu.i18n.WidgetsStrings
import com.github.zly2006.zhihu.navigation.isWebPageUrl
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.staticCFunction
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSURL
import platform.Foundation.NSURLErrorCancelled
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLRequest
import platform.Foundation.addObserver
import platform.Foundation.removeObserver
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationActionPolicy.WKNavigationActionPolicyAllow
import platform.WebKit.WKNavigationActionPolicy.WKNavigationActionPolicyCancel
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKNavigationResponse
import platform.WebKit.WKNavigationResponsePolicy
import platform.WebKit.WKNavigationResponsePolicy.WKNavigationResponsePolicyAllow
import platform.WebKit.WKUIDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWindowFeatures
import platform.darwin.NSObject
import platform.objc.class_addMethod
import platform.objc.class_getInstanceMethod
import platform.objc.method_getTypeEncoding
import platform.objc.object_getClass

@Composable
internal actual fun rememberWebPageBrowser(initialUrl: String): WebPageBrowser? {
    val browser = remember(initialUrl) { AppleWebPageBrowser(initialUrl) }
    DisposableEffect(browser) { onDispose(browser::dispose) }
    return browser
}

@Composable
internal expect fun AppleWebPageView(modifier: Modifier, factory: () -> WKWebView)

private val webStateKeys = listOf("URL", "title", "canGoBack", "canGoForward")

/** K/N 将 NSObject 的 KVO 类别方法导出为不可覆写的扩展；只在私有观察类上注册该 selector。 */
private val stateObserver = object : NSObject() {}.also { observer ->
    val selector = NSSelectorFromString("observeValueForKeyPath:ofObject:change:context:")
    val callback = staticCFunction(::onWebStateChanged)
    check(
        class_addMethod(
            object_getClass(observer),
            selector,
            callback.reinterpret(),
            method_getTypeEncoding(class_getInstanceMethod(NSObject, selector)),
        ),
    )
}

@Suppress("UNUSED_PARAMETER")
private fun onWebStateChanged(
    observer: COpaquePointer?,
    selector: COpaquePointer?,
    keyPath: COpaquePointer?,
    observedObject: COpaquePointer?,
    change: COpaquePointer?,
    context: COpaquePointer,
) = context.asStableRef<AppleWebPageBrowser>().get().syncState()

private class AppleWebPageBrowser(
    initialUrl: String,
) : WebPageBrowser(initialUrl) {
    private var view: WKWebView? = null
    private var observation: StableRef<AppleWebPageBrowser>? = null
    private var activeNavigation: WKNavigation? = null
    private var uncommittedRequest: NSURLRequest? = null

    private val delegate = object : NSObject(), WKNavigationDelegateProtocol, WKUIDelegateProtocol {
        override fun webView(
            webView: WKWebView,
            decidePolicyForNavigationAction: WKNavigationAction,
            decisionHandler: (WKNavigationActionPolicy) -> Unit,
        ) {
            val action = decidePolicyForNavigationAction
            val frame = action.targetFrame
            if (frame?.mainFrame != false && !acceptWebRequest(action.request)) {
                decisionHandler(WKNavigationActionPolicyCancel)
                return
            }
            if (frame?.mainFrame == true) uncommittedRequest = action.request
            decisionHandler(WKNavigationActionPolicyAllow)
        }

        override fun webView(
            webView: WKWebView,
            createWebViewWithConfiguration: WKWebViewConfiguration,
            forNavigationAction: WKNavigationAction,
            windowFeatures: WKWindowFeatures,
        ): WKWebView? {
            val action = forNavigationAction
            if (action.targetFrame == null && acceptWebRequest(action.request)) {
                load(action.request)
            }
            return null
        }

        override fun webView(
            webView: WKWebView,
            decidePolicyForNavigationResponse: WKNavigationResponse,
            decisionHandler: (WKNavigationResponsePolicy) -> Unit,
        ) {
            val response = decidePolicyForNavigationResponse
            val status = (response.response as? NSHTTPURLResponse)?.statusCode
            if (response.forMainFrame && status != null && status >= 400) errorMessage = { WidgetsStrings.webRequestFailed(status.toInt()) }
            decisionHandler(WKNavigationResponsePolicyAllow)
        }

        @ObjCSignatureOverride
        override fun webView(webView: WKWebView, didStartProvisionalNavigation: WKNavigation?) {
            activeNavigation = didStartProvisionalNavigation
            errorMessage = null
        }

        @ObjCSignatureOverride
        override fun webView(webView: WKWebView, didCommitNavigation: WKNavigation?) {
            if (didCommitNavigation == activeNavigation) uncommittedRequest = null
        }

        @ObjCSignatureOverride
        override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) =
            navigationFailed(didFailProvisionalNavigation, withError)

        @ObjCSignatureOverride
        override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) =
            navigationFailed(didFailNavigation, withError)

        override fun webViewWebContentProcessDidTerminate(webView: WKWebView) {
            errorMessage = WidgetsStrings::webProcessEnded
        }
    }

    @Composable
    override fun Content(modifier: Modifier) = AppleWebPageView(modifier, ::getOrCreateView)

    private fun getOrCreateView(): WKWebView {
        view?.let { return it }
        val webView = WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = WKWebViewConfiguration())
        view = webView
        webView.navigationDelegate = delegate
        webView.UIDelegate = delegate
        val context = StableRef.create(this).also { observation = it }.asCPointer()
        webStateKeys.forEach { (webView as NSObject).addObserver(stateObserver, forKeyPath = it, options = 0uL, context = context) }
        val url = NSURL.URLWithString(initialUrl)
        if (url?.absoluteString?.let(::isWebPageUrl) == true) {
            load(NSURLRequest.requestWithURL(url))
        } else {
            errorMessage = WidgetsStrings::invalidWebAddress
        }
        return webView
    }

    fun syncState() {
        val webView = view ?: return
        webView.URL
            ?.absoluteString
            ?.takeIf(::isWebPageUrl)
            ?.let { currentUrl = it }
        title = webView.title.orEmpty()
        canGoBack = webView.canGoBack
        canGoForward = webView.canGoForward
    }

    private fun acceptWebRequest(request: NSURLRequest): Boolean {
        if (request.URL?.absoluteString?.let(::isWebPageUrl) == true) return true
        errorMessage = WidgetsStrings::notHttpAddress
        return false
    }

    private fun load(request: NSURLRequest) {
        val webView = view ?: return
        uncommittedRequest = request
        if (webView.loadRequest(request) == null) errorMessage = WidgetsStrings::webCouldNotLoad
    }

    private fun navigationFailed(navigation: WKNavigation?, error: NSError) {
        if (navigation != null && activeNavigation != null && navigation != activeNavigation) return
        if (error.domain == NSURLErrorDomain && error.code == NSURLErrorCancelled) return
        val detail = error.localizedDescription
        errorMessage = { WidgetsStrings.webLoadFailed(detail) }
    }

    override fun goBack() {
        view?.goBack()
    }

    override fun goForward() {
        view?.goForward()
    }

    override fun reload() {
        val webView = view ?: return
        val request = uncommittedRequest
        if (request != null) {
            load(request)
        } else if (webView.reload() == null && errorMessage == null) {
            errorMessage = WidgetsStrings::webReloadFailed
        }
    }

    fun dispose() {
        val webView = view ?: return
        webView.navigationDelegate = null
        webView.UIDelegate = null
        observation?.let { ref ->
            webStateKeys.forEach { (webView as NSObject).removeObserver(stateObserver, forKeyPath = it, context = ref.asCPointer()) }
            ref.dispose()
        }
        observation = null
        webView.stopLoading()
        view = null
    }
}
