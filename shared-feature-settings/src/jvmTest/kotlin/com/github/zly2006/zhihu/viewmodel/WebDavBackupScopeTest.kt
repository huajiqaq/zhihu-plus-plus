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

package com.github.zly2006.zhihu.viewmodel

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.github.zly2006.zhihu.notification.NotificationSettingsStore
import com.github.zly2006.zhihu.notification.NotificationType
import com.github.zly2006.zhihu.platform.MapSettingsStore
import com.github.zly2006.zhihu.viewmodel.filter.ContentFilterDatabase
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** WebDAV 上传和恢复不能泄漏或覆盖本机凭据。 */
@OptIn(ExperimentalCoroutinesApi::class)
class WebDavBackupScopeTest {
    @Test
    fun uploadAndRestoreKeepLocalCredentialsOnThisDevice() = withWebDav { server, database ->
        server.remoteSettings = """{"values":{"githubToken":"remote-token","webdav_url":"https://evil.example/dav/","readingFontSize":120}}"""
        val settings = MapSettingsStore(
            mutableMapOf(
                "githubToken" to "local-token",
                "webdav_url" to "https://old.example/",
                "webdav_username" to "old-user",
                "webdav_password" to "old-password",
                "readingFontSize" to 100,
            ),
        )
        val model = WebDavSyncViewModel(settings, database, MemoryNotificationSettings(), setOf(BOTTOM_BAR_KEY), server.client)
        model.url.setTextAndPlaceCursorAtEnd("dav://fixture:fixture-password@dav.example/backup/")
        model.upload()
        assertEquals("https://dav.example/backup/", model.url.text.toString(), "立即上传也必须先拆分地址")
        assertFalse(model.url.undoState.canUndo, "明文地址栏不能撤销回带凭据的链接")
        model.awaitIdle()
        val body = server.uploaded["settings.json"] ?: error("设置没有上传：${model.status}")
        assertFalse("githubToken" in body, "GitHub Token 不应随设置上传：$body")
        assertFalse("webdav" in body, "WebDAV 连接信息不应随设置上传：$body")
        assertTrue("readingFontSize" in body, "普通设置仍要上传：$body")

        model.url.setTextAndPlaceCursorAtEnd("davs://dav.example/backup/")
        model.normalizeAddress("dav://stale:stale-password@old.example/")
        model.restore()
        model.awaitIdle()
        assertEquals("local-token", settings.getString("githubToken", ""), "恢复不能覆盖本机 GitHub Token：${model.status}")
        assertEquals("https://dav.example/backup/", settings.getString("webdav_url", ""), "恢复不能覆盖本机 WebDAV 地址")
        assertEquals(120, settings.getInt("readingFontSize", 0), "普通设置仍要恢复：${model.status}")
        assertEquals(setOf(FIXTURE_BASIC_AUTH), server.authorizations.toSet(), "上传和恢复的每个请求都带标准 Basic 认证头")
    }

    private suspend fun WebDavSyncViewModel.awaitIdle() = withTimeout(15_000) {
        while (isRunning) delay(20)
    }

    private fun withWebDav(block: suspend (FakeWebDav, ContentFilterDatabase) -> Unit) {
        // Room 查询跑在自己的执行器上，虚拟时间推进不到它；这里用真实线程和超时等待请求结束。
        val executor = Executors.newSingleThreadExecutor()
        Dispatchers.setMain(executor.asCoroutineDispatcher())
        val server = FakeWebDav()
        val database = getContentFilterDatabase(createTempDirectory("webdav-scope").resolve("content-filter.db").toFile())
        try {
            runBlocking { block(server, database) }
        } finally {
            database.close()
            server.client.close()
            Dispatchers.resetMain()
            executor.shutdown()
        }
    }

    private class FakeWebDav {
        val uploaded = ConcurrentHashMap<String, String>()

        val authorizations: MutableSet<String> = ConcurrentHashMap.newKeySet()

        @Volatile
        var remoteSettings: String? = null

        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    authorizations += request.headers[HttpHeaders.Authorization].orEmpty()
                    val file = request.url.encodedPath.substringAfterLast('/')
                    when (request.method) {
                        HttpMethod.Put -> {
                            uploaded[file] = request.body.toByteArray().decodeToString()
                            respond("", HttpStatusCode.Created)
                        }
                        HttpMethod.Get -> {
                            val body = if (file == "settings.json") remoteSettings else uploaded[file]
                            if (body == null) {
                                respond("", HttpStatusCode.NotFound)
                            } else {
                                respond(body, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
                            }
                        }
                        else -> respond("", HttpStatusCode.Created)
                    }
                }
            }
        }
    }

    private class MemoryNotificationSettings : NotificationSettingsStore {
        private val system = mutableMapOf<NotificationType, Boolean>()
        private val inApp = mutableMapOf<NotificationType, Boolean>()
        private var autoMarkAsRead = true
        private var unreadBadge = true

        override fun getSystemNotificationEnabled(type: NotificationType) = system[type] ?: false

        override fun setSystemNotificationEnabled(type: NotificationType, enabled: Boolean) {
            system[type] = enabled
        }

        override fun getDisplayInAppEnabled(type: NotificationType) = inApp[type] ?: type.defaultValue

        override fun setDisplayInAppEnabled(type: NotificationType, enabled: Boolean) {
            inApp[type] = enabled
        }

        override fun getAutoMarkAsReadEnabled() = autoMarkAsRead

        override fun setAutoMarkAsReadEnabled(enabled: Boolean) {
            autoMarkAsRead = enabled
        }

        override fun getUnreadBadgeEnabled() = unreadBadge

        override fun setUnreadBadgeEnabled(enabled: Boolean) {
            unreadBadge = enabled
        }
    }

    private companion object {
        const val BOTTOM_BAR_KEY = "bottom_bar_items"

        /** fixture:fixture-password 按 RFC 7617 编码的结果，由标准库之外的实现算出。 */
        const val FIXTURE_BASIC_AUTH = "Basic Zml4dHVyZTpmaXh0dXJlLXBhc3N3b3Jk"
    }
}
