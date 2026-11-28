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

@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.github.zly2006.zhihu.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import com.github.zly2006.zhihu.i18n.AppStrings
import com.github.zly2006.zhihu.i18n.WidgetsStrings
import com.github.zly2006.zhihu.navigation.browserNavigationTarget
import com.github.zly2006.zhihu.navigation.isSystemContactUri
import com.github.zly2006.zhihu.platform.ExternalUrlOpener
import com.github.zly2006.zhihu.platform.LocalWebUrlOpener
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import kotlinx.browser.window
import org.w3c.dom.url.URL

@Composable
fun WebUserMessageHost(
    blockedLinkPrompt: @Composable (host: String, retry: () -> Unit, dismiss: () -> Unit) -> Unit,
    content: @Composable () -> Unit,
) = WindowUserMessageHost {
    val messages = rememberUserMessageSink()
    var pending by remember { mutableStateOf<URL?>(null) }
    val opener = remember(messages) {
        object : ExternalUrlOpener, UriHandler {
            override fun invoke(url: String) {
                val target = browserNavigationTarget(url)?.let { runCatching { URL(it) }.getOrNull() }
                if (target == null || target.protocol !in setOf("https:", "http:") || target.username.isNotEmpty() || target.password.isNotEmpty()) {
                    messages.showShortMessage(WidgetsStrings.invalidWebAddress)
                } else {
                    pending = target.takeUnless { openWebTab(it.href, confirmed = false) }
                }
            }

            override fun openUri(uri: String) {
                val value = uri.substringAfter(':', "")
                if (isSystemContactUri(uri) && value.isNotBlank() && !value.startsWith("//") && uri.none(Char::isISOControl)) {
                    /** 通信协议交给浏览器已注册的处理器，不打开网页标签。 */
                    runCatching { window.location.assign(uri) }.onFailure { messages.showShortMessage(AppStrings.linkNoApp) }
                } else {
                    invoke(uri)
                }
            }
        }
    }
    CompositionLocalProvider(LocalWebUrlOpener provides opener, LocalUriHandler provides opener) {
        content()
        pending?.let { target ->
            blockedLinkPrompt(target.host, { if (openWebTab(target.href, confirmed = true)) pending = null }, { pending = null })
        }
    }
}

/** 带 noopener 的 window.open 成功也会返回 null。先取得空白页，断开 opener 后在新页里无 referrer 地导航。 */
@JsFun(
    """(url, confirmed) => {
        if (!(navigator.userActivation?.isActive ?? confirmed)) return false;
        let tab;
        try {
            tab = window.open('', '_blank');
            if (!tab || tab.closed) return false;
            tab.opener = null;
            if (tab.opener !== null) { tab.close(); return false; }
            const link = tab.document.createElement('a');
            link.href = url;
            link.target = '_self';
            link.rel = 'noopener noreferrer';
            link.click();
            return true;
        } catch (_) {
            try { tab?.close(); } catch (_) {}
            return false;
        }
    }""",
)
private external fun openWebTab(url: String, confirmed: Boolean): Boolean
