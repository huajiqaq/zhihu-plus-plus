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
interface RoomBlockedContentRecordDao : BlockedContentRecordDao {
    @Query("SELECT * FROM ${BlockedContentRecord.TABLE_NAME} ORDER BY blockedTime DESC LIMIT ${BlockedContentRecord.MAX_RECORDS}")
    override fun observeRecentRecords(): Flow<List<BlockedContentRecord>>

    @Insert(entity = BlockedContentRow::class, onConflict = OnConflictStrategy.REPLACE)
    override suspend fun insertRecord(record: BlockedContentRecord): Long

    @Query("DELETE FROM ${BlockedContentRecord.TABLE_NAME} WHERE id = :id")
    override suspend fun deleteRecord(id: Long)

    @Query("DELETE FROM ${BlockedContentRecord.TABLE_NAME}")
    override suspend fun clearAllRecords()

    @Query("SELECT COUNT(*) FROM ${BlockedContentRecord.TABLE_NAME}")
    override suspend fun getRecordCount(): Int

    @Query(
        """
        DELETE FROM ${BlockedContentRecord.TABLE_NAME} 
        WHERE id IN (
            SELECT id FROM ${BlockedContentRecord.TABLE_NAME} 
            ORDER BY blockedTime ASC 
            LIMIT :deleteCount
        )
    """,
    )
    override suspend fun deleteOldestRecords(deleteCount: Int)
}
