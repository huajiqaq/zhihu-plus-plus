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

package com.github.zly2006.zhihu.navigation

import androidx.compose.runtime.compositionLocalOf

/** 由当前导航宿主持有的页面、返回与顶层标签入口。 */
data class Navigator(
    val onNavigate: (NavDestination) -> Unit,
    val onNavigateBack: () -> Unit,
    val onNavigateTopLevel: (TopLevelDestination) -> Unit = {},
)

/** 在组合树中共享当前导航宿主的入口，避免逐层传递回调。 */
val LocalNavigator = compositionLocalOf<Navigator> {
    error("LocalNavigator not provided")
}
