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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppMetadataLoadState
import com.github.zly2006.zhihu.ui.components.AppOutlinedButton
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import com.github.zly2006.zhihu.util.formatCompactCount
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

@Composable
internal fun QuestionOverviewContent(
    title: String,
    questionDetail: DataHolder.Question?,
    followerCount: Int,
    isLoading: Boolean,
    errorMessage: String?,
    isFollowing: Boolean,
    questionId: Long,
    questionContentPlainText: String,
    isExpanded: Boolean,
    currentSort: String,
    items: List<FeedDisplayItem>,
    onShowComments: () -> Unit,
    onRetry: () -> Unit,
    onNavigate: (NavDestination) -> Unit,
    onFollowClick: () -> Unit,
    onWriteAnswerClick: () -> Unit,
    onTopicClick: (DataHolder.Topic) -> Unit,
    onToggleExpanded: () -> Unit,
    onSortChange: (String) -> Unit,
    onSelectAnswer: (FeedDisplayItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space12),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Space12),
    ) {
        QuestionHeaderSection(
            title = title,
            visitCount = questionDetail?.visitCount,
            commentCount = questionDetail?.commentCount,
            followerCount = followerCount.takeIf { questionDetail != null },
            onShowComments = onShowComments,
        )
        AppMetadataLoadState(
            isLoading = isLoading,
            errorMessage = errorMessage,
            hasContent = questionDetail != null,
            onRetry = onRetry,
            loadingTag = QUESTION_DETAIL_LOADING_TAG,
            errorTag = QUESTION_DETAIL_ERROR_TAG,
            retryTag = QUESTION_DETAIL_RETRY_TAG,
        )
        questionDetail
            ?.author
            ?.takeIf {
                !questionDetail.relationship.isAnonymous &&
                    it.id.isNotBlank() &&
                    it.name.isNotBlank()
            }?.let { author ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(QUESTION_AUTHOR_TAG)
                        .clickable {
                            onNavigate(
                                com.github.zly2006.zhihu.navigation.Person(
                                    author.id,
                                    author.urlToken,
                                    author.name,
                                ),
                            )
                        },
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = author.avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(AppDimens.AvatarSizeSmall)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                    Text(
                        text = "提问者 · ${author.name}",
                        style = LocalAppSkinTokens.current.body2,
                        color = LocalAppSkinTokens.current.onSurfaceVariantSummary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        if (questionDetail != null) {
            QuestionPrimaryActions(
                isFollowing = isFollowing,
                onFollowClick = onFollowClick,
                onWriteAnswerClick = onWriteAnswerClick,
            )
            QuestionAnimatedBodyHeader(
                questionId = questionId,
                questionContent = questionDetail.detail,
                questionContentPlainText = questionContentPlainText,
                topics = questionDetail.topics,
                onTopicClick = onTopicClick,
                isExpanded = isExpanded,
                onToggleExpanded = onToggleExpanded,
                answerCount = questionDetail.answerCount,
                currentSort = currentSort,
                onSortChange = onSortChange,
            )
        }
        if (items.isNotEmpty()) {
            Text(
                text = "浏览更多回答",
                style = LocalAppSkinTokens.current.footnote1,
                color = LocalAppSkinTokens.current.onSurfaceVariantSummary,
                modifier = Modifier.semantics { heading() },
            )
            QuestionAnswerStrip(
                items = items.take(8),
                onSelect = onSelectAnswer,
            )
        }
    }
}

@Composable
private fun QuestionHeaderSection(
    title: String,
    visitCount: Int?,
    commentCount: Int?,
    followerCount: Int?,
    onShowComments: () -> Unit,
) {
    val skin = LocalAppSkinTokens.current
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppDimens.Space12)) {
        SelectionContainer(modifier = Modifier.questionSelectionWorkaround()) {
            Text(
                text = title,
                style = if (settingSkinIsMiuix()) skin.title2 else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = skin.onSurface,
                modifier = Modifier.testTag(QUESTION_TITLE_TAG),
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth().testTag(QUESTION_STATS_TAG),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Space8),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            visitCount?.let { StatItem(icon = Icons.Outlined.Visibility, text = "${formatCompactCount(it)} 浏览") }
            followerCount?.let { StatItem(icon = Icons.Outlined.Groups, text = "${formatCompactCount(it)} 关注") }
            AppTextButton(
                onClick = onShowComments,
                contentColor = skin.onSurface,
                contentPadding = PaddingValues(horizontal = AppDimens.Space4),
                modifier = Modifier.testTag(QUESTION_COMMENTS_BUTTON_TAG),
            ) {
                Icon(Icons.AutoMirrored.Filled.Comment, contentDescription = null, modifier = Modifier.size(AppDimens.IconSizeSmall))
                Spacer(Modifier.width(AppDimens.Space4))
                Text(commentCount?.let { "${formatCompactCount(it)} 评论" } ?: "评论", style = skin.footnote1)
            }
        }
    }
}

@Composable
private fun QuestionPrimaryActions(
    isFollowing: Boolean,
    onFollowClick: () -> Unit,
    onWriteAnswerClick: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Space8),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        AppButton(
            onClick = onWriteAnswerClick,
            modifier = Modifier.width(IntrinsicSize.Max).heightIn(min = AppDimens.TouchTarget).testTag(QUESTION_WRITE_ANSWER_BUTTON_TAG),
        ) {
            Icon(Icons.Filled.Edit, contentDescription = null)
            Spacer(Modifier.width(AppDimens.Space8))
            Text("写回答")
        }
        AppOutlinedButton(
            onClick = onFollowClick,
            modifier =
                Modifier.width(IntrinsicSize.Max).heightIn(min = AppDimens.TouchTarget).testTag(QUESTION_FOLLOW_BUTTON_TAG).semantics {
                    selected = isFollowing
                    contentDescription = if (isFollowing) "取消关注" else "关注问题"
                },
            containerColor = if (isFollowing) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                null
            },
            contentColor = if (isFollowing) {
                MaterialTheme.colorScheme.onTertiaryContainer
            } else {
                null
            },
        ) {
            Icon(
                imageVector = if (isFollowing) Icons.Filled.Check else Icons.Filled.Add,
                contentDescription = null,
            )
            Spacer(Modifier.width(8.dp))
            Text(if (isFollowing) "已关注" else "关注问题")
        }
    }
}

@Composable
private fun StatItem(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    val skin = LocalAppSkinTokens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(AppDimens.IconSizeSmall),
            tint = skin.onSurfaceVariantSummary,
        )
        Spacer(Modifier.width(AppDimens.Space4))
        Text(text = text, style = skin.footnote1, color = skin.onSurfaceVariantSummary)
    }
}
