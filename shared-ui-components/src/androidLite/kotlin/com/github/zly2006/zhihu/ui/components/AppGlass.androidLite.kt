/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 * Licensed under the GNU Affero General Public License, version 3 only.
 */

package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun Modifier.miuixFloatingGlassSurface(
    shape: RoundedCornerShape,
    isDark: Boolean,
): Modifier = error("HyperOS glass controls are not supported by the lite variant")
