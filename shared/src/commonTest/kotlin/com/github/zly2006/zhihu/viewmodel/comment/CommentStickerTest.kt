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

import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommentStickerTest {
    @Test
    fun parsesLibraryStickerSnakeCaseFields() {
        val sticker = ZhihuJson.decodeJson<CommentSticker>(
            ZhihuJson.json.parseToJsonElement(
                """
                {
                  "id": "1999524033515972103",
                  "static_image_url": "https://picx.zhimg.com/v2-a.jpg",
                  "dynamic_image_url": "https://picx.zhimg.com/v2-a.gif",
                  "title": "欢呼",
                  "group_id": "0",
                  "sticker_type": 2,
                  "is_vip": true,
                  "collected": false
                }
                """.trimIndent(),
            ),
        )

        assertEquals("1999524033515972103", sticker.id)
        assertEquals("https://picx.zhimg.com/v2-a.gif", sticker.commentUrl)
        assertEquals("https://picx.zhimg.com/v2-a.jpg", sticker.thumbnailUrl)
        assertTrue(sticker.isVipSticker)
    }

    @Test
    fun stickerHtmlMatchesRnClientContract() {
        val html = commentStickerHtml(
            CommentSticker(
                id = "sticker-1",
                staticImageUrl = "https://picx.zhimg.com/v2-a.jpg",
                title = "欢呼",
            ),
        )

        assertEquals(
            "<a class=\"comment_sticker\" href=\"https://picx.zhimg.com/v2-a.jpg\" " +
                "data-sticker-id=\"sticker-1\">[欢呼]</a>",
            html,
        )
    }

    @Test
    fun commentBodyEmbedsStickerHtml() {
        val article = Article(type = ArticleType.Answer, id = 1L)
        val body = article.buildSubmitCommentBody(
            commentText = "",
            replyToCommentId = null,
            stickers = listOf(
                CommentSticker(
                    id = "sticker-1",
                    staticImageUrl = "https://picx.zhimg.com/v2-a.jpg",
                    title = "欢呼",
                ),
            ),
        )

        val content = body["content"]!!.jsonPrimitive.content
        assertTrue(content.contains("class=\"comment_sticker\""))
        assertTrue(content.contains("data-sticker-id=\"sticker-1\""))
        // 知乎客户端只把图片计入 has_img；贴纸通过 sticker_type 标记。
        assertEquals("false", body["has_img"]!!.jsonPrimitive.content)
        assertEquals(
            "normal",
            body["sticker_type"]!!
                .jsonArray
                .first()
                .jsonPrimitive.content,
        )
    }
}
