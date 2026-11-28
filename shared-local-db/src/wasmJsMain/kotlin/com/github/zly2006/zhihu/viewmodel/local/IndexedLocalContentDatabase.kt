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

import com.github.zly2006.zhihu.data.indexeddb.IdTable
import com.github.zly2006.zhihu.data.indexeddb.IndexedDatabase
import com.github.zly2006.zhihu.data.indexeddb.IndexedTransaction
import com.github.zly2006.zhihu.data.indexeddb.TextTable
import com.github.zly2006.zhihu.data.indexeddb.sqlLimit
import com.github.zly2006.zhihu.data.indexeddb.sqliteTextOrder

private val tasks = IdTable("crawling_tasks", CrawlingTask.serializer(), CrawlingTask::id, { row, id -> row.copy(id = id) })
private val results = IdTable("crawling_results", CrawlingResult.serializer(), CrawlingResult::id, { row, id -> row.copy(id = id) })
private val feeds = TextTable("local_feeds", LocalFeed.serializer(), LocalFeed::id)
private val behaviors = IdTable("user_behaviors", UserBehavior.serializer(), UserBehavior::id, { row, id -> row.copy(id = id) })

private val browserLocalDatabase by lazy { IndexedLocalContentDatabase("zhihu-plus-local-content") }

actual fun getLocalContentDatabase(): LocalContentDatabase = browserLocalDatabase

internal class IndexedLocalContentDatabase(
    name: String,
) : LocalContentDatabase,
    LocalContentDao,
    AutoCloseable {
    private val db = IndexedDatabase(name, tasks, results, feeds, behaviors)

    override fun contentDao(): LocalContentDao = this

    /** 抓取锁以同源持久库为粒度；浏览器没有 SQLite 文件路径。 */
    override suspend fun databaseFilePath() = "indexeddb:${db.name}"

    override suspend fun recoverAbandonedTasks() = db.write(tasks) {
        val abandoned = rows(tasks).filter { it.status == CrawlingStatus.InProgress }
        abandoned.forEach { task -> change(tasks, task.id) { it.copy(status = CrawlingStatus.NotStarted, errorMessage = null) } }
        abandoned.size
    }

    override suspend fun getTasksByStatus(status: CrawlingStatus) = db.read(tasks) {
        rows(tasks).filter { it.status == status }.sortedWith(compareByDescending<CrawlingTask> { it.priority }.thenBy { it.createdAt })
    }

    override suspend fun getTaskCountByReasonAndStatus(reason: CrawlingReason, status: CrawlingStatus) = db.read(tasks) {
        rows(tasks).count { it.reason == reason && it.status == status }
    }

    override suspend fun insertTasks(tasks: List<CrawlingTask>) = db.write(taskTable) { tasks.forEach { insert(taskTable, it) } }

    override suspend fun claimTask(id: Long, previousExecution: Long?, executedAt: Long) = db.write(tasks) {
        change(tasks, id) { task ->
            if (task.status == CrawlingStatus.NotStarted && task.executedAt == previousExecution && (previousExecution == null || previousExecution < executedAt)) {
                task.copy(status = CrawlingStatus.InProgress, executedAt = executedAt, errorMessage = null)
            } else {
                null
            }
        }
    }

    private suspend fun IndexedTransaction.finish(id: Long, executedAt: Long, status: CrawlingStatus, errorMessage: String?) = change(tasks, id) { task ->
        if (task.status == CrawlingStatus.InProgress && task.executedAt == executedAt) task.copy(status = status, errorMessage = errorMessage) else null
    }

    override suspend fun finishTask(id: Long, executedAt: Long, status: CrawlingStatus, errorMessage: String?) = db.write(tasks) {
        finish(id, executedAt, status, errorMessage)
    }

    private suspend fun IndexedTransaction.saveResults(values: List<CrawlingResult>) = values.forEach {
        require(!it.score.isNaN()) { "crawling_results.score cannot be NULL" }
        insert(resultTable, it, replace = true)
    }

    override suspend fun completeTask(id: Long, executedAt: Long, results: List<CrawlingResult>) = db.write(tasks, resultTable) {
        if (finish(id, executedAt, CrawlingStatus.Completed, null) == 1) saveResults(results)
    }

    override suspend fun cleanupOldTasks(status: CrawlingStatus, before: Long) = db.write(tasks) {
        deleteWhere(tasks) { it.status == status && it.createdAt < before }
    }

    override suspend fun getResultsByReason(reason: CrawlingReason) = db.read(results) {
        rows(results).filter { it.reason == reason }.sortedWith(compareByDescending<CrawlingResult> { it.score }.thenByDescending { it.createdAt })
    }

    override suspend fun getRecentResults(limit: Int) = db.read(results) { rows(results).sortedByDescending { it.createdAt }.sqlLimit(limit) }

    override suspend fun getLatestFilterFactResults(contentIds: List<String>) = db.read(results) {
        val ids = contentIds.toSet()
        rows(results)
            .filter { it.contentId in ids && it.filterFactsJson != null }
            .groupBy { it.contentId }
            .values
            .map { it.maxBy(CrawlingResult::id) }
    }

    override suspend fun getResultCountByReason(reason: CrawlingReason) = db.read(results) { rows(results).count { it.reason == reason } }

    override suspend fun insertResults(results: List<CrawlingResult>) = db.write(resultTable) { saveResults(results) }

    override suspend fun cleanupOldResults(before: Long) = db.write(results) { deleteWhere(results) { it.createdAt < before } }

    override suspend fun getFeedById(feedId: String) = db.read(feeds) { get(feeds, feedId) }

    override suspend fun insertFeed(feed: LocalFeed) {
        require(!feed.userFeedback.isNaN()) { "local_feeds.userFeedback cannot be NULL" }
        db.write(feeds) { replace(feeds, feed) }
    }

    override suspend fun updateFeedFeedback(feedId: String, feedback: Double) = db.write(feeds) {
        change(feeds, feedId) {
            require(!feedback.isNaN()) { "local_feeds.userFeedback cannot be NULL" }
            it.copy(userFeedback = feedback)
        }
    }

    override suspend fun cleanupOldFeeds(before: Long) = db.write(feeds) { deleteWhere(feeds) { it.createdAt < before } }

    override suspend fun insertBehavior(behavior: UserBehavior) {
        db.write(behaviors) { insert(behaviors, behavior) }
    }

    override suspend fun getBehaviorsByActionSince(action: String, since: Long) = db.read(behaviors) {
        rows(behaviors).filter { it.action == action && it.timestamp > since }
    }

    override suspend fun getBehaviorsSince(since: Long) = db.read(behaviors) { rows(behaviors).filter { it.timestamp > since } }

    override suspend fun getMostLikedContent(limit: Int) = db.read(behaviors) {
        rows(behaviors)
            .filter { it.action == "like" }
            .groupingBy { it.contentId }
            .eachCount()
            .map { ContentLikeCount(it.key, it.value) }
            .sortedWith(compareByDescending<ContentLikeCount> { it.count }.thenByDescending(sqliteTextOrder) { it.contentId })
            .sqlLimit(limit)
    }

    override suspend fun cleanupOldBehaviors(before: Long) = db.write(behaviors) { deleteWhere(behaviors) { it.timestamp < before } }

    override fun closeDatabase() = db.close()

    override fun close() = closeDatabase()
}

private val taskTable get() = tasks
private val resultTable get() = results
