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

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode
import com.github.zly2006.zhihu.util.toSegmentSourceText
import com.hrm.markdown.parser.ast.ContainerNode
import com.hrm.markdown.parser.ast.Document
import com.hrm.markdown.parser.ast.Emphasis
import com.hrm.markdown.parser.ast.HardLineBreak
import com.hrm.markdown.parser.ast.Highlight
import com.hrm.markdown.parser.ast.InlineCode
import com.hrm.markdown.parser.ast.KeyboardInput
import com.hrm.markdown.parser.ast.Link
import com.hrm.markdown.parser.ast.Node
import com.hrm.markdown.parser.ast.Paragraph
import com.hrm.markdown.parser.ast.Strikethrough
import com.hrm.markdown.parser.ast.StrongEmphasis
import com.hrm.markdown.parser.ast.StyledText
import com.hrm.markdown.parser.ast.Subscript
import com.hrm.markdown.parser.ast.Superscript
import com.hrm.markdown.parser.ast.Text
import com.fleeksoft.ksoup.nodes.Node as HtmlNode

/** 渲染器选区中的一个完整块；偏移是该块文本中的 UTF-16 半开区间。 */
data class SegmentSelectionRange(
    val paragraph: Paragraph?,
    val blockText: String,
    val startOffset: Int,
    val endOffset: Int,
)

data class ValidatedSegmentSelection(
    val paragraphId: String,
    val startOffset: Int,
    val endOffset: Int,
    val text: String,
)

/** 由转换器覆盖写入的显示块身份；不能用 PID 唯一性代替显示块身份。 */
const val SEGMENT_SELECTION_BLOCK_ATTRIBUTE = "data-source-selection-block"

/**
 * 验证同一个原始 PID 内的纯文本，显示块可有多个，无法证明对应关系时返回 null。
 * [sourceHtml] 必须是原始响应；保留 UTF-16 文本与 br 对应的 LF，不猜测未知块结构的分隔符。
 * 保留所有选区端点，不用相同文字的第一次出现猜位置；未知替换对象之后的范围仍不提交。
 */
fun validatePlainTextSegmentSelection(
    sourceHtml: String,
    document: Document,
    selectedText: String,
    ranges: List<SegmentSelectionRange>,
): ValidatedSegmentSelection? = mapPlainTextSegmentSelection(sourceHtml, document, selectedText, ranges)?.selection

/** 已有划线使用源段落偏移，选区 API 使用显示块偏移；只在同块可完整验证时展开。 */
fun expandPlainTextSegmentSelection(
    sourceHtml: String,
    document: Document,
    range: SegmentSelectionRange,
    startOffset: Int,
    endOffset: Int,
): SegmentSelectionRange? {
    if (range.startOffset < 0 || range.endOffset > range.blockText.length || range.startOffset >= range.endOffset) return null
    val mapping = mapPlainTextSegmentSelection(
        sourceHtml,
        document,
        range.blockText.substring(range.startOffset, range.endOffset),
        listOf(range),
    ) ?: return null
    val blockStart = mapping.blockStarts.single()
    val start = startOffset - blockStart
    val end = endOffset - blockStart
    if (start < 0 || end <= start || end > range.blockText.length) return null
    val expanded = range.copy(startOffset = start, endOffset = end)
    val validated = validatePlainTextSegmentSelection(
        sourceHtml,
        document,
        range.blockText.substring(start, end),
        listOf(expanded),
    ) ?: return null
    return expanded.takeIf { validated.startOffset == startOffset && validated.endOffset == endOffset }
}

private data class SegmentSelectionMapping(
    val selection: ValidatedSegmentSelection,
    val blockStarts: List<Int>,
)

private fun mapPlainTextSegmentSelection(
    sourceHtml: String,
    document: Document,
    selectedText: String,
    ranges: List<SegmentSelectionRange>,
): SegmentSelectionMapping? {
    val first = ranges.firstOrNull()?.paragraph ?: return null
    val pid = first.blockAttributes["data-pid"]?.takeIf(String::isNotBlank) ?: return null
    if (ranges.any { it.paragraph?.blockAttributes?.get("data-pid") != pid }) return null
    val allParagraphs = mutableListOf<Paragraph>()

    fun visit(node: Node) {
        if (node is Paragraph) allParagraphs += node
        if (node is ContainerNode) node.children.forEach(::visit)
    }
    visit(document)
    val documentIndex = allParagraphs.indexOfFirst { it === first }
    if (documentIndex < 0 || ranges.indices.any { allParagraphs.getOrNull(documentIndex + it) !== ranges[it].paragraph }) return null
    val paragraphs = allParagraphs.filter { it.blockAttributes["data-pid"] == pid }
    val firstIndex = paragraphs.indexOfFirst { it === first }

    val sourceParagraph = Ksoup
        .parseBodyFragment(sourceHtml)
        .select("[data-pid]")
        .filter { it.attr("data-pid") == pid }
        .singleOrNull() ?: return null
    if (sourceParagraph.tagName().lowercase() !in setOf("p", "div", "blockquote")) return null
    val source = SelectionProjection()
    sourceParagraph.childNodes().forEach { source.appendHtml(it, sourceParagraph.hasAttr("style")) }
    val sourceText = source.text.toString().toSegmentSourceText()

    var cursor = 0
    var start = -1
    var end = -1
    val selectedParts = mutableListOf<String>()
    val blockStarts = mutableListOf<Int>()
    var renderedTruncated = false
    for ((index, paragraph) in paragraphs.withIndex()) {
        if (index > firstIndex + ranges.lastIndex) break
        val rendered = SelectionProjection()
        paragraph.children.forEach { rendered.appendAst(it, formatted = false) }
        val leadingBreaks = rendered.text.takeWhile { it == '\n' }.length
        var boundaryEnd = cursor
        while (boundaryEnd in source.breaks) boundaryEnd++
        cursor += (boundaryEnd - cursor - leadingBreaks).coerceAtLeast(0)
        val length = minOf(sourceText.length - cursor, rendered.text.length)
        if (length < 0 || sourceText.substring(cursor, cursor + length) != rendered.text.substring(0, length)) return null
        if (length < rendered.text.length && !source.truncated) return null
        if (index < firstIndex) {
            if (rendered.truncated || length != rendered.text.length) return null
            cursor += length
            continue
        }
        val range = ranges[index - firstIndex]
        val from = range.startOffset
        val to = range.endOffset
        if (from < 0 || to <= from || to > range.blockText.length || to > length) return null
        if (range.blockText.splitsSurrogatePair(from) || range.blockText.splitsSurrogatePair(to)) return null
        if (!range.blockText.startsWith(rendered.text.substring(0, length))) return null
        if (!rendered.truncated && length == rendered.text.length && range.blockText.length != length) return null
        if (index > firstIndex && from != 0) return null
        if (index < firstIndex + ranges.lastIndex && (to != range.blockText.length || rendered.truncated)) return null
        if (source.formatted.any { cursor + from <= it.last && cursor + to > it.first } ||
            rendered.formatted.any { from <= it.last && to > it.first }
        ) {
            return null
        }
        selectedParts += range.blockText.substring(from, to)
        renderedTruncated = rendered.truncated
        blockStarts += cursor
        if (start < 0) start = cursor + from
        end = cursor + to
        cursor += length
    }
    if (selectedText != selectedParts.joinToString("\n") && selectedText != selectedParts.joinToString("\n\n")) return null
    if (start < 0 || end <= start || sourceText.splitsSurrogatePair(start) || sourceText.splitsSurrogatePair(end)) return null
    if (sourceText.substring(start, end).isBlank()) return null
    if (!source.truncated && !renderedTruncated && firstIndex + ranges.size == paragraphs.size && cursor != sourceText.length) {
        if ((cursor until sourceText.length).any { it !in source.breaks }) return null
    }
    return SegmentSelectionMapping(ValidatedSegmentSelection(pid, start, end, sourceText.substring(start, end)), blockStarts)
}

private fun CharSequence.splitsSurrogatePair(offset: Int): Boolean =
    offset > 0 && offset < length && this[offset - 1] in '\uD800'..'\uDBFF' && this[offset] in '\uDC00'..'\uDFFF'

private class SelectionProjection {
    val text = StringBuilder()
    val breaks = mutableSetOf<Int>()
    val formatted = mutableListOf<IntRange>()
    var truncated = false
        private set

    private fun append(value: String, isFormatted: Boolean) {
        val start = text.length
        text.append(value)
        if (isFormatted && text.length > start) formatted += start until text.length
    }

    private fun lineBreak() {
        breaks += text.length
        text.append('\n')
    }

    fun appendHtml(node: HtmlNode, isFormatted: Boolean) {
        if (truncated) return
        when (node) {
            is TextNode -> append(node.getWholeText(), isFormatted)
            is Element -> {
                val tag = node.tagName().lowercase()
                when {
                    node.hasAttr("data-pid") -> truncated = true
                    tag == "br" -> lineBreak()
                    tag in htmlTextContainers -> {
                        val segmentHighlight = tag == "span" &&
                            node.hasClass("highlight-wrap") &&
                            node.attr("data-highlight-id").isNotBlank() &&
                            node.classNames().all { it in highlightClasses } &&
                            !node.hasAttr("style")
                        node.childNodes().forEach { appendHtml(it, isFormatted || !segmentHighlight) }
                    }
                    else -> truncated = true
                }
            }
            else -> truncated = true
        }
    }

    fun appendAst(node: Node, formatted: Boolean) {
        if (truncated) return
        when (node) {
            is Text -> append(node.literal, formatted)
            is HardLineBreak -> lineBreak()
            is InlineCode -> append(node.literal, isFormatted = true)
            is KeyboardInput -> append(node.literal, isFormatted = true)
            is Highlight -> {
                val classes = node.attributes["class"]
                    .orEmpty()
                    .split(Regex("\\s+"))
                    .filter(String::isNotEmpty)
                val segmentHighlight = "highlight-wrap" in classes &&
                    classes.all { it in highlightClasses } &&
                    !node.attributes["data-highlight-id"].isNullOrBlank() &&
                    "style" !in node.attributes
                node.children.forEach { appendAst(it, formatted || !segmentHighlight) }
            }
            is StrongEmphasis, is Emphasis, is Strikethrough, is Subscript, is Superscript, is Link, is StyledText -> {
                node.children.forEach { appendAst(it, formatted = true) }
            }
            else -> truncated = true
        }
    }
}

private val htmlTextContainers = setOf(
    "span",
    "b",
    "strong",
    "i",
    "em",
    "del",
    "s",
    "strike",
    "mark",
    "sub",
    "sup",
    "a",
    "code",
    "kbd",
)
private val highlightClasses = setOf("highlight-wrap", "other", "has-comments")
