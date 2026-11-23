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

package com.github.zly2006.zhihu.data

import io.ktor.http.Cookie
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ZhihuCookieStorageTest {
    @Test
    fun cookiesAreScopedToTheRequestedHost() = runTest {
        val storage = ZhihuCookieStorage(
            cookies = mutableMapOf(
                "z_c0" to "session-token",
                "d_c0" to "device-token",
            ),
        )

        val apiCookies = storage.get(Url("https://api.zhihu.com/topstory/recommend"))
        assertEquals(setOf("z_c0", "d_c0"), apiCookies.map { it.name }.toSet())
        assertEquals(setOf("api.zhihu.com"), apiCookies.mapNotNull { it.domain }.toSet())

        val webCookies = storage.get(Url("https://www.zhihu.com/api/v4/me"))
        assertEquals(setOf("www.zhihu.com"), webCookies.mapNotNull { it.domain }.toSet())
    }

    @Test
    fun loginCookiesStayOnZhihuHosts() = runTest {
        val cookies = mutableMapOf("z_c0" to "session-token")
        val storage = ZhihuCookieStorage(cookies)

        listOf(
            "https://redenmc.com/api/zhihu/releases/latest",
            "https://api.github.com/repos/zly2006/zhihu-plus-plus/releases/latest",
            "https://dav.example.com/zhihu-plus/backup.json",
            "https://zhihu-pics-upload.zhimg.com/v2-abc",
            "https://evilzhihu.com/",
        ).forEach { url -> assertEquals(emptyList(), storage.get(Url(url)), url) }

        storage.addCookie(Url("https://dav.example.com/"), Cookie("z_c0", "attacker"))
        storage.addCookie(Url("https://dav.example.com/"), Cookie("z_c0", "attacker", domain = ".zhihu.com"))
        storage.addCookie(Url("https://www.zhihu.com/"), Cookie("z_c0", "attacker", domain = "evilzhihu.com"))
        storage.addCookie(Url("https://www.zhihu.com/"), Cookie("z_c0", "", domain = ".zhihu.com"))
        assertEquals("session-token", cookies["z_c0"])
        storage.addCookie(Url("https://api.zhihu.com/"), Cookie("z_c0", "updated-token", domain = ".zhihu.com"))
        assertEquals("updated-token", cookies["z_c0"])
    }
}
