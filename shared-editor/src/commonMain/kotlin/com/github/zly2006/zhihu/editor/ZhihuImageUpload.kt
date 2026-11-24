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

import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.util.ZhihuFetchSignature
import com.github.zly2006.zhihu.util.json
import com.github.zly2006.zhihu.util.raiseForStatus
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.min

/** 来源：zhihu_obsidian/src/image_service.ts；GIF 使用 OSS 分片上传。 */
suspend fun uploadZhihuImage(
    environment: ZhihuApiEnvironment,
    bytes: ByteArray,
    mimeType: String?,
    fileName: String?,
    source: ZhihuImageUploadSource,
): UploadedZhihuImage {
    if (bytes.isEmpty()) {
        throw IllegalStateException("无法读取所选图片的内容，请重试或换一张图片")
    }
    val client = environment.httpClient()
    val contentType = mimeType?.takeIf { it.startsWith("image/") }
        ?: guessMimeTypeFromFileName(fileName)
        ?: throw UnknownImageFormatException()
    val inputFormat = guessExtension(contentType, fileName)
        ?: throw UnknownImageFormatException()
    val (rawWidth, rawHeight) = decodeImageSize(bytes)
    val md5Hex = ZhihuFetchSignature.md5Hex(bytes)
    val applyJson = client
        .post("https://api.zhihu.com/images") {
            contentType(ContentType.Application.Json)
            setBody(ApplyImageUploadRequest(imageHash = md5Hex, source = source.apiValue))
        }.raiseForStatus()
        .json()
    val applyResponse = ZhihuJson.decodeJson(ApplyImageUploadResponse.serializer(), applyJson)
    val imageId = applyResponse.uploadFile.imageId
    val uploadFileState = applyResponse.uploadFile.state

    if (uploadFileState == 2) {
        val uploadToken = applyResponse.uploadToken
            ?: throw IllegalStateException(
                "知乎返回的图片需要重新上传，但响应里缺少 uploadToken。imageId=$imageId, state=$uploadFileState",
            )
        if (contentType.equals("image/gif", ignoreCase = true)) {
            uploadGifMultipart(client, md5Hex, bytes, uploadToken)
        } else {
            putZhihuOssObject(
                client = client,
                endpoint = "https://zhihu-pics-upload.zhimg.com",
                bucket = "zhihu-pics",
                objectKey = "v2-$md5Hex",
                bytes = bytes,
                contentType = contentType,
                credentials = ZhihuOssCredentials(
                    accessId = uploadToken.accessId,
                    accessKey = uploadToken.accessKey,
                    securityToken = uploadToken.accessToken,
                ),
                userAgent = OSS_USER_AGENT,
            )
        }
        notifyUploadingStatus(client, imageId)
    }

    val status = pollImageStatus(client, imageId)
    val src = status.originalSrc?.takeIf(String::isNotBlank)
        ?: status.src?.takeIf(String::isNotBlank)
        ?: error("知乎图片处理成功但没有返回图片地址。imageId=$imageId")
    val original = status.originalSrc ?: src
    val watermarkSrc = status.watermarkSrc
    val watermark = status.watermarkFlag
    val outputFormat = status.format?.takeIf(String::isNotBlank) ?: inputFormat

    val url = normalizeZhihuImageUrl(src, outputFormat)
    val originalUrl = normalizeZhihuImageUrl(original, outputFormat)
    val watermarkUrl = watermarkSrc?.let { normalizeZhihuImageUrl(it, outputFormat) }

    return UploadedZhihuImage(
        url = url,
        originalUrl = originalUrl,
        watermark = watermark,
        watermarkMode = status.watermark,
        watermarkUrl = watermarkUrl,
        rawWidth = status.width.takeIf { it > 0 } ?: rawWidth,
        rawHeight = status.height.takeIf { it > 0 } ?: rawHeight,
        imageId = imageId,
        hash = status.originalHash?.takeIf(String::isNotBlank)
            ?: error("知乎图片处理成功但没有返回 original_hash。imageId=$imageId"),
    )
}

private suspend fun uploadGifMultipart(
    client: HttpClient,
    imageHash: String,
    bytes: ByteArray,
    token: UploadToken,
) {
    val date = rfc1123Now()
    val ossUserAgent = OSS_USER_AGENT
    val uploadId = initMultipartUpload(client, imageHash, token, date, ossUserAgent)

    val partSize = 1024 * 1024
    val etags = mutableListOf<Pair<Int, String>>()
    var partNumber = 1
    var offset = 0
    while (offset < bytes.size) {
        val end = min(bytes.size, offset + partSize)
        val partBytes = bytes.copyOfRange(offset, end)
        val etag = uploadMultipartPart(
            client = client,
            imageHash = imageHash,
            uploadId = uploadId,
            partNumber = partNumber,
            bytes = partBytes,
            token = token,
            date = date,
            ossUserAgent = ossUserAgent,
        )
        etags.add(partNumber to etag)
        offset = end
        partNumber += 1
    }

    completeMultipartUpload(client, imageHash, uploadId, etags, token, date, ossUserAgent)
}

private suspend fun initMultipartUpload(
    client: HttpClient,
    imageHash: String,
    token: UploadToken,
    date: String,
    ossUserAgent: String,
): String {
    val stringToSign = buildZhihuOssStringToSign(
        method = "POST",
        contentType = "",
        date = date,
        securityToken = token.accessToken,
        userAgent = ossUserAgent,
        resourcePath = "/zhihu-pics/v2-$imageHash",
        subResource = "uploads",
    )
    val signature = hmacSha1Base64(token.accessKey, stringToSign)
    val authorization = "OSS ${token.accessId}:$signature"
    val response = client
        .post("https://zhihu-pics-upload.zhimg.com/v2-$imageHash?uploads") {
            header("x-oss-date", date)
            header("x-oss-user-agent", ossUserAgent)
            header("x-oss-security-token", token.accessToken)
            header(HttpHeaders.Authorization, authorization)
        }.raiseForStatus()

    val xml = response.body<String>()
    return Regex("<UploadId>([^<]+)</UploadId>").find(xml)?.groupValues?.get(1)
        ?: throw IllegalStateException("无法解析 OSS UploadId: $xml")
}

private suspend fun uploadMultipartPart(
    client: HttpClient,
    imageHash: String,
    uploadId: String,
    partNumber: Int,
    bytes: ByteArray,
    token: UploadToken,
    date: String,
    ossUserAgent: String,
): String {
    val subResource = "partNumber=$partNumber&uploadId=$uploadId"
    val stringToSign = buildZhihuOssStringToSign(
        method = "PUT",
        contentType = "application/octet-stream",
        date = date,
        securityToken = token.accessToken,
        userAgent = ossUserAgent,
        resourcePath = "/zhihu-pics/v2-$imageHash",
        subResource = subResource,
    )
    val signature = hmacSha1Base64(token.accessKey, stringToSign)
    val authorization = "OSS ${token.accessId}:$signature"

    val response = client
        .put("https://zhihu-pics-upload.zhimg.com/v2-$imageHash?$subResource") {
            contentType(ContentType.Application.OctetStream)
            header("x-oss-date", date)
            header("x-oss-user-agent", ossUserAgent)
            header("x-oss-security-token", token.accessToken)
            header(HttpHeaders.Authorization, authorization)
            setBody(bytes)
        }.raiseForStatus()

    return response.headers[HttpHeaders.ETag]
        ?: response.headers["etag"]
        ?: throw IllegalStateException("OSS 分片上传未返回 ETag")
}

private suspend fun completeMultipartUpload(
    client: HttpClient,
    imageHash: String,
    uploadId: String,
    etags: List<Pair<Int, String>>,
    token: UploadToken,
    date: String,
    ossUserAgent: String,
) {
    val subResource = "uploadId=$uploadId"
    val xmlBody = buildString {
        append("<CompleteMultipartUpload>")
        etags.forEach { (partNumber, etag) ->
            append("<Part>")
            append("<PartNumber>").append(partNumber).append("</PartNumber>")
            append("<ETag>").append(etag).append("</ETag>")
            append("</Part>")
        }
        append("</CompleteMultipartUpload>")
    }

    val stringToSign = buildZhihuOssStringToSign(
        method = "POST",
        contentType = "application/xml",
        date = date,
        securityToken = token.accessToken,
        userAgent = ossUserAgent,
        resourcePath = "/zhihu-pics/v2-$imageHash",
        subResource = subResource,
    )
    val signature = hmacSha1Base64(token.accessKey, stringToSign)
    val authorization = "OSS ${token.accessId}:$signature"

    client
        .post("https://zhihu-pics-upload.zhimg.com/v2-$imageHash?$subResource") {
            contentType(ContentType.Application.Xml)
            header("x-oss-date", date)
            header("x-oss-user-agent", ossUserAgent)
            header("x-oss-security-token", token.accessToken)
            header(HttpHeaders.Authorization, authorization)
            setBody(xmlBody)
        }.raiseForStatus()
}

private suspend fun notifyUploadingStatus(
    client: HttpClient,
    imageId: String,
) {
    client
        .put("https://api.zhihu.com/images/$imageId/uploading_status") {
            contentType(ContentType.Application.Json)
            setBody("""{"upload_result":"success"}""")
        }.raiseForStatus()
}

private suspend fun pollImageStatus(
    client: HttpClient,
    imageId: String,
): ImageStatus {
    repeat(10) { attempt ->
        val response = client
            .get("https://api.zhihu.com/images/$imageId")
            .raiseForStatus()
            .json()
        val status = ZhihuJson.decodeJson(ImageStatus.serializer(), response)
        when (status.status) {
            "success" -> return status
            "process_fail", "upload_fail" -> error("知乎图片处理失败：${status.status}，imageId=$imageId")
        }
        if (attempt < 9) delay(2_000)
    }
    error("等待知乎图片处理超时，imageId=$imageId")
}

@Serializable
private data class ApplyImageUploadRequest(
    @SerialName("image_hash")
    val imageHash: String,
    val source: String,
)

@Serializable
private data class ApplyImageUploadResponse(
    val uploadFile: UploadFile,
    val uploadToken: UploadToken? = null,
)

@Serializable
private data class UploadFile(
    val imageId: String,
    val state: Int,
)

@Serializable
private data class UploadToken(
    val accessId: String,
    val accessKey: String,
    val accessToken: String,
)

@Serializable
private data class ImageStatus(
    val status: String? = null,
    val src: String? = null,
    val originalSrc: String? = null,
    val watermark: String? = null,
    val watermarkSrc: String? = null,
    /** emoji/upload 使用服务端重编码后的 original_hash，不能用本地 MD5。 */
    val originalHash: String? = null,
    val format: String? = null,
    val width: Int = 0,
    val height: Int = 0,
) {
    val watermarkFlag: Boolean?
        get() = when (watermark?.lowercase()) {
            "watermark", "true", "1" -> true
            "original", "false", "0" -> false
            else -> null
        }
}

private const val OSS_USER_AGENT =
    "aliyun-sdk-js/6.8.0 Chrome 99.0.4844.84 on Windows 10 64-bit"

private fun decodeImageSize(bytes: ByteArray): Pair<Int, Int> =
    decodePngSize(bytes)
        ?: decodeJpegSize(bytes)
        ?: decodeGifSize(bytes)
        ?: decodeWebpSize(bytes)
        ?: (0 to 0)

private fun decodePngSize(bytes: ByteArray): Pair<Int, Int>? {
    if (bytes.size < 24 ||
        bytes[0] != 0x89.toByte() ||
        bytes[1] != 'P'.code.toByte() ||
        bytes[2] != 'N'.code.toByte() ||
        bytes[3] != 'G'.code.toByte()
    ) {
        return null
    }
    return readUInt32BigEndian(bytes, 16) to readUInt32BigEndian(bytes, 20)
}

private fun decodeJpegSize(bytes: ByteArray): Pair<Int, Int>? {
    if (bytes.size < 4 || bytes[0] != 0xff.toByte() || bytes[1] != 0xd8.toByte()) {
        return null
    }

    var offset = 2
    while (offset + 3 < bytes.size) {
        if (bytes[offset] != 0xff.toByte()) {
            offset += 1
            continue
        }
        while (offset < bytes.size && bytes[offset] == 0xff.toByte()) {
            offset += 1
        }
        if (offset >= bytes.size) return null

        val marker = bytes[offset].toInt() and 0xff
        offset += 1
        if (marker == 0xd9 || marker == 0xda) return null
        if (marker == 0x01 || marker in 0xd0..0xd7) continue
        if (offset + 1 >= bytes.size) return null

        val segmentLength = readUInt16BigEndian(bytes, offset)
        if (segmentLength < 2 || offset + segmentLength > bytes.size) return null
        if (isJpegStartOfFrame(marker)) {
            if (offset + 6 >= bytes.size) return null
            val height = readUInt16BigEndian(bytes, offset + 3)
            val width = readUInt16BigEndian(bytes, offset + 5)
            return width to height
        }
        offset += segmentLength
    }
    return null
}

private fun decodeGifSize(bytes: ByteArray): Pair<Int, Int>? {
    if (bytes.size < 10 ||
        bytes[0] != 'G'.code.toByte() ||
        bytes[1] != 'I'.code.toByte() ||
        bytes[2] != 'F'.code.toByte()
    ) {
        return null
    }
    return readUInt16LittleEndian(bytes, 6) to readUInt16LittleEndian(bytes, 8)
}

private fun decodeWebpSize(bytes: ByteArray): Pair<Int, Int>? {
    if (bytes.size < 20 ||
        !bytes.hasAscii(0, "RIFF") ||
        !bytes.hasAscii(8, "WEBP")
    ) {
        return null
    }

    var offset = 12
    while (offset + 8 <= bytes.size) {
        val chunkType = bytes.ascii(offset, 4)
        val chunkSize = readUInt32LittleEndian(bytes, offset + 4)
        val dataOffset = offset + 8
        if (chunkSize < 0 || dataOffset + chunkSize > bytes.size) return null

        when (chunkType) {
            "VP8X" -> {
                if (chunkSize >= 10) {
                    val width = readUInt24LittleEndian(bytes, dataOffset + 4) + 1
                    val height = readUInt24LittleEndian(bytes, dataOffset + 7) + 1
                    return width to height
                }
            }
            "VP8L" -> {
                if (chunkSize >= 5 && bytes[dataOffset] == 0x2f.toByte()) {
                    val bits = readUInt32LittleEndian(bytes, dataOffset + 1)
                    val width = (bits and 0x3fff) + 1
                    val height = ((bits ushr 14) and 0x3fff) + 1
                    return width to height
                }
            }
            "VP8 " -> {
                if (chunkSize >= 10 &&
                    bytes[dataOffset + 3] == 0x9d.toByte() &&
                    bytes[dataOffset + 4] == 0x01.toByte() &&
                    bytes[dataOffset + 5] == 0x2a.toByte()
                ) {
                    val width = readUInt16LittleEndian(bytes, dataOffset + 6) and 0x3fff
                    val height = readUInt16LittleEndian(bytes, dataOffset + 8) and 0x3fff
                    return width to height
                }
            }
        }

        offset = dataOffset + chunkSize + (chunkSize and 1)
    }
    return null
}

private fun isJpegStartOfFrame(marker: Int): Boolean =
    marker in 0xc0..0xcf && marker != 0xc4 && marker != 0xc8 && marker != 0xcc

private fun readUInt16BigEndian(bytes: ByteArray, offset: Int): Int =
    ((bytes[offset].toInt() and 0xff) shl 8) or
        (bytes[offset + 1].toInt() and 0xff)

private fun readUInt16LittleEndian(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() and 0xff) or
        ((bytes[offset + 1].toInt() and 0xff) shl 8)

private fun readUInt24LittleEndian(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() and 0xff) or
        ((bytes[offset + 1].toInt() and 0xff) shl 8) or
        ((bytes[offset + 2].toInt() and 0xff) shl 16)

private fun readUInt32LittleEndian(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() and 0xff) or
        ((bytes[offset + 1].toInt() and 0xff) shl 8) or
        ((bytes[offset + 2].toInt() and 0xff) shl 16) or
        ((bytes[offset + 3].toInt() and 0xff) shl 24)

private fun ByteArray.hasAscii(offset: Int, value: String): Boolean {
    if (offset + value.length > size) return false
    return value.indices.all { index -> this[offset + index] == value[index].code.toByte() }
}

private fun ByteArray.ascii(offset: Int, length: Int): String =
    CharArray(length) { index -> this[offset + index].toInt().toChar() }.concatToString()

private fun guessMimeTypeFromFileName(fileName: String?): String? = when {
    fileName == null -> null
    fileName.endsWith(".png", ignoreCase = true) -> "image/png"
    fileName.endsWith(".jpg", ignoreCase = true) || fileName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
    fileName.endsWith(".gif", ignoreCase = true) -> "image/gif"
    fileName.endsWith(".webp", ignoreCase = true) -> "image/webp"
    else -> null
}

private fun guessExtension(mimeType: String, fileName: String?): String? {
    val extFromName = fileName
        ?.substringAfterLast('.', "")
        ?.takeIf { it.isNotBlank() }
        ?.lowercase()
    return when (extFromName) {
        "png" -> "png"
        "jpg", "jpeg" -> "jpg"
        "gif" -> "gif"
        "webp" -> "webp"
        null -> when {
            mimeType.equals("image/png", ignoreCase = true) -> "png"
            mimeType.equals("image/jpeg", ignoreCase = true) -> "jpg"
            mimeType.equals("image/gif", ignoreCase = true) -> "gif"
            mimeType.equals("image/webp", ignoreCase = true) -> "webp"
            else -> null
        }

        else -> null
    }
}

private fun normalizeZhihuImageUrl(rawUrl: String, ext: String): String {
    val trimmed = rawUrl.trimEnd('.')
    val base = trimmed.substringBefore('?')
    val query = trimmed.substringAfter('?', missingDelimiterValue = "")

    val baseWithExt = run {
        val lastSegment = base.substringAfterLast('/')
        if ('.' in lastSegment) base else "$base.$ext"
    }

    return if (query.isBlank()) {
        baseWithExt
    } else {
        "$baseWithExt?$query"
    }
}
