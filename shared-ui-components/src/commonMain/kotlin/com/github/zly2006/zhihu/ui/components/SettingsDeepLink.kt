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

package com.github.zly2006.zhihu.ui.components

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.staticCompositionLocalOf

/** 本次进入的目标由已布局的设置行领取；行移除或页面被覆盖都不重新发起定位。 */
class SettingsDeepLink internal constructor(
    private val pendingKey: MutableState<String>,
    private val titleBar: AppPageTitleBarScrollState,
) {
    val highlightedKey = pendingKey.value

    /** 程序化滚动不经过 nestedScroll，须在定位前收起顶栏。 */
    internal fun consume(): Boolean {
        if (pendingKey.value.isEmpty()) return false
        pendingKey.value = ""
        titleBar.collapse()
        return true
    }
}

val LocalSettingsDeepLink = staticCompositionLocalOf<SettingsDeepLink?> { null }
