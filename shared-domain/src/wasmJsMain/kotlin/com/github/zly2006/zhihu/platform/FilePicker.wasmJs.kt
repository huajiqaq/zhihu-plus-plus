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

@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.github.zly2006.zhihu.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.github.zly2006.zhihu.i18n.DomainStrings
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.browser.document
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toByteArray
import org.w3c.dom.HTMLInputElement
import org.w3c.files.Blob
import org.w3c.files.FileReader

@Composable
internal actual fun rememberFilePicker(
    title: String,
    mimeTypes: List<String>,
    extensions: List<String>,
    maxBytes: Int?,
    onPicked: (suspend () -> Pair<String, RawSource>) -> Unit,
): () -> Unit {
    val currentOnPicked by rememberUpdatedState(onPicked)
    return {
        (document.createElement("input") as HTMLInputElement).apply {
            type = "file"
            setAttribute("aria-label", title)
            accept = (extensions.map { ".${it.trimStart('.')}" } + mimeTypes.filterNot { it == "*/*" }).distinct().joinToString(",")
            onchange = {
                files?.item(0)?.let { file ->
                    currentOnPicked {
                        require(file.size.toDouble() <= (maxBytes ?: Int.MAX_VALUE).toDouble()) {
                            DomainStrings.fileMaxMb((maxBytes ?: Int.MAX_VALUE) / 1024 / 1024)
                        }
                        file.name to Buffer().apply { write(file.readBytes()) }
                    }
                }
            }
            click()
        }
    }
}

private suspend fun Blob.readBytes(): ByteArray = suspendCancellableCoroutine { continuation ->
    val reader = FileReader()
    reader.onload = {
        if (continuation.isActive) continuation.resume(Int8Array(reader.result as ArrayBuffer).toByteArray())
    }
    reader.onerror = {
        if (continuation.isActive) continuation.resumeWithException(IllegalStateException(DomainStrings.browserFileReadFailed))
    }
    continuation.invokeOnCancellation { reader.abort() }
    reader.readAsArrayBuffer(this)
}
