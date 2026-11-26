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

import com.github.zly2006.zhihu.navigation.AppNavHost
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.Video
import com.github.zly2006.zhihu.navigation.isDetailPaneDestination
import com.github.zly2006.zhihu.navigation.readingDestinationOrNull

/**
 * 主栈与详情栏之间的路由：决定一个目的地进主栈还是进详情栏，以及打开前要不要先退栈。
 * [layout] 是创建时那次重组的栏位快照，一次回调内不随栈的变化改变。
 */
internal class ZhihuMainRouter(
    private val navHost: AppNavHost,
    private val detailHost: AppNavHost,
    private val paneState: ZhihuMainPaneState,
    private val layout: ZhihuMainPaneLayout,
    private val navigate: (NavDestination) -> Unit,
    private val navigateContent: (NavDestination, AppNavHost) -> Unit,
) {
    fun openListDetail(destination: NavDestination) {
        paneState.detailOwnerEntryId = navHost.topEntryId
        navigateContent(destination, detailHost)
    }

    /** 朗读队列切换到另一篇内容时打开它；[onlyIfReading] 为 true 时，当前不在阅读页就不跳转。 */
    fun openReadingDestination(destination: NavDestination, onlyIfReading: Boolean = false) {
        val current = if (layout.hasOpenDetail) layout.selectedContentDestination else layout.currentDestination.readingDestinationOrNull()
        if (onlyIfReading && current == null) return
        if (destination is Question && current?.isDetailPaneDestination() == true) {
            if (layout.hasOpenDetail) {
                if (layout.currentDestination != destination) navigate(destination)
            } else {
                openListDetail(destination)
            }
            return
        }
        if (current == destination) {
            if (layout.hasMainReadingListContext && !layout.isLargeLandscape) detailHost.popToRoot()
            return
        }
        if (layout.hasMainReadingListContext && destination.isDetailPaneDestination()) {
            navigate(destination)
            paneState.detailOwnerEntryId = navHost.topEntryId
            return
        }
        val opensInDetail = layout.useSecondaryContentNavigation && destination.isDetailPaneDestination()
        if (layout.hasOpenDetail) {
            if (opensInDetail) {
                detailHost.back()
            } else {
                detailHost.popToRoot()
            }
        } else if (current != null && !opensInDetail) {
            navHost.back()
        }
        if (opensInDetail) {
            openListDetail(destination)
        } else {
            navigate(destination)
        }
    }

    /** 主栈里的页面发起的导航。 */
    fun navigateFromMainPane(destination: NavDestination) {
        /** 返回和打开可能在同一回调内执行，路由判定须读取当前栈。 */
        val current = layout.copy(
            currentDestination = navHost.top,
            currentEntryId = navHost.topEntryId,
            detailTop = detailHost.top,
            detailOwnerEntryId = paneState.detailOwnerEntryId,
            mainListEntry = paneState.mainListEntry(navHost),
        )
        val opensInDetailHost = (destination is Question && current.currentDestination?.isDetailPaneDestination() == true) ||
            (current.useSecondaryContentNavigation && destination.opensInDetailPane())
        if (opensInDetailHost) openListDetail(destination) else navigate(destination)
    }

    /** 详情栏里的页面发起的导航。 */
    fun navigateFromDetailPane(destination: NavDestination) {
        when {
            layout.hasMainReadingListContext && destination.isDetailPaneDestination() -> {
                if (destination == layout.currentDestination) {
                    if (!layout.isLargeLandscape) detailHost.popToRoot()
                } else {
                    navigate(destination)
                    paneState.detailOwnerEntryId = navHost.topEntryId
                }
            }
            destination is Question && layout.hasMainReadingListContext -> navigateContent(destination, detailHost)
            destination is Video && layout.hasMainReadingListContext -> navigateContent(destination, navHost)
            destination is Video || destination.opensInDetailPane() -> {
                navigateContent(destination, detailHost)
            }
            else -> navigate(destination)
        }
    }
}
