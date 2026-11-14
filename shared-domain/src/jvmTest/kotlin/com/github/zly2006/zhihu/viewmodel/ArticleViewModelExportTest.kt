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

package com.github.zly2006.zhihu.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.zly2006.zhihu.account.ZhihuAccountRepository
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.data.CommonFeed
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.Person
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.installZhihuCommonClientConfig
import com.github.zly2006.zhihu.data.navDestination
import com.github.zly2006.zhihu.data.prepareNavigation
import com.github.zly2006.zhihu.data.toFeedDisplayItemNavDestinationJson
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.platform.UserMessageSink
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CopyableThrowable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ArticleViewModelExportTest {
    @Test
    fun listNavigationSeedsFirstFrameWithoutTreatingExcerptAsFullContent() {
        val author = Person(
            id = "author-id",
            url = "",
            userType = "people",
            urlToken = "author-token",
            name = "列表作者",
            headline = "作者简介",
            avatarUrl = "https://example.com/avatar.png",
        )
        val targets = listOf(
            Feed.AnswerTarget(
                id = 123,
                url = "",
                author = author,
                question = Feed.QuestionTarget(id = 456, _title = "列表问题", url = "", type = "question"),
                excerpt = "列表回答摘要 &amp; 后续内容",
                content = "<p data-pid=\"first\">列表中的正文片段</p><p data-pid=\"second\"><strong>第二段正文</strong></p>",
            ),
            Feed.ArticleTarget(id = 789, url = "", author = author, title = "列表文章", excerpt = "文章摘要", content = "<p>文章第一段</p><p>文章第二段</p>"),
        )
        targets.forEach { target ->
            val route = FeedDisplayItem(
                title = target.title,
                summary = target.excerpt,
                details = "",
                feed = CommonFeed(id = target.title, target = target),
            ).prepareNavigation("list-source") as Article
            val previewHtml = when (target) {
                is Feed.AnswerTarget -> target.content
                is Feed.ArticleTarget -> target.content
                else -> error("Unexpected target")
            }
            assertFalse(route.toFeedDisplayItemNavDestinationJson().contains(if (target is Feed.AnswerTarget) "列表中的正文片段" else "文章第一段"))
            // Also exercise the serialized navigation used by stored lists.
            val restored = FeedDisplayItem(
                title = target.title,
                summary = target.excerpt,
                details = "",
                feed = null,
                navDestinationJson = route.toFeedDisplayItemNavDestinationJson(),
            ).navDestination as Article
            val model = ArticleViewModel(restored, null)
            assertEquals(target.title, model.title)
            assertEquals(author.name, model.authorName)
            assertEquals(author.headline, model.authorBio)
            assertEquals(author.avatarUrl, model.authorAvatarSrc)
            assertEquals(author.id, model.authorId)
            assertEquals(author.urlToken, model.authorUrlToken)
            assertEquals(if (target is Feed.AnswerTarget) 456L else 0L, model.questionId)
            assertEquals(target.excerpt, model.previewExcerpt)
            assertEquals(previewHtml, model.previewContent)
            assertEquals("list-source", restored.readingQueueSourceId)
            assertEquals("", model.content)
            assertNull(model.sourceContent)
            assertNull(model.segmentCommentSnapshot)
            assertTrue(model.segmentInfos.isEmpty())
            val cached = model.toCachedContent()
            assertEquals("", cached.content)
            assertNull(cached.sourceContent)
            assertNull(cached.exportSourceContent)
            assertEquals("", ArticleViewModel(restored, null).previewContent)
        }
    }

    @Test
    fun directLinksDoNotDisplayLegacyLoadingPlaceholders() {
        val model = ArticleViewModel(Article(type = ArticleType.Answer, id = 1), null)
        assertEquals("", model.title)
        assertEquals("", model.authorName)
        assertEquals("", model.authorBio)
        assertEquals("", model.previewExcerpt)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun detailResponseReplacesListMetadataAndKeepsFullBodySeparate() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val requested = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val client = HttpClient(
            MockEngine { request ->
                assertEquals("/api/v4/articles/123", request.url.encodedPath)
                requested.complete(Unit)
                release.await()
                respond(
                    """{
                    "id":123,"type":"article","title":"详情标题","content":"<p>完整正文</p>","excerpt":"详情摘要",
                    "created":1710000000,"updated":1710000600,"url":"","voteup_count":42,"comment_count":7,
                    "can_comment":{"status":true,"reason":""},
                    "author":{"id":"detail-author","url_token":"detail-token","name":"详情作者","headline":"详情简介",
                    "avatar_url":"https://example.com/detail.png","gender":0,"is_advertiser":false,"is_org":false,
                    "type":"people","url":"","user_type":"people"}
                    }""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) {
            installZhihuCommonClientConfig(mutableMapOf(), "article-preview-test")
        }
        val previewHtml = "<p data-pid=\"preview\">列表中的已加载正文</p>"
        val route = FeedDisplayItem(
            title = "列表标题",
            summary = "列表摘要",
            details = "",
            feed = CommonFeed(
                id = "preview",
                target = Feed.ArticleTarget(
                    id = 123,
                    url = "",
                    author = Person(id = "list-author", url = "", userType = "people", name = "列表作者", headline = "", avatarUrl = ""),
                    title = "列表标题",
                    excerpt = "列表摘要",
                    content = previewHtml,
                ),
            ),
        ).prepareNavigation() as Article
        val model = ArticleViewModel(route, client)
        val environment = object : ArticleLoadEnvironment {
            override fun httpClient() = client

            override fun authenticatedCookies() = emptyMap<String, String>()

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
        }
        try {
            model.loadArticle(environment)
            requested.await()
            assertEquals("列表标题", model.title)
            assertEquals("列表作者", model.authorName)
            assertEquals("", model.content)
            assertNull(model.sourceContent)
            assertNull(model.segmentCommentSnapshot)
            assertTrue(model.segmentInfos.isEmpty())
            assertFalse(model.canFollowAuthor)
            assertTrue(model.isLoading)
            assertEquals(previewHtml, model.previewContent)
            release.complete(Unit)
            model.viewModelScope.coroutineContext[Job]!!
                .children
                .toList()
                .joinAll()
            assertNull(model.loadError)
            assertEquals("详情标题", model.title)
            assertEquals("详情作者", model.authorName)
            assertEquals("detail-author", model.authorId)
            assertEquals("<p>完整正文</p>", model.content)
            assertEquals("<p>完整正文</p>", model.sourceContent)
            assertEquals("", model.previewContent)
            assertEquals(42, model.voteUpCount)
            assertEquals(7, model.commentCount)
        } finally {
            release.complete(Unit)
            model.viewModelScope.coroutineContext[Job]!!.cancelAndJoin()
            client.close()
            client.engine.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun convert_to_markdown_should_reuse_md_ast_html_conversion() {
        val viewModel = ArticleViewModel(
            article = Article(type = ArticleType.Answer, id = 1L),
            httpClient = null,
        )
        viewModel.title = "导出标题"
        viewModel.authorName = "导出作者"
        viewModel.authorBio = "导出简介"
        viewModel.content =
            """
            <p>正文包含脚注<sup data-draft-type="reference" data-numero="1" data-text="参考资料" data-url="https://example.com/ref">[1]</sup> 和 <a href="https://link.zhihu.com/?target=https%3A%2F%2Fexample.com%2Ftarget">链接</a>。</p>
            <a class="video-box" href="https://link.zhihu.com/?target=https%3A//www.zhihu.com/video/2029631316597973958" data-lens-id="2029631316597973958">
              <img src="https://example.com/cover.jpg" />
            </a>
            """.trimIndent()

        assertEquals(
            """
            # 导出标题

            **作者**: 导出作者

            **简介**: 导出简介

            ---

            正文包含脚注[^1] 和 [链接](https://example.com/target)。

            [视频](https://www.zhihu.com/video/2029631316597973958)

            [^1]: 参考资料[https://example.com/ref](https://example.com/ref)
            """.trimIndent(),
            viewModel.convertToMarkdown(),
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun summaryUsesTheSameAuthenticatedClientCookiesForXsrfAndSignature() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        var received: HttpRequestData? = null
        val client = HttpClient(
            MockEngine { request ->
                received = request
                respond("data: {\"event\":\"answer\",\"data\":{\"summary\":\"可信摘要\",\"delta\":false}}\n\ndata: {\"event\":\"end\",\"data\":{}}\n\n", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream"))
            },
        )
        val cookies = mapOf("_xsrf" to "captured-xsrf", "d_c0" to "captured-device")
        val environment = object : ZhihuApiEnvironment {
            override fun httpClient() = client

            override fun authenticatedCookies() = emptyMap<String, String>()

            override suspend fun <T> withAuthenticatedClient(block: suspend (HttpClient, Map<String, String>) -> T): T = block(client, cookies)

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
        }
        val model = ArticleViewModel(Article(type = ArticleType.Article, id = 42), client)
        model.title = "完整标题"
        try {
            model.requestAiSummary(environment)
            model.viewModelScope.coroutineContext[Job]!!
                .children
                .forEach { it.join() }
            val request = checkNotNull(received)
            assertEquals("captured-xsrf", request.headers["x-xsrftoken"])
            assertTrue(request.headers["x-zse-96"].orEmpty().startsWith("2.0_"))
            assertEquals("/ai_ingress/stream/completion", request.url.encodedPath)
            assertEquals(
                Json.parseToJsonElement("""{"quiz_type":"QT_CHAT","attachments":[{"type":"DOC","value":"NDJ8Ojp8QVJUSUNMRQ==","title":"完整标题"}],"message_source_type":"text","session_id":"","zhida_source":"one_tap_summary","content_id":"42","content_type":"article","message_content":"这篇内容讲了什么"}"""),
                Json.parseToJsonElement((request.body as TextContent).text),
            )
            assertEquals("可信摘要", model.aiSummaryText)
            assertNull(model.aiSummaryError)
            assertFalse(model.aiSummaryLoading)
        } finally {
            val scopeJob = model.viewModelScope.coroutineContext[Job]!!
            scopeJob.cancel()
            scopeJob.join()
            client.close()
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun loadArticlePublishesLoadingAndErrorThenRetriesWithoutLosingCachedContent() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val responseGate = CompletableDeferred<Unit>()
        var requests = 0
        val client = HttpClient(
            MockEngine { request ->
                assertEquals("/api/v4/articles/1", request.url.encodedPath)
                requests++
                if (requests == 1) {
                    responseGate.await()
                    error("detail load failed")
                }
                respond(articleResponse(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ) {
            installZhihuCommonClientConfig(mutableMapOf(), "article-load-test")
        }
        val viewModel = ArticleViewModel(Article(type = ArticleType.Article, id = 1L), client)
        val environment = object : ArticleLoadEnvironment {
            override fun httpClient() = client

            override fun authenticatedCookies() = emptyMap<String, String>()

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
        }
        viewModel.seedFromCached(
            ArticleViewModel.CachedAnswerContent(
                article = Article(type = ArticleType.Article, id = 1L),
                title = "缓存完整标题",
                authorName = "缓存作者",
                authorBio = "缓存简介",
                authorAvatarUrl = "",
                content = "<p>缓存正文</p>",
                voteUpCount = 12,
                commentCount = 3,
            ),
        )
        try {
            viewModel.loadArticle(environment)
            val firstLoad = viewModel.viewModelScope.coroutineContext[Job]!!
                .children
                .toList()
            assertTrue(viewModel.isLoading, "loadArticle 必须公开加载状态")
            assertEquals("<p>缓存正文</p>", viewModel.content)
            responseGate.complete(Unit)
            firstLoad.joinAll()
            assertFalse(viewModel.isLoading)
            val loadError = viewModel.loadError
            assertEquals("暂时无法获取内容，请重试", loadError)
            assertEquals("缓存完整标题", viewModel.title)
            assertEquals("缓存作者", viewModel.authorName)
            assertEquals("<p>缓存正文</p>", viewModel.content)

            viewModel.loadArticle(environment)
            viewModel.viewModelScope.coroutineContext[Job]!!
                .children
                .toList()
                .joinAll()
            assertEquals(2, requests, "重试必须重新执行当前 VM 的真实详情请求")
            assertNull(viewModel.loadError)
            assertFalse(viewModel.isLoading)
            assertEquals("完整文章标题", viewModel.title)
            assertEquals("测试作者", viewModel.authorName)
            assertEquals("<p>加载后的正文</p>", viewModel.content)
        } finally {
            responseGate.complete(Unit)
            val scopeJob = viewModel.viewModelScope.coroutineContext[Job]!!
            scopeJob.cancel()
            scopeJob.join()
            client.close()
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun missingDetailDoesNotBecomeFakeHtmlAndSuccessfulEmptyContentIsNotAnError() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        var requests = 0
        val client = HttpClient(
            MockEngine {
                requests++
                if (requests == 1) {
                    respond("", HttpStatusCode.NoContent)
                } else {
                    respond(articleResponse(content = ""), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            },
        ) {
            installZhihuCommonClientConfig(mutableMapOf(), "article-load-test")
        }
        val viewModel = ArticleViewModel(Article(type = ArticleType.Article, id = 1L), client)
        val environment = object : ArticleLoadEnvironment {
            override fun httpClient() = client

            override fun authenticatedCookies() = emptyMap<String, String>()

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
        }
        try {
            viewModel.loadArticle(environment)
            viewModel.viewModelScope.coroutineContext[Job]!!
                .children
                .toList()
                .joinAll()
            assertEquals("", viewModel.content, "未找到详情不能伪装成正文 HTML")
            assertEquals("暂时无法获取内容，请重试", viewModel.loadError)

            viewModel.loadArticle(environment)
            viewModel.viewModelScope.coroutineContext[Job]!!
                .children
                .toList()
                .joinAll()
            assertEquals("完整文章标题", viewModel.title)
            assertEquals("", viewModel.content)
            assertNull(viewModel.loadError, "成功的空正文与加载失败必须分离")
            assertFalse(viewModel.isLoading)
        } finally {
            val scopeJob = viewModel.viewModelScope.coroutineContext[Job]!!
            scopeJob.cancel()
            scopeJob.join()
            client.close()
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun cancelledDetailLoadRemainsCancellationAndDoesNotOverwriteCachedBody() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val responseGate = CompletableDeferred<Unit>()
        val client = HttpClient(
            MockEngine {
                responseGate.await()
                throw CancellationException("cancelled detail load")
            },
        )
        val viewModel = ArticleViewModel(Article(type = ArticleType.Article, id = 1L), client).apply {
            title = "缓存标题"
            authorName = "缓存作者"
            content = "<p>缓存正文</p>"
        }
        val environment = object : ArticleLoadEnvironment {
            override fun httpClient() = client

            override fun authenticatedCookies() = emptyMap<String, String>()

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
        }
        try {
            viewModel.loadArticle(environment)
            val cancelledLoad = viewModel.viewModelScope.coroutineContext[Job]!!
                .children
                .toList()
            responseGate.complete(Unit)
            cancelledLoad.joinAll()
            assertTrue(cancelledLoad.single().isCancelled, "取消必须从 loadArticle 的生产协程传播")
            assertEquals("缓存标题", viewModel.title)
            assertEquals("缓存作者", viewModel.authorName)
            assertEquals("<p>缓存正文</p>", viewModel.content)
            assertNull(viewModel.loadError, "取消不能成为失败提示")
            assertFalse(viewModel.isLoading)
        } finally {
            responseGate.complete(Unit)
            val scopeJob = viewModel.viewModelScope.coroutineContext[Job]!!
            scopeJob.cancel()
            scopeJob.join()
            client.close()
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun imageExportKeepsOriginalFailureAndAttemptsEveryCleanup() = runTest {
        class SaveCancellation :
            CancellationException("cancelled save"),
            CopyableThrowable<SaveCancellation> {
            override fun createCopy(): SaveCancellation? = null
        }

        class CleanupFailure(
            message: String,
        ) : IllegalStateException(message),
            CopyableThrowable<CleanupFailure> {
            override fun createCopy(): CleanupFailure? = null
        }
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val client = HttpClient(
            MockEngine {
                respond(articleResponse(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ) {
            installZhihuCommonClientConfig(mutableMapOf(), "export-cancellation-test")
        }
        val model = ArticleViewModel(Article(type = ArticleType.Article, id = 1L), client)
        var export: Job? = null
        try {
            for (failureStage in 0..2) {
                val captureStarted = CompletableDeferred<Unit>()
                val originalCancellation = CompletableDeferred<CancellationException>()
                val escapedError = CompletableDeferred<Throwable>()
                val recycleError = CleanupFailure("recycle failed")
                val destroyError = CleanupFailure("destroy failed")
                val prepared = object : PreparedArticleExportContent {}
                val captured = Any()
                var recycled = 0
                var destroyed = 0
                var saved = 0
                var completed: Boolean? = null
                val renderer = object : ArticleImageExportRenderer {
                    override suspend fun prepareExportWebView(htmlContent: String, timeoutMs: Long): PreparedArticleExportContent {
                        assertTrue(htmlContent.contains("加载后的正文"))
                        return prepared
                    }

                    override suspend fun captureExportBitmap(preparedWebView: PreparedArticleExportContent): Any {
                        assertSame(prepared, preparedWebView)
                        if (failureStage != 0) return captured
                        captureStarted.complete(Unit)
                        try {
                            awaitCancellation()
                        } catch (error: CancellationException) {
                            originalCancellation.complete(error)
                            throw error
                        }
                    }

                    override suspend fun destroyExportWebView(preparedWebView: PreparedArticleExportContent) {
                        withContext(Dispatchers.Main) {
                            assertSame(prepared, preparedWebView)
                            destroyed++
                            throw destroyError
                        }
                    }

                    override fun recycleExportBitmap(bitmap: Any) {
                        assertSame(captured, bitmap)
                        recycled++
                        throw recycleError
                    }
                }
                val environment = object : PaginationEnvironment {
                    override fun httpClient() = client

                    override fun authenticatedCookies() = emptyMap<String, String>()

                    override suspend fun handleFetchFailure(tag: String?, error: Exception) = throw error

                    override fun hasImageExportPermission() = true

                    override fun articleImageExportRenderer() = renderer

                    override fun loadExportAssetText(fileName: String) = ""

                    override fun saveImageToMediaStore(displayName: String, bitmap: Any) {
                        assertSame(captured, bitmap)
                        if (failureStage == 1) {
                            val error = SaveCancellation()
                            originalCancellation.complete(error)
                            throw error
                        }
                        saved++
                    }
                }
                if (!model.hasLoaded) {
                    model.loadArticle(environment)
                    model.viewModelScope.coroutineContext[Job]!!
                        .children
                        .forEach { it.join() }
                    assertNull(model.loadError)
                    assertTrue(model.hasLoaded)
                }
                export = launch(Dispatchers.Default) {
                    try {
                        model.exportToImage(environment, includeAppAttribution = true) { completed = it }
                    } catch (error: Throwable) {
                        escapedError.complete(error)
                        if (error is CancellationException) throw error
                    }
                }
                if (failureStage == 0) {
                    captureStarted.await()
                    export.cancelAndJoin()
                } else {
                    export.join()
                }
                val error = escapedError.await()
                if (failureStage == 2) {
                    assertSame(recycleError, error, "没有主异常时不能吞掉清理失败")
                    assertFalse(export.isCancelled)
                    assertEquals(true, completed)
                    assertEquals(1, saved)
                } else {
                    assertSame(originalCancellation.await(), error, "清理失败不能替换真实取消异常")
                    assertTrue(export.isCancelled)
                    assertNull(completed, "取消不能被报告为成功或普通失败")
                    assertEquals(0, saved)
                }
                val expectedSuppressed = if (failureStage == 1) 2 else 1
                assertEquals(expectedSuppressed, error.suppressedExceptions.size)
                if (failureStage == 1) assertSame(recycleError, error.suppressedExceptions[0])
                assertSame(destroyError, error.suppressedExceptions.last())
                assertEquals(if (failureStage == 0) 0 else 1, recycled)
                assertEquals(1, destroyed, "回收抛错后仍必须进入实际主线程销毁")
            }
        } finally {
            export?.cancelAndJoin()
            model.viewModelScope.coroutineContext[Job]!!.cancelAndJoin()
            client.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun cachedReturnPreservesAuthorTypedExportAndRawSourceIdentity() {
        val raw = ZhihuJson.decodeJson<DataHolder.Article>(ZhihuJson.json.parseToJsonElement(articleResponse()))
        val article = Article(type = ArticleType.Article, id = 1L)
        val client = HttpClient(MockEngine { error("缓存恢复不能请求网络") })
        try {
            val model = ArticleViewModel(article, client)
            model.seedFromCached(
                ArticleViewModel.CachedAnswerContent(
                    article = article,
                    title = raw.title,
                    authorName = raw.author.name,
                    authorBio = raw.author.headline,
                    authorAvatarUrl = raw.author.avatarUrl,
                    authorId = raw.author.id,
                    authorUrlToken = raw.author.urlToken,
                    authorIsFollowing = true,
                    authorIsSelf = true,
                    exportSourceContent = raw,
                    content = "<p>渲染正文</p>",
                    sourceContent = raw.content,
                    voteUpCount = raw.voteupCount,
                    commentCount = raw.commentCount,
                ),
            )
            assertEquals(raw.author.id, model.authorId)
            assertEquals(raw.author.urlToken, model.authorUrlToken)
            assertTrue(model.authorIsFollowing)
            assertTrue(model.authorIsSelf)
            assertFalse(model.canFollowAuthor)
            val cached = model.toCachedContent("收藏夹")
            assertSame(raw, cached.exportSourceContent)
            assertEquals(raw.content, cached.sourceContent)
            assertEquals("<p>渲染正文</p>", cached.content)
            assertEquals("收藏夹", cached.sourceLabel)
            assertTrue(model.hasLoaded)
        } finally {
            client.close()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun failedFollowRetainsAuthorRelationshipAndRejectsDuplicateSubmission() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val gate = CompletableDeferred<Unit>()
        var requests = 0
        val client = HttpClient(
            MockEngine { request ->
                requests++
                assertEquals("/api/v4/members/test-author/followers", request.url.encodedPath)
                assertEquals(io.ktor.http.HttpMethod.Post, request.method)
                gate.await()
                respond("{}", HttpStatusCode.Forbidden, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
        val environment = object : ZhihuApiEnvironment {
            override fun httpClient() = client

            override fun authenticatedCookies() = mapOf("d_c0" to "test-device")

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
        }
        val article = Article(type = ArticleType.Article, id = 1)
        val raw = ZhihuJson.decodeJson<DataHolder.Article>(ZhihuJson.json.parseToJsonElement(articleResponse()))
        val model = ArticleViewModel(article, client)
        model.seedFromCached(
            ArticleViewModel.CachedAnswerContent(
                article = article,
                title = raw.title,
                authorName = raw.author.name,
                authorBio = raw.author.headline,
                authorAvatarUrl = raw.author.avatarUrl,
                authorId = raw.author.id,
                authorUrlToken = raw.author.urlToken,
                authorIsFollowing = raw.author.isFollowing,
                authorIsSelf = raw.isMine,
                exportSourceContent = raw,
                content = raw.content,
                sourceContent = raw.content,
                voteUpCount = raw.voteupCount,
                commentCount = raw.commentCount,
            ),
        )
        try {
            assertTrue(model.hasLoaded)
            assertTrue(model.canFollowAuthor)
            model.toggleAuthorFollow(environment)
            model.toggleAuthorFollow(environment)
            assertTrue(model.authorFollowChanging)
            assertFalse(model.authorIsFollowing)
            gate.complete(Unit)
            model.viewModelScope.coroutineContext[Job]!!
                .children
                .toList()
                .joinAll()
            assertEquals(1, requests)
            assertFalse(model.authorIsFollowing)
            assertFalse(model.authorFollowChanging)
        } finally {
            gate.complete(Unit)
            model.viewModelScope.coroutineContext[Job]!!.cancelAndJoin()
            client.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun desktopExportUsesPackagedTemplate() {
        val directory = java.nio.file.Files
            .createTempDirectory("desktop-export-template-")
            .toFile()
        val store = ZhihuAccountStore(
            ZhihuAccountRepository(kotlinx.io.files.Path(directory.resolve("account.json").path)),
            MockEngine { error("读取模板不应发起网络请求") },
        )
        try {
            val environment = DesktopPaginationEnvironment(notificationSettingsStore = MemoryNotificationSettings(), store = store)
            val template = environment.loadExportAssetText("article_export_template.html")
            assertTrue(template.startsWith("<!DOCTYPE html>"))
            assertTrue("{{bodyHtml}}" in template)
            assertFailsWith<IllegalStateException> {
                environment.loadExportAssetText("missing-export-template.html")
            }
        } finally {
            store.close()
            directory.deleteRecursively()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun clearingArticleOwnerCancelsInFlightFavoriteWithoutReloadOrSuccessMessage() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val messages = mutableListOf<String>()
        var favoriteRequests = 0
        var reloadRequests = 0
        val client = HttpClient(MockEngine) {
            engine {
                this.dispatcher = dispatcher
                addHandler { request ->
                    favoriteRequests++
                    assertEquals(HttpMethod.Put, request.method)
                    assertEquals("api.zhihu.com", request.url.host)
                    assertEquals("/collections/contents/article/42", request.url.encodedPath)
                    assertEquals("add_collections=fixture-collection", (request.body as TextContent).text)
                    started.complete(Unit)
                    release.await()
                    respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
        }
        val environment = object : ZhihuApiEnvironment {
            override fun httpClient() = client

            override fun authenticatedCookies() = emptyMap<String, String>()

            override suspend fun fetchJson(url: String, include: String): JsonObject? {
                reloadRequests++
                return null
            }

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
        }
        val model = ArticleViewModel(
            Article(type = ArticleType.Article, id = 42),
            client,
            object : UserMessageSink {
                override fun showShortMessage(message: String) {
                    messages += message
                }
            },
        )
        val owner = ViewModelStore()
        try {
            ViewModelProvider.create(owner, viewModelFactory { initializer { model } })[ArticleViewModel::class]
            val ownerJob = checkNotNull(model.viewModelScope.coroutineContext[Job])
            model.toggleFavorite("fixture-collection", remove = false, environment = environment)
            started.await()
            assertEquals(1, favoriteRequests)

            owner.clear()
            release.complete(Unit)
            runCurrent()
            ownerJob.join()
            assertTrue(ownerJob.isCancelled)
            assertEquals(0, reloadRequests, "已释放的阅读页面不能重新加载收藏状态")
            assertEquals(emptyList(), messages, "已释放的阅读页面不能发布收藏成功提示")
            assertTrue(model.collections.isEmpty())
        } finally {
            release.complete(Unit)
            owner.clear()
            model.viewModelScope.coroutineContext[Job]!!.cancelAndJoin()
            client.close()
            client.engine.close()
            Dispatchers.resetMain()
        }
    }

    private fun articleResponse(content: String = "<p>加载后的正文</p>"): String = ZhihuJson.json.encodeToString(
        DataHolder.Article.serializer(),
        DataHolder.Article(
            id = 1L,
            author = DataHolder.Author(
                avatarUrl = "",
                gender = 0,
                headline = "测试简介",
                id = "test-author",
                isAdvertiser = false,
                isOrg = false,
                name = "测试作者",
                type = "people",
                url = "https://www.zhihu.com/people/test-author",
                urlToken = "test-author",
                userType = "people",
            ),
            canComment = DataHolder.CanComment(status = true, reason = ""),
            title = "完整文章标题",
            content = content,
            excerpt = "测试摘要",
            type = "article",
            created = 1L,
            updated = 2L,
            url = "https://zhuanlan.zhihu.com/p/1",
            voteupCount = 12,
        ),
    )

    private class MemoryNotificationSettings : com.github.zly2006.zhihu.notification.NotificationSettingsStore {
        override fun getSystemNotificationEnabled(type: com.github.zly2006.zhihu.notification.NotificationType) = false

        override fun setSystemNotificationEnabled(type: com.github.zly2006.zhihu.notification.NotificationType, enabled: Boolean) = error("未使用通知设置")

        override fun getDisplayInAppEnabled(type: com.github.zly2006.zhihu.notification.NotificationType) = type.defaultValue

        override fun setDisplayInAppEnabled(type: com.github.zly2006.zhihu.notification.NotificationType, enabled: Boolean) = error("未使用通知设置")

        override fun getAutoMarkAsReadEnabled() = false

        override fun setAutoMarkAsReadEnabled(enabled: Boolean) = error("未使用通知设置")

        override fun getUnreadBadgeEnabled() = true

        override fun setUnreadBadgeEnabled(enabled: Boolean) = error("未使用通知设置")
    }
}
