/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 * Licensed under the GNU Affero General Public License, version 3 only.
 */

package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.glass.GlassNavigationBarDefaults
import top.yukonga.miuix.kmp.glass.glassPanel
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.GlassShadows
import top.yukonga.miuix.kmp.material.GlassShape

/** 原生导航栏没有逐项 modifier；保留共享导航的标签与手势，仅使用原生导航材质。 */
@Composable
internal actual fun Modifier.miuixFloatingGlassSurface(
    shape: RoundedCornerShape,
    isDark: Boolean,
): Modifier = glassPanel(
    backdrop = checkNotNull(LocalAppBackdrop.current),
    shape = GlassShape(shape.topStart, shape.topEnd, shape.bottomEnd, shape.bottomStart),
    style = GlassDefaults.style(isDark),
    material = GlassNavigationBarDefaults.material(),
    stroke = GlassNavigationBarDefaults.stroke(),
    shadow = GlassShadows.Float,
    shading = false,
)
