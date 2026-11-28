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

package com.github.zly2006.zhihu.viewmodel.filter

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RoomContentFilterDao : ContentFilterDao {
    @Query("SELECT * FROM ${ContentViewRecord.TABLE_NAME} WHERE id = :id")
    override suspend fun getViewRecord(id: String): ContentViewRecord?

    @Insert(entity = ViewRow::class, onConflict = OnConflictStrategy.REPLACE)
    override suspend fun insertOrUpdateViewRecord(record: ContentViewRecord)

    @Query("UPDATE ${ContentViewRecord.TABLE_NAME} SET viewCount = viewCount + 1, lastViewTime = :currentTime WHERE id = :id")
    override suspend fun incrementViewCount(id: String, currentTime: Long)

    @Query("UPDATE ${ContentViewRecord.TABLE_NAME} SET hasInteraction = 1, lastInteractionTime = :currentTime WHERE id = :id")
    override suspend fun markAsInteracted(id: String, currentTime: Long)

    @Query("SELECT * FROM ${ContentViewRecord.TABLE_NAME} WHERE viewCount > :maxCount")
    override suspend fun getFilteredContent(maxCount: Int): List<ContentViewRecord>

    @Query("SELECT id FROM ${ContentViewRecord.TABLE_NAME} WHERE id IN (:ids)")
    override suspend fun getViewedContentIdsByIds(ids: List<String>): List<String>

    @Query("DELETE FROM ${ContentViewRecord.TABLE_NAME}")
    override suspend fun clearAllRecords()

    @Query("SELECT COUNT(*) FROM ${ContentViewRecord.TABLE_NAME}")
    override suspend fun getRecordCount(): Int
}
