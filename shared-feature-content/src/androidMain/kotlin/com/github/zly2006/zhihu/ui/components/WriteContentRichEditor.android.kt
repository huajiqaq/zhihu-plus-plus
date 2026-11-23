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
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.account.webViewFailureObserver
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream

actual val isRichEditorSupported: Boolean = true

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun WriteContentRichEditor(
    state: RichEditorState,
    placeholder: String,
    enabled: Boolean,
    modifier: Modifier,
) {
    val account = rememberZhihuAccountStore().client
    val observeHttpFailure = remember(account) { account.webViewFailureObserver() }
    val contentHeight = remember(state, account) { mutableStateOf(224.dp) }
    val main = remember { Handler(Looper.getMainLooper()) }
    val ready = remember(state, account) { CompletableDeferred<Unit>() }
    val active = remember(state, account) { mutableStateOf(true) }
    val backgroundColor = MaterialTheme.colorScheme.background
    val foreground = "#%06x".format(MaterialTheme.colorScheme.onSurface.toArgb() and 0xffffff)
    val background = "#%06x".format(backgroundColor.toArgb() and 0xffffff)
    val muted = "#%06x".format(MaterialTheme.colorScheme.onSurfaceVariant.toArgb() and 0xffffff)
    val configuration = rememberUpdatedState("editorConfigure($enabled,${Json.encodeToString(placeholder)},${Json.encodeToString(foreground)},${Json.encodeToString(background)},${Json.encodeToString(muted)})")
    val view = remember(state, account) { mutableStateOf<WebView?>(null) }

    key(state, account) {
        Box(
            modifier = modifier.fillMaxWidth().height(contentHeight.value).background(backgroundColor),
            propagateMinConstraints = true,
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        setBackgroundColor(backgroundColor.toArgb())
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.domStorageEnabled = false
                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun ready() {
                                    main.post { if (active.value) ready.complete(Unit) }
                                }

                                @JavascriptInterface
                                fun contentChanged(html: String, text: String) {
                                    main.post { if (active.value) state.update(html, text) }
                                }

                                @JavascriptInterface
                                fun resized(height: Int) {
                                    main.post { if (active.value && height > 0) contentHeight.value = height.dp }
                                }

                                @JavascriptInterface
                                fun revealCaret(top: Int, bottom: Int) {
                                    if (top < 0 || bottom <= top) return
                                    main.post {
                                        val editor = view.value ?: return@post
                                        if (!active.value || !editor.hasFocus()) return@post
                                        val density = editor.resources.displayMetrics.density
                                        // WebView 随正文增高，光标要在外层页面而不是网页视口中露出。
                                        editor.requestRectangleOnScreen(Rect(0, (top * density).toInt(), 1, ((bottom + 4) * density).toInt()))
                                    }
                                }
                            },
                            "EditorBridge",
                        )
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                                if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                                    android.util.Log.e("RichEditor", "编辑器脚本失败，行 ${message.lineNumber()}")
                                }
                                return true
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                                super.onReceivedHttpError(view, request, errorResponse)
                                observeHttpFailure(request, errorResponse)
                            }

                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                val uri = request.url
                                if (uri.scheme == "https" && uri.host == "editor.zhihu-plus.local") {
                                    val file = uri.lastPathSegment?.takeIf { it in setOf("index.html", "quill.js", "quill.snow.css") }
                                        ?: return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                                    val mime = when (file) {
                                        "quill.js" -> "application/javascript"
                                        "quill.snow.css" -> "text/css"
                                        else -> "text/html"
                                    }
                                    return WebResourceResponse(mime, "utf-8", context.assets.open("rich-editor/$file"))
                                }
                                if (uri.scheme == "https" && uri.host == "pic-private.zhihu.com") {
                                    /** 评论/草稿私有图片的签名 URL 仍需当前账号 Cookie；认证仅限已验证的 pic-private.zhihu.com。 */
                                    return runCatching {
                                        runBlocking {
                                            val response = account.httpClient().get(uri.toString())
                                            check(response.status.isSuccess()) { "图片加载失败：${response.status}" }
                                            val mime = response.headers["Content-Type"]?.substringBefore(';') ?: "image/jpeg"
                                            check(mime.startsWith("image/")) { "图片服务返回了非图片内容" }
                                            WebResourceResponse(mime, null, ByteArrayInputStream(response.body<ByteArray>()))
                                        }
                                    }.getOrElse { WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))) }
                                }
                                if (uri.scheme != "https") return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                                return null
                            }
                        }
                        view.value = this
                        loadUrl("https://editor.zhihu-plus.local/index.html")
                    }
                },
                update = { it.setBackgroundColor(backgroundColor.toArgb()) },
            )
            if (!state.isReady) {
                Box(
                    modifier = Modifier.matchParentSize().background(backgroundColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier.padding(AppDimens.Space16),
                        verticalArrangement = Arrangement.spacedBy(AppDimens.Space12),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AppSpinner()
                        Text(
                            text = "正在加载图文编辑器",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
    LaunchedEffect(state, account) {
        ready.await()
        withContext(Dispatchers.Main.immediate) {
            /** 配置与初始正文同批执行后才标记就绪，避免显示未配置的编辑器。 */
            view.value?.evaluateJavascript("${configuration.value};editorLoad(${Json.encodeToString(state.html)})") {
                if (active.value) state.setReady(true)
            }
        }
        state.commands.collect { command ->
            withContext(Dispatchers.Main.immediate) {
                when (command) {
                    is RichEditorCommand.Load -> view.value?.evaluateJavascript("editorLoad(${Json.encodeToString(command.html)})", null)
                    is RichEditorCommand.Image -> view.value?.evaluateJavascript("editorImage(${Json.encodeToString(command.url)},${command.width},${command.height})", null)
                    is RichEditorCommand.Snapshot -> {
                        view.value?.evaluateJavascript("JSON.stringify({html:quill.getSemanticHTML(),text:quill.getText()})") { value ->
                            runCatching {
                                check(active.value) { "编辑器已关闭" }
                                val result = Json.parseToJsonElement(Json.decodeFromString<String>(value)).jsonObject
                                val html = result.getValue("html").jsonPrimitive.content
                                val text = result.getValue("text").jsonPrimitive.content
                                state.update(html, text)
                                command.result.complete(html)
                            }.onFailure { command.result.completeExceptionally(it) }
                        } ?: command.result.completeExceptionally(IllegalStateException("编辑器已关闭"))
                    }
                }
            }
        }
    }
    LaunchedEffect(state, account, state.isReady, enabled, placeholder, foreground, background, muted) {
        if (!state.isReady) return@LaunchedEffect
        ready.await()
        withContext(Dispatchers.Main.immediate) {
            view.value?.evaluateJavascript(configuration.value, null)
        }
    }
    DisposableEffect(state, account) {
        onDispose {
            active.value = false
            state.setReady(false)
            ready.cancel()
            view.value?.removeJavascriptInterface("EditorBridge")
            view.value?.stopLoading()
            view.value?.destroy()
            view.value = null
        }
    }
}
