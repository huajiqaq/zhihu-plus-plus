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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationViewModelTest {
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

    private fun notificationPagingEnvironment(client: HttpClient, failures: MutableList<Exception>, autoRead: () -> Boolean = { false }) = object : NotificationEnvironment {
        override val notificationSettingsStore = object : NotificationSettingsStore {
            override fun getSystemNotificationEnabled(type: NotificationType) = false

            override fun setSystemNotificationEnabled(type: NotificationType, enabled: Boolean) = Unit

            override fun getDisplayInAppEnabled(type: NotificationType) = type.defaultValue

            override fun setDisplayInAppEnabled(type: NotificationType, enabled: Boolean) = Unit

            override fun getAutoMarkAsReadEnabled() = autoRead()

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
