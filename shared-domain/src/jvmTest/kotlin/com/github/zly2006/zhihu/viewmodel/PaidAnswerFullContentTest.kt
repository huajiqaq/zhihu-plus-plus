package com.github.zly2006.zhihu.viewmodel

import androidx.lifecycle.viewModelScope
import com.fleeksoft.ksoup.Ksoup
import com.github.zly2006.zhihu.account.ZhihuAccountRepository
import com.github.zly2006.zhihu.account.ZhihuAccountSession
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.account.ZhihuAccounts
import com.github.zly2006.zhihu.account.ZhihuSavedAccount
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.platform.UserMessageSink
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.io.files.Path
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaidAnswerFullContentTest {
    private val answerJson = buildJsonObject {
        put("answer_type", JsonPrimitive("normal"))
        put(
            "author",
            buildJsonObject {
                put("avatar_url", JsonPrimitive("https://example.com/avatar.jpg"))
                put("gender", JsonPrimitive(0))
                put("headline", JsonPrimitive("简介"))
                put("id", JsonPrimitive("1"))
                put("is_advertiser", JsonPrimitive(false))
                put("is_org", JsonPrimitive(false))
                put("name", JsonPrimitive("作者"))
                put("type", JsonPrimitive("people"))
                put("url", JsonPrimitive("https://www.zhihu.com/people/author"))
                put("url_token", JsonPrimitive("author"))
                put("user_type", JsonPrimitive("people"))
            },
        )
        put(
            "can_comment",
            buildJsonObject {
                put("status", JsonPrimitive(true))
                put("reason", JsonPrimitive(""))
            },
        )
        put("created_time", JsonPrimitive(0))
        put("updated_time", JsonPrimitive(0))
        put("excerpt", JsonPrimitive("摘要"))
        put("id", JsonPrimitive(42L))
        put(
            "question",
            buildJsonObject {
                put("created", JsonPrimitive(0))
                put("id", JsonPrimitive(7L))
                put("question_type", JsonPrimitive("normal"))
                put("title", JsonPrimitive("问题"))
                put("type", JsonPrimitive("question"))
                put("updated_time", JsonPrimitive(0))
                put("url", JsonPrimitive("https://www.zhihu.com/question/7"))
            },
        )
        put("thanks_count", JsonPrimitive(0))
        put("type", JsonPrimitive("answer"))
        put("url", JsonPrimitive("https://www.zhihu.com/answer/42"))
        put("voteup_count", JsonPrimitive(0))
    }

    private fun answer(content: String, paid: Boolean): DataHolder.Answer =
        ZhihuJson.decodeJson<DataHolder.Answer>(
            buildJsonObject {
                answerJson.forEach { (key, value) -> put(key, value) }
                put("content", JsonPrimitive(content))
                if (paid) {
                    put("paid_info", buildJsonObject { })
                    put("content_need_truncated", JsonPrimitive(true))
                }
            },
        )

    @Test
    fun truncated_answer_exposes_anchor_and_strips_app_prompt() {
        val html = "<p>第一段</p><p>最后可见段</p><div>使用 App 查看完整内容，<a>App 内查看</a></div>"
        val model = ArticleViewModel(Article(type = ArticleType.Answer, id = 42L), null)
        val rendered = model.preparePaidContent(answer(content = html, paid = true))

        assertTrue(model.paidContentActionVisible)
        assertTrue(rendered.contains("最后可见段"))
        assertFalse(rendered.contains("App 内查看"))
        val full = "<div class=\"RichText\"><p>第一段</p><p>重复段</p><p>最后可见段</p><p>购买后可见段</p><figure><img data-original=\"https://pic.example/full.jpg\" src=\"data:image/gif;base64,R0lGODlh\"/></figure></div>"
        val extra = model.fullContentAfterAnchor(full, "最后可见段")
        assertTrue(extra!!.contains("购买后可见段"))
        assertTrue(extra.contains("data-original=\"https://pic.example/full.jpg\""))
        assertFalse(extra.contains("第一段"))
        model.appendPaidContent(extra!!)
        assertTrue(model.content.contains("购买后可见段"))
        assertFalse(model.paidContentActionVisible)
    }

    @Test
    fun repeated_anchor_uses_the_matching_occurrence() {
        val model = ArticleViewModel(Article(type = ArticleType.Answer, id = 42L), null)
        val full = "<div class=\"RichText\"><p>相同段</p><p>相同段</p><p>第一段续写</p></div>"
        assertEquals("<p>第一段续写</p>", model.fullContentAfterAnchor(full, "相同段"))
    }

    @Test
    fun missing_purchase_or_missing_richtext_does_not_produce_extra_content() {
        val model = ArticleViewModel(Article(type = ArticleType.Answer, id = 42L), null)
        assertNull(model.fullContentAfterAnchor("<div>没有正文</div>", "最后可见段"))
        assertNull(model.fullContentAfterAnchor("<div class=\"RichText\"><p>第一段</p><p>最后可见段</p></div>", "最后可见段"))
        assertNull(model.fullContentAfterAnchor("<div class=\"RichText\"><p>无关</p><p>最后可见段</p></div>", "最后可见段"))
    }

    @Test
    fun unpaid_answer_has_no_full_content_action() {
        val model = ArticleViewModel(Article(type = ArticleType.Answer, id = 42L), null)
        model.preparePaidContent(answer(content = "<p>第一段</p><p>最后一段</p>", paid = false))
        assertFalse(model.paidContentActionVisible)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun loaded_paid_answer_retries_server_gates_and_appends_after_its_private_last_visible_anchor() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val directory = Files.createTempDirectory("paid-answer-current-account-")
        val previousHome = System.getProperty("user.home")
        System.setProperty("user.home", directory.toString())
        var store: ZhihuAccountStore? = null
        var model: ArticleViewModel? = null
        val responseGate = AtomicReference<CompletableDeferred<Unit>?>(null)
        val requestStarted = AtomicReference<CompletableDeferred<Unit>?>(null)
        try {
            val preview = "<p>第一段</p><p>最后可见段</p><div>使用 App 查看完整内容，<a>App 内查看</a></div>"
            val full = "<div class=\"RichText\"><p>第一段</p><p>最后可见段</p><p>购买后可见段</p><figure><img data-original=\"https://pic.example/full.jpg\" src=\"data:image/gif;base64,R0lGODlh\"/></figure></div>"

            fun detail(content: String, paid: Boolean): String = buildJsonObject {
                answerJson.forEach { (key, value) -> put(key, value) }
                put(
                    "question",
                    buildJsonObject {
                        put("created", JsonPrimitive(0))
                        put("id", JsonPrimitive(842007L))
                        put("question_type", JsonPrimitive("normal"))
                        put("title", JsonPrimitive("付费续文离线回归"))
                        put("type", JsonPrimitive("question"))
                        put("updated_time", JsonPrimitive(0))
                        put("url", JsonPrimitive("https://www.zhihu.com/question/842007"))
                    },
                )
                put("content", JsonPrimitive(content))
                if (paid) {
                    put("paid_info", buildJsonObject { })
                    put("content_need_truncated", JsonPrimitive(true))
                }
            }.toString()
            val detailResponse = AtomicReference(detail(preview, paid = true))
            val appViewResponse = AtomicReference(HttpStatusCode.OK to full)
            val appViewRequests = AtomicInteger()
            val requests = Collections.synchronizedList(mutableListOf<String>())
            val messages = mutableListOf<String>()
            val repository = ZhihuAccountRepository(Path(directory.resolve("synthetic-account.json").toString()))
            val session = ZhihuAccountSession(
                login = true,
                username = "paid-answer-synthetic",
                cookies = mutableMapOf("d_c0" to "paid-answer-synthetic-dc0", "z_c0" to "paid-answer-synthetic-session"),
                userAgent = "paid-answer-regression",
            )
            repository.saveAccounts(ZhihuAccounts(session.username, listOf(ZhihuSavedAccount(session.username, session))))
            val accountStore = ZhihuAccountStore(
                repository,
                MockEngine { request ->
                    check(request.url.protocol == URLProtocol.HTTPS && request.url.host == "www.zhihu.com")
                    check(request.method == HttpMethod.Get)
                    val path = request.url.encodedPath
                    requests.add(path)
                    val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
                    when (path) {
                        "/api/v4/answers/42" -> {
                            check(request.url.parameters["include"]?.contains("content") == true)
                            respond(detailResponse.get(), HttpStatusCode.OK, jsonHeaders)
                        }
                        "/api/v4/answers/42/relationship" -> respond("{}", HttpStatusCode.OK, jsonHeaders)
                        "/api/v4/answers/42/upvoters", "/api/v4/questions/842007/feeds" ->
                            respond("""{"data":[],"paging":{"is_end":true,"next":"","totals":0}}""", HttpStatusCode.OK, jsonHeaders)
                        "/appview/v2/answer/42" -> {
                            appViewRequests.incrementAndGet()
                            check(request.headers["x-zse-93"] != null && request.headers["x-zse-96"] != null)
                            check(request.headers[HttpHeaders.Cookie]?.contains("z_c0=paid-answer-synthetic-session") == true)
                            val response = appViewResponse.get()
                            requestStarted.get()?.complete(Unit)
                            responseGate.get()?.await()
                            respond(response.second, response.first, headersOf(HttpHeaders.ContentType, "text/html"))
                        }
                        else -> error("不应发起请求：${request.method} ${request.url}")
                    }
                },
            )
            store = accountStore
            val environment = object : ArticleLoadEnvironment {
                override fun httpClient(): HttpClient = accountStore.client.httpClient()

                override fun authenticatedCookies(): Map<String, String> = accountStore.session.cookies.toMap()

                override suspend fun <T> withAuthenticatedClient(block: suspend (HttpClient, Map<String, String>) -> T): T =
                    accountStore.client.withAuthenticatedClient(block)

                override suspend fun handleFetchFailure(tag: String?, error: Exception) = throw error
            }
            val loaded = ArticleViewModel(
                Article(type = ArticleType.Answer, id = 42L),
                environment.httpClient(),
                object : UserMessageSink {
                    override fun showShortMessage(message: String) {
                        messages.add(message)
                    }
                },
            )
            model = loaded
            loaded.loadArticle(environment)
            awaitArticleJobs(loaded)
            assertNull(loaded.loadError)
            assertFalse(loaded.isLoading)
            assertTrue(loaded.hasLoaded)
            assertTrue(accountStore.session.login)
            assertTrue(loaded.paidContentActionVisible)
            assertEquals(
                listOf("第一段", "最后可见段"),
                Ksoup
                    .parseBodyFragment(loaded.content)
                    .body()
                    .children()
                    .map { it.text() },
            )
            assertFalse(loaded.content.contains("App 内查看"))
            assertEquals(1, requests.count { it == "/api/v4/answers/42" })
            val renderedPreview = loaded.content
            val cachedPreview = loaded.toCachedContent()

            val failures = listOf(
                HttpStatusCode.OK to "<div class=\"RichText\"><p>第一段</p><p>最后可见段</p></div>",
                HttpStatusCode.OK to "<div><p>第一段</p><p>最后可见段</p><p>无 RichText 不得拼接</p></div>",
                HttpStatusCode.InternalServerError to full,
            )
            failures.forEachIndexed { index, response ->
                appViewResponse.set(response)
                val gate = CompletableDeferred<Unit>()
                responseGate.set(gate)
                loaded.loadFullContent(environment)
                loaded.loadFullContent(environment)
                assertTrue(loaded.isLoadingFullContent)
                assertTrue(loaded.paidContentActionVisible)
                assertEquals(renderedPreview, loaded.content)
                gate.complete(Unit)
                awaitArticleJobs(loaded)
                assertFalse(loaded.isLoadingFullContent)
                assertTrue(loaded.paidContentActionVisible, "缺续文或请求失败必须允许重试")
                assertEquals(renderedPreview, loaded.content, "服务端门禁或失败不得追加预览末段")
                assertEquals(index + 1, appViewRequests.get(), "加载中重复点击不得重复请求")
                assertEquals(index + 1, messages.size)
            }
            assertEquals(listOf("获取失败，请检查是否开通会员或是否购买", "获取失败，请检查是否开通会员或是否购买", "请求失败：HTTP 500 Internal Server Error"), messages)

            appViewResponse.set(HttpStatusCode.OK to full)
            responseGate.set(null)
            loaded.loadFullContent(environment)
            awaitArticleJobs(loaded)
            assertFalse(loaded.isLoadingFullContent)
            assertFalse(loaded.paidContentActionVisible)
            val body = Ksoup.parseBodyFragment(loaded.content).body()
            assertEquals(listOf("第一段", "最后可见段", "购买后可见段"), body.select("p").map { it.text() })
            assertEquals("https://pic.example/full.jpg", body.select("img").single().attr("data-original"))
            assertEquals("data:image/gif;base64,R0lGODlh", body.select("img").single().attr("src"))
            assertEquals(4, appViewRequests.get())
            loaded.loadFullContent(environment)
            awaitArticleJobs(loaded)
            assertEquals(4, appViewRequests.get(), "成功后不应再次追加同一续文")

            val completed = loaded.toCachedContent()
            val completedAnswer = completed.exportSourceContent as DataHolder.Answer
            assertTrue(completedAnswer.contentNeedTruncated && completedAnswer.paidInfo != null)
            val restored = ArticleViewModel(
                completed.article,
                environment.httpClient(),
                object : UserMessageSink {
                    override fun showShortMessage(message: String) {
                        messages.add(message)
                    }
                },
            )
            try {
                restored.seedFromCached(cachedPreview.copy(canLoadFullContent = null))
                assertTrue(restored.paidContentActionVisible, "未携带加载状态的预取详情仍须提供全文入口")
                restored.seedFromCached(cachedPreview)
                assertTrue(restored.paidContentActionVisible, "未完成的全文加载经缓存恢复后仍可重试")
                restored.seedFromCached(completed)
                assertFalse(restored.paidContentActionVisible, "全文完成状态不能被旧 paidInfo 和截断字段重置")
                assertEquals(completed, restored.toCachedContent(), "正文、原始导出和段评快照必须完整恢复")
                restored.loadFullContent(environment)
                awaitArticleJobs(restored)
                assertEquals(4, appViewRequests.get(), "缓存恢复后不得重新请求或重复追加尾图")
                assertEquals(completed.content, restored.content)
                assertEquals(3, messages.size, "缓存恢复不得产生购买失败提示")
            } finally {
                restored.viewModelScope.coroutineContext[Job]!!.cancelAndJoin()
            }

            detailResponse.set(detail("<p>第一段</p><p>最后可见段</p><p> </p><p></p><div>App 内查看完整内容</div>", paid = true))
            loaded.loadArticle(environment)
            awaitArticleJobs(loaded)
            assertNull(loaded.loadError)
            assertTrue(loaded.paidContentActionVisible, "锚点必须跳过末尾空块")
            loaded.loadFullContent(environment)
            awaitArticleJobs(loaded)
            assertEquals(
                listOf("第一段", "最后可见段", "购买后可见段"),
                Ksoup
                    .parseBodyFragment(loaded.content)
                    .select("p")
                    .map { it.text() }
                    .filter(String::isNotBlank),
            )
            assertEquals(5, appViewRequests.get())
            assertFalse(loaded.isLoadingFullContent)
            assertFalse(loaded.paidContentActionVisible)

            detailResponse.set(detail("<p>普通正文</p>", paid = false))
            loaded.loadArticle(environment)
            awaitArticleJobs(loaded)
            assertNull(loaded.loadError)
            assertFalse(loaded.paidContentActionVisible)
            loaded.loadFullContent(environment)
            awaitArticleJobs(loaded)
            assertEquals(5, appViewRequests.get(), "普通回答不能发起付费续文请求")
            assertEquals("普通正文", Ksoup.parseBodyFragment(loaded.content).text())
            assertFalse(loaded.isLoadingFullContent)

            detailResponse.set(detail(preview, paid = true))
            loaded.loadArticle(environment)
            awaitArticleJobs(loaded)
            val beforeAccountChange = loaded.content
            val started = CompletableDeferred<Unit>()
            val lateResponse = CompletableDeferred<Unit>()
            requestStarted.set(started)
            responseGate.set(lateResponse)
            loaded.loadFullContent(environment)
            started.await()
            accountStore.replaceSession(session.copy(cookies = mutableMapOf("d_c0" to "synthetic-new-device", "z_c0" to "synthetic-new-session")))
            lateResponse.complete(Unit)
            awaitArticleJobs(loaded)
            assertEquals(beforeAccountChange, loaded.content, "旧账号续文响应不能写入切换后的页面")
            assertTrue(loaded.paidContentActionVisible)
            assertFalse(loaded.isLoadingFullContent)
            assertEquals(6, appViewRequests.get())
            assertEquals(3, messages.size)
        } finally {
            responseGate.get()?.complete(Unit)
            try {
                model
                    ?.viewModelScope
                    ?.coroutineContext
                    ?.get(Job)
                    ?.cancelAndJoin()
            } finally {
                try {
                    store?.close()
                } finally {
                    if (previousHome == null) System.clearProperty("user.home") else System.setProperty("user.home", previousHome)
                    directory.toFile().deleteRecursively()
                    Dispatchers.resetMain()
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun loginPageDoesNotBecomePurchaseFailureOrAppendContent() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        var status = HttpStatusCode.OK
        val client = HttpClient(
            MockEngine {
                respond("<html><body>请您登录后查看更多专业优质内容。</body></html>", status, headersOf(HttpHeaders.ContentType, "text/html"))
            },
        )
        val messages = mutableListOf<String>()
        val model = ArticleViewModel(
            Article(type = ArticleType.Answer, id = 42),
            client,
            object : UserMessageSink {
                override fun showShortMessage(message: String) {
                    messages += message
                }
            },
        )
        val environment = object : ZhihuApiEnvironment {
            override fun httpClient() = client

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
        }
        model.preparePaidContent(answer("<p>预览</p><p>最后可见段</p><div>App 内查看完整内容</div>", paid = true))
        val before = model.content
        try {
            for (responseStatus in listOf(HttpStatusCode.OK, HttpStatusCode.Forbidden)) {
                status = responseStatus
                model.loadFullContent(environment)
                awaitArticleJobs(model)
                assertTrue(messages.last().contains("需要登录"), "需登录的网页不能冒充购买失败")
                assertEquals(before, model.content)
                assertTrue(model.paidContentActionVisible)
                assertFalse(model.isLoadingFullContent)
            }
        } finally {
            model.viewModelScope.coroutineContext[Job]!!.cancelAndJoin()
            client.close()
            Dispatchers.resetMain()
        }
    }

    private suspend fun awaitArticleJobs(model: ArticleViewModel) {
        val owner = model.viewModelScope.coroutineContext[Job]!!
        while (true) {
            val jobs = owner.children.toList()
            if (jobs.isEmpty()) return
            jobs.joinAll()
        }
    }
}
