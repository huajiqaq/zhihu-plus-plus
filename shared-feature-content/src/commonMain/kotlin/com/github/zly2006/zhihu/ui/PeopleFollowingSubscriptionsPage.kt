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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppOutlinedButton
import com.github.zly2006.zhihu.ui.components.PageTurnTarget
import com.github.zly2006.zhihu.ui.components.PaginatedList
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment

private val PEOPLE_SCREEN_SUBSCRIPTION_TITLES = listOf(
    "我订阅的专栏",
    "关注的话题",
    "关注的问题",
    "关注的收藏夹",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FollowingSubscriptionsPage(
    viewModel: PersonViewModel,
    paginationEnvironment: PaginationEnvironment,
    listState: LazyListState,
    pageTurnTarget: PageTurnTarget,
    modifier: Modifier = Modifier,
) {
    var selectedPage by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(selectedPage) {
        val model = when (selectedPage) {
            0 -> viewModel.followingColumnsFeedModel
            1 -> viewModel.followingTopicsFeedModel
            2 -> viewModel.followingQuestionsFeedModel
            else -> viewModel.followingCollectionsFeedModel
        }
        if (model.allData.isEmpty() && !model.isEnd && model.errorMessage == null) {
            model.loadMore(paginationEnvironment)
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(PEOPLE_SCREEN_SUBSCRIPTION_TABS_TAG)
                .padding(vertical = AppDimens.Space8, horizontal = AppDimens.PageHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Space8),
        ) {
            PEOPLE_SCREEN_SUBSCRIPTION_TITLES.forEachIndexed { index, title ->
                AppOutlinedButton(
                    onClick = { selectedPage = index },
                    modifier = Modifier
                        .testTag("people_screen_subscription_tab_$index")
                        .semantics {
                            selected = selectedPage == index
                            role = Role.Tab
                        },
                    shape = RoundedCornerShape(AppDimens.SmallCornerRadius),
                    containerColor = if (selectedPage == index) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                    contentColor = if (selectedPage == index) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                ) {
                    Text(title)
                }
            }
        }

        when (selectedPage) {
            0 -> PaginatedList(
                items = viewModel.followingColumnsFeedModel.allData,
                listState = listState,
                onLoadMore = { viewModel.followingColumnsFeedModel.loadMore(paginationEnvironment) },
                isEnd = { viewModel.followingColumnsFeedModel.isEnd },
                isLoading = { viewModel.followingColumnsFeedModel.isLoading },
                loadError = { viewModel.followingColumnsFeedModel.errorMessage },
                onRetry = { viewModel.followingColumnsFeedModel.retry(paginationEnvironment) },
                modifier = Modifier
                    .fillMaxSize()
                    .pageTurnViewportWithGuide(pageTurnTarget)
                    .testTag(PEOPLE_SCREEN_SUBSCRIPTIONS_LIST_TAG),
                key = { it.id },
            ) { column ->
                ColumnListItem(
                    column = column,
                    itemTag = "people_screen_column_item_${column.id}",
                )
            }

            1 -> PaginatedList(
                items = viewModel.followingTopicsFeedModel.allData,
                listState = listState,
                onLoadMore = { viewModel.followingTopicsFeedModel.loadMore(paginationEnvironment) },
                isEnd = { viewModel.followingTopicsFeedModel.isEnd },
                isLoading = { viewModel.followingTopicsFeedModel.isLoading },
                loadError = { viewModel.followingTopicsFeedModel.errorMessage },
                onRetry = { viewModel.followingTopicsFeedModel.retry(paginationEnvironment) },
                modifier = Modifier
                    .fillMaxSize()
                    .pageTurnViewportWithGuide(pageTurnTarget)
                    .testTag(PEOPLE_SCREEN_SUBSCRIPTIONS_LIST_TAG),
                key = { it.displayId },
            ) { topic ->
                FollowedTopicListItem(topic)
            }

            2 -> PaginatedList(
                items = viewModel.followingQuestionsFeedModel.allData,
                listState = listState,
                onLoadMore = { viewModel.followingQuestionsFeedModel.loadMore(paginationEnvironment) },
                isEnd = { viewModel.followingQuestionsFeedModel.isEnd },
                isLoading = { viewModel.followingQuestionsFeedModel.isLoading },
                loadError = { viewModel.followingQuestionsFeedModel.errorMessage },
                onRetry = { viewModel.followingQuestionsFeedModel.retry(paginationEnvironment) },
                modifier = Modifier
                    .fillMaxSize()
                    .pageTurnViewportWithGuide(pageTurnTarget)
                    .testTag(PEOPLE_SCREEN_SUBSCRIPTIONS_LIST_TAG),
                key = { it.id },
            ) { question ->
                FollowedQuestionListItem(question)
            }

            3 -> PaginatedList(
                items = viewModel.followingCollectionsFeedModel.allData,
                listState = listState,
                onLoadMore = { viewModel.followingCollectionsFeedModel.loadMore(paginationEnvironment) },
                isEnd = { viewModel.followingCollectionsFeedModel.isEnd },
                isLoading = { viewModel.followingCollectionsFeedModel.isLoading },
                loadError = { viewModel.followingCollectionsFeedModel.errorMessage },
                onRetry = { viewModel.followingCollectionsFeedModel.retry(paginationEnvironment) },
                modifier = Modifier
                    .fillMaxSize()
                    .pageTurnViewportWithGuide(pageTurnTarget)
                    .testTag(PEOPLE_SCREEN_SUBSCRIPTIONS_LIST_TAG),
                key = { it.id },
            ) { collection ->
                CollectionListItem(
                    collection = collection,
                    itemTag = "people_screen_collection_item_${collection.id}",
                )
            }
        }
    }
}
