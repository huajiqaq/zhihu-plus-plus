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

@file:OptIn(ExperimentalWasmJsInterop::class)

package com.github.zly2006.zhihu.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.window.PopupProperties
import com.github.zly2006.zhihu.i18n.WidgetsStrings
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.nlp.KeywordWithWeight
import com.github.zly2006.zhihu.platform.rememberUserMessageSink

internal actual val hrmSelectionToolbarPopupProperties = PopupProperties(focusable = true)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal actual fun MacosModalBottomSheetDialog(
    onDismissRequest: () -> Unit,
    properties: ModalBottomSheetProperties,
    content: @Composable () -> Unit,
) = content()

/** 跨域页面不能提供可靠的内嵌地址和历史；由共享网页页提供新标签入口。 */
@Composable
internal actual fun rememberWebPageBrowser(initialUrl: String): WebPageBrowser? = null

actual val feedKeywordExtractionAvailable: Boolean = false

actual suspend fun extractFeedKeywords(title: String, excerpt: String?): List<KeywordWithWeight> =
    throw UnsupportedOperationException(WidgetsStrings.webKeywordExtractionUnsupported)

@Composable
actual fun rememberShareActionExecutor(): ShareActionExecutor {
    val messages = rememberUserMessageSink()
    return remember(messages) {
        object : ShareActionExecutor {
            override fun invoke(action: ShareAction, content: NavDestination, shareText: String) {
                shareInBrowser(action == ShareAction.CopyLink, shareText) { message ->
                    if (message.isNotEmpty()) messages.showShortMessage(message)
                }
            }
        }
    }
}

private fun shareInBrowser(copy: Boolean, text: String, done: (String) -> Unit): Unit = js(
    """{
        (async () => {
            try {
                if (!copy && navigator.share) {
                    await navigator.share({text});
                    done('');
                } else {
                    await navigator.clipboard.writeText(text);
                    done(copy ? '已复制链接' : '已复制分享文本');
                }
            } catch (e) {
                done(e.name === 'AbortError' ? '' : '浏览器未能完成分享或复制：' + e.message);
            }
        })();
    }""",
)
