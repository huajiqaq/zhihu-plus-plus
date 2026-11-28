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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.github.zly2006.zhihu.i18n.WidgetsStrings
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.platform.LocalNativeFileDelivery
import com.github.zly2006.zhihu.platform.rememberPlainTextClipboard
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
actual fun rememberShareActionExecutor(): ShareActionExecutor {
    val scope = rememberCoroutineScope()
    val delivery = LocalNativeFileDelivery.current
    val copyPlainText = rememberPlainTextClipboard()
    val messages = rememberUserMessageSink()
    return remember(scope, delivery, copyPlainText, messages) {
        object : ShareActionExecutor {
            override fun invoke(action: ShareAction, content: NavDestination, shareText: String) {
                if (action == ShareAction.CopyLink) {
                    copyPlainText(WidgetsStrings.link, shareText)
                    messages.showMessage(WidgetsStrings.linkCopied)
                    return
                }
                if (delivery == null) {
                    messages.showShortMessage(WidgetsStrings.systemShareUnavailable)
                    return
                }
                scope.launch {
                    try {
                        delivery.shareText(shareText, getShareTitle(content))
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        messages.showShortMessage(WidgetsStrings.systemShareFailed)
                    }
                }
            }
        }
    }
}
