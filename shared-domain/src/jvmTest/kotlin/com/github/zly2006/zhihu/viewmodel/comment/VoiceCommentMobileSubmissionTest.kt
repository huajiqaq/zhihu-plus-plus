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

import com.github.zly2006.zhihu.data.installZhihuCommonClientConfig
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.CommentHolder
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceCommentMobileSubmissionTest {
    @Test
    fun rootVoiceCommentUsesMobileEndpoint() = verifySubmission(child = false, voice = true)

    @Test
    fun childVoiceCommentUsesMobileEndpointAndKeepsReplyTarget() = verifySubmission(child = true, voice = true)

    @Test
    fun rootTextCommentKeepsWebEndpoint() = verifySubmission(child = false, voice = false)

    @Test
    fun childTextCommentKeepsWebEndpoint() = verifySubmission(child = true, voice = false)

    private fun verifySubmission(child: Boolean, voice: Boolean) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val client = HttpClient(
            MockEngine { request ->
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(if (voice) "api.zhihu.com" else "www.zhihu.com", request.url.host)
                assertEquals(if (voice) "/comment_v5/answers/9001/comment" else "/api/v4/comment_v5/answers/9001/comment", request.url.encodedPath)
                val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                if (voice) {
                    assertEquals(
                        "voice-contract",
                        body
                            .getValue("voice_info")
                            .jsonObject
                            .getValue("voice_id")
                            .jsonPrimitive.content,
                    )
                    assertEquals("3.1.8", request.headers["x-api-version"])
                    assertEquals("11.12.0", request.headers["x-app-version"])
                    val userAgent = request.headers[HttpHeaders.UserAgent].orEmpty()
                    assertTrue(userAgent.contains("com.zhihu.android/Futureve/11.12.0"))
                    assertFalse(userAgent.contains("Build/"))
                    val appIdentity = request.headers["x-app-za"].orEmpty()
                    assertTrue(appIdentity.contains("Product=com.zhihu.android"))
                    assertFalse(appIdentity.contains("Model="))
                    assertFalse(appIdentity.contains("Width="))
                    assertFalse(appIdentity.contains("Height="))
                }
                assertEquals(if (child) "root-1" else "", body.getValue("reply_comment_id").jsonPrimitive.content)
                respond("""{"error":{"code":400,"message":"验证失败恢复，不发布评论"}}""", HttpStatusCode.BadRequest, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ) { installZhihuCommonClientConfig(mutableMapOf("d_c0" to "test-dc0"), "web-contract-test") }
        try {
            val environment = object : ZhihuApiEnvironment {
                override fun httpClient() = client

                override fun authenticatedCookies() = mapOf("d_c0" to "test-dc0")

                override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
            }
            val article = Article(type = ArticleType.Answer, id = 9001)
            val target = if (child) CommentHolder("root-1", article) else article
            val viewModel: BaseCommentViewModel = if (child) ChildCommentViewModel(target) else RootCommentViewModel(target)
            val finished = CompletableDeferred<Unit>()
            viewModel.submitComment(
                content = target,
                commentText = if (voice) "" else "文字评论",
                environment = environment,
                voiceId = if (voice) "voice-contract" else null,
                onFinished = { finished.complete(Unit) },
                onSuccess = { error("失败响应不能清空草稿") },
            )
            finished.await()
            assertTrue(viewModel.errorMessage.orEmpty().contains("400"))
        } finally {
            client.close()
            Dispatchers.resetMain()
        }
    }
}
