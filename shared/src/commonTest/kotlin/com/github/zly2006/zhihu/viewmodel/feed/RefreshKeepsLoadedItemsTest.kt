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
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #597：网络失败时刷新不应清空已加载内容。
 */
class RefreshKeepsLoadedItemsTest {
    private class TestFeedViewModel : BaseFeedViewModel() {
        override val initialUrl = "https://example.invalid/feed"
        var failNextFetch = false
        var nextItems: List<FeedDisplayItem> = emptyList()

        override suspend fun fetchFeeds(environment: PaginationEnvironment) {
            if (failNextFetch) error("network unavailable")
            addDisplayItems(nextItems)
        }
    }

    @Test
    fun failedRefreshKeepsExistingItems() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = TestFeedViewModel()
            viewModel.addDisplayItems(listOf(item("a"), item("b")))
            viewModel.failNextFetch = true

            viewModel.refresh(environment())
            advanceUntilIdle()

            assertEquals(listOf("a", "b"), viewModel.displayItems.map { it.title })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun successfulRefreshReplacesExistingItems() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = TestFeedViewModel()
            viewModel.addDisplayItems(listOf(item("a"), item("b")))
            viewModel.nextItems = listOf(item("c"))

            viewModel.refresh(environment())
            advanceUntilIdle()

            assertEquals(listOf("c"), viewModel.displayItems.map { it.title })
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun item(title: String) = FeedDisplayItem(
        title = title,
        summary = null,
        details = "",
        feed = null,
    )

    private fun environment() = object : PaginationEnvironment {
        override fun httpClient() = HttpClient(MockEngine { error("Unexpected HTTP request") })

        override fun authenticatedCookies() = emptyMap<String, String>()

        override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
    }
}
