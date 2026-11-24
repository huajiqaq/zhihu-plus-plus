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

import com.fleeksoft.ksoup.Ksoup
import com.github.zly2006.zhihu.data.AdvertisementFeed
import com.github.zly2006.zhihu.data.CommonFeed
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.FeedItemIndexGroup
import com.github.zly2006.zhihu.data.MomentsFeed
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.sourceLabel
import com.github.zly2006.zhihu.data.target
import com.github.zly2006.zhihu.data.toDisplayItem
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.platform.platformSettingsStore
import com.github.zly2006.zhihu.util.extractImageUrl
import com.github.zly2006.zhihu.viewmodel.FeedDisplaySettings
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.PaginationViewModel
import com.github.zly2006.zhihu.viewmodel.QualityFilterMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.reflect.typeOf

@Serializable
data class ActivityActor(
    val id: String = "",
    val urlToken: String? = null,
    val name: String = "",
    val avatarUrl: String = "",
    val badgeV2: DataHolder.BadgeV2? = null,
) {
    val destination: Person?
        get() = if (id.isNotBlank() || !urlToken.isNullOrBlank()) Person(id.ifBlank { Person.EMPTY_ID }, urlToken.orEmpty(), name) else null
}

/** 动作人、动作时间来自动态外层；互动数属于目标，未下发和零不能混用。 */
data class PeopleActivity(
    val item: FeedDisplayItem,
    val actor: ActivityActor?,
    val action: String?,
    val time: Long?,
    val quote: String?,
    val imageUrls: List<String>,
    val likes: Int?,
    val favorites: Int?,
    val comments: Int?,
) {
    companion object {
        fun decode(raw: JsonObject, person: Person, settings: FeedDisplaySettings): PeopleActivity? {
            val feed = ZhihuJson.decodeJson<Feed>(raw)
            val base = feed.toDisplayItem(
                enableQualityFilter = settings.qualityFilterMode != QualityFilterMode.OFF,
                reverseBlock = settings.reverseBlock,
                qualityFilterSettings = settings.qualityFilter,
            )
            if ((feed is AdvertisementFeed && !settings.reverseBlock) || (base.isQualityFiltered && settings.qualityFilterMode == QualityFilterMode.HIDE)) return null
            val fields = ZhihuJson.decodeJson<ActivityFields>(raw)
            val actors = fields.actors.orEmpty()
            val actor = actors.firstOrNull {
                (it.id.isNotBlank() && it.id == person.id) || (!it.urlToken.isNullOrBlank() && it.urlToken == person.urlToken)
            } ?: actors.singleOrNull()
            val time = when (feed) {
                is CommonFeed -> feed.createdTime
                is FeedItemIndexGroup -> feed.actionTime
                else -> null
            }?.takeIf { it > 0 && fields.showActorTime != false }
            val target = feed.target
            val counts = fields.target
            val likes = when (target) {
                is Feed.PinTarget -> counts?.likeCount
                is Feed.VideoTarget -> counts?.voteCount
                else -> counts?.voteupCount
            }?.takeIf { it >= 0 }
            val favorites = counts?.favoriteCount?.takeIf { it >= 0 }
            val comments = counts?.commentCount?.takeIf { it >= 0 }
            val details = listOfNotNull(
                target?.description(),
                likes?.let { "$it 赞" },
                favorites?.let { "$it 收藏" },
                comments?.let { "$it 评论" },
            ).joinToString(" · ")
            val id = when (feed) {
                is CommonFeed -> feed.id
                is MomentsFeed -> feed.id
                is FeedItemIndexGroup -> feed.id
                else -> null
            }
            val pin = target as? Feed.PinTarget
            val quote = pin?.content?.filterIsInstance<DataHolder.Pin.ContentLinkCard>()?.firstNotNullOfOrNull { it.dataDraftTitle.takeIf(String::isNotBlank) }
            val item = base.copy(
                title = if (target is Feed.VideoTarget) target.title else base.title,
                summary = (if (target is Feed.VideoTarget || base.isQualityFiltered) target?.excerpt else base.summary)?.takeUnless { it.isBlank() || it == quote },
                details = if (target == null) base.details else details,
                historyKey = id?.takeIf(String::isNotBlank)?.let { "activity:$it" },
            )
            return PeopleActivity(item, actor, feed.sourceLabel, time, quote, if (base.isFiltered) emptyList() else target.activityImages(counts?.imageUrl), likes, favorites, comments)
        }
    }
}

@Serializable
private data class ActivityFields(
    val actors: List<ActivityActor>? = null,
    val showActorTime: Boolean? = null,
    val target: Target? = null,
) {
    @Serializable
    data class Target(
        val likeCount: Int? = null,
        val voteupCount: Int? = null,
        val voteCount: Int? = null,
        val favoriteCount: Int? = null,
        val commentCount: Int? = null,
        val imageUrl: String? = null,
    )
}

/** 仅展示实际交付的图片；thumbnail_info.count 可能大于缩略图数组，不用它补图或标注剩余张数。 */
private fun Feed.Target?.activityImages(cover: String?): List<String> {
    fun Sequence<String>.visible() = filter { it.startsWith("https://") || it.startsWith("http://") }.distinct().take(2).toList()
    val explicit = when (this) {
        is Feed.PinTarget ->
            return content
                .asSequence()
                .filterIsInstance<DataHolder.Pin.ContentImage>()
                .map { it.url.ifBlank { it.thumbnail } }
                .visible()
        is Feed.AnswerTarget ->
            thumbnailInfo
                ?.thumbnails
                .orEmpty()
                .asSequence()
                .filterIsInstance<DataHolder.ThumbnailRich>()
                .map { it.url }
                .visible()
                .ifEmpty { thumbnails.asSequence().visible() }
        else -> emptyList()
    }
    if (explicit.isNotEmpty()) return explicit
    val html = when (this) {
        is Feed.ArticleTarget -> content
        is Feed.AnswerTarget -> content
        else -> return emptyList()
    }
    if (html.contains("<figure", ignoreCase = true)) {
        val figures = Ksoup
            .parse(html)
            .select("figure")
            .asSequence()
            .mapNotNull { figure -> figure.selectFirst("img")?.let { extractImageUrl(it::attr) } }
            .visible()
        if (figures.isNotEmpty()) return figures
    }
    return listOfNotNull((this as? Feed.AnswerTarget)?.thumbnail ?: cover).asSequence().visible()
}

class PeopleActivitiesViewModel(
    val person: Person,
) : PaginationViewModel<PeopleActivity>(typeOf<PeopleActivity>()) {
    val readingQueueSourceId: String get() = "people:${person.userTokenOrId}:activities:created"
    override val initialUrl: String get() = "https://www.zhihu.com/api/v3/moments/${person.userTokenOrId}/activities"
    override val include: String get() = if (lastPaging == null) super.include else ""

    override fun decodePage(environment: PaginationEnvironment, rawData: JsonArray): List<PeopleActivity> {
        val settings = FeedDisplaySettings(platformSettingsStore())
        val seen = if (isRefreshPending) mutableSetOf() else allData.mapTo(mutableSetOf()) { it.item.stableKey }
        return rawData
            .asSequence()
            .filterIsInstance<JsonObject>()
            .flatMap { row ->
                if ((row["type"] as? JsonPrimitive)?.content == "feed_group") {
                    (row["list"] as? JsonArray).orEmpty().asSequence().filterIsInstance<JsonObject>()
                } else {
                    sequenceOf(row)
                }
            }.mapNotNull { row ->
                try {
                    PeopleActivity.decode(row, person, settings)?.takeIf { seen.add(it.item.stableKey) }
                } catch (error: SerializationException) {
                    environment.logDecodeFailure("PeopleActivities", row, error)
                    null
                }
            }.toList()
    }
}
