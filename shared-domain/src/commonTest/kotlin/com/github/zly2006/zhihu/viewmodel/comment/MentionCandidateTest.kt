package com.github.zly2006.zhihu.viewmodel.comment

import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MentionCandidateTest {
    @Test
    fun requestEncodesQueryAndParsesWrappedAndDirectMembers() = runTest {
        val queries = mutableListOf<String?>()
        val client = HttpClient(
            MockEngine { request ->
                assertEquals(HttpMethod.Get, request.method)
                assertEquals("api.zhihu.com", request.url.host)
                assertEquals("/people/ats", request.url.encodedPath)
                assertEquals("comment_editor", request.url.parameters["scene"])
                assertEquals("0", request.url.parameters["offset"])
                assertEquals("20", request.url.parameters["limit"])
                queries += request.url.parameters["q"]
                respond(
                    """{"data":[{"member":{"id":"member-1","name":"张三","url_token":"zhangsan","avatar_url":"https://picx.zhimg.com/avatar.png"}},{"id":"member-2","name":"李四","url_token":""},{"id":"missing-name"},{"id":"","name":"没有身份"}]}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
        try {
            val environment = environment(client)
            val expected = listOf(
                MentionCandidate("member-1", "张三", "zhangsan", "https://picx.zhimg.com/avatar.png"),
                MentionCandidate("member-2", "李四", "member-2", null),
            )
            assertEquals(expected, fetchMentionCandidates(environment, " 张 & 李 "))
            assertEquals(expected, fetchMentionCandidates(environment, " "))
            assertEquals(listOf("张 & 李", null), queries)
        } finally {
            client.close()
        }
    }

    @Test
    fun cancellingSearchDoesNotBecomeEmptyResults() = runTest {
        val client = HttpClient(MockEngine { throw CancellationException("cancel mention search") })
        try {
            assertFailsWith<CancellationException> { fetchMentionCandidates(environment(client), "张") }
        } finally {
            client.close()
        }
    }

    private fun environment(client: HttpClient) = object : ZhihuApiEnvironment {
        override fun httpClient() = client

        override fun authenticatedCookies() = emptyMap<String, String>()

        override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
    }
}
