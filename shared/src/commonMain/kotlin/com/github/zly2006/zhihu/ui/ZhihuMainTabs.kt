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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.github.zly2006.zhihu.filter.ContentOpenFrom
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.Daily
import com.github.zly2006.zhihu.navigation.Follow
import com.github.zly2006.zhihu.navigation.Home
import com.github.zly2006.zhihu.navigation.HotList
import com.github.zly2006.zhihu.navigation.MyCollections
import com.github.zly2006.zhihu.navigation.OnlineHistory
import com.github.zly2006.zhihu.navigation.TopLevelDestination
import com.github.zly2006.zhihu.platform.PlatformBackHandler
import com.github.zly2006.zhihu.ui.components.BottomBarItem
import com.github.zly2006.zhihu.ui.components.NoOpPagerNestedScrollConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal sealed class MainTabPage(
    val bottomDestination: TopLevelDestination,
    val key: String,
) {
    data object HomePage : MainTabPage(Home, "home")

    data object FollowPage : MainTabPage(Follow, "follow")

    data object HotListPage : MainTabPage(HotList, "hotlist")

    data object DailyPage : MainTabPage(Daily, "daily")

    data object MyCollectionsPage : MainTabPage(MyCollections, "my_collections")

    data object AccountPage : MainTabPage(Account, "account")
}

/** 底栏与侧边栏可选的全部入口；显示哪些、按什么顺序由设置决定。 */
internal val MAIN_BOTTOM_BAR_ITEMS: List<Triple<TopLevelDestination, String, ImageVector>> = listOf(
    Triple(Home, "主页", Icons.Filled.Home),
    Triple(Follow, "关注", Icons.Filled.Group),
    Triple(HotList, "热榜", Icons.Filled.Whatshot),
    Triple(Daily, "日报", Icons.Filled.Newspaper),
    Triple(OnlineHistory, "历史", Icons.Filled.History),
    Triple(MyCollections, "收藏夹", Icons.Filled.Bookmarks),
    Triple(Account, "账号", Icons.Filled.ManageAccounts),
)

/** 历史入口打开独立页面，不占主页分页；一个分页都没有时回退到主页。 */
internal fun mainTabPagesFor(items: List<Triple<TopLevelDestination, String, ImageVector>>): List<MainTabPage> = items
    .mapNotNull { item ->
        when (item.first) {
            Home -> MainTabPage.HomePage
            Follow -> MainTabPage.FollowPage
            HotList -> MainTabPage.HotListPage
            Daily -> MainTabPage.DailyPage
            MyCollections -> MainTabPage.MyCollectionsPage
            Account -> MainTabPage.AccountPage
            else -> null
        }
    }.ifEmpty { listOf(MainTabPage.HomePage) }

/** 底栏与侧边栏共用的导航项；[onClick] 的第二个参数表示点击时该项是否已选中。 */
internal fun mainTabBarItems(
    items: List<Triple<TopLevelDestination, String, ImageVector>>,
    current: TopLevelDestination?,
    onClick: (destination: TopLevelDestination, selected: Boolean) -> Unit,
): List<BottomBarItem> = items.map { (destination, label, icon) ->
    val selected = current?.let { it::class == destination::class } == true
    BottomBarItem(
        label = label,
        icon = icon,
        testTag = "nav_tab_${destination.name.lowercase()}",
        selected = selected,
        onClick = { onClick(destination, selected) },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MainTabsPager(
    pagerState: PagerState,
    pages: List<MainTabPage>,
    isActive: Boolean,
    scrollToTopTrigger: Int,
    innerPadding: PaddingValues,
    collectionDirectBrowseEnabled: Boolean,
    showHomeTopActions: Boolean,
) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        pageNestedScrollConnection = NoOpPagerNestedScrollConnection,
    ) { pageIndex ->
        val page = pages.getOrNull(pageIndex) ?: return@HorizontalPager
        when (page) {
            MainTabPage.HomePage -> HomeScreen(
                scrollToTopTrigger = scrollToTopTrigger,
                innerPadding = innerPadding,
                showTopActions = showHomeTopActions,
                isActive = isActive && pagerState.currentPage == pageIndex,
            )
            MainTabPage.FollowPage -> FollowScreen(
                scrollToTopTrigger = scrollToTopTrigger,
                innerPadding = innerPadding,
                parentPagerState = pagerState,
                isActive = isActive && pagerState.currentPage == pageIndex,
            )
            MainTabPage.HotListPage -> HotListScreen(
                innerPadding = innerPadding,
                scrollToTopTrigger = scrollToTopTrigger,
                isActive = isActive && pagerState.currentPage == pageIndex,
            )
            MainTabPage.DailyPage -> DailyScreen(
                scrollToTopTrigger = scrollToTopTrigger,
                innerPadding = innerPadding,
                isActive = isActive && pagerState.currentPage == pageIndex,
            )
            MainTabPage.MyCollectionsPage -> MyCollectionsTopLevelPage(
                scrollToTopTrigger = scrollToTopTrigger,
                innerPadding = innerPadding,
                collectionDirectBrowseEnabled = collectionDirectBrowseEnabled,
                isActive = isActive && pagerState.currentPage == pageIndex,
            )
            MainTabPage.AccountPage -> AccountSettingScreen(
                innerPadding = innerPadding,
                isActive = isActive && pagerState.currentPage == pageIndex,
            )
        }
    }
}

@Composable
private fun MyCollectionsTopLevelPage(
    scrollToTopTrigger: Int,
    innerPadding: PaddingValues,
    collectionDirectBrowseEnabled: Boolean,
    isActive: Boolean,
) {
    val account = rememberAccountSettingsAccountState().value
    if (collectionDirectBrowseEnabled) {
        CollectionBrowseScreen(
            urlToken = account.urlToken,
            contentPadding = innerPadding,
            showBackButton = false,
            scrollToTopTrigger = scrollToTopTrigger,
            isActive = isActive,
        )
    } else {
        CollectionScreen(
            urlToken = account.urlToken,
            contentPadding = innerPadding,
            showBackButton = false,
            isActive = isActive,
        )
    }
}

/**
 * 主页分页的返回历史：返回键回到上一个停留过的分页，没有记录时回到第一页。
 * [enabled] 为 false 时不拦截返回键，历史照常记录。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MainTabBackHistory(
    pages: List<MainTabPage>,
    pagerState: PagerState,
    currentDestination: () -> TopLevelDestination,
    enabled: Boolean,
    animateScroll: Boolean,
    coroutineScope: CoroutineScope,
) {
    var history by rememberSaveable {
        mutableStateOf(arrayOf(pages.getOrNull(pagerState.settledPage)?.key ?: pages.first().key))
    }
    var backTargetKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pages) {
        if (pages.none { it.key == backTargetKey }) {
            backTargetKey = null
        }
        val validHistory = history.copyOf()
        var validSize = 0
        for (key in history) {
            if (pages.any { it.key == key } && (validSize == 0 || validHistory[validSize - 1] != key)) {
                validHistory[validSize++] = key
            }
        }
        history = validHistory.copyOfRange(0, validSize)
        snapshotFlow {
            val page = pages.getOrNull(pagerState.settledPage)
            val settledKey = page
                ?.takeIf {
                    !pagerState.isScrollInProgress && it.bottomDestination == currentDestination()
                }?.key
            settledKey to backTargetKey
        }.collect { (key, _) ->
            if (key == null || (backTargetKey != null && backTargetKey != key)) return@collect
            backTargetKey = null
            if (history.lastOrNull() != key) history += key
        }
    }

    PlatformBackHandler(
        enabled &&
            (backTargetKey != null || pagerState.isScrollInProgress || history.size > 1 || pagerState.settledPage != 0),
    ) {
        if (backTargetKey == null && !pagerState.isScrollInProgress) {
            val previousKey = history.getOrNull(history.lastIndex - 1)
            val targetPage = pages.indexOfFirst { it.key == previousKey }.takeIf { it >= 0 } ?: 0
            val targetKey = pages[targetPage].key
            /** 同步占用返回目标至停稳，连按不能在动画协程启动前再消费一层。 */
            backTargetKey = targetKey
            history = if (previousKey == targetKey) {
                history.copyOfRange(0, history.lastIndex)
            } else {
                arrayOf(targetKey)
            }
            coroutineScope.launch {
                try {
                    if (animateScroll) pagerState.animateScrollToPage(targetPage) else pagerState.scrollToPage(targetPage)
                } finally {
                    if (backTargetKey == targetKey && pages.getOrNull(pagerState.settledPage)?.key != targetKey) {
                        backTargetKey = null
                    }
                }
            }
        }
    }
}

internal val TopLevelDestination.openFrom: String?
    get() = when (this) {
        Home -> ContentOpenFrom.HOME_FEED
        else -> null
    }
