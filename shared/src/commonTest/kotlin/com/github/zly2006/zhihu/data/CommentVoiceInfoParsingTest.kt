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

import kotlin.test.Test
import kotlin.test.assertEquals

class CommentVoiceInfoParsingTest {
    @Test
    fun parsesVoiceInfoFromSnakeCaseJson() {
        val comment = ZhihuJson.decodeJson<DataHolder.Comment>(
            ZhihuJson.json.parseToJsonElement(
                """
                    {
                  "id": "c1",
                  "type": "comment",
                  "resource_type": "answer",
                  "url": "https://www.zhihu.com/api/v4/comment_v5/comment/c1",
                  "content": "<p>hi</p>",
                  "created_time": 1,
                  "is_delete": false,
                  "collapsed": false,
                  "reviewing": false,
                  "author": {
                    "id": "u1",
                    "url_token": "u1",
                    "name": "作者",
                    "avatar_url": "",
                    "avatar_url_template": "",
                    "is_org": false,
                    "type": "people",
                    "url": "https://www.zhihu.com/people/u1",
                    "user_type": "people",
                    "headline": "",
                    "gender": 0,
                    "is_advertiser": false
                  },
                  "voice_info": {
                    "voice_id": "voice-1",
                    "url": "https://media.zhihu.com/a.m4a",
                    "asr_text": "这是转写",
                    "duration_ms": 12345,
                    "file_size": 100,
                    "mime_type": "audio/mp4",
                    "status": "success",
                    "host_id": "host-1"
                  }
                }
                """.trimIndent(),
            ),
        )

        val voice = comment.voiceInfo
        assertEquals("voice-1", voice?.voiceId)
        assertEquals("https://media.zhihu.com/a.m4a", voice?.url)
        assertEquals("这是转写", voice?.asrText)
        assertEquals(12345, voice?.durationMs)
        assertEquals(100, voice?.fileSize)
        assertEquals("audio/mp4", voice?.mimeType)
        assertEquals("success", voice?.status)
        assertEquals("host-1", voice?.hostId)
    }
}
