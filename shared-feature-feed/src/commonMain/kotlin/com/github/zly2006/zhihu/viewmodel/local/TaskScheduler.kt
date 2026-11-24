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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TaskScheduler(
    private val dao: LocalContentDao,
    private val crawlingExecutor: CrawlingExecutor,
    private val scope: CoroutineScope,
) {
    private var schedulerJob: Job? = null

    fun startScheduling() {
        schedulerJob?.cancel()
        schedulerJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                try {
                    executePendingTasks()
                    cleanupLocalRecommendationData(dao)
                    delay(60_000)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    delay(300_000)
                }
            }
        }
    }

    private suspend fun executePendingTasks() {
        crawlingExecutor.recoverAbandonedTasks()
        val pendingTasks = dao.getTasksByStatus(CrawlingStatus.NotStarted)

        pendingTasks.take(3).forEach { task ->
            try {
                crawlingExecutor.executeTask(task)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
        }
    }
}
