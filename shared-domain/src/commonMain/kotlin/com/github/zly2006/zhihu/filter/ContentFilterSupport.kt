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

package com.github.zly2006.zhihu.filter

import com.github.zly2006.zhihu.util.Log
import com.github.zly2006.zhihu.viewmodel.LoadState
import com.github.zly2006.zhihu.viewmodel.filter.ContentFilterDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.flow.onStart

data class ContentFilterStats(
    val totalRecords: Int,
    val filteredCount: Int,
    val filterRate: Float,
)

suspend fun ContentFilterDao.loadFilterStats(): ContentFilterStats {
    val totalRecords = getRecordCount()
    val filteredCount = getFilteredContent().size
    return ContentFilterStats(
        totalRecords = totalRecords,
        filteredCount = filteredCount,
        filterRate = if (totalRecords > 0) filteredCount.toFloat() / totalRecords else 0f,
    )
}

suspend fun ContentFilterDao.clearAllData(): ContentFilterStats {
    clearAllRecords()
    return loadFilterStats()
}

/** 无数据库平台的 DAO 返回 emptyFlow()，按空列表显示以免页面一直加载。 */
fun <T> Flow<List<T>>.asListLoadState(): Flow<LoadState<List<T>>> =
    onEmpty { emit(emptyList()) }
        .map<List<T>, LoadState<List<T>>> {
            currentCoroutineContext().ensureActive()
            LoadState.Ready(it)
        }.onStart { emit(LoadState.Loading) }
        .catch { e ->
            if (e is CancellationException) throw e
            currentCoroutineContext().ensureActive()
            Log.e("ContentFilterSupport", "Failed to load local filter data", e)
            emit(LoadState.Failed(e.message ?: e.toString()))
        }
