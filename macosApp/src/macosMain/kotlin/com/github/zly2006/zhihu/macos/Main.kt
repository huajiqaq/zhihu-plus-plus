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

@file:OptIn(kotlinx.cinterop.BetaInteropApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package com.github.zly2006.zhihu.macos

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.window.Window
import com.github.zly2006.zhihu.account.LocalZhihuAccountStore
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.account.createNativeAccountStore
import com.github.zly2006.zhihu.i18n.HostStrings
import com.github.zly2006.zhihu.notification.installAppleSystemNotificationDelegate
import com.github.zly2006.zhihu.platform.MacosBackNavigationHost
import com.github.zly2006.zhihu.platform.NativeFileDeliveryHost
import com.github.zly2006.zhihu.platform.applySavedAppLanguage
import com.github.zly2006.zhihu.platform.isMacosQuitOnWindowCloseEnabled
import com.github.zly2006.zhihu.platform.platformSettingsStore
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.ui.MacosZhihuMain
import com.github.zly2006.zhihu.ui.components.AppStartupSplash
import com.github.zly2006.zhihu.ui.components.MacosUserMessageHost
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.setUnhandledExceptionHook
import kotlinx.cinterop.autoreleasepool
import platform.AppKit.NSApplication
import platform.AppKit.NSApplicationActivationPolicy
import platform.AppKit.NSApplicationDelegateProtocol
import platform.AppKit.NSImage
import platform.AppKit.NSWindow
import platform.AppKit.NSWindowDelegateProtocol
import platform.Foundation.NSBundle
import platform.Foundation.NSNotification
import platform.darwin.NSObject

@OptIn(ExperimentalNativeApi::class)
fun main() {
    setUnhandledExceptionHook { error ->
        runCatching {
            println("Unhandled coroutine exception: ${error.message ?: error::class.simpleName}")
            error.printStackTrace()
        }
    }

    autoreleasepool {
        applySavedAppLanguage(platformSettingsStore())
        val application = NSApplication.sharedApplication()
        application.setActivationPolicy(NSApplicationActivationPolicy.NSApplicationActivationPolicyRegular)
        val accountStore = createNativeAccountStore()
        val applicationDelegate = MacosApplicationDelegate(application, accountStore)
        application.delegate = applicationDelegate
        installAppleSystemNotificationDelegate()
        NSBundle.mainBundle
            .pathForResource("desktop-icon", ofType = "png")
            ?.let { iconPath -> NSImage(contentsOfFile = iconPath) }
            ?.let(application::setApplicationIconImage)
        Window(
            title = HostStrings.appName,
        ) {
            val title = HostStrings.appName
            SideEffect { if (window.title != title) window.title = title }
            applicationDelegate.window = window
            window.delegate = applicationDelegate
            MacosBackNavigationHost(window) {
                AppStartupSplash {
                    ZhihuTheme {
                        CompositionLocalProvider(LocalZhihuAccountStore provides accountStore) {
                            MacosUserMessageHost {
                                NativeFileDeliveryHost(window) {
                                    MacosZhihuMain(
                                        onNotificationOpen = {
                                            if (window.miniaturized) window.deminiaturize(null)
                                            window.makeKeyAndOrderFront(null)
                                            application.activateIgnoringOtherApps(true)
                                        },
                                    ) { chrome, content ->
                                        MacosNativeWindowChrome(
                                            window = window,
                                            chrome = chrome,
                                            content = content,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        application.activateIgnoringOtherApps(true)
        application.run()
    }
}

private class MacosApplicationDelegate(
    private val application: NSApplication,
    private val accountStore: ZhihuAccountStore,
) : NSObject(),
    NSApplicationDelegateProtocol,
    NSWindowDelegateProtocol {
    var window: NSWindow? = null

    override fun applicationWillTerminate(notification: NSNotification) {
        accountStore.close()
    }

    override fun applicationShouldHandleReopen(
        sender: NSApplication,
        hasVisibleWindows: Boolean,
    ): Boolean {
        if (!hasVisibleWindows) {
            window?.let {
                it.makeKeyAndOrderFront(null)
                application.activateIgnoringOtherApps(true)
            }
        }
        return true
    }

    override fun windowShouldClose(sender: NSWindow): Boolean {
        if (isMacosQuitOnWindowCloseEnabled()) {
            application.terminate(null)
            return false
        }
        sender.orderOut(null)
        return false
    }
}
