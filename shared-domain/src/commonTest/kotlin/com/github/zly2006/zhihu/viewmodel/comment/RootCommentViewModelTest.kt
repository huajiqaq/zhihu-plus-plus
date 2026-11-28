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
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.jsonPrimitive

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RootCommentViewModelTest {
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
