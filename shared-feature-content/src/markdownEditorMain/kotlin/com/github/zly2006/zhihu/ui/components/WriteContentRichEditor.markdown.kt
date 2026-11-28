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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.i18n.ContentStrings
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.i18n.WidgetsStrings
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.utils.io.readBuffer
import kotlinx.io.readByteArray
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

actual val isRichEditorSupported: Boolean = true

/** 平台只承载隔离的本地文档；编辑、工具栏和快照协议由同一份 Quill 页面实现。 */
internal abstract class RichEditorBrowser : AutoCloseable {
    abstract suspend fun execute(command: String): String

    @Composable
    abstract fun Content(document: String, modifier: Modifier)
}

@Composable
internal expect fun rememberRichEditorBrowser(onMessage: (String) -> Unit, onFailure: () -> Unit): RichEditorBrowser
internal expect suspend fun readRichEditorAsset(name: String): String

internal suspend fun bundledRichEditorDocument(): String {
    val html = readRichEditorAsset("index.html")
    val css = readRichEditorAsset("quill.snow.css")
    val script = readRichEditorAsset("quill.js")
    // srcdoc/loadHTMLString/loadContent 都无需网络资源基址，只有安装包中的确定脚本可以执行。
    return html.replace("<link rel=\"stylesheet\" href=\"quill.snow.css\">", "<style>$css</style>")
        .replace("<script src=\"quill.js\"></script>", "<script>${script.replace("</script", "<\\/script", ignoreCase = true)}</script>")
}

@Composable
actual fun WriteContentRichEditor(state: RichEditorState, placeholder: String, enabled: Boolean, modifier: Modifier) {
    val account = rememberZhihuAccountStore().client
    key(state, account) {
        var attempt by remember { mutableStateOf(0) }
        key(attempt) {
            HostedRichEditor(state, placeholder, enabled, modifier, onRetry = { attempt++ })
        }
    }
}

@Composable
private fun HostedRichEditor(state: RichEditorState, placeholder: String, enabled: Boolean, modifier: Modifier, onRetry: () -> Unit) {
    val scope = rememberCoroutineScope()
    val account = rememberZhihuAccountStore()
    val owner = account.client
    val messages = rememberUserMessageSink()
    val density = LocalDensity.current
    val reveal = remember { BringIntoViewRequester() }
    var document by remember { mutableStateOf<String?>(null) }
    var height by remember { mutableStateOf(224.dp) }
    var failed by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(true) }
    val ready = remember { CompletableDeferred<Unit>() }
    val backgroundColor = MaterialTheme.colorScheme.background
    fun color(value: Int) = "#" + (value and 0xffffff).toString(16).padStart(6, '0')
    val configuration = buildJsonObject {
        put("action", "configure")
        put("enabled", enabled)
        put("placeholder", placeholder)
        put("foreground", color(MaterialTheme.colorScheme.onSurface.toArgb()))
        put("background", color(backgroundColor.toArgb()))
        put("muted", color(MaterialTheme.colorScheme.onSurfaceVariant.toArgb()))
        put("labels", JsonObject(richEditorUiLabels().mapValues { JsonPrimitive(it.value) }))
    }.toString()
    val latestConfiguration by rememberUpdatedState(configuration)
    fun fail() {
        if (!active) return
        failed = true
        state.setReady(false)
        ready.completeExceptionally(IllegalStateException(ContentStrings.richEditorLoadFailed))
    }
    fun updateSnapshot(result: JsonObject) {
        state.update(result.getValue("html").jsonPrimitive.content, result.getValue("text").jsonPrimitive.content)
        state.updateSelection(result["index"]?.jsonPrimitive?.intOrNull ?: state.selectionIndex, result["length"]?.jsonPrimitive?.intOrNull ?: state.selectionLength)
    }
    lateinit var browser: RichEditorBrowser
    browser = rememberRichEditorBrowser(
        onMessage = { message ->
            if (active) runCatching {
                val event = Json.parseToJsonElement(message).jsonObject
                when (event["event"]?.jsonPrimitive?.content) {
                    "ready" -> ready.complete(Unit)
                    "content" -> if (state.isReady) updateSnapshot(event)
                    "selection" -> state.updateSelection(event.getValue("index").jsonPrimitive.intOrNull ?: 0, event.getValue("length").jsonPrimitive.intOrNull ?: 0)
                    "size" -> event["height"]?.jsonPrimitive?.intOrNull?.takeIf { it in 1..1_000_000 }?.let { height = maxOf(224.dp, it.dp) }
                    "caret" -> {
                        val top = event["top"]?.jsonPrimitive?.intOrNull
                        val bottom = event["bottom"]?.jsonPrimitive?.intOrNull
                        if (top != null && bottom != null && top >= 0 && bottom > top) scope.launch {
                            with(density) { reveal.bringIntoView(Rect(0f, top.dp.toPx(), 1f, (bottom + 8).dp.toPx())) }
                        }
                    }
                    "privateImage" -> {
                        val url = event.getValue("url").jsonPrimitive.content
                        val parsed = Url(url)
                        if (parsed.protocol.name == "https" && parsed.host == "pic-private.zhihu.com" && parsed.port == 443 && parsed.user == null && parsed.password == null) scope.launch {
                            try {
                                val data = owner.withAuthenticatedClient { client, _ ->
                                    val response = client.get(url)
                                    check(response.status.isSuccess())
                                    val mime = response.headers["Content-Type"]?.substringBefore(';')
                                    check(mime in setOf("image/jpeg", "image/png", "image/gif", "image/webp"))
                                    val bytes = response.bodyAsChannel().readBuffer(20L * 1024 * 1024 + 1).readByteArray()
                                    check(bytes.size in 1..20 * 1024 * 1024)
                                    "data:$mime;base64," + Base64.encode(bytes)
                                }
                                if (active && account.client === owner) browser.execute(buildJsonObject {
                                    put("action", "imageSource"); put("url", url); put("data", data)
                                }.toString())
                            } catch (error: CancellationException) { throw error } catch (_: Exception) {
                                if (active && account.client === owner) messages.showShortMessage(WidgetsStrings.imageLoadFailed)
                            }
                        }
                    }
                    "error" -> fail()
                }
            }.onFailure { fail() }
        },
        onFailure = ::fail,
    )
    LaunchedEffect(browser) {
        try {
            document = bundledRichEditorDocument()
            withTimeout(20_000) { ready.await() }
            browser.execute(latestConfiguration)
            browser.execute(buildJsonObject {
                put("action", "load"); put("html", state.html)
                put("index", state.selectionIndex); put("length", state.selectionLength)
            }.toString())
            state.setReady(true)
            state.commands.collect { command ->
                try {
                    when (command) {
                        is RichEditorCommand.Load -> browser.execute(buildJsonObject {
                            put("action", "load"); put("html", command.html)
                        }.toString())
                        is RichEditorCommand.Image -> browser.execute(buildJsonObject {
                            put("action", "image"); put("url", command.url); put("width", command.width); put("height", command.height)
                        }.toString())
                        is RichEditorCommand.Snapshot -> {
                            val snapshot = Json.parseToJsonElement(browser.execute("{\"action\":\"snapshot\"}")).jsonObject
                            updateSnapshot(snapshot)
                            command.result.complete(state.html)
                        }
                    }
                } catch (error: Exception) {
                    (command as? RichEditorCommand.Snapshot)?.result?.completeExceptionally(error)
                    throw error
                }
            }
        } catch (error: CancellationException) {
            if (active && error is kotlinx.coroutines.TimeoutCancellationException) fail() else throw error
        } catch (_: Exception) {
            fail()
        }
    }
    LaunchedEffect(configuration, state.isReady) {
        if (state.isReady) try { browser.execute(configuration) } catch (error: CancellationException) { throw error } catch (_: Exception) { fail() }
    }
    DisposableEffect(browser) {
        onDispose {
            active = false
            state.setReady(false)
            ready.cancel()
            browser.close()
        }
    }
    Box(modifier.fillMaxWidth().height(height).bringIntoViewRequester(reveal).background(backgroundColor)) {
        document?.takeUnless { failed }?.let { browser.Content(it, Modifier.matchParentSize()) }
        if (!state.isReady) Box(Modifier.matchParentSize().background(backgroundColor), contentAlignment = Alignment.Center) {
            Column(Modifier.padding(AppDimens.Space16), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppDimens.Space12)) {
                if (failed) {
                    Text(ContentStrings.richEditorLoadFailed)
                    AppButton(text = ContentStrings.retry, onClick = onRetry)
                } else {
                    AppSpinner()
                    Text(ContentStrings.loadingRichEditor)
                }
            }
        }
    }
}
