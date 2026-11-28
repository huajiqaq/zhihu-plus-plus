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

import com.github.zly2006.zhihu.platform.MapSettingsStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ContinuousUsageSessionTest {
    @Test
    fun foregroundOnlyAndFiveMinuteContinuityPreserveAndroidPolicy() {
        val settings = MapSettingsStore()
        settings.putInt(ContinuousUsageSession.INTERVAL_KEY, 15)
        var elapsed = 0L
        var wall = 1_000_000L
        val session = ContinuousUsageSession(settings, { elapsed }, { wall })
        session.foreground()
        elapsed += 15 * 60_000L
        assertNotNull(session.consumeReminder())
        assertNull(session.consumeReminder())
        session.background()
        elapsed += 4 * 60_000L
        wall += 4 * 60_000L
        session.foreground()
        assertEquals(15 * 60_000L, session.elapsedForegroundMs())
        assertNull(session.consumeReminder())
        session.background()
        wall += 5 * 60_000L + 1
        elapsed += 5 * 60_000L + 1
        session.foreground()
        assertEquals(0L, session.elapsedForegroundMs())
        elapsed += 15 * 60_000L
        assertNotNull(session.consumeReminder())
    }
}
