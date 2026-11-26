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

package com.github.zly2006.zhihu.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class NavDestinationTest {
    @Test
    fun activeLinksPreferNativeContentAndOtherwiseOpenTheActualWebAddress() {
        assertEquals(Question(42), resolveNavigationUrl("https://www.zhihu.com/question/42"))
        val url = "http://example.org/story?q=two#comments"
        assertEquals(WebPage(url), resolveNavigationUrl(url))
        assertEquals(WebPage(url), resolveNavigationUrl("https://link.zhihu.com/?target=http%3A%2F%2Fexample.org%2Fstory%3Fq%3Dtwo%23comments"))
        assertEquals(null, resolveContent(url))
        listOf("file:///private/file", "javascript:alert(1)", "//example.org/story", "https:///story").forEach {
            assertEquals(null, resolveNavigationUrl(it), it)
        }
    }
}
