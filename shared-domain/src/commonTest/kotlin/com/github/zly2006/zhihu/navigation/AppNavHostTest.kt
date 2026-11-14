package com.github.zly2006.zhihu.navigation

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.yukonga.miuix.kmp.nav.core.navBackStackOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

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
    }

    @Test
    fun settingsAndSearchVisitsKeepIdentityAfterSerialization() {
        val host = AppNavHost(navBackStackOf(AppNavEntry.Root("root", MainTabs)))
        listOf(Account.AppearanceSettings(), Search("同一搜索"), Account.AppearanceSettings(), Search("同一搜索")).forEach(host::open)
        val entries = host.entries.map { it as AppNavEntry }
        val restored = Json.decodeFromString<List<AppNavEntry>>(Json.encodeToString(entries))
        assertEquals(entries, restored)
        assertEquals(entries.size, restored.map { it.id }.distinct().size)
        assertEquals(host.backStack, restored.map { it.destination })
    }
}
