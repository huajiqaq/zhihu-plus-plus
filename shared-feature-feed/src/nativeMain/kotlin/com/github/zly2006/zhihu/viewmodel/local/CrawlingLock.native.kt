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

package com.github.zly2006.zhihu.viewmodel.local

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.posix.EACCES
import platform.posix.EAGAIN
import platform.posix.F_SETLK
import platform.posix.F_WRLCK
import platform.posix.O_CLOEXEC
import platform.posix.O_CREAT
import platform.posix.O_NOFOLLOW
import platform.posix.O_RDWR
import platform.posix.PATH_MAX
import platform.posix.SEEK_SET
import platform.posix.__error
import platform.posix.close
import platform.posix.fcntl
import platform.posix.flock
import platform.posix.lstat
import platform.posix.open
import platform.posix.realpath
import platform.posix.stat

internal actual suspend fun tryAcquireCrawlingLock(databasePath: String): AutoCloseable? = memScoped {
    val canonicalPath = checkNotNull(realpath(databasePath, allocArray<ByteVar>(PATH_MAX))) { "抓取数据库不存在" }.toKString()
    val info = alloc<stat>()
    /** 硬链接别名会分裂 sidecar 身份，不能安全恢复另一别名的任务。 */
    check(lstat(canonicalPath, info.ptr) == 0 && info.st_nlink.toInt() == 1) { "抓取数据库不支持硬链接" }
    val specification = alloc<flock>().apply {
        l_type = F_WRLCK.toShort()
        l_whence = SEEK_SET.toShort()
        l_start = 0L
        l_len = 1L
        l_pid = 0
    }
    val fd = open("$canonicalPath.crawling-lock", O_RDWR or O_CREAT or O_NOFOLLOW or O_CLOEXEC, 384)
    check(fd >= 0) { "无法打开抓取锁" }
    val code = try {
        if (fcntl(fd, F_SETLK, specification.ptr) == 0) {
            /** 与 JVM 共用 fcntl 字节区间；保留 inode，进程死亡由 OS 释放锁。 */
            return@memScoped AutoCloseable { check(close(fd) == 0) { "关闭抓取锁失败" } }
        }
        checkNotNull(__error()).pointed.value
    } catch (error: Throwable) {
        if (close(fd) != 0) error.addSuppressed(IllegalStateException("关闭抓取锁失败"))
        throw error
    }
    if (code == EACCES || code == EAGAIN) {
        check(close(fd) == 0) { "关闭抓取锁失败" }
        return@memScoped null
    }
    val error = IllegalStateException("无法取得抓取锁：errno=$code")
    if (close(fd) != 0) error.addSuppressed(IllegalStateException("关闭抓取锁失败"))
    throw error
}
