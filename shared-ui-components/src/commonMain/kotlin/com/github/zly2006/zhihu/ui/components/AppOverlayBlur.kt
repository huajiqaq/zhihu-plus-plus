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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** 仅模糊浮层下方的背景，浮层自身不参与采样。 */
class AppOverlayBlurController {
    var openedOverlayCount by mutableIntStateOf(0)
        private set

    fun onOverlayOpened() {
        openedOverlayCount += 1
    }

    fun onOverlayClosed() {
        openedOverlayCount = (openedOverlayCount - 1).coerceAtLeast(0)
    }
}

val LocalAppOverlayBlurController = staticCompositionLocalOf<AppOverlayBlurController?> { null }

@Composable
fun RegisterOverlayBlur() {
    val controller = LocalAppOverlayBlurController.current ?: return
    DisposableEffect(controller) {
        controller.onOverlayOpened()
        onDispose(controller::onOverlayClosed)
    }
}
