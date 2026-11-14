/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.github.zly2006.zhihu.data

import com.github.zly2006.zhihu.util.isZhihuLoginRiskControl
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.takeFrom
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
private data class FailureBody(
    val code: Int? = null,
    val name: String? = null,
    val message: String? = null,
    val redirect: String? = null,
)

/** 诊断仅接受知乎非空子域，不接受裸域名或相似后缀。 */
fun isZhihuRequestHost(host: String): Boolean = host.length > ".zhihu.com".length && host.endsWith(".zhihu.com", ignoreCase = true)

/** 验证地址必须来自知乎响应，且解析后的域名、协议和路径仍符合受信边界。 */
fun resolveVerificationUrl(requestUrl: Url, location: String?): String? {
    if ((requestUrl.protocol != URLProtocol.HTTP && requestUrl.protocol != URLProtocol.HTTPS) ||
        !isZhihuRequestHost(requestUrl.host)
    ) {
        return null
    }
    val target = if (location == null) {
        requestUrl
    } else {
        runCatching {
            URLBuilder(requestUrl).apply { parameters.clear() }.takeFrom(location).build()
        }.getOrNull() ?: return null
    }
    return target
        .takeIf { isZhihuRequestHost(it.host) && it.isZhihuLoginRiskControl() && it.user == null && it.password == null }
        ?.toString()
}

fun classifyHttpFailure(record: HttpFailureRecord, location: String?, body: String?): HttpFailureRecord {
    val url = runCatching { Url(record.url) }.getOrNull() ?: return record
    if ((url.protocol != URLProtocol.HTTP && url.protocol != URLProtocol.HTTPS) || !isZhihuRequestHost(url.host)) return record
    val error = body?.let {
        runCatching {
            val root = ZhihuJson.json.parseToJsonElement(it) as? JsonObject
            val value = root?.get("error")
            when (value) {
                is JsonObject -> runCatching { ZhihuJson.decodeJson(FailureBody.serializer(), value) }.getOrNull()
                null -> null
                else -> FailureBody(name = value.jsonPrimitive.content)
            }
        }.getOrNull()
    }
    val verificationUrl = resolveVerificationUrl(url, location)
        ?: resolveVerificationUrl(url, error?.redirect)
    val code = error?.code
    val name = error?.name
    val message = error?.message
    val kind = when {
        verificationUrl != null || url.isZhihuLoginRiskControl() -> HttpFailureKind.HUMAN_VERIFICATION
        code == 100 && message == "ERR_TICKET_NOT_EXIST" -> HttpFailureKind.SESSION_EXPIRED
        name == "need_login" || message == "need_login" -> HttpFailureKind.LOGIN_REQUIRED
        // need_login 优先于表示人工验证的 40352。
        name == "unhuman" || message == "unhuman" || code == 40352 -> HttpFailureKind.HUMAN_VERIFICATION
        else -> HttpFailureKind.FORBIDDEN
    }
    return record.copy(
        kind = kind,
        errorCode = code,
        errorName = name,
        errorMessage = message,
        verificationUrl = verificationUrl
            ?: url.takeIf { it.isZhihuLoginRiskControl() }?.toString()
            ?: if (kind == HttpFailureKind.HUMAN_VERIFICATION) "https://www.zhihu.com/account/unhuman" else null,
    )
}
