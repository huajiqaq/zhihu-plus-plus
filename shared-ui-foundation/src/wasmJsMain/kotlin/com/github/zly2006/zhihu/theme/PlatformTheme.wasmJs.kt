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

package com.github.zly2006.zhihu.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

@Composable
actual fun currentSystemInDarkTheme(): Boolean = isSystemInDarkTheme()

@Composable
actual fun platformDynamicThemeSeedColor(): Color? = null

@Composable
actual fun PlatformSystemBarEffect(darkTheme: Boolean) {
    DisposableEffect(darkTheme) {
        val style = (document.documentElement as? HTMLElement)?.style
        val previous = style?.getPropertyValue("color-scheme").orEmpty()
        style?.setProperty("color-scheme", if (darkTheme) "dark" else "light")
        onDispose {
            if (previous.isEmpty()) style?.removeProperty("color-scheme") else style?.setProperty("color-scheme", previous)
        }
    }
}
