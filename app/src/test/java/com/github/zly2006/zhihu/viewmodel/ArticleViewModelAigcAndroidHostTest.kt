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
import com.github.zly2006.zhihu.data.AigcVoteVoter
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.platform.UserMessageSink
import com.github.zly2006.zhihu.platform.isAigcVoteSupported
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArticleViewModelAigcAndroidHostTest {
    @Test
    fun disabledPreferenceBlocksAllRequests() = exercise {
        environment.enabled = false
        model.content = FIXTURE_HTML
        model.loadAigcFlagStatus(environment)
        model.syncAigcReadEventIfEligible(environment)
        runCurrent()
        assertFalse(model.aigcVoteAvailable)
        assertEquals(VOTER.name, model.aigcVoterName)
        assertNull(model.aigcVoteError)

        model.submitAigcFlag(environment)
        runCurrent()
        assertEquals("未配置 AIGC 投票服务", model.aigcVoteError)
        assertEquals(listOf("未配置 AIGC 投票服务"), messages)
        assertEquals(0, environment.clientLookups)
        assertTrue(requests.isEmpty())
        assertFalse(model.aigcVoteLoading)
    }

    @Test
    fun statusUsesOptionalVoterQueryAndClearsMissingLegacyFields() = exercise {
        environment.voter = null
        responseBody = STATUS_CURRENT
        model.loadAigcFlagStatus(environment)
        runCurrent()

        with(requests.single()) {
            assertEquals(HttpMethod.Get, method)
            assertEquals("/v1/contents/answer/601/aigc-flag", url.encodedPath)
            assertEquals(setOf("client_id"), url.parameters.names())
            assertEquals(CLIENT_ID, url.parameters["client_id"])
        }
        assertTrue(model.aigcVoteAvailable)
        assertEquals("", model.aigcVoterName)
        assertTrue(model.aigcFlagged)
        assertTrue(model.aigcCreditBypassAvailable)
        assertEquals(2, model.aigcVoteCredit)
        assertEquals(3, model.aigcVoteProgress)
        assertEquals(8, model.aigcVoteCap)
        assertEquals(4, model.aigcEffectiveFlagCount)
        assertEquals(0, model.aigcCurrentVersionFlagCount)
        assertEquals(6, model.zhihuaiAigcSupportVoterCount)
        assertEquals(10, model.aigcSupportVoterCount)
        assertEquals("fixture-other", model.aigcNamedVoters.single().voterId)
        assertFalse(model.aigcVoteLoading)
        assertNull(model.aigcVoteError)

        environment.voter = VOTER
        responseBody = STATUS_LEGACY
        model.loadAigcFlagStatus(environment)
        runCurrent()
        with(requests.last().url.parameters) {
            assertEquals(setOf("client_id", "voter_id", "voter_name", "voter_url_token"), names())
            assertEquals(VOTER.id, this["voter_id"])
            assertEquals(VOTER.name, this["voter_name"])
            assertEquals(VOTER.urlToken, this["voter_url_token"])
        }
        assertEquals(VOTER.name, model.aigcVoterName)
        assertFalse(model.aigcFlagged)
        assertFalse(model.aigcCreditBypassAvailable)
        assertTrue(model.aigcNamedVoters.isEmpty())
        assertEquals(0, model.zhihuaiAigcSupportVoterCount)
        assertEquals(1, model.aigcSupportVoterCount)

        environment.voter = VOTER.copy(urlToken = " ")
        model.loadAigcFlagStatus(environment)
        runCurrent()
        assertEquals(3, requests.size)
        val lastRequestUrl = requests.last().url
        assertEquals(setOf("client_id", "voter_id", "voter_name"), lastRequestUrl.parameters.names())
    }

    @Test
    fun statusClearsLoadingOnFailureAndRecoversOnNextRequest() = exercise {
        responseGate = CompletableDeferred()
        responseBody = "not-json"
        model.loadAigcFlagStatus(environment)
        runCurrent()
        assertEquals(1, requests.size)
        assertTrue(model.aigcVoteLoading)
        assertNull(model.aigcVoteError)

        checkNotNull(responseGate).complete(Unit)
        runCurrent()
        assertFalse(model.aigcVoteLoading)
        assertTrue(!model.aigcVoteError.isNullOrBlank())
        assertEquals(0, model.aigcSupportVoterCount)

        responseGate = null
        responseBody = STATUS_LEGACY
        model.loadAigcFlagStatus(environment)
        runCurrent()
        assertEquals(2, requests.size)
        assertNull(model.aigcVoteError)
        assertFalse(model.aigcVoteLoading)
        assertEquals(1, model.aigcSupportVoterCount)
    }

    @Test
    fun submitPreconditionsDoNotPostAndReadSyncRejectsBlankOrInsufficientEvidence() = exercise {
        model.updateAigcReadProgress(currentScroll = 100, maxScroll = 100)
        model.syncAigcReadEventIfEligible(environment)
        assertTrue(model.aigcVoteAvailable)
        model.submitAigcFlag(environment)
        assertEquals("正文尚未加载完成", model.aigcVoteError)

        model.content = FIXTURE_HTML
        model.createdAt = 1_700_000_000L
        model.updateAigcReadProgress(currentScroll = -3, maxScroll = 10)
        assertFalse(model.isAigcFlagEvidenceReady())
        model.syncAigcReadEventIfEligible(environment)
        environment.voter = null
        model.submitAigcFlag(environment)
        assertEquals("需要登录后才能记名投票", model.aigcVoteError)

        environment.voter = VOTER
        model.submitAigcFlag(environment)
        assertEquals("投票积分不足", model.aigcVoteError)
        runCurrent()
        assertTrue(requests.isEmpty())

        responseBody = STATUS_LEGACY.replace("\"credit\":0", "\"credit\":2")
        model.loadAigcFlagStatus(environment)
        runCurrent()
        assertEquals(2, model.aigcVoteCredit)
        model.submitAigcFlag(environment)
        runCurrent()
        assertEquals("需要继续阅读后才能标记", model.aigcVoteError)
        assertEquals(HttpMethod.Get, requests.single().method)
        assertFalse(model.aigcVoteLoading)
        assertEquals(
            listOf("正文尚未加载完成", "需要登录后才能记名投票", "投票积分不足", "需要继续阅读后才能标记"),
            messages,
        )
    }

    @Test
    fun bypassSubmitPublishesResultAndPreservesReadCounters() = exercise(ArticleType.Article) {
        responseBody = STATUS_LEGACY.replace("\"credit\":0", "\"credit\":0,\"credit_bypass_available\":true")
        model.loadAigcFlagStatus(environment)
        runCurrent()
        assertEquals(0, model.aigcVoteCredit)
        assertFalse(model.isAigcFlagEvidenceReady())
        assertTrue(model.aigcCreditBypassAvailable)

        model.content = FIXTURE_HTML
        model.title = "合成标题"
        model.authorId = "abc"
        model.authorUrlToken = "synthetic-author-slug"
        model.authorName = "合成作者"
        model.createdAt = 1_700_000_000L
        model.updatedAt = 1_700_000_001L
        model.updateAigcReadProgress(currentScroll = 12, maxScroll = 10)
        model.updateAigcReadProgress(currentScroll = 2, maxScroll = 10)

        responseBody = FLAG_LEGACY
        model.submitAigcFlag(environment)
        runCurrent()
        assertEquals(2, requests.size)
        with(requests.last()) {
            assertEquals(HttpMethod.Post, method)
            assertEquals("https", url.protocol.name)
            assertEquals("aigc.example.invalid", url.host)
            assertEquals("/v1/contents/article/601/aigc-flag", url.encodedPath)
            assertTrue(url.parameters.isEmpty())
            val sent = body as OutgoingContent.ByteArrayContent
            assertEquals("application/json", sent.contentType?.toString()?.substringBefore(';'))
            val payload = Json.parseToJsonElement(sent.bytes().decodeToString()).jsonObject
            assertEquals(setOf("client_id", "voter", "title", "author_hash", "content_html", "content_updated_at", "evidence"), payload.keys)
            assertEquals(CLIENT_ID, payload.getValue("client_id").jsonPrimitive.content)
            assertEquals("合成标题", payload.getValue("title").jsonPrimitive.content)
            assertEquals(FIXTURE_HTML, payload.getValue("content_html").jsonPrimitive.content)
            assertEquals("900150983cd24fb0d6963f7d28e17f72", payload.getValue("author_hash").jsonPrimitive.content)
            assertEquals("1700000001", payload.getValue("content_updated_at").jsonPrimitive.content)
            val voter = payload.getValue("voter").jsonObject
            assertEquals(VOTER.id, voter.getValue("id").jsonPrimitive.content)
            assertEquals(VOTER.name, voter.getValue("name").jsonPrimitive.content)
            assertEquals(VOTER.urlToken, voter.getValue("url_token").jsonPrimitive.content)
            val evidence = payload.getValue("evidence").jsonObject
            assertEquals(setOf("client_view_duration_ms", "scroll_depth", "opened_at"), evidence.keys)
            val scrollDepth = evidence.getValue("scroll_depth").jsonPrimitive.content
            val viewDuration = evidence.getValue("client_view_duration_ms").jsonPrimitive.content
            val openedAt = evidence.getValue("opened_at").jsonPrimitive.content
            assertEquals(1.0, scrollDepth.toDouble(), 0.0)
            assertTrue(viewDuration.toLong() >= 0L)
            assertTrue(openedAt.toLong() > 0L)
        }
        assertTrue(model.aigcFlagged)
        assertEquals(1, model.aigcVoteCredit)
        assertEquals(2, model.aigcVoteProgress)
        assertEquals(7, model.aigcVoteCap)
        assertFalse(model.aigcCreditBypassAvailable)
        assertEquals(3, model.aigcEffectiveFlagCount)
        assertEquals(2, model.aigcCurrentVersionFlagCount)
        assertEquals(3, model.aigcSupportVoterCount)
        assertTrue(model.aigcNamedVoters.isEmpty())
        assertFalse(model.aigcVoteLoading)
        assertNull(model.aigcVoteError)
        assertEquals(listOf("已标记疑似 AIGC"), messages)
    }

    @Test
    fun submitFailurePreservesStateAndRetryAppliesCurrentResponse() = exercise {
        responseBody = STATUS_CURRENT
        model.loadAigcFlagStatus(environment)
        runCurrent()
        model.content = FIXTURE_HTML
        model.createdAt = 1_700_000_000L
        model.authorUrlToken = "abc"
        model.authorName = "合成作者"
        model.updateAigcReadProgress(currentScroll = 0, maxScroll = 0)

        responseBody = "not-json"
        model.submitAigcFlag(environment)
        runCurrent()
        assertFalse(model.aigcVoteLoading)
        assertTrue(!model.aigcVoteError.isNullOrBlank())
        assertTrue(model.aigcFlagged)
        assertEquals(2, model.aigcVoteCredit)
        assertEquals(10, model.aigcSupportVoterCount)
        assertTrue(messages.single().startsWith("AIGC 标记失败:"))

        responseBody = FLAG_CURRENT
        model.submitAigcFlag(environment)
        runCurrent()
        assertEquals(listOf(HttpMethod.Get, HttpMethod.Post, HttpMethod.Post), requests.map { it.method })
        with(requests.last()) {
            assertEquals("/v1/contents/answer/601/aigc-flag", url.encodedPath)
            val sent = body as OutgoingContent.ByteArrayContent
            val payload = Json.parseToJsonElement(sent.bytes().decodeToString()).jsonObject
            assertEquals("1700000000", payload.getValue("content_updated_at").jsonPrimitive.content)
            assertEquals("900150983cd24fb0d6963f7d28e17f72", payload.getValue("author_hash").jsonPrimitive.content)
            val payloadEvidence = payload.getValue("evidence").jsonObject
            val depth = payloadEvidence.getValue("scroll_depth").jsonPrimitive.content
            assertEquals(1.0, depth.toDouble(), 0.0)
        }
        assertNull(model.aigcVoteError)
        assertFalse(model.aigcVoteLoading)
        assertEquals(7, model.zhihuaiAigcSupportVoterCount)
        assertEquals(10, model.aigcSupportVoterCount)
        assertEquals("fixture-voter", model.aigcNamedVoters.single().voterId)
        assertEquals("已标记疑似 AIGC", messages.last())
    }

    private fun exercise(type: ArticleType = ArticleType.Answer, block: Fixture.() -> Unit) = runTest {
        assertTrue("Run on the Android variant, not the desktop JVM variant", isAigcVoteSupported)
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val fixture = Fixture(this, type)
            try {
                fixture.block()
            } finally {
                fixture.model.viewModelScope.cancel()
                fixture.client.close()
                runCurrent()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class Fixture(
        private val scope: TestScope,
        type: ArticleType,
    ) {
        val messages = mutableListOf<String>()
        val requests = mutableListOf<HttpRequestData>()
        var responseBody = STATUS_LEGACY
        var responseGate: CompletableDeferred<Unit>? = null
        val client = HttpClient(MockEngine) {
            install(ContentNegotiation) { json() }
            engine {
                dispatcher = StandardTestDispatcher(scope.testScheduler)
                addHandler { request ->
                    requests += request
                    responseGate?.await()
                    respond(responseBody, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
        }
        val environment = FakeEnvironment(client)
        val model = ArticleViewModel(
            Article(type = type, id = 601L),
            httpClient = null,
            userMessages = object : UserMessageSink {
                override fun showShortMessage(message: String) {
                    messages += message
                }
            },
        )

        fun runCurrent() = scope.runCurrent()
    }

    private class FakeEnvironment(
        private val client: HttpClient,
    ) : AigcVoteEnvironment {
        var enabled = true
        var voter: AigcVoteVoter? = VOTER
        var clientLookups = 0

        override fun isAigcVoteEnabled() = enabled

        override fun aigcVoteVoter() = voter

        override fun aigcVoteHttpClient(): HttpClient {
            clientLookups++
            return client
        }

        override fun aigcVoteBaseUrl() = "https://aigc.example.invalid///"

        override fun aigcVoteClientId() = CLIENT_ID
    }

    private companion object {
        const val CLIENT_ID = "fixture-client"
        const val FIXTURE_HTML = "<p>合成正文</p>"
        val VOTER = AigcVoteVoter("fixture-voter", "合成投票人", urlToken = "fixture-public-slug")
        const val STATUS_LEGACY = """{
            "my_flagged":false,"credit":0,"progress":2,"cap":7,
            "effective_flag_count":1,"raw_flag_count":1,"confidence":"fixture"
        }"""
        const val STATUS_CURRENT = """{
            "my_flagged":true,"credit":2,"progress":3,"cap":8,"credit_bypass_available":true,
            "effective_flag_count":4,"raw_flag_count":31,"confidence":"fixture",
            "voters":[{"voter_id":"fixture-other","voter_name":"另一合成人物","created_at":1700000000}],
            "external_source":{"source":"fixture","content_type":"answer","content_id":"601",
                "total_votes":15,"voter_count":6,"total_downvotes":11,"downvoter_count":9,"refreshed_at":1700000000}
        }"""
        const val FLAG_LEGACY = """{
            "my_flagged":true,"credit":1,"effective_flag_count":3,"raw_flag_count":3,
            "current_version_flag_count":2,"content_hash":"fixture-version",
            "content_updated_at":1700000001,"confidence":"fixture"
        }"""
        const val FLAG_CURRENT = """{
            "my_flagged":true,"credit":1,"effective_flag_count":3,"raw_flag_count":21,
            "current_version_flag_count":2,"content_hash":"fixture-version",
            "content_updated_at":1700000000,"confidence":"fixture","credit_bypass_available":true,
            "voters":[{"voter_id":"fixture-voter","voter_name":"合成投票人","created_at":1700000000}],
            "external_source":{"source":"fixture","content_type":"answer","content_id":"601",
                "total_votes":16,"voter_count":7,"total_downvotes":12,"downvoter_count":8,"refreshed_at":1700000000}
        }"""
    }
}
