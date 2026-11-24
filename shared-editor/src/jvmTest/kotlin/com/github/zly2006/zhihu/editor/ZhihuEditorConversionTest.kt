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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ZhihuEditorConversionTest {
    @Test
    fun editorModeRoundTripRetainsUnderlineAndImageMetadata() = runTest {
        val html = """<p><strong><img src="https://picx.zhimg.com/v2-editor.png" data-rawwidth="420" data-rawheight="280" data-watermark="original" data-watermark-src="https://picx.zhimg.com/v2-watermark.png"></strong></p><p><strong>Native</strong><em>Rich</em><u>Check</u></p>"""
        val result = compileMdToZhihuHtml(zhihuHtmlToEditorMarkdown(html))
        val document = Ksoup.parseBodyFragment(result)
        val underline = document.selectFirst("u")
        assertNotNull(underline)
        assertEquals("Check", underline.text())
        val image = document.selectFirst("img")
        assertNotNull(image)
        assertEquals("420", image.attr("data-rawwidth"))
        assertEquals("280", image.attr("data-rawheight"))
        assertEquals("original", image.attr("data-watermark"))
        assertEquals("https://picx.zhimg.com/v2-watermark.png", image.attr("data-watermark-src"))
        assertEquals("NativeRichCheck", document.select("p").last()!!.text())
    }

    @Test
    fun editsAroundPreservedHtmlAndHeadingsRemainEditable() = runTest {
        val original = "<h1>标题一</h1><h4>标题四</h4><p><u>保留格式</u></p>"
        val markdown = zhihuHtmlToEditorMarkdown(original)
        val result = compileMdToZhihuHtml("开头正文\n\n$markdown\n\n**结尾正文**")
        val document = Ksoup.parseBodyFragment(result)
        assertEquals("标题一", document.selectFirst("h1")?.text())
        assertEquals("标题四", document.selectFirst("h4")?.text())
        assertEquals("保留格式", document.selectFirst("u")?.text())
        assertTrue(document.text().contains("开头正文"))
        assertTrue(document.text().contains("结尾正文"))
        assertTrue(document.select("b, strong").any { it.text() == "结尾正文" })
    }

    @Test
    fun rawHtmlDoesNotAllowScriptsEventsOrDangerousLinks() = runTest {
        val result = compileMdToZhihuHtml("""<p><u onclick="alert(1)">格式内容</u><script>alert(2)</script><a href="javascript:alert(3)">链接</a><img src="https://picx.zhimg.com/a.jpg" onerror="alert(4)" data-rawwidth="420"></p>""")
        val document = Ksoup.parseBodyFragment(result)
        assertEquals("格式内容", document.selectFirst("u")?.text())
        assertTrue(document.select("script, [onclick], [onerror]").isEmpty())
        assertTrue(document.select("a").all { !it.attr("href").startsWith("javascript:") })
        assertEquals("420", document.selectFirst("img")?.attr("data-rawwidth"))
    }

    @Test
    fun rawHtmlWithOrdinaryMarkdownFormatsIsNotDropped() = runTest {
        val result = compileMdToZhihuHtml("前面 <u>下划线</u> 后面\n\n<blockquote><p><em>引用内容</em></p></blockquote>")
        val document = Ksoup.parseBodyFragment(result)
        assertEquals("下划线", document.selectFirst("u")?.text())
        assertEquals("引用内容", document.selectFirst("blockquote em")?.text())
    }
}
