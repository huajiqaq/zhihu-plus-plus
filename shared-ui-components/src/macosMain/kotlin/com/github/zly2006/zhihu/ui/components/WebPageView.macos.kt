/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import platform.AppKit.NSApplication
import platform.AppKit.NSWindow
import platform.CoreGraphics.CGRectGetMaxY
import platform.CoreGraphics.CGRectGetMinX
import platform.CoreGraphics.CGRectMake
import platform.WebKit.WKWebView

/** Compose macOS 只将 NSWindow 暴露给 WindowScope；当前宿主只有一个可作为主窗口的窗口，浮窗不承载网页。 */
@Composable
internal actual fun AppleWebPageView(modifier: Modifier, factory: () -> WKWebView) {
    if (!rememberNavEntryOnTop()) return
    val window = remember {
        NSApplication.sharedApplication.windows
            .filterIsInstance<NSWindow>()
            .singleOrNull { it.canBecomeMainWindow() }
    }
    val parent = window?.contentView
    if (window == null || parent == null) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("当前场景没有可显示网页的主窗口") }
        return
    }
    val webView by remember(factory) { lazy(factory) }
    DisposableEffect(parent, factory) {
        parent.addSubview(webView)
        onDispose { webView.removeFromSuperview() }
    }
    Spacer(
        modifier.onGloballyPositioned { coordinates ->
            val rect = coordinates.boundsInWindow()
            val scale = window.backingScaleFactor
            val bounds = parent.bounds
            webView.setFrame(
                CGRectMake(
                    CGRectGetMinX(bounds) + rect.left / scale,
                    CGRectGetMaxY(bounds) - rect.bottom / scale,
                    rect.width / scale,
                    rect.height / scale,
                ),
            )
        },
    )
}
