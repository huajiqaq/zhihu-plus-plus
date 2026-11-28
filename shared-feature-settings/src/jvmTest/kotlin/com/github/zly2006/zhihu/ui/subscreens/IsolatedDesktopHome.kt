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

package com.github.zly2006.zhihu.ui.subscreens

import com.github.zly2006.zhihu.platform.desktopSettingsStore
import com.github.zly2006.zhihu.theme.ThemeStyle
import java.nio.file.Files

/** 把 user.home 指到临时目录并固定 M3E 皮肤：页面读写的设置和主题都不碰开发机上的真实文件。 */
internal fun withIsolatedDesktopHome(block: () -> Unit) {
    val originalUserHome = System.getProperty("user.home")
    val home = Files.createTempDirectory("zhihu-settings-ui-")
    try {
        System.setProperty("user.home", home.toString())
        desktopSettingsStore().putString("uiThemeStyle", "MATERIAL_EXPRESSIVE")
        block()
    } finally {
        System.setProperty("user.home", originalUserHome)
        home.toFile().deleteRecursively()
    }
}
