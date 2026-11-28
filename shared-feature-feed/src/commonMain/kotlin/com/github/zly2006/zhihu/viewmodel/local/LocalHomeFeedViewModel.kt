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

import androidx.lifecycle.viewModelScope
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.account.accountIdentityKey
import com.github.zly2006.zhihu.data.ContentDetailCache
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.pinSummaryText
import com.github.zly2006.zhihu.i18n.FeedStrings
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.platform.isJvm
import com.github.zly2006.zhihu.platform.isNative
import com.github.zly2006.zhihu.util.AppLog
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.feed.HomeFeedStateViewModel
import com.github.zly2006.zhihu.viewmodel.filter.ContentDetailProvider
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

class LocalHomeFeedViewModel(
    private val accountStore: ZhihuAccountStore,
) : HomeFeedStateViewModel() {
    private lateinit var recommendationEngine: LocalRecommendationEngine
    private val recommendationResults = mutableMapOf<String, CrawlingResult>()

    override val initialUrl: String
        get() = error("LocalHomeFeedViewModel should not be used directly. Use LocalFeedViewModel instead.")

    override fun loadMore(environment: PaginationEnvironment) {
        if (displayItems.isEmpty()) {
            super.loadMore(environment)
        }
    }

    override suspend fun fetchFeeds(environment: PaginationEnvironment) {
        val generation = pagingGeneration
        val client = environment.httpClient()
        try {
            val dao = getLocalContentDatabase().contentDao()
            if (!::recommendationEngine.isInitialized) {
                recommendationEngine = LocalRecommendationEngine(dao, CrawlingExecutor(dao, accountStore), viewModelScope)
            }
            val recommendations = recommendationEngine.generateRecommendations(20)
            currentCoroutineContext().ensureActive()
            if (generation != pagingGeneration || environment.httpClient() !== client) return
            if (recommendations.isEmpty()) {
                recommendationResults.clear()
                generateFallbackContent(environment, generation, client)
            } else {
                val loadedItems = dao.toLocalDisplayItems(recommendations, accountStore.session.accountIdentityKey())
                publishHomePage(environment, generation, client, loadedItems, detailProvider = ContentDetailProvider { destination -> ContentDetailCache.getOrFetch(client, destination) { null } }) {
                    recommendationResults.clear()
                    loadedItems.forEachIndexed { index, item -> recommendationResults[item.stableKey] = recommendations[index].result }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (generation != pagingGeneration || environment.httpClient() !== client) return
            if (isJvm || isNative) {
                environment.handleFetchFailure("LocalHomeFeedViewModel", e)
            } else {
                AppLog.e("LocalHomeFeedViewModel", "Error fetching local feeds", e)
            }
            currentCoroutineContext().ensureActive()
            if (generation != pagingGeneration || environment.httpClient() !== client) return
            if (e.message?.contains("does not exist. Is Room annotation processor correctly configured?") == true) {
                environment.showLocalRecommendationDatabaseError()
            }
            if (displayItems.isEmpty()) generateFallbackContent(environment, generation, client)
        } finally {
            if (generation == pagingGeneration && environment.httpClient() === client) isLoading = false
        }
    }

    fun onLocalItemOpened(item: FeedDisplayItem) {
        val result = recommendationResults[item.stableKey] ?: return
        if (!::recommendationEngine.isInitialized) {
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            recommendationEngine.recordContentOpened(result.contentId, result.reason)
        }
    }

    private suspend fun generateFallbackContent(environment: PaginationEnvironment, generation: Int, client: HttpClient) {
        val fallbackItems = listOf(
            FeedDisplayItem(
                title = FeedStrings.localFeedWarming,
                summary = FeedStrings.localFeedWarmingHint,
                details = FeedStrings.localFeedWarmingLabel,
                feed = null,
                isFiltered = false,
            ),
            FeedDisplayItem(
                title = FeedStrings.localFeedPrivate,
                summary = FeedStrings.localFeedPrivateHint,
                details = FeedStrings.localFeedPrivateLabel,
                feed = null,
                isFiltered = false,
            ),
        )

        publishHomeNotice(environment, generation, client, fallbackItems)
    }
}

internal suspend fun LocalContentDao.toLocalDisplayItems(recommendations: List<LocalRecommendationEntry>, accountId: String?): List<FeedDisplayItem> {
    val facts = getLatestFilterFactResults(recommendations.map { it.result.contentId }).associate { it.contentId to it.filterFactsFor(accountId) }
    return recommendations.map { entry ->
        FeedDisplayItem(
            title = entry.feed.title,
            summary = if (entry.navDestination is Pin) entry.feed.summary.pinSummaryText() else entry.feed.summary,
            details = entry.feed.reasonDisplay,
            feed = null,
            destination = entry.navDestination,
            filterFacts = facts[entry.result.contentId],
        )
    }
}
