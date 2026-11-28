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

package com.github.zly2006.zhihu.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import com.github.zly2006.zhihu.i18n.AppLocale
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.events.Event

@Composable
internal actual fun PlatformLanguageContent(languageTag: String, content: @Composable () -> Unit) {
    DisposableEffect(Unit) {
        val listener: (Event) -> Unit = { AppLocale.refreshSystemLanguage() }
        window.addEventListener("languagechange", listener)
        onDispose { window.removeEventListener("languagechange", listener) }
    }
    SideEffect {
        document.documentElement?.setAttribute("lang", languageTag)
        document.title = if (languageTag.startsWith("zh")) "知乎++" else "Zhihu++"
    }
    content()
}
