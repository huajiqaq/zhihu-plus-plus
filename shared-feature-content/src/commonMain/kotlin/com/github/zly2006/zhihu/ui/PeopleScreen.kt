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

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.fleeksoft.ksoup.Ksoup
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.LocalHistoryStorage
import com.github.zly2006.zhihu.data.OfficialBadge
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.officialBadge
import com.github.zly2006.zhihu.data.officialBadgeDetails
import com.github.zly2006.zhihu.data.toFeedDisplayItemNavDestinationJson
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.CollectionContent
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.platform.rememberExternalUrlOpener
import com.github.zly2006.zhihu.platform.rememberImagePreviewOpener
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.platform.rememberZhihuWebUrlOpener
import com.github.zly2006.zhihu.reading.RegisterReadingQueueSource
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppAlertDialog
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppCard
import com.github.zly2006.zhihu.ui.components.AppDialog
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppDropdownMenu
import com.github.zly2006.zhihu.ui.components.AppDropdownMenuItem
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppMetadataLoadState
import com.github.zly2006.zhihu.ui.components.AppOutlinedButton
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.AppSelectionDropdownMenu
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.AppTabRow
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.AppTopBar
import com.github.zly2006.zhihu.ui.components.AuthorBadge
import com.github.zly2006.zhihu.ui.components.FeedCard
import com.github.zly2006.zhihu.ui.components.PageTurnTarget
import com.github.zly2006.zhihu.ui.components.PaginatedList
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import com.github.zly2006.zhihu.util.Log
import com.github.zly2006.zhihu.util.formatCompactCount
import com.github.zly2006.zhihu.util.jsonObject
import com.github.zly2006.zhihu.util.raiseForStatus
import com.github.zly2006.zhihu.viewmodel.ContentBlocklistEnvironment
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.PaginationViewModel
import com.github.zly2006.zhihu.viewmodel.ProfileLoadEnvironment
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import com.github.zly2006.zhihu.viewmodel.addReadHistory
import com.github.zly2006.zhihu.viewmodel.deleteSigned
import com.github.zly2006.zhihu.viewmodel.feed.BaseFeedViewModel
import com.github.zly2006.zhihu.viewmodel.postSigned
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.painterResource
import zhihu.uicomponents.generated.resources.Res
import zhihu.uicomponents.generated.resources.ic_zh_plus_author_badge
import kotlin.math.roundToInt
import kotlin.reflect.typeOf
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.github.zly2006.zhihu.navigation.Search as SearchDestination
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

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

class PeopleActivitiesViewModel(
    val person: Person,
    val sort: String = "created",
) : BaseFeedViewModel() {
    override val initialUrl: String
        get() = "https://www.zhihu.com/api/v3/moments/${person.userTokenOrId}/activities"
}

class PeopleFollowersViewModel(
    val person: Person,
) : PaginationViewModel<DataHolder.People>(
        typeOf<DataHolder.People>(),
    ) {
    /** 新 followers API 存在签名问题，因此回退旧 API。 */
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

    override fun decodePage(environment: PaginationEnvironment, rawData: JsonArray): List<DataHolder.Column> =
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
        val jojo = response.raiseForStatus().jsonObject()
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
                ?: error("用户资料为空")

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
                Log.e("PersonViewModel", "Failed to load optional social media profile detail", error)
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
                profileErrorMessage = error.message?.takeIf(String::isNotBlank) ?: "用户资料加载失败"
            }
        } finally {
            if (generation == profileLoadGeneration) {
                isProfileLoading = false
                profileLoadJob = null
            }
        }
    }

}

private fun DataHolder.Answer.toPeopleAnswerDisplayItem(): FeedDisplayItem {
    val destination = Article(
        type = ArticleType.Answer,
        id = id,
        title = question.title,
        questionId = question.id,
        authorName = author.name,
        authorBio = author.headline,
        avatarSrc = author.avatarUrl,
        excerpt = excerpt,
        authorId = author.id,
        authorUrlToken = author.urlToken,
    )
    return FeedDisplayItem(
        title = question.title,
        summary = excerpt,
        details = "回答 · $voteupCount 赞同 · $commentCount 评论",
        feed = null,
        navDestinationJson = destination.toFeedDisplayItemNavDestinationJson(),
        raw = this,
    )
}

private fun DataHolder.Article.toPeopleArticleDisplayItem(): FeedDisplayItem {
    val destination = Article(
        type = ArticleType.Article,
        id = id,
        title = title,
        authorName = author.name,
        authorBio = author.headline,
        avatarSrc = author.avatarUrl,
        excerpt = excerpt,
        authorId = author.id,
        authorUrlToken = author.urlToken,
    )
    return FeedDisplayItem(
        title = title,
        summary = excerpt,
        details = "文章 · $voteupCount 赞同 · $commentCount 评论",
        feed = null,
        navDestinationJson = destination.toFeedDisplayItemNavDestinationJson(),
        raw = this,
    )
}

private fun DataHolder.Pin.toPeoplePinDisplayItem(): FeedDisplayItem? {
    val pinId = id.toLongOrNull() ?: return null
    return FeedDisplayItem(
        title = Ksoup.parse(excerptTitle).text(),
        summary = null,
        details = "想法 · $likeCount 赞 · $commentCount 评论",
        feed = null,
        navDestinationJson = Pin(id = pinId, authorName = author.name).toFeedDisplayItemNavDestinationJson(),
        raw = this,
    )
}

private val PEOPLE_SCREEN_TITLES = listOf(
    "回答",
    "文章",
    "动态",
    "收藏",
    "提问",
    "想法",
    "专栏",
    "粉丝",
    "关注",
    "关注订阅",
)

private val PEOPLE_SCREEN_SUBSCRIPTION_TITLES = listOf(
    "我订阅的专栏",
    "关注的话题",
    "关注的问题",
    "关注的收藏夹",
)

const val PEOPLE_SCREEN_ROOT_TAG = "people_screen_root"
const val PEOPLE_SCREEN_HEADER_TAG = "people_screen_header"
const val PEOPLE_SCREEN_BACK_BUTTON_TAG = "people_screen_back_button"
const val PEOPLE_SCREEN_AVATAR_TAG = "people_screen_avatar"
const val PEOPLE_SCREEN_TAB_ROW_TAG = "people_screen_tab_row"
const val PEOPLE_SCREEN_PAGER_TAG = "people_screen_pager"
const val PEOPLE_SCREEN_ANSWERS_LIST_TAG = "people_screen_answers_list"
const val PEOPLE_SCREEN_ARTICLES_LIST_TAG = "people_screen_articles_list"
const val PEOPLE_SCREEN_ACTIVITIES_LIST_TAG = "people_screen_activities_list"
const val PEOPLE_SCREEN_COLLECTIONS_LIST_TAG = "people_screen_collections_list"
const val PEOPLE_SCREEN_QUESTIONS_LIST_TAG = "people_screen_questions_list"
const val PEOPLE_SCREEN_PINS_LIST_TAG = "people_screen_pins_list"
const val PEOPLE_SCREEN_COLUMNS_LIST_TAG = "people_screen_columns_list"
const val PEOPLE_SCREEN_FOLLOWERS_LIST_TAG = "people_screen_followers_list"
const val PEOPLE_SCREEN_FOLLOWING_LIST_TAG = "people_screen_following_list"
const val PEOPLE_SCREEN_SUBSCRIPTION_TABS_TAG = "people_screen_subscription_tabs"
const val PEOPLE_SCREEN_SUBSCRIPTIONS_LIST_TAG = "people_screen_subscriptions_list"
const val PEOPLE_SCREEN_ANSWER_COUNT_TAG = "people_screen_stat_answers"
const val PEOPLE_SCREEN_ARTICLE_COUNT_TAG = "people_screen_stat_articles"
const val PEOPLE_SCREEN_FOLLOWER_COUNT_TAG = "people_screen_stat_followers"
const val PEOPLE_SCREEN_FOLLOWING_COUNT_TAG = "people_screen_stat_following"
const val PEOPLE_SCREEN_FOLLOW_BUTTON_TAG = "people_screen_follow_button"
const val PEOPLE_SCREEN_BLOCK_BUTTON_TAG = "people_screen_block_button"
const val PEOPLE_SCREEN_RECOMMENDATION_BLOCK_BUTTON_TAG = "people_screen_recommendation_block_button"
const val PEOPLE_SCREEN_QUESTION_AUTHOR_BLOCK_BUTTON_TAG = "people_screen_question_author_block_button"
const val PEOPLE_SCREEN_SEARCH_BUTTON_TAG = "people_screen_search_button"
const val PEOPLE_SCREEN_MORE_BUTTON_TAG = "people_screen_more_button"
const val PEOPLE_SCREEN_PROFILE_DETAILS_TAG = "people_screen_profile_details"
const val PEOPLE_SCREEN_GITHUB_STARS_TAG = "people_screen_github_stars"
const val PEOPLE_SCREEN_ANSWER_SORT_HOT_TAG = "people_screen_answer_sort_voteups"
const val PEOPLE_SCREEN_ANSWER_SORT_TIME_TAG = "people_screen_answer_sort_created"
const val PEOPLE_SCREEN_ARTICLE_SORT_HOT_TAG = "people_screen_article_sort_voteups"
const val PEOPLE_SCREEN_ARTICLE_SORT_TIME_TAG = "people_screen_article_sort_created"
const val PEOPLE_SCREEN_OFFICIAL_BADGE_TAG = "people_screen_official_badge"
const val PEOPLE_SCREEN_PROFILE_LOADING_TAG = "people_screen_profile_loading"
const val PEOPLE_SCREEN_PROFILE_ERROR_TAG = "people_screen_profile_error"
const val PEOPLE_SCREEN_PROFILE_RETRY_TAG = "people_screen_profile_retry"

private fun peopleScreenInitialPage(person: Person): Int {
    val jumpToIndex = PEOPLE_SCREEN_TITLES.indexOf(person.jumpTo)
    return if (jumpToIndex >= 0) jumpToIndex else 0
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(
    person: Person,
) {
    val navigator = LocalNavigator.current
    val userMessages = rememberUserMessageSink()
    val paginationEnvironment = rememberPaginationEnvironment(allowGuestAccess = false)
    val viewModel = composeViewModel { PersonViewModel(person) }
    val coroutineScope = rememberCoroutineScope()

    val pagerState = rememberPagerState(
        initialPage = peopleScreenInitialPage(person),
        pageCount = { PEOPLE_SCREEN_TITLES.size },
    )
    val readingQueueSourceId = when (pagerState.currentPage) {
        0 -> "people:${person.userTokenOrId}:answers:${viewModel.answersFeedModel.sortBy}"
        1 -> "people:${person.userTokenOrId}:articles:${viewModel.articlesFeedModel.sortBy}"
        2 -> "people:${person.userTokenOrId}:activities:${viewModel.activitiesFeedModel.sort}"
        5 -> "people:${person.userTokenOrId}:pins"
        else -> null
    }
    when (pagerState.currentPage) {
        0 -> RegisterReadingQueueSource(
            sourceId = requireNotNull(readingQueueSourceId),
            items = viewModel.answersFeedModel.allData.map(DataHolder.Answer::toPeopleAnswerDisplayItem),
        )
        1 -> RegisterReadingQueueSource(
            sourceId = requireNotNull(readingQueueSourceId),
            items = viewModel.articlesFeedModel.allData.map(DataHolder.Article::toPeopleArticleDisplayItem),
        )
        2 -> RegisterReadingQueueSource(
            sourceId = requireNotNull(readingQueueSourceId),
            items = viewModel.activitiesFeedModel.displayItems,
        )
        5 -> RegisterReadingQueueSource(
            sourceId = requireNotNull(readingQueueSourceId),
            items = viewModel.pinsFeedModel.allData.mapNotNull(DataHolder.Pin::toPeoplePinDisplayItem),
        )
    }

    LaunchedEffect(viewModel) {
        if (!viewModel.hasLoadedProfile) viewModel.load(paginationEnvironment)
    }
    LaunchedEffect(pagerState.currentPage) {
        try {
            viewModel.subFeedModels.getOrNull(pagerState.currentPage)?.let { feedModel ->
                val hasData = when (feedModel) {
                    is BaseFeedViewModel -> feedModel.allData.isNotEmpty() || feedModel.displayItems.isNotEmpty()
                    else -> feedModel.allData.isNotEmpty()
                }
                if (!hasData && !feedModel.isEnd && feedModel.errorMessage == null) {
                    feedModel.loadMore(paginationEnvironment)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            userMessages.showShortMessage("加载页面内容失败: ${e.message}")
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var expandedHeaderHeightPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(expandedHeaderHeightPx) {
        if (expandedHeaderHeightPx > 0) {
            val fraction = scrollBehavior.state.collapsedFraction.coerceIn(0f, 1f)
            scrollBehavior.state.heightOffsetLimit = -expandedHeaderHeightPx.toFloat()
            scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit * fraction
        }
    }
    val collapsedFraction = scrollBehavior.state.collapsedFraction
    val searchInTopBar = scrollBehavior.state.heightOffset < -with(LocalDensity.current) { AppDimens.Space8.toPx() }
    val skin = LocalAppSkinTokens.current
    var showActionsMenu by rememberSaveable(person.userTokenOrId) { mutableStateOf(false) }
    var showBlockConfirm by rememberSaveable(person.userTokenOrId) { mutableStateOf(false) }
    val onSearch: (() -> Unit)? = if (
        viewModel.memberHashId.isNotBlank() && viewModel.memberHashId != Person.EMPTY_ID
    ) {
        {
            navigator.onNavigate(
                SearchDestination(
                    restrictedMemberHashId = viewModel.memberHashId,
                    restrictedMemberName = viewModel.name.takeIf(String::isNotBlank) ?: person.name,
                ),
            )
        }
    } else {
        null
    }

    AppScaffold(
        modifier = Modifier
            .testTag(PEOPLE_SCREEN_ROOT_TAG)
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta -> scrollBehavior.state.heightOffset += delta },
                        onDragStopped = { velocity ->
                            scrollBehavior.nestedScrollConnection.onPostFling(
                                consumed = Velocity.Zero,
                                available = Velocity(0f, velocity),
                            )
                        },
                    ),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(Modifier.widthIn(max = AppDimens.ArticleReadingMaxWidth).fillMaxWidth()) {
                    AppTopBar(
                        title = {
                            Text(
                                text = if (collapsedFraction > 0.5f) viewModel.name.ifBlank { person.name } else "个人主页",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        onBack = navigator.onNavigateBack,
                        backTag = PEOPLE_SCREEN_BACK_BUTTON_TAG,
                        actions = {
                            if (searchInTopBar && onSearch != null) {
                                AppIconButton(
                                    onClick = onSearch,
                                    modifier = Modifier.testTag(PEOPLE_SCREEN_SEARCH_BUTTON_TAG),
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = "搜索 TA 的创作", tint = skin.onSurfaceVariantActions)
                                }
                            }
                            Box {
                                AppIconButton(
                                    onClick = { showActionsMenu = true },
                                    modifier = Modifier.testTag(PEOPLE_SCREEN_MORE_BUTTON_TAG),
                                ) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "更多选项", tint = skin.onSurfaceVariantActions)
                                }
                                AppDropdownMenu(expanded = showActionsMenu, onDismissRequest = { showActionsMenu = false }) {
                                    AppDropdownMenuItem(
                                        text = { Text(if (viewModel.isBlocking) "取消拉黑" else "拉黑", color = skin.error) },
                                        leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = skin.error) },
                                        modifier = Modifier.testTag(PEOPLE_SCREEN_BLOCK_BUTTON_TAG),
                                        onClick = {
                                            showActionsMenu = false
                                            if (viewModel.isBlocking) {
                                                coroutineScope.launch {
                                                    try {
                                                        viewModel.toggleBlock(paginationEnvironment)
                                                        userMessages.showShortMessage("已取消拉黑")
                                                    } catch (error: Exception) {
                                                        userMessages.showShortMessage("操作失败: ${error.message}")
                                                    }
                                                }
                                            } else {
                                                showBlockConfirm = true
                                            }
                                        },
                                    )
                                    AppDropdownMenuItem(
                                        text = { Text(if (viewModel.isBlockedInRecommendations) "取消屏蔽推荐" else "屏蔽推荐") },
                                        leadingIcon = { Icon(Icons.Default.VisibilityOff, contentDescription = null) },
                                        modifier = Modifier.testTag(PEOPLE_SCREEN_RECOMMENDATION_BLOCK_BUTTON_TAG),
                                        onClick = {
                                            showActionsMenu = false
                                            coroutineScope.launch {
                                                try {
                                                    viewModel.toggleRecommendationBlock(paginationEnvironment)
                                                    userMessages.showShortMessage(if (viewModel.isBlockedInRecommendations) "已屏蔽推荐" else "已取消屏蔽推荐")
                                                } catch (error: CancellationException) {
                                                    throw error
                                                } catch (error: Exception) {
                                                    userMessages.showShortMessage("操作失败: ${error.message}")
                                                }
                                            }
                                        },
                                    )
                                    AppDropdownMenuItem(
                                        text = { Text(if (viewModel.isBlockedAsQuestionAuthor) "取消屏蔽其提问" else "屏蔽其提问") },
                                        leadingIcon = { Icon(Icons.Default.QuestionAnswer, contentDescription = null) },
                                        modifier = Modifier.testTag(PEOPLE_SCREEN_QUESTION_AUTHOR_BLOCK_BUTTON_TAG),
                                        onClick = {
                                            showActionsMenu = false
                                            coroutineScope.launch {
                                                try {
                                                    viewModel.toggleQuestionAuthorBlock(paginationEnvironment)
                                                    userMessages.showShortMessage(if (viewModel.isBlockedAsQuestionAuthor) "已屏蔽其提问" else "已取消屏蔽其提问")
                                                } catch (error: CancellationException) {
                                                    throw error
                                                } catch (error: Exception) {
                                                    userMessages.showShortMessage("操作失败: ${error.message}")
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        },
                        windowInsets = WindowInsets(0, 0, 0, 0),
                    )
                    CollapsibleHeader(
                        collapsedFraction = collapsedFraction,
                        onExpandedHeightChanged = { expandedHeaderHeightPx = it },
                    ) {
                        Column {
                            UserInfoHeader(
                                viewModel = viewModel,
                                pagerState = pagerState,
                                modifier = Modifier
                                    .padding(horizontal = AppDimens.PageHorizontalPadding)
                                    .testTag(PEOPLE_SCREEN_HEADER_TAG),
                                onSearch = onSearch,
                                showSearch = !searchInTopBar,
                                onFollowToggle = {
                                    coroutineScope.launch {
                                        try {
                                            viewModel.toggleFollow(paginationEnvironment)
                                        } catch (e: Exception) {
                                            userMessages.showShortMessage("操作失败: ${e.message}")
                                        }
                                    }
                                },
                            )
                            AppMetadataLoadState(
                                isLoading = viewModel.isProfileLoading,
                                errorMessage = viewModel.profileErrorMessage,
                                hasContent = viewModel.hasLoadedProfile,
                                onRetry = { coroutineScope.launch { viewModel.load(paginationEnvironment) } },
                                loadingTag = PEOPLE_SCREEN_PROFILE_LOADING_TAG,
                                errorTag = PEOPLE_SCREEN_PROFILE_ERROR_TAG,
                                retryTag = PEOPLE_SCREEN_PROFILE_RETRY_TAG,
                            )
                        }
                    }
                    AppTabRow(
                        titles = PEOPLE_SCREEN_TITLES,
                        selectedIndex = pagerState.currentPage,
                        onSelect = { index ->
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        modifier = Modifier.testTag(PEOPLE_SCREEN_TAB_ROW_TAG),
                        scrollable = true,
                        secondary = true,
                        testTags = PEOPLE_SCREEN_TITLES.indices.map { "people_screen_tab_$it" },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.widthIn(max = AppDimens.ArticleReadingMaxWidth).fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(PEOPLE_SCREEN_PAGER_TAG),
                ) { page ->
                    val listState = rememberLazyListState()
                    val pageTurnTarget = rememberPageTurnTarget(
                        listState = listState,
                        enabled = pagerState.currentPage == page,
                    )
                    when (page) {
                        0 -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("people_screen_page_$page"),
                            ) {
                                PaginatedList(
                                    items = viewModel.answersFeedModel.allData,
                                    listState = listState,
                                    onLoadMore = { viewModel.answersFeedModel.loadMore(paginationEnvironment) },
                                    isEnd = { viewModel.answersFeedModel.isEnd },
                                    isLoading = { viewModel.answersFeedModel.isLoading },
                                    loadError = { viewModel.answersFeedModel.errorMessage },
                                    onRetry = { viewModel.answersFeedModel.retry(paginationEnvironment) },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pageTurnViewportWithGuide(pageTurnTarget)
                                        .testTag(PEOPLE_SCREEN_ANSWERS_LIST_TAG),
                                    key = { it.id },
                                    topContent = {
                                        item(key = PEOPLE_SCREEN_ANSWER_SORT_HOT_TAG) {
                                            SortBar(
                                                currentSort = viewModel.answersFeedModel.sortBy,
                                                onSortChange = { viewModel.answersFeedModel.changeSortBy(it, paginationEnvironment) },
                                                hotTag = PEOPLE_SCREEN_ANSWER_SORT_HOT_TAG,
                                                timeTag = PEOPLE_SCREEN_ANSWER_SORT_TIME_TAG,
                                            )
                                        }
                                    },
                                ) {
                                    FeedCard(
                                        it.toPeopleAnswerDisplayItem(),
                                        readingQueueSourceId = readingQueueSourceId,
                                        modifier = Modifier.testTag("people_screen_answer_item_${it.id}"),
                                    ) { _, destination ->
                                        destination?.let(navigator.onNavigate)
                                    }
                                }
                            }
                        }

                        1 -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("people_screen_page_$page"),
                            ) {
                                PaginatedList(
                                    items = viewModel.articlesFeedModel.allData,
                                    listState = listState,
                                    onLoadMore = { viewModel.articlesFeedModel.loadMore(paginationEnvironment) },
                                    isEnd = { viewModel.articlesFeedModel.isEnd },
                                    isLoading = { viewModel.articlesFeedModel.isLoading },
                                    loadError = { viewModel.articlesFeedModel.errorMessage },
                                    onRetry = { viewModel.articlesFeedModel.retry(paginationEnvironment) },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pageTurnViewportWithGuide(pageTurnTarget)
                                        .testTag(PEOPLE_SCREEN_ARTICLES_LIST_TAG),
                                    key = { it.id },
                                    topContent = {
                                        item(key = PEOPLE_SCREEN_ARTICLE_SORT_HOT_TAG) {
                                            SortBar(
                                                currentSort = viewModel.articlesFeedModel.sortBy,
                                                onSortChange = { viewModel.articlesFeedModel.changeSortBy(it, paginationEnvironment) },
                                                hotTag = PEOPLE_SCREEN_ARTICLE_SORT_HOT_TAG,
                                                timeTag = PEOPLE_SCREEN_ARTICLE_SORT_TIME_TAG,
                                            )
                                        }
                                    },
                                ) {
                                    FeedCard(
                                        it.toPeopleArticleDisplayItem(),
                                        readingQueueSourceId = readingQueueSourceId,
                                        modifier = Modifier.testTag("people_screen_article_item_${it.id}"),
                                    ) { _, destination ->
                                        destination?.let(navigator.onNavigate)
                                    }
                                }
                            }
                        }

                        2 -> {
                            PaginatedList(
                                items = viewModel.activitiesFeedModel.displayItems,
                                listState = listState,
                                onLoadMore = { viewModel.activitiesFeedModel.loadMore(paginationEnvironment) },
                                isEnd = { viewModel.activitiesFeedModel.isEnd },
                                isLoading = { viewModel.activitiesFeedModel.isLoading },
                                loadError = { viewModel.activitiesFeedModel.errorMessage },
                                onRetry = { viewModel.activitiesFeedModel.retry(paginationEnvironment) },
                                key = { it.stableKey },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pageTurnViewportWithGuide(pageTurnTarget)
                                    .testTag(PEOPLE_SCREEN_ACTIVITIES_LIST_TAG),
                            ) {
                                FeedCard(
                                    it,
                                    readingQueueSourceId = readingQueueSourceId,
                                    modifier = Modifier.testTag("people_screen_activity_item_${it.stableKey}"),
                                )
                            }
                        }

                        3 -> {
                            PaginatedList(
                                items = viewModel.collectionsFeedModel.allData,
                                listState = listState,
                                onLoadMore = { viewModel.collectionsFeedModel.loadMore(paginationEnvironment) },
                                isEnd = { viewModel.collectionsFeedModel.isEnd },
                                isLoading = { viewModel.collectionsFeedModel.isLoading },
                                loadError = { viewModel.collectionsFeedModel.errorMessage },
                                onRetry = { viewModel.collectionsFeedModel.retry(paginationEnvironment) },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pageTurnViewportWithGuide(pageTurnTarget)
                                    .testTag(PEOPLE_SCREEN_COLLECTIONS_LIST_TAG),
                                key = { it.id },
                            ) { collection ->
                                CollectionListItem(
                                    collection = collection,
                                    itemTag = "people_screen_collection_item_${collection.id}",
                                )
                            }
                        }

                        4 -> {
                            PaginatedList(
                                items = viewModel.questionsFeedModel.allData,
                                listState = listState,
                                onLoadMore = { viewModel.questionsFeedModel.loadMore(paginationEnvironment) },
                                isEnd = { viewModel.questionsFeedModel.isEnd },
                                isLoading = { viewModel.questionsFeedModel.isLoading },
                                loadError = { viewModel.questionsFeedModel.errorMessage },
                                onRetry = { viewModel.questionsFeedModel.retry(paginationEnvironment) },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pageTurnViewportWithGuide(pageTurnTarget)
                                    .testTag(PEOPLE_SCREEN_QUESTIONS_LIST_TAG),
                                key = { it.id },
                            ) { question ->
                                QuestionListItem(
                                    question = question,
                                    itemTag = "people_screen_question_item_${question.id}",
                                )
                            }
                        }

                        5 -> {
                            PaginatedList(
                                items = viewModel.pinsFeedModel.allData,
                                listState = listState,
                                onLoadMore = { viewModel.pinsFeedModel.loadMore(paginationEnvironment) },
                                isEnd = { viewModel.pinsFeedModel.isEnd },
                                isLoading = { viewModel.pinsFeedModel.isLoading },
                                loadError = { viewModel.pinsFeedModel.errorMessage },
                                onRetry = { viewModel.pinsFeedModel.retry(paginationEnvironment) },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pageTurnViewportWithGuide(pageTurnTarget)
                                    .testTag(PEOPLE_SCREEN_PINS_LIST_TAG),
                                key = { it.id },
                            ) { pin ->
                                PinListItem(
                                    pin = pin,
                                    itemTag = "people_screen_pin_item_${pin.id}",
                                    readingQueueSourceId = readingQueueSourceId,
                                )
                            }
                        }

                        6 -> {
                            PaginatedList(
                                items = viewModel.columnsFeedModel.allData,
                                listState = listState,
                                onLoadMore = { viewModel.columnsFeedModel.loadMore(paginationEnvironment) },
                                isEnd = { viewModel.columnsFeedModel.isEnd },
                                isLoading = { viewModel.columnsFeedModel.isLoading },
                                loadError = { viewModel.columnsFeedModel.errorMessage },
                                onRetry = { viewModel.columnsFeedModel.retry(paginationEnvironment) },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pageTurnViewportWithGuide(pageTurnTarget)
                                    .testTag(PEOPLE_SCREEN_COLUMNS_LIST_TAG),
                                key = { it.id },
                            ) { column ->
                                ColumnListItem(
                                    column = column,
                                    itemTag = "people_screen_column_item_${column.id}",
                                )
                            }
                        }

                        7 -> {
                            PaginatedList(
                                items = viewModel.followersFeedModel.allData,
                                listState = listState,
                                onLoadMore = { viewModel.followersFeedModel.loadMore(paginationEnvironment) },
                                isEnd = { viewModel.followersFeedModel.isEnd },
                                isLoading = { viewModel.followersFeedModel.isLoading },
                                loadError = { viewModel.followersFeedModel.errorMessage },
                                onRetry = { viewModel.followersFeedModel.retry(paginationEnvironment) },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pageTurnViewportWithGuide(pageTurnTarget)
                                    .testTag(PEOPLE_SCREEN_FOLLOWERS_LIST_TAG),
                                key = { it.id },
                            ) { people ->
                                PeopleListItem(
                                    people = people,
                                    itemTag = "people_screen_follower_item_${people.id}",
                                    actionTag = "people_screen_follower_action_${people.id}",
                                )
                            }
                        }

                        8 -> {
                            PaginatedList(
                                items = viewModel.followingFeedModel.allData,
                                listState = listState,
                                onLoadMore = { viewModel.followingFeedModel.loadMore(paginationEnvironment) },
                                isEnd = { viewModel.followingFeedModel.isEnd },
                                isLoading = { viewModel.followingFeedModel.isLoading },
                                loadError = { viewModel.followingFeedModel.errorMessage },
                                onRetry = { viewModel.followingFeedModel.retry(paginationEnvironment) },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pageTurnViewportWithGuide(pageTurnTarget)
                                    .testTag(PEOPLE_SCREEN_FOLLOWING_LIST_TAG),
                                key = { it.id },
                            ) { people ->
                                PeopleListItem(
                                    people = people,
                                    itemTag = "people_screen_following_item_${people.id}",
                                    actionTag = "people_screen_following_action_${people.id}",
                                )
                            }
                        }

                        9 -> {
                            FollowingSubscriptionsPage(
                                viewModel = viewModel,
                                listState = listState,
                                pageTurnTarget = pageTurnTarget,
                                paginationEnvironment = paginationEnvironment,
                                modifier = Modifier.testTag("people_screen_page_$page"),
                            )
                        }
                    }
                }
            }
        }
    }
    if (showBlockConfirm) {
        AppDialog(
            title = "拉黑 ${viewModel.name}？",
            summary = "会同步到你的知乎账号。之后可以在这个页面点“取消拉黑”恢复。",
            confirmText = "拉黑",
            onConfirm = {
                showBlockConfirm = false
                coroutineScope.launch {
                    try {
                        viewModel.toggleBlock(paginationEnvironment)
                        userMessages.showShortMessage("已拉黑")
                    } catch (error: Exception) {
                        userMessages.showShortMessage("操作失败: ${error.message}")
                    }
                }
            },
            dismissText = "取消",
            onDismissRequest = { showBlockConfirm = false },
            destructive = true,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FollowingSubscriptionsPage(
    viewModel: PersonViewModel,
    paginationEnvironment: PaginationEnvironment,
    listState: LazyListState,
    pageTurnTarget: PageTurnTarget,
    modifier: Modifier = Modifier,
) {
    var selectedPage by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(selectedPage) {
        val model = when (selectedPage) {
            0 -> viewModel.followingColumnsFeedModel
            1 -> viewModel.followingTopicsFeedModel
            2 -> viewModel.followingQuestionsFeedModel
            else -> viewModel.followingCollectionsFeedModel
        }
        if (model.allData.isEmpty() && !model.isEnd && model.errorMessage == null) {
            model.loadMore(paginationEnvironment)
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(PEOPLE_SCREEN_SUBSCRIPTION_TABS_TAG)
                .padding(vertical = AppDimens.Space8, horizontal = AppDimens.PageHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Space8),
        ) {
            PEOPLE_SCREEN_SUBSCRIPTION_TITLES.forEachIndexed { index, title ->
                AppOutlinedButton(
                    onClick = { selectedPage = index },
                    modifier = Modifier
                        .testTag("people_screen_subscription_tab_$index")
                        .semantics {
                            selected = selectedPage == index
                            role = Role.Tab
                        },
                    shape = RoundedCornerShape(AppDimens.SmallCornerRadius),
                    containerColor = if (selectedPage == index) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                    contentColor = if (selectedPage == index) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                ) {
                    Text(title)
                }
            }
        }

        when (selectedPage) {
            0 -> PaginatedList(
                items = viewModel.followingColumnsFeedModel.allData,
                listState = listState,
                onLoadMore = { viewModel.followingColumnsFeedModel.loadMore(paginationEnvironment) },
                isEnd = { viewModel.followingColumnsFeedModel.isEnd },
                isLoading = { viewModel.followingColumnsFeedModel.isLoading },
                loadError = { viewModel.followingColumnsFeedModel.errorMessage },
                onRetry = { viewModel.followingColumnsFeedModel.retry(paginationEnvironment) },
                modifier = Modifier
                    .fillMaxSize()
                    .pageTurnViewportWithGuide(pageTurnTarget)
                    .testTag(PEOPLE_SCREEN_SUBSCRIPTIONS_LIST_TAG),
                key = { it.id },
            ) { column ->
                ColumnListItem(
                    column = column,
                    itemTag = "people_screen_column_item_${column.id}",
                )
            }

            1 -> PaginatedList(
                items = viewModel.followingTopicsFeedModel.allData,
                listState = listState,
                onLoadMore = { viewModel.followingTopicsFeedModel.loadMore(paginationEnvironment) },
                isEnd = { viewModel.followingTopicsFeedModel.isEnd },
                isLoading = { viewModel.followingTopicsFeedModel.isLoading },
                loadError = { viewModel.followingTopicsFeedModel.errorMessage },
                onRetry = { viewModel.followingTopicsFeedModel.retry(paginationEnvironment) },
                modifier = Modifier
                    .fillMaxSize()
                    .pageTurnViewportWithGuide(pageTurnTarget)
                    .testTag(PEOPLE_SCREEN_SUBSCRIPTIONS_LIST_TAG),
                key = { it.displayId },
            ) { topic ->
                FollowedTopicListItem(topic)
            }

            2 -> PaginatedList(
                items = viewModel.followingQuestionsFeedModel.allData,
                listState = listState,
                onLoadMore = { viewModel.followingQuestionsFeedModel.loadMore(paginationEnvironment) },
                isEnd = { viewModel.followingQuestionsFeedModel.isEnd },
                isLoading = { viewModel.followingQuestionsFeedModel.isLoading },
                loadError = { viewModel.followingQuestionsFeedModel.errorMessage },
                onRetry = { viewModel.followingQuestionsFeedModel.retry(paginationEnvironment) },
                modifier = Modifier
                    .fillMaxSize()
                    .pageTurnViewportWithGuide(pageTurnTarget)
                    .testTag(PEOPLE_SCREEN_SUBSCRIPTIONS_LIST_TAG),
                key = { it.id },
            ) { question ->
                FollowedQuestionListItem(question)
            }

            3 -> PaginatedList(
                items = viewModel.followingCollectionsFeedModel.allData,
                listState = listState,
                onLoadMore = { viewModel.followingCollectionsFeedModel.loadMore(paginationEnvironment) },
                isEnd = { viewModel.followingCollectionsFeedModel.isEnd },
                isLoading = { viewModel.followingCollectionsFeedModel.isLoading },
                loadError = { viewModel.followingCollectionsFeedModel.errorMessage },
                onRetry = { viewModel.followingCollectionsFeedModel.retry(paginationEnvironment) },
                modifier = Modifier
                    .fillMaxSize()
                    .pageTurnViewportWithGuide(pageTurnTarget)
                    .testTag(PEOPLE_SCREEN_SUBSCRIPTIONS_LIST_TAG),
                key = { it.id },
            ) { collection ->
                CollectionListItem(
                    collection = collection,
                    itemTag = "people_screen_collection_item_${collection.id}",
                )
            }
        }
    }
}

@Composable
private fun CollectionListItem(
    collection: DataHolder.Collection,
    itemTag: String? = null,
) {
    val navigator = LocalNavigator.current
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Space16, vertical = AppDimens.Space4)
            .then(if (itemTag != null) Modifier.testTag(itemTag) else Modifier),
        onClick = {
            navigator.onNavigate(CollectionContent(collection.id))
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.Space16),
        ) {
            Text(
                text = collection.title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "${collection.answerCount} 内容 · ${collection.followerCount} 关注",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AppDimens.Space4),
            )
        }
    }
}

@Composable
private fun QuestionListItem(
    question: DataHolder.Question,
    itemTag: String? = null,
) {
    val navigator = LocalNavigator.current
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Space16, vertical = AppDimens.Space4)
            .then(if (itemTag != null) Modifier.testTag(itemTag) else Modifier),
        onClick = {
            navigator.onNavigate(Question(question.id, question.title))
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.Space16),
        ) {
            Text(
                text = question.title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "${question.answerCount} 回答 · ${question.followerCount} 关注",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AppDimens.Space4),
            )
        }
    }
}

@Composable
private fun PinListItem(
    pin: DataHolder.Pin,
    itemTag: String? = null,
    readingQueueSourceId: String? = null,
) {
    val navigator = LocalNavigator.current
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Space16, vertical = AppDimens.Space4)
            .then(if (itemTag != null) Modifier.testTag(itemTag) else Modifier),
        onClick = {
            navigator.onNavigate(
                Pin(
                    id = pin.id.toLong(),
                    authorName = pin.author.name,
                    readingQueueSourceId = readingQueueSourceId,
                ),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.Space16),
        ) {
            val text = remember { Ksoup.parse(pin.excerptTitle).text() }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${pin.likeCount} 赞 · ${pin.commentCount} 评论",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AppDimens.Space4),
            )
        }
    }
}

@Composable
private fun ColumnListItem(
    column: DataHolder.Column,
    itemTag: String? = null,
) {
    val openZhihuWebUrl = rememberZhihuWebUrlOpener()
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Space16, vertical = AppDimens.Space4)
            .then(if (itemTag != null) Modifier.testTag(itemTag) else Modifier),
        onClick = {
            openZhihuWebUrl(column.webUrl())
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.Space16),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = column.title,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (column.description.isNotEmpty()) {
                    Text(
                        text = column.description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = AppDimens.Space4),
                    )
                }
                val counts = listOfNotNull(
                    column.articlesCount?.let { "$it 文章" },
                    listOfNotNull(column.followerCount, column.followers).maxOrNull()?.let { "$it 关注" },
                )
                if (counts.isNotEmpty()) {
                    Text(
                        text = counts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = AppDimens.Space4),
                    )
                }
            }
        }
    }
}

@Composable
private fun FollowedQuestionListItem(question: FollowedQuestion) {
    val navigator = LocalNavigator.current
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Space16, vertical = AppDimens.Space4)
            .testTag("people_screen_followed_question_item_${question.id}"),
        onClick = {
            question.id.toLongOrNull()?.let {
                navigator.onNavigate(Question(it, question.title))
            }
        },
    ) {
        Text(
            text = question.title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(AppDimens.Space16),
        )
    }
}

@Composable
private fun FollowedTopicListItem(topic: FollowedTopic) {
    val navigator = LocalNavigator.current
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Space16, vertical = AppDimens.Space4)
            .testTag("people_screen_followed_topic_item_${topic.displayId}"),
        onClick = {
            navigator.onNavigate(
                com.github.zly2006.zhihu.navigation
                    .Topic(topic.displayId, topic.displayName),
            )
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.Space16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = topic.displayAvatarUrl,
                contentDescription = "话题头像",
                modifier = Modifier
                    .padding(end = AppDimens.Space12)
                    .size(AppDimens.ProfileActionSize)
                    .clip(CircleShape),
            )
            Text(
                text = topic.displayName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun DataHolder.Column.webUrl(): String = when {
    url.contains("/api/v4/columns/") ->
        url
            .replace("http://", "https://")
            .replace("/api/v4/columns/", "/column/")

    url.startsWith("http") && !url.contains("/api/") -> url.replace("http://", "https://")
    else -> "https://www.zhihu.com/column/$id"
}

@Composable
private fun PeopleListItem(
    people: DataHolder.People,
    itemTag: String? = null,
    actionTag: String? = null,
) {
    val navigator = LocalNavigator.current
    val skin = LocalAppSkinTokens.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space4)
            .then(itemTag?.let { Modifier.testTag(it) } ?: Modifier),
    ) {
        AppCard(
            modifier = Modifier
                .fillMaxWidth()
                .then(actionTag?.let { Modifier.testTag(it) } ?: Modifier),
            onClick = {
                navigator.onNavigate(Person(id = people.id, name = people.name, urlToken = people.urlToken.orEmpty()))
            },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(AppDimens.Space16),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = people.avatarUrl,
                    contentDescription = "用户头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(AppDimens.ProfileEditActionSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.Space4),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = people.name,
                            style = skin.title4,
                            color = skin.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        val officialBadge = people.badgeV2.officialBadge()
                        if (officialBadge?.isUsefulInList == true) {
                            AuthorBadge(badge = officialBadge, compact = true, modifier = Modifier.padding(start = AppDimens.Space6))
                        }
                    }
                    if (people.headline.isNotBlank()) {
                        Text(
                            text = people.headline,
                            style = skin.footnote2,
                            color = skin.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = "${formatCompactCount(people.answerCount)} 回答 · ${formatCompactCount(people.articlesCount)} 文章 · ${formatCompactCount(people.followerCount)} 粉丝",
                        style = skin.footnote2,
                        color = skin.onSurfaceVariantSummary,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = skin.onSurfaceVariantActions)
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    tag: String? = null,
) {
    val skin = LocalAppSkinTokens.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppDimens.Space4),
        modifier = modifier
            .then(tag?.let { Modifier.testTag(it) } ?: Modifier)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$value $label" }
            .padding(horizontal = AppDimens.Space4, vertical = AppDimens.Space6),
    ) {
        Text(text = formatCompactCount(value), style = skin.title2, color = skin.onSurface, fontWeight = FontWeight.SemiBold)
        Text(text = label, style = skin.footnote2, color = skin.onSurfaceVariantSummary)
    }
}

@Composable
private fun OfficialBadgeDetails(
    badges: List<OfficialBadge>,
    modifier: Modifier = Modifier,
) {
    if (badges.isEmpty()) return
    val primaryBadge = badges.first()
    val skin = LocalAppSkinTokens.current
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (primaryBadge.iconUrl.isNotBlank()) {
                if (primaryBadge.iconUrl == DataHolder.ZH_PLUS_AUTHOR_BADGE_ICON) {
                    Image(
                        painter = painterResource(Res.drawable.ic_zh_plus_author_badge),
                        contentDescription = primaryBadge.description,
                        modifier = Modifier
                            .padding(end = AppDimens.Space6)
                            .size(AppDimens.IconSizeCompact),
                    )
                } else {
                    AsyncImage(
                        model = primaryBadge.iconUrl,
                        contentDescription = primaryBadge.description,
                        modifier = Modifier
                            .padding(end = AppDimens.Space6)
                            .size(AppDimens.IconSizeCompact),
                    )
                }
            }
            Text(
                text = "${primaryBadge.peopleDetailTitle}: ${primaryBadge.description}",
                style = skin.footnote2,
                color = skin.onSurfaceVariantSummary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (badges.size > 1) {
                Spacer(Modifier.width(AppDimens.Space6))
                Text(
                    text = "+${badges.size - 1}",
                    style = skin.footnote2,
                    color = if (settingSkinIsMiuix()) skin.onSurfaceVariantSummary else skin.onSurfaceVariantActions,
                )
            }
        }
    }
}

private val OfficialBadge.peopleDetailTitle: String
    get() = when {
        title == "认证" || title == "已认证的个人" -> "认证信息"
        else -> title
    }

@Composable
private fun SortBar(
    currentSort: String,
    onSortChange: (String) -> Unit,
    hotTag: String,
    timeTag: String,
) {
    val skin = LocalAppSkinTokens.current
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space4),
        horizontalArrangement = Arrangement.End,
    ) {
        Box {
            AppTextButton(
                onClick = { expanded = true },
                modifier = Modifier.heightIn(min = AppDimens.TouchTarget).testTag("${hotTag}_control"),
                contentColor = if (settingSkinIsMiuix()) skin.onSurface else skin.onSurfaceVariantActions,
                contentPadding = PaddingValues(horizontal = AppDimens.Space8),
            ) {
                Text(if (currentSort == "voteups") "按热度" else "按时间", style = skin.footnote1)
                Spacer(Modifier.width(AppDimens.Space4))
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(AppDimens.IconSizeSmall))
            }
            AppSelectionDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                options = listOf("按热度", "按时间"),
                selectedIndex = if (currentSort == "voteups") 0 else 1,
                onSelect = { index ->
                    expanded = false
                    val sort = if (index == 0) "voteups" else "created"
                    if (currentSort != sort) onSortChange(sort)
                },
            ) {
                listOf(Triple("按热度", "voteups", hotTag), Triple("按时间", "created", timeTag)).forEach { (label, sort, tag) ->
                    AppDropdownMenuItem(
                        text = { Text(label) },
                        modifier = Modifier.testTag(tag).semantics { selected = currentSort == sort },
                        trailingIcon = if (currentSort == sort) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        onClick = {
                            expanded = false
                            if (currentSort != sort) onSortChange(sort)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun CollapsibleHeader(
    collapsedFraction: Float,
    onExpandedHeightChanged: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    SubcomposeLayout(modifier = Modifier.clipToBounds()) { constraints ->
        val placeable = subcompose("header", content).single().measure(
            constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity),
        )
        if (placeable.height > 0) {
            onExpandedHeightChanged(placeable.height)
        }
        val height = (placeable.height * (1f - collapsedFraction)).roundToInt()
        layout(constraints.maxWidth, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            placeable.place(0, height - placeable.height)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserInfoHeader(
    viewModel: PersonViewModel,
    pagerState: PagerState,
    onSearch: (() -> Unit)?,
    showSearch: Boolean,
    modifier: Modifier = Modifier,
    onFollowToggle: () -> Unit,
) {
    val skin = LocalAppSkinTokens.current
    val coroutineScope = rememberCoroutineScope()
    val openImagePreview = rememberImagePreviewOpener()
    val openExternalUrl = rememberExternalUrlOpener()
    var showFullProfile by rememberSaveable(viewModel.person.userTokenOrId) { mutableStateOf(false) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val minimumNameWidth = with(density) {
        textMeasurer
            .measure(
                viewModel.name.take(4),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                softWrap = false,
            ).size.width
            .toDp()
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = AppDimens.Space8, bottom = AppDimens.Space16),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Space12),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space16),
            verticalAlignment = Alignment.Top,
        ) {
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Space16),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Space12),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = viewModel.avatar,
                    contentDescription = "用户头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .testTag(PEOPLE_SCREEN_AVATAR_TAG)
                        .size(AppDimens.ProfileAvatarLarge)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable(enabled = viewModel.avatar.isNotBlank()) {
                            openImagePreview(viewModel.avatar.substringBefore("_") + ".jpg")
                        },
                )
                Column(
                    modifier = Modifier.widthIn(min = minimumNameWidth).weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.Space8),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = viewModel.name,
                            style = MaterialTheme.typography.headlineMedium,
                            color = skin.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f, fill = false).semantics { heading() },
                        )
                        viewModel.officialBadge?.let { badge ->
                            AuthorBadge(
                                badge = badge,
                                modifier = Modifier
                                    .padding(start = AppDimens.Space6)
                                    .testTag(PEOPLE_SCREEN_OFFICIAL_BADGE_TAG),
                            )
                        }
                    }
                    val followModifier = Modifier
                        .heightIn(min = AppDimens.TouchTarget)
                        .testTag(PEOPLE_SCREEN_FOLLOW_BUTTON_TAG)
                        .semantics {
                            selected = viewModel.isFollowing
                            role = Role.Button
                        }
                    if (viewModel.isFollowing) {
                        AppOutlinedButton(onClick = onFollowToggle, modifier = followModifier) {
                            Text("已关注")
                        }
                    } else {
                        AppButton(onClick = onFollowToggle, modifier = followModifier) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(AppDimens.IconSizeSmall))
                            Spacer(Modifier.width(AppDimens.Space4))
                            Text("关注")
                        }
                    }
                }
            }
            if (onSearch != null) {
                Box(Modifier.size(AppDimens.TouchTarget), contentAlignment = Alignment.Center) {
                    if (showSearch) {
                        AppIconButton(onClick = onSearch, modifier = Modifier.testTag(PEOPLE_SCREEN_SEARCH_BUTTON_TAG)) {
                            Icon(Icons.Default.Search, contentDescription = "搜索 TA 的创作", tint = skin.onSurfaceVariantActions)
                        }
                    }
                }
            }
        }
        if (viewModel.headline.isNotBlank() || viewModel.officialBadgeDetails.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { showFullProfile = true }
                    .testTag(PEOPLE_SCREEN_PROFILE_DETAILS_TAG)
                    .semantics { contentDescription = "查看完整资料" },
                verticalArrangement = Arrangement.spacedBy(AppDimens.Space6),
            ) {
                if (viewModel.headline.isNotBlank()) {
                    Text(
                        text = viewModel.headline,
                        style = skin.body2,
                        color = skin.onSurfaceVariantSummary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OfficialBadgeDetails(badges = viewModel.officialBadgeDetails)
            }
        }
        viewModel.githubSocial?.let { githubSocial ->
            Row(
                modifier = Modifier
                    .testTag(PEOPLE_SCREEN_GITHUB_STARS_TAG)
                    .clickable { openExternalUrl(githubSocial.profileUrl) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Space6),
            ) {
                githubSocial.iconUrl?.let { iconUrl ->
                    AsyncImage(model = iconUrl, contentDescription = null, modifier = Modifier.size(AppDimens.IconSizeSmall))
                }
                Text(
                    text = githubSocial.title,
                    modifier = Modifier.weight(1f, fill = false),
                    style = skin.footnote1,
                    color = skin.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "· ${githubSocial.starCount} stars",
                    style = skin.footnote1,
                    color = skin.onSurfaceVariantSummary,
                    maxLines = 1,
                )
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val minimumStatWidth = with(density) {
                listOf(
                    "回答" to viewModel.answerCount,
                    "文章" to viewModel.articleCount,
                    "粉丝" to viewModel.followerCount,
                    "关注" to viewModel.followingCount,
                ).maxOf { (label, value) ->
                    maxOf(
                        textMeasurer.measure(formatCompactCount(value), style = skin.title2.copy(fontWeight = FontWeight.SemiBold), softWrap = false).size.width,
                        textMeasurer.measure(label, style = skin.footnote2, softWrap = false).size.width,
                    )
                }.toDp() + AppDimens.Space4 * 2
            }
            val columns = when {
                maxWidth >= minimumStatWidth * 4 + AppDimens.Space4 * 3 -> 4
                maxWidth >= minimumStatWidth * 2 + AppDimens.Space4 -> 2
                else -> 1
            }
            val statWidth = (maxWidth - AppDimens.Space4 * (columns - 1)) / columns
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Space4),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Space8),
                maxItemsInEachRow = columns,
            ) {
                StatItem("回答", viewModel.answerCount, modifier = Modifier.width(statWidth), onClick = {
                    coroutineScope.launch { pagerState.animateScrollToPage(0) }
                }, tag = PEOPLE_SCREEN_ANSWER_COUNT_TAG)
                StatItem("文章", viewModel.articleCount, modifier = Modifier.width(statWidth), onClick = {
                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                }, tag = PEOPLE_SCREEN_ARTICLE_COUNT_TAG)
                StatItem("粉丝", viewModel.followerCount, modifier = Modifier.width(statWidth), onClick = {
                    coroutineScope.launch { pagerState.animateScrollToPage(7) }
                }, tag = PEOPLE_SCREEN_FOLLOWER_COUNT_TAG)
                StatItem("关注", viewModel.followingCount, modifier = Modifier.width(statWidth), onClick = {
                    coroutineScope.launch { pagerState.animateScrollToPage(8) }
                }, tag = PEOPLE_SCREEN_FOLLOWING_COUNT_TAG)
            }
        }
    }
    if (showFullProfile) {
        AppAlertDialog(
            title = { Text(viewModel.name) },
            text = {
                Text(
                    buildString {
                        append(viewModel.headline)
                        viewModel.officialBadgeDetails.forEach { badge ->
                            if (isNotEmpty()) append("\n\n")
                            append(badge.peopleDetailTitle).append(": ").append(badge.description)
                        }
                    },
                )
            },
            confirmButton = {
                AppTextButton("关闭", { showFullProfile = false })
            },
            onDismissRequest = { showFullProfile = false },
        )
    }
}
