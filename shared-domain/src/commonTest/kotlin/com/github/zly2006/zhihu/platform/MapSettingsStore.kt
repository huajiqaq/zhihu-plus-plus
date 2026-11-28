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

package com.github.zly2006.zhihu.platform

class MapSettingsStore(
    private val values: MutableMap<String, SettingValue> = mutableMapOf(),
) : SettingsStore() {
    private val listeners = mutableSetOf<(String) -> Unit>()

    override fun readValue(key: String): SettingValue? = values[key]

    override fun readEntries(): Map<String, SettingValue> = values.toMap()

    override fun commit(updates: Map<String, SettingValue?>) {
        val changed = updates.filter { (key, value) -> values[key] != value }
        changed.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
        changed.keys.forEach { key -> listeners.toList().forEach { it(key) } }
    }

    override fun observeKeyChanges(onChanged: (String) -> Unit): AutoCloseable {
        listeners.add(onChanged)
        return AutoCloseable { listeners.remove(onChanged) }
    }
}
