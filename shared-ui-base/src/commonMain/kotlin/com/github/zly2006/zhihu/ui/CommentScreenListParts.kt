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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zly2006.zhihu.data.CommentSortOrder
import com.github.zly2006.zhihu.theme.AppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppAlertDialog
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppChip
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppDropdownMenuItem
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppSelectionDropdownMenu
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.RecordedVoiceComment
import com.github.zly2006.zhihu.ui.components.VoiceCommentPlayer
import com.github.zly2006.zhihu.viewmodel.CommentItem
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.comment.BaseCommentViewModel
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

@Composable
internal fun CommentScreenHeader(
    commentTitle: String,
    skin: AppSkinTokens,
    viewModel: BaseCommentViewModel,
    activeCommentItem: CommentItem?,
    onDismiss: (() -> Unit)?,
    onSelectSort: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = commentTitle,
                style = skin.title2,
                fontWeight = FontWeight.SemiBold,
                color = skin.onSurface,
                modifier = Modifier.testTag(COMMENT_HEADER_TAG),
            )
            Text(
                text = "已加载${viewModel.allData.size}条",
                style = skin.footnote2,
                color = skin.onSurfaceVariantSummary,
                modifier = Modifier.testTag(COMMENT_LOADED_COUNT_TAG),
            )
        }
        if (activeCommentItem == null && viewModel.supportsSorting) {
            var sortMenuExpanded by remember(viewModel) { mutableStateOf(false) }
            val options = listOf("最热", "最新")
            val selectedIndex = if (viewModel.sortOrder == CommentSortOrder.SCORE) 0 else 1
            val selectSort: (Int) -> Unit = { index ->
                sortMenuExpanded = false
                onSelectSort(index)
            }
            Box {
                AppIconButton(
                    onClick = { sortMenuExpanded = true },
                    modifier = Modifier
                        .size(AppDimens.TouchTarget)
                        .testTag(COMMENT_SORT_BUTTON_TAG)
                        .semantics { stateDescription = options[selectedIndex] },
                ) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "排序", tint = skin.onSurfaceVariantActions)
                }
                AppSelectionDropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false },
                    options = options,
                    selectedIndex = selectedIndex,
                    onSelect = selectSort,
                    renderInRootScaffold = false,
                ) {
                    options.forEachIndexed { index, label ->
                        AppDropdownMenuItem(
                            text = { Text(label) },
                            modifier = Modifier
                                .testTag(if (index == 0) COMMENT_SORT_SCORE_TAG else COMMENT_SORT_TIME_TAG)
                                .semantics { selected = index == selectedIndex },
                            onClick = { selectSort(index) },
                            trailingIcon = if (index == selectedIndex) {
                                { Icon(Icons.Filled.Check, contentDescription = null) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
        if (onDismiss != null) {
            AppIconButton(
                onClick = onDismiss,
                modifier = Modifier.size(AppDimens.TouchTarget).testTag(COMMENT_CLOSE_BUTTON_TAG),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "关闭$commentTitle",
                    tint = skin.onSurfaceVariantActions,
                )
            }
        }
    }
}

@Composable
internal fun CommentScreenSentenceTabs(
    sentenceMode: Boolean,
    onSentenceModeChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppChip(
            selected = !sentenceMode,
            modifier = Modifier.testTag("comment_tab_normal"),
            label = { Text("评论", color = if (!sentenceMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
            onClick = { onSentenceModeChange(false) },
        )
        AppChip(
            selected = sentenceMode,
            modifier = Modifier.testTag("comment_tab_sentence"),
            label = { Text("句子评论", color = if (sentenceMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
            onClick = { onSentenceModeChange(true) },
        )
    }
}

@Composable
internal fun CommentScreenRow(
    commentItem: CommentItem,
    allowDelete: Boolean = true,
    showChildPreview: Boolean,
    voicePreviewPlayer: VoiceCommentPlayer,
    submittedVoices: Map<String, RecordedVoiceComment>,
    sentenceQuotes: Map<String, String>,
    viewModel: BaseCommentViewModel,
    paginationEnvironment: PaginationEnvironment,
    actionChipColor: Color,
    actionChipIconColor: Color,
    onDeleteComment: (CommentItem) -> Unit,
    onChildCommentClick: (CommentItem) -> Unit,
) {
    var isLiked by remember { mutableStateOf(commentItem.item.liked) }
    var likeCount by remember { mutableIntStateOf(commentItem.item.likeCount) }

    Column {
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
                { onDeleteComment(commentItem) }
            } else {
                null
            },
        )

        if (showChildPreview && commentItem.item.childCommentCount > 0) {
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
                                { onDeleteComment(childCommentItem) }
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

@Composable
internal fun CommentScreenDeleteDialog(
    isDeletingComment: Boolean,
    deleteCommentError: String?,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AppAlertDialog(
        modifier = Modifier.testTag(COMMENT_DELETE_DIALOG_TAG),
        onDismissRequest = onDismissRequest,
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
                onClick = onConfirm,
            ) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            AppTextButton(
                modifier = Modifier.testTag(COMMENT_DELETE_CANCEL_TAG),
                enabled = !isDeletingComment,
                onClick = onCancel,
            ) {
                Text("取消")
            }
        },
    )
}
