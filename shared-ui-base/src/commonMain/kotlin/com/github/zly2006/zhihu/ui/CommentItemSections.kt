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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppDropdownMenu
import com.github.zly2006.zhihu.ui.components.AppDropdownMenuItem
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.RecordedVoiceComment
import com.github.zly2006.zhihu.ui.components.VoiceCommentPlayer
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import com.github.zly2006.zhihu.viewmodel.CommentItem
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

@Composable
internal fun CommentItemHeader(
    comment: CommentItem,
    authorPerson: Person,
    quotedSentence: String?,
    onNavigate: (NavDestination) -> Unit,
) {
    val commentData = comment.item
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = commentData.author.name,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier
                .testTag("comment_author_${commentData.id}")
                .clickable(onClickLabel = "打开个人主页") { onNavigate(authorPerson) },
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
                        onNavigate(
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
}

@Composable
internal fun CommentItemText(
    commentId: String,
    string: AnnotatedString,
    inlineContent: Map<String, InlineTextContent>,
    fontSizePercent: Int,
    lineHeightPercent: Int,
    density: Density,
    canExpand: Boolean,
    isFolded: Boolean,
    collapsedTextHeight: Int,
    isContentExpanded: Boolean,
    onTextLayout: (TextLayoutResult) -> Unit,
    onToggleExpanded: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().clipToBounds()) {
        SelectionContainer(
            modifier = Modifier.commentSelectionWorkaround().then(if (canExpand) Modifier.padding(bottom = 32.dp) else Modifier),
        ) {
            Text(
                text = string,
                fontSize = 16.sp * fontSizePercent / 100,
                lineHeight = 16.sp * fontSizePercent / 100 * lineHeightPercent / 100,
                inlineContent = inlineContent,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isFolded && collapsedTextHeight > 0) {
                            Modifier.height(with(density) { collapsedTextHeight.toDp() })
                        } else {
                            Modifier
                        },
                    ),
                onTextLayout = onTextLayout,
            )
        }
        if (isFolded) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(with(density) { collapsedTextHeight.toDp() })
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, if (settingSkinIsMiuix()) LocalAppSkinTokens.current.surface else MaterialTheme.colorScheme.surfaceContainerLow),
                        ),
                    ),
            )
        }
        if (canExpand) {
            AppTextButton(
                text = if (isContentExpanded) "收起" else "展开",
                onClick = onToggleExpanded,
                modifier = Modifier.align(Alignment.BottomStart).testTag("comment_expand_$commentId"),
            )
        }
    }
}

@Composable
internal fun ColumnScope.CommentItemMedia(
    comment: CommentItem,
    commentImages: List<CommentImageItem>,
    commentStickers: List<String>,
    isFolded: Boolean,
    localVoice: RecordedVoiceComment?,
    voicePlayer: VoiceCommentPlayer,
) {
    val commentData = comment.item
    AnimatedVisibility(visible = !isFolded) {
        Column {
            if (commentStickers.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    commentStickers.forEach { stickerUrl ->
                        ClickableImageWithMenu(
                            imageUrl = stickerUrl,
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentDescription = "评论贴纸",
                        )
                    }
                }
            }
            CommentImageGrid(
                images = commentImages,
                modifier = Modifier
                    .testTag("comment_image_${commentData.id}")
                    .padding(top = 8.dp),
            )
            commentData.voiceInfo?.let { voice -> VoiceCommentBubble(voice, localVoice, voicePlayer) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CommentItemActions(
    comment: CommentItem,
    showMoreMenu: Boolean,
    onShowMoreMenu: () -> Unit,
    onDismissMoreMenu: () -> Unit,
    onDelete: (() -> Unit)?,
    onReport: () -> Unit,
    isLiked: Boolean,
    likeCount: Int,
    isLikeLoading: Boolean,
    toggleLike: () -> Unit,
    onChildCommentClick: (CommentItem) -> Unit,
) {
    val commentData = comment.item
    Box {
        AppIconButton(
            onClick = onShowMoreMenu,
            modifier = Modifier.size(24.dp).testTag("comment_more_button_${commentData.id}"),
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
            onDismissRequest = onDismissMoreMenu,
            modifier = Modifier.testTag("comment_more_menu_${commentData.id}"),
            renderInRootScaffold = false,
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
                    onClick = onDelete,
                )
            }
            AppDropdownMenuItem(
                text = { Text("举报") },
                leadingIcon = { Icon(Icons.Filled.Report, contentDescription = null) },
                onClick = onReport,
            )
        }
    }
    Spacer(modifier = Modifier.width(8.dp))

    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tokens = LocalAppSkinTokens.current
        val actionContentColor = if (settingSkinIsMiuix()) tokens.onSurface else tokens.onSurfaceVariantActions
        AppButton(
            onClick = toggleLike,
            enabled = !isLikeLoading,
            modifier = Modifier
                .testTag("comment_like_button_${commentData.id}")
                .semantics {
                    contentDescription = if (isLiked) "取消喜欢" else "喜欢"
                    stateDescription = if (isLiked) "已喜欢，$likeCount" else "$likeCount"
                    selected = isLiked
                },
            shape = ButtonGroupDefaults.connectedLeadingButtonShape,
            containerColor = if (isLiked) tokens.primary else tokens.surfaceContainerHigh,
            contentColor = if (isLiked) tokens.onPrimary else actionContentColor,
            contentPadding = PaddingValues(horizontal = AppDimens.Space12),
        ) {
            Icon(
                if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(AppDimens.Space4))
            Text(
                text = likeCount.toString(),
                modifier = Modifier.testTag("comment_like_count_${commentData.id}"),
                style = tokens.footnote2,
            )
        }
        AppButton(
            onClick = { onChildCommentClick(comment) },
            modifier = Modifier.testTag("comment_reply_button_${commentData.id}"),
            shape = ButtonGroupDefaults.connectedTrailingButtonShape,
            containerColor = tokens.surfaceContainerHigh,
            contentColor = actionContentColor,
            contentPadding = PaddingValues(horizontal = AppDimens.Space12),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.Comment,
                contentDescription = "回复",
                modifier = Modifier.size(16.dp),
            )
            if (commentData.childCommentCount > 0) {
                Spacer(Modifier.width(AppDimens.Space4))
                Text(
                    text = commentData.childCommentCount.toString(),
                    modifier = Modifier.testTag("comment_reply_count_${commentData.id}"),
                    style = tokens.footnote2,
                )
            }
        }
    }
}
