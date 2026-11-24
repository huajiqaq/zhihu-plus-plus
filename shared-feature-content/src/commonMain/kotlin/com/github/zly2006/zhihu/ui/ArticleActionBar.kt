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
import com.github.zly2006.zhihu.platform.UserMessageSink
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppFloatingToolbar
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppIconToggleButton
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import com.github.zly2006.zhihu.util.formatCompactCount
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import org.jetbrains.compose.resources.painterResource
import zhihu.uicomponents.generated.resources.Res
import zhihu.uicomponents.generated.resources.ic_vote_down_24dp
import zhihu.uicomponents.generated.resources.ic_vote_up_24dp
import androidx.compose.material3.Icon as MaterialIcon
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

@Composable
internal fun ArticleActionBar(
    viewModel: ArticleViewModel,
    environment: PaginationEnvironment,
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
                        userMessages.showMessage("已停止朗读")
                    },
                    enabled = ttsState !in listOf(TtsState.Error, TtsState.Uninitialized, TtsState.Initializing),
                    modifier = Modifier.size(AppDimens.TouchTarget).testTag("article_stop_tts"),
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeOff, contentDescription = "停止朗读")
                }
            }
        }
        AppFloatingToolbar(modifier = Modifier.testTag("article_action_dock")) {
            AppButton(
                onClick = {
                    viewModel.toggleVoteUp(
                        environment,
                        if (viewModel.voteUpState == VoteUpState.Up) VoteUpState.Neutral else VoteUpState.Up,
                    )
                },
                modifier = Modifier
                    .widthIn(min = AppDimens.TouchTarget)
                    .height(AppDimens.TouchTarget)
                    .testTag("article_vote_up")
                    .semantics {
                        stateDescription = if (viewModel.voteUpState == VoteUpState.Up) "已赞同" else "未赞同"
                    },
                containerColor = if (viewModel.voteUpState == VoteUpState.Up) tokens.primary else Color.Transparent,
                contentColor = if (viewModel.voteUpState == VoteUpState.Up) tokens.onPrimary else actionContentColor,
                contentPadding = PaddingValues(horizontal = AppDimens.Space8),
            ) {
                MaterialIcon(
                    painter = painterResource(Res.drawable.ic_vote_up_24dp),
                    contentDescription = "赞同",
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
                Icon(
                    Icons.AutoMirrored.Filled.Comment,
                    contentDescription = "评论",
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
                        stateDescription = if (viewModel.isFavorited) "已收藏" else "未收藏"
                    },
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = if (viewModel.isFavorited) tokens.primary else actionContentColor,
                ),
            ) {
                Icon(
                    if (viewModel.isFavorited) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = "管理收藏",
                    modifier = Modifier.size(AppDimens.IconSizeMedium),
                )
            }
            AppIconToggleButton(
                checked = viewModel.voteUpState == VoteUpState.Down,
                onCheckedChange = { checked ->
                    viewModel.toggleVoteUp(environment, if (checked) VoteUpState.Down else VoteUpState.Neutral)
                },
                modifier = Modifier
                    .size(AppDimens.TouchTarget)
                    .testTag("article_vote_down")
                    .semantics {
                        stateDescription = if (viewModel.voteUpState == VoteUpState.Down) "已反对" else "未反对"
                    },
                colors = IconButtonDefaults.iconToggleButtonColors(
                    contentColor = actionContentColor,
                    checkedContainerColor = tokens.primary,
                    checkedContentColor = tokens.onPrimary,
                ),
            ) {
                MaterialIcon(
                    painter = painterResource(Res.drawable.ic_vote_down_24dp),
                    contentDescription = "反对",
                    modifier = Modifier.size(AppDimens.IconSizeMedium),
                )
            }
        }
    }
}
