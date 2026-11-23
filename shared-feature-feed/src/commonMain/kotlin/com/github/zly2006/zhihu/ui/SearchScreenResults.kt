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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.officialBadge
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppDropdownMenuItem
import com.github.zly2006.zhihu.ui.components.AppEmptyState
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.AuthorBadge
import com.github.zly2006.zhihu.ui.components.DraggableRefreshButton
import com.github.zly2006.zhihu.ui.components.FeedAuthorBlockRequest
import com.github.zly2006.zhihu.ui.components.FeedCard
import com.github.zly2006.zhihu.ui.components.FeedPullToRefresh
import com.github.zly2006.zhihu.ui.components.PaginatedList
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import com.github.zly2006.zhihu.util.parseEmphasizedHtmlTextWithTheme
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.feed.SearchEntity
import com.github.zly2006.zhihu.viewmodel.feed.SearchFilters
import com.github.zly2006.zhihu.viewmodel.feed.SearchTab
import com.github.zly2006.zhihu.viewmodel.feed.SearchViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

@Composable
internal fun SearchScreenGeneralResults(
    viewModel: SearchViewModel,
    paginationEnvironment: PaginationEnvironment,
    generalListState: LazyListState,
    filterDraft: SearchFilters?,
    feedAuthorBlockRequest: FeedAuthorBlockRequest?,
    isMemberSearch: Boolean,
    memberSearchName: String,
    readingQueueSourceId: String,
    onFilterClick: () -> Unit,
    onBlockUser: (SearchEntity.Content, () -> Unit) -> Unit,
    onNavigatePerson: (DataHolder.People) -> Unit,
    refreshFab: @Composable () -> Unit,
) {
    val pageTurnTarget = rememberPageTurnTarget(
        listState = generalListState,
        enabled = filterDraft == null && feedAuthorBlockRequest == null,
    )
    FeedPullToRefresh(viewModel, paginationEnvironment) {
        PaginatedList(
            items = viewModel.entities,
            listState = generalListState,
            onLoadMore = { viewModel.loadMore(paginationEnvironment) },
            modifier = Modifier
                .pageTurnViewportWithGuide(pageTurnTarget)
                .testTag("search_general_results"),
            topContent = {
                item(key = "search_filters") {
                    SearchFilterEntry(viewModel.filters, onFilterClick)
                    if (isMemberSearch) {
                        Text(
                            text = "以下结果来自 $memberSearchName 的创作",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space8),
                        )
                    }
                }
            },
            isEnd = { viewModel.isEnd },
            isLoading = { viewModel.isLoading },
            loadError = { viewModel.errorMessage },
            onRetry = { viewModel.retry(paginationEnvironment) },
            contentPadding = androidx.compose.foundation.layout
                .PaddingValues(bottom = LocalReadingPlayerOverlayPadding.current),
            key = SearchEntity::id,
        ) { result ->
            when (result) {
                is SearchEntity.Content -> FeedCard(
                    item = result.item,
                    modifier = Modifier.testTag("search_general_content_${result.id}"),
                    readingQueueSourceId = readingQueueSourceId,
                    menuItems = { dismissMenu ->
                        AppDropdownMenuItem(
                            text = { Text("屏蔽用户") },
                            onClick = { onBlockUser(result, dismissMenu) },
                        )
                    },
                )
                is SearchEntity.Person -> PersonSearchResultRow(result.person) { onNavigatePerson(result.person) }
                is SearchEntity.Topic -> Unit
            }
        }
        refreshFab()
    }
}

@Composable
internal fun SearchScreenEntityResults(
    viewModel: SearchViewModel,
    paginationEnvironment: PaginationEnvironment,
    topicListState: LazyListState,
    peopleListState: LazyListState,
    coroutineScope: CoroutineScope,
    onNavigateTopic: (SearchEntity.Topic) -> Unit,
    onNavigatePerson: (DataHolder.People) -> Unit,
    onFollowFailure: (Throwable) -> Unit,
) {
    val resultListState = if (viewModel.searchTab == SearchTab.Topic) topicListState else peopleListState
    val pageTurnTarget = rememberPageTurnTarget(resultListState, enabled = true)
    FeedPullToRefresh(viewModel, paginationEnvironment) {
        PaginatedList(
            items = viewModel.entities,
            listState = resultListState,
            key = SearchEntity::id,
            onLoadMore = { viewModel.loadMore(paginationEnvironment) },
            isEnd = { viewModel.isEnd },
            isLoading = { viewModel.isLoading },
            loadError = { viewModel.errorMessage },
            onRetry = { viewModel.retry(paginationEnvironment) },
            contentPadding = androidx.compose.foundation.layout
                .PaddingValues(bottom = LocalReadingPlayerOverlayPadding.current),
            modifier = Modifier.fillMaxSize().pageTurnViewportWithGuide(pageTurnTarget),
        ) { result ->
            when (result) {
                is SearchEntity.Topic -> {
                    val topic = result.topic
                    SettingItem(
                        title = { Text(topic.name, style = LocalAppSkinTokens.current.title4) },
                        description = {
                            Column {
                                result.excerpt.takeIf(String::isNotBlank)?.let {
                                    Text(it, style = LocalAppSkinTokens.current.body2, color = LocalAppSkinTokens.current.onSurfaceVariantSummary)
                                }
                                Text(
                                    "${formatTopicCount(result.visitCount.toString())} 浏览 · ${formatTopicCount(result.discussCount.toString())} 讨论",
                                    style = LocalAppSkinTokens.current.footnote2,
                                    color = LocalAppSkinTokens.current.onSurfaceVariantSummary,
                                )
                            }
                        },
                        icon = {
                            AsyncImage(
                                model = topic.avatarUrl,
                                contentDescription = "${topic.name}的话题头像",
                                modifier = Modifier.size(AppDimens.TouchTarget).clip(CircleShape),
                            )
                        },
                        onClick = { onNavigateTopic(result) },
                        endAction = {
                            AppTextButton(
                                text = if (result.isFollowing) "已关注" else "关注",
                                onClick = {
                                    coroutineScope.launch {
                                        viewModel
                                            .setTopicFollowing(paginationEnvironment, topic.id, !result.isFollowing)
                                            .onFailure(onFollowFailure)
                                    }
                                },
                                modifier = Modifier.testTag("search_topic_follow_${topic.id}"),
                                enabled = topic.id !in viewModel.changingTopicIds,
                            )
                        },
                        modifier = Modifier.testTag("search_topic_result_${topic.id}"),
                    )
                }
                is SearchEntity.Person -> PersonSearchResultRow(result.person) { onNavigatePerson(result.person) }
                is SearchEntity.Content -> Unit
            }
        }
    }
}

@Composable
internal fun SearchScreenEmptyResults(
    searchTab: SearchTab,
    generalListState: LazyListState,
    filters: SearchFilters,
    onFilterClick: () -> Unit,
    onModifyKeyword: () -> Unit,
) {
    val emptyContent: @Composable () -> Unit = {
        AppEmptyState(
            title = when (searchTab) {
                SearchTab.General -> "没有找到相关内容"
                SearchTab.People -> "没有找到相关用户"
                SearchTab.Topic -> "没有找到相关话题"
            },
            description = "试试其他关键词或调整筛选条件。",
            modifier = Modifier.fillMaxSize().testTag("search_results_empty"),
            action = {
                AppTextButton("修改关键词", onModifyKeyword)
            },
        )
    }
    if (searchTab == SearchTab.General) {
        LazyColumn(state = generalListState, modifier = Modifier.fillMaxSize().testTag("search_general_results")) {
            item(key = "search_filters") {
                SearchFilterEntry(filters, onFilterClick)
            }
            item { emptyContent() }
        }
    } else {
        emptyContent()
    }
}

@Composable
private fun PersonSearchResultRow(
    person: DataHolder.People,
    onClick: () -> Unit,
) {
    val skin = LocalAppSkinTokens.current
    val name = parseEmphasizedHtmlTextWithTheme(person.name)
    SettingItem(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = skin.title4,
                    modifier = Modifier.weight(1f, fill = false),
                )
                val badge = person.badgeV2.officialBadge()
                if (badge?.isUsefulInList == true) {
                    Spacer(Modifier.width(AppDimens.Space4))
                    AuthorBadge(badge, compact = true)
                }
            }
        },
        description = {
            Column {
                person.headline.takeIf(String::isNotEmpty)?.let {
                    Text(it, style = skin.body2, color = skin.onSurfaceVariantSummary)
                }
                Text(
                    "${person.followerCount} 粉丝 · ${person.answerCount} 回答",
                    style = skin.footnote2,
                    color = skin.onSurfaceVariantSummary,
                )
            }
        },
        icon = {
            AsyncImage(
                model = person.avatarUrl,
                contentDescription = "${name.text}的头像",
                modifier = Modifier.size(AppDimens.TouchTarget).clip(CircleShape),
            )
        },
        onClick = onClick,
        endAction = {},
        modifier = Modifier.testTag("search_people_result_${person.id}"),
    )
}

@Composable
internal fun SearchScreenRefreshFab(
    showRefreshFab: Boolean,
    isLoading: Boolean,
    onRefresh: () -> Unit,
) {
    if (showRefreshFab) {
        DraggableRefreshButton(
            preferenceName = "searchRefresh",
            bottomAvoidance = LocalReadingPlayerOverlayPadding.current,
            onClick = onRefresh,
        ) {
            if (isLoading) {
                AppSpinner(size = if (settingSkinIsMiuix()) null else AppDimens.IconSizeLarge)
            } else {
                Icon(Icons.Default.Refresh, contentDescription = "刷新")
            }
        }
    }
}
