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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.github.zly2006.zhihu.filter.ContentOpenFrom
import com.github.zly2006.zhihu.i18n.AppStrings
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.Daily
import com.github.zly2006.zhihu.navigation.Follow
import com.github.zly2006.zhihu.navigation.Home
import com.github.zly2006.zhihu.navigation.HotList
import com.github.zly2006.zhihu.navigation.MyCollections
import com.github.zly2006.zhihu.navigation.OnlineHistory
import com.github.zly2006.zhihu.navigation.TopLevelDestination
import com.github.zly2006.zhihu.ui.components.BottomBarItem
import com.github.zly2006.zhihu.ui.components.NoOpPagerNestedScrollConnection

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
internal val MAIN_BOTTOM_BAR_ITEMS: List<Triple<TopLevelDestination, String, ImageVector>>
    get() = listOf(
        Triple(Home, AppStrings.home, Icons.Filled.Home),
        Triple(Follow, AppStrings.following, Icons.Filled.Group),
        Triple(HotList, AppStrings.trending, Icons.Filled.Whatshot),
        Triple(Daily, AppStrings.daily, Icons.Filled.Newspaper),
        Triple(OnlineHistory, AppStrings.history, Icons.Filled.History),
        Triple(MyCollections, AppStrings.collections, Icons.Filled.Bookmarks),
        Triple(Account, AppStrings.account, Icons.Filled.ManageAccounts),
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
    isActive: Boolean,
) {
    val account = rememberAccountSettingsAccountState().value
    CollectionBrowseScreen(
        urlToken = account.urlToken,
        contentPadding = innerPadding,
        showBackButton = false,
        scrollToTopTrigger = scrollToTopTrigger,
        isActive = isActive,
    )
}

internal val TopLevelDestination.openFrom: String?
    get() = when (this) {
        Home -> ContentOpenFrom.HOME_FEED
        else -> null
    }
