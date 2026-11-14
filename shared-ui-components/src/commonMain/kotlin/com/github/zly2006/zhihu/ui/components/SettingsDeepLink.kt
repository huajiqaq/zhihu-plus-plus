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

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf

/** AppSettingsPage 持有目标键；highlightSetting 注册已组合设置行的定位器。 */
class SettingsDeepLink(
    val highlightedKey: String,
) {
    private val requesters = mutableStateMapOf<String, BringIntoViewRequester>()

    fun requesterFor(settingKey: String): BringIntoViewRequester = requesters.getOrPut(settingKey) { BringIntoViewRequester() }

    /** 目标行已经组合出来时返回它的定位器；还没出现（例如在收起的分组里）时返回 null。 */
    val targetRequester: BringIntoViewRequester?
        get() = highlightedKey.takeIf { it.isNotEmpty() }?.let { requesters[it] }
}

val LocalSettingsDeepLink = staticCompositionLocalOf<SettingsDeepLink?> { null }
