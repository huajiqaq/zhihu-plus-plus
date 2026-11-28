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

import com.github.zly2006.zhihu.platform.SettingsStore
import kotlin.time.Clock
import kotlin.time.TimeSource

private val usageClockOrigin = TimeSource.Monotonic.markNow()

/** 单个应用生命周期拥有的前台使用会话；持久键及五分钟连续使用宽限与 Android 一致。 */
class ContinuousUsageSession(
    private val settings: SettingsStore,
    private val monotonicMillis: () -> Long = { usageClockOrigin.elapsedNow().inWholeMilliseconds },
    private val wallClockMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val policy = ContinuousUsageReminderPolicy(interval())
    private var accumulatedMs = settings.getLong(ACCUMULATED_KEY, 0L).coerceAtLeast(0L)
    private var foregroundStart: Long? = null

    fun foreground() {
        if (foregroundStart == null) {
            val lastBackground = settings.getLong(BACKGROUND_KEY, 0L)
            if (shouldContinueSession(lastBackground, wallClockMillis())) {
                accumulatedMs = settings.getLong(ACCUMULATED_KEY, 0L).coerceAtLeast(0L)
            } else {
                accumulatedMs = 0L
                policy.resetSession()
                settings.putLong(ACCUMULATED_KEY, 0L)
            }
            settings.putLong(BACKGROUND_KEY, 0L)
            foregroundStart = monotonicMillis()
        }
        // 恢复前台不补发离开前已越过的提醒，与 Android 原策略一致。
        policy.updateInterval(interval(), elapsedForegroundMs())
    }

    fun background() {
        if (foregroundStart == null) return
        accumulatedMs = elapsedForegroundMs().coerceAtLeast(0L)
        foregroundStart = null
        settings.putLong(ACCUMULATED_KEY, accumulatedMs)
        settings.putLong(BACKGROUND_KEY, wallClockMillis())
    }

    fun elapsedForegroundMs(): Long = accumulatedMs + (foregroundStart?.let { monotonicMillis() - it } ?: 0L).coerceAtLeast(0L)

    fun consumeReminder(): ContinuousUsageReminder? {
        if (foregroundStart == null) return null
        val elapsed = elapsedForegroundMs()
        val interval = interval()
        if (interval != policy.intervalMinutes) policy.updateInterval(interval, elapsed)
        return policy.consumeReminder(elapsed)
    }

    private fun interval(): Int = ContinuousUsageReminderPolicy.normalizeIntervalMinutes(settings.getInt(INTERVAL_KEY, 0))

    companion object {
        const val INTERVAL_KEY = "continuousUsageReminderIntervalMinutes"
        const val CHECK_INTERVAL_MS = 10_000L
        private const val ACCUMULATED_KEY = "continuousUsageReminderSessionAccumulatedMs"
        private const val BACKGROUND_KEY = "continuousUsageReminderLastBackgroundWallClockMs"

        fun shouldContinueSession(lastBackgroundWallClockMs: Long, nowWallClockMs: Long): Boolean =
            lastBackgroundWallClockMs > 0 && nowWallClockMs >= lastBackgroundWallClockMs &&
                nowWallClockMs - lastBackgroundWallClockMs <= 5 * 60_000L
    }
}
