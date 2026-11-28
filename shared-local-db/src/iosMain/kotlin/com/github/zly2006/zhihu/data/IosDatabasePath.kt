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

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/** 与应用的 Documents 数据目录一致；目录不可用时失败，绝不退回临时库或空库。 */
@OptIn(ExperimentalForeignApi::class)
internal fun iosDatabasePath(name: String): String {
    val files = NSFileManager.defaultManager
    val directory = (files.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask).firstOrNull() as? NSURL)?.path
        ?: error("iOS Documents directory is unavailable")
    check(files.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)) {
        "Cannot create iOS database directory"
    }
    return "$directory/$name"
}
