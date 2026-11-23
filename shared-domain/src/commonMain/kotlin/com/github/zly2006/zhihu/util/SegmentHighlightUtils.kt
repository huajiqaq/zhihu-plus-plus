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

package com.github.zly2006.zhihu.util

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.github.zly2006.zhihu.data.SegmentInfoMark
import com.github.zly2006.zhihu.data.SegmentInfoMeta
import com.github.zly2006.zhihu.data.SegmentInfoParagraph
import com.github.zly2006.zhihu.data.effectiveSegInfo

data class SegmentHighlightSpan(
    val text: String,
    val meta: SegmentInfoMeta,
    val displayText: String = text,
    val sourceUrl: String? = null,
    val contentId: String? = null,
    val contentType: String? = null,
    val paragraphId: String? = null,
    val startOffset: Int? = null,
    val endOffset: Int? = null,
)

data class SegmentTextPart(
    val text: String,
    val highlight: SegmentHighlightSpan? = null,
)

private data class SegmentTextParagraph(
    val pid: String?,
    val text: String,
    val parts: List<SegmentTextPart>,
)

private data class NormalizedSegmentMark(
    val startIndex: Int,
    val endIndex: Int,
    val meta: SegmentInfoMeta,
    val isMaster: Boolean,
)

private val segmentInfoInlineFormatTags = setOf("b", "strong", "i", "em", "br")

private fun Node.hasUnsupportedSegmentInfoFormat(): Boolean =
    this is Element &&
        (
            tagName().lowercase() !in segmentInfoInlineFormatTags ||
                childNodes().any(Node::hasUnsupportedSegmentInfoFormat)
        )

fun buildSegmentTextParts(
    text: String,
    marks: List<SegmentInfoMark>,
    sourceUrl: String? = null,
    contentId: String? = null,
    contentType: String? = null,
    paragraphId: String? = null,
): List<SegmentTextPart> {
    if (text.isEmpty()) return emptyList()
    if (marks.isEmpty()) return listOf(SegmentTextPart(text))

    val normalized = marks
        .mapNotNull { mark ->
            val start = mark.startIndex.coerceIn(0, text.length)
            val end = mark.endIndex.coerceIn(start, text.length)
            val meta = mark.effectiveSegInfo
            if (start >= end || meta == null) {
                null
            } else {
                NormalizedSegmentMark(
                    startIndex = start,
                    endIndex = end,
                    meta = meta,
                    isMaster = mark.masterSegInfo != null,
                )
            }
        }.groupBy { it.startIndex to it.endIndex }
        .map { (_, sameRangeMarks) ->
            sameRangeMarks.mergeSameRangeMarks()
        }.sortedWith(compareBy<NormalizedSegmentMark> { it.startIndex }.thenByDescending { it.endIndex })
    if (normalized.isEmpty()) return listOf(SegmentTextPart(text))

    val parts = mutableListOf<SegmentTextPart>()
    var cursor = 0
    normalized.forEach { mark ->
        if (mark.startIndex < cursor) return@forEach
        if (mark.startIndex > cursor) {
            parts += SegmentTextPart(text.substring(cursor, mark.startIndex))
        }
        parts += SegmentTextPart(
            text = text.substring(mark.startIndex, mark.endIndex),
            highlight = SegmentHighlightSpan(
                text = text.substring(mark.startIndex, mark.endIndex),
                meta = mark.meta,
                sourceUrl = sourceUrl,
                contentId = contentId,
                contentType = contentType,
                paragraphId = paragraphId,
                startOffset = mark.startIndex,
                endOffset = mark.endIndex,
            ),
        )
        cursor = mark.endIndex
    }
    if (cursor < text.length) {
        parts += SegmentTextPart(text.substring(cursor))
    }
    return parts.filter { it.text.isNotEmpty() }
}

private fun List<NormalizedSegmentMark>.mergeSameRangeMarks(): NormalizedSegmentMark {
    val masterMarks = filter { it.isMaster }
    val ordered = masterMarks + filterNot { it.isMaster }
    val mergedMeta = SegmentInfoMeta(
        segIds = ordered
            .flatMap { it.meta.segIds }
            .distinct(),
        isLike = any { it.meta.isLike },
        likeCount = maxOf { it.meta.likeCount },
        commentCount = maxOf { it.meta.commentCount },
        myCommentCount = maxOf { it.meta.myCommentCount },
        isSpan = any { it.meta.isSpan },
    )
    val first = first()
    return first.copy(
        meta = mergedMeta,
        isMaster = masterMarks.isNotEmpty(),
    )
}

/** 已验证的服务端差异仅为段首单个 U+200B → U+0020；其余字符与 UTF-16 偏移不变。 */
internal fun String.toSegmentSourceText(): String = if (startsWith('\u200B')) " " + substring(1) else this

fun applySegmentInfosToHtml(
    content: String,
    segmentInfos: List<SegmentInfoParagraph>,
    sourceUrl: String? = null,
    contentId: String? = null,
    contentType: String? = null,
): String {
    if (content.isBlank() || segmentInfos.isEmpty()) return content

    val document = Ksoup.parseBodyFragment(content)
    document.outputSettings().prettyPrint(false)
    val segmentInfosByPid = segmentInfos.associateBy(SegmentInfoParagraph::pid)
    val preparedParagraphs = document
        .select("p[data-pid]")
        .mapNotNull { target ->
            val paragraph = segmentInfosByPid[target.attr("data-pid")] ?: return@mapNotNull null
            val sourceText = target.wholeText()
            if (sourceText != paragraph.text && sourceText.toSegmentSourceText() != paragraph.text) return@mapNotNull null
            // TODO(#418): 支持在保留内联格式的同时注入 segment_infos。
            // https://github.com/zly2006/zhihu-plus-plus/issues/418
            if (target.childNodes().any(Node::hasUnsupportedSegmentInfoFormat)) return@mapNotNull null
            target to SegmentTextParagraph(
                pid = paragraph.pid,
                text = paragraph.text,
                parts = buildSegmentTextParts(
                    text = paragraph.text,
                    marks = paragraph.marks,
                    sourceUrl = sourceUrl,
                    contentId = contentId,
                    contentType = contentType,
                    paragraphId = paragraph.pid,
                ),
            )
        }
    val spanFragments = preparedParagraphs.flatMap { (_, paragraph) ->
        paragraph.parts.mapNotNull { part ->
            val highlight = part.highlight?.takeIf { it.meta.isSpan } ?: return@mapNotNull null
            val segmentId = highlight.meta.segIds
                .joinToString(",")
                .takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            segmentId to (paragraph.pid to part.text)
        }
    }
    val spanDisplayTexts = mutableMapOf<String, String>()
    spanFragments
        .groupBy({ it.first }, { it.second })
        .forEach { (segmentId, fragments) ->
            spanDisplayTexts[segmentId] = buildString {
                var previousParagraphId: String? = null
                fragments.forEach { (paragraphId, text) ->
                    if (isNotEmpty() && paragraphId != previousParagraphId) append("\n\n")
                    append(text)
                    previousParagraphId = paragraphId
                }
            }
        }
    val displayTextOwners = spanDisplayTexts.keys.toMutableSet()

    /** 服务端 LF 要还原为 br；直接写文本节点会被 HTML 渲染折叠为空格。 */
    fun Element.appendSegmentText(value: String) {
        value.split('\n').forEachIndexed { index, line ->
            if (index > 0) appendElement("br")
            if (line.isNotEmpty()) appendText(line)
        }
    }
    preparedParagraphs.forEach { (target, paragraph) ->
        target.empty()
        paragraph.parts.forEach { part ->
            val highlight = part.highlight
            if (highlight == null) {
                target.appendSegmentText(part.text)
            } else {
                target.appendChild(
                    Element("span").apply {
                        addClass("highlight-wrap")
                        addClass("other")
                        if (highlight.meta.commentCount > 0) {
                            addClass("has-comments")
                        }
                        attr("data-highlight-id", highlight.meta.segIds.joinToString(","))
                        attr("data-highlight-like-count", highlight.meta.likeCount.toString())
                        attr("data-highlight-comment-count", highlight.meta.commentCount.toString())
                        attr("data-highlight-my-comment-count", highlight.meta.myCommentCount.toString())
                        attr("data-highlight-is-like", highlight.meta.isLike.toString())
                        attr("data-highlight-is-span", highlight.meta.isSpan.toString())
                        val segmentId = highlight.meta.segIds.joinToString(",")
                        if (highlight.meta.isSpan && displayTextOwners.remove(segmentId)) {
                            spanDisplayTexts[segmentId]
                                ?.takeIf { it != part.text }
                                ?.let { attr("data-highlight-display-text", it) }
                        }
                        attr(
                            "data-highlight-split-type",
                            when {
                                part.text == paragraph.text -> "both"
                                paragraph.text.startsWith(part.text) -> "head"
                                paragraph.text.endsWith(part.text) -> "tail"
                                else -> "middle"
                            },
                        )
                        attr("data-highlight-id-extra", "")
                        highlight.sourceUrl?.let { attr("data-highlight-source-url", it) }
                        contentId?.let { attr("data-highlight-content-id", it) }
                        contentType?.let { attr("data-highlight-content-type", it) }
                        highlight.paragraphId?.let { attr("data-highlight-pid", it) }
                        highlight.startOffset?.let { attr("data-highlight-start-offset", it.toString()) }
                        highlight.endOffset?.let { attr("data-highlight-end-offset", it.toString()) }
                        appendSegmentText(part.text)
                    },
                )
            }
        }
    }
    return document.body().html()
}
