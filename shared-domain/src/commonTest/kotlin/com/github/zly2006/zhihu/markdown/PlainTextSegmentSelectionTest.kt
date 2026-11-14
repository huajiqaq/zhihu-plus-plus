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
import kotlin.test.assertNotNull
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
    fun renderingMayAddHighlightWithoutChangingTheOriginalPlainText() {
        val source = """<p data-pid="p">前重复后</p>"""
        val rendered = applySegmentInfosToHtml(
            content = source,
            segmentInfos = listOf(
                SegmentInfoParagraph(
                    pid = "p",
                    text = "前重复后",
                    marks = listOf(SegmentInfoMark(1, 3, segInfo = SegmentInfoMeta(segIds = listOf("seg")))),
                ),
            ),
        )
        val document = htmlToMdAst(rendered)
        val paragraph = document.children.single() as Paragraph

        assertEquals(ValidatedSegmentSelection("p", 1, 3, "重复"), validate(source, document, paragraph, "前重复后", 1, 3, "重复"))
    }

    @Test
    fun ordinaryFormattingOnlyRejectsOverlappingCharacters() {
        val html = """<p data-pid="p">前<strong>粗</strong><em>斜</em><a href="https://example.org">链</a><code>码</code><span data-note="keep">字</span>后</p>"""
        val document = htmlToMdAst(html)
        val paragraph = document.children.single() as Paragraph
        val text = "前粗斜链码字后"

        assertNotNull(validate(html, document, paragraph, text, 0, 1, "前"))
        assertNotNull(validate(html, document, paragraph, text, 6, 7, "后"))
        for (index in 1..5) {
            assertNull(validate(html, document, paragraph, text, index, index + 1, text.substring(index, index + 1)))
        }
        assertNull(validate(html, document, paragraph, text, 0, 2, "前粗"))
        assertNull(validate(html, document, paragraph, text, 5, 7, "字后"))
    }

    @Test
    fun existingHighlightIsMetadataButCannotHideNestedFormatting() {
        val plain = """<p data-pid="p">前<span class="highlight-wrap other has-comments" data-highlight-id="s">重复</span>后</p>"""
        val plainDocument = htmlToMdAst(plain)
        val plainParagraph = plainDocument.children.single() as Paragraph
        assertNotNull(validate(plain, plainDocument, plainParagraph, "前重复后", 1, 3, "重复"))
        assertNotNull(validate(plain, plainDocument, plainParagraph, "前重复后", 0, 4, "前重复后"))

        val formatted = """<p data-pid="p">前<span class="highlight-wrap other" data-highlight-id="s">普<strong>粗</strong></span>后</p>"""
        val formattedDocument = htmlToMdAst(formatted)
        val paragraph = formattedDocument.children.single() as Paragraph
        assertNotNull(validate(formatted, formattedDocument, paragraph, "前普粗后", 1, 2, "普"))
        assertNull(validate(formatted, formattedDocument, paragraph, "前普粗后", 2, 3, "粗"))
        assertNotNull(validate(formatted, formattedDocument, paragraph, "前普粗后", 3, 4, "后"))

        val styled = """<p data-pid="p">前<span class="highlight-wrap other" data-highlight-id="s" style="font-weight:bold">字</span>后</p>"""
        val styledDocument = htmlToMdAst(styled)
        assertNull(validate(styled, styledDocument, styledDocument.children.single() as Paragraph, "前字后", 1, 2, "字"))
    }

    @Test
    fun imageAndFormulaReplacementOnlyAllowProvenPrefix() {
        val insertions = listOf(
            """<img src="https://example.org/image.png" alt="重复">""",
            """<img src="https://www.zhihu.com/equation?tex=x" alt="x">""",
            """<br>""",
            """<sup data-draft-type="reference" data-numero="1" data-text="ref">[1]</sup>""",
        )
        for (insertion in insertions) {
            val html = """<p data-pid="p">前${insertion}重复</p>"""
            val document = htmlToMdAst(html)
            val paragraph = document.children.filterIsInstance<Paragraph>().single()
            // An altered inline object's rendered alternate text can match the following source
            // text. Equality of the selected fragment alone must not prove its position.
            assertNotNull(validate(html, document, paragraph, "前重复重复", 0, 1, "前"), insertion)
            assertNull(validate(html, document, paragraph, "前重复重复", 1, 3, "重复"), insertion)
        }
    }

    @Test
    fun requiresUniqueParagraphAndExactGeometryIncludingWholeSelection() {
        val html = """<p data-pid="p">相同</p><p data-pid="q">相同</p>"""
        val document = htmlToMdAst(html)
        val first = document.children[0] as Paragraph
        val second = document.children[1] as Paragraph
        val firstRange = SegmentSelectionRange(first, "相同", 0, 2)
        val emptyEndpoint = SegmentSelectionRange(second, "相同", 0, 0)

        assertEquals(ValidatedSegmentSelection("q", 0, 2, "相同"), validate(html, document, second, "相同", 0, 2, "相同"))
        assertNull(validatePlainTextSegmentSelection(html, document, "相同", listOf(firstRange, emptyEndpoint)))
        assertNull(validatePlainTextSegmentSelection(html, document, "相同\n", listOf(firstRange)))
        assertNull(validatePlainTextSegmentSelection(html, document, "相同", listOf(firstRange.copy(paragraph = null))))
        assertNull(validatePlainTextSegmentSelection(html, document, "相同", emptyList()))
        assertNull(validate(html, document, first, "相同", -1, 2, "相同"))
        assertNull(validate(html, document, first, "相同", 0, 3, "相同"))
        assertNull(validate(html, document, first, "相同", 2, 2, ""))
        assertNull(validate(html, document, first, "相同", 0, 2, "相"))
        assertNull(validate(html, document, first, "相同", 0, 2, " 相同"))

        val foreignParagraph = htmlToMdAst("""<p data-pid="p">相同</p>""").children.single() as Paragraph
        assertNull(validate(html, document, foreignParagraph, "相同", 0, 2, "相同"))
        val duplicate = """<p data-pid="p">相同</p><p data-pid="p">相同</p>"""
        assertNull(validate(duplicate, document, first, "相同", 0, 2, "相同"))
        val duplicateDocument = htmlToMdAst(duplicate)
        assertNull(validate(duplicate, duplicateDocument, duplicateDocument.children.first() as Paragraph, "相同", 0, 2, "相同"))
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

    @Test
    fun paragraphClassDoesNotImplyFormattingButInlineStyleDoes() {
        val html = """<p data-pid="p" class="RichContent-inner">前<strong>粗</strong>后</p>"""
        val document = htmlToMdAst(html)
        val paragraph = document.children.single() as Paragraph
        assertNotNull(validate(html, document, paragraph, "前粗后", 0, 1, "前"))
        assertNull(validate(html, document, paragraph, "前粗后", 1, 2, "粗"))
        assertNotNull(validate(html, document, paragraph, "前粗后", 2, 3, "后"))

        val styled = """<p data-pid="p" style="font-weight:bold">前后</p>"""
        val styledDocument = htmlToMdAst(styled)
        assertNull(validate(styled, styledDocument, styledDocument.children.single() as Paragraph, "前后", 0, 1, "前"))
    }

    @Test
    fun whitespaceNormalizationCannotShiftOriginalOffsets() {
        val html = """<p data-pid="p">前  重复</p>"""
        val document = htmlToMdAst(html)
        val paragraph = document.children.single() as Paragraph

        assertNull(validate(html, document, paragraph, "前 重复", 2, 4, "重复"))
        assertNull(validate(html, document, paragraph, "前 重复", 0, 1, "前"))
    }

    @Test
    fun decodedEntitiesAndAdjacentFormattingPreserveOffsets() {
        val html = """<p data-pid="p">A &amp; <b>B</b> C</p>"""
        val document = htmlToMdAst(html)
        val paragraph = document.children.single() as Paragraph

        assertEquals(ValidatedSegmentSelection("p", 2, 3, "&"), validate(html, document, paragraph, "A & B C", 2, 3, "&"))
        assertNull(validate(html, document, paragraph, "A & B C", 4, 5, "B"))
        assertEquals(ValidatedSegmentSelection("p", 6, 7, "C"), validate(html, document, paragraph, "A & B C", 6, 7, "C"))
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
