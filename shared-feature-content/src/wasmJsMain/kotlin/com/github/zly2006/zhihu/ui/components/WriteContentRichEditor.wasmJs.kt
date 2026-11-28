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

@file:OptIn(ExperimentalWasmJsInterop::class)

package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.compose.resources.ExperimentalResourceApi
import zhihu.uicomponents.generated.resources.Res

@OptIn(ExperimentalResourceApi::class)
internal actual suspend fun readRichEditorAsset(name: String): String = suspendCancellableCoroutine { continuation ->
    val request = loadEditorAsset(Res.getUri("files/rich-editor/$name"), name) { text, succeeded ->
        if (continuation.isActive) {
            if (succeeded) continuation.resume(text) else continuation.resumeWithException(IllegalStateException("Editor resource unavailable"))
        }
    }
    continuation.invokeOnCancellation { request.cancel() }
}

private external interface EditorAssetRequest : JsAny { fun cancel() }

@JsFun("""(resource, name, done) => {
    const abort = new AbortController();
    (async () => {
        try {
            if (!['index.html','quill.js','quill.snow.css'].includes(name)) throw new Error('asset');
            const base = new URL(window.zhihuAppResourceBase);
            if (base.origin !== location.origin || !base.pathname.endsWith('/')) throw new Error('base');
            const marker = 'composeResources/';
            const relative = resource.slice(resource.indexOf(marker));
            if (!relative.startsWith(marker) || !relative.endsWith('/files/rich-editor/' + name) || relative.includes('..')) throw new Error('path');
            const url = new URL(relative, base);
            if (url.origin !== base.origin || !url.pathname.startsWith(base.pathname)) throw new Error('path');
            const response = await fetch(url, {signal: abort.signal, credentials:'omit', redirect:'error'});
            if (!response.ok) throw new Error('load');
            const text = await response.text();
            if (text.length > 3000000) throw new Error('size');
            done(text, true);
        } catch (_) { if (!abort.signal.aborted) done('', false); }
    })();
    return {cancel: () => abort.abort()};
}""")
private external fun loadEditorAsset(resource: String, name: String, done: (String, Boolean) -> Unit): EditorAssetRequest

@Composable
internal actual fun rememberRichEditorBrowser(onMessage: (String) -> Unit, onFailure: () -> Unit): RichEditorBrowser {
    val message = rememberUpdatedState(onMessage)
    val failure = rememberUpdatedState(onFailure)
    return remember { WebRichEditorBrowser({ message.value(it) }, { failure.value() }) }
}

private class WebRichEditorBrowser(private val onMessage: (String) -> Unit, private val onFailure: () -> Unit) : RichEditorBrowser() {
    private var browser: EditorFrame? = null
    private var closed = false

    @Composable
    override fun Content(document: String, modifier: Modifier) {
        val density = LocalDensity.current.density
        val onTop = rememberNavEntryOnTop() && (LocalAppOverlayBlurController.current?.openedOverlayCount ?: 0) == 0
        val frame = remember(document) { createEditorFrame(document, onMessage, onFailure).also { browser = it } }
        DisposableEffect(frame) { onDispose { frame.close(); if (browser === frame) browser = null } }
        DisposableEffect(frame, onTop) {
            frame.visible(onTop)
            onDispose { frame.visible(false) }
        }
        Spacer(modifier.onGloballyPositioned { coordinates ->
            val rect = coordinates.boundsInWindow()
            frame.layout(rect.left / density, rect.top / density, rect.width / density, rect.height / density)
        })
    }

    override suspend fun execute(command: String): String = suspendCancellableCoroutine { continuation ->
        val frame = browser
        if (closed || frame == null) continuation.resumeWithException(IllegalStateException("Editor closed"))
        else frame.execute(command) { result, succeeded ->
            if (continuation.isActive) {
                if (succeeded && !closed) continuation.resume(result) else continuation.resumeWithException(IllegalStateException("Editor command failed"))
            }
        }
    }

    override fun close() { closed = true; browser?.close(); browser = null }
}

private external interface EditorFrame : JsAny {
    fun layout(left: Float, top: Float, width: Float, height: Float)
    fun visible(value: Boolean)
    fun execute(command: String, done: (String, Boolean) -> Unit)
    fun close()
}

@JsFun("""(html, message, failed) => {
    const frame = document.createElement('iframe');
    frame.setAttribute('sandbox', 'allow-scripts');
    frame.setAttribute('referrerpolicy','no-referrer');
    frame.style.cssText = 'position:fixed;border:0;z-index:1;display:none;';
    let closed = false, visible = false, next = 0;
    const pending = new Map();
    const port = crypto.randomUUID();
    const bridge = '<script>const editorPort=' + JSON.stringify(port) + ';' +
        'window.EditorBridge={postMessage:function(value){parent.postMessage({editorPort,value},"*")}};' +
        'window.addEventListener("message",function(e){if(e.source!==parent||e.data.editorPort!==editorPort)return;try{' +
        'const value=window.editorCommand(JSON.parse(e.data.command));parent.postMessage({editorPort,id:e.data.id,result:value,ok:true},"*");' +
        '}catch(_){parent.postMessage({editorPort,id:e.data.id,result:"",ok:false},"*")}});' +
        'window.addEventListener("error",function(){EditorBridge.postMessage("{\\"event\\":\\"error\\"}")});<\/script>';
    const receive = e => {
        if (closed || e.source !== frame.contentWindow || e.data?.editorPort !== port) return;
        const data = e.data;
        if (typeof data.value === 'string' && data.value.length <= 3000000) message(data.value);
        else if (Number.isInteger(data.id)) {
            const operation = pending.get(data.id);
            if (operation) { pending.delete(data.id); clearTimeout(operation.timer); operation.done(String(data.result || ''), data.ok === true); }
        }
    };
    window.addEventListener('message', receive);
    frame.srcdoc = html.replace('<!-- EDITOR_HOST_BRIDGE -->', bridge);
    frame.onerror = () => { if (!closed) failed(); };
    document.body.appendChild(frame);
    return {
        layout(left, top, width, height) {
            if (closed) return;
            const root = document.getElementById('app')?.getBoundingClientRect();
            frame.style.left = (left + (root?.left || 0)) + 'px';
            frame.style.top = (top + (root?.top || 0)) + 'px';
            frame.style.width = Math.max(0,width) + 'px'; frame.style.height = Math.max(0,height) + 'px';
            const x = Math.max(0,-top), bottom = Math.max(0,top+height-(root?.height || innerHeight));
            frame.style.clipPath = 'inset(' + x + 'px 0px ' + bottom + 'px 0px)';
        },
        visible(value) { visible=value; frame.style.display = !closed && visible ? 'block' : 'none'; },
        execute(command, done) {
            if (closed) { done('',false); return; }
            const id=++next;
            const timer=setTimeout(()=>{const op=pending.get(id);if(op){pending.delete(id);op.done('',false)}},10000);
            pending.set(id,{done,timer});
            frame.contentWindow.postMessage({editorPort:port,id,command},'*');
        },
        close() {
            if(closed)return;closed=true;window.removeEventListener('message',receive);frame.remove();frame.srcdoc='';
            for(const op of pending.values()){clearTimeout(op.timer);op.done('',false)}pending.clear();
        }
    };
}""")
private external fun createEditorFrame(html: String, message: (String) -> Unit, failed: () -> Unit): EditorFrame
