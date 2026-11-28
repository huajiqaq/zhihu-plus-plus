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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fleeksoft.ksoup.Ksoup
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.navDestination
import com.github.zly2006.zhihu.data.target
import com.github.zly2006.zhihu.i18n.ContentStrings
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.Topic
import com.github.zly2006.zhihu.navigation.WriteAnswer
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.platform.rememberWebViewUrlOpener
import com.github.zly2006.zhihu.reading.RegisterReadingQueueSource
import com.github.zly2006.zhihu.ui.components.AppIcon
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.AppTopBar
import com.github.zly2006.zhihu.ui.components.CommentScreenComponent
import com.github.zly2006.zhihu.ui.components.FeedCard
import com.github.zly2006.zhihu.ui.components.FeedPullToRefresh
import com.github.zly2006.zhihu.ui.components.PaginatedList
import com.github.zly2006.zhihu.ui.components.ShareDialog
import com.github.zly2006.zhihu.ui.components.getShareText
import com.github.zly2006.zhihu.ui.components.handleShareAction
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.ui.components.rememberShareActionExecutor
import com.github.zly2006.zhihu.viewmodel.feed.QuestionFeedViewModel
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.sharedArticleAnswerSwitchState
import kotlinx.coroutines.launch

const val QUESTION_SCREEN_BACK_BUTTON_TAG = "question_screen_back_button"

const val QUESTION_SCREEN_LIST_TAG = "question_screen_list"
const val QUESTION_TITLE_TAG = "question_title"
const val QUESTION_DETAIL_TOGGLE_TAG = "question_detail_toggle"
const val QUESTION_DETAIL_CONTENT_TAG = "question_detail_content"
const val QUESTION_DETAIL_PREVIEW_TAG = "question_detail_preview"
const val QUESTION_SORT_DEFAULT_TAG = "question_sort_default"
const val QUESTION_SORT_UPDATED_TAG = "question_sort_updated"
const val QUESTION_FOLLOW_BUTTON_TAG = "question_follow_button"
const val QUESTION_VIEW_LOG_BUTTON_TAG = "question_view_log_button"
const val QUESTION_SHARE_BUTTON_TAG = "question_share_button"
const val QUESTION_WRITE_ANSWER_BUTTON_TAG = "question_write_answer_button"
const val QUESTION_COMMENTS_BUTTON_TAG = "question_comments_button"
const val QUESTION_STATS_TAG = "question_stats"
const val QUESTION_DETAIL_LOADING_TAG = "question_detail_loading"
const val QUESTION_DETAIL_ERROR_TAG = "question_detail_error"
const val QUESTION_DETAIL_RETRY_TAG = "question_detail_retry"
const val QUESTION_AUTHOR_TAG = "question_author"
const val QUESTION_SORT_CONTROL_TAG = "question_sort_control"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionScreen(
    question: Question,
) {
    val readingPlayerOverlayPadding = LocalReadingPlayerOverlayPadding.current
    val settings = rememberSettingsStore()
    val executeShareAction = rememberShareActionExecutor()
    val openWebUrl = rememberWebViewUrlOpener()
    val navigator = LocalNavigator.current
    val viewModel: QuestionFeedViewModel = viewModel(key = "question_${question.questionId}") {
        QuestionFeedViewModel(question.questionId)
    }
    val answerReadingQueueSourceId = "question:${question.questionId}:answers:${viewModel.sortOrder}"
    RegisterReadingQueueSource(
        sourceId = answerReadingQueueSourceId,
        items = viewModel.displayItems,
    )
    val paginationEnvironment = rememberPaginationEnvironment(allowGuestAccess = false)
    val answerSwitchState = sharedArticleAnswerSwitchState
    val listState = rememberLazyListState()
    var followerCount by remember(viewModel.questionDetail) { mutableIntStateOf(viewModel.questionDetail?.followerCount ?: 0) }
    var isFollowing by remember(viewModel.questionDetail) {
        mutableStateOf(viewModel.questionDetail?.relationship?.isFollowing ?: false)
    }
    var showComments by rememberSaveable(question.questionId) { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    val userMessages = rememberUserMessageSink()
    var isQuestionDetailExpanded by rememberSaveable(question.questionId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val questionContentPlainText = remember(viewModel.questionDetail?.detail.orEmpty()) {
        Ksoup.parse(viewModel.questionDetail?.detail.orEmpty()).text().trim()
    }
    val shareText = getShareText(question, viewModel.questionDetail?.title ?: question.title)
    val topBarTitleThresholdPx = with(LocalDensity.current) { 160.dp.roundToPx() }
    val showTopBarTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset >= topBarTitleThresholdPx
        }
    }
    val pageTurnTarget = rememberPageTurnTarget(
        listState = listState,
        enabled = !showComments && !showShareDialog,
    )

    LaunchedEffect(question.questionId, viewModel) {
        if (viewModel.displayItems.isEmpty() && !viewModel.isEnd && viewModel.errorMessage == null) {
            viewModel.loadMore(paginationEnvironment)
        }
        if (viewModel.questionDetail == null && viewModel.questionDetailError == null) {
            viewModel.loadQuestionDetail(paginationEnvironment, question)
        }
    }

    LaunchedEffect(viewModel, viewModel.pageGeneration, viewModel.completedPageCount, viewModel.isLoading) {
        if (viewModel.completedPageCount > 0 &&
            viewModel.displayItems.isEmpty() &&
            viewModel.hasNextPage &&
            !viewModel.isLoading &&
            viewModel.errorMessage == null
        ) {
            viewModel.loadMore(paginationEnvironment)
        }
    }

    AppScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            QuestionTopBar(
                title = viewModel.questionDetail?.title ?: question.title,
                showTitle = showTopBarTitle,
                onNavigateBack = navigator.onNavigateBack,
                onOpenLog = {
                    try {
                        openWebUrl("https://www.zhihu.com/question/${question.questionId}/log")
                    } catch (e: Exception) {
                        userMessages.showShortMessage(ContentStrings.openLogError(e.message))
                    }
                },
                onShare = {
                    if (shareText != null) {
                        handleShareAction(question, settings, executeShareAction) { showShareDialog = true }
                    }
                },
                canShare = shareText != null,
            )
        },
    ) { innerPadding ->
        FeedPullToRefresh(
            viewModel,
            padding = PaddingValues(top = innerPadding.calculateTopPadding()),
        ) {
            PaginatedList(
                items = viewModel.displayItems,
                onLoadMore = { viewModel.loadMore(paginationEnvironment) },
                isEnd = { viewModel.isEnd },
                isLoading = { viewModel.isLoading },
                loadError = { viewModel.errorMessage },
                onRetry = { viewModel.retry(paginationEnvironment) },
                key = { it.stableKey },
                listState = listState,
                modifier = Modifier
                    .padding(innerPadding)
                    .pageTurnViewportWithGuide(pageTurnTarget)
                    .testTag(QUESTION_SCREEN_LIST_TAG),
                contentPadding = PaddingValues(bottom = readingPlayerOverlayPadding),
                topContent = {
                    item {
                        QuestionOverviewContent(
                            title = viewModel.questionDetail?.title ?: question.title,
                            questionDetail = viewModel.questionDetail,
                            followerCount = followerCount,
                            isLoading = viewModel.isQuestionDetailLoading,
                            errorMessage = viewModel.questionDetailError,
                            isFollowing = isFollowing,
                            questionId = question.questionId,
                            questionContentPlainText = questionContentPlainText,
                            isExpanded = isQuestionDetailExpanded,
                            currentSort = viewModel.sortOrder,
                            items = viewModel.displayItems,
                            onShowComments = { showComments = true },
                            onRetry = { scope.launch { viewModel.loadQuestionDetail(paginationEnvironment, question) } },
                            onNavigate = navigator.onNavigate,
                            onFollowClick = {
                                scope.launch {
                                    try {
                                        val nextFollowing = !isFollowing
                                        viewModel.followQuestion(paginationEnvironment, nextFollowing)
                                        isFollowing = nextFollowing
                                        followerCount = (followerCount + if (isFollowing) 1 else -1).coerceAtLeast(0)
                                        userMessages.showShortMessage(if (isFollowing) ContentStrings.questionFollowed else ContentStrings.questionUnfollowed)
                                    } catch (error: kotlinx.coroutines.CancellationException) {
                                        throw error
                                    } catch (error: Exception) {
                                        userMessages.showShortMessage(ContentStrings.followError(error.message))
                                    }
                                }
                            },
                            onWriteAnswerClick = {
                                navigator.onNavigate(
                                    WriteAnswer(
                                        questionId = question.questionId,
                                        questionTitle = viewModel.questionDetail?.title ?: question.title,
                                        questionDetail = viewModel.questionDetail?.detail.orEmpty(),
                                    ),
                                )
                            },
                            onTopicClick = { topic -> navigator.onNavigate(Topic(topic.id, topic.name)) },
                            onToggleExpanded = { isQuestionDetailExpanded = !isQuestionDetailExpanded },
                            onSortChange = { sortOrder ->
                                if (sortOrder != viewModel.sortOrder) {
                                    viewModel.updateSortOrder(sortOrder)
                                    viewModel.refresh(paginationEnvironment)
                                }
                            },
                            onSelectAnswer = { item ->
                                answerSwitchState.pendingNavigator =
                                    viewModel.createAnswerNavigatorFor(item, paginationEnvironment)
                                item.navDestination?.let(navigator.onNavigate)
                            },
                        )
                    }
                },
            ) { item ->
                FeedCard(
                    item = item.localizedQuestionAnswer(),
                    readingQueueSourceId = answerReadingQueueSourceId,
                    thumbnailUrl = (item.feed?.target as? Feed.AnswerTarget)
                        ?.thumbnailInfo
                        ?.thumbnails
                        ?.filterIsInstance<DataHolder.ThumbnailRich>()
                        ?.firstOrNull()
                        ?.url,
                    modifier = Modifier.testTag("question_feed_item_${item.stableKey}"),
                ) { _, destination ->
                    answerSwitchState.pendingNavigator = viewModel.createAnswerNavigatorFor(item, paginationEnvironment)
                    destination?.let(navigator.onNavigate)
                }
            }
        }
    }
    CommentScreenComponent(
        showComments = showComments,
        onDismiss = { showComments = false },
        content = question,
    )

    if (shareText != null) {
        ShareDialog(
            content = question,
            shareText = shareText,
            showDialog = showShareDialog,
            onDismissRequest = { showShareDialog = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestionTopBar(
    title: String,
    showTitle: Boolean,
    onNavigateBack: () -> Unit,
    onOpenLog: () -> Unit,
    onShare: () -> Unit,
    canShare: Boolean,
) {
    AppTopBar(
        title = {
            AnimatedContent(
                targetState = showTitle,
                transitionSpec = {
                    (fadeIn() + slideInVertically(initialOffsetY = { it / 2 })) togetherWith
                        (fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 }))
                },
                label = "question_top_bar_title",
            ) { shouldShowTitle ->
                Text(
                    text = if (shouldShowTitle) title else ContentStrings.question,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        onBack = onNavigateBack,
        backTag = QUESTION_SCREEN_BACK_BUTTON_TAG,
        actions = {
            AppIconButton(onClick = onOpenLog, modifier = Modifier.testTag(QUESTION_VIEW_LOG_BUTTON_TAG)) {
                AppIcon(Icons.Filled.History, contentDescription = ContentStrings.questionLog)
            }
            AppIconButton(
                onClick = onShare,
                enabled = canShare,
                modifier = Modifier.testTag(QUESTION_SHARE_BUTTON_TAG),
            ) {
                AppIcon(Icons.Filled.Share, contentDescription = ContentStrings.share)
            }
        },
    )
}
