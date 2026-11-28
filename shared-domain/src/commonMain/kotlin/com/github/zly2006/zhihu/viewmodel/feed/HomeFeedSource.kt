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

import com.github.zly2006.zhihu.data.AdvertisementFeed
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.ZhihuPaging
import com.github.zly2006.zhihu.data.flattenFeeds
import com.github.zly2006.zhihu.data.toDisplayItem
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.util.DisplayFailure
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

internal data class HomeFeedPage(
    val items: List<FeedDisplayItem>,
    val data: List<Feed>,
    val rawData: JsonArray,
    val paging: ZhihuPaging?,
)

internal interface HomeFeedSource {
    val initialUrl: String
    suspend fun load(environment: PaginationEnvironment, url: String): HomeFeedPage
}

internal object WebHomeFeedSource : HomeFeedSource {
    override val initialUrl = "https://api.zhihu.com/topstory/recommend"

    override suspend fun load(environment: PaginationEnvironment, url: String): HomeFeedPage {
        val json = environment.fetchJson(url.replace("http://", "https://"), "data[*].content,excerpt,headline,target.author.badge_v2")
            ?: throw DisplayFailure { DomainStrings.emptyResponse }
        val rawData = json["data"] as? JsonArray ?: throw DisplayFailure { DomainStrings.missingList }
        val data = rawData.mapNotNull { element ->
            if ((element as? JsonObject)?.get("type")?.jsonPrimitive?.content in listOf("invited_answer", "tab_list", "feed_item_index_group")) return@mapNotNull null
            try {
                ZhihuJson.decodeJson<Feed>(element)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                environment.logDecodeFailure("WebHomeFeedSource", element, error)
                null
            }
        }
        // 结构广告保留原始身份交给统一规则分流，不能先发布到列表。
        val items = data.flattenFeeds().map { it.toDisplayItem(enableQualityFilter = false, reverseBlock = it is AdvertisementFeed) }
        return HomeFeedPage(items, data, rawData, decodeHomeFeedPaging(json))
    }
}

internal fun decodeHomeFeedPaging(json: JsonObject): ZhihuPaging? = (json["paging"] as? JsonObject)?.let { value ->
    val terminal = (value["is_end"] ?: value["isEnd"])?.jsonPrimitive?.booleanOrNull == true
    ZhihuJson.decodeJson<ZhihuPaging>(if (terminal && "next" !in value) JsonObject(value + ("next" to JsonPrimitive(""))) else value)
}
