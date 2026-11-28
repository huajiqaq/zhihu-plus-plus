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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.zly2006.zhihu.platform.LocalWindowUserMessages
import com.github.zly2006.zhihu.platform.UserMessageSink
import kotlinx.coroutines.launch

/** 短消息随当前窗口排队和销毁；只有提示自身避让安全区，不改变页面布局。 */
@Composable
fun WindowUserMessageHost(content: @Composable () -> Unit) {
    val state = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val messages = remember(state, scope) {
        object : UserMessageSink {
            override fun showShortMessage(message: String) {
                scope.launch { state.showSnackbar(message, duration = SnackbarDuration.Short) }
            }

            override fun showLongMessage(message: String) {
                scope.launch { state.showSnackbar(message, duration = SnackbarDuration.Long) }
            }
        }
    }
    CompositionLocalProvider(LocalWindowUserMessages provides messages) {
        Box(Modifier.fillMaxSize()) {
            content()
            SnackbarHost(state, Modifier.align(Alignment.BottomCenter).safeDrawingPadding())
        }
    }
}
