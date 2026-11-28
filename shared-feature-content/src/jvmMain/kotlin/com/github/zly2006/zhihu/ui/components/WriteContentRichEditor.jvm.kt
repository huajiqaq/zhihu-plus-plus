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

package com.github.zly2006.zhihu.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import java.util.concurrent.atomic.AtomicBoolean
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebView
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.compose.resources.ExperimentalResourceApi
import zhihu.uicomponents.generated.resources.Res

@OptIn(ExperimentalResourceApi::class)
internal actual suspend fun readRichEditorAsset(name: String): String = Res.readBytes("files/rich-editor/$name").decodeToString()

@Composable
internal actual fun rememberRichEditorBrowser(onMessage: (String) -> Unit, onFailure: () -> Unit): RichEditorBrowser {
    val message = rememberUpdatedState(onMessage)
    val failure = rememberUpdatedState(onFailure)
    val scope = rememberCoroutineScope()
    return remember { JvmRichEditorBrowser(scope, { message.value(it) }, { failure.value() }) }
}

private class JvmRichEditorBrowser(
    private val scope: CoroutineScope,
    private val onMessage: (String) -> Unit,
    private val onFailure: () -> Unit,
) : RichEditorBrowser() {
    private val closed = AtomicBoolean(false)
    private var webView: WebView? = null
    private val toolkitStarted = AtomicBoolean(false)
    private var loaded = false

    override suspend fun execute(command: String): String = suspendCancellableCoroutine { continuation ->
        Platform.runLater {
            if (!continuation.isActive) return@runLater
            try {
                check(!closed.get())
                val engine = checkNotNull(webView).engine
                continuation.resume(engine.executeScript("window.editorCommand($command)") as? String ?: "")
            } catch (error: Exception) {
                continuation.resumeWithException(error)
            }
        }
    }

    @Composable
    override fun Content(document: String, modifier: Modifier) {
        val panel = remember { runCatching { JFXPanel() } }
        val target = panel.getOrNull()
        if (target == null) {
            LaunchedEffect(Unit) { onFailure() }
            return
        }
        toolkitStarted.set(true)
        val visible = rememberNavEntryOnTop() && (LocalAppOverlayBlurController.current?.openedOverlayCount ?: 0) == 0
        SwingPanel(factory = { target }, modifier = modifier, update = {
            it.isVisible = visible
            Platform.setImplicitExit(false)
            Platform.runLater {
                if (closed.get() || loaded) return@runLater
                loaded = true
                try {
                    val view = WebView()
                    webView = view
                    val engine = view.engine
                    engine.isJavaScriptEnabled = true
                    // alert 被替换为本地单向消息接收，不反射 Java 对象、不弹系统对话框。
                    engine.setOnAlert { event -> scope.launch { if (!closed.get()) onMessage(event.data) } }
                    engine.setCreatePopupHandler { null }
                    engine.locationProperty().addListener { _, _, location ->
                        if (!location.isNullOrEmpty() && location != "about:blank") engine.loadWorker.cancel()
                    }
                    engine.loadWorker.stateProperty().addListener { _, _, state ->
                        if (state == Worker.State.FAILED) scope.launch { if (!closed.get()) onFailure() }
                    }
                    target.scene = Scene(view)
                    val bridge = "<script>window.EditorBridge={postMessage:function(message){window.alert(message)}};</script>"
                    engine.loadContent(document.replace("<!-- EDITOR_HOST_BRIDGE -->", bridge))
                } catch (_: Exception) {
                    scope.launch { if (!closed.get()) onFailure() }
                }
            }
        })
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        if (!toolkitStarted.get()) return
        Platform.runLater {
            webView?.engine?.let { engine ->
                engine.setOnAlert(null)
                engine.loadWorker.cancel()
                engine.load(null)
            }
            webView = null
        }
    }
}
