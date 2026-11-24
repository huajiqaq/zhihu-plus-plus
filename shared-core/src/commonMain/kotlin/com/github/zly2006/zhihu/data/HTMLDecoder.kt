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

package com.github.zly2006.zhihu.data

import com.fleeksoft.ksoup.Ksoup
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object HTMLDecoder : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("HTMLDecoder", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }

    override fun deserialize(decoder: Decoder): String {
        val string = decoder.decodeString()
        return Ksoup
            .parse(string)
            .apply {
                select(".invisible").forEach { it.remove() }
            }.text()
    }
}

/** 想法正文、excerpt_title 是 HTML；已提取的摘要和普通标题不能再次解析。 */
fun String.pinSummaryText(): String = if (isBlank()) "" else Ksoup.parse(this).text()

/** 搜索字段只识别原文中的非嵌套 <em> 标记；实体解码出的尖括号仍是字面文本。 */
fun String.decodeSearchText(onEmphasis: (Int, Int) -> Unit = { _, _ -> }): String {
    val html = this
    if ('&' !in html && "<em>" !in html && "</em>" !in html) return html
    return buildString(html.length) {
        var cursor = 0
        var emphasisStart = -1
        while (cursor < html.length) {
            when {
                html.startsWith("<em>", cursor) -> {
                    emphasisStart = length
                    cursor += 4
                }
                html.startsWith("</em>", cursor) -> {
                    if (emphasisStart in 0 until length) onEmphasis(emphasisStart, length)
                    emphasisStart = -1
                    cursor += 5
                }
                html[cursor] == '&' -> {
                    val end = html.indexOf(';', cursor + 1)
                    val decoded = if (end < 0) null else decodeSearchEntity(html.substring(cursor + 1, end))
                    append(decoded ?: "&")
                    cursor = if (decoded == null) cursor + 1 else end + 1
                }
                else -> append(html[cursor++])
            }
        }
    }
}

private fun decodeSearchEntity(entity: String): String? = when (entity) {
    "lt" -> "<"
    "gt" -> ">"
    "quot" -> "\""
    "amp" -> "&"
    else -> {
        val codePoint = when {
            entity.startsWith("#x", ignoreCase = true) -> entity.substring(2).toIntOrNull(16)
            entity.startsWith('#') -> entity.substring(1).toIntOrNull()
            else -> null
        }
        when {
            codePoint == null || codePoint !in 0..0x10FFFF || codePoint in 0xD800..0xDFFF -> null
            codePoint <= 0xFFFF -> codePoint.toChar().toString()
            else -> {
                val offset = codePoint - 0x10000
                charArrayOf(((offset shr 10) + 0xD800).toChar(), ((offset and 0x3FF) + 0xDC00).toChar()).concatToString()
            }
        }
    }
}
