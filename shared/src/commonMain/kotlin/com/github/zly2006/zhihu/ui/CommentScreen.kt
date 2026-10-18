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
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.editor.ZhihuImageUploadSource
import com.github.zly2006.zhihu.editor.isImagePickerSupported
import com.github.zly2006.zhihu.editor.rememberImagePickerLauncher
import com.github.zly2006.zhihu.editor.uploadVoiceComment
import com.github.zly2006.zhihu.editor.uploadZhihuImage
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
import com.github.zly2006.zhihu.ui.components.PageTurnFab
import com.github.zly2006.zhihu.ui.components.RecordedVoiceComment
import com.github.zly2006.zhihu.ui.components.pageTurnContentEndMarker
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.ui.components.rememberVoiceCommentPlayer
import com.github.zly2006.zhihu.ui.components.rememberVoiceCommentRecorder
import com.github.zly2006.zhihu.ui.components.rememberVoiceRecordPermissionRequester
import com.github.zly2006.zhihu.ui.components.replaceSelection
import com.github.zly2006.zhihu.ui.reportUrl
import com.github.zly2006.zhihu.ui.subscreens.PREF_FONT_SIZE
import com.github.zly2006.zhihu.ui.subscreens.PREF_LINE_HEIGHT
import com.github.zly2006.zhihu.util.twoDigitString
import com.github.zly2006.zhihu.viewmodel.CommentItem
import com.github.zly2006.zhihu.viewmodel.comment.BaseCommentViewModel
import com.github.zly2006.zhihu.viewmodel.comment.ChildCommentViewModel
import com.github.zly2006.zhihu.viewmodel.comment.CommentImageAttachment
import com.github.zly2006.zhihu.viewmodel.comment.CommentMention
import com.github.zly2006.zhihu.viewmodel.comment.CommentSortOrder
import com.github.zly2006.zhihu.viewmodel.comment.CommentSticker
import com.github.zly2006.zhihu.viewmodel.comment.CommentStickerGroup
import com.github.zly2006.zhihu.viewmodel.comment.MentionCandidate
import com.github.zly2006.zhihu.viewmodel.comment.RootCommentViewModel
import com.github.zly2006.zhihu.viewmodel.comment.collectCommentSticker
import com.github.zly2006.zhihu.viewmodel.comment.fetchCollectedStickers
import com.github.zly2006.zhihu.viewmodel.comment.fetchGroupStickers
import com.github.zly2006.zhihu.viewmodel.comment.fetchMentionCandidates
import com.github.zly2006.zhihu.viewmodel.comment.fetchRecommendedStickers
import com.github.zly2006.zhihu.viewmodel.comment.fetchStickerGroups
import com.github.zly2006.zhihu.viewmodel.comment.loadRecentCommentStickers
import com.github.zly2006.zhihu.viewmodel.comment.recordRecentCommentSticker
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
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Instant

typealias CommentModel = CommentItem

const val COMMENT_SCREEN_LIST_TAG = "comment_screen_list"
const val COMMENT_REPLY_BANNER_TAG = "comment_reply_banner"
const val COMMENT_CANCEL_REPLY_TAG = "comment_cancel_reply"
const val COMMENT_INPUT_TAG = "comment_input"
const val COMMENT_EMOJI_BUTTON_TAG = "comment_emoji_button"
const val COMMENT_IMAGE_BUTTON_TAG = "comment_image_button"
const val COMMENT_VOICE_BUTTON_TAG = "comment_voice_button"
const val COMMENT_VOICE_RECORD_BUTTON_TAG = "comment_voice_record_button"
const val COMMENT_VOICE_PREVIEW_TAG = "comment_voice_preview"
const val COMMENT_VOICE_DELETE_TAG = "comment_voice_delete"
const val COMMENT_MENTION_BUTTON_TAG = "comment_mention_button"
const val COMMENT_MENTION_SEARCH_TAG = "comment_mention_search"
const val COMMENT_SYNC_PIN_TAG = "comment_sync_pin"
const val COMMENT_EMOJI_PICKER_TAG = "comment_emoji_picker"
const val COMMENT_EMOJI_TAB_TAG_PREFIX = "comment_emoji_tab_"
const val COMMENT_EMOJI_ITEM_TAG_PREFIX = "comment_emoji_item_"
const val COMMENT_STICKER_ITEM_TAG_PREFIX = "comment_sticker_item_"
const val COMMENT_STICKER_UPLOAD_TAG = "comment_sticker_upload"
const val COMMENT_RECENT_EMOJI_TAG_PREFIX = "comment_recent_emoji_"
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

sealed interface EmojiPanelEntry {
    data object Default : EmojiPanelEntry

    data object Favorites : EmojiPanelEntry

    data class Group(
        val group: CommentStickerGroup,
    ) : EmojiPanelEntry
}

enum class CommentImageMenuAction {
    Open,
    OpenInBrowser,
    Save,
    Share,
}

@Composable
fun SwipeToReplyContainer(
    modifier: Modifier = Modifier,
    onArchive: (() -> Unit)? = null, // 向右滑触发，传 null 则禁向右滑
    onReply: (() -> Unit)? = null, // 向左滑触发，传 null 则禁向左滑
    archiveIcon: ImageVector = Icons.Default.Archive,
    replyIcon: ImageVector = Icons.AutoMirrored.Filled.Reply,
    content: @Composable () -> Unit,
) {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current

    // 触发阈值 (60dp)
    val triggerThreshold = with(density) { 60.dp.toPx() }
    // 最大滑动距离 (100dp)
    val maxDragDistance = with(density) { 100.dp.toPx() }

    var hasVibrated by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        // --- 背景层 (图标) ---
        // 只有在发生位移时才计算显示逻辑
        if (offsetX.value != 0f) {
            val isRightSwipe = offsetX.value > 0

            // 计算进度 (0.0 ~ 1.0)
            val progress = (abs(offsetX.value) / triggerThreshold).coerceIn(0f, 1f)

            val iconScale = 0.5f + (0.5f * progress)

            // 决定背景颜色和对齐方式
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

        // --- 前景层 (内容) ---
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            val current = offsetX.value
                            // 加上阻尼系数 0.5
                            val target = current + delta * 0.5f

                            // --- 核心逻辑：判断是否允许滑动 ---
                            val isTryingToSwipeRight = target > 0
                            val isTryingToSwipeLeft = target < 0

                            val canSwipeRight = onArchive != null
                            val canSwipeLeft = onReply != null

                            // 如果试图向右滑但 onReply 为空 -> 强制为 0 (或保持在非正数)
                            // 如果试图向左滑但 onArchive 为空 -> 强制为 0 (或保持在非负数)
                            var newOffset = target

                            if (isTryingToSwipeRight && !canSwipeRight) {
                                newOffset = 0f
                            }
                            if (isTryingToSwipeLeft && !canSwipeLeft) {
                                newOffset = 0f
                            }

                            // 限制最大滑动距离 (正负方向)
                            if (newOffset > maxDragDistance) newOffset = maxDragDistance
                            if (newOffset < -maxDragDistance) newOffset = -maxDragDistance

                            // 应用位移
                            offsetX.snapTo(newOffset)

                            // --- 震动反馈逻辑 ---
                            // 绝对值超过阈值
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

                        // 判断触发逻辑
                        if (currentVal >= triggerThreshold && onArchive != null) {
                            onArchive()
                        } else if (currentVal <= -triggerThreshold && onReply != null) {
                            onReply()
                        }

                        // 无论如何回弹归零
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

private data class CommentImageItem(
    val url: String,
    val width: Int,
    val height: Int,
)

/**
 * 与知乎 RN 评论区 `ImageBlock` 一致：
 * - 宽高优先取 `data-width` / `data-height`，缺省 120；
 * - 单边限制在 120..200；
 * - 宽高比限制在 0.5..2；
 * - 普通图片 `Crop`，贴纸 `Fit`。
 */
internal fun commentImageDisplaySize(width: Int, height: Int): Pair<Dp, Dp> {
    val w = width.takeIf { it > 0 }?.toFloat() ?: 120f
    val h = height.takeIf { it > 0 }?.toFloat() ?: 120f
    val clampedWidth = w.coerceIn(120f, 200f)
    val clampedHeight = h.coerceIn(120f, 200f)
    val ratio = (w / h).coerceIn(0.5f, 2f)
    return if (ratio > 1f) {
        (clampedHeight * ratio).dp to (clampedWidth / ratio).dp
    } else {
        clampedWidth.dp to clampedHeight.dp
    }
}

@Composable
private fun CommentImageGrid(
    images: List<CommentImageItem>,
    modifier: Modifier = Modifier,
    onAction: ((CommentImageMenuAction, String) -> Unit)? = null,
) {
    if (images.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        images.forEach { image ->
            val (width, height) = commentImageDisplaySize(image.width, image.height)
            ClickableImageWithMenu(
                imageUrl = image.url,
                modifier = Modifier
                    .width(width)
                    .height(height)
                    .clip(RoundedCornerShape(6.dp)),
                contentDescription = "评论图片",
                onAction = onAction,
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/**
 * 可点击的图片组件，支持点击查看和长按显示菜单
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClickableImageWithMenu(
    imageUrl: String,
    modifier: Modifier = Modifier,
    contentDescription: String = "图片",
    onAction: ((CommentImageMenuAction, String) -> Unit)? = null,
    contentScale: ContentScale = ContentScale.Fit,
) {
    var showContextMenu by remember { mutableStateOf(false) }
    val openImagePreview = rememberImagePreviewOpener()
    val openExternalUrl = rememberExternalUrlOpener()
    val saveImage = rememberImageSaver()
    val shareImage = rememberImageSharer()

    PlatformBackHandler(enabled = showContextMenu) {
        showContextMenu = false
    }

    fun handleAction(action: CommentImageMenuAction) {
        if (onAction != null) {
            onAction(action, imageUrl)
            return
        }
        when (action) {
            CommentImageMenuAction.Open -> openImagePreview(imageUrl)
            CommentImageMenuAction.OpenInBrowser -> openExternalUrl(imageUrl)
            CommentImageMenuAction.Save -> saveImage(imageUrl)
            CommentImageMenuAction.Share -> shareImage(imageUrl)
        }
    }

    Box(
        modifier = modifier.combinedClickable(
            onClick = { handleAction(CommentImageMenuAction.Open) },
            onLongClick = { showContextMenu = true },
        ),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
        )

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
        ) {
            DropdownMenuItem(
                modifier = Modifier.testTag(COMMENT_IMAGE_MENU_OPEN_TAG),
                text = { Text("查看图片") },
                onClick = {
                    handleAction(CommentImageMenuAction.Open)
                    showContextMenu = false
                },
            )
            DropdownMenuItem(
                modifier = Modifier.testTag(COMMENT_IMAGE_MENU_BROWSER_TAG),
                text = { Text("在浏览器中打开") },
                onClick = {
                    handleAction(CommentImageMenuAction.OpenInBrowser)
                    showContextMenu = false
                },
            )
            DropdownMenuItem(
                modifier = Modifier.testTag(COMMENT_IMAGE_MENU_SAVE_TAG),
                text = { Text("保存图片") },
                onClick = {
                    handleAction(CommentImageMenuAction.Save)
                    showContextMenu = false
                },
            )
            DropdownMenuItem(
                modifier = Modifier.testTag(COMMENT_IMAGE_MENU_SHARE_TAG),
                text = { Text("分享图片") },
                onClick = {
                    showContextMenu = false
                    handleAction(CommentImageMenuAction.Share)
                },
            )
        }
    }
}

/**
 * 评论列表与回复页面。
 *
 * 页面根据 [content] 指向的文章、问题、想法或片段选择对应评论 ViewModel，展示父评论和子评论层级，并提供发送回复、图片预览、
 * 长按图片菜单等交互。因为评论页经常以底部弹窗形式被其他页面嵌入，布局和状态不能假设自己拥有完整 NavHost。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentScreen(
    content: () -> NavDestination,
    initialCommentId: String? = null,
    activeCommentItem: CommentModel? = null,
    onChildCommentClick: (CommentModel) -> Unit,
    commentInput: String,
    onCommentInputChange: (String) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    initialComment: DataHolder.Comment? = null,
    onInitialChildCommentResolved: (CommentModel, DataHolder.Comment) -> Unit = { _, _ -> },
    pageTurnEnabled: Boolean = false,
    showPageTurnFab: Boolean = false,
) {
    val paginationEnvironment = rememberPaginationEnvironment(allowGuestAccess = false)
    val readingSettings = rememberSettingsStore()
    val initialReadingCommentOrder = remember(readingSettings) {
        loadReadingPreferences(readingSettings).commentOrder
    }
    val resolvedContent = content()
    var isSending by remember { mutableStateOf(false) }
    var replyToComment by remember { mutableStateOf<CommentModel?>(null) }
    var showEmojiPicker by remember { mutableStateOf(true) }
    var commentPendingDeletion by remember { mutableStateOf<CommentModel?>(null) }
    var isDeletingComment by remember { mutableStateOf(false) }
    var deleteCommentError by remember { mutableStateOf<String?>(null) }
    var isCommentInputFocused by remember { mutableStateOf(false) }
    val pageTurnActive = pageTurnEnabled &&
        !showEmojiPicker &&
        !isCommentInputFocused &&
        commentPendingDeletion == null
    val pageTurnTarget = rememberPageTurnTarget(
        listState = listState,
        enabled = pageTurnActive,
    )
    val initialTargetId = initialCommentId ?: initialComment?.id
    val viewModelKey = resolvedContent.commentThreadKey() + initialTargetId?.let { ":initial:$it" }.orEmpty()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val commentInputFocusRequester = remember { FocusRequester() }
    var commentFieldValue by remember(resolvedContent) {
        mutableStateOf(
            TextFieldValue(
                text = commentInput,
                selection = TextRange(commentInput.length),
            ),
        )
    }
    val commentEmojis = rememberCommentEmojis()
    val emojiInlineContent = rememberCommentEmojiInlineContent(
        remember(commentEmojis) { commentEmojis.mapTo(mutableSetOf(), CommentEmoji::inlineKey) },
    )

    LaunchedEffect(commentInput) {
        if (commentInput != commentFieldValue.text) {
            commentFieldValue = TextFieldValue(
                text = commentInput,
                selection = TextRange(commentInput.length),
            )
        }
    }

    PlatformBackHandler(enabled = showEmojiPicker) {
        showEmojiPicker = false
    }

    // 根据内容类型选择合适的ViewModel
    val viewModel: BaseCommentViewModel = when (resolvedContent) {
        is CommentHolder -> remember(viewModelKey) {
            // 子评论不进行状态保存
            ChildCommentViewModel(resolvedContent, initialComment)
        }

        else -> viewModel(key = viewModelKey) {
            RootCommentViewModel(resolvedContent, initialCommentId).apply {
                sortOrder = when (initialReadingCommentOrder) {
                    ReadingCommentOrder.Score -> CommentSortOrder.SCORE
                    ReadingCommentOrder.Time -> CommentSortOrder.TIME
                }
            }
        }
    }
    val restoredListPosition = remember(resolvedContent) {
        listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
    }
    var restoredListPositionApplied by remember(resolvedContent) {
        mutableStateOf(restoredListPosition.first == 0 && restoredListPosition.second == 0)
    }
    LaunchedEffect(viewModel.allData.size) {
        if (!restoredListPositionApplied && viewModel.allData.isNotEmpty()) {
            // 子评论 ViewModel 异步重建且列表仍为空时，恢复的 LazyListState 会先被压回顶部；
            // 等回复数据到达后必须重新应用原位置。
            listState.scrollToItem(
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
    val commentBackgroundColor = MaterialTheme.colorScheme.background
    val commentInputBarColor = MaterialTheme.colorScheme.surfaceContainer
    val actionChipColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val actionChipIconColor = MaterialTheme.colorScheme.onSurfaceVariant

    commentPendingDeletion?.let { target ->
        AlertDialog(
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
                TextButton(
                    modifier = Modifier.testTag(COMMENT_DELETE_CONFIRM_TAG),
                    enabled = !isDeletingComment,
                    onClick = {
                        isDeletingComment = true
                        deleteCommentError = null
                        viewModel.deleteComment(
                            commentData = target.item,
                            environment = paginationEnvironment,
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
                TextButton(
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

    // 监控滚动位置以实现加载更多
    val loadMore = remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItemsCount = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0

            lastVisibleItemIndex >= totalItemsCount - 3 && !viewModel.isLoading && !viewModel.isEnd
        }
    }

    // 监控滚动加载更多
    LaunchedEffect(loadMore.value) {
        if (loadMore.value && viewModel.errorMessage == null) {
            viewModel.loadMore(paginationEnvironment)
        }
    }

    // 初始加载评论
    LaunchedEffect(resolvedContent) {
        if (viewModel.article.commentThreadKey() != resolvedContent.commentThreadKey()) {
            error("Internal Error: Detected content mismatch")
        }
        if (viewModel.errorMessage == null) {
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
                        ZhihuImageUploadSource.Article,
                    )
                    pendingImages = pendingImages + CommentImageAttachment(
                        url = uploaded.url,
                        width = uploaded.rawWidth,
                        height = uploaded.rawHeight,
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
    var recentStickers by remember { mutableStateOf(loadRecentCommentStickers(readingSettings)) }

    fun addPendingSticker(sticker: CommentSticker) {
        pendingStickers = pendingStickers + sticker
        recordRecentCommentSticker(readingSettings, sticker)
        recentStickers = loadRecentCommentStickers(readingSettings)
    }

    val launchStickerPicker = if (isImagePickerSupported) {
        rememberImagePickerLauncher { picked ->
            if (isUploadingSticker) return@rememberImagePickerLauncher
            isUploadingSticker = true
            coroutineScope.launch {
                try {
                    val uploaded = uploadZhihuImage(
                        paginationEnvironment,
                        picked.bytes,
                        picked.mimeType,
                        picked.fileName,
                        ZhihuImageUploadSource.Article,
                    )
                    val token = uploaded.imageId
                        ?: extractZhihuImageHash(uploaded.url)
                        ?: throw IllegalStateException("未拿到图片 token")
                    val uploadedStickers = uploadCommentSticker(paginationEnvironment, token)
                    collectedStickers = (uploadedStickers + collectedStickers).distinctBy { it.id }
                    emojiEntry = EmojiPanelEntry.Favorites
                    userMessages.showShortMessage("表情已上传")
                } catch (e: CancellationException) {
                    throw e
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
            // 分组加载失败时保留空面板。
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
                    // 收藏/推荐加载失败时保留空面板，用户仍可上传。
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
                    } finally {
                        emojiPanelLoading = false
                    }
                }
            }
        }
    }

    val voiceRecorder = rememberVoiceCommentRecorder()
    val voicePreviewPlayer = rememberVoiceCommentPlayer()
    var recordedVoice by remember(resolvedContent) { mutableStateOf<RecordedVoiceComment?>(null) }
    var uploadedVoiceId by remember(resolvedContent) { mutableStateOf<String?>(null) }
    var isUploadingVoice by remember { mutableStateOf(false) }
    var recordingElapsedMs by remember { mutableStateOf(0L) }
    var voiceErrorMessage by remember { mutableStateOf<String?>(null) }
    var isVoiceScreenActive by remember { mutableStateOf(true) }

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
        // 录完先本地回放，服务端转写结果会随评论列表返回。
        voicePreviewPlayer.toggle(recorded.filePath)

        val bytes = voiceRecorder.readBytes(recorded)
        if (bytes == null) {
            voiceErrorMessage = "读取录音失败"
            return
        }
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
                voiceErrorMessage = "语音上传失败：${e.message}"
            } finally {
                isUploadingVoice = false
            }
        }
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
        } finally {
            mentionLoading = false
        }
    }

    fun insertMention(user: MentionCandidate) {
        val text = commentFieldValue.text
        val start = commentFieldValue.selection.start.coerceIn(0, text.length)
        val end = commentFieldValue.selection.end.coerceIn(start, text.length)
        val insert = "@${user.name} "
        val newText = text.substring(0, start) + insert + text.substring(end)
        commentFieldValue = TextFieldValue(newText, TextRange(start + insert.length))
        onCommentInputChange(newText)
        mentions = mentions + CommentMention(user.id, user.name, user.urlToken)
        showMentionPicker = false
    }

    if (showMentionPicker) {
        AlertDialog(
            onDismissRequest = { showMentionPicker = false },
            title = { Text("提及用户") },
            text = {
                Column {
                    OutlinedTextField(
                        value = mentionQuery,
                        onValueChange = { mentionQuery = it },
                        singleLine = true,
                        placeholder = { Text("搜索用户") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(COMMENT_MENTION_SEARCH_TAG),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    when {
                        mentionLoading -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
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
                TextButton(onClick = { showMentionPicker = false }) { Text("关闭") }
            },
        )
    }

    // 提交评论函数
    fun submitComment() {
        if (
            (
                commentFieldValue.text.isBlank() &&
                    pendingImages.isEmpty() &&
                    pendingStickers.isEmpty() &&
                    uploadedVoiceId == null
            ) ||
            isSending ||
            isUploadingVoice
        ) {
            return
        }

        isSending = true
        viewModel.submitComment(
            content = content(),
            commentText = commentFieldValue.text,
            environment = paginationEnvironment,
            replyToCommentId = replyToComment?.item?.id,
            images = pendingImages,
            stickers = pendingStickers,
            mentions = mentions,
            syncToPin = syncToPin,
            voiceId = uploadedVoiceId,
        ) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            commentFieldValue = TextFieldValue("")
            onCommentInputChange("")
            pendingImages = emptyList()
            pendingStickers = emptyList()
            mentions = emptyList()
            syncToPin = false
            replyToComment = null
            showEmojiPicker = false
            discardRecordedVoice()
            isSending = false
            coroutineScope.launch {
                listState.animateScrollToItem(
                    0,
                    0,
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics { paneTitle = "评论" },
    ) {
        // 评论内容区域
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
                Box(modifier = Modifier.weight(1f)) {
                    when {
                        viewModel.isLoading && viewModel.allData.isEmpty() -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }

                        viewModel.errorMessage != null && viewModel.allData.isEmpty() -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(viewModel.errorMessage!!, color = MaterialTheme.colorScheme.error)
                            }
                        }

                        activeCommentItem == null && viewModel.allData.isEmpty() -> {
                            // activeCommentItem != null 的空态在下面的 LazyColumn 中处理。
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(viewModel.commentClosedMessage ?: "暂无评论")
                            }
                        }

                        else -> {
                            @Composable
                            fun Comment(
                                commentItem: CommentModel,
                                modifier: Modifier = Modifier,
                                allowDelete: Boolean = true,
                                onChildCommentClick: (CommentModel) -> Unit,
                            ) {
                                var isLiked by remember { mutableStateOf(commentItem.item.liked) }
                                var likeCount by remember { mutableIntStateOf(commentItem.item.likeCount) }
                                var isLikeLoading by remember { mutableStateOf(false) }

                                Column(modifier = modifier) {
                                    CommentItem(
                                        comment = commentItem,
                                        isLiked = isLiked,
                                        likeCount = likeCount,
                                        isLikeLoading = isLikeLoading,
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

                                    // 在根评论区时 子评论
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
                                                    val childCommentItem = CommentModel(
                                                        item = childComment,
                                                        clickTarget = null, // 子评论不需要点击跳转
                                                    )
                                                    CommentItem(
                                                        comment = childCommentItem,
                                                        modifier = Modifier.testTag("comment_row_${childComment.id}"),
                                                        isLiked = liked,
                                                        likeCount = likeCount,
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
                                            Button(
                                                onClick = { onChildCommentClick(commentItem) },
                                                modifier = Modifier
                                                    .height(28.dp)
                                                    .testTag("comment_child_button_${commentItem.item.id}"),
                                                shape = RoundedCornerShape(50),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = actionChipColor,
                                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                                ),
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
                                state = listState,
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
                                                        // activeCommentItem 非空时这里展示的是子评论回复列表。
                                                        "暂无回复",
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    item("sorting") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .layout { measurable, constraints ->
                                                    val contentHeight = 32.dp.roundToPx()
                                                    val placeable = measurable.measure(
                                                        constraints.copy(
                                                            minHeight = contentHeight,
                                                            maxHeight = contentHeight,
                                                        ),
                                                    )
                                                    layout(placeable.width, 24.dp.roundToPx()) {
                                                        placeable.placeRelative(0, 0)
                                                    }
                                                },
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.End,
                                        ) {
                                            SuggestionChip(
                                                modifier = Modifier.testTag(COMMENT_SORT_SCORE_TAG),
                                                label = {
                                                    Text(
                                                        "最热",
                                                        color = if (viewModel.sortOrder == CommentSortOrder.SCORE) {
                                                            MaterialTheme.colorScheme.primary
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                        },
                                                        fontWeight = if (viewModel.sortOrder == CommentSortOrder.SCORE) {
                                                            FontWeight.SemiBold
                                                        } else {
                                                            FontWeight.Normal
                                                        },
                                                    )
                                                },
                                                onClick = {
                                                    viewModel.changeSortOrder(CommentSortOrder.SCORE, paginationEnvironment)
                                                    saveReadingPreferences(
                                                        readingSettings,
                                                        loadReadingPreferences(readingSettings).copy(
                                                            commentOrder = ReadingCommentOrder.Score,
                                                        ),
                                                    )
                                                },
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            SuggestionChip(
                                                modifier = Modifier.testTag(COMMENT_SORT_TIME_TAG),
                                                label = {
                                                    Text(
                                                        "最新",
                                                        color = if (viewModel.sortOrder == CommentSortOrder.TIME) {
                                                            MaterialTheme.colorScheme.primary
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                        },
                                                        fontWeight = if (viewModel.sortOrder == CommentSortOrder.TIME) {
                                                            FontWeight.SemiBold
                                                        } else {
                                                            FontWeight.Normal
                                                        },
                                                    )
                                                },
                                                onClick = {
                                                    viewModel.changeSortOrder(CommentSortOrder.TIME, paginationEnvironment)
                                                    saveReadingPreferences(
                                                        readingSettings,
                                                        loadReadingPreferences(readingSettings).copy(
                                                            commentOrder = ReadingCommentOrder.Time,
                                                        ),
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }

                                items(
                                    items = viewModel.allData,
                                    key = { it.id },
                                ) { dto ->
                                    val commentItem = viewModel.createCommentItem(dto, article = rootContent)
                                    SwipeToReplyContainer(
                                        modifier = Modifier.testTag("comment_row_${dto.id}"),
                                        onReply = {
                                            if (activeCommentItem == null) {
                                                if (commentItem.clickTarget != null) {
                                                    onChildCommentClick(commentItem)
                                                }
                                            } else {
                                                // 滑动回复时设置回复目标。
                                                replyToComment = commentItem
                                            }
                                        },
                                    ) {
                                        Comment(
                                            commentItem,
                                            modifier = Modifier.animateItem(
                                                fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                                fadeOutSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                                placementSpec = spring(
                                                    stiffness = Spring.StiffnessMediumLow,
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                ),
                                            ),
                                        ) { _ ->
                                            if (activeCommentItem == null) {
                                                if (commentItem.clickTarget != null) {
                                                    onChildCommentClick(commentItem)
                                                }
                                            } else {
                                                // 滑动回复时设置回复目标。
                                                replyToComment = commentItem
                                            }
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
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 作者关闭评论后没有可发送的内容，不再保留输入栏。
                if (viewModel.commentClosedMessage != null) return@Column
                Surface(
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                    color = commentInputBarColor,
                ) {
                    Column {
                        // 回复目标提示栏。
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
                                    IconButton(
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
                                            model = image.url,
                                            contentDescription = "待发送图片",
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop,
                                        )
                                        IconButton(
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
                                            model = sticker.thumbnailUrl,
                                            contentDescription = sticker.title.ifBlank { "待发送贴纸" },
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Fit,
                                        )
                                        IconButton(
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

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = syncToPin,
                                onCheckedChange = { syncToPin = it },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag(COMMENT_SYNC_PIN_TAG),
                            )
                            Text(
                                text = "同时发布到想法",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        recordedVoice?.let { voice ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(
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
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "语音上传中…",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }

                                    uploadedVoiceId != null -> Text(
                                        text = "语音已就绪",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )

                                    else -> Text(
                                        text = "等待上传",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(
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

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 40.dp, max = 140.dp)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (voiceRecorder.isSupported) {
                                if (voiceRecorder.isRecording) {
                                    IconButton(
                                        onClick = { stopVoiceRecording() },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .testTag(COMMENT_VOICE_RECORD_BUTTON_TAG),
                                    ) {
                                        Icon(
                                            Icons.Filled.Stop,
                                            contentDescription = "停止录音",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                    Text(
                                        text = "${(recordingElapsedMs / 1000).coerceAtLeast(0)}″",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                } else {
                                    IconButton(
                                        onClick = { requestVoicePermission() },
                                        enabled = !isSending && !isUploadingImage && !isUploadingVoice,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .testTag(COMMENT_VOICE_BUTTON_TAG),
                                    ) {
                                        Icon(
                                            Icons.Outlined.Mic,
                                            contentDescription = "录制语音评论",
                                            tint = if (uploadedVoiceId != null) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            if (launchImagePicker != null) {
                                IconButton(
                                    onClick = { launchImagePicker.launch() },
                                    enabled = !isUploadingImage && !isSending,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .testTag(COMMENT_IMAGE_BUTTON_TAG),
                                ) {
                                    if (isUploadingImage) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        Icon(
                                            Icons.Outlined.Image,
                                            contentDescription = "添加图片",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            IconButton(
                                onClick = {
                                    showMentionPicker = true
                                    mentionQuery = ""
                                },
                                enabled = !isSending,
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag(COMMENT_MENTION_BUTTON_TAG),
                            ) {
                                Icon(
                                    Icons.Outlined.AlternateEmail,
                                    contentDescription = "提及用户",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
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

                            if (recentStickers.isNotEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.height(22.dp),
                                ) {
                                    recentStickers.take(5).forEach { sticker ->
                                        AsyncImage(
                                            model = sticker.thumbnailUrl,
                                            contentDescription = "插入 emoji",
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clickable { addPendingSticker(sticker) }
                                                .testTag(COMMENT_RECENT_EMOJI_TAG_PREFIX + sticker.id),
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            BasicTextField(
                                value = commentFieldValue,
                                onValueChange = {
                                    commentFieldValue = it
                                    onCommentInputChange(it.text)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(commentInputFocusRequester)
                                    .onFocusChanged {
                                        isCommentInputFocused = it.isFocused
                                        if (it.isFocused) showEmojiPicker = false
                                    }.testTag(COMMENT_INPUT_TAG),
                                decorationBox = { inner ->
                                    Box {
                                        if (commentFieldValue.text.isEmpty()) {
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
                                textStyle = TextStyle.Default.copy(
                                    fontSize = 16.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                            )

                            IconButton(
                                onClick = { submitComment() },
                                modifier = Modifier
                                    .size(24.dp)
                                    .testTag(COMMENT_SEND_BUTTON_TAG),
                                enabled = !isSending &&
                                    !isUploadingVoice &&
                                    (
                                        commentFieldValue.text.isNotBlank() ||
                                            pendingImages.isNotEmpty() ||
                                            pendingStickers.isNotEmpty() ||
                                            uploadedVoiceId != null
                                    ),
                            ) {
                                if (isSending) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                } else {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.Send,
                                        contentDescription = "发送评论",
                                        tint = if (
                                            commentFieldValue.text.isNotBlank() ||
                                            pendingImages.isNotEmpty() ||
                                            pendingStickers.isNotEmpty() ||
                                            uploadedVoiceId != null
                                        ) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        },
                                    )
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
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
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
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    }
                                } else {
                                    when (val entry = emojiEntry) {
                                        EmojiPanelEntry.Default -> DefaultCommentEmojiGrid(
                                            emojis = commentEmojis,
                                            emojiInlineContent = emojiInlineContent,
                                            onEmojiClick = { emoji ->
                                                val updatedValue = commentFieldValue.replaceSelection(
                                                    insert = emoji.placeholder,
                                                    cursorOffsetInInsert = emoji.placeholder.length,
                                                )
                                                commentFieldValue = updatedValue
                                                onCommentInputChange(updatedValue.text)
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
                                                addPendingSticker(sticker)
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
                                                addPendingSticker(sticker)
                                            },
                                            onRecommendedLongClick = { sticker ->
                                                coroutineScope.launch {
                                                    try {
                                                        collectCommentSticker(paginationEnvironment, sticker.id)
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
                                                addPendingSticker(sticker)
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
    comment: CommentModel,
    modifier: Modifier = Modifier,
    isLiked: Boolean = false,
    likeCount: Int = 0,
    isLikeLoading: Boolean = false,
    toggleLike: () -> Unit = {},
    onChildCommentClick: (CommentModel) -> Unit,
    onImageMenuAction: ((CommentImageMenuAction, String) -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
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
        // 作者信息
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            // 头像
            AsyncImage(
                model = commentData.author.avatarUrl,
                contentDescription = null,
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
                    // 作者名
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
                                .clickable(onClickLabel = "打开个人主页") {
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

                val document = Ksoup.parseBodyFragment(commentData.content)
                val commentImages = remember(commentData.content) {
                    document.select("a.comment_img, a.comment_gif").mapNotNull { element ->
                        val url = element.attr("href").takeIf { it.isNotBlank() }
                            ?: return@mapNotNull null
                        CommentImageItem(
                            url = url,
                            width = element.attr("data-width").toIntOrNull() ?: 0,
                            height = element.attr("data-height").toIntOrNull() ?: 0,
                        )
                    }
                }
                val commentStickers = remember(commentData.content) {
                    document.select("a.comment_sticker").mapNotNull { element ->
                        element
                            .attr("href")
                            .takeIf { it.isNotBlank() }
                            ?.let { CommentImageItem(url = it, width = 0, height = 0) }
                    }
                }
                // 收集所有使用的emoji
                val emojisUsed = remember { mutableSetOf<String>() }
                val openExternalUrl = rememberExternalUrlOpener()
                val string = remember(commentData.content) {
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
                            componentUsed = emojisUsed,
                        )
                    }
                }

                // 创建inlineContent映射
                val inlineContent = rememberCommentEmojiInlineContent(emojisUsed)

                Column {
                    val settings = rememberSettingsStore()
                    val fontSizePercent = remember { settings.getInt(PREF_FONT_SIZE, 100) }
                    val lineHeightPercent = remember { settings.getInt(PREF_LINE_HEIGHT, 160) }
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
                    CommentImageGrid(
                        images = commentImages,
                        modifier = Modifier
                            .testTag("comment_image_${commentData.id}")
                            .padding(top = 8.dp),
                        onAction = onImageMenuAction,
                    )
                    if (commentStickers.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            commentStickers.forEach { sticker ->
                                ClickableImageWithMenu(
                                    imageUrl = sticker.url,
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentDescription = "评论贴纸",
                                    onAction = onImageMenuAction,
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        }
                    }
                    commentData.voiceInfo?.let { voice ->
                        VoiceCommentBubble(voice)
                    }
                }
            }
        }

        // 底部信息栏
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 44.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // 时间
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

            if (onDelete != null) {
                Box {
                    IconButton(
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
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                    ) {
                        DropdownMenuItem(
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
                        DropdownMenuItem(
                            text = { Text("举报") },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Report,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                openSystemUrl(reportUrl(commentData.id, "comment"))
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            // 回复按钮
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

            // 点赞
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .testTag("comment_like_button_${commentData.id}")
                    .clickable(enabled = !isLikeLoading) { toggleLike() }
                    .semantics {
                        contentDescription = if (isLiked) "取消点赞" else "点赞"
                        stateDescription = if (isLiked) "已点赞，$likeCount" else "$likeCount"
                    },
            ) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    if (isLiked) {
                        Icons.Filled.ThumbUp
                    } else {
                        Icons.Outlined.ThumbUp
                    },
                    contentDescription = null,
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
                                TextLinkStyles(style = SpanStyle(color = Color(0xff66CCFF))),
                            ) {
                                resolveContent(href)?.let(onNavigate) ?: openExternalUrl(href)
                            },
                        ) {
                            append(linkText)
                        }
                    }
                }

                else -> node.childNodes().forEach {
                    dfsSimple(it, onNavigate, openExternalUrl, componentUsed)
                }
            }
        }

        is TextNode -> {
            // 服务端返回的纯文本链接也要可点击；已包裹在 <a> 里的链接走上面的分支。
            val text = node.text()
            var cursor = 0
            findPlainUrls(text).forEach { match ->
                if (match.range.first > cursor) {
                    processTextWithEmoji(text.substring(cursor, match.range.first), componentUsed)
                }
                withLink(
                    LinkAnnotation.Clickable(
                        match.url,
                        TextLinkStyles(style = SpanStyle(color = Color(0xff66CCFF))),
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

/**
 * 语音评论：播放/暂停 + 时长；服务端返回 asr_text 时提供“转文字”展开。
 * 自动播放交给调用方在需要时触发，避免列表滚动时同时播放多条。
 */
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
            IconButton(
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
    FilterChip(
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
                    modifier = Modifier.size(16.dp),
                )
            }
        } else {
            null
        },
        trailingIcon = if (isVip) {
            {
                Text(
                    text = "VIP",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            null
        },
        modifier = Modifier.testTag(testTag),
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
        if (showTitle) {
            Text(
                text = sticker.title.ifBlank { "表情" },
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
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
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

private fun extractZhihuImageHash(url: String): String? =
    Regex("""(v2-[0-9a-fA-F]+)""").find(url)?.groupValues?.get(1)

@Composable
private fun VoiceCommentBubble(voice: DataHolder.Comment.CommentVoiceInfo) {
    val player = rememberVoiceCommentPlayer()
    val isPlaying = player.playingUrl == voice.url && voice.url.isNotBlank()
    // 服务端已返回转写时直接展示，用户仍可点击折叠。
    var showTranscription by remember(voice.voiceId) { mutableStateOf(voice.asrText.isNotBlank()) }

    Column(modifier = Modifier.padding(top = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(enabled = player.isSupported && voice.url.isNotBlank()) {
                    player.toggle(voice.url)
                }.padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("comment_voice_${voice.voiceId}"),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "暂停语音" else "播放语音",
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${(voice.durationMs / 1000).coerceAtLeast(0)}″",
                style = MaterialTheme.typography.bodyMedium,
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
