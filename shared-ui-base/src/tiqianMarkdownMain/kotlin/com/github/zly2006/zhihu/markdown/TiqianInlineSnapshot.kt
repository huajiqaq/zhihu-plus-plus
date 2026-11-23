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

import com.hrm.markdown.parser.ast.Abbreviation
import com.hrm.markdown.parser.ast.Autolink
import com.hrm.markdown.parser.ast.CitationReference
import com.hrm.markdown.parser.ast.ContainerNode
import com.hrm.markdown.parser.ast.DirectiveInline
import com.hrm.markdown.parser.ast.Document
import com.hrm.markdown.parser.ast.Emoji
import com.hrm.markdown.parser.ast.Emphasis
import com.hrm.markdown.parser.ast.EscapedChar
import com.hrm.markdown.parser.ast.FootnoteReference
import com.hrm.markdown.parser.ast.HardLineBreak
import com.hrm.markdown.parser.ast.Highlight
import com.hrm.markdown.parser.ast.HtmlEntity
import com.hrm.markdown.parser.ast.Image
import com.hrm.markdown.parser.ast.InlineCode
import com.hrm.markdown.parser.ast.InlineHtml
import com.hrm.markdown.parser.ast.InlineMath
import com.hrm.markdown.parser.ast.InsertedText
import com.hrm.markdown.parser.ast.KeyboardInput
import com.hrm.markdown.parser.ast.LeafNode
import com.hrm.markdown.parser.ast.Link
import com.hrm.markdown.parser.ast.Node
import com.hrm.markdown.parser.ast.Paragraph
import com.hrm.markdown.parser.ast.RubyText
import com.hrm.markdown.parser.ast.SoftLineBreak
import com.hrm.markdown.parser.ast.Spoiler
import com.hrm.markdown.parser.ast.Strikethrough
import com.hrm.markdown.parser.ast.StrongEmphasis
import com.hrm.markdown.parser.ast.StyledText
import com.hrm.markdown.parser.ast.Subscript
import com.hrm.markdown.parser.ast.Superscript
import com.hrm.markdown.parser.ast.Text
import com.hrm.markdown.parser.ast.WikiLink
import com.hrm.markdown.parser.inline.InlineParser
import org.tiqian.markdown.MarkdownCapabilityIssue
import org.tiqian.markdown.MarkdownCapabilityIssueKind
import org.tiqian.markdown.MarkdownNodeMetadata
import org.tiqian.markdown.MarkdownText
import org.tiqian.markdown.MarkdownTextMark
import org.tiqian.markdown.MarkdownTextRange
import org.tiqian.markdown.MarkdownTextSpan

private val styledTextVisualClasses = setOf(
    "red",
    "blue",
    "green",
    "yellow",
    "orange",
    "purple",
    "pink",
    "gray",
    "grey",
    "bold",
    "italic",
    "underline",
    "line-through",
    "strikethrough",
    "highlight",
)

internal fun Node.isTiqianInline(): Boolean = when (this) {
    is Text, is SoftLineBreak, is HardLineBreak, is Emphasis, is StrongEmphasis, is Strikethrough,
    is InlineCode, is Link, is Image, is Autolink, is InlineHtml, is HtmlEntity, is EscapedChar,
    is FootnoteReference, is InlineMath, is Highlight, is Superscript, is Subscript, is InsertedText,
    is Emoji, is StyledText, is Abbreviation, is KeyboardInput, is DirectiveInline, is CitationReference,
    is Spoiler, is WikiLink, is RubyText,
    -> true
    else -> false
}

internal fun compileTiqianText(
    node: ContainerNode,
    metadata: MarkdownNodeMetadata,
    issues: MutableList<MarkdownCapabilityIssue>,
    inlineOnly: Boolean = false,
): MarkdownText {
    val builder = TiqianTextSnapshotBuilder(issues)
    node.children.forEachIndexed { index, child ->
        if (!inlineOnly || child.isTiqianInline()) builder.append(child, metadata.children[index])
    }
    return builder.build()
}

/** 字段再解析所得节点不是原 AST；用独立负路径命名空间，所有坐标与持久 ID 均未知。 */
internal fun compileTiqianFieldText(
    content: String,
    metadata: MarkdownNodeMetadata,
    field: Int,
    document: Document,
    issues: MutableList<MarkdownCapabilityIssue>,
): MarkdownText {
    val holder = Paragraph()
    InlineParser(document).parseInlines(content, holder)
    val builder = TiqianTextSnapshotBuilder(issues)
    holder.children.forEachIndexed { index, child ->
        val snapshot = child.tiqianSnapshot(metadata.key.path + listOf(-3, field, index), null, null, hasOriginalSource = false)
        builder.append(child, snapshot)
    }
    return builder.build()
}

private class TiqianTextSnapshotBuilder(
    private val documentIssues: MutableList<MarkdownCapabilityIssue>,
) {
    private val value = StringBuilder()
    private val spans = mutableListOf<MarkdownTextSpan>()
    private val issues = mutableListOf<MarkdownCapabilityIssue>()

    fun append(node: Node, metadata: MarkdownNodeMetadata) {
        val start = value.length
        val mark = when (node) {
            is InlineCode -> MarkdownTextMark.InlineCode
            is Emphasis -> MarkdownTextMark.Emphasis
            is StrongEmphasis -> MarkdownTextMark.Strong
            is Strikethrough -> MarkdownTextMark.Strikethrough
            is Highlight -> if (metadata.attributes.containsKey("data-highlight-id")) {
                MarkdownTextMark.Custom(ZHIHU_SEGMENT_HIGHLIGHT_KIND, metadata.attributes)
            } else {
                MarkdownTextMark.Highlight
            }
            is Superscript -> MarkdownTextMark.Superscript
            is Subscript -> MarkdownTextMark.Subscript
            is InsertedText -> MarkdownTextMark.Inserted
            is Link -> MarkdownTextMark.Link(node.destination, node.title)
            is Autolink -> MarkdownTextMark.Link(node.destination)
            is WikiLink -> MarkdownTextMark.Link(node.target)
            is Abbreviation -> MarkdownTextMark.Abbreviation(node.fullText)
            is FootnoteReference -> MarkdownTextMark.Footnote(node.label, node.index)
            is RubyText -> MarkdownTextMark.Ruby(node.annotation)
            is KeyboardInput -> MarkdownTextMark.KeyboardInput
            is CitationReference -> MarkdownTextMark.Citation(node.key)
            is Spoiler -> MarkdownTextMark.Spoiler
            is Image -> MarkdownTextMark.InlineImage(
                node.destination,
                node.children.joinToString("") { it.tiqianReadableText() },
                node.title,
                node.imageWidth,
                node.imageHeight,
                metadata.attributes,
            )
            is InlineMath -> MarkdownTextMark.InlineMath(node.literal)
            else -> null
        }
        when (node) {
            is SoftLineBreak -> value.append(' ')
            is HardLineBreak -> value.append('\n')
            is HtmlEntity -> value.append(node.resolved.ifEmpty { node.literal })
            is Emoji -> value.append(node.unicode ?: node.literal)
            is FootnoteReference -> value.append("[${node.index}]")
            is CitationReference -> value.append("[@${node.key}]")
            is Image -> {
                node.children.forEachIndexed { index, child -> append(child, metadata.children[index]) }
                if (node.children.isEmpty()) value.append(node.title ?: node.destination)
            }
            is ContainerNode -> node.children.forEachIndexed { index, child -> append(child, metadata.children[index]) }
            is LeafNode -> value.append(node.literal)
        }
        val range = MarkdownTextRange(start, value.length)
        /** 空文本也有节点身份；事件与原树快照须共享元数据实例。 */
        spans += MarkdownTextSpan(range, mark, metadata)
        val unsupported = when (node) {
            is Text, is SoftLineBreak, is HardLineBreak, is HtmlEntity, is EscapedChar, is Emoji -> false
            is StyledText -> !node.style.isNullOrBlank() || node.cssClasses.any { it.lowercase() in styledTextVisualClasses }
            else -> mark == null
        }
        if (unsupported) {
            val issue = MarkdownCapabilityIssue(
                MarkdownCapabilityIssueKind.UnsupportedInline,
                metadata.nodeType ?: "UnknownNode",
                metadata.sourceSpan,
                range,
            )
            issues += issue
            documentIssues += issue
        }
    }

    fun build() = MarkdownText(value.toString(), spans.toList(), issues.toList())
}

internal fun Node.tiqianReadableText(): String = when (this) {
    is HtmlEntity -> resolved.ifEmpty { literal }
    is Emoji -> unicode ?: literal
    is LeafNode -> literal
    is ContainerNode -> children.joinToString("") { it.tiqianReadableText() }
}
