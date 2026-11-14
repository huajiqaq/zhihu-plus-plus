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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface LocalContentDao {
    @Query("SELECT file FROM pragma_database_list WHERE name = 'main'")
    suspend fun databaseFilePath(): String

    @Query("UPDATE crawling_tasks SET status = 'NotStarted', errorMessage = NULL WHERE status = 'InProgress'")
    suspend fun recoverAbandonedTasks(): Int

    @Query("SELECT * FROM crawling_tasks WHERE status = :status ORDER BY priority DESC, createdAt ASC")
    suspend fun getTasksByStatus(status: CrawlingStatus): List<CrawlingTask>

    @Query("SELECT COUNT(*) FROM crawling_tasks WHERE reason = :reason AND status = :status")
    suspend fun getTaskCountByReasonAndStatus(reason: CrawlingReason, status: CrawlingStatus): Int

    @Insert
    suspend fun insertTasks(tasks: List<CrawlingTask>)

    @Query("UPDATE crawling_tasks SET status = 'InProgress', executedAt = :executedAt, errorMessage = NULL WHERE id = :id AND status = 'NotStarted' AND executedAt IS :previousExecution AND (executedAt IS NULL OR executedAt < :executedAt)")
    suspend fun claimTask(id: Long, previousExecution: Long?, executedAt: Long): Int

    @Query("UPDATE crawling_tasks SET status = :status, errorMessage = :errorMessage WHERE id = :id AND status = 'InProgress' AND executedAt = :executedAt")
    suspend fun finishTask(id: Long, executedAt: Long, status: CrawlingStatus, errorMessage: String? = null): Int

    @Transaction
    suspend fun completeTask(id: Long, executedAt: Long, results: List<CrawlingResult>) {
        if (finishTask(id, executedAt, CrawlingStatus.Completed) == 1) insertResults(results)
    }

    @Query("DELETE FROM crawling_tasks WHERE status = :status AND createdAt < :before")
    suspend fun cleanupOldTasks(status: CrawlingStatus, before: Long)

    @Query("SELECT * FROM crawling_results WHERE reason = :reason ORDER BY score DESC, createdAt DESC")
    suspend fun getResultsByReason(reason: CrawlingReason): List<CrawlingResult>

    @Query("SELECT * FROM crawling_results ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentResults(limit: Int): List<CrawlingResult>

    @Query("SELECT COUNT(*) FROM crawling_results WHERE reason = :reason")
    suspend fun getResultCountByReason(reason: CrawlingReason): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResults(results: List<CrawlingResult>)

    @Query("DELETE FROM crawling_results WHERE createdAt < :before")
    suspend fun cleanupOldResults(before: Long)

    @Query("SELECT * FROM local_feeds WHERE id = :feedId LIMIT 1")
    suspend fun getFeedById(feedId: String): LocalFeed?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeed(feed: LocalFeed)

    @Query("UPDATE local_feeds SET userFeedback = :feedback WHERE id = :feedId")
    suspend fun updateFeedFeedback(feedId: String, feedback: Double)

    @Query("DELETE FROM local_feeds WHERE createdAt < :before")
    suspend fun cleanupOldFeeds(before: Long)

    @Insert
    suspend fun insertBehavior(behavior: UserBehavior)

    @Query("SELECT * FROM user_behaviors WHERE action = :action AND timestamp > :since")
    suspend fun getBehaviorsByActionSince(action: String, since: Long): List<UserBehavior>

    @Query("SELECT * FROM user_behaviors WHERE timestamp > :since")
    suspend fun getBehaviorsSince(since: Long): List<UserBehavior>

    @Query("SELECT contentId, COUNT(*) as count FROM user_behaviors WHERE action = 'like' GROUP BY contentId ORDER BY count DESC LIMIT :limit")
    suspend fun getMostLikedContent(limit: Int): List<ContentLikeCount>

    @Query("DELETE FROM user_behaviors WHERE timestamp < :before")
    suspend fun cleanupOldBehaviors(before: Long)
}

data class ContentLikeCount(
    val contentId: String,
    val count: Int,
)
