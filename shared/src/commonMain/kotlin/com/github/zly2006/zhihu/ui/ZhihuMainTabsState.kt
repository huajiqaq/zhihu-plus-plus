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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
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
import com.github.zly2006.zhihu.platform.PlatformBackHandler
import com.github.zly2006.zhihu.ui.subscreens.navDestinationFromName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 主标签唯一所有者；请求、当前选择、手势停稳位置和已提交历史具有不同语义。 */
@OptIn(ExperimentalFoundationApi::class)
@Stable
class ZhihuMainTabsState internal constructor(
    val preferences: ZhihuMainPreferenceState,
    internal val pagerState: PagerState,
    private val coroutineScope: CoroutineScope,
    initialDestination: TopLevelDestination,
    initialHistory: List<TopLevelDestination> = listOf(initialDestination),
    startupHandled: Boolean = false,
    initialRequest: TopLevelDestination? = null,
) {
    internal val pages by derivedStateOf {
        mainTabPagesFor(preferences.selectedBottomBarItemKeys.mapNotNull { key -> MAIN_BOTTOM_BAR_ITEMS.firstOrNull { it.first.name == key } })
    }

    var currentDestination by mutableStateOf(initialDestination)
        private set

    val settledDestination: TopLevelDestination
        get() = pages.getOrNull(pagerState.settledPage)?.bottomDestination ?: currentDestination

    private var visited by mutableStateOf(initialHistory)
    private var backTarget by mutableStateOf<TopLevelDestination?>(null)
    internal val settledVisit: Pair<TopLevelDestination?, TopLevelDestination?>
        get() = settledDestination.takeIf {
            !pagerState.isScrollInProgress && pendingNavigation == null && it == currentDestination
        } to backTarget
    internal var pendingNavigation by mutableStateOf(initialRequest?.let(::MainTabRequest))
        private set
    internal var startupHandled by mutableStateOf(startupHandled)
        private set

    /** 浏览器地址需要显式根页；原生返回历史仍保留实际首次停留页。 */
    val history: List<TopLevelDestination>
        get() {
            val first = pages.first().bottomDestination
            return if (visited.firstOrNull() == first) visited else listOf(first) + visited
        }

    internal var scrollToTopTrigger by mutableIntStateOf(0)
    internal var isBottomBarVisible by mutableStateOf(true)
    internal var navigationBarHeightPx by mutableIntStateOf(0)

    internal val bottomBarScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            when {
                available.y < -3f -> isBottomBarVisible = false
                available.y > 3f -> isBottomBarVisible = true
            }
            return Offset.Zero
        }
    }

    /** Compose 导航与原生 chrome 共享重选契约；首次选择仍交各宿主既有路由动作。 */
    fun selectOrReselect(destination: TopLevelDestination, selected: Boolean, onSelect: (TopLevelDestination) -> Unit) {
        if (!selected) onSelect(destination) else if (preferences.tapToScrollToTopEnabled) scrollToTopTrigger++
    }

    /** 宿主只能发起请求或读取状态；null 撤销尚未执行的外部请求。 */
    fun requestTab(destination: TopLevelDestination?) {
        pendingNavigation = destination?.let(::MainTabRequest)
    }

    internal fun pageIndex(destination: TopLevelDestination): Int = pages
        .indexOfFirst { it.bottomDestination == destination }
        .takeIf { it >= 0 }
        ?: pages.indexOfFirst { it.bottomDestination == preferences.startDestination }.takeIf { it >= 0 }
        ?: 0

    internal fun start(navHost: AppNavHost, navigate: (NavDestination) -> Unit) {
        if (startupHandled) return
        if (preferences.startDestination == OnlineHistory && pendingNavigation == null && navHost.backStack == listOf(MainTabs)) {
            navigate(OnlineHistoryRoute)
        }
        startupHandled = true
    }

    internal fun select(destination: TopLevelDestination, animate: Boolean) {
        val page = pageIndex(destination)
        currentDestination = pages[page].bottomDestination
        coroutineScope.launch { scrollToPage(page, animate) }
    }

    internal suspend fun alignPager(navigate: (NavDestination) -> Unit) {
        val request = pendingNavigation
        if (request?.destination == OnlineHistory) {
            pendingNavigation = null
            navigate(OnlineHistoryRoute)
            return
        }
        val page = pageIndex(request?.destination ?: currentDestination)
        currentDestination = pages[page].bottomDestination
        pagerState.scrollToPage(page)
        if (pendingNavigation === request) pendingNavigation = null
    }

    internal fun observeCurrentPage(page: Int) {
        pages.getOrNull(page)?.let { currentDestination = it.bottomDestination }
    }

    internal fun reconcileHistory() {
        val available = pages.map { it.bottomDestination }
        if (backTarget !in available) backTarget = null
        visited = visited.filter { it in available }.fold(emptyList()) { result, destination ->
            if (result.lastOrNull() == destination) result else result + destination
        }
    }

    internal fun recordSettledPage(destination: TopLevelDestination?) {
        if (destination == null || (backTarget != null && backTarget != destination)) return
        backTarget = null
        if (visited.lastOrNull() != destination) visited = visited + destination
    }

    internal val canHandleBack: Boolean
        get() = pendingNavigation != null || backTarget != null || currentDestination != settledDestination || pagerState.isScrollInProgress || visited.size > 1 || pagerState.settledPage != 0

    internal fun back(animate: Boolean) {
        if (pendingNavigation != null || backTarget != null || currentDestination != settledDestination || pagerState.isScrollInProgress) return
        val previous = visited.getOrNull(visited.lastIndex - 1)
        val page = pages.indexOfFirst { it.bottomDestination == previous }.takeIf { it >= 0 } ?: 0
        val target = pages[page].bottomDestination
        /** 同步占用返回目标至停稳，连按不能在动画协程启动前再消费一层。 */
        backTarget = target
        visited = if (previous == target) visited.dropLast(1) else listOf(target)
        coroutineScope.launch {
            try {
                scrollToPage(page, animate)
            } finally {
                if (backTarget == target && settledDestination != target) backTarget = null
            }
        }
    }

    /** 浏览器恢复在同一帧更新目的地、分页请求和历史，不经宿主回传。 */
    internal fun restoreHistory(destinations: List<TopLevelDestination>) {
        val valid = destinations.filter { destination -> pages.any { it.bottomDestination == destination } }
            .ifEmpty { listOf(pages.first().bottomDestination) }
        pendingNavigation = null
        backTarget = null
        visited = valid
        currentDestination = valid.last()
        pagerState.requestScrollToPage(pageIndex(currentDestination))
    }

    private suspend fun scrollToPage(page: Int, animate: Boolean) {
        if (animate) pagerState.animateScrollToPage(page) else pagerState.scrollToPage(page)
    }

    internal companion object {
        fun saver(preferences: ZhihuMainPreferenceState, pager: PagerState, scope: CoroutineScope) = listSaver<ZhihuMainTabsState, String>(
            save = {
                // 返回动画中重建时恢复已消费的目标，不把离开的中间页再加入历史。
                listOf(
                    (it.backTarget ?: it.currentDestination).name,
                    it.startupHandled.toString(),
                    it.pendingNavigation?.destination?.name.orEmpty(),
                ) + it.visited.map(TopLevelDestination::name)
            },
            restore = { saved ->
                val startup = saved.getOrNull(1)?.toBooleanStrictOrNull()
                if (saved.size < 3 || startup == null) {
                    null
                } else {
                    ZhihuMainTabsState(
                        preferences = preferences,
                        pagerState = pager,
                        coroutineScope = scope,
                        initialDestination = navDestinationFromName(saved[0]),
                        initialHistory = saved.drop(3).map(::navDestinationFromName),
                        startupHandled = startup,
                        initialRequest = saved[2].takeIf(String::isNotEmpty)?.let(::navDestinationFromName),
                    )
                }
            },
        )
    }
}

internal class MainTabRequest(val destination: TopLevelDestination)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberZhihuMainTabsState(preferences: ZhihuMainPreferenceState = rememberZhihuMainPreferenceState()): ZhihuMainTabsState {
    val pages = mainTabPagesFor(preferences.selectedBottomBarItemKeys.mapNotNull { key -> MAIN_BOTTOM_BAR_ITEMS.firstOrNull { it.first.name == key } })
    val startPage = pages.indexOfFirst { it.bottomDestination == preferences.startDestination }.takeIf { it >= 0 } ?: 0
    val pager = rememberPagerState(initialPage = startPage, pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    return rememberSaveable(preferences, pager, saver = ZhihuMainTabsState.saver(preferences, pager, scope)) {
        ZhihuMainTabsState(preferences, pager, scope, pages[startPage].bottomDestination)
    }
}

/** 一次重组里的呈现和动作；所有可变状态只保存在 [state]。 */
internal class ZhihuMainTabs(
    val state: ZhihuMainTabsState,
    val barItems: List<Triple<TopLevelDestination, String, ImageVector>>,
    val navigateTopLevel: (TopLevelDestination) -> Unit,
    val onTabClick: (destination: TopLevelDestination, selected: Boolean) -> Unit,
) {
    val pages: List<MainTabPage> get() = state.pages
}

/** 平台只有在普通启动和可保存状态就绪后，才能用外部地址替换当前访问。 */
internal val LocalMainNavigationBinding = staticCompositionLocalOf<((AppNavHost, ZhihuMainPaneState) -> Unit)?> { null }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun rememberZhihuMainTabs(
    state: ZhihuMainTabsState,
    navHost: AppNavHost,
    detailHost: AppNavHost,
    layout: ZhihuMainPaneLayout,
    animatePageScroll: Boolean,
    navigate: (NavDestination) -> Unit,
    onTabClicked: () -> Unit,
): ZhihuMainTabs {
    val preferences = state.preferences
    val pages = state.pages
    val isMainTabsPaneVisible = layout.isMainTabsPaneVisible
    val pendingNavigation = state.pendingNavigation
    val currentNavigate by rememberUpdatedState(navigate)
    val barItems = preferences.selectedBottomBarItemKeys.mapNotNull { key -> MAIN_BOTTOM_BAR_ITEMS.firstOrNull { it.first.name == key } }

    LaunchedEffect(state, navHost) { state.start(navHost, currentNavigate) }
    LaunchedEffect(navHost) {
        loginNavigationRequestFlow.collect { navHost.open(Login) }
    }
    LaunchedEffect(state, pages, pendingNavigation, isMainTabsPaneVisible) {
        if (!isMainTabsPaneVisible && pendingNavigation == null) return@LaunchedEffect
        state.alignPager(currentNavigate)
        if (!isMainTabsPaneVisible) return@LaunchedEffect
        snapshotFlow { state.pagerState.currentPage }.collect(state::observeCurrentPage)
    }
    LaunchedEffect(state.currentDestination, layout.currentDestination) {
        if (state.currentDestination == Account && layout.hasOpenDetail) {
            /** 阅读详情属于原信息流，不随主 tab 带入账号栏。 */
            detailHost.popToRoot()
        }
    }
    LaunchedEffect(state, pages) {
        state.reconcileHistory()
        snapshotFlow { state.settledVisit }
            .collect { (destination, _) -> state.recordSettledPage(destination) }
    }
    PlatformBackHandler(layout.showMainNavigation && !layout.showDetailPane && state.canHandleBack) {
        state.back(animatePageScroll)
    }

    fun navigateTopLevel(destination: TopLevelDestination) {
        if (destination == OnlineHistory) {
            navigate(OnlineHistoryRoute)
        } else {
            if (destination == Account && layout.hasOpenDetail) detailHost.popToRoot()
            state.select(destination, animatePageScroll)
        }
    }
    return ZhihuMainTabs(
        state = state,
        barItems = barItems,
        navigateTopLevel = ::navigateTopLevel,
        onTabClick = { destination, selected ->
            onTabClicked()
            state.selectOrReselect(destination, selected, ::navigateTopLevel)
        },
    )
}
