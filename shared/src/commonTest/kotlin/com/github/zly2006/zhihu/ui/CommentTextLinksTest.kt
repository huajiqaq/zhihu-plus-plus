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

package com.github.zly2006.zhihu.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class CommentTextLinksTest {
    @Test
    fun chinesePunctuationAfterUrlIsNotPartOfTheLink() {
        val matches = findPlainUrls("看这里 https://example.com/a?b=1。后面")

        assertEquals(1, matches.size)
        assertEquals("https://example.com/a?b=1", matches.single().url)
    }

    @Test
    fun multipleUrlsAreFoundInOrder() {
        val matches = findPlainUrls("A https://a.example.com B http://b.example.com/c?d=2")

        assertEquals(listOf("https://a.example.com", "http://b.example.com/c?d=2"), matches.map { it.url })
    }

    @Test
    fun textWithoutUrlProducesNoMatches() {
        assertEquals(emptyList(), findPlainUrls("没有链接的评论"))
    }
}
