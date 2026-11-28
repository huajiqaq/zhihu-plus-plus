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

package com.github.zly2006.zhihu.account

import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.util.DisplayFailure
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.callContext
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.content.OutgoingContent
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.JsAny
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

actual fun createDirectHttpClient(configure: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(DirectFetch) {
    configure()
    followRedirects = false
}

private object DirectFetch : HttpClientEngineFactory<HttpClientEngineConfig> {
    override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine = DirectFetchEngine(HttpClientEngineConfig().apply(block))
}

/** 只承载 AIGC/WebDAV 的文本接口，浏览器自行实施 CORS，不接入账号请求桥。 */
private class DirectFetchEngine(override val config: HttpClientEngineConfig) : HttpClientEngineBase("DirectFetch") {
    @OptIn(InternalAPI::class)
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val url = data.url
        require(url.protocol == URLProtocol.HTTPS ||
            (url.protocol == URLProtocol.HTTP && url.host in setOf("localhost", "127.0.0.1", "::1", "[::1]"))) {
            "Direct HTTP requires HTTPS except for local addresses"
        }
        require(url.user == null && url.password == null) { "URL credentials must be supplied as an explicit authorization header" }
        val body = when (val content = data.body) {
            is OutgoingContent.NoContent -> null
            is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString(throwOnInvalidSequence = true)
            else -> error("Direct HTTP only supports text request bodies")
        }
        val headers = HeadersBuilder().apply {
            appendAll(data.headers)
            appendAll(data.body.headers)
            data.body.contentType?.let { set(HttpHeaders.ContentType, it.toString()) }
        }.build()
        require(!headers.contains(HttpHeaders.Cookie)) { "Direct HTTP does not accept account cookies" }
        val headerJson = JsonObject(headers.entries().associate { (name, values) -> name to JsonPrimitive(values.joinToString(",")) }).toString()
        val started = GMTDate()
        val response = suspendCancellableCoroutine<DirectResponse> { continuation ->
            val request = startDirectFetch(url.toString(), data.method.value, headerJson, body) { status, responseHeaders, text ->
                if (continuation.isActive) {
                    if (status == 0) {
                        continuation.resumeWithException(DisplayFailure { DomainStrings.browserDirectHttpFailed })
                    } else {
                        continuation.resume(DirectResponse(status, responseHeaders, text))
                    }
                }
            }
            continuation.invokeOnCancellation { request.abort() }
        }
        val responseHeaders = HeadersBuilder().apply {
            Json.parseToJsonElement(response.headers).jsonObject.forEach { (name, value) -> append(name, value.jsonPrimitive.content) }
        }.build()
        return HttpResponseData(
            HttpStatusCode.fromValue(response.status), started, responseHeaders,
            HttpProtocolVersion.HTTP_1_1, ByteReadChannel(response.body.encodeToByteArray()), callContext(),
        )
    }
}

private data class DirectResponse(val status: Int, val headers: String, val body: String)

private external interface DirectRequestHandle : JsAny {
    fun abort()
}

@JsFun(
    """(url, method, headersJson, body, done) => {
        const controller = new AbortController();
        fetch(url, {
            method, headers: JSON.parse(headersJson), body: body === null ? undefined : body,
            mode: 'cors', credentials: 'omit', redirect: 'error', cache: 'no-store',
            referrerPolicy: 'no-referrer', signal: controller.signal
        }).then(async response => {
            const text = await response.text();
            const headers = Object.fromEntries(response.headers.entries());
            delete headers['content-encoding'];
            delete headers['content-length'];
            done(response.status, JSON.stringify(headers), text);
        }).catch(() => done(0, '{}', ''));
        return {abort: () => controller.abort()};
    }""",
)
private external fun startDirectFetch(url: String, method: String, headers: String, body: String?, done: (Int, String, String) -> Unit): DirectRequestHandle
