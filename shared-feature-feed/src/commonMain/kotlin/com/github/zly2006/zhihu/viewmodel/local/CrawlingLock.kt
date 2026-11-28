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

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex

/** POSIX 进程锁随同文件任一描述符关闭而释放，须在打开文件前排除本进程竞争者。 */
private val crawlingProcessGate = Mutex()

internal suspend fun withCrawlingLock(databasePath: String, block: suspend () -> Unit) {
    require(databasePath.isNotBlank()) { "抓取恢复需要持久化数据库" }
    if (!crawlingProcessGate.tryLock()) return
    try {
        tryAcquireCrawlingLock(databasePath).use { lock ->
            if (lock == null) return
            currentCoroutineContext().ensureActive()
            block()
        }
    } finally {
        crawlingProcessGate.unlock()
    }
}

internal expect suspend fun tryAcquireCrawlingLock(databasePath: String): AutoCloseable?
