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

@file:Suppress("unused")

package com.github.zly2006.zhihu.data

import com.github.zly2006.zhihu.data.Feed.Badge
import com.github.zly2006.zhihu.i18n.CoreStrings
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonNames

data class QualityFilterSettings(
    val answerVoteupCount: Int = 10,
    val articleVoteupCount: Int = 20,
    val articleFollowersCount: Int = 50,
    val videoVoteCount: Int = 20,
    val videoFollowersCount: Int = 50,
    val questionAnswerCount: Int = 5,
    val questionFollowersCount: Int = 50,
) {
    fun answerReason(votes: Int?, following: Boolean?): String? =
        if (votes != null && votes >= 0 && votes < answerVoteupCount && following != true) CoreStrings.filterAnswerRule(answerVoteupCount) else null

    fun articleReason(votes: Int?, followers: Int?, following: Boolean?): String? =
        if (following != true && ((votes != null && votes >= 0 && votes < articleVoteupCount) || (followers != null && followers >= 0 && followers < articleFollowersCount))) {
            CoreStrings.filterArticleRule(articleFollowersCount, articleVoteupCount)
        } else {
            null
        }

    fun videoReason(votes: Int?, followers: Int?, following: Boolean?): String? =
        if (following != true && votes != null && votes >= 0 && votes < videoVoteCount && followers != null && followers >= 0 && followers < videoFollowersCount) {
            CoreStrings.filterVideoRule(videoFollowersCount, videoVoteCount)
        } else {
            null
        }
}

@Serializable
sealed interface Feed {
    @Serializable
    sealed interface Target {
        fun filterReason(): String? = null

        fun description(): String = when (this) {
            is AnswerTarget -> CoreStrings.typeAnswer
            is VideoTarget -> CoreStrings.typeVideo
            is ArticleTarget -> CoreStrings.typeArticle
            is PinTarget -> CoreStrings.typePin
            is QuestionTarget -> CoreStrings.typeQuestion
        }

        val displayDetails: String
            get() = when (this) {
                is AnswerTarget -> CoreStrings.feedAnswerDetails(voteupCount, commentCount)
                is VideoTarget -> CoreStrings.feedVideoDetails(voteCount, commentCount)
                is ArticleTarget -> CoreStrings.feedArticleDetails(voteupCount, commentCount)
                is PinTarget -> CoreStrings.feedPinDetails(likeCount, commentCount)
                is QuestionTarget -> CoreStrings.feedQuestionDetails(followerCount, answerCount)
            }

        val detailsText: String
        val title: String
        val url: String
        val excerpt: String?
        val author: Person?
        val createdTime: Long
        val updatedTime: Long
    }

    private object LegacyAuthorSerCompat : KSerializer<Person?> {
        private val personSerializer = Person.serializer().nullable

        override val descriptor: SerialDescriptor = personSerializer.descriptor

        override fun serialize(
            encoder: Encoder,
            value: Person?,
        ) {
            personSerializer.serialize(encoder, value)
        }

        override fun deserialize(decoder: Decoder) = try {
            personSerializer.deserialize(decoder)
        } catch (_: Exception) {
            // 作者字段也可能返回字符串；消费该值后按无作者处理。
            decoder.decodeString()
            null
        }
    }

    @Serializable
    @SerialName("answer")
    data class AnswerTarget(
        val id: Long,
        override val url: String,
        @Serializable(with = LegacyAuthorSerCompat::class)
        override val author: Person? = null,
        /**
         * -1 广告
         */
        override val createdTime: Long = -1,
        override val updatedTime: Long = -1,
        val voteupCount: Int = -1,
        val thanksCount: Int = -1,
        val commentCount: Int = -1,
        val isCopyable: Boolean = false,
        val question: QuestionTarget,
        val thumbnail: String? = null,
        val thumbnailInfo: DataHolder.ThumbnailInfo? = null,
        override val excerpt: String? = null,
        val reshipmentSettings: String = "",
        val content: String = "",
        val relationship: Relationship? = null,
        val isLabeled: Boolean = false,
        val visitedCount: Int = 0,
        val thumbnails: List<String> = emptyList(),
        val favoriteCount: Int = 0,
        val answerType: String? = null,
        val segmentInfos: List<SegmentInfoParagraph> = emptyList(),
        @Serializable(with = BooleanCompatSerializer::class)
        val allowSegmentInteraction: Boolean = false,
    ) : Target {
        override fun filterReason(): String? = filterReason(QualityFilterSettings())

        fun filterReason(settings: QualityFilterSettings): String? = settings.answerReason(voteupCount, author?.isFollowing)

        override val detailsText = "回答 · $voteupCount 赞同 · $commentCount 评论"
        override val title: String
            get() = question.title
    }

    @Serializable
    @SerialName("zvideo")
    data class VideoTarget(
        val id: Long,
        override val url: String = "",
        override val author: Person,
        val voteCount: Int = -1,
        val commentCount: Int,
        override val title: String,
        val description: String,
        override val excerpt: String,
    ) : Target {
        override fun filterReason(): String? = filterReason(QualityFilterSettings())

        fun filterReason(settings: QualityFilterSettings): String? = settings.videoReason(voteCount, author.followersCount, author.isFollowing)

        override val detailsText = "视频 · $voteCount 赞 · $commentCount 评论"

        override val createdTime: Long
            get() = -1

        override val updatedTime: Long
            get() = -1
    }

    /** 旧版信息流接口不返回 [content]、[created] 和 [updated]。 */
    @Serializable
    @SerialName("article")
    data class ArticleTarget(
        val id: Long,
        override val url: String,
        override val author: Person,
        val voteupCount: Int = 0,
        val commentCount: Int = 0,
        override val title: String,
        override val excerpt: String = "",
        val content: String = "",
        val created: Long = 0,
        val updated: Long = 0,
        val isLabeled: Boolean = false,
        val visitedCount: Int = 0,
        val favoriteCount: Int = 0,
        val segmentInfos: List<SegmentInfoParagraph> = emptyList(),
        @Serializable(with = BooleanCompatSerializer::class)
        val allowSegmentInteraction: Boolean = false,
    ) : Target {
        override fun filterReason(): String? = filterReason(QualityFilterSettings())

        fun filterReason(settings: QualityFilterSettings): String? = settings.articleReason(voteupCount, author.followersCount, author.isFollowing)

        override val detailsText = "文章 · $voteupCount 赞 · $commentCount 评论"

        override val updatedTime: Long
            get() = updated

        override val createdTime: Long
            get() = created
    }

    @Serializable
    @SerialName("pin")
    data class PinTarget(
        val id: Long,
        override val url: String,
        override val author: Person,
        val commentCount: Int = 0,
        val content: List<DataHolder.Pin.ContentItem> = emptyList(),
        val likeCount: Int = 0,
        val excerptTitle: String = "",
        val contentHtml: String = "",
        val created: Long = 0,
        val updated: Long = 0,
        val reactionCount: Int = 0,
        val favoriteCount: Int = 0,
    ) : Target {
        override fun filterReason(): String? = null

        override val detailsText = "想法 · $likeCount 赞 · $commentCount 评论"
        override val title: String
            get() = CoreStrings.typePin
        override val excerpt = excerptTitle.ifEmpty { null }

        override val updatedTime: Long
            get() = updated

        override val createdTime: Long
            get() = created
    }

    /** 旧版信息流接口不返回 [questionType]、[created] 和 [detail]。 */
    @Suppress("PropertyName")
    @Serializable
    @SerialName("question")
    data class QuestionTarget(
        val id: Long,
        @SerialName("name")
        val _name: String? = null,
        @SerialName("title")
        val _title: String? = null,
        override val url: String,
        val type: String,
        val questionType: String = "",
        val created: Long = 0,
        val answerCount: Int = 0,
        val commentCount: Int = 0,
        val followerCount: Int = 0,
        val detail: String = "",
        override val excerpt: String = "",
        val boundTopicIds: List<Long> = emptyList(),
        val relationship: Relationship? = null,
        val isFollowing: Boolean = false,
        @Serializable(with = LegacyAuthorSerCompat::class)
        override val author: Person? = null,
    ) : Target {
        override val title: String
            get() = _title ?: _name.orEmpty()

        override fun filterReason(): String? = filterReason(QualityFilterSettings())

        fun filterReason(settings: QualityFilterSettings): String? = if (answerCount < settings.questionAnswerCount && followerCount < settings.questionFollowersCount) {
            CoreStrings.filterQuestionRule(settings.questionAnswerCount, settings.questionFollowersCount)
        } else {
            null
        }

        override val detailsText = "问题 · $followerCount 关注 · $answerCount 回答"

        override val updatedTime: Long
            get() = -1
        override val createdTime: Long
            get() = created
    }

    @Serializable
    data class Badge(
        val type: String,
        val description: String,
    )
}

const val SEARCH_RESULT_VERB = "SEARCH_RESULT"

val Feed?.isSearchResult: Boolean
    get() = this is CommonFeed && verb == SEARCH_RESULT_VERB

fun Feed?.plainSearchText(text: String): String = if (isSearchResult) text.decodeSearchText() else text

val Feed.target: Feed.Target?
    get() = when (this) {
        is CommonFeed -> target
        is QuestionFeedCard -> target
        is MomentsFeed -> target
        is FeedItemIndexGroup -> target
        is HotListFeed -> target
        is TopicFeed -> target
        else -> null
    }

val Feed.actionText: String?
    get() = when (this) {
        is CommonFeed -> actionText
        is GroupFeed -> groupText
        is AdvertisementFeed -> actionText
        is QuestionFeedCard -> null
        is MomentsFeed -> null
        is FeedItemIndexGroup -> actionText
        is HotListFeed -> detailText
        is TopicFeed -> targetDescription
    }

val Feed.sourceLabel: String?
    get() = when (this) {
        is CommonFeed -> actionText
        is FeedItemIndexGroup -> actionText
        is MomentsFeed -> momentDesc
        is TopicFeed -> targetDescription
        else -> null
    }?.trim()?.takeIf { it.isNotEmpty() }

@Serializable
@SerialName("feed_advert")
class AdvertisementFeed(
    val actionText: String = "",
    val ad: Ad,
) : Feed {
    @Serializable
    class Ad(
        val creatives: List<Creative> = emptyList(),
    )

    @Serializable
    class Creative(
        val landingUrl: String,
        val title: String,
        val description: String,
    )
}

@Serializable
@SerialName("feed_group")
class GroupFeed(
    val id: String = "",
    val attachedInfo: String = "",
    val brief: String,
    val groupText: String,
    val list: List<CommonFeed>,
    val styleType: Int = 0,
) : Feed

@Serializable
@SerialName("question_feed_card")
class QuestionFeedCard(
    val position: Int = 0,
    val target: Feed.Target,
    val cursor: String,
    val targetType: String,
    val isJumpNative: Boolean = false,
    val skipCount: Boolean = false,
) : Feed

@Serializable
@SerialName("feed")
data class CommonFeed(
    val id: String = "",
    val verb: String = "possibly ads, filter me",
    val createdTime: Long = -1,
    val updatedTime: Long = -1,
    /**
     * 广告没有target
     */
    val target: Feed.Target? = null,
    val brief: String = "<none>",
    val attachedInfo: String = "",
    val actionCard: Boolean = false,
    /**
     * 屏蔽
     */
    val promotionExtra: String? = null,
    val cursor: String = "",
    val actionText: String? = null,
) : Feed

@Serializable
@SerialName("topic_feed")
data class TopicFeed(
    val id: String = "",
    val target: Feed.Target,
    val targetDescription: String? = null,
) : Feed

@Serializable
@SerialName("feed_item_index_group")
data class FeedItemIndexGroup(
    val id: String = "",
    val target: Feed.Target,
    val actors: List<Person>,
    val actionText: String,
    val actionTime: Long,
) : Feed

@Serializable
@SerialName("moments_feed")
data class MomentsFeed(
    val id: String,
    val momentDesc: String = "",
    val target: Feed.Target,
    val targetType: String,
) : Feed

@Serializable
data class Person(
    val id: String,
    val url: String,
    val userType: String,
    val urlToken: String? = null,
    val name: String,
    @Serializable(HTMLDecoder::class)
    val headline: String,
    val avatarUrl: String,
    val isOrg: Boolean = false,
    val gender: Int = 0,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("followerCount")
    val followersCount: Int = 0,
    val isFollowing: Boolean = false,
    val isFollowed: Boolean = false,
    val badge: List<Badge>? = null,
    @SerialName("badgeV2")
    private val apiBadgeV2: DataHolder.BadgeV2? = null,
) {
    val badgeV2: DataHolder.BadgeV2?
        get() = DataHolder.injectZhPlusAuthorBadge(id, apiBadgeV2)
}

val Feed.Target.questionAuthor: Person?
    get() = when (this) {
        is Feed.AnswerTarget -> question.author
        is Feed.QuestionTarget -> author
        else -> null
    }

@Serializable
data class Relationship(
    val isFollowing: Boolean = false,
    val isFollowed: Boolean = false,
)

@Serializable
@SerialName("hot_list_feed")
class HotListFeed(
    val id: String,
    val detailText: String,
    val target: Feed.Target,
    val children: List<Children> = emptyList(),
) : Feed {
    @Serializable
    class Children(
        val type: String,
        val thumbnail: String? = null,
    )
}
