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

import com.github.zly2006.zhihu.account.ZhihuLoginRequiredException
import com.github.zly2006.zhihu.account.ZhihuRequestProfile
import com.github.zly2006.zhihu.data.CommonFeed
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.FeedFilterFacts
import com.github.zly2006.zhihu.data.Person
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.resolveContent
import com.github.zly2006.zhihu.util.DisplayFailure
import com.github.zly2006.zhihu.util.raiseForStatus
import com.github.zly2006.zhihu.util.signZhihuMobileRequest
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import io.ktor.client.call.body
import io.ktor.http.decodeURLPart
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 移动推荐只加载页面，不拥有列表、任务或分页状态。 */
internal object MobileHomeFeedSource : HomeFeedSource {
    override val initialUrl = "https://api.zhihu.com/topstory/recommend"

    override suspend fun load(environment: PaginationEnvironment, url: String): HomeFeedPage {
        if (environment.accountClient?.load()?.login == false) {
            throw ZhihuLoginRequiredException()
        }
        val response = environment.get(url, ZhihuRequestProfile.Mobile) {
            signZhihuMobileRequest(url, environment.authenticatedCookies())
        }.raiseForStatus()
        val json = response.body<JsonObject>()
        val data = json["data"]?.jsonArray ?: throw DisplayFailure { DomainStrings.missingList }
        val items = data.mapNotNull { element ->
            try {
                parseMobileHomeFeedDisplayItem(element.jsonObject)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                environment.logDecodeFailure("MobileHomeFeedSource", element, error)
                null
            }
        }
        return HomeFeedPage(items, emptyList(), data, decodeHomeFeedPaging(json))
    }
}

fun parseMobileHomeFeedDisplayItem(card: JsonObject): FeedDisplayItem? {
    if (card["type"]?.jsonPrimitive?.content != "ComponentCard") {
        return null
    }
    val route =
        card["action"]!!
            .jsonObject["parameter"]!!
            .jsonPrimitive.content
            .substringAfter("route_url=")
    val routeUrl = route.decodeURLPart()
    val routeDest = resolveContent(routeUrl) ?: return null
    val children = card["children"]?.jsonArray?.map { it.jsonObject } ?: return null
    val extra = card["extra"]?.let { runCatching { ZhihuJson.decodeJson<MobileHomeCardExtra>(it) }.getOrNull() }
    val passthrough = extra?.businessExtMap?.passthroughInfo ?: extra?.passthroughInfo
    val author = passthrough?.author
    val originalContent = extra
        ?.takeIf { routeDest is Pin }
        ?.businessExtMap
        ?.oriContent
        ?.takeIf { it.isNotBlank() }
        ?.let { encoded ->
            runCatching {
                ZhihuJson.decodeJson<MobileHomeOriginalContent>(
                    ZhihuJson.json.parseToJsonElement(Base64.Default.decode(encoded).decodeToString()),
                )
            }.getOrNull()
        }
    val title = if (routeDest is Pin) {
        passthrough
            ?.content
            ?.title
            ?.takeIf { it.isNotBlank() }
            ?: children
                .firstOrNull { it["id"]?.jsonPrimitive?.content == "Text" }
                ?.get("text")
                ?.jsonPrimitive
                ?.content
                .orEmpty()
    } else {
        children.joStrMatch("id", "Text")["text"]!!.jsonPrimitive.content
    }
    val summary = children.joStrMatch("id", "text_pin_summary")["text"]!!.jsonPrimitive.content
    val footer = children.filter { it["type"]!!.jsonPrimitive.content == "Line" }.getOrNull(1) ?: return null
    val footerLine = footer["elements"]!!.jsonArray.map { it.jsonObject }

    fun reactionCount(reaction: String) = footerLine
        .firstOrNull { it["reaction"]?.jsonPrimitive?.content == reaction }
        ?.get("count")
        ?.jsonPrimitive
        ?.intOrNull
        ?.takeIf { it >= 0 }
    val voteUpCount = reactionCount("Vote")
    val commentCount = reactionCount("Comment")
    val collectCount = reactionCount("Collect")
    val footerText = if (voteUpCount != null && commentCount != null && collectCount != null) {
        DomainStrings.mobileFeedCounts(voteUpCount, commentCount, collectCount)
    } else {
        footerLine
            .firstOrNull { it["type"]?.jsonPrimitive?.content == "Text" }
            ?.get("text")
            ?.jsonPrimitive
            ?.content
            .orEmpty()
    }
    val lineAuthor =
        children
            .first {
                it["style"]!!.jsonPrimitive.content.startsWith("RecommendAuthorLine") ||
                    it["style"]!!.jsonPrimitive.content.startsWith("LineAuthor_default")
            }["elements"]!!
            .jsonArray
            .map { it.jsonObject }
    val avatar = lineAuthor
        .joStrMatch("style", "Avatar_default")["image"]!!
        .jsonObject["url"]!!
        .jsonPrimitive.content
    val authorName = lineAuthor.joStrMatch("type", "Text")["text"]!!.jsonPrimitive.content
    if (routeDest is Article) {
        routeDest.authorName = authorName
        routeDest.title = title
        routeDest.avatarSrc = avatar
    }
    val feed = if (routeDest is Pin) {
        CommonFeed(
            id = card["id"]?.jsonPrimitive?.content.orEmpty(),
            target = Feed.PinTarget(
                id = routeDest.id,
                url = routeUrl,
                author = Person(
                    id = author?.id.orEmpty(),
                    url = author?.url.orEmpty(),
                    userType = "people",
                    urlToken = author?.urlToken,
                    name = authorName,
                    headline = "",
                    avatarUrl = avatar,
                    isFollowing = author?.isFollowing == true,
                    isFollowed = author?.isFollowed == true,
                ),
                content = buildList {
                    add(DataHolder.Pin.ContentText(title = title, content = summary))
                    originalContent
                        ?.mediaInfo
                        ?.images
                        .orEmpty()
                        .filter { it.url.isNotBlank() }
                        .forEachIndexed { index, image ->
                            add(
                                DataHolder.Pin.ContentImage(
                                    url = image.url,
                                    thumbnail = extra
                                        ?.businessExtMap
                                        ?.images
                                        ?.getOrNull(index)
                                        ?.url
                                        .orEmpty(),
                                    width = image.width,
                                    height = image.height,
                                ),
                            )
                        }
                },
                excerptTitle = summary,
            ),
        )
    } else {
        null
    }

    return FeedDisplayItem(
        destination = routeDest,
        avatarSrc = avatar,
        authorName = authorName,
        summary = summary,
        title = title,
        details = DomainStrings.mobileFeedAttribution(footerText),
        feed = feed,
        filterFacts = FeedFilterFacts(
            voteCount = voteUpCount ?: passthrough
                ?.reactions
                ?.get("vote")
                ?.count
                ?.takeIf { it >= 0 },
            authorId = author?.id?.takeIf(String::isNotBlank),
            authorIsFollowing = author?.isFollowing,
        ),
    )
}

@Serializable
private data class MobileHomeCardExtra(
    val businessExtMap: MobileHomeBusinessExtMap = MobileHomeBusinessExtMap(),
    val passthroughInfo: MobileHomePassthroughInfo? = null,
)

@Serializable
private data class MobileHomeBusinessExtMap(
    val oriContent: String = "",
    val images: List<MobileHomeImage> = emptyList(),
    val passthroughInfo: MobileHomePassthroughInfo? = null,
)

@Serializable
private data class MobileHomePassthroughInfo(
    val author: MobileHomeAuthor? = null,
    val content: MobileHomeContent? = null,
    val reactions: Map<String, MobileHomeReaction> = emptyMap(),
)

@Serializable
private data class MobileHomeReaction(
    val count: Int? = null,
)

@Serializable
private data class MobileHomeAuthor(
    val id: String = "",
    val url: String = "",
    val urlToken: String? = null,
    val isFollowing: Boolean? = null,
    val isFollowed: Boolean = false,
)

@Serializable
private data class MobileHomeContent(
    val title: String? = null,
)

@Serializable
private data class MobileHomeOriginalContent(
    val mediaInfo: MobileHomeMediaInfo? = null,
)

@Serializable
private data class MobileHomeMediaInfo(
    val images: List<MobileHomeImage> = emptyList(),
)

@Serializable
private data class MobileHomeImage(
    val url: String = "",
    val width: Int = 0,
    val height: Int = 0,
)

@Suppress("NOTHING_TO_INLINE")
private inline fun List<JsonObject>.joStrMatch(key: String, value: String): JsonObject =
    this.firstOrNull { it[key]?.jsonPrimitive?.content == value }
        ?: throw IllegalStateException("No matching JsonObject found for $key = $value: $this")
