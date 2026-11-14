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
import com.hrm.markdown.parser.ast.ContainerNode
import com.hrm.markdown.parser.ast.Document
import com.hrm.markdown.parser.ast.Emphasis
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

/**
 * 验证原始正文中的单段纯文本；无法证明时返回 null。
 *
 * [sourceHtml] 须为未执行 `applySegmentInfosToHtml` 的原始响应正文，[document] 为当前完整渲染 AST。
 * 按渲染器块身份解析 [SegmentSelectionRange.paragraph]，保留所有区间（含未解析块、空端点），
 * 不按文字搜索偏移。HRM 的 [selectedText] 须取 `MarkdownSelectionInfo.text`，不能以区间子串
 * 代替，否则会漏掉跨块空端点。
 *
 * 图片、公式、换行等替换对象会使偏移失真，仅接受其前可精确验证的前缀。
 */
fun validatePlainTextSegmentSelection(
    sourceHtml: String,
    document: Document,
    selectedText: String,
    ranges: List<SegmentSelectionRange>,
): ValidatedSegmentSelection? {
    val range = ranges.singleOrNull() ?: return null
    val paragraph = range.paragraph ?: return null
    val pid = paragraph.blockAttributes["data-pid"]?.takeIf(String::isNotBlank) ?: return null
    val start = range.startOffset
    val end = range.endOffset
    if (start < 0 || end <= start || end > range.blockText.length) return null
    if (range.blockText.substring(start, end) != selectedText) return null
    if (range.blockText.splitsSurrogatePair(start) || range.blockText.splitsSurrogatePair(end)) return null
    if (range.blockText.substring(0, end).any { it in "\n\r\t\u000c" }) return null

    val matchingParagraphs = mutableListOf<Paragraph>()

    fun visit(node: Node) {
        if (node is Paragraph && node.blockAttributes["data-pid"] == pid) matchingParagraphs += node
        if (node is ContainerNode) node.children.forEach(::visit)
    }
    visit(document)
    if (matchingParagraphs.size != 1 || matchingParagraphs.single() !== paragraph) return null

    val sourceParagraphs = Ksoup
        .parseBodyFragment(sourceHtml)
        .select("p[data-pid]")
        .filter { it.attr("data-pid") == pid }
    val sourceParagraph = sourceParagraphs.singleOrNull() ?: return null

    val source = SelectionProjection()
    val paragraphFormatted = sourceParagraph.hasAttr("style")
    sourceParagraph.childNodes().forEach { source.appendHtml(it, paragraphFormatted) }
    val rendered = SelectionProjection()
    paragraph.children.forEach { rendered.appendAst(it, formatted = false) }

    val prefixLength = minOf(source.text.length, rendered.text.length)
    if (end > prefixLength) return null
    if (source.text.substring(0, prefixLength) != rendered.text.substring(0, prefixLength)) return null
    if (!range.blockText.startsWith(source.text.substring(0, prefixLength))) return null
    if (!source.truncated &&
        !rendered.truncated &&
        (source.text.length != rendered.text.length || range.blockText.length != source.text.length)
    ) {
        return null
    }
    if (source.text.splitsSurrogatePair(start) || source.text.splitsSurrogatePair(end)) return null
    if (source.formatted.any { start < it.last + 1 && end > it.first }) return null
    if (rendered.formatted.any { start < it.last + 1 && end > it.first }) return null

    return ValidatedSegmentSelection(pid, start, end, selectedText)
}

private fun CharSequence.splitsSurrogatePair(offset: Int): Boolean =
    offset > 0 && offset < length && this[offset - 1] in '\uD800'..'\uDBFF' && this[offset] in '\uDC00'..'\uDFFF'

private class SelectionProjection {
    val text = StringBuilder()
    val formatted = mutableListOf<IntRange>()
    var truncated = false
        private set

    private fun append(value: String, isFormatted: Boolean) {
        val start = text.length
        text.append(value)
        if (isFormatted && text.length > start) formatted += start until text.length
    }

    fun appendHtml(node: HtmlNode, isFormatted: Boolean) {
        if (truncated) return
        when (node) {
            is TextNode -> append(node.getWholeText(), isFormatted)
            is Element -> {
                val tag = node.tagName().lowercase()
                if (tag in htmlTextContainers) {
                    val segmentHighlight = tag == "span" &&
                        node.hasClass("highlight-wrap") &&
                        node.attr("data-highlight-id").isNotBlank() &&
                        node.classNames().all { it in highlightClasses } &&
                        !node.hasAttr("style")
                    val childFormatted = isFormatted || !segmentHighlight
                    node.childNodes().forEach { appendHtml(it, childFormatted) }
                } else {
                    truncated = true
                }
            }
            else -> truncated = true
        }
    }

    fun appendAst(node: Node, formatted: Boolean) {
        if (truncated) return
        when (node) {
            is Text -> append(node.literal, formatted)
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
