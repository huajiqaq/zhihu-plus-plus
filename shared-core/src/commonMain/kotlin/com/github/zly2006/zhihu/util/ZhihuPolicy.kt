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

package com.github.zly2006.zhihu.util

import com.github.zly2006.zhihu.i18n.CoreStrings

data class ContinuousUsageReminder(
    val elapsedForegroundMs: Long,
) {
    val durationText: String
        get() {
            val totalMinutes = (elapsedForegroundMs / 60_000L).coerceAtLeast(1L)
            val hours = totalMinutes / 60L
            val minutes = totalMinutes % 60L
            return when {
                hours == 0L -> CoreStrings.durationMinutes(minutes)
                minutes == 0L -> CoreStrings.durationHours(hours)
                else -> CoreStrings.durationHoursMinutes(CoreStrings.durationHours(hours), CoreStrings.durationMinutes(minutes))
            }
        }
}

class ContinuousUsageReminderPolicy(
    intervalMinutes: Int,
) {
    var intervalMinutes: Int = normalizeIntervalMinutes(intervalMinutes)
        private set

    val isEnabled: Boolean
        get() = intervalMinutes > 0

    private var lastReminderBucket: Int = 0

    fun consumeReminder(elapsedForegroundMs: Long): ContinuousUsageReminder? {
        if (!isEnabled || elapsedForegroundMs <= 0L) return null

        val intervalMs = intervalMinutes * ONE_MINUTE_MS
        val currentBucket = (elapsedForegroundMs / intervalMs).toInt()
        if (currentBucket <= lastReminderBucket) return null

        lastReminderBucket = currentBucket
        return ContinuousUsageReminder(
            elapsedForegroundMs = elapsedForegroundMs,
        )
    }

    fun updateInterval(intervalMinutes: Int, elapsedForegroundMs: Long) {
        this.intervalMinutes = normalizeIntervalMinutes(intervalMinutes)
        if (!isEnabled) {
            lastReminderBucket = 0
            return
        }

        val intervalMs = this.intervalMinutes * ONE_MINUTE_MS
        lastReminderBucket = (elapsedForegroundMs / intervalMs).toInt()
    }

    fun resetSession() {
        lastReminderBucket = 0
    }

    companion object {
        private const val ONE_MINUTE_MS = 60_000L

        val SUPPORTED_INTERVALS_MINUTES = setOf(15, 30, 60)

        fun normalizeIntervalMinutes(intervalMinutes: Int): Int = if (intervalMinutes in SUPPORTED_INTERVALS_MINUTES) {
            intervalMinutes
        } else {
            0
        }
    }
}
