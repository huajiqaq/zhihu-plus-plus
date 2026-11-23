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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.github.zly2006.zhihu.navigation.AppNavHost
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.accountSettings
import com.github.zly2006.zhihu.navigation.contentBrowseEntries
import com.github.zly2006.zhihu.navigation.contentMainEntries
import com.github.zly2006.zhihu.navigation.feedSearchEntry
import com.github.zly2006.zhihu.navigation.historyEntries
import com.github.zly2006.zhihu.navigation.loginEntry
import com.github.zly2006.zhihu.navigation.mainNotificationEntries
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import com.github.zly2006.zhihu.navigation.AppNavEntryBuilder as NavEntryBuilder

internal fun NavEntryBuilder.mainStackPages(
    navHost: AppNavHost,
    currentDestination: NavDestination?,
    innerPadding: PaddingValues,
    swipeBack: NavSwipeDirection,
    articleContent: @Composable (Article) -> Unit,
    reloadBottomBarPreferences: () -> Unit,
    blocklistSettingsNlpContent: @Composable (onNavigateBack: () -> Unit) -> Unit,
) {
    loginEntry(navHost, swipeBack)
    contentMainEntries(swipeBack, articleContent)
    historyEntries(currentDestination, swipeBack)
    feedSearchEntry(swipeBack)
    contentBrowseEntries(innerPadding, swipeBack)
    accountSettings(reloadBottomBarPreferences, blocklistSettingsNlpContent, swipeBack)
    mainNotificationEntries(swipeBack)
}
