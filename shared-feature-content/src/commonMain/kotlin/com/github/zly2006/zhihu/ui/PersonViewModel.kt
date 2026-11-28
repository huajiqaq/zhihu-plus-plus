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

package com.github.zly2006.zhihu.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.LocalHistoryStorage
import com.github.zly2006.zhihu.data.OfficialBadge
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.officialBadge
import com.github.zly2006.zhihu.data.officialBadgeDetails
import com.github.zly2006.zhihu.i18n.ContentStrings
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.util.AppLog
import com.github.zly2006.zhihu.util.raiseForStatus
import com.github.zly2006.zhihu.viewmodel.ContentBlocklistEnvironment
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.PaginationViewModel
import com.github.zly2006.zhihu.viewmodel.ProfileLoadEnvironment
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import com.github.zly2006.zhihu.viewmodel.addReadHistory
import com.github.zly2006.zhihu.viewmodel.deleteSigned
import com.github.zly2006.zhihu.viewmodel.feed.PeopleActivitiesViewModel
import com.github.zly2006.zhihu.viewmodel.postSigned
import io.ktor.client.call.body
import kotlin.reflect.typeOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class PeopleAnswersViewModel(
    val person: Person,
) : PaginationViewModel<DataHolder.Answer>(
        typeOf<DataHolder.Answer>(),
    ) {
    var sortBy by mutableStateOf("voteups")
        private set

    override val initialUrl: String
        get() = "https://www.zhihu.com/api/v4/members/${person.userTokenOrId}/answers?sort_by=$sortBy"

    override val include: String
        get() = "data[*].is_normal,admin_closed_comment,reward_info,is_collapsed,annotation_action,annotation_detail,collapse_reason,collapsed_by,suggest_edit,comment_count,thanks_count,can_comment,content,editable_content,attachment,voteup_count,reshipment_settings,comment_permission,created_time,updated_time,review_info,excerpt,paid_info,reaction_instruction,is_labeled,label_info,relationship.is_authorized,voting,is_author,is_thanked,is_nothelp,author.badge_v2"

    fun updateSortBy(newSort: String): Boolean {
        if (sortBy == newSort) {
            return false
        }
        sortBy = newSort
        return true
    }

    fun changeSortBy(newSort: String, environment: PaginationEnvironment) {
        if (updateSortBy(newSort)) {
            refresh(environment)
        }
    }
}

class PeopleArticlesViewModel(
    val person: Person,
) : PaginationViewModel<DataHolder.Article>(
        typeOf<DataHolder.Article>(),
    ) {
    var sortBy by mutableStateOf("created")
        private set

    override val initialUrl: String
        get() = "https://www.zhihu.com/api/v4/members/${person.userTokenOrId}/articles?sort_by=$sortBy"

    override val include: String
        get() = "data[*].comment_count,suggest_edit,is_normal,thumbnail_extra_info,thumbnail,can_comment,comment_permission,admin_closed_comment,content,voteup_count,created,updated,upvoted_followees,voting,review_info,reaction_instruction,is_labeled,label_info,author.badge_v2;data[*].vessay_info;data[*].author.badge[?(type=best_answerer)].topics;"

    fun updateSortBy(newSort: String): Boolean {
        if (sortBy == newSort) {
            return false
        }
        sortBy = newSort
        return true
    }

    fun changeSortBy(newSort: String, environment: PaginationEnvironment) {
        if (updateSortBy(newSort)) {
            refresh(environment)
        }
    }
}

class PeopleFollowersViewModel(
    val person: Person,
) : PaginationViewModel<DataHolder.People>(
        typeOf<DataHolder.People>(),
    ) {
    /** Web followers 接口存在签名限制；粉丝列表使用移动端接口。 */
    override val initialUrl: String
        get() = "https://api.zhihu.com/people/${person.id}/followers"

    override val include: String
        get() = "data[*].answer_count,articles_count,gender,follower_count,is_followed,is_following,badge_v2,badge[?(type=best_answerer)].topics"
}

class PeopleFollowingViewModel(
    val person: Person,
) : PaginationViewModel<DataHolder.People>(
        typeOf<DataHolder.People>(),
    ) {
    override val initialUrl: String
        get() = "https://www.zhihu.com/api/v4/members/${person.userTokenOrId}/followees"

    override val include: String
        get() = "data[*].answer_count,articles_count,gender,follower_count,is_followed,is_following,badge_v2,badge[?(type=best_answerer)].topics"
}

class PeopleCollectionsViewModel(
    val person: Person,
) : PaginationViewModel<DataHolder.Collection>(
        typeOf<DataHolder.Collection>(),
    ) {
    override val initialUrl: String
        get() = "https://www.zhihu.com/api/v4/members/${person.userTokenOrId}/favlists"

    override val include: String
        get() = "data[*].updated_time,answer_count,follower_count,creator"
}

class PeopleQuestionsViewModel(
    val person: Person,
) : PaginationViewModel<DataHolder.Question>(
        typeOf<DataHolder.Question>(),
    ) {
    override val initialUrl: String
        get() = "https://www.zhihu.com/api/v4/members/${person.userTokenOrId}/questions"

    override val include: String
        get() = "data[*].created,answer_count,follower_count,author,visit_count,comment_count,detail,relationship,topics,voteup_count"
}

class PeoplePinsViewModel(
    val person: Person,
) : PaginationViewModel<DataHolder.Pin>(
        typeOf<DataHolder.Pin>(),
    ) {
    override val initialUrl: String
        get() = "https://www.zhihu.com/api/v4/v2/pins/${person.userTokenOrId}/moments"

    override val include: String
        get() = "data[*].like_count,comment_count,created,updated,content"
}

class PeopleColumnContributionsViewModel(
    val person: Person,
) : PaginationViewModel<DataHolder.Column>(
        typeOf<DataHolder.Column>(),
    ) {
    override val initialUrl: String
        get() = "https://www.zhihu.com/api/v4/members/${person.userTokenOrId}/column-contributions"

    override val include: String
        get() = "data[*].column.articles_count"

    override suspend fun decodePage(environment: PaginationEnvironment, rawData: JsonArray): List<DataHolder.Column> =
        super.decodePage(environment, JsonArray(rawData.map { it.jsonObject.getValue("column") }))
}

class PeopleFollowingCollectionsViewModel(
    val person: Person,
) : PaginationViewModel<DataHolder.Collection>(
        typeOf<DataHolder.Collection>(),
    ) {
    override val initialUrl: String
        get() = "https://www.zhihu.com/api/v4/members/${person.userTokenOrId}/following-favlists"

    override val include: String
        get() = "data[*].updated_time,answer_count,follower_count,creator"
}

@Serializable
data class FollowedQuestion(
    val id: String,
    val type: String = "question",
    val url: String = "",
    val title: String = "",
    val questionType: String = "",
    val created: Long = 0L,
    val updatedTime: Long = 0L,
)

@Serializable
data class FollowedTopic(
    val id: String = "",
    val type: String = "topic",
    val url: String = "",
    val name: String = "",
    val avatarUrl: String? = null,
    val topicType: String? = null,
    val topic: DataHolder.Topic? = null,
) {
    val displayId: String get() = topic?.id ?: id
    val displayName: String get() = topic?.name ?: name
    val displayAvatarUrl: String? get() = topic?.avatarUrl ?: avatarUrl
}

class PeopleFollowingQuestionsViewModel(
    val person: Person,
) : PaginationViewModel<FollowedQuestion>(
        typeOf<FollowedQuestion>(),
    ) {
    override val initialUrl: String
        get() = "https://www.zhihu.com/api/v4/members/${person.userTokenOrId}/following-questions"

    override val include: String
        get() = ""
}

class PeopleFollowingTopicsViewModel(
    val person: Person,
) : PaginationViewModel<FollowedTopic>(
        typeOf<FollowedTopic>(),
    ) {
    override val initialUrl: String
        get() = "https://www.zhihu.com/api/v4/members/${person.userTokenOrId}/following-topic-contributions"

    override val include: String
        get() = ""
}

class PeopleFollowingColumnsViewModel(
    val person: Person,
) : PaginationViewModel<DataHolder.Column>(
        typeOf<DataHolder.Column>(),
    ) {
    override val initialUrl: String
        get() = "https://www.zhihu.com/api/v4/members/${person.userTokenOrId}/following-columns"

    override val include: String
        get() = "data[*].articles_count,followers,author"
}

class PersonViewModel(
    val person: Person,
) : ViewModel() {
    var avatar by mutableStateOf("")
    var name by mutableStateOf(person.name)
    var headline by mutableStateOf("")
    var officialBadge by mutableStateOf<OfficialBadge?>(null)
    var officialBadgeDetails by mutableStateOf<List<OfficialBadge>>(emptyList())
    var githubSocial by mutableStateOf<GithubSocialUiState?>(null)
    var followerCount by mutableIntStateOf(0)
    var followingCount by mutableIntStateOf(0)
    var answerCount by mutableIntStateOf(0)
    var articleCount by mutableIntStateOf(0)
    var isFollowing by mutableStateOf(false)
    var isBlocking by mutableStateOf(false)
    var isBlockedInRecommendations by mutableStateOf(false)
    var isBlockedAsQuestionAuthor by mutableStateOf(false)
    var memberHashId by mutableStateOf(person.id)
    var isProfileLoading by mutableStateOf(false)
        private set
    var profileErrorMessage by mutableStateOf<String?>(null)
        private set
    var hasLoadedProfile by mutableStateOf(false)
        private set

    val answersFeedModel = PeopleAnswersViewModel(person)
    val articlesFeedModel = PeopleArticlesViewModel(person)
    val activitiesFeedModel = PeopleActivitiesViewModel(person)
    val collectionsFeedModel = PeopleCollectionsViewModel(person)
    val questionsFeedModel = PeopleQuestionsViewModel(person)
    val pinsFeedModel = PeoplePinsViewModel(person)
    val columnsFeedModel = PeopleColumnContributionsViewModel(person)
    val followersFeedModel = PeopleFollowersViewModel(person)
    val followingFeedModel = PeopleFollowingViewModel(person)
    val followingCollectionsFeedModel = PeopleFollowingCollectionsViewModel(person)
    val followingQuestionsFeedModel = PeopleFollowingQuestionsViewModel(person)
    val followingTopicsFeedModel = PeopleFollowingTopicsViewModel(person)
    val followingColumnsFeedModel = PeopleFollowingColumnsViewModel(person)
    val subFeedModels = arrayOf(
        answersFeedModel,
        articlesFeedModel,
        activitiesFeedModel,
        collectionsFeedModel,
        questionsFeedModel,
        pinsFeedModel,
        columnsFeedModel,
        followersFeedModel,
        followingFeedModel,
    )

    private var profileLoadJob: Job? = null
    private var profileLoadGeneration = 0

    override fun onCleared() {
        profileLoadGeneration++
        profileLoadJob?.cancel()
        for (model in arrayOf(*subFeedModels, followingCollectionsFeedModel, followingQuestionsFeedModel, followingTopicsFeedModel, followingColumnsFeedModel)) {
            model.viewModelScope.cancel()
        }
        super.onCleared()
    }

    suspend fun toggleFollow(environment: ZhihuApiEnvironment) {
        val followersUrl = "https://www.zhihu.com/api/v4/members/${person.urlToken}/followers"
        val newFollowingState = !isFollowing
        val response = if (newFollowingState) {
            environment.postSigned(followersUrl)
        } else {
            environment.deleteSigned(followersUrl)
        }
        val jojo = response.raiseForStatus().body<JsonObject>()
        followerCount = jojo["follower_count"]?.jsonPrimitive?.int ?: (followerCount + if (newFollowingState) 1 else -1)
        isFollowing = newFollowingState
    }

    suspend fun toggleBlock(environment: ZhihuApiEnvironment) {
        val blockUrl = "https://www.zhihu.com/api/v4/members/${person.urlToken}/actions/block"
        val newBlockingState = !isBlocking
        if (newBlockingState) {
            environment.postSigned(blockUrl)
        } else {
            environment.deleteSigned(blockUrl)
        }.raiseForStatus()
        isBlocking = newBlockingState
    }

    suspend fun toggleRecommendationBlock(environment: ContentBlocklistEnvironment) {
        if (isBlockedInRecommendations) {
            environment.removeBlockedUser(person.id)
            isBlockedInRecommendations = false
        } else {
            environment.addBlockedUser(
                userId = person.id,
                userName = name,
                urlToken = person.urlToken,
                avatarUrl = avatar,
            )
            isBlockedInRecommendations = true
        }
    }

    suspend fun toggleQuestionAuthorBlock(environment: ContentBlocklistEnvironment) {
        if (isBlockedAsQuestionAuthor) {
            environment.removeBlockedQuestionAuthor(person.id)
            isBlockedAsQuestionAuthor = false
        } else {
            environment.addBlockedQuestionAuthor(
                userId = person.id,
                userName = name,
                urlToken = person.urlToken,
                avatarUrl = avatar,
            )
            isBlockedAsQuestionAuthor = true
        }
    }

    suspend fun load(environment: ProfileLoadEnvironment) {
        if (isProfileLoading) return
        val generation = ++profileLoadGeneration
        val client = environment.httpClient()
        profileLoadJob = currentCoroutineContext()[Job]
        isProfileLoading = true
        profileErrorMessage = null
        try {
            environment.addReadHistory(person.id, "profile")
            currentCoroutineContext().ensureActive()
            if (generation != profileLoadGeneration || environment.httpClient() !== client) {
                throw CancellationException("加载已失效")
            }

            val profileUrl = "https://api.zhihu.com/people/${person.urlToken.takeIf(String::isNotBlank) ?: person.id}"
            val jojo = environment.fetchJson(profileUrl, PEOPLE_PROFILE_INCLUDE_PATH)
                ?: error(ContentStrings.profileEmpty)

            val loadedPerson = ZhihuJson.decodeJson<DataHolder.People>(jojo)
            val urlToken = loadedPerson.urlToken
            currentCoroutineContext().ensureActive()
            if (generation != profileLoadGeneration || environment.httpClient() !== client) {
                throw CancellationException("加载已失效")
            }

            LocalHistoryStorage().add(
                Person(
                    id = loadedPerson.id,
                    name = loadedPerson.name,
                    urlToken = urlToken ?: "",
                ),
            )

            val recommendationBlocked = environment.isUserBlocked(loadedPerson.id)
            val questionAuthorBlocked = environment.isQuestionAuthorBlocked(loadedPerson.id)
            currentCoroutineContext().ensureActive()
            if (generation != profileLoadGeneration || environment.httpClient() !== client) {
                throw CancellationException("加载已失效")
            }
            this.avatar = loadedPerson.avatarUrl
            this.name = loadedPerson.name
            this.headline = loadedPerson.headline
            this.officialBadge = loadedPerson.badgeV2.officialBadge()
            this.officialBadgeDetails = loadedPerson.badgeV2.officialBadgeDetails()
            this.followerCount = loadedPerson.followerCount
            this.followingCount = loadedPerson.followingCount
            this.answerCount = loadedPerson.answerCount
            this.articleCount = loadedPerson.articlesCount
            this.isFollowing = loadedPerson.isFollowing
            this.isBlocking = loadedPerson.isBlocking
            this.isBlockedInRecommendations = recommendationBlocked
            this.isBlockedAsQuestionAuthor = questionAuthorBlocked
            this.memberHashId = loadedPerson.id
            this.person.id = loadedPerson.id
            if (urlToken != null) {
                this.person.urlToken = urlToken
            }

            hasLoadedProfile = true
            val loadedSocial = try {
                val detailUrl =
                    "https://api.zhihu.com/people/${person.urlToken.takeIf(String::isNotBlank) ?: person.id}/profile/detail"
                environment
                    .fetchJson(detailUrl, "")
                    ?.let { ZhihuJson.decodeJson<DataHolder.People>(it).githubSocialUiState() }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLog.e("PersonViewModel", "Failed to load optional social media profile detail", error)
                null
            }
            currentCoroutineContext().ensureActive()
            if (generation != profileLoadGeneration || environment.httpClient() !== client) {
                throw CancellationException("加载已失效")
            }
            this.githubSocial = loadedSocial
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (generation == profileLoadGeneration && environment.httpClient() === client) {
                profileErrorMessage = error.message?.takeIf(String::isNotBlank) ?: ContentStrings.profileLoadFailed
            }
        } finally {
            if (generation == profileLoadGeneration) {
                isProfileLoading = false
                profileLoadJob = null
            }
        }
    }
}

data class GithubSocialUiState(
    val title: String,
    val starCount: String,
    val profileUrl: String,
    val iconUrl: String? = null,
)

internal fun DataHolder.People.githubSocialUiState(): GithubSocialUiState? = socialMedias.firstNotNullOfOrNull { media ->
    if (!media.title.startsWith("GitHub", ignoreCase = true)) {
        return@firstNotNullOfOrNull null
    }
    val starCount = media.modules
        .firstOrNull { it.title.equals("stars", ignoreCase = true) }
        ?.value
        ?.takeIf { it.isNotBlank() }
        ?: return@firstNotNullOfOrNull null
    val profileLink = media.link.takeIf { it.isNotBlank() }
        ?: return@firstNotNullOfOrNull null
    val profileUrl = if (profileLink.startsWith("zhihu://", ignoreCase = true)) {
        val username = media.title
            .substringAfter('·', missingDelimiterValue = "")
            .trim()
            .takeIf { it.isNotBlank() }
            ?: return@firstNotNullOfOrNull null
        "https://github.com/$username"
    } else {
        profileLink
    }

    GithubSocialUiState(
        title = media.title,
        starCount = starCount,
        profileUrl = profileUrl,
        iconUrl = media.icon.takeIf { it.isNotBlank() },
    )
}
