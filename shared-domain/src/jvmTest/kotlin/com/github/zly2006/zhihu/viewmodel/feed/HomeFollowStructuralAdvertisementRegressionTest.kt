/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 */

package com.github.zly2006.zhihu.viewmodel.feed

import androidx.lifecycle.viewModelScope
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.fleeksoft.ksoup.Ksoup
import com.github.zly2006.zhihu.data.AdvertisementFeed
import com.github.zly2006.zhihu.data.CommonFeed
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.flattenFeeds
import com.github.zly2006.zhihu.data.installZhihuCommonClientConfig
import com.github.zly2006.zhihu.data.navDestination
import com.github.zly2006.zhihu.data.target
import com.github.zly2006.zhihu.data.toDisplayItem
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.platform.platformSettingsStore
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.QUALITY_FILTER_MODE_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.QualityFilterMode
import com.github.zly2006.zhihu.viewmodel.fetchContentDetail
import com.github.zly2006.zhihu.viewmodel.filter.BlockedContentRecord
import com.github.zly2006.zhihu.viewmodel.filter.BlockedFeedRecord
import com.github.zly2006.zhihu.viewmodel.filter.BlockedKeyword
import com.github.zly2006.zhihu.viewmodel.filter.BlockedQuestionAuthor
import com.github.zly2006.zhihu.viewmodel.filter.BlockedUser
import com.github.zly2006.zhihu.viewmodel.filter.ContentDetailProvider
import com.github.zly2006.zhihu.viewmodel.filter.ContentOpenEvent
import com.github.zly2006.zhihu.viewmodel.filter.ContentViewRecord
import com.github.zly2006.zhihu.viewmodel.filter.FeedDisplayFilterPipeline
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import com.github.zly2006.zhihu.viewmodel.filter.toFeedFilterSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 命中生产分页解码、processResponse、sourceFeed 映射和首页过滤环境。
 * 原始真实响应只脱敏身份；syntheticAdvertisement 是明确标注的合成协议边界。
 * Android 的 RULES/HIDE 分页回归留在对应 instrument 用例，不改变 JVM 的 OFF 策略。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeFollowStructuralAdvertisementRegressionTest {
    @Test
    fun followRecommendDropsStructuralAdvertisementsWithQualityFilteringOff() = runTest {
        withEnvironment { environment ->
            val model = FollowRecommendViewModel().also(environment.models::add)
            environment.page = JsonArray(listOf(syntheticAdvertisement()))
            model.pullToRefresh(environment)

            assertEquals(0, model.displayItems.size)
            assertFalse(model.displayItems.any { it.isFiltered })
            assertEquals("https://api.zhihu.com/moments_v3?feed_type=recommend", environment.requestedUrlWithoutInclude())
        }
    }

    @Test
    fun homePipelineDropsStructuralAdvertisementWithoutResolvingDetails() = runTest {
        withEnvironment { environment ->
            val advert = ZhihuJson.decodeJson<Feed>(syntheticAdvertisement())
            val mapped = advert.toDisplayItem()
            assertIs<AdvertisementFeed>(mapped.feed)
            assertTrue(mapped.isFiltered)
            assertNull(mapped.navDestinationJson)

            assertEquals(emptyList(), environment.pipeline.filter(listOf(mapped)))
            assertEquals(emptyList(), environment.detailRequests)

            environment.page = JsonArray(listOf(syntheticAdvertisement()))
            val model = HomeFeedViewModel().also(environment.models::add)
            model.pullToRefresh(environment)
            assertIs<AdvertisementFeed>(model.allData.single())
            assertEquals(emptyList(), model.displayItems.toList())
            assertEquals(emptyList(), model.latestLoadedDisplayItems.value)
            assertEquals(emptyList(), environment.detailRequests)
        }
    }

    @Test
    fun anonymousHomeKeepsRecommendationWithoutFetchingLoginOnlyDetails() = runTest {
        withEnvironment { environment ->
            environment.loggedIn = false
            val answer = fixture("/real-api/answer-detail.json").jsonObject
            val question = answer.getValue("question").jsonObject
            environment.page = JsonArray(
                listOf(
                    buildJsonObject {
                        put("type", "feed")
                        put("target", JsonObject(answer + ("question" to JsonObject(question + ("title" to JsonPrimitive("合成游客推荐内容"))))))
                    },
                ),
            )
            val model = HomeFeedViewModel().also(environment.models::add)
            model.pullToRefresh(environment)
            assertEquals("合成游客推荐内容", model.displayItems.single().title)
            assertEquals(1, model.completedPageCount)
            assertEquals(emptyList(), environment.detailRequests)
            val requestUrl = environment.requests.single().url
            assertEquals("api.zhihu.com", requestUrl.host)
            assertEquals("/topstory/recommend", requestUrl.encodedPath)
        }
    }

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

    @Test
    fun homeReverseKeepsEachStructuralAdvertisementOnce() = runTest {
        withEnvironment(reverseBlock = true) { environment ->
            val adverts = listOf(syntheticAdvertisement("first"), syntheticAdvertisement("second"))
            environment.page = JsonArray(adverts)
            val model = HomeFeedViewModel().also(environment.models::add)
            model.pullToRefresh(environment)

            assertEquals(listOf("first", "second"), model.displayItems.map { it.title })
            assertTrue(model.displayItems.all { it.feed is AdvertisementFeed && !it.isFiltered })
            assertEquals(model.displayItems.toList(), model.latestLoadedDisplayItems.value)
            assertEquals(emptyList(), environment.detailRequests)
            assertEquals(emptyList(), environment.blockedReasons())
        }
    }

    @Test
    fun followReverseKeepsExplicitAdvertisementsWithoutAddingPaidDetailRequests() = runTest {
        withEnvironment(reverseBlock = true) { environment ->
            val model = FollowViewModel().also(environment.models::add)
            environment.page = JsonArray(realMoments() + syntheticAdvertisement())
            model.pullToRefresh(environment)

            assertEquals(realMoments().size + 1, model.displayItems.size)
            val advert = model.displayItems.last()
            assertIs<AdvertisementFeed>(advert.feed)
            assertEquals("advert", advert.title)
            assertFalse(advert.isFiltered)
            assertEquals(emptyList(), environment.detailRequests)
        }
    }

    @Test
    fun reversePreservesExistingPaidDetectionWhenPaidBlockingPreferenceIsDisabled() = runTest {
        withEnvironment(reverseBlock = true, blockPaidContent = false) { environment ->
            val paid = CommonFeed(
                target = Feed.AnswerTarget(
                    id = 1,
                    url = "https://api.zhihu.com/answers/1",
                    question = Feed.QuestionTarget(id = 2, url = "https://api.zhihu.com/questions/2", type = "question"),
                ),
            ).toDisplayItem(reverseBlock = true)
            val advert = ZhihuJson.decodeJson<Feed>(syntheticAdvertisement()).toDisplayItem(reverseBlock = true)
            environment.contentDetail = ZhihuJson
                .decodeJson<DataHolder.Answer>(fixture("/real-api/answer-detail.json"))
                .copy(paidInfo = buildJsonObject { put("type", "paid") })

            val result = environment.pipeline.filter(listOf(paid, advert))
            assertEquals(listOf(paid, advert), result)
            assertEquals(1, environment.detailRequests.size)
            assertEquals(emptyList(), environment.blockedReasons())
        }
    }

    @Test
    fun realDynamicEnvelopeRemovesOnlyStructuralAdAndPreservesTitlelessPinBodies() = runTest {
        withEnvironment { environment ->
            val response = fixture("/home-follow/follow-dynamic-page-0-sanitized.json").jsonObject
            environment.responseEnvelope = response
            val model = FollowViewModel().also(environment.models::add)
            model.pullToRefresh(environment)

            assertEquals(response.getValue("data").jsonArray.toList(), model.debugData)
            assertEquals(14, model.allData.size)
            val advert = assertIs<AdvertisementFeed>(model.allData.single { it is AdvertisementFeed })
            val flattened = model.allData.toList().flattenFeeds()
            assertEquals(15, flattened.size)
            assertEquals(14, model.displayItems.size)
            assertEquals(model.displayItems.toList(), model.latestLoadedDisplayItems.value)
            assertFalse(model.displayItems.any { it.feed is AdvertisementFeed })
            val pinItems = model.displayItems.filter { it.feed?.target is Feed.PinTarget }
            assertEquals(3, pinItems.size)
            val textPinItems = pinItems.filter { item ->
                (item.feed?.target as Feed.PinTarget).content.any { it is DataHolder.Pin.ContentText }
            }
            assertEquals(2, textPinItems.size)
            textPinItems.forEach { item ->
                val pin = assertIs<Feed.PinTarget>(item.feed?.target)
                val text = pin.content.filterIsInstance<DataHolder.Pin.ContentText>().first()
                assertEquals("", text.title)
                assertEquals("", item.title)
                assertEquals(Ksoup.parse(text.content).text(), item.summary)
                assertTrue(item.summary?.isNotBlank() == true)
                assertFalse(item.isFiltered)
            }
            // link_card-only 想法仍保留空标题与来源 target；关联内容标题由独立显示回归验证。
            val linkOnlyPin = pinItems.single { it !in textPinItems }
            assertEquals("", linkOnlyPin.title)
            assertFalse(linkOnlyPin.isFiltered)
            val questionItem = model.displayItems.single { it.feed?.target is Feed.QuestionTarget }
            assertEquals(assertIs<Feed.QuestionTarget>(questionItem.feed?.target).title, questionItem.title)
            assertTrue(questionItem.title.isNotBlank())
            assertEquals(emptyList(), environment.pipeline.filter(listOf(advert.toDisplayItem())))
            assertEquals(emptyList(), environment.detailRequests)
        }
    }

    @Test
    fun followPinsRetainContentAndUseServerNextWithoutRebuildingQuery() = runTest {
        withEnvironment { environment ->
            val model = FollowPinViewModel().also(environment.models::add)
            // 复用想法语料构造已证实标识的协议边界，不称作关注想法线上响应。
            val pin = fixture("/real-api/pin-target.json").jsonObject
            val aliasPin = JsonObject(pin + ("type" to JsonPrimitive("moments_pin")))
            val next = "https://api.zhihu.com/moments_v3?feed_type=pin&after_id=opaque%2Btoken&include=server_field&limit=7"
            val first = pinEnvelope(aliasPin, "moments_top_feed", next, false)
            environment.responseEnvelope = first
            model.pullToRefresh(environment)

            assertEquals(HttpMethod.Get, environment.requests.single().method)
            assertEquals("https://api.zhihu.com/moments_v3?feed_type=pin", environment.requestedUrlWithoutInclude())
            assertEquals(first.getValue("data").jsonArray.toList(), model.debugData)
            val item = model.displayItems.single()
            val target = assertIs<Feed.PinTarget>(item.feed?.target)
            val raw = assertIs<DataHolder.Pin>(item.raw)
            assertEquals(raw.content, target.content)
            assertTrue(target.content.any { it is DataHolder.Pin.ContentImage })
            assertEquals(Pin(target.id, target.author.name), item.feed?.target?.navDestination)
            assertFalse(item.isFiltered)
            assertFalse(model.isEnd)
            assertEquals(1, model.completedPageCount)

            environment.responseEnvelope = pinEnvelope(aliasPin, "moments_recommend_feed", "", true)
            model.loadMore(environment)
            runCurrent()
            assertEquals(
                next,
                environment.requests
                    .last()
                    .url
                    .toString(),
            )
            assertEquals(1, model.displayItems.size, "相同想法分页去重仍保留完整卡片")
            assertTrue(model.isEnd)
            assertEquals(2, model.completedPageCount)
            model.loadMore(environment)
            runCurrent()
            assertEquals(2, environment.requests.size)
        }
    }

    @Test
    fun followPinsUnknownRefreshKeepsCardsAndRetryCanReplaceThemWithSuccessfulEmptyPage() = runTest {
        withEnvironment { environment ->
            val model = FollowPinViewModel().also(environment.models::add)
            environment.responseEnvelope = pinEnvelope(fixture("/real-api/pin-target.json").jsonObject, "moments_feed", "", true)
            model.pullToRefresh(environment)
            val previous = model.displayItems.toList()
            val previousRaw = model.debugData.toList()
            environment.responseEnvelope = buildJsonObject {
                put("data", JsonArray(listOf(buildJsonObject { put("type", "unknown_follow_pin") })))
                put(
                    "paging",
                    buildJsonObject {
                        put("is_end", true)
                        put("next", "")
                    },
                )
            }
            model.pullToRefresh(environment)
            assertTrue(model.errorMessage != null)
            assertEquals(previous, model.displayItems.toList())
            assertEquals(previousRaw, model.debugData)
            assertEquals(1, model.completedPageCount)
            assertEquals(2, environment.failures.size)
            environment.failures.clear()

            environment.responseEnvelope = buildJsonObject {
                put("data", JsonArray(emptyList()))
                put(
                    "paging",
                    buildJsonObject {
                        put("is_end", true)
                        put("next", "")
                    },
                )
            }
            model.retry(environment)
            runCurrent()
            assertNull(model.errorMessage)
            assertTrue(model.displayItems.isEmpty())
            assertTrue(model.allData.isEmpty())
            assertTrue(model.isEnd)
            assertEquals(2, model.completedPageCount)
            assertEquals(emptyList(), environment.detailRequests)
        }
    }

    private fun pinEnvelope(pin: JsonObject, type: String, next: String, end: Boolean) = buildJsonObject {
        put(
            "data",
            JsonArray(
                listOf(
                    buildJsonObject {
                        put("id", "follow-pin-fixture")
                        put("type", type)
                        put("target_type", "pin")
                        put("target", pin)
                    },
                ),
            ),
        )
        put(
            "paging",
            buildJsonObject {
                put("is_end", end)
                put("next", next)
            },
        )
    }

    private fun realMoments(): List<JsonElement> = fixture("/real-api/moments-feed-items.json").jsonArray.toList()

    private fun fixture(path: String): JsonElement = ZhihuJson.json.parseToJsonElement(
        checkNotNull(javaClass.getResource(path)).readText(),
    )

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
                        (environment.responseEnvelope ?: buildJsonObject { put("data", environment.page) }).toString(),
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
            environment.pipeline = FeedDisplayFilterPipeline(
                settings = settings.toFeedFilterSettings(),
                contentDetailProvider = ContentDetailProvider(environment::fetchContentDetail),
                database = database,
            )
            environment.blockedReasons = {
                database
                    .blockedFeedRecordDao()
                    .observeAll()
                    .first()
                    .map { it.blockedReason }
            }
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
        lateinit var pipeline: FeedDisplayFilterPipeline
        lateinit var blockedReasons: suspend () -> List<String>
        var page = JsonArray(emptyList())
        var responseEnvelope: JsonObject? = null
        var contentDetail: DataHolder.Content? = null
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
                return when (val content = contentDetail) {
                    is DataHolder.Answer -> ZhihuJson.json.encodeToJsonElement(content).jsonObject
                    is DataHolder.Article -> ZhihuJson.json.encodeToJsonElement(content).jsonObject
                    is DataHolder.Pin -> ZhihuJson.json.encodeToJsonElement(content).jsonObject
                    else -> null
                }
            }
            return super<PaginationEnvironment>.fetchJson(url, include)
        }

        override suspend fun handleFetchFailure(tag: String?, error: Exception) {
            failures += error
        }

        override fun logDecodeFailure(tag: String?, item: JsonElement, error: Exception) {
            failures += error
        }

        fun requestedUrlWithoutInclude(): String = requests
            .single()
            .url
            .toString()
            .substringBefore("&include=")
    }
}
