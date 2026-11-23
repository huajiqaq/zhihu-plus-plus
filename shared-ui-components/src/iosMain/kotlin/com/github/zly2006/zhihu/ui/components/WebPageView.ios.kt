/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.github.zly2006.zhihu.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import platform.WebKit.WKWebView

@Composable
internal actual fun AppleWebPageView(modifier: Modifier, factory: () -> WKWebView) {
    UIKitView(
        factory = factory,
        modifier = modifier,
        properties = UIKitInteropProperties(isNativeAccessibilityEnabled = true),
    )
}
