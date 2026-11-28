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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import top.yukonga.miuix.kmp.nav.core.navBackStackOf

class AppNavHostTest {
    @Test
    fun repeatedPagesRemainIndependentAndReturnInVisitOrder() {
        val host = AppNavHost(navBackStackOf(AppNavEntry.Root("root", MainTabs)))
        val answer = Article(type = ArticleType.Answer, id = 42)
        val person = Person("author", "author")
        host.open(answer)
        val first = host.topEntryId
        host.open(person)
        host.open(answer)
        assertNotEquals(first, host.topEntryId)
        host.open(answer)
        val forwardEntry = host.pageEntries.last()
        assertEquals(listOf(MainTabs, answer, person, answer, answer), host.backStack)
        assertTrue(host.back())
        assertTrue(host.back())
        assertEquals(person, host.top)
        host.back()
        assertEquals(answer, host.top)
        assertEquals(first, host.topEntryId)
        host.open(MainTabs)
        assertEquals(listOf(MainTabs), host.backStack)
        assertFalse(host.back())
        host.restore(forwardEntry)
        assertEquals(forwardEntry.id, host.topEntryId)
        assertEquals(answer, host.top)
        assertFailsWith<IllegalArgumentException> { host.restore(forwardEntry) }
        assertFailsWith<IllegalArgumentException> { host.restore(AppNavEntry.Root("other-root", MainTabs)) }
    }
}
