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

package com.github.zly2006.zhihu.viewmodel.feed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.github.zly2006.zhihu.data.AdvertisementFeed
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.flattenFeeds
import com.github.zly2006.zhihu.data.toDisplayItem
import com.github.zly2006.zhihu.platform.UserMessageSink
import com.github.zly2006.zhihu.platform.platformSettingsStore
import com.github.zly2006.zhihu.viewmodel.FeedDisplaySettings
import com.github.zly2006.zhihu.viewmodel.HomeFeedFilterResult
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.PaginationViewModel
import com.github.zly2006.zhihu.viewmodel.QualityFilterMode
import com.github.zly2006.zhihu.viewmodel.filter.BlockedTopic
import com.github.zly2006.zhihu.viewmodel.filter.ContentDetailProvider
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import com.github.zly2006.zhihu.viewmodel.getOrFetchContentDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlin.reflect.typeOf

abstract class BaseFeedViewModel : PaginationViewModel<Feed>(typeOf<Feed>()) {
    var displayItems = mutableStateListOf<FeedDisplayItem>()
    var latestLoadedDisplayItems = mutableStateOf<List<FeedDisplayItem>>(emptyList())
    var completedPageCount by mutableIntStateOf(0)
    var isPullToRefresh by mutableStateOf(false)
        protected set

    /** 刷新首屏成功前保留旧列表，网络失败时仍可阅读已加载内容。 */
    internal var pendingRefresh = mutableStateOf(false)

    override fun processResponse(environment: PaginationEnvironment, data: List<Feed>, rawData: JsonArray) {
        super.processResponse(environment, data, rawData)
        val settings = FeedDisplaySettings(platformSettingsStore())
        val loadedItems = data
            .flattenFeeds()
            .map { createDisplayItem(settings, it) }
            .filterNot {
                (it.feed is AdvertisementFeed && !settings.reverseBlock) ||
                    (it.isQualityFiltered && settings.qualityFilterMode == QualityFilterMode.HIDE)
            }
        addDisplayItems(loadedItems)
        latestLoadedDisplayItems.value = loadedItems
    }

    public override fun resetForRefresh() {
        super.resetForRefresh()
        pendingRefresh.value = true
        isPullToRefresh = false
    }

    suspend fun pullToRefresh(environment: PaginationEnvironment) {
        if (isLoading) return
        resetForRefresh()
        isPullToRefresh = true
        isLoading = true
        val generation = pagingGeneration
        val client = environment.httpClient()
        try {
            fetchFeeds(environment)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (generation == pagingGeneration && environment.accountClient?.isCurrent != false && environment.httpClient() === client) errorHandle(e)
        } finally {
            if (generation == pagingGeneration && environment.accountClient?.isCurrent != false && environment.httpClient() === client) {
                isLoading = false
                isPullToRefresh = false
            }
        }
    }

    open fun createDisplayItem(settings: FeedDisplaySettings, feed: Feed): FeedDisplayItem = feed.toDisplayItem(
        enableQualityFilter = settings.qualityFilterMode != QualityFilterMode.OFF,
        reverseBlock = settings.reverseBlock,
        qualityFilterSettings = settings.qualityFilter,
    )

    fun addDisplayItems(newItems: List<FeedDisplayItem>) {
        if (pendingRefresh.value) {
            // 第一页成功返回后才替换旧列表；失败时 addDisplayItems 不会被调用。
            displayItems.clear()
            pendingRefresh.value = false
        }
        newItems.forEach {
            // 首页会在后台过滤前插入卡片；只跳过普通模式结构广告，不吞掉 RULES 质量占位。
            if (it.feed is AdvertisementFeed && it.isFiltered) return@forEach
            if (displayItems.none { existing -> existing.stableKey == it.stableKey }) {
                displayItems.add(it)
            }
        }
    }

    fun handleBlockUser(
        environment: PaginationEnvironment,
        userMessages: UserMessageSink,
        feedItem: FeedDisplayItem,
        onShowDialog: (Pair<String, String>) -> Unit,
    ) {
        viewModelScope.launch {
            val authorInfo = resolveFeedBlockAuthorInfo(
                feedItem,
                ContentDetailProvider(environment::getOrFetchContentDetail),
            )
            if (authorInfo != null) {
                onShowDialog(authorInfo)
            } else {
                userMessages.showLongMessage("无法获取屏蔽用户所需的数据，请尝试进入内容详情页操作")
            }
        }
    }

    fun handleBlockQuestionAuthor(
        environment: PaginationEnvironment,
        userMessages: UserMessageSink,
        feedItem: FeedDisplayItem,
        onShowDialog: (Pair<String, String>) -> Unit,
    ) {
        viewModelScope.launch {
            val authorInfo = resolveFeedQuestionAuthorInfo(
                feedItem,
                ContentDetailProvider(environment::getOrFetchContentDetail),
            )
            if (authorInfo != null) {
                onShowDialog(authorInfo)
            } else {
                userMessages.showLongMessage("当前条目没有可用的提问者数据，无法屏蔽提问者")
            }
        }
    }

    fun handleBlockByKeywords(
        environment: PaginationEnvironment,
        userMessages: UserMessageSink,
        feedItem: FeedDisplayItem,
        onShowDialog: (Pair<FeedDisplayItem, Triple<String, String, String?>>) -> Unit,
    ) {
        viewModelScope.launch {
            val contentInfo = resolveFeedKeywordBlockingContent(
                feedItem,
                ContentDetailProvider(environment::getOrFetchContentDetail),
            )
            if (contentInfo != null) {
                onShowDialog(feedItem to contentInfo)
            } else {
                userMessages.showLongMessage("无法获取关键词屏蔽所需的数据，请尝试进入内容详情页操作")
            }
        }
    }

    fun handleBlockTopic(
        userMessages: UserMessageSink,
        topicId: String,
        topicName: String,
    ) {
        viewModelScope.launch {
            try {
                getContentFilterDatabase()
                    .blockedTopicDao()
                    .insertTopic(BlockedTopic(topicId = topicId, topicName = topicName))
                userMessages.showShortMessage("已屏蔽主题「$topicName」")
                removeFeedItemsByBlockedTopic(this@BaseFeedViewModel, topicId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                userMessages.showShortMessage("屏蔽失败: ${e.message}")
            }
        }
    }
}

/** 只更新当前代次的前台卡片，保留无关条目和已有原始内容。 */
internal fun MutableList<FeedDisplayItem>.replaceHomeFeedItemsWithFilteredResult(filterResult: HomeFeedFilterResult) {
    if (filterResult.reverseBlock) return

    val foregroundKeys = filterResult.foregroundItems.map { it.stableKey }.toSet()
    val filteredItemsByKey = filterResult.filteredItems.associateBy { it.stableKey }
    var index = 0
    while (index < size) {
        val item = this[index]
        if (item.stableKey !in foregroundKeys) {
            index++
            continue
        }

        val filteredVersion = filteredItemsByKey[item.stableKey]
        if (filteredVersion == null) {
            removeAt(index)
        } else {
            this[index] = filteredVersion.copy(raw = filteredVersion.raw ?: item.raw)
            index++
        }
    }
}
