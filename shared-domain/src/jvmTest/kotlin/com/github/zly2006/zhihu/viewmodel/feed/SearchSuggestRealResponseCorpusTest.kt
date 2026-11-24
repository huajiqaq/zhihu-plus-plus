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

import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 搜索建议夹具来自 `www.zhihu.com/api/v4/search/suggest` 的真实响应，仅删去会话跟踪字段。 */
class SearchSuggestRealResponseCorpusTest {
    @Test
    fun realSuggestResponsesDecodeIntoItems() = runTest {
        val items = fetchSearchSuggest(environment(respondFixture = "search-suggest-2-sanitized.json"), "AI")
        assertEquals(10, items.size)
        assertTrue(items.all { it.query.isNotBlank() })
        // 真实响应包含带徽标图的建议（hot 热搜火焰、koc 盐选"小说"标签）与普通建议
        assertTrue(items.any { it.iconUrl.isNotEmpty() && it.label == "hot" })
        assertTrue(items.any { it.iconUrl.isEmpty() && it.label.isEmpty() })
    }

    @Test
    fun productionUrlConstructionKeepsSingleEncodedQueryParameter() = runTest {
        val requestedUrls = mutableListOf<String>()
        val env = environment(respondFixture = "search-suggest-1-sanitized.json") { requestedUrls += it }
        fetchSearchSuggest(env, "知乎")
        val query = URLBuilder(requestedUrls.last()).parameters
        assertEquals(listOf("知乎"), query.getAll("q"))
    }

    @Test
    fun failedRequestYieldsEmptySuggestListInsteadOfThrowing() = runTest {
        val items = fetchSearchSuggest(
            environment(respondFixture = null, statusCode = HttpStatusCode.InternalServerError),
            "知乎",
        )
        assertTrue(items.isEmpty())
    }

    @Test
    fun suggestionsKeepDuplicateQueriesAndTheirBadges() = runTest {
        val items = fetchSearchSuggest(environment(respondFixture = "search-suggest-5-sanitized.json"), "你好")
        val duplicates = items.filter { it.query == "你好迷人" }

        assertEquals(2, duplicates.size)
        assertEquals("koc", duplicates.single { it.iconUrl.isNotEmpty() }.label)
    }

    private fun fixture(name: String) = checkNotNull(javaClass.getResource("/search/$name")).readText()

    private fun environment(
        respondFixture: String?,
        statusCode: HttpStatusCode = HttpStatusCode.OK,
        onRequest: (String) -> Unit = {},
    ) = object : PaginationEnvironment {
        override fun httpClient() = HttpClient(
            MockEngine { request ->
                onRequest(request.url.toString())
                val body = respondFixture?.let(::fixture) ?: "<html><body>500 Internal Server Error</body></html>"
                respond(
                    body,
                    statusCode,
                    headers = headersOf(
                        HttpHeaders.ContentType,
                        if (respondFixture == null) "text/html" else "application/json",
                    ),
                )
            },
        ) {
            install(ContentNegotiation) {
                json(ZhihuJson.json)
            }
        }

        override fun authenticatedCookies() = mapOf("d_c0" to "test-dc0")

        override suspend fun handleFetchFailure(
            tag: String?,
            error: Exception,
        ) = Unit
    }
}
