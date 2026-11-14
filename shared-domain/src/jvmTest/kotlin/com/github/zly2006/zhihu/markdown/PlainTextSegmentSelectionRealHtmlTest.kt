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

import com.hrm.markdown.parser.ast.Paragraph
import org.jsoup.Jsoup
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlainTextSegmentSelectionRealHtmlTest {
    @Test
    fun capturedAnswerKeepsExactParagraphAndFormulaBoundary() {
        val fixture = File(requireNotNull(javaClass.classLoader.getResource("zhihu_answer_2035661632110585441_content.html")).toURI())
        val html = requireNotNull(Jsoup.parse(fixture, Charsets.UTF_8.name()).selectFirst("article.RichContent-inner")).html()
        val document = htmlToMdAst(html)
        val plain = document.children.filterIsInstance<Paragraph>().single { it.blockAttributes["data-pid"] == "UlOvk1v0" }
        val plainText = "我大概知道你想问什么，你是想说既然静态部分 E = mc^2，那为何动态部分的动能非要多个 1/2？"
        val formula = document.children.filterIsInstance<Paragraph>().single { it.blockAttributes["data-pid"] == "7uzCIVKM" }
        val beforeFormula = "要解答这个问题还得回到动能概念的起源，那时候还真就没有 "
        val renderedWithFormula = "${beforeFormula}1/2 这个拖油瓶系数。"

        assertEquals(
            ValidatedSegmentSelection("UlOvk1v0", 0, 2, "我大"),
            validatePlainTextSegmentSelection(html, document, "我大", listOf(SegmentSelectionRange(plain, plainText, 0, 2))),
        )
        assertEquals(
            ValidatedSegmentSelection("7uzCIVKM", 0, 2, "要解"),
            validatePlainTextSegmentSelection(html, document, "要解", listOf(SegmentSelectionRange(formula, renderedWithFormula, 0, 2))),
        )
        assertNull(
            validatePlainTextSegmentSelection(
                html,
                document,
                "1/2",
                listOf(SegmentSelectionRange(formula, renderedWithFormula, beforeFormula.length, beforeFormula.length + 3)),
            ),
        )
    }
}
