package com.github.zly2006.zhihu.editor

import com.github.zly2006.zhihu.data.installZhihuCommonClientConfig
import com.github.zly2006.zhihu.util.ZhihuFetchSignature
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
                    else -> error("Unexpected upload request")
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
            assertFalse(detailsRequested, "Official comment uploads do not wait for playback/ASR details")
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
        val alternate = HttpClient(MockEngine { error("Upload must remain on its captured session") })
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
                    else -> error("Unexpected upload request")
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
    fun emptyAudioAndInvalidDurationDoNotCreateLensSessions() = runTest {
        var requests = 0
        val client = HttpClient(
            MockEngine {
                requests++
                error("No upload expected")
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
