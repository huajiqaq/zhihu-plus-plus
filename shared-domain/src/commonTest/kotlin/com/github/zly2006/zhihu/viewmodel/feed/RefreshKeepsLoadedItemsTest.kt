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

import com.github.zly2006.zhihu.data.CommonFeed
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.Person
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #597：网络失败时刷新不应清空已加载内容。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RefreshKeepsLoadedItemsTest {
    private class TestFeedViewModel : BaseFeedViewModel() {
        override val initialUrl = "https://example.invalid/feed"
        var failNextFetch = false
        var nextItems: List<FeedDisplayItem> = emptyList()

        override suspend fun fetchFeeds(environment: PaginationEnvironment) {
            if (failNextFetch) error("network unavailable")
            addDisplayItems(nextItems)
        }
    }

    @Test
    fun failedRefreshKeepsExistingItems() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val client = HttpClient(MockEngine { error("不应发起 HTTP 请求") })
        try {
            val viewModel = TestFeedViewModel()
            viewModel.addDisplayItems(listOf(item("a"), item("b")))
            viewModel.failNextFetch = true

            viewModel.refresh(environment(client))
            advanceUntilIdle()

            assertEquals(listOf("a", "b"), viewModel.displayItems.map { it.title })
        } finally {
            client.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun successfulRefreshReplacesExistingItems() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val client = HttpClient(MockEngine { error("不应发起 HTTP 请求") })
        try {
            val viewModel = TestFeedViewModel()
            viewModel.addDisplayItems(listOf(item("a"), item("b")))
            viewModel.nextItems = listOf(item("c"))

            viewModel.refresh(environment(client))
            advanceUntilIdle()

            assertEquals(listOf("c"), viewModel.displayItems.map { it.title })
        } finally {
            client.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun stalePullResponseCannotReplaceNewRefreshOrFinishItsLoading() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            for (oldRequestFails in arrayOf(false, true)) {
                val oldResponse = CompletableDeferred<Unit>()
                val newResponse = CompletableDeferred<Unit>()
                var requests = 0
                val client = HttpClient(MockEngine) {
                    engine {
                        this.dispatcher = dispatcher
                        addHandler {
                            val request = ++requests
                            if (request == 1) oldResponse.await() else newResponse.await()
                            if (request == 1 && oldRequestFails) error("outdated response failed")
                            val feed = CommonFeed(
                                target = Feed.ArticleTarget(
                                    id = request.toLong(),
                                    url = "https://zhuanlan.zhihu.com/p/$request",
                                    title = "round-$request",
                                    voteupCount = 1,
                                    author = Person(id = "fixture-author", url = "", userType = "people", name = "离线作者", headline = "", avatarUrl = ""),
                                ),
                            )
                            respond(
                                """{"data":[${ZhihuJson.json.encodeToString<Feed>(feed)}],"paging":{"is_end":true,"next":""}}""",
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }
                    }
                    install(ContentNegotiation) { json(ZhihuJson.json) }
                }
                val environment = environment(client)
                val model = object : BaseFeedViewModel() {
                    override val initialUrl = "https://example.invalid/feed"
                }
                try {
                    model.displayItems.add(item("existing"))
                    val pull = launch { model.pullToRefresh(environment) }
                    runCurrent()
                    assertEquals(1, requests)
                    model.refresh(environment)
                    runCurrent()
                    assertEquals(2, requests)
                    oldResponse.complete(Unit)
                    runCurrent()
                    pull.join()
                    assertEquals("existing", model.displayItems.single().title, "旧轮不能覆盖仍在加载的新轮")
                    assertTrue(model.allData.isEmpty())
                    assertTrue(model.isLoading, "旧轮 finally 不能关掉新轮加载状态")
                    assertNull(model.errorMessage, "旧轮失败不能写进新轮错误")
                    assertFalse(model.isEnd, "旧轮终页不能阻止新轮分页")
                    newResponse.complete(Unit)
                    advanceUntilIdle()
                    assertEquals("round-2", model.displayItems.single().title)
                    assertEquals(1, model.allData.size)
                    assertTrue(model.isEnd)
                    assertFalse(model.isLoading)
                    assertFalse(model.isPullToRefresh)
                } finally {
                    client.close()
                }
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun item(title: String) = FeedDisplayItem(
        title = title,
        summary = null,
        details = "",
        feed = null,
    )

    private fun environment(client: HttpClient) = object : PaginationEnvironment {
        override fun httpClient() = client

        override fun authenticatedCookies() = emptyMap<String, String>()

        override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
    }
}
