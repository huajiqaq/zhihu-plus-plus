/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 */

package com.github.zly2006.zhihu.ui

import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.WriteAnswer
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import io.ktor.client.HttpClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 夹具保留真实问题、邀请目标与分页字段，移除账号和邀请者身份。 */
@OptIn(ExperimentalCoroutinesApi::class)
class WriteAnswerChooserTest {
    @Test
    fun realNestedQuestionsUseProductionDecoderAndPreserveDetailAndNext() {
        val recommended = decodeWriteAnswerChoices(fixture("recommend"), WriteAnswerChoiceTab.Recommended)
        assertEquals(1899516798845575580, recommended.items.single().questionId)
        assertEquals("请问已经高三了，早上8点上课，下午3点放学，算累吗？", recommended.items.single().title)
        assertEquals("问题 · 35429 关注 · 35022 回答", recommended.items.single().summary)
        assertEquals(
            "https://api.zhihu.com/creators/question_route/notification_related/recommended?limit=20&offset=20",
            recommended.paging.next,
        )
        val hot = decodeWriteAnswerChoices(fixture("hot"), WriteAnswerChoiceTab.Hot)
        assertEquals(1942359082284590984, hot.items.single().questionId)
        assertEquals("<p>求解答</p>", hot.items.single().questionDetail)
        assertEquals(
            WriteAnswer(1942359082284590984, "下雨天，做什么事最舒服？", "<p>求解答</p>"),
            hot.items.single().answerDestination,
        )
        val invite = decodeWriteAnswerChoices(fixture("invite"), WriteAnswerChoiceTab.Invite)
        assertEquals(1967980736595337716, invite.items.single().questionId)
        assertEquals("4 回答 · 5 关注", invite.items.single().summary)
        assertFalse(invite.items.single().hasAnswer)
        assertEquals(WriteAnswer(1967980736595337716, "手机钢化膜蓝光和高清哪个好一点？"), invite.items.single().answerDestination)
        assertTrue(invite.paging.next.contains("message_offset=9223372036854775807&offset=1792478346"))
    }

    @Test
    fun boundaryMutationsRejectInvalidIdsAndWrongNestedShape() {
        // 以下只变异真实样本用于拒绝边界，不是声称采集到了这些服务端字段值。
        listOf("0", "-1", "not-a-question", "9223372036854775808").forEach { id ->
            listOf("recommend" to WriteAnswerChoiceTab.Recommended, "invite" to WriteAnswerChoiceTab.Invite).forEach { (name, tab) ->
                val raw = fixture(name)
                val key = if (tab == WriteAnswerChoiceTab.Invite) "target" else "question"
                val item = raw
                    .getValue("data")
                    .jsonArray
                    .single()
                    .jsonObject
                val target = item.getValue(key).jsonObject
                val mutated = JsonObject(raw + ("data" to JsonArray(listOf(JsonObject(item + (key to JsonObject(target + ("id" to JsonPrimitive(id)))))))))
                assertFailsWith<IllegalStateException>("$name/$id") { decodeWriteAnswerChoices(mutated, tab) }
            }
        }
        assertFailsWith<IllegalStateException> {
            decodeWriteAnswerChoices(fixture("invite"), WriteAnswerChoiceTab.Recommended)
        }
        assertFailsWith<IllegalArgumentException> { WriteAnswerChoice(0, "不能写入空问题", "") }
    }

    @Test
    fun existingAnswerRequiresValidAnswerRouteAndNeverOpensOtherContent() {
        val raw = fixture("invite")
        val item = raw
            .getValue("data")
            .jsonArray
            .single()
            .jsonObject
        val target = item.getValue("target").jsonObject
        listOf(
            "https://www.zhihu.com/question/1967980736595337716/answer/42" to Article(type = ArticleType.Answer, id = 42),
            "https://www.zhihu.com/answer/0" to null,
            "https://www.zhihu.com/answer/-1" to null,
            "https://www.zhihu.com/question/1967980736595337716" to null,
            "https://example.invalid/answer/42" to null,
        ).forEach { (url, expected) ->
            val mutatedTarget = JsonObject(target + mapOf("my_answer_url" to JsonPrimitive(url), "has_answer" to JsonPrimitive(true)))
            val mutated = JsonObject(raw + ("data" to JsonArray(listOf(JsonObject(item + ("target" to mutatedTarget))))))
            assertEquals(expected, decodeWriteAnswerChoices(mutated, WriteAnswerChoiceTab.Invite).items.single().answerDestination, url)
        }
    }

    @Test
    fun tabsKeepSeparateListsAndUseExactServerPagingWithRetryAndDeduplication() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val recommendedRaw = fixture("recommend")
            // 分页状态变异：数据项仍来自两份真实样本，首屏与下一页重复项只保留一份。
            val secondPage = JsonObject(
                recommendedRaw + mapOf(
                    "data" to JsonArray(recommendedRaw.getValue("data").jsonArray + fixture("hot").getValue("data").jsonArray),
                    "paging" to JsonObject(recommendedRaw.getValue("paging").jsonObject + ("is_end" to JsonPrimitive(true))),
                ),
            )
            val recommended = WriteAnswerChoicesViewModel(WriteAnswerChoiceTab.Recommended)
            val invite = WriteAnswerChoicesViewModel(WriteAnswerChoiceTab.Invite)
            val requests = mutableListOf<Pair<String, String>>()
            var failNext = false
            val environment = environment { url, include ->
                requests += url to include
                if (failNext) {
                    failNext = false
                    error("recoverable-page-failure")
                }
                when {
                    url.contains("/entry/invite") -> fixture("invite")
                    url == WriteAnswerChoiceTab.Recommended.initialUrl -> recommendedRaw
                    else -> secondPage
                }
            }
            recommended.loadMore(environment)
            invite.loadMore(environment)
            advanceUntilIdle()
            assertEquals(1, recommended.items.size)
            assertEquals(1, invite.items.size)
            assertEquals(WriteAnswerChoiceTab.Invite.initialUrl to "", requests[1])
            assertEquals(WriteAnswerChoiceTab.Recommended.initialUrl to "", requests[0])
            val next = decodeWriteAnswerChoices(recommendedRaw, WriteAnswerChoiceTab.Recommended).paging.next
            failNext = true
            recommended.loadMore(environment)
            advanceUntilIdle()
            assertEquals(next to "", requests.last())
            assertEquals("recoverable-page-failure", recommended.errorMessage)
            assertEquals(1, recommended.items.size)
            recommended.retry(environment)
            advanceUntilIdle()
            assertEquals(next to "", requests.last())
            assertEquals(2, recommended.items.size)
            assertTrue(recommended.isEnd)
            assertNull(recommended.errorMessage)
            val requestsAtEnd = requests.size
            recommended.loadMore(environment)
            advanceUntilIdle()
            assertEquals(requestsAtEnd, requests.size)
            assertNull(invite.errorMessage)
            assertEquals(1967980736595337716, invite.items.single().questionId)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun refreshCancelsOldRequestAndEmptyPageIsNotDecodeFailure() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val raw = fixture("hot")
            val delayed = CompletableDeferred<JsonObject>()
            var count = 0
            val environment = environment { _, _ ->
                count++
                if (count == 1) {
                    delayed.await()
                } else {
                    JsonObject(
                        raw + mapOf(
                            "data" to JsonArray(emptyList()),
                            "paging" to JsonObject(raw.getValue("paging").jsonObject + ("is_end" to JsonPrimitive(true))),
                        ),
                    )
                }
            }
            val model = WriteAnswerChoicesViewModel(WriteAnswerChoiceTab.Hot)
            model.loadMore(environment)
            advanceUntilIdle()
            assertTrue(model.isLoading)
            model.refresh(environment)
            advanceUntilIdle()
            delayed.complete(raw)
            advanceUntilIdle()
            assertTrue(model.hasLoaded)
            assertTrue(model.isEnd)
            assertFalse(model.isLoading)
            assertTrue(model.items.isEmpty())
            assertNull(model.errorMessage)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun refusesCrossHostPagingBeforePublishingPage() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val raw = fixture("recommend")
            val unsafe = JsonObject(raw + ("paging" to JsonObject(raw.getValue("paging").jsonObject + ("next" to JsonPrimitive("https://example.invalid/next")))))
            val model = WriteAnswerChoicesViewModel(WriteAnswerChoiceTab.Recommended)
            model.loadMore(environment { _, _ -> unsafe })
            advanceUntilIdle()
            assertTrue(model.items.isEmpty())
            assertFalse(model.hasLoaded)
            assertTrue(model.errorMessage.orEmpty().contains("无效的分页地址"))
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun fixture(name: String): JsonObject = ZhihuJson.json
        .parseToJsonElement(checkNotNull(javaClass.getResource("/answer-choices/$name.json")).readText())
        .jsonObject

    private fun environment(fetch: suspend (String, String) -> JsonObject) = object : ZhihuApiEnvironment {
        override fun httpClient(): HttpClient = error("这里只替换生产 fetchJson 调用")

        override fun authenticatedCookies(): Map<String, String> = emptyMap()

        override suspend fun fetchJson(url: String, include: String): JsonObject = fetch(url, include)

        override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
    }
}
