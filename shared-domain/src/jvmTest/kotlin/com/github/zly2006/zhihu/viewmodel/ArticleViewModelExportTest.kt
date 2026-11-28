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
import com.fleeksoft.ksoup.Ksoup
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.installZhihuCommonClientConfig
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.i18n.WidgetsStrings
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.platform.UserMessageSink
import com.github.zly2006.zhihu.util.ArticleExportComment
import com.github.zly2006.zhihu.util.ArticleExportData
import com.github.zly2006.zhihu.util.buildArticleExportCommentsHtml
import com.github.zly2006.zhihu.util.inlineArticleExportImagesForOfflineHtml
import com.github.zly2006.zhihu.util.renderArticleExportHtml
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CopyableThrowable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class ArticleViewModelExportTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun exportRemovesActiveContentWithoutLosingFormattingImagesMathOrFootnotes() = runTest {
        val html = renderArticleExportHtml(
            template = "<html><head><style>p { color: black }</style><script id='trusted'>/* local template */</script></head>" +
                "<body><h1>{{title}}</h1>{{authorAvatar}}<main>{{bodyHtml}}</main>{{extraSections}}</body></html>",
            exportData = ArticleExportData(
                title = "标题 {{bodyHtml}}",
                authorName = "作者",
                authorBio = "简介",
                authorAvatarSrc = "file:///synthetic-avatar.png",
                voteUpCount = 0,
                commentCount = 0,
                content = """
                    <script>untrusted()</script><style>@import 'https://example.invalid/style';</style>
                    <form><input autofocus onfocus="untrusted()"></form>
                    <iframe src="https://example.invalid/video" title="原视频"></iframe>
                    <p id="body" onclick="untrusted()" style="background-color:#00ffff;background:url(file:///synthetic)">
                        <strong>正文</strong><em>格式</em><a href="#ref-1">脚注</a>
                        <a href="java&#x73;cript:untrusted()">不可执行链接</a>
                    </p>
                    <table><tr><td>表格</td></tr></table>
                    <img id="photo" data-original="//example.invalid/photo.png" src="data:text/html,invalid" onerror="untrusted()">
                    <img id="math" class="ee_img" eeimg="1" src="/equation?tex=x%5E2" style="vertical-align:-2px">
                    <img src="content://synthetic/private"><img src="file:///synthetic/private">
                    <img src="javascript:untrusted()"><img src="https://user:password@example.invalid/private">
                    <sup data-draft-type="reference" data-numero="1" data-text="参考资料" data-url="/question/1">1</sup>
                    <sup data-draft-type="reference" data-numero="2" data-text="保留说明" data-url="javascript:untrusted()">2</sup>
                """.trimIndent(),
            ),
            extraSectionsHtml = buildArticleExportCommentsHtml(
                listOf(ArticleExportComment("评论者", "<b>评论</b><img src='file:///synthetic'><script>untrusted()</script>", "日期", "javascript:untrusted()")),
            ),
        )
        val document = Ksoup.parse(html)
        assertEquals("trusted", document.select("script").single().id())
        assertEquals(1, document.select("style").size, "保留受信模板，不保留正文样式表")
        assertTrue(document.select("iframe, form, input, [onclick], [onerror], [srcset]").isEmpty())
        assertEquals("标题 {{bodyHtml}}", document.selectFirst("h1")!!.text(), "内容不能触发第二轮模板替换")
        assertEquals("正文", document.selectFirst("strong")!!.text())
        assertEquals("表格", document.selectFirst("td")!!.text())
        assertEquals("background-color:#00ffff", document.selectFirst("#body")!!.attr("style"))
        assertEquals("https://example.invalid/photo.png", document.selectFirst("#photo")!!.attr("src"))
        assertEquals("https://www.zhihu.com/equation?tex=x%5E2", document.selectFirst("#math")!!.attr("src"))
        assertEquals("vertical-align:-2px", document.selectFirst("#math")!!.attr("style"))
        assertEquals(2, document.select("img").size)
        assertTrue(document.select("a").any { it.attr("href") == "#ref-1" })
        assertTrue(document.select("a").any { it.attr("href") == "https://example.invalid/video" && it.text() == "原视频" })
        assertFalse(document.select("a").first { it.text() == "不可执行链接" }.hasAttr("href"))
        val references = document.select("sup[data-draft-type=reference]")
        assertEquals("https://www.zhihu.com/question/1", references[0].attr("data-url"))
        assertEquals("参考资料", references[0].attr("data-text"))
        assertFalse(references[1].hasAttr("data-url"))
        assertEquals("评论", document.selectFirst(".comment-content b")!!.text())

        val requested = mutableListOf<String>()
        val engine = MockEngine(
            MockEngineConfig().apply {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler { request ->
                    val path = request.url.encodedPath
                    requested += path
                    if (path == "/still-open") {
                        assertEquals("synthetic-only", request.headers[HttpHeaders.Authorization])
                    } else {
                        assertNull(request.headers[HttpHeaders.Authorization])
                        assertNull(request.headers[HttpHeaders.Cookie])
                    }
                    when (path) {
                        "/photo.png" -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://cdn.example.invalid/images/step"))
                        "/images/step" -> respond("", HttpStatusCode.TemporaryRedirect, headersOf(HttpHeaders.Location, "../photo-ready.png"))
                        "/broken" -> respond("", HttpStatusCode.NotFound)
                        "/downgrade" -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "http://example.invalid/forbidden"))
                        "/credentials" -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://synthetic:only@example.invalid/forbidden"))
                        "/loop" -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "#again"))
                        "/missing-location" -> respond("", HttpStatusCode.Found)
                        "/slow-start" -> {
                            delay(8_000)
                            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/slow-end"))
                        }
                        else -> {
                            if (path.startsWith("/hops/")) {
                                val hop = path.substringAfterLast('/').toInt()
                                if (hop < 6) {
                                    return@addHandler respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/hops/${hop + 1}"))
                                }
                            }
                            if (path == "/photo-ready.png") assertEquals("cdn.example.invalid", request.url.host)
                            if (path == "/slow-end") delay(8_000)
                            respond(byteArrayOf(1, 2, 3), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/png"))
                        }
                    }
                }
            },
        )
        val client = HttpClient(engine) {
            defaultRequest {
                header(HttpHeaders.Authorization, "synthetic-only")
                header(HttpHeaders.Cookie, "synthetic=only")
            }
        }
        try {
            val offline = Ksoup.parse(inlineArticleExportImagesForOfflineHtml(html, client))
            assertTrue(offline.select("img").all { it.attr("src") == "data:image/png;base64,AQID" })
            assertEquals(setOf("/photo.png", "/images/step", "/photo-ready.png", "/equation"), requested.toSet())
            assertEquals("trusted", offline.select("script").single().id())

            val withFailedImage = inlineArticleExportImagesForOfflineHtml(
                html = "<p>首段</p><img src='https://example.invalid/photo.png'>" +
                    "<img src='https://example.invalid/broken' alt='&lt;script&gt;not markup&lt;/script&gt;'><p>末段</p>",
                httpClient = client,
                replaceFailedImagesWithPlaceholder = true,
            )
            val withPlaceholder = Ksoup.parse(withFailedImage)
            assertEquals("data:image/png;base64,AQID", withPlaceholder.select("img").single().attr("src"))
            assertEquals("[${WidgetsStrings.imageLoadFailed}] <script>not markup</script>", withPlaceholder.select(".export-image-placeholder").single().text())
            assertTrue(withPlaceholder.select("script").isEmpty(), "占位文字与 alt 不能变成可执行 HTML")
            assertEquals(listOf("首段", "末段"), withPlaceholder.select("p").map { it.text() })
            assertFalse(withFailedImage.contains("https://"), "失败图片不能回退为联网资源")
            assertFailsWith<IllegalStateException>("普通离线 HTML 仍严格报告缺图") {
                inlineArticleExportImagesForOfflineHtml("<img src='https://example.invalid/broken'>", client)
            }
            for (path in listOf("downgrade", "credentials", "loop", "missing-location", "hops/0")) {
                requested.clear()
                assertFailsWith<Exception>(path) {
                    inlineArticleExportImagesForOfflineHtml("<img src='https://example.invalid/$path'>", client)
                }
                assertFalse("/forbidden" in requested)
                assertFalse("/hops/6" in requested, "最多跟随五次重定向")
                if (path == "loop") assertEquals(listOf("/loop"), requested)
            }
            val fiveHops = Ksoup.parse(inlineArticleExportImagesForOfflineHtml("<img src='https://example.invalid/hops/1'>", client))
            assertEquals("data:image/png;base64,AQID", fiveHops.select("img").single().attr("src"))
            val startedAt = testScheduler.currentTime
            val timeout = assertFailsWith<IllegalStateException> {
                inlineArticleExportImagesForOfflineHtml("<img src='https://example.invalid/slow-start'>", client)
            }
            assertEquals(DomainStrings.renderTimedOut, timeout.message)
            assertEquals(15_000L, testScheduler.currentTime - startedAt, "重定向不能重置整张图片的下载预算")
            val timedOutImage = Ksoup.parse(
                inlineArticleExportImagesForOfflineHtml(
                    "<img src='https://example.invalid/slow-start'><p>末段</p>",
                    client,
                    replaceFailedImagesWithPlaceholder = true,
                ),
            )
            assertTrue(timedOutImage.select("img").isEmpty())
            assertEquals("[${WidgetsStrings.imageLoadFailed}]", timedOutImage.select(".export-image-placeholder").single().text())
            assertEquals("末段", timedOutImage.select("p").single().text())
            assertFailsWith<TimeoutCancellationException>("外部期限取消不能转成图片占位") {
                withTimeout(100) {
                    inlineArticleExportImagesForOfflineHtml(
                        "<img src='https://example.invalid/slow-start'>",
                        client,
                        replaceFailedImagesWithPlaceholder = true,
                    )
                }
            }
            client.get("https://example.invalid/still-open")
            assertTrue("/still-open" in requested, "关闭图片 client 不得关闭共享 engine")
        } finally {
            client.close()
            engine.close()
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
        val messages = mutableListOf<String>()
        val model = ArticleViewModel(
            Article(type = ArticleType.Article, id = 1L),
            client,
            object : UserMessageSink {
                override fun showShortMessage(message: String) { messages += message }
            },
        )
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
                if (failureStage == 2) {
                    assertFalse(escapedError.isCompleted, "已保存的产物不能因清理失败被报告为导出失败")
                    assertTrue(messages.last().contains(DomainStrings.exportCleanupFailed), "清理失败必须有可见警告")
                    assertFalse(export.isCancelled)
                    assertEquals(true, completed)
                    assertEquals(1, saved)
                    assertEquals(listOf(destroyError), recycleError.suppressedExceptions)
                } else {
                    val error = escapedError.await()
                    assertSame(originalCancellation.await(), error, "清理失败不能替换真实取消异常")
                    assertTrue(export.isCancelled)
                    assertNull(completed, "取消不能被报告为成功或普通失败")
                    assertEquals(0, saved)
                    val expectedSuppressed = if (failureStage == 1) 2 else 1
                    assertEquals(expectedSuppressed, error.suppressedExceptions.size)
                    if (failureStage == 1) assertSame(recycleError, error.suppressedExceptions[0])
                    assertSame(destroyError, error.suppressedExceptions.last())
                }
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
