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

package com.github.zly2006.zhihu.desktop

import com.github.zly2006.zhihu.notification.JvmNotificationFiles
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.FileAlreadyExistsException
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardOpenOption.WRITE

/** 跨进程只独占通知检查；普通启动仍创建窗口，不建立任意启动 IPC。 */
internal class DesktopNotificationInstance private constructor(
    private val channel: FileChannel,
    private val lock: FileLock,
) : AutoCloseable {
    override fun close() {
        lock.release()
        channel.close()
    }

    companion object {
        fun tryAcquire(): DesktopNotificationInstance? {
            val lockFile = JvmNotificationFiles.directory().resolve("desktop.lock")
            try {
                JvmNotificationFiles.createPrivateFile(lockFile).close()
            } catch (_: FileAlreadyExistsException) {
                /** 锁文件保留，不能删除正在被另一进程锁定的 inode。 */
            }
            JvmNotificationFiles.verifyPrivate(lockFile)
            val channel = FileChannel.open(lockFile, WRITE, NOFOLLOW_LINKS)
            try {
                val lock = channel.tryLock()
                if (lock == null) {
                    channel.close()
                    return null
                }
                return DesktopNotificationInstance(channel, lock)
            } catch (error: Exception) {
                channel.close()
                throw error
            }
        }
    }
}
