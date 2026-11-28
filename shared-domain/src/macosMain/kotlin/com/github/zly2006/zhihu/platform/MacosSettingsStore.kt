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

@file:OptIn(kotlinx.cinterop.BetaInteropApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package com.github.zly2006.zhihu.platform

import platform.Foundation.NSDataWritingAtomic
import platform.Foundation.NSFileManager
import platform.Foundation.NSRecursiveLock
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.writeToURL

/** 同一物理文件共用设置实例，避免独立消费者用过期快照相互覆盖。 */
private val macosSettingsStores = mutableMapOf<String, SettingsStore>()
private val macosSettingsStoreLock = NSRecursiveLock()

internal actual fun nativeSettingsStore(relativePath: String): SettingsStore {
    val filePath = NSURL
        .fileURLWithPath("${nativeAppPrivateDirectoryPath()}/$relativePath")
        .standardizedURL?.path ?: error("macOS 设置路径无效")
    macosSettingsStoreLock.lock()
    try {
        return macosSettingsStores.getOrPut(filePath) {
            createMacosSettingsStore(filePath)
        }
    } finally {
        macosSettingsStoreLock.unlock()
    }
}

private fun createMacosSettingsStore(filePath: String): SettingsStore {
    val propertiesFile = MacosPropertiesFile(filePath)

    return object : SettingsStore() {
        override fun observeKeyChanges(onChanged: (String) -> Unit): AutoCloseable = propertiesFile.observe(onChanged)

        override fun readValue(key: String): SettingValue? = propertiesFile[key]

        override fun commit(updates: Map<String, SettingValue?>) = propertiesFile.commit(updates)

        override fun readEntries(): Map<String, SettingValue> = propertiesFile.entries()

        override fun removeByPrefix(prefix: String) = propertiesFile.removeByPrefix(prefix)
    }
}

private class MacosPropertiesFile(
    private val filePath: String,
) {
    private var values: Map<String, SettingValue> = emptyMap()
    private val listeners = mutableListOf<(String) -> Unit>()
    private val lock = NSRecursiveLock()

    private inline fun <T> locked(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    fun observe(listener: (String) -> Unit): AutoCloseable = locked {
        listeners.add(listener)
        AutoCloseable { locked { listeners.remove(listener) } }
    }

    init {
        val fileManager = NSFileManager.defaultManager
        if (fileManager.fileExistsAtPath(filePath)) {
            val data = checkNotNull(fileManager.contentsAtPath(filePath)) { "无法读取 macOS 设置" }
            val text = checkNotNull(NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()) { "无法解码 macOS 设置" }
            values = SettingsFileCodec.decode(text) ?: readLegacyProperties(text)
        }
    }

    operator fun get(key: String): SettingValue? = locked { values[key] }

    fun entries(): Map<String, SettingValue> = locked { values.toMap() }

    fun removeByPrefix(prefix: String) = locked {
        commit(values.keys.filter { it.startsWith(prefix) }.associateWith { null })
    }

    fun commit(updates: Map<String, SettingValue?>) = locked {
        val changed = updates.filter { (key, value) -> values[key] != value }
        if (changed.isEmpty()) return@locked
        val next = LinkedHashMap(values).apply {
            changed.forEach { (key, value) -> if (value == null) remove(key) else put(key, value) }
        }
        save(next)
        values = next
        changed.keys.forEach { key -> listeners.toList().forEach { it(key) } }
    }

    private fun readLegacyProperties(text: String): Map<String, SettingValue> {
        val result = linkedMapOf<String, SettingValue>()
        val logicalLines = mutableListOf<String>()
        var currentLine = ""
        text.lineSequence().forEach { physicalLine ->
            currentLine += physicalLine
            var trailingBackslashes = 0
            for (index in currentLine.lastIndex downTo 0) {
                if (currentLine[index] != '\\') break
                trailingBackslashes += 1
            }
            if (trailingBackslashes % 2 == 1) {
                currentLine = currentLine.dropLast(1)
            } else {
                logicalLines += currentLine
                currentLine = ""
            }
        }
        if (currentLine.isNotEmpty()) logicalLines += currentLine

        logicalLines.forEach { line ->
            val trimmedStart = line.trimStart()
            if (trimmedStart.isEmpty() || trimmedStart.startsWith('#') || trimmedStart.startsWith('!')) return@forEach
            var escaped = false
            var separatorIndex = -1
            for (index in trimmedStart.indices) {
                val character = trimmedStart[index]
                if (!escaped && (character == '=' || character == ':' || character.isWhitespace())) {
                    separatorIndex = index
                    break
                }
                escaped = !escaped && character == '\\'
                if (character != '\\') escaped = false
            }
            val rawKey = if (separatorIndex >= 0) trimmedStart.substring(0, separatorIndex) else trimmedStart
            var valueStart = if (separatorIndex >= 0) separatorIndex else trimmedStart.length
            while (valueStart < trimmedStart.length && trimmedStart[valueStart].isWhitespace()) valueStart += 1
            if (valueStart < trimmedStart.length && (trimmedStart[valueStart] == '=' || trimmedStart[valueStart] == ':')) {
                valueStart += 1
            }
            while (valueStart < trimmedStart.length && trimmedStart[valueStart].isWhitespace()) valueStart += 1
            val key = unescape(rawKey)
            result[key] = decodeLegacySetting(key, unescape(trimmedStart.substring(valueStart)))
        }
        return result
    }

    private fun save(next: Map<String, SettingValue>) {
        val parentDirectory = filePath.substringBeforeLast('/')
        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(parentDirectory)) {
            check(
                fileManager.createDirectoryAtPath(
                    parentDirectory,
                    withIntermediateDirectories = true,
                    attributes = null,
                    error = null,
                ),
            ) { "无法创建 macOS 设置目录" }
        }
        val text = SettingsFileCodec.encode(next)
        val data = checkNotNull(NSString.create(string = text).dataUsingEncoding(NSUTF8StringEncoding)) {
            "无法编码 macOS 设置"
        }
        check(data.writeToURL(NSURL.fileURLWithPath(filePath), NSDataWritingAtomic, null)) {
            "无法保存 macOS 设置"
        }
    }

    private fun unescape(value: String): String = buildString {
        var index = 0
        while (index < value.length) {
            val character = value[index]
            if (character != '\\' || index == value.lastIndex) {
                append(character)
                index += 1
                continue
            }
            index += 1
            when (val escaped = value[index]) {
                't' -> append('\t')
                'n' -> append('\n')
                'r' -> append('\r')
                'f' -> append('\u000C')
                'u' -> {
                    val endIndex = (index + 5).coerceAtMost(value.length)
                    val codePoint = value.substring(index + 1, endIndex).toIntOrNull(16)
                    if (codePoint != null && endIndex == index + 5) {
                        append(codePoint.toChar())
                        index += 4
                    } else {
                        append(escaped)
                    }
                }
                else -> append(escaped)
            }
            index += 1
        }
    }

}
