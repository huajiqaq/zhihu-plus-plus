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

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.ui.components.AppPullToRefreshBox
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.PageTurnTarget
import com.github.zly2006.zhihu.ui.components.RecordedVoiceComment
import com.github.zly2006.zhihu.ui.components.VoiceCommentPlayer
import com.github.zly2006.zhihu.ui.components.pageTurnContentEndMarker
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.viewmodel.CommentItem
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.comment.BaseCommentViewModel

/** 评论列表区：首屏加载、加载失败、空列表和正常列表。 */
@Composable
internal fun CommentScreenList(
    viewModel: BaseCommentViewModel,
    paginationEnvironment: PaginationEnvironment,
    activeCommentItem: CommentItem?,
    sentenceMode: Boolean,
    activeListState: LazyListState,
    pageTurnTarget: PageTurnTarget,
    voicePreviewPlayer: VoiceCommentPlayer,
    submittedVoices: Map<String, RecordedVoiceComment>,
    sentenceQuotes: Map<String, String>,
    rootContent: NavDestination,
    actionChipColor: Color,
    actionChipIconColor: Color,
    onDeleteComment: (CommentItem) -> Unit,
    /** 根评论列表里进入子评论，子评论列表里把这条设为回复对象。 */
    onCommentClick: (CommentItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppPullToRefreshBox(
        isRefreshing = viewModel.isRefreshing,
        onRefresh = { viewModel.refresh(paginationEnvironment) },
        modifier = modifier,
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
                                CommentScreenRow(
                                    commentItem = activeCommentItem,
                                    allowDelete = false,
                                    showChildPreview = false,
                                    voicePreviewPlayer = voicePreviewPlayer,
                                    submittedVoices = submittedVoices,
                                    sentenceQuotes = sentenceQuotes,
                                    viewModel = viewModel,
                                    paginationEnvironment = paginationEnvironment,
                                    actionChipColor = actionChipColor,
                                    actionChipIconColor = actionChipIconColor,
                                    onDeleteComment = onDeleteComment,
                                ) { }
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
                            onReply = { onCommentClick(commentItem) },
                        ) {
                            CommentScreenRow(
                                commentItem = commentItem,
                                showChildPreview = activeCommentItem == null,
                                voicePreviewPlayer = voicePreviewPlayer,
                                submittedVoices = submittedVoices,
                                sentenceQuotes = sentenceQuotes,
                                viewModel = viewModel,
                                paginationEnvironment = paginationEnvironment,
                                actionChipColor = actionChipColor,
                                actionChipIconColor = actionChipIconColor,
                                onDeleteComment = onDeleteComment,
                            ) { _ -> onCommentClick(commentItem) }
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
}
