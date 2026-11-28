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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.github.zly2006.zhihu.data.VoteUpState
import com.github.zly2006.zhihu.i18n.ContentStrings
import com.github.zly2006.zhihu.platform.UserMessageSink
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppFloatingToolbar
import com.github.zly2006.zhihu.ui.components.AppIcon
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppIconToggleButton
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import com.github.zly2006.zhihu.util.formatCompactCount
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel
import org.jetbrains.compose.resources.painterResource
import zhihu.uicomponents.generated.resources.Res
import zhihu.uicomponents.generated.resources.ic_vote_down_24dp
import zhihu.uicomponents.generated.resources.ic_vote_up_24dp

@Composable
internal fun ArticleActionBar(
    viewModel: ArticleViewModel,
    onVote: ((VoteUpState) -> Unit)?,
    userMessages: UserMessageSink,
    onShowComments: () -> Unit,
    onShowCollectionDialog: () -> Unit,
) {
    val tokens = LocalAppSkinTokens.current
    val actionContentColor = if (settingSkinIsMiuix()) tokens.onSurface else tokens.onSurfaceVariantSummary
    val ttsState = rememberArticleTtsState()
    val toggleArticleSpeech = rememberArticleSpeechToggler()
    Column(
        modifier = Modifier
            .padding(bottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + AppDimens.Space16)
            .fillMaxWidth()
            .padding(horizontal = AppDimens.PageHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(
            visible = ttsState.isSpeaking,
            modifier = Modifier.align(Alignment.End).padding(bottom = AppDimens.Space8),
        ) {
            AppFloatingToolbar {
                AppIconButton(
                    onClick = {
                        toggleArticleSpeech("", "")
                        userMessages.showMessage(ContentStrings.readingStopped)
                    },
                    enabled = ttsState !in listOf(TtsState.Error, TtsState.Uninitialized, TtsState.Initializing),
                    modifier = Modifier.size(AppDimens.TouchTarget).testTag("article_stop_tts"),
                ) {
                    AppIcon(Icons.AutoMirrored.Filled.VolumeOff, contentDescription = ContentStrings.stopReading)
                }
            }
        }
        AppFloatingToolbar(modifier = Modifier.testTag("article_action_dock")) {
            AppButton(
                enabled = onVote != null,
                onClick = {
                    onVote?.invoke(if (viewModel.voteUpState == VoteUpState.Up) VoteUpState.Neutral else VoteUpState.Up)
                },
                modifier = Modifier
                    .widthIn(min = AppDimens.TouchTarget)
                    .height(AppDimens.TouchTarget)
                    .testTag("article_vote_up")
                    .semantics {
                        stateDescription = if (viewModel.voteUpState == VoteUpState.Up) ContentStrings.upvoted else ContentStrings.notUpvoted
                    },
                containerColor = if (viewModel.voteUpState == VoteUpState.Up) tokens.primary else Color.Transparent,
                contentColor = if (viewModel.voteUpState == VoteUpState.Up) tokens.onPrimary else actionContentColor,
                contentPadding = PaddingValues(horizontal = AppDimens.Space8),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_vote_up_24dp),
                    contentDescription = if (onVote == null) ContentStrings.upvoteReadOnly else ContentStrings.upvote,
                    modifier = Modifier.size(AppDimens.IconSizeMedium),
                )
                Spacer(Modifier.width(AppDimens.Space4))
                Text(formatCompactCount(viewModel.voteUpCount), style = tokens.footnote1, maxLines = 1)
            }
            AppTextButton(
                onClick = onShowComments,
                modifier = Modifier
                    .widthIn(min = AppDimens.TouchTarget)
                    .height(AppDimens.TouchTarget)
                    .testTag("article_comments"),
                contentColor = actionContentColor,
                contentPadding = PaddingValues(horizontal = AppDimens.Space8),
            ) {
                AppIcon(
                    Icons.AutoMirrored.Filled.Comment,
                    contentDescription = ContentStrings.comments,
                    modifier = Modifier.size(AppDimens.IconSizeMedium),
                )
                Spacer(Modifier.width(AppDimens.Space4))
                Text(formatCompactCount(viewModel.commentCount), style = tokens.footnote1, maxLines = 1)
            }
            AppIconButton(
                onClick = onShowCollectionDialog,
                modifier = Modifier
                    .size(AppDimens.TouchTarget)
                    .testTag("article_collection")
                    .semantics {
                        stateDescription = if (viewModel.isFavorited) ContentStrings.saved else ContentStrings.notSaved
                    },
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = if (viewModel.isFavorited) tokens.primary else actionContentColor,
                ),
            ) {
                AppIcon(
                    if (viewModel.isFavorited) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = ContentStrings.manageCollections,
                    modifier = Modifier.size(AppDimens.IconSizeMedium),
                )
            }
            AppIconToggleButton(
                checked = viewModel.voteUpState == VoteUpState.Down,
                enabled = onVote != null,
                onCheckedChange = { checked ->
                    onVote?.invoke(if (checked) VoteUpState.Down else VoteUpState.Neutral)
                },
                modifier = Modifier
                    .size(AppDimens.TouchTarget)
                    .testTag("article_vote_down")
                    .semantics {
                        stateDescription = if (viewModel.voteUpState == VoteUpState.Down) ContentStrings.downvoted else ContentStrings.notDownvoted
                    },
                colors = IconButtonDefaults.iconToggleButtonColors(
                    contentColor = actionContentColor,
                    checkedContainerColor = tokens.primary,
                    checkedContentColor = tokens.onPrimary,
                ),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_vote_down_24dp),
                    contentDescription = if (onVote == null) ContentStrings.downvoteReadOnly else ContentStrings.downvote,
                    modifier = Modifier.size(AppDimens.IconSizeMedium),
                )
            }
        }
    }
}
