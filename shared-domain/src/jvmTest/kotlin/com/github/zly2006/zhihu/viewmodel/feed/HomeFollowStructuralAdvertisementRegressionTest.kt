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
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.github.zly2006.zhihu.data.AdvertisementFeed
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.installZhihuCommonClientConfig
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.platform.platformSettingsStore
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.QUALITY_FILTER_MODE_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.QualityFilterMode
import com.github.zly2006.zhihu.viewmodel.filter.BlockedContentRecord
import com.github.zly2006.zhihu.viewmodel.filter.BlockedFeedRecord
import com.github.zly2006.zhihu.viewmodel.filter.BlockedKeyword
import com.github.zly2006.zhihu.viewmodel.filter.BlockedQuestionAuthor
import com.github.zly2006.zhihu.viewmodel.filter.BlockedUser
import com.github.zly2006.zhihu.viewmodel.filter.ContentOpenEvent
import com.github.zly2006.zhihu.viewmodel.filter.ContentViewRecord
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 生产分页与历史同步并发时，结构广告不能先发布到列表。 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeFollowStructuralAdvertisementRegressionTest {
    @Test
    fun homeNeverInsertsStructuralAdvertisementWhileBackgroundFilterIsRunning() = runTest {
        withEnvironment { environment ->
            environment.page = JsonArray(listOf(syntheticAdvertisement()))
            environment.historyGate = CompletableDeferred()
            val model = HomeFeedViewModel().also(environment.models::add)
            val history = checkNotNull(model.remoteHistory.start(this, environment))
            val pull = async { model.pullToRefresh(environment) }
            try {
                environment.historyStarted.await()
                while (model.allData.isEmpty()) {
                    runCurrent()
                    kotlinx.coroutines.yield()
                }
                // 广告不会请求详情；用真实前台同步等待观察尚未完成的过滤，而不是生产回调钩子。
                advanceTimeBy(299)
                runCurrent()
                assertFalse(pull.isCompleted)
                assertIs<AdvertisementFeed>(model.allData.single())
                assertEquals(emptyList(), model.displayItems.toList())
                assertEquals(emptyList(), model.latestLoadedDisplayItems.value)
                assertEquals(0, model.completedPageCount)
                assertEquals(1, environment.requests.size, "推荐 GET 必须在历史等待完成前发出")
                assertEquals(1, environment.historyRequests)
                assertEquals(emptyList(), environment.detailRequests)

                environment.historyGate!!.complete(Unit)
                history.join()
                pull.await()
                assertIs<AdvertisementFeed>(model.allData.single())
                assertEquals(emptyList(), model.displayItems.toList())
                assertEquals(emptyList(), model.latestLoadedDisplayItems.value)
                assertEquals(1, model.completedPageCount)
                assertEquals(1, environment.requests.size)
                assertEquals(emptyList(), environment.detailRequests)
            } finally {
                environment.historyGate!!.complete(Unit)
                pull.cancelAndJoin()
                history.cancelAndJoin()
            }
        }
    }

    private fun syntheticAdvertisement(title: String = "advert") = buildJsonObject {
        put("type", "feed_advert")
        put("action_text", "广告")
        put(
            "ad",
            buildJsonObject {
                put(
                    "creatives",
                    JsonArray(
                        listOf(
                            buildJsonObject {
                                put("landing_url", "https://advert.example/$title")
                                put("title", title)
                                put("description", "synthetic feed_advert protocol boundary")
                            },
                        ),
                    ),
                )
            },
        )
    }

    private suspend fun TestScope.withEnvironment(
        reverseBlock: Boolean = false,
        blockPaidContent: Boolean = true,
        block: suspend (TestEnvironment) -> Unit,
    ) {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        // 正式 JVM Test 组合根已隔离 user.home；复用生产 singleton，不切换根或关闭它。
        val settings = platformSettingsStore()
        val values = mapOf(
            QUALITY_FILTER_MODE_PREFERENCE_KEY to QualityFilterMode.OFF.name,
            "reverseBlock" to reverseBlock.toString(),
            "enableContentFilter" to "true",
            "filterFollowedUserContent" to "false",
            "enableKeywordBlocking" to "true",
            "enableNLPBlocking" to "true",
            "nlpSimilarityThreshold" to "0.8",
            "enableUserBlocking" to "true",
            "enableTopicBlocking" to "true",
            "topicBlockingThreshold" to "1",
            "blockZhihuAdPlatform" to "true",
            "blockZhihuSchool" to "true",
            "blockWeChatOfficialAccount" to "true",
            "blockPaidContent" to blockPaidContent.toString(),
            "lastRemoteHistorySync" to "0",
            "remoteHistoryReadTime" to "0",
        )
        val previousValues = values.keys.associateWith(settings::getStringOrNull)
        val database = getContentFilterDatabase()
        val tables = arrayOf(
            ContentViewRecord.TABLE_NAME,
            ContentOpenEvent.TABLE_NAME,
            BlockedKeyword.TABLE_NAME,
            BlockedUser.TABLE_NAME,
            BlockedQuestionAuthor.TABLE_NAME,
            BlockedContentRecord.TABLE_NAME,
            "blocked_topics",
            BlockedFeedRecord.TABLE_NAME,
        )
        val environment = TestEnvironment()
        val client = HttpClient(MockEngine) {
            engine {
                this.dispatcher = dispatcher
                addHandler { request ->
                    environment.requests += request
                    respond(
                        buildJsonObject { put("data", environment.page) }.toString(),
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
            installZhihuCommonClientConfig(mutableMapOf(), "test")
        }
        environment.client = client
        var saved = false
        try {
            database.useWriterConnection { connection ->
                connection.immediateTransaction {
                    tables.forEach { table ->
                        connection.usePrepared("CREATE TEMP TABLE home_follow_saved_$table AS SELECT * FROM $table") { it.step() }
                        connection.usePrepared("DELETE FROM $table") { it.step() }
                    }
                }
            }
            saved = true
            values.forEach { (key, value) -> settings.putString(key, value) }
            block(environment)
            assertEquals(emptyList(), environment.failures)
            environment.requests.forEach { request ->
                assertEquals("101_3_3.0", request.headers["x-zse-93"])
                assertTrue(request.headers["x-zse-96"]?.startsWith("2.0_") == true)
            }
        } finally {
            environment.models.forEach { it.viewModelScope.coroutineContext[Job]!!.cancelAndJoin() }
            client.close()
            try {
                if (saved) {
                    database.useWriterConnection { connection ->
                        connection.immediateTransaction {
                            tables.forEach { table ->
                                connection.usePrepared("DELETE FROM $table") { it.step() }
                                connection.usePrepared("INSERT INTO $table SELECT * FROM home_follow_saved_$table") { it.step() }
                                connection.usePrepared("DROP TABLE home_follow_saved_$table") { it.step() }
                            }
                        }
                    }
                }
            } finally {
                try {
                    previousValues.forEach { (key, value) ->
                        if (value == null) settings.remove(key) else settings.putString(key, value)
                    }
                } finally {
                    Dispatchers.resetMain()
                }
            }
        }
    }

    private class TestEnvironment : PaginationEnvironment {
        lateinit var client: HttpClient
        var page = JsonArray(emptyList())
        var historyGate: CompletableDeferred<Unit>? = null
        val historyStarted = CompletableDeferred<Unit>()
        var historyRequests = 0
        val models = mutableListOf<BaseFeedViewModel>()
        val detailRequests = mutableListOf<NavDestination>()
        val requests = mutableListOf<HttpRequestData>()
        val failures = mutableListOf<Exception>()

        var loggedIn = true
        override val isLoggedIn: Boolean
            get() = loggedIn

        override fun httpClient() = client

        override fun authenticatedCookies() = mapOf("d_c0" to "test-device")

        override suspend fun fetchJson(url: String, include: String): JsonObject? {
            val path = Url(url).encodedPath
            if (path == "/unify-consumption/read_history") {
                historyRequests++
                historyStarted.complete(Unit)
                historyGate?.await()
                return ZhihuJson.json.parseToJsonElement("""{"data":[],"paging":{"is_end":true,"next":""}}""").jsonObject
            }
            val destination = when {
                path.startsWith("/api/v4/answers/") -> Article(id = path.substringAfterLast('/').toLong(), type = ArticleType.Answer)
                path.startsWith("/api/v4/articles/") -> Article(id = path.substringAfterLast('/').toLong(), type = ArticleType.Article)
                path.startsWith("/api/v4/pins/") -> Pin(path.substringAfterLast('/').toLong())
                else -> null
            }
            if (destination != null) {
                detailRequests += destination
                return null
            }
            return super<PaginationEnvironment>.fetchJson(url, include)
        }

        override suspend fun handleFetchFailure(tag: String?, error: Exception) {
            failures += error
        }

        override fun logDecodeFailure(tag: String?, item: JsonElement, error: Exception) {
            failures += error
        }
    }
}
