package com.github.zly2006.zhihu.editor

import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.installZhihuCommonClientConfig
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import com.github.zly2006.zhihu.viewmodel.comment.StickerAlreadyCollectedException
import com.github.zly2006.zhihu.viewmodel.comment.uploadCommentSticker
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ZhihuImageUploadTest {
    @Test
    fun existingImageUsesServerOriginalHash() = runTest {
        val imageId = "2099012057236501149"
        val client = HttpClient(
            MockEngine { request ->
                val resource = when (request.url.encodedPath) {
                    "/images" -> "image-upload-existing.json"
                    "/images/$imageId" -> "image-upload-status-zim.json"
                    else -> error("Unexpected upload request: ${request.url}")
                }
                respond(
                    checkNotNull(javaClass.getResource("/real-api/$resource")).readText(),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) {
            installZhihuCommonClientConfig(mutableMapOf(), "upload-contract-test")
        }
        try {
            val environment = object : ZhihuApiEnvironment {
                override fun httpClient() = client

                override fun authenticatedCookies() = emptyMap<String, String>()

                override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
            }
            val image = ByteArray(24).apply {
                this[0] = 0x89.toByte()
                this[1] = 'P'.code.toByte()
                this[2] = 'N'.code.toByte()
                this[3] = 'G'.code.toByte()
                this[19] = 128.toByte()
                this[23] = 96
            }
            val uploaded = uploadZhihuImage(environment, image, "image/png", "verification.png", ZhihuImageUploadSource.Comment)

            assertEquals("v2-60060f83bf9651d9c8b1b15bc41009bd", uploaded.hash)
            assertEquals("https://picx.zhimg.com/v2-60060f83bf9651d9c8b1b15bc41009bd.png", uploaded.url)
            assertEquals(imageId, uploaded.imageId)
        } finally {
            client.close()
        }
    }

    @Test
    fun favoriteUploadPreservesServerTokenAndParsesRealResponse() = runTest {
        val token = "v2-60060f83bf9651d9c8b1b15bc41009bd"
        val client = HttpClient(
            MockEngine { request ->
                assertEquals("/emoji/upload", request.url.encodedPath)
                val body = ZhihuJson.json.parseToJsonElement((request.body as TextContent).text).jsonObject
                assertEquals(
                    token,
                    body
                        .getValue("image_tokens")
                        .jsonArray
                        .single()
                        .jsonPrimitive.content,
                )
                respond(
                    checkNotNull(javaClass.getResource("/real-api/emoji-upload-success.json")).readText(),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) {
            installZhihuCommonClientConfig(mutableMapOf(), "upload-contract-test")
        }
        try {
            val environment = object : ZhihuApiEnvironment {
                override fun httpClient() = client

                override fun authenticatedCookies() = emptyMap<String, String>()

                override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
            }
            val uploaded = uploadCommentSticker(environment, token)
            assertEquals("2099013042772108513", uploaded.single().id)
            assertEquals(true, uploaded.single().collected)
        } finally {
            client.close()
        }
    }

    @Test
    fun gifMultipartPreservesPartOrderBeforePublishingProcessedMetadata() = runTest {
        val bytes = ByteArray(1024 * 1024 + 17) { (it % 251).toByte() }
        val hash = com.github.zly2006.zhihu.util.ZhihuFetchSignature
            .md5Hex(bytes)
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
                    else -> error("Unexpected image request")
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

    @Test
    fun processingFailureTimeoutAndCancellationNeverPublishGuessedUrls() = runTest {
        for (mode in listOf("upload_fail", "pending", "cancel")) {
            var statusRequests = 0
            val client = HttpClient(
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/images" -> respond("""{"upload_file":{"image_id":"image-1","state":1}}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                        "/images/image-1" -> {
                            statusRequests++
                            if (mode == "cancel") throw CancellationException("cancel image polling")
                            respond("""{"status":"$mode"}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                        }
                        else -> error("Unexpected upload request")
                    }
                },
            ) { installZhihuCommonClientConfig(mutableMapOf(), "upload-contract-test") }
            try {
                if (mode == "cancel") {
                    assertFailsWith<CancellationException> {
                        uploadZhihuImage(environment(client), byteArrayOf(1), "image/png", "test.png", ZhihuImageUploadSource.Comment)
                    }
                } else {
                    val error = assertFailsWith<IllegalStateException> {
                        uploadZhihuImage(environment(client), byteArrayOf(1), "image/png", "test.png", ZhihuImageUploadSource.Comment)
                    }
                    assertTrue(error.message.orEmpty().contains(if (mode == "pending") "超时" else "upload_fail"))
                }
                assertEquals(if (mode == "pending") 10 else 1, statusRequests)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun invalidImageBytesAndFormatNeverReachTheServer() = runTest {
        var requests = 0
        val client = HttpClient(
            MockEngine {
                requests++
                error("No image request expected")
            },
        )
        try {
            assertFailsWith<IllegalStateException> { uploadZhihuImage(environment(client), byteArrayOf(), "image/png", "test.png", ZhihuImageUploadSource.Comment) }
            assertFailsWith<UnknownImageFormatException> { uploadZhihuImage(environment(client), byteArrayOf(1), "application/pdf", "test.pdf", ZhihuImageUploadSource.Comment) }
            assertEquals(0, requests)
        } finally {
            client.close()
        }
    }

    @Test
    fun duplicateStickerIsNotReportedAsANewUpload() = runTest {
        val client = HttpClient(
            MockEngine {
                respond("""{"code":40001,"message":"该表情已添加"}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
        try {
            assertEquals("该表情已添加", assertFailsWith<StickerAlreadyCollectedException> { uploadCommentSticker(environment(client), "v2-server-token") }.message)
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
