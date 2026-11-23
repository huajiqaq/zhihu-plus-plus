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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** WebDAV 备份范围：本机凭据不进备份；底栏项目和通知设置随备份走；旧备份没有这两项时不动本机设置。 */
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

    /** 设备 A 上传，设备 B 恢复：底栏项目（字符串集合）和通知设置（单独的存储）都要到 B 上。 */
    @Test
    fun bottomBarItemsAndNotificationSettingsTravelWithTheBackup() = withWebDav { server, database ->
        val deviceA = MapSettingsStore(
            mutableMapOf(
                "webdav_url" to "https://dav.example/backup/",
                BOTTOM_BAR_KEY to linkedSetOf("Home", "Daily", "Account"),
                "bottom_bar_item_order" to "Daily,Home,Account",
            ),
        )
        val notificationsA = MemoryNotificationSettings().apply {
            setAutoMarkAsReadEnabled(false)
            setUnreadBadgeEnabled(false)
            setSystemNotificationEnabled(NotificationType.REPLY_COMMENT, true)
            setDisplayInAppEnabled(NotificationType.LIKE_ANSWER, false)
        }
        val uploader = WebDavSyncViewModel(deviceA, database, notificationsA, setOf(BOTTOM_BAR_KEY), server.client)
        uploader.upload()
        uploader.awaitIdle()
        val body = server.uploaded["settings.json"] ?: error("设置没有上传：${uploader.status}")
        // 旧版本按旧结构解码：新字段被忽略；底栏集合不在 values 里，旧版不会把它当字符串写回。
        val legacy = Json { ignoreUnknownKeys = true }.decodeFromString(LegacySettingsBackup.serializer(), body)
        assertFalse(BOTTOM_BAR_KEY in legacy.values, "底栏项目不能混进旧版本也会恢复的 values：$body")
        assertEquals("Daily,Home,Account", legacy.values["bottom_bar_item_order"]?.content)

        server.remoteSettings = body
        val deviceB = MapSettingsStore(mutableMapOf("webdav_url" to "https://dav.example/backup/"))
        val notificationsB = MemoryNotificationSettings()
        val restorer = WebDavSyncViewModel(deviceB, database, notificationsB, setOf(BOTTOM_BAR_KEY), server.client)
        restorer.restore()
        restorer.awaitIdle()
        assertFalse(restorer.isStatusError, "恢复失败：${restorer.status}")
        assertEquals(setOf("Home", "Daily", "Account"), deviceB.getStringSet(BOTTOM_BAR_KEY, emptySet()), "底栏项目要随备份恢复")
        assertEquals("Daily,Home,Account", deviceB.getString("bottom_bar_item_order", ""))
        assertFalse(notificationsB.getAutoMarkAsReadEnabled(), "通知设置要随备份恢复")
        assertFalse(notificationsB.getUnreadBadgeEnabled())
        assertTrue(notificationsB.getSystemNotificationEnabled(NotificationType.REPLY_COMMENT))
        assertFalse(notificationsB.getDisplayInAppEnabled(NotificationType.LIKE_ANSWER))
    }

    @Test
    fun oldBackupWithoutTheNewFieldsLeavesLocalBottomBarAndNotificationsAlone() = withWebDav { server, database ->
        // 旧版本的备份：没有 stringSets / notifications；旧桌面端还把底栏集合存成了 values 里的拼接字符串。
        server.remoteSettings = """{"version":1,"exportTime":0,"values":{"bottom_bar_items":"Home\u001FAccount","readingFontSize":120}}"""
        val settings = MapSettingsStore(
            mutableMapOf(
                "webdav_url" to "https://dav.example/backup/",
                BOTTOM_BAR_KEY to linkedSetOf("Home", "Follow", "Daily"),
                "readingFontSize" to 100,
            ),
        )
        val notifications = MemoryNotificationSettings().apply {
            setAutoMarkAsReadEnabled(false)
            setSystemNotificationEnabled(NotificationType.LIKE_ANSWER, true)
        }
        val model = WebDavSyncViewModel(settings, database, notifications, setOf(BOTTOM_BAR_KEY), server.client)
        model.restore()
        model.awaitIdle()
        assertFalse(model.isStatusError, "旧备份要能正常恢复：${model.status}")
        assertEquals(120, settings.getInt("readingFontSize", 0), "旧备份里的普通设置照常恢复")
        assertEquals(setOf("Home", "Follow", "Daily"), settings.getStringSet(BOTTOM_BAR_KEY, emptySet()), "旧备份没有底栏项目，本机设置不变")
        assertFalse(notifications.getAutoMarkAsReadEnabled(), "旧备份没有通知设置，本机设置不变")
        assertTrue(notifications.getSystemNotificationEnabled(NotificationType.LIKE_ANSWER))
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

    /** 记下上传的文件；下载 settings.json 时返回 [remoteSettings]，其余文件返回上传过的内容。 */
    private class FakeWebDav {
        val uploaded = ConcurrentHashMap<String, String>()

        /** 每个请求带上的认证头，没带记空串。 */
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

    /** 旧版本解析 settings.json 时用的结构：只认识 values。 */
    @Serializable
    private data class LegacySettingsBackup(
        val version: Int = 1,
        val exportTime: Long = 0,
        val values: Map<String, JsonPrimitive> = emptyMap(),
    )

    private companion object {
        const val BOTTOM_BAR_KEY = "bottom_bar_items"

        /** fixture:fixture-password 按 RFC 7617 编码的结果，由标准库之外的实现算出。 */
        const val FIXTURE_BASIC_AUTH = "Basic Zml4dHVyZTpmaXh0dXJlLXBhc3N3b3Jk"
    }
}
