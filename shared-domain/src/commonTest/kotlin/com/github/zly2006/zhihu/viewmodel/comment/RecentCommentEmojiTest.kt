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

package com.github.zly2006.zhihu.viewmodel.comment

import com.github.zly2006.zhihu.platform.MapSettingsStore
import kotlin.test.Test
import kotlin.test.assertEquals

class RecentCommentEmojiTest {
    @Test
    fun recentEmojisAreDeduplicatedByLatestUse() {
        val settings = MapSettingsStore()

        recordRecentCommentEmoji(settings, "[感谢]")
        recordRecentCommentEmoji(settings, "[哇]")
        recordRecentCommentEmoji(settings, "[感谢]")

        val recent = loadRecentCommentEmojis(settings)
        assertEquals(listOf("[感谢]", "[哇]"), recent)
    }

    @Test
    fun recentEmojisAreCappedAndSkipBlank() {
        val settings = MapSettingsStore()
        repeat(12) { index -> recordRecentCommentEmoji(settings, "[$index]") }
        recordRecentCommentEmoji(settings, "   ")

        val recent = loadRecentCommentEmojis(settings)
        assertEquals(10, recent.size)
        assertEquals("[11]", recent.first())
    }
}
