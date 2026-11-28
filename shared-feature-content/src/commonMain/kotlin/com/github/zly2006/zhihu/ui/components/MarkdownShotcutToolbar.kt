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

package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.zly2006.zhihu.i18n.ContentStrings

enum class MarkdownShortcut(
    private val symbol: String,
    private val operationText: () -> String,
) {
    H1("H1", { ContentStrings.headingOne }),
    H2("H2", { ContentStrings.headingTwo }),
    Bold("B", { ContentStrings.bold }),
    Italic("I", { ContentStrings.italic }),
    Link("Link", { ContentStrings.insertLink }),
    Quote(">", { ContentStrings.quote }),
    InlineCode("</>", { ContentStrings.inlineCode }),
    CodeBlock("```", { ContentStrings.codeBlock }),
    Divider("HR", { ContentStrings.divider }),
    Math("∑", { ContentStrings.mathFormula }),
    Topic("#", { ContentStrings.insertTopic }),
    ;

    val label: String get() = if (this == Link) ContentStrings.link else symbol
    val operationName: String get() = operationText()
}

@Composable
fun MarkdownShortcutToolbar(
    onApplyShortcut: (MarkdownShortcut) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shortcuts: List<MarkdownShortcut> = MarkdownShortcut.entries,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag("markdown_shortcut_toolbar"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            shortcuts.forEach { shortcut ->
                ShortcutButton(
                    label = shortcut.label,
                    operationName = shortcut.operationName,
                    enabled = enabled,
                    onClick = { onApplyShortcut(shortcut) },
                )
            }
            Spacer(Modifier.width(2.dp))
        }
    }
}

@Composable
private fun ShortcutButton(
    label: String,
    operationName: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    AppTextButton(
        onClick = onClick,
        enabled = enabled,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { contentDescription = operationName },
    ) {
        Text(
            text = label,
            modifier = Modifier.clearAndSetSemantics { },
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

fun TextFieldBuffer.applyMarkdownShortcut(shortcut: MarkdownShortcut) {
    when (shortcut) {
        MarkdownShortcut.H1 -> toggleHeading(targetPrefix = "# ", otherPrefix = "## ")
        MarkdownShortcut.H2 -> toggleHeading(targetPrefix = "## ", otherPrefix = "# ")
        MarkdownShortcut.Bold -> toggleWrapSelection(prefix = "**", suffix = "**", placeholder = ContentStrings.bold)
        MarkdownShortcut.Italic -> toggleWrapSelection(prefix = "*", suffix = "*", placeholder = ContentStrings.italic)
        MarkdownShortcut.Quote -> toggleQuoteAtLineStart()
        MarkdownShortcut.Divider -> insertBlock("---")
        MarkdownShortcut.CodeBlock -> toggleCodeBlock()
        MarkdownShortcut.InlineCode -> toggleWrapSelection(prefix = "`", suffix = "`", placeholder = ContentStrings.codePlaceholder)
        MarkdownShortcut.Math -> toggleWrapSelection(prefix = "$", suffix = "$", placeholder = "x")
        MarkdownShortcut.Topic -> replaceSelection("#", 1)
        MarkdownShortcut.Link -> insertLink()
    }
}

fun TextFieldBuffer.replaceSelection(
    insert: String,
    cursorOffsetInInsert: Int,
) {
    val start = selection.min
    replace(start, selection.max, insert)
    selection = TextRange((start + cursorOffsetInInsert).coerceIn(0, length))
}

private fun TextFieldBuffer.wrapSelection(
    prefix: String,
    suffix: String,
    placeholder: String,
) {
    val text = toString()
    val start = selection.min
    val end = selection.max
    val selected = text.substring(start, end)
    val core = selected.ifEmpty { placeholder }
    val insert = prefix + core + suffix
    val cursorOffset =
        if (selected.isEmpty()) {
            prefix.length
        } else {
            insert.length
        }
    return replaceSelection(insert = insert, cursorOffsetInInsert = cursorOffset)
}

private fun TextFieldBuffer.toggleHeading(
    targetPrefix: String,
    otherPrefix: String,
) {
    val text = toString()
    val cursor = selection.min
    val lineStart = text.lineStartOf(cursor)
    return when {
        text.startsWith(targetPrefix, startIndex = lineStart) -> {
            replaceAt(
                replaceStart = lineStart,
                replaceEndExclusive = lineStart + targetPrefix.length,
                replaceWith = "",
            )
        }
        text.startsWith(otherPrefix, startIndex = lineStart) -> {
            replaceAt(
                replaceStart = lineStart,
                replaceEndExclusive = lineStart + otherPrefix.length,
                replaceWith = targetPrefix,
            )
        }
        else -> {
            replaceAt(
                replaceStart = lineStart,
                replaceEndExclusive = lineStart,
                replaceWith = targetPrefix,
            )
        }
    }
}

private fun TextFieldBuffer.toggleQuoteAtLineStart() {
    val text = toString()
    val cursor = selection.min
    val lineStart = text.lineStartOf(cursor)
    val lineEnd = text.lineEndOf(cursor)
    var idx = lineStart
    while (idx + 2 <= lineEnd && text.startsWith("> ", startIndex = idx)) {
        idx += 2
    }
    return if (idx > lineStart) {
        replaceAt(
            replaceStart = lineStart,
            replaceEndExclusive = idx,
            replaceWith = "",
        )
    } else {
        replaceAt(
            replaceStart = lineStart,
            replaceEndExclusive = lineStart,
            replaceWith = "> ",
        )
    }
}

private fun TextFieldBuffer.toggleWrapSelection(
    prefix: String,
    suffix: String,
    placeholder: String,
) {
    val text = toString()

    fun isSingleMarker(marker: Char): Boolean =
        prefix.length == 1 && suffix.length == 1 && prefix[0] == marker && suffix[0] == marker

    fun isSingleMarkerBoundary(index: Int, marker: Char): Boolean {
        val prev = text.getOrNull(index - 1)
        val next = text.getOrNull(index + 1)
        if (prev == marker || next == marker) return false
        return true
    }

    val start = selection.min
    val end = selection.max
    if (start != end) {
        val canUnwrapAroundSelection =
            start >= prefix.length &&
                end + suffix.length <= text.length &&
                text.regionMatches(start - prefix.length, prefix, 0, prefix.length) &&
                text.regionMatches(end, suffix, 0, suffix.length) &&
                when {
                    isSingleMarker('*') && prefix == "*" -> isSingleMarkerBoundary(start - 1, '*') && isSingleMarkerBoundary(end, '*')
                    isSingleMarker('$') && prefix == "$" -> isSingleMarkerBoundary(start - 1, '$') && isSingleMarkerBoundary(end, '$')
                    else -> true
                }

        if (canUnwrapAroundSelection) {
            val removePrefixStart = start - prefix.length
            val removePrefixEndExclusive = start
            val removeSuffixStart = end
            val removeSuffixEndExclusive = end + suffix.length
            return removeRanges(
                ranges =
                    listOf(
                        removeSuffixStart to removeSuffixEndExclusive,
                        removePrefixStart to removePrefixEndExclusive,
                    ),
                newSelection = TextRange(removePrefixStart, removeSuffixStart - prefix.length),
            )
        }

        val selected = text.substring(start, end)
        val canUnwrapInsideSelection =
            selected.startsWith(prefix) &&
                selected.endsWith(suffix) &&
                selected.length >= prefix.length + suffix.length &&
                when {
                    isSingleMarker('*') && prefix == "*" -> isSingleMarkerBoundary(start, '*') && isSingleMarkerBoundary(end - 1, '*')
                    isSingleMarker('$') && prefix == "$" -> isSingleMarkerBoundary(start, '$') && isSingleMarkerBoundary(end - 1, '$')
                    else -> true
                }

        if (canUnwrapInsideSelection) {
            val core = selected.substring(prefix.length, selected.length - suffix.length)
            replace(start, end, core)
            selection = TextRange(start, start + core.length)
            return
        }

        val insert = prefix + selected + suffix
        return replaceSelection(insert = insert, cursorOffsetInInsert = insert.length)
    }

    val cursor = selection.min
    val lineStart = text.lineStartOf(cursor)
    val lineEnd = text.lineEndOf(cursor)
    val prefixStart = text.lastIndexOf(prefix, startIndex = (cursor - 1).coerceAtLeast(lineStart))
    if (prefixStart >= lineStart && prefixStart + prefix.length <= cursor) {
        val suffixStart = text.indexOf(suffix, startIndex = cursor)
        if (suffixStart >= 0 && suffixStart + suffix.length <= lineEnd) {
            val canUnwrapAtCursor =
                when {
                    isSingleMarker('*') && prefix == "*" -> isSingleMarkerBoundary(prefixStart, '*') && isSingleMarkerBoundary(suffixStart, '*')
                    isSingleMarker('$') && prefix == "$" -> isSingleMarkerBoundary(prefixStart, '$') && isSingleMarkerBoundary(suffixStart, '$')
                    else -> true
                }
            if (canUnwrapAtCursor) {
                val removePrefixStart = prefixStart
                val removePrefixEndExclusive = prefixStart + prefix.length
                val removeSuffixStart = suffixStart
                val removeSuffixEndExclusive = suffixStart + suffix.length
                val newCursor = (cursor - prefix.length).coerceAtLeast(0)
                return removeRanges(
                    ranges =
                        listOf(
                            removeSuffixStart to removeSuffixEndExclusive,
                            removePrefixStart to removePrefixEndExclusive,
                        ),
                    newSelection = TextRange(newCursor, newCursor),
                )
            }
        }
    }

    text
        .findWordRangeAtCursor(cursor)
        ?.takeIf { it.min != it.max }
        ?.let { wordRange ->
            val wordStart = wordRange.min
            val wordEnd = wordRange.max
            val cursorOffsetInWord = (cursor - wordStart).coerceIn(0, wordEnd - wordStart)
            val word = text.substring(wordStart, wordEnd)
            val insert = prefix + word + suffix
            replace(wordStart, wordEnd, insert)
            selection = TextRange((wordStart + prefix.length + cursorOffsetInWord).coerceIn(0, length))
            return
        }

    return wrapSelection(prefix = prefix, suffix = suffix, placeholder = placeholder)
}

private fun TextFieldBuffer.insertBlock(block: String) {
    val text = toString()
    val cursor = selection.min
    val before = text.getOrNull(cursor - 1)
    val after = text.getOrNull(cursor)
    val prefix = if (cursor == 0 || before == '\n') "" else "\n"
    val suffix = if (after == null || after == '\n') "" else "\n"
    val insert = prefix + block + "\n" + suffix
    val cursorOffset = insert.length
    return replaceSelection(insert = insert, cursorOffsetInInsert = cursorOffset)
}

private fun TextFieldBuffer.insertCodeBlock() {
    val text = toString()
    val oldSelection = selection
    val start = selection.min
    val end = selection.max
    val hasSelection = start != end
    val endForLine =
        if (hasSelection && end > 0 && text.getOrNull(end - 1) == '\n') {
            end - 1
        } else {
            end
        }
    val startLineStart = text.lineStartOf(start)
    val endLineStart = text.lineStartOf(endForLine)
    val wrapEndExclusive = text.lineEndExclusiveOf(endLineStart)
    val content = text.substring(startLineStart, wrapEndExclusive)
    val open = "```\n"
    val close = "```\n"
    val insert = open + (if (content.endsWith('\n')) content else "$content\n") + close
    replace(startLineStart, wrapEndExclusive, insert)

    val shift = open.length
    selection = TextRange(
        (oldSelection.start + shift).coerceIn(0, length),
        (oldSelection.end + shift).coerceIn(0, length),
    )
}

private fun TextFieldBuffer.toggleCodeBlock() {
    val text = toString()
    val cursor = selection.min
    val startFenceLineStart = text.findPreviousFenceLineStart(cursor) ?: return insertCodeBlock()
    val startFenceLineEndExclusive = text.lineEndExclusiveOf(startFenceLineStart)
    val endFenceLineStart = text.findNextFenceLineStart(startFenceLineEndExclusive) ?: return insertCodeBlock()
    val endFenceLineEndExclusive = text.lineEndExclusiveOf(endFenceLineStart)

    val isCursorInside =
        cursor >= startFenceLineEndExclusive &&
            cursor <= endFenceLineStart

    if (!isCursorInside) return insertCodeBlock()

    val startFenceLen = startFenceLineEndExclusive - startFenceLineStart
    val desiredSelection =
        if (selection.min == selection.max) {
            val newCursor = (cursor - startFenceLen).coerceAtLeast(0)
            TextRange(newCursor, newCursor)
        } else {
            null
        }
    return removeRanges(
        ranges =
            listOf(
                endFenceLineStart to endFenceLineEndExclusive,
                startFenceLineStart to startFenceLineEndExclusive,
            ),
        newSelection = desiredSelection,
    )
}

private fun TextFieldBuffer.insertLink() {
    val text = toString()
    val start = selection.min
    val end = selection.max
    val selected = text.substring(start, end)
    val linkText = selected.ifEmpty { ContentStrings.linkText }
    val insert = "[$linkText](https://)"
    val cursorOffset =
        if (selected.isEmpty()) {
            1
        } else {
            insert.indexOf("https://")
        }
    return replaceSelection(insert = insert, cursorOffsetInInsert = cursorOffset)
}

private fun TextFieldBuffer.replaceAt(
    replaceStart: Int,
    replaceEndExclusive: Int,
    replaceWith: String,
) {
    val oldLength = length
    val oldSelection = selection
    val start = replaceStart.coerceIn(0, oldLength)
    val endExclusive = replaceEndExclusive.coerceIn(start, oldLength)
    val delta = replaceWith.length - (endExclusive - start)
    replace(start, endExclusive, replaceWith)

    fun map(pos: Int): Int {
        val p = pos.coerceIn(0, oldLength)
        if (start == endExclusive) {
            return if (p < start) {
                p
            } else {
                (p + replaceWith.length).coerceIn(0, length)
            }
        }
        return when {
            p < start -> p
            p < endExclusive -> start + replaceWith.length
            else -> (p + delta).coerceIn(0, length)
        }
    }

    selection = TextRange(map(oldSelection.start), map(oldSelection.end))
}

private fun TextFieldBuffer.removeRanges(
    ranges: List<Pair<Int, Int>>,
    newSelection: TextRange? = null,
) {
    var newStart = selection.start
    var newEnd = selection.end

    fun map(pos: Int, start: Int, endExclusive: Int): Int =
        when {
            pos <= start -> pos
            pos < endExclusive -> start
            else -> pos - (endExclusive - start)
        }

    ranges
        .sortedByDescending { it.first }
        .forEach { (rawStart, rawEndExclusive) ->
            val start = rawStart.coerceIn(0, length)
            val endExclusive = rawEndExclusive.coerceIn(start, length)
            if (endExclusive == start) return@forEach
            replace(start, endExclusive, "")
            newStart = map(newStart, start, endExclusive).coerceIn(0, length)
            newEnd = map(newEnd, start, endExclusive).coerceIn(0, length)
        }

    val finalSelection = newSelection ?: TextRange(newStart, newEnd)
    selection = TextRange(
        finalSelection.start.coerceIn(0, length),
        finalSelection.end.coerceIn(0, length),
    )
}

private fun String.lineStartOf(index: Int): Int {
    val cursor = index.coerceIn(0, length)
    val idx = lastIndexOf('\n', startIndex = cursor - 1)
    return if (idx < 0) 0 else idx + 1
}

private fun String.lineEndOf(index: Int): Int {
    val cursor = index.coerceIn(0, length)
    val idx = indexOf('\n', startIndex = cursor)
    return if (idx < 0) length else idx
}

private fun String.lineEndExclusiveOf(lineStart: Int): Int {
    val start = lineStart.coerceIn(0, length)
    val idx = indexOf('\n', startIndex = start)
    return if (idx < 0) length else idx + 1
}

private fun String.isFenceLineStart(lineStart: Int): Boolean {
    if (lineStart < 0 || lineStart > length) return false
    if (!startsWith("```", startIndex = lineStart)) return false
    val after = getOrNull(lineStart + 3) ?: return true
    return after == '\n'
}

private fun String.findPreviousFenceLineStart(fromIndex: Int): Int? {
    var lineStart = lineStartOf(fromIndex)
    while (true) {
        if (isFenceLineStart(lineStart)) return lineStart
        if (lineStart == 0) return null
        lineStart = lineStartOf(lineStart - 1)
    }
}

private fun String.findNextFenceLineStart(fromIndex: Int): Int? {
    var index = fromIndex.coerceIn(0, length)
    while (index < length) {
        val lineStart = lineStartOf(index)
        if (lineStart >= index && isFenceLineStart(lineStart)) return lineStart
        val next = lineEndExclusiveOf(lineStart)
        if (next <= index) return null
        index = next
    }
    return null
}

private fun String.findWordRangeAtCursor(cursor: Int): TextRange? {
    fun isCjkLike(codePoint: Int): Boolean =
        codePoint in 0x3400..0x4DBF ||
            codePoint in 0x4E00..0x9FFF ||
            codePoint in 0xF900..0xFAFF ||
            codePoint in 0x2F800..0x2FA1F ||
            codePoint in 0x3040..0x309F ||
            codePoint in 0x30A0..0x30FF ||
            codePoint in 0xAC00..0xD7AF ||
            codePoint in 0x20000..0x2A6DF ||
            codePoint in 0x2A700..0x2B73F ||
            codePoint in 0x2B740..0x2B81F ||
            codePoint in 0x2B820..0x2CEAF ||
            codePoint in 0x2CEB0..0x2EBEF

    fun classify(c: Char): WordCharClass =
        when {
            c == '_' -> WordCharClass.Latin
            c.isLetterOrDigit() -> if (isCjkLike(c.code)) WordCharClass.Cjk else WordCharClass.Latin
            else -> WordCharClass.Other
        }

    val index = cursor.coerceIn(0, length)
    val beforeClass = getOrNull(index - 1)?.let(::classify) ?: WordCharClass.Other
    val afterClass = getOrNull(index)?.let(::classify) ?: WordCharClass.Other
    val baseClass = if (afterClass != WordCharClass.Other) afterClass else beforeClass
    if (baseClass == WordCharClass.Other) return null
    val baseIndex = if (afterClass != WordCharClass.Other) index else (index - 1).coerceAtLeast(0)

    var start = baseIndex
    while (start > 0 && classify(this[start - 1]) == baseClass) {
        start--
    }
    var endExclusive = baseIndex + 1
    while (endExclusive < length && classify(this[endExclusive]) == baseClass) {
        endExclusive++
    }
    return TextRange(start, endExclusive)
}

private enum class WordCharClass {
    Latin,
    Cjk,
    Other,
}
