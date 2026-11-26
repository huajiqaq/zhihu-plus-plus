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
import com.github.zly2006.zhihu.util.ZHIHU_WEB_ZSE93
import com.github.zly2006.zhihu.util.ZhihuFetchSignature
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.http.parseClientCookiesHeader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class VoiceUploadRequestRegressionTest {
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
}
