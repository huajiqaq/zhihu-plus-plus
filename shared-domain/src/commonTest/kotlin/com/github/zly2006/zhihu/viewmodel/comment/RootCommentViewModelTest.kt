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
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
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
import kotlin.test.assertFailsWith
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
        assertFalse(RootCommentViewModel(target).supportsSorting)

        val body = target.buildSubmitCommentBody("，", replyToCommentId = null)

        assertEquals("<p>，</p>", body["content"]!!.jsonPrimitive.content)
        assertFalse("unfriendly_check" in body)
        assertFalse("selected_settings" in body)
        assertEquals(ZhihuJson.json.parseToJsonElement("""{"segment_id":"segment-id"}"""), body.getValue("segment"))
        assertFalse("segment_id" in body)
    }

    @Test
    fun existingSegmentRichPayloadKeepsIdAndOmitsOrdinarySettings() {
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
        assertEquals(
            ZhihuJson.json.parseToJsonElement("""{"segment_id":"segment-id"}"""),
            body.getValue("segment"),
            "已有 ID 的片段不受新建时的最少长度限制，也不重建位置或引用",
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
    fun newSegmentPayloadKeepsUtf16EndpointsAndRequiresFiveQuotedCodeUnits() {
        val quote = "前😀句评文后"
        val start = quote.indexOf("😀")
        val end = start + "😀句评文".length
        val target = SegmentCommentHolder("123", "answer", "", quote.substring(start, end), "paragraph", start, end)

        assertEquals(
            "https://www.zhihu.com/api/v4/comment_v5/answers/123/segment/comment",
            target.submitCommentUrl,
        )
        val body = target.buildSubmitCommentBody("，", replyToCommentId = "reply-1", syncToPin = true)
        assertEquals(
            ZhihuJson.json.parseToJsonElement(
                """{
                  "content":"😀句评文","segment_id":"",
                  "position":{"start":{"offset":1,"paragraph_id":"paragraph"},"end":{"offset":6,"paragraph_id":"paragraph"}}
                }""",
            ),
            body.getValue("segment"),
        )
        assertEquals("<p>，</p>", body.getValue("content").jsonPrimitive.content)
        assertEquals("reply-1", body.getValue("reply_comment_id").jsonPrimitive.content)
        assertFalse("unfriendly_check" in body)
        assertFalse("selected_settings" in body)
        assertFalse("segment_id" in body)

        for (shortQuote in listOf("", "四字片段", "😀句评")) {
            assertFailsWith<IllegalArgumentException> {
                target.copy(segmentContent = shortQuote, endOffset = start + shortQuote.length).buildSubmitCommentBody(
                    commentText = "评论正文足够长也不能绕过引文长度检查",
                    replyToCommentId = null,
                )
            }
        }
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

    @Test
    fun rootCountUsesSameResourceMultiSegmentTotalOnlyWhenMissing() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val path = "/api/v4/comment_v5/articles/123/"
        val next = "https://www.zhihu.com${path}root_comment?cursor=second"
        var rootCounts = """{"total_counts":80}"""
        val requests = mutableListOf<Url>()
        val client = commentClient(dispatcher) { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("www.zhihu.com", request.url.host)
            requests += request.url
            val body = when (request.url.encodedPath) {
                "${path}root_comment" -> if (request.url.parameters["cursor"] == "second") {
                    """{"counts":{"total_counts":81},"data":[],"paging":{"is_end":true}}"""
                } else {
                    val row = pagingComment("normal", "123", resourceType = "article")
                    """{"counts":$rootCounts,"data":[$row],"paging":{"is_end":false,"next":"$next"}}"""
                }
                "${path}segment_comment" -> {
                    assertEquals(
                        mapOf("limit" to "1", "offset" to "", "order_by" to "score"),
                        request.url.parameters
                            .names()
                            .associateWith { request.url.parameters[it] },
                    )
                    """{"counts":{"total_counts":6},"data":[${pagingComment("sentence", "A")}],
                       "segment_infos":{"A":{"content":"引文"}},"paging":{"is_end":true,"totals":1}}"""
                }
                else -> error("不应请求 ${request.url}")
            }
            respond(body, headers = jsonHeaders)
        }
        val environment = commentEnvironment { client }
        val model = RootCommentViewModel(Article(type = ArticleType.Article, id = 123))
        try {
            model.loadMore(environment)
            advanceUntilIdle()
            assertEquals(6, model.sentenceCommentCount)
            assertEquals(listOf("normal"), model.allData.map { it.id })
            assertTrue(model.sentenceQuotes.isEmpty())
            assertFalse(model.isEnd)
            model.loadMore(environment)
            advanceUntilIdle()
            assertEquals("second", requests.last().parameters["cursor"])
            assertTrue(model.isEnd)
            assertEquals(6, model.sentenceCommentCount, "普通后续页缺字段不能覆盖已确认的计数")

            for (explicit in listOf(0, 3)) {
                rootCounts = """{"segment_comment_counts":$explicit,"total_counts":80}"""
                model.refresh(environment)
                advanceUntilIdle()
                assertEquals(explicit, model.sentenceCommentCount)
            }
            assertEquals(
                listOf("root_comment", "segment_comment", "root_comment", "root_comment", "root_comment"),
                requests.map { it.encodedPath.substringAfterLast('/') },
            )
        } finally {
            model.viewModelScope.cancel()
            client.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun unavailableMultiSegmentCountDoesNotFailOrRepeatDuringRootPaging() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val next = "https://www.zhihu.com/api/v4/comment_v5/answers/456/root_comment?cursor=next"
        val cases = listOf(
            Triple(HttpStatusCode.OK, """{"data":[${pagingComment("sentence", "A")}],"paging":{"totals":9}}""", null),
            Triple(HttpStatusCode.ServiceUnavailable, """{"counts":{"total_counts":7}}""", null),
            Triple(HttpStatusCode.OK, """{"error":{"message":"unavailable"},"counts":{"total_counts":7}}""", null),
            Triple(HttpStatusCode.OK, """{"counts":{"total_counts":0},"paging":{"totals":9}}""", 0),
        )
        var index = 0
        var probes = 0
        val client = commentClient(dispatcher) { request ->
            when (request.url.encodedPath) {
                "/api/v4/comment_v5/answers/456/root_comment" -> {
                    val paging = if (request.url.parameters["cursor"] == null) """{"is_end":false,"next":"$next"}""" else """{"is_end":true}"""
                    val row = pagingComment("root", "456", resourceType = "answer")
                    respond("""{"counts":{"segment_comment_counts":null,"total_counts":24},"data":[$row],"paging":$paging}""", headers = jsonHeaders)
                }
                "/api/v4/comment_v5/answers/456/segment_comment" -> {
                    probes++
                    val (status, body) = cases[index]
                    respond(body, status, jsonHeaders)
                }
                else -> error("不应请求 ${request.url}")
            }
        }
        val environment = commentEnvironment { client }
        val model = RootCommentViewModel(Article(type = ArticleType.Answer, id = 456))
        try {
            for ((i, case) in cases.withIndex()) {
                index = i
                model.refresh(environment)
                advanceUntilIdle()
                assertEquals(case.third, model.sentenceCommentCount)
                assertEquals(listOf("root"), model.allData.map { it.id })
                model.loadMore(environment)
                advanceUntilIdle()
                assertNull(model.errorMessage)
                assertTrue(model.isEnd)
                assertEquals(i + 1, probes, "可选计数无论成功与否，都不跟随普通分页重复查询")
            }
        } finally {
            model.viewModelScope.cancel()
            client.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun delayedCountCannotOverwriteNewSortOrAccount() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val gates = List(2) { CompletableDeferred<Unit>() }
        var probes = 0
        var rootCounts = "{}"
        val oldClient = commentClient(dispatcher) { request ->
            if (request.url.encodedPath.endsWith("/segment_comment")) {
                val gate = gates[probes++]
                gate.await()
                respond("""{"counts":{"total_counts":12}}""", headers = jsonHeaders)
            } else {
                respond("""{"counts":$rootCounts,"data":[],"paging":{"is_end":true}}""", headers = jsonHeaders)
            }
        }
        val newClient = commentClient(dispatcher) { request ->
            assertTrue(request.url.encodedPath.endsWith("/root_comment"))
            respond("""{"counts":{"segment_comment_counts":3},"data":[],"paging":{"is_end":true}}""", headers = jsonHeaders)
        }
        var currentClient = oldClient
        val environment = commentEnvironment { currentClient }
        val model = RootCommentViewModel(Article(type = ArticleType.Answer, id = 456))
        try {
            model.loadMore(environment)
            advanceUntilIdle()
            assertEquals(1, probes)
            assertFalse(model.isLoading, "计数在途时普通列表已经可以使用")
            assertNull(model.sentenceCommentCount)
            rootCounts = """{"segment_comment_counts":0}"""
            model.changeSortOrder(CommentSortOrder.TIME, environment)
            advanceUntilIdle()
            gates[0].complete(Unit)
            advanceUntilIdle()
            assertEquals(0, model.sentenceCommentCount)

            rootCounts = "{}"
            model.refresh(environment)
            advanceUntilIdle()
            assertEquals(2, probes)
            currentClient = newClient
            gates[1].complete(Unit)
            advanceUntilIdle()
            assertNull(model.sentenceCommentCount, "账号已经切换，旧请求不能回写，即使新账号还没有加载")
            model.loadMore(environment)
            advanceUntilIdle()
            assertEquals(3, model.sentenceCommentCount, "旧账号的终页不能阻止新账号读取自己的首页")
        } finally {
            gates.forEach { it.complete(Unit) }
            model.viewModelScope.cancel()
            oldClient.close()
            newClient.close()
            Dispatchers.resetMain()
        }
    }

    /** 合成分页边界，不作为服务端非空句评样本验收。 */
    @Test
    fun knownSegmentsKeepOpaqueNextAndRestartAfterFailureWithoutChangingSort() = runTest {
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
        val normal = RootCommentViewModel(target, commentSegments = { article.commentSegments })
        assertNull(normal.sentenceCommentCount)
        assertTrue(normal.hasKnownSentenceComments, "正文已知片段不能因普通 API 缺少句评计数而失去入口")
        for (mismatch in listOf(target.copy(id = 456), target.copy(type = ArticleType.Article))) {
            assertFalse(RootCommentViewModel(mismatch, commentSegments = { article.commentSegments }).hasKnownSentenceComments)
        }
        val noComments = article.commentSegments.mapValues { (_, span) -> span.copy(meta = span.meta.copy(commentCount = 0)) }
        assertFalse(RootCommentViewModel(target, commentSegments = { noComments }).hasKnownSentenceComments)
        val requests = mutableListOf<Url>()
        val opaqueNext = "https://www.zhihu.com/api/v4/comment_v5/answers/123/segment/root_comment?cursor=opaque%2Bnext"
        var failNext = false
        val client = commentClient(dispatcher) { request ->
            requests += request.url
            if (request.url.parameters["cursor"] != null && failNext) {
                failNext = false
                return@commentClient respond("{}", HttpStatusCode.ServiceUnavailable, jsonHeaders)
            }
            val body = when {
                request.url.encodedPath.endsWith("/segment_comment") -> """{"data":null,"segment_infos":null,"paging":{"is_end":true,"next":""}}"""
                request.url.parameters["segment_id"] == "A" -> """{"data":[],"paging":{"is_end":true}}"""
                request.url.parameters["segment_id"] == "B" -> """{"data":[${pagingComment("b1", "B", children = 16)}],"counts":{"total_counts":18},"paging":{"is_end":false,"next":"$opaqueNext"}}"""
                request.url.parameters["cursor"] != null -> """{"data":[${pagingComment("b1", "B", children = 16)},${pagingComment("b2", "B")}],"paging":{"is_end":true}}"""
                request.url.parameters["segment_id"] == "C" -> """{"data":[${pagingComment("c1", "C")}],"paging":{"is_end":true,"next":""}}"""
                else -> error("不应发起请求 ${request.url}")
            }
            respond(body, headers = jsonHeaders)
        }
        val environment = commentEnvironment { client }
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
            assertEquals("被评论的句子", childTarget.segmentContent)
            assertEquals(1, childTarget.startOffset)
            val replyBody = childTarget.buildSubmitCommentBody("回复", "b1")
            assertEquals(ZhihuJson.json.parseToJsonElement("""{"segment_id":"B"}"""), replyBody.getValue("segment"))
            assertEquals("b1", replyBody.getValue("reply_comment_id").jsonPrimitive.content)

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
            val refreshedRequests = requests.size
            model.changeSortOrder(CommentSortOrder.TIME, environment)
            advanceUntilIdle()
            assertFalse(model.supportsSorting)
            assertEquals(CommentSortOrder.SCORE, model.sortOrder)
            assertEquals(refreshedRequests, requests.size)
            assertEquals("score", requests.last().parameters["order_by"])
            assertEquals("A", requests[requests.lastIndex - 1].parameters["segment_id"])
            assertEquals(paragraphs, article.toCachedContent().segmentInfos)
        } finally {
            model.viewModelScope.cancel()
            article.viewModelScope.cancel()
            client.close()
            Dispatchers.resetMain()
        }
    }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun commentClient(dispatcher: CoroutineDispatcher, handler: MockRequestHandler) = HttpClient(MockEngine) {
        engine {
            this.dispatcher = dispatcher
            addHandler(handler)
        }
        install(ContentNegotiation) { json(ZhihuJson.json) }
    }

    private fun commentEnvironment(client: () -> HttpClient) = object : PaginationEnvironment {
        override fun httpClient() = client()

        override fun authenticatedCookies() = emptyMap<String, String>()

        override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
    }

    private fun pagingComment(id: String, segmentId: String, children: Int = 0, resourceType: String = "segment"): String = """{
      "id":"$id","type":"comment","resource_type":"$resourceType","resource_id":"$segmentId",
      "url":"https://www.zhihu.com/comment/$id","content":"<p>测试评论</p>","created_time":1,
      "is_delete":false,"collapsed":false,"reviewing":false,"child_comment_count":$children,
      "author":{"id":"author","url_token":"author","name":"测试作者","avatar_url":"","avatar_url_template":"",
      "is_org":false,"type":"people","url":"","user_type":"people","headline":"","gender":0,"is_advertiser":false}
    }"""
}
