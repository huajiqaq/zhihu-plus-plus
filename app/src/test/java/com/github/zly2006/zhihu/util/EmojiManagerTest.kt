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
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class EmojiManagerTest {
    @Test
    fun signedAccountRequestDecodesAndPersistsTheExistingCacheFormat() = runBlocking {
        val directory = Files.createTempDirectory("emoji-http-test-").toFile()
        val context = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this

            override fun getFilesDir(): File = directory
        }
        var requests = 0
        val engine = MockEngine { request ->
            requests++
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("https://www.zhihu.com/api/v4/sticker-groups/1114161698310770688", request.url.toString())
            assertEquals("emoji-http-test", request.headers[HttpHeaders.UserAgent])
            assertTrue(request.headers[HttpHeaders.Cookie].orEmpty().contains("d_c0=synthetic-emoji-device"))
            assertEquals("101_3_3.0", request.headers["x-zse-93"])
            assertTrue(request.headers["x-zse-96"].orEmpty().startsWith("2.0_"))
            val timeout = checkNotNull(request.getCapabilityOrNull(HttpTimeoutCapability))
            assertEquals(10_000L, timeout.connectTimeoutMillis)
            assertEquals(10_000L, timeout.socketTimeoutMillis)
            respond(
                """{"data":{"stickers":[{"id":"123","placeholder":"[测试]","static_image_url":"https://example.invalid/emoji.png"}]}}""",
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
            EmojiManager.initialize(context)
            assertEquals(1, requests)
            assertEquals("1", File(directory, "emoji_version.txt").readText())
            assertEquals(
                mapOf("[测试]" to "emoji_123.png"),
                ZhihuJson.json.decodeFromString<Map<String, String>>(File(directory, "emoji_mapping.json").readText()),
            )
            assertEquals(listOf("[测试]"), EmojiManager.placeholders.value)
            EmojiManager.initialize(context)
            assertEquals(1, requests)
        } finally {
            replaceAndroidZhihuAccountStoreForTesting(null)
            store.close()
            check(directory.deleteRecursively())
        }
    }
}
