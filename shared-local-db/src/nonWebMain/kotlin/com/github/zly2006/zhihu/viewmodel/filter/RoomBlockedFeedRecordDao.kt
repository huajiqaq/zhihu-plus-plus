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
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomBlockedFeedRecordDao : BlockedFeedRecordDao {
    @Query("SELECT * FROM ${BlockedFeedRecord.TABLE_NAME} ORDER BY blockedTime DESC")
    override fun observeAll(): Flow<List<BlockedFeedRecord>>

    @Insert(entity = BlockedFeedRow::class, onConflict = OnConflictStrategy.REPLACE)
    override suspend fun insert(record: BlockedFeedRecord): Long

    @Query("DELETE FROM ${BlockedFeedRecord.TABLE_NAME} WHERE id = :id")
    override suspend fun deleteById(id: Long)

    @Query("DELETE FROM ${BlockedFeedRecord.TABLE_NAME}")
    override suspend fun clearAll()

    @Query(
        """
        DELETE FROM ${BlockedFeedRecord.TABLE_NAME}
        WHERE id NOT IN (
            SELECT id FROM ${BlockedFeedRecord.TABLE_NAME}
            ORDER BY blockedTime DESC
            LIMIT ${BlockedFeedRecord.MAX_RECORDS}
        )
    """,
    )
    override suspend fun maintainLimit()
}
