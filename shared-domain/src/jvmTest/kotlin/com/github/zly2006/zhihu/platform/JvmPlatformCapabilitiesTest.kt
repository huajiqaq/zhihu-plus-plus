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
import kotlin.test.assertNull

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

    /** 同一 key 的订阅跨 store 生效；通知时读取到已提交值，关闭订阅后不再收到后续写入。 */
    @Test
    fun keyChangesAreObservableAcrossStoresAndSubscriptionCloseIsLocal() = withTemporaryUserHome {
        val shell = desktopSettingsStore()
        val settingsPage = desktopSettingsStore()
        val shellChanges = mutableListOf<Pair<String, String>>()
        val pageChanges = mutableListOf<Pair<String, String>>()
        val shellSubscription = shell.observeKeyChanges { key ->
            shellChanges += key to shell.getString(key, "missing")
        }
        val pageSubscription = settingsPage.observeKeyChanges { key ->
            pageChanges += key to settingsPage.getString(key, "missing")
        }
        try {
            settingsPage.putString("shareActionMode", "copy")
            assertEquals(listOf("shareActionMode" to "copy"), shellChanges)
            assertEquals(shellChanges, pageChanges)

            shellSubscription.close()
            settingsPage.putString("shareActionMode", "share")
            assertEquals(listOf("shareActionMode" to "copy"), shellChanges, "关闭只影响当前订阅")
            assertEquals(listOf("shareActionMode" to "copy", "shareActionMode" to "share"), pageChanges)
            assertEquals("share", shell.getString("shareActionMode", "ask"))
        } finally {
            shellSubscription.close()
            pageSubscription.close()
        }
    }

    /** 删除必须通知存活页面；批量按前缀删除不能覆盖兄弟页面保存的无关设置。 */
    @Test
    fun removalsPublishCommittedAbsenceAndKeepUnrelatedPreferences() = withTemporaryUserHome { home ->
        val shell = desktopSettingsStore()
        shell.putString("reader-position-1", "10")
        shell.putString("reader-position-2", "20")
        shell.putString("startDestination", "Daily")
        val maintenancePage = desktopSettingsStore()
        shell.putString("shareActionMode", "copy")
        val changes = mutableListOf<Pair<String, String?>>()
        val subscription = shell.observeKeyChanges { key -> changes += key to shell.getStringOrNull(key) }
        try {
            maintenancePage.remove("reader-position-1")
            assertNull(shell.getStringOrNull("reader-position-1"))
            assertEquals(listOf<Pair<String, String?>>("reader-position-1" to null), changes)
            changes.clear()

            maintenancePage.removeByPrefix("reader-position-")
            assertNull(shell.getStringOrNull("reader-position-2"))
            assertEquals(listOf<Pair<String, String?>>("reader-position-2" to null), changes)
            assertEquals("copy", maintenancePage.getString("shareActionMode", "ask"))
            assertEquals("Daily", shell.getString("startDestination", "Home"))
            val persisted = Properties().apply {
                Files.newInputStream(home.resolve(".zhihu-plus/settings.properties")).use(::load)
            }
            assertNull(persisted.getProperty("reader-position-1"))
            assertNull(persisted.getProperty("reader-position-2"))
            assertEquals("copy", persisted.getProperty("shareActionMode"))
            assertEquals("Daily", persisted.getProperty("startDestination"))
        } finally {
            subscription.close()
        }
    }

    /** 权威 owner 必须按真实路径隔离；临时测试根不能串入另一个应用数据目录。 */
    @Test
    fun canonicalSettingsAreIsolatedByResolvedApplicationDirectory() = withTemporaryUserHome { home ->
        val first = desktopSettingsStore()
        first.putString("startDestination", "Daily")
        System.setProperty("user.home", home.resolve("other-home").toString())
        val second = desktopSettingsStore()
        assertEquals("Home", second.getString("startDestination", "Home"))
        second.putString("startDestination", "Follow")
        assertEquals("Daily", first.getString("startDestination", "Home"))
        System.setProperty("user.home", home.toString())
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
