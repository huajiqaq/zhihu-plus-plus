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

fun formatTopicCount(raw: String): String {
    val value = raw.toLongOrNull() ?: return raw

    fun scaled(divisor: Double, unit: String): String {
        val number = (value / divisor * 10).toLong() / 10.0
        val text = if (number % 1.0 == 0.0) number.toLong().toString() else number.toString()
        return "$text $unit"
    }
    return when {
        value >= 100_000_000 -> scaled(100_000_000.0, "亿")
        value >= 10_000 -> scaled(10_000.0, "万")
        else -> value.toString()
    }
}
