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

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.platform.UserMessageSink
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppDropdownMenu
import com.github.zly2006.zhihu.ui.components.AppDropdownMenuItem
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppMetadataLoadState
import com.github.zly2006.zhihu.ui.components.AppTabRow
import com.github.zly2006.zhihu.ui.components.AppTopBar
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun CollapsibleHeader(
    collapsedFraction: Float,
    onExpandedHeightChanged: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    SubcomposeLayout(modifier = Modifier.clipToBounds()) { constraints ->
        val placeable = subcompose("header", content).single().measure(
            constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity),
        )
        if (placeable.height > 0) {
            onExpandedHeightChanged(placeable.height)
        }
        val height = (placeable.height * (1f - collapsedFraction)).roundToInt()
        layout(constraints.maxWidth, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            placeable.place(0, height - placeable.height)
        }
    }
}

/** 个人主页顶部：标题栏和更多菜单、可折叠的资料区、分页标签。 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun PeopleScreenTopBar(
    person: Person,
    viewModel: PersonViewModel,
    pagerState: PagerState,
    scrollBehavior: TopAppBarScrollBehavior,
    collapsedFraction: Float,
    searchInTopBar: Boolean,
    onSearch: (() -> Unit)?,
    showActionsMenu: Boolean,
    onShowActionsMenuChange: (Boolean) -> Unit,
    onExpandedHeaderHeightChange: (Int) -> Unit,
    onRequestBlockConfirm: () -> Unit,
    navigator: Navigator,
    paginationEnvironment: PaginationEnvironment,
    coroutineScope: CoroutineScope,
    userMessages: UserMessageSink,
) {
    val skin = LocalAppSkinTokens.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta -> scrollBehavior.state.heightOffset += delta },
                onDragStopped = { velocity ->
                    scrollBehavior.nestedScrollConnection.onPostFling(
                        consumed = Velocity.Zero,
                        available = Velocity(0f, velocity),
                    )
                },
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = AppDimens.ArticleReadingMaxWidth).fillMaxWidth()) {
            AppTopBar(
                title = {
                    Text(
                        text = if (collapsedFraction > 0.5f) viewModel.name.ifBlank { person.name } else "个人主页",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                onBack = navigator.onNavigateBack,
                backTag = PEOPLE_SCREEN_BACK_BUTTON_TAG,
                actions = {
                    if (searchInTopBar && onSearch != null) {
                        AppIconButton(
                            onClick = onSearch,
                            modifier = Modifier.testTag(PEOPLE_SCREEN_SEARCH_BUTTON_TAG),
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "搜索 TA 的创作", tint = skin.onSurfaceVariantActions)
                        }
                    }
                    Box {
                        AppIconButton(
                            onClick = { onShowActionsMenuChange(true) },
                            modifier = Modifier.testTag(PEOPLE_SCREEN_MORE_BUTTON_TAG),
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多选项", tint = skin.onSurfaceVariantActions)
                        }
                        AppDropdownMenu(expanded = showActionsMenu, onDismissRequest = { onShowActionsMenuChange(false) }) {
                            AppDropdownMenuItem(
                                text = { Text(if (viewModel.isBlocking) "取消拉黑" else "拉黑", color = skin.error) },
                                leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = skin.error) },
                                modifier = Modifier.testTag(PEOPLE_SCREEN_BLOCK_BUTTON_TAG),
                                onClick = {
                                    onShowActionsMenuChange(false)
                                    if (viewModel.isBlocking) {
                                        coroutineScope.launch {
                                            try {
                                                viewModel.toggleBlock(paginationEnvironment)
                                                userMessages.showShortMessage("已取消拉黑")
                                            } catch (error: Exception) {
                                                userMessages.showShortMessage("操作失败: ${error.message}")
                                            }
                                        }
                                    } else {
                                        onRequestBlockConfirm()
                                    }
                                },
                            )
                            AppDropdownMenuItem(
                                text = { Text(if (viewModel.isBlockedInRecommendations) "取消屏蔽推荐" else "屏蔽推荐") },
                                leadingIcon = { Icon(Icons.Default.VisibilityOff, contentDescription = null) },
                                modifier = Modifier.testTag(PEOPLE_SCREEN_RECOMMENDATION_BLOCK_BUTTON_TAG),
                                onClick = {
                                    onShowActionsMenuChange(false)
                                    coroutineScope.launch {
                                        try {
                                            viewModel.toggleRecommendationBlock(paginationEnvironment)
                                            userMessages.showShortMessage(if (viewModel.isBlockedInRecommendations) "已屏蔽推荐" else "已取消屏蔽推荐")
                                        } catch (error: CancellationException) {
                                            throw error
                                        } catch (error: Exception) {
                                            userMessages.showShortMessage("操作失败: ${error.message}")
                                        }
                                    }
                                },
                            )
                            AppDropdownMenuItem(
                                text = { Text(if (viewModel.isBlockedAsQuestionAuthor) "取消屏蔽其提问" else "屏蔽其提问") },
                                leadingIcon = { Icon(Icons.Default.QuestionAnswer, contentDescription = null) },
                                modifier = Modifier.testTag(PEOPLE_SCREEN_QUESTION_AUTHOR_BLOCK_BUTTON_TAG),
                                onClick = {
                                    onShowActionsMenuChange(false)
                                    coroutineScope.launch {
                                        try {
                                            viewModel.toggleQuestionAuthorBlock(paginationEnvironment)
                                            userMessages.showShortMessage(if (viewModel.isBlockedAsQuestionAuthor) "已屏蔽其提问" else "已取消屏蔽其提问")
                                        } catch (error: CancellationException) {
                                            throw error
                                        } catch (error: Exception) {
                                            userMessages.showShortMessage("操作失败: ${error.message}")
                                        }
                                    }
                                },
                            )
                        }
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
            CollapsibleHeader(
                collapsedFraction = collapsedFraction,
                onExpandedHeightChanged = onExpandedHeaderHeightChange,
            ) {
                Column {
                    UserInfoHeader(
                        viewModel = viewModel,
                        pagerState = pagerState,
                        modifier = Modifier
                            .padding(horizontal = AppDimens.PageHorizontalPadding)
                            .testTag(PEOPLE_SCREEN_HEADER_TAG),
                        onSearch = onSearch,
                        showSearch = !searchInTopBar,
                        onFollowToggle = {
                            coroutineScope.launch {
                                try {
                                    viewModel.toggleFollow(paginationEnvironment)
                                } catch (e: Exception) {
                                    userMessages.showShortMessage("操作失败: ${e.message}")
                                }
                            }
                        },
                    )
                    AppMetadataLoadState(
                        isLoading = viewModel.isProfileLoading,
                        errorMessage = viewModel.profileErrorMessage,
                        hasContent = viewModel.hasLoadedProfile,
                        onRetry = { coroutineScope.launch { viewModel.load(paginationEnvironment) } },
                        loadingTag = PEOPLE_SCREEN_PROFILE_LOADING_TAG,
                        errorTag = PEOPLE_SCREEN_PROFILE_ERROR_TAG,
                        retryTag = PEOPLE_SCREEN_PROFILE_RETRY_TAG,
                    )
                }
            }
            AppTabRow(
                titles = PEOPLE_SCREEN_TITLES,
                selectedIndex = pagerState.currentPage,
                onSelect = { index ->
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(index)
                    }
                },
                modifier = Modifier.testTag(PEOPLE_SCREEN_TAB_ROW_TAG),
                scrollable = true,
                secondary = true,
                testTags = PEOPLE_SCREEN_TITLES.indices.map { "people_screen_tab_$it" },
            )
        }
    }
}
