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

package com.github.zly2006.zhihu.editor

import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.installZhihuCommonClientConfig
import com.github.zly2006.zhihu.util.ZhihuFetchSignature
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ZhihuImageUploadTest {
    @Test
    fun gifMultipartPreservesPartOrderBeforePublishingProcessedMetadata() = runTest {
        val bytes = ByteArray(1024 * 1024 + 17) { (it % 251).toByte() }
        val hash = ZhihuFetchSignature.md5Hex(bytes)
        val requests = mutableListOf<String>()
        val client = HttpClient(
            MockEngine { request ->
                val path = request.url.encodedPath
                requests += "${request.method.value} ${request.url.host}$path"
                when {
                    request.url.host == "api.zhihu.com" && path == "/images" -> {
                        assertEquals(HttpMethod.Post, request.method)
                        val body = ZhihuJson.json.parseToJsonElement((request.body as TextContent).text).jsonObject
                        assertEquals("comment", body.getValue("source").jsonPrimitive.content)
                        assertEquals(hash, body.getValue("image_hash").jsonPrimitive.content)
                        respond("""{"upload_file":{"image_id":"gif-1","state":2},"upload_token":{"access_id":"oss-id","access_key":"oss-key","access_token":"oss-token"}}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                    request.url.host == "zhihu-pics-upload.zhimg.com" -> {
                        assertEquals("/v2-$hash", path)
                        assertTrue(request.headers[HttpHeaders.Authorization].orEmpty().startsWith("OSS oss-id:"))
                        assertEquals("oss-token", request.headers["x-oss-security-token"])
                        assertEquals(null, request.headers[HttpHeaders.Cookie])
                        when {
                            "uploads" in request.url.parameters -> {
                                assertEquals(HttpMethod.Post, request.method)
                                respond("<InitiateMultipartUploadResult><UploadId>upload-1</UploadId></InitiateMultipartUploadResult>")
                            }
                            request.url.parameters["partNumber"] != null -> {
                                assertEquals(HttpMethod.Put, request.method)
                                assertEquals("upload-1", request.url.parameters["uploadId"])
                                val part = checkNotNull(request.url.parameters["partNumber"]).toInt()
                                val begin = (part - 1) * 1024 * 1024
                                val end = minOf(begin + 1024 * 1024, bytes.size)
                                assertContentEquals(bytes.copyOfRange(begin, end), (request.body as OutgoingContent.ByteArrayContent).bytes())
                                respond("", headers = headersOf(HttpHeaders.ETag, "part-$part"))
                            }
                            else -> {
                                assertEquals(HttpMethod.Post, request.method)
                                assertEquals("upload-1", request.url.parameters["uploadId"])
                                assertEquals(
                                    "<CompleteMultipartUpload><Part><PartNumber>1</PartNumber><ETag>part-1</ETag></Part><Part><PartNumber>2</PartNumber><ETag>part-2</ETag></Part></CompleteMultipartUpload>",
                                    (request.body as TextContent).text,
                                )
                                respond("")
                            }
                        }
                    }
                    path == "/images/gif-1/uploading_status" -> {
                        assertEquals(HttpMethod.Put, request.method)
                        assertEquals("""{"upload_result":"success"}""", (request.body as TextContent).text)
                        respond("{}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                    path == "/images/gif-1" -> {
                        assertEquals(HttpMethod.Get, request.method)
                        respond("""{"status":"success","original_src":"https://picx.zhimg.com/server-gif","src":"https://picx.zhimg.com/watermarked","original_hash":"v2-server-gif","format":"gif","width":320,"height":240}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                    else -> error("不应发起图片请求")
                }
            },
        ) { installZhihuCommonClientConfig(mutableMapOf("d_c0" to "synthetic-image-session"), "upload-contract-test") }
        try {
            val image = uploadZhihuImage(environment(client), bytes, "image/gif", "test.gif", ZhihuImageUploadSource.Comment)
            assertEquals("v2-server-gif", image.hash)
            assertEquals("https://picx.zhimg.com/server-gif.gif", image.url)
            assertEquals(320, image.rawWidth)
            assertEquals(240, image.rawHeight)
            assertEquals(
                listOf(
                    "POST api.zhihu.com/images",
                    "POST zhihu-pics-upload.zhimg.com/v2-$hash",
                    "PUT zhihu-pics-upload.zhimg.com/v2-$hash",
                    "PUT zhihu-pics-upload.zhimg.com/v2-$hash",
                    "POST zhihu-pics-upload.zhimg.com/v2-$hash",
                    "PUT api.zhihu.com/images/gif-1/uploading_status",
                    "GET api.zhihu.com/images/gif-1",
                ),
                requests,
            )
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
