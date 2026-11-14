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

package com.github.zly2006.zhihu.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals

class WebDavAddressTest {
    @Test
    fun should_extract_credentials_from_full_url() {
        val parsed = parseWebDavAddress("dav://user:app-pass@example.com/dav/")
        assertEquals("https://example.com/dav/", parsed.url)
        assertEquals("user", parsed.username)
        assertEquals("app-pass", parsed.password)
    }

    @Test
    fun should_decode_percent_encoded_credentials() {
        val parsed = parseWebDavAddress("https://user:p%40ss%3Aword@example.com/dav/")
        assertEquals("https://example.com/dav/", parsed.url)
        assertEquals("user", parsed.username)
        assertEquals("p@ss:word", parsed.password)
    }

    @Test
    fun should_split_user_at_first_colon_and_keep_rest_as_password() {
        val parsed = parseWebDavAddress("davs://name:pa:ss@host/dav/")
        assertEquals("name", parsed.username)
        assertEquals("pa:ss", parsed.password)
    }

    @Test
    fun should_normalize_dav_aliases_to_https() {
        assertEquals("https://host/dav/", parseWebDavAddress("dav://host/dav/").url)
        assertEquals("https://host/dav/", parseWebDavAddress("davs://host/dav/").url)
        assertEquals("https://host/dav/", parseWebDavAddress("webdav://host/dav/").url)
        assertEquals("https://host/dav/", parseWebDavAddress("webdavs://host/dav/").url)
    }

    @Test
    fun should_keep_plain_url_untouched() {
        val parsed = parseWebDavAddress("https://dav.jianguoyun.com/dav/")
        assertEquals("https://dav.jianguoyun.com/dav/", parsed.url)
        assertEquals("", parsed.username)
        assertEquals("", parsed.password)
    }

    @Test
    fun should_ignore_at_sign_in_path() {
        val parsed = parseWebDavAddress("https://host/dav/@weird/folder")
        assertEquals("https://host/dav/@weird/folder", parsed.url)
        assertEquals("", parsed.username)
    }

    @Test
    fun should_trim_surrounding_whitespace() {
        val parsed = parseWebDavAddress("  dav://u:p@host/dav/  ")
        assertEquals("https://host/dav/", parsed.url)
        assertEquals("u", parsed.username)
        assertEquals("p", parsed.password)
    }
}
