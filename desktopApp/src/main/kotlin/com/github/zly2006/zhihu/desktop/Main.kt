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

package com.github.zly2006.zhihu.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.github.zly2006.zhihu.notification.LocalJvmNotificationWindow
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.ui.DesktopZhihuMain
import com.github.zly2006.zhihu.ui.components.AppStartupSplash

fun main() {
    System.setProperty("java.awt.im.style", "below-the-spot")
    val windows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    val instance = if (System.getProperty("os.name").startsWith("Linux", ignoreCase = true)) {
        try {
            DesktopNotificationInstance.tryAcquire()
        } catch (_: Exception) {
            System.err.println("无法取得本机通知租约，已禁用此窗口的系统通知；应用仍可正常使用。")
            null
        }
    } else {
        null
    }
    instance.use {
        application(exitProcessOnExit = false) {
            Window(
                onCloseRequest = ::exitApplication,
                title = "Zhihu++",
                icon = painterResource("desktop-icon.png"),
            ) {
                val wakeWindow = remember(window) {
                    {
                        if (window.isDisplayable) {
                            window.extendedState = window.extendedState and java.awt.Frame.ICONIFIED
                                .inv()
                            window.isVisible = true
                            window.toFront()
                            window.requestFocus()
                            true
                        } else {
                            false
                        }
                    }
                }
                CompositionLocalProvider(LocalJvmNotificationWindow provides wakeWindow.takeIf { windows || instance != null }) {
                    AppStartupSplash {
                        ZhihuTheme {
                            DesktopZhihuMain()
                        }
                    }
                }
            }
        }
    }
    kotlin.system.exitProcess(0)
}
