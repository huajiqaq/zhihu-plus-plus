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

import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.editor.PIN_TOPIC_SEPARATOR
import com.github.zly2006.zhihu.editor.PinContentTopicItem
import com.github.zly2006.zhihu.editor.UploadedZhihuImage
import com.github.zly2006.zhihu.editor.pinTopicTokens
import com.github.zly2006.zhihu.editor.toPinMarkdown
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
internal data class LocalPinDraft(
    val schema: Int = 2,
    val title: String = "",
    val content: String = "",
    val images: List<UploadedZhihuImage> = emptyList(),
    val titleSelectionStart: Int = 0,
    val titleSelectionEnd: Int = 0,
    val contentSelectionStart: Int = 0,
    val contentSelectionEnd: Int = 0,
)

@Serializable
private data class LegacyPinMarker(
    val start: Int,
    val endExclusive: Int,
    val topic: PinContentTopicItem,
    val hasTrailingSpace: Boolean? = null,
) {
    fun endIn(text: String): Int = endExclusive + if (hasTrailingSpace != false && text.getOrNull(endExclusive) == ' ') 1 else 0
}

/** 旧草稿里的普通 Markdown 超链接仍可访问，但不能因升级而被赋予官方话题身份。 */
private fun LocalPinDraft.keepLegacyLinksOrdinary(migrated: Set<Int>): LocalPinDraft {
    val ordinary = pinTopicTokens(content).filter { it.start !in migrated }
    if (ordinary.isEmpty()) return this
    val delimiters = buildList {
        for (token in ordinary) {
            add(content.lastIndexOf("](", token.end - 1) + 2 to '<')
            add(token.end - 1 to '>')
        }
    }
    val markdown = StringBuilder(content)
        .apply {
            for ((offset, ch) in delimiters.asReversed()) insert(offset, ch)
        }.toString()

    fun map(offset: Int) = offset + delimiters.count { (at, _) -> at < offset }
    return copy(content = markdown, contentSelectionStart = map(contentSelectionStart), contentSelectionEnd = map(contentSelectionEnd))
}

/** 旧标记逐项读取；一个损坏区间不能使标题、图片或其余正文丢失。 */
internal fun readLocalPinDraft(raw: String): LocalPinDraft? = runCatching {
    val json = ZhihuJson.json
    val fields = json.parseToJsonElement(raw).jsonObject
    val schema = fields["schema"]?.jsonPrimitive?.intOrNull ?: if ("schema" in fields) return null else 1
    if (schema !in 1..2) return null
    val decoded = json.decodeFromJsonElement<LocalPinDraft>(fields)
    val draft = decoded.copy(
        schema = 2,
        titleSelectionStart = decoded.titleSelectionStart.coerceIn(0, decoded.title.length),
        titleSelectionEnd = decoded.titleSelectionEnd.coerceIn(0, decoded.title.length),
        contentSelectionStart = decoded.contentSelectionStart.coerceIn(0, decoded.content.length),
        contentSelectionEnd = decoded.contentSelectionEnd.coerceIn(0, decoded.content.length),
    )
    if (schema == 2) return draft
    val markers = (fields["topics"] as? JsonArray)
        .orEmpty()
        .mapNotNull { element ->
            runCatching { json.decodeFromJsonElement<LegacyPinMarker>(element) }.getOrNull()
        }.filter { it.start >= 0 && it.start < it.endExclusive && it.endExclusive <= draft.content.length }
        .sortedBy { it.start }
    val namesById = markers.groupBy { it.topic.topicId }.mapValues { (_, group) -> group.map { it.topic.topicName.removeSurrounding("#") }.distinct() }
    var previousEnd = 0
    val replacements = buildList {
        for ((i, marker) in markers.withIndex()) {
            val overlaps = marker.start < previousEnd || marker.endExclusive > (markers.getOrNull(i + 1)?.start ?: Int.MAX_VALUE)
            previousEnd = maxOf(previousEnd, marker.endExclusive)
            if (overlaps) continue
            val name = namesById[marker.topic.topicId]?.singleOrNull() ?: continue
            if (draft.content.substring(marker.start, marker.endExclusive) != "#$name") continue
            val link = marker.topic.copy(topicName = name).toPinMarkdown() ?: continue
            add(marker to link)
        }
    }
    if (replacements.isEmpty()) return draft.keepLegacyLinksOrdinary(emptySet())

    fun rewrite(items: List<Pair<LegacyPinMarker, String>>) = StringBuilder(draft.content)
        .apply {
            for ((marker, link) in items.asReversed()) {
                val end = marker.endIn(draft.content)
                setRange(marker.start, end, if (end == marker.endExclusive) link else link + PIN_TOPIC_SEPARATOR)
            }
        }.toString()

    /** 代码或转义例子仍是旧正文，不把 URL 写进示例。 */
    val recognized = pinTopicTokens(rewrite(replacements)).associateBy { it.start }
    var shift = 0

    fun delta(marker: LegacyPinMarker, link: String): Int {
        val separator = if (marker.endIn(draft.content) == marker.endExclusive) 0 else PIN_TOPIC_SEPARATOR.length - 1
        return link.length - (marker.endExclusive - marker.start) + separator
    }
    val accepted = replacements.filter { (marker, link) ->
        val token = recognized[marker.start + shift]
        shift += delta(marker, link)
        token?.topic?.topicId == marker.topic.topicId
    }

    fun mapOffset(original: Int): Int {
        val offset = original.coerceIn(0, draft.content.length)
        var shift = 0
        for ((marker, link) in accepted) {
            if (offset <= marker.start) break
            if (offset < marker.endExclusive) {
                var inside = 1
                repeat(offset - marker.start) {
                    if (link[inside] == '\\') inside++
                    inside++
                }
                return marker.start + shift + inside
            }
            if (offset == marker.endExclusive) return marker.start + shift + link.length
            shift += delta(marker, link)
        }
        return offset + shift
    }
    var deltaBefore = 0
    val migrated = accepted.mapTo(mutableSetOf()) { (marker, link) ->
        (marker.start + deltaBefore).also { deltaBefore += delta(marker, link) }
    }
    draft
        .copy(
            content = rewrite(accepted),
            contentSelectionStart = mapOffset(draft.contentSelectionStart),
            contentSelectionEnd = mapOffset(draft.contentSelectionEnd),
        ).keepLegacyLinksOrdinary(migrated)
}.getOrNull()
