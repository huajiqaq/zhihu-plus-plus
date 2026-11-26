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

import com.github.zly2006.zhihu.data.SegmentInfoMark
import com.github.zly2006.zhihu.data.SegmentInfoMeta
import com.github.zly2006.zhihu.data.SegmentInfoParagraph
import com.github.zly2006.zhihu.util.applySegmentInfosToHtml
import com.hrm.markdown.parser.ast.Document
import com.hrm.markdown.parser.ast.Paragraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlainTextSegmentSelectionTest {
    @Test
    fun singleCharacterAndSecondRepeatedTextKeepTheirActualUtf16Offsets() {
        val html = """<p data-pid="p">重复😀重复</p>"""
        val document = htmlToMdAst(html)
        val paragraph = document.children.single() as Paragraph

        assertEquals(
            ValidatedSegmentSelection("p", 4, 6, "重复"),
            validate(html, document, paragraph, "重复😀重复", 4, 6, "重复"),
        )
        assertEquals(
            ValidatedSegmentSelection("p", 2, 4, "😀"),
            validate(html, document, paragraph, "重复😀重复", 2, 4, "😀"),
        )
        assertEquals(
            ValidatedSegmentSelection("p", 5, 6, "复"),
            validate(html, document, paragraph, "重复😀重复", 5, 6, "复"),
        )
        assertNull(validate(html, document, paragraph, "重复😀重复", 2, 3, "\uD83D"))
        assertNull(validate(html, document, paragraph, "重复😀重复", 3, 4, "\uDE00"))
    }

    @Test
    fun actualSourceFormatIsRejectedEvenWhenHighlightInjectionLostIt() {
        val sourceHtml = """<p data-pid="p">前<strong>重复</strong>后</p>"""
        val renderedHtml = applySegmentInfosToHtml(
            content = sourceHtml,
            segmentInfos = listOf(
                SegmentInfoParagraph(
                    pid = "p",
                    text = "前重复后",
                    marks = listOf(SegmentInfoMark(1, 3, segInfo = SegmentInfoMeta(segIds = listOf("seg")))),
                ),
            ),
        )
        val document = htmlToMdAst(renderedHtml)
        val paragraph = document.children.single() as Paragraph

        assertEquals(ValidatedSegmentSelection("p", 0, 1, "前"), validate(sourceHtml, document, paragraph, "前重复后", 0, 1, "前"))
        assertNull(validate(sourceHtml, document, paragraph, "前重复后", 1, 3, "重复"))
        assertEquals(ValidatedSegmentSelection("p", 3, 4, "后"), validate(sourceHtml, document, paragraph, "前重复后", 3, 4, "后"))
    }

    @Test
    fun renderedTextMustMatchSourceAndAstNotJustSelectedSubstring() {
        val html = """<p data-pid="p">甲重复乙</p>"""
        val document = htmlToMdAst(html)
        val paragraph = document.children.single() as Paragraph

        assertNull(validate(html, document, paragraph, "甲重复丙", 1, 3, "重复"))
        assertNull(validate("""<p data-pid="p">丙重复乙</p>""", document, paragraph, "甲重复乙", 1, 3, "重复"))
        assertNull(validate(html, document, paragraph, "甲重复乙多", 1, 3, "重复"))
        assertNull(validate("""<p>甲重复乙</p>""", document, paragraph, "甲重复乙", 1, 3, "重复"))
    }

    private fun validate(
        source: String,
        document: Document,
        paragraph: Paragraph,
        blockText: String,
        start: Int,
        end: Int,
        selectedText: String,
    ): ValidatedSegmentSelection? = validatePlainTextSegmentSelection(
        sourceHtml = source,
        document = document,
        selectedText = selectedText,
        ranges = listOf(SegmentSelectionRange(paragraph, blockText, start, end)),
    )
}
