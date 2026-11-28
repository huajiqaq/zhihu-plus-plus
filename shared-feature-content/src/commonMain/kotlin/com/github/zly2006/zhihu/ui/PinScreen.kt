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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.i18n.ContentStrings
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.reading.ReadingPlaybackStatus
import com.github.zly2006.zhihu.reading.hasReadableFields
import com.github.zly2006.zhihu.reading.loadReadingPlaybackSpeed
import com.github.zly2006.zhihu.reading.loadReadingPreferences
import com.github.zly2006.zhihu.reading.rememberReadingPlayerController
import com.github.zly2006.zhihu.reading.toReadingQueueItem
import com.github.zly2006.zhihu.ui.components.AppPullToRefreshBox
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.getShareText
import com.github.zly2006.zhihu.ui.components.handleShareAction
import com.github.zly2006.zhihu.ui.components.rememberAppPageTitleBarScrollState
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.ui.components.rememberShareActionExecutor
import com.github.zly2006.zhihu.util.raiseForStatus
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import com.github.zly2006.zhihu.viewmodel.deleteSigned
import com.github.zly2006.zhihu.viewmodel.loadVotersPage
import com.github.zly2006.zhihu.viewmodel.nextUrlOrNull
import com.github.zly2006.zhihu.viewmodel.postSigned
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.replaceOrAppendUniqueVoters
import io.ktor.client.call.body
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonArray

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
        environment.deleteSigned(endpoint).raiseForStatus().body<JsonObject>()
    } else {
        environment.postSigned(endpoint).raiseForStatus().body<JsonObject>()
    }
    return PinLikeResult(
        isLiked = !isLiked,
        likeCount = jojo["liked_count"]?.jsonPrimitive?.intOrNull ?: -1,
    )
}

const val PIN_SCREEN_AUTHOR_TAG = "pin_screen_author"

const val PIN_SCREEN_AUTHOR_FOLLOW_TAG = "pin_screen_author_follow"

const val PIN_SCREEN_AUTHOR_FOLLOW_ERROR_TAG = "pin_screen_author_follow_error"

const val PIN_SCREEN_TIMESTAMP_TAG = "pin_screen_timestamp"

const val PIN_SCREEN_IP_INFO_TAG = "pin_screen_ip_info"

const val PIN_SCREEN_LINK_CARD_TAG = "pin_screen_link_card"

const val PIN_SCREEN_LIKE_BUTTON_TAG = "pin_screen_like_button"

const val PIN_SCREEN_COMMENT_BUTTON_TAG = "pin_screen_comment_button"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinScreen(
    pin: Pin,
) {
    val navigator = LocalNavigator.current
    val coroutineScope = rememberCoroutineScope()
    val paginationEnvironment = rememberPaginationEnvironment(allowGuestAccess = rememberZhihuAccountStore().usesBrowserSession)
    val client = paginationEnvironment.httpClient()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val settings = rememberSettingsStore()
    val readingPreferences = loadReadingPreferences(settings)
    val readingPlaybackSpeed = loadReadingPlaybackSpeed(settings)
    val readingPlayer = rememberReadingPlayerController()
    val readingPlayerState = readingPlayer?.state?.value
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

    LaunchedEffect(pin.id, screenData, client) {
        if (screenData.contentClient !== client) {
            screenData.load(paginationEnvironment, pin)
        }
    }
    DisposableEffect(screenData, client, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) screenData.cancelAuthorFollow()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            screenData.cancelAuthorFollow()
        }
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
                    operationError = error.message?.takeIf(String::isNotBlank) ?: ContentStrings.likeFailedRetry
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
                votersError = e.message?.takeIf(String::isNotBlank) ?: ContentStrings.votersLoadFailed
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
            val readingItem = pinContent?.toReadingQueueItem(pin)
            val isCurrentReadingItem = readingItem != null && readingItem.key == readingPlayerState?.currentItem?.key
            PinScreenTopBar(
                pin = pin,
                pinContent = pinContent,
                scrollState = pinTopBarScrollBehavior,
                onBack = navigator.onNavigateBack,
                readingItem = readingItem,
                isCurrentReadingItem = isCurrentReadingItem,
                readingEnabled = readingPlayer != null && readingItem?.hasReadableFields(readingPreferences) == true,
                readingStatus = readingPlayerState?.status ?: ReadingPlaybackStatus.Idle,
                isActivelyPlaying = readingPlayerState?.isActivelyPlaying == true,
                readingPlayer = readingPlayer,
                coroutineScope = coroutineScope,
                readingPreferences = readingPreferences,
                readingPlaybackSpeed = readingPlaybackSpeed,
                onShareClick = {
                    val shareText = getShareText(pin)
                    if (shareText != null) {
                        handleShareAction(pin, settings, executeShareAction) {
                            showShareDialog = true
                        }
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
        PinScreenBody(
            innerPadding = innerPadding,
            isLoading = isLoading,
            errorMessage = errorMessage,
            hasContent = pinContent != null,
            errorContent = {
                PinScreenErrorContent(
                    errorMessage = errorMessage,
                    scrollState = rememberScrollState(),
                    onRetry = { coroutineScope.launch { screenData.load(paginationEnvironment, pin) } },
                )
            },
        ) {
            val loadedPin = pinContent ?: return@PinScreenBody
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
                    canFollowAuthor = screenData.canFollowAuthor(paginationEnvironment),
                    authorFollowChanging = screenData.authorFollowChanging.value || isLoading,
                    authorFollowError = screenData.authorFollowError.value,
                    onFollowAuthor = {
                        coroutineScope.launch {
                            if (lifecycle.currentState == Lifecycle.State.RESUMED) {
                                screenData.toggleAuthorFollow(paginationEnvironment)
                            }
                        }
                    },
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
                                paginationEnvironment
                                    .postSigned("https://www.zhihu.com/api/v4/polls/$pollId") {
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
                                pollErrorMessage = e.message?.takeIf(String::isNotBlank) ?: ContentStrings.voteFailed
                            } finally {
                                pollVotingOptionId = null
                            }
                        }
                    },
                )
            }

            PinScreenOverlays(
                pin = pin,
                showComments = showComments,
                isZhPlusAuthorContent = pinContent?.author?.id == DataHolder.ZH_PLUS_AUTHOR_USER_ID,
                onCommentsDismiss = { showComments = false },
                showShareDialog = showShareDialog,
                onShareDismiss = { showShareDialog = false },
                showVoters = showVoters,
                likeCount = likeCount,
                voters = voters,
                votersLoading = votersLoading,
                votersError = votersError,
                canLoadMoreVoters = votersNextUrl != null,
                onVotersDismiss = { showVoters = false },
                onLoadMoreVoters = { loadMoreVoters() },
                onRetryVoters = { loadMoreVoters(reset = voters.isEmpty()) },
                onNavigateVoter = { person ->
                    showVoters = false
                    navigator.onNavigate(person)
                },
            )
        }
    }
}
