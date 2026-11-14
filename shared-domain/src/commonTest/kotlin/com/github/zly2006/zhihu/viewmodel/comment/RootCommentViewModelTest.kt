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

import androidx.lifecycle.viewModelScope
import com.github.zly2006.zhihu.data.CommentSortOrder
import com.github.zly2006.zhihu.data.SegmentInfoParagraph
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.SegmentCommentHolder
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.comment.RootCommentViewModel.Companion.submitCommentUrl
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RootCommentViewModelTest {
    @Test
    fun segmentCommentUsesBodyTargetInsteadOfQueryParameter() {
        val target = SegmentCommentHolder(
            contentId = "123",
            contentType = "answer",
            segmentId = "segment-id",
            segmentContent = "都不能称为 Rust 的杀手级项目",
            paragraphId = "KFR9BNtv",
            startOffset = 22,
            endOffset = 39,
        )

        assertEquals(
            "https://www.zhihu.com/api/v4/comment_v5/answers/123/segment/comment",
            target.submitCommentUrl,
        )

        val body = target.buildSubmitCommentBody("，", replyToCommentId = null)
        val segment = body["segment"]!!.jsonObject
        val position = segment["position"]!!.jsonObject

        assertEquals("<p>，</p>", body["content"]!!.jsonPrimitive.content)
        assertFalse("unfriendly_check" in body)
        assertFalse("selected_settings" in body)
        assertEquals("都不能称为 Rust 的杀手级项目", segment["content"]!!.jsonPrimitive.content)
        assertEquals("22", position["start"]!!.jsonObject["offset"]!!.jsonPrimitive.content)
        assertEquals("KFR9BNtv", position["start"]!!.jsonObject["paragraph_id"]!!.jsonPrimitive.content)
        assertEquals("39", position["end"]!!.jsonObject["offset"]!!.jsonPrimitive.content)
        assertEquals("KFR9BNtv", position["end"]!!.jsonObject["paragraph_id"]!!.jsonPrimitive.content)
        assertFalse("segment_id" in body)
    }

    @Test
    fun segmentRichPayloadKeepsUtf16OffsetsAndOmitsOrdinarySettings() {
        val quote = "前😀句评后"
        val start = quote.indexOf("😀")
        val end = start + "😀句评".length
        val target = SegmentCommentHolder("123", "answer", "segment-id", quote.substring(start, end), "paragraph", start, end)
        val body = target.buildSubmitCommentBody(
            commentText = "@张三 [感谢] <正文>",
            replyToCommentId = "reply-1",
            images = listOf(CommentImageAttachment("https://picx.zhimg.com/a.gif", 20, 10, isGif = true)),
            stickers = listOf(CommentSticker(id = "vip-1", staticImageUrl = "https://picx.zhimg.com/sticker.png", isVip = true)),
            mentions = listOf(CommentMention("user-1", "张三", "zhangsan")),
            syncToPin = true,
            voiceId = "voice-1",
        )
        val segment = body.getValue("segment").jsonObject
        val position = segment.getValue("position").jsonObject
        assertEquals("😀句评", segment.getValue("content").jsonPrimitive.content)
        assertEquals(
            "1",
            position
                .getValue("start")
                .jsonObject
                .getValue("offset")
                .jsonPrimitive.content,
        )
        assertEquals(
            "5",
            position
                .getValue("end")
                .jsonObject
                .getValue("offset")
                .jsonPrimitive.content,
        )
        assertEquals(
            "paragraph",
            position
                .getValue("start")
                .jsonObject
                .getValue("paragraph_id")
                .jsonPrimitive.content,
        )
        assertEquals(
            "paragraph",
            position
                .getValue("end")
                .jsonObject
                .getValue("paragraph_id")
                .jsonPrimitive.content,
        )
        assertEquals("reply-1", body.getValue("reply_comment_id").jsonPrimitive.content)
        assertEquals("true", body.getValue("has_img").jsonPrimitive.content)
        assertEquals(listOf("vip"), body.getValue("sticker_type").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(
            "voice-1",
            body
                .getValue("voice_info")
                .jsonObject
                .getValue("voice_id")
                .jsonPrimitive.content,
        )
        assertEquals(
            "<p><a data-hash=\"user-1\" href=\"/people/zhangsan\" class=\"member_mention\">@张三 </a>[感谢] &lt;正文&gt;</p>" +
                "<a href=\"https://picx.zhimg.com/a.gif\" class=\"comment_gif\" data-width=\"20\" data-height=\"10\">查看动图</a>" +
                "<a href=\"https://picx.zhimg.com/sticker.png\" class=\"comment_sticker\" data-width=\"0\" data-height=\"0\" data-sticker-id=\"vip-1\">[表情]</a>",
            body.getValue("content").jsonPrimitive.content,
        )
        assertFalse("unfriendly_check" in body)
        assertFalse("selected_settings" in body)
        assertFalse("segment_id" in body)
    }

    @Test
    fun ordinaryPayloadHasExplicitNullOptionalObjectsAndEscapesHtml() {
        val body = Article(type = ArticleType.Answer, id = 1).buildSubmitCommentBody("<&>\"'", null)
        assertEquals("<p>&lt;&amp;&gt;&quot;&#39;</p>", body.getValue("content").jsonPrimitive.content)
        assertEquals(JsonNull, body.getValue("segment"))
        assertEquals(JsonNull, body.getValue("sticker_type"))
        assertEquals(JsonNull, body.getValue("voice_info"))
        assertEquals("", body.getValue("reply_comment_id").jsonPrimitive.content)
        assertEquals("strict", body.getValue("unfriendly_check").jsonPrimitive.content)
        assertEquals(emptyList(), body.getValue("selected_settings").jsonArray.toList())
    }

    /** 合成分页边界，不作为服务端非空句评样本验收。 */
    @Test
    fun knownSegmentsKeepOpaqueNextAndRestartAfterFailureRefreshAndSort() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val target = Article(type = ArticleType.Answer, id = 123)
        val paragraphs = ZhihuJson.decodeJson<List<SegmentInfoParagraph>>(
            ZhihuJson.json.parseToJsonElement(
                """[
                  {"pid":"a","text":"没有评论","marks":[{"start_index":0,"end_index":5,"seg_info":{"seg_ids":["A"],"comment_count":1}}]},
                  {"pid":"b","text":"前被评论的句子后","marks":[{"start_index":1,"end_index":7,"seg_info":{"seg_ids":["B"],"comment_count":18}}]},
                  {"pid":"c","text":"末段","marks":[{"start_index":0,"end_index":2,"seg_info":{"seg_ids":["C"],"comment_count":1}}]}
                ]""",
            ),
        )
        val article = ArticleViewModel(target, null)
        article.seedFromCached(
            ArticleViewModel.CachedAnswerContent(
                article = target,
                title = "测试",
                authorName = "",
                authorBio = "",
                authorAvatarUrl = "",
                content = "<p data-pid=\"b\">前被评论的句子后</p>",
                voteUpCount = 0,
                commentCount = 0,
                segmentInfos = paragraphs,
            ),
        )
        val requests = mutableListOf<Url>()
        val opaqueNext = "https://www.zhihu.com/api/v4/comment_v5/answers/123/segment/root_comment?cursor=opaque%2Bnext"
        var failNext = false
        val client = HttpClient(MockEngine) {
            engine {
                this.dispatcher = dispatcher
                addHandler { request ->
                    requests += request.url
                    if (request.url.parameters["cursor"] != null && failNext) {
                        failNext = false
                        return@addHandler respond("{}", HttpStatusCode.ServiceUnavailable, headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                    val body = when {
                        request.url.encodedPath.endsWith("/segment_comment") -> """{"data":null,"segment_infos":null,"paging":{"is_end":true,"next":""}}"""
                        request.url.parameters["segment_id"] == "A" -> """{"data":[],"paging":{"is_end":true}}"""
                        request.url.parameters["segment_id"] == "B" -> """{"data":[${pagingComment("b1", "B", children = 16)}],"counts":{"total_counts":18},"paging":{"is_end":false,"next":"$opaqueNext"}}"""
                        request.url.parameters["cursor"] != null -> """{"data":[${pagingComment("b1", "B", children = 16)},${pagingComment("b2", "B")}],"paging":{"is_end":true}}"""
                        request.url.parameters["segment_id"] == "C" -> """{"data":[${pagingComment("c1", "C")}],"paging":{"is_end":true,"next":""}}"""
                        else -> error("Unexpected request ${request.url}")
                    }
                    respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
            install(ContentNegotiation) { json(ZhihuJson.json) }
        }
        val environment = object : PaginationEnvironment {
            override fun httpClient() = client

            override fun authenticatedCookies() = emptyMap<String, String>()

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
        }
        val model = RootCommentViewModel(target, sentenceOnly = true, commentSegments = { article.commentSegments })
        try {
            model.loadMore(environment)
            advanceUntilIdle()
            assertEquals(listOf(null, "A", "B"), requests.map { it.parameters["segment_id"] })
            assertEquals(listOf("b1"), model.allData.map { it.id })
            assertFalse(model.isEnd, "单段终页不能截断剩余片段")
            assertEquals(16, model.allData.single().childCommentCount)
            assertEquals("被评论的句子", article.commentSegments.getValue("B").displayText)
            val childTarget = assertIs<SegmentCommentHolder>(model.createCommentItem(model.allData.single(), target).clickTarget?.article)
            val replyBody = childTarget.buildSubmitCommentBody("回复", "b1")
            assertEquals("被评论的句子", replyBody["segment"]!!.jsonObject["content"]!!.jsonPrimitive.content)
            assertEquals(
                "1",
                replyBody["segment"]!!
                    .jsonObject["position"]!!
                    .jsonObject["start"]!!
                    .jsonObject["offset"]!!
                    .jsonPrimitive.content,
            )

            failNext = true
            model.loadMore(environment)
            advanceUntilIdle()
            assertNotNull(model.errorMessage)
            assertEquals(listOf("b1"), model.allData.map { it.id })
            model.retry(environment)
            advanceUntilIdle()
            assertNull(model.errorMessage)
            assertEquals(listOf("b1", "b2"), model.allData.map { it.id })
            assertEquals("opaque+next", requests.last().parameters["cursor"])
            assertFalse(model.isEnd)
            model.loadMore(environment)
            advanceUntilIdle()
            assertEquals(listOf("b1", "b2", "c1"), model.allData.map { it.id })
            assertTrue(model.isEnd)
            val endedRequests = requests.size
            model.loadMore(environment)
            advanceUntilIdle()
            assertEquals(endedRequests, requests.size)
            model.refresh(environment)
            advanceUntilIdle()
            assertEquals(listOf("b1"), model.allData.map { it.id }, "刷新保留的旧数据不能阻止本轮回退")
            assertFalse(model.isEnd)
            model.changeSortOrder(CommentSortOrder.TIME, environment)
            advanceUntilIdle()
            assertEquals("ts", requests.last().parameters["order_by"])
            assertEquals("A", requests[requests.lastIndex - 1].parameters["segment_id"])
            val sortedRequests = requests.size
            model.changeSortOrder(CommentSortOrder.TIME, environment)
            advanceUntilIdle()
            assertEquals(sortedRequests, requests.size)
            assertEquals(paragraphs, article.toCachedContent().segmentInfos)
        } finally {
            model.viewModelScope.cancel()
            article.viewModelScope.cancel()
            client.close()
            Dispatchers.resetMain()
        }
    }

    private fun pagingComment(id: String, segmentId: String, children: Int = 0): String = """{
      "id":"$id","type":"comment","resource_type":"segment","resource_id":"$segmentId",
      "url":"https://www.zhihu.com/comment/$id","content":"<p>测试评论</p>","created_time":1,
      "is_delete":false,"collapsed":false,"reviewing":false,"child_comment_count":$children,
      "author":{"id":"author","url_token":"author","name":"测试作者","avatar_url":"","avatar_url_template":"",
      "is_org":false,"type":"people","url":"","user_type":"people","headline":"","gender":0,"is_advertiser":false}
    }"""
}
