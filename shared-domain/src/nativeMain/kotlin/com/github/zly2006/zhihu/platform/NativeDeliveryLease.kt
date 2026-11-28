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

import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

/**
 * 系统读者持有独立附件副本；页面取消只结束等待，不能推断分享扩展已经读完。
 * 程序关闭面板未收到终态时保留系统临时目录，由系统清理而不猜测读取期限。
 */
internal class NativeDeliveryLease private constructor(val filePath: String, private val directory: String) {
    private var released = false

    fun release() {
        if (released) return
        released = true
        if (!NSFileManager.defaultManager.removeItemAtPath(directory, null)) {
            AppLog.e("NativeFileDelivery", "System delivery cache cleanup failed", IllegalStateException(DomainStrings.exportCleanupFailed))
        }
    }

    companion object {
        suspend fun copyOf(source: String): NativeDeliveryLease {
            var pending: NativeDeliveryLease? = null
            try {
                return withContext(Dispatchers.Default) {
                    val directory = "${NSTemporaryDirectory().trimEnd('/')}/zhihu-system-delivery-${NSUUID().UUIDString}"
                    check(NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)) { DomainStrings.exportCacheDirectoryFailed }
                    val target = "$directory/${source.substringAfterLast('/')}"
                    val lease = NativeDeliveryLease(target, directory)
                    pending = lease
                    SystemFileSystem.source(Path(source)).buffered().use { input ->
                        SystemFileSystem.sink(Path(target)).buffered().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.readAtMostTo(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    lease
                }
            } catch (error: Throwable) {
                pending?.release()
                throw error
            }
        }
    }
}
