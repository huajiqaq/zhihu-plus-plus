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

package com.github.zly2006.zhihu.ui

import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.RecommendationMode
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.util.AppLog
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

private const val HOME_FEED_STARTUP_CACHE_FILE_PREFIX = "home_feed_startup_cache_"
private const val HOME_FEED_STARTUP_CACHE_FILE_SUFFIX = ".json"
private const val HOME_FEED_STARTUP_SNAPSHOT_MAX_ITEMS = 10
private const val HOME_FEED_STARTUP_SNAPSHOT_VERSION = 2

internal val HOME_FEED_FILTER_SETTING_KEYS = setOf(
    "qualityFilterMode",
    "answerVoteupThreshold",
    "articleVoteupThreshold",
    "articleFollowersThreshold",
    "videoVoteThreshold",
    "videoFollowersThreshold",
    "questionAnswerThreshold",
    "questionFollowersThreshold",
    "enableContentFilter",
    "reverseBlock",
    "filterFollowedUserContent",
    "enableKeywordBlocking",
    "enableNLPBlocking",
    "nlpSimilarityThreshold",
    "enableUserBlocking",
    "enableTopicBlocking",
    "topicBlockingThreshold",
    "blockZhihuAdPlatform",
    "blockZhihuSchool",
    "blockWeChatOfficialAccount",
    "blockPaidContent",
)

internal fun shouldContinueEmptyHomeFeed(
    mode: RecommendationMode,
    completedPageCount: Int,
    isEmpty: Boolean,
    hasNextPage: Boolean,
    isLoading: Boolean,
    hasError: Boolean,
): Boolean = mode != RecommendationMode.LOCAL && completedPageCount > 0 && isEmpty && hasNextPage && !isLoading && !hasError

@Serializable
private data class HomeFeedStartupSnapshot(
    val version: Int,
    val filterFingerprint: String,
    val items: List<FeedDisplayItem>,
)

fun homeFeedStartupCacheFileName(recommendationMode: RecommendationMode, owner: String? = null): String =
    HOME_FEED_STARTUP_CACHE_FILE_PREFIX + recommendationMode.key + owner?.let { "_$it" }.orEmpty() + HOME_FEED_STARTUP_CACHE_FILE_SUFFIX

fun encodeHomeFeedStartupSnapshot(items: List<FeedDisplayItem>, filterFingerprint: String): String? {
    // raw 的多态内容自带 type 字段，会与 kotlinx.serialization 的默认类型判别字段冲突；启动恢复也不需要这份可重新获取的详情缓存。
    val snapshotItems = items.take(HOME_FEED_STARTUP_SNAPSHOT_MAX_ITEMS).map { it.copy(raw = null) }

    return try {
        ZhihuJson.json.encodeToString(
            HomeFeedStartupSnapshot(HOME_FEED_STARTUP_SNAPSHOT_VERSION, filterFingerprint, snapshotItems),
        )
    } catch (error: Exception) {
        AppLog.e("HomeFeedStartupSnapshot", "Failed to encode home feed startup snapshot", error)
        null
    }
}

fun decodeHomeFeedStartupSnapshot(serialized: String?, filterFingerprint: String): List<FeedDisplayItem> {
    if (serialized.isNullOrBlank()) return emptyList()

    return runCatching {
        val snapshot = ZhihuJson.json.decodeFromString<HomeFeedStartupSnapshot>(serialized)
        if (snapshot.version != HOME_FEED_STARTUP_SNAPSHOT_VERSION || snapshot.filterFingerprint != filterFingerprint) {
            return@runCatching emptyList()
        }
        /** 旧 RULES 卡片把过滤说明写进正文，不能把这份有损结果当成可复检内容。 */
        if (snapshot.items.any { it.isQualityFiltered && it.qualityFilterReason == null }) return@runCatching emptyList()
        snapshot.items
    }.getOrDefault(emptyList())
}
