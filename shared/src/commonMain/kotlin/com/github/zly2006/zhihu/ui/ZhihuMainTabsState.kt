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
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.AppNavHost
import com.github.zly2006.zhihu.navigation.Login
import com.github.zly2006.zhihu.navigation.MainTabs
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.OnlineHistory
import com.github.zly2006.zhihu.navigation.OnlineHistoryRoute
import com.github.zly2006.zhihu.navigation.TopLevelDestination
import com.github.zly2006.zhihu.navigation.loginNavigationRequestFlow
import com.github.zly2006.zhihu.ui.subscreens.navDestinationFromName
import kotlinx.coroutines.launch

/** 主页分页跨重组保留的界面状态。 */
@OptIn(ExperimentalFoundationApi::class)
@Stable
internal class ZhihuMainTabsState(
    val pagerState: PagerState,
    currentDestination: MutableState<TopLevelDestination>,
) {
    var currentDestination by currentDestination
    var scrollToTopTrigger by mutableIntStateOf(0)
    var isBottomBarVisible by mutableStateOf(true)
    var navigationBarHeightPx by mutableIntStateOf(0)

    /** 内容上滑时隐藏底栏，下滑时恢复；只观察，不消费滚动量。 */
    val bottomBarScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            when {
                available.y < -3f -> isBottomBarVisible = false
                available.y > 3f -> isBottomBarVisible = true
            }
            return Offset.Zero
        }
    }
}

/** 一次重组里的分页配置和操作入口；[state] 跨重组不变，其余字段随设置和栏位重新生成。 */
internal class ZhihuMainTabs(
    val state: ZhihuMainTabsState,
    val barItems: List<Triple<TopLevelDestination, String, ImageVector>>,
    val pages: List<MainTabPage>,
    val navigateTopLevel: (TopLevelDestination) -> Unit,
    val onTabClick: (destination: TopLevelDestination, selected: Boolean) -> Unit,
)

/** 建立主页分页的状态，并处理启动页、外部跳转请求、分页同步和返回历史。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun rememberZhihuMainTabs(
    navHost: AppNavHost,
    detailHost: AppNavHost,
    layout: ZhihuMainPaneLayout,
    preferenceState: ZhihuMainPreferenceState,
    mainTabNavigationTarget: TopLevelDestination?,
    animatePageScroll: Boolean,
    navigate: (NavDestination) -> Unit,
    consumeMainTabNavigationTarget: (TopLevelDestination) -> Unit,
    setCurrentMainTabOpenFrom: (String?) -> Unit,
    onCurrentMainTabDestinationChange: (TopLevelDestination) -> Unit,
    onTabClicked: () -> Unit,
): ZhihuMainTabs {
    val tapToScrollToTopEnabled = preferenceState.tapToScrollToTopEnabled
    val selectedBottomBarItemKeys = preferenceState.selectedBottomBarItemKeys
    val startDestination = preferenceState.startDestination
    val currentOnMainTabDestinationChange by rememberUpdatedState(onCurrentMainTabDestinationChange)

    val bottomBarItems = selectedBottomBarItemKeys.mapNotNull { key ->
        MAIN_BOTTOM_BAR_ITEMS.firstOrNull { it.first.name == key }
    }
    val mainTabPages = remember(bottomBarItems) { mainTabPagesFor(bottomBarItems) }

    fun pageIndexForDestination(destination: TopLevelDestination): Int = mainTabPages
        .indexOfFirst {
            it.bottomDestination::class == destination::class
        }.takeIf { it >= 0 } ?: mainTabPages
        .indexOfFirst {
            it.bottomDestination::class == startDestination::class
        }.takeIf { it >= 0 } ?: 0

    val mainPagerState = rememberPagerState(
        initialPage = pageIndexForDestination(startDestination),
        pageCount = { mainTabPages.size },
    )
    val coroutineScope = rememberCoroutineScope()

    val currentDestinationState = rememberSaveable(
        stateSaver = Saver<TopLevelDestination, String>(
            save = { it.name },
            restore = ::navDestinationFromName,
        ),
    ) {
        mutableStateOf(mainTabPages.getOrNull(mainPagerState.currentPage)?.bottomDestination ?: startDestination)
    }
    val state = remember { ZhihuMainTabsState(mainPagerState, currentDestinationState) }
    val isMainTabsPaneVisible = layout.isMainTabsPaneVisible
    val isAccountSurface = state.currentDestination == Account

    var startupDestinationHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(navHost) {
        if (!startupDestinationHandled) {
            startupDestinationHandled = true
            if (startDestination == OnlineHistory && mainTabNavigationTarget == null && navHost.backStack.toList() == listOf(MainTabs)) {
                navigate(OnlineHistoryRoute)
            }
        }
    }

    fun navigateTopLevel(destination: TopLevelDestination) {
        if (destination == OnlineHistory) {
            navigate(OnlineHistoryRoute)
            return
        }
        if (destination == Account && layout.hasOpenDetail) {
            detailHost.popToRoot()
        }
        val targetPage = pageIndexForDestination(destination)
        state.currentDestination = mainTabPages.getOrNull(targetPage)?.bottomDestination ?: startDestination
        coroutineScope.launch {
            if (animatePageScroll) mainPagerState.animateScrollToPage(targetPage) else mainPagerState.scrollToPage(targetPage)
        }
    }

    fun onMainTabClick(destination: TopLevelDestination, selected: Boolean) {
        onTabClicked()
        if (!selected) {
            navigateTopLevel(destination)
        } else if (tapToScrollToTopEnabled) {
            state.scrollToTopTrigger++
        }
    }

    LaunchedEffect(navHost) {
        loginNavigationRequestFlow.collect {
            navHost.open(Login)
        }
    }

    LaunchedEffect(mainPagerState, mainTabPages, mainTabNavigationTarget, isMainTabsPaneVisible) {
        val requestedDestination = mainTabNavigationTarget
        if (requestedDestination == OnlineHistory) {
            consumeMainTabNavigationTarget(requestedDestination)
            navigate(OnlineHistoryRoute)
            return@LaunchedEffect
        }
        if (!isMainTabsPaneVisible && requestedDestination == null) return@LaunchedEffect
        val targetPage = pageIndexForDestination(requestedDestination ?: state.currentDestination)
        state.currentDestination = mainTabPages.getOrNull(targetPage)?.bottomDestination ?: return@LaunchedEffect
        mainPagerState.scrollToPage(targetPage)
        requestedDestination?.let(consumeMainTabNavigationTarget)
        if (!isMainTabsPaneVisible) return@LaunchedEffect
        snapshotFlow { mainPagerState.currentPage }.collect { page ->
            mainTabPages.getOrNull(page)?.bottomDestination?.let { destination ->
                state.currentDestination = destination
                setCurrentMainTabOpenFrom(destination.openFrom)
                currentOnMainTabDestinationChange(destination)
            }
        }
    }

    LaunchedEffect(state.currentDestination, layout.currentDestination) {
        if (isAccountSurface && layout.hasOpenDetail) {
            /** 阅读详情属于原信息流，不随主 tab 带入账号栏。 */
            detailHost.popToRoot()
        }
    }

    MainTabBackHistory(
        pages = mainTabPages,
        pagerState = mainPagerState,
        currentDestination = { state.currentDestination },
        enabled = layout.showMainNavigation && !layout.showDetailPane,
        animateScroll = animatePageScroll,
        coroutineScope = coroutineScope,
    )

    return ZhihuMainTabs(
        state = state,
        barItems = bottomBarItems,
        pages = mainTabPages,
        navigateTopLevel = ::navigateTopLevel,
        onTabClick = ::onMainTabClick,
    )
}
