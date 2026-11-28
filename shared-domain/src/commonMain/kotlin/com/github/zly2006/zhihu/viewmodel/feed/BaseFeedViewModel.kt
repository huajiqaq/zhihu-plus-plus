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
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.viewModelScope
import com.github.zly2006.zhihu.data.AdvertisementFeed
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.flattenFeeds
import com.github.zly2006.zhihu.data.toDisplayItem
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.platform.UserMessageSink
import com.github.zly2006.zhihu.platform.platformSettingsStore
import com.github.zly2006.zhihu.viewmodel.FeedDisplaySettings
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.PaginationViewModel
import com.github.zly2006.zhihu.viewmodel.QualityFilterMode
import com.github.zly2006.zhihu.viewmodel.filter.BlockedTopic
import com.github.zly2006.zhihu.viewmodel.filter.BlockedUser
import com.github.zly2006.zhihu.viewmodel.filter.BlockedQuestionAuthor
import com.github.zly2006.zhihu.viewmodel.filter.extractTopicIds
import com.github.zly2006.zhihu.viewmodel.filter.ContentDetailProvider
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import com.github.zly2006.zhihu.viewmodel.getOrFetchContentDetail
import kotlin.reflect.typeOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray

abstract class BaseFeedViewModel : PaginationViewModel<Feed>(typeOf<Feed>()) {
    private val mutableDisplayItems = mutableStateListOf<FeedDisplayItem>()
    protected val committedDisplayItems: List<FeedDisplayItem> get() = mutableDisplayItems
    private class PagePreview(val items: List<FeedDisplayItem>, val isCurrent: () -> Boolean)
    private var pagePreview by mutableStateOf<PagePreview?>(null)
    protected open val previewBeforeFiltering: Boolean get() = false
    protected open val hasCommittedPageItems: Boolean get() = committedDisplayItems.isNotEmpty()
    protected val previewDisplayItems: List<FeedDisplayItem>?
        get() = pagePreview?.takeIf { it.isCurrent() && !platformSettingsStore().getBoolean("reverseBlock", false) }?.items
    open val displayItems: List<FeedDisplayItem>
        get() {
            val preview = previewDisplayItems?.takeIf { it.isNotEmpty() } ?: return committedDisplayItems
            return ((if (isRefreshPending) emptyList() else committedDisplayItems) + preview).distinctBy { it.stableKey }
        }
    var completedPageCount by mutableIntStateOf(0)
    var isPullToRefresh by mutableStateOf(false)
        protected set

    override fun previewPage(environment: PaginationEnvironment, data: List<Feed>, rawData: JsonArray): ((Throwable?) -> Unit)? {
        if (!previewBeforeFiltering) return null
        val settings = FeedDisplaySettings(platformSettingsStore())
        if (settings.reverseBlock) {
            pagePreview = null
            return null
        }
        val generation = pagingGeneration
        val client = environment.httpClient()
        val preview = PagePreview(createDisplayItems(settings, data)) {
            runCatching {
                generation == pagingGeneration && environment.accountClient?.isCurrent != false && environment.httpClient() === client
            }.getOrDefault(false)
        }
        pagePreview = preview
        return { error ->
            if (pagePreview === preview && (error == null || error is CancellationException || hasCommittedPageItems || !preview.isCurrent())) {
                pagePreview = null
            }
        }
    }

    override suspend fun prepareResponse(environment: PaginationEnvironment, data: List<Feed>, rawData: JsonArray): () -> Unit {
        val settings = FeedDisplaySettings(platformSettingsStore())
        val loadedItems = createDisplayItems(settings, data)
        return { commitDisplayPage(loadedItems, data, rawData) }
    }

    protected fun createDisplayItems(settings: FeedDisplaySettings, data: List<Feed>): List<FeedDisplayItem> = data
        .flattenFeeds()
        .mapNotNull { settings.applyQualityFilter(createDisplayItem(settings, it)) }
        .filterNot { it.feed is AdvertisementFeed && !settings.reverseBlock }

    public override fun resetForRefresh() {
        super.resetForRefresh()
        pagePreview = null
        isPullToRefresh = false
    }

    suspend fun pullToRefresh(environment: PaginationEnvironment) {
        if (isLoading) return
        resetForRefresh()
        isPullToRefresh = true
        val generation = pagingGeneration
        try {
            launchPageRequest(environment)?.join()
        } finally {
            if (generation == pagingGeneration) isPullToRefresh = false
        }
    }

    /** 所有挂起处理成功后一次提交；首个成功页替换旧内容，失败不消费刷新标记。 */
    protected fun commitDisplayPage(items: List<FeedDisplayItem>, data: List<Feed> = emptyList(), rawData: JsonArray = JsonArray(emptyList())) {
        val replacing = isRefreshPending
        if (replacing) {
            allData.clear()
            debugData.clear()
            isRefreshPending = false
        }
        allData.addAll(data)
        debugData.addAll(rawData)
        val keys = if (replacing) hashSetOf() else committedDisplayItems.mapTo(hashSetOf()) { it.stableKey }
        val additions = items.filterNot { it.feed is AdvertisementFeed && it.isFiltered }.filter { keys.add(it.stableKey) }
        updateDisplayItems {
            if (replacing) clear()
            addAll(additions)
        }
        completedPageCount++
    }

    open fun createDisplayItem(settings: FeedDisplaySettings, feed: Feed): FeedDisplayItem = feed.toDisplayItem(
        enableQualityFilter = settings.qualityFilterMode != QualityFilterMode.OFF,
        reverseBlock = settings.reverseBlock,
        qualityFilterSettings = settings.qualityFilter,
    )

    private fun updateDisplayItems(update: MutableList<FeedDisplayItem>.() -> Unit) {
        Snapshot.withMutableSnapshot {
            val previous = mutableDisplayItems.toList()
            mutableDisplayItems.update()
            if (previous != mutableDisplayItems.toList()) onDisplayItemsChanged()
        }
    }

    protected open fun onDisplayItemsChanged() = Unit

    protected open fun onFilterPolicyChanged() = Unit

    protected fun addDisplayItems(newItems: List<FeedDisplayItem>) {
        val keys = committedDisplayItems.mapTo(hashSetOf()) { it.stableKey }
        val additions = newItems.filterNot { it.feed is AdvertisementFeed && it.isFiltered }.filter { keys.add(it.stableKey) }
        updateDisplayItems { addAll(additions) }
    }

    protected fun replaceDisplayItems(items: List<FeedDisplayItem>) = updateDisplayItems {
        clear()
        addAll(items)
    }

    protected fun removeDisplayItems(predicate: (FeedDisplayItem) -> Boolean) = updateDisplayItems {
        removeAll(predicate)
    }

    /** 规则写入成功后才通知页面；首页据此复检并更新离线快照。 */
    suspend fun blockUser(userId: String, userName: String, urlToken: String?, avatarUrl: String?) {
        getContentFilterDatabase().blockedUserDao().insertUser(BlockedUser(userId, userName, urlToken, avatarUrl))
        onFilterPolicyChanged()
    }

    suspend fun blockQuestionAuthor(userId: String, userName: String, urlToken: String?, avatarUrl: String?) {
        getContentFilterDatabase().blockedQuestionAuthorDao().insertUser(BlockedQuestionAuthor(userId, userName, urlToken, avatarUrl))
        onFilterPolicyChanged()
    }

    fun removeItemsByBlockedTopic(topicId: String) {
        onFilterPolicyChanged()
        removeDisplayItems { item -> item.raw?.let(::extractTopicIds)?.contains(topicId) == true }
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
                userMessages.showLongMessage(DomainStrings.blockUserDataMissing)
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
                userMessages.showLongMessage(DomainStrings.blockQuestionAuthorMissing)
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
                userMessages.showLongMessage(DomainStrings.blockKeywordDataMissing)
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
                userMessages.showShortMessage(DomainStrings.topicBlocked(topicName))
                removeItemsByBlockedTopic(topicId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                userMessages.showShortMessage(DomainStrings.blockFailed(e.message.orEmpty()))
            }
        }
    }
}
