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

package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import com.github.zly2006.zhihu.viewmodel.feed.BaseFeedViewModel
import com.github.zly2006.zhihu.i18n.WidgetsStrings
import com.github.zly2006.zhihu.nlp.KeywordWithWeight
import com.github.zly2006.zhihu.platform.rememberImageSaver
import com.github.zly2006.zhihu.platform.rememberImageSharer
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.util.AppLog
import com.github.zly2006.zhihu.viewmodel.filter.BlockedKeyword
import com.github.zly2006.zhihu.viewmodel.filter.KeywordScope
import com.github.zly2006.zhihu.viewmodel.filter.KeywordType
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

expect suspend fun extractFeedKeywords(
    title: String,
    excerpt: String?,
): List<KeywordWithWeight>

expect val feedKeywordExtractionAvailable: Boolean

@Composable
fun FeedAuthorBlockConfirmDialog(
    request: FeedAuthorBlockRequest?,
    viewModel: BaseFeedViewModel,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val userMessages = rememberUserMessageSink()
    val environment = rememberPaginationEnvironment(allowGuestAccess = false)
    var questionAuthorStats by remember(request) { mutableStateOf<QuestionAuthorActivityStats?>(null) }
    var isQuestionAuthorStatsLoading by remember(request) {
        mutableStateOf(request?.type == FeedAuthorBlockType.QUESTION_AUTHOR)
    }

    LaunchedEffect(request) {
        if (request?.type != FeedAuthorBlockType.QUESTION_AUTHOR) return@LaunchedEffect

        isQuestionAuthorStatsLoading = true
        questionAuthorStats = try {
            environment
                .fetchJson(
                    "https://api.zhihu.com/people/${request.userId}",
                    "answer_count,question_count",
                )?.let { profile ->
                    val questionCount = profile["question_count"]?.jsonPrimitive?.intOrNull
                    val answerCount = profile["answer_count"]?.jsonPrimitive?.intOrNull
                    if (questionCount != null && answerCount != null) {
                        QuestionAuthorActivityStats(questionCount, answerCount)
                    } else {
                        null
                    }
                }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLog.e("FeedBlocking", "Failed to load question author activity stats", error)
            null
        } finally {
            isQuestionAuthorStatsLoading = false
        }
    }

    FeedAuthorBlockConfirmDialogContent(
        request = request,
        displayItems = viewModel.displayItems,
        questionAuthorStats = questionAuthorStats,
        isQuestionAuthorStatsLoading = isQuestionAuthorStatsLoading,
        onDismiss = onDismiss,
        onConfirmBlock = { author ->
            coroutineScope.launch {
                try {
                    when (request?.type) {
                        FeedAuthorBlockType.CONTENT_AUTHOR -> viewModel.blockUser(author.id, author.name, author.urlToken, author.avatarUrl)
                        FeedAuthorBlockType.QUESTION_AUTHOR -> viewModel.blockQuestionAuthor(author.id, author.name, author.urlToken, author.avatarUrl)
                        null -> return@launch
                    }
                    onConfirm()
                    userMessages.showShortMessage(
                        if (request.type == FeedAuthorBlockType.QUESTION_AUTHOR) {
                            WidgetsStrings.blockedQuestionAuthor(author.name)
                        } else {
                            WidgetsStrings.blockedUser(author.name)
                        },
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.e("FeedBlocking", "Failed to block feed author", e)
                    userMessages.showShortMessage(WidgetsStrings.blockFailed(e.message ?: WidgetsStrings.unknownError))
                }
            }
        },
    )
}

@Composable
fun BlockByKeywordsDialog(
    showDialog: Boolean,
    feedTitle: String,
    feedExcerpt: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val userMessages = rememberUserMessageSink()
    val coroutineScope = rememberCoroutineScope()
    val database = remember { getContentFilterDatabase() }

    var keywordScope by remember(showDialog, feedTitle, feedExcerpt) { mutableStateOf(KeywordScope.ALL) }
    var extractedKeywords by remember { mutableStateOf<List<String>>(emptyList()) }
    var keywordInfoList by remember { mutableStateOf<List<KeywordWithWeight>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isAdding by remember { mutableStateOf(false) }

    LaunchedEffect(showDialog, feedTitle, feedExcerpt) {
        if (showDialog) {
            isLoading = true
            try {
                val keywordsWithWeight = extractFeedKeywords(feedTitle, feedExcerpt)
                keywordInfoList = keywordsWithWeight
                extractedKeywords = keywordsWithWeight.take(8).map { it.keyword }
            } catch (e: Exception) {
                AppLog.e("FeedBlocking", "Failed to extract block keywords", e)
                userMessages.showShortMessage(WidgetsStrings.keywordExtractionFailed(e.message ?: WidgetsStrings.unknownError))
            } finally {
                isLoading = false
            }
        }
    }

    BlockByKeywordsDialogContent(
        showDialog = showDialog,
        feedTitle = feedTitle,
        feedExcerpt = feedExcerpt,
        extractedKeywords = extractedKeywords,
        keywordInfoList = keywordInfoList,
        isLoading = isLoading,
        isAdding = isAdding,
        onDismiss = onDismiss,
        scope = keywordScope,
        onScopeChange = { keywordScope = it },
        onConfirmPhrase = { phrase ->
            isAdding = true
            coroutineScope.launch {
                try {
                    database.blockedKeywordDao().insertKeyword(
                        BlockedKeyword(
                            keyword = phrase.trim(),
                            keywordType = KeywordType.NLP_SEMANTIC.name,
                            scope = keywordScope.name,
                        ),
                    )
                    userMessages.showShortMessage(WidgetsStrings.blockingPhraseAdded(phrase))
                    onConfirm()
                } catch (e: Exception) {
                    AppLog.e("FeedBlocking", "Failed to add NLP block phrase", e)
                    userMessages.showShortMessage(WidgetsStrings.addPhraseFailed(e.message ?: WidgetsStrings.unknownError))
                } finally {
                    isAdding = false
                }
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OpenImagePreviewContent(
    urls: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onOpenInBrowser: (String) -> Unit,
    showControls: Boolean = false,
    imageContent: @Composable (
        url: String,
        onClick: () -> Unit,
        onLongClick: (Offset) -> Unit,
        onPageSwipeEnabledChange: (Boolean) -> Unit,
    ) -> Unit,
) {
    val onSaveImage = rememberImageSaver()
    val onShareImage = rememberImageSharer()
    var showMenu by remember { mutableStateOf(false) }
    var windowPressPoint by remember { mutableStateOf<Offset?>(null) }
    var windowImageBounds by remember { mutableStateOf<Rect?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val imageUrls = remember(urls) {
        urls
            .filter { it.isNotBlank() && !it.startsWith("data") }
            .distinct()
            .ifEmpty { listOf("") }
    }
    val initialPage = initialIndex.coerceIn(0, imageUrls.lastIndex)
    val pagerState = rememberPagerState(initialPage = initialPage) { imageUrls.size }
    val pageSwipeEnabled = remember(imageUrls) {
        mutableStateMapOf<String, Boolean>().apply {
            imageUrls.forEach { put(it, true) }
        }
    }
    val currentPageSwipeEnabled = pageSwipeEnabled[imageUrls[pagerState.currentPage]] ?: true
    val verticalSwipeModifier = if (imageUrls.size > 1 && currentPageSwipeEnabled) {
        Modifier.pointerInput(imageUrls.size) {
            val threshold = 96f
            var totalDrag = 0f
            detectVerticalDragGestures(
                onVerticalDrag = { _, dragAmount ->
                    totalDrag += dragAmount
                },
                onDragEnd = {
                    val targetPage = when {
                        totalDrag < -threshold -> pagerState.currentPage + 1
                        totalDrag > threshold -> pagerState.currentPage - 1
                        else -> null
                    }?.coerceIn(0, imageUrls.lastIndex)

                    if (targetPage != null && targetPage != pagerState.currentPage) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(targetPage)
                        }
                    }
                    totalDrag = 0f
                },
                onDragCancel = {
                    totalDrag = 0f
                },
            )
        }
    } else {
        Modifier
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(showControls) {
        if (showControls) focusRequester.requestFocus()
    }
    fun moveTo(page: Int) {
        coroutineScope.launch { pagerState.animateScrollToPage(page.coerceIn(imageUrls.indices)) }
    }
    val keyboardControls = if (showControls) {
        Modifier.focusRequester(focusRequester).onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || showMenu) {
                false
            } else {
                when (event.key) {
                    Key.Escape -> onDismiss()
                    Key.DirectionLeft, Key.PageUp -> moveTo(pagerState.currentPage - 1)
                    Key.DirectionRight, Key.PageDown -> moveTo(pagerState.currentPage + 1)
                    Key.MoveHome -> moveTo(0)
                    Key.MoveEnd -> moveTo(imageUrls.lastIndex)
                    else -> return@onKeyEvent false
                }
                true
            }
        }.focusable()
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(keyboardControls),
    ) {
        // 禁用图片查看器自带的震动反馈，保持长按菜单手感稳定。
        CompositionLocalProvider(LocalHapticFeedback provides NoopHapticFeedback) {
            HorizontalPager(
                state = pagerState,
                key = { page -> "$page:${imageUrls[page]}" },
                userScrollEnabled = imageUrls.size > 1 && currentPageSwipeEnabled,
                modifier = Modifier
                    .fillMaxSize()
                    .then(verticalSwipeModifier),
            ) { page ->
                var imageCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                Box(Modifier.fillMaxSize().onGloballyPositioned { imageCoordinates = it }) {
                    imageContent(
                        imageUrls[page],
                        onDismiss,
                        { offset ->
                            val coordinates = imageCoordinates?.takeIf { it.isAttached }
                            windowImageBounds = coordinates?.boundsInWindow()
                            windowPressPoint = coordinates?.localToWindow(offset)
                            showMenu = true
                        },
                        { enabled ->
                            pageSwipeEnabled[imageUrls[page]] = enabled
                        },
                    )
                }
            }
        }

        if (showControls) {
            AppIconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).background(Color.Black.copy(alpha = 0.6f)),
            ) {
                AppIcon(Icons.Default.Close, WidgetsStrings.close, tint = Color.White)
            }
            AppIconButton(
                onClick = {
                    windowPressPoint = null
                    windowImageBounds = null
                    showMenu = true
                },
                modifier = Modifier.align(Alignment.TopEnd).background(Color.Black.copy(alpha = 0.6f)),
            ) {
                AppIcon(Icons.Default.MoreVert, WidgetsStrings.moreOptions, tint = Color.White)
            }
            if (imageUrls.size > 1) {
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).background(Color.Black.copy(alpha = 0.6f)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIconButton(onClick = { moveTo(pagerState.currentPage - 1) }, enabled = pagerState.currentPage > 0) {
                        AppIcon(Icons.AutoMirrored.Filled.ArrowBack, WidgetsStrings.previousImage, tint = Color.White)
                    }
                    Text(
                        text = "${pagerState.currentPage + 1} / ${imageUrls.size}",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = AppDimens.Space8),
                    )
                    AppIconButton(onClick = { moveTo(pagerState.currentPage + 1) }, enabled = pagerState.currentPage < imageUrls.lastIndex) {
                        AppIcon(Icons.AutoMirrored.Filled.ArrowForward, WidgetsStrings.nextImage, tint = Color.White)
                    }
                }
            }
        }

        AppImageContextMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            windowImageBounds = windowImageBounds,
            windowPressPoint = windowPressPoint,
        ) {
            AppDropdownMenuItem(
                text = { Text(WidgetsStrings.saveImage) },
                onClick = {
                    showMenu = false
                    onSaveImage(imageUrls[pagerState.currentPage])
                },
            )
            AppDropdownMenuItem(
                text = { Text(WidgetsStrings.shareImage) },
                onClick = {
                    showMenu = false
                    onShareImage(imageUrls[pagerState.currentPage])
                },
            )
            AppDropdownMenuItem(
                text = { Text(WidgetsStrings.openInBrowser) },
                onClick = {
                    showMenu = false
                    onOpenInBrowser(imageUrls[pagerState.currentPage])
                },
            )
        }
    }
}

private object NoopHapticFeedback : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        // 无操作。
    }
}
