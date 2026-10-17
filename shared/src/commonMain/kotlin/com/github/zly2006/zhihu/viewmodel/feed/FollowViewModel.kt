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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.executeZhihuAuthenticatedRequest
import com.github.zly2006.zhihu.data.sourceLabel
import com.github.zly2006.zhihu.data.target
import com.github.zly2006.zhihu.util.signZhihuFetchRequest
import com.github.zly2006.zhihu.viewmodel.FeedDisplayEnvironment
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class FollowViewModel : BaseFeedViewModel() {
    override val initialUrl: String
        get() = "https://www.zhihu.com/api/v3/moments?limit=10&desktop=true"

    override fun createDisplayItem(environment: FeedDisplayEnvironment, feed: Feed): FeedDisplayItem {
        val item = super.createDisplayItem(environment, feed)
        return if (item.isFiltered || feed.sourceLabel == null) {
            item
        } else {
            item.copy(details = feed.target?.detailsText ?: item.details)
        }
    }
}

class FollowRecommendViewModel : BaseFeedViewModel() {
    override val initialUrl: String
        get() = "https://api.zhihu.com/moments_v3?feed_type=recommend"
}

class RecentMomentsViewModel : ViewModel() {
    @Serializable
    data class Actor(
        val id: String,
        val urlToken: String,
        val name: String,
        val avatarUrl: String,
    )

    @Serializable
    data class FollowingUserItem(
        val actor: Actor,
        val unreadCount: Int,
        val brief: String = "",
    )

    var users = mutableStateListOf<FollowingUserItem>()
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    /**
     * 点击头像后先本地清掉红点，再用服务端返回的 brief 标记该用户动态已读。
     * brief 是服务端给这条最近动态的不透明 token，不能由 actor id 推导。
     */
    fun markRead(
        environment: ZhihuApiEnvironment,
        actorId: String,
    ) {
        val index = users.indexOfFirst { it.actor.id == actorId }
        if (index == -1) return
        val previous = users[index]
        if (previous.unreadCount <= 0) return
        users[index] = previous.copy(unreadCount = 0)
        if (previous.brief.isBlank()) return
        viewModelScope.launch {
            try {
                val response = environment.withAuthenticatedClient { client, cookies ->
                    executeZhihuAuthenticatedRequest(client, "https://api.zhihu.com/moments/recent/read") {
                        method = HttpMethod.Post
                        url { parameters["brief"] = previous.brief }
                        signZhihuFetchRequest(cookies)
                    }
                }
                val acknowledged = ZhihuJson.json
                    .parseToJsonElement(response.bodyAsText())
                    .jsonObject["success"]
                    ?.jsonPrimitive
                    ?.booleanOrNull == true
                check(response.status.value in 200..299 && acknowledged) {
                    "标记关注动态已读失败（${response.status.value}）"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val currentIndex = users.indexOfFirst { it.actor.id == actorId }
                if (currentIndex != -1 && users[currentIndex].unreadCount == 0) {
                    users[currentIndex] = users[currentIndex].copy(unreadCount = previous.unreadCount)
                }
                environment.handleFetchFailure("RecentMomentsVM", e)
            }
        }
    }

    fun load(environment: ZhihuApiEnvironment) {
        if (isLoading || users.isNotEmpty()) return
        isLoading = true
        viewModelScope.launch {
            try {
                val json = environment.fetchJson("https://api.zhihu.com/moments/recent?type=raw", "") ?: return@launch
                val dataArray = json["data"]?.jsonArray ?: return@launch
                users.addAll(
                    dataArray.mapNotNull { item ->
                        try {
                            ZhihuJson.decodeJson<FollowingUserItem>(item)
                        } catch (e: Exception) {
                            environment.logDecodeFailure("RecentMomentsVM", item, e)
                            null
                        }
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                environment.handleFetchFailure("RecentMomentsVM", e)
                errorMessage = "加载关注动态失败"
            } finally {
                isLoading = false
            }
        }
    }
}
