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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.pinSummaryText
import com.github.zly2006.zhihu.data.toFeedDisplayItemNavDestinationJson
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.reading.RegisterReadingQueueSource
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppDropdownMenuItem
import com.github.zly2006.zhihu.ui.components.AppSelectionDropdownMenu
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.FeedCard
import com.github.zly2006.zhihu.ui.components.PaginatedList
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

private fun DataHolder.Answer.toPeopleAnswerDisplayItem(): FeedDisplayItem {
    val destination = Article(
        type = ArticleType.Answer,
        id = id,
        title = question.title,
        questionId = question.id,
        authorName = author.name,
        authorBio = author.headline,
        avatarSrc = author.avatarUrl,
        excerpt = excerpt,
        authorId = author.id,
        authorUrlToken = author.urlToken,
    )
    return FeedDisplayItem(
        title = question.title,
        summary = excerpt,
        details = "回答 · $voteupCount 赞同 · $commentCount 评论",
        feed = null,
        navDestinationJson = destination.toFeedDisplayItemNavDestinationJson(),
        raw = this,
    )
}

private fun DataHolder.Article.toPeopleArticleDisplayItem(): FeedDisplayItem {
    val destination = Article(
        type = ArticleType.Article,
        id = id,
        title = title,
        authorName = author.name,
        authorBio = author.headline,
        avatarSrc = author.avatarUrl,
        excerpt = excerpt,
        authorId = author.id,
        authorUrlToken = author.urlToken,
    )
    return FeedDisplayItem(
        title = title,
        summary = excerpt,
        details = "文章 · $voteupCount 赞同 · $commentCount 评论",
        feed = null,
        navDestinationJson = destination.toFeedDisplayItemNavDestinationJson(),
        raw = this,
    )
}

private fun DataHolder.Pin.toPeoplePinDisplayItem(): FeedDisplayItem? {
    val pinId = id.toLongOrNull() ?: return null
    return FeedDisplayItem(
        title = excerptTitle.pinSummaryText(),
        summary = null,
        details = "想法 · $likeCount 赞 · $commentCount 评论",
        feed = null,
        navDestinationJson = Pin(id = pinId, authorName = author.name).toFeedDisplayItemNavDestinationJson(),
        raw = this,
    )
}

@Composable
internal fun PeopleScreenRegisterReadingQueueSource(
    page: Int,
    readingQueueSourceId: String?,
    viewModel: PersonViewModel,
) {
    when (page) {
        0 -> RegisterReadingQueueSource(
            sourceId = requireNotNull(readingQueueSourceId),
            items = viewModel.answersFeedModel.allData.map(DataHolder.Answer::toPeopleAnswerDisplayItem),
        )
        1 -> RegisterReadingQueueSource(
            sourceId = requireNotNull(readingQueueSourceId),
            items = viewModel.articlesFeedModel.allData.map(DataHolder.Article::toPeopleArticleDisplayItem),
        )
        5 -> RegisterReadingQueueSource(
            sourceId = requireNotNull(readingQueueSourceId),
            items = viewModel.pinsFeedModel.allData.mapNotNull(DataHolder.Pin::toPeoplePinDisplayItem),
        )
    }
}

@Composable
private fun SortBar(
    currentSort: String,
    onSortChange: (String) -> Unit,
    hotTag: String,
    timeTag: String,
) {
    val skin = LocalAppSkinTokens.current
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space4),
        horizontalArrangement = Arrangement.End,
    ) {
        Box {
            AppTextButton(
                onClick = { expanded = true },
                modifier = Modifier.heightIn(min = AppDimens.TouchTarget).testTag("${hotTag}_control"),
                contentColor = if (settingSkinIsMiuix()) skin.onSurface else skin.onSurfaceVariantActions,
                contentPadding = PaddingValues(horizontal = AppDimens.Space8),
            ) {
                Text(if (currentSort == "voteups") "按热度" else "按时间", style = skin.footnote1)
                Spacer(Modifier.width(AppDimens.Space4))
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(AppDimens.IconSizeSmall))
            }
            AppSelectionDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                options = listOf("按热度", "按时间"),
                selectedIndex = if (currentSort == "voteups") 0 else 1,
                onSelect = { index ->
                    expanded = false
                    val sort = if (index == 0) "voteups" else "created"
                    if (currentSort != sort) onSortChange(sort)
                },
            ) {
                listOf(Triple("按热度", "voteups", hotTag), Triple("按时间", "created", timeTag)).forEach { (label, sort, tag) ->
                    AppDropdownMenuItem(
                        text = { Text(label) },
                        modifier = Modifier.testTag(tag).semantics { selected = currentSort == sort },
                        trailingIcon = if (currentSort == sort) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        onClick = {
                            expanded = false
                            if (currentSort != sort) onSortChange(sort)
                        },
                    )
                }
            }
        }
    }
}

/** 个人主页的一页，页号依次对应回答、文章、动态、收藏、提问、想法、专栏、粉丝、关注、关注订阅。 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun PeopleScreenPage(
    page: Int,
    pagerState: PagerState,
    viewModel: PersonViewModel,
    paginationEnvironment: PaginationEnvironment,
    readingQueueSourceId: String?,
    navigator: Navigator,
) {
    val listState = rememberLazyListState()
    val pageTurnTarget = rememberPageTurnTarget(
        listState = listState,
        enabled = pagerState.currentPage == page,
    )
    when (page) {
        0 -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("people_screen_page_$page"),
            ) {
                PaginatedList(
                    items = viewModel.answersFeedModel.allData,
                    listState = listState,
                    onLoadMore = { viewModel.answersFeedModel.loadMore(paginationEnvironment) },
                    isEnd = { viewModel.answersFeedModel.isEnd },
                    isLoading = { viewModel.answersFeedModel.isLoading },
                    loadError = { viewModel.answersFeedModel.errorMessage },
                    onRetry = { viewModel.answersFeedModel.retry(paginationEnvironment) },
                    modifier = Modifier
                        .fillMaxSize()
                        .pageTurnViewportWithGuide(pageTurnTarget)
                        .testTag(PEOPLE_SCREEN_ANSWERS_LIST_TAG),
                    key = { it.id },
                    topContent = {
                        item(key = PEOPLE_SCREEN_ANSWER_SORT_HOT_TAG) {
                            SortBar(
                                currentSort = viewModel.answersFeedModel.sortBy,
                                onSortChange = { viewModel.answersFeedModel.changeSortBy(it, paginationEnvironment) },
                                hotTag = PEOPLE_SCREEN_ANSWER_SORT_HOT_TAG,
                                timeTag = PEOPLE_SCREEN_ANSWER_SORT_TIME_TAG,
                            )
                        }
                    },
                ) {
                    FeedCard(
                        it.toPeopleAnswerDisplayItem(),
                        readingQueueSourceId = readingQueueSourceId,
                        modifier = Modifier.testTag("people_screen_answer_item_${it.id}"),
                    ) { _, destination ->
                        destination?.let(navigator.onNavigate)
                    }
                }
            }
        }

        1 -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("people_screen_page_$page"),
            ) {
                PaginatedList(
                    items = viewModel.articlesFeedModel.allData,
                    listState = listState,
                    onLoadMore = { viewModel.articlesFeedModel.loadMore(paginationEnvironment) },
                    isEnd = { viewModel.articlesFeedModel.isEnd },
                    isLoading = { viewModel.articlesFeedModel.isLoading },
                    loadError = { viewModel.articlesFeedModel.errorMessage },
                    onRetry = { viewModel.articlesFeedModel.retry(paginationEnvironment) },
                    modifier = Modifier
                        .fillMaxSize()
                        .pageTurnViewportWithGuide(pageTurnTarget)
                        .testTag(PEOPLE_SCREEN_ARTICLES_LIST_TAG),
                    key = { it.id },
                    topContent = {
                        item(key = PEOPLE_SCREEN_ARTICLE_SORT_HOT_TAG) {
                            SortBar(
                                currentSort = viewModel.articlesFeedModel.sortBy,
                                onSortChange = { viewModel.articlesFeedModel.changeSortBy(it, paginationEnvironment) },
                                hotTag = PEOPLE_SCREEN_ARTICLE_SORT_HOT_TAG,
                                timeTag = PEOPLE_SCREEN_ARTICLE_SORT_TIME_TAG,
                            )
                        }
                    },
                ) {
                    FeedCard(
                        it.toPeopleArticleDisplayItem(),
                        readingQueueSourceId = readingQueueSourceId,
                        modifier = Modifier.testTag("people_screen_article_item_${it.id}"),
                    ) { _, destination ->
                        destination?.let(navigator.onNavigate)
                    }
                }
            }
        }

        2 -> {
            PeopleActivityList(
                model = viewModel.activitiesFeedModel,
                environment = paginationEnvironment,
                listState = listState,
                active = pagerState.currentPage == page,
                modifier = Modifier
                    .fillMaxSize()
                    .pageTurnViewportWithGuide(pageTurnTarget)
                    .testTag(PEOPLE_SCREEN_ACTIVITIES_LIST_TAG),
            )
        }

        3 -> {
            PaginatedList(
                items = viewModel.collectionsFeedModel.allData,
                listState = listState,
                onLoadMore = { viewModel.collectionsFeedModel.loadMore(paginationEnvironment) },
                isEnd = { viewModel.collectionsFeedModel.isEnd },
                isLoading = { viewModel.collectionsFeedModel.isLoading },
                loadError = { viewModel.collectionsFeedModel.errorMessage },
                onRetry = { viewModel.collectionsFeedModel.retry(paginationEnvironment) },
                modifier = Modifier
                    .fillMaxSize()
                    .pageTurnViewportWithGuide(pageTurnTarget)
                    .testTag(PEOPLE_SCREEN_COLLECTIONS_LIST_TAG),
                key = { it.id },
            ) { collection ->
                CollectionListItem(
                    collection = collection,
                    itemTag = "people_screen_collection_item_${collection.id}",
                )
            }
        }

        4 -> {
            PaginatedList(
                items = viewModel.questionsFeedModel.allData,
                listState = listState,
                onLoadMore = { viewModel.questionsFeedModel.loadMore(paginationEnvironment) },
                isEnd = { viewModel.questionsFeedModel.isEnd },
                isLoading = { viewModel.questionsFeedModel.isLoading },
                loadError = { viewModel.questionsFeedModel.errorMessage },
                onRetry = { viewModel.questionsFeedModel.retry(paginationEnvironment) },
                modifier = Modifier
                    .fillMaxSize()
                    .pageTurnViewportWithGuide(pageTurnTarget)
                    .testTag(PEOPLE_SCREEN_QUESTIONS_LIST_TAG),
                key = { it.id },
            ) { question ->
                QuestionListItem(
                    question = question,
                    itemTag = "people_screen_question_item_${question.id}",
                )
            }
        }

        5 -> {
            PaginatedList(
                items = viewModel.pinsFeedModel.allData,
                listState = listState,
                onLoadMore = { viewModel.pinsFeedModel.loadMore(paginationEnvironment) },
                isEnd = { viewModel.pinsFeedModel.isEnd },
                isLoading = { viewModel.pinsFeedModel.isLoading },
                loadError = { viewModel.pinsFeedModel.errorMessage },
                onRetry = { viewModel.pinsFeedModel.retry(paginationEnvironment) },
                modifier = Modifier
                    .fillMaxSize()
                    .pageTurnViewportWithGuide(pageTurnTarget)
                    .testTag(PEOPLE_SCREEN_PINS_LIST_TAG),
                key = { it.id },
            ) { pin ->
                PinListItem(
                    pin = pin,
                    itemTag = "people_screen_pin_item_${pin.id}",
                    readingQueueSourceId = readingQueueSourceId,
                )
            }
        }

        6 -> {
            PaginatedList(
                items = viewModel.columnsFeedModel.allData,
                listState = listState,
                onLoadMore = { viewModel.columnsFeedModel.loadMore(paginationEnvironment) },
                isEnd = { viewModel.columnsFeedModel.isEnd },
                isLoading = { viewModel.columnsFeedModel.isLoading },
                loadError = { viewModel.columnsFeedModel.errorMessage },
                onRetry = { viewModel.columnsFeedModel.retry(paginationEnvironment) },
                modifier = Modifier
                    .fillMaxSize()
                    .pageTurnViewportWithGuide(pageTurnTarget)
                    .testTag(PEOPLE_SCREEN_COLUMNS_LIST_TAG),
                key = { it.id },
            ) { column ->
                ColumnListItem(
                    column = column,
                    itemTag = "people_screen_column_item_${column.id}",
                )
            }
        }

        7 -> {
            PaginatedList(
                items = viewModel.followersFeedModel.allData,
                listState = listState,
                onLoadMore = { viewModel.followersFeedModel.loadMore(paginationEnvironment) },
                isEnd = { viewModel.followersFeedModel.isEnd },
                isLoading = { viewModel.followersFeedModel.isLoading },
                loadError = { viewModel.followersFeedModel.errorMessage },
                onRetry = { viewModel.followersFeedModel.retry(paginationEnvironment) },
                modifier = Modifier
                    .fillMaxSize()
                    .pageTurnViewportWithGuide(pageTurnTarget)
                    .testTag(PEOPLE_SCREEN_FOLLOWERS_LIST_TAG),
                key = { it.id },
            ) { people ->
                PeopleListItem(
                    people = people,
                    itemTag = "people_screen_follower_item_${people.id}",
                    actionTag = "people_screen_follower_action_${people.id}",
                )
            }
        }

        8 -> {
            PaginatedList(
                items = viewModel.followingFeedModel.allData,
                listState = listState,
                onLoadMore = { viewModel.followingFeedModel.loadMore(paginationEnvironment) },
                isEnd = { viewModel.followingFeedModel.isEnd },
                isLoading = { viewModel.followingFeedModel.isLoading },
                loadError = { viewModel.followingFeedModel.errorMessage },
                onRetry = { viewModel.followingFeedModel.retry(paginationEnvironment) },
                modifier = Modifier
                    .fillMaxSize()
                    .pageTurnViewportWithGuide(pageTurnTarget)
                    .testTag(PEOPLE_SCREEN_FOLLOWING_LIST_TAG),
                key = { it.id },
            ) { people ->
                PeopleListItem(
                    people = people,
                    itemTag = "people_screen_following_item_${people.id}",
                    actionTag = "people_screen_following_action_${people.id}",
                )
            }
        }

        9 -> {
            FollowingSubscriptionsPage(
                viewModel = viewModel,
                listState = listState,
                pageTurnTarget = pageTurnTarget,
                paginationEnvironment = paginationEnvironment,
                modifier = Modifier.testTag("people_screen_page_$page"),
            )
        }
    }
}
