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

package com.github.zly2006.zhihu.viewmodel

import com.github.zly2006.zhihu.data.Feed
import kotlinx.serialization.Serializable

data class CollectionHtmlExportProgress(
    val totalCount: Int,
    val processedCount: Int,
    val successCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val currentTitle: String = "",
)

data class CollectionHtmlExportResult(
    val totalCount: Int,
    val successCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val zipFilePath: String?,
)

interface CollectionExportEnvironment {
    suspend fun exportCollectionItemsToHtmlZip(
        collectionTitle: String,
        items: List<CollectionItem>,
        includeImages: Boolean,
        onProgress: suspend (CollectionHtmlExportProgress) -> Unit,
    ): CollectionHtmlExportResult

    suspend fun handleCollectionExportFailure(error: Exception)
}

interface CollectionContentEnvironment :
    PaginationEnvironment,
    CollectionExportEnvironment

@Serializable
class CollectionItem(
    val created: String,
    val content: Feed.Target,
)
