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

import android.content.Context
import android.content.ContextWrapper
import com.github.zly2006.zhihu.account.ZhihuAccountRepository
import com.github.zly2006.zhihu.account.ZhihuAccountSession
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.account.replaceAndroidZhihuAccountStoreForTesting
import com.github.zly2006.zhihu.data.ZhihuJson
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class EmojiManagerTest {
    @Test
    fun signedAccountRequestsRefreshByRemoteVersionAndPreserveCacheOnCancellation() = runBlocking {
        resetInitialization()
        val directory = Files.createTempDirectory("emoji-http-test-").toFile()
        val context = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this

            override fun getFilesDir(): File = directory
        }
        val groupsUrl = "https://www.zhihu.com/api/v4/me/sticker-groups"
        val detailUrl = "https://www.zhihu.com/api/v4/sticker-groups/1114161698310770688"
        val requests = mutableListOf<String>()
        var remoteVersion = 11
        var detailVersion = 12
        var failGroups = false
        var cancelledUrl: String? = null
        var replaceOwner: (() -> Unit)? = null
        val engine = MockEngine { request ->
            val url = request.url.toString()
            requests += url
            assertEquals(HttpMethod.Get, request.method)
            assertTrue(url == groupsUrl || url == detailUrl)
            assertEquals("emoji-http-test", request.headers[HttpHeaders.UserAgent])
            assertTrue(request.headers[HttpHeaders.Cookie].orEmpty().contains("d_c0=synthetic-emoji-device"))
            assertEquals("101_3_3.0", request.headers["x-zse-93"])
            assertTrue(request.headers["x-zse-96"].orEmpty().startsWith("2.0_"))
            val timeout = checkNotNull(request.getCapabilityOrNull(HttpTimeoutCapability))
            assertEquals(10_000L, timeout.connectTimeoutMillis)
            assertEquals(10_000L, timeout.socketTimeoutMillis)
            if (url == cancelledUrl) throw CancellationException("synthetic emoji cancellation")
            replaceOwner?.invoke()
            respond(
                if (url == groupsUrl) {
                    """{"data":[{"type":"sticker","version":99},{"type":"emoji","version":$remoteVersion}]}"""
                } else {
                    """{"data":{"version":$detailVersion,"stickers":[{"id":"123","placeholder":"[测试]","static_image_url":"https://example.invalid/emoji.png"}]}}"""
                },
                status = if (url == groupsUrl && failGroups) HttpStatusCode.ServiceUnavailable else HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val store = ZhihuAccountStore(ZhihuAccountRepository(Path(directory.path, "account.json")), engine)
        try {
            store.replaceSession(
                ZhihuAccountSession(
                    login = true,
                    username = "synthetic-emoji-account",
                    cookies = mutableMapOf("d_c0" to "synthetic-emoji-device"),
                    userAgent = "emoji-http-test",
                ),
            )
            replaceAndroidZhihuAccountStoreForTesting(store)
            val mapping = File(directory, "emoji_mapping.json").apply { writeText("""{"[旧表情]":"emoji_123.png"}""") }
            val version = File(directory, "emoji_version.txt").apply { writeText("1") }
            val emoji = File(directory, "emojis/emoji_123.png").apply {
                check(parentFile!!.mkdirs())
                writeBytes(byteArrayOf(1, 2, 3))
            }
            EmojiManager.initialize(context)
            assertEquals(listOf(groupsUrl, detailUrl), requests)
            assertEquals("12", version.readText())
            assertEquals(
                mapOf("[测试]" to "emoji_123.png"),
                ZhihuJson.json.decodeFromString<Map<String, String>>(mapping.readText()),
            )
            assertEquals(listOf("[测试]"), EmojiManager.placeholders.value)
            EmojiManager.initialize(context)
            assertEquals(2, requests.size)

            remoteVersion = 12
            resetInitialization()
            requests.clear()
            EmojiManager.initialize(context)
            assertEquals(listOf(groupsUrl), requests)
            assertEquals(emoji.absolutePath, EmojiManager.getEmojiPath("[测试]"))
            val savedMapping = mapping.readText()

            failGroups = true
            resetInitialization()
            requests.clear()
            EmojiManager.initialize(context)
            assertEquals(listOf(groupsUrl), requests)
            assertEquals(savedMapping, mapping.readText())
            assertEquals("12", version.readText())
            assertEquals(listOf("[测试]"), EmojiManager.placeholders.value)

            failGroups = false
            remoteVersion = 13
            for (url in listOf(groupsUrl, detailUrl)) {
                resetInitialization()
                requests.clear()
                cancelledUrl = url
                try {
                    EmojiManager.initialize(context)
                    throw AssertionError("表情请求取消必须向调用方传播")
                } catch (_: CancellationException) {
                    assertEquals(if (url == groupsUrl) listOf(groupsUrl) else listOf(groupsUrl, detailUrl), requests)
                    assertEquals(savedMapping, mapping.readText())
                    assertEquals("12", version.readText())
                }
            }
            cancelledUrl = null
            detailVersion = 14
            requests.clear()
            EmojiManager.initialize(context)
            assertEquals(listOf(groupsUrl, detailUrl), requests)
            assertEquals("14", version.readText())

            resetInitialization()
            requests.clear()
            replaceOwner = { store.replaceSession(store.session.copy(username = "replacement-emoji-account")) }
            try {
                EmojiManager.initialize(context)
                throw AssertionError("旧账号的分组响应不能继续刷新缓存")
            } catch (_: CancellationException) {
                assertEquals(listOf(groupsUrl), requests)
                assertEquals(savedMapping, mapping.readText())
                assertEquals("14", version.readText())
            }
        } finally {
            resetInitialization()
            replaceAndroidZhihuAccountStoreForTesting(null)
            store.close()
            check(directory.deleteRecursively())
        }
    }

    private fun resetInitialization() {
        EmojiManager.javaClass
            .getDeclaredField("isInitialized")
            .apply { isAccessible = true }
            .setBoolean(EmojiManager, false)
    }
}
