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
import com.github.zly2006.zhihu.navigation.isDetailPaneDestination
import com.github.zly2006.zhihu.navigation.isListPaneDestination

/**
 * 主栈第一个阅读条目之前的列表条目，即两栏布局里留在左侧的来源页。
 * 传入 [readingEntryId] 时，只有它正是那个阅读条目才返回。
 */
internal fun AppNavHost.listEntryBeforeFirstReading(readingEntryId: String? = null): AppNavEntry? {
    val readingIndex = pageEntries.indexOfFirst { it.destination.isDetailPaneDestination() }
    if (readingIndex <= 0) return null
    if (readingEntryId != null && pageEntries.elementAt(readingIndex).id != readingEntryId) return null
    return pageEntries.elementAt(readingIndex - 1).takeIf { it.destination.isListPaneDestination() }
}
