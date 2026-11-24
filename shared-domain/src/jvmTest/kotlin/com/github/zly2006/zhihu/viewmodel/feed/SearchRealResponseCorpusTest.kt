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
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.installZhihuCommonClientConfig
import com.github.zly2006.zhihu.data.target
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 搜索夹具来自 zhurl 的真实生产响应，仅对个人身份字段脱敏。 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchRealResponseCorpusTest {
    @Test
    fun realZhihuSearchResponsesExerciseEveryTabAndDecodeFailureGuard() = runTest {
        val general = CorpusSearchViewModel()
        val generalFeeds = general.decode(SearchTab.General, fixture("search-general-sanitized.json"))
        general.process(generalFeeds)
        assertEquals(
            "如何评价<em>kotlin</em>？",
            general.displayItems
                .single()
                .feed
                ?.target
                ?.title,
        )

        val people = CorpusSearchViewModel()
        people.process(people.decode(SearchTab.People, fixture("search-people-sanitized.json")))
        val person = assertIs<SearchEntity.Person>(people.entities.single()).person
        assertEquals("redacted-person-token", person.urlToken)
        assertEquals("<em>脱敏用户</em>", person.name)

        val topic = CorpusSearchViewModel()
        topic.process(topic.decode(SearchTab.Topic, fixture("search-topic-sanitized.json")))
        val topicEntity = assertIs<SearchEntity.Topic>(topic.entities.single())
        assertEquals("Kotlin", topicEntity.topic.name)
        assertFalse(topicEntity.isFollowing)
        assertFalse(
            topic.setTopicFollowing(environment(HttpStatusCode.InternalServerError), topicEntity.id, true).isSuccess,
        )
        assertFalse(assertIs<SearchEntity.Topic>(topic.entities.single()).isFollowing)

        val drift = assertFailsWith<IllegalStateException> {
            CorpusSearchViewModel().decode(SearchTab.Topic, fixture("search-people-sanitized.json"))
        }
        assertEquals("服务端返回了 1 条话题搜索结果，但均无法解码", drift.message)
    }

    @Test
    fun failedRefreshRetainsEntitiesInEverySearchTab() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            SearchTab.entries.forEach { tab ->
                var fail = false
                var requests = 0
                val failures = mutableListOf<String>()
                val response = fixture(
                    when (tab) {
                        SearchTab.General -> "search-general-sanitized.json"
                        SearchTab.People -> "search-people-sanitized.json"
                        SearchTab.Topic -> "search-topic-sanitized.json"
                    },
                )
                val client = HttpClient(MockEngine) {
                    engine {
                        this.dispatcher = dispatcher
                        addHandler { request ->
                            requests++
                            assertEquals("/api/v4/search_v3", request.url.encodedPath)
                            assertEquals(tab.parameter, request.url.parameters["t"])
                            if (fail) error("search refresh unavailable")
                            respond(response, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                        }
                    }
                    installZhihuCommonClientConfig(mutableMapOf(), "test")
                }
                val environment = object : PaginationEnvironment {
                    override fun httpClient() = client

                    override fun authenticatedCookies() = mapOf("d_c0" to "test")

                    override suspend fun handleFetchFailure(tag: String?, error: Exception) {
                        failures += "${error.javaClass.name}: ${error.message}"
                    }
                }
                try {
                    val model = SearchViewModel("kotlin")
                    if (tab == SearchTab.General) {
                        model.refresh(environment)
                    } else {
                        model.selectTab(environment, tab)
                    }
                    advanceUntilIdle()
                    val loaded = model.entities.toList()
                    assertTrue(
                        loaded.isNotEmpty(),
                        "夹具必须加载实际的 $tab 数据项；失败=$failures；模型错误=${model.errorMessage}；请求数=$requests；加载中=${model.isLoading}",
                    )
                    fail = true

                    model.refresh(environment)
                    advanceUntilIdle()

                    assertEquals(
                        loaded,
                        model.entities.toList(),
                        "$tab 刷新失败时必须保留可见结果；失败=$failures；模型错误=${model.errorMessage}",
                    )
                    assertNotNull(model.errorMessage)
                    assertFalse(model.isLoading)
                    assertEquals(2, requests)
                } finally {
                    client.close()
                }
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun filteredFirstSearchPageContinuesToNextVisibleResult() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val requests = mutableListOf<String>()
        val failures = mutableListOf<String>()
        val response = ZhihuJson.json.parseToJsonElement(fixture("search-general-sanitized.json")).jsonObject
        val firstPage = JsonObject(
            response + (
                "paging" to buildJsonObject {
                    put("is_end", false)
                    put("next", "https://www.zhihu.com/api/v4/search_v3?t=general&q=kotlin&offset=20")
                }
            ),
        )
        var blockedAuthors = setOf("redacted-author-id")
        val client = HttpClient(MockEngine) {
            engine {
                this.dispatcher = dispatcher
                addHandler { request ->
                    requests += request.url.parameters["offset"].orEmpty()
                    if (request.url.parameters["offset"] == "20") {
                        blockedAuthors = emptySet()
                    }
                    respond(
                        (if (request.url.parameters["offset"] == "20") response else firstPage).toString(),
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
            installZhihuCommonClientConfig(mutableMapOf(), "test")
        }
        val environment = object : PaginationEnvironment {
            override fun httpClient() = client

            override fun authenticatedCookies() = mapOf("d_c0" to "test")

            override fun blockedUserIds() = blockedAuthors

            override suspend fun handleFetchFailure(tag: String?, error: Exception) {
                failures += "${error.javaClass.name}: ${error.message}"
            }
        }
        try {
            val model = SearchViewModel("kotlin")

            model.refresh(environment)
            // 第二页的真实 Room 写入使用 Default，不能用虚拟时间空闲冒充分页任务完成。
            model.viewModelScope.coroutineContext.job.children
                .forEach { it.join() }

            assertEquals(
                listOf("0", "20"),
                requests,
                "被过滤的搜索结果必须继续请求下一页；失败=$failures；模型错误=${model.errorMessage}；加载中=${model.isLoading}；已结束=${model.isEnd}",
            )
            assertEquals(1, model.entities.size, "缺少可见结果；失败=$failures；模型错误=${model.errorMessage}")
            assertTrue(model.isEnd)
            assertFalse(model.isLoading)
        } finally {
            client.close()
            Dispatchers.resetMain()
        }
    }

    private fun fixture(name: String) =
        checkNotNull(javaClass.getResource("/search/$name")).readText()

    private class CorpusSearchViewModel : SearchViewModel("kotlin") {
        private val testEnvironment = environment()

        override fun refresh(environment: PaginationEnvironment) = Unit

        fun decode(
            tab: SearchTab,
            response: String,
        ): List<Feed> {
            selectTab(testEnvironment, tab)
            return decodePage(
                testEnvironment,
                ZhihuJson.json
                    .parseToJsonElement(response)
                    .jsonObject
                    .getValue("data")
                    .jsonArray,
            )
        }

        fun process(feeds: List<Feed>) = processResponse(testEnvironment, feeds, JsonArray(emptyList()))
    }

    private companion object {
        fun environment(status: HttpStatusCode = HttpStatusCode.OK) = object : PaginationEnvironment {
            private val client by lazy { HttpClient(MockEngine { respond("{}", status) }) }

            override fun httpClient() = client

            override fun authenticatedCookies() = mapOf("d_c0" to "test")

            override suspend fun fetchJson(
                url: String,
                include: String,
            ): JsonObject? = null

            override suspend fun handleFetchFailure(
                tag: String?,
                error: Exception,
            ) = Unit
        }
    }
}
