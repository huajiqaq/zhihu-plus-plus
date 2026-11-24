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

package com.github.zly2006.zhihu.ui

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode

private fun AnnotatedString.Builder.processTextWithEmoji(
    text: String,
    componentUsed: MutableSet<String>?,
) {
    var buffer = StringBuilder()
    var emojiBuffer = StringBuilder()
    var isEmoji = false

    for (ch in text) {
        if (ch == '[') {
            if (buffer.isNotEmpty()) {
                append(buffer.toString())
                buffer = StringBuilder()
            }
            isEmoji = true
            emojiBuffer.append(ch)
        } else if (ch == ']') {
            if (isEmoji) {
                emojiBuffer.append(ch)
                val placeholder = emojiBuffer.toString()
                val emojiKey = commentEmojiInlineKey(placeholder)
                if (emojiKey != null) {
                    appendInlineContent(emojiKey, placeholder)
                    componentUsed?.add(emojiKey)
                } else {
                    append(placeholder)
                }
                emojiBuffer = StringBuilder()
                isEmoji = false
            } else {
                buffer.append(ch)
            }
        } else {
            if (isEmoji) {
                emojiBuffer.append(ch)
            } else {
                buffer.append(ch)
            }
        }
    }

    if (buffer.isNotEmpty()) {
        append(buffer.toString())
    }
    if (isEmoji && emojiBuffer.isNotEmpty()) {
        append(emojiBuffer.toString())
    }
}

fun AnnotatedString.Builder.dfsSimple(
    node: Node,
    onOpenLink: (String) -> Unit,
    linkColor: Color,
    componentUsed: MutableSet<String>? = null,
) {
    when (node) {
        is Element -> {
            when (node.tagName()) {
                "br" -> append("\n")
                "a" -> {
                    val href = node.attr("href")
                    val linkText = node.text()
                    if (linkText.isNotEmpty()) {
                        withLink(
                            LinkAnnotation.Clickable(
                                href,
                                TextLinkStyles(style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                            ) { onOpenLink(href) },
                        ) {
                            append(linkText)
                        }
                    }
                }

                else -> node.childNodes().forEach {
                    dfsSimple(it, onOpenLink, linkColor, componentUsed)
                }
            }
        }

        is TextNode -> {
            val text = node.text()
            var cursor = 0
            findPlainUrls(text).forEach { match ->
                if (match.range.first > cursor) {
                    processTextWithEmoji(text.substring(cursor, match.range.first), componentUsed)
                }
                withLink(
                    LinkAnnotation.Clickable(
                        match.url,
                        TextLinkStyles(style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                    ) { onOpenLink(match.url) },
                ) {
                    append(match.url)
                }
                val trailingStart = match.range.first + match.url.length
                val trailingEnd = match.range.last + 1
                if (trailingEnd > trailingStart) {
                    processTextWithEmoji(text.substring(trailingStart, trailingEnd), componentUsed)
                }
                cursor = trailingEnd
            }
            if (cursor < text.length) {
                processTextWithEmoji(text.substring(cursor), componentUsed)
            }
        }
        else -> append(node.outerHtml())
    }
}
