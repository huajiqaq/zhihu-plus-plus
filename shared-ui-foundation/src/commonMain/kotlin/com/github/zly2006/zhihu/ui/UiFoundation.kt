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

import com.github.zly2006.zhihu.i18n.AppLanguage
import com.github.zly2006.zhihu.i18n.AppLocale
import com.github.zly2006.zhihu.i18n.WidgetsStrings

fun formatTopicCount(raw: String): String {
    val value = raw.toLongOrNull() ?: return raw
    val base = when (AppLocale.current) {
        AppLanguage.Chinese -> 10_000L
        AppLanguage.English -> 1_000L
    }
    fun scaled(divisor: Long): String {
        val whole = value / divisor
        val tenth = value % divisor / (divisor / 10)
        return if (tenth == 0L) whole.toString() else "$whole.$tenth"
    }
    return when {
        value >= base * base * base -> WidgetsStrings.topicCountCompactHigh(scaled(base * base * base))
        value >= base * base -> WidgetsStrings.topicCountCompactMiddle(scaled(base * base))
        value >= base -> WidgetsStrings.topicCountCompactLow(scaled(base))
        else -> value.toString()
    }
}
