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

package com.github.zly2006.zhihu.data

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.observer.ResponseObserver
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.util.AttributeKey
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException

private data class RequestSession(
    val loggedIn: Boolean?,
)

private val RequestSessionKey = AttributeKey<RequestSession>("ZhihuRequestSession")

/** 首次可续期的 GET 由所属账号处理 401；诊断旁路不能在续期完成前提示退出。 */
val RecoverableTicketRequest = AttributeKey<Unit>("ZhihuRecoverableTicketRequest")

private class RequestSessionConfig {
    var loggedIn: () -> Boolean? = { null }
}

private val RequestSessionSnapshot = createClientPlugin("ZhihuRequestSessionSnapshot", ::RequestSessionConfig) {
    val snapshot = pluginConfig.loggedIn
    onRequest { request, _ -> request.attributes.put(RequestSessionKey, RequestSession(snapshot())) }
}

internal fun HttpClientConfig<*>.installHttpFailureObserver(
    loggedIn: () -> Boolean? = { null },
    onFailure: suspend (HttpFailureRecord) -> Unit,
) {
    install(RequestSessionSnapshot) { this.loggedIn = loggedIn }
    install(ResponseObserver) {
        filter { call ->
            val response = call.response
            val url = call.request.url
            val zhihu = (url.protocol == URLProtocol.HTTP || url.protocol == URLProtocol.HTTPS) &&
                isZhihuRequestHost(url.host)
            zhihu &&
                (
                    response.status.value >= 400 ||
                        resolveVerificationUrl(url, response.headers[HttpHeaders.Location]) != null ||
                        response.contentType()?.match(ContentType.Application.Json) == true ||
                        response.contentType()?.match(ContentType.Text.Html) == true
                )
        }
        onResponse { response ->
            try {
                val request = response.call.request
                val record = HttpFailureRecord(
                    id = Uuid.random().toString(),
                    timestampMillis = Clock.System.now().toEpochMilliseconds(),
                    method = request.method.value,
                    url = request.url.toString(),
                    status = response.status.value,
                    headers = request.headers.entries().associate { (name, values) -> name to values },
                    loggedIn = request.attributes.getOrNull(RequestSessionKey)?.loggedIn,
                    authenticatedRequest = request.headers[HttpHeaders.Authorization]?.isNotBlank() == true ||
                        request.headers.getAll(HttpHeaders.Cookie).orEmpty().any { cookie ->
                            cookie.split(';').any { it.trim().startsWith("z_c0=") && it.substringAfter('=').isNotBlank() }
                        },
                    kind = HttpFailureKind.FORBIDDEN,
                )
                // ResponseObserver 分叉正文，诊断读取不能消费业务 call。
                val body = try {
                    response.bodyAsText()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
                val failure = classifyHttpFailure(record, response.headers[HttpHeaders.Location], body)
                if (failure.status == 401 && failure.kind == HttpFailureKind.SESSION_EXPIRED && request.attributes.contains(RecoverableTicketRequest)) return@onResponse
                if (failure.status == 403 || failure.kind != HttpFailureKind.FORBIDDEN) onFailure(failure)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // 诊断失败不改变业务响应，也不输出可能含凭据的异常文本。
            }
        }
    }
}
