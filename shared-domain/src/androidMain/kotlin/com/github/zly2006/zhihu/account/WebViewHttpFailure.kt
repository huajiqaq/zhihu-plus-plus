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

package com.github.zly2006.zhihu.account

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.github.zly2006.zhihu.data.HttpFailureKind
import com.github.zly2006.zhihu.data.HttpFailureRecord
import com.github.zly2006.zhihu.data.classifyHttpFailure
import com.github.zly2006.zhihu.data.isZhihuRequestHost
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.uuid.Uuid

fun ZhihuAccountClient.webViewFailureObserver(
    candidateCookies: (() -> Map<String, String>)? = null,
): (WebResourceRequest, WebResourceResponse) -> Unit {
    val owner = this
    val loggedIn = load().login
    return observer@{ request, response ->
        if (response.statusCode != 403) return@observer
        val url = runCatching { Url(request.url.toString()) }.getOrNull() ?: return@observer
        if ((url.protocol != URLProtocol.HTTP && url.protocol != URLProtocol.HTTPS) ||
            !isZhihuRequestHost(url.host)
        ) {
            return@observer
        }
        // WebView 只暴露部分请求头，不能用 CookieManager 补成实际线上请求。
        val headers = request.requestHeaders.orEmpty().mapValues { (_, value) -> listOf(value) }
        val record = HttpFailureRecord(
            id = Uuid.random().toString(),
            timestampMillis = Clock.System.now().toEpochMilliseconds(),
            method = request.method,
            url = request.url.toString(),
            status = response.statusCode,
            headers = headers,
            loggedIn = loggedIn,
            authenticatedRequest = headers.any { (name, values) ->
                name.equals(HttpHeaders.Authorization, ignoreCase = true) &&
                    values.any { it.isNotBlank() } ||
                    name.equals(HttpHeaders.Cookie, ignoreCase = true) &&
                    values.any { cookie ->
                        cookie.split(';').any { it.trim().startsWith("z_c0=") && it.substringAfter('=').isNotBlank() }
                    }
            },
            kind = HttpFailureKind.FORBIDDEN,
            source = "WebView",
        )
        val failure = classifyHttpFailure(
            record,
            location = response.responseHeaders
                ?.entries
                ?.firstOrNull { it.key.equals(HttpHeaders.Location, ignoreCase = true) }
                ?.value,
            body = null,
        )
        val candidate = candidateCookies?.invoke()?.toMap()
        // 在途失败即使 owner 已关闭仍要落盘；验证提示由所属 owner 自行门控。
        CoroutineScope(Dispatchers.IO).launch {
            try {
                owner.reportHttpFailure(failure, candidate)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // 诊断失败不改变 WebView 响应，也不输出可能含凭据的异常文本。
            }
        }
    }
}
