/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.github.zly2006.zhihu.data

import com.fleeksoft.ksoup.Ksoup
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
    val needLogin: Boolean? = null,
)

const val ZHIHU_LOGIN_REQUIRED_MESSAGE = "请您登录后查看更多专业优质内容"

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
    val redirectedLogin = verificationUrl?.let { runCatching { Url(it).parameters["need_login"] == "true" }.getOrDefault(false) } == true
    val htmlRequiresLogin = body?.takeIf { it.trimStart().startsWith('<') && ZHIHU_LOGIN_REQUIRED_MESSAGE in it }?.let { html ->
        val document = Ksoup.parse(html)
        document.select(".RichText, .QuestionRichText, .RichContent-inner, script, style, blockquote").remove()
        ZHIHU_LOGIN_REQUIRED_MESSAGE in document.body().text()
    } == true
    val kind = when {
        code == 100 && message in setOf("ERR_TICKET_NOT_EXIST", "ERR_LOGIN_TICKET_EXPIRED") -> HttpFailureKind.SESSION_EXPIRED
        error?.needLogin == true ||
            code == 40353 ||
            name == "need_login" ||
            message == "need_login" ||
            message?.contains(ZHIHU_LOGIN_REQUIRED_MESSAGE) == true ||
            redirectedLogin ||
            (url.isZhihuLoginRiskControl() && url.parameters["need_login"] == "true") ||
            htmlRequiresLogin -> HttpFailureKind.LOGIN_REQUIRED
        verificationUrl != null || url.isZhihuLoginRiskControl() -> HttpFailureKind.HUMAN_VERIFICATION
        name == "unhuman" || message == "unhuman" || code == 40352 -> HttpFailureKind.HUMAN_VERIFICATION
        else -> HttpFailureKind.FORBIDDEN
    }
    return record.copy(
        kind = kind,
        errorCode = code,
        errorName = name,
        errorMessage = message,
        verificationUrl = if (kind == HttpFailureKind.HUMAN_VERIFICATION) {
            verificationUrl ?: url.takeIf { it.isZhihuLoginRiskControl() }?.toString() ?: "https://www.zhihu.com/account/unhuman"
        } else {
            null
        },
    )
}
