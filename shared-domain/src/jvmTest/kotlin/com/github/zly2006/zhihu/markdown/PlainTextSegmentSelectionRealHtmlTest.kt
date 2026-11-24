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
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.util.applySegmentInfosToHtml
import com.hrm.markdown.parser.ast.ContainerNode
import com.hrm.markdown.parser.ast.HardLineBreak
import com.hrm.markdown.parser.ast.Highlight
import com.hrm.markdown.parser.ast.Node
import com.hrm.markdown.parser.ast.Paragraph
import com.hrm.markdown.parser.ast.Text
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlainTextSegmentSelectionRealHtmlTest {
    @Test
    fun capturedIndependentParagraphsKeepTheirOwnPidAndRejectCrossPidSelection() {
        val html = capturedExcerpt("2098367908585985052").getValue("content").jsonPrimitive.content
        val document = htmlToMdAst(html)
        val paragraphs = document.children.filterIsInstance<Paragraph>()
        assertEquals(listOf("CLv6tlf6", "Itns88tN", "zLR7y5RR", "ejamWGL9"), paragraphs.map { it.blockAttributes["data-pid"] })
        val firstText = text(paragraphs[0])
        val secondText = text(paragraphs[1])
        assertEquals("2019年3月，熊猫直播关闭服务器。", firstText)
        assertEquals(69, secondText.length)
        assertEquals(' ', secondText.first())
        assertEquals(
            ValidatedSegmentSelection("Itns88tN", 1, 5, "公司倒了"),
            validatePlainTextSegmentSelection(html, document, "公司倒了", listOf(SegmentSelectionRange(paragraphs[1], secondText, 1, 5))),
        )
        assertNull(
            validatePlainTextSegmentSelection(
                html,
                document,
                "$firstText\n${secondText.take(5)}",
                listOf(
                    SegmentSelectionRange(paragraphs[0], firstText, 0, firstText.length),
                    SegmentSelectionRange(paragraphs[1], secondText, 0, 5),
                ),
            ),
        )
    }

    @Test
    fun capturedZeroWidthSpaceKeepsServerTextAndOffsetsBeforeAndAfterInjection() {
        val answer = capturedExcerpt("2098367908585985052")
        val html = answer.getValue("content").jsonPrimitive.content
        val segment = ZhihuJson.decodeJson<SegmentInfoParagraph>(answer.getValue("segment_infos").jsonArray.single())
        val mark = segment.marks.single()
        val rawText = Jsoup.parseBodyFragment(html).selectFirst("p[data-pid=${segment.pid}]")!!.wholeText()
        assertEquals('\u200B', rawText.first())
        assertEquals(' ', segment.text.first())
        assertEquals(rawText.length, segment.text.length)
        assertEquals(" " + rawText.substring(1), segment.text)
        assertEquals(69, mark.startIndex)
        assertEquals(91, mark.endIndex)
        val selectedText = segment.text.substring(mark.startIndex, mark.endIndex)
        val highlightedHtml = applySegmentInfosToHtml(html, listOf(segment))
        val highlightedDocument = htmlToMdAst(highlightedHtml)
        val highlightedParagraph = highlightedDocument.children.filterIsInstance<Paragraph>().single { it.blockAttributes["data-pid"] == segment.pid }
        val highlight = highlightedParagraph.children.filterIsInstance<Highlight>().single()
        assertEquals("69", highlight.attributes["data-highlight-start-offset"])
        assertEquals("91", highlight.attributes["data-highlight-end-offset"])
        assertEquals(selectedText, text(highlight))

        for (document in listOf(htmlToMdAst(html), highlightedDocument)) {
            val paragraph = document.children.filterIsInstance<Paragraph>().single { it.blockAttributes["data-pid"] == segment.pid }
            assertEquals(segment.text, text(paragraph))
            val range = SegmentSelectionRange(paragraph, text(paragraph), mark.startIndex, mark.endIndex)
            assertEquals(
                ValidatedSegmentSelection(segment.pid, 0, 3, " “熊"),
                validatePlainTextSegmentSelection(html, document, " “熊", listOf(range.copy(startOffset = 0, endOffset = 3))),
            )
            assertNull(validatePlainTextSegmentSelection(html, document, " ", listOf(range.copy(startOffset = 0, endOffset = 1))))
            assertEquals(
                ValidatedSegmentSelection(segment.pid, mark.startIndex, mark.endIndex, selectedText),
                validatePlainTextSegmentSelection(html, document, selectedText, listOf(range)),
            )
            assertEquals(
                range,
                expandPlainTextSegmentSelection(html, document, range.copy(startOffset = 70, endOffset = 71), 69, 91),
            )
        }
    }

    @Test
    fun capturedLongParagraphDoubleBrKeepsRangesAndLaterSelectionsOnOnePid() {
        val html = capturedExcerpt("2106819095878448312").getValue("content").jsonPrimitive.content
        val sourceText = Jsoup.parseBodyFragment(html).selectFirst("p[data-pid=miZQGQZN]")!!.wholeText()
        assertEquals(229, sourceText.length)
        val selections = listOf(
            ValidatedSegmentSelection("miZQGQZN", 84, 96, "I相关。\n\n但是显然，按"),
            ValidatedSegmentSelection("miZQGQZN", 90, 94, "但是显然"),
            ValidatedSegmentSelection("miZQGQZN", 185, 197, "的情况。\n\n当然，我也反"),
            ValidatedSegmentSelection("miZQGQZN", 191, 197, "当然，我也反"),
            ValidatedSegmentSelection("miZQGQZN", 224, 229, "考虑到呢？"),
        )

        /** 原响应无 segment_infos；用真实原文上的两处范围验证注入不会丢失连续 br。 */
        val highlightedHtml = applySegmentInfosToHtml(
            html,
            listOf(
                SegmentInfoParagraph(
                    pid = "miZQGQZN",
                    text = sourceText,
                    marks = listOf(selections[0], selections[2]).map {
                        SegmentInfoMark(it.startOffset, it.endOffset, segInfo = SegmentInfoMeta(segIds = listOf("test-${it.startOffset}")))
                    },
                ),
            ),
        )
        val highlighted = Jsoup.parseBodyFragment(highlightedHtml)
        assertEquals(4, highlighted.select("br").size)
        assertEquals(listOf("84", "185"), highlighted.select("span.highlight-wrap").map { it.attr("data-highlight-start-offset") })
        for (renderedHtml in listOf(html, highlightedHtml)) {
            val document = htmlToMdAst(renderedHtml)
            val paragraph = assertIs<Paragraph>(document.children.single())
            val renderedText = text(paragraph)
            assertEquals("miZQGQZN", paragraph.blockAttributes["data-pid"])
            assertEquals(sourceText, renderedText)
            for (selection in selections) {
                assertEquals(
                    selection,
                    validatePlainTextSegmentSelection(
                        html,
                        document,
                        selection.text,
                        listOf(SegmentSelectionRange(paragraph, renderedText, selection.startOffset, selection.endOffset)),
                    ),
                )
            }
        }
    }

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

    @Test
    fun capturedAnswerBrKeepsServerTextAndMarkOffsets() {
        val page = ZhihuJson.json
            .parseToJsonElement(
                requireNotNull(javaClass.getResource("/home-follow/follow-dynamic-page-0-sanitized.json")).readText(),
            ).jsonObject
        val answer = page
            .getValue("data")
            .jsonArray
            .mapNotNull { it.jsonObject["target"]?.jsonObject }
            .single { it["id"]?.jsonPrimitive?.content == "2015880959820572573" }
        val segment = answer
            .getValue("segment_infos")
            .jsonArray
            .map { ZhihuJson.decodeJson<SegmentInfoParagraph>(it) }
            .single { it.pid == "01Rqb9ww" }
        val mark = segment.marks.single { it.startIndex == 29 && it.endIndex == 175 }
        val html = answer.getValue("content").jsonPrimitive.content
        val document = htmlToMdAst(html)
        val paragraph = document.children.filterIsInstance<Paragraph>().single { it.blockAttributes["data-pid"] == segment.pid }

        assertEquals(3, paragraph.children.size)
        val beforeBreak = assertIs<Text>(paragraph.children[0]).literal
        assertIs<HardLineBreak>(paragraph.children[1])
        val afterBreak = assertIs<Text>(paragraph.children[2]).literal
        val renderedText = "$beforeBreak\n$afterBreak"
        assertEquals(segment.text.indexOf('\n'), beforeBreak.length)
        assertEquals(segment.text, renderedText)

        val selectedText = segment.text.substring(mark.startIndex, mark.endIndex)
        assertTrue(selectedText.contains('\n'))
        assertEquals(
            ValidatedSegmentSelection(segment.pid, mark.startIndex, mark.endIndex, selectedText),
            validatePlainTextSegmentSelection(
                html,
                document,
                selectedText,
                listOf(SegmentSelectionRange(paragraph, renderedText, mark.startIndex, mark.endIndex)),
            ),
        )
        val highlightedHtml = applySegmentInfosToHtml(html, listOf(segment))
        assertEquals(segment.text, Jsoup.parseBodyFragment(highlightedHtml).selectFirst("p[data-pid=${segment.pid}]")!!.wholeText())
        val highlightedDocument = htmlToMdAst(highlightedHtml)
        val highlightedParagraph = highlightedDocument.children.filterIsInstance<Paragraph>().single { it.blockAttributes["data-pid"] == segment.pid }

        assertEquals(segment.text, text(highlightedParagraph))
        val highlight = highlightedParagraph.children.filterIsInstance<Highlight>().single { it.attributes["data-highlight-start-offset"] == "29" }
        assertEquals("175", highlight.attributes["data-highlight-end-offset"])
        assertEquals(selectedText, text(highlight))
        assertEquals(
            ValidatedSegmentSelection(segment.pid, mark.startIndex, mark.endIndex, selectedText),
            validatePlainTextSegmentSelection(html, highlightedDocument, selectedText, listOf(SegmentSelectionRange(highlightedParagraph, segment.text, mark.startIndex, mark.endIndex))),
        )
    }

    /** 首条回答保留前四个完整 p；第二条仅保留同一 p 中前三个 brbr 视觉段，正文与 PID 不改写。 */
    private fun capturedExcerpt(id: String) = ZhihuJson.json
        .parseToJsonElement(requireNotNull(javaClass.getResource("/real-api/segment-source-excerpts.json")).readText())
        .jsonArray
        .map { it.jsonObject }
        .single { it.getValue("id").jsonPrimitive.content == id }

    private fun text(node: Node): String = when (node) {
        is Text -> node.literal
        is HardLineBreak -> "\n"
        is ContainerNode -> node.children.joinToString("") { text(it) }
        else -> error("意外替换对象：$node")
    }
}
