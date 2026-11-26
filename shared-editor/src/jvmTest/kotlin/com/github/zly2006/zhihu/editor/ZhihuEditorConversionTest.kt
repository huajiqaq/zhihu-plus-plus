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

package com.github.zly2006.zhihu.editor

import com.fleeksoft.ksoup.Ksoup
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZhihuEditorConversionTest {
    @Test
    fun rawHtmlDoesNotAllowScriptsEventsOrDangerousLinks() = runTest {
        val result = compileMdToZhihuHtml("""<p><u onclick="alert(1)">格式内容</u><script>alert(2)</script><a href="javascript:alert(3)">链接</a><img src="https://picx.zhimg.com/a.jpg" onerror="alert(4)" data-rawwidth="420"></p>""")
        val document = Ksoup.parseBodyFragment(result)
        assertEquals("格式内容", document.selectFirst("u")?.text())
        assertTrue(document.select("script, [onclick], [onerror]").isEmpty())
        assertTrue(document.select("a").all { !it.attr("href").startsWith("javascript:") })
        assertEquals("420", document.selectFirst("img")?.attr("data-rawwidth"))
    }
}
