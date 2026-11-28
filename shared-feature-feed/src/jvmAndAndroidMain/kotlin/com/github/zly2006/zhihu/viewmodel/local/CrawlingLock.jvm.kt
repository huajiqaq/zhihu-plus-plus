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

package com.github.zly2006.zhihu.viewmodel.local

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files

internal actual suspend fun tryAcquireCrawlingLock(databasePath: String): AutoCloseable? {
    val database = File(databasePath).canonicalFile
    check(database.isFile) { "抓取数据库不存在" }
    val windows = System.getProperty("os.name")?.startsWith("Windows", ignoreCase = true) == true
    if (!windows) {
        /** 硬链接别名会分裂 sidecar 身份，不能安全恢复另一别名的任务。 */
        check((Files.getAttribute(database.toPath(), "unix:nlink") as Number).toLong() == 1L) { "抓取数据库不支持硬链接" }
    }
    /** NTFS 流随文件身份共享；POSIX sidecar 与 Native 共用字节区间。 */
    val lockPath = database.path + if (windows) ":zhihu-crawling-lock" else ".crawling-lock"
    if (!windows) check(!Files.isSymbolicLink(File(lockPath).toPath())) { "抓取锁不能是符号链接" }
    val file = RandomAccessFile(lockPath, "rw")
    try {
        if (file.channel.tryLock(0L, 1L, false) == null) {
            file.close()
            return null
        }
        /** 删除锁文件会让活执行与恢复分别锁住不同 inode。 */
        return AutoCloseable { file.close() }
    } catch (error: Throwable) {
        runCatching { file.close() }.exceptionOrNull()?.let { if (it !== error) error.addSuppressed(it) }
        throw error
    }
}
