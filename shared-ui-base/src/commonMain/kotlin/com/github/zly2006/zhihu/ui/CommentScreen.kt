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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zly2006.zhihu.account.CommentSender
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.CommentSortOrder
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.editor.ZhihuImageUploadSource
import com.github.zly2006.zhihu.editor.isImagePickerSupported
import com.github.zly2006.zhihu.editor.rememberImagePickerLauncher
import com.github.zly2006.zhihu.editor.uploadZhihuImage
import com.github.zly2006.zhihu.i18n.CommentsStrings
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.CommentHolder
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.SegmentCommentHolder
import com.github.zly2006.zhihu.platform.PlatformBackHandler
import com.github.zly2006.zhihu.platform.platformName
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.reading.ReadingCommentOrder
import com.github.zly2006.zhihu.reading.loadReadingPreferences
import com.github.zly2006.zhihu.reading.saveReadingPreferences
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.DISABLE_BOTTOM_SHEET_ROUNDED_CORNERS_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.components.PageTurnFab
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.viewmodel.CommentItem
import com.github.zly2006.zhihu.viewmodel.comment.BaseCommentViewModel
import com.github.zly2006.zhihu.viewmodel.comment.ChildCommentViewModel
import com.github.zly2006.zhihu.viewmodel.comment.CommentForwardList
import com.github.zly2006.zhihu.viewmodel.comment.CommentImageAttachment
import com.github.zly2006.zhihu.viewmodel.comment.CommentLinkInfo
import com.github.zly2006.zhihu.viewmodel.comment.CommentMention
import com.github.zly2006.zhihu.viewmodel.comment.CommentSticker
import com.github.zly2006.zhihu.viewmodel.comment.MentionCandidate
import com.github.zly2006.zhihu.viewmodel.comment.RootCommentViewModel
import com.github.zly2006.zhihu.viewmodel.comment.fetchMentionCandidates
import com.github.zly2006.zhihu.viewmodel.comment.loadRecentCommentEmojis
import com.github.zly2006.zhihu.viewmodel.comment.parseCommentLinks
import com.github.zly2006.zhihu.viewmodel.comment.recordRecentCommentEmoji
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 页面可嵌入评论弹层，不能假设自己拥有完整 NavHost。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentScreen(
    content: () -> NavDestination,
    initialCommentId: String? = null,
    activeCommentItem: CommentItem? = null,
    onChildCommentClick: (CommentItem) -> Unit,
    commentInput: String,
    onCommentInputChange: (String) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    commentSegments: Map<String, com.github.zly2006.zhihu.util.SegmentHighlightSpan> = emptyMap(),
    tabState: MutableState<CommentPanelTab> = rememberSaveable(content().commentThreadKey(), initialCommentId) { mutableStateOf(CommentPanelTab.Normal) },
    sentenceListState: LazyListState = rememberLazyListState(),
    forwardListState: LazyListState = rememberLazyListState(),
    initialComment: DataHolder.Comment? = null,
    onInitialChildCommentResolved: (CommentItem, DataHolder.Comment) -> Unit = { _, _ -> },
    pageTurnEnabled: Boolean = false,
    showPageTurnFab: Boolean = false,
    rootHeader: (@Composable () -> Unit)? = null,
    emojiPickerState: MutableState<Boolean> = remember { mutableStateOf(false) },
    /** 系统选图或权限窗口启动前通知 owner，避免窗口失焦卸载注册与上传协程。 */
    onPickerLaunched: () -> Unit = {},
    onDismiss: (() -> Unit)? = null,
) {
    val paginationEnvironment = rememberPaginationEnvironment(allowGuestAccess = false)
    val commentAccount = rememberZhihuAccountStore()
    val commentClient = commentAccount.client
    val readingSettings = rememberSettingsStore()
    val squarePopups by rememberSetting(readingSettings, DISABLE_BOTTOM_SHEET_ROUNDED_CORNERS_PREFERENCE_KEY, false)
    val initialReadingCommentOrder = remember(readingSettings) {
        loadReadingPreferences(readingSettings).commentOrder
    }
    val resolvedContent = content()
    var selectedTab by tabState
    val currentCommentSegments by rememberUpdatedState(commentSegments)
    val isSendingState = remember { mutableStateOf(false) }
    var isSending by isSendingState
    var replyToComment by remember { mutableStateOf<CommentItem?>(null) }
    var showEmojiPicker by emojiPickerState
    var commentPendingDeletion by remember(resolvedContent, commentClient) { mutableStateOf<CommentItem?>(null) }
    var isDeletingComment by remember(resolvedContent, commentClient) { mutableStateOf(false) }
    var deleteCommentError by remember(resolvedContent, commentClient) { mutableStateOf<(() -> String)?>(null) }
    var isCommentInputFocused by remember { mutableStateOf(false) }
    val initialTargetId = initialCommentId ?: initialComment?.id
    val viewModelKey = resolvedContent.commentThreadKey() + initialTargetId?.let { ":initial:$it" }.orEmpty()
    val mobileComposer = platformName == "Android" || platformName == "iOS"
    var composerOpen by remember(viewModelKey) { mutableStateOf(false) }
    val commentInputFocusRequester = remember { FocusRequester() }
    val commentInputScrollState = rememberScrollState()
    val commentFieldState = remember(resolvedContent) {
        TextFieldState(commentInput, TextRange(commentInput.length))
    }
    val commentEmojis = rememberCommentEmojis()
    val emojiInlineContent = rememberCommentEmojiInlineContent(
        remember(commentEmojis) { commentEmojis.mapTo(mutableSetOf(), CommentEmoji::inlineKey) },
    )

    LaunchedEffect(commentInput) {
        if (commentInput != commentFieldState.text.toString()) {
            commentFieldState.setTextAndPlaceCursorAtEnd(commentInput)
        }
    }

    LaunchedEffect(commentFieldState) {
        snapshotFlow { commentFieldState.text.toString() }.collect { onCommentInputChange(it) }
    }

    if (!mobileComposer) {
        PlatformBackHandler(enabled = showEmojiPicker) { showEmojiPicker = false }
    }

    val normalComments: RootCommentViewModel? = if (resolvedContent is CommentHolder) {
        null
    } else {
        viewModel(key = viewModelKey) {
            RootCommentViewModel(resolvedContent, initialCommentId, commentSegments = { currentCommentSegments }).apply {
                if (supportsSorting) {
                    sortOrder = when (initialReadingCommentOrder) {
                        ReadingCommentOrder.Score -> CommentSortOrder.SCORE
                        ReadingCommentOrder.Time -> CommentSortOrder.TIME
                    }
                }
            }
        }
    }
    val hasOfficialSentenceComments = (normalComments?.sentenceCommentCount ?: 0) > 0
    val hasSentenceTabs = resolvedContent is Article && (hasOfficialSentenceComments || normalComments?.hasKnownSentenceComments == true)
    val forwards = remember(resolvedContent, commentClient) { CommentForwardList.forContent(resolvedContent, commentClient) }
    val forwardScope = key(forwards) { rememberCoroutineScope() }
    var tabChecked by remember(forwards) { mutableStateOf(false) }
    LaunchedEffect(forwards) {
        forwards?.loadTab(refresh = true)
        if (forwards?.tab?.isShowTab == false && selectedTab == CommentPanelTab.Forward) selectedTab = CommentPanelTab.Normal
        tabChecked = true
    }
    val hasForwards = tabChecked &&
        forwards?.tab?.isShowTab == true &&
        (normalComments?.rootPageReady == true || selectedTab == CommentPanelTab.Forward)
    val activeTab = when (selectedTab) {
        CommentPanelTab.Normal -> CommentPanelTab.Normal
        CommentPanelTab.Sentence -> if (hasSentenceTabs) selectedTab else CommentPanelTab.Normal
        CommentPanelTab.Forward -> if (hasForwards) selectedTab else CommentPanelTab.Normal
    }
    val sentenceMode = activeTab == CommentPanelTab.Sentence
    val forwardMode = activeTab == CommentPanelTab.Forward
    LaunchedEffect(forwards, forwardMode, forwards?.tab) {
        if (forwardMode) forwards?.loadFirstPage()
    }
    val activeListState = if (sentenceMode) sentenceListState else listState
    val viewModel: BaseCommentViewModel = when (resolvedContent) {
        is CommentHolder -> remember(viewModelKey) {
            ChildCommentViewModel(resolvedContent, initialComment)
        }
        else -> if (sentenceMode) {
            viewModel(key = "$viewModelKey:sentences") {
                RootCommentViewModel(resolvedContent, sentenceOnly = true, commentSegments = { currentCommentSegments })
            }
        } else {
            checkNotNull(normalComments)
        }
    }
    SideEffect {
        normalComments?.commentSegments = { currentCommentSegments }
        (viewModel as? RootCommentViewModel)?.commentSegments = { currentCommentSegments }
    }
    LaunchedEffect(hasSentenceTabs) {
        if (!hasSentenceTabs && selectedTab == CommentPanelTab.Sentence) selectedTab = CommentPanelTab.Normal
    }
    val pageTurnActive = pageTurnEnabled &&
        !composerOpen &&
        !showEmojiPicker &&
        !isCommentInputFocused &&
        commentPendingDeletion == null
    val pageTurnTarget = rememberPageTurnTarget(
        listState = if (forwardMode) forwardListState else activeListState,
        enabled = pageTurnActive,
    )
    var restoredListPosition by remember(viewModel, activeListState) {
        mutableStateOf(activeListState.firstVisibleItemIndex to activeListState.firstVisibleItemScrollOffset)
    }
    var restoredListPositionApplied by remember(viewModel, activeListState) { mutableStateOf(false) }
    LaunchedEffect(viewModel, activeListState, forwardMode, viewModel.isLoading, viewModel.allData.size, restoredListPosition, restoredListPositionApplied) {
        if (!forwardMode && !restoredListPositionApplied && !viewModel.isLoading && viewModel.allData.isNotEmpty()) {
            /** 数据到达后恢复位置，避免按旧评论 ID 锚回。 */
            activeListState.requestScrollToItem(
                restoredListPosition.first.coerceAtMost(viewModel.allData.size),
                restoredListPosition.second,
            )
            restoredListPositionApplied = true
        }
    }
    val rootContent = when (resolvedContent) {
        is CommentHolder -> resolvedContent.article
        else -> resolvedContent
    }
    val sentenceQuotes = commentSegments.mapValues { it.value.displayText } +
        (viewModel as? RootCommentViewModel)?.sentenceQuotes.orEmpty()
    val canReplyToCurrentThread = resolvedContent !is CommentHolder ||
        activeCommentItem?.item?.resourceType != "segment" ||
        resolvedContent.article is SegmentCommentHolder
    val composerAllowed = activeTab == CommentPanelTab.Normal && viewModel.commentClosedMessage == null && canReplyToCurrentThread
    val initialChildComment = (viewModel as? RootCommentViewModel)?.initialChildComment
    var initialChildCommentHandled by remember(viewModelKey) { mutableStateOf(false) }
    LaunchedEffect(initialChildComment?.id, viewModel.allData.size) {
        if (!initialChildCommentHandled && initialChildComment != null) {
            val rootCommentId = initialChildComment.replyRootCommentId
            val rootComment = viewModel.allData.firstOrNull { it.id == rootCommentId }
            if (rootComment != null) {
                initialChildCommentHandled = true
                onInitialChildCommentResolved(
                    viewModel.createCommentItem(rootComment, rootContent),
                    initialChildComment,
                )
            }
        }
    }
    val commentBackgroundColor = MaterialTheme.colorScheme.surfaceContainerLow
    val commentInputBarColor = MaterialTheme.colorScheme.surfaceContainer
    val actionChipColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val actionChipIconColor = MaterialTheme.colorScheme.onSurfaceVariant

    commentPendingDeletion?.let { target ->
        CommentScreenDeleteDialog(
            isDeletingComment = isDeletingComment,
            deleteCommentError = deleteCommentError?.invoke(),
            onDismissRequest = {
                if (!isDeletingComment) {
                    commentPendingDeletion = null
                    deleteCommentError = null
                }
            },
            onConfirm = confirm@{
                if (commentAccount.client !== commentClient) return@confirm
                isDeletingComment = true
                deleteCommentError = null
                viewModel.deleteComment(
                    commentData = target.item,
                    environment = paginationEnvironment,
                    ownComment = target.item.author.id == commentAccount.session.profile?.id,
                    onSuccess = {
                        isDeletingComment = false
                        commentPendingDeletion = null
                    },
                    onFailure = { message ->
                        isDeletingComment = false
                        deleteCommentError = message
                    },
                )
            },
            onCancel = {
                commentPendingDeletion = null
                deleteCommentError = null
            },
        )
    }

    val loadMore = remember(viewModel, activeListState) {
        derivedStateOf {
            val layoutInfo = activeListState.layoutInfo
            val totalItemsCount = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0

            val needsNextPage = (viewModel.hasLoaded && viewModel.allData.isEmpty()) ||
                (layoutInfo.visibleItemsInfo.isNotEmpty() && lastVisibleItemIndex >= totalItemsCount - 3)
            needsNextPage && !viewModel.isLoading && !viewModel.isEnd
        }
    }

    LaunchedEffect(viewModel, forwardMode, loadMore.value) {
        if (!forwardMode && loadMore.value && viewModel.errorMessage == null) {
            viewModel.loadMore(paginationEnvironment)
        }
    }

    LaunchedEffect(viewModel, forwardMode) {
        if (forwardMode) return@LaunchedEffect
        if (viewModel.article.commentThreadKey() != resolvedContent.commentThreadKey()) {
            error("Internal Error: Detected content mismatch")
        }
        if (viewModel.errorMessage == null && !viewModel.hasLoaded && viewModel.allData.isEmpty()) {
            viewModel.loadMore(paginationEnvironment)
        }
    }
    val userMessages = rememberUserMessageSink()
    var pendingImages by remember(resolvedContent) {
        mutableStateOf<List<CommentImageAttachment>>(emptyList())
    }
    var mentions by remember(resolvedContent) { mutableStateOf<List<CommentMention>>(emptyList()) }
    var defaultSyncToPin by rememberSetting(readingSettings, "commentSyncToPinDefault", false)
    var syncOnce by remember(resolvedContent) { mutableStateOf(false) }
    val syncToPin = syncOnce || defaultSyncToPin
    var sender by remember(commentClient, resolvedContent) { mutableStateOf<CommentSender?>(null) }

    fun canAttachMedia(): Boolean {
        if (sender == null) return true
        userMessages.showShortMessage(CommentsStrings.attachmentsRequireCurrentAccount)
        return false
    }
    var isUploadingImage by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val launchImagePicker = if (isImagePickerSupported) {
        rememberImagePickerLauncher { picked ->
            if (isUploadingImage || isSending || !canAttachMedia()) return@rememberImagePickerLauncher
            isUploadingImage = true
            coroutineScope.launch {
                try {
                    val uploaded = uploadZhihuImage(
                        paginationEnvironment,
                        picked.bytes,
                        picked.mimeType,
                        picked.fileName,
                        ZhihuImageUploadSource.Comment,
                    )
                    pendingImages = listOf(
                        CommentImageAttachment(
                            url = uploaded.url,
                            width = uploaded.rawWidth,
                            height = uploaded.rawHeight,
                            isGif = picked.mimeType.equals("image/gif", ignoreCase = true) ||
                                picked.fileName?.endsWith(".gif", ignoreCase = true) == true,
                        ),
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    userMessages.showShortMessage(CommentsStrings.imageUploadFailed(e.message.orEmpty()))
                } finally {
                    isUploadingImage = false
                }
            }
        }
    } else {
        null
    }

    var pendingStickers by remember(resolvedContent) {
        mutableStateOf<List<CommentSticker>>(emptyList())
    }

    /** 固定面板快照，避免点击表情后位置变化。 */
    val recentEmojis = remember { loadRecentCommentEmojis(readingSettings) }

    fun insertCommentEmoji(placeholder: String) {
        val text = commentFieldState.text
        val start = commentFieldState.selection.min.coerceIn(0, text.length)
        val end = commentFieldState.selection.max.coerceIn(start, text.length)
        commentFieldState.edit {
            replace(start, end, placeholder)
            selection = TextRange(start + placeholder.length)
        }
        recordRecentCommentEmoji(readingSettings, placeholder)
    }

    val stickerPanel = rememberCommentStickerPanelState(
        emojiPickerState = emojiPickerState,
        isSendingState = isSendingState,
        paginationEnvironment = paginationEnvironment,
        userMessages = userMessages,
        coroutineScope = coroutineScope,
    )

    val voice = key(resolvedContent, commentClient) {
        rememberCommentVoiceState(
            resolvedContent = resolvedContent,
            accountClient = commentClient,
            paginationEnvironment = paginationEnvironment,
            isSendingState = isSendingState,
            isCurrentAccount = { commentAccount.client === commentClient },
            coroutineScope = coroutineScope,
            userMessages = userMessages,
            onPickerLaunched = onPickerLaunched,
        )
    }
    var showCommentTools by remember { mutableStateOf(false) }
    val commentToolsRequired = voice.recorder.isRecording || voice.recorder.isBusy ||
        voice.recordedVoice != null ||
        voice.voiceUploadError != null ||
        voice.isUploadingVoice ||
        isUploadingImage ||
        stickerPanel.isUploadingSticker ||
        pendingImages.isNotEmpty() ||
        pendingStickers.isNotEmpty()
    val commentToolsVisible = showCommentTools || commentToolsRequired

    var showMentionPicker by remember { mutableStateOf(false) }
    val mentionQuery = rememberTextFieldState()
    var mentionResults by remember { mutableStateOf<List<MentionCandidate>>(emptyList()) }
    var mentionLoading by remember { mutableStateOf(false) }

    val queryText = mentionQuery.text.toString()
    LaunchedEffect(showMentionPicker, queryText) {
        if (!showMentionPicker) return@LaunchedEffect
        delay(300)
        mentionLoading = true
        try {
            mentionResults = fetchMentionCandidates(paginationEnvironment, queryText)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mentionResults = emptyList()
            userMessages.showShortMessage(CommentsStrings.userSearchFailed(e.message.orEmpty()))
        } finally {
            mentionLoading = false
        }
    }

    fun insertMention(user: MentionCandidate) {
        val text = commentFieldState.text
        val start = commentFieldState.selection.min.coerceIn(0, text.length)
        val end = commentFieldState.selection.max.coerceIn(start, text.length)
        val insert = "@${user.name} "
        commentFieldState.edit {
            replace(start, end, insert)
            selection = TextRange(start + insert.length)
        }
        mentions = mentions + CommentMention(user.id, user.name, user.urlToken)
        showMentionPicker = false
    }

    if (showMentionPicker) {
        CommentScreenMentionPicker(
            mentionQuery = mentionQuery,
            mentionLoading = mentionLoading,
            mentionResults = mentionResults,
            onSelect = ::insertMention,
            onDismiss = { showMentionPicker = false },
        )
    }

    fun submitComment(onSent: () -> Unit) {
        if (
            !composerAllowed ||
            (
                commentFieldState.text.isBlank() &&
                    pendingImages.isEmpty() &&
                    pendingStickers.isEmpty() &&
                    voice.uploadedVoiceId == null
            ) ||
            isSending ||
            voice.isUploadingVoice ||
            isUploadingImage ||
            stickerPanel.isUploadingSticker ||
            voice.recorder.isRecording || voice.recorder.isBusy
        ) {
            return
        }

        isSending = true
        viewModel.submitComment(
            content = content(),
            commentText = commentFieldState.text.toString(),
            environment = paginationEnvironment,
            replyToCommentId = replyToComment?.item?.id,
            images = pendingImages,
            stickers = pendingStickers,
            mentions = mentions,
            syncToPin = syncToPin && rootContent !is SegmentCommentHolder,
            sender = sender,
            voiceId = voice.uploadedVoiceId,
            onFinished = {
                isSending = false
                viewModel.errorMessage?.let(userMessages::showShortMessage)
            },
        ) {
            onSent()
            commentFieldState.clearText()
            pendingImages = emptyList()
            pendingStickers = emptyList()
            mentions = emptyList()
            syncOnce = false
            if (mobileComposer) sender = null
            replyToComment = null
            showEmojiPicker = false
            voice.uploadedVoiceId?.let { voiceId ->
                voice.recordedVoice?.let { voice.submittedVoices[voiceId] = it }
            }
            /** 转码响应可能暂缺 URL，保留本地录音供已发送气泡立即播放。 */
            voice.recordedVoice = null
            voice.discardRecordedVoice()
            coroutineScope.launch {
                activeListState.animateScrollToItem(
                    0,
                    0,
                )
            }
        }
    }

    val commentTitle = if (resolvedContent is CommentHolder) {
        CommentsStrings.replies
    } else if (forwardMode) {
        CommentsStrings.forwards
    } else {
        CommentsStrings.normalComments
    }
    val skin = LocalAppSkinTokens.current
    Box(
        modifier = Modifier.fillMaxSize().semantics { paneTitle = commentTitle },
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.BottomCenter),
            shape = if (squarePopups) RectangleShape else RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = commentBackgroundColor,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
            ) {
                if (rootHeader != null) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        Box(Modifier.heightIn(max = maxHeight / 2)) { rootHeader() }
                    }
                }
                CommentScreenHeader(
                    commentTitle = commentTitle,
                    skin = skin,
                    loadedCount = if (forwardMode) checkNotNull(forwards).items.size else viewModel.allData.size,
                    sortOrder = viewModel.sortOrder.takeIf { !forwardMode && activeCommentItem == null && viewModel.supportsSorting },
                    onRetryForwards = if (tabChecked && forwards != null && forwards.tabError != null) {
                        { forwardScope.launch { forwards.loadTab(refresh = true) } }
                    } else {
                        null
                    },
                    onDismiss = onDismiss,
                    onSelectSort = { index ->
                        val order = if (index == 0) CommentSortOrder.SCORE else CommentSortOrder.TIME
                        if (order != viewModel.sortOrder) {
                            restoredListPosition = 0 to 0
                            restoredListPositionApplied = false
                            activeListState.requestScrollToItem(0)
                            viewModel.changeSortOrder(order, paginationEnvironment)
                        }
                        saveReadingPreferences(
                            readingSettings,
                            loadReadingPreferences(readingSettings).copy(
                                commentOrder = if (index == 0) ReadingCommentOrder.Score else ReadingCommentOrder.Time,
                            ),
                        )
                    },
                )
                if (hasSentenceTabs || hasForwards) {
                    CommentScreenTabs(
                        selected = activeTab,
                        hasSentences = hasSentenceTabs,
                        hasForwards = hasForwards,
                        normalCount = normalComments?.totalCommentCount,
                        sentenceCount = normalComments?.sentenceCommentCount,
                        forwardCount = forwards?.tab?.count,
                    ) { tab ->
                        if (tab == CommentPanelTab.Forward) {
                            showEmojiPicker = false
                            isCommentInputFocused = false
                        }
                        selectedTab = tab
                    }
                }
                if (forwardMode && forwards != null) {
                    CommentScreenForwardList(forwards, forwardListState, pageTurnTarget, modifier = Modifier.weight(1f))
                    return@Column
                }
                CommentScreenList(
                    viewModel = viewModel,
                    paginationEnvironment = paginationEnvironment,
                    activeCommentItem = activeCommentItem,
                    readOnly = false,
                    sentenceMode = sentenceMode,
                    activeListState = activeListState,
                    pageTurnTarget = pageTurnTarget,
                    voicePreviewPlayer = voice.previewPlayer,
                    submittedVoices = voice.submittedVoices,
                    sentenceQuotes = sentenceQuotes,
                    rootContent = rootContent,
                    actionChipColor = actionChipColor,
                    actionChipIconColor = actionChipIconColor,
                    onDeleteComment = { item ->
                        commentPendingDeletion = item
                        deleteCommentError = null
                    },
                    onCommentClick = { commentItem ->
                        if (activeCommentItem == null) {
                            if (commentItem.clickTarget != null) {
                                onChildCommentClick(commentItem)
                            }
                        } else {
                            if (composerAllowed) {
                                replyToComment = commentItem
                                if (mobileComposer) composerOpen = true
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )

                if (!canReplyToCurrentThread) {
                    Text(CommentsStrings.segmentReplyLocationMissing, modifier = Modifier.padding(12.dp))
                }
                if (!composerAllowed) return@Column
                val commentLinkTitles = remember { mutableStateMapOf<String, CommentLinkInfo>() }
                val requestedCommentLinks = remember { mutableSetOf<String>() }

                fun requestCommentLinkTitles(urls: List<String>) {
                    val missing = urls.filter {
                        it !in requestedCommentLinks && it !in commentLinkTitles
                    }
                    if (missing.isEmpty()) return
                    missing.forEach { requestedCommentLinks += it }
                    coroutineScope.launch {
                        try {
                            val links = parseCommentLinks(paginationEnvironment, missing)
                            commentLinkTitles.putAll(links.filterValues { it.title != it.url })
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            requestedCommentLinks.removeAll(missing.toSet())
                        }
                    }
                }
                val editor: @Composable () -> Unit = {
                    val focusManager = LocalFocusManager.current
                    val keyboardController = LocalSoftwareKeyboardController.current
                    Surface(
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth(),
                        color = commentInputBarColor,
                    ) {
                        Column {
                            CommentScreenReplyBanner(replyToComment) { replyToComment = null }

                            CommentScreenPendingAttachments(
                                pendingImages = pendingImages,
                                pendingStickers = pendingStickers,
                                onRemoveImage = { pendingImages = pendingImages - it },
                                onRemoveSticker = { pendingStickers = pendingStickers - it },
                            )

                            voice.recordedVoice?.let { recorded ->
                                CommentScreenVoicePreview(
                                    voice = recorded,
                                    voicePreviewPlayer = voice.previewPlayer,
                                    isUploadingVoice = voice.isUploadingVoice,
                                    uploadedVoiceId = voice.uploadedVoiceId,
                                    voiceUploadError = voice.voiceUploadError,
                                    isSending = isSending,
                                    uploadRecordedVoice = voice.uploadRecordedVoice,
                                    discardRecordedVoice = voice.discardRecordedVoice,
                                )
                            }

                            CommentScreenInputRow(
                                commentFieldState = commentFieldState,
                                commentInputFocusRequester = commentInputFocusRequester,
                                commentInputScrollState = commentInputScrollState,
                                mentions = mentions,
                                commentLinkTitles = commentLinkTitles,
                                requestCommentLinkTitles = ::requestCommentLinkTitles,
                                replyToComment = replyToComment,
                                showEmojiPicker = showEmojiPicker,
                                onShowEmojiPickerChange = { showEmojiPicker = it },
                                onInputFocusChange = { isCommentInputFocused = it },
                                isSending = isSending,
                                sendEnabled = !isSending && !isUploadingImage && !stickerPanel.isUploadingSticker && !voice.isUploadingVoice && !voice.recorder.isRecording && !voice.recorder.isBusy && (commentFieldState.text.isNotBlank() || pendingImages.isNotEmpty() || pendingStickers.isNotEmpty() || voice.uploadedVoiceId != null),
                                onSend = {
                                    submitComment {
                                        focusManager.clearFocus(force = true)
                                        keyboardController?.hide()
                                        if (mobileComposer) composerOpen = false
                                    }
                                },
                            )

                            CommentScreenOptionsRow(
                                syncToPin = syncToPin.takeUnless { rootContent is SegmentCommentHolder },
                                defaultSyncToPin = defaultSyncToPin,
                                enabled = !isSending,
                                onSyncToPinChange = { thisComment, byDefault ->
                                    syncOnce = thisComment && !byDefault
                                    defaultSyncToPin = byDefault
                                },
                                senderMenu = {
                                    CommentSenderMenu(commentAccount, sender, !isSending) { newSender ->
                                        if (newSender != null && commentToolsRequired) {
                                            userMessages.showShortMessage(CommentsStrings.removeAttachmentsBeforeSwitching)
                                        } else {
                                            sender = newSender
                                        }
                                    }
                                },
                                recentEmojis = recentEmojis,
                                emojiInlineContent = emojiInlineContent,
                                onEmojiClick = ::insertCommentEmoji,
                                showCommentTools = showCommentTools,
                                commentToolsRequired = commentToolsRequired,
                                commentToolsVisible = commentToolsVisible,
                                onToggleCommentTools = { showCommentTools = !showCommentTools },
                            )

                            if (commentToolsVisible) {
                                CommentScreenTools(
                                    voiceRecorder = voice.recorder,
                                    recordingElapsedMs = voice.recordingElapsedMs,
                                    isSending = isSending,
                                    isUploadingImage = isUploadingImage,
                                    isUploadingVoice = voice.isUploadingVoice,
                                    commentInputBarColor = commentInputBarColor,
                                    uploadedVoiceId = voice.uploadedVoiceId,
                                    stopVoiceRecording = voice.stopVoiceRecording,
                                    requestVoicePermission = { if (canAttachMedia()) voice.requestVoicePermission() },
                                    launchImagePicker = launchImagePicker?.let { picker ->
                                        {
                                            if (canAttachMedia()) {
                                                onPickerLaunched()
                                                picker.launch()
                                            }
                                        }
                                    },
                                    onMentionClick = {
                                        showMentionPicker = true
                                        mentionQuery.clearText()
                                    },
                                )
                            }

                            AnimatedVisibility(
                                visible = showEmojiPicker,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut(),
                            ) {
                                CommentScreenEmojiPanel(
                                    panel = stickerPanel,
                                    commentEmojis = commentEmojis,
                                    emojiInlineContent = emojiInlineContent,
                                    onEmojiClick = ::insertCommentEmoji,
                                    onStickerClick = { sticker -> if (canAttachMedia()) pendingStickers = listOf(sticker) },
                                    onPickerLaunched = onPickerLaunched,
                                )
                            }
                        }
                    }
                }
                if (!mobileComposer) {
                    editor()
                } else {
                    CommentScreenMobileComposer(
                        editorTitle = if (resolvedContent is CommentHolder) CommentsStrings.writeReply else CommentsStrings.writeComment,
                        composerOpen = composerOpen,
                        onOpen = { composerOpen = true },
                        onClose = {
                            if (voice.previewPlayer.playingUrl == voice.recordedVoice?.filePath) voice.previewPlayer.stop()
                            showEmojiPicker = false
                            isCommentInputFocused = false
                            composerOpen = false
                            sender = null
                            syncOnce = false
                        },
                        showEmojiPicker = showEmojiPicker,
                        onHideEmojiPicker = { showEmojiPicker = false },
                        voiceRecorder = voice.recorder,
                        commentInputFocusRequester = commentInputFocusRequester,
                        commentInputBarColor = commentInputBarColor,
                        titleStyle = skin.title3,
                        userMessages = userMessages,
                        coroutineScope = coroutineScope,
                        editor = editor,
                    )
                }
            }
        }
        if (showPageTurnFab) {
            PageTurnFab(
                preferenceName = "fabPageTurnComment",
            )
        }
    }
}

internal fun NavDestination.commentThreadKey(): String = when (this) {
    is Article -> "article:$type:$id"
    is Pin -> "pin:$id"
    is Question -> "question:$questionId"
    is SegmentCommentHolder -> "segment:$contentType:$contentId:$segmentId"
    is CommentHolder -> "comment:$commentId:${article.commentThreadKey()}"
    else -> "comment:${this::class.qualifiedName}:${hashCode()}"
}
