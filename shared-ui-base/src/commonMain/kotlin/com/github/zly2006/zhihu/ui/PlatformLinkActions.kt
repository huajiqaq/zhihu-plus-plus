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

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.github.zly2006.zhihu.i18n.PlatformStrings
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.resolveContent
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.ui.components.SettingItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

@Composable
internal expect fun rememberPlatformClipboardReader(): suspend () -> String?

/** 只在用户点击后访问剪贴板，系统授权失败保持可重试，不自动外开普通网址。 */
@Composable
fun PlatformLinkActions() {
    val read = rememberPlatformClipboardReader()
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val messages = rememberUserMessageSink()
    SettingItem(
        title = { Text(PlatformStrings.openClipboardLink) },
        onClick = {
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    val destination = read()?.trim()?.takeIf { it.length <= 8192 }?.let(::resolveContent)
                    if (destination == null) messages.showShortMessage(PlatformStrings.clipboardLinkUnavailable)
                    else navigator.onNavigate(destination)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    messages.showShortMessage(PlatformStrings.clipboardLinkUnavailable)
                }
            }
        },
    )
}
