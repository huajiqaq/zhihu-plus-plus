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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.navigation.AppNavEntry
import com.github.zly2006.zhihu.navigation.AppNavHost
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.EmptyDetail
import com.github.zly2006.zhihu.navigation.Login
import com.github.zly2006.zhihu.navigation.MainTabs
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.isDetailPaneDestination
import com.github.zly2006.zhihu.navigation.isListPaneDestination
import com.github.zly2006.zhihu.navigation.readingDestinationOrNull
import com.github.zly2006.zhihu.platform.SettingsStore

/** 主栈与详情栏共用的可变状态；路由和界面读写同一份。 */
@Stable
internal class ZhihuMainPaneState(
    detailOwnerEntryId: MutableState<String?>,
    initialListPaneRatio: Float,
) {
    /** 窄屏覆盖详情时保留原条目，返回原来源后恢复。 */
    var detailOwnerEntryId by detailOwnerEntryId

    /** 主栈里正在显示的阅读条目及其来源列表页，由条目外框在进场、退场时登记和清除。 */
    var renderedMainListEntry by mutableStateOf<AppNavEntry?>(null)
    var renderedMainReadingEntryId by mutableStateOf<String?>(null)

    var navigationRailWidth by mutableStateOf(0.dp)
    var savedListPaneRatio by mutableFloatStateOf(initialListPaneRatio)
    var previewListPaneRatio by mutableStateOf<Float?>(null)
    val listPaneRatio: Float get() = previewListPaneRatio ?: savedListPaneRatio
}

@Composable
internal fun rememberZhihuMainPaneState(settings: SettingsStore): ZhihuMainPaneState {
    val detailOwnerEntryId = rememberSaveable { mutableStateOf<String?>(null) }

    fun savedRatio() = settings.getFloat(LANDSCAPE_LIST_PANE_RATIO_KEY, DEFAULT_LIST_PANE_RATIO)

    val state = remember(settings, detailOwnerEntryId) { ZhihuMainPaneState(detailOwnerEntryId, savedRatio()) }
    DisposableEffect(state) {
        val subscription = settings.observeKeyChanges { key ->
            if (key == LANDSCAPE_LIST_PANE_RATIO_KEY) state.savedListPaneRatio = savedRatio()
        }
        state.savedListPaneRatio = savedRatio()
        onDispose(subscription::close)
    }
    return state
}

/**
 * 栏位规则：由两个导航栈的栈顶和窗口尺寸推出各栏显示什么、新目的地进哪个栈。
 * 界面和路由读同一份快照；路由需要按最新的栈重新判断时，用 [copy] 换入当前栈顶。
 */
@Immutable
internal data class ZhihuMainPaneLayout(
    val currentDestination: NavDestination?,
    val currentEntryId: String?,
    val detailTop: NavDestination?,
    val detailOwnerEntryId: String?,
    val mainListEntry: AppNavEntry?,
    val mainReadingStartIndex: Int,
    val sideRailEligible: Boolean,
    val minimumListWidth: Dp,
    val minimumDetailWidth: Dp,
    val isLargeLandscape: Boolean,
    val enableLandscapeListDetail: Boolean,
) {
    val selectedContentDestination: NavDestination?
        get() = detailTop.readingDestinationOrNull()

    val hasOpenSecondaryDetail: Boolean
        get() = detailTop != null && detailTop != EmptyDetail

    val showMainNavigation: Boolean
        get() = currentDestination == MainTabs

    val useSideRail: Boolean
        get() = sideRailEligible && showMainNavigation

    /** 主栈阅读保持原 entry；它打开的问题使用第二栈，宽屏放左、窄屏暂时覆盖。 */
    val hasMainReadingListContext: Boolean
        get() = hasOpenSecondaryDetail &&
            detailTop is Question &&
            currentEntryId == detailOwnerEntryId &&
            currentDestination?.isDetailPaneDestination() == true

    val showMainReadingListContext: Boolean
        get() = isLargeLandscape && hasMainReadingListContext

    val showMainStackListDetail: Boolean
        get() = isLargeLandscape && mainListEntry != null && !hasMainReadingListContext

    val showListDetail: Boolean
        get() = isLargeLandscape && currentDestination.isListPaneDestination() && !showMainStackListDetail

    val showDetailPane: Boolean
        get() = hasOpenSecondaryDetail &&
            (showListDetail || currentEntryId == detailOwnerEntryId) &&
            !showMainStackListDetail &&
            !hasMainReadingListContext

    val hasOpenDetail: Boolean
        get() = showDetailPane && selectedContentDestination != null

    /** 竖屏使用主栈，避免预测返回预览到详情栏占位页。 */
    val useSecondaryContentNavigation: Boolean
        get() = enableLandscapeListDetail && showListDetail && !showMainStackListDetail

    val isOnReadingDetail: Boolean
        get() = hasOpenDetail || currentDestination.readingDestinationOrNull() != null

    val isOnArticle: Boolean
        get() = (hasOpenDetail && detailTop is Article) || currentDestination is Article

    val isMainTabsPaneVisible: Boolean
        get() = (showListDetail || !showDetailPane) &&
            (showMainNavigation || (showMainStackListDetail && currentDestination != Login && mainListEntry?.destination == MainTabs))
}

/** 读取两个导航栈和窗口尺寸，算出本次重组的栏位快照。 */
@Composable
internal fun zhihuMainPaneLayout(
    navHost: AppNavHost,
    detailHost: AppNavHost,
    paneState: ZhihuMainPaneState,
    containerWidth: Dp,
    containerHeight: Dp,
    showMainNavigationBar: Boolean,
    floatingBottomBar: Boolean,
    enableLandscapeListDetail: Boolean,
    isListDetailCapable: Boolean,
    landscapeListDetailEnabled: Boolean,
): ZhihuMainPaneLayout {
    val currentDestination = navHost.top
    val sideRailEligible = containerWidth >= 600.dp && containerWidth > containerHeight && showMainNavigationBar && !floatingBottomBar

    /** 出栈先于退场完成，来源栏位须保留到阅读条目退场。 */
    val mainListEntry = navHost.listEntryBeforeFirstReading() ?: paneState.renderedMainListEntry?.takeIf { navHost.containsEntry(it.id) }
    val mainReadingStartIndex = mainListEntry?.let { navHost.pageEntries.indexOfFirst { entry -> entry.id == it.id } + 1 } ?: -1
    val paneFontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val sideRailWidth = if (sideRailEligible && (currentDestination == MainTabs || mainListEntry?.destination == MainTabs)) {
        paneState.navigationRailWidth
    } else {
        0.dp
    }

    /** 阅读与其打开的问题共用来源栏宽约束，避免暂时隐藏侧栏时改变已保存的分栏位置。 */
    val minimumListWidth = minimumListContentWidth(paneFontScale) + sideRailWidth
    val minimumDetailWidth = MIN_DETAIL_PANE_WIDTH * paneFontScale
    return ZhihuMainPaneLayout(
        currentDestination = currentDestination,
        currentEntryId = navHost.topEntryId,
        detailTop = detailHost.top,
        detailOwnerEntryId = paneState.detailOwnerEntryId,
        mainListEntry = mainListEntry,
        mainReadingStartIndex = mainReadingStartIndex,
        sideRailEligible = sideRailEligible,
        minimumListWidth = minimumListWidth,
        minimumDetailWidth = minimumDetailWidth,
        isLargeLandscape = canShowLandscapeListDetail(
            enabled = enableLandscapeListDetail,
            deviceSupportsListDetail = isListDetailCapable,
            preferenceEnabled = landscapeListDetailEnabled,
            width = containerWidth,
            height = containerHeight,
            minimumListWidth = minimumListWidth,
            minimumDetailWidth = minimumDetailWidth,
        ),
        enableLandscapeListDetail = enableLandscapeListDetail,
    )
}
