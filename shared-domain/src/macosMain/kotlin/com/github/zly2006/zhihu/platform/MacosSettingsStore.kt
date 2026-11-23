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

import platform.Foundation.NSFileManager
import platform.Foundation.NSRecursiveLock
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding

/** 同一物理文件共用设置实例，避免独立消费者用过期快照相互覆盖。 */
private val macosSettingsStores = mutableMapOf<String, SettingsStore>()
private val macosSettingsStoreLock = NSRecursiveLock()

private const val STRING_SET_SEPARATOR = '\u001F'

internal actual fun nativeSettingsStore(relativePath: String): SettingsStore {
    val filePath = NSURL
        .fileURLWithPath("${nativeAppPrivateDirectoryPath()}/$relativePath")
        .standardizedURL.path ?: error("macOS 设置路径无效")
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

    return object : SettingsStore {
        override fun observeKeyChanges(onChanged: (String) -> Unit): AutoCloseable = propertiesFile.observe(onChanged)

        override fun getBoolean(key: String, defaultValue: Boolean) = propertiesFile[key]?.toBooleanStrictOrNull() ?: defaultValue

        override fun putBoolean(key: String, value: Boolean) {
            propertiesFile[key] = value.toString()
        }

        override fun getString(key: String, defaultValue: String) = propertiesFile[key] ?: defaultValue

        override fun putString(key: String, value: String) {
            propertiesFile[key] = value
        }

        override fun getStringOrNull(key: String) = propertiesFile[key]

        override fun putStringSet(key: String, value: Set<String>) {
            propertiesFile[key] = value.joinToString(STRING_SET_SEPARATOR.toString())
        }

        override fun getStringSet(key: String, defaultValue: Set<String>) = propertiesFile[key]?.split(STRING_SET_SEPARATOR)?.filter(String::isNotEmpty)?.toSet() ?: defaultValue

        override fun getInt(key: String, defaultValue: Int) = propertiesFile[key]?.toIntOrNull() ?: defaultValue

        override fun putInt(key: String, value: Int) {
            propertiesFile[key] = value.toString()
        }

        override fun getLong(key: String, defaultValue: Long) = propertiesFile[key]?.toLongOrNull() ?: defaultValue

        override fun putLong(key: String, value: Long) {
            propertiesFile[key] = value.toString()
        }

        override fun getFloat(key: String, defaultValue: Float) = propertiesFile[key]?.toFloatOrNull() ?: defaultValue

        override fun putFloat(key: String, value: Float) {
            propertiesFile[key] = value.toString()
        }

        override fun remove(key: String) = propertiesFile.remove(key)

        override fun removeByPrefix(prefix: String) = propertiesFile.removeByPrefix(prefix)

        override fun allEntries(): Map<String, Any> = propertiesFile.entries()
    }
}

private class MacosPropertiesFile(
    private val filePath: String,
) {
    private var values = linkedMapOf<String, String>()
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
            val data = fileManager.contentsAtPath(filePath)
            val text = data?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding)?.toString() }
            if (text != null) {
                read(text)
            }
        }
    }

    operator fun get(key: String): String? = locked { values[key] }

    fun entries(): Map<String, String> = locked { values.toMap() }

    operator fun set(key: String, value: String) = commit(mapOf(key to value))

    fun remove(key: String) = commit(mapOf(key to null))

    fun removeByPrefix(prefix: String) = locked {
        commit(values.keys.filter { it.startsWith(prefix) }.associateWith { null })
    }

    private fun commit(updates: Map<String, String?>) = locked {
        val changed = updates.filter { (key, value) -> values[key] != value }
        if (changed.isEmpty()) return@locked
        val next = LinkedHashMap(values).apply {
            changed.forEach { (key, value) -> if (value == null) remove(key) else put(key, value) }
        }
        save(next)
        values = next
        changed.keys.forEach { key -> listeners.toList().forEach { it(key) } }
    }

    private fun read(text: String) {
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
            values[unescape(rawKey)] = unescape(trimmedStart.substring(valueStart))
        }
    }

    private fun save(next: Map<String, String>) {
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
        val text = buildString {
            append("#Zhihu++ desktop settings\n")
            next.forEach { (key, value) ->
                append(escape(key))
                append('=')
                append(escape(value))
                append('\n')
            }
        }
        val data = checkNotNull(NSString.create(string = text).dataUsingEncoding(NSUTF8StringEncoding)) {
            "无法编码 macOS 设置"
        }
        check(fileManager.createFileAtPath(filePath, contents = data, attributes = null)) {
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

    private fun escape(value: String): String = buildString {
        value.forEachIndexed { index, character ->
            when {
                character == ' ' && index == 0 -> append("\\ ")
                character == '\\' -> append("\\\\")
                character == '\t' -> append("\\t")
                character == '\n' -> append("\\n")
                character == '\r' -> append("\\r")
                character == '\u000C' -> append("\\f")
                character == '=' || character == ':' || character == '#' || character == '!' -> {
                    append('\\')
                    append(character)
                }
                character.code !in 0x20..0x7E -> append("\\u${character.code.toString(16).uppercase().padStart(4, '0')}")
                else -> append(character)
            }
        }
    }
}
