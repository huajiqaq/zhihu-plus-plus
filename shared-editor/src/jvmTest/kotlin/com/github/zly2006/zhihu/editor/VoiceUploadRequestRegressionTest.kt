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

import com.github.zly2006.zhihu.account.ZhihuAccountRepository
import com.github.zly2006.zhihu.account.ZhihuAccountSession
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.data.installZhihuCommonClientConfig
import com.github.zly2006.zhihu.util.ZHIHU_WEB_ZSE93
import com.github.zly2006.zhihu.util.ZhihuFetchSignature
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.http.parseClientCookiesHeader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class VoiceUploadRequestRegressionTest {
    @Test
    fun successfulObjectUploadReportsPutSuccessWithoutWaitingForPlaylist() = runTest {
        val events = mutableListOf<Pair<HttpMethod, String>>()
        var detailsRequested = false
        val client = HttpClient(
            MockEngine { request ->
                val path = request.url.encodedPath
                val body = (request.body as? TextContent)?.text.orEmpty()
                val content = when {
                    path == "/api/v4/videos" -> {
                        assertEquals(HttpMethod.Post, request.method)
                        assertEquals("audio/mp4", request.headers["X-Upload-Conent-Type"])
                        assertEquals(
                            Json.parseToJsonElement("""{"file_md5":"${ZhihuFetchSignature.md5Hex(byteArrayOf(1, 2, 3))}","file_size":3,"source":"comment","duration_ms":1500}"""),
                            Json.parseToJsonElement(body),
                        )
                        """{"upload_file":{"video_id":"voice-1","object_key":"comment/voice.m4a","state":2},"upload_token":{"access_id":"id","access_key":"key","access_token":"token"}}"""
                    }
                    request.url.host == "upload-oss.vzuu.com" -> ""
                    path.endsWith("/uploading_status") -> {
                        events += request.method to Json
                            .parseToJsonElement(body)
                            .jsonObject
                            .getValue("upload_event")
                            .jsonPrimitive.content
                        "{}"
                    }
                    path == "/api/v4/videos/voice-1" -> {
                        detailsRequested = true
                        """{"playlist":null}"""
                    }
                    else -> error("不应发起上传请求")
                }
                respond(content, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ) { installZhihuCommonClientConfig(mutableMapOf(), "voice-contract-test") }
        val environment = object : ZhihuApiEnvironment {
            override fun httpClient() = client

            override fun authenticatedCookies() = emptyMap<String, String>()

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
        }
        try {
            assertEquals("voice-1", uploadVoiceComment(environment, byteArrayOf(1, 2, 3), 1500).voiceId)
            assertEquals(listOf(HttpMethod.Put to "UPLOADING_START", HttpMethod.Put to "UPLOADING_SUCCESS"), events)
            assertFalse(detailsRequested, "官方评论上传不等待播放或语音识别详情")
        } finally {
            client.close()
        }
    }

    @Test
    fun cancelledStatusReportMustNotContinueUploading() = runTest {
        var objectUploaded = false
        val client = HttpClient(
            MockEngine { request ->
                when {
                    request.url.encodedPath == "/api/v4/videos" -> respond("""{"upload_file":{"video_id":"voice-2","object_key":"comment/voice.m4a","state":2},"upload_token":{"access_id":"id","access_key":"key","access_token":"token"}}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    request.url.encodedPath.endsWith("/uploading_status") -> throw CancellationException("Upload cancelled")
                    request.url.host == "upload-oss.vzuu.com" -> {
                        objectUploaded = true
                        respond("", HttpStatusCode.OK)
                    }
                    else -> respond("""{"playlist":null}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            },
        ) { installZhihuCommonClientConfig(mutableMapOf(), "voice-contract-test") }
        val environment = object : ZhihuApiEnvironment {
            override fun httpClient() = client

            override fun authenticatedCookies() = emptyMap<String, String>()

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
        }
        try {
            assertFailsWith<CancellationException> { uploadVoiceComment(environment, byteArrayOf(1, 2, 3), 1500) }
            assertFalse(objectUploaded)
        } finally {
            client.close()
        }
    }

    @Test
    fun uploadedObjectSurvivesTelemetryFailuresWithoutSwitchingClients() = runTest {
        var current: HttpClient? = null
        val alternate = HttpClient(MockEngine { error("上传必须使用已捕获的会话") })
        val requests = mutableListOf<String>()
        val events = mutableListOf<String>()
        val client = HttpClient(
            MockEngine { request ->
                requests += "${request.method.value} ${request.url.host}${request.url.encodedPath}"
                when {
                    request.url.encodedPath == "/api/v4/videos" -> {
                        current = alternate
                        respond("""{"upload_file":{"video_id":"voice-bound","object_key":"comment/voice.m4a","state":2},"upload_id":"upload-bound","upload_token":{"access_id":"oss-id","access_key":"oss-key","access_token":"oss-token"}}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                    request.url.encodedPath.endsWith("/uploading_status") -> {
                        val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                        assertEquals("voice-bound", body.getValue("video_id").jsonPrimitive.content)
                        assertEquals("comment/voice.m4a", body.getValue("object_key").jsonPrimitive.content)
                        assertEquals("upload-bound", body.getValue("upload_id").jsonPrimitive.content)
                        assertEquals("comment", body.getValue("video_source").jsonPrimitive.content)
                        events += body.getValue("upload_event").jsonPrimitive.content
                        respond("{}", HttpStatusCode.ServiceUnavailable, headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                    request.url.host == "upload-oss.vzuu.com" -> {
                        assertEquals(HttpMethod.Put, request.method)
                        assertEquals("/comment/voice.m4a", request.url.encodedPath)
                        assertTrue(request.headers[HttpHeaders.Authorization].orEmpty().startsWith("OSS oss-id:"))
                        assertEquals("oss-token", request.headers["x-oss-security-token"])
                        assertEquals("aliyun-sdk-android/2.9.5", request.headers["x-oss-user-agent"])
                        assertEquals(null, request.headers[HttpHeaders.Cookie])
                        respond("")
                    }
                    else -> error("不应发起上传请求")
                }
            },
        ) { installZhihuCommonClientConfig(mutableMapOf("d_c0" to "synthetic-voice-session"), "voice-contract-test") }
        current = client
        val environment = object : ZhihuApiEnvironment {
            override fun httpClient() = checkNotNull(current)

            override fun authenticatedCookies() = mapOf("d_c0" to "synthetic-voice-session")

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
        }
        try {
            assertEquals("voice-bound", uploadVoiceComment(environment, byteArrayOf(1, 2, 3), 1500).voiceId)
            assertEquals(listOf("UPLOADING_START", "UPLOADING_SUCCESS"), events)
            assertEquals(
                listOf(
                    "POST lens.zhihu.com/api/v4/videos",
                    "PUT lens.zhihu.com/api/v4/videos/voice-bound/uploading_status",
                    "PUT upload-oss.vzuu.com/comment/voice.m4a",
                    "PUT lens.zhihu.com/api/v4/videos/voice-bound/uploading_status",
                ),
                requests,
            )
        } finally {
            client.close()
            alternate.close()
        }
    }

    @Test
    fun signedVoiceUploadKeepsCookieSnapshotDuringSameAccountRefresh() = runTest {
        val originalCookies = mapOf("z_c0" to "synthetic-old-token", "d_c0" to "synthetic-old-device", "_xsrf" to "synthetic-old-xsrf")
        val refreshedCookies = mapOf("z_c0" to "synthetic-new-token", "d_c0" to "synthetic-new-device", "_xsrf" to "synthetic-new-xsrf")
        val signatureReady = CompletableDeferred<Unit>()
        val credentialsUpdated = CompletableDeferred<Unit>()
        var signingCookies = emptyMap<String, String>()
        var signatureBeforeSending: String? = null
        val directory = Files.createTempDirectory("voice-signed-cookie-contract-")
        try {
            val engine = MockEngine { request ->
                val content = when {
                    request.url.host == "lens.zhihu.com" && request.url.encodedPath == "/api/v4/videos" ->
                        """{"upload_file":{"video_id":"voice-snapshot","object_key":"comment/voice.m4a","state":2},"upload_id":"upload-snapshot","upload_token":{"access_id":"oss-id","access_key":"oss-key","access_token":"oss-token"}}"""
                    request.url.host == "lens.zhihu.com" && request.url.encodedPath == "/api/v4/videos/voice-snapshot/uploading_status" -> "{}"
                    request.url.host == "upload-oss.vzuu.com" && request.url.encodedPath == "/comment/voice.m4a" -> ""
                    else -> error("不应发起上传请求")
                }
                respond(content, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
            ZhihuAccountStore(ZhihuAccountRepository(Path(directory.toString(), "account.json")), engine).use { store ->
                store.save(
                    ZhihuAccountSession(
                        login = true,
                        username = "synthetic-voice-account",
                        userAgent = "voice-contract-test",
                        cookies = originalCookies.toMutableMap(),
                    ),
                )
                val owner = store.client
                val client = owner.httpClient()
                /** 签名已生成、HttpCookies 尚未读取发送值，确定性覆盖凭据刷新窗口。 */
                client.requestPipeline.intercept(HttpRequestPipeline.Before) {
                    if (context.url.host == "lens.zhihu.com" && context.url.build().encodedPath == "/api/v4/videos") {
                        signatureBeforeSending = checkNotNull(context.headers["x-zse-96"])
                        signatureReady.complete(Unit)
                        credentialsUpdated.await()
                    }
                }
                val environment = object : ZhihuApiEnvironment {
                    override val accountClient = owner

                    override suspend fun <T> withAuthenticatedClient(block: suspend (HttpClient, Map<String, String>) -> T): T =
                        owner.withAuthenticatedClient { boundClient, cookies ->
                            signingCookies = cookies
                            block(boundClient, cookies)
                        }

                    override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
                }
                try {
                    val upload = async { uploadVoiceComment(environment, byteArrayOf(1, 2, 3), 1500) }
                    signatureReady.await()
                    store.save(store.session.copy(cookies = refreshedCookies.toMutableMap()))
                    assertSame(owner, store.client)
                    credentialsUpdated.complete(Unit)
                    assertEquals("voice-snapshot", upload.await().voiceId)
                    assertEquals(refreshedCookies, store.session.cookies, "请求快照不得写回并覆盖已刷新的凭据")
                    assertEquals(originalCookies, signingCookies)
                    val requests = engine.requestHistory
                    assertEquals(
                        listOf(
                            "POST lens.zhihu.com/api/v4/videos",
                            "PUT lens.zhihu.com/api/v4/videos/voice-snapshot/uploading_status",
                            "PUT upload-oss.vzuu.com/comment/voice.m4a",
                            "PUT lens.zhihu.com/api/v4/videos/voice-snapshot/uploading_status",
                        ),
                        requests.map { "${it.method.value} ${it.url.host}${it.url.encodedPath}" },
                    )
                    val oss = requests.single { it.url.host == "upload-oss.vzuu.com" }
                    assertNull(oss.headers[HttpHeaders.Cookie])
                    assertNull(oss.headers["x-zse-93"])
                    assertNull(oss.headers["x-zse-96"])
                    assertTrue(oss.headers[HttpHeaders.Authorization].orEmpty().startsWith("OSS oss-id:"))
                    assertEquals("oss-token", oss.headers["x-oss-security-token"])
                    assertEquals(signatureBeforeSending, requests.first().headers["x-zse-96"])
                    for (request in requests.filter { it.url.host == "lens.zhihu.com" }) {
                        assertEquals(ZHIHU_WEB_ZSE93, request.headers["x-zse-93"])
                        assertTrue(
                            (0..126).any { seed ->
                                request.headers["x-zse-96"] == ZhihuFetchSignature.createZse96Header(
                                    ZHIHU_WEB_ZSE93,
                                    request.url.toString(),
                                    signingCookies.getValue("d_c0"),
                                    (request.body as TextContent).text,
                                    seed,
                                )
                            },
                            "签名必须匹配生产上传入口捕获的 Cookie、最终 URL 和正文",
                        )
                        assertEquals(
                            signingCookies,
                            parseClientCookiesHeader(request.headers[HttpHeaders.Cookie].orEmpty()),
                            "${request.method.value} ${request.url.encodedPath} 的签名与最终 Cookie 必须来自同一快照",
                        )
                    }
                } finally {
                    credentialsUpdated.complete(Unit)
                }
            }
        } finally {
            check(directory.toFile().deleteRecursively())
        }
    }

    @Test
    fun emptyAudioAndInvalidDurationDoNotCreateLensSessions() = runTest {
        var requests = 0
        val client = HttpClient(
            MockEngine {
                requests++
                error("不应发起上传请求")
            },
        )
        val environment = object : ZhihuApiEnvironment {
            override fun httpClient() = client

            override fun authenticatedCookies() = emptyMap<String, String>()

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
        }
        try {
            assertFailsWith<IllegalArgumentException> { uploadVoiceComment(environment, byteArrayOf(), 1500) }
            assertFailsWith<IllegalArgumentException> { uploadVoiceComment(environment, byteArrayOf(1), 0) }
            assertEquals(0, requests)
        } finally {
            client.close()
        }
    }
}
