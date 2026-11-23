/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.github.zly2006.zhihu.markdown

import com.hrm.markdown.parser.SourcePosition
import com.hrm.markdown.parser.ast.ContainerNode
import com.hrm.markdown.parser.ast.LeafNode
import com.hrm.markdown.parser.ast.Node
import com.hrm.markdown.parser.core.Attributes
import org.tiqian.markdown.MarkdownNodeValue

internal fun tiqianValue(value: String?): MarkdownNodeValue = value?.let { MarkdownNodeValue.Text(it) } ?: MarkdownNodeValue.Null

internal fun tiqianValue(value: Int?): MarkdownNodeValue = value?.let { MarkdownNodeValue.Integer(it.toLong()) } ?: MarkdownNodeValue.Null

internal fun tiqianValue(value: Long): MarkdownNodeValue = MarkdownNodeValue.Integer(value)

internal fun tiqianValue(value: Boolean): MarkdownNodeValue = MarkdownNodeValue.Boolean(value)

internal fun tiqianStrings(values: List<String>): MarkdownNodeValue = MarkdownNodeValue.ListValue(values.map(::tiqianValue))

internal fun tiqianMap(values: Map<String, String>): MarkdownNodeValue = MarkdownNodeValue.ObjectValue(values.mapValues { tiqianValue(it.value) })

internal fun tiqianPosition(value: SourcePosition?): MarkdownNodeValue = if (value == null) {
    MarkdownNodeValue.Null
} else {
    MarkdownNodeValue.ObjectValue(
        mapOf("line" to tiqianValue(value.line), "column" to tiqianValue(value.column), "offset" to tiqianValue(value.offset)),
    )
}

internal fun tiqianAttributes(value: Attributes): MarkdownNodeValue = MarkdownNodeValue.ObjectValue(
    mapOf(
        "id" to tiqianValue(value.id),
        "classes" to tiqianStrings(value.classes),
        "pairs" to tiqianMap(value.pairs),
        "isEmpty" to tiqianValue(value.isEmpty),
    ),
)

internal fun Node.tiqianCommonProperties(hasOriginalSource: Boolean = true): Map<String, MarkdownNodeValue> = buildMap {
    put(
        "sourceRange",
        if (hasOriginalSource) {
            MarkdownNodeValue.ObjectValue(
                mapOf(
                    "start" to tiqianPosition(sourceRange.start),
                    "end" to tiqianPosition(sourceRange.end),
                    "length" to tiqianValue(sourceRange.length),
                ),
            )
        } else {
            MarkdownNodeValue.Null
        },
    )
    put(
        "lineRange",
        if (hasOriginalSource) {
            MarkdownNodeValue.ObjectValue(
                mapOf(
                    "startLine" to tiqianValue(lineRange.startLine),
                    "endLine" to tiqianValue(lineRange.endLine),
                    "lineCount" to tiqianValue(lineRange.lineCount),
                ),
            )
        } else {
            MarkdownNodeValue.Null
        },
    )
    put("stableKey", tiqianValue(stableKey))
    put("persistentId", if (hasOriginalSource) tiqianValue(persistentId) else MarkdownNodeValue.Null)
    put("contentHash", tiqianValue(contentHash))
    if (this@tiqianCommonProperties is LeafNode) put("literal", tiqianValue(literal))
    if (this@tiqianCommonProperties is ContainerNode) put("isInlineParsed", tiqianValue(isInlineParsed))
}
