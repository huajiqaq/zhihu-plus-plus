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
import com.github.zly2006.zhihu.util.signZhihuFetchRequest
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 语音评论上传。流程与知乎客户端一致：
 * 1) POST https://lens.zhihu.com/api/v4/videos 申请上传会话；
 * 2) PUT 到 OSS（桶 zhihu-video-input，CNAME upload-oss.vzuu.com）；
 * 3) PATCH uploading_status 上报 UPLOADING_START / UPLOADING；
 * 4) 轮询 lens 视频详情，等待服务端完成音频处理。
 *
 * 返回的 [UploadedVoiceComment.voiceId] 即评论 `voice_info.voice_id`。
 */
internal suspend fun uploadVoiceComment(
    environment: ZhihuApiEnvironment,
    bytes: ByteArray,
    durationMs: Long,
    mimeType: String = "audio/mp4",
): UploadedVoiceComment {
    val client = environment.httpClient()
    val cookies = environment.authenticatedCookies()
    val session = requestVoiceUploadSession(
        client = client,
        cookies = cookies,
        fileMd5 = ZhihuFetchSignature.md5Hex(bytes),
        fileSize = bytes.size.toLong(),
        durationMs = durationMs,
        mimeType = mimeType,
    )
    val uploadFile = session.uploadFile
        ?: throw IllegalStateException("知乎未返回语音上传文件信息")
    val videoId = uploadFile.videoId
    if (videoId.isBlank()) {
        throw IllegalStateException("知乎未返回语音 video_id")
    }

    if (uploadFile.state != 1) {
        if (uploadFile.objectKey.isBlank()) {
            throw IllegalStateException("知乎未返回语音 object_key")
        }
        val token = session.uploadToken
            ?: throw IllegalStateException("知乎未返回语音上传凭证")
        reportUploadingStatus(
            client = client,
            cookies = cookies,
            videoId = videoId,
            objectKey = uploadFile.objectKey,
            uploadId = session.uploadId,
            event = UPLOADING_START_EVENT,
        )
        uploadVoiceBytes(
            client = client,
            objectKey = uploadFile.objectKey,
            bytes = bytes,
            mimeType = mimeType,
            token = token,
        )
        reportUploadingStatus(
            client = client,
            cookies = cookies,
            videoId = videoId,
            objectKey = uploadFile.objectKey,
            uploadId = session.uploadId,
            event = UPLOADING_EVENT,
        )
    }

    pollVoiceReady(client, cookies, videoId)
    return UploadedVoiceComment(voiceId = videoId)
}

internal data class UploadedVoiceComment(
    val voiceId: String,
)

private suspend fun requestVoiceUploadSession(
    client: HttpClient,
    cookies: Map<String, String>,
    fileMd5: String,
    fileSize: Long,
    durationMs: Long,
    mimeType: String,
): VoiceUploadSession {
    val body = buildVoiceUploadRequestBody(fileMd5, fileSize, durationMs).toString()
    val response = client
        .post(LENS_VIDEOS_URL) {
            contentType(ContentType.Application.Json)
            // 客户端原样使用这个拼写（Conent），服务端按该名称读取。
            header("X-Upload-Conent-Type", mimeType)
            setBody(body)
            signZhihuFetchRequest(cookies, body)
        }.raiseForStatus(dumpRequest = true)
        .json()
    return parseVoiceUploadSession(response)
}

internal fun parseVoiceUploadSession(json: kotlinx.serialization.json.JsonElement): VoiceUploadSession =
    ZhihuJson.decodeJson(VoiceUploadSession.serializer(), json)

internal fun buildVoiceUploadRequestBody(
    fileMd5: String,
    fileSize: Long,
    durationMs: Long,
): kotlinx.serialization.json.JsonObject = buildJsonObject {
    put("file_md5", fileMd5)
    put("file_size", fileSize)
    put("source", VOICE_UPLOAD_SOURCE)
    put("duration_ms", durationMs)
}

private suspend fun uploadVoiceBytes(
    client: HttpClient,
    objectKey: String,
    bytes: ByteArray,
    mimeType: String,
    token: VoiceUploadToken,
) {
    putZhihuOssObject(
        client = client,
        endpoint = OSS_UPLOAD_URL,
        bucket = OSS_BUCKET,
        objectKey = objectKey,
        bytes = bytes,
        contentType = mimeType,
        credentials = ZhihuOssCredentials(
            accessId = token.accessId,
            accessKey = token.accessKey,
            securityToken = token.accessToken,
        ),
        userAgent = OSS_USER_AGENT,
    )
}

private suspend fun reportUploadingStatus(
    client: HttpClient,
    cookies: Map<String, String>,
    videoId: String,
    objectKey: String,
    uploadId: String,
    event: String,
) {
    val body = buildJsonObject {
        put("video_id", videoId)
        put("object_key", objectKey)
        put("upload_id", uploadId)
        put("video_source", VOICE_UPLOAD_SOURCE)
        put("upload_event", event)
    }.toString()
    client
        .patch("$LENS_VIDEOS_URL/$videoId/uploading_status") {
            contentType(ContentType.Application.Json)
            setBody(body)
            signZhihuFetchRequest(cookies, body)
        }.raiseForStatus(dumpRequest = true)
}

private suspend fun pollVoiceReady(
    client: HttpClient,
    cookies: Map<String, String>,
    videoId: String,
) {
    repeat(VOICE_READY_POLL_COUNT) { index ->
        val ready = runCatching {
            val response = client
                .get("$LENS_VIDEOS_URL/$videoId") {
                    signZhihuFetchRequest(cookies)
                }.raiseForStatus(dumpRequest = true)
                .json()
            val obj = response as? kotlinx.serialization.json.JsonObject ?: return@runCatching false
            obj["playlist"] != null ||
                obj["playlist_v2"] != null ||
                obj["uploading_status"]?.toString()?.contains("success", ignoreCase = true) == true
        }.getOrDefault(false)
        if (ready) return
        if (index != VOICE_READY_POLL_COUNT - 1) delay(1_000)
    }
}

/**
 * 注意：`ZhihuJson.decodeJson` 会先把 snake_case 键转成 camelCase，
 * 所以这里的 SerialName 使用转换后的名字。
 */
@Serializable
internal data class VoiceUploadSession(
    @SerialName("uploadFile")
    val uploadFileJson: VoiceUploadFile? = null,
    @SerialName("uploadfile")
    val uploadFileAlt: VoiceUploadFile? = null,
    @SerialName("uploadId")
    val uploadId: String = "",
    @SerialName("uploadToken")
    val uploadTokenJson: VoiceUploadToken? = null,
    @SerialName("uploadtoken")
    val uploadTokenAlt: VoiceUploadToken? = null,
) {
    val uploadFile: VoiceUploadFile?
        get() = uploadFileJson ?: uploadFileAlt
    val uploadToken: VoiceUploadToken?
        get() = uploadTokenJson ?: uploadTokenAlt
}

@Serializable
internal data class VoiceUploadFile(
    @SerialName("videoId")
    val videoId: String = "",
    @SerialName("objectKey")
    val objectKey: String = "",
    val state: Int = 0,
)

@Serializable
internal data class VoiceUploadToken(
    @SerialName("accessId")
    val accessId: String = "",
    @SerialName("accessKey")
    val accessKey: String = "",
    @SerialName("accessToken")
    val accessToken: String = "",
    @SerialName("accessTimestamp")
    val accessTimestamp: String = "",
)

private const val LENS_VIDEOS_URL = "https://lens.zhihu.com/api/v4/videos"
private const val OSS_UPLOAD_URL = "https://upload-oss.vzuu.com"
private const val OSS_BUCKET = "zhihu-video-input"
private const val OSS_USER_AGENT = "aliyun-sdk-android/2.9.5"
private const val VOICE_UPLOAD_SOURCE = "comment"
private const val UPLOADING_START_EVENT = "UPLOADING_START"
private const val UPLOADING_EVENT = "UPLOADING"
private const val VOICE_READY_POLL_COUNT = 10
