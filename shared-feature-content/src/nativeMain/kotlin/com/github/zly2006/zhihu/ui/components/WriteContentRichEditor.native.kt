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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.github.zly2006.zhihu.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.cinterop.ObjCSignatureOverride
import org.jetbrains.compose.resources.ExperimentalResourceApi
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSError
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationActionPolicy.WKNavigationActionPolicyAllow
import platform.WebKit.WKNavigationActionPolicy.WKNavigationActionPolicyCancel
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.NSObject
import zhihu.uicomponents.generated.resources.Res

@OptIn(ExperimentalResourceApi::class)
internal actual suspend fun readRichEditorAsset(name: String): String = Res.readBytes("files/rich-editor/$name").decodeToString()

@Composable
internal actual fun rememberRichEditorBrowser(onMessage: (String) -> Unit, onFailure: () -> Unit): RichEditorBrowser {
    val message = rememberUpdatedState(onMessage)
    val failure = rememberUpdatedState(onFailure)
    return remember { AppleRichEditorBrowser({ message.value(it) }, { failure.value() }) }
}

private class AppleRichEditorBrowser(
    private val onMessage: (String) -> Unit,
    private val onFailure: () -> Unit,
) : RichEditorBrowser() {
    private var closed = false
    private var view: WKWebView? = null
    private val controller = WKUserContentController()
    private val delegate = object : NSObject(), WKScriptMessageHandlerProtocol, WKNavigationDelegateProtocol {
        override fun userContentController(userContentController: WKUserContentController, didReceiveScriptMessage: WKScriptMessage) {
            if (!closed && didReceiveScriptMessage.webView === view && didReceiveScriptMessage.frameInfo.mainFrame) {
                (didReceiveScriptMessage.body as? String)?.let(onMessage)
            }
        }

        override fun webView(webView: WKWebView, decidePolicyForNavigationAction: WKNavigationAction, decisionHandler: (WKNavigationActionPolicy) -> Unit) {
            // 本地文档没有外部导航；链接编辑仍在 Quill 工具栏，点击链接不可离开编辑器。
            val url = decidePolicyForNavigationAction.request.URL?.absoluteString
            decisionHandler(if (!closed && url == "about:blank") WKNavigationActionPolicyAllow else WKNavigationActionPolicyCancel)
        }

        @ObjCSignatureOverride
        override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) {
            if (!closed) onFailure()
        }

        @ObjCSignatureOverride
        override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) {
            if (!closed) onFailure()
        }

        override fun webViewWebContentProcessDidTerminate(webView: WKWebView) {
            if (!closed) onFailure()
        }
    }

    @Composable
    override fun Content(document: String, modifier: Modifier) {
        val factory = remember(document) { { createView(document) } }
        if (rememberNavEntryOnTop() && (LocalAppOverlayBlurController.current?.openedOverlayCount ?: 0) == 0) {
            AppleWebPageView(modifier, factory)
        }
    }

    private fun createView(document: String): WKWebView {
        view?.let { return it }
        check(!closed)
        controller.addScriptMessageHandler(delegate, "editor")
        val configuration = WKWebViewConfiguration().apply {
            userContentController = controller
            websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore()
        }
        return WKWebView(CGRectMake(0.0, 0.0, 0.0, 0.0), configuration).also { web ->
            view = web
            web.navigationDelegate = delegate
            val bridge = "<script>window.EditorBridge={postMessage:function(message){window.webkit.messageHandlers.editor.postMessage(message)}};window.addEventListener('error',function(){EditorBridge.postMessage('{\"event\":\"error\"}')});</script>"
            web.loadHTMLString(document.replace("<!-- EDITOR_HOST_BRIDGE -->", bridge), baseURL = null)
        }
    }

    override suspend fun execute(command: String): String = suspendCancellableCoroutine { continuation ->
        val web = view
        if (closed || web == null) {
            continuation.resumeWithException(IllegalStateException("Editor closed"))
        } else web.evaluateJavaScript("window.editorCommand($command)") { result, error ->
            if (continuation.isActive) {
                if (closed || error != null) continuation.resumeWithException(IllegalStateException("Editor command failed"))
                else continuation.resume(result as? String ?: "")
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        controller.removeScriptMessageHandlerForName("editor")
        view?.navigationDelegate = null
        view?.stopLoading()
        view = null
    }
}
