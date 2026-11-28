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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.zly2006.zhihu.editor

import com.github.zly2006.zhihu.account.requestBrowserBusiness
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.i18n.EditorStrings
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

actual suspend fun uploadZhihuImage(
    environment: ZhihuApiEnvironment,
    bytes: ByteArray,
    mimeType: String?,
    fileName: String?,
    source: ZhihuImageUploadSource,
): UploadedZhihuImage {
    require(bytes.isNotEmpty()) { EditorStrings.imageReadFailed }
    val mime = mimeType?.takeIf { it.startsWith("image/") } ?: guessMimeTypeFromFileName(fileName) ?: throw UnknownImageFormatException()
    require(mime in setOf("image/png", "image/jpeg", "image/gif", "image/webp")) { EditorStrings.unsupportedImageFormat }
    val (width, height) = decodeImageSize(bytes)
    val metadata = buildJsonObject {
        put("mime", mime)
        put("source", source.apiValue)
        put("width", width)
        put("height", height)
    }.toString()
    val (status, body) = requireNotNull(environment.accountClient).requestBrowserBusiness("uploadImage", body = metadata, bytes = bytes)
    if (status !in 200..299) {
        throw object : IllegalStateException() {
            override val message: String get() = EditorStrings.imageUploadHttpFailed(status)
        }
    }
    return ZhihuJson.json.decodeFromString(UploadedZhihuImage.serializer(), body)
}

actual suspend fun uploadVoiceComment(
    environment: ZhihuApiEnvironment,
    bytes: ByteArray,
    durationMs: Long,
    mimeType: String,
): UploadedVoiceComment {
    require(bytes.isNotEmpty()) { EditorStrings.recordingFileEmpty }
    require(durationMs > 0) { EditorStrings.recordingDurationInvalid }
    if (mimeType != "audio/mp4") {
        throw object : IllegalArgumentException() {
            override val message: String get() = EditorStrings.recordingMp4Required
        }
    }
    val metadata = buildJsonObject {
        put("mime", mimeType)
        put("durationMs", durationMs)
    }.toString()
    val (status, body) = requireNotNull(environment.accountClient).requestBrowserBusiness("uploadVoice", body = metadata, bytes = bytes)
    if (status !in 200..299) {
        throw object : IllegalStateException() {
            override val message: String get() = EditorStrings.voiceUploadHttpFailed(status)
        }
    }
    val id = ZhihuJson.json.parseToJsonElement(body).jsonObject.getValue("voiceId").jsonPrimitive.content
    check(id.isNotBlank()) { EditorStrings.voiceVideoIdMissing }
    return UploadedVoiceComment(id)
}
