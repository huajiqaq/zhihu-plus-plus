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

import com.github.zly2006.zhihu.data.installZhihuCommonClientConfig
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression for https://github.com/zly2006/zhihu-plus-plus/issues/691：
 * 点击关注动态头像后，应使用服务端返回的 brief 标记该用户已读，且不影响其他用户。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FollowUserUpdatesTest {
    @Test
    fun viewingOneUserAcknowledgesItsOriginalBriefWithoutClearingOtherUsers() = runTest {
        withEnvironment({ request ->
            respond(fixture(if (request.method == HttpMethod.Post) "read.json" else "recent.json"), headers = jsonHeaders)
        }) { environment, requests, _ ->
            val model = RecentMomentsViewModel()
            model.load(environment)
            advanceUntilIdle()
            assertEquals(5, model.users.size)
            assertEquals(listOf(1, 1, 0, 0, 0), model.users.map { it.unreadCount })
            val user = model.users.first()
            assertTrue(user.brief.isNotBlank())

            model.markRead(environment, user.actor.id)
            model.markRead(environment, user.actor.id)
            advanceUntilIdle()
            assertEquals(listOf(0, 1, 0, 0, 0), model.users.map { it.unreadCount })
            val read = requests.single { it.method == HttpMethod.Post }
            assertEquals("/moments/recent/read", read.url.encodedPath)
            assertEquals(user.brief, read.url.parameters["brief"])
            assertEquals("api.zhihu.com", read.url.host)
            assertEquals(
                1,
                read.url.parameters
                    .getAll("brief")
                    ?.size,
            )
            assertNull(model.errorMessage)
        }
    }

    @Test
    fun failedAcknowledgementRestoresOnlyTheAffectedUnreadBadge() = runTest {
        val release = CompletableDeferred<Unit>()
        withEnvironment({ request ->
            if (request.method == HttpMethod.Post) {
                release.await()
                throw IOException("连接中断")
            }
            respond(fixture("recent.json"), headers = jsonHeaders)
        }) { environment, _, failures ->
            val model = RecentMomentsViewModel()
            model.load(environment)
            advanceUntilIdle()
            model.markRead(
                environment,
                model.users
                    .first()
                    .actor.id,
            )
            assertEquals(listOf(0, 1, 0, 0, 0), model.users.map { it.unreadCount })
            release.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf(1, 1, 0, 0, 0), model.users.map { it.unreadCount })
            assertEquals(1, failures.size)
            assertNull(model.errorMessage)
        }
    }

    @Test
    fun alreadyReadUsersDoNotSendAnotherAcknowledgement() = runTest {
        withEnvironment({ respond(fixture("recent.json"), headers = jsonHeaders) }) { environment, requests, _ ->
            val model = RecentMomentsViewModel()
            model.load(environment)
            advanceUntilIdle()
            model.markRead(environment, model.users[2].actor.id)
            model.markRead(environment, "missing-user")
            advanceUntilIdle()
            assertEquals(1, requests.size)
            assertEquals(listOf(1, 1, 0, 0, 0), model.users.map { it.unreadCount })
        }
    }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun fixture(name: String) = checkNotNull(javaClass.classLoader.getResourceAsStream("follow-updates/$name"))
        .bufferedReader(Charsets.UTF_8)
        .use { it.readText() }

    private suspend fun TestScope.withEnvironment(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
        block: suspend (ZhihuApiEnvironment, MutableList<HttpRequestData>, MutableList<Exception>) -> Unit,
    ) {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val requests = mutableListOf<HttpRequestData>()
        val failures = mutableListOf<Exception>()
        val client = HttpClient(MockEngine) {
            engine {
                this.dispatcher = dispatcher
                addHandler { request ->
                    requests += request
                    handler(request)
                }
            }
            installZhihuCommonClientConfig(mutableMapOf(), "test")
        }
        val environment = object : ZhihuApiEnvironment {
            override fun httpClient() = client

            override fun authenticatedCookies() = mapOf("d_c0" to "device", "_xsrf" to "xsrf")

            override suspend fun handleFetchFailure(
                tag: String?,
                error: Exception,
            ) {
                failures += error
            }
        }
        try {
            block(environment, requests, failures)
        } finally {
            client.close()
            Dispatchers.resetMain()
        }
    }
}
