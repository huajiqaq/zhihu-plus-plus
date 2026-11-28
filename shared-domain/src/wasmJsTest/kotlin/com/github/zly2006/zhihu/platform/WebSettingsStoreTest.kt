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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.zly2006.zhihu.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.browser.localStorage

class WebSettingsStoreTest {
    @Test
    fun usageDurationDoesNotPermitCredentialStorage() {
        val prefix = "zhihu++:test:usage-store:"
        val store = WebSettingsStore(prefix)
        val elapsed = "continuousUsageReminderSessionAccumulatedMs"
        val background = "continuousUsageReminderLastBackgroundWallClockMs"
        val interval = "continuousUsageReminderIntervalMinutes"
        val forbidden = listOf("session", "access_token", "Cookie", "webdavPassword", elapsed + "Token", "unknownSession")
        try {
            store.putLong(elapsed, 15_000)
            store.putLong(background, 20_000)
            store.putInt(interval, 15)
            assertEquals(15_000L, store.getLong(elapsed, 0))
            assertEquals(20_000L, store.getLong(background, 0))
            assertEquals(15, store.getInt(interval, 0))
            assertFailsWith<IllegalArgumentException> { store.putString(elapsed, "synthetic-not-a-duration") }
            forbidden.forEach { key ->
                assertFailsWith<IllegalArgumentException> { store.putString(key, "synthetic-value") }
                assertEquals(null, localStorage.getItem(prefix + key))
            }
        } finally {
            (forbidden + elapsed + background + interval).forEach { localStorage.removeItem(prefix + it) }
        }
    }
}
