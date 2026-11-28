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

@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.github.zly2006.zhihu.platform

import androidx.compose.runtime.Composable
import com.github.zly2006.zhihu.data.isSensitiveRequestHeader
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.serialization.json.Json
import org.w3c.dom.StorageEvent
import org.w3c.dom.events.Event

private const val FILE_PREFIX = "zhihu++:file:"
private const val SETTINGS_PREFIX = "zhihu++:settings:"

actual fun appPrivateTextFile(name: String): AppTextFile {
    require(name == "history.json" || Regex("home_feed_startup_cache_server_(guest|[0-9a-f]{32})[.]json").matches(name)) {
        "该文件不属于浏览器的历史或首页缓存"
    }
    return object : AppTextFile {
        override val key = FILE_PREFIX + name

        override fun read(): String? = localStorage.getItem(key)

        override fun write(text: String) = localStorage.setItem(key, text)
    }
}

/** 只枚举本应用的普通偏好。账号会话、WebDAV 连接及第三方凭据不读写明文存储。 */
internal class WebSettingsStore(
    private val prefix: String,
) : SettingsStore() {
    private val listeners = mutableSetOf<(String) -> Unit>()

    private fun canPersist(key: String): Boolean {
        val name = key.lowercase()
        return name.isNotEmpty() &&
            !isSensitiveRequestHeader(name) &&
            !name.startsWith("webdav") &&
            forbiddenKeys.none(name::contains) &&
            name !in setOf("auth", "account", "accounts", "phone", "mobile", "pwd", "z_c0", "d_c0")
    }

    private fun read(key: String): String? = if (canPersist(key)) localStorage.getItem(prefix + key) else null

    private fun entries(): List<String> = (0 until localStorage.length).mapNotNull { index ->
        localStorage
            .key(index)
            ?.takeIf { it.startsWith(prefix) }
            ?.removePrefix(prefix)
            ?.takeIf(::canPersist)
    }

    override fun readValue(key: String): SettingValue? {
        val encoded = read(key) ?: return null
        val text = encoded.drop(1)
        // 已发布的浏览器格式已有类型标签，保留键、值格式及跨标签页通知。
        return when (encoded.firstOrNull()) {
            'b' -> text.toBooleanStrictOrNull()?.let { SettingValue.BooleanValue(it) }
            's' -> SettingValue.StringValue(text)
            'i' -> text.toIntOrNull()?.let { SettingValue.IntValue(it) }
            'l' -> text.toLongOrNull()?.let { SettingValue.LongValue(it) }
            'f' -> text.toFloatOrNull()?.let { SettingValue.FloatValue(it.toRawBits()) }
            'a' -> SettingValue.StringSetValue(Json.decodeFromString<Set<String>>(text))
            else -> error("Unknown browser setting encoding")
        }
    }

    override fun commit(updates: Map<String, SettingValue?>) {
        val encoded = updates.mapValues { (key, value) ->
            require(value == null || canPersist(key)) { "浏览器不能以明文保存此项凭据" }
            when (value) {
                null -> null
                is SettingValue.BooleanValue -> "b${value.value}"
                is SettingValue.StringValue -> "s${value.value}"
                is SettingValue.IntValue -> "i${value.value}"
                is SettingValue.LongValue -> "l${value.value}"
                is SettingValue.FloatValue -> "f${value.value}"
                is SettingValue.StringSetValue -> "a" + Json.encodeToString(value.value)
            }
        }.filter { (key, value) -> read(key) != value }
        val applied = mutableListOf<String>()
        try {
            encoded.forEach { (key, value) ->
                if (value == null) localStorage.removeItem(prefix + key) else localStorage.setItem(prefix + key, value)
                applied += key
            }
        } finally {
            // 同一同步回合结束后本地观察者才读取；配额失败可部分落存，跨标签页也不保证多键原子。
            applied.forEach { key -> listeners.toList().forEach { it(key) } }
        }
    }

    override fun readEntries(): Map<String, SettingValue> = entries().mapNotNull { key -> readValue(key)?.let { key to it } }.toMap()

    private val storageListener: (Event) -> Unit = { event ->
        val change = event as StorageEvent
        if (change.storageArea == localStorage) {
            change.key?.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.takeIf(::canPersist)?.let { key ->
                listeners.toList().forEach { it(key) }
            }
        }
    }

    override fun observeKeyChanges(onChanged: (String) -> Unit): AutoCloseable {
        if (listeners.isEmpty()) window.addEventListener("storage", storageListener)
        listeners.add(onChanged)
        return AutoCloseable {
            listeners.remove(onChanged)
            if (listeners.isEmpty()) window.removeEventListener("storage", storageListener)
        }
    }

    private companion object {
        val forbiddenKeys = listOf("password", "passwd", "credential", "session", "api_key", "phonenumber", "phone_number", "accesskey", "privatekey")
    }
}

private val settings = WebSettingsStore(SETTINGS_PREFIX)

actual fun platformSettingsStore(): SettingsStore = settings

@Composable
actual fun rememberSettingsStore(): SettingsStore = settings
