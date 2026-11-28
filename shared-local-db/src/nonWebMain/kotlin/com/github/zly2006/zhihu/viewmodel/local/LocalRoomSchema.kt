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

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_feeds", primaryKeys = ["id"])
internal class FeedRow(
    @Embedded val value: LocalFeed,
)

@Entity(tableName = "crawling_tasks")
internal class TaskRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val url: String,
    val reason: CrawlingReason,
    val status: CrawlingStatus,
    val priority: Int,
    val createdAt: Long,
    val executedAt: Long?,
    val errorMessage: String?,
)

@Entity(tableName = "crawling_results")
internal class ResultRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val taskId: Long,
    val contentId: String,
    val title: String,
    val summary: String,
    val url: String,
    val reason: CrawlingReason,
    val score: Double,
    val createdAt: Long,
    val filterFactsJson: String?,
)

@Entity(tableName = "user_behaviors")
internal class BehaviorRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val contentId: String,
    val action: String,
    val timestamp: Long,
    val duration: Long?,
)
