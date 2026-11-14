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
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.toFeedDisplayItemNavDestinationJson
import com.github.zly2006.zhihu.platform.isJvm
import com.github.zly2006.zhihu.platform.isNative
import com.github.zly2006.zhihu.util.Log
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.feed.BaseFeedViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

class LocalHomeFeedViewModel(
    private val accountStore: ZhihuAccountStore,
) : BaseFeedViewModel() {
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
            if (!::recommendationEngine.isInitialized) {
                val dao = (getLocalContentDatabase() ?: error("LocalRecommendationEngine is required for local home feed")).contentDao()
                recommendationEngine = LocalRecommendationEngine(dao, CrawlingExecutor(dao, accountStore), viewModelScope)
            }
            val recommendations = recommendationEngine.generateRecommendations(20)
            currentCoroutineContext().ensureActive()
            if (generation != pagingGeneration || environment.httpClient() !== client) return
            recommendationResults.clear()

            if (recommendations.isEmpty()) {
                generateFallbackContent(environment, generation, client)
            } else {
                val loadedItems = recommendations.map { entry ->
                    FeedDisplayItem(
                        title = entry.feed.title,
                        summary = entry.feed.summary,
                        details = entry.feed.reasonDisplay,
                        feed = null,
                        navDestinationJson = entry.navDestination?.toFeedDisplayItemNavDestinationJson(),
                        isFiltered = false,
                    ).also { item ->
                        recommendationResults[item.stableKey] = entry.result
                    }
                }
                addDisplayItems(loadedItems)
                latestLoadedDisplayItems.value = loadedItems
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (generation != pagingGeneration || environment.httpClient() !== client) return
            if (isJvm || isNative) {
                environment.handleFetchFailure("LocalHomeFeedViewModel", e)
            } else {
                Log.e("LocalHomeFeedViewModel", "Error fetching local feeds", e)
            }
            currentCoroutineContext().ensureActive()
            if (generation != pagingGeneration || environment.httpClient() !== client) return
            if (e.message?.contains("does not exist. Is Room annotation processor correctly configured?") == true) {
                environment.showLocalRecommendationDatabaseError()
            }
            generateFallbackContent(environment, generation, client)
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

    private suspend fun generateFallbackContent(environment: PaginationEnvironment, generation: Int, client: io.ktor.client.HttpClient) {
        val fallbackItems = listOf(
            FeedDisplayItem(
                title = "本地推荐正在建立候选池",
                summary = "系统会先抓取关注动态、热门内容和相关话题，再根据你的点击逐步调整排序。",
                details = "本地推荐 · 冷启动",
                feed = null,
                isFiltered = false,
            ),
            FeedDisplayItem(
                title = "你的行为只在本地学习",
                summary = "点开内容会影响后续排序，但这些学习信号不会作为推荐特征上传到服务器。",
                details = "本地推荐 · 隐私优先",
                feed = null,
                isFiltered = false,
            ),
        )

        fallbackItems.forEach { item ->
            currentCoroutineContext().ensureActive()
            if (generation != pagingGeneration || environment.httpClient() !== client) return
            if (displayItems.none { existing -> existing.stableKey == item.stableKey }) {
                displayItems.add(item)
            }
            delay(300)
        }
        if (generation == pagingGeneration && environment.httpClient() === client) latestLoadedDisplayItems.value = fallbackItems
    }
}
