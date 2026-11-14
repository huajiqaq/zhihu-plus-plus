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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.target
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.platform.UserMessageDuration
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.reading.RegisterReadingQueueSource
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppDropdownMenuItem
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.AppTabRow
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.FeedAuthorBlockConfirmDialog
import com.github.zly2006.zhihu.ui.components.FeedAuthorBlockRequest
import com.github.zly2006.zhihu.ui.components.FeedAuthorBlockType
import com.github.zly2006.zhihu.ui.components.FeedCard
import com.github.zly2006.zhihu.ui.components.FeedPullToRefresh
import com.github.zly2006.zhihu.ui.components.NoOpPagerNestedScrollConnection
import com.github.zly2006.zhihu.ui.components.PaginatedList
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.appGlassBlur
import com.github.zly2006.zhihu.ui.components.appGlassContainerColor
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.ui.components.rememberNestedHorizontalPagerConnection
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import com.github.zly2006.zhihu.viewmodel.feed.BaseFeedViewModel
import com.github.zly2006.zhihu.viewmodel.feed.FollowRecommendViewModel
import com.github.zly2006.zhihu.viewmodel.feed.FollowViewModel
import com.github.zly2006.zhihu.viewmodel.feed.RecentMomentsViewModel
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import kotlinx.coroutines.launch

class FollowScreenData : ViewModel() {
    var selectedTabIndex by mutableIntStateOf(0)
}

const val FOLLOW_SCREEN_TAB_ROW_TAG = "follow_screen_tab_row"
const val FOLLOW_SCREEN_PAGER_TAG = "follow_screen_pager"
const val FOLLOWING_USERS_ROW_TAG = "following_users_row"
const val FOLLOW_RECOMMEND_LIST_TAG = "follow_recommend_list"
const val FOLLOW_RECOMMEND_REFRESH_BUTTON_TAG = "follow_recommend_refresh_button"
const val FOLLOW_DYNAMIC_LIST_TAG = "follow_dynamic_list"
const val FOLLOW_DYNAMIC_REFRESH_BUTTON_TAG = "follow_dynamic_refresh_button"

/** A preloaded inner page may own page-turn input only while the outer Follow page is visible. */
internal fun isFollowPageTurnTargetActive(
    followScreenActive: Boolean,
    selectedPage: Int,
    page: Int,
): Boolean = followScreenActive && selectedPage == page

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FollowScreen(
    scrollToTopTrigger: Int = 0,
    innerPadding: PaddingValues,
    parentPagerState: PagerState,
    isActive: Boolean = true,
) {
    val viewModel = viewModel { FollowScreenData() }
    val titles = listOf("推荐", "动态")
    val pagerState = rememberPagerState(
        initialPage = viewModel.selectedTabIndex,
        pageCount = { titles.size },
    )
    val coroutineScope = rememberCoroutineScope()
    val skin = LocalAppSkinTokens.current
    val background = if (settingSkinIsMiuix()) skin.surface else MaterialTheme.colorScheme.background
    val glassTint = if (settingSkinIsMiuix()) {
        LocalAppSkinTokens.current.surfaceContainer.copy(alpha = 0.30f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.30f)
    }
    val overlayPadding = LocalReadingPlayerOverlayPadding.current

    LaunchedEffect(pagerState.currentPage) {
        viewModel.selectedTabIndex = pagerState.currentPage
    }

    AppScaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .appGlassBlur(shape = RectangleShape, tint = glassTint)
                    .background(appGlassContainerColor(background))
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FollowTabRow(
                    pagerState = pagerState,
                    selectedTabIndex = pagerState.currentPage,
                    onTabSelected = { index ->
                        viewModel.selectedTabIndex = index
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    modifier = Modifier,
                )
            }
        },
    ) { scaffoldPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(
                    rememberNestedHorizontalPagerConnection(
                        parentState = parentPagerState,
                        childState = pagerState,
                    ),
                ).testTag(FOLLOW_SCREEN_PAGER_TAG),
            pageNestedScrollConnection = NoOpPagerNestedScrollConnection,
        ) { page ->
            val contentPadding = PaddingValues(
                top = scaffoldPadding.calculateTopPadding() + AppDimens.Space8,
                bottom = maxOf(
                    innerPadding.calculateBottomPadding(),
                    scaffoldPadding.calculateBottomPadding(),
                ) + overlayPadding + AppDimens.Space8,
            )
            FollowFeedScreen(
                recommendation = page == 0,
                scrollToTopTrigger = scrollToTopTrigger,
                isActive = isFollowPageTurnTargetActive(isActive, pagerState.currentPage, page),
                contentPadding = contentPadding,
            )
        }
    }
}

@Composable
private fun FollowTabRow(
    pagerState: PagerState,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val titles = listOf("推荐", "动态")
    val tabs: @Composable () -> Unit = {
        titles.forEachIndexed { index, title ->
            Tab(
                modifier = Modifier.testTag("follow_screen_tab_$index"),
                selected = selectedTabIndex == index,
                onClick = { onTabSelected(index) },
                text = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                },
            )
        }
    }

    if (!settingSkinIsMiuix()) {
        BoxWithConstraints(modifier) {
            val tabWidth = maxWidth / titles.size
            PrimaryTabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.testTag(FOLLOW_SCREEN_TAB_ROW_TAG),
                indicator = {
                    val page = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                        .coerceIn(0f, (titles.size - 1).toFloat())
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier
                            .offset(x = tabWidth * page)
                            .width(tabWidth),
                    )
                },
            ) {
                tabs()
            }
        }
        return
    }

    AppTabRow(
        titles = titles,
        selectedIndex = selectedTabIndex,
        onSelect = onTabSelected,
        modifier = modifier.testTag(FOLLOW_SCREEN_TAB_ROW_TAG),
        testTags = titles.indices.map { "follow_screen_tab_$it" },
    )
}

@Composable
fun FollowingUsersRow(
    model: RecentMomentsViewModel = viewModel { RecentMomentsViewModel() },
    isActive: Boolean = true,
    refreshing: Boolean = false,
) {
    val navigator = LocalNavigator.current
    val environment = rememberPaginationEnvironment(allowGuestAccess = false)
    val skin = LocalAppSkinTokens.current
    LaunchedEffect(isActive, refreshing) {
        if (isActive && model.errorMessage == null && (refreshing || model.users.isEmpty())) model.load(environment)
    }
    Column(Modifier.fillMaxWidth()) {
        SettingItem(
            title = { Text("最近动态", style = skin.footnote1, color = skin.onSurface) },
            description = {
                Text(
                    when {
                        model.users.isNotEmpty() -> "${model.users.size} 位关注用户，点击头像查看动态"
                        model.isLoading -> "正在加载关注动态…"
                        model.errorMessage != null -> "动态暂时不可用，推荐内容仍可浏览"
                        else -> "暂无最近动态，关注用户的新内容会显示在这里"
                    },
                    style = skin.footnote2,
                    color = skin.onSurfaceVariantSummary,
                )
            },
            endAction = {
                if (model.isLoading) {
                    AppSpinner(size = AppDimens.InlineSpinnerSize)
                } else {
                    AppTextButton(
                        text = if (model.errorMessage != null) "重试" else "刷新",
                        onClick = { model.load(environment) },
                        modifier = Modifier.testTag("following_users_retry"),
                    )
                }
            },
            modifier = Modifier.testTag("following_users_status"),
        )
        model.errorMessage?.let { error ->
            Text(
                error,
                style = skin.footnote2,
                color = skin.error,
                modifier = Modifier.padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space4),
            )
        }
        if (model.users.isNotEmpty()) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val itemWidth = (maxWidth / 4).coerceIn(AppDimens.Space80, AppDimens.Space120)
                LazyRow(
                    modifier = Modifier.testTag(FOLLOWING_USERS_ROW_TAG),
                    contentPadding = PaddingValues(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space8),
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12),
                ) {
                    items(model.users, key = { it.actor.id }) { user ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(itemWidth)
                                .testTag("following_users_item_${user.actor.id}")
                                .semantics {
                                    stateDescription = if (user.unreadCount > 0) "${user.unreadCount} 条未读动态" else "动态已读"
                                }.clickable(role = androidx.compose.ui.semantics.Role.Button) {
                                    model.markRead(environment, user.actor.id)
                                    navigator.onNavigate(
                                        Person(user.actor.id, user.actor.urlToken, user.actor.name, jumpTo = "动态"),
                                    )
                                }.padding(vertical = AppDimens.Space4),
                        ) {
                            BadgedBox(badge = { if (user.unreadCount > 0) Badge() }) {
                                AsyncImage(
                                    model = user.actor.avatarUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(AppDimens.Space56).clip(CircleShape),
                                )
                            }
                            Spacer(Modifier.height(AppDimens.Space8))
                            Text(
                                text = user.actor.name,
                                style = skin.footnote2,
                                color = skin.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowFeedScreen(
    recommendation: Boolean,
    scrollToTopTrigger: Int,
    isActive: Boolean,
    contentPadding: PaddingValues,
) {
    val viewModel: BaseFeedViewModel = if (recommendation) {
        viewModel<FollowRecommendViewModel> { FollowRecommendViewModel() }
    } else {
        viewModel<FollowViewModel> { FollowViewModel() }
    }
    val readingQueueSourceId = if (recommendation) "follow:recommend" else "follow:dynamic"
    val listTag = if (recommendation) FOLLOW_RECOMMEND_LIST_TAG else FOLLOW_DYNAMIC_LIST_TAG
    val itemTagPrefix = if (recommendation) "follow_recommend_item_" else "follow_dynamic_item_"
    if (isActive) {
        RegisterReadingQueueSource(
            sourceId = readingQueueSourceId,
            items = viewModel.displayItems,
        )
    }
    val environment = rememberPaginationEnvironment(allowGuestAccess = viewModel.allowGuestAccess)
    val userMessages = rememberUserMessageSink()
    val listState = rememberLazyListState()
    var cachedScrollToTopTrigger by remember { mutableIntStateOf(scrollToTopTrigger) }

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

    LaunchedEffect(Unit) {
        if (viewModel.displayItems.isEmpty()) {
            viewModel.refresh(environment)
        }
    }

    LaunchedEffect(viewModel.errorMessage) {
        viewModel.errorMessage?.let {
            userMessages.showMessage(it, UserMessageDuration.Long)
        }
    }

    var feedAuthorBlockRequest by remember { mutableStateOf<FeedAuthorBlockRequest?>(null) }
    val pageTurnTarget = rememberPageTurnTarget(
        listState = listState,
        enabled = isActive && feedAuthorBlockRequest == null,
    )

    Column {
        FeedPullToRefresh(
            viewModel,
            environment,
            padding = PaddingValues(top = contentPadding.calculateTopPadding()),
        ) {
            PaginatedList(
                items = viewModel.displayItems,
                listState = listState,
                modifier = Modifier
                    .pageTurnViewportWithGuide(pageTurnTarget)
                    .testTag(listTag),
                contentPadding = contentPadding,
                topContent = {
                    if (recommendation) {
                        item { FollowingUsersRow(isActive = isActive, refreshing = viewModel.isLoading) }
                    }
                },
                onLoadMore = { viewModel.loadMore(environment) },
                isEnd = { viewModel.isEnd },
                isLoading = { viewModel.isLoading },
                loadError = { viewModel.errorMessage },
                onRetry = { viewModel.retry(environment) },
            ) { item ->
                FeedCard(
                    item = item,
                    readingQueueSourceId = readingQueueSourceId.takeIf { isActive },
                    modifier = Modifier.testTag("$itemTagPrefix${item.stableKey}"),
                    showSourceLabel = !recommendation,
                    menuItems = { dismissMenu ->
                        AppDropdownMenuItem(
                            text = { Text("屏蔽用户") },
                            onClick = {
                                dismissMenu()
                                viewModel.handleBlockUser(environment, userMessages, item) { authorInfo ->
                                    feedAuthorBlockRequest = FeedAuthorBlockRequest(
                                        FeedAuthorBlockType.CONTENT_AUTHOR,
                                        authorInfo.first,
                                        authorInfo.second,
                                    )
                                }
                            },
                        )
                        val canBlockQuestionAuthor = when (item.feed?.target) {
                            is Feed.AnswerTarget, is Feed.QuestionTarget -> true
                            else -> item.raw is DataHolder.Answer || item.raw is DataHolder.Question
                        }
                        if (canBlockQuestionAuthor) {
                            AppDropdownMenuItem(
                                text = { Text("屏蔽提问者") },
                                onClick = {
                                    dismissMenu()
                                    viewModel.handleBlockQuestionAuthor(environment, userMessages, item) { authorInfo ->
                                        feedAuthorBlockRequest = FeedAuthorBlockRequest(
                                            FeedAuthorBlockType.QUESTION_AUTHOR,
                                            authorInfo.first,
                                            authorInfo.second,
                                        )
                                    }
                                },
                            )
                        }
                        val topics = when (val raw = item.raw) {
                            is DataHolder.Answer -> raw.question.topics
                            is DataHolder.Question -> raw.topics
                            is DataHolder.Article -> raw.topics ?: emptyList()
                            is DataHolder.Pin -> raw.topics ?: emptyList()
                            else -> emptyList()
                        }
                        topics.forEach { topic ->
                            AppDropdownMenuItem(
                                text = { Text("屏蔽「${topic.name}」") },
                                onClick = {
                                    dismissMenu()
                                    viewModel.handleBlockTopic(userMessages, topic.id, topic.name)
                                },
                            )
                        }
                    },
                )
            }
        }

        FeedAuthorBlockConfirmDialog(
            request = feedAuthorBlockRequest,
            displayItems = viewModel.displayItems,
            onDismiss = { feedAuthorBlockRequest = null },
            onConfirm = {
                viewModel.refresh(environment)
                feedAuthorBlockRequest = null
            },
        )
    }
}
