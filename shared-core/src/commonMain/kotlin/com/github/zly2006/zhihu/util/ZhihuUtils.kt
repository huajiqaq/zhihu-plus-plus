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

package com.github.zly2006.zhihu.util

import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.i18n.AppLanguage
import com.github.zly2006.zhihu.i18n.AppLocale
import com.github.zly2006.zhihu.i18n.CoreStrings
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlin.jvm.JvmName
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonElement

class HttpStatusException(
    val status: HttpStatusCode,
    val requestUrl: Url,
    val bodyText: String,
    val requestMethod: HttpMethod? = null,
) : Exception("HTTP ${status.value} ${status.description}")

suspend fun HttpResponse.raiseForStatus(): HttpResponse {
    if (status.value >= 400) {
        throw HttpStatusException(
            status = status,
            requestUrl = request.url,
            bodyText = bodyAsText(),
            requestMethod = request.method,
        )
    }
    return this
}

fun Url.isZhihuLoginRiskControl(): Boolean =
    protocol == URLProtocol.HTTPS &&
        (host == "zhihu.com" || host.endsWith(".zhihu.com")) &&
        encodedPath.split('/').any { it == "unhuman" || it == "risk_control" }

fun formatCompactCount(count: Int): String {
    val english = AppLocale.current == AppLanguage.English
    val scale = when {
        !english && count >= 10_000 -> 10_000
        english && count >= 1_000_000_000 -> 1_000_000_000
        english && count >= 1_000_000 -> 1_000_000
        english && count >= 1_000 -> 1_000
        else -> return count.toString()
    }
    val tenths = (count.toLong() * 10 + scale / 2) / scale
    val number = if (tenths % 10 == 0L && (english || count % scale == 0)) (tenths / 10).toString() else (tenths / 10.0).toString()
    return when (scale) {
        1_000_000_000 -> CoreStrings.compactBillions(number)
        1_000_000 -> CoreStrings.compactMillions(number)
        1_000 -> CoreStrings.compactThousands(number)
        else -> CoreStrings.compactTenThousands(number)
    }
}

fun formatDailyDate(dateString: String): String {
    if (dateString.length != 8 || dateString.any { !it.isDigit() }) {
        return dateString
    }
    return CoreStrings.dailyDate(dateString.substring(0, 4), dateString.substring(4, 6), dateString.substring(6, 8))
}

fun formatRelativeTime(
    epochSeconds: Long,
    nowEpochSeconds: Long = Clock.System.now().epochSeconds,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val diff = nowEpochSeconds - epochSeconds

    return when {
        diff < 60 -> CoreStrings.justNow
        diff < 3_600 -> CoreStrings.minutesAgo(diff / 60)
        diff < 86_400 -> CoreStrings.hoursAgo(diff / 3_600)
        diff < 604_800 -> CoreStrings.daysAgo(diff / 86_400)
        else -> {
            val dateTime = Instant
                .fromEpochSeconds(epochSeconds)
                .toLocalDateTime(timeZone)
            val currentYear = Instant.fromEpochSeconds(nowEpochSeconds).toLocalDateTime(timeZone).year
            val date = "${(dateTime.month.ordinal + 1).twoDigitString()}-${dateTime.day.twoDigitString()}"
            if (dateTime.year == currentYear) {
                "$date ${dateTime.hour.twoDigitString()}:${dateTime.minute.twoDigitString()}"
            } else {
                "${dateTime.year}-$date"
            }
        }
    }
}

fun formatFullDateTime(epochSeconds: Long): String {
    val dateTime = Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(TimeZone.currentSystemDefault())
    return buildString {
        append(dateTime.year.toString().padStart(4, '0'))
        append('-')
        append(dateTime.month.number.twoDigitString())
        append('-')
        append(dateTime.day.twoDigitString())
        append(' ')
        append(dateTime.hour.twoDigitString())
        append(':')
        append(dateTime.minute.twoDigitString())
        append(':')
        append(dateTime.second.twoDigitString())
    }
}

fun Int.twoDigitString(): String = toString().padStart(2, '0')

fun extractImageUrl(attribute: (String) -> String): String? =
    attribute("data-original-token")
        .takeIf { it.startsWith("v2-") }
        ?.let { "https://pic1.zhimg.com/$it" }
        ?: attribute("data-original").takeIf { it.isNotBlank() }
        ?: attribute("data-default-watermark-src").takeIf { it.isNotBlank() }
        ?: attribute("data-actualsrc").takeIf { it.isNotBlank() }
        ?: attribute("data-thumbnail").takeIf { it.isNotBlank() }
        ?: attribute("src").takeIf { it.isNotBlank() }

@JvmName("jsonTyped")
suspend inline fun <reified T> HttpResponse.json() = ZhihuJson.decodeJson<T>(this.body<JsonElement>())
