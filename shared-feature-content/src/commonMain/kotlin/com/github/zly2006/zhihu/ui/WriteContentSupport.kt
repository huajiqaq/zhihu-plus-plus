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

package com.github.zly2006.zhihu.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.github.zly2006.zhihu.i18n.ContentStrings
import com.github.zly2006.zhihu.ui.components.AppAlertDialog
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.util.HttpStatusException

@Composable
internal fun WriteOperationErrorDialog(
    message: String?,
    onDismissRequest: () -> Unit,
    onCopy: (String) -> Unit,
) {
    if (message == null) return

    AppAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(ContentStrings.operationFailed) },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        confirmButton = {
            AppButton(
                text = ContentStrings.confirm,
                onClick = onDismissRequest,
            )
        },
        dismissButton = {
            AppTextButton(
                text = ContentStrings.copy,
                onClick = { onCopy(message) },
            )
        },
    )
}

internal fun buildWriteOperationErrorMessage(
    title: String,
    throwable: Throwable,
): String {
    val chain = buildList {
        var current: Throwable? = throwable
        while (current != null && current !in this) {
            add(current)
            current = current.cause
        }
    }
    val httpStatusException = chain.filterIsInstance<HttpStatusException>().firstOrNull()
    return buildString {
        append(title).append("\n\n")
        chain.forEachIndexed { index, error ->
            append('#')
                .append(index + 1)
                .append(' ')
                .append(error::class.qualifiedName)
                .append(": ")
            if (error is HttpStatusException) {
                append("HTTP ").append(error.status.value).append(' ').append(error.status.description)
            } else {
                append(redactWriteDiagnostic(error.message.orEmpty()))
            }
            append('\n')
        }
        if (httpStatusException != null) {
            append('\n')
            append(ContentStrings.httpStatus(httpStatusException.status.value, httpStatusException.status.description)).append('\n')
            httpStatusException.requestMethod?.value?.let { append(ContentStrings.requestMethod(it)).append('\n') }
            append(ContentStrings.requestUrl(redactWriteDiagnostic(httpStatusException.requestUrl.toString()))).append('\n')
            if (httpStatusException.bodyText.isNotBlank()) {
                append('\n').append(ContentStrings.responseBody(redactWriteDiagnostic(httpStatusException.bodyText.trim()))).append('\n')
            }
            append(ContentStrings.headersCredentialsHidden)
        }
    }.trim()
}

/** 只服务创作错误的公开文案；URL、嵌套 JSON 与文本回显使用同一认证字段边界。 */
private fun redactWriteDiagnostic(text: String): String {
    val credential = "(?:cookies?|set[-_]?cookie|authorization|proxy[-_]?authorization|(?:access|refresh|id)[-_]?token|token|x[-_]?xsrf[-_]?token|xsrf|csrf|x[-_]?csrf[-_]?token|password|passwd|secret|api[-_]?key|client[-_]?secret|z_c0|d_c0|_xsrf)"
    return text
        .replace(Regex("(?i)(https?://)[^/\\s?#]*@"), "$1")
        .replace(Regex("(?i)([?&]$credential=)[^&#\\s]*")) { "${it.groupValues[1]}${ContentStrings.redacted}" }
        .replace(Regex("""(?i)("$credential"\s*:\s*)"(?:\\.|[^"\\])*["]""")) { "${it.groupValues[1]}\"${ContentStrings.redacted}\"" }
        .replace(Regex("""(?im)^.*(?:-H|--header)\s+['"].*$"""), ContentStrings.headersOmitted)
        .replace(Regex("""(?im)\b($credential)\s*[:=]\s*(?:'[^'\r\n]*'|[^\r\n,;&"}]+)""")) { "${it.groupValues[1]}=${ContentStrings.redacted}" }
        .replace(Regex("""(?i)\bBearer\s+[^\s"'<>]+"""), "Bearer ${ContentStrings.redacted}")
}
