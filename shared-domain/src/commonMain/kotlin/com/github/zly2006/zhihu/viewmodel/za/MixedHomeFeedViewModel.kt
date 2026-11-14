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

package com.github.zly2006.zhihu.viewmodel.za

import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.filter.RemoteHistorySync
import com.github.zly2006.zhihu.util.Log
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import com.github.zly2006.zhihu.viewmodel.feed.BaseFeedViewModel
import com.github.zly2006.zhihu.viewmodel.feed.HomeFeedInteractionViewModel
import com.github.zly2006.zhihu.viewmodel.feed.HomeFeedViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope

class MixedHomeFeedViewModel(
    remoteHistory: RemoteHistorySync = RemoteHistorySync(),
) : BaseFeedViewModel(),
    HomeFeedInteractionViewModel {
    val android = AndroidHomeFeedViewModel(remoteHistory)
    val web = HomeFeedViewModel(remoteHistory)
    override val initialUrl: String
        get() = "https://www.zhihu.com/api/v3/feed/topstory/recommend?desktop=true&limit=10"

    init {
        android.displayItems = this.displayItems
        web.displayItems = this.displayItems
        android.latestLoadedDisplayItems = this.latestLoadedDisplayItems
        web.latestLoadedDisplayItems = this.latestLoadedDisplayItems
        // 两来源写同一列表，第一份成功响应只能清空一次；各自持有 pending 标记会追加旧数据或互相覆盖。
        android.pendingRefresh = this.pendingRefresh
        web.pendingRefresh = this.pendingRefresh
    }

    internal override fun resetForRefresh() {
        android.resetForRefresh()
        web.resetForRefresh()
        super.resetForRefresh()
    }

    override suspend fun fetchFeeds(environment: PaginationEnvironment) {
        // 两条链路必须互不取消：Android 端接口失败时不能连带取消 Web 推荐。
        supervisorScope {
            val androidJob = async { runFeed("AndroidHomeFeedViewModel") { android.fetchFeeds(environment) } }
            val webJob = async { runFeed("HomeFeedViewModel") { web.fetchFeeds(environment) } }
            androidJob.await()
            webJob.await()
        }
    }

    private suspend fun runFeed(tag: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Mixed feed source failed", e)
        }
    }

    override fun onUiContentClick(environment: ZhihuApiEnvironment, feed: Feed) {
        web.onUiContentClick(environment, feed)
    }
}
