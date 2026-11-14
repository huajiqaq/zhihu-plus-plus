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

import com.github.zly2006.zhihu.data.Collection
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.Person
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.ZhihuPaging
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CollectionsViewModelTest {
    /**
     * Contract: https://github.com/zly2006/zhihu-plus-plus/issues/525
     * Introduced by: https://github.com/zly2006/zhihu-plus-plus/pull/607
     */
    @Test
    fun createCollectionUsesVerifiedWebContract() = runTest {
        val client = jsonClient { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v4/collections", request.url.encodedPath)
            assertEquals("101_3_3.0", request.headers["x-zse-93"])
            assertTrue(request.headers["x-zse-96"].orEmpty().startsWith("2.0_"))
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals("测试收藏夹", body.getValue("title").jsonPrimitive.content)
            assertEquals("测试描述", body.getValue("description").jsonPrimitive.content)
            assertTrue(body.getValue("is_public").jsonPrimitive.boolean)
            respondJson(
                """{"status":100,"message":"","collection":{"id":123}}""",
            )
        }
        val viewModel = CollectionsViewModel("owner")

        assertTrue(
            viewModel.createCollection(
                environment = testEnvironment(client),
                title = "测试收藏夹",
                description = "测试描述",
                isPublic = true,
            ),
        )
        assertFalse(viewModel.isCreatingCollection)
        assertNull(viewModel.createCollectionError)
    }

    /**
     * Contract: https://github.com/zly2006/zhihu-plus-plus/issues/525
     * Introduced by: https://github.com/zly2006/zhihu-plus-plus/pull/607
     */
    @Test
    fun deleteCollectionTargetsOnlyTheSelectedNonDefaultCollection() = runTest {
        var requestCount = 0
        val client = jsonClient { request ->
            requestCount++
            assertEquals(HttpMethod.Delete, request.method)
            assertEquals("/api/v4/collections/selected-id", request.url.encodedPath)
            respondJson("""{"success":true}""")
        }
        val viewModel = CollectionsViewModel("owner")
        val environment = testEnvironment(client)

        assertTrue(
            viewModel.deleteCollection(
                environment,
                Collection(id = "selected-id", title = "待删除收藏夹"),
            ),
        )
        assertFalse(
            viewModel.deleteCollection(
                environment,
                Collection(id = "default-id", title = "默认收藏夹", isDefault = true),
            ),
        )
        assertEquals(1, requestCount)
        assertEquals("默认收藏夹不能删除", viewModel.deleteCollectionError)
    }

    /**
     * Contract: https://github.com/zly2006/zhihu-plus-plus/issues/525
     * Introduced by: https://github.com/zly2006/zhihu-plus-plus/pull/607
     */
    @Test
    fun httpSuccessWithoutDeleteConfirmationIsRejected() = runTest {
        val client = jsonClient { request ->
            assertEquals(HttpMethod.Delete, request.method)
            respondJson("""{"success":false,"message":"不能删除"}""")
        }
        val viewModel = CollectionsViewModel("owner")

        assertFalse(
            viewModel.deleteCollection(
                testEnvironment(client),
                Collection(id = "selected-id", title = "待删除收藏夹"),
            ),
        )
        assertEquals("不能删除", viewModel.deleteCollectionError)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun collectionContentRefreshKeepsVisibleItemsUntilAReplacementPageSucceeds() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        var fail = false
        var revised = false
        var gate: CompletableDeferred<Unit>? = null
        val failures = mutableListOf<Exception>()
        val client = HttpClient(MockEngine) {
            engine {
                this.dispatcher = dispatcher
                addHandler { request ->
                    if (request.url.encodedPath.endsWith("/items")) {
                        gate?.await()
                        check(!fail) { "收藏夹刷新失败" }
                        val items = if (revised) {
                            listOf(collectionItem(1L, "刷新后的标题"))
                        } else {
                            listOf(
                                collectionItem(1L, "原来的标题"),
                                collectionItem(2L, "原来的第二项"),
                            )
                        }
                        respondJson("""{"data":${ZhihuJson.json.encodeToString(items)},"paging":${ZhihuJson.json.encodeToString(ZhihuPaging(isEnd = true, next = ""))}}""")
                    } else {
                        respondJson("""{"collection":${ZhihuJson.json.encodeToString(Collection("collection-refresh", title = "测试收藏夹"))}}""")
                    }
                }
            }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val environment = object : PaginationEnvironment {
            override fun httpClient() = client

            override fun authenticatedCookies() = emptyMap<String, String>()

            override suspend fun handleFetchFailure(tag: String?, error: Exception) {
                failures += error
            }
        }
        try {
            val model = CollectionContentViewModel("collection-refresh")
            model.refresh(environment)
            advanceUntilIdle()
            assertEquals(listOf("原来的标题", "原来的第二项"), model.displayItems.map { it.title })

            fail = true
            gate = CompletableDeferred()
            model.refresh(environment)
            assertEquals(listOf("原来的标题", "原来的第二项"), model.displayItems.map { it.title }, "等待刷新不能先清空可见内容")
            gate!!.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf("原来的标题", "原来的第二项"), model.displayItems.map { it.title })
            assertFalse(model.isLoading)
            assertTrue(model.errorMessage?.isNotBlank() == true)
            assertEquals(1, failures.size)

            gate = null
            fail = false
            revised = true
            model.refresh(environment)
            advanceUntilIdle()
            assertEquals(listOf("刷新后的标题"), model.displayItems.map { it.title })
            assertEquals(listOf(1L), model.allData.map { (it.content as Feed.ArticleTarget).id })
            assertNull(model.errorMessage)
            assertFalse(model.isLoading)
        } finally {
            gate?.complete(Unit)
            client.close()
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun collectionMetadataFailureIsVisibleAndRetryRecoversTheExistingPage() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        var failMetadata = false
        val failures = mutableListOf<Exception>()
        val client = HttpClient(MockEngine) {
            engine {
                this.dispatcher = dispatcher
                addHandler { request ->
                    if (request.url.encodedPath.endsWith("/items")) {
                        respondJson("""{"data":[],"paging":{"is_end":true,"next":""}}""")
                    } else {
                        check(!failMetadata) { "收藏夹信息加载失败" }
                        respondJson("""{"collection":${ZhihuJson.json.encodeToString(Collection("collection-metadata", title = "已加载的收藏夹"))}}""")
                    }
                }
            }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val environment = object : PaginationEnvironment {
            override fun httpClient() = client

            override fun authenticatedCookies() = emptyMap<String, String>()

            override suspend fun handleFetchFailure(tag: String?, error: Exception) {
                failures += error
            }
        }
        try {
            val model = CollectionContentViewModel("collection-metadata")
            model.refresh(environment)
            advanceUntilIdle()
            assertEquals("已加载的收藏夹", model.title)
            failMetadata = true
            model.refresh(environment)
            advanceUntilIdle()
            assertEquals("已加载的收藏夹", model.title)
            assertFalse(model.isLoading)
            assertTrue(model.errorMessage?.isNotBlank() == true, "元数据失败必须进入可重试错误状态")
            assertEquals(1, failures.size)
            failMetadata = false
            model.refresh(environment)
            advanceUntilIdle()
            assertNull(model.errorMessage)
            assertEquals("已加载的收藏夹", model.title)
        } finally {
            client.close()
            Dispatchers.resetMain()
        }
    }

    private fun collectionItem(id: Long, title: String) = CollectionItem(
        created = "0",
        content = Feed.ArticleTarget(
            id = id,
            url = "https://zhuanlan.zhihu.com/p/$id",
            author = Person(id = "offline-author", url = "", userType = "people", name = "离线作者", headline = "", avatarUrl = ""),
            title = title,
        ),
    )

    private fun jsonClient(handler: MockRequestHandler) =
        HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

    private fun testEnvironment(client: HttpClient) = object : ZhihuApiEnvironment {
        override fun httpClient() = client

        override fun authenticatedCookies() = mapOf("d_c0" to "test-cookie")

        override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
    }

    private fun MockRequestHandleScope.respondJson(content: String) = respond(
        content = content,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )
}
