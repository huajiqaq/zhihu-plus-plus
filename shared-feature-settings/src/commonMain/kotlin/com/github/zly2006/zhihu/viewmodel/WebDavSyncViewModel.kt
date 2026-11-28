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
import com.github.zly2006.zhihu.i18n.AccountStrings
import com.github.zly2006.zhihu.i18n.SettingsStrings
import com.github.zly2006.zhihu.notification.NotificationSettingsStore
import com.github.zly2006.zhihu.notification.NotificationType
import com.github.zly2006.zhihu.platform.SettingValue
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
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val BACKUP_DIRECTORY = "zhihu-plus-plus/"
private const val BLOCKLIST_FILE = "blocklist.json"
private const val SETTINGS_FILE = "settings.json"
private const val WEBDAV_URL_KEY = "webdav_url"
private const val WEBDAV_USERNAME_KEY = "webdav_username"
private const val WEBDAV_PASSWORD_KEY = "webdav_password"

/** 连接信息与第三方凭据只属于本机：上传时不带出，恢复时也不让备份覆盖。 */
private fun isLocalOnlySettingKey(key: String) = key.startsWith("webdav") || key == "githubToken"

private val webDavJson = Json {
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

/** 缺少的键不会覆盖本机设置，空集合则是应当恢复的有效值。 */
@Serializable
private data class SettingsBackup(
    val version: Int,
    val exportTime: Long,
    val entries: Map<String, SettingValue>,
    /** 通知设置在单独的存储里。 */
    val notifications: NotificationSettingsBackup? = null,
) {
    init {
        if (version != 1) throw WebDavMessageException { SettingsStrings.unsupportedSettingsBackupVersion(version) }
    }
}

@Serializable
private data class NotificationSettingsBackup(
    val autoMarkAsRead: Boolean,
    val unreadBadge: Boolean,
    val systemNotifications: Map<String, Boolean> = emptyMap(),
    val displayInApp: Map<String, Boolean> = emptyMap(),
)

/**
 * WebDAV 备份与恢复：屏蔽列表、类型化设置快照和通知设置。
 * 连接信息与 GitHub Token 只属于本机，上传和恢复都跳过。
 */
class WebDavSyncViewModel(
    private val settings: SettingsStore,
    private val database: ContentFilterDatabase,
    private val notificationSettings: NotificationSettingsStore,
    // WebDAV 的连接与认证独立于知乎账户。
    private val client: HttpClient,
    private val persistConnectionConfig: Boolean = true,
) : ViewModel() {
    val url = TextFieldState(if (persistConnectionConfig) settings.getString(WEBDAV_URL_KEY, "") else "")
    val username = TextFieldState(if (persistConnectionConfig) settings.getString(WEBDAV_USERNAME_KEY, "") else "")
    val password = TextFieldState(if (persistConnectionConfig) settings.getString(WEBDAV_PASSWORD_KEY, "") else "")

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

    private var runningText by mutableStateOf<(() -> String)?>(null)
    val runningLabel: String? get() = runningText?.invoke()
    val isRunning: Boolean
        get() = runningLabel != null
    private var statusText by mutableStateOf<(() -> String)?>(null)
    val status: String? get() = statusText?.invoke()

    var isStatusError by mutableStateOf(false)
        private set

    private var requestAuthHeader: String? = null

    private fun HttpRequestBuilder.withAuth() {
        requestAuthHeader?.let { header(HttpHeaders.Authorization, it) }
    }

    fun saveConfig() {
        normalizeAddress()
        if (!persistConnectionConfig) return
        settings.putString(WEBDAV_URL_KEY, url.text.toString())
        settings.putString(WEBDAV_USERNAME_KEY, username.text.toString())
        settings.putString(WEBDAV_PASSWORD_KEY, password.text.toString())
    }

    fun testConnection() = launchRequest({ AccountStrings.webdavConnecting }) { directory ->
        client
            .request(directory) {
                withAuth()
                method = HttpMethod("PROPFIND")
                header("Depth", "0")
            }.requireSuccess { AccountStrings.webdavConnect }
        return@launchRequest { AccountStrings.webdavConnected }
    }

    fun upload() = launchRequest({ AccountStrings.webdavUploading }) { directory ->
        val backupDirectory = directory + BACKUP_DIRECTORY
        val created = client.request(backupDirectory) {
            withAuth()
            method = HttpMethod("MKCOL")
        }
        if (created.status != HttpStatusCode.MethodNotAllowed) created.requireSuccess { AccountStrings.webdavCreateDirectory }
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
            }.requireSuccess { AccountStrings.webdavUploadBlocklist }
        val entries = settings.snapshot().filterKeys { !isLocalOnlySettingKey(it) }
        val notifications = NotificationSettingsBackup(
            autoMarkAsRead = notificationSettings.getAutoMarkAsReadEnabled(),
            unreadBadge = notificationSettings.getUnreadBadgeEnabled(),
            systemNotifications = NotificationType.entries.associate { it.name to notificationSettings.getSystemNotificationEnabled(it) },
            displayInApp = NotificationType.entries.associate { it.name to notificationSettings.getDisplayInAppEnabled(it) },
        )
        val text = webDavJson.encodeToString(
            SettingsBackup.serializer(),
            SettingsBackup(version = 1, exportTime = Clock.System.now().toEpochMilliseconds(), entries = entries, notifications = notifications),
        )
        client
            .put(backupDirectory + SETTINGS_FILE) {
                withAuth()
                setBody(TextContent(text, ContentType.Application.Json))
            }.requireSuccess { AccountStrings.webdavUploadSettings }
        return@launchRequest { AccountStrings.webdavUploaded(entries.size) }
    }

    fun restore() = launchRequest({ AccountStrings.webdavRestoring }) { directory ->
        val backupDirectory = directory + BACKUP_DIRECTORY
        val blocklistResponse = client.get(backupDirectory + BLOCKLIST_FILE) { withAuth() }
        val settingsResponse = client.get(backupDirectory + SETTINGS_FILE) { withAuth() }
        if (blocklistResponse.status == HttpStatusCode.NotFound && settingsResponse.status == HttpStatusCode.NotFound) {
            throw WebDavMessageException { AccountStrings.webdavNoBackup }
        }
        // 在任何恢复写入前验证设置文档；未知版本或缺少字段不能当作空备份成功。
        val backup = if (settingsResponse.status == HttpStatusCode.NotFound) null else {
            webDavJson.decodeFromString(SettingsBackup.serializer(), settingsResponse.requireSuccess { AccountStrings.webdavDownloadSettings }.bodyAsText())
        }
        val restored = mutableListOf<() -> String>()
        if (blocklistResponse.status != HttpStatusCode.NotFound) {
            val summary = importBlocklistBackupFromJsonText(
                database.blockedKeywordDao(),
                database.blockedUserDao(),
                database.blockedQuestionAuthorDao(),
                database.blockedTopicDao(),
                blocklistResponse.requireSuccess { AccountStrings.webdavDownloadBlocklist }.bodyAsText(),
            )
            restored += { AccountStrings.webdavBlocklistRestored(summary) }
        }
        if (backup != null) {
            val entries = backup.entries.filterKeys { !isLocalOnlySettingKey(it) }
            settings.restore(entries)
            backup.notifications?.let { notifications ->
                notificationSettings.setAutoMarkAsReadEnabled(notifications.autoMarkAsRead)
                notificationSettings.setUnreadBadgeEnabled(notifications.unreadBadge)
                NotificationType.entries.forEach { type ->
                    notifications.systemNotifications[type.name]?.let { notificationSettings.setSystemNotificationEnabled(type, it) }
                    notifications.displayInApp[type.name]?.let { notificationSettings.setDisplayInAppEnabled(type, it) }
                }
            }
            restored += {
                val notificationsPart = if (backup.notifications != null) AccountStrings.webdavNotificationsPart else ""
                AccountStrings.webdavSettingsRestored(entries.size, notificationsPart)
            }
        }
        return@launchRequest { AccountStrings.webdavRestoreResult(restored.joinToString(AccountStrings.listSeparator) { it() }) }
    }

    private fun launchRequest(label: () -> String, block: suspend (directory: String) -> (() -> String)) {
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
            statusText = { AccountStrings.webdavHttpsRequired }
            isStatusError = true
            return
        }
        runningText = label
        statusText = null
        requestAuthHeader = if (username.text.isNotEmpty()) {
            "Basic " + Base64.Default.encode("${username.text}:${password.text}".encodeToByteArray())
        } else {
            null
        }
        viewModelScope.launch {
            try {
                statusText = block(directory)
                isStatusError = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                statusText = { e.message ?: AccountStrings.webdavFailed }
                isStatusError = true
            } finally {
                requestAuthHeader = null
                runningText = null
            }
        }
    }

    override fun onCleared() {
        client.close()
    }
}

private fun HttpResponse.requireSuccess(action: () -> String): HttpResponse {
    if (status.isSuccess()) return this
    throw WebDavMessageException {
        val reason = when (status) {
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> AccountStrings.webdavCredentialsInvalid
            HttpStatusCode.NotFound -> AccountStrings.webdavDirectoryMissing
            else -> status.description
        }
        AccountStrings.webdavActionFailed(action(), reason, status.value)
    }
}

/** 同步结果与错误在展示时取文案，切换语言不会重启正在进行的传输。 */
private class WebDavMessageException(
    private val text: () -> String,
) : IllegalStateException() {
    override val message: String get() = text()
}
