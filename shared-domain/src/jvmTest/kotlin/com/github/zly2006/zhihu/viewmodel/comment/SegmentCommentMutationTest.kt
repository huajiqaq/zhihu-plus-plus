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

package com.github.zly2006.zhihu.viewmodel.comment

import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.installZhihuCommonClientConfig
import com.github.zly2006.zhihu.navigation.SegmentCommentHolder
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SegmentCommentMutationTest {
    @Test
    fun completedSubmissionUsesTheCapturedClientAfterAccountSwitch() = withFixture {
        val alternate = HttpClient(MockEngine { error("账户切换后不应由新客户端发送旧请求") })
        val target = segment("segment-a")
        val oldSnapshot = SegmentCommentEvents.capture(client, "answer", "9001")
        val newSnapshot = SegmentCommentEvents.capture(alternate, "answer", "9001")
        val boundRequestStarted = CompletableDeferred<Unit>()
        val releaseBoundRequest = CompletableDeferred<Unit>()
        var currentClient = client
        val switchingEnvironment = object : ZhihuApiEnvironment {
            override fun httpClient() = currentClient

            override fun authenticatedCookies() = mapOf("d_c0" to "test-dc0")

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit

            override suspend fun <T> withAuthenticatedClient(
                block: suspend (HttpClient, Map<String, String>) -> T,
            ): T {
                val requestClient = currentClient
                boundRequestStarted.complete(Unit)
                releaseBoundRequest.await()
                return block(requestClient, authenticatedCookies())
            }
        }
        try {
            val model = RootCommentViewModel(target)
            val finished = CompletableDeferred<Unit>()
            model.submitComment(
                target,
                "测试评论",
                switchingEnvironment,
                onFinished = { finished.complete(Unit) },
                onSuccess = {},
            )
            boundRequestStarted.await()
            currentClient = alternate
            releaseBoundRequest.complete(Unit)
            finished.await()
            assertNull(model.errorMessage)
            assertEquals(1, oldSnapshot.effectiveDelta(listOf("segment-a")))
            assertEquals(0, newSnapshot.effectiveDelta(listOf("segment-a")))
            assertEquals(listOf(HttpMethod.Post), requests)
        } finally {
            releaseBoundRequest.complete(Unit)
            alternate.close()
        }
    }

    private fun segment(id: String) = SegmentCommentHolder("9001", "answer", id, "划线内容", "paragraph", 0, 4)

    private fun withFixture(test: suspend Fixture.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            fixture.test()
        } finally {
            fixture.client.close()
            Dispatchers.resetMain()
        }
    }

    private class Fixture {
        val requests = mutableListOf<HttpMethod>()
        val responseComment = DataHolder.Comment(
            id = "comment-1",
            type = "comment",
            resourceType = "answer",
            url = "https://example.invalid/comment-1",
            content = "<p>本地测试评论</p>",
            createdTime = 1,
            isDelete = false,
            collapsed = false,
            reviewing = false,
            canDelete = true,
            author = DataHolder.Comment.Author(
                id = "author",
                urlToken = "author",
                name = "测试作者",
                avatarUrl = "",
                avatarUrlTemplate = "",
                isOrg = false,
                type = "people",
                url = "https://example.invalid/author",
                userType = "people",
                headline = "",
                gender = 0,
                isAdvertiser = false,
            ),
        )
        val client = HttpClient(
            MockEngine { request ->
                requests += request.method
                respond(ZhihuJson.json.encodeToString(responseComment), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ) { installZhihuCommonClientConfig(mutableMapOf("d_c0" to "test-dc0"), "segment-count-test") }
    }
}
