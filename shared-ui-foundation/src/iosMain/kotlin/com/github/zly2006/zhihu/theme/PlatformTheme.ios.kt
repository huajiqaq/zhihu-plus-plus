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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.uikit.LocalUIViewController
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIUserInterfaceStyle

@Composable
actual fun currentSystemInDarkTheme(): Boolean = isSystemInDarkTheme()

@Composable
@OptIn(ExperimentalForeignApi::class)
actual fun PlatformSystemBarEffect(darkTheme: Boolean) {
    val controller = LocalUIViewController.current
    val mode = rememberThemeSnapshot(rememberSettingsStore()).value.themeMode
    DisposableEffect(controller, darkTheme, mode) {
        val previous = controller.overrideUserInterfaceStyle
        controller.overrideUserInterfaceStyle = when {
            mode == ThemeMode.SYSTEM -> UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
            darkTheme -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
            else -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
        }
        controller.setNeedsStatusBarAppearanceUpdate()
        onDispose {
            controller.overrideUserInterfaceStyle = previous
            controller.setNeedsStatusBarAppearanceUpdate()
        }
    }
}
