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

package com.github.zly2006.zhihu.platform

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class JvmPlatformCapabilitiesTest {
    @Test
    fun siblingStoresPreserveCrossPageWritesInMemoryAndOnDisk() = withTemporaryUserHome { home ->
        val shell = desktopSettingsStore()
        val settingsPage = desktopSettingsStore()
        shell.putBoolean("appBlurEnabled", false)
        settingsPage.putString("startDestination", "Daily")

        val persisted = Properties().apply {
            Files.newInputStream(home.resolve(".zhihu-plus/settings.properties")).use(::load)
        }
        assertEquals("false", persisted.getProperty("appBlurEnabled"), "兄弟页面写入不能覆盖主壳设置")
        assertEquals("Daily", persisted.getProperty("startDestination"))
        assertFalse(settingsPage.getBoolean("appBlurEnabled", true), "已创建的设置页必须看到主壳写入")
        assertEquals("Daily", shell.getString("startDestination", "Home"), "已创建的主壳必须看到设置页写入")
        assertFalse(desktopSettingsStore().getBoolean("appBlurEnabled", true))
        assertEquals("Daily", desktopSettingsStore().getString("startDestination", "Home"))
    }

    private fun withTemporaryUserHome(block: (Path) -> Unit) = synchronized(System.getProperties()) {
        val originalUserHome = System.getProperty("user.home")
        val directory = Files.createTempDirectory("zhihu-settings-owner-")
        try {
            System.setProperty("user.home", directory.toString())
            block(directory)
        } finally {
            if (originalUserHome == null) System.clearProperty("user.home") else System.setProperty("user.home", originalUserHome)
            directory.toFile().deleteRecursively()
        }
    }
}
