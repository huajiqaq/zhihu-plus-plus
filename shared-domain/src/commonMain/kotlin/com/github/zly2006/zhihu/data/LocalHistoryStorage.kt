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

package com.github.zly2006.zhihu.data

import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.navigation.WebPage
import com.github.zly2006.zhihu.navigation.withReadingQueueSource
import com.github.zly2006.zhihu.platform.AppTextFile
import com.github.zly2006.zhihu.platform.DiskTextFile
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.appPrivateTextFile
import com.github.zly2006.zhihu.platform.platformSettingsStore
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.io.files.Path
import kotlinx.serialization.json.Json

const val HISTORY_RECORDING_ENABLED_KEY = "historyRecordingEnabled"

/** 每个文件共享一个同步所有者；读改写串行，损坏数据不能被空快照覆盖。 */
class LocalHistoryStorage private constructor(
    file: AppTextFile,
    private val settings: SettingsStore,
) {
    constructor(settings: SettingsStore = platformSettingsStore()) : this(appPrivateTextFile("history.json"), settings)

    constructor(file: Path, settings: SettingsStore = platformSettingsStore()) : this(DiskTextFile(file), settings)

    private val owner = fileOwnersLock.withLock { fileOwners.getOrPut(file.key) { FileOwner(file) } }

    val recordingEnabled: Boolean
        get() = settings.getBoolean(HISTORY_RECORDING_ENABLED_KEY, true)

    val history: Array<NavDestination>
        get() = owner.lock.withLock {
            owner
                .read()
                .values
                .toTypedArray()
                .apply { reverse() }
        }

    fun add(destination: NavDestination) {
        if (destination is WebPage || !recordingEnabled) return
        val key = destination.withReadingQueueSource(null)
        update {
            // 旧人物路由可能只有 token，解析 ID 后仍相等但 hash 不同；不能仅靠 Map 的 key 去重。
            if (key is Person) entries.removeAll { it.value == key } else remove(key)
            put(key, key)
            while (size > 1000) remove(keys.first())
        }
    }

    fun remove(destinations: Set<NavDestination>) {
        if (destinations.isEmpty()) return
        update {
            for (destination in destinations) {
                val key = destination.withReadingQueueSource(null)
                if (key is Person) entries.removeAll { it.value == key } else remove(key)
            }
        }
    }

    fun clearAndSave() = update { clear() }

    fun setRecordingEnabled(enabled: Boolean) {
        settings.putBoolean(HISTORY_RECORDING_ENABLED_KEY, enabled)
    }

    private fun update(change: LinkedHashMap<NavDestination, NavDestination>.() -> Unit) = owner.update(change)

    companion object {
        private val fileOwnersLock = reentrantLock()
        private val fileOwners = mutableMapOf<String, FileOwner>()
    }

    private class FileOwner(
        private val file: AppTextFile,
    ) {
        val lock = reentrantLock()

        fun read(): LinkedHashMap<NavDestination, NavDestination> {
            val entries = file.read()?.let { Json.decodeFromString<Array<NavDestination>>(it) }.orEmpty()
            return linkedMapOf<NavDestination, NavDestination>().apply {
                for (entry in entries) {
                    val destination = entry.withReadingQueueSource(null)
                    if (destination is Person) this.entries.removeAll { it.value == destination }
                    put(destination, destination)
                }
            }
        }

        fun update(change: LinkedHashMap<NavDestination, NavDestination>.() -> Unit) = lock.withLock {
            val entries = read().apply(change)
            file.write(Json.encodeToString(entries.values.toTypedArray()))
        }
    }
}
