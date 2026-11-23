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

package com.github.zly2006.zhihu.util

import com.github.zly2006.zhihu.data.installZhihuCommonClientConfig
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import com.github.zly2006.zhihu.viewmodel.postSigned
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ZhihuFetchSignatureTest {
    @Test
    fun md5HexMatchesKnownVectors() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", ZhihuFetchSignature.md5Hex(""))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", ZhihuFetchSignature.md5Hex("abc"))
        assertEquals("5d41402abc4b2a76b9719d911017c592", ZhihuFetchSignature.md5Hex("hello"))
    }

    @Test
    fun capturedEmptyPostSignatureAndRandomSeeds() {
        val url = "https://www.zhihu.com/udid"
        val captured = "2.0_ZuxYx4/qfsop8D4ImgGbYyHI3lAHLpOwRIq5n0zV1nSPN3coQNQUVrGK/XcHRDJd"
        assertEquals(captured, ZhihuFetchSignature.createZse96Header(ZHIHU_WEB_ZSE93, url, null, seed = 4))
        assertEquals(captured, ZhihuFetchSignature.createZse96Header(ZHIHU_WEB_ZSE93, url, "", "", seed = 4))
        assertNotEquals(captured, ZhihuFetchSignature.createZse96Header(ZHIHU_WEB_ZSE93, url, null, "{}", seed = 4))
        assertEquals(ZseSigner.encryptZseV4("hello", seed = 4), ZseSigner.encryptZseV4("hello", seed = 4))
        assertNotEquals(ZseSigner.encryptZseV4("hello", seed = 4), ZseSigner.encryptZseV4("world", seed = 4))
        assertTrue(List(16) { ZhihuFetchSignature.createZse96Header(ZHIHU_WEB_ZSE93, url, null) }.toSet().size > 1)
    }

    @Test
    fun signedRequestPreservesExplicitMethodAndSerializedBody() = runTest {
        val methods = mutableListOf<HttpMethod>()
        val cookies = mutableMapOf("d_c0" to "signed-request-fixture")
        val url = "https://api.zhihu.com/articles/42/draft"
        val body = """{"title":"validation","content":"draft"}"""
        val formBody = "title=validation&content=%3Cp%3Edraft%3C%2Fp%3E"
        val client = HttpClient(
            MockEngine { request ->
                methods += request.method
                assertEquals(url, request.url.toString())
                val serializedBody = if (methods.size == 3) formBody else body
                assertEquals(serializedBody, (request.body as TextContent).text)
                assertEquals(ZHIHU_WEB_ZSE93, request.headers["x-zse-93"])
                assertTrue(
                    (0..126).any { seed ->
                        request.headers["x-zse-96"] == ZhihuFetchSignature.createZse96Header(
                            ZHIHU_WEB_ZSE93,
                            url,
                            cookies.getValue("d_c0"),
                            serializedBody,
                            seed,
                        )
                    },
                )
                respond("{}")
            },
        ) { installZhihuCommonClientConfig(cookies, "signed-request-fixture") }
        val environment = object : ZhihuApiEnvironment {
            override fun httpClient() = client

            override fun authenticatedCookies() = cookies

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
        }
        try {
            environment.postSigned(url) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            environment.postSigned(url) {
                method = HttpMethod.Put
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            environment.postSigned(url) {
                method = HttpMethod.Put
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(formBody)
            }
            assertEquals(listOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Put), methods)
        } finally {
            client.close()
        }
    }

    @Test
    fun signZhihuMobileRequestAddsZseHeaders() {
        val requestUrl = "https://api.zhihu.com/topstory/recommend"
        val builder = HttpRequestBuilder().apply {
            url.takeFrom(requestUrl)
            signZhihuMobileRequest(requestUrl, cookies = mapOf("d_c0" to "token"), body = "payload")
        }

        assertEquals("101_1_1.0", builder.headers["x-zse-93"])
        assertTrue(
            (0..126).any { seed ->
                builder.headers["x-zse-96"] == ZhihuFetchSignature.createZse96Header("101_1_1.0", requestUrl, "token", "payload", seed)
            },
        )
    }

    @Test
    fun signZhihuFetchRequestAddsRequiredHeaders() {
        val builder = HttpRequestBuilder().apply {
            url.takeFrom("https://www.zhihu.com/api/v4/me")
            signZhihuFetchRequest(
                dc0 = "token",
                body = """{"hello":"world"}""",
            )
        }

        assertEquals("101_3_3.0", builder.headers["x-zse-93"])
        assertEquals("fetch", builder.headers["x-requested-with"])
        assertTrue(
            (0..126).any { seed ->
                builder.headers["x-zse-96"] == ZhihuFetchSignature.createZse96Header(
                    ZHIHU_WEB_ZSE93,
                    builder.url.buildString(),
                    "token",
                    """{"hello":"world"}""",
                    seed,
                )
            },
        )
        assertTrue(HttpHeaders.UserAgent !in builder.headers.names())
    }
}
