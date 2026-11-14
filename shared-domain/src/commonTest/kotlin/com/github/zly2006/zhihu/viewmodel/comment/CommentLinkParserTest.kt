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

class CommentLinkParserTest {
    @Test
    fun oneRequestBatchesDistinctUrlsAndKeepsNormalizedKeys() = runTest {
        var requests = 0
        val client = HttpClient(
            MockEngine { request ->
                requests++
                assertEquals(HttpMethod.Get, request.method)
                assertEquals("api.zhihu.com", request.url.host)
                assertEquals("/content/publish/parse_url", request.url.encodedPath)
                assertEquals("https://example.invalid/a?x=1&y=2,https://example.invalid/b", request.url.parameters["url"])
                assertEquals("editor", request.url.parameters["scene"])
                respond(
                    """{"code":0,"data":{"https://example.invalid/normalized":{"url":"https://example.invalid/a?x=1&y=2","title":"页面 A","icon_name":"answer"},"https://example.invalid/b":{"title":"页面 B","icon_name":null},"https://example.invalid/untitled":{"title":" "}}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
        try {
            val parsed = parseCommentLinks(
                environment(client),
                listOf(" https://example.invalid/a?x=1&y=2 ", "", "https://example.invalid/b", "https://example.invalid/b"),
            )
            assertEquals(1, requests)
            assertEquals(
                mapOf(
                    "https://example.invalid/normalized" to CommentLinkInfo("https://example.invalid/a?x=1&y=2", "页面 A", "answer"),
                    "https://example.invalid/b" to CommentLinkInfo("https://example.invalid/b", "页面 B", null),
                ),
                parsed,
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun emptyBatchDoesNotRequestAndCancellationPropagates() = runTest {
        var requests = 0
        val client = HttpClient(
            MockEngine {
                requests++
                throw CancellationException("cancel parse_url")
            },
        )
        try {
            val environment = environment(client)
            assertEquals(emptyMap(), parseCommentLinks(environment, listOf("", " ")))
            assertEquals(0, requests)
            assertFailsWith<CancellationException> { parseCommentLinks(environment, listOf("https://example.invalid/a")) }
            assertEquals(1, requests)
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
