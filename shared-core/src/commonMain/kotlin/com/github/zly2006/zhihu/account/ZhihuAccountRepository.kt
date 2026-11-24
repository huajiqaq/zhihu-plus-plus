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

package com.github.zly2006.zhihu.account

import com.github.zly2006.zhihu.data.ZhihuJson
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

const val DEFAULT_ZHIHU_USER_AGENT =
    "Mozilla/5.0 (X11; U; Linux x86_64; en-US) AppleWebKit/540.0 (KHTML, like Gecko) Ubuntu/10.10 Chrome/9.1.0.0 Safari/540.0"

@Serializable
data class ZhihuAccountProfileSnapshot(
    val id: String = "",
    val name: String = "",
    val urlToken: String? = null,
    val userType: String = "",
    val avatarUrl: String? = null,
)

@Serializable
data class ZhihuAccountSession(
    val login: Boolean = false,
    val username: String = "",
    val cookies: MutableMap<String, String> = mutableMapOf(),
    val userAgent: String = DEFAULT_ZHIHU_USER_AGENT,
    val profile: ZhihuAccountProfileSnapshot? = null,
    val self: JsonElement? = null,
    val mobileAccessToken: String? = null,
    val mobileRefreshToken: String? = null,
    val mobileTokenType: String? = null,
    val mobileTokenExpiresAt: Long? = null,
)

@Serializable
data class ZhihuSavedAccount(
    val id: String,
    val session: ZhihuAccountSession,
    /** 切回来源身份所需的最新凭据，不是应用的活动会话。 */
    val pendingCommentIdentity: ZhihuAccountSession? = null,
)

@Serializable
data class ZhihuAccounts(
    val activeAccountId: String? = null,
    val accounts: List<ZhihuSavedAccount> = emptyList(),
    val guestCookies: Map<String, String> = emptyMap(),
) {
    @Transient
    private val guestSession = ZhihuAccountSession(cookies = guestCookies.toMutableMap())

    val session: ZhihuAccountSession
        get() = accounts.firstOrNull { it.id == activeAccountId }?.session ?: guestSession
}

class ZhihuAccountRepository(
    private val file: Path,
) {
    val directory: Path get() = requireNotNull(file.parent)

    fun loadAccounts(): ZhihuAccounts {
        if (!SystemFileSystem.exists(file)) return ZhihuAccounts()
        val text = SystemFileSystem.source(file).buffered().use { it.readString() }
        if (text.isBlank()) return ZhihuAccounts()
        return runCatching {
            val json = ZhihuJson.json
            val element = json.parseToJsonElement(text)
            val loaded = if (element is JsonObject && "accounts" in element) {
                json.decodeFromString<ZhihuAccounts>(text)
            } else {
                val session = json.decodeFromString<ZhihuAccountSession>(text)
                if (session.login) {
                    val id = session.accountSlotId()
                    ZhihuAccounts(id, listOf(ZhihuSavedAccount(id, session)))
                } else {
                    ZhihuAccounts()
                }
            }
            loaded.copy(
                accounts = loaded.accounts.map { account ->
                    val session = account.session
                    val profile = session.profile ?: session.self?.let { raw ->
                        runCatching { ZhihuJson.decodeJson<ZhihuAccountProfileSnapshot>(raw) }.getOrNull()?.takeIf { it.id.isNotBlank() }
                    }
                    if (profile == session.profile) account else account.copy(session = session.copy(profile = profile))
                },
            )
        }.getOrDefault(ZhihuAccounts())
    }

    fun saveAccounts(accounts: ZhihuAccounts) {
        val directory = checkNotNull(file.parent)
        SystemFileSystem.createDirectories(directory)
        val temporary = Path(directory, file.name + ".tmp")
        try {
            SystemFileSystem.sink(temporary).buffered().use { sink ->
                sink.writeString(ZhihuJson.json.encodeToString(accounts))
            }
            SystemFileSystem.atomicMove(temporary, file)
        } finally {
            SystemFileSystem.delete(temporary, mustExist = false)
        }
    }

    fun clear() {
        SystemFileSystem.delete(file, mustExist = false)
    }
}

fun ZhihuAccountSession.accountIdentityKey(): String? = profile?.id?.takeIf(String::isNotBlank)
    ?: profile?.urlToken?.takeIf(String::isNotBlank)
    ?: username.takeIf(String::isNotBlank)

fun ZhihuAccountSession.accountSlotId(): String = accountIdentityKey() ?: "account"
