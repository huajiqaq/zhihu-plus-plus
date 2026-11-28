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

package com.github.zly2006.zhihu.data

import com.github.zly2006.zhihu.i18n.CoreStrings
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.CollectionContent
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.Topic
import com.github.zly2006.zhihu.navigation.Video
import com.github.zly2006.zhihu.navigation.WebPage
import kotlinx.serialization.Serializable
import com.github.zly2006.zhihu.navigation.Person as PersonDestination

@Serializable
data class FeedDisplayItem(
    val title: String,
    val summary: String?,
    val details: String,
    val feed: Feed?,
    val destination: NavDestination? = null,
    val avatarSrc: String? = null,
    val authorName: String? = null,
    val authorBadgeV2: DataHolder.BadgeV2? = null,
    val isFiltered: Boolean = false,
    val isQualityFiltered: Boolean = false,
    val content: String? = null,
    var raw: DataHolder.Content? = null,
    val historyType: String? = null,
    val titleBadges: Array<String> = emptyArray(),
    val historyKey: String? = null,
    val filterFacts: FeedFilterFacts? = null,
    val qualityFilterReason: String? = null,
) {
    val displayDetails: String
        get() {
            val target = feed?.target ?: return details
            val sourceDetails = target.detailsText
            if (details == sourceDetails) return target.displayDetails
            val action = feed.actionText ?: return details
            return if (details == "$sourceDetails · $action") "${target.displayDetails} · $action" else details
        }

    /** 内容身份不含标题、来源或历史记录实例，混合推荐按它跨来源去重。 */
    val contentIdentity: FeedContentIdentity?
        get() = when (val target = feed?.target) {
            is Feed.AnswerTarget -> FeedContentIdentity("answer", target.id.toString())
            is Feed.ArticleTarget -> FeedContentIdentity("article", target.id.toString())
            is Feed.QuestionTarget -> FeedContentIdentity("question", target.id.toString())
            is Feed.PinTarget -> FeedContentIdentity("pin", target.id.toString())
            is Feed.VideoTarget -> FeedContentIdentity("video", target.id.toString())
            null -> when (val nav = destination) {
                is Article -> FeedContentIdentity(nav.type.toString(), nav.id.toString())
                is Question -> FeedContentIdentity("question", nav.questionId.toString())
                is Pin -> FeedContentIdentity("pin", nav.id.toString())
                is PersonDestination -> FeedContentIdentity("people", nav.id.takeUnless { it.isBlank() || it == PersonDestination.EMPTY_ID } ?: nav.urlToken)
                is Topic -> FeedContentIdentity("topic", nav.id)
                is Video -> FeedContentIdentity("video", nav.id.toString())
                is CollectionContent -> FeedContentIdentity("collection", nav.collectionId)
                is WebPage -> FeedContentIdentity("web", nav.url)
                else -> null
            }
        }

    /** 历史页面保留记录实例身份，不能按内容身份合并用户的历史条目。 */
    val stableKey: String
        get() = historyKey ?: contentIdentity?.key ?: destination?.let { "route:$it" } ?: "$title|${summary.orEmpty()}|$details"
}

@Serializable
data class FeedContentIdentity(val type: String, val id: String) {
    val key: String get() = "$type:$id"
}

/** 无标准 feed 的卡片保留服务端实际下发的过滤依据，null 不等于零或未关注。 */
@Serializable
data class FeedFilterFacts(
    val voteCount: Int? = null,
    val authorId: String? = null,
    val authorIsFollowing: Boolean? = null,
    val authorFollowers: Int? = null,
)

fun FeedDisplayItem.findQualityFilterReason(settings: QualityFilterSettings): String? {
    val target = feed?.target
    val author = when (val value = raw) {
        is DataHolder.Answer -> value.author
        is DataHolder.Article -> value.author
        else -> null
    }
    val following = filterFacts?.authorIsFollowing ?: target?.author?.isFollowing ?: author?.isFollowing
    val votes = filterFacts?.voteCount?.takeIf { it >= 0 } ?: when (target) {
        is Feed.AnswerTarget -> target.voteupCount.takeIf { it >= 0 }
        is Feed.ArticleTarget -> target.voteupCount.takeIf { it >= 0 }
        is Feed.VideoTarget -> target.voteCount.takeIf { it >= 0 }
        else -> null
    } ?: when (val value = raw) {
        is DataHolder.Answer -> value.voteupCount.takeIf { it >= 0 }
        is DataHolder.Article -> value.voteupCount.takeIf { it >= 0 }
        else -> null
    }
    val followers = filterFacts?.authorFollowers ?: target?.author?.followersCount
    return when (target) {
        is Feed.AnswerTarget -> settings.answerReason(votes, following)
        is Feed.ArticleTarget -> settings.articleReason(votes, followers, following)
        is Feed.VideoTarget -> settings.videoReason(votes, followers, following)
        is Feed.QuestionTarget -> target.filterReason(settings)
        else -> when ((navDestination as? Article)?.type) {
            ArticleType.Answer -> settings.answerReason(votes, following)
            ArticleType.Article -> settings.articleReason(votes, followers, following)
            null -> null
        }
    }
}

val Feed.Target.displayExcerpt: String?
    get() = if (this is Feed.PinTarget) excerpt?.pinSummaryText()?.takeIf(String::isNotBlank) else excerpt

fun List<Feed>.flattenFeeds(): List<Feed> = flatMap {
    (it as? GroupFeed)?.list ?: listOf(it)
}

fun Feed.toDisplayItem(
    enableQualityFilter: Boolean = true,
    reverseBlock: Boolean = false,
    qualityFilterSettings: QualityFilterSettings = QualityFilterSettings(),
): FeedDisplayItem = when (this) {
    is CommonFeed, is FeedItemIndexGroup, is MomentsFeed, is HotListFeed, is TopicFeed, is QuestionFeedCard -> toTargetDisplayItem(
        enableQualityFilter = enableQualityFilter,
        reverseBlock = reverseBlock,
        qualityFilterSettings = qualityFilterSettings,
    )

    is AdvertisementFeed -> FeedDisplayItem(
        title = if (reverseBlock) {
            ad.creatives
                .firstOrNull()
                ?.title ?: ""
        } else {
            ""
        },
        summary = if (reverseBlock) {
            ad.creatives
                .firstOrNull()
                ?.description ?: actionText
        } else {
            actionText
        },
        details = CoreStrings.adWithAction(actionText).trim(),
        feed = this,
        isFiltered = !reverseBlock,
        content = ad.creatives
            .firstOrNull()
            ?.landingUrl,
    )

    is GroupFeed -> error("GroupFeed should be flattened before creating display items")
}

private fun Feed.toTargetDisplayItem(
    enableQualityFilter: Boolean,
    reverseBlock: Boolean,
    qualityFilterSettings: QualityFilterSettings,
): FeedDisplayItem {
    val target = target
    val filterReason = if (!enableQualityFilter || reverseBlock) {
        null
    } else {
        when (target) {
            is Feed.AnswerTarget -> target.filterReason(qualityFilterSettings)
            is Feed.ArticleTarget -> target.filterReason(qualityFilterSettings)
            is Feed.VideoTarget -> target.filterReason(qualityFilterSettings)
            is Feed.QuestionTarget -> target.filterReason(qualityFilterSettings)
            else -> target?.filterReason()
        }
    }

    if (filterReason != null) {
        return FeedDisplayItem(
            title = target!!.title,
            summary = target.excerpt ?: target.detailsText,
            details = target.detailsText,
            avatarSrc = target.author?.avatarUrl,
            authorName = target.author?.name,
            feed = this,
            destination = navDestination,
            isFiltered = true,
            isQualityFiltered = true,
            qualityFilterReason = filterReason,
        )
    }

    return when (target) {
        is Feed.AnswerTarget,
        is Feed.ArticleTarget,
        is Feed.QuestionTarget,
        -> FeedDisplayItem(
            title = target.title,
            summary = target.excerpt,
            details = listOfNotNull(target.detailsText, actionText).joinToString(" · "),
            avatarSrc = target.author?.avatarUrl,
            authorName = target.author?.name,
            authorBadgeV2 = target.author?.badgeV2,
            feed = this,
        )

        is Feed.PinTarget -> {
            val textContent = target.content
                .filterIsInstance<DataHolder.Pin.ContentText>()
                .firstOrNull()
            val title = textContent?.title.orEmpty()
            val summary = (
                textContent?.content?.pinSummaryText()?.takeIf(String::isNotBlank) ?: target.displayExcerpt ?: target.content
                    .filterIsInstance<DataHolder.Pin.ContentLinkCard>()
                    .firstNotNullOfOrNull { it.dataDraftTitle.takeIf { title -> title.isNotBlank() } }
            )?.takeUnless { it == title }

            FeedDisplayItem(
                title = title,
                summary = summary,
                details = target.detailsText,
                avatarSrc = target.author.avatarUrl,
                authorName = target.author.name,
                authorBadgeV2 = target.author.badgeV2,
                feed = this,
            )
        }

        else -> FeedDisplayItem(
            title = target?.description() ?: CoreStrings.adLabel,
            summary = CoreStrings.unsupportedFeed,
            details = target?.detailsText ?: CoreStrings.adLabel,
            feed = this,
        )
    }
}
