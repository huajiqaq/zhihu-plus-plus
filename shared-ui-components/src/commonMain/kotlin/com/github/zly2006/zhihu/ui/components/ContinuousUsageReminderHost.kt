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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.util.ContinuousUsageReminder
import com.github.zly2006.zhihu.util.ContinuousUsageSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** 每个应用根只装配一次。浏览器传入 visibilityState/pagehide 真值，窗口宿主使用生命周期。 */
@Composable
fun ContinuousUsageReminderHost(active: Boolean? = null) {
    val settings = rememberSettingsStore()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val foreground = active ?: (lifecycle == Lifecycle.State.RESUMED)
    val session = remember(settings) { ContinuousUsageSession(settings) }
    var reminder by remember { mutableStateOf<ContinuousUsageReminder?>(null) }
    DisposableEffect(session, foreground) {
        if (foreground) session.foreground() else {
            session.background()
            reminder = null
        }
        onDispose { session.background() }
    }
    LaunchedEffect(session, foreground) {
        if (foreground) {
            while (isActive) {
                val next = session.consumeReminder()
                if (next != null && reminder == null) reminder = next
                delay(ContinuousUsageSession.CHECK_INTERVAL_MS)
            }
        }
    }
    reminder?.let { current ->
        AppDialog(
            title = DomainStrings.breakReminder,
            summary = DomainStrings.breakReminderTime(current.durationText),
            confirmText = DomainStrings.gotIt,
            onConfirm = { reminder = null },
            onDismissRequest = { reminder = null },
        )
    }
}
