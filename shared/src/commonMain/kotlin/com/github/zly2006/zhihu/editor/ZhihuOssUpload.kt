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

import com.github.zly2006.zhihu.util.raiseForStatus
import com.github.zly2006.zhihu.util.twoDigitString
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock

/** 知乎 OSS 的临时上传凭证（图片和语音共用同一套签名格式）。 */
internal data class ZhihuOssCredentials(
    val accessId: String,
    val accessKey: String,
    val securityToken: String,
)

/**
 * OSS v1 签名串。图片走 `zhihu-pics` 桶，语音走 `zhihu-video-input` 桶，
 * 但 canonical resource 和 header 顺序完全一致。
 */
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

/** 单次 PUT 上传。图片（非 GIF）和语音评论都走这里。 */
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
        }.raiseForStatus(dumpRequest = true)
}

internal fun rfc1123Now(): String {
    val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
    val day = RFC1123_DAY_NAMES[now.dayOfWeek.ordinal]
    val month = RFC1123_MONTH_NAMES[now.month.ordinal]
    return "$day, ${now.day.twoDigitString()} $month ${now.year} " +
        "${now.hour.twoDigitString()}:${now.minute.twoDigitString()}:${now.second.twoDigitString()} GMT"
}

@OptIn(ExperimentalEncodingApi::class)
internal fun hmacSha1Base64(secret: String, message: String): String {
    val rawKey = secret.encodeToByteArray()
    val key = if (rawKey.size > HMAC_SHA1_BLOCK_SIZE) sha1(rawKey) else rawKey
    val keyBlock = ByteArray(HMAC_SHA1_BLOCK_SIZE)
    key.copyInto(keyBlock)

    val outerPad = ByteArray(HMAC_SHA1_BLOCK_SIZE)
    val innerPad = ByteArray(HMAC_SHA1_BLOCK_SIZE)
    for (index in keyBlock.indices) {
        outerPad[index] = (keyBlock[index].toInt() xor 0x5c).toByte()
        innerPad[index] = (keyBlock[index].toInt() xor 0x36).toByte()
    }

    return Base64.Default.encode(sha1(outerPad + sha1(innerPad + message.encodeToByteArray())))
}

private fun sha1(input: ByteArray): ByteArray {
    val bitLength = input.size.toLong() * 8
    val paddedLength = (((input.size + 8) / 64) + 1) * 64
    val padded = ByteArray(paddedLength)
    input.copyInto(padded)
    padded[input.size] = 0x80.toByte()
    for (index in 0 until 8) {
        padded[paddedLength - 1 - index] = (bitLength ushr (index * 8)).toByte()
    }

    var h0 = 0x67452301
    var h1 = 0xefcdab89.toInt()
    var h2 = 0x98badcfe.toInt()
    var h3 = 0x10325476
    var h4 = 0xc3d2e1f0.toInt()
    val words = IntArray(80)

    var chunkOffset = 0
    while (chunkOffset < padded.size) {
        for (index in 0 until 16) {
            words[index] = readUInt32BigEndian(padded, chunkOffset + index * 4)
        }
        for (index in 16 until 80) {
            words[index] = (words[index - 3] xor words[index - 8] xor words[index - 14] xor words[index - 16]).rotateLeft(1)
        }

        var a = h0
        var b = h1
        var c = h2
        var d = h3
        var e = h4

        for (index in 0 until 80) {
            val (f, k) = when (index) {
                in 0..19 -> ((b and c) or (b.inv() and d)) to 0x5a827999
                in 20..39 -> (b xor c xor d) to 0x6ed9eba1
                in 40..59 -> ((b and c) or (b and d) or (c and d)) to 0x8f1bbcdc.toInt()
                else -> (b xor c xor d) to 0xca62c1d6.toInt()
            }
            val temp = a.rotateLeft(5) + f + e + k + words[index]
            e = d
            d = c
            c = b.rotateLeft(30)
            b = a
            a = temp
        }

        h0 += a
        h1 += b
        h2 += c
        h3 += d
        h4 += e
        chunkOffset += 64
    }

    return ByteArray(20).apply {
        writeIntBigEndian(0, h0)
        writeIntBigEndian(4, h1)
        writeIntBigEndian(8, h2)
        writeIntBigEndian(12, h3)
        writeIntBigEndian(16, h4)
    }
}

internal fun readUInt32BigEndian(bytes: ByteArray, offset: Int): Int =
    ((bytes[offset].toInt() and 0xff) shl 24) or
        ((bytes[offset + 1].toInt() and 0xff) shl 16) or
        ((bytes[offset + 2].toInt() and 0xff) shl 8) or
        (bytes[offset + 3].toInt() and 0xff)

private fun ByteArray.writeIntBigEndian(offset: Int, value: Int) {
    this[offset] = (value ushr 24).toByte()
    this[offset + 1] = (value ushr 16).toByte()
    this[offset + 2] = (value ushr 8).toByte()
    this[offset + 3] = value.toByte()
}

private const val HMAC_SHA1_BLOCK_SIZE = 64
private val RFC1123_DAY_NAMES = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val RFC1123_MONTH_NAMES = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
