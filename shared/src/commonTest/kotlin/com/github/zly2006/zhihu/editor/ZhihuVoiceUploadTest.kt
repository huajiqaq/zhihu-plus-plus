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
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZhihuVoiceUploadTest {
    @Test
    fun uploadRequestBodyMatchesAndroidContract() {
        val body = buildVoiceUploadRequestBody(
            fileMd5 = "0123456789abcdef0123456789abcdef",
            fileSize = 12_345L,
            durationMs = 4_321L,
        )

        assertEquals("0123456789abcdef0123456789abcdef", body["file_md5"]?.jsonPrimitive?.content)
        assertEquals("12345", body["file_size"]?.jsonPrimitive?.content)
        assertEquals("comment", body["source"]?.jsonPrimitive?.content)
        assertEquals("4321", body["duration_ms"]?.jsonPrimitive?.content)
        assertEquals(4, body.size)
    }

    @Test
    fun ossStringToSignUsesZhihuVideoInputBucket() {
        val stringToSign = buildZhihuOssStringToSign(
            method = "PUT",
            contentType = "audio/mp4",
            date = "Sun, 18 Oct 2026 00:00:00 GMT",
            securityToken = "security-token",
            userAgent = "aliyun-sdk-android/2.9.5",
            resourcePath = "/zhihu-video-input/comment/2026/voice.m4a",
        )

        assertTrue(stringToSign.startsWith("PUT\n\naudio/mp4\nSun, 18 Oct 2026 00:00:00 GMT\n"))
        assertTrue(stringToSign.contains("x-oss-security-token:security-token\n"))
        assertTrue(stringToSign.endsWith("/zhihu-video-input/comment/2026/voice.m4a"))
    }

    @Test
    fun parsesSnakeCaseLensSession() {
        val json = ZhihuJson.json.parseToJsonElement(
            """
            {
              "upload_file": {"video_id": "video-1", "object_key": "comment/a.m4a", "state": 2},
              "upload_id": "upload-1",
              "upload_token": {
                "access_id": "id",
                "access_key": "key",
                "access_token": "token",
                "access_timestamp": "123"
              }
            }
            """.trimIndent(),
        )

        val session = parseVoiceUploadSession(json)
        assertEquals("video-1", session.uploadFile?.videoId)
        assertEquals("comment/a.m4a", session.uploadFile?.objectKey)
        assertEquals(2, session.uploadFile?.state)
        assertEquals("upload-1", session.uploadId)
        assertEquals("id", session.uploadToken?.accessId)
        assertEquals("key", session.uploadToken?.accessKey)
        assertEquals("token", session.uploadToken?.accessToken)
        assertEquals("123", session.uploadToken?.accessTimestamp)
    }
}
