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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import com.github.zly2006.zhihu.i18n.ContentStrings
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.platform.UserMessageSink
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.ui.components.AppDialog
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.github.zly2006.zhihu.navigation.Search as SearchDestination

/** jumpTo 的旧中文值是持久化导航协议，不随界面语言变化。 */
private val PEOPLE_SCREEN_NAVIGATION_TOKENS = listOf(
    "回答",
    "文章",
    "动态",
    "收藏",
    "提问",
    "想法",
    "专栏",
    "粉丝",
    "关注",
    "关注订阅",
)

internal val PEOPLE_SCREEN_TITLES: List<String>
    get() = listOf(
        ContentStrings.answers,
        ContentStrings.articles,
        ContentStrings.activity,
        ContentStrings.savedContent,
        ContentStrings.questionsAsked,
        ContentStrings.posts,
        ContentStrings.columns,
        ContentStrings.followers,
        ContentStrings.followingPeople,
        ContentStrings.subscriptions,
    )

internal fun peopleScreenInitialPage(person: Person): Int =
    PEOPLE_SCREEN_NAVIGATION_TOKENS.indexOf(person.jumpTo).takeIf { it >= 0 } ?: 2

@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(
    person: Person,
) {
    val navigator = LocalNavigator.current
    val userMessages = rememberUserMessageSink()
    val paginationEnvironment = rememberPaginationEnvironment(allowGuestAccess = false)
    val viewModel = composeViewModel { PersonViewModel(person) }
    val coroutineScope = rememberCoroutineScope()

    val pagerState = rememberPagerState(
        initialPage = peopleScreenInitialPage(person),
        pageCount = { PEOPLE_SCREEN_TITLES.size },
    )
    val readingQueueSourceId = when (pagerState.currentPage) {
        0 -> "people:${person.userTokenOrId}:answers:${viewModel.answersFeedModel.sortBy}"
        1 -> "people:${person.userTokenOrId}:articles:${viewModel.articlesFeedModel.sortBy}"
        5 -> "people:${person.userTokenOrId}:pins"
        else -> null
    }
    PeopleScreenRegisterReadingQueueSource(
        page = pagerState.currentPage,
        readingQueueSourceId = readingQueueSourceId,
        viewModel = viewModel,
    )

    LaunchedEffect(viewModel) {
        if (!viewModel.hasLoadedProfile) viewModel.load(paginationEnvironment)
    }
    LaunchedEffect(pagerState.currentPage) {
        try {
            viewModel.subFeedModels.getOrNull(pagerState.currentPage)?.let { feedModel ->
                if (feedModel !== viewModel.activitiesFeedModel && feedModel.allData.isEmpty() && !feedModel.isEnd && feedModel.errorMessage == null) {
                    feedModel.loadMore(paginationEnvironment)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            userMessages.showShortMessage(ContentStrings.pageLoadError(e.message))
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var expandedHeaderHeightPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(expandedHeaderHeightPx) {
        if (expandedHeaderHeightPx > 0) {
            val fraction = scrollBehavior.state.collapsedFraction.coerceIn(0f, 1f)
            scrollBehavior.state.heightOffsetLimit = -expandedHeaderHeightPx.toFloat()
            scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit * fraction
        }
    }
    val collapsedFraction = scrollBehavior.state.collapsedFraction
    val searchInTopBar = scrollBehavior.state.heightOffset < -with(LocalDensity.current) { AppDimens.Space8.toPx() }
    var showActionsMenu by rememberSaveable(person.userTokenOrId) { mutableStateOf(false) }
    var showBlockConfirm by rememberSaveable(person.userTokenOrId) { mutableStateOf(false) }
    val onSearch: (() -> Unit)? = if (
        viewModel.memberHashId.isNotBlank() && viewModel.memberHashId != Person.EMPTY_ID
    ) {
        {
            navigator.onNavigate(
                SearchDestination(
                    restrictedMemberHashId = viewModel.memberHashId,
                    restrictedMemberName = viewModel.name.takeIf(String::isNotBlank) ?: person.name,
                ),
            )
        }
    } else {
        null
    }

    AppScaffold(
        modifier = Modifier
            .testTag(PEOPLE_SCREEN_ROOT_TAG)
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        topBar = {
            PeopleScreenTopBar(
                person = person,
                viewModel = viewModel,
                pagerState = pagerState,
                scrollBehavior = scrollBehavior,
                collapsedFraction = collapsedFraction,
                searchInTopBar = searchInTopBar,
                onSearch = onSearch,
                showActionsMenu = showActionsMenu,
                onShowActionsMenuChange = { showActionsMenu = it },
                onExpandedHeaderHeightChange = { expandedHeaderHeightPx = it },
                onRequestBlockConfirm = { showBlockConfirm = true },
                navigator = navigator,
                paginationEnvironment = paginationEnvironment,
                coroutineScope = coroutineScope,
                userMessages = userMessages,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.widthIn(max = AppDimens.ArticleReadingMaxWidth).fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(PEOPLE_SCREEN_PAGER_TAG),
                ) { page ->
                    PeopleScreenPage(
                        page = page,
                        pagerState = pagerState,
                        viewModel = viewModel,
                        paginationEnvironment = paginationEnvironment,
                        readingQueueSourceId = readingQueueSourceId,
                        navigator = navigator,
                    )
                }
            }
        }
    }
    PeopleScreenBlockConfirmDialog(
        viewModel = viewModel,
        paginationEnvironment = paginationEnvironment,
        coroutineScope = coroutineScope,
        userMessages = userMessages,
        showBlockConfirm = showBlockConfirm,
        onShowBlockConfirmChange = { showBlockConfirm = it },
    )
}

@Composable
private fun PeopleScreenBlockConfirmDialog(
    viewModel: PersonViewModel,
    paginationEnvironment: PaginationEnvironment,
    coroutineScope: CoroutineScope,
    userMessages: UserMessageSink,
    showBlockConfirm: Boolean,
    onShowBlockConfirmChange: (Boolean) -> Unit,
) {
    if (showBlockConfirm) {
        AppDialog(
            title = ContentStrings.blockUserConfirm(viewModel.name),
            summary = ContentStrings.blockUserHelp,
            confirmText = ContentStrings.blockUser,
            onConfirm = {
                onShowBlockConfirmChange(false)
                coroutineScope.launch {
                    try {
                        viewModel.toggleBlock(paginationEnvironment)
                        userMessages.showShortMessage(ContentStrings.userBlocked)
                    } catch (error: Exception) {
                        userMessages.showShortMessage(ContentStrings.operationError(error.message))
                    }
                }
            },
            dismissText = ContentStrings.cancel,
            onDismissRequest = { onShowBlockConfirmChange(false) },
            destructive = true,
        )
    }
}
