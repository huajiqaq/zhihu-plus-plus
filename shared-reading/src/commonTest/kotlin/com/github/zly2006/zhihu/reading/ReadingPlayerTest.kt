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

package com.github.zly2006.zhihu.reading

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class ReadingPlayerTest {
    @AfterTest
    fun clearRegistry() {
        ReadingQueueSourceRegistry.clearForTesting()
    }

    @Test
    fun partiallyMatchingFallbackDoesNotMixDivergingOrderIntoOrigin() = runTest {
        val current = ReadingQueueItem(ReadingContentType.Answer, id = 1)
        val firstLoaded = ReadingQueueItem(ReadingContentType.Answer, id = 11)
        val secondLoaded = ReadingQueueItem(ReadingContentType.Answer, id = 12)
        ReadingQueueSourceRegistry.register(
            "question:1:answers:default",
            listOf(current, firstLoaded, secondLoaded),
        )

        val queue = ReadingQueueSourceRegistry.queueStartingAt(
            current = current,
            sourceId = "question:1:answers:default",
            limit = 5,
            fallbackAfterCurrent = listOf(
                firstLoaded,
                ReadingQueueItem(ReadingContentType.Answer, id = 99),
                ReadingQueueItem(ReadingContentType.Answer, id = 13),
            ),
        )

        assertEquals(listOf(1L, 11L, 12L), queue.map(ReadingQueueItem::id))
    }
}
