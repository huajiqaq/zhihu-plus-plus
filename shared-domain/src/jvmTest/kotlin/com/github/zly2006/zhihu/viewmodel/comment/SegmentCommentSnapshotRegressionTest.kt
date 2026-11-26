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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fleeksoft.ksoup.Ksoup
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.SegmentInfoMark
import com.github.zly2006.zhihu.data.SegmentInfoMeta
import com.github.zly2006.zhihu.data.SegmentInfoParagraph
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.installZhihuCommonClientConfig
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.SegmentCommentHolder
import com.github.zly2006.zhihu.viewmodel.ArticleLoadEnvironment
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SegmentCommentSnapshotRegressionTest {
    @Test
    fun commentCompletedDuringRefreshRemainsAfterTheNewBaseline() = withFixture {
        val account = account("during-refresh")
        val article = article(account, 81002L)
        val (target, comments) = comments(81002L)
        load(article, account)
        val beforeRefresh = submit(comments, target, account)
        account.serverCounts[81002L] = 1

        val gate = DetailGate()
        account.detailGate = gate
        val loadJobs = startLoad(article, account)
        gate.started.await()
        assertTrue(article.isLoading)
        val duringRefresh = submit(comments, target, account)
        gate.release.complete(Unit)
        loadJobs.joinAll()
        assertNull(article.loadError)
        assertFalse(article.isLoading)
        assertEquals(1, serverCommentCount(article))
        val afterRefresh = effectiveCommentCount(article)

        delete(comments, duringRefresh, account)
        val afterFirstDelete = effectiveCommentCount(article)
        delete(comments, beforeRefresh, account)
        val afterSecondDelete = effectiveCommentCount(article)
        assertEquals(
            listOf(2, 1, 0),
            listOf(afterRefresh, afterFirstDelete, afterSecondDelete),
            "刷新期间成功的评论必须保留，但刷新前的评论不能重复计算",
        )
    }

    private fun withFixture(test: suspend Fixture.() -> Unit) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            fixture.test()
        } finally {
            try {
                fixture.close()
            } finally {
                Dispatchers.resetMain()
            }
        }
    }

    private class Fixture {
        private val accounts = mutableListOf<TestAccount>()
        private val models = mutableListOf<ViewModel>()

        fun account(name: String): TestAccount = TestAccount(name).also(accounts::add)

        fun article(account: TestAccount, id: Long): ArticleViewModel =
            ArticleViewModel(Article(type = ArticleType.Article, id = id), account.client).also(models::add)

        fun comments(id: Long): Pair<SegmentCommentHolder, RootCommentViewModel> {
            val target = SegmentCommentHolder(
                contentId = id.toString(),
                contentType = "article",
                segmentId = SEGMENT_ID,
                segmentContent = SEGMENT_TEXT,
                paragraphId = PARAGRAPH_ID,
                startOffset = 0,
                endOffset = SEGMENT_TEXT.length,
            )
            return target to RootCommentViewModel(target).also(models::add)
        }

        fun startLoad(article: ArticleViewModel, account: TestAccount): List<Job> {
            article.loadArticle(account.environment)
            return article.viewModelScope.coroutineContext[Job]!!
                .children
                .toList()
        }

        suspend fun load(article: ArticleViewModel, account: TestAccount) {
            startLoad(article, account).joinAll()
            assertNull(article.loadError, "详情请求或正文解码失败")
            assertFalse(article.isLoading)
            assertTrue(article.content.isNotBlank())
        }

        suspend fun submit(
            comments: RootCommentViewModel,
            target: SegmentCommentHolder,
            account: TestAccount,
        ): DataHolder.Comment {
            val finished = CompletableDeferred<Unit>()
            var accepted = false
            comments.submitComment(
                target,
                "离线测试评论",
                account.environment,
                onFinished = { finished.complete(Unit) },
                onSuccess = { accepted = true },
            )
            finished.await()
            assertTrue(accepted, "评论提交失败: ${comments.errorMessage}")
            return comments.allData.first()
        }

        suspend fun delete(comments: RootCommentViewModel, comment: DataHolder.Comment, account: TestAccount) {
            val finished = CompletableDeferred<String?>()
            comments.deleteComment(
                comment,
                account.environment,
                onSuccess = { finished.complete(null) },
                onFailure = { finished.complete(it) },
                ownComment = true,
            )
            assertNull(finished.await(), "评论删除应成功")
        }

        suspend fun close() {
            val jobs = models.mapNotNull { it.viewModelScope.coroutineContext[Job] }
            jobs.forEach { it.cancel() }
            jobs.joinAll()
            accounts.forEach { it.client.close() }
        }
    }

    private class DetailGate {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
    }

    private class TestAccount(
        name: String,
    ) {
        val serverCounts = ConcurrentHashMap<Long, Int>()
        private val cookies = mapOf("d_c0" to "synthetic-$name")
        private val commentSequence = AtomicInteger()

        @Volatile
        var detailGate: DetailGate? = null

        val client = HttpClient(
            MockEngine { request ->
                check(request.url.host == "www.zhihu.com") { "不应请求此主机：${request.url.host}" }
                val path = request.url.encodedPath
                val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
                when {
                    request.method == HttpMethod.Get && path.startsWith("/api/v4/articles/") -> {
                        val id = path.substringAfterLast('/').toLong()
                        check(request.url.parameters["include"]?.contains("content") == true)
                        val count = serverCounts[id] ?: 0
                        detailGate?.let { gate ->
                            gate.started.complete(Unit)
                            gate.release.await()
                        }
                        respond(articleResponse(id, count), HttpStatusCode.OK, jsonHeaders)
                    }
                    request.method == HttpMethod.Post && path.matches(Regex("/api/v4/comment_v5/articles/\\d+/segment/comment")) -> {
                        val comment = responseComment("comment-${commentSequence.incrementAndGet()}")
                        respond(ZhihuJson.json.encodeToString(comment), HttpStatusCode.OK, jsonHeaders)
                    }
                    request.method == HttpMethod.Delete && path.startsWith("/api/v4/comment_v5/comment/comment-") ->
                        respond("{}", HttpStatusCode.OK, jsonHeaders)
                    else -> error("不应发起请求：${request.method} ${request.url}")
                }
            },
        ) {
            installZhihuCommonClientConfig(cookies.toMutableMap(), "segment-snapshot-regression")
        }

        val environment = object : ArticleLoadEnvironment {
            override fun httpClient() = client

            override fun authenticatedCookies() = cookies

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
        }
    }

    companion object {
        private const val SEGMENT_ID = "shared-segment"
        private const val SEGMENT_TEXT = "段评测试"
        private const val PARAGRAPH_ID = "paragraph-1"

        private fun articleResponse(id: Long, segmentCommentCount: Int): String = ZhihuJson.json.encodeToString(
            DataHolder.Article.serializer(),
            DataHolder.Article(
                id = id,
                author = DataHolder.Author(
                    avatarUrl = "",
                    gender = 0,
                    headline = "测试简介",
                    id = "author",
                    isAdvertiser = false,
                    isOrg = false,
                    name = "测试作者",
                    type = "people",
                    url = "https://www.zhihu.com/people/author",
                    urlToken = "author",
                    userType = "people",
                ),
                canComment = DataHolder.CanComment(status = true, reason = ""),
                title = "测试文章",
                content = """<p data-pid="$PARAGRAPH_ID">$SEGMENT_TEXT</p>""",
                excerpt = "测试摘要",
                type = "article",
                created = 1L,
                updated = 2L,
                url = "https://zhuanlan.zhihu.com/p/$id",
                voteupCount = 0,
                segmentInfos = listOf(
                    SegmentInfoParagraph(
                        pid = PARAGRAPH_ID,
                        text = SEGMENT_TEXT,
                        marks = listOf(
                            SegmentInfoMark(
                                startIndex = 0,
                                endIndex = SEGMENT_TEXT.length,
                                segInfo = SegmentInfoMeta(segIds = listOf(SEGMENT_ID), commentCount = segmentCommentCount),
                            ),
                        ),
                    ),
                ),
            ),
        )

        private fun responseComment(id: String) = DataHolder.Comment(
            id = id,
            type = "comment",
            resourceType = "article",
            url = "https://www.zhihu.com/comment/$id",
            content = "<p>离线测试评论</p>",
            createdTime = 1L,
            isDelete = false,
            collapsed = false,
            reviewing = false,
            canDelete = true,
            author = DataHolder.Comment.Author(
                id = "comment-author",
                urlToken = "comment-author",
                name = "测试评论者",
                avatarUrl = "",
                avatarUrlTemplate = "",
                isOrg = false,
                type = "people",
                url = "https://www.zhihu.com/people/comment-author",
                userType = "people",
                headline = "",
                gender = 0,
                isAdvertiser = false,
            ),
        )

        private fun serverCommentCount(article: ArticleViewModel): Int =
            Ksoup
                .parseBodyFragment(article.content)
                .select("span.highlight-wrap")
                .single()
                .attr("data-highlight-comment-count")
                .toInt()

        /** 读取真实正文及 loadArticle 发布的快照；此断言不替代 UI 端到端验证。 */
        private fun effectiveCommentCount(article: ArticleViewModel): Int {
            val highlight = Ksoup.parseBodyFragment(article.content).select("span.highlight-wrap").single()
            val baseline = highlight.attr("data-highlight-comment-count").toInt()
            val delta = assertNotNull(article.segmentCommentSnapshot)
                .effectiveDelta(highlight.attr("data-highlight-id").split(','))
            return (baseline + delta).coerceAtLeast(0)
        }
    }
}
