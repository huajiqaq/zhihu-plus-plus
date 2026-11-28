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

import com.github.zly2006.zhihu.viewmodel.currentEpochMillis
import kotlinx.serialization.Serializable

@Serializable
data class CrawlingTask(
    val id: Long = 0,
    val url: String,
    val reason: CrawlingReason,
    val status: CrawlingStatus = CrawlingStatus.NotStarted,
    val priority: Int = 0,
    val createdAt: Long = currentEpochMillis(),
    val executedAt: Long? = null,
    val errorMessage: String? = null,
)

@Serializable
data class CrawlingResult(
    val id: Long = 0,
    val taskId: Long,
    /** 内容类型与 ID 组成的键，如 `answer:12345678`。 */
    val contentId: String,
    val title: String,
    val summary: String,
    val url: String,
    val reason: CrawlingReason,
    val score: Double = 0.0,
    val createdAt: Long = currentEpochMillis(),
    /** 来源列表提供的过滤依据；旧候选和未提供依据的内容保持为空。 */
    val filterFactsJson: String? = null,
)

@Serializable
data class LocalFeed(
    val id: String,
    val resultId: Long,
    val title: String,
    val summary: String,
    val reasonDisplay: String,
    val navDestination: String?,
    val userFeedback: Double = 0.0,
    val createdAt: Long = currentEpochMillis(),
)

@Serializable
data class UserBehavior(
    val id: Long = 0,
    val contentId: String,
    val action: String, // "like", "dislike", "share", "comment", "view"
    val timestamp: Long = currentEpochMillis(),
    val duration: Long? = null, // 阅读时长，毫秒
)

enum class CrawlingStatus {
    NotStarted,
    InProgress,
    Completed,
    Failed,
}

enum class CrawlingReason(
    val defaultWeight: Double,
    val displayText: String,
) {
    /** 已关注用户的内容。 */
    Following(1.2, "关注用户的最新动态"),

    /** 官方推荐的内容。 */
    Trending(1.0, "热门推荐"),

    /** 已关注用户的赞同。 */
    FollowingUpvote(0.88, "关注用户点赞的内容"),

    /** 用户赞同了某个回答，相同问题的优质回答。 */
    UpvotedQuestion(0.95, "相关问题的优质回答"),

    /** 相似用户点赞的内容。 */
    CollaborativeFiltering(0.8, "相似用户喜欢的内容"),
}
