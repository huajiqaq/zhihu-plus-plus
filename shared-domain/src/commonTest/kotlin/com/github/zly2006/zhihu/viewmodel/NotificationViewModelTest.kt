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
import com.github.zly2006.zhihu.notification.NotificationSettingsStore
import com.github.zly2006.zhihu.notification.NotificationType
import com.github.zly2006.zhihu.util.ZhihuMessageBodyEncryptor
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationViewModelTest {
    @Test
    fun marksOnlyTheRequestedMobileNotificationCategoryAsRead() = runTest {
        for (failedCategory in MobileNotificationCategory.entries) {
            val requests = mutableListOf<Pair<HttpMethod, String>>()
            val client = HttpClient(
                MockEngine { request ->
                    requests.add(request.method to request.url.toString())
                    if (request.url.toString() == failedCategory.readAllUrl) {
                        respond("", HttpStatusCode.ServiceUnavailable)
                    } else {
                        respond("", HttpStatusCode.NoContent)
                    }
                },
            )
            val environment = privateMessageEnvironment(client)
            val model = NotificationViewModel()
            model.categoryUnreadCounts.putAll(MobileNotificationCategory.entries.associateWith { 1 })
            try {
                assertFalse(model.markAllAsRead(environment))
                for (category in MobileNotificationCategory.entries) {
                    assertEquals(if (category == failedCategory) 1 else 0, model.categoryUnreadCounts.getValue(category))
                }
                assertEquals(1, model.unreadCount)
                assertEquals(MobileNotificationCategory.entries.map { HttpMethod.Post to it.readAllUrl }, requests)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun marksEachUnreadMobileNotificationCategoryWithItsOwnRequest() = runTest {
        val requests = mutableListOf<Pair<HttpMethod, String>>()
        val client = HttpClient(
            MockEngine { request ->
                requests += request.method to request.url.toString()
                respond("", HttpStatusCode.NoContent)
            },
        )
        val environment = object : MobileHomeFeedEnvironment {
            override fun httpClient() = client

            override fun mobileHomeFeedHttpClient() = client

            override fun authenticatedCookies() = emptyMap<String, String>()

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
        }
        val viewModel = NotificationViewModel()
        MobileNotificationCategory.entries.forEach { category ->
            viewModel.categoryUnreadCounts[category] = 1
        }

        assertTrue(viewModel.markAllAsRead(environment))
        for (category in MobileNotificationCategory.entries) {
            assertEquals(0, viewModel.categoryUnreadCounts.getValue(category))
        }
        assertEquals(0, viewModel.unreadCount)

        assertEquals(
            MobileNotificationCategory.entries.map { category ->
                HttpMethod.Post to category.readAllUrl
            },
            requests,
        )
    }

    @Test
    fun encryptsPrivateMessageFormAndAddsServerMessage() = runTest {
        var requestMethod: HttpMethod? = null
        var requestUrl = ""
        var requestContentType = ""
        var requestEncryptionHeader = ""
        var requestBody = ""
        val client = HttpClient(
            MockEngine { request ->
                requestMethod = request.method
                requestUrl = request.url.toString()
                val body = request.body as OutgoingContent.ByteArrayContent
                requestContentType = body.contentType?.toString().orEmpty()
                requestEncryptionHeader = request.headers["X-Zse-93"].orEmpty()
                requestBody = body.bytes().decodeToString()
                respond(
                    content =
                        """
                        {
                          "id":"sent-message",
                          "type":"message",
                          "content_type":0,
                          "content":"测试 & 消息",
                          "created_time":1785743000,
                          "sender":{"id":"me","name":"我"},
                          "receiver":{"id":"peer-id","name":"关注者"}
                        }
                        """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        )
        val environment = object : MobileHomeFeedEnvironment {
            override fun httpClient() = client

            override fun mobileHomeFeedHttpClient() = client

            override fun authenticatedCookies() = mapOf("d_c0" to "test-device-cookie")

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
        }
        val viewModel = PrivateMessageViewModel("peer-id")

        assertTrue(viewModel.sendMessage("测试 & 消息", environment))

        assertEquals(HttpMethod.Post, requestMethod)
        assertEquals("https://api.zhihu.com/messages", requestUrl)
        assertTrue(requestContentType.startsWith(ContentType.Application.FormUrlEncoded.toString()))
        assertEquals("101_1_1.0", requestEncryptionHeader)
        assertEquals(
            "iysSOrmMn/PKkw/O3kD3zj8Zek4IHXEc68IeyNqGB11qQkNt9MTqRTLNSVgELiW3" +
                "TEqBuzSn0sFSH+2mf9H1JNqm4Wpvdlz4c+5Zh4dD65OLN4k32e2ArRDBoLJMj48abfwSVd0Daz9a4aMjncI6cA==",
            requestBody,
        )
        assertEquals("sent-message", viewModel.allData.single().id)
        assertFalse(viewModel.isSending)
    }

    @Test
    fun messageBodyEncryptionMatchesDeidentifiedProtocolVector() {
        assertEquals("14RJeQ+vLOS4ihOY/LtYCg==", ZhihuMessageBodyEncryptor.encrypt("hello"))
    }

    @Test
    fun failedPrivateMessageSendKeepsListAndExposesServerReason() = runTest {
        var protocolHeader: String? = null
        var signatureHeader: String? = null
        val client = HttpClient(
            MockEngine { request ->
                protocolHeader = request.headers["X-Zse-93"]
                signatureHeader = request.headers["X-Zse-96"]
                respond(
                    content = """{"error":{"code":40380,"message":"暂时不能向对方发送私信"}}""",
                    status = HttpStatusCode.Forbidden,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        )
        val environment = object : MobileHomeFeedEnvironment {
            override fun httpClient() = client

            override fun mobileHomeFeedHttpClient() = client

            override fun authenticatedCookies() = emptyMap<String, String>()

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
        }
        val viewModel = PrivateMessageViewModel("peer-id")

        assertFalse(viewModel.sendMessage("保留的草稿", environment))

        assertTrue(viewModel.allData.isEmpty())
        assertEquals("暂时不能向对方发送私信", viewModel.errorMessage)
        assertFalse(viewModel.isSending)
        assertEquals("101_1_1.0", protocolHeader)
        assertNull(signatureHeader)
    }

    @Test
    fun privateMessagePollingFetchesUntilOverlapAndPreservesHistory() = runTest {
        val requestedUrls = mutableListOf<String>()
        val client = HttpClient(
            MockEngine { request ->
                requestedUrls += request.url.toString()
                val content = if (request.url.parameters["after_id"] == null) {
                    privateMessagePage(
                        ids = listOf("new-2" to 300L, "new-1" to 200L),
                        next = "https://api.zhihu.com/messages?sender_id=peer-id&after_id=200&limit=20",
                    )
                } else {
                    privateMessagePage(
                        ids = listOf("old-2" to 100L, "old-1" to 50L),
                        isEnd = true,
                    )
                }
                respond(
                    content = content,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        ) {
            install(ContentNegotiation) { json() }
        }
        val environment = object : MobileHomeFeedEnvironment {
            override fun httpClient() = client

            override fun mobileHomeFeedHttpClient() = client

            override fun authenticatedCookies() = emptyMap<String, String>()

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
        }
        val viewModel = PrivateMessageViewModel("peer-id")
        viewModel.allData += listOf(
            privateMessage("old-2", 100L),
            privateMessage("old-1", 50L),
        )

        viewModel.refreshLatest(environment)

        assertEquals(
            listOf("new-2", "new-1", "old-2", "old-1"),
            viewModel.allData.map { it.id },
        )
        assertEquals(2, requestedUrls.size)
    }

    @Test
    fun privateMessagePollingPreservesServerOrderWithinTheSameSecond() = runTest {
        var poll = 0
        val client = HttpClient(
            MockEngine {
                val ids = if (poll++ == 0) {
                    listOf("old-4" to 100L, "old-3" to 100L, "old-2" to 100L, "old-1" to 100L)
                } else {
                    listOf("new-2" to 100L, "new-1" to 100L, "old-4" to 100L, "old-3" to 100L)
                }
                respond(
                    privateMessagePage(ids, next = "https://api.zhihu.com/messages?after_id=history"),
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        ) {
            install(ContentNegotiation) { json() }
        }
        val environment = privateMessageEnvironment(client)
        val viewModel = PrivateMessageViewModel("peer-id")

        viewModel.refreshLatest(environment)
        repeat(2) { viewModel.refreshLatest(environment) }

        assertEquals(listOf("new-2", "new-1", "old-4", "old-3", "old-2", "old-1"), viewModel.allData.map { it.id })
    }

    @Test
    fun privateMessagePollingBridgesBacklogAfterSendingBeforePolling() = runTest {
        val requestedPages = mutableListOf<String?>()
        var sent = false
        val client = HttpClient(
            MockEngine { request ->
                val content = if (request.method == HttpMethod.Post) {
                    sent = true
                    """{"id":"sent","created_time":400}"""
                } else {
                    val afterId = request.url.parameters["after_id"]
                    requestedPages += afterId
                    when {
                        !sent -> privateMessagePage(listOf("old" to 100L), next = "https://api.zhihu.com/messages?after_id=history")
                        afterId == "middle" -> privateMessagePage(
                            listOf("new-2" to 200L, "new-1" to 100L),
                            next = "https://api.zhihu.com/messages?after_id=overlap",
                        )
                        afterId == "overlap" -> privateMessagePage(listOf("old" to 100L), isEnd = true)
                        else -> privateMessagePage(
                            listOf("sent" to 400L, "new-3" to 300L),
                            next = "https://api.zhihu.com/messages?after_id=middle",
                        )
                    }
                }
                respond(content, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            },
        ) {
            install(ContentNegotiation) { json() }
        }
        val environment = privateMessageEnvironment(client)
        val viewModel = PrivateMessageViewModel("peer-id")
        viewModel.refreshLatest(environment)
        assertTrue(viewModel.sendMessage("消息", environment))
        assertEquals("sent", viewModel.allData.first().id)
        requestedPages.clear()

        viewModel.refreshLatest(environment)

        assertEquals(listOf(null, "middle", "overlap"), requestedPages)
        assertEquals(listOf("sent", "new-3", "new-2", "new-1", "old"), viewModel.allData.map { it.id })
        requestedPages.clear()
        viewModel.refreshLatest(environment)
        assertEquals(listOf<String?>(null), requestedPages)
    }

    @Test
    fun privateMessagePollingKeepsConcurrentSendAheadOfItsOlderSnapshot() = runTest {
        val pollStarted = CompletableDeferred<Unit>()
        val completePoll = CompletableDeferred<Unit>()
        var polls = 0
        val client = HttpClient(
            MockEngine { request ->
                val content = when {
                    request.method == HttpMethod.Post -> """{"id":"sent","created_time":100}"""
                    polls++ == 0 -> privateMessagePage(listOf("old" to 100L), isEnd = true)
                    else -> {
                        pollStarted.complete(Unit)
                        completePoll.await()
                        privateMessagePage(listOf("new" to 100L, "old" to 100L), isEnd = true)
                    }
                }
                respond(content, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            },
        ) {
            install(ContentNegotiation) { json() }
        }
        val environment = privateMessageEnvironment(client)
        val viewModel = PrivateMessageViewModel("peer-id")
        viewModel.refreshLatest(environment)
        val polling = launch { viewModel.refreshLatest(environment) }
        pollStarted.await()

        assertTrue(viewModel.sendMessage("消息", environment))
        completePoll.complete(Unit)
        polling.join()

        assertEquals(listOf("sent", "new", "old"), viewModel.allData.map { it.id })
    }

    @Test
    fun privateMessagePollingRetriesTheGapAfterAMiddlePageFails() = runTest {
        var sent = false
        var failMiddlePage = true
        val client = HttpClient(
            MockEngine { request ->
                val content = when {
                    request.method == HttpMethod.Post -> {
                        sent = true
                        """{"id":"sent","created_time":300}"""
                    }
                    !sent -> privateMessagePage(listOf("old" to 100L), isEnd = true)
                    request.url.parameters["after_id"] == "middle" -> {
                        check(!failMiddlePage) { "中间页暂时失败" }
                        privateMessagePage(listOf("new-1" to 150L, "old" to 100L), isEnd = true)
                    }
                    else -> privateMessagePage(
                        listOf("sent" to 300L, "new-2" to 200L),
                        next = "https://api.zhihu.com/messages?after_id=middle",
                    )
                }
                respond(content, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            },
        ) {
            install(ContentNegotiation) { json() }
        }
        val environment = privateMessageEnvironment(client)
        val viewModel = PrivateMessageViewModel("peer-id")
        viewModel.refreshLatest(environment)
        assertTrue(viewModel.sendMessage("消息", environment))

        viewModel.refreshLatest(environment)

        assertEquals(listOf("sent", "old"), viewModel.allData.map { it.id })
        failMiddlePage = false
        viewModel.refreshLatest(environment)
        assertEquals(listOf("sent", "new-2", "new-1", "old"), viewModel.allData.map { it.id })
    }

    private fun privateMessageEnvironment(client: HttpClient) = object : MobileHomeFeedEnvironment {
        override fun httpClient() = client

        override fun mobileHomeFeedHttpClient() = client

        override fun authenticatedCookies() = emptyMap<String, String>()

        override suspend fun handleFetchFailure(tag: String?, error: Exception) = throw error
    }

    private fun privateMessage(id: String, createdTime: Long) =
        com.github.zly2006.zhihu.data.ZhihuPrivateMessage(
            id = id,
            createdTime = createdTime,
        )

    private fun privateMessagePage(
        ids: List<Pair<String, Long>>,
        next: String = "",
        isEnd: Boolean = false,
    ): String =
        """
        {
          "data": [${ids.joinToString(",") { (id, createdTime) ->
            """{"id":"$id","type":"message","content":"$id","created_time":$createdTime}"""
        }}],
          "paging": {"is_end":$isEnd,"next":"$next"}
        }
        """.trimIndent()

    @Test
    fun refreshPreservesLoadedTail() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        try {
            var firstPageRequests = 0
            val client = HttpClient(
                MockEngine { request ->
                    val body = when {
                        request.url.toString().contains("/page-2") ->
                            notificationPage(listOf("n3", "n4"), next = null)
                        else -> {
                            firstPageRequests += 1
                            if (firstPageRequests == 1) {
                                notificationPage(
                                    listOf("n1", "n2"),
                                    next = "https://example.invalid/notifications/page-2",
                                )
                            } else {
                                notificationPage(listOf("n1"), next = null)
                            }
                        }
                    }
                    respond(
                        content = body,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                },
            ) {
                install(ContentNegotiation) { json() }
            }
            var fetchFailure: Exception? = null
            val environment = object : PaginationEnvironment {
                override fun httpClient() = client

                override fun authenticatedCookies() = emptyMap<String, String>()

                override suspend fun handleFetchFailure(tag: String?, error: Exception) {
                    fetchFailure = error
                }
            }
            val viewModel = NotificationViewModel()

            viewModel.loadMore(environment)
            awaitIdle(viewModel)
            assertEquals(null, fetchFailure)
            assertEquals(listOf("n1", "n2"), viewModel.allData.map { it.stableId })

            viewModel.loadMore(environment)
            awaitIdle(viewModel)
            assertEquals(listOf("n1", "n2", "n3", "n4"), viewModel.allData.map { it.stableId })

            viewModel.refresh(environment)
            awaitIdle(viewModel)

            // refresh 更新第一页，同时保留已加载的尾部，避免返回时 index 锚点失效。
            assertEquals(listOf("n1", "n2", "n3", "n4"), viewModel.allData.map { it.stableId })
        } finally {
            Dispatchers.resetMain()
        }
    }

    /** 三个真实分页入口首次请求失败都必须结束等待并公开可重试错误，不依赖临时 Toast。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun everyNotificationPaginationOwnerPublishesItsInitialFetchError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val failures = mutableListOf<Exception>()
        val client = HttpClient(MockEngine) {
            engine {
                this.dispatcher = dispatcher
                addHandler { error("通知列表离线") }
            }
            install(ContentNegotiation) { json() }
        }
        val environment = notificationPagingEnvironment(client, failures)
        try {
            for (model in listOf<PaginationViewModel<*>>(NotificationViewModel(), NotificationTimelineViewModel("like"), PrivateMessageViewModel("peer-id"))) {
                model.refresh(environment)
                advanceUntilIdle()
                assertFalse(model.isLoading)
                assertTrue(model.allData.isEmpty())
                assertTrue(model.errorMessage?.isNotBlank() == true, "${model::class.simpleName} 首次失败必须有错误状态")
            }
            assertEquals(3, failures.size)
        } finally {
            client.close()
            Dispatchers.resetMain()
        }
    }

    /** 普通时间线刷新同 ID 记录必须更新；请求等待和失败不能丢失已显示记录。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun timelineRefreshUpdatesSameIdAndKeepsLoadedItemsOnFailure() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        var title = "原通知"
        var fail = false
        var gate: CompletableDeferred<Unit>? = null
        val failures = mutableListOf<Exception>()
        val client = HttpClient(MockEngine) {
            engine {
                this.dispatcher = dispatcher
                addHandler {
                    gate?.await()
                    check(!fail) { "通知刷新失败" }
                    respond(
                        """{"data":[{"id":"timeline-1","unique_id":"timeline-1","type":"notification","content":{"title":"$title"}}],"paging":{"is_end":true,"next":""}}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
            install(ContentNegotiation) { json() }
        }
        val environment = notificationPagingEnvironment(client, failures)
        try {
            val model = NotificationTimelineViewModel("like")
            model.refresh(environment)
            advanceUntilIdle()
            assertEquals(
                "原通知",
                model.allData
                    .single()
                    .content
                    ?.title,
            )
            gate = CompletableDeferred()
            model.refresh(environment)
            assertEquals(
                "原通知",
                model.allData
                    .single()
                    .content
                    ?.title,
                "等待刷新仍展示原内容",
            )
            title = "更新通知"
            gate!!.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, model.allData.size)
            assertEquals(
                "更新通知",
                model.allData
                    .single()
                    .content
                    ?.title,
            )
            gate = null
            fail = true
            model.refresh(environment)
            advanceUntilIdle()
            assertEquals(
                "更新通知",
                model.allData
                    .single()
                    .content
                    ?.title,
            )
            assertTrue(model.errorMessage?.isNotBlank() == true)
            assertFalse(model.isLoading)
            assertEquals(1, failures.size)
            fail = false
            model.loadMore(environment)
            advanceUntilIdle()
            assertNull(model.errorMessage)
            assertEquals(
                "更新通知",
                model.allData
                    .single()
                    .content
                    ?.title,
            )
        } finally {
            gate?.complete(Unit)
            client.close()
            Dispatchers.resetMain()
        }
    }

    /** 会话刷新走实际列表请求，不能因预先按旧 ID 去重吞掉更新；失败保留当前消息。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun privateMessageRefreshUpdatesSameIdAndKeepsMessagesOnFailure() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        var body = "原消息"
        var fail = false
        var gate: CompletableDeferred<Unit>? = null
        val failures = mutableListOf<Exception>()
        val client = HttpClient(MockEngine) {
            engine {
                this.dispatcher = dispatcher
                addHandler { request ->
                    val response = if (request.url.encodedPath.startsWith("/messages/user/")) {
                        """{"id":"peer-id","name":"离线用户"}"""
                    } else {
                        gate?.await()
                        check(!fail) { "私信刷新失败" }
                        """{"data":[{"id":"message-1","type":"message","content":"$body","created_time":100}],"paging":{"is_end":true,"next":""}}"""
                    }
                    respond(response, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
            install(ContentNegotiation) { json() }
        }
        val environment = notificationPagingEnvironment(client, failures)
        try {
            val model = PrivateMessageViewModel("peer-id")
            model.refresh(environment)
            advanceUntilIdle()
            assertEquals("原消息", model.allData.single().content)
            gate = CompletableDeferred()
            model.refresh(environment)
            assertEquals("原消息", model.allData.single().content, "等待刷新仍展示消息")
            body = "更新消息"
            gate!!.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, model.allData.size)
            assertEquals("更新消息", model.allData.single().content)
            gate = null
            fail = true
            model.refresh(environment)
            advanceUntilIdle()
            assertEquals("更新消息", model.allData.single().content)
            assertTrue(model.errorMessage?.isNotBlank() == true)
            assertFalse(model.isLoading)
            assertEquals(1, failures.size)
            fail = false
            model.loadMore(environment)
            advanceUntilIdle()
            assertNull(model.errorMessage)
            assertEquals("更新消息", model.allData.single().content)
        } finally {
            gate?.complete(Unit)
            client.close()
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun replacingTheAccountClearsEveryNotificationOwnerBeforeItsNextResponse() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val newResponse = CompletableDeferred<Unit>()
        val headers = headersOf(HttpHeaders.ContentType, "application/json")

        fun client(account: String) = HttpClient(MockEngine) {
            install(ContentNegotiation) { json() }
            engine {
                this.dispatcher = dispatcher
                addHandler { request ->
                    if (account == "new") newResponse.await()
                    val body = when {
                        request.url.encodedPath.startsWith("/messages/user/") -> """{"id":"peer-id","name":"$account"}"""
                        request.url.encodedPath == "/messages" -> privateMessagePage(arrayOf("$account-message" to 1L).asList(), isEnd = true)
                        request.url.encodedPath.contains("/timeline/") -> """{"data":[{"id":"$account-notification","type":"aggregate_notification"}],"paging":{"is_end":true,"next":""}}"""
                        else -> notificationPage(arrayOf("$account-notification").asList(), next = null)
                    }
                    respond(body, headers = headers)
                }
            }
        }
        val oldClient = client("old")
        val newClient = client("new")
        var currentClient = oldClient
        val failures = mutableListOf<Exception>()
        val environment = object : NotificationEnvironment by notificationPagingEnvironment(oldClient, failures) {
            override fun httpClient() = currentClient

            override fun mobileHomeFeedHttpClient() = currentClient
        }
        val models = arrayOf<PaginationViewModel<*>>(NotificationViewModel(), NotificationTimelineViewModel("like"), PrivateMessageViewModel("peer-id"))
        try {
            for (model in models) model.refresh(environment)
            advanceUntilIdle()
            models.forEach { assertEquals(1, it.allData.size) }
            currentClient = newClient
            for (model in models) {
                model.refresh(environment)
                assertTrue(model.allData.isEmpty(), "${model::class.simpleName} 必须立即清除旧账户行，不能等新账户网络响应")
            }
            runCurrent()
            newResponse.complete(Unit)
            advanceUntilIdle()
            assertEquals("new-notification", (models[0] as NotificationViewModel).allData.single().id)
            assertEquals("new-notification", (models[1] as NotificationTimelineViewModel).allData.single().id)
            assertEquals("new-message", (models[2] as PrivateMessageViewModel).allData.single().id)
            assertTrue(failures.isEmpty())
        } finally {
            newResponse.complete(Unit)
            models.forEach { it.viewModelScope.coroutineContext[kotlinx.coroutines.Job]!!.cancelAndJoin() }
            oldClient.close()
            newClient.close()
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun replacementAccountSecondPageKeepsItsFirstPage() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val oldClient = HttpClient(MockEngine) {
            install(ContentNegotiation) { json() }
            engine {
                this.dispatcher = dispatcher
                addHandler { respond(notificationPage(arrayOf("old").asList(), null), headers = headersOf(HttpHeaders.ContentType, "application/json")) }
            }
        }
        val newClient = HttpClient(MockEngine) {
            install(ContentNegotiation) { json() }
            engine {
                this.dispatcher = dispatcher
                addHandler { request ->
                    val second = request.url.parameters["page"] == "2"
                    respond(notificationPage(arrayOf(if (second) "new-2" else "new-1").asList(), if (second) null else "https://api.zhihu.com/notifications/v3/message/v3?page=2"), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
        }
        var currentClient = oldClient
        val failures = mutableListOf<Exception>()
        val environment = object : NotificationEnvironment by notificationPagingEnvironment(oldClient, failures) {
            override fun httpClient() = currentClient

            override fun mobileHomeFeedHttpClient() = currentClient
        }
        val model = NotificationViewModel()
        try {
            model.refresh(environment)
            advanceUntilIdle()
            currentClient = newClient
            model.refresh(environment)
            advanceUntilIdle()
            model.loadMore(environment)
            advanceUntilIdle()
            assertEquals(arrayOf("new-1", "new-2").asList(), model.allData.map { it.id })
            assertTrue(failures.isEmpty())
        } finally {
            model.viewModelScope.coroutineContext[kotlinx.coroutines.Job]!!.cancelAndJoin()
            oldClient.close()
            newClient.close()
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun abandonedNotificationResponsesCannotChangeReplacementAccountOrEndItsLoading() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val oldStarted = CompletableDeferred<Unit>()
        val releaseOld = CompletableDeferred<Unit>()
        val releaseNew = CompletableDeferred<Unit>()
        var oldRequests = 0
        val oldClient = HttpClient(MockEngine) {
            install(ContentNegotiation) { json() }
            engine {
                this.dispatcher = dispatcher
                addHandler {
                    if (oldRequests++ == 1) {
                        oldStarted.complete(Unit)
                        releaseOld.await()
                    }
                    respond(notificationPage(arrayOf("old-notification").asList(), null), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
        }
        val newClient = HttpClient(MockEngine) {
            install(ContentNegotiation) { json() }
            engine {
                this.dispatcher = dispatcher
                addHandler {
                    releaseNew.await()
                    respond(notificationPage(arrayOf("new-notification").asList(), null), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
        }
        var currentClient = oldClient
        val failures = mutableListOf<Exception>()
        val environment = object : NotificationEnvironment by notificationPagingEnvironment(oldClient, failures) {
            override fun httpClient() = currentClient

            override fun mobileHomeFeedHttpClient() = currentClient
        }
        val model = NotificationViewModel()
        try {
            model.refresh(environment)
            advanceUntilIdle()
            model.refresh(environment)
            runCurrent()
            oldStarted.await()
            currentClient = newClient
            model.refresh(environment)
            runCurrent()
            assertTrue(model.isLoading)
            assertTrue(model.allData.isEmpty())
            releaseOld.complete(Unit)
            runCurrent()
            assertTrue(model.isLoading, "旧 finally 不能结束新账户加载")
            assertTrue(model.allData.isEmpty(), "旧响应不能重新发布旧账户消息")
            assertNull(model.errorMessage)
            assertTrue(failures.isEmpty())
            releaseNew.complete(Unit)
            advanceUntilIdle()
            assertEquals("new-notification", model.allData.single().id)
            assertFalse(model.isLoading)
        } finally {
            releaseOld.complete(Unit)
            releaseNew.complete(Unit)
            model.viewModelScope.coroutineContext[kotlinx.coroutines.Job]!!.cancelAndJoin()
            oldClient.close()
            newClient.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun oldAccountReadAndSendCannotCommitOrContinueAgainstReplacementClient() = runTest {
        val readStarted = CompletableDeferred<Unit>()
        val sendStarted = CompletableDeferred<Unit>()
        val releaseRead = CompletableDeferred<Unit>()
        val releaseSend = CompletableDeferred<Unit>()
        var newAccountRequests = 0
        val oldClient = HttpClient(
            MockEngine { request ->
                if (request.url.encodedPath == "/messages") {
                    sendStarted.complete(Unit)
                    releaseSend.await()
                    respond("""{"id":"old-sent-message","created_time":100}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                } else {
                    readStarted.complete(Unit)
                    releaseRead.await()
                    respond("", HttpStatusCode.NoContent)
                }
            },
        )
        val newClient = HttpClient(
            MockEngine {
                newAccountRequests++
                respond("", HttpStatusCode.NoContent)
            },
        )
        var currentClient = oldClient
        val environment = object : MobileHomeFeedEnvironment {
            override fun httpClient() = currentClient

            override fun mobileHomeFeedHttpClient() = currentClient

            override fun authenticatedCookies() = emptyMap<String, String>()

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
        }
        val notifications = NotificationViewModel()
        notifications.categoryUnreadCounts.putAll(MobileNotificationCategory.entries.associateWith { 1 })
        val messages = PrivateMessageViewModel("peer-id")
        val read = async { notifications.markAllAsRead(environment) }
        val send = async { messages.sendMessage("只发给原账户的消息", environment) }
        try {
            readStarted.await()
            sendStarted.await()
            currentClient = newClient
            releaseRead.complete(Unit)
            releaseSend.complete(Unit)
            assertFalse(read.await(), "旧账户操作不能被报告为新账户成功")
            assertFalse(send.await(), "旧发送确认不能清除新账户草稿")
            assertEquals(0, newAccountRequests, "全部已读不能在中途改投新账户")
            assertTrue(messages.allData.isEmpty())
            assertEquals(1, notifications.categoryUnreadCounts.getValue(MobileNotificationCategory.Comment))
        } finally {
            releaseRead.complete(Unit)
            releaseSend.complete(Unit)
            read.cancelAndJoin()
            send.cancelAndJoin()
            oldClient.close()
            newClient.close()
        }
    }

    @Test
    fun successfulHttpStatusWithoutServerMessageIdCannotConfirmASend() = runTest {
        val client = HttpClient(MockEngine { respond("{}", headers = headersOf(HttpHeaders.ContentType, "application/json")) })
        val model = PrivateMessageViewModel("peer-id")
        model.allData.add(privateMessage("retained", 1L))
        try {
            assertFalse(model.sendMessage("保留草稿", privateMessageEnvironment(client)))
            assertEquals(listOf("retained"), model.allData.map { it.id })
            assertEquals("发送未获服务端确认", model.errorMessage)
            assertFalse(model.isSending)
        } finally {
            client.close()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun manualRefreshRetainsConfirmedSendHistoryAndItsOpaquePageCursor() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            for (oldIsEnd in listOf(false, true)) {
                for (newPageCount in listOf(2, 3)) {
                    val latestUrl = "https://api.zhihu.com/messages?limit=20&sender_id=peer-id"
                    val sendUrl = "https://api.zhihu.com/messages"
                    val peerUrl = "https://api.zhihu.com/messages/user/peer-id"
                    val cursor = "https://api.zhihu.com/messages?sender_id=peer-id&after_id=opaque-history&limit=20"
                    val middleUrl = "https://api.zhihu.com/messages?sender_id=peer-id&after_id=opaque-middle&limit=20"
                    val boundaryUrl = "https://api.zhihu.com/messages?sender_id=peer-id&after_id=opaque-boundary&limit=20"
                    val started = CompletableDeferred<Unit>()
                    val release = CompletableDeferred<Unit>()
                    var firstPages = 0
                    var sends = 0
                    var refreshing = false
                    var failMiddle = true
                    val requested = mutableListOf<Pair<HttpMethod, String>>()
                    val peers = mutableListOf<String>()
                    val client = HttpClient(MockEngine) {
                        install(ContentNegotiation) { json() }
                        engine {
                            this.dispatcher = dispatcher
                            addHandler { request ->
                                val url = request.url.toString()
                                val body = if (url == peerUrl) {
                                    peers.add(url)
                                    """{"id":"peer-id","name":"用户"}"""
                                } else {
                                    requested.add(request.method to url)
                                    when {
                                        request.method == HttpMethod.Post -> {
                                            sends++
                                            if (sends == 1) """{"id":"sent","created_time":600}""" else """{"id":"late-sent","created_time":700}"""
                                        }
                                        url == cursor -> privateMessagePage(listOf("history" to 10L), isEnd = true)
                                        url == middleUrl -> {
                                            check(!failMiddle) { "中间页暂时失败" }
                                            privateMessagePage(
                                                if (newPageCount == 2) {
                                                    listOf("new-2" to 300L, "new-1" to 200L, "old" to 100L, "old-older" to 50L)
                                                } else {
                                                    listOf("new-2" to 300L, "new-1" to 200L)
                                                },
                                                next = if (newPageCount == 3) {
                                                    boundaryUrl
                                                } else if (oldIsEnd) {
                                                    ""
                                                } else {
                                                    cursor
                                                },
                                                isEnd = newPageCount == 2 && oldIsEnd,
                                            )
                                        }
                                        url == boundaryUrl -> privateMessagePage(
                                            listOf("old" to 100L, "old-older" to 50L),
                                            next = if (oldIsEnd) "" else cursor,
                                            isEnd = oldIsEnd,
                                        )
                                        else -> {
                                            assertEquals(latestUrl, url)
                                            firstPages++
                                            if (refreshing) {
                                                if (!started.isCompleted) started.complete(Unit)
                                                release.await()
                                                privateMessagePage(listOf("sent" to 600L, "new-4" to 500L, "new-3" to 400L), next = middleUrl)
                                            } else {
                                                privateMessagePage(listOf("old" to 100L, "old-older" to 50L), next = if (oldIsEnd) "" else cursor, isEnd = oldIsEnd)
                                            }
                                        }
                                    }
                                }
                                respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                            }
                        }
                    }
                    val failures = mutableListOf<Exception>()
                    val environment = notificationPagingEnvironment(client, failures)
                    val model = PrivateMessageViewModel("peer-id")
                    try {
                        model.refresh(environment)
                        advanceUntilIdle()
                        assertEquals(listOf("old", "old-older"), model.allData.map { it.id })
                        assertEquals(oldIsEnd, model.isEnd)
                        val sent = async { model.sendMessage("消息", environment) }
                        advanceUntilIdle()
                        assertTrue(sent.await())
                        assertEquals(listOf("sent", "old", "old-older"), model.allData.map { it.id })

                        refreshing = true
                        model.refresh(environment)
                        runCurrent()
                        assertTrue(started.isCompleted)
                        assertTrue(model.isLoading)
                        assertEquals(listOf("sent", "old", "old-older"), model.allData.map { it.id })
                        val lateSend = async { model.sendMessage("刷新中发送", environment) }
                        advanceUntilIdle()
                        assertTrue(lateSend.await())
                        assertEquals(listOf("late-sent", "sent", "old", "old-older"), model.allData.map { it.id })
                        release.complete(Unit)
                        advanceUntilIdle()
                        assertFalse(model.isLoading)
                        assertTrue(model.errorMessage?.isNotBlank() == true)
                        assertEquals(1, failures.size)
                        assertEquals(listOf("late-sent", "sent", "old", "old-older"), model.allData.map { it.id }, "未补齐的首页不能变为连续同步边界")
                        val expected = mutableListOf(
                            HttpMethod.Get to latestUrl,
                            HttpMethod.Post to sendUrl,
                            HttpMethod.Get to latestUrl,
                            HttpMethod.Post to sendUrl,
                            HttpMethod.Get to middleUrl,
                        )
                        assertEquals(expected, requested)

                        failMiddle = false
                        model.retry(environment)
                        advanceUntilIdle()
                        expected.add(HttpMethod.Get to latestUrl)
                        expected.add(HttpMethod.Get to middleUrl)
                        if (newPageCount == 3) expected.add(HttpMethod.Get to boundaryUrl)
                        val completeIds = listOf("late-sent", "sent", "new-4", "new-3", "new-2", "new-1", "old", "old-older")
                        assertEquals(completeIds, model.allData.map { it.id })
                        assertEquals(expected, requested)
                        assertNull(model.errorMessage)
                        assertFalse(model.isLoading)
                        assertEquals(oldIsEnd, model.isEnd)
                        assertEquals(listOf(peerUrl), peers)

                        val polling = async { model.refreshLatest(environment) }
                        advanceUntilIdle()
                        polling.await()
                        expected.add(HttpMethod.Get to latestUrl)
                        assertEquals(expected, requested)
                        assertEquals(completeIds, model.allData.map { it.id })
                        assertEquals(oldIsEnd, model.isEnd)
                        assertEquals(4, firstPages)
                        model.loadMore(environment)
                        advanceUntilIdle()
                        if (!oldIsEnd) expected.add(HttpMethod.Get to cursor)
                        assertEquals(expected, requested, "已结束的旧历史不得重开；未结束时必须继续原 opaque cursor")
                        assertEquals(if (oldIsEnd) completeIds else completeIds + "history", model.allData.map { it.id })
                        assertTrue(model.isEnd)
                        assertEquals(1, failures.size)
                        assertEquals(listOf(peerUrl), peers)
                    } finally {
                        release.complete(Unit)
                        model.viewModelScope.coroutineContext[kotlinx.coroutines.Job]!!.cancelAndJoin()
                        client.close()
                    }
                }
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun lateReadAndSendCallbacksCannotReenterAClosedNotificationOwner() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var active = true
        var posts = 0
        val client = HttpClient(
            MockEngine { request ->
                posts++
                if (posts == 2) started.complete(Unit)
                release.await()
                if (request.url.encodedPath == "/messages") {
                    respond("""{"id":"late-send","created_time":1}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                } else {
                    respond("", HttpStatusCode.NoContent)
                }
            },
        )
        val environment = object : MobileHomeFeedEnvironment {
            override fun httpClient(): HttpClient {
                check(active)
                return client
            }

            override fun mobileHomeFeedHttpClient(): HttpClient {
                check(active)
                return client
            }

            override fun authenticatedCookies() = emptyMap<String, String>()

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = throw error
        }
        val notifications = NotificationViewModel()
        notifications.categoryUnreadCounts.putAll(MobileNotificationCategory.entries.associateWith { 1 })
        val messages = PrivateMessageViewModel("peer-id")
        val read = async { notifications.markAllAsRead(environment) }
        val send = async { messages.sendMessage("未确认草稿", environment) }
        try {
            started.await()
            active = false
            release.complete(Unit)
            assertFalse(read.await())
            assertFalse(send.await())
            assertEquals(2, posts)
            assertTrue(messages.allData.isEmpty())
            assertNull(messages.errorMessage)
            assertEquals(1, notifications.categoryUnreadCounts.getValue(MobileNotificationCategory.Comment))
        } finally {
            release.complete(Unit)
            read.cancelAndJoin()
            send.cancelAndJoin()
            client.close()
        }
    }

    private fun notificationPagingEnvironment(client: HttpClient, failures: MutableList<Exception>) = object : NotificationEnvironment {
        override val notificationSettingsStore = object : NotificationSettingsStore {
            override fun getSystemNotificationEnabled(type: NotificationType) = false

            override fun setSystemNotificationEnabled(type: NotificationType, enabled: Boolean) = Unit

            override fun getDisplayInAppEnabled(type: NotificationType) = type.defaultValue

            override fun setDisplayInAppEnabled(type: NotificationType, enabled: Boolean) = Unit

            override fun getAutoMarkAsReadEnabled() = false

            override fun setAutoMarkAsReadEnabled(enabled: Boolean) = Unit

            override fun getUnreadBadgeEnabled() = true

            override fun setUnreadBadgeEnabled(enabled: Boolean) = Unit
        }

        override fun httpClient() = client

        override fun mobileHomeFeedHttpClient() = client

        override fun authenticatedCookies() = emptyMap<String, String>()

        override suspend fun handleFetchFailure(tag: String?, error: Exception) {
            failures += error
        }
    }

    private suspend fun awaitIdle(viewModel: NotificationViewModel) {
        withTimeout(5_000) {
            while (viewModel.isLoading) delay(10)
        }
    }

    private fun notificationPage(ids: List<String>, next: String?): String {
        val data = ids.joinToString(",") { id ->
            """{"id":"$id","unique_id":"$id","type":"message","card_type":"","detail_title":"","is_read":true,"unread_count":0,"created":0,"created_str":""}"""
        }
        val paging = if (next == null) {
            """{"is_end":true,"next":""}"""
        } else {
            """{"is_end":false,"next":"$next"}"""
        }
        return """{"head":[],"column_head":[],"data":[$data],"paging":$paging,"unread":{"message":{"count":0,"show_count":false}}}"""
    }
}
