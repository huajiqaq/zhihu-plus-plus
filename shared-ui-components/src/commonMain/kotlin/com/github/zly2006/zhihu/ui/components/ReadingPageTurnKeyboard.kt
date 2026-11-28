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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.zly2006.zhihu.navigation.LocalAppNavEntry
import com.github.zly2006.zhihu.platform.isSoftwareKeyboardVisible
import com.github.zly2006.zhihu.ui.LocalArticleNavHost

private val LocalReadingPageTurnKeyboardAvailable = staticCompositionLocalOf<() -> Boolean> { { false } }

/** 仅处理子控件未消费的阅读按键，不接管输入框或注册系统快捷键。 */
@Composable
fun ReadingPageTurnKeyboardHost(content: @Composable () -> Unit) {
    val dispatcher = LocalPageTurnDispatcher.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsState()
    val keyboardVisible by rememberUpdatedState(isSoftwareKeyboardVisible())
    var inputSessions by remember { mutableIntStateOf(0) }
    val inputInterceptor = remember {
        PlatformTextInputInterceptor { request, nextHandler ->
            // 外接键盘编辑时 IME 可以完全不可见，输入会话才是编辑状态的依据。
            inputSessions += 1
            try {
                nextHandler.startInputMethod(request)
            } finally {
                inputSessions -= 1
            }
        }
    }
    val isAvailable = remember(lifecycle) {
        {
            lifecycleState == Lifecycle.State.RESUMED && lifecycle.currentState == Lifecycle.State.RESUMED &&
                !keyboardVisible && inputSessions == 0
        }
    }
    val focusRequester = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    val canPageTurn = isAvailable() && dispatcher.hasKeyboardTarget
    LaunchedEffect(canPageTurn, hasFocus) {
        // 仅为完全没有 Compose 焦点的阅读窗口补兜底焦点，不从任何子控件抢焦点。
        if (isAvailable() && dispatcher.hasKeyboardTarget && !hasFocus) focusRequester.requestFocus()
    }
    InterceptPlatformTextInput(inputInterceptor) {
        CompositionLocalProvider(LocalReadingPageTurnKeyboardAvailable provides isAvailable) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onKeyEvent { event ->
                        if (!isAvailable()) {
                            false
                        } else {
                            event.pageTurnCommand()?.let(dispatcher::dispatchFromKeyboard) == true
                        }
                    }
                    .focusRequester(focusRequester)
                    .onFocusChanged { hasFocus = it.hasFocus }
                    .focusTarget(),
            ) {
                content()
            }
        }
    }
}

/** 只在正文路由启用；页面自身的 enabled 仍控制菜单和首尾切篇。 */
@Composable
fun ReadingPageTurnKeyboardScope(enabled: Boolean, content: @Composable () -> Unit) {
    val isAvailable = LocalReadingPageTurnKeyboardAvailable.current
    val navHost = LocalArticleNavHost.current
    val entryId = LocalAppNavEntry.current?.id
    val overlays = LocalAppOverlayBlurController.current
    val textToolbar = LocalTextToolbar.current
    CompositionLocalProvider(
        LocalPageTurnKeyboardEnabled provides {
            /** 缓存回答可同步换栈；分发和出队均实时核对访问 ID，不等待生命周期或重组。 */
            entryId != null && navHost?.topEntryId == entryId &&
                enabled && isAvailable() && (overlays?.openedOverlayCount ?: 0) == 0 &&
                textToolbar.status != TextToolbarStatus.Shown
        },
        content = content,
    )
}

private fun KeyEvent.pageTurnCommand(): PageTurnCommand? {
    if (type != KeyEventType.KeyDown || isCtrlPressed || isMetaPressed || isAltPressed) return null
    if (isShiftPressed) return if (key == Key.Spacebar) PageTurnCommand.PageUp else null
    return when (key) {
        Key.DirectionUp, Key.DirectionLeft, Key.PageUp -> PageTurnCommand.PageUp
        Key.DirectionDown, Key.DirectionRight, Key.PageDown, Key.Spacebar -> PageTurnCommand.PageDown
        Key.MoveHome -> PageTurnCommand.JumpToTop
        Key.MoveEnd -> PageTurnCommand.JumpToBottom
        else -> null
    }
}
