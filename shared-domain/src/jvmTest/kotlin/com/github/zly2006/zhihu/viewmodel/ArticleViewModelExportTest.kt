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

import androidx.lifecycle.viewModelScope
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.installZhihuCommonClientConfig
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ArticleViewModelExportTest {
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
}
