/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.github.zly2006.zhihu.markdown

import com.hrm.markdown.parser.SourceRange
import com.hrm.markdown.parser.ast.ContainerNode
import com.hrm.markdown.parser.ast.DirectiveBlock
import com.hrm.markdown.parser.ast.DirectiveInline
import com.hrm.markdown.parser.ast.Document
import com.hrm.markdown.parser.ast.Heading
import com.hrm.markdown.parser.ast.Node
import com.hrm.markdown.parser.ast.Paragraph
import com.hrm.markdown.parser.ast.SetextHeading
import com.hrm.markdown.parser.ast.TableCell
import org.tiqian.markdown.MarkdownNodeKey
import org.tiqian.markdown.MarkdownNodeMetadata
import org.tiqian.markdown.MarkdownNodeValue
import org.tiqian.markdown.MarkdownSourceSpan

/** 原树只做一次后序快照；渲染编译直接沿子节点索引复用，不另建节点身份表。 */
internal fun Node.tiqianSnapshot(
    path: List<Int>,
    source: String?,
    locator: TiqianSourceLocator?,
    hasOriginalSource: Boolean = true,
    allowLineSlice: Boolean = true,
): MarkdownNodeMetadata {
    val children = if (this is ContainerNode) {
        val childLineSlice = allowLineSlice &&
            this !is Paragraph &&
            this !is Heading &&
            this !is SetextHeading &&
            this !is TableCell &&
            !isTiqianInline()
        children.mapIndexed { index, child ->
            child.tiqianSnapshot(path + index, source, locator, hasOriginalSource, childLineSlice)
        }
    } else {
        emptyList()
    }
    val completeChildren = if (this is Document) {
        buildList {
            addAll(children)
            /** 脱离子节点列表的定义追加到末尾，不能改变原节点路径索引。 */
            val definitions = listOf(linkDefinitions.values, footnoteDefinitions.values, abbreviationDefinitions.values)
            definitions.forEachIndexed { type, entries ->
                entries.forEachIndexed { index, definition ->
                    var root: Node = definition
                    while (root.parent != null) root = root.parent!!
                    if (root !== this@tiqianSnapshot) {
                        add(definition.tiqianSnapshot(path + listOf(-2, type, index), source, locator))
                    }
                }
            }
        }
    } else {
        children
    }
    val fields = accept(TiqianNodeProperties)
    val exactSource = hasOriginalSource && sourceRange != SourceRange.EMPTY
    val span = if (exactSource) {
        MarkdownSourceSpan(
            sourceRange.start.offset,
            sourceRange.end.offset,
            sourceRange.start.line,
            sourceRange.start.column,
            sourceRange.end.line,
            sourceRange.end.column,
        )
    } else {
        MarkdownSourceSpan(0, 0, 0, 0, 0, 0)
    }
    val sliceSpan = when {
        exactSource -> span
        hasOriginalSource && allowLineSlice && locator != null && lineRange.endLine > lineRange.startLine ->
            locator.span(lineRange.startLine, lineRange.endLine)
        else -> null
    }
    val properties = buildMap {
        putAll(tiqianCommonProperties(hasOriginalSource))
        putAll(fields.properties)
    }
    return MarkdownNodeMetadata(
        key = MarkdownNodeKey(stableKey, path.toList()),
        sourceSpan = span,
        /** 行切片不是精确范围；事件坐标只认原 sourceRange，不借用父范围。 */
        sourceMarkdown = if (source != null &&
            sliceSpan != null &&
            sliceSpan.startOffset in 0..source.length &&
            sliceSpan.endOffset in sliceSpan.startOffset..source.length
        ) {
            source.substring(sliceSpan.startOffset, sliceSpan.endOffset)
        } else {
            null
        },
        nodeType = fields.type,
        attributes = fields.attributes.toMap(),
        payload = buildMap {
            put("startLine", lineRange.startLine.toString())
            put("endLine", lineRange.endLine.toString())
            /** payload 仅存标量，结构化字段留在 properties 中以保留类型。 */
            properties.forEach { (name, value) ->
                when (value) {
                    is MarkdownNodeValue.Text -> put(name, value.value)
                    is MarkdownNodeValue.Integer -> put(name, value.value.toString())
                    is MarkdownNodeValue.Boolean -> put(name, value.value.toString())
                    is MarkdownNodeValue.Decimal -> put(name, value.value.toString())
                    else -> Unit
                }
            }
            if (this@tiqianSnapshot is DirectiveInline || this@tiqianSnapshot is DirectiveBlock) {
                fields.attributes.forEach { (name, value) -> put("args.$name", value) }
            }
        },
        persistentId = persistentId.takeIf { hasOriginalSource && it != 0L },
        hasSourceSpan = exactSource,
        properties = properties,
        children = completeChildren,
        contentHash = contentHash,
    )
}

internal class TiqianSourceLocator(
    private val source: String,
) {
    private val lineStarts = buildList {
        add(0)
        source.forEachIndexed { index, char -> if (char == '\n') add(index + 1) }
    }

    fun span(startLine: Int, endLineExclusive: Int): MarkdownSourceSpan {
        val safeStart = startLine.coerceIn(0, lineStarts.lastIndex)
        val safeLast = (endLineExclusive - 1).coerceIn(safeStart, lineStarts.lastIndex)
        val start = lineStarts[safeStart]
        var end = if (safeLast == lineStarts.lastIndex) source.length else lineStarts[safeLast + 1]
        if (safeLast != lineStarts.lastIndex) {
            if (end > 0 && source[end - 1] == '\n') end--
            if (end > 0 && source[end - 1] == '\r') end--
        }
        return MarkdownSourceSpan(start, end, safeStart, 0, safeLast, end - lineStarts[safeLast])
    }
}
