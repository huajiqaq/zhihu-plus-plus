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

import android.app.AlertDialog
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.platform.androidSettingsStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Android 仍由原 Activity 前后台事件驱动，时钟与系统对话框语义不变。 */
class ContinuousUsageReminderManager(private val activity: ComponentActivity) {
    private val session = ContinuousUsageSession(androidSettingsStore(activity), SystemClock::elapsedRealtime, System::currentTimeMillis)
    private var checkJob: Job? = null
    private var reminderDialog: AlertDialog? = null

    init { current = this }

    fun onAppForeground() {
        session.foreground()
        if (checkJob?.isActive != true) {
            checkJob = activity.lifecycleScope.launch {
                while (isActive) {
                    delay(ContinuousUsageSession.CHECK_INTERVAL_MS)
                    checkAndShowReminder()
                }
            }
        }
        checkAndShowReminder()
    }

    fun onAppBackground() {
        checkJob?.cancel()
        checkJob = null
        session.background()
        reminderDialog?.dismiss()
        reminderDialog = null
    }

    fun onDestroy() {
        checkJob?.cancel()
        checkJob = null
        reminderDialog?.dismiss()
        reminderDialog = null
        if (current === this) current = null
    }

    fun currentElapsedForegroundMs(): Long = session.elapsedForegroundMs()

    private fun checkAndShowReminder() {
        val reminder = session.consumeReminder() ?: return
        if (activity.isFinishing || activity.isDestroyed || reminderDialog?.isShowing == true) return
        reminderDialog = AlertDialog.Builder(activity)
            .setTitle(DomainStrings.breakReminder)
            .setMessage(DomainStrings.breakReminderTime(reminder.durationText))
            .setPositiveButton(DomainStrings.gotIt, null)
            .show()
    }

    companion object {
        private var current: ContinuousUsageReminderManager? = null
        fun currentElapsedForegroundMs(): Long = current?.currentElapsedForegroundMs() ?: 0L
        const val KEY_CONTINUOUS_USAGE_REMINDER_INTERVAL_MINUTES = ContinuousUsageSession.INTERVAL_KEY
        internal fun shouldContinueSession(lastBackgroundWallClockMs: Long, nowWallClockMs: Long): Boolean =
            ContinuousUsageSession.shouldContinueSession(lastBackgroundWallClockMs, nowWallClockMs)
    }
}
