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

package com.github.zly2006.zhihu.editor

import com.github.zly2006.zhihu.util.hmacSha1
import com.github.zly2006.zhihu.util.raiseForStatus
import com.github.zly2006.zhihu.util.twoDigitString
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

internal data class ZhihuOssCredentials(
    val accessId: String,
    val accessKey: String,
    val securityToken: String,
)

/** OSS v1 的 canonical headers 顺序及桶路径参与签名。 */
internal fun buildZhihuOssStringToSign(
    method: String,
    contentType: String,
    date: String,
    securityToken: String,
    userAgent: String,
    resourcePath: String,
    subResource: String? = null,
): String {
    val canonicalHeaders = buildString {
        append("x-oss-date:").append(date).append('\n')
        append("x-oss-security-token:").append(securityToken).append('\n')
        append("x-oss-user-agent:").append(userAgent).append('\n')
    }
    val canonicalResource = if (subResource.isNullOrBlank()) {
        resourcePath
    } else {
        "$resourcePath?$subResource"
    }
    return buildString {
        append(method).append('\n')
        append('\n')
        append(contentType).append('\n')
        append(date).append('\n')
        append(canonicalHeaders)
        append(canonicalResource)
    }
}

internal suspend fun putZhihuOssObject(
    client: HttpClient,
    endpoint: String,
    bucket: String,
    objectKey: String,
    bytes: ByteArray,
    contentType: String,
    credentials: ZhihuOssCredentials,
    userAgent: String,
) {
    val date = rfc1123Now()
    val stringToSign = buildZhihuOssStringToSign(
        method = "PUT",
        contentType = contentType,
        date = date,
        securityToken = credentials.securityToken,
        userAgent = userAgent,
        resourcePath = "/$bucket/$objectKey",
    )
    val signature = hmacSha1Base64(credentials.accessKey, stringToSign)
    client
        .put("$endpoint/$objectKey") {
            contentType(ContentType.parse(contentType))
            header("x-oss-date", date)
            header("x-oss-user-agent", userAgent)
            header("x-oss-security-token", credentials.securityToken)
            header(HttpHeaders.Authorization, "OSS ${credentials.accessId}:$signature")
            setBody(bytes)
        }.raiseForStatus()
}

internal fun rfc1123Now(): String {
    val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
    val day = RFC1123_DAY_NAMES[now.dayOfWeek.ordinal]
    val month = RFC1123_MONTH_NAMES[now.month.ordinal]
    return "$day, ${now.day.twoDigitString()} $month ${now.year} " +
        "${now.hour.twoDigitString()}:${now.minute.twoDigitString()}:${now.second.twoDigitString()} GMT"
}

internal fun hmacSha1Base64(secret: String, message: String): String =
    Base64.Default.encode(hmacSha1(secret.encodeToByteArray(), message.encodeToByteArray()))

internal fun readUInt32BigEndian(bytes: ByteArray, offset: Int): Int =
    ((bytes[offset].toInt() and 0xff) shl 24) or
        ((bytes[offset + 1].toInt() and 0xff) shl 16) or
        ((bytes[offset + 2].toInt() and 0xff) shl 8) or
        (bytes[offset + 3].toInt() and 0xff)

private val RFC1123_DAY_NAMES = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val RFC1123_MONTH_NAMES = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
