/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 */

package com.github.zly2006.zhihu.viewmodel.feed

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.zly2006.zhihu.data.AdvertisementFeed
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.initializeAndroidApplicationContext
import com.github.zly2006.zhihu.data.installZhihuCommonClientConfig
import com.github.zly2006.zhihu.data.target
import com.github.zly2006.zhihu.platform.androidSettingsStore
import com.github.zly2006.zhihu.ui.PREFERENCE_NAME
import com.github.zly2006.zhihu.viewmodel.ANSWER_VOTEUP_THRESHOLD_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.ARTICLE_FOLLOWERS_THRESHOLD_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.ARTICLE_VOTEUP_THRESHOLD_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.QUALITY_FILTER_MODE_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.QUESTION_ANSWER_THRESHOLD_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.QUESTION_FOLLOWERS_THRESHOLD_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.QualityFilterMode
import com.github.zly2006.zhihu.viewmodel.VIDEO_FOLLOWERS_THRESHOLD_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.VIDEO_VOTE_THRESHOLD_PREFERENCE_KEY
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * RULES/HIDE 是 Android 的真实分页策略；JVM 固定 OFF，不能用测试环境 getter 改写生产策略。
 * 合成响应经公开的 pullToRefresh 入口；设置只改本用例的质量键并在 finally 恢复，不加载账户、数据库或发送真实请求。
 */
@RunWith(AndroidJUnit4::class)
class HomeFollowStructuralAdvertisementRegressionTest {
    @Test
    fun followDropsStructuralAdvertisementsButKeepsQualityRulePlaceholders() = runBlocking {
        withEnvironment(QualityFilterMode.RULES) { environment ->
            val model = FollowViewModel().also(environment.models::add)
            environment.page = JsonArray(syntheticMoments() + syntheticAdvertisement())
            model.pullToRefresh(environment)

            assertEquals(syntheticMoments().size + 1, model.allData.size)
            assertTrue(model.allData.last() is AdvertisementFeed)
            assertEquals(syntheticMoments().size, model.displayItems.size)
            assertTrue(model.displayItems.toTypedArray().contentEquals(model.latestLoadedDisplayItems.value.toTypedArray()))
            val answer = model.displayItems.first()
            assertTrue(answer.feed?.target is Feed.AnswerTarget)
            assertEquals("合成低赞回答", answer.title)
            assertTrue(answer.isQualityFiltered)
            assertTrue(answer.isFiltered)
            assertEquals("规则：回答；赞数 < 10，未关注作者", answer.content)
            assertFalse(model.displayItems.last().isQualityFiltered)
            assertEquals("https://www.zhihu.com/api/v3/moments?limit=10&desktop=true", environment.requestedUrlWithoutInclude())
        }
    }

    @Test
    fun followHideRemovesQualityFilteredItemsWithoutChangingRawPage() = runBlocking {
        withEnvironment(QualityFilterMode.HIDE) { environment ->
            val model = FollowViewModel().also(environment.models::add)
            environment.page = JsonArray(syntheticMoments().take(1) + syntheticAdvertisement())
            model.pullToRefresh(environment)

            assertEquals(2, model.allData.size)
            assertEquals(environment.page.toList(), model.debugData.toList())
            assertTrue(model.displayItems.isEmpty())
            assertTrue(model.latestLoadedDisplayItems.value.isEmpty())
        }
    }

    @Test
    fun titlelessPinKeepsBodySummaryAndDoesNotBecomeAnAdvertisement() = runBlocking {
        withEnvironment(QualityFilterMode.HIDE) { environment ->
            val model = FollowViewModel().also(environment.models::add)
            // 想法 target 使用合成响应；仅为进入分页调用链包一层明确的 feed 外层结构。
            environment.page = JsonArray(
                listOf(
                    buildJsonObject {
                        put("type", "feed")
                        put("target", fixture("pin-target.json"))
                    },
                    syntheticAdvertisement(),
                ),
            )
            model.pullToRefresh(environment)

            val item = model.displayItems.single()
            assertTrue(item.feed?.target is Feed.PinTarget)
            assertEquals("", item.title)
            assertEquals("合成想法正文 保留完整摘要", item.summary)
            assertFalse(item.isFiltered)
        }
    }

    private fun syntheticMoments(): JsonArray = fixture("moments-feed-items.json").jsonArray

    private fun fixture(name: String): JsonElement = ZhihuJson.json.parseToJsonElement(
        InstrumentationRegistry
            .getInstrumentation()
            .context.assets
            .open("home-follow/$name")
            .bufferedReader()
            .use { it.readText() },
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

    private suspend fun withEnvironment(
        mode: QualityFilterMode,
        block: suspend (TestEnvironment) -> Unit,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        initializeAndroidApplicationContext(context)
        val preferences = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
        val settings = androidSettingsStore(context)
        val values: Map<String, Any> = mapOf(
            QUALITY_FILTER_MODE_PREFERENCE_KEY to mode.name,
            ANSWER_VOTEUP_THRESHOLD_PREFERENCE_KEY to 10,
            ARTICLE_VOTEUP_THRESHOLD_PREFERENCE_KEY to 20,
            ARTICLE_FOLLOWERS_THRESHOLD_PREFERENCE_KEY to 50,
            VIDEO_VOTE_THRESHOLD_PREFERENCE_KEY to 20,
            VIDEO_FOLLOWERS_THRESHOLD_PREFERENCE_KEY to 50,
            QUESTION_ANSWER_THRESHOLD_PREFERENCE_KEY to 0,
            QUESTION_FOLLOWERS_THRESHOLD_PREFERENCE_KEY to 50,
            "reverseBlock" to false,
        )
        // 只读取受测键，不枚举账户偏好或其他用户数据；值和缺省状态均须还原。
        val previousValues = values.mapValues { (key, value) ->
            if (!preferences.contains(key)) {
                null
            } else {
                when (value) {
                    is Boolean -> settings.getBoolean(key, value)
                    is Int -> settings.getInt(key, value)
                    is String -> settings.getString(key, value)
                    else -> error("不支持的合成设置类型")
                }
            }
        }
        val environment = TestEnvironment()
        val engine = MockEngine { request ->
            environment.requests += request
            respond(
                buildJsonObject { put("data", environment.page) }.toString(),
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            installZhihuCommonClientConfig(mutableMapOf(), "test")
        }
        environment.client = client
        try {
            values.forEach { (key, value) ->
                when (value) {
                    is Boolean -> settings.putBoolean(key, value)
                    is Int -> settings.putInt(key, value)
                    is String -> settings.putString(key, value)
                }
            }
            block(environment)
            assertTrue("解码和分页失败必须可见", environment.failures.isEmpty())
            assertEquals("每个 Follow 用例只允许原分页请求，不追加详情", 1, environment.requests.size)
            environment.requests.forEach { request ->
                assertEquals("101_3_3.0", request.headers["x-zse-93"])
                assertTrue(request.headers["x-zse-96"]?.startsWith("2.0_") == true)
                assertEquals("/api/v3/moments", request.url.encodedPath)
            }
        } finally {
            try {
                environment.models.forEach { it.viewModelScope.coroutineContext[Job]!!.cancelAndJoin() }
            } finally {
                try {
                    client.close()
                    engine.close()
                } finally {
                    val restore = preferences.edit()
                    previousValues.forEach { (key, value) ->
                        when (value) {
                            null -> restore.remove(key)
                            is Boolean -> restore.putBoolean(key, value)
                            is Int -> restore.putInt(key, value)
                            is String -> restore.putString(key, value)
                        }
                    }
                    check(restore.commit())
                }
            }
        }
    }

    private class TestEnvironment : PaginationEnvironment {
        lateinit var client: HttpClient
        var page = JsonArray(emptyList())
        val models = mutableListOf<BaseFeedViewModel>()
        val requests = mutableListOf<HttpRequestData>()
        val failures = mutableListOf<Exception>()

        override fun httpClient() = client

        override fun authenticatedCookies() = mapOf("d_c0" to "test-device")

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
