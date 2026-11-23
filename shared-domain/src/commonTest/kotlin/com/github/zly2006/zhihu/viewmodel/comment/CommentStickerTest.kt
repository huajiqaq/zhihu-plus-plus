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
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
            "<a href=\"https://picx.zhimg.com/v2-a.jpg\" class=\"comment_sticker\" " +
                "data-width=\"0\" data-height=\"0\" data-sticker-id=\"sticker-1\">[欢呼]</a>",
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

    @Test
    fun collectedPageKeepsNullFieldsAndNonterminalPaging() = runTest {
        val client = HttpClient(
            MockEngine { request ->
                assertEquals(HttpMethod.Get, request.method)
                assertEquals("api.zhihu.com", request.url.host)
                assertEquals("/emoji/user/list", request.url.encodedPath)
                assertEquals("3", request.url.parameters["limit"])
                assertEquals("7", request.url.parameters["offset"])
                respond(
                    """{"data":[{"id":"sticker-1","static_image_url":"https://picx.zhimg.com/a.png","dynamic_image_url":null,"title":null,"collected":true},{"static_image_url":42},{},{"id":" ","static_image_url":"https://picx.zhimg.com/no-id.png"},{"id":"no-url","static_image_url":null,"dynamic_image_url":null}],"paging":{"is_end":false}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
        try {
            val page = fetchCollectedStickers(environment(client), limit = 3, offset = 7)
            assertEquals(false, page.isEnd)
            assertEquals(listOf("sticker-1"), page.stickers.map { it.id })
            val sticker = page.stickers.single()
            assertEquals("", sticker.dynamicImageUrl)
            assertEquals("", sticker.title)
            assertEquals("https://picx.zhimg.com/a.png", sticker.commentUrl)
            assertEquals(true, sticker.collected)
        } finally {
            client.close()
        }
    }

    @Test
    fun collectAndRemoveRejectHttp200BusinessErrors() = runTest {
        val paths = mutableListOf<String>()
        val client = HttpClient(
            MockEngine { request ->
                assertEquals(HttpMethod.Post, request.method)
                assertEquals("api.zhihu.com", request.url.host)
                paths += request.url.encodedPath
                val body = ZhihuJson.json.parseToJsonElement((request.body as TextContent).text).jsonObject
                assertEquals("comment", body.getValue("source").jsonPrimitive.content)
                assertEquals(listOf("sticker-1"), body.getValue("emoji_id").jsonArray.map { it.jsonPrimitive.content })
                respond("""{"code":40001,"message":"参数错误"}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
        try {
            val environment = environment(client)
            assertTrue(assertFailsWith<IllegalStateException> { collectCommentSticker(environment, "sticker-1") }.message.orEmpty().contains("参数错误"))
            assertTrue(assertFailsWith<IllegalStateException> { removeCollectedCommentSticker(environment, "sticker-1") }.message.orEmpty().contains("参数错误"))
            assertEquals(listOf("/emoji/collect", "/emoji/user/remove"), paths)
        } finally {
            client.close()
        }
    }

    @Test
    fun groupAndUploadKeepOnlyUsableStickersWithoutRejectingNullableImages() = runTest {
        val records = """[{"id":"dynamic-only","static_image_url":null,"dynamic_image_url":"https://picx.zhimg.com/animated.gif","title":null},{"static_image_url":42},{},{"id":"no-url","static_image_url":null,"dynamic_image_url":null}]"""
        val paths = mutableListOf<String>()
        val client = HttpClient(
            MockEngine { request ->
                paths += request.url.encodedPath
                val body = when (request.url.encodedPath) {
                    "/api/v4/sticker-groups/group-1" -> {
                        assertEquals(HttpMethod.Get, request.method)
                        """{"data":{"stickers":$records}}"""
                    }
                    "/emoji/upload" -> {
                        assertEquals(HttpMethod.Post, request.method)
                        """{"code":0,"data":{"emojis":$records}}"""
                    }
                    else -> error("不应发起贴纸请求")
                }
                respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
        try {
            val environment = environment(client)
            val group = fetchGroupStickers(environment, "group-1")
            assertEquals(listOf("dynamic-only"), group.map { it.id })
            assertEquals("", group.single().staticImageUrl)
            assertEquals("", group.single().title)
            assertEquals("https://picx.zhimg.com/animated.gif", group.single().commentUrl)
            val uploaded = uploadCommentSticker(environment, "v2-synthetic-token")
            assertEquals(listOf("dynamic-only"), uploaded.map { it.id })
            assertEquals("https://picx.zhimg.com/animated.gif", uploaded.single().commentUrl)
            assertEquals(true, uploaded.single().collected)
            assertEquals(listOf("/api/v4/sticker-groups/group-1", "/emoji/upload"), paths)
        } finally {
            client.close()
        }
    }

    @Test
    fun uploadWithOnlyDefaultOrAddresslessRecordsDoesNotReportSuccess() = runTest {
        val client = HttpClient(
            MockEngine {
                respond("""{"code":0,"data":{"emojis":[{},{"id":"no-url","dynamic_image_url":null}]}}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
        try {
            val error = assertFailsWith<IllegalStateException> { uploadCommentSticker(environment(client), "v2-synthetic-token") }
            assertEquals("表情上传失败：服务端未返回新增表情", error.message)
        } finally {
            client.close()
        }
    }

    private fun environment(client: HttpClient) = object : ZhihuApiEnvironment {
        override fun httpClient() = client

        override fun authenticatedCookies() = emptyMap<String, String>()

        override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
    }
}
