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

package com.github.zly2006.zhihu.viewmodel.comment

import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommentImageBodyTest {
    @Test
    fun imageOnlyCommentBuildsCommentImageMarkup() {
        val body = article().buildSubmitCommentBody(
            commentText = "",
            replyToCommentId = null,
            images = listOf(CommentImageAttachment("https://pic1.zhimg.com/a.jpg", 640, 480)),
        )

        assertEquals(
            "<a href=\"https://pic1.zhimg.com/a.jpg\" class=\"comment_img\" " +
                "data-width=\"640\" data-height=\"480\">查看图片</a>",
            body["content"]!!.jsonPrimitive.content,
        )
        assertEquals("true", body["has_img"]!!.jsonPrimitive.content)
    }

    @Test
    fun textAndImageAreBothIncludedAndTextIsEscaped() {
        val body = article().buildSubmitCommentBody(
            commentText = "<hello>",
            replyToCommentId = null,
            images = listOf(CommentImageAttachment("https://pic1.zhimg.com/a.jpg", 1, 2)),
        )
        val content = body["content"]!!.jsonPrimitive.content

        assertTrue(content.startsWith("<p>&lt;hello&gt;</p>"))
        assertTrue(content.contains("class=\"comment_img\""))
    }

    @Test
    fun syncToPinAddsSelectedSetting() {
        val body = article().buildSubmitCommentBody(
            commentText = "哈哈哈",
            replyToCommentId = null,
            syncToPin = true,
        )

        assertEquals(
            listOf("publish_sync_pin"),
            body["selected_settings"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals("false", body["has_img"]!!.jsonPrimitive.content)
    }

    @Test
    fun syncToPinDisabledKeepsSelectedSettingsEmpty() {
        val body = article().buildSubmitCommentBody(
            commentText = "哈哈哈",
            replyToCommentId = null,
        )

        assertTrue(body["selected_settings"]!!.jsonArray.isEmpty())
    }

    @Test
    fun mentionIsRenderedAsMemberMentionMarkup() {
        val body = article().buildSubmitCommentBody(
            commentText = "@张三 你好",
            replyToCommentId = null,
            mentions = listOf(CommentMention(userId = "u1", name = "张三", urlToken = "zhangsan")),
        )

        assertEquals(
            "<p><a data-hash=\"u1\" href=\"/people/zhangsan\" " +
                "class=\"member_mention\">@张三 </a>你好</p>",
            body["content"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun bodyMatchesCommentV5Contract() {
        val body = article().buildSubmitCommentBody(
            commentText = "hi",
            replyToCommentId = "c1",
        )

        assertEquals("", body["comment_id"]!!.jsonPrimitive.content)
        assertEquals("", body["extra_params"]!!.jsonPrimitive.content)
        assertEquals("c1", body["reply_comment_id"]!!.jsonPrimitive.content)
        assertEquals("0", body["score"]!!.jsonPrimitive.content)
        assertEquals("strict", body["unfriendly_check"]!!.jsonPrimitive.content)
    }

    @Test
    fun voiceIdIsSerializedAsVoiceInfo() {
        val body = article().buildSubmitCommentBody(
            commentText = "",
            replyToCommentId = null,
            voiceId = "voice-1",
        )

        assertEquals(
            "voice-1",
            body["voice_info"]!!.jsonObject["voice_id"]!!.jsonPrimitive.content,
        )
        // 纯语音评论：没有文本、没有图片，服务端仍应收到 voice_info。
        assertEquals("", body["content"]!!.jsonPrimitive.content)
        assertEquals("false", body["has_img"]!!.jsonPrimitive.content)
    }

    private fun article() = Article(type = ArticleType.Answer, id = 1L)
}
