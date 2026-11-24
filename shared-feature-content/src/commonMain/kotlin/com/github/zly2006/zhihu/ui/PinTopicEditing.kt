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

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import com.github.zly2006.zhihu.editor.PIN_TOPIC_SEPARATOR
import com.github.zly2006.zhihu.editor.PinContentTopicItem
import com.github.zly2006.zhihu.editor.PinTopicToken
import com.github.zly2006.zhihu.editor.pinTopicTokens
import com.github.zly2006.zhihu.editor.toPinMarkdown
import com.github.zly2006.zhihu.ui.components.replaceSelection

/** 只缓存最近一次源码的投影；旧 IME 快照、撤销和重做都按自身的正文重新定位。 */
internal class PinTopicEditing : InputTransformation {
    private var source = ""
    private var tokens: List<PinTopicToken> = emptyList()

    private fun tokens(text: CharSequence): List<PinTopicToken> {
        val markdown = text.toString()
        if (source != markdown) {
            tokens = pinTopicTokens(markdown)
            source = markdown
        }
        return tokens
    }

    fun output(style: SpanStyle) = OutputTransformation {
        val source = toString()
        val projection = tokens(source)
        for (token in projection.asReversed()) {
            val end = token.endWithSeparator(source)
            val label = "#${token.topic.topicName}"
            replace(token.start, end, if (end == token.end) label else "$label ")
        }
        var shift = 0
        for (token in projection) {
            val start = token.start + shift
            val length = 1 + token.topic.topicName.length
            addStyle(style, start, start + length)
            val end = token.endWithSeparator(source)
            shift += length + (if (end == token.end) 0 else 1) - (end - token.start)
        }
    }

    /** 默认复制可见的 #名称；只有显式「复制 Markdown」才复制选区中的公开话题 URL。 */
    fun selectedMarkdown(state: TextFieldState): String? {
        val range = state.selection
        if (range.collapsed || tokens(state.text).none { range.min < it.end && range.max > it.start }) return null
        val whole = selection(state.text, range)
        return state.text.substring(whole.min, whole.max)
    }

    fun selection(text: CharSequence, range: TextRange, replaceTopic: Boolean = false): TextRange {
        var start = range.min
        var end = range.max
        for (token in tokens(text)) {
            val fullEnd = token.endWithSeparator(text)
            val limit = if (replaceTopic || range.max > token.end) fullEnd else token.end
            val hit = if (range.collapsed) range.start > token.start && range.start < fullEnd else range.min < limit && range.max > token.start
            if (hit) {
                start = minOf(start, token.start)
                end = maxOf(end, limit)
            }
        }
        return if (range.reversed) TextRange(end, start) else TextRange(start, end)
    }

    fun insert(state: TextFieldState, target: TextRange, topic: PinContentTopicItem): Boolean {
        val link = topic.toPinMarkdown() ?: return false
        val source = state.text.toString()
        val whole = selection(source, target, replaceTopic = true)
        val insertion = link + PIN_TOPIC_SEPARATOR
        val next = source.replaceRange(whole.min, whole.max, insertion)
        if (tokens(next).none { it.start == whole.min && it.topic == topic }) return false
        state.edit {
            selection = whole
            replaceSelection(insertion, insertion.length)
        }
        return true
    }

    override fun TextFieldBuffer.transformInput() {
        val original = originalText
        val previous = tokens(original)
        if (previous.isEmpty()) return
        if (changes.changeCount == 0) {
            if (!selection.collapsed) {
                selection = selection(original, selection)
            } else {
                val cursor = selection.start
                val token = previous.firstOrNull { cursor > it.start && cursor < it.endWithSeparator(original) } ?: return
                selection = TextRange(if (cursor < originalSelection.end) token.start else token.endWithSeparator(original))
            }
            return
        }

        val edits = List(changes.changeCount) { changes.getOriginalRange(it) to changes.getRange(it) }
        val damaged = previous.mapNotNull { token ->
            val end = token.endWithSeparator(original)
            val hit = edits.any { (change, _) ->
                if (change.collapsed) change.start > token.start && change.start < end else change.min < end && change.max > token.start
            }
            if (hit) TextRange(token.start, end) else null
        }
        if (damaged.isEmpty()) return

        /** 保留每个用户插入片段，只移除未改区间里残留的话题；普通输入不触碰 IME composition。 */
        val remnants = mutableListOf<TextRange>()
        var oldStart = 0
        var newStart = 0

        fun collectUntil(oldEnd: Int) {
            for (range in damaged) {
                val from = maxOf(oldStart, range.min)
                val to = minOf(oldEnd, range.max)
                if (from < to) remnants += TextRange(newStart + from - oldStart, newStart + to - oldStart)
            }
        }
        for ((before, after) in edits) {
            collectUntil(before.min)
            oldStart = before.max
            newStart = after.max
        }
        collectUntil(original.length)
        if (remnants.isEmpty()) return
        val proposed = selection

        fun map(offset: Int) = offset - remnants.sumOf { (offset - it.min).coerceIn(0, it.length) }
        for (range in remnants.asReversed()) delete(range.min, range.max)
        selection = TextRange(map(proposed.start), map(proposed.end))
    }
}
