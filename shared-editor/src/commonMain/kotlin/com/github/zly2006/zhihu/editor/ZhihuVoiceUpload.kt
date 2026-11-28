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
import com.github.zly2006.zhihu.i18n.EditorStrings
import com.github.zly2006.zhihu.util.ZhihuFetchSignature
import com.github.zly2006.zhihu.util.raiseForStatus
import com.github.zly2006.zhihu.util.signZhihuFetchRequest
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 平台负责上传事务；浏览器仅接收业务 voiceId，不持有上传密钥。 */
expect suspend fun uploadVoiceComment(
    environment: ZhihuApiEnvironment,
    bytes: ByteArray,
    durationMs: Long,
    mimeType: String = "audio/mp4",
): UploadedVoiceComment

/** Lens video_id 即 voice_info.voice_id；评论上传不等待播放列表或转写。 */
internal suspend fun uploadVoiceCommentDirect(
    environment: ZhihuApiEnvironment,
    bytes: ByteArray,
    durationMs: Long,
    mimeType: String = "audio/mp4",
): UploadedVoiceComment = environment.withAuthenticatedClient { client, cookies ->
    require(bytes.isNotEmpty()) { EditorStrings.recordingFileEmpty }
    require(durationMs > 0) { EditorStrings.recordingDurationInvalid }
    val body = buildJsonObject {
        put("file_md5", ZhihuFetchSignature.md5Hex(bytes))
        put("file_size", bytes.size.toLong())
        put("source", VOICE_UPLOAD_SOURCE)
        put("duration_ms", durationMs)
    }.toString()
    val response = client
        .post(LENS_VIDEOS_URL) {
            contentType(ContentType.Application.Json)
            /** 服务端使用官方拼写 Conent。 */
            header("X-Upload-Conent-Type", mimeType)
            setBody(body)
            signZhihuFetchRequest(cookies, body)
        }.raiseForStatus()
        .body<JsonElement>()
    val session = ZhihuJson.decodeJson(VoiceUploadSession.serializer(), response)
    val uploadFile = session.uploadFile ?: session.uploadfile
        ?: throw IllegalStateException(EditorStrings.voiceUploadFileMissing)
    val videoId = uploadFile.videoId
    if (videoId.isBlank()) {
        throw IllegalStateException(EditorStrings.voiceVideoIdMissing)
    }

    if (uploadFile.state != 1) {
        if (uploadFile.objectKey.isBlank()) {
            throw IllegalStateException(EditorStrings.voiceObjectKeyMissing)
        }
        val token = session.uploadToken ?: session.uploadtoken
            ?: throw IllegalStateException(EditorStrings.voiceUploadCredentialsMissing)
        try {
            reportUploadingStatus(
                client = client,
                cookies = cookies,
                videoId = videoId,
                objectKey = uploadFile.objectKey,
                uploadId = session.uploadId,
                event = UPLOADING_START_EVENT,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            /** 统计上报失败不取消 OSS 上传。 */
        }
        putZhihuOssObject(
            client = client,
            endpoint = OSS_UPLOAD_URL,
            bucket = OSS_BUCKET,
            objectKey = uploadFile.objectKey,
            bytes = bytes,
            contentType = mimeType,
            credentials = ZhihuOssCredentials(
                accessId = token.accessId,
                accessKey = token.accessKey,
                securityToken = token.accessToken,
            ),
            userAgent = OSS_USER_AGENT,
        )
        try {
            reportUploadingStatus(
                client = client,
                cookies = cookies,
                videoId = videoId,
                objectKey = uploadFile.objectKey,
                uploadId = session.uploadId,
                event = UPLOADING_SUCCESS_EVENT,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            /** 上传结果不依赖统计上报，播放列表和转写由服务端异步生成。 */
        }
    }

    UploadedVoiceComment(voiceId = videoId)
}

data class UploadedVoiceComment(
    val voiceId: String,
)

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
        .put("$LENS_VIDEOS_URL/$videoId/uploading_status") {
            contentType(ContentType.Application.Json)
            setBody(body)
            signZhihuFetchRequest(cookies, body)
        }.raiseForStatus()
}

@Serializable
internal data class VoiceUploadSession(
    val uploadFile: VoiceUploadFile? = null,
    val uploadfile: VoiceUploadFile? = null,
    val uploadId: String = "",
    val uploadToken: VoiceUploadToken? = null,
    val uploadtoken: VoiceUploadToken? = null,
)

@Serializable
internal data class VoiceUploadFile(
    val videoId: String = "",
    val objectKey: String = "",
    val state: Int = 0,
)

@Serializable
internal data class VoiceUploadToken(
    val accessId: String = "",
    val accessKey: String = "",
    val accessToken: String = "",
)

private const val LENS_VIDEOS_URL = "https://lens.zhihu.com/api/v4/videos"
private const val OSS_UPLOAD_URL = "https://upload-oss.vzuu.com"
private const val OSS_BUCKET = "zhihu-video-input"
private const val OSS_USER_AGENT = "aliyun-sdk-android/2.9.5"
private const val VOICE_UPLOAD_SOURCE = "comment"
private const val UPLOADING_START_EVENT = "UPLOADING_START"
private const val UPLOADING_SUCCESS_EVENT = "UPLOADING_SUCCESS"
