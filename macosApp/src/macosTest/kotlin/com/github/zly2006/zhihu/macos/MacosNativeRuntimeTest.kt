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

import com.github.zly2006.zhihu.data.BACKGROUND_UI_DEBUG_DATA_HOME_ENV
import com.github.zly2006.zhihu.data.macosAppDataDirectoryPath
import com.github.zly2006.zhihu.platform.platformSettingsStore
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.toKString
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.posix.getenv
import platform.posix.setenv
import platform.posix.unsetenv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MacosNativeRuntimeTest {
    @Test
    fun settingsCommitOnlyAfterPersistenceAndShareNormalizedPaths() = autoreleasepool {
        val directory = "${NSTemporaryDirectory().trimEnd('/')}/zhpp-settings-${NSUUID().UUIDString}"
        val previous = getenv(BACKGROUND_UI_DEBUG_DATA_HOME_ENV)?.toKString()
        val files = NSFileManager.defaultManager
        check(files.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null))
        try {
            check(setenv(BACKGROUND_UI_DEBUG_DATA_HOME_ENV, directory, 1) == 0)
            assertEquals(directory, macosAppDataDirectoryPath())
            val settings = platformSettingsStore()
            check(setenv(BACKGROUND_UI_DEBUG_DATA_HOME_ENV, "$directory/./unused/..", 1) == 0)
            assertSame(settings, platformSettingsStore())
            settings.putString("keep", "before")
            settings.putString("prefix.one", "one")
            settings.putString("prefix.two", "two")
            val filePath = "$directory/settings.properties"
            val saved = checkNotNull(files.contentsAtPath(filePath))
            val initial = settings.allEntries()
            val changes = mutableListOf<String>()
            val subscription = settings.observeKeyChanges { changes += it }
            try {
                check(files.removeItemAtPath(filePath, error = null))
                check(files.createDirectoryAtPath(filePath, withIntermediateDirectories = false, attributes = null, error = null))
                assertFailsWith<IllegalStateException> { settings.putString("keep", "after") }
                assertFailsWith<IllegalStateException> { settings.remove("keep") }
                assertFailsWith<IllegalStateException> { settings.removeByPrefix("prefix.") }
                assertEquals(initial, settings.allEntries())
                assertTrue(changes.isEmpty())
                check(files.removeItemAtPath(filePath, error = null))
                check(files.createFileAtPath(filePath, contents = saved, attributes = null))
                settings.putString("keep", "after")
                settings.removeByPrefix("prefix.")
                assertEquals(mapOf("keep" to "after"), settings.allEntries())
                assertEquals(listOf("keep", "prefix.one", "prefix.two"), changes)
                val persisted = NSString.create(data = checkNotNull(files.contentsAtPath(filePath)), encoding = NSUTF8StringEncoding).toString()
                assertTrue(persisted.contains("keep=after\n"))
                assertTrue(!persisted.contains("prefix."))
            } finally {
                subscription.close()
            }
        } finally {
            if (previous == null) {
                check(unsetenv(BACKGROUND_UI_DEBUG_DATA_HOME_ENV) == 0)
            } else {
                check(setenv(BACKGROUND_UI_DEBUG_DATA_HOME_ENV, previous, 1) == 0)
            }
            check(files.removeItemAtPath(directory, error = null))
        }
    }
}
