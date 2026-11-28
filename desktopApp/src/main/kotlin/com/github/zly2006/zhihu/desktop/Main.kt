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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.github.zly2006.zhihu.account.LocalZhihuAccountStore
import com.github.zly2006.zhihu.account.createDesktopAccountStore
import com.github.zly2006.zhihu.desktop.resources.Res
import com.github.zly2006.zhihu.desktop.resources.desktop_icon
import com.github.zly2006.zhihu.i18n.HostStrings
import com.github.zly2006.zhihu.notification.LocalJvmNotificationWindow
import com.github.zly2006.zhihu.notification.installPortableSystemNotifications
import com.github.zly2006.zhihu.platform.applySavedAppLanguage
import com.github.zly2006.zhihu.platform.platformSettingsStore
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.ui.DesktopZhihuMain
import com.github.zly2006.zhihu.ui.handlePlatformIncomingUrl
import java.awt.Desktop
import com.github.zly2006.zhihu.ui.components.AppStartupSplash
import com.github.zly2006.zhihu.ui.components.WindowUserMessageHost
import org.jetbrains.compose.resources.painterResource

fun main(args: Array<String>) {
    System.setProperty("java.awt.im.style", "below-the-spot")
    args.filter { it.startsWith("zhihu-plus-plus://", ignoreCase = true) }.forEach(::handlePlatformIncomingUrl)
    if (Desktop.isDesktopSupported()) {
        val desktop = Desktop.getDesktop()
        if (desktop.isSupported(Desktop.Action.APP_OPEN_URI)) {
            desktop.setOpenURIHandler { event -> handlePlatformIncomingUrl(event.uri.toASCIIString()) }
        }
    }
    applySavedAppLanguage(platformSettingsStore())
    val operatingSystem = System.getProperty("os.name")
    val windows = operatingSystem.startsWith("Windows", ignoreCase = true)
    val macos = operatingSystem.startsWith("Mac", ignoreCase = true)
    if (windows || macos) installPortableSystemNotifications()
    val instance = if (operatingSystem.startsWith("Linux", ignoreCase = true)) {
        try {
            DesktopNotificationInstance.tryAcquire()
        } catch (_: Exception) {
            System.err.println(HostStrings.notificationLeaseFailed)
            null
        }
    } else {
        null
    }
    instance.use {
        application(exitProcessOnExit = false) {
            val accountStore = remember { createDesktopAccountStore() }
            DisposableEffect(accountStore) { onDispose(accountStore::close) }
            Window(
                onCloseRequest = ::exitApplication,
                title = HostStrings.appName,
                icon = painterResource(Res.drawable.desktop_icon),
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
                CompositionLocalProvider(
                    LocalZhihuAccountStore provides accountStore,
                    LocalJvmNotificationWindow provides wakeWindow.takeIf { windows || macos || instance != null },
                ) {
                    AppStartupSplash {
                        ZhihuTheme {
                            WindowUserMessageHost {
                                DesktopZhihuMain()
                            }
                        }
                    }
                }
            }
        }
    }
    kotlin.system.exitProcess(0)
}
