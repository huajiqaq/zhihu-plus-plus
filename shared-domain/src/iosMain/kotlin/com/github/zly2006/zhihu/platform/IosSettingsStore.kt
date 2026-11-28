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

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.github.zly2006.zhihu.platform

import kotlinx.serialization.json.Json
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSRecursiveLock
import platform.Foundation.NSUserDefaults

private val settingsLock = NSRecursiveLock()

private inline fun <T> withSettingsLock(block: () -> T): T {
    settingsLock.lock()
    try {
        return block()
    } finally {
        settingsLock.unlock()
    }
}

internal actual fun nativeSettingsStore(relativePath: String): SettingsStore {
    val defaults = NSUserDefaults.standardUserDefaults
    val namespace = "zhpp.$relativePath."
    val changes = "${namespace}changed"
    return object : SettingsStore() {
        override fun readValue(key: String): SettingValue? = withSettingsLock {
            when (val value = defaults.objectForKey(namespace + key)) {
                null -> null
                is String -> decodeLegacySetting(key, value)
                // 原生容器区分新值与旧 NSString / NSArray，不把普通字符串当序列化数据。
                is Map<*, *> -> Json.decodeFromString(SettingValue.serializer(), checkNotNull(value["typedValue"] as? String))
                is List<*> -> SettingValue.StringSetValue(value.map { it as String }.toSet())
                else -> error("Unsupported NSUserDefaults setting type")
            }
        }

        override fun commit(updates: Map<String, SettingValue?>) {
            val applied = mutableListOf<String>()
            try {
                withSettingsLock {
                    val encoded = updates.filter { (key, value) -> readValue(key) != value }.mapValues { (_, value) ->
                        value?.let { mapOf("typedValue" to Json.encodeToString(SettingValue.serializer(), it)) }
                    }
                    // 锁只保护本进程适配器；NSUserDefaults 不提供跨进程多键事务或同步落盘保证。
                    encoded.forEach { (key, value) ->
                        if (value == null) defaults.removeObjectForKey(namespace + key) else defaults.setObject(value, forKey = namespace + key)
                        applied += key
                    }
                }
            } finally {
                // 不持锁切到主线程通知；失败时也只通知已实际更新的键，不声称回滚。
                applied.forEach { key -> NSNotificationCenter.defaultCenter.postNotificationName(changes, `object` = key) }
            }
        }

        override fun readEntries(): Map<String, SettingValue> = withSettingsLock {
            defaults.dictionaryRepresentation().keys
                .map { it.toString() }
                .filter { it.startsWith(namespace) }
                .mapNotNull { persistedKey ->
                    val key = persistedKey.removePrefix(namespace)
                    readValue(key)?.let { key to it }
                }.toMap()
        }

        override fun observeKeyChanges(onChanged: (String) -> Unit): AutoCloseable {
            val center = NSNotificationCenter.defaultCenter
            val observer = center.addObserverForName(changes, `object` = null, queue = NSOperationQueue.mainQueue) { note ->
                note?.`object`?.toString()?.let(onChanged)
            }
            return AutoCloseable { center.removeObserver(observer) }
        }
    }
}
