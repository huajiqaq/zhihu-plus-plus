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

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import kotlinx.coroutines.flow.first
import kotlin.math.min

internal data class EmojiInputRun(
    val transformedStart: Int,
    val transformedEnd: Int,
    val inlineKey: String,
)

internal class CommentInputReplacement(
    val start: Int,
    val end: Int,
    val text: String,
    val inlineKey: String? = null,
)

internal class CommentInputPlan(
    val replacements: List<CommentInputReplacement>,
    val emojiRuns: List<EmojiInputRun>,
    val linkSpans: List<IntRange>,
)

private fun findEmojiPlaceholderRanges(source: String): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    var index = 0
    while (index < source.length) {
        val open = source.indexOf('[', index)
        if (open < 0) break
        val close = source.indexOf(']', open + 1)
        if (close < 0) break
        if (commentEmojiInlineKey(source.substring(open, close + 1)) != null) {
            ranges += open until (close + 1)
            index = close + 1
        } else {
            index = open + 1
        }
    }
    return ranges
}

private fun findMentionRanges(source: String, mentionNames: List<String>): List<IntRange> {
    if (mentionNames.isEmpty() || '@' !in source) return emptyList()
    val found = mutableListOf<IntRange>()
    mentionNames.sortedByDescending { it.length }.forEach { name ->
        if (name.isBlank()) return@forEach
        val token = "@$name"
        var index = source.indexOf(token)
        while (index >= 0) {
            val range = index until (index + token.length)
            if (found.none { it.first < range.last + 1 && range.first < it.last + 1 }) {
                found += range
            }
            index = source.indexOf(token, index + 1)
        }
    }
    return found.sortedBy { it.first }
}

/** 全角空格为表情绘制预留字符边界；显示替换不写回原始文本。 */
internal fun planCommentInput(source: String, linkTitles: Map<String, String>): CommentInputPlan {
    val candidates = mutableListOf<CommentInputReplacement>()
    findEmojiPlaceholderRanges(source).forEach { range ->
        val placeholder = source.substring(range.first, range.last + 1)
        val inlineKey = commentEmojiInlineKey(placeholder) ?: return@forEach
        candidates += CommentInputReplacement(range.first, range.last + 1, "\u3000", inlineKey)
    }
    findPlainUrls(source).forEach { match ->
        val title = linkTitles[match.url] ?: return@forEach
        val end = match.range.first + match.url.length
        if (end > match.range.first && title.isNotEmpty()) {
            candidates += CommentInputReplacement(match.range.first, end, title)
        }
    }
    val replacements = mutableListOf<CommentInputReplacement>()
    candidates.sortedBy { it.start }.forEach { candidate ->
        if (replacements.isEmpty() || candidate.start >= replacements.last().end) {
            replacements += candidate
        }
    }

    val emojiRuns = mutableListOf<EmojiInputRun>()
    val linkSpans = mutableListOf<IntRange>()
    var transformed = 0
    var original = 0
    replacements.forEach { replacement ->
        transformed += replacement.start - original
        val transformedStart = transformed
        transformed += replacement.text.length
        val inlineKey = replacement.inlineKey
        if (inlineKey != null) {
            emojiRuns += EmojiInputRun(
                transformedStart = transformedStart,
                transformedEnd = transformedStart + 1,
                inlineKey = inlineKey,
            )
        } else {
            linkSpans += transformedStart until transformed
        }
        original = replacement.end
    }
    return CommentInputPlan(replacements, emojiRuns, linkSpans)
}

private fun mapOriginalToTransformed(
    replacements: List<CommentInputReplacement>,
    offset: Int,
): Int {
    var delta = 0
    replacements.forEach { replacement ->
        if (replacement.end <= offset) {
            delta += replacement.text.length - (replacement.end - replacement.start)
        } else if (replacement.start < offset) {
            return replacement.start + delta
        } else {
            return offset + delta
        }
    }
    return offset + delta
}

/** 光标偏移映射交给框架；样式范围使用显示态偏移。 */
internal class CommentInputOutputTransformation(
    private val mentionNames: List<String>,
    private val accentColor: Color,
    private val linkTitles: Map<String, String>,
) : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        val source = toString()
        val plan = planCommentInput(source, linkTitles)
        plan.replacements.asReversed().forEach { replacement ->
            replace(replacement.start, replacement.end, replacement.text)
        }
        plan.linkSpans.forEach { span ->
            addStyle(
                SpanStyle(color = accentColor, textDecoration = TextDecoration.Underline),
                span.first,
                span.last + 1,
            )
        }
        findMentionRanges(source, mentionNames).forEach { range ->
            val start = mapOriginalToTransformed(plan.replacements, range.first)
            val end = mapOriginalToTransformed(plan.replacements, range.last + 1)
            if (end > start) {
                addStyle(
                    SpanStyle(color = accentColor, fontWeight = FontWeight.Medium),
                    start,
                    end,
                )
            }
        }
    }
}

/** 原子删除表情、已解析链接和提及，避免留下不完整的显示标记。 */
internal class CommentInputGuardTransformation(
    private val mentionNames: List<String>,
    private val linkUrls: Set<String>,
    private val onLinkCandidates: (List<String>) -> Unit,
) : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val oldText = originalText.toString()
        val newText = toString()
        if (oldText == newText) return
        val linkCandidates = commentLinkCandidates(oldText, newText)
        if (linkCandidates.isNotEmpty()) onLinkCandidates(linkCandidates)
        if (newText.length >= oldText.length) return
        val linkRanges = if (linkUrls.isEmpty()) {
            emptyList()
        } else {
            findPlainUrls(oldText)
                .filter { it.url in linkUrls }
                .map { it.range.first until (it.range.first + it.url.length) }
        }
        val spans = findEmojiPlaceholderRanges(oldText) + linkRanges + findMentionRanges(oldText, mentionNames)
        if (spans.isEmpty()) return
        val maxPrefix = min(oldText.length, newText.length)
        var prefix = 0
        while (prefix < maxPrefix && oldText[prefix] == newText[prefix]) prefix++
        var suffix = 0
        while (
            suffix < oldText.length - prefix &&
            suffix < newText.length - prefix &&
            oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]
        ) {
            suffix++
        }
        val removedStart = prefix
        val removedEnd = oldText.length - suffix
        if (removedEnd <= removedStart) return
        val span = spans.firstOrNull { span ->
            removedStart < span.last + 1 &&
                removedEnd > span.first &&
                !(removedStart <= span.first && removedEnd >= span.last + 1)
        } ?: return
        val leftLength = (removedStart - span.first).coerceAtLeast(0)
        val rightLength = (span.last + 1 - removedEnd).coerceAtLeast(0)
        replace(span.first, span.first + leftLength + rightLength, "")
        selection = TextRange(span.first)
    }
}

/** 遵循官方 `LinkToTextDetector.s()`：单个空格仅触发恰好在其前结束的 URL，多字符粘贴扫描插入段。 */
private fun commentLinkCandidates(oldText: String, newText: String): List<String> {
    val insertedLength = newText.length - oldText.length
    if (insertedLength <= 0) return emptyList()
    var prefix = 0
    val maxPrefix = min(oldText.length, newText.length)
    while (prefix < maxPrefix && oldText[prefix] == newText[prefix]) prefix++
    if (prefix + insertedLength > newText.length) return emptyList()
    val inserted = newText.substring(prefix, prefix + insertedLength)
    return when {
        inserted == " " -> findPlainUrls(newText)
            .filter { it.range.first + it.url.length == prefix }
            .map { it.url }

        inserted.length > 1 -> findPlainUrls(inserted).map { it.url }
        else -> emptyList()
    }
}
