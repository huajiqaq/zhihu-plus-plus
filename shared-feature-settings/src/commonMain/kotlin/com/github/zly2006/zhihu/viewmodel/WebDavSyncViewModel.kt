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

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.zly2006.zhihu.notification.NotificationSettingsStore
import com.github.zly2006.zhihu.notification.NotificationType
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.viewmodel.filter.ContentFilterDatabase
import com.github.zly2006.zhihu.viewmodel.filter.encodeBlocklistBackup
import com.github.zly2006.zhihu.viewmodel.filter.importBlocklistBackupFromJsonText
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import io.ktor.http.decodeURLPart
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.time.Clock

private const val BACKUP_DIRECTORY = "zhihu-plus-plus/"
private const val BLOCKLIST_FILE = "blocklist.json"
private const val SETTINGS_FILE = "settings.json"
private const val WEBDAV_URL_KEY = "webdav_url"
private const val WEBDAV_USERNAME_KEY = "webdav_username"
private const val WEBDAV_PASSWORD_KEY = "webdav_password"

/** 连接信息与第三方凭据只属于本机：上传时不带出，恢复时也不让备份覆盖。 */
private fun isLocalOnlySettingKey(key: String) = key.startsWith("webdav") || key == "githubToken"

private val webDavJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    encodeDefaults = true
}

data class ParsedWebDavAddress(
    val url: String,
    val username: String,
    val password: String,
)

private val webDavSchemeAlias = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*)://")

/**
 * 解析用户粘贴的 WebDAV 地址。各网盘（坚果云、Alist 等）分享的链接通常是
 * `dav://user:pass@host/path/` 一整条：这里把别名 scheme 归一为 https，并把
 * authority 段的 `user:pass@` 拆出成独立凭据，避免用户逐字段手填。
 * 只在 authority 段找 userinfo，路径里的 `@` 不受影响；密码可含 `:`。
 */
fun parseWebDavAddress(raw: String): ParsedWebDavAddress {
    val text = raw.trim()
    if (text.isEmpty()) return ParsedWebDavAddress("", "", "")
    val schemeMatch = webDavSchemeAlias.find(text) ?: return ParsedWebDavAddress(text, "", "")
    val scheme = when (schemeMatch.groupValues[1].lowercase()) {
        "dav", "davs", "webdav", "webdavs" -> "https"
        else -> schemeMatch.groupValues[1].lowercase()
    }
    val normalized = "$scheme://" + text.substring(schemeMatch.groupValues[0].length)
    val rest = normalized.substringAfter("://")
    val authority = rest.substringBefore('/')
    val tail = rest.removePrefix(authority)
    val userinfoEnd = authority.lastIndexOf('@')
    if (userinfoEnd <= 0) return ParsedWebDavAddress(normalized, "", "")
    val host = authority.substring(userinfoEnd + 1)
    if (host.isEmpty()) return ParsedWebDavAddress(normalized, "", "")
    val credentials = authority.substring(0, userinfoEnd)
    return ParsedWebDavAddress(
        url = "$scheme://$host$tail",
        username = credentials.substringBefore(':').decodeURLPart(),
        password = credentials.substringAfter(':', "").decodeURLPart(),
    )
}

/** 缺少集合或通知字段的备份不会覆盖对应设置。 */
@Serializable
private data class SettingsBackup(
    val version: Int = 1,
    val exportTime: Long = Clock.System.now().toEpochMilliseconds(),
    val values: Map<String, JsonPrimitive> = emptyMap(),
    /** 以字符串集合保存的设置（底栏项目）。JSON 标量放不下集合，单独成组。 */
    val stringSets: Map<String, List<String>> = emptyMap(),
    /** 通知设置在单独的存储里，不在 [values] 中。 */
    val notifications: NotificationSettingsBackup? = null,
)

@Serializable
private data class NotificationSettingsBackup(
    val autoMarkAsRead: Boolean,
    val unreadBadge: Boolean,
    val systemNotifications: Map<String, Boolean> = emptyMap(),
    val displayInApp: Map<String, Boolean> = emptyMap(),
)

/**
 * WebDAV 备份与恢复：屏蔽列表、普通设置、[stringSetKeys] 列出的集合设置和通知设置。
 * 连接信息与 GitHub Token 只属于本机，上传和恢复都跳过。
 */
class WebDavSyncViewModel(
    private val settings: SettingsStore,
    private val database: ContentFilterDatabase,
    private val notificationSettings: NotificationSettingsStore,
    /** 以字符串集合保存的设置键：只按集合读写，不能当字符串恢复。 */
    private val stringSetKeys: Set<String>,
    // WebDAV 的连接与认证独立于知乎账户。
    private val client: HttpClient,
) : ViewModel() {
    val url = TextFieldState(settings.getString(WEBDAV_URL_KEY, ""))
    val username = TextFieldState(settings.getString(WEBDAV_USERNAME_KEY, ""))
    val password = TextFieldState(settings.getString(WEBDAV_PASSWORD_KEY, ""))

    internal fun normalizeAddress(raw: String = url.text.toString()) {
        val parsed = parseWebDavAddress(raw)
        if (parsed.url == raw) return

        Snapshot.withMutableSnapshot {
            if (!url.text.contentEquals(raw)) return@withMutableSnapshot
            url.edit { replace(0, length, parsed.url) }
            if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
                /** 撤销不能把已拆分的凭据恢复到明文地址栏。 */
                url.undoState.clearHistory()
            }
            if (parsed.username.isNotEmpty() && !username.text.contentEquals(parsed.username)) {
                username.setTextAndPlaceCursorAtEnd(parsed.username)
            }
            if (parsed.password.isNotEmpty() && !password.text.contentEquals(parsed.password)) {
                password.setTextAndPlaceCursorAtEnd(parsed.password)
            }
        }
    }

    var runningLabel by mutableStateOf<String?>(null)
        private set
    val isRunning: Boolean
        get() = runningLabel != null
    var status by mutableStateOf<String?>(null)
        private set

    var isStatusError by mutableStateOf(false)
        private set

    private var requestAuthHeader: String? = null

    private fun HttpRequestBuilder.withAuth() {
        requestAuthHeader?.let { header(HttpHeaders.Authorization, it) }
    }

    fun saveConfig() {
        normalizeAddress()
        settings.putString(WEBDAV_URL_KEY, url.text.toString())
        settings.putString(WEBDAV_USERNAME_KEY, username.text.toString())
        settings.putString(WEBDAV_PASSWORD_KEY, password.text.toString())
    }

    fun testConnection() = launchRequest("正在检查连接…") { directory ->
        client
            .request(directory) {
                withAuth()
                method = HttpMethod("PROPFIND")
                header("Depth", "0")
            }.requireSuccess("连接")
        "连接成功"
    }

    fun upload() = launchRequest("正在上传…") { directory ->
        val backupDirectory = directory + BACKUP_DIRECTORY
        val created = client.request(backupDirectory) {
            withAuth()
            method = HttpMethod("MKCOL")
        }
        if (created.status != HttpStatusCode.MethodNotAllowed) created.requireSuccess("创建备份目录")
        val blocklist = encodeBlocklistBackup(
            database.blockedKeywordDao(),
            database.blockedUserDao(),
            database.blockedQuestionAuthorDao(),
            database.blockedTopicDao(),
        )
        client
            .put(backupDirectory + BLOCKLIST_FILE) {
                withAuth()
                setBody(TextContent(blocklist, ContentType.Application.Json))
            }.requireSuccess("上传屏蔽列表")
        // 全量枚举本机设置。集合设置走 stringSets：桌面端把集合存成拼接字符串，
        // 混进 values 会在 Android 上被当字符串恢复，读回时类型不符。
        val entries = settings.allEntries().filterKeys { !isLocalOnlySettingKey(it) }
        val values = entries
            .filterKeys { it !in stringSetKeys }
            .filterValues { it is String || it is Boolean || it is Int || it is Long || it is Float }
            .mapValues { (_, value) ->
                when (value) {
                    is Boolean -> JsonPrimitive(value)
                    is Number -> JsonPrimitive(value)
                    else -> JsonPrimitive(value.toString())
                }
            }
        val stringSets = stringSetKeys
            .filter { it in entries }
            .associateWith { settings.getStringSet(it, emptySet()).toList() }
        val notifications = NotificationSettingsBackup(
            autoMarkAsRead = notificationSettings.getAutoMarkAsReadEnabled(),
            unreadBadge = notificationSettings.getUnreadBadgeEnabled(),
            systemNotifications = NotificationType.entries.associate { it.name to notificationSettings.getSystemNotificationEnabled(it) },
            displayInApp = NotificationType.entries.associate { it.name to notificationSettings.getDisplayInAppEnabled(it) },
        )
        val text = webDavJson.encodeToString(
            SettingsBackup.serializer(),
            SettingsBackup(values = values, stringSets = stringSets, notifications = notifications),
        )
        client
            .put(backupDirectory + SETTINGS_FILE) {
                withAuth()
                setBody(TextContent(text, ContentType.Application.Json))
            }.requireSuccess("上传设置")
        "已上传屏蔽列表、${values.size + stringSets.size} 项设置和通知设置"
    }

    fun restore() = launchRequest("正在恢复…") { directory ->
        val backupDirectory = directory + BACKUP_DIRECTORY
        val blocklistResponse = client.get(backupDirectory + BLOCKLIST_FILE) { withAuth() }
        val settingsResponse = client.get(backupDirectory + SETTINGS_FILE) { withAuth() }
        if (blocklistResponse.status == HttpStatusCode.NotFound && settingsResponse.status == HttpStatusCode.NotFound) {
            error("WebDAV 上还没有备份")
        }
        val restored = mutableListOf<String>()
        if (blocklistResponse.status != HttpStatusCode.NotFound) {
            val summary = importBlocklistBackupFromJsonText(
                database.blockedKeywordDao(),
                database.blockedUserDao(),
                database.blockedQuestionAuthorDao(),
                database.blockedTopicDao(),
                blocklistResponse.requireSuccess("下载屏蔽列表").bodyAsText(),
            )
            restored += "屏蔽列表（$summary）"
        }
        if (settingsResponse.status != HttpStatusCode.NotFound) {
            val backup = webDavJson.decodeFromString(
                SettingsBackup.serializer(),
                settingsResponse.requireSuccess("下载设置").bodyAsText(),
            )
            // 集合设置只从 stringSets 恢复：旧的桌面备份把它存成 values 里的拼接字符串，按字符串写回会让 Android 读取时类型不符。
            val applied = backup.values.filterKeys { !isLocalOnlySettingKey(it) && it !in stringSetKeys }.count { (key, value) ->
                when {
                    value.isString -> {
                        settings.putString(key, value.content)
                        true
                    }
                    value.content == "true" || value.content == "false" -> {
                        settings.putBoolean(key, value.content.toBoolean())
                        true
                    }
                    else -> {
                        val number = value.content
                        when {
                            number.contains('.') || number.contains('e') || number.contains('E') ->
                                number.toFloatOrNull()?.let { settings.putFloat(key, it) } != null
                            else -> number.toLongOrNull()?.let { long ->
                                if (long in Int.MIN_VALUE..Int.MAX_VALUE) settings.putInt(key, long.toInt()) else settings.putLong(key, long)
                                true
                            } ?: false
                        }
                    }
                }
            }
            val restoredSets = backup.stringSets
                .filterKeys { it in stringSetKeys }
                .filterValues { it.isNotEmpty() }
            restoredSets.forEach { (key, value) -> settings.putStringSet(key, value.toSet()) }
            backup.notifications?.let { notifications ->
                notificationSettings.setAutoMarkAsReadEnabled(notifications.autoMarkAsRead)
                notificationSettings.setUnreadBadgeEnabled(notifications.unreadBadge)
                NotificationType.entries.forEach { type ->
                    notifications.systemNotifications[type.name]?.let { notificationSettings.setSystemNotificationEnabled(type, it) }
                    notifications.displayInApp[type.name]?.let { notificationSettings.setDisplayInAppEnabled(type, it) }
                }
            }
            val notificationsPart = if (backup.notifications != null) "和通知设置" else ""
            restored += "${applied + restoredSets.size} 项设置$notificationsPart（部分设置重启后生效）"
        }
        "已恢复" + restored.joinToString("、")
    }

    private fun launchRequest(label: String, block: suspend (directory: String) -> String) {
        if (isRunning) return
        saveConfig()
        val directory = url.text
            .toString()
            .trim()
            .let { if (it.endsWith('/')) it else "$it/" }
        val host = runCatching { Url(directory).host }.getOrNull().orEmpty()
        val secure = directory.startsWith("https://", ignoreCase = true) ||
            (directory.startsWith("http://", ignoreCase = true) && host in setOf("127.0.0.1", "localhost", "::1", "[::1]"))
        if (!secure || host.isEmpty()) {
            status = "请填写以 https:// 开头的 WebDAV 目录地址"
            isStatusError = true
            return
        }
        runningLabel = label
        status = null
        requestAuthHeader = if (username.text.isNotEmpty()) {
            "Basic " + Base64.Default.encode("${username.text}:${password.text}".encodeToByteArray())
        } else {
            null
        }
        viewModelScope.launch {
            try {
                status = block(directory)
                isStatusError = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = e.message ?: "WebDAV 请求失败"
                isStatusError = true
            } finally {
                requestAuthHeader = null
                runningLabel = null
            }
        }
    }

    override fun onCleared() {
        client.close()
    }
}

private fun HttpResponse.requireSuccess(action: String): HttpResponse {
    if (status.isSuccess()) return this
    val reason = when (status) {
        HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> "用户名或应用密码错误"
        HttpStatusCode.NotFound -> "目录不存在"
        else -> status.description
    }
    throw IllegalStateException("${action}失败：$reason（HTTP ${status.value}）")
}
