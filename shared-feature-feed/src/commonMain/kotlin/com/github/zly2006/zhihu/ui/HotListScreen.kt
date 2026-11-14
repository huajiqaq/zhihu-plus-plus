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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zly2006.zhihu.data.HotListFeed
import com.github.zly2006.zhihu.platform.UserMessageDuration
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.reading.RegisterReadingQueueSource
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppPageHeader
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.DraggableRefreshButton
import com.github.zly2006.zhihu.ui.components.FeedCard
import com.github.zly2006.zhihu.ui.components.FeedPullToRefresh
import com.github.zly2006.zhihu.ui.components.PaginatedList
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import com.github.zly2006.zhihu.viewmodel.feed.HotListViewModel
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

const val HOT_LIST_LIST_TAG = "hot_list_list"
const val HOT_LIST_REFRESH_BUTTON_TAG = "hot_list_refresh_button"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotListScreen(
    innerPadding: PaddingValues = PaddingValues(0.dp),
    scrollToTopTrigger: Int = 0,
    isActive: Boolean = true,
) {
    val viewModel: HotListViewModel = viewModel { HotListViewModel() }
    val readingQueueSourceId = "hot-list:total"
    if (isActive) {
        RegisterReadingQueueSource(
            sourceId = readingQueueSourceId,
            items = viewModel.displayItems,
        )
    }
    val environment = rememberPaginationEnvironment(viewModel.allowGuestAccess)
    val userMessages = rememberUserMessageSink()
    val settings = rememberSettingsStore()
    val listState = rememberLazyListState()
    val pageTurnTarget = rememberPageTurnTarget(listState = listState, enabled = isActive)
    var cachedScrollToTopTrigger by remember { mutableIntStateOf(scrollToTopTrigger) }

    LaunchedEffect(Unit) {
        if (viewModel.displayItems.isEmpty()) {
            viewModel.refresh(environment)
        }
    }

    LaunchedEffect(scrollToTopTrigger, isActive) {
        val action = topLevelReselectAction(
            triggerDelta = scrollToTopTrigger - cachedScrollToTopTrigger,
            isAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0,
        )
        if (isActive) {
            when (action) {
                TopLevelReselectAction.Refresh -> viewModel.refresh(environment)
                TopLevelReselectAction.ScrollToTop -> listState.animateScrollToItem(0)
                null -> {}
            }
        }
        cachedScrollToTopTrigger = scrollToTopTrigger
    }

    LaunchedEffect(viewModel.errorMessage) {
        viewModel.errorMessage?.let {
            userMessages.showMessage(it, UserMessageDuration.Long)
        }
    }

    val overlayPadding = LocalReadingPlayerOverlayPadding.current
    val skin = LocalAppSkinTokens.current
    val background = if (settingSkinIsMiuix()) skin.surface else MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
        contentAlignment = Alignment.TopCenter,
    ) {
        FeedPullToRefresh(viewModel, environment, padding = PaddingValues(top = innerPadding.calculateTopPadding())) {
            PaginatedList(
                items = viewModel.displayItems,
                listState = listState,
                onLoadMore = { viewModel.loadMore(environment) },
                modifier = Modifier
                    .fillMaxSize()
                    .pageTurnViewportWithGuide(pageTurnTarget)
                    .testTag(HOT_LIST_LIST_TAG),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + overlayPadding + AppDimens.Space8,
                ),
                isEnd = { viewModel.isEnd },
                isLoading = { viewModel.isLoading },
                loadError = { viewModel.errorMessage },
                onRetry = { viewModel.retry(environment) },
                key = { it.stableKey },
                topContent = {
                    item("hot_list_header") {
                        AppPageHeader(
                            title = "热榜",
                            subtitle = "实时热点，按热度排序",
                        )
                    }
                },
            ) { item ->
                FeedCard(
                    item,
                    readingQueueSourceId = readingQueueSourceId.takeIf { isActive },
                    thumbnailUrl = (item.feed as? HotListFeed)?.children?.firstOrNull()?.thumbnail,
                )
            }

            val showRefreshFab = settings.getBoolean("showRefreshFab", true)
            if (showRefreshFab) {
                DraggableRefreshButton(
                    modifier = Modifier.testTag(HOT_LIST_REFRESH_BUTTON_TAG),
                    preferenceName = "hotListRefresh",
                    bottomAvoidance = innerPadding.calculateBottomPadding() + overlayPadding,
                    onClick = { viewModel.refresh(environment) },
                ) {
                    if (viewModel.isLoading) {
                        AppSpinner(size = if (settingSkinIsMiuix()) null else AppDimens.IconSizeLarge)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            }
        }
    }
}
