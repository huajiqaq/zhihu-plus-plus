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

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString

/** 单个业务快照；写入原子替换，只有条目不存在时 read 才返回 null。 */
interface AppTextFile {
    val key: String

    fun read(): String?

    fun write(text: String)
}

expect fun appPrivateTextFile(name: String): AppTextFile

internal class DiskTextFile(
    private val path: Path,
) : AppTextFile {
    override val key: String = path.toString()

    override fun read(): String? = if (SystemFileSystem.exists(path)) {
        SystemFileSystem.source(path).buffered().use { it.readString() }
    } else {
        null
    }

    override fun write(text: String) {
        val directory = checkNotNull(path.parent)
        SystemFileSystem.createDirectories(directory)
        val temporary = Path(directory, path.name + ".tmp")
        try {
            SystemFileSystem.sink(temporary).buffered().use { it.writeString(text) }
            SystemFileSystem.atomicMove(temporary, path)
        } finally {
            SystemFileSystem.delete(temporary, mustExist = false)
        }
    }
}
