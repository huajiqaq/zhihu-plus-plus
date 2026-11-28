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

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.flattenFeeds
import com.github.zly2006.zhihu.data.pinSummaryText
import com.github.zly2006.zhihu.data.toDisplayItem
import com.github.zly2006.zhihu.i18n.ContentStrings
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Topic
import com.github.zly2006.zhihu.navigation.WritePin
import com.github.zly2006.zhihu.platform.platformSettingsStore
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppCompactTopBar
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppIcon
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppMetadataLoadState
import com.github.zly2006.zhihu.ui.components.AppOutlinedButton
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.AppSegmentedControl
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.AppTabRow
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.FeedCard
import com.github.zly2006.zhihu.ui.components.PaginatedList
import com.github.zly2006.zhihu.ui.components.ShareDialog
import com.github.zly2006.zhihu.ui.components.getShareText
import com.github.zly2006.zhihu.ui.components.handleShareAction
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.ui.components.rememberShareActionExecutor
import com.github.zly2006.zhihu.util.raiseForStatus
import com.github.zly2006.zhihu.viewmodel.FeedDisplaySettings
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import com.github.zly2006.zhihu.viewmodel.deleteSigned
import com.github.zly2006.zhihu.viewmodel.postSigned
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

const val TOPIC_SCREEN_BACK_BUTTON_TAG = "topic_screen_back_button"

const val TOPIC_SCREEN_TAG = "topic_screen"
const val TOPIC_SHARE_BUTTON_TAG = "topic_share_button"
const val TOPIC_FOLLOW_BUTTON_TAG = "topic_follow_button"
const val TOPIC_WRITE_PIN_BUTTON_TAG = "topic_write_pin_button"
const val TOPIC_INTRODUCTION_TOGGLE_TAG = "topic_introduction_toggle"
const val TOPIC_RETRY_BUTTON_TAG = "topic_retry_button"
const val TOPIC_DETAIL_LOADING_TAG = "topic_detail_loading"
const val TOPIC_DETAIL_ERROR_TAG = "topic_detail_error"
const val TOPIC_DETAIL_RETRY_TAG = "topic_detail_retry"

@Serializable
data class TopicDetail(
    val id: String,
    val name: String = "",
    val excerpt: String = "",
    val avatarUrl: String? = null,
    val followersCount: Int = 0,
    val questionsCount: Int = 0,
    val isFollowing: Boolean = false,
    val topicId: Long? = null,
    val totalPv: String = "",
    val discussCount: String = "",
)

enum class TopicFeedTab(
    private val titleText: () -> String,
) {
    Discussion({ ContentStrings.discussion }),
    Ideas({ ContentStrings.posts }),
    Unanswered({ ContentStrings.unanswered }),
    ;

    val title: String get() = titleText()
}

enum class TopicDiscussionSort(
    private val titleText: () -> String,
) {
    Essence({ ContentStrings.essence }),
    Hot({ ContentStrings.hottest }),
    Timeline({ ContentStrings.latest }),
    ;

    val title: String get() = titleText()
}

enum class TopicIdeasSort(
    private val titleText: () -> String,
    val endpoint: String,
) {
    Hot({ ContentStrings.hottest }, "pin-hot"),
    Latest({ ContentStrings.latest }, "pin-new"),
    ;

    val title: String get() = titleText()
}

@Serializable
data class TopicPinFeed(
    val type: String,
    val target: TopicPinTarget,
)

@Serializable
data class TopicPinTarget(
    val id: JsonPrimitive,
    val type: String,
    val url: String = "",
    val author: TopicPinAuthor,
    val title: String = "",
    val excerpt: String = "",
    val content: String = "",
    val plainContent: String = "",
    val counter: TopicPinCounter = TopicPinCounter(),
)

@Serializable
data class TopicPinAuthor(
    val avatarUrl: String = "",
    val name: String = "",
)

@Serializable
data class TopicPinCounter(
    val applaud: Int = 0,
    val comment: Int = 0,
    val favorite: Int = 0,
    val forward: Int = 0,
    val pv: Int = 0,
)

@Serializable
private data class TopicPaging(
    val isEnd: Boolean = true,
    val next: String? = null,
)

class TopicViewModel(
    private val topicId: String,
    initialDetail: TopicDetail? = null,
) : ViewModel() {
    var detail by mutableStateOf(initialDetail)
        private set
    val items = mutableStateListOf<FeedDisplayItem>()
    private val pinTargets = mutableMapOf<String, TopicPinTarget>()

    fun localizedItem(item: FeedDisplayItem): FeedDisplayItem {
        val target = pinTargets[item.stableKey] ?: return item
        return item.copy(
            title = target.title.ifBlank { ContentStrings.pin },
            details = ContentStrings.pinDetails(target.counter.applaud, target.counter.comment),
        )
    }

    var selectedTab by mutableStateOf(TopicFeedTab.Discussion)
        private set
    var discussionSort by mutableStateOf(TopicDiscussionSort.Hot)
        private set
    var ideasSort by mutableStateOf(TopicIdeasSort.Hot)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var detailErrorMessage by mutableStateOf<String?>(null)
        private set
    var isDetailLoading by mutableStateOf(false)
        private set
    private var nextUrl: String? = null
    private val fetchedPageUrls = mutableSetOf<String>()
    var completedPageCount by mutableIntStateOf(0)
        private set
    var isEnd by mutableStateOf(false)
        private set
    private var requestGeneration = 0L
    private var loadJob: Job? = null
    var isFollowingChanging by mutableStateOf(false)
        private set

    private var detailLoadJob: Job? = null
    private var detailLoadGeneration = 0

    override fun onCleared() {
        detailLoadGeneration++
        detailLoadJob?.cancel()
        super.onCleared()
    }

    suspend fun loadDetail(environment: PaginationEnvironment) {
        if (isDetailLoading) return
        val generation = ++detailLoadGeneration
        val client = environment.httpClient()
        detailLoadJob = currentCoroutineContext()[Job]
        isDetailLoading = true
        detailErrorMessage = null
        try {
            val json = environment.fetchJson(
                "https://www.zhihu.com/api/v5.1/topics/$topicId",
                "name,excerpt,avatar_url,followers_count,questions_count,is_following,topic_id,total_pv,discuss_count",
            ) ?: error(ContentStrings.topicDetailEmptyResponse)
            val loadedDetail = ZhihuJson.decodeJson(TopicDetail.serializer(), json)
            currentCoroutineContext().ensureActive()
            if (generation != detailLoadGeneration || environment.httpClient() !== client) {
                throw CancellationException("加载已失效")
            }
            detail = loadedDetail
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (generation == detailLoadGeneration && environment.httpClient() === client) {
                detailErrorMessage = error.message?.takeIf(String::isNotBlank) ?: ContentStrings.topicLoadFailed
            }
        } finally {
            if (generation == detailLoadGeneration) {
                isDetailLoading = false
                detailLoadJob = null
            }
        }
    }

    fun selectTab(environment: PaginationEnvironment, tab: TopicFeedTab) {
        if (selectedTab == tab) return
        loadJob?.cancel()
        loadJob = null
        isLoading = false
        selectedTab = tab
        items.clear()
        pinTargets.clear()
        fetchedPageUrls.clear()
        errorMessage = null
        nextUrl = null
        isEnd = false
        requestGeneration++
        loadMore(environment)
    }

    fun selectDiscussionSort(environment: PaginationEnvironment, sort: TopicDiscussionSort) {
        if (discussionSort == sort) return
        loadJob?.cancel()
        loadJob = null
        isLoading = false
        discussionSort = sort
        items.clear()
        pinTargets.clear()
        fetchedPageUrls.clear()
        errorMessage = null
        nextUrl = null
        isEnd = false
        requestGeneration++
        loadMore(environment)
    }

    fun selectIdeasSort(environment: PaginationEnvironment, sort: TopicIdeasSort) {
        if (ideasSort == sort) return
        loadJob?.cancel()
        loadJob = null
        isLoading = false
        ideasSort = sort
        items.clear()
        pinTargets.clear()
        fetchedPageUrls.clear()
        errorMessage = null
        nextUrl = null
        isEnd = false
        requestGeneration++
        loadMore(environment)
    }

    private suspend fun loadMoreNow(environment: PaginationEnvironment) {
        if (isEnd) return
        val generation = requestGeneration
        isLoading = true
        errorMessage = null
        try {
            val url = nextUrl ?: topicFeedUrl(topicId, selectedTab, discussionSort, ideasSort)
            val json = environment.fetchJson(url, "data[*].content,excerpt,target.author.badge_v2")
                ?: error(ContentStrings.topicContentEmptyResponse)
            currentCoroutineContext().ensureActive()
            if (generation != requestGeneration) return
            val responseItems = (json["data"] as? JsonArray).orEmpty()
            val decodedItems = if (selectedTab == TopicFeedTab.Ideas) {
                decodeTopicPinFeeds(json) { item, target -> pinTargets[item.stableKey] = target }
            } else {
                val feeds = responseItems.mapNotNull { element ->
                    runCatching { ZhihuJson.decodeJson<Feed>(element) }.getOrNull()
                }
                feeds.flattenFeeds().map { it.toDisplayItem(enableQualityFilter = false) }
            }
            if (responseItems.isNotEmpty() && decodedItems.isEmpty()) {
                error(ContentStrings.topicContentDecodeError(responseItems.size))
            }
            val loadedItems = if (selectedTab == TopicFeedTab.Discussion) {
                val settings = FeedDisplaySettings(platformSettingsStore())
                decodedItems.mapNotNull(settings::applyQualityFilter)
            } else {
                decodedItems
            }
            loadedItems.forEach { item ->
                if (items.none { it.stableKey == item.stableKey }) items += item
            }
            val paging = json["paging"]?.let { ZhihuJson.decodeJson<TopicPaging>(it) }
            val rawNext = paging?.next?.takeIf(String::isNotBlank)
            nextUrl = rawNext?.let(::normalizeTopicPagingUrl)
            fetchedPageUrls += url
            isEnd = if (rawNext != null && nextUrl == null) {
                errorMessage = ContentStrings.untrustedPagingUrl
                true
            } else {
                paging?.isEnd != false || nextUrl == null || nextUrl in fetchedPageUrls
            }
            completedPageCount++
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (generation == requestGeneration) {
                errorMessage = error.message ?: error::class.simpleName ?: ContentStrings.unknownError
            }
        } finally {
            if (generation == requestGeneration) isLoading = false
        }
    }

    fun loadMore(environment: PaginationEnvironment) {
        if (isEnd || isLoading || errorMessage != null || loadJob?.isActive == true) return
        loadJob = viewModelScope.launch { loadMoreNow(environment) }
    }

    fun retry(environment: PaginationEnvironment) {
        errorMessage = null
        loadMore(environment)
    }

    fun initializeSection(section: String) {
        selectedTab = when (section) {
            "unanswered" -> TopicFeedTab.Unanswered
            else -> TopicFeedTab.Discussion
        }
        discussionSort = when (section) {
            "top-answers" -> TopicDiscussionSort.Essence
            "newest" -> TopicDiscussionSort.Timeline
            else -> TopicDiscussionSort.Hot
        }
    }

    suspend fun setFollowing(environment: ZhihuApiEnvironment, following: Boolean): Result<Unit> {
        val current = detail ?: return Result.failure(IllegalStateException(ContentStrings.topicDetailsNotLoaded))
        if (isFollowingChanging || current.isFollowing == following) return Result.success(Unit)
        isFollowingChanging = true
        detail = current.copy(
            isFollowing = following,
            followersCount = (current.followersCount + if (following) 1 else -1).coerceAtLeast(0),
        )
        return runCatching {
            val endpoint = "https://www.zhihu.com/api/v4/topics/$topicId/followers"
            if (following) environment.postSigned(endpoint) else environment.deleteSigned(endpoint)
        }.mapCatching { response ->
            response.raiseForStatus()
            Unit
        }.onFailure {
            detail = current
        }.also {
            isFollowingChanging = false
        }
    }
}

fun topicFeedUrl(
    topicId: String,
    tab: TopicFeedTab,
    discussionSort: TopicDiscussionSort = TopicDiscussionSort.Hot,
    ideasSort: TopicIdeasSort = TopicIdeasSort.Hot,
): String = when (tab) {
    TopicFeedTab.Discussion -> when (discussionSort) {
        TopicDiscussionSort.Essence -> "https://www.zhihu.com/api/v5.1/topics/$topicId/feeds/top_activity/v2?limit=20&offset=0"
        TopicDiscussionSort.Hot -> "https://www.zhihu.com/api/v5.1/topics/$topicId/feeds/essence/v2?limit=20&offset=0"
        TopicDiscussionSort.Timeline -> "https://www.zhihu.com/api/v5.1/topics/$topicId/feeds/timeline_activity/v2?limit=20&offset=0"
    }
    TopicFeedTab.Ideas -> "https://www.zhihu.com/api/v5.1/topics/$topicId/feeds/${ideasSort.endpoint}?offset=0&limit=10"
    TopicFeedTab.Unanswered -> "https://www.zhihu.com/api/v5.1/topics/$topicId/feeds/top_question/v2?limit=20&offset=0"
}

fun decodeTopicPinFeeds(
    json: kotlinx.serialization.json.JsonObject,
    onDecoded: (FeedDisplayItem, TopicPinTarget) -> Unit = { _, _ -> },
): List<FeedDisplayItem> =
    (json["data"] as? JsonArray).orEmpty().mapNotNull { element ->
        runCatching { ZhihuJson.decodeJson<TopicPinFeed>(element) }.getOrNull()?.target?.let { target ->
            val pinId = target.id.content.toLongOrNull() ?: return@let null
            FeedDisplayItem(
                title = target.title.ifBlank { ContentStrings.pin },
                summary = target.plainContent.ifBlank { target.excerpt.ifBlank { target.content.pinSummaryText() } }.takeIf(String::isNotBlank),
                details = ContentStrings.pinDetails(target.counter.applaud, target.counter.comment),
                feed = null,
                destination = Pin(pinId),
                avatarSrc = target.author.avatarUrl,
                authorName = target.author.name,
            ).also { onDecoded(it, target) }
        }
    }

fun normalizeTopicPagingUrl(rawUrl: String): String? {
    val url = runCatching { Url(rawUrl) }.getOrNull() ?: return null
    if (url.host == "www.zhihu.com" &&
        (url.encodedPath.startsWith("/api/v4/") || url.encodedPath.startsWith("/api/v5.1/"))
    ) {
        return "https://www.zhihu.com${url.encodedPath}" + url.encodedQuery
            .takeIf(String::isNotEmpty)
            ?.let { "?$it" }
            .orEmpty()
    }
    if (url.host != "172.16.201.121" || url.port != 80) return null
    return buildString {
        append("https://www.zhihu.com/api/v4")
        append(url.encodedPath)
        if (url.encodedQuery.isNotEmpty()) append('?').append(url.encodedQuery)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicScreen(topic: Topic) {
    val navigator = LocalNavigator.current
    val environment = rememberPaginationEnvironment(allowGuestAccess = false)
    val messages = rememberUserMessageSink()
    val settings = rememberSettingsStore()
    val executeShareAction = rememberShareActionExecutor()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var showShareDialog by androidx.compose.runtime.remember { mutableStateOf(false) }
    var isIntroductionExpanded by rememberSaveable(topic.id) { mutableStateOf(false) }
    val viewModel: TopicViewModel = viewModel(key = "topic_${topic.id}_${topic.section}") {
        TopicViewModel(topic.id).apply { initializeSection(topic.section) }
    }
    val listState = rememberLazyListState()
    val pageTurnTarget = rememberPageTurnTarget(listState = listState, enabled = !showShareDialog)

    LaunchedEffect(viewModel) {
        if (viewModel.detail == null || viewModel.detailErrorMessage != null) {
            launch { viewModel.loadDetail(environment) }
        }
        if (viewModel.items.isEmpty() && !viewModel.isEnd && viewModel.errorMessage == null) {
            viewModel.loadMore(environment)
        }
    }

    LaunchedEffect(viewModel, viewModel.completedPageCount, viewModel.isLoading) {
        if (viewModel.selectedTab == TopicFeedTab.Discussion &&
            viewModel.completedPageCount > 0 &&
            viewModel.items.isEmpty() &&
            !viewModel.isEnd &&
            !viewModel.isLoading &&
            viewModel.errorMessage == null
        ) {
            viewModel.loadMore(environment)
        }
    }

    AppScaffold(
        modifier = Modifier.testTag(TOPIC_SCREEN_TAG),
        topBar = {
            AppCompactTopBar(
                title = viewModel.detail?.name?.ifBlank { topic.name } ?: topic.name.ifBlank { ContentStrings.topic },
                onBack = navigator.onNavigateBack,
                backTag = TOPIC_SCREEN_BACK_BUTTON_TAG,
                actions = {
                    AppIconButton(
                        modifier = Modifier.testTag(TOPIC_SHARE_BUTTON_TAG),
                        onClick = {
                            val loadedTopic = topic.copy(name = viewModel.detail?.name ?: topic.name)
                            handleShareAction(loadedTopic, settings, executeShareAction) { showShareDialog = true }
                        },
                    ) { AppIcon(Icons.Default.Share, contentDescription = ContentStrings.share) }
                },
            )
        },
    ) { padding ->
        PaginatedList(
            items = viewModel.items,
            onLoadMore = { viewModel.loadMore(environment) },
            isEnd = { viewModel.isEnd },
            isLoading = { viewModel.isLoading },
            loadError = { viewModel.errorMessage },
            onRetry = { viewModel.retry(environment) },
            key = FeedDisplayItem::stableKey,
            listState = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pageTurnViewportWithGuide(pageTurnTarget),
            contentPadding = PaddingValues(bottom = AppDimens.Space24),
            topContent = {
                item {
                    TopicHeader(
                        detail = viewModel.detail,
                        detailErrorMessage = viewModel.detailErrorMessage,
                        isDetailLoading = viewModel.isDetailLoading,
                        destination = topic,
                        isIntroductionExpanded = isIntroductionExpanded,
                        onIntroductionExpandedChange = { isIntroductionExpanded = it },
                        isFollowingChanging = viewModel.isFollowingChanging,
                        onRetryDetail = { scope.launch { viewModel.loadDetail(environment) } },
                        onFollowingChange = { following ->
                            scope.launch {
                                viewModel.setFollowing(environment, following).onFailure {
                                    messages.showShortMessage(if (following) ContentStrings.followFailed(it.message) else ContentStrings.unfollowFailed(it.message))
                                }
                            }
                        },
                        onWritePin = {
                            navigator.onNavigate(
                                WritePin(
                                    topicName = viewModel.detail?.name?.ifBlank { topic.name } ?: topic.name,
                                    publishTopicId = viewModel.detail
                                        ?.topicId
                                        ?.toString()
                                        .orEmpty(),
                                ),
                            )
                        },
                    )
                    AppTabRow(
                        titles = TopicFeedTab.entries.map { it.title },
                        selectedIndex = viewModel.selectedTab.ordinal,
                        onSelect = { index ->
                            viewModel.selectTab(environment, TopicFeedTab.entries[index])
                        },
                        testTags = TopicFeedTab.entries.map { "topic_tab_${it.name.lowercase()}" },
                    )
                    if (viewModel.selectedTab == TopicFeedTab.Discussion) {
                        AppSegmentedControl(
                            options = TopicDiscussionSort.entries.map { it.title },
                            selectedIndex = viewModel.discussionSort.ordinal,
                            onSelect = { index ->
                                viewModel.selectDiscussionSort(environment, TopicDiscussionSort.entries[index])
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            testTags = TopicDiscussionSort.entries.map {
                                "topic_discussion_sort_${it.name.lowercase()}"
                            },
                        )
                    }
                    if (viewModel.selectedTab == TopicFeedTab.Ideas) {
                        AppSegmentedControl(
                            options = TopicIdeasSort.entries.map { it.title },
                            selectedIndex = viewModel.ideasSort.ordinal,
                            onSelect = { index ->
                                viewModel.selectIdeasSort(environment, TopicIdeasSort.entries[index])
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            testTags = TopicIdeasSort.entries.map {
                                "topic_ideas_sort_${it.name.lowercase()}"
                            },
                        )
                    }
                }
            },
        ) { item ->
            FeedCard(item = viewModel.localizedItem(item), modifier = Modifier.testTag("topic_feed_${item.stableKey}"))
        }
    }
    val loadedTopic = topic.copy(name = viewModel.detail?.name ?: topic.name)
    getShareText(loadedTopic)?.let { shareText ->
        ShareDialog(
            content = loadedTopic,
            shareText = shareText,
            showDialog = showShareDialog,
            onDismissRequest = { showShareDialog = false },
        )
    }
}

@Composable
private fun TopicHeader(
    detail: TopicDetail?,
    detailErrorMessage: String?,
    isDetailLoading: Boolean,
    destination: Topic,
    isIntroductionExpanded: Boolean,
    onIntroductionExpandedChange: (Boolean) -> Unit,
    isFollowingChanging: Boolean,
    onRetryDetail: () -> Unit,
    onFollowingChange: (Boolean) -> Unit,
    onWritePin: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space12),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Space16),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = detail?.avatarUrl,
                contentDescription = ContentStrings.topicAvatar,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(AppDimens.MediaCornerRadius))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = detail?.name?.ifBlank { destination.name } ?: destination.name,
                    style = LocalAppSkinTokens.current.headline2,
                    color = LocalAppSkinTokens.current.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                when {
                    detail != null -> Text(
                        text = listOfNotNull(
                            detail.totalPv.takeIf(String::isNotBlank)?.let { ContentStrings.topicViews(formatTopicCount(it)) },
                            detail.discussCount.takeIf(String::isNotBlank)?.let { ContentStrings.topicDiscussions(formatTopicCount(it)) },
                        ).joinToString(" · ").ifBlank { ContentStrings.topic },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        AppMetadataLoadState(
            isLoading = isDetailLoading,
            errorMessage = detailErrorMessage,
            hasContent = detail != null,
            onRetry = onRetryDetail,
            loadingTag = TOPIC_DETAIL_LOADING_TAG,
            errorTag = TOPIC_DETAIL_ERROR_TAG,
            retryTag = TOPIC_DETAIL_RETRY_TAG,
        )
        if (detail != null) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Space24),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Space8),
            ) {
                TopicStat(
                    label = ContentStrings.followers,
                    value = formatTopicCount(detail.followersCount.toString()),
                )
                TopicStat(
                    label = ContentStrings.questionsAsked,
                    value = formatTopicCount(detail.questionsCount.toString()),
                )
                TopicStat(
                    label = ContentStrings.views,
                    value = detail.totalPv.takeIf(String::isNotBlank)?.let(::formatTopicCount) ?: "—",
                )
                TopicStat(
                    label = ContentStrings.discussion,
                    value = detail.discussCount.takeIf(String::isNotBlank)?.let(::formatTopicCount) ?: "—",
                )
            }
        }
        detail?.excerpt?.takeIf(String::isNotBlank)?.let { introduction ->
            TopicIntroduction(
                introduction = introduction,
                isExpanded = isIntroductionExpanded,
                onExpandedChange = onIntroductionExpandedChange,
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Space8),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            val isFollowing = detail?.isFollowing == true
            AppButton(
                onClick = { detail?.let { onFollowingChange(!it.isFollowing) } },
                enabled = detail != null && !isFollowingChanging,
                modifier =
                    Modifier
                        .width(IntrinsicSize.Max)
                        .testTag(TOPIC_FOLLOW_BUTTON_TAG)
                        .semantics { selected = isFollowing },
                containerColor = if (isFollowing) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.primary
                },
                contentColor = if (isFollowing) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onPrimary
                },
            ) {
                if (isFollowingChanging) {
                    AppSpinner()
                } else {
                    Text(if (isFollowing) ContentStrings.following else ContentStrings.followTopic)
                }
            }
            AppOutlinedButton(
                onClick = onWritePin,
                enabled = detail?.topicId != null,
                modifier = Modifier.width(IntrinsicSize.Max).testTag(TOPIC_WRITE_PIN_BUTTON_TAG),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Text(ContentStrings.writePin)
            }
        }
    }
}

@Composable
private fun TopicStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TopicIntroduction(
    introduction: String,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    var canCollapse by remember(introduction) { mutableStateOf(false) }
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerLow
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clipToBounds()
                .animateContentSize(
                    animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                ),
    ) {
        Text(
            text = introduction,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (isExpanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Clip,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(if (canCollapse) Modifier.padding(bottom = 56.dp) else Modifier),
            onTextLayout = { result ->
                canCollapse = result.lineCount > 3 || result.hasVisualOverflow
            },
        )
        if (canCollapse && !isExpanded) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(88.dp)
                        .blur(12.dp)
                        .background(
                            brush =
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        surfaceColor.copy(alpha = 0.7f),
                                        surfaceColor,
                                    ),
                                ),
                        ),
            )
        }
        if (canCollapse) {
            AppTextButton(
                onClick = { onExpandedChange(!isExpanded) },
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(y = 4.dp)
                        .padding(end = 4.dp)
                        .testTag(TOPIC_INTRODUCTION_TOGGLE_TAG),
            ) {
                AppIcon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                )
                Spacer(Modifier.width(4.dp))
                Text(if (isExpanded) ContentStrings.collapseDescription else ContentStrings.expandDescription)
            }
        }
    }
}
