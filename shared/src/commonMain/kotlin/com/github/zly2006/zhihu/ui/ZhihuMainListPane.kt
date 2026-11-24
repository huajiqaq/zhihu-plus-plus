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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.github.zly2006.zhihu.navigation.AppNavEntry
import com.github.zly2006.zhihu.navigation.AppNavHost
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.MainTabs
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Navigator
import com.github.zly2006.zhihu.navigation.isDetailPaneDestination
import com.github.zly2006.zhihu.navigation.isListPaneDestination
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.ui.components.AppBottomBar
import com.github.zly2006.zhihu.ui.components.AppNavigationRail
import com.github.zly2006.zhihu.ui.components.AppScaffold

/** 主栈每个条目的外框：登记阅读条目的来源列表页，按目的地限制栏宽，并在主页条目旁放侧边栏。 */
@Composable
internal fun ZhihuMainListEntryFrame(
    entry: AppNavEntry,
    navHost: AppNavHost,
    paneState: ZhihuMainPaneState,
    layout: ZhihuMainPaneLayout,
    tabs: ZhihuMainTabs,
    listPaneWidth: Dp,
    navigate: (NavDestination) -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val destination = entry.destination
    val sourceEntry = remember(entry.id) { navHost.listEntryBeforeFirstReading(readingEntryId = entry.id) }
    DisposableEffect(entry.id, sourceEntry?.id) {
        if (sourceEntry != null) {
            paneState.renderedMainListEntry = sourceEntry
            paneState.renderedMainReadingEntryId = entry.id
        }
        onDispose {
            if (paneState.renderedMainReadingEntryId == entry.id) {
                paneState.renderedMainListEntry = null
                paneState.renderedMainReadingEntryId = null
            }
        }
    }
    /** 退场页按自身目的地测量，不能随新栈顶撑满窗口。 */
    val entryPaneModifier = if (!layout.showMainStackListDetail && layout.isLargeLandscape && destination.isListPaneDestination()) {
        Modifier.width(listPaneWidth).fillMaxHeight()
    } else {
        Modifier.fillMaxSize()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
        Row(
            entryPaneModifier.clipToBounds().then(
                if (layout.hasMainReadingListContext) Modifier else Modifier.testTag("list_pane"),
            ),
        ) {
            if (layout.sideRailEligible && destination == MainTabs) {
                val currentBottomDestination = tabs.pages.getOrNull(tabs.state.pagerState.targetPage)?.bottomDestination
                AppNavigationRail(
                    modifier = Modifier
                        .fillMaxHeight()
                        .onSizeChanged {
                            paneState.navigationRailWidth = with(density) { it.width.toDp() }
                        }.testTag("nav_rail"),
                    items = mainTabBarItems(tabs.barItems, currentBottomDestination, tabs.onTabClick),
                )
            }
            Box(Modifier.weight(1f).fillMaxSize()) {
                val parentNavigator = LocalNavigator.current
                CompositionLocalProvider(
                    LocalIsDualPaneReading provides (
                        (layout.showMainStackListDetail || layout.showMainReadingListContext) && destination.isDetailPaneDestination()
                    ),
                    LocalNavigator provides if (layout.showMainStackListDetail && entry.id == layout.mainListEntry?.id) {
                        Navigator(
                            onNavigate = { navigate(it) },
                            onNavigateBack = {
                                while (navHost.topEntryId != entry.id && navHost.back()) { }
                                navHost.back()
                            },
                            onNavigateTopLevel = tabs.navigateTopLevel,
                        )
                    } else {
                        parentNavigator
                    },
                ) { content() }
            }
        }
    }
}

/** 主页条目：分页内容加底栏。 */
@Composable
internal fun ZhihuMainTabsSurface(
    tabs: ZhihuMainTabs,
    isActive: Boolean,
    showBottomBar: Boolean,
    autoHideBottomBar: Boolean,
    floatingBottomBar: Boolean,
    bottomPadding: Dp,
    showHomeTopActions: Boolean,
) {
    // 底栏与列表属于同一条目，返回过程中保留完整的来源页和内容边界。
    AppScaffold(
        modifier = Modifier.fillMaxSize().testTag("main_tabs_surface").nestedScroll(tabs.state.bottomBarScrollConnection),
        bottomBar = {
            if (showBottomBar) {
                val currentBottomDestination = tabs.pages.getOrNull(tabs.state.pagerState.targetPage)?.bottomDestination
                AnimatedVisibility(
                    modifier = Modifier.fillMaxWidth().onSizeChanged { tabs.state.navigationBarHeightPx = it.height },
                    visible = !autoHideBottomBar || tabs.state.isBottomBarVisible,
                    enter = slideInVertically(tween(200)) { it },
                    exit = slideOutVertically(tween(200)) { it },
                ) {
                    AppBottomBar(
                        items = mainTabBarItems(tabs.barItems, currentBottomDestination, tabs.onTabClick),
                        floating = floatingBottomBar,
                        bottomPadding = bottomPadding,
                    )
                }
            }
        },
    ) { mainPadding ->
        MainTabsPager(
            pagerState = tabs.state.pagerState,
            pages = tabs.pages,
            isActive = isActive,
            scrollToTopTrigger = tabs.state.scrollToTopTrigger,
            innerPadding = mainPadding,
            showHomeTopActions = showHomeTopActions,
        )
    }
}

/** 两栏之间可拖动的分隔条：拖动和微调按当前可见比例计算，松手后把比例写入设置。 */
@Composable
internal fun ZhihuMainPaneDivider(
    modifier: Modifier,
    paneState: ZhihuMainPaneState,
    settings: SettingsStore,
    containerWidth: Dp,
    minimumListWidth: Dp,
    minimumDetailWidth: Dp,
) {
    val usableWidthPx = with(LocalDensity.current) { (containerWidth - LIST_DETAIL_DIVIDER_WIDTH).toPx() }

    fun visibleRatio(ratio: Float) = visibleListPaneRatio(containerWidth, ratio, minimumListWidth, minimumDetailWidth)

    fun adjustBy(delta: Float) {
        if (delta == 0f) return
        val before = visibleRatio(paneState.listPaneRatio)
        val after = visibleRatio(before + delta)
        if (after != before) paneState.previewListPaneRatio = after
    }

    DisposableEffect(paneState) {
        onDispose { paneState.previewListPaneRatio = null }
    }
    ListDetailDivider(
        modifier = modifier,
        onDrag = { deltaPx -> if (usableWidthPx > 0f) adjustBy(deltaPx / usableWidthPx) },
        onAdjustBy = ::adjustBy,
        onDragStopped = {
            paneState.previewListPaneRatio?.let { ratio ->
                settings.putFloat(LANDSCAPE_LIST_PANE_RATIO_KEY, visibleRatio(ratio))
                paneState.previewListPaneRatio = null
            }
        },
    )
}

/** 覆盖时保留活组合与原栏宽，不以零宽重排正文；不放置隐藏页，返回只恢复原布局。 */
@Composable
internal fun Modifier.retainVisibleConstraints(visible: Boolean): Modifier {
    var visibleConstraints by remember { mutableStateOf<Constraints?>(null) }
    return this.layout { measurable, constraints ->
        if (visible) visibleConstraints = constraints
        val placeable = measurable.measure(if (visible) constraints else visibleConstraints ?: constraints)
        layout(if (visible) placeable.width else 0, if (visible) placeable.height else 0) {
            if (visible) placeable.placeRelative(0, 0)
        }
    }
}
