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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.LocalHistoryStorage
import com.github.zly2006.zhihu.data.decodePinContentDetail
import com.github.zly2006.zhihu.data.officialBadge
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.resolveContent
import com.github.zly2006.zhihu.navigation.withListPreview
import com.github.zly2006.zhihu.platform.rememberExternalUrlOpener
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.reading.ReadingPlaybackStatus
import com.github.zly2006.zhihu.reading.ReadingQueueSourceRegistry
import com.github.zly2006.zhihu.reading.ReadingStartRequest
import com.github.zly2006.zhihu.reading.hasReadableFields
import com.github.zly2006.zhihu.reading.isReadingPlayerSupported
import com.github.zly2006.zhihu.reading.loadReadingPlaybackSpeed
import com.github.zly2006.zhihu.reading.loadReadingPreferences
import com.github.zly2006.zhihu.reading.rememberReadingPlayerController
import com.github.zly2006.zhihu.reading.toReadingQueueItem
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppCard
import com.github.zly2006.zhihu.ui.components.AppChip
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppEmptyState
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppOutlinedButton
import com.github.zly2006.zhihu.ui.components.AppPageTitleBar
import com.github.zly2006.zhihu.ui.components.AppPageTitleBarScrollState
import com.github.zly2006.zhihu.ui.components.AppPullToRefreshBox
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.AuthorBadge
import com.github.zly2006.zhihu.ui.components.CommentScreenComponent
import com.github.zly2006.zhihu.ui.components.ContentEndMarker
import com.github.zly2006.zhihu.ui.components.PageTurnTarget
import com.github.zly2006.zhihu.ui.components.ShareDialog
import com.github.zly2006.zhihu.ui.components.VotersSheet
import com.github.zly2006.zhihu.ui.components.appPageTitleBarScroll
import com.github.zly2006.zhihu.ui.components.getShareText
import com.github.zly2006.zhihu.ui.components.handleShareAction
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.ui.components.rememberAppPageTitleBarScrollState
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.ui.components.rememberShareActionExecutor
import com.github.zly2006.zhihu.util.formatCompactCount
import com.github.zly2006.zhihu.util.jsonObject
import com.github.zly2006.zhihu.util.raiseForStatus
import com.github.zly2006.zhihu.util.twoDigitString
import com.github.zly2006.zhihu.viewmodel.ContentLoadEnvironment
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import com.github.zly2006.zhihu.viewmodel.addReadHistory
import com.github.zly2006.zhihu.viewmodel.deleteSigned
import com.github.zly2006.zhihu.viewmodel.loadVotersPage
import com.github.zly2006.zhihu.viewmodel.nextUrlOrNull
import com.github.zly2006.zhihu.viewmodel.postSigned
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.replaceOrAppendUniqueVoters
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonArray
import kotlin.time.Instant
import androidx.compose.material.icons.outlined.ThumbUp as OutlinedThumbUp
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

const val PIN_SCREEN_BACK_BUTTON_TAG = "pin_screen_back_button"
const val PIN_SCREEN_SHARE_BUTTON_TAG = "pin_screen_share_button"
const val PIN_SCREEN_READING_BUTTON_TAG = "pin_screen_reading_button"
const val PIN_SCREEN_LOADING_TAG = "pin_screen_loading"
const val PIN_SCREEN_ERROR_TAG = "pin_screen_error"
const val PIN_SCREEN_SCROLL_TAG = "pin_screen_scroll"
const val PIN_SCREEN_POLL_CARD_TAG = "pin_screen_poll_card"
const val PIN_SCREEN_RETRY_TAG = "pin_screen_retry"
const val PIN_SCREEN_BODY_EMPTY_TAG = "pin_screen_body_empty"
const val PIN_SCREEN_OPERATION_ERROR_TAG = "pin_screen_operation_error"

fun pinScreenPollOptionTag(optionId: String): String = "pin_screen_poll_option_$optionId"

private suspend fun togglePinLike(
    environment: ZhihuApiEnvironment,
    pin: Pin,
    isLiked: Boolean,
): PinLikeResult {
    val endpoint = "https://www.zhihu.com/api/v4/pins/${pin.id}/voters/up"
    val jojo = if (isLiked) {
        environment.deleteSigned(endpoint).raiseForStatus().jsonObject()
    } else {
        environment.postSigned(endpoint).raiseForStatus().jsonObject()
    }
    return PinLikeResult(
        isLiked = !isLiked,
        likeCount = jojo["liked_count"]?.jsonPrimitive?.intOrNull ?: -1,
    )
}

const val PIN_SCREEN_AUTHOR_TAG = "pin_screen_author"
const val PIN_SCREEN_LINK_CARD_TAG = "pin_screen_link_card"
const val PIN_SCREEN_LIKE_BUTTON_TAG = "pin_screen_like_button"
const val PIN_SCREEN_COMMENT_BUTTON_TAG = "pin_screen_comment_button"

internal class PinScreenData : ViewModel() {
    val content = mutableStateOf<DataHolder.Pin?>(null)
    val isLiked = mutableStateOf(false)
    val likeCount = mutableIntStateOf(0)
    val errorMessage = mutableStateOf<String?>(null)
    val isLoading = mutableStateOf(false)
    private var detailLoadJob: Job? = null
    private var detailLoadGeneration = 0

    override fun onCleared() {
        detailLoadGeneration++
        detailLoadJob?.cancel()
        super.onCleared()
    }

    suspend fun load(environment: ContentLoadEnvironment, pin: Pin) {
        if (isLoading.value) return
        val generation = ++detailLoadGeneration
        val client = environment.httpClient()
        detailLoadJob = currentCoroutineContext()[Job]
        isLoading.value = true
        errorMessage.value = null
        try {
            environment.addReadHistory(pin.id.toString(), "pin")
            currentCoroutineContext().ensureActive()
            if (generation != detailLoadGeneration || environment.httpClient() !== client) {
                throw CancellationException("加载已失效")
            }
            val jsonObject = environment.fetchJson("https://www.zhihu.com/api/v4/pins/${pin.id}?include=topics", "")
                ?: error("想法详情为空")
            val loadedPin = decodePinContentDetail(jsonObject)
            currentCoroutineContext().ensureActive()
            if (generation != detailLoadGeneration || environment.httpClient() !== client) {
                throw CancellationException("加载已失效")
            }
            LocalHistoryStorage().add(pin.copy(authorName = loadedPin.author.name))
            environment.recordContentOpenEvent(destination = pin)
            currentCoroutineContext().ensureActive()
            if (generation != detailLoadGeneration || environment.httpClient() !== client) {
                throw CancellationException("加载已失效")
            }
            content.value = loadedPin
            isLiked.value = loadedPin.virtuals.booleanCompat("isLiked", "is_liked")
            likeCount.intValue = loadedPin.likeCount
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (generation == detailLoadGeneration && environment.httpClient() === client) {
                errorMessage.value = error.message?.takeIf(String::isNotBlank) ?: "想法详情加载失败"
            }
        } finally {
            if (generation == detailLoadGeneration) {
                isLoading.value = false
                detailLoadJob = null
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinScreen(
    pin: Pin,
) {
    val navigator = LocalNavigator.current
    val coroutineScope = rememberCoroutineScope()
    val paginationEnvironment = rememberPaginationEnvironment(allowGuestAccess = false)

    val settings = rememberSettingsStore()
    val readingPreferences = loadReadingPreferences(settings)
    val readingPlaybackSpeed = loadReadingPlaybackSpeed(settings)
    val readingPlayer = rememberReadingPlayerController()
    val readingPlayerState by readingPlayer.state
    val executeShareAction = rememberShareActionExecutor()
    val screenData: PinScreenData = viewModel(key = "pin_${pin.id}") { PinScreenData() }
    val isLoading by screenData.isLoading
    val errorMessage by screenData.errorMessage
    var pinContent by screenData.content
    var isLiked by screenData.isLiked
    var likeCount by screenData.likeCount
    var pollVotingOptionId by remember(pin.id) { mutableStateOf<String?>(null) }
    var pollErrorMessage by remember(pin.id) { mutableStateOf<String?>(null) }
    var likeChanging by remember(pin.id) { mutableStateOf(false) }
    var operationError by remember(pin.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(pin.id, screenData) {
        if (pinContent == null && errorMessage == null) screenData.load(paginationEnvironment, pin)
    }

    var showShareDialog by remember { mutableStateOf(false) }
    var showComments by rememberSaveable(pin.id) { mutableStateOf(false) }
    var showVoters by rememberSaveable(pin.id) { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val pinTopBarScrollBehavior = rememberAppPageTitleBarScrollState()
    var votersNextUrl by remember(pin.id) { mutableStateOf<String?>(null) }
    var votersLoading by remember(pin.id) { mutableStateOf(false) }
    var votersError by remember(pin.id) { mutableStateOf<String?>(null) }
    val voters = remember(pin.id) { mutableStateListOf<DataHolder.Author>() }
    val pageTurnActive = pinContent != null && !showComments && !showVoters && !showShareDialog
    val pageTurnTarget = rememberPageTurnTarget(
        scrollState = scrollState,
        enabled = pageTurnActive,
    )

    val toggleLike: () -> Unit = {
        if (!likeChanging) {
            likeChanging = true
            operationError = null
            coroutineScope.launch {
                try {
                    val wasLiked = isLiked
                    val result = togglePinLike(paginationEnvironment, pin, wasLiked)
                    isLiked = result.isLiked
                    likeCount = result.likeCount.takeIf { it >= 0 }
                        ?: (likeCount + if (wasLiked) -1 else 1).coerceAtLeast(0)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    operationError = error.message?.takeIf(String::isNotBlank) ?: "点赞操作失败，请重试"
                } finally {
                    likeChanging = false
                }
            }
        }
    }

    fun loadMoreVoters(reset: Boolean = false) {
        if (votersLoading) return
        coroutineScope.launch {
            votersLoading = true
            votersError = null
            try {
                val page = loadVotersPage(
                    environment = paginationEnvironment,
                    initialUrl = "https://www.zhihu.com/api/v4/pins/${pin.id}/upvoters?limit=10&offset=0",
                    nextUrl = votersNextUrl,
                    reset = reset,
                )
                voters.replaceOrAppendUniqueVoters(page.data, reset)
                val total = page.paging.totals.takeIf { it > 0 } ?: likeCount
                likeCount = total
                votersNextUrl = page.nextUrlOrNull()
            } catch (error: CancellationException) {
                throw error
            } catch (e: Exception) {
                votersError = e.message?.takeIf(String::isNotBlank) ?: "加载赞同者失败"
            } finally {
                votersLoading = false
            }
        }
    }

    LaunchedEffect(showVoters, pin.id) {
        if (showVoters && voters.isEmpty()) loadMoreVoters(reset = true)
    }

    AppScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AppPageTitleBar(
                title = pinContent?.author?.name?.let { "${it}的想法" } ?: "想法",
                scrollState = pinTopBarScrollBehavior,
                onBack = navigator.onNavigateBack,
                backTag = PIN_SCREEN_BACK_BUTTON_TAG,
                actions = {
                    val readingItem = pinContent?.toReadingQueueItem(pin)
                    val isCurrentReadingItem = readingItem?.key == readingPlayerState.currentItem?.key
                    AppIconButton(
                        onClick = {
                            val item = readingItem ?: return@AppIconButton
                            if (isCurrentReadingItem) {
                                readingPlayer.togglePlayPause()
                            } else {
                                coroutineScope.launch {
                                    readingPlayer.start(
                                        ReadingStartRequest(
                                            queue = ReadingQueueSourceRegistry.queueStartingAt(
                                                current = item,
                                                sourceId = pin.readingQueueSourceId,
                                                limit = readingPreferences.queueLimit,
                                            ),
                                            preferences = readingPreferences,
                                            sourceId = pin.readingQueueSourceId,
                                            playbackSpeed = readingPlaybackSpeed,
                                        ),
                                    )
                                }
                            }
                        },
                        enabled = isReadingPlayerSupported && readingItem?.hasReadableFields(readingPreferences) == true,
                        modifier = Modifier.testTag(PIN_SCREEN_READING_BUTTON_TAG),
                    ) {
                        when {
                            isCurrentReadingItem &&
                                readingPlayerState.status in setOf(
                                    ReadingPlaybackStatus.Initializing,
                                    ReadingPlaybackStatus.Loading,
                                ) -> AppSpinner(modifier = Modifier, size = 22.dp)
                            isCurrentReadingItem && readingPlayerState.isActivelyPlaying -> Icon(
                                Icons.Default.Pause,
                                contentDescription = "暂停朗读",
                            )
                            else -> Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isCurrentReadingItem) "继续朗读" else "开始连续朗读",
                            )
                        }
                    }
                    AppIconButton(
                        onClick = {
                            val shareText = getShareText(pin)
                            if (shareText != null) {
                                handleShareAction(pin, settings, executeShareAction) {
                                    showShareDialog = true
                                }
                            }
                        },
                        modifier = Modifier.testTag(PIN_SCREEN_SHARE_BUTTON_TAG),
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "分享")
                    }
                },
            )
        },
        bottomBar = {
            if (pinContent != null) {
                PinActionBar(
                    isLiked = isLiked,
                    likeCount = likeCount,
                    commentCount = pinContent?.commentCount ?: 0,
                    onLikeClick = toggleLike,
                    likeChanging = likeChanging,
                    onCommentClick = { showComments = true },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                isLoading && pinContent == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(PIN_SCREEN_LOADING_TAG),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppSpinner()
                    }
                }

                errorMessage != null && pinContent == null -> {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        AppEmptyState(
                            title = "想法加载失败",
                            description = errorMessage,
                            modifier = Modifier.testTag(PIN_SCREEN_ERROR_TAG),
                            action = {
                                AppButton(
                                    text = "重试",
                                    onClick = { coroutineScope.launch { screenData.load(paginationEnvironment, pin) } },
                                    modifier = Modifier.testTag(PIN_SCREEN_RETRY_TAG),
                                )
                            },
                        )
                    }
                }

                pinContent != null -> {
                    val loadedPin = pinContent ?: return@Box
                    AppPullToRefreshBox(
                        isRefreshing = isLoading,
                        modifier = Modifier.fillMaxSize(),
                        onRefresh = {
                            if (!isLoading && !likeChanging && pollVotingOptionId == null) {
                                coroutineScope.launch { screenData.load(paginationEnvironment, pin) }
                            }
                        },
                    ) {
                        PinContent(
                            pin = loadedPin,
                            scrollState = scrollState,
                            pageTurnTarget = pageTurnTarget,
                            topBarScrollBehavior = pinTopBarScrollBehavior,
                            environment = paginationEnvironment,
                            likeCount = likeCount,
                            pollVotingOptionId = pollVotingOptionId,
                            pollErrorMessage = pollErrorMessage,
                            loadError = errorMessage,
                            operationError = operationError,
                            onRetry = { coroutineScope.launch { screenData.load(paginationEnvironment, pin) } },
                            onRetryLike = toggleLike,
                            onSocialCreditClick = { showVoters = true },
                            onPollVote = { pollId, optionId ->
                                coroutineScope.launch {
                                    pollVotingOptionId = optionId
                                    pollErrorMessage = null
                                    try {
                                        paginationEnvironment.postSigned("https://www.zhihu.com/api/v4/polls/$pollId") {
                                            contentType(ContentType.Application.Json)
                                            setBody(buildJsonObject { putJsonArray("options") { add(optionId) } }.toString())
                                        }.raiseForStatus()
                                        pinContent = pinContent?.withSelectedPinPollOption(pollId, optionId)
                                        val result = togglePinLike(paginationEnvironment, pin, false)
                                        isLiked = result.isLiked
                                        likeCount = result.likeCount
                                        pollVotingOptionId = null
                                    } catch (error: CancellationException) {
                                        throw error
                                    } catch (e: Exception) {
                                        pollVotingOptionId = null
                                        pollErrorMessage = e.message?.takeIf(String::isNotBlank) ?: "投票失败"
                                    } finally {
                                        pollVotingOptionId = null
                                    }
                                }
                            },
                        )
                    }

                    CommentScreenComponent(
                        showComments = showComments,
                        onDismiss = { showComments = false },
                        content = pin,
                        isZhPlusAuthorContent = pinContent?.author?.id == DataHolder.ZH_PLUS_AUTHOR_USER_ID,
                    )

                    val shareText = getShareText(pin)
                    if (shareText != null) {
                        ShareDialog(
                            content = pin,
                            shareText = shareText,
                            showDialog = showShareDialog,
                            onDismissRequest = { showShareDialog = false },
                        )
                    }

                    VotersSheet(
                        show = showVoters,
                        title = "${formatCompactCount(likeCount)} 人赞同了该想法",
                        voters = voters,
                        isLoading = votersLoading,
                        errorMessage = votersError,
                        canLoadMore = votersNextUrl != null,
                        onDismissRequest = { showVoters = false },
                        onLoadMore = { loadMoreVoters() },
                        onRetry = { loadMoreVoters(reset = voters.isEmpty()) },
                        onNavigate = { person ->
                            showVoters = false
                            navigator.onNavigate(person)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PinActionBar(
    isLiked: Boolean,
    likeCount: Int,
    commentCount: Int,
    onLikeClick: () -> Unit,
    likeChanging: Boolean,
    onCommentClick: () -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.PageHorizontalPadding)
            .padding(
                top = 4.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Space8),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        AppOutlinedButton(
            onClick = onLikeClick,
            enabled = !likeChanging,
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .testTag(PIN_SCREEN_LIKE_BUTTON_TAG)
                .semantics {
                    stateDescription = if (isLiked) "已点赞" else "未点赞"
                    onClick(label = if (isLiked) "取消点赞" else "点赞", action = null)
                },
            containerColor = if (isLiked) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (isLiked) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ) {
            Icon(
                if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.OutlinedThumbUp,
                contentDescription = if (isLiked) "取消点赞" else "点赞",
                modifier = Modifier.size(AppDimens.Space20),
            )
            Spacer(Modifier.width(AppDimens.Space6))
            Text(
                if (likeChanging) "提交中…" else "点赞 · $likeCount",
                style = MaterialTheme.typography.labelLarge,
            )
        }
        AppOutlinedButton(
            onClick = onCommentClick,
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .testTag(PIN_SCREEN_COMMENT_BUTTON_TAG),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Comment,
                contentDescription = "评论",
                modifier = Modifier.size(AppDimens.Space20),
            )
            Spacer(Modifier.width(AppDimens.Space6))
            Text("评论 · $commentCount", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PinContent(
    pin: DataHolder.Pin,
    scrollState: ScrollState,
    pageTurnTarget: PageTurnTarget,
    topBarScrollBehavior: AppPageTitleBarScrollState,
    environment: ZhihuApiEnvironment,
    likeCount: Int,
    pollVotingOptionId: String?,
    pollErrorMessage: String?,
    loadError: String?,
    operationError: String?,
    onRetry: () -> Unit,
    onRetryLike: () -> Unit,
    onSocialCreditClick: () -> Unit,
    onPollVote: (pollId: String, optionId: String) -> Unit,
) {
    val navigator = LocalNavigator.current
    val readingPlayerOverlayPadding = LocalReadingPlayerOverlayPadding.current
    val openExternalUrl = rememberExternalUrlOpener()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .appPageTitleBarScroll(topBarScrollBehavior)
            .pageTurnViewportWithGuide(pageTurnTarget)
            .verticalScroll(scrollState)
            .testTag(PIN_SCREEN_SCROLL_TAG)
            .padding(
                start = AppDimens.PageHorizontalPadding,
                end = AppDimens.PageHorizontalPadding,
                top = 12.dp,
                bottom = 24.dp,
            ),
    ) {
        if (loadError != null) {
            Text(
                text = "更新失败：$loadError，已保留原内容",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(PIN_SCREEN_ERROR_TAG),
            )
            AppTextButton(text = "重试", onClick = onRetry, modifier = Modifier.testTag(PIN_SCREEN_RETRY_TAG))
        }
        if (operationError != null) {
            Text(
                text = "操作失败：$operationError",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(PIN_SCREEN_OPERATION_ERROR_TAG),
            )
            AppTextButton(text = "重试点赞", onClick = onRetryLike)
        }
        val textMeasurer = rememberTextMeasurer()
        val minimumAuthorWidth = with(LocalDensity.current) {
            textMeasurer
                .measure(
                    pin.author.name.take(4),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    softWrap = false,
                ).size.width
                .toDp() + AppDimens.TouchTarget + AppDimens.Space12
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(PIN_SCREEN_AUTHOR_TAG)
                .clickable {
                    navigator.onNavigate(
                        Person(
                            id = pin.author.id,
                            urlToken = pin.author.urlToken,
                            name = pin.author.name,
                        ),
                    )
                },
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Space8),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.widthIn(min = minimumAuthorWidth).weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = pin.author.avatarUrl,
                    contentDescription = "头像",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            pin.author.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        val authorBadge = pin.author.badgeV2.officialBadge()
                        if (authorBadge != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                            AuthorBadge(authorBadge)
                        }
                    }
                    if (pin.author.headline.isNotEmpty()) {
                        Text(
                            pin.author.headline,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (!pin.selfCreate) {
                AppTextButton(
                    text = if (pin.author.isFollowing) "已关注 · 查看" else "查看主页",
                    onClick = {
                        navigator.onNavigate(Person(pin.author.id, pin.author.urlToken, pin.author.name))
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            buildString {
                append("发布于")
                append(Instant.fromEpochSeconds(pin.created).toLocalDateTime(TimeZone.currentSystemDefault()).run { "$year-${(month.ordinal + 1).twoDigitString()}-${day.twoDigitString()} ${hour.twoDigitString()}:${minute.twoDigitString()}" })
                if (pin.updated > pin.created) {
                    append(" · 编辑于")
                    append(Instant.fromEpochSeconds(pin.updated).toLocalDateTime(TimeZone.currentSystemDefault()).run { "$year-${(month.ordinal + 1).twoDigitString()}-${day.twoDigitString()} ${hour.twoDigitString()}:${minute.twoDigitString()}" })
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (likeCount > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            val firstLiker = pin.likers.firstOrNull()
            Text(
                text = if (firstLiker != null) {
                    "${firstLiker.name} 等 ${formatCompactCount(likeCount)} 人赞同了该想法"
                } else {
                    "${formatCompactCount(likeCount)} 人赞同了该想法"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onSocialCreditClick),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (pin.contentHtml.isBlank() && pin.content.isEmpty() && pin.bottomPoll == null) {
            AppEmptyState(
                title = "暂无可显示的正文",
                description = "可以查看作者主页或评论，也可以下拉刷新",
                modifier = Modifier.testTag(PIN_SCREEN_BODY_EMPTY_TAG),
            )
        } else {
            PinHtmlContent(pin.contentHtml)
        }

        val votingPoll = pin.bottomPoll?.voting
        if (votingPoll != null) {
            Spacer(modifier = Modifier.height(16.dp))
            PinPollCard(
                poll = votingPoll,
                votingOptionId = pollVotingOptionId,
                errorMessage = pollErrorMessage,
                onPollVote = onPollVote,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        val linkCard = pin.content.firstOrNull {
            it is DataHolder.Pin.ContentLinkCard
        } as? DataHolder.Pin.ContentLinkCard
        if (linkCard != null) {
            var relatedTitle by remember(linkCard.dataContentType, linkCard.dataContentId, linkCard.url) {
                mutableStateOf<String?>(null)
            }
            var relatedPreview by remember(linkCard.dataContentType, linkCard.dataContentId, linkCard.url) {
                mutableStateOf<String?>(null)
            }
            var isRelatedLoading by remember(linkCard.dataContentType, linkCard.dataContentId, linkCard.url) {
                mutableStateOf(true)
            }

            LaunchedEffect(linkCard.dataContentType, linkCard.dataContentId, linkCard.url) {
                isRelatedLoading = true
                val preview = fetchPinLinkCardPreview(linkCard, environment)
                relatedTitle = preview?.title
                relatedPreview = preview?.preview
                isRelatedLoading = false
            }

            AppCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(PIN_SCREEN_LINK_CARD_TAG)
                    .clickable {
                        val targetUrl = linkCard.url.takeIf { it.isNotBlank() }
                        val destination = targetUrl?.let(::resolveContent)?.withListPreview(title = relatedTitle, excerpt = relatedPreview)

                        if (destination != null) {
                            navigator.onNavigate(destination)
                        } else {
                            targetUrl?.let {
                                openExternalUrl(it)
                            }
                        }
                    },
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        "关联内容",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isRelatedLoading) {
                        Text(
                            text = "正在加载关联内容...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (!relatedTitle.isNullOrBlank()) {
                        Text(
                            text = relatedTitle!!,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!relatedPreview.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = relatedPreview!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        Text(
                            text = "${linkCardTypeLabel(linkCard.dataContentType)} · ${linkCard.dataContentId}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (relatedTitle.isNullOrBlank() && linkCard.url.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = linkCard.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        val topics = pin.topics
        if (topics?.isNotEmpty() == true) {
            Spacer(modifier = Modifier.height(24.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                topics.forEach { topic ->
                    AppChip(
                        label = "# ${topic.name}",
                        selected = false,
                        onClick = {
                            navigator.onNavigate(
                                com.github.zly2006.zhihu.navigation
                                    .Topic(topic.id, topic.name),
                            )
                        },
                    )
                }
            }
        }
        ContentEndMarker()
        Spacer(modifier = Modifier.height(readingPlayerOverlayPadding))
    }
}

@Composable
private fun PinPollCard(
    poll: DataHolder.Pin.Poll,
    votingOptionId: String?,
    errorMessage: String?,
    onPollVote: (pollId: String, optionId: String) -> Unit,
) {
    val acceptsVote = poll.acceptsVote()
    val showsResult = poll.isVoted || !acceptsVote
    val pollVoterCount = poll.memberCount.takeIf { it > 0 } ?: poll.votingCount
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PIN_SCREEN_POLL_CARD_TAG),
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = poll.title.ifBlank { "想法投票" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = poll.statusText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            poll.options.forEach { option ->
                if (showsResult) {
                    PinPollResultRow(
                        option = option,
                        totalVoterCount = pollVoterCount,
                    )
                } else {
                    AppOutlinedButton(
                        onClick = { onPollVote(poll.id, option.id) },
                        enabled = votingOptionId == null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(pinScreenPollOptionTag(option.id)),
                    ) {
                        Text(
                            text = option.title,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (votingOptionId == option.id) {
                            Text("提交中...")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (errorMessage != null) {
                Text(
                    text = "投票失败: $errorMessage",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun PinPollResultRow(
    option: DataHolder.Pin.PollOption,
    totalVoterCount: Int,
) {
    val voteFraction = if (totalVoterCount > 0) {
        (option.votingCount.toFloat() / totalVoterCount).coerceIn(0f, 1f)
    } else {
        0f
    }
    val rowShape = RoundedCornerShape(12.dp)
    val indicatorColor = if (option.isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(pinScreenPollOptionTag(option.id)),
        shape = rowShape,
        color = if (option.isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    if (voteFraction > 0f) {
                        drawRoundRect(
                            color = indicatorColor,
                            size = Size(
                                width = size.width * voteFraction,
                                height = size.height,
                            ),
                            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                        )
                    }
                }.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = option.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (option.isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f),
            )
            if (option.isSelected) {
                Text(
                    text = "已选",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = "${option.votingCount} 票",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun linkCardTypeLabel(dataContentType: String): String = when (dataContentType.lowercase()) {
    "answer" -> "回答"
    "article" -> "文章"
    "question" -> "问题"
    "pin" -> "想法"
    "people" -> "用户"
    "video", "zvideo" -> "视频"
    else -> dataContentType
}
