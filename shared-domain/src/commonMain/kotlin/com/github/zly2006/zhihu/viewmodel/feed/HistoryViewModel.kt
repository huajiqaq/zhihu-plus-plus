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

import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.HistoryType
import com.github.zly2006.zhihu.data.LocalHistoryStorage
import com.github.zly2006.zhihu.data.navDestination
import com.github.zly2006.zhihu.data.toFeedDisplayItemNavDestinationJson
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.Topic
import com.github.zly2006.zhihu.navigation.Video
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment

class HistoryViewModel : BaseFeedViewModel() {
    override val initialUrl: String get() = error("本地历史不请求网络")
    override val isEnd: Boolean get() = true
    private val storage by lazy { LocalHistoryStorage() }

    override fun refresh(environment: PaginationEnvironment) {
        if (isLoading) return
        isLoading = true
        errorMessage = null
        try {
            val entries = storage.history.map { destination ->
                val type = HistoryType.from(destination)
                val title = when (destination) {
                    is Article -> destination.title
                    is Question -> destination.title
                    is Person -> destination.name
                    is Topic -> destination.name
                    is Video -> "视频 ${destination.id}"
                    is Pin -> "想法 ${destination.id}"
                    else -> type
                }
                FeedDisplayItem(
                    title = title.takeUnless { it.isBlank() || it == "loading..." } ?: type,
                    summary = (destination as? Article)?.excerpt.orEmpty(),
                    details = type,
                    authorName = when (destination) {
                        is Article -> destination.authorName
                        is Pin -> destination.authorName
                        else -> null
                    },
                    feed = null,
                    avatarSrc = (destination as? Article)?.avatarSrc,
                    navDestinationJson = destination.toFeedDisplayItemNavDestinationJson(),
                    historyType = type,
                )
            }
            displayItems.clear()
            displayItems.addAll(entries)
        } catch (error: Exception) {
            errorMessage = error.message ?: "本地历史读取失败"
        } finally {
            isLoading = false
        }
    }

    fun removeItems(keys: Set<String>) {
        val destinations = displayItems.filter { it.stableKey in keys }.mapNotNullTo(linkedSetOf()) { it.navDestination }
        if (destinations.isEmpty()) return
        storage.remove(destinations)
        displayItems.removeAll { it.stableKey in keys }
    }

    override suspend fun fetchFeeds(environment: PaginationEnvironment) = Unit

    override fun loadMore(environment: PaginationEnvironment) = Unit
}
