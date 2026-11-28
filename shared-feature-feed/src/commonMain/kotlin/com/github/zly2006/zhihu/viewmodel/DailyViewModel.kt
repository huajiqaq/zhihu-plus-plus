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

package com.github.zly2006.zhihu.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.github.zly2006.zhihu.data.DailySection
import com.github.zly2006.zhihu.data.DailyStoriesResponse
import com.github.zly2006.zhihu.data.fetchDailyStoriesBefore
import com.github.zly2006.zhihu.data.fetchLatestDailyStories
import com.github.zly2006.zhihu.i18n.FeedStrings
import com.github.zly2006.zhihu.util.AppLog
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

class DailyViewModel : ViewModel() {
    var sections by mutableStateOf<List<DailySection>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var isLoadingMore by mutableStateOf(false)
        private set
    private var loadFailure by mutableStateOf<Exception?>(null)
    val error: String? get() = loadFailure?.let { FeedStrings.dailyLoadError(it.message.orEmpty()) }
    private var earlierFailure by mutableStateOf<Exception?>(null)
    val loadMoreError: String? get() = earlierFailure?.let { FeedStrings.earlierDailyError(it.message.orEmpty()) }
    var isEnd by mutableStateOf(false)
        private set
    private var nextDate: String? = null
    private var requestedDate: String? = null

    suspend fun loadLatest(httpClient: HttpClient) {
        requestedDate = null
        isLoading = true
        loadFailure = null
        try {
            val data: DailyStoriesResponse = httpClient.fetchLatestDailyStories()
            sections = if (data.stories.isEmpty()) emptyList() else listOf(DailySection(data.date, data.stories))
            nextDate = data.date
            earlierFailure = null
            isEnd = data.stories.isEmpty()
            loadFailure = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loadFailure = e
        } finally {
            isLoading = false
        }
    }

    suspend fun loadDate(httpClient: HttpClient, date: String) {
        requestedDate = date
        isLoading = true
        loadFailure = null
        try {
            val nextApiDate = LocalDate
                .parse("${date.substring(0, 4)}-${date.substring(4, 6)}-${date.substring(6, 8)}")
                .plus(1, DateTimeUnit.DAY)
                .toString()
                .replace("-", "")
            val data: DailyStoriesResponse = httpClient.fetchDailyStoriesBefore(nextApiDate)
            sections = if (data.stories.isEmpty()) emptyList() else listOf(DailySection(data.date, data.stories))
            nextDate = data.date
            earlierFailure = null
            isEnd = data.stories.isEmpty()
            loadFailure = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loadFailure = e
        } finally {
            isLoading = false
        }
    }

    suspend fun retry(httpClient: HttpClient) {
        val date = requestedDate
        if (date == null) loadLatest(httpClient) else loadDate(httpClient, date)
    }

    suspend fun retryLoadMore(httpClient: HttpClient) {
        earlierFailure = null
        loadMore(httpClient)
    }

    suspend fun loadMore(httpClient: HttpClient) {
        val date = nextDate ?: return
        if (isLoading || isLoadingMore || isEnd || loadMoreError != null || error != null) return
        isLoadingMore = true
        try {
            val data: DailyStoriesResponse = httpClient.fetchDailyStoriesBefore(date)
            if (data.stories.isNotEmpty() && sections.none { it.date == data.date }) {
                sections = sections + DailySection(data.date, data.stories)
            }
            isEnd = data.stories.isEmpty() || data.date >= date
            nextDate = data.date
            earlierFailure = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("DailyViewModel", "Failed to load more daily stories", e)
            earlierFailure = e
        } finally {
            isLoadingMore = false
        }
    }
}
