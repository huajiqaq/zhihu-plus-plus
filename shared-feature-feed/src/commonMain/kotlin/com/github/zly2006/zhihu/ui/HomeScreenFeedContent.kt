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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.target
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.platform.UserMessageSink
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.FeedAuthorBlockRequest
import com.github.zly2006.zhihu.ui.components.FeedCard
import com.github.zly2006.zhihu.ui.components.FeedPullToRefresh
import com.github.zly2006.zhihu.ui.components.PageTurnTarget
import com.github.zly2006.zhihu.ui.components.PaginatedList
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.feed.BaseFeedViewModel
import com.github.zly2006.zhihu.viewmodel.feed.HomeFeedInteractionViewModel
import com.github.zly2006.zhihu.viewmodel.local.LocalHomeFeedViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreenFeedContent(
    createMenuBlurRadius: Dp,
    pageBackground: Color,
    showTopActions: Boolean,
    avatarUrl: String?,
    unreadCount: Int,
    showUnreadBadge: Boolean,
    onSearchClick: () -> Unit,
    onAccountClick: () -> Unit,
    showAccountBottomSheet: Boolean,
    onAccountDismiss: () -> Unit,
    viewModel: BaseFeedViewModel,
    paginationEnvironment: PaginationEnvironment,
    listState: LazyListState,
    pageTurnTarget: PageTurnTarget,
    onFeedListPositioned: (LayoutCoordinates) -> Unit,
    feedBottomPadding: Dp,
    transactions: List<HomeTransaction>,
    transactionsExpanded: () -> Boolean,
    onTransactionsClick: () -> Unit,
    readingQueueSourceId: String,
    userMessages: UserMessageSink,
    onBlockByKeywords: (String, String?) -> Unit,
    onAuthorBlockRequest: (FeedAuthorBlockRequest) -> Unit,
    localHomeViewModel: LocalHomeFeedViewModel?,
    navigator: Navigator,
) {
    AppScaffold(
        modifier = Modifier
            .fillMaxSize()
            .blur(createMenuBlurRadius),
        containerColor = pageBackground,
        topBar = {
            if (!showTopActions) {
                return@AppScaffold
            }
            HomeScreenTopBar(
                avatarUrl = avatarUrl,
                unreadCount = unreadCount,
                showUnreadBadge = showUnreadBadge,
                onSearchClick = onSearchClick,
                onAccountClick = onAccountClick,
                pageBackground = pageBackground,
            )
        },
    ) { scaffoldPadding ->
        HomeScreenAccountSheet(
            visible = showAccountBottomSheet,
            unreadCount = unreadCount,
            showUnreadBadge = showUnreadBadge,
            onDismiss = onAccountDismiss,
        )

        FeedPullToRefresh(viewModel, paginationEnvironment, padding = PaddingValues(top = scaffoldPadding.calculateTopPadding())) {
            PaginatedList(
                items = viewModel.displayItems,
                listState = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .pageTurnViewportWithGuide(pageTurnTarget)
                    .onGloballyPositioned(onFeedListPositioned)
                    .testTag(HOME_FEED_LIST_TAG),
                contentPadding = PaddingValues(
                    top = scaffoldPadding.calculateTopPadding() + AppDimens.Space8,
                    bottom = feedBottomPadding,
                ),
                onLoadMore = { viewModel.loadMore(paginationEnvironment) },
                isEnd = { viewModel.isEnd },
                isLoading = { viewModel.isLoading },
                loadError = { viewModel.errorMessage },
                onRetry = { viewModel.retry(paginationEnvironment) },
                key = { item -> item.stableKey },
                topContent = {
                    if (transactions.isNotEmpty()) {
                        item("home_transactions") {
                            HomeScreenTransactionsToggle(
                                transactions = transactions,
                                expanded = transactionsExpanded(),
                                onClick = onTransactionsClick,
                            )
                        }
                        if (transactionsExpanded()) {
                            transactions.forEach { transaction ->
                                item(transaction.key) {
                                    HomeTransactionRow(transaction)
                                }
                            }
                        }
                    }
                },
            ) { item ->
                FeedCard(
                    item,
                    readingQueueSourceId = readingQueueSourceId,
                    thumbnailUrl = when (val target = item.feed?.target) {
                        is Feed.AnswerTarget -> target.thumbnail
                        else -> null
                    },
                    menuItems = { dismissMenu ->
                        HomeFeedCardMenuItems(
                            item = item,
                            viewModel = viewModel,
                            paginationEnvironment = paginationEnvironment,
                            userMessages = userMessages,
                            dismissMenu = dismissMenu,
                            onBlockByKeywords = onBlockByKeywords,
                            onAuthorBlockRequest = onAuthorBlockRequest,
                        )
                    },
                ) { clickedItem, destination ->
                    val feed = clickedItem.feed
                    if (feed != null) {
                        (viewModel as? HomeFeedInteractionViewModel)
                            ?.onUiContentClick(paginationEnvironment, feed)
                    } else {
                        localHomeViewModel?.onLocalItemOpened(clickedItem)
                    }
                    if (destination != null) {
                        navigator.onNavigate(destination)
                    }
                }
            }
        }
    }
}
