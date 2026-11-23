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
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.sourceLabel
import com.github.zly2006.zhihu.data.target
import com.github.zly2006.zhihu.viewmodel.FeedDisplaySettings
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class FollowViewModel : BaseFeedViewModel() {
    override val initialUrl: String
        get() = "https://www.zhihu.com/api/v3/moments?limit=10&desktop=true"

    override fun createDisplayItem(settings: FeedDisplaySettings, feed: Feed): FeedDisplayItem {
        val item = super.createDisplayItem(settings, feed)
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

class FollowPinViewModel : BaseFeedViewModel() {
    override val initialUrl: String
        get() = "https://api.zhihu.com/moments_v3?feed_type=pin"

    /** 续页使用服务端完整 next，不覆盖其中的查询参数。 */
    override val include: String
        get() = if (lastPaging == null) super.include else ""

    /** 官方关注注册表的同型标识共用已有模型，原始响应仍由分页层保留。 */
    override fun decodePage(environment: PaginationEnvironment, rawData: JsonArray): List<Feed> {
        val normalized = JsonArray(
            rawData.map { item ->
                val objectValue = item as? JsonObject ?: return@map item
                val values = objectValue.toMutableMap()
                if ((values["type"] as? JsonPrimitive)?.content in setOf("moments_top_feed", "moments_recommend_feed")) {
                    values["type"] = JsonPrimitive("moments_feed")
                }
                val target = values["target"] as? JsonObject
                if (target != null && (target["type"] as? JsonPrimitive)?.content == "moments_pin") {
                    values["target"] = JsonObject(target + ("type" to JsonPrimitive("pin")))
                }
                JsonObject(values)
            },
        )
        val data = super.decodePage(environment, normalized)
        check(rawData.isEmpty() || data.isNotEmpty()) { "关注想法暂不支持此响应，请重试" }
        return data
    }

    override fun processResponse(environment: PaginationEnvironment, data: List<Feed>, rawData: JsonArray) {
        super.processResponse(environment, data, rawData)
        rawData.forEach { raw ->
            val target = (raw as? JsonObject)?.get("target") as? JsonObject ?: return@forEach
            if ((target["type"] as? JsonPrimitive)?.content !in setOf("pin", "moments_pin")) return@forEach
            val pin = runCatching { ZhihuJson.decodeJson<DataHolder.Pin>(target) }.getOrNull() ?: return@forEach
            displayItems.firstOrNull { (it.feed?.target as? Feed.PinTarget)?.id?.toString() == pin.id }?.raw = pin
        }
        completedPageCount++
    }
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

    /** brief 是服务端的不透明动态令牌，不能从 actor id 推导。 */
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
                    environment.post("https://api.zhihu.com/moments/recent/read") {
                        url { parameters["brief"] = previous.brief }
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
        if (isLoading) return
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            try {
                val json = environment.fetchJson("https://api.zhihu.com/moments/recent?type=raw", "")
                    ?: error("关注动态响应不可用")
                val dataArray = json["data"]?.jsonArray ?: error("关注动态响应缺少列表")
                val loadedUsers = dataArray
                    .mapNotNull { item ->
                        try {
                            ZhihuJson.decodeJson<FollowingUserItem>(item)
                        } catch (e: Exception) {
                            environment.logDecodeFailure("RecentMomentsVM", item, e)
                            null
                        }
                    }.distinctBy { it.actor.id }
                users.clear()
                users.addAll(loadedUsers)
                errorMessage = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                environment.handleFetchFailure("RecentMomentsVM", e)
                errorMessage = "加载关注动态失败：${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
}
