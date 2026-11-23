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

import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.takeFrom
import kotlinx.serialization.Serializable

@Serializable
enum class HttpFailureKind(
    val label: String,
) {
    LOGIN_REQUIRED("需要登录"),
    SESSION_EXPIRED("登录已失效"),
    HUMAN_VERIFICATION("需要人工验证"),
    FORBIDDEN("其他访问拒绝"),
}

/** 请求时的状态与响应分类分别保存；本机有登录记录不代表服务端仍认可凭据。 */
@Serializable
data class HttpFailureRecord(
    val id: String,
    val timestampMillis: Long,
    val method: String,
    val url: String,
    val status: Int,
    val headers: Map<String, List<String>>,
    val loggedIn: Boolean?,
    val authenticatedRequest: Boolean,
    val kind: HttpFailureKind,
    val errorCode: Int? = null,
    val errorName: String? = null,
    val errorMessage: String? = null,
    val verificationUrl: String? = null,
    val source: String = "Ktor",
)

/** 凭据不进入403反馈记录或剪贴板。 */
fun isSensitiveRequestHeader(name: String): Boolean {
    val key = name.lowercase()
    return key.contains("cookie") ||
        key.contains("authorization") ||
        key.contains("token") ||
        key.contains("secret") ||
        key.contains("signature") ||
        key.contains("api-key") ||
        key.contains("apikey") ||
        key.contains("csrf") ||
        key.contains("xsrf") ||
        key.startsWith("x-zse-") ||
        key.startsWith("x-zst-") ||
        key == "x-udid" ||
        key == "x-device-id"
}

fun HttpFailureRecord.redacted(): HttpFailureRecord = copy(
    url = diagnosticDisplayUrl(url),
    headers = headers.mapValues { (name, values) ->
        values.map { value ->
            when {
                isSensitiveRequestHeader(name) -> "已隐藏"
                value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true) ->
                    diagnosticDisplayUrl(value)
                else -> value
            }
        }
    },
    errorName = errorName?.let { "已隐藏" },
    errorMessage = errorMessage?.let { "已隐藏" },
    verificationUrl = verificationUrl?.let(::diagnosticDisplayUrl),
)

fun HttpFailureRecord.feedbackText(): String = redacted().let { record ->
    buildString {
        appendLine("遇到 HTTP 403，请作者检查接口是否需要更新。")
        appendLine("请求来源：${record.source}")
        appendLine("请求：${record.method} ${record.url}")
        appendLine("响应分类：${record.kind.label}")
        appendLine(
            "本机登录状态：${when (record.loggedIn) {
                true -> "已登录"
                false -> "未登录"
                null -> "未知"
            }}",
        )
        appendLine("可见请求头携带认证：${if (record.authenticatedRequest) "是" else "否"}")
        record.errorCode?.let { appendLine("错误码：$it") }
        appendLine("请求头（Token、Cookie、签名与地址参数已脱敏）：")
        record.headers.forEach { (name, values) -> values.forEach { appendLine("$name: $it") } }
    }
}

/** 诊断正文与剪贴板共用脱敏结果，保留堆栈结构但隐藏凭据字段和地址参数。 */
fun redactDiagnosticText(text: String): String {
    fun sensitive(name: String): Boolean = isSensitiveRequestHeader(name) ||
        name.lowercase() in setOf("z_c0", "d_c0", "_xsrf", "password", "passwd", "pwd")

    val urlsRedacted = Regex("""https?://[^\s<>\"']+""", RegexOption.IGNORE_CASE)
        .replace(text) { diagnosticDisplayUrl(it.value) }
    val quotedFields = Regex("""((["'])([A-Za-z0-9_-]+)\2\s*[:=]\s*)(?:"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|(?![\{\[])[^\s,}\]]+)""")
    val fieldsRedacted = quotedFields.replace(urlsRedacted) { match ->
        if (sensitive(match.groupValues[3])) match.groupValues[1] + "\"已隐藏\"" else match.value
    }
    val field = Regex("""(?:^[ \t]*|[{\[,;][ \t]*)([A-Za-z0-9_-]+)[ \t]*[:=][ \t]*""")
    return fieldsRedacted.lineSequence().joinToString("\n") { line ->
        val credential = field.findAll(line).firstOrNull { sensitive(it.groupValues[1]) }
        if (credential == null) line else line.substring(0, credential.range.last + 1) + "已隐藏"
    }
}

private fun diagnosticDisplayUrl(rawUrl: String): String {
    val url = runCatching { Url(rawUrl) }.getOrNull() ?: return "无法显示的请求地址"
    return URLBuilder()
        .apply {
            takeFrom(url)
            user = null
            password = null
            fragment = ""
            // 查询参数也可能携带凭据；默认仅保留字段名，避免未知认证字段漏出。
            parameters.names().toTypedArray().forEach { name ->
                parameters.remove(name)
                parameters.append(name, "已隐藏")
            }
        }.buildString()
}
