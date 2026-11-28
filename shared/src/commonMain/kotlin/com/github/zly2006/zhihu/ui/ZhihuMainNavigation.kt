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

import com.github.zly2006.zhihu.navigation.AppNavEntry
import com.github.zly2006.zhihu.navigation.AppNavHost
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.WebPage
import com.github.zly2006.zhihu.navigation.isDetailPaneDestination
import com.github.zly2006.zhihu.navigation.isListPaneDestination
import com.github.zly2006.zhihu.navigation.isSecondaryDetailDestination

internal fun NavDestination.opensInDetailPane(): Boolean = isDetailPaneDestination() || isSecondaryDetailDestination()

/** 在选栏、改写详情归属和入栈之前分流，当前页和浏览器前进历史都不变。 */
internal fun navigateWithWebPageOpener(
    openWebPage: ((String) -> Unit)?,
    navigate: (NavDestination) -> Unit,
): (NavDestination) -> Unit {
    if (openWebPage == null) return navigate
    return { page -> if (page is WebPage) openWebPage(page.url) else navigate(page) }
}

/**
 * 主栈中连续详情条目的来源列表；传入 [detailEntryId] 时，只为首个详情登记退场来源。
 * 阅读页保留原来源直到出栈；单栏中进入的设置等二级页也可在原栈就地展宽。
 */
internal fun AppNavHost.listEntryForMainDetail(detailEntryId: String? = null): AppNavEntry? {
    var source: AppNavEntry? = null
    var firstDetailId: String? = null
    for (entry in pageEntries) {
        val destination = entry.destination
        if (destination.opensInDetailPane() && (source != null || !destination.isListPaneDestination())) {
            if (firstDetailId == null) firstDetailId = entry.id
            if (destination.isDetailPaneDestination()) break
        } else {
            source = entry.takeIf { destination.isListPaneDestination() }
            firstDetailId = null
        }
    }
    if (firstDetailId == null || (detailEntryId != null && firstDetailId != detailEntryId)) return null
    return source
}
