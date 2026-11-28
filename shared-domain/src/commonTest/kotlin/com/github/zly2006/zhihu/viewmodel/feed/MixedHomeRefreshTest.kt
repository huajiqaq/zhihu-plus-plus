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

package com.github.zly2006.zhihu.viewmodel.feed

import androidx.lifecycle.viewModelScope
import com.github.zly2006.zhihu.data.CommonFeed
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.Person
import com.github.zly2006.zhihu.data.RecommendationMode
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.platform.platformSettingsStore
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.PaginationViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

@OptIn(ExperimentalCoroutinesApi::class)
class MixedHomeRefreshTest {
    @Test
    fun failedPagePreparationRetriesTheSameTerminalPageWithoutReplacingOldItems() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val client = HttpClient(MockEngine) { engine { addHandler { error("unused") } } }
        try {
            for (metadataFailure in listOf(false, true)) {
                val requests = mutableListOf<String>()
                var fail = true
                val model = object : PaginationViewModel<String>(typeOf<String>()) {
                    override val initialUrl = "https://example.test/first"
                    override suspend fun prepareResponse(environment: PaginationEnvironment, data: List<String>, rawData: JsonArray): () -> Unit {
                        val commit = super.prepareResponse(environment, data, rawData)
                        if (fail && !metadataFailure) error("filter failed")
                        return commit
                    }
                    override fun preparePageMetadata(json: JsonObject): () -> Unit {
                        if (fail && metadataFailure) error("metadata failed")
                        return {}
                    }
                }
                val environment = object : PaginationEnvironment {
                    override fun httpClient() = client
                    override fun authenticatedCookies() = emptyMap<String, String>()
                    override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
                    override suspend fun fetchJson(url: String, include: String): JsonObject {
                        requests += url
                        return ZhihuJson.json.parseToJsonElement("""{"data":[],"paging":{"is_end":true,"next":""}}""") as JsonObject
                    }
                }
                model.allData += "old"
                model.refresh(environment)
                advanceUntilIdle()
                assertEquals(listOf("old"), model.allData.toList())
                assertTrue(!model.isEnd && model.errorMessage != null)
                fail = false
                model.retry(environment)
                advanceUntilIdle()
                assertEquals(listOf("https://example.test/first", "https://example.test/first"), requests)
                assertTrue(model.isEnd && model.errorMessage == null)
                assertTrue(model.allData.isEmpty())
            }
        } finally {
            client.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun mixedContentIdentityDeduplicatesSourcesButPreservesHistoryInstances() {
        val feed = CommonFeed(target = Feed.AnswerTarget(id = 42, url = "", question = Feed.QuestionTarget(id = 7, _title = "question", url = "", type = "question")))
        val web = FeedDisplayItem(title = "web", summary = null, details = "", feed = feed)
        val mobile = FeedDisplayItem(title = "mobile", summary = null, details = "", feed = null, destination = Article(type = ArticleType.Answer, id = 42))
        val model = object : HomeFeedStateViewModel() {
            override val initialUrl = "unused"
            fun appendPage(items: List<FeedDisplayItem>) = commitDisplayPage(items)
            fun removeHistoryInstance(key: String) = removeDisplayItems { it.historyKey == key }
        }
        model.restoreStartupItems(listOf(web, mobile), "fixture-rules")
        assertEquals(web.contentIdentity, mobile.contentIdentity)
        assertEquals(1, model.displayItems.size)
        model.appendPage(listOf(mobile.copy(historyKey = "visit:1"), mobile.copy(historyKey = "visit:2")))
        assertEquals(3, model.displayItems.size)
        val revision = model.snapshotRevision
        model.removeHistoryInstance("visit:1")
        assertTrue(model.snapshotRevision > revision, "owner移除必须触发启动快照更新")
        val snapshot = checkNotNull(model.startupSnapshot())
        assertEquals(model.displayItems.toList(), snapshot.first)
        assertTrue(snapshot.first.none { it.historyKey == "visit:1" })
    }

    /** 下拉和按钮刷新均须重置两个来源的游标，成功后替换旧列表而非继续追加，并保留两来源新数据。 */
    @Test
    fun refreshReplacesBothSourcesAndResetsTheirPageCursors() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val settings = platformSettingsStore()
        val previousContentFilter = settings.getBoolean("enableContentFilter", true)
        val previousKeywordBlocking = settings.getBoolean("enableKeywordBlocking", true)
        val previousNlpBlocking = settings.getBoolean("enableNLPBlocking", true)
        val previousUserBlocking = settings.getBoolean("enableUserBlocking", true)
        val previousTopicBlocking = settings.getBoolean("enableTopicBlocking", true)
        settings.putBoolean("enableContentFilter", false)
        settings.putBoolean("enableKeywordBlocking", false)
        settings.putBoolean("enableNLPBlocking", false)
        settings.putBoolean("enableUserBlocking", false)
        settings.putBoolean("enableTopicBlocking", false)
        try {
            for (pull in arrayOf(true, false)) {
                val requests = mutableListOf<String>()
                var round = 0
                var failRequests = false
                val mobile = HttpClient(MockEngine) {
                    engine {
                        this.dispatcher = dispatcher
                        addHandler { request ->
                            requests += request.url.toString()
                            if (failRequests) error("mobile offline")
                            respond(
                                """{"data":[{"type":"ComponentCard","action":{"parameter":"route_url=https%3A%2F%2Fwww.zhihu.com%2Fanswer%2F${200 + round}"},"children":[{"type":"Text","id":"Text","style":"Text","text":"mobile-$round"},{"type":"Text","id":"text_pin_summary","style":"text_pin_summary","text":"summary"},{"type":"Line","style":"LineText","elements":[]},{"type":"Line","style":"Footer","elements":[{"type":"Text","text":"footer"}]},{"type":"Line","style":"RecommendAuthorLine","elements":[{"type":"Avatar","style":"Avatar_default","image":{"url":""}},{"type":"Text","style":"Text","text":"author"}]}]}],"paging":{"next":"https://api.zhihu.com/topstory/recommend?page=2","is_end":false}}""",
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                        }
                    }
                    install(ContentNegotiation) { json(ZhihuJson.json) }
                }
                val environment = object : PaginationEnvironment {
                    override fun httpClient() = mobile

                    override fun mobileHomeFeedHttpClient() = mobile

                    override fun authenticatedCookies() = emptyMap<String, String>()

                    override suspend fun handleFetchFailure(tag: String?, error: Exception) = throw error

                    override suspend fun fetchJson(url: String, include: String): JsonObject {
                        requests += url
                        if (failRequests) error("web offline")
                        val feed = CommonFeed(
                            target = Feed.ArticleTarget(
                                id = (100 + round).toLong(),
                                url = "https://zhuanlan.zhihu.com/p/${100 + round}",
                                title = "web-$round",
                                voteupCount = 10,
                                author = Person(id = "author", url = "", userType = "people", name = "author", headline = "", avatarUrl = ""),
                            ),
                        )
                        return ZhihuJson.json.parseToJsonElement("""{"data":[${ZhihuJson.json.encodeToString<Feed>(feed)}],"paging":{"next":"https://www.zhihu.com/api/v3/feed/topstory/recommend?page=2","is_end":false}}""") as JsonObject
                    }
                }
                val model = HomeFeedViewModel(RecommendationMode.MIXED)
                val modelJob = checkNotNull(model.viewModelScope.coroutineContext[Job])
                // 规则快照由 Room 的 Dispatchers.Default 查询；测试调度器空闲不代表分页完成。
                suspend fun refreshAndAwait(pull: Boolean) {
                    if (pull) {
                        model.pullToRefresh(environment)
                    } else {
                        model.refresh(environment)
                        modelJob.children.toList().forEach { it.join() }
                    }
                }
                try {
                    refreshAndAwait(pull = false)
                    assertEquals(setOf("web-0", "mobile-0"), model.displayItems.map { it.title }.toSet())
                    requests.clear()
                    round = 1
                    refreshAndAwait(pull)
                    assertEquals(setOf("web-1", "mobile-1"), model.displayItems.map { it.title }.toSet(), "pull=$pull 必须替换旧内容")
                    assertTrue(requests.none { "page=2" in it }, "pull=$pull 刷新不能请求两个来源的旧下一页: $requests")
                    assertEquals(false, model.isPullToRefresh)
                    assertEquals(false, model.isLoading)
                    failRequests = true
                    refreshAndAwait(pull)
                    assertEquals(setOf("web-1", "mobile-1"), model.displayItems.map { it.title }.toSet(), "两来源失败不能丢失已有内容")
                    assertEquals(false, model.isLoading)
                    assertEquals(false, model.isPullToRefresh)
                } finally {
                    modelJob.cancelAndJoin()
                    mobile.close()
                }
            }
        } finally {
            settings.putBoolean("enableContentFilter", previousContentFilter)
            settings.putBoolean("enableKeywordBlocking", previousKeywordBlocking)
            settings.putBoolean("enableNLPBlocking", previousNlpBlocking)
            settings.putBoolean("enableUserBlocking", previousUserBlocking)
            settings.putBoolean("enableTopicBlocking", previousTopicBlocking)
            Dispatchers.resetMain()
        }
    }
}
