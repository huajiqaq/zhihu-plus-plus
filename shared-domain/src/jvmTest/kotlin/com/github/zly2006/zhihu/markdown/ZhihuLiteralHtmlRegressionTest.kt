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

package com.github.zly2006.zhihu.markdown

import com.hrm.markdown.parser.ast.FencedCodeBlock
import com.hrm.markdown.parser.ast.HtmlBlock
import com.hrm.markdown.parser.ast.InlineHtml
import com.hrm.markdown.parser.ast.Paragraph
import com.hrm.markdown.parser.ast.Text
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ZhihuLiteralHtmlRegressionTest {
    @Test
    fun real_answer_code_blank_line_should_preserve_code_and_following_paragraphs() {
        // 小玄回答 1936828177608583018，2026-10-29 真实详情响应的 content 原样投影。
        val html = requireNotNull(
            javaClass.classLoader.getResourceAsStream("zhihu_answer_1936828177608583018_content.html"),
        ).bufferedReader(Charsets.UTF_8).use { it.readText() }

        listOf(false, true).forEach { noNativeBlock ->
            val document = htmlToMdAst(html, noNativeBlock = noNativeBlock)
            val nodes = document.allNodes()
            val code = nodes.filterIsInstance<FencedCodeBlock>().single()

            assertEquals("kotlin", code.language)
            assertEquals(
                "val foo = if (a) b else c\n\nval bar = try { a } catch (_: Exception) { b }",
                code.literal,
            )
            assertFalse(nodes.any { it is HtmlBlock || it is InlineHtml })

            val paragraph = document.children.filterIsInstance<Paragraph>().single {
                it.blockAttributes["data-pid"] == "rn-EqR_X"
            }
            assertEquals(
                "不强制 Check Exception。",
                paragraph.allNodes().filterIsInstance<Text>().joinToString(separator = "") { it.literal },
            )
        }
    }
}
