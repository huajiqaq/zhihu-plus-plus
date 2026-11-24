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

package com.github.zly2006.zhihu.util

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge

@Suppress("NOTHING_TO_INLINE")
inline fun ComponentActivity.enableEdgeToEdgeCompat() {
    enableEdgeToEdge()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Android 16 的强制对比度会在透明状态栏上叠加深色遮罩，改变应用表面颜色。
        window.isStatusBarContrastEnforced = false
        // 三键导航的对比度遮罩会阻碍透明边到边显示，参见 https://issuetracker.google.com/issues/298296168。
        window.isNavigationBarContrastEnforced = false
    }
}
