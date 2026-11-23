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

package com.github.zly2006.zhihu.data

import com.github.zly2006.zhihu.util.jsonObject
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ZhihuAccountTest {
    @Test
    fun fetchVerifiedSessionReturnsNullForUnauthorizedResponse() = runTest {
        val client = mockClient(
            status = HttpStatusCode.Unauthorized,
            body = """{"error":"unauthorized"}""",
        )

        assertNull(fetchVerifiedZhihuSession(client, emptyMap(), "test-agent"))
    }

    @Test
    fun fetchVerifiedSessionKeepsProfileAndRawSelf() = runTest {
        val cookies = mutableMapOf("z_c0" to "token", "d_c0" to "dc0")
        val client = mockClient(
            status = HttpStatusCode.OK,
            body = """{"id":"1","name":"Alice","url_token":"alice-token","user_type":"people","avatar_url":"https://example.com/avatar.jpg"}""",
            cookies = cookies,
        )

        val session = fetchVerifiedZhihuSession(client, cookies, "test-agent")

        requireNotNull(session)
        assertEquals(true, session.login)
        assertEquals("Alice", session.username)
        assertEquals(cookies, session.cookies)
        assertEquals("test-agent", session.userAgent)
        assertEquals("1", session.profile?.id)
        assertEquals("alice-token", session.profile?.urlToken)
        assertEquals("https://example.com/avatar.jpg", session.profile?.avatarUrl)
        assertEquals(
            "https://example.com/avatar.jpg",
            session.self
                ?.jsonObject
                ?.get("avatar_url")
                ?.jsonPrimitive
                ?.content,
        )
    }

    private fun mockClient(
        status: HttpStatusCode,
        body: String,
        cookies: MutableMap<String, String> = mutableMapOf("_xsrf" to "token"),
    ): HttpClient = HttpClient(
        MockEngine { request ->
            assertEquals(ZHIHU_ME_URL, request.url.toString())
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        },
    ) {
        installZhihuCommonClientConfig(
            cookies = cookies,
            userAgent = "test-agent",
        )
    }
}
