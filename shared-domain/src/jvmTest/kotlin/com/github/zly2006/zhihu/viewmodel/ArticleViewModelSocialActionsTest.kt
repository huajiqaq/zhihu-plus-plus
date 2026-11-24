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
import com.github.zly2006.zhihu.data.Collection
import com.github.zly2006.zhihu.data.CollectionResponse
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.VoteUpState
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.ZhihuPaging
import com.github.zly2006.zhihu.data.ZhihuVotersResponse
import com.github.zly2006.zhihu.data.installZhihuCommonClientConfig
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.platform.UserMessageSink
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 文章页点赞、赞同者、收藏和关注入口对外发布的状态与请求顺序。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArticleViewModelSocialActionsTest {
    @Test
    fun answerVoteUpPublishesServerCountThenReloadsVotersAndSocialText() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val voteBodies = mutableListOf<JsonObject>()
        val client = mockClient(json = true) { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v4/answers/7/voters", request.url.encodedPath)
            voteBodies += requestJson(request)
            respond("""{"voteup_count":12}""", HttpStatusCode.OK, JSON_HEADERS)
        }
        val fetched = mutableListOf<String>()
        val environment = FixtureEnvironment(client) { url ->
            fetched += url
            if (url.endsWith("/relationship?desktop=true")) {
                ZhihuJson.json.parseToJsonElement("""{"type":"fixture","text":"fixture 等 2 人赞同"}""").jsonObject
            } else {
                votersPage(listOf("a", "b"), next = "", isEnd = true, totals = 12)
            }
        }
        val model = ArticleViewModel(Article(type = ArticleType.Answer, id = 7), client)
        try {
            model.toggleVoteUp(environment, VoteUpState.Up)
            model.awaitIdle()

            val voteBody = voteBodies.single()
            assertEquals("up", voteBody.getValue("type").jsonPrimitive.content)
            assertEquals(VoteUpState.Up, model.voteUpState)
            assertEquals(12, model.voteUpCount)
            assertEquals(12, model.votersTotal)
            assertEquals("fixture 等 2 人赞同", model.votersSocialText)
            assertEquals(listOf("a", "b"), model.voters.map { it.id })
            assertNull(model.votersNextUrl)
            assertNull(model.votersError)
            assertFalse(model.votersLoading)
            assertEquals(
                setOf(
                    "https://www.zhihu.com/api/v4/answers/7/relationship?desktop=true",
                    "https://www.zhihu.com/api/v4/answers/7/upvoters?limit=10&offset=0",
                ),
                fetched.toSet(),
            )
        } finally {
            model.release(client)
        }
    }

    @Test
    fun articleVoteUpSendsVotingFlagAndDoesNotLoadAnswerVoters() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val voteBodies = mutableListOf<JsonObject>()
        val client = mockClient(json = true) { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v4/articles/9/voters", request.url.encodedPath)
            voteBodies += requestJson(request)
            respond("""{"voteup_count":5}""", HttpStatusCode.OK, JSON_HEADERS)
        }
        val fetched = mutableListOf<String>()
        val environment = FixtureEnvironment(client) { url ->
            fetched += url
            null
        }
        val model = ArticleViewModel(Article(type = ArticleType.Article, id = 9), client)
        try {
            model.toggleVoteUp(environment, VoteUpState.Up)
            model.awaitIdle()
            model.toggleVoteUp(environment, VoteUpState.Neutral)
            model.awaitIdle()

            assertEquals(listOf("1", "0"), voteBodies.map { it.getValue("voting").jsonPrimitive.content })
            assertEquals(VoteUpState.Neutral, model.voteUpState)
            assertEquals(5, model.voteUpCount)
            assertEquals(5, model.votersTotal)
            assertEquals(emptyList(), fetched)
        } finally {
            model.release(client)
        }
    }

    @Test
    fun votersPagingAppendsUniqueAuthorsAndFailureKeepsLoadedPages() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val client = mockClient(json = false) { error("赞同者列表不直接使用页面客户端") }
        val fetched = mutableListOf<String>()
        var failNext = false
        val environment = FixtureEnvironment(client) { url ->
            fetched += url
            if (failNext) throw IllegalStateException("fixture voters failure")
            if (url.endsWith("offset=0")) {
                votersPage(
                    listOf("a", "b"),
                    next = "http://www.zhihu.com/api/v4/answers/7/upvoters?limit=10&offset=10",
                    isEnd = false,
                    totals = 3,
                )
            } else {
                votersPage(listOf("b", "c"), next = "", isEnd = true, totals = 3)
            }
        }
        val model = ArticleViewModel(Article(type = ArticleType.Answer, id = 7), client)
        try {
            model.loadMoreVoters(environment, reset = true)
            model.awaitIdle()
            assertEquals(listOf("a", "b"), model.voters.map { it.id })
            assertEquals(3, model.votersTotal)
            assertEquals("https://www.zhihu.com/api/v4/answers/7/upvoters?limit=10&offset=10", model.votersNextUrl)

            model.loadMoreVoters(environment)
            model.awaitIdle()
            assertEquals(listOf("a", "b", "c"), model.voters.map { it.id })
            assertNull(model.votersNextUrl)
            assertEquals(
                listOf(
                    "https://www.zhihu.com/api/v4/answers/7/upvoters?limit=10&offset=0",
                    "https://www.zhihu.com/api/v4/answers/7/upvoters?limit=10&offset=10",
                ),
                fetched,
            )

            failNext = true
            model.loadMoreVoters(environment, reset = true)
            model.awaitIdle()
            assertEquals("fixture voters failure", model.votersError)
            assertFalse(model.votersLoading)
            assertEquals(listOf("a", "b", "c"), model.voters.map { it.id })
        } finally {
            model.release(client)
        }
    }

    @Test
    fun collectionsKeepEstablishedOrderAndListNewOnesFirst() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val client = mockClient(json = false) { error("收藏夹列表不直接使用页面客户端") }
        val pages = ArrayDeque(
            listOf(
                listOf("c1" to true, "c2" to false),
                listOf("c2" to false, "c3" to false, "c1" to true),
            ),
        )
        val fetched = mutableListOf<String>()
        val environment = FixtureEnvironment(client) { url ->
            fetched += url
            collectionsPage(pages.removeFirst())
        }
        val model = ArticleViewModel(Article(type = ArticleType.Answer, id = 7), client)
        try {
            model.loadCollections(environment)
            model.awaitIdle()
            assertEquals(listOf("c1", "c2"), model.collections.map { it.id })
            assertTrue(model.isFavorited)

            model.loadCollections(environment)
            model.awaitIdle()
            assertEquals(listOf("c3", "c1", "c2"), model.collections.map { it.id })
            assertEquals(List(2) { "https://api.zhihu.com/collections/contents/answer/7?limit=50" }, fetched)
        } finally {
            model.release(client)
        }
    }

    @Test
    fun favoriteResponseStatusDecidesReloadAndMessage() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val bodies = mutableListOf<String>()
        var status = HttpStatusCode.OK
        val client = mockClient(json = false) { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("api.zhihu.com", request.url.host)
            assertEquals("/collections/contents/answer/7", request.url.encodedPath)
            bodies += request.body.toByteArray().decodeToString()
            respond("{}", status, JSON_HEADERS)
        }
        var reloads = 0
        val messages = mutableListOf<String>()
        val environment = FixtureEnvironment(client) {
            reloads++
            collectionsPage(listOf("c1" to false))
        }
        val model = ArticleViewModel(Article(type = ArticleType.Answer, id = 7), client, messageSink(messages))
        try {
            model.toggleFavorite("c1", remove = true, environment = environment)
            model.awaitIdle()
            assertEquals(listOf("remove_collections=c1"), bodies)
            assertEquals(1, reloads)
            assertEquals(listOf("取消收藏成功"), messages)

            status = HttpStatusCode.InternalServerError
            model.toggleFavorite("c1", remove = false, environment = environment)
            model.awaitIdle()
            assertEquals(listOf("remove_collections=c1", "add_collections=c1"), bodies)
            assertEquals(1, reloads)
            assertEquals(listOf("取消收藏成功", "收藏操作失败"), messages)
        } finally {
            model.release(client)
        }
    }

    @Test
    fun newCollectionIsPostedAsJsonBeforeCollectionsReload() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val events = mutableListOf<String>()
        val client = mockClient(json = true) { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v4/collections", request.url.encodedPath)
            val body = requestJson(request)
            val title = body.getValue("title").jsonPrimitive.content
            val description = body.getValue("description").jsonPrimitive.content
            val isPublic = body.getValue("is_public").jsonPrimitive.boolean
            events += "post:$title:$description:$isPublic"
            respond("{}", HttpStatusCode.OK, JSON_HEADERS)
        }
        val environment = FixtureEnvironment(client) {
            events += "reload"
            collectionsPage(listOf("created" to false))
        }
        val model = ArticleViewModel(Article(type = ArticleType.Answer, id = 7), client)
        try {
            model.createNewCollection(environment, title = "fixture 收藏夹", description = "fixture 描述")
            model.awaitIdle()
            assertEquals(listOf("post:fixture 收藏夹:fixture 描述:false", "reload"), events)
            assertEquals(listOf("created"), model.collections.map { it.id })
        } finally {
            model.release(client)
        }
    }

    @Test
    fun successfulFollowAndUnfollowPublishTheNewRelationship() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val methods = mutableListOf<HttpMethod>()
        val client = mockClient(json = false) { request ->
            assertEquals("/api/v4/members/fixture-author/followers", request.url.encodedPath)
            methods += request.method
            respond("{}", HttpStatusCode.OK, JSON_HEADERS)
        }
        val environment = FixtureEnvironment(client) { error("关注不读取列表接口") }
        val article = Article(type = ArticleType.Answer, id = 7)
        val model = ArticleViewModel(article, client)
        model.seedFromCached(
            ArticleViewModel.CachedAnswerContent(
                article = article,
                title = "fixture",
                authorName = "fixture author",
                authorBio = "",
                authorAvatarUrl = "",
                authorId = "fixture-author",
                authorUrlToken = "fixture-author",
                content = "<p>fixture</p>",
                voteUpCount = 0,
                commentCount = 0,
            ),
        )
        try {
            assertTrue(model.canFollowAuthor)
            model.toggleAuthorFollow(environment)
            model.awaitIdle()
            assertTrue(model.authorIsFollowing)
            assertFalse(model.authorFollowChanging)

            model.toggleAuthorFollow(environment)
            model.awaitIdle()
            assertFalse(model.authorIsFollowing)
            assertFalse(model.authorFollowChanging)
            assertEquals(listOf(HttpMethod.Post, HttpMethod.Delete), methods)
        } finally {
            model.release(client)
        }
    }

    private class FixtureEnvironment(
        private val client: HttpClient,
        private val json: suspend (url: String) -> JsonObject?,
    ) : ZhihuApiEnvironment {
        override fun httpClient() = client

        override fun authenticatedCookies() = mapOf("d_c0" to "test-device")

        override suspend fun fetchJson(url: String, include: String): JsonObject? = json(url)

        override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
    }

    private fun mockClient(
        json: Boolean,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient = HttpClient(MockEngine(handler)) {
        if (json) installZhihuCommonClientConfig(mutableMapOf(), "article-social-actions-test")
    }

    private suspend fun requestJson(request: HttpRequestData): JsonObject {
        val text = request.body.toByteArray().decodeToString()
        return ZhihuJson.json.parseToJsonElement(text).jsonObject
    }

    private fun messageSink(messages: MutableList<String>) = object : UserMessageSink {
        override fun showShortMessage(message: String) {
            messages += message
        }
    }

    private suspend fun ArticleViewModel.awaitIdle() {
        val owner = checkNotNull(viewModelScope.coroutineContext[Job])
        while (true) {
            val running = owner.children.toList()
            if (running.isEmpty()) return
            running.joinAll()
        }
    }

    private suspend fun ArticleViewModel.release(client: HttpClient) {
        checkNotNull(viewModelScope.coroutineContext[Job]).cancelAndJoin()
        client.close()
        Dispatchers.resetMain()
    }

    private fun votersPage(
        ids: List<String>,
        next: String,
        isEnd: Boolean,
        totals: Int,
    ): JsonObject {
        val page = ZhihuVotersResponse(
            paging = ZhihuPaging(isEnd = isEnd, totals = totals, next = next),
            data = ids.map(::author),
        )
        return ZhihuJson.json.encodeToJsonElement(ZhihuVotersResponse.serializer(), page).jsonObject
    }

    private fun collectionsPage(items: List<Pair<String, Boolean>>): JsonObject {
        val page = CollectionResponse(
            data = items.map { (id, favorited) -> Collection(id = id, isFavorited = favorited) },
            paging = ZhihuPaging(isEnd = true, next = ""),
        )
        return ZhihuJson.json.encodeToJsonElement(CollectionResponse.serializer(), page).jsonObject
    }

    private fun author(id: String) = DataHolder.Author(
        avatarUrl = "",
        gender = 0,
        headline = "",
        id = id,
        isAdvertiser = false,
        isOrg = false,
        name = "fixture $id",
        type = "people",
        url = "https://www.zhihu.com/people/$id",
        urlToken = id,
        userType = "people",
    )

    private companion object {
        val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
