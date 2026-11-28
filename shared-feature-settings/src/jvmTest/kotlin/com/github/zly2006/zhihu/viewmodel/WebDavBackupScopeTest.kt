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
import com.github.zly2006.zhihu.platform.SettingValue
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.desktopSettingsStore
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout

/** WebDAV 上传和恢复不能泄漏或覆盖本机凭据。 */
@OptIn(ExperimentalCoroutinesApi::class)
class WebDavBackupScopeTest {
    @Test
    fun uploadAndRestoreKeepLocalCredentialsOnThisDevice() = withWebDav { server, database ->
        server.remoteSettings = """{"version":1,"exportTime":0,"entries":{"githubToken":{"type":"string","value":"remote-token"},"webdav_url":{"type":"string","value":"https://evil.example/dav/"},"contentFontSize":{"type":"int","value":120}}}"""
        val settings = MapSettingsStore().apply {
            putString("githubToken", "local-token")
            putString("webdav_url", "https://old.example/")
            putString("webdav_username", "old-user")
            putString("webdav_password", "old-password")
            putInt("contentFontSize", 100)
        }
        val model = WebDavSyncViewModel(settings, database, MemoryNotificationSettings(), server.client)
        model.url.setTextAndPlaceCursorAtEnd("dav://fixture:fixture-password@dav.example/backup/")
        model.upload()
        assertEquals("https://dav.example/backup/", model.url.text.toString(), "立即上传也必须先拆分地址")
        assertFalse(model.url.undoState.canUndo, "明文地址栏不能撤销回带凭据的链接")
        model.awaitIdle()
        val body = server.uploaded["settings.json"] ?: error("设置没有上传：${model.status}")
        assertFalse("githubToken" in body, "GitHub Token 不应随设置上传：$body")
        assertFalse("webdav" in body, "WebDAV 连接信息不应随设置上传：$body")
        assertTrue("contentFontSize" in body, "普通设置仍要上传：$body")

        model.url.setTextAndPlaceCursorAtEnd("davs://dav.example/backup/")
        model.normalizeAddress("dav://stale:stale-password@old.example/")
        model.restore()
        model.awaitIdle()
        assertEquals("local-token", settings.getString("githubToken", ""), "恢复不能覆盖本机 GitHub Token：${model.status}")
        assertEquals("https://dav.example/backup/", settings.getString("webdav_url", ""), "恢复不能覆盖本机 WebDAV 地址")
        assertEquals(120, settings.getInt("contentFontSize", 0), "普通设置仍要恢复：${model.status}")
        assertEquals(setOf(FIXTURE_BASIC_AUTH), server.authorizations.toSet(), "上传和恢复的每个请求都带标准 Basic 认证头")
    }

    @Test
    fun browserConnectionCredentialsRemainInPageState() = withWebDav { server, database ->
        val settings = MapSettingsStore().apply { putInt("contentFontSize", 100) }
        val model = WebDavSyncViewModel(settings, database, MemoryNotificationSettings(), server.client, persistConnectionConfig = false)
        model.url.setTextAndPlaceCursorAtEnd("dav://fixture:fixture-password@dav.example/backup/")
        model.upload()
        model.awaitIdle()
        model.saveConfig()
        assertFalse(model.isStatusError, model.status)
        assertEquals("https://dav.example/backup/", model.url.text.toString())
        assertEquals(setOf(FIXTURE_BASIC_AUTH), server.authorizations.toSet())
        assertFalse(settings.snapshot().keys.any { it.startsWith("webdav") })
        assertFalse("fixture-password" in server.uploaded.getValue("settings.json"))
    }

    @Test
    fun restoreRejectsUnknownVersionsAndMissingTypedEntries() = withWebDav { server, database ->
        val settings = MapSettingsStore().apply { putInt("contentFontSize", 100) }
        val model = WebDavSyncViewModel(settings, database, MemoryNotificationSettings(), server.client)
        model.url.setTextAndPlaceCursorAtEnd("https://dav.example/backup/")
        listOf(
            """{"version":999,"exportTime":0,"entries":{}}""",
            """{"version":1,"exportTime":0}""",
            """{"exportTime":0,"entries":{}}""",
            """{"values":{"contentFontSize":120}}""",
        ).forEach { invalid ->
            server.remoteSettings = invalid
            model.restore()
            model.awaitIdle()
            assertTrue(model.isStatusError, "无效备份不能被当作空恢复成功")
            assertEquals(100, settings.getInt("contentFontSize", 0))
        }
    }

    @Test
    fun typedBackupRoundTripsDesktopPropertiesAndLegacyValuesWithoutGuessingUnknownStrings() = withWebDav { server, database ->
        val home = createTempDirectory("settings-roundtrip").toFile()
        val previousHome = System.getProperty("user.home")
        val directory = home.resolve(".zhihu-plus").apply { mkdirs() }
        // 合成旧格式文件，不读取用户设置；两种桌面后端都必须保留这些值。
        directory.resolve("settings.properties").writeText(
            "useDynamicColor=false\ncustomThemeColor=-123\nlastRemoteHistorySync=7\nbottom_bar_items=Home\\u001FAccount\nunknownCode=001\nprefixText=@zhpp:setting:1:ordinary text\n",
        )
        try {
            System.setProperty("user.home", home.absolutePath)
            val desktop = desktopSettingsStore()
            desktop.putString("contentFontSize", "001")
            assertEquals(SettingValue.StringValue("001"), desktop.snapshot()["contentFontSize"], "新值显式类型不能被旧键迁移重写")
            val batch = mapOf(
                "unknownLong" to SettingValue.LongValue(7L),
                "unknownFloat" to SettingValue.FloatValue((-0.0f).toRawBits()),
                "unknownNaN" to SettingValue.FloatValue(Float.NaN.toRawBits()),
                "emptySet" to SettingValue.StringSetValue(emptySet()),
                "separatorSet" to SettingValue.StringSetValue(setOf("", "a\u001Fb", "中文")),
            )
            val expected = desktop.snapshot() + batch
            val observed = mutableListOf<Map<String, SettingValue>>()
            val subscription = desktop.observeKeyChanges { observed += desktop.snapshot() }
            try {
                desktop.restore(batch)
            } finally {
                subscription.close()
            }
            assertEquals(List(batch.size) { expected }, observed, "批量恢复的每次通知必须看到完整最终快照")
            assertEquals(SettingValue.BooleanValue(false), desktop.snapshot()["useDynamicColor"])
            assertEquals(SettingValue.LongValue(7L), desktop.snapshot()["lastRemoteHistorySync"])
            assertEquals(SettingValue.StringValue("001"), desktop.snapshot()["unknownCode"])
            assertEquals("@zhpp:setting:1:ordinary text", desktop.getString("prefixText", ""))
            val reopenedHome = createTempDirectory("settings-reopened").toFile()
            try {
                val reopenedDirectory = reopenedHome.resolve(".zhihu-plus").apply { mkdirs() }
                directory.resolve("settings.properties").copyTo(reopenedDirectory.resolve("settings.properties"))
                System.setProperty("user.home", reopenedHome.absolutePath)
                assertEquals(desktop.snapshot(), desktopSettingsStore().snapshot(), "新文件重读必须保留类型与普通字符串前缀")
            } finally {
                System.setProperty("user.home", home.absolutePath)
                reopenedHome.deleteRecursively()
            }
            val writer = WebDavSyncViewModel(desktop, database, MemoryNotificationSettings(), server.client)
            writer.url.setTextAndPlaceCursorAtEnd("https://dav.example/backup/")
            writer.upload()
            writer.awaitIdle()
            assertFalse(writer.isStatusError, writer.status)
            val backup = server.uploaded.getValue("settings.json")
            server.remoteSettings = backup
            val restored = MapSettingsStore()
            val reader = WebDavSyncViewModel(restored, database, MemoryNotificationSettings(), server.client)
            reader.url.setTextAndPlaceCursorAtEnd("https://dav.example/backup/")
            reader.restore()
            reader.awaitIdle()
            assertFalse(reader.isStatusError, reader.status)
            fun SettingsStore.sharedEntries() = snapshot().filterKeys { !it.startsWith("webdav") }
            assertEquals(desktop.sharedEntries(), restored.sharedEntries())
            assertEquals(7L, restored.getLong("unknownLong", 0L))
            assertEquals(setOf("Home", "Account"), restored.getStringSet(BOTTOM_BAR_KEY, emptySet()))
            assertEquals(setOf("", "a\u001Fb", "中文"), restored.getStringSet("separatorSet", emptySet()))
            assertEquals((-0.0f).toRawBits(), restored.getFloat("unknownFloat", 1f).toRawBits())
            assertTrue(restored.getFloat("unknownNaN", 0f).isNaN())
            restored.putString("customThemeColor", "-456")
            assertEquals(0, restored.getInt("customThemeColor", 0), "显式文本不再被数字getter猜类型")
            restored.putString("customThemeColor", "not-a-color")
            assertEquals(42, restored.getInt("customThemeColor", 42))
        } finally {
            System.setProperty("user.home", previousHome)
            home.deleteRecursively()
        }
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
            followRedirects = false
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
