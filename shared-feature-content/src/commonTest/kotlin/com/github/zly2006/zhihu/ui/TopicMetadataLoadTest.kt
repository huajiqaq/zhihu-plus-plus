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

package com.github.zly2006.zhihu.ui

import androidx.lifecycle.viewModelScope
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.navDestination
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TopicMetadataLoadTest {
    private val clients = mutableListOf<HttpClient>()

    @AfterTest
    fun closeClients() {
        clients.forEach(HttpClient::close)
        clients.clear()
    }

    @Test
    fun topicDetailFailureWithoutExceptionMessageStillPublishesRetryableError() = runTest {
        val environment = RecordingEnvironment { throw IllegalStateException() }
        val model = TopicViewModel("1")

        model.loadDetail(environment)

        assertNull(model.detail)
        assertNotNull(model.detailErrorMessage)
        assertTrue(model.detailErrorMessage!!.isNotBlank())
        assertEquals(1, environment.requests.size)
    }

    @Test
    fun topicMalformedDetailKeepsCachedMetadataAndPublishesFailure() = runTest {
        val environment = RecordingEnvironment { json("""{"name":"missing id"}""") }
        val cached = TopicDetail(id = "1", name = "已有话题", followersCount = 7)
        val model = TopicViewModel("1", cached)

        val result = runCatching { model.loadDetail(environment) }

        assertTrue(result.isSuccess, "解码失败必须进入可重试的页面状态")
        assertEquals(cached, model.detail)
        assertNotNull(model.detailErrorMessage)
        assertEquals(1, environment.requests.size)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun topicFailedAppendStopsAutomaticLoadingAndExplicitRetryKeepsCursor() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val firstUrl = "https://www.zhihu.com/api/v5.1/topics/1/feeds/pin-hot?offset=0&limit=10"
        val nextUrl = "https://www.zhihu.com/api/v5.1/topics/1/feeds/pin-hot?offset=10&limit=10"
        val include = "data[*].content,excerpt,target.author.badge_v2"
        val firstPage = json(
            """{"data":[{"type":"pin","target":{"type":"pin","id":"101","author":{"name":"作者"},"title":"想法 101","plain_content":"想法正文"}}],"paging":{"is_end":false,"next":"$nextUrl"}}""",
        )
        val nextPage = json(
            """{"data":[{"type":"pin","target":{"type":"pin","id":"102","author":{"name":"作者"},"title":"想法 102","plain_content":"追加正文"}}],"paging":{"is_end":true}}""",
        )
        val environment = RecordingEnvironment { firstPage }
        val model = TopicViewModel("1")
        try {
            model.selectTab(environment, TopicFeedTab.Ideas)
            runCurrent()
            val cached = model.items.single()
            assertEquals(Pin(101), cached.navDestination)
            assertEquals("想法 101", cached.title)
            assertEquals("想法正文", cached.summary)
            assertEquals("作者", cached.authorName)
            assertFalse(model.isEnd)

            environment.response = { error("offline append") }
            model.loadMore(environment)
            runCurrent()
            assertEquals(listOf(cached), model.items.toList())
            assertSame(cached, model.items.single())
            assertEquals("offline append", model.errorMessage)
            assertFalse(model.isLoading)
            model.loadMore(environment)
            runCurrent()
            assertEquals(listOf(firstUrl to include, nextUrl to include), environment.requests)

            environment.response = { nextPage }
            model.retry(environment)
            runCurrent()
            assertSame(cached, model.items.first())
            assertEquals(listOf(Pin(101), Pin(102)), model.items.map { it.navDestination })
            assertEquals(listOf("想法 101", "想法 102"), model.items.map { it.title })
            assertEquals("追加正文", model.items.last().summary)
            assertTrue(model.isEnd)
            assertFalse(model.isLoading)
            assertNull(model.errorMessage)
            assertEquals(listOf(firstUrl to include, nextUrl to include, nextUrl to include), environment.requests)
        } finally {
            model.viewModelScope.coroutineContext[Job]!!.cancelAndJoin()
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun emptyTopicDoesNotReloadOnReselectAndFailedTabKeepsExplicitRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val environment = RecordingEnvironment { json("""{"data":[],"paging":{"is_end":true}}""") }
        val model = TopicViewModel("1")
        try {
            model.loadMore(environment)
            runCurrent()
            assertTrue(model.items.isEmpty())
            assertTrue(model.isEnd)
            model.selectTab(environment, TopicFeedTab.Discussion)
            runCurrent()
            assertEquals(1, environment.requests.size)

            environment.response = { error("offline tab") }
            model.selectTab(environment, TopicFeedTab.Ideas)
            runCurrent()
            assertEquals("offline tab", model.errorMessage)
            model.selectTab(environment, TopicFeedTab.Ideas)
            runCurrent()
            assertEquals(2, environment.requests.size)
            assertEquals("offline tab", model.errorMessage)

            environment.response = { json("""{"data":[],"paging":{"is_end":true}}""") }
            model.retry(environment)
            runCurrent()
            assertEquals(3, environment.requests.size)
            assertEquals(environment.requests[1], environment.requests[2])
            assertTrue(model.items.isEmpty())
            assertTrue(model.isEnd)
            assertNull(model.errorMessage)
        } finally {
            model.viewModelScope.coroutineContext[Job]!!.cancelAndJoin()
            Dispatchers.resetMain()
        }
    }

    private fun json(value: String): JsonObject = ZhihuJson.json.parseToJsonElement(value).jsonObject

    private inner class RecordingEnvironment(
        var response: suspend (String) -> JsonObject?,
    ) : PaginationEnvironment {
        val requests = mutableListOf<Pair<String, String>>()
        private val client = HttpClient(MockEngine { error("不应发起 HTTP 请求") }).also(clients::add)

        override fun httpClient(): HttpClient = client

        override fun authenticatedCookies(): Map<String, String> = emptyMap()

        override suspend fun fetchJson(url: String, include: String): JsonObject? {
            requests += url to include
            return response(url)
        }

        override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
    }
}
