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

@file:OptIn(
    androidx.compose.ui.InternalComposeUiApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package com.github.zly2006.zhihu.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.backhandler.LocalCompatNavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import platform.AppKit.NSEvent
import platform.AppKit.NSResponder
import platform.AppKit.NSWindow

private const val MACOS_ESCAPE_KEY_CODE = 53

private class MacosEscapeNavigationInput : NavigationEventInput() {
    fun dispatchBack() = dispatchOnBackCompleted()
}

private class MacosEscapeNavigationResponder(
    private val input: MacosEscapeNavigationInput,
) : NSResponder() {
    override fun keyDown(event: NSEvent) {
        if (event.keyCode.toInt() == MACOS_ESCAPE_KEY_CODE && !event.isARepeat()) {
            input.dispatchBack()
        } else {
            super.keyDown(event)
        }
    }
}

/**
 * 未消费的 ESC 沿 AppKit responder chain 回退，焦点控件先处理，再派发到窗口现有 dispatcher。
 * 不另建 owner、不抢占 preview；上游补齐原生 ESC fallback 后删除本桥接，避免双派发。
 */
@Composable
fun MacosBackNavigationHost(
    window: NSWindow,
    content: @Composable () -> Unit,
) {
    val dispatcherOwner = checkNotNull(LocalCompatNavigationEventDispatcherOwner.current) {
        "Compose window navigation event dispatcher is unavailable"
    }
    val dispatcher = dispatcherOwner.navigationEventDispatcher
    val input = remember { MacosEscapeNavigationInput() }
    val responder = remember(input) { MacosEscapeNavigationResponder(input) }

    DisposableEffect(window, dispatcher, input, responder) {
        val contentView = checkNotNull(window.contentView) {
            "Compose window content view is unavailable"
        }
        val originalNextResponder = contentView.nextResponder

        dispatcher.addInput(input)
        responder.nextResponder = originalNextResponder
        contentView.nextResponder = responder

        onDispose {
            if (contentView.nextResponder === responder) {
                contentView.nextResponder = originalNextResponder
            }
            responder.nextResponder = null
            dispatcher.removeInput(input)
        }
    }

    content()
}
