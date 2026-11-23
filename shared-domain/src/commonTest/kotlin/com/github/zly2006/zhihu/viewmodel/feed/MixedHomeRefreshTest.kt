package com.github.zly2006.zhihu.viewmodel.feed

import com.github.zly2006.zhihu.data.CommonFeed
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.Person
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.platform.platformSettingsStore
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.za.MixedHomeFeedViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MixedHomeRefreshTest {
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
                try {
                    val model = MixedHomeFeedViewModel()
                    model.refresh(environment)
                    advanceUntilIdle()
                    assertEquals(setOf("web-0", "mobile-0"), model.displayItems.map { it.title }.toSet())
                    requests.clear()
                    round = 1
                    if (pull) model.pullToRefresh(environment) else model.refresh(environment)
                    advanceUntilIdle()
                    assertEquals(setOf("web-1", "mobile-1"), model.displayItems.map { it.title }.toSet(), "pull=$pull 必须替换旧内容")
                    assertTrue(requests.none { "page=2" in it }, "pull=$pull 刷新不能请求两个来源的旧下一页: $requests")
                    assertEquals(false, model.isPullToRefresh)
                    assertEquals(false, model.isLoading)
                    failRequests = true
                    if (pull) model.pullToRefresh(environment) else model.refresh(environment)
                    advanceUntilIdle()
                    assertEquals(setOf("web-1", "mobile-1"), model.displayItems.map { it.title }.toSet(), "两来源失败不能丢失已有内容")
                    assertEquals(false, model.isLoading)
                    assertEquals(false, model.isPullToRefresh)
                } finally {
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
