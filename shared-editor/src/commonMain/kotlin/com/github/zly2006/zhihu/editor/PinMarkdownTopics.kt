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

import com.hrm.markdown.parser.MarkdownParser
import com.hrm.markdown.parser.ast.ContainerNode
import com.hrm.markdown.parser.ast.DefinitionTerm
import com.hrm.markdown.parser.ast.Document
import com.hrm.markdown.parser.ast.EscapedChar
import com.hrm.markdown.parser.ast.Heading
import com.hrm.markdown.parser.ast.HtmlEntity
import com.hrm.markdown.parser.ast.Image
import com.hrm.markdown.parser.ast.InlineCode
import com.hrm.markdown.parser.ast.InlineHtml
import com.hrm.markdown.parser.ast.LeafNode
import com.hrm.markdown.parser.ast.Link
import com.hrm.markdown.parser.ast.Node
import com.hrm.markdown.parser.ast.Paragraph
import com.hrm.markdown.parser.ast.SetextHeading
import com.hrm.markdown.parser.ast.TableCell
import com.hrm.markdown.parser.ast.Text
import com.hrm.markdown.parser.html.HtmlTagCategories
import com.hrm.markdown.parser.html.HtmlTagKind
import com.hrm.markdown.parser.html.HtmlTagParser

private const val TOPIC_URL = "https://www.zhihu.com/topic/"

/** 普通 Markdown 空格实体仅在紧邻规范话题时属于该话题；用户的字面空格不随删除。 */
const val PIN_TOPIC_SEPARATOR = "&#32;"

private val topicSyntax = Regex("""\[#(?:\\[^\r\n]|[^\[\]\\\r\n])+\]\(https://www\.zhihu\.com/topic/[1-9][0-9]{0,18}\)""")

private fun Char.isMarkdownPunctuation() = this in '!'..'/' || this in ':'..'@' || this in '['..'`' || this in '{'..'~'

fun PinContentTopicItem.toPinMarkdown(): String? {
    val id = topicId.toLongOrNull() ?: return null
    if (id <= 0 || id.toString() != topicId || topicName.isBlank() || topicName.any(Char::isISOControl)) return null
    return buildString {
        append("[#")
        for (ch in topicName) {
            if (ch.isMarkdownPunctuation()) append('\\')
            append(ch)
        }
        append("](").append(TOPIC_URL).append(topicId).append(')')
    }
}

/** 仅对这份 Markdown 有效的 UTF-16 源码区间，不随编辑保存。 */
data class PinTopicToken(
    val start: Int,
    val end: Int,
    val topic: PinContentTopicItem,
) {
    fun endWithSeparator(source: CharSequence): Int =
        if (source.startsWith(PIN_TOPIC_SEPARATOR, end)) end + PIN_TOPIC_SEPARATOR.length else end
}

private fun MutableList<String>.consumeHtml(literal: String): Boolean {
    val tag = HtmlTagParser.parse(literal) ?: return false
    val name = tag.name ?: return true
    when (tag.kind) {
        HtmlTagKind.OPENING -> if (!HtmlTagCategories.isVoidTag(name)) add(name)
        HtmlTagKind.CLOSING -> {
            if (lastOrNull() != name) return false
            removeAt(lastIndex)
        }
        else -> Unit
    }
    return true
}

private fun Link.topic(): PinContentTopicItem? {
    if (!destination.startsWith(TOPIC_URL) || title != null || attributes.isNotEmpty()) return null
    if (children.any { it !is Text && it !is EscapedChar }) return null
    val label = children.joinToString("") { (it as LeafNode).literal }
    if (!label.startsWith('#')) return null
    return PinContentTopicItem(destination.removePrefix(TOPIC_URL), label.drop(1))
}

private data class Candidate(
    val local: Int,
    val start: Int,
    val source: String,
) {
    val destination: String = source.substringAfterLast('(').dropLast(1)
}

private fun locatePinLinks(
    document: Document,
    block: ContainerNode,
    raw: String,
    candidates: List<Candidate>,
    record: (Link, PinTopicToken) -> Unit,
) {
    if (candidates.isEmpty()) return
    val links = mutableListOf<Pair<Link, PinContentTopicItem>>()
    val tags = mutableListOf<String>()
    val html = mutableListOf<String>()

    fun collect(node: Node): Boolean = when (node) {
        is InlineHtml -> {
            tags += node.literal
            html.consumeHtml(node.literal)
        }
        is Link -> {
            if (html.isEmpty()) node.topic()?.let { links += node to it }
            true
        }
        is Image, is Text, is InlineCode, is EscapedChar, is HtmlEntity -> true
        is LeafNode -> "]($TOPIC_URL" !in node.literal
        is ContainerNode -> node.children.all(::collect)
    }
    if (!collect(block) || html.isNotEmpty() || links.isEmpty()) return

    /** 只认完整的直接话题 token；反引号、原始 HTML 和普通链接内部均不是插入位置。 */
    val byOffset = candidates.associateBy(Candidate::local)
    val direct = mutableListOf<Candidate>()
    var ticks = 0
    var brackets = 0
    var destinationDepth = 0
    var quote: Char? = null
    var hasOtherLabel = false
    var imageStart = -1
    var nextTag = 0
    var i = 0
    while (i < raw.length) {
        val ch = raw[i]
        if (ch == '`' && destinationDepth == 0 && html.isEmpty()) {
            val start = i++
            while (i < raw.length && raw[i] == '`') i++
            val run = i - start
            if (ticks == 0) {
                ticks = run
            } else if (ticks == run) {
                ticks = 0
            }
            continue
        }
        if (ticks != 0) {
            i++
            continue
        }
        if (ch == '\\') {
            i += 2
            continue
        }
        if (destinationDepth != 0) {
            when {
                quote != null -> if (ch == quote) quote = null
                ch == '"' || ch == '\'' -> quote = ch
                ch == '(' -> destinationDepth++
                ch == ')' -> destinationDepth--
            }
            i++
            continue
        }
        if (ch == '<') {
            val literal = tags.getOrNull(nextTag)
            if (literal != null && raw.startsWith(literal, i)) {
                if (!html.consumeHtml(literal)) return
                nextTag++
                i += literal.length
                continue
            }
            val next = raw.getOrNull(i + 1)
            if (next != null && (next.isLetter() || next in "/!?")) return
        }
        if (html.isEmpty()) {
            val candidate = byOffset[i]
            if (candidate != null) {
                if (brackets == 0 && imageStart != i) direct += candidate
                i += candidate.source.length
                continue
            }
            when (ch) {
                '!' -> imageStart = i + 1
                '[' -> {
                    brackets++
                    hasOtherLabel = true
                }
                ']' -> {
                    if (brackets > 0) brackets--
                    if (brackets == 0 && raw.getOrNull(i + 1) == '(') {
                        destinationDepth = 1
                        i++
                    }
                }
            }
        }
        i++
    }
    if (ticks != 0 || brackets != 0 || destinationDepth != 0 || nextTag != tags.size || html.isNotEmpty()) return

    val counts = direct.groupingBy(Candidate::destination).eachCount()
    val references = if (hasOtherLabel) document.linkDefinitions.values.mapTo(mutableSetOf()) { it.destination } else emptySet()
    val urls = links
        .groupingBy { it.first.destination }
        .eachCount()
        .filter { (url, count) ->
            counts[url] == count && url !in references
        }.keys
    val orderedLinks = links.filter { it.first.destination in urls }
    val orderedSource = direct.filter { it.destination in urls }
    if (orderedLinks.indices.any { orderedLinks[it].first.destination != orderedSource[it].destination }) return
    orderedLinks.forEachIndexed { index, (link, topic) ->
        val source = orderedSource[index]
        if (topic.toPinMarkdown() == source.source) record(link, PinTopicToken(source.start, source.start + source.source.length, topic))
    }
}

/** HRM 1.7.1 的内联 Link 没有 sourceRange，引用链接也使用同一节点类型。 */
internal fun pinTopicLinks(markdown: String, document: Document? = null): Map<Link, PinTopicToken> {
    if ("]($TOPIC_URL" !in markdown) return emptyMap()
    val ast = document ?: MarkdownParser().parse(markdown)
    val lines = buildList {
        add(0)
        for (i in markdown.indices) {
            if (markdown[i] == '\n' || markdown[i] == '\r' && markdown.getOrNull(i + 1) != '\n') add(i + 1)
        }
        add(markdown.length)
    }
    return buildMap {
        var floor = 0

        fun visit(node: Node) {
            if (node !is ContainerNode) return
            val raw = when (node) {
                is Paragraph -> node.rawContent
                is Heading -> node.rawContent
                is SetextHeading -> node.rawContent
                is TableCell -> node.rawContent
                is DefinitionTerm -> null
                else -> {
                    node.children.forEach(::visit)
                    return
                }
            }
            val from = lines.getOrNull(node.lineRange.startLine) ?: return
            val to = lines.getOrNull(node.lineRange.endLine) ?: return
            val inline = raw ?: markdown.substring(from, to).trimEnd('\r', '\n')
            if ("]($TOPIC_URL" !in inline) return
            var at = maxOf(from, floor)
            var local = 0
            val candidates = mutableListOf<Candidate>()
            for (line in inline.lineSequence()) {
                val start = markdown.indexOf(line, at)
                if (start < 0 || start + line.length > to) return
                for (m in topicSyntax.findAll(line)) candidates += Candidate(local + m.range.first, start + m.range.first, m.value)
                at = start + line.length
                local += line.length + 1
            }
            floor = at
            locatePinLinks(ast, node, inline, candidates) { link, token -> put(link, token) }
        }
        visit(ast)
    }
}

fun pinTopicTokens(markdown: String): List<PinTopicToken> = pinTopicLinks(markdown).values.sortedBy(PinTopicToken::start)
