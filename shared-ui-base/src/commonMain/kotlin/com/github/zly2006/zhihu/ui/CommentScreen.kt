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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.CommentSortOrder
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.editor.ZhihuImageUploadSource
import com.github.zly2006.zhihu.editor.isImagePickerSupported
import com.github.zly2006.zhihu.editor.rememberImagePickerLauncher
import com.github.zly2006.zhihu.editor.uploadVoiceComment
import com.github.zly2006.zhihu.editor.uploadZhihuImage
import com.github.zly2006.zhihu.markdown.rememberMarkdownImageModel
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.CommentHolder
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.SegmentCommentHolder
import com.github.zly2006.zhihu.navigation.resolveContent
import com.github.zly2006.zhihu.platform.PlatformBackHandler
import com.github.zly2006.zhihu.platform.rememberExternalUrlOpener
import com.github.zly2006.zhihu.platform.rememberImagePreviewOpener
import com.github.zly2006.zhihu.platform.rememberImageSaver
import com.github.zly2006.zhihu.platform.rememberImageSharer
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberSystemUrlOpener
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.reading.ReadingCommentOrder
import com.github.zly2006.zhihu.reading.loadReadingPreferences
import com.github.zly2006.zhihu.reading.saveReadingPreferences
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppAlertDialog
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppCheckbox
import com.github.zly2006.zhihu.ui.components.AppChip
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppDropdownMenu
import com.github.zly2006.zhihu.ui.components.AppDropdownMenuItem
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppPullToRefreshBox
import com.github.zly2006.zhihu.ui.components.AppSegmentedControl
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.AppTextField
import com.github.zly2006.zhihu.ui.components.LocalVoiceCommentPlayer
import com.github.zly2006.zhihu.ui.components.PageTurnFab
import com.github.zly2006.zhihu.ui.components.RecordedVoiceComment
import com.github.zly2006.zhihu.ui.components.VoiceCommentPlayer
import com.github.zly2006.zhihu.ui.components.pageTurnContentEndMarker
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.ui.components.rememberVoiceCommentPlayer
import com.github.zly2006.zhihu.ui.components.rememberVoiceCommentRecorder
import com.github.zly2006.zhihu.ui.components.rememberVoiceRecordPermissionRequester
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import com.github.zly2006.zhihu.ui.subscreens.PREF_FONT_SIZE
import com.github.zly2006.zhihu.ui.subscreens.PREF_LINE_HEIGHT
import com.github.zly2006.zhihu.util.twoDigitString
import com.github.zly2006.zhihu.viewmodel.CommentItem
import com.github.zly2006.zhihu.viewmodel.comment.BaseCommentViewModel
import com.github.zly2006.zhihu.viewmodel.comment.ChildCommentViewModel
import com.github.zly2006.zhihu.viewmodel.comment.CommentImageAttachment
import com.github.zly2006.zhihu.viewmodel.comment.CommentLinkInfo
import com.github.zly2006.zhihu.viewmodel.comment.CommentMention
import com.github.zly2006.zhihu.viewmodel.comment.CommentSticker
import com.github.zly2006.zhihu.viewmodel.comment.CommentStickerGroup
import com.github.zly2006.zhihu.viewmodel.comment.MentionCandidate
import com.github.zly2006.zhihu.viewmodel.comment.RootCommentViewModel
import com.github.zly2006.zhihu.viewmodel.comment.StickerAlreadyCollectedException
import com.github.zly2006.zhihu.viewmodel.comment.collectCommentSticker
import com.github.zly2006.zhihu.viewmodel.comment.fetchCollectedStickers
import com.github.zly2006.zhihu.viewmodel.comment.fetchGroupStickers
import com.github.zly2006.zhihu.viewmodel.comment.fetchMentionCandidates
import com.github.zly2006.zhihu.viewmodel.comment.fetchRecommendedStickers
import com.github.zly2006.zhihu.viewmodel.comment.fetchStickerGroups
import com.github.zly2006.zhihu.viewmodel.comment.loadRecentCommentEmojis
import com.github.zly2006.zhihu.viewmodel.comment.parseCommentLinks
import com.github.zly2006.zhihu.viewmodel.comment.recordRecentCommentEmoji
import com.github.zly2006.zhihu.viewmodel.comment.removeCollectedCommentSticker
import com.github.zly2006.zhihu.viewmodel.comment.uploadCommentSticker
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Instant
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

const val COMMENT_SCREEN_LIST_TAG = "comment_screen_list"
const val COMMENT_REPLY_BANNER_TAG = "comment_reply_banner"
const val COMMENT_CANCEL_REPLY_TAG = "comment_cancel_reply"
const val COMMENT_INPUT_TAG = "comment_input"
const val COMMENT_EMOJI_BUTTON_TAG = "comment_emoji_button"
const val COMMENT_EMOJI_PICKER_TAG = "comment_emoji_picker"
const val COMMENT_EMOJI_ITEM_TAG_PREFIX = "comment_emoji_item_"
const val COMMENT_IMAGE_BUTTON_TAG = "comment_image_button"
const val COMMENT_VOICE_BUTTON_TAG = "comment_voice_button"
const val COMMENT_VOICE_RECORD_BUTTON_TAG = "comment_voice_record_button"
const val COMMENT_VOICE_PREVIEW_TAG = "comment_voice_preview"
const val COMMENT_VOICE_DELETE_TAG = "comment_voice_delete"
const val COMMENT_VOICE_UPLOAD_RETRY_TAG = "comment_voice_upload_retry"
const val COMMENT_MENTION_BUTTON_TAG = "comment_mention_button"
const val COMMENT_MENTION_SEARCH_TAG = "comment_mention_search"
const val COMMENT_SYNC_PIN_TAG = "comment_sync_pin"
const val COMMENT_EMOJI_TAB_TAG_PREFIX = "comment_emoji_tab_"
const val COMMENT_STICKER_ITEM_TAG_PREFIX = "comment_sticker_item_"
const val COMMENT_STICKER_UPLOAD_TAG = "comment_sticker_upload"
const val COMMENT_TOOLS_BUTTON_TAG = "comment_tools_button"
const val COMMENT_TOOLS_ROW_TAG = "comment_tools_row"

sealed interface EmojiPanelEntry {
    data object Default : EmojiPanelEntry

    data object Favorites : EmojiPanelEntry

    data class Group(
        val group: CommentStickerGroup,
    ) : EmojiPanelEntry
}

const val COMMENT_SEND_BUTTON_TAG = "comment_send_button"
const val COMMENT_SORT_SCORE_TAG = "comment_sort_score"
const val COMMENT_SORT_TIME_TAG = "comment_sort_time"
const val COMMENT_IMAGE_MENU_OPEN_TAG = "comment_image_menu_open"
const val COMMENT_IMAGE_MENU_BROWSER_TAG = "comment_image_menu_browser"
const val COMMENT_IMAGE_MENU_SAVE_TAG = "comment_image_menu_save"
const val COMMENT_IMAGE_MENU_SHARE_TAG = "comment_image_menu_share"
const val COMMENT_DELETE_DIALOG_TAG = "comment_delete_dialog"
const val COMMENT_DELETE_CONFIRM_TAG = "comment_delete_confirm"
const val COMMENT_DELETE_CANCEL_TAG = "comment_delete_cancel"

@Composable
fun SwipeToReplyContainer(
    modifier: Modifier = Modifier,
    onArchive: (() -> Unit)? = null,
    onReply: (() -> Unit)? = null,
    archiveIcon: ImageVector = Icons.Default.Archive,
    replyIcon: ImageVector = Icons.AutoMirrored.Filled.Reply,
    content: @Composable () -> Unit,
) {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current

    val triggerThreshold = with(density) { 60.dp.toPx() }
    val maxDragDistance = with(density) { 100.dp.toPx() }

    var hasVibrated by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        if (offsetX.value != 0f) {
            val isRightSwipe = offsetX.value > 0

            val progress = (abs(offsetX.value) / triggerThreshold).coerceIn(0f, 1f)

            val iconScale = 0.5f + (0.5f * progress)

            val align = if (isRightSwipe) Alignment.CenterStart else Alignment.CenterEnd
            val icon = if (isRightSwipe) archiveIcon else replyIcon
            val iconTint = if (isRightSwipe) Color.Gray else MaterialTheme.colorScheme.primary

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = 16.dp),
                contentAlignment = align,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                        alpha = progress
                    },
                )
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            val current = offsetX.value
                            val target = current + delta * 0.5f

                            val isTryingToSwipeRight = target > 0
                            val isTryingToSwipeLeft = target < 0

                            val canSwipeRight = onArchive != null
                            val canSwipeLeft = onReply != null

                            var newOffset = target

                            if (isTryingToSwipeRight && !canSwipeRight) {
                                newOffset = 0f
                            }
                            if (isTryingToSwipeLeft && !canSwipeLeft) {
                                newOffset = 0f
                            }

                            if (newOffset > maxDragDistance) newOffset = maxDragDistance
                            if (newOffset < -maxDragDistance) newOffset = -maxDragDistance

                            offsetX.snapTo(newOffset)

                            if (abs(newOffset) >= triggerThreshold) {
                                if (!hasVibrated) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    hasVibrated = true
                                }
                            } else {
                                hasVibrated = false
                            }
                        }
                    },
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        val currentVal = offsetX.value

                        if (currentVal >= triggerThreshold && onArchive != null) {
                            onArchive()
                        } else if (currentVal <= -triggerThreshold && onReply != null) {
                            onReply()
                        }

                        scope.launch {
                            offsetX.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow,
                                ),
                            )
                            hasVibrated = false
                        }
                    },
                ),
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClickableImageWithMenu(
    imageUrl: String,
    modifier: Modifier = Modifier,
    contentDescription: String = "图片",
) {
    var showContextMenu by remember { mutableStateOf(false) }
    val openImagePreview = rememberImagePreviewOpener()
    val openExternalUrl = rememberExternalUrlOpener()
    val saveImage = rememberImageSaver()
    val shareImage = rememberImageSharer()

    PlatformBackHandler(enabled = showContextMenu) {
        showContextMenu = false
    }

    Box(
        modifier = modifier.combinedClickable(
            onClick = { openImagePreview(imageUrl) },
            onLongClick = { showContextMenu = true },
        ),
    ) {
        AsyncImage(
            model = rememberMarkdownImageModel(imageUrl),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
        )

        AppDropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
        ) {
            AppDropdownMenuItem(
                modifier = Modifier.testTag(COMMENT_IMAGE_MENU_OPEN_TAG),
                text = { Text("查看图片") },
                onClick = {
                    openImagePreview(imageUrl)
                    showContextMenu = false
                },
            )
            AppDropdownMenuItem(
                modifier = Modifier.testTag(COMMENT_IMAGE_MENU_BROWSER_TAG),
                text = { Text("在浏览器中打开") },
                onClick = {
                    openExternalUrl(imageUrl)
                    showContextMenu = false
                },
            )
            AppDropdownMenuItem(
                modifier = Modifier.testTag(COMMENT_IMAGE_MENU_SAVE_TAG),
                text = { Text("保存图片") },
                onClick = {
                    saveImage(imageUrl)
                    showContextMenu = false
                },
            )
            AppDropdownMenuItem(
                modifier = Modifier.testTag(COMMENT_IMAGE_MENU_SHARE_TAG),
                text = { Text("分享图片") },
                onClick = {
                    showContextMenu = false
                    shareImage(imageUrl)
                },
            )
        }
    }
}

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
    sentenceCommentsState: MutableState<Boolean> = rememberSaveable(content().commentThreadKey()) { mutableStateOf(false) },
    sentenceListState: LazyListState = rememberLazyListState(),
    initialComment: DataHolder.Comment? = null,
    onInitialChildCommentResolved: (CommentItem, DataHolder.Comment) -> Unit = { _, _ -> },
    pageTurnEnabled: Boolean = false,
    showPageTurnFab: Boolean = false,
) {
    val paginationEnvironment = rememberPaginationEnvironment(allowGuestAccess = false)
    val commentAccount = rememberZhihuAccountStore()
    val commentClient = commentAccount.client
    val readingSettings = rememberSettingsStore()
    val initialReadingCommentOrder = remember(readingSettings) {
        loadReadingPreferences(readingSettings).commentOrder
    }
    val resolvedContent = content()
    var showSentenceComments by sentenceCommentsState
    val currentCommentSegments by rememberUpdatedState(commentSegments)
    var isSending by remember { mutableStateOf(false) }
    var replyToComment by remember { mutableStateOf<CommentItem?>(null) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var commentPendingDeletion by remember(resolvedContent, commentClient) { mutableStateOf<CommentItem?>(null) }
    var isDeletingComment by remember(resolvedContent, commentClient) { mutableStateOf(false) }
    var deleteCommentError by remember(resolvedContent, commentClient) { mutableStateOf<String?>(null) }
    var isCommentInputFocused by remember { mutableStateOf(false) }
    val initialTargetId = initialCommentId ?: initialComment?.id
    val viewModelKey = resolvedContent.commentThreadKey() + initialTargetId?.let { ":initial:$it" }.orEmpty()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val commentInputFocusRequester = remember { FocusRequester() }
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

    PlatformBackHandler(enabled = showEmojiPicker) {
        showEmojiPicker = false
    }

    val normalComments: RootCommentViewModel? = if (resolvedContent is CommentHolder) {
        null
    } else {
        viewModel(key = viewModelKey) {
            RootCommentViewModel(resolvedContent, initialCommentId, commentSegments = { currentCommentSegments }).apply {
                sortOrder = when (initialReadingCommentOrder) {
                    ReadingCommentOrder.Score -> CommentSortOrder.SCORE
                    ReadingCommentOrder.Time -> CommentSortOrder.TIME
                }
            }
        }
    }
    val hasOfficialSentenceComments = (normalComments?.sentenceCommentCount ?: 0) > 0
    val hasSentenceTabs = resolvedContent is Article && hasOfficialSentenceComments
    val sentenceMode = showSentenceComments && hasSentenceTabs
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
        if (!hasSentenceTabs) showSentenceComments = false
    }
    val pageTurnActive = pageTurnEnabled &&
        !showEmojiPicker &&
        !isCommentInputFocused &&
        commentPendingDeletion == null
    val pageTurnTarget = rememberPageTurnTarget(
        listState = activeListState,
        enabled = pageTurnActive,
    )
    var restoredListPosition by remember(viewModel, activeListState) {
        mutableStateOf(activeListState.firstVisibleItemIndex to activeListState.firstVisibleItemScrollOffset)
    }
    var restoredListPositionApplied by remember(viewModel, activeListState) { mutableStateOf(false) }
    LaunchedEffect(viewModel, activeListState, viewModel.isLoading, viewModel.allData.size, restoredListPosition, restoredListPositionApplied) {
        if (!restoredListPositionApplied && !viewModel.isLoading && viewModel.allData.isNotEmpty()) {
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
        AppAlertDialog(
            modifier = Modifier.testTag(COMMENT_DELETE_DIALOG_TAG),
            onDismissRequest = {
                if (!isDeletingComment) {
                    commentPendingDeletion = null
                    deleteCommentError = null
                }
            },
            title = { Text("删除评论") },
            text = {
                Column {
                    Text("删除后无法恢复，确认删除这条评论吗？")
                    deleteCommentError?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                AppTextButton(
                    modifier = Modifier.testTag(COMMENT_DELETE_CONFIRM_TAG),
                    enabled = !isDeletingComment,
                    onClick = {
                        if (commentAccount.client !== commentClient) return@AppTextButton
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
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                AppTextButton(
                    modifier = Modifier.testTag(COMMENT_DELETE_CANCEL_TAG),
                    enabled = !isDeletingComment,
                    onClick = {
                        commentPendingDeletion = null
                        deleteCommentError = null
                    },
                ) {
                    Text("取消")
                }
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

    LaunchedEffect(viewModel, loadMore.value) {
        if (loadMore.value && viewModel.errorMessage == null) {
            viewModel.loadMore(paginationEnvironment)
        }
    }

    LaunchedEffect(viewModel) {
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
    var syncToPin by remember(resolvedContent) { mutableStateOf(false) }
    var isUploadingImage by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val launchImagePicker = if (isImagePickerSupported) {
        rememberImagePickerLauncher { picked ->
            if (isUploadingImage || isSending) return@rememberImagePickerLauncher
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
                    userMessages.showShortMessage("图片上传失败：${e.message}")
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
    var emojiEntry by remember { mutableStateOf<EmojiPanelEntry>(EmojiPanelEntry.Default) }
    var collectedStickers by remember { mutableStateOf<List<CommentSticker>>(emptyList()) }
    var recommendedStickers by remember { mutableStateOf<List<CommentSticker>>(emptyList()) }
    var stickerGroups by remember { mutableStateOf<List<CommentStickerGroup>>(emptyList()) }
    var groupStickers by remember { mutableStateOf<List<CommentSticker>>(emptyList()) }
    val groupStickerCache = remember { mutableStateMapOf<String, List<CommentSticker>>() }
    var favoritesLoaded by remember { mutableStateOf(false) }
    var emojiPanelLoading by remember { mutableStateOf(false) }
    var stickerGroupsLoading by remember { mutableStateOf(false) }
    var isUploadingSticker by remember { mutableStateOf(false) }

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

    val launchStickerPicker = if (isImagePickerSupported) {
        rememberImagePickerLauncher { picked ->
            if (isUploadingSticker || isSending) return@rememberImagePickerLauncher
            isUploadingSticker = true
            coroutineScope.launch {
                try {
                    val uploaded = uploadZhihuImage(
                        paginationEnvironment,
                        picked.bytes,
                        picked.mimeType,
                        picked.fileName,
                        ZhihuImageUploadSource.Zim,
                    )
                    val token = uploaded.hash
                        ?: throw IllegalStateException("未拿到图片 token")
                    val uploadedStickers = uploadCommentSticker(paginationEnvironment, token)
                    collectedStickers = (uploadedStickers + collectedStickers).distinctBy { it.id }
                    emojiEntry = EmojiPanelEntry.Favorites
                    userMessages.showShortMessage("表情已上传")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: StickerAlreadyCollectedException) {
                    try {
                        collectedStickers = fetchCollectedStickers(paginationEnvironment).stickers
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        userMessages.showShortMessage("刷新收藏失败，请重新打开表情面板")
                    }
                    emojiEntry = EmojiPanelEntry.Favorites
                    userMessages.showShortMessage("该表情已在收藏中")
                } catch (e: Exception) {
                    userMessages.showShortMessage("表情上传失败：${e.message}")
                } finally {
                    isUploadingSticker = false
                }
            }
        }
    } else {
        null
    }

    LaunchedEffect(showEmojiPicker) {
        if (!showEmojiPicker || stickerGroups.isNotEmpty() || stickerGroupsLoading) {
            return@LaunchedEffect
        }
        stickerGroupsLoading = true
        try {
            stickerGroups = fetchStickerGroups(paginationEnvironment)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            userMessages.showShortMessage("表情分组加载失败，请重新打开表情面板")
        } finally {
            stickerGroupsLoading = false
        }
    }

    LaunchedEffect(emojiEntry, showEmojiPicker) {
        if (!showEmojiPicker) return@LaunchedEffect
        when (val entry = emojiEntry) {
            EmojiPanelEntry.Default -> Unit

            EmojiPanelEntry.Favorites -> if (!favoritesLoaded && !emojiPanelLoading) {
                emojiPanelLoading = true
                try {
                    collectedStickers = fetchCollectedStickers(paginationEnvironment).stickers
                    recommendedStickers = fetchRecommendedStickers(paginationEnvironment).stickers
                    favoritesLoaded = true
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    userMessages.showShortMessage("收藏表情加载失败，请重新打开表情面板")
                } finally {
                    emojiPanelLoading = false
                }
            }

            is EmojiPanelEntry.Group -> {
                val cached = groupStickerCache[entry.group.id]
                if (cached != null) {
                    groupStickers = cached
                } else if (!emojiPanelLoading) {
                    emojiPanelLoading = true
                    try {
                        val stickers = fetchGroupStickers(paginationEnvironment, entry.group.id)
                        groupStickers = stickers
                        if (stickers.isNotEmpty()) {
                            groupStickerCache[entry.group.id] = stickers
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        groupStickers = emptyList()
                        userMessages.showShortMessage("贴纸加载失败，请重新打开表情面板")
                    } finally {
                        emojiPanelLoading = false
                    }
                }
            }
        }
    }

    val voiceRecorder = rememberVoiceCommentRecorder()
    val voicePreviewPlayer = LocalVoiceCommentPlayer.current ?: rememberVoiceCommentPlayer()
    val recordedVoiceState = remember(resolvedContent) { mutableStateOf<RecordedVoiceComment?>(null) }
    var recordedVoice by recordedVoiceState
    var uploadedVoiceId by remember(resolvedContent) { mutableStateOf<String?>(null) }
    val submittedVoices = remember(resolvedContent) { mutableStateMapOf<String, RecordedVoiceComment>() }
    DisposableEffect(submittedVoices, voiceRecorder, recordedVoiceState) {
        onDispose {
            voiceRecorder.cancel()
            recordedVoiceState.value?.let(voiceRecorder::delete)
            submittedVoices.values.forEach(voiceRecorder::delete)
        }
    }
    var isUploadingVoice by remember { mutableStateOf(false) }
    var recordingElapsedMs by remember { mutableStateOf(0L) }
    var voiceErrorMessage by remember { mutableStateOf<String?>(null) }
    var voiceUploadError by remember(resolvedContent) { mutableStateOf<String?>(null) }
    var isVoiceScreenActive by remember { mutableStateOf(true) }
    var showCommentTools by remember { mutableStateOf(false) }
    val commentToolsRequired = voiceRecorder.isRecording ||
        recordedVoice != null ||
        voiceUploadError != null ||
        isUploadingVoice ||
        isUploadingImage ||
        isUploadingSticker ||
        pendingImages.isNotEmpty() ||
        pendingStickers.isNotEmpty()
    val commentToolsVisible = showCommentTools || commentToolsRequired

    DisposableEffect(Unit) {
        onDispose {
            isVoiceScreenActive = false
            voicePreviewPlayer.stop()
        }
    }

    fun discardRecordedVoice() {
        voicePreviewPlayer.stop()
        recordedVoice?.let { voiceRecorder.delete(it) }
        recordedVoice = null
        uploadedVoiceId = null
        voiceUploadError = null
    }

    fun uploadRecordedVoice() {
        if (isUploadingVoice || isSending) return
        val recorded = recordedVoice ?: return
        val bytes = voiceRecorder.readBytes(recorded)
        if (bytes == null || bytes.isEmpty()) {
            voiceUploadError = "读取录音失败"
            voiceErrorMessage = voiceUploadError
            return
        }
        voiceUploadError = null
        isUploadingVoice = true
        coroutineScope.launch {
            try {
                val uploaded = uploadVoiceComment(
                    environment = paginationEnvironment,
                    bytes = bytes,
                    durationMs = recorded.durationMs,
                    mimeType = recorded.mimeType,
                )
                uploadedVoiceId = uploaded.voiceId
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                voiceUploadError = "语音上传失败：${e.message}"
                voiceErrorMessage = voiceUploadError
            } finally {
                isUploadingVoice = false
            }
        }
    }

    fun stopVoiceRecording() {
        val recorded = voiceRecorder.stop()
        if (recorded == null || recorded.sizeBytes <= 0L) {
            voiceErrorMessage = "录音时间太短"
            return
        }
        recordedVoice?.let { voiceRecorder.delete(it) }
        recordedVoice = recorded
        uploadedVoiceId = null
        recordingElapsedMs = recorded.durationMs
        voicePreviewPlayer.toggle(recorded.filePath)
        uploadRecordedVoice()
    }

    val requestVoicePermission = rememberVoiceRecordPermissionRequester { granted ->
        if (!isVoiceScreenActive) return@rememberVoiceRecordPermissionRequester
        if (granted) {
            discardRecordedVoice()
            if (voiceRecorder.start()) {
                recordingElapsedMs = 0L
            } else {
                voiceErrorMessage = "无法开始录音"
            }
        } else {
            voiceErrorMessage = "需要麦克风权限才能录制语音评论"
        }
    }

    LaunchedEffect(voiceRecorder.isRecording) {
        while (voiceRecorder.isRecording) {
            recordingElapsedMs = voiceRecorder.elapsedMs
            delay(100)
        }
    }

    LaunchedEffect(voiceErrorMessage) {
        voiceErrorMessage?.let { message ->
            userMessages.showShortMessage(message)
            voiceErrorMessage = null
        }
    }

    var showMentionPicker by remember { mutableStateOf(false) }
    var mentionQuery by remember { mutableStateOf("") }
    var mentionResults by remember { mutableStateOf<List<MentionCandidate>>(emptyList()) }
    var mentionLoading by remember { mutableStateOf(false) }

    LaunchedEffect(showMentionPicker, mentionQuery) {
        if (!showMentionPicker) return@LaunchedEffect
        delay(300)
        mentionLoading = true
        try {
            mentionResults = fetchMentionCandidates(paginationEnvironment, mentionQuery)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mentionResults = emptyList()
            userMessages.showShortMessage("搜索用户失败：${e.message}")
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
        AppAlertDialog(
            onDismissRequest = { showMentionPicker = false },
            title = { Text("提及用户") },
            text = {
                Column {
                    AppTextField(
                        value = mentionQuery,
                        onValueChange = { mentionQuery = it },
                        singleLine = true,
                        labelText = "搜索提及的用户",
                        placeholder = "搜索用户",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = AppDimens.TouchTarget)
                            .testTag(COMMENT_MENTION_SEARCH_TAG),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    when {
                        mentionLoading -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                AppSpinner(size = 20.dp)
                            }
                        }

                        mentionResults.isEmpty() -> {
                            Text(
                                text = "没有找到用户",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        else -> {
                            Column(
                                modifier = Modifier
                                    .heightIn(max = 320.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                mentionResults.take(8).forEach { user ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = AppDimens.TouchTarget)
                                            .clickable { insertMention(user) }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (!user.avatarUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = user.avatarUrl,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape),
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Text(user.name)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                AppTextButton(
                    text = "关闭",
                    onClick = { showMentionPicker = false },
                    modifier = Modifier.heightIn(min = AppDimens.TouchTarget),
                )
            },
        )
    }

    fun submitComment() {
        if (
            (
                commentFieldState.text.isBlank() &&
                    pendingImages.isEmpty() &&
                    pendingStickers.isEmpty() &&
                    uploadedVoiceId == null
            ) ||
            isSending ||
            isUploadingVoice ||
            isUploadingImage ||
            isUploadingSticker ||
            voiceRecorder.isRecording
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
            syncToPin = syncToPin,
            voiceId = uploadedVoiceId,
            onFinished = {
                isSending = false
                viewModel.errorMessage?.let(userMessages::showShortMessage)
            },
        ) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            commentFieldState.clearText()
            pendingImages = emptyList()
            pendingStickers = emptyList()
            mentions = emptyList()
            syncToPin = false
            replyToComment = null
            showEmojiPicker = false
            uploadedVoiceId?.let { voiceId ->
                recordedVoice?.let { submittedVoices[voiceId] = it }
            }
            /** 转码响应可能暂缺 URL，保留本地录音供已发送气泡立即播放。 */
            recordedVoice = null
            discardRecordedVoice()
            coroutineScope.launch {
                activeListState.animateScrollToItem(
                    0,
                    0,
                )
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = commentBackgroundColor,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
            ) {
                if (hasSentenceTabs) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppChip(
                            selected = !sentenceMode,
                            modifier = Modifier.testTag("comment_tab_normal"),
                            label = { Text("评论", color = if (!sentenceMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = { showSentenceComments = false },
                        )
                        AppChip(
                            selected = sentenceMode,
                            modifier = Modifier.testTag("comment_tab_sentence"),
                            label = { Text("句子评论", color = if (sentenceMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = { showSentenceComments = true },
                        )
                    }
                }
                if (sentenceMode && (viewModel as? RootCommentViewModel)?.isUsingSegmentFallback == true) {
                    Text(
                        text = "全回答句子评论列表未返回内容，当前按正文已知片段读取；不代表完整列表或全局排序。",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("comment_segment_fallback_notice"),
                    )
                }
                AppPullToRefreshBox(
                    isRefreshing = viewModel.isRefreshing,
                    onRefresh = { viewModel.refresh(paginationEnvironment) },
                    modifier = Modifier.weight(1f),
                ) {
                    when {
                        viewModel.isLoading && viewModel.allData.isEmpty() -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                AppSpinner()
                            }
                        }

                        viewModel.errorMessage != null && viewModel.allData.isEmpty() -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(viewModel.errorMessage!!, color = MaterialTheme.colorScheme.error)
                                    AppTextButton(onClick = { viewModel.retry(paginationEnvironment) }, modifier = Modifier.testTag("comment_load_retry")) { Text("重试") }
                                }
                            }
                        }

                        activeCommentItem == null && viewModel.allData.isEmpty() -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(viewModel.commentClosedMessage ?: if (sentenceMode) "当前列表暂无片段评论" else "暂无评论")
                            }
                        }

                        else -> {
                            @Composable
                            fun Comment(
                                commentItem: CommentItem,
                                modifier: Modifier = Modifier,
                                allowDelete: Boolean = true,
                                onChildCommentClick: (CommentItem) -> Unit,
                            ) {
                                var isLiked by remember { mutableStateOf(commentItem.item.liked) }
                                var likeCount by remember { mutableIntStateOf(commentItem.item.likeCount) }

                                Column(modifier = modifier) {
                                    CommentItem(
                                        comment = commentItem,
                                        voicePlayer = voicePreviewPlayer,
                                        localVoice = commentItem.item.voiceInfo
                                            ?.voiceId
                                            ?.let(submittedVoices::get),
                                        quotedSentence = commentItem.item.resourceId
                                            ?.takeIf { commentItem.item.resourceType == "segment" }
                                            ?.let(sentenceQuotes::get),
                                        isLiked = isLiked,
                                        likeCount = likeCount,
                                        isLikeLoading = viewModel.isLikeLoading,
                                        toggleLike = {
                                            viewModel.toggleLikeComment(
                                                commentData = commentItem.item,
                                                environment = paginationEnvironment,
                                            ) {
                                                val newLikeState = !isLiked
                                                isLiked = newLikeState
                                                likeCount += if (newLikeState) 1 else -1
                                                commentItem.item.liked = newLikeState
                                                commentItem.item.likeCount = likeCount
                                            }
                                        },
                                        onChildCommentClick = onChildCommentClick,
                                        onDelete = if (allowDelete && commentItem.item.canDelete) {
                                            {
                                                commentPendingDeletion = commentItem
                                                deleteCommentError = null
                                            }
                                        } else {
                                            null
                                        },
                                    )

                                    if (activeCommentItem == null && commentItem.item.childCommentCount > 0) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 40.dp, top = 8.dp),
                                        ) {
                                            if (commentItem.item.childComments.isNotEmpty()) {
                                                commentItem.item.childComments.forEach { childComment ->
                                                    var liked by remember { mutableStateOf(childComment.liked) }
                                                    var likeCount by remember { mutableIntStateOf(childComment.likeCount) }
                                                    val childCommentItem = CommentItem(
                                                        item = childComment,
                                                        clickTarget = null,
                                                    )
                                                    CommentItem(
                                                        comment = childCommentItem,
                                                        voicePlayer = voicePreviewPlayer,
                                                        localVoice = childComment.voiceInfo?.voiceId?.let(submittedVoices::get),
                                                        quotedSentence = childComment.resourceId?.takeIf { childComment.resourceType == "segment" }?.let(sentenceQuotes::get),
                                                        modifier = Modifier.testTag("comment_row_${childComment.id}"),
                                                        isLiked = liked,
                                                        likeCount = likeCount,
                                                        isLikeLoading = viewModel.isLikeLoading,
                                                        toggleLike = {
                                                            viewModel.toggleLikeComment(
                                                                commentData = childCommentItem.item,
                                                                environment = paginationEnvironment,
                                                            ) {
                                                                val newLikeState = !liked
                                                                liked = newLikeState
                                                                likeCount += if (newLikeState) 1 else -1
                                                                childCommentItem.item.liked = newLikeState
                                                                childCommentItem.item.likeCount = likeCount
                                                            }
                                                        },
                                                        onChildCommentClick = onChildCommentClick,
                                                        onDelete = if (childComment.canDelete) {
                                                            {
                                                                commentPendingDeletion = childCommentItem
                                                                deleteCommentError = null
                                                            }
                                                        } else {
                                                            null
                                                        },
                                                    )
                                                }
                                            }
                                            AppButton(
                                                onClick = { onChildCommentClick(commentItem) },
                                                modifier = Modifier
                                                    .height(28.dp)
                                                    .testTag("comment_child_button_${commentItem.item.id}"),
                                                shape = RoundedCornerShape(50),
                                                containerColor = actionChipColor,
                                                contentColor = MaterialTheme.colorScheme.onSurface,
                                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                            ) {
                                                Icon(
                                                    Icons.AutoMirrored.Outlined.Comment,
                                                    contentDescription = "查看子评论",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = actionChipIconColor,
                                                )
                                                Text(
                                                    "查看 ${commentItem.item.childCommentCount} 条子评论",
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.padding(vertical = 1.dp, horizontal = 4.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            LazyColumn(
                                state = activeListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pageTurnViewportWithGuide(pageTurnTarget)
                                    .testTag(COMMENT_SCREEN_LIST_TAG),
                                contentPadding = PaddingValues(bottom = 16.dp, start = 16.dp, end = 16.dp, top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                if (activeCommentItem != null) {
                                    item(
                                        key = "active_${activeCommentItem.item.id}",
                                    ) {
                                        Column(
                                            modifier = Modifier.animateItem(
                                                fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                                fadeOutSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                                placementSpec = spring(
                                                    stiffness = Spring.StiffnessMediumLow,
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                ),
                                            ),
                                        ) {
                                            Comment(activeCommentItem, allowDelete = false) { }
                                            HorizontalDivider()
                                            if (viewModel.allData.isEmpty()) {
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    Text(
                                                        "暂无回复",
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    item("sorting") {
                                        AppSegmentedControl(
                                            options = listOf("最热", "最新"),
                                            selectedIndex = if (viewModel.sortOrder == CommentSortOrder.SCORE) 0 else 1,
                                            testTags = listOf(COMMENT_SORT_SCORE_TAG, COMMENT_SORT_TIME_TAG),
                                            onSelect = { index ->
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
                                    }
                                }

                                items(
                                    items = viewModel.allData,
                                    key = { it.id },
                                ) { dto ->
                                    val commentItem = viewModel.createCommentItem(dto, article = rootContent)
                                    SwipeToReplyContainer(
                                        modifier = Modifier
                                            .animateItem(
                                                fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                                fadeOutSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                                placementSpec = spring(
                                                    stiffness = Spring.StiffnessMediumLow,
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                ),
                                            ).testTag("comment_row_${dto.id}"),
                                        onReply = {
                                            if (activeCommentItem == null) {
                                                if (commentItem.clickTarget != null) {
                                                    onChildCommentClick(commentItem)
                                                }
                                            } else {
                                                replyToComment = commentItem
                                            }
                                        },
                                    ) {
                                        Comment(
                                            commentItem,
                                        ) { _ ->
                                            if (activeCommentItem == null) {
                                                if (commentItem.clickTarget != null) {
                                                    onChildCommentClick(commentItem)
                                                }
                                            } else {
                                                replyToComment = commentItem
                                            }
                                        }
                                    }
                                }

                                if (viewModel.errorMessage != null && viewModel.allData.isNotEmpty()) {
                                    item(key = "comment_load_error") {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                            Text(viewModel.errorMessage!!, color = MaterialTheme.colorScheme.error)
                                            AppTextButton(onClick = { viewModel.retry(paginationEnvironment) }, modifier = Modifier.testTag("comment_load_retry")) { Text("重试") }
                                        }
                                    }
                                }
                                if (viewModel.isEnd && viewModel.allData.isNotEmpty()) {
                                    pageTurnContentEndMarker()
                                }

                                if (viewModel.isLoading && viewModel.allData.isNotEmpty()) {
                                    item(key = "loading_indicator") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            AppSpinner(size = 24.dp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (!canReplyToCurrentThread) {
                    Text("当前片段缺少可验证的位置，请从正文打开后回复。", modifier = Modifier.padding(12.dp))
                }
                if (viewModel.commentClosedMessage != null || sentenceMode || !canReplyToCurrentThread) return@Column
                Surface(
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                    color = commentInputBarColor,
                ) {
                    Column {
                        AnimatedVisibility(
                            visible = replyToComment != null,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(COMMENT_REPLY_BANNER_TAG),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Reply,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "回复 ${replyToComment?.item?.author?.name ?: ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.weight(1f),
                                    )
                                    AppIconButton(
                                        onClick = { replyToComment = null },
                                        modifier = Modifier
                                            .size(24.dp)
                                            .testTag(COMMENT_CANCEL_REPLY_TAG),
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "取消回复",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        )
                                    }
                                }
                            }
                        }

                        val openPendingPreview = rememberImagePreviewOpener()

                        if (pendingImages.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                pendingImages.forEach { image ->
                                    Box {
                                        AsyncImage(
                                            model = rememberMarkdownImageModel(image.url),
                                            contentDescription = "待发送图片",
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .combinedClickable(
                                                    onClick = { openPendingPreview(image.url) },
                                                    onLongClick = { openPendingPreview(image.url) },
                                                ),
                                            contentScale = ContentScale.Crop,
                                        )
                                        AppIconButton(
                                            onClick = { pendingImages = pendingImages - image },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(20.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "移除图片",
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (pendingStickers.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                pendingStickers.forEach { sticker ->
                                    Box {
                                        AsyncImage(
                                            model = sticker.commentUrl,
                                            contentDescription = sticker.title.ifBlank { "待发送贴纸" },
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .combinedClickable(
                                                    onClick = { openPendingPreview(sticker.commentUrl) },
                                                    onLongClick = { openPendingPreview(sticker.commentUrl) },
                                                ),
                                            contentScale = ContentScale.Fit,
                                        )
                                        AppIconButton(
                                            onClick = { pendingStickers = pendingStickers - sticker },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(20.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "移除贴纸",
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        recordedVoice?.let { voice ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIconButton(
                                    onClick = { voicePreviewPlayer.toggle(voice.filePath) },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag(COMMENT_VOICE_PREVIEW_TAG),
                                ) {
                                    Icon(
                                        imageVector = if (voicePreviewPlayer.playingUrl == voice.filePath) {
                                            Icons.Filled.Pause
                                        } else {
                                            Icons.Filled.PlayArrow
                                        },
                                        contentDescription = if (voicePreviewPlayer.playingUrl == voice.filePath) {
                                            "暂停语音"
                                        } else {
                                            "播放语音"
                                        },
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Text(
                                    text = "${(voice.durationMs / 1000).coerceAtLeast(0)}″",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                when {
                                    isUploadingVoice -> {
                                        AppSpinner(size = 14.dp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "语音上传中…",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                        )
                                    }

                                    uploadedVoiceId != null -> Text(
                                        text = "语音已就绪",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                    )

                                    voiceUploadError != null -> AppTextButton(
                                        text = "重试上传",
                                        onClick = { uploadRecordedVoice() },
                                        enabled = !isSending,
                                        modifier = Modifier
                                            .testTag(COMMENT_VOICE_UPLOAD_RETRY_TAG)
                                            .semantics {
                                                stateDescription = voiceUploadError.orEmpty()
                                                liveRegion = LiveRegionMode.Polite
                                            },
                                    )

                                    else -> Text(
                                        text = "等待上传",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                AppIconButton(
                                    onClick = { discardRecordedVoice() },
                                    enabled = !isUploadingVoice,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag(COMMENT_VOICE_DELETE_TAG),
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "移除语音",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }

                        val commentAccentColor = MaterialTheme.colorScheme.primary
                        val mentionNames = remember(mentions) {
                            mentions.map { it.name }.filter { it.isNotBlank() }
                        }
                        val commentInputScope = rememberCoroutineScope()
                        val commentLinkTitles = remember { mutableStateMapOf<String, CommentLinkInfo>() }
                        val requestedCommentLinks = remember { mutableSetOf<String>() }

                        fun requestCommentLinkTitles(urls: List<String>) {
                            val missing = urls.filter {
                                it !in requestedCommentLinks && it !in commentLinkTitles
                            }
                            if (missing.isEmpty()) return
                            missing.forEach { requestedCommentLinks += it }
                            commentInputScope.launch {
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
                        val commentLinkTitleMap = commentLinkTitles.mapValues { it.value.title }
                        val commentInputGuard = remember(mentionNames, commentLinkTitleMap) {
                            CommentInputGuardTransformation(
                                mentionNames,
                                commentLinkTitleMap.keys,
                                { urls -> requestCommentLinkTitles(urls) },
                            )
                        }
                        val commentOutputTransformation = remember(
                            mentionNames,
                            commentAccentColor,
                            commentLinkTitleMap,
                        ) {
                            CommentInputOutputTransformation(
                                mentionNames,
                                commentAccentColor,
                                commentLinkTitleMap,
                            )
                        }
                        val commentInputEmojiRuns = remember(
                            commentFieldState.text.toString(),
                            commentLinkTitleMap,
                        ) {
                            planCommentInput(
                                commentFieldState.text.toString(),
                                commentLinkTitleMap,
                            ).emojiRuns
                        }
                        val emojiInputBitmaps = rememberCommentEmojiBitmaps(
                            remember(commentInputEmojiRuns) {
                                commentInputEmojiRuns.mapTo(mutableSetOf(), EmojiInputRun::inlineKey)
                            },
                        )
                        val commentInputScrollState = rememberScrollState()
                        var commentInputTextLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 40.dp, max = 140.dp)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIconButton(
                                onClick = {
                                    if (showEmojiPicker) {
                                        showEmojiPicker = false
                                        commentInputFocusRequester.requestFocus()
                                        keyboardController?.show()
                                    } else {
                                        focusManager.clearFocus(force = true)
                                        keyboardController?.hide()
                                        showEmojiPicker = true
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag(COMMENT_EMOJI_BUTTON_TAG),
                            ) {
                                Icon(
                                    imageVector = if (showEmojiPicker) {
                                        Icons.Outlined.Keyboard
                                    } else {
                                        Icons.Outlined.EmojiEmotions
                                    },
                                    contentDescription = if (showEmojiPicker) {
                                        "切换到键盘"
                                    } else {
                                        "选择表情"
                                    },
                                    tint = if (showEmojiPicker) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))

                            BasicTextField(
                                state = commentFieldState,
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                inputTransformation = commentInputGuard,
                                outputTransformation = commentOutputTransformation,
                                onTextLayout = { getLayout -> commentInputTextLayout = getLayout() },
                                scrollState = commentInputScrollState,
                                textStyle = TextStyle.Default.copy(
                                    fontSize = 16.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics {
                                        contentDescription = replyToComment
                                            ?.item
                                            ?.author
                                            ?.name
                                            ?.let { "回复 $it 的评论" } ?: "评论内容"
                                    }.focusRequester(commentInputFocusRequester)
                                    .onFocusChanged {
                                        isCommentInputFocused = it.isFocused
                                        if (it.isFocused) showEmojiPicker = false
                                    }.testTag(COMMENT_INPUT_TAG)
                                    .clipToBounds()
                                    .drawWithContent {
                                        drawContent()
                                        val layout = commentInputTextLayout ?: return@drawWithContent
                                        val scroll = commentInputScrollState.value
                                        emojiInputRunsLocal@ for (run in commentInputEmojiRuns) {
                                            if (run.transformedEnd > layout.layoutInput.text.length) {
                                                continue@emojiInputRunsLocal
                                            }
                                            val bitmap = emojiInputBitmaps[run.inlineKey]
                                                ?: continue@emojiInputRunsLocal
                                            val box = layout.getBoundingBox(run.transformedStart)
                                            val side = min(box.width, box.height)
                                            if (side <= 0f) continue@emojiInputRunsLocal
                                            drawImage(
                                                image = bitmap,
                                                srcOffset = IntOffset.Zero,
                                                srcSize = IntSize(bitmap.width, bitmap.height),
                                                dstOffset = IntOffset(
                                                    (box.left + (box.width - side) / 2f).roundToInt(),
                                                    (box.top + (box.height - side) / 2f - scroll).roundToInt(),
                                                ),
                                                dstSize = IntSize(side.roundToInt(), side.roundToInt()),
                                                filterQuality = FilterQuality.Medium,
                                            )
                                        }
                                    },
                                decorator = TextFieldDecorator { inner ->
                                    Box {
                                        if (commentFieldState.text.isEmpty()) {
                                            Text(
                                                if (replyToComment != null) {
                                                    "回复 ${replyToComment?.item?.author?.name}..."
                                                } else {
                                                    "写下你的评论..."
                                                },
                                                fontSize = 16.sp,
                                            )
                                        }
                                        inner()
                                    }
                                },
                            )
                            AppIconButton(
                                onClick = { submitComment() },
                                modifier = Modifier
                                    .size(24.dp)
                                    .testTag(COMMENT_SEND_BUTTON_TAG),
                                enabled = !isSending && !isUploadingImage && !isUploadingSticker && !isUploadingVoice && !voiceRecorder.isRecording && (commentFieldState.text.isNotBlank() || pendingImages.isNotEmpty() || pendingStickers.isNotEmpty() || uploadedVoiceId != null),
                            ) {
                                if (isSending) {
                                    AppSpinner(size = 16.dp, color = MaterialTheme.colorScheme.primary)
                                } else {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.Send,
                                        contentDescription = "发送评论",
                                        tint = if (commentFieldState.text.isNotBlank()) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        },
                                    )
                                }
                            }
                        }

                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppDimens.PageHorizontalPadding),
                        ) {
                            val syncMaxWidth = (maxWidth - AppDimens.TouchTarget * 2).coerceAtLeast(AppDimens.Space32)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .widthIn(max = syncMaxWidth)
                                        .heightIn(min = AppDimens.TouchTarget)
                                        .testTag(COMMENT_SYNC_PIN_TAG)
                                        .toggleable(
                                            value = syncToPin,
                                            role = Role.Checkbox,
                                            onValueChange = { syncToPin = it },
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AppCheckbox(
                                        checked = syncToPin,
                                        onCheckedChange = null,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clearAndSetSemantics {},
                                    )
                                    Text(
                                        text = "同时发布到想法",
                                        style = LocalAppSkinTokens.current.footnote2,
                                        color = LocalAppSkinTokens.current.onSurfaceVariantSummary,
                                    )
                                }
                                if (recentEmojis.isNotEmpty()) {
                                    LazyRow(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        contentPadding = PaddingValues(start = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        items(recentEmojis.take(6), key = { it }) { placeholder ->
                                            Text(
                                                text = buildAnnotatedString {
                                                    val key = commentEmojiInlineKey(placeholder)
                                                    if (key != null) {
                                                        appendInlineContent(key, placeholder)
                                                    } else {
                                                        append(placeholder)
                                                    }
                                                },
                                                inlineContent = emojiInlineContent,
                                                fontSize = 22.sp,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .clickable { insertCommentEmoji(placeholder) },
                                            )
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                                AppIconButton(
                                    onClick = { showCommentTools = !showCommentTools },
                                    enabled = !commentToolsRequired,
                                    modifier = Modifier
                                        .size(AppDimens.TouchTarget)
                                        .testTag(COMMENT_TOOLS_BUTTON_TAG)
                                        .semantics {
                                            stateDescription = when {
                                                commentToolsRequired -> "有录音或附件，工具保持展开"
                                                showCommentTools -> "已展开"
                                                else -> "已收起"
                                            }
                                        },
                                ) {
                                    Icon(
                                        imageVector = when {
                                            commentToolsRequired -> Icons.Filled.MoreVert
                                            showCommentTools -> Icons.Filled.Close
                                            else -> Icons.Filled.Add
                                        },
                                        contentDescription = when {
                                            commentToolsRequired -> "评论工具已展开"
                                            showCommentTools -> "收起评论工具"
                                            else -> "更多评论工具"
                                        },
                                        tint = when {
                                            commentToolsRequired -> LocalAppSkinTokens.current.disabledOnSurface
                                            commentToolsVisible -> LocalAppSkinTokens.current.primary
                                            else -> LocalAppSkinTokens.current.onSurfaceVariantActions
                                        },
                                    )
                                }
                            }
                        }

                        if (commentToolsVisible) {
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space4)
                                    .testTag(COMMENT_TOOLS_ROW_TAG),
                                horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8),
                                verticalArrangement = Arrangement.spacedBy(AppDimens.Space4),
                                itemVerticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (voiceRecorder.isSupported) {
                                    if (voiceRecorder.isRecording) {
                                        AppIconButton(
                                            onClick = { stopVoiceRecording() },
                                            modifier = Modifier
                                                .size(AppDimens.TouchTarget)
                                                .testTag(COMMENT_VOICE_RECORD_BUTTON_TAG),
                                        ) {
                                            Icon(
                                                Icons.Filled.Stop,
                                                contentDescription = "停止录音",
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                        Text(
                                            text = "录音中 ${(recordingElapsedMs / 1000).coerceAtLeast(0)}″",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    } else {
                                        AppButton(
                                            onClick = { requestVoicePermission() },
                                            enabled = !isSending && !isUploadingImage && !isUploadingVoice,
                                            modifier = Modifier
                                                .heightIn(min = AppDimens.TouchTarget)
                                                .testTag(COMMENT_VOICE_BUTTON_TAG),
                                            shape = MaterialTheme.shapes.medium,
                                            containerColor = commentInputBarColor,
                                            contentColor = if (uploadedVoiceId != null) LocalAppSkinTokens.current.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            contentPadding = PaddingValues(horizontal = AppDimens.Space12, vertical = AppDimens.Space8),
                                        ) {
                                            Icon(Icons.Outlined.Mic, contentDescription = "录制语音评论")
                                            Spacer(modifier = Modifier.width(AppDimens.Space4))
                                            Text("语音", style = LocalAppSkinTokens.current.footnote2)
                                        }
                                    }
                                }
                                if (launchImagePicker != null) {
                                    AppButton(
                                        onClick = { launchImagePicker.launch() },
                                        enabled = !isUploadingImage && !isSending,
                                        modifier = Modifier
                                            .heightIn(min = AppDimens.TouchTarget)
                                            .testTag(COMMENT_IMAGE_BUTTON_TAG),
                                        shape = MaterialTheme.shapes.medium,
                                        containerColor = commentInputBarColor,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        contentPadding = PaddingValues(horizontal = AppDimens.Space12, vertical = AppDimens.Space8),
                                    ) {
                                        if (isUploadingImage) {
                                            AppSpinner(size = AppDimens.IconSizeCompact)
                                        } else {
                                            Icon(Icons.Outlined.Image, contentDescription = "添加图片")
                                        }
                                        Spacer(modifier = Modifier.width(AppDimens.Space4))
                                        Text("图片", style = LocalAppSkinTokens.current.footnote2)
                                    }
                                }
                                if (!voiceRecorder.isSupported || launchImagePicker == null) {
                                    Text(
                                        "当前平台暂不支持" + listOfNotNull(
                                            "录音".takeUnless { voiceRecorder.isSupported },
                                            "选图".takeIf { launchImagePicker == null },
                                        ).joinToString("、"),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                AppButton(
                                    onClick = {
                                        showMentionPicker = true
                                        mentionQuery = ""
                                    },
                                    enabled = !isSending,
                                    modifier = Modifier
                                        .heightIn(min = AppDimens.TouchTarget)
                                        .testTag(COMMENT_MENTION_BUTTON_TAG),
                                    shape = MaterialTheme.shapes.medium,
                                    containerColor = commentInputBarColor,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    contentPadding = PaddingValues(horizontal = AppDimens.Space12, vertical = AppDimens.Space8),
                                ) {
                                    Icon(Icons.Outlined.AlternateEmail, contentDescription = "提及用户")
                                    Spacer(modifier = Modifier.width(AppDimens.Space4))
                                    Text("提及", style = LocalAppSkinTokens.current.footnote2)
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = showEmojiPicker,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp)
                                    .testTag(COMMENT_EMOJI_PICKER_TAG),
                            ) {
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    item(key = "default") {
                                        EmojiPanelChip(
                                            selected = emojiEntry == EmojiPanelEntry.Default,
                                            label = "默认表情包",
                                            onClick = { emojiEntry = EmojiPanelEntry.Default },
                                            testTag = COMMENT_EMOJI_TAB_TAG_PREFIX + "default",
                                        )
                                    }
                                    item(key = "favorites") {
                                        EmojiPanelChip(
                                            selected = emojiEntry == EmojiPanelEntry.Favorites,
                                            label = "收藏",
                                            icon = Icons.Filled.Favorite,
                                            onClick = { emojiEntry = EmojiPanelEntry.Favorites },
                                            testTag = COMMENT_EMOJI_TAB_TAG_PREFIX + "favorites",
                                        )
                                    }
                                    items(
                                        items = stickerGroups.filter { !it.isVip && it.type != "emoji" },
                                        key = { "group_${it.id}" },
                                    ) { group ->
                                        EmojiPanelChip(
                                            selected = (emojiEntry as? EmojiPanelEntry.Group)
                                                ?.group
                                                ?.id == group.id,
                                            label = group.title.ifBlank { "贴纸" },
                                            onClick = { emojiEntry = EmojiPanelEntry.Group(group) },
                                            testTag = COMMENT_EMOJI_TAB_TAG_PREFIX + "group_${group.id}",
                                        )
                                    }
                                    items(
                                        items = stickerGroups.filter { it.isVip },
                                        key = { "vip_${it.id}" },
                                    ) { group ->
                                        EmojiPanelChip(
                                            selected = (emojiEntry as? EmojiPanelEntry.Group)
                                                ?.group
                                                ?.id == group.id,
                                            label = group.title.ifBlank { "会员贴纸" },
                                            isVip = true,
                                            onClick = { emojiEntry = EmojiPanelEntry.Group(group) },
                                            testTag = COMMENT_EMOJI_TAB_TAG_PREFIX + "vip_${group.id}",
                                        )
                                    }
                                }

                                if (emojiPanelLoading) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        AppSpinner(size = 24.dp)
                                    }
                                } else {
                                    when (val entry = emojiEntry) {
                                        EmojiPanelEntry.Default -> DefaultCommentEmojiGrid(
                                            emojis = commentEmojis,
                                            emojiInlineContent = emojiInlineContent,
                                            onEmojiClick = { emoji ->
                                                insertCommentEmoji(emoji.placeholder)
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                        )

                                        EmojiPanelEntry.Favorites -> FavoritesStickerPanel(
                                            collected = collectedStickers,
                                            recommended = recommendedStickers,
                                            isUploading = isUploadingSticker,
                                            onUpload = launchStickerPicker?.let { launcher ->
                                                { launcher.launch() }
                                            },
                                            onCollectedClick = { sticker ->
                                                pendingStickers = listOf(sticker)
                                            },
                                            onCollectedLongClick = { sticker ->
                                                coroutineScope.launch {
                                                    try {
                                                        removeCollectedCommentSticker(
                                                            paginationEnvironment,
                                                            sticker.id,
                                                        )
                                                        collectedStickers = collectedStickers - sticker
                                                        userMessages.showShortMessage("已取消收藏")
                                                    } catch (e: CancellationException) {
                                                        throw e
                                                    } catch (e: Exception) {
                                                        userMessages.showShortMessage("取消收藏失败：${e.message}")
                                                    }
                                                }
                                            },
                                            onRecommendedClick = { sticker ->
                                                pendingStickers = listOf(sticker)
                                            },
                                            onRecommendedLongClick = { sticker ->
                                                coroutineScope.launch {
                                                    try {
                                                        collectCommentSticker(paginationEnvironment, sticker.id)
                                                        recommendedStickers = recommendedStickers.filterNot { it.id == sticker.id }
                                                        collectedStickers = (
                                                            listOf(sticker.copy(collected = true)) +
                                                                collectedStickers
                                                        ).distinctBy { it.id }
                                                        userMessages.showShortMessage("已收藏")
                                                    } catch (e: CancellationException) {
                                                        throw e
                                                    } catch (e: Exception) {
                                                        userMessages.showShortMessage("收藏失败：${e.message}")
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                        )
                                        is EmojiPanelEntry.Group -> StickerGrid(
                                            stickers = groupStickers,
                                            showTitles = true,
                                            onClick = { sticker ->
                                                pendingStickers = listOf(sticker)
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommentItem(
    comment: CommentItem,
    voicePlayer: VoiceCommentPlayer,
    modifier: Modifier = Modifier,
    localVoice: RecordedVoiceComment? = null,
    isLiked: Boolean = false,
    likeCount: Int = 0,
    isLikeLoading: Boolean = false,
    toggleLike: () -> Unit = {},
    onChildCommentClick: (CommentItem) -> Unit,
    onDelete: (() -> Unit)? = null,
    quotedSentence: String? = null,
) {
    val navigator = LocalNavigator.current
    val openSystemUrl = rememberSystemUrlOpener()
    val commentData = comment.item
    val authorPerson =
        Person(
            id = commentData.author.id,
            name = commentData.author.name,
            urlToken = commentData.author.urlToken,
        )
    var showMoreMenu by remember(commentData.id) { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            AsyncImage(
                model = commentData.author.avatarUrl,
                contentDescription = "头像",
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = "打开个人主页") { navigator.onNavigate(authorPerson) },
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(
                verticalArrangement = Arrangement.Top,
                modifier = Modifier,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = commentData.author.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .testTag("comment_author_${commentData.id}")
                            .clickable(onClickLabel = "打开个人主页") { navigator.onNavigate(authorPerson) },
                    )

                    val authorTag = comment.item.authorTag
                        .firstOrNull()
                        ?.get("text")
                        ?.jsonPrimitive
                        ?.contentOrNull

                    if (authorTag != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        AuthorTag(authorTag)
                    }

                    val replyToAuthor = commentData.replyToAuthor
                    if (replyToAuthor != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "回复",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = replyToAuthor.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .testTag("comment_reply_to_author_${commentData.id}")
                                .clickable {
                                    navigator.onNavigate(
                                        Person(
                                            id = replyToAuthor.id,
                                            name = replyToAuthor.name,
                                            urlToken = replyToAuthor.urlToken,
                                        ),
                                    )
                                },
                        )
                    }
                }

                if (!quotedSentence.isNullOrBlank()) {
                    Surface(
                        color = LocalAppSkinTokens.current.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(vertical = AppDimens.Space8),
                    ) {
                        Text(
                            text = "“$quotedSentence”",
                            style = LocalAppSkinTokens.current.footnote1,
                            color = LocalAppSkinTokens.current.onSurfaceVariantSummary,
                            modifier = Modifier.padding(AppDimens.Space12).testTag("comment_sentence_quote_${commentData.id}"),
                        )
                    }
                }
                val document = Ksoup.parseBodyFragment(commentData.content)
                val commentImg =
                    document.selectFirst("a.comment_img")?.attr("href")
                        ?: document.selectFirst("a.comment_gif")?.attr("href")
                        ?: document.selectFirst("a.comment_sticker")?.attr("href")
                val emojisUsed = remember { mutableSetOf<String>() }
                val openExternalUrl = rememberExternalUrlOpener()
                val linkSkin = LocalAppSkinTokens.current
                val linkBackground = MaterialTheme.colorScheme.surfaceContainerLow
                val accentLuminance = linkSkin.primary.luminance()
                val backgroundLuminance = linkBackground.luminance()
                val linkContrast = (maxOf(accentLuminance, backgroundLuminance) + 0.05f) / (minOf(accentLuminance, backgroundLuminance) + 0.05f)
                val linkColor = if (linkContrast >= 4.5f) linkSkin.primary else linkSkin.onSurface
                val string = remember(commentData.content, linkColor) {
                    emojisUsed.clear()
                    buildAnnotatedString {
                        val stripped = document.body().clone()
                        stripped.select("a.comment_img").forEach { it.remove() }
                        stripped.select("a.comment_gif").forEach { it.remove() }
                        stripped.select("a.comment_sticker").forEach { it.remove() }
                        dfsSimple(
                            node = stripped,
                            onNavigate = navigator.onNavigate,
                            openExternalUrl = openExternalUrl::invoke,
                            linkColor = linkColor,
                            componentUsed = emojisUsed,
                        )
                    }
                }

                val inlineContent = rememberCommentEmojiInlineContent(emojisUsed)

                Column {
                    val settings = rememberSettingsStore()
                    val fontSizePercent by rememberObservedSetting(settings, PREF_FONT_SIZE) { getInt(PREF_FONT_SIZE, 100) }
                    val lineHeightPercent by rememberObservedSetting(settings, PREF_LINE_HEIGHT) { getInt(PREF_LINE_HEIGHT, 160) }
                    SelectionContainer(
                        modifier = Modifier.commentSelectionWorkaround(),
                    ) {
                        Text(
                            text = string,
                            fontSize = 16.sp * fontSizePercent / 100,
                            lineHeight = 16.sp * fontSizePercent / 100 * lineHeightPercent / 100,
                            inlineContent = inlineContent,
                        )
                    }
                    if (commentImg != null) {
                        ClickableImageWithMenu(
                            imageUrl = commentImg,
                            modifier = Modifier
                                .testTag("comment_image_${commentData.id}")
                                .padding(top = 8.dp)
                                .sizeIn(maxHeight = 100.dp, maxWidth = 240.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentDescription = "评论图片",
                        )
                    }
                }
                commentData.voiceInfo?.let { voice -> VoiceCommentBubble(voice, localVoice, voicePlayer) }
            }
        }

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 44.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val formattedTime = remember(commentData.createdTime) {
                formatCommentTime(commentData.createdTime)
            }

            Text(
                text = formattedTime,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val ipInfo = comment.item.commentTag
                .firstOrNull {
                    it.type == "ip_info"
                }?.text
            if (ipInfo != null) {
                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = ipInfo,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            run {
                Box {
                    AppIconButton(
                        onClick = { showMoreMenu = true },
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("comment_more_button_${commentData.id}"),
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "更多操作",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AppDropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                    ) {
                        if (onDelete != null) {
                            AppDropdownMenuItem(
                                modifier = Modifier.testTag("comment_delete_menu_item_${commentData.id}"),
                                text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onDelete()
                                },
                            )
                        }
                        AppDropdownMenuItem(
                            text = { Text("举报") },
                            leadingIcon = { Icon(Icons.Filled.Report, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                openSystemUrl(reportUrl(commentData.id, "comment"))
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .testTag("comment_reply_button_${commentData.id}")
                    .clickable { onChildCommentClick(comment) },
            ) {
                Spacer(modifier = Modifier.width(4.dp))
                Column(
                    modifier = Modifier.height(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Comment,
                        contentDescription = "回复",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                if (comment.item.childCommentCount > 0) {
                    Text(
                        text = comment.item.childCommentCount.toString(),
                        modifier = Modifier.testTag("comment_reply_count_${commentData.id}"),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .testTag("comment_like_button_${commentData.id}")
                    .clickable(enabled = !isLikeLoading) { toggleLike() },
            ) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    if (isLiked) {
                        Icons.Filled.ThumbUp
                    } else {
                        Icons.Outlined.ThumbUp
                    },
                    contentDescription = "点赞",
                    modifier = Modifier.size(16.dp),
                    tint = if (isLiked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = likeCount.toString(),
                    modifier = Modifier.testTag("comment_like_count_${commentData.id}"),
                    fontSize = 12.sp,
                    color = if (isLiked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
        }
    }
}

private fun formatCommentTime(createdTimeSeconds: Long): String {
    val zone = TimeZone.currentSystemDefault()
    val dateTime = Instant.fromEpochSeconds(createdTimeSeconds).toLocalDateTime(zone)
    val now = Clock.System.now().toLocalDateTime(zone)
    return when {
        dateTime.date == now.date -> dateTime.formatHms()
        dateTime.year == now.year -> "${dateTime.month.number.twoDigitString()}-${dateTime.day.twoDigitString()} ${dateTime.formatHms()}"
        else -> "${dateTime.year}-${dateTime.month.number.twoDigitString()}-${dateTime.day.twoDigitString()} ${dateTime.formatHms()}"
    }
}

private fun LocalDateTime.formatHms(): String =
    "${hour.twoDigitString()}:${minute.twoDigitString()}:${second.twoDigitString()}"

private data class EmojiInputRun(
    val transformedStart: Int,
    val transformedEnd: Int,
    val inlineKey: String,
)

private class CommentInputReplacement(
    val start: Int,
    val end: Int,
    val text: String,
    val inlineKey: String? = null,
)

private class CommentInputPlan(
    val replacements: List<CommentInputReplacement>,
    val emojiRuns: List<EmojiInputRun>,
    val linkSpans: List<IntRange>,
)

private fun findEmojiPlaceholderRanges(source: String): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    var index = 0
    while (index < source.length) {
        val open = source.indexOf('[', index)
        if (open < 0) break
        val close = source.indexOf(']', open + 1)
        if (close < 0) break
        if (commentEmojiInlineKey(source.substring(open, close + 1)) != null) {
            ranges += open until (close + 1)
            index = close + 1
        } else {
            index = open + 1
        }
    }
    return ranges
}

private fun findMentionRanges(source: String, mentionNames: List<String>): List<IntRange> {
    if (mentionNames.isEmpty() || '@' !in source) return emptyList()
    val found = mutableListOf<IntRange>()
    mentionNames.sortedByDescending { it.length }.forEach { name ->
        if (name.isBlank()) return@forEach
        val token = "@$name"
        var index = source.indexOf(token)
        while (index >= 0) {
            val range = index until (index + token.length)
            if (found.none { it.first < range.last + 1 && range.first < it.last + 1 }) {
                found += range
            }
            index = source.indexOf(token, index + 1)
        }
    }
    return found.sortedBy { it.first }
}

/** 全角空格为表情绘制预留字符边界；显示替换不写回原始文本。 */
private fun planCommentInput(source: String, linkTitles: Map<String, String>): CommentInputPlan {
    val candidates = mutableListOf<CommentInputReplacement>()
    findEmojiPlaceholderRanges(source).forEach { range ->
        val placeholder = source.substring(range.first, range.last + 1)
        val inlineKey = commentEmojiInlineKey(placeholder) ?: return@forEach
        candidates += CommentInputReplacement(range.first, range.last + 1, "\u3000", inlineKey)
    }
    findPlainUrls(source).forEach { match ->
        val title = linkTitles[match.url] ?: return@forEach
        val end = match.range.first + match.url.length
        if (end > match.range.first && title.isNotEmpty()) {
            candidates += CommentInputReplacement(match.range.first, end, title)
        }
    }
    val replacements = mutableListOf<CommentInputReplacement>()
    candidates.sortedBy { it.start }.forEach { candidate ->
        if (replacements.isEmpty() || candidate.start >= replacements.last().end) {
            replacements += candidate
        }
    }

    val emojiRuns = mutableListOf<EmojiInputRun>()
    val linkSpans = mutableListOf<IntRange>()
    var transformed = 0
    var original = 0
    replacements.forEach { replacement ->
        transformed += replacement.start - original
        val transformedStart = transformed
        transformed += replacement.text.length
        val inlineKey = replacement.inlineKey
        if (inlineKey != null) {
            emojiRuns += EmojiInputRun(
                transformedStart = transformedStart,
                transformedEnd = transformedStart + 1,
                inlineKey = inlineKey,
            )
        } else {
            linkSpans += transformedStart until transformed
        }
        original = replacement.end
    }
    return CommentInputPlan(replacements, emojiRuns, linkSpans)
}

private fun mapOriginalToTransformed(
    replacements: List<CommentInputReplacement>,
    offset: Int,
): Int {
    var delta = 0
    replacements.forEach { replacement ->
        if (replacement.end <= offset) {
            delta += replacement.text.length - (replacement.end - replacement.start)
        } else if (replacement.start < offset) {
            return replacement.start + delta
        } else {
            return offset + delta
        }
    }
    return offset + delta
}

/** 光标偏移映射交给框架；样式范围使用显示态偏移。 */
private class CommentInputOutputTransformation(
    private val mentionNames: List<String>,
    private val accentColor: Color,
    private val linkTitles: Map<String, String>,
) : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        val source = toString()
        val plan = planCommentInput(source, linkTitles)
        plan.replacements.asReversed().forEach { replacement ->
            replace(replacement.start, replacement.end, replacement.text)
        }
        plan.linkSpans.forEach { span ->
            addStyle(
                SpanStyle(color = accentColor, textDecoration = TextDecoration.Underline),
                span.first,
                span.last + 1,
            )
        }
        findMentionRanges(source, mentionNames).forEach { range ->
            val start = mapOriginalToTransformed(plan.replacements, range.first)
            val end = mapOriginalToTransformed(plan.replacements, range.last + 1)
            if (end > start) {
                addStyle(
                    SpanStyle(color = accentColor, fontWeight = FontWeight.Medium),
                    start,
                    end,
                )
            }
        }
    }
}

/** 原子删除表情、已解析链接和提及，避免留下不完整的显示标记。 */
private class CommentInputGuardTransformation(
    private val mentionNames: List<String>,
    private val linkUrls: Set<String>,
    private val onLinkCandidates: (List<String>) -> Unit,
) : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val oldText = originalText.toString()
        val newText = toString()
        if (oldText == newText) return
        val linkCandidates = commentLinkCandidates(oldText, newText)
        if (linkCandidates.isNotEmpty()) onLinkCandidates(linkCandidates)
        if (newText.length >= oldText.length) return
        val linkRanges = if (linkUrls.isEmpty()) {
            emptyList()
        } else {
            findPlainUrls(oldText)
                .filter { it.url in linkUrls }
                .map { it.range.first until (it.range.first + it.url.length) }
        }
        val spans = findEmojiPlaceholderRanges(oldText) + linkRanges + findMentionRanges(oldText, mentionNames)
        if (spans.isEmpty()) return
        val maxPrefix = min(oldText.length, newText.length)
        var prefix = 0
        while (prefix < maxPrefix && oldText[prefix] == newText[prefix]) prefix++
        var suffix = 0
        while (
            suffix < oldText.length - prefix &&
            suffix < newText.length - prefix &&
            oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]
        ) {
            suffix++
        }
        val removedStart = prefix
        val removedEnd = oldText.length - suffix
        if (removedEnd <= removedStart) return
        val span = spans.firstOrNull { span ->
            removedStart < span.last + 1 &&
                removedEnd > span.first &&
                !(removedStart <= span.first && removedEnd >= span.last + 1)
        } ?: return
        val leftLength = (removedStart - span.first).coerceAtLeast(0)
        val rightLength = (span.last + 1 - removedEnd).coerceAtLeast(0)
        replace(span.first, span.first + leftLength + rightLength, "")
        selection = TextRange(span.first)
    }
}

/** 遵循官方 `LinkToTextDetector.s()`：单个空格仅触发恰好在其前结束的 URL，多字符粘贴扫描插入段。 */
private fun commentLinkCandidates(oldText: String, newText: String): List<String> {
    val insertedLength = newText.length - oldText.length
    if (insertedLength <= 0) return emptyList()
    var prefix = 0
    val maxPrefix = min(oldText.length, newText.length)
    while (prefix < maxPrefix && oldText[prefix] == newText[prefix]) prefix++
    if (prefix + insertedLength > newText.length) return emptyList()
    val inserted = newText.substring(prefix, prefix + insertedLength)
    return when {
        inserted == " " -> findPlainUrls(newText)
            .filter { it.range.first + it.url.length == prefix }
            .map { it.url }

        inserted.length > 1 -> findPlainUrls(inserted).map { it.url }
        else -> emptyList()
    }
}

private fun AnnotatedString.Builder.processTextWithEmoji(
    text: String,
    componentUsed: MutableSet<String>?,
) {
    var buffer = StringBuilder()
    var emojiBuffer = StringBuilder()
    var isEmoji = false

    for (ch in text) {
        if (ch == '[') {
            if (buffer.isNotEmpty()) {
                append(buffer.toString())
                buffer = StringBuilder()
            }
            isEmoji = true
            emojiBuffer.append(ch)
        } else if (ch == ']') {
            if (isEmoji) {
                emojiBuffer.append(ch)
                val placeholder = emojiBuffer.toString()
                val emojiKey = commentEmojiInlineKey(placeholder)
                if (emojiKey != null) {
                    appendInlineContent(emojiKey, placeholder)
                    componentUsed?.add(emojiKey)
                } else {
                    append(placeholder)
                }
                emojiBuffer = StringBuilder()
                isEmoji = false
            } else {
                buffer.append(ch)
            }
        } else {
            if (isEmoji) {
                emojiBuffer.append(ch)
            } else {
                buffer.append(ch)
            }
        }
    }

    if (buffer.isNotEmpty()) {
        append(buffer.toString())
    }
    if (isEmoji && emojiBuffer.isNotEmpty()) {
        append(emojiBuffer.toString())
    }
}

fun AnnotatedString.Builder.dfsSimple(
    node: Node,
    onNavigate: (NavDestination) -> Unit,
    openExternalUrl: (String) -> Unit,
    linkColor: Color,
    componentUsed: MutableSet<String>? = null,
) {
    when (node) {
        is Element -> {
            when (node.tagName()) {
                "br" -> append("\n")
                "a" -> {
                    val href = node.attr("href")
                    val linkText = node.text()
                    if (linkText.isNotEmpty()) {
                        withLink(
                            LinkAnnotation.Clickable(
                                href,
                                TextLinkStyles(style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                            ) {
                                resolveContent(href)?.let(onNavigate) ?: openExternalUrl(href)
                            },
                        ) {
                            append(linkText)
                        }
                    }
                }

                else -> node.childNodes().forEach {
                    dfsSimple(it, onNavigate, openExternalUrl, linkColor, componentUsed)
                }
            }
        }

        is TextNode -> {
            val text = node.text()
            var cursor = 0
            findPlainUrls(text).forEach { match ->
                if (match.range.first > cursor) {
                    processTextWithEmoji(text.substring(cursor, match.range.first), componentUsed)
                }
                withLink(
                    LinkAnnotation.Clickable(
                        match.url,
                        TextLinkStyles(style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                    ) { openExternalUrl(match.url) },
                ) {
                    append(match.url)
                }
                val trailingStart = match.range.first + match.url.length
                val trailingEnd = match.range.last + 1
                if (trailingEnd > trailingStart) {
                    processTextWithEmoji(text.substring(trailingStart, trailingEnd), componentUsed)
                }
                cursor = trailingEnd
            }
            if (cursor < text.length) {
                processTextWithEmoji(text.substring(cursor), componentUsed)
            }
        }
        else -> append(node.outerHtml())
    }
}

@Composable
fun AuthorTag(authorTag: String) {
    Box(
        modifier = Modifier
            .border(
                width = 0.5.dp,
                color = Color.Gray,
                shape = RoundedCornerShape(3.dp),
            ).padding(horizontal = 3.dp),
    ) {
        Text(
            text = authorTag,
            fontSize = 12.sp,
            lineHeight = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DefaultCommentEmojiGrid(
    emojis: List<CommentEmoji>,
    emojiInlineContent: Map<String, InlineTextContent>,
    onEmojiClick: (CommentEmoji) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (emojis.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "暂无可用表情",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 48.dp),
        modifier = modifier,
        contentPadding = PaddingValues(8.dp),
    ) {
        items(
            items = emojis,
            key = CommentEmoji::placeholder,
        ) { emoji ->
            AppIconButton(
                onClick = { onEmojiClick(emoji) },
                modifier = Modifier
                    .size(48.dp)
                    .testTag(COMMENT_EMOJI_ITEM_TAG_PREFIX + emoji.placeholder),
            ) {
                Text(
                    text = remember(emoji) {
                        buildAnnotatedString {
                            appendInlineContent(emoji.inlineKey, emoji.placeholder)
                        }
                    },
                    inlineContent = emojiInlineContent,
                    fontSize = 28.sp,
                )
            }
        }
    }
}

@Composable
private fun EmojiPanelChip(
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    label: String? = null,
    icon: ImageVector? = null,
    isVip: Boolean = false,
) {
    AppChip(
        selected = selected,
        onClick = onClick,
        label = {
            if (label != null) {
                Text(label)
            }
        },
        leadingIcon = if (icon != null) {
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(AppDimens.IconSizeSmall),
                )
            }
        } else {
            null
        },
        trailingIcon = if (isVip) {
            {
                Text(
                    text = "VIP",
                    style = if (settingSkinIsMiuix()) LocalAppSkinTokens.current.footnote2 else MaterialTheme.typography.labelSmall,
                    color = if (selected && settingSkinIsMiuix()) LocalContentColor.current else LocalAppSkinTokens.current.primary,
                )
            }
        } else {
            null
        },
        modifier = Modifier
            .heightIn(min = AppDimens.TouchTarget)
            .testTag(testTag),
    )
}

@Composable
private fun FavoritesStickerPanel(
    collected: List<CommentSticker>,
    recommended: List<CommentSticker>,
    isUploading: Boolean,
    onUpload: (() -> Unit)?,
    onCollectedClick: (CommentSticker) -> Unit,
    onCollectedLongClick: (CommentSticker) -> Unit,
    onRecommendedClick: (CommentSticker) -> Unit,
    onRecommendedLongClick: (CommentSticker) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 64.dp),
        modifier = modifier,
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(
            key = "collected_header",
            span = { GridItemSpan(maxLineSpan) },
        ) {
            Text(
                text = "收藏的表情包",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        if (onUpload != null) {
            item(key = "sticker_upload") {
                StickerUploadTile(isUploading = isUploading, onUpload = onUpload)
            }
        }
        items(collected, key = { "collected_${it.id}" }) { sticker ->
            StickerCell(
                sticker = sticker,
                onClick = { onCollectedClick(sticker) },
                onLongClick = { onCollectedLongClick(sticker) },
            )
        }
        item(
            key = "recommended_header",
            span = { GridItemSpan(maxLineSpan) },
        ) {
            Text(
                text = "推荐表情",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        items(recommended, key = { "recommended_${it.id}" }) { sticker ->
            StickerCell(
                sticker = sticker,
                onClick = { onRecommendedClick(sticker) },
                onLongClick = { onRecommendedLongClick(sticker) },
            )
        }
    }
}

@Composable
private fun StickerUploadTile(
    isUploading: Boolean,
    onUpload: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(64.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = !isUploading, onClick = onUpload)
            .testTag(COMMENT_STICKER_UPLOAD_TAG),
    ) {
        if (isUploading) {
            AppSpinner(size = 18.dp)
        } else {
            Icon(
                Icons.Default.Add,
                contentDescription = "上传表情",
                modifier = Modifier.size(18.dp),
            )
            Text("上传", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun StickerGrid(
    stickers: List<CommentSticker>,
    onClick: (CommentSticker) -> Unit,
    modifier: Modifier = Modifier,
    showTitles: Boolean = false,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 64.dp),
        modifier = modifier,
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (stickers.isEmpty()) {
            item(
                key = "sticker_empty",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "暂无贴纸",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(stickers, key = { it.id }) { sticker ->
            StickerCell(
                sticker = sticker,
                onClick = { onClick(sticker) },
                showTitle = showTitles,
            )
        }
    }
}

@Composable
private fun StickerCell(
    sticker: CommentSticker,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    showTitle: Boolean = true,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ).testTag(COMMENT_STICKER_ITEM_TAG_PREFIX + sticker.id)
            .padding(2.dp),
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = sticker.thumbnailUrl,
                contentDescription = sticker.title.ifBlank { "贴纸" },
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            if (sticker.isVipSticker) {
                Text(
                    text = "VIP",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(4.dp),
                        ).padding(horizontal = 3.dp, vertical = 1.dp),
                )
            }
        }
        if (showTitle && sticker.title.isNotBlank()) {
            Text(
                text = sticker.title,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun VoiceCommentBubble(voice: DataHolder.Comment.CommentVoiceInfo, localVoice: RecordedVoiceComment?, player: VoiceCommentPlayer) {
    val playbackUrl = voice.url.ifBlank { localVoice?.filePath.orEmpty() }
    val durationMs = voice.durationMs.takeIf { it > 0 } ?: localVoice?.durationMs ?: 0
    val isPlaying = player.playingUrl == playbackUrl && playbackUrl.isNotBlank()
    val canPlay = player.isSupported && playbackUrl.isNotBlank()
    val skin = LocalAppSkinTokens.current
    var showTranscription by remember(voice.voiceId) { mutableStateOf(voice.asrText.isNotBlank()) }

    Column(modifier = Modifier.padding(top = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(enabled = canPlay) {
                    player.toggle(playbackUrl)
                }.padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("comment_voice_${voice.voiceId}"),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "暂停语音" else "播放语音",
                tint = if (canPlay) skin.onSurface else skin.disabledOnSurface,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${(durationMs / 1000).coerceAtLeast(0)}″",
                style = MaterialTheme.typography.bodyMedium,
                color = if (canPlay) skin.onSurface else skin.disabledOnSurface,
            )
            if (!player.isSupported) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "当前平台暂不支持播放",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (voice.asrText.isNotBlank()) {
            Text(
                text = if (showTranscription) voice.asrText else "转文字",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { showTranscription = !showTranscription }
                    .testTag("comment_voice_text_${voice.voiceId}"),
            )
        }
    }
}
