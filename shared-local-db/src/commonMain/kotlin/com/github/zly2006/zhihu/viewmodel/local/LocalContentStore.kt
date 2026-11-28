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

expect fun getLocalContentDatabase(): LocalContentDatabase

interface LocalContentDatabase {
    fun closeDatabase()

    fun contentDao(): LocalContentDao
}

interface LocalContentDao {
    suspend fun databaseFilePath(): String

    suspend fun recoverAbandonedTasks(): Int

    suspend fun getTasksByStatus(status: CrawlingStatus): List<CrawlingTask>

    suspend fun getTaskCountByReasonAndStatus(reason: CrawlingReason, status: CrawlingStatus): Int

    suspend fun insertTasks(tasks: List<CrawlingTask>)

    suspend fun claimTask(id: Long, previousExecution: Long?, executedAt: Long): Int

    suspend fun finishTask(id: Long, executedAt: Long, status: CrawlingStatus, errorMessage: String? = null): Int

    suspend fun completeTask(id: Long, executedAt: Long, results: List<CrawlingResult>)

    suspend fun cleanupOldTasks(status: CrawlingStatus, before: Long)

    suspend fun getResultsByReason(reason: CrawlingReason): List<CrawlingResult>

    suspend fun getRecentResults(limit: Int): List<CrawlingResult>

    /** 既有排序可能选中旧行；过滤使用同一内容最近入库的有效依据。 */
    suspend fun getLatestFilterFactResults(contentIds: List<String>): List<CrawlingResult>

    suspend fun getResultCountByReason(reason: CrawlingReason): Int

    suspend fun insertResults(results: List<CrawlingResult>)

    suspend fun cleanupOldResults(before: Long)

    suspend fun getFeedById(feedId: String): LocalFeed?

    suspend fun insertFeed(feed: LocalFeed)

    suspend fun updateFeedFeedback(feedId: String, feedback: Double)

    suspend fun cleanupOldFeeds(before: Long)

    suspend fun insertBehavior(behavior: UserBehavior)

    suspend fun getBehaviorsByActionSince(action: String, since: Long): List<UserBehavior>

    suspend fun getBehaviorsSince(since: Long): List<UserBehavior>

    suspend fun getMostLikedContent(limit: Int): List<ContentLikeCount>

    suspend fun cleanupOldBehaviors(before: Long)
}

data class ContentLikeCount(
    val contentId: String,
    val count: Int,
)
