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
import com.github.zly2006.zhihu.data.LocalHistoryStorage
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.platform.appPrivateDirectory
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HistoryMutationTest {
    private lateinit var previousHome: String
    private lateinit var home: java.nio.file.Path

    @BeforeTest
    fun isolateHistoryStorage() {
        previousHome = System.getProperty("user.home")
        home = Files.createTempDirectory("history-owner-contract-")
        System.setProperty("user.home", home.toString())
    }

    @AfterTest
    fun restoreHistoryStorage() {
        System.setProperty("user.home", previousHome)
        home.toFile().deleteRecursively()
    }

    @Test
    fun batchDeleteUsesDistinctPairsAndRetainsAllRowsOnFailure() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        var status = HttpStatusCode.ServiceUnavailable
        var responseBody = "{}"
        val client = HttpClient(
            MockEngine { request ->
                requests += request
                respond(responseBody, status)
            },
        )
        Dispatchers.setMain(Dispatchers.Unconfined)
        val model = OnlineHistoryViewModel()
        val page = Json
            .parseToJsonElement(
                """
                {"data":[
                  {"card_type":"single_card","data":{"header":{"icon":"","title":"回答","badges":["https://example.invalid/badge.png"]},"action":{"type":"router","url":"zhihu://answers/1"},"extra":{"content_token":"t1","content_type":"answer","read_time":1,"question_token":"1"}}},
                  {"card_type":"single_card","data":{"header":{"icon":"","title":"文章"},"action":{"type":"router","url":"zhihu://articles/2"},"extra":{"content_token":"t2","content_type":"article","read_time":2,"question_token":"1"}}}
                ],"paging":{"is_end":true,"is_start":true,"next":""}}
                """.trimIndent(),
            ).jsonObject
        val environment = object : PaginationEnvironment {
            override fun httpClient() = client

            override fun authenticatedCookies() = emptyMap<String, String>()

            override suspend fun fetchJson(url: String, include: String): JsonObject = page

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
        }
        try {
            model.loadFor(environment, "synthetic-history")
            assertEquals(2, model.displayItems.size)
            val localHistory = LocalHistoryStorage()
            localHistory.add(Question(9, "在线删除不影响本机"))
            val historyFile = java.nio.file.Path
                .of(appPrivateDirectory().toString(), "history.json")
            val originalLocalBytes = Files.readAllBytes(historyFile)
            val originalRows = model.displayItems.toList()
            val keys = model.displayItems.mapTo(linkedSetOf()) { it.stableKey }
            assertFails { model.deleteItems(environment, keys) }
            assertEquals(originalRows, model.displayItems.toList())
            assertContentEquals(originalLocalBytes, Files.readAllBytes(historyFile))
            assertEquals(1, requests.size)
            assertEquals(HttpMethod.Post, requests.single().method)
            assertEquals("/read_history/batch_del", requests.single().url.encodedPath)
            val body = Json.parseToJsonElement((requests.single().body as TextContent).text).jsonObject
            assertEquals(Json.parseToJsonElement("""[{"content_token":"t1","content_type":"answer"},{"content_token":"t2","content_type":"article"}]"""), body.getValue("pairs"))
            assertEquals("false", body.getValue("clear").toString())
            assertEquals(
                "https://example.invalid/badge.png",
                model.displayItems
                    .first()
                    .titleBadges
                    .single(),
            )
            status = HttpStatusCode.OK
            responseBody = """{"error":{"code":40353,"message":"need_login"}}"""
            assertFails { model.deleteItems(environment, keys) }
            assertEquals(originalRows, model.displayItems.toList())
            assertContentEquals(originalLocalBytes, Files.readAllBytes(historyFile))
            responseBody = "{}"
            assertTrue(model.deleteItems(environment, keys))
            assertTrue(model.displayItems.isEmpty())
            assertEquals(3, requests.size)
            assertContentEquals(originalLocalBytes, Files.readAllBytes(historyFile))
        } finally {
            client.close()
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun localModelUsesCanonicalStorageAndKeepsRowsWhenFileReadOrDeleteFails() = runBlocking {
        val client = HttpClient(MockEngine { error("本地历史不能请求网络") })
        val environment = object : PaginationEnvironment {
            override fun httpClient() = client

            override fun authenticatedCookies() = emptyMap<String, String>()

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
        }
        val storage = LocalHistoryStorage()
        val model = HistoryViewModel()
        try {
            storage.add(Question(1, "应保留的本地问题"))
            model.refresh(environment)
            val item = model.displayItems.single()
            assertEquals("应保留的本地问题", item.title)
            assertEquals("问题", item.historyType)
            val file = java.nio.file.Path
                .of(appPrivateDirectory().toString(), "history.json")
            Files.writeString(file, "invalid original history")
            assertFails { model.removeItems(setOf(item.stableKey)) }
            assertEquals(item, model.displayItems.single())
            model.refresh(environment)
            assertEquals(item, model.displayItems.single())
            assertFalse(model.isLoading)
            assertTrue(model.errorMessage != null)
            assertEquals("invalid original history", Files.readString(file))
            Files.delete(file)
            storage.add(Question(1, "应保留的本地问题"))
            storage.add(Question(2, "下拉读回的新问题"))
            model.pullToRefresh(environment)
            assertEquals(listOf("下拉读回的新问题", "应保留的本地问题"), model.displayItems.map { it.title })
            assertFalse(model.isLoading)
            assertFalse(model.isPullToRefresh)
            model.removeItems(setOf(item.stableKey))
            model.refresh(environment)
            assertEquals(Question(2), storage.history.single())
            assertEquals("下拉读回的新问题", model.displayItems.single().title)
        } finally {
            client.close()
            model.viewModelScope.cancel()
        }
    }

    @Test
    fun oldAccountTotalAndSwitchCannotChangeReenteredAccountOrFinishItsSwitch() = runBlocking {
        val history = LocalHistoryStorage()
        val oldTotalGate = CompletableDeferred<Unit>()
        val oldTotalStarted = CompletableDeferred<Unit>()
        val oldSwitchGate = CompletableDeferred<Unit>()
        val oldSwitchStarted = CompletableDeferred<Unit>()
        val newSwitchGate = CompletableDeferred<Unit>()
        val newSwitchStarted = CompletableDeferred<Unit>()
        val client = HttpClient(
            MockEngine { request ->
                if (request.url.encodedPath == "/read_history/total") {
                    oldTotalStarted.complete(Unit)
                    oldTotalGate.await()
                    respond("""{"disabled":true}""")
                } else {
                    oldSwitchStarted.complete(Unit)
                    oldSwitchGate.await()
                    respond("{}")
                }
            },
        )
        val replacement = HttpClient(
            MockEngine {
                newSwitchStarted.complete(Unit)
                newSwitchGate.await()
                respond("{}")
            },
        )
        var currentClient = client
        val environment = object : PaginationEnvironment {
            override fun httpClient() = currentClient

            override fun authenticatedCookies() = emptyMap<String, String>()

            override suspend fun fetchJson(url: String, include: String): JsonObject =
                Json.parseToJsonElement("""{"data":[],"paging":{"is_end":true,"next":""}}""").jsonObject

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
        }
        Dispatchers.setMain(Dispatchers.Unconfined)
        val model = OnlineHistoryViewModel()
        try {
            model.loadFor(environment, "account-a")
            history.add(Question(1, "保持到当前关闭成功"))
            val oldTotal = async(start = CoroutineStart.UNDISPATCHED) { model.loadRecordingSetting(environment, "account-a") }
            oldTotalStarted.await()
            val oldSwitch = async(start = CoroutineStart.UNDISPATCHED) { model.setRecordingEnabled(environment, false) }
            oldSwitchStarted.await()
            currentClient = replacement
            model.loadFor(environment, "account-b")
            model.loadFor(environment, "account-a")
            assertFalse(model.isChangingRecording)
            oldTotalGate.complete(Unit)
            oldTotal.await()
            assertTrue(model.recordingEnabled)
            assertTrue(history.recordingEnabled)
            val newSwitch = async(start = CoroutineStart.UNDISPATCHED) { model.setRecordingEnabled(environment, false) }
            newSwitchStarted.await()
            oldSwitchGate.complete(Unit)
            assertFalse(oldSwitch.await())
            assertTrue(model.isChangingRecording)
            assertTrue(model.recordingEnabled)
            assertEquals(1, history.history.size)
            newSwitchGate.complete(Unit)
            assertTrue(newSwitch.await())
            assertFalse(model.isChangingRecording)
            assertFalse(model.recordingEnabled)
            assertTrue(history.history.isEmpty())
        } finally {
            oldTotalGate.complete(Unit)
            oldSwitchGate.complete(Unit)
            newSwitchGate.complete(Unit)
            client.close()
            replacement.close()
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun clientReplacementRejectsDeleteAndClearBeforeLoadForAndClearFailureKeepsBytes() = runBlocking {
        val file = java.nio.file.Path
            .of(appPrivateDirectory().toString(), "history.json")
        val history = LocalHistoryStorage()
        val pending = CompletableDeferred<Unit>()
        val deleteStarted = CompletableDeferred<Unit>()
        val clearStarted = CompletableDeferred<Unit>()
        val requests = ConcurrentLinkedQueue<HttpRequestData>()
        var delayed = true
        var status = HttpStatusCode.OK
        var responseBody = "{}"
        val client = HttpClient(
            MockEngine { request ->
                requests += request
                val responseStatus = status
                if (delayed) {
                    val clear = Json
                        .parseToJsonElement((request.body as TextContent).text)
                        .jsonObject
                        .getValue("clear")
                        .toString() == "true"
                    if (clear) clearStarted.complete(Unit) else deleteStarted.complete(Unit)
                    pending.await()
                }
                respond(responseBody, responseStatus)
            },
        )
        val replacement = HttpClient(MockEngine { respond("{}") })
        var currentClient = client
        val page = Json
            .parseToJsonElement(
                """
                {"data":[{"card_type":"single_card","data":{"header":{"icon":"","title":"保持记录"},"action":{"type":"router","url":"zhihu://answers/1"},"extra":{"content_token":"1","content_type":"answer","read_time":1,"question_token":"1"}}}],"paging":{"is_end":true,"next":""}}
                """.trimIndent(),
            ).jsonObject
        val environment = object : PaginationEnvironment {
            override fun httpClient() = currentClient

            override fun authenticatedCookies() = emptyMap<String, String>()

            override suspend fun fetchJson(url: String, include: String): JsonObject = page

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
        }
        Dispatchers.setMain(Dispatchers.Unconfined)
        val model = OnlineHistoryViewModel()
        try {
            model.loadFor(environment, "account-a")
            history.add(Question(1, "本地原记录"))
            val original = Files.readAllBytes(file)
            val keys = model.displayItems.mapTo(linkedSetOf()) { it.stableKey }
            val oldDelete = async(start = CoroutineStart.UNDISPATCHED) { model.deleteItems(environment, keys) }
            deleteStarted.await()
            val oldClear = async(start = CoroutineStart.UNDISPATCHED) { model.clearAllHistory(environment) }
            assertFalse(clearStarted.isCompleted, "同一历史会话的删除和清空不能并发提交")
            currentClient = replacement
            pending.complete(Unit)
            assertFalse(oldDelete.await())
            assertFalse(oldClear.await())
            assertEquals(1, model.displayItems.size)
            assertContentEquals(original, Files.readAllBytes(file))
            val count = requests.size
            assertFalse(model.deleteItems(environment, keys))
            assertFalse(model.clearAllHistory(environment))
            assertFalse(model.setRecordingEnabled(environment, false))
            assertEquals(count, requests.size)

            currentClient = client
            model.loadFor(environment, "account-b")
            model.loadFor(environment, "account-a")
            delayed = false
            status = HttpStatusCode.ServiceUnavailable
            assertFails { model.clearAllHistory(environment) }
            assertEquals(1, model.displayItems.size)
            assertContentEquals(original, Files.readAllBytes(file))
            status = HttpStatusCode.OK
            responseBody = """{"error":{"message":"unhuman"}}"""
            assertFails { model.clearAllHistory(environment) }
            assertEquals(1, model.displayItems.size)
            assertContentEquals(original, Files.readAllBytes(file))
            responseBody = "{}"
            assertTrue(model.clearAllHistory(environment))
            assertTrue(history.history.isEmpty())
            assertTrue(history.recordingEnabled)
            assertTrue(model.displayItems.isEmpty())
            assertTrue(model.isEnd)
            assertEquals(Json.parseToJsonElement("""{"pairs":[],"clear":true}"""), Json.parseToJsonElement((requests.last().body as TextContent).text))
        } finally {
            pending.complete(Unit)
            client.close()
            replacement.close()
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }
}
