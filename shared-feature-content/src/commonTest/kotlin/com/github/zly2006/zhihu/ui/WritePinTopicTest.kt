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

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.toTextFieldBuffer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.editor.PIN_TOPIC_SEPARATOR
import com.github.zly2006.zhihu.editor.PinContentTopicItem
import com.github.zly2006.zhihu.editor.compilePinMarkdown
import com.github.zly2006.zhihu.editor.toPinMarkdown
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WritePinTopicTest {
    @Test
    fun sameLabelKeepsBothIdsAndNativeUndoRestoresReversedSelection() {
        val first = PinContentTopicItem("1354", "机器 学习")
        val second = first.copy(topicId = "2")
        val suffix = checkNotNull(first.toPinMarkdown())
        val before = "选择 正文$suffix "
        val reversed = TextRange(2, 0)
        val state = TextFieldState(before, reversed)
        val editing = PinTopicEditing()
        assertEquals(true, editing.insert(state, reversed, second))
        val added = checkNotNull(second.toPinMarkdown())
        assertEquals("$added$PIN_TOPIC_SEPARATOR 正文$suffix ", state.text.toString())
        assertEquals(TextRange(added.length + PIN_TOPIC_SEPARATOR.length), state.selection)
        assertEquals(listOf(second, first), compilePinMarkdown(state.text.toString()).topics)
        val visible = state.toTextFieldBuffer()
        with(editing.output(SpanStyle(color = Color.Red))) { visible.transformOutput() }
        assertEquals("#机器 学习  正文#机器 学习 ", visible.toString())
        state.undoState.undo()
        assertEquals(before, state.text.toString())
        assertEquals(reversed, state.selection)
        assertEquals(listOf(first), compilePinMarkdown(state.text.toString()).topics)
        state.undoState.redo()
        assertEquals(listOf(second, first), compilePinMarkdown(state.text.toString()).topics)
    }

    @Test
    fun oldDraftConvertsOnlyUnambiguousMarkersAndMapsBothSelectionEnds() {
        val raw = """{"title":"保留","content":"#A] #坏 #坏","contentSelectionStart":9,"contentSelectionEnd":2,"topics":[{"topic":{"topic_id":"1","topic_name":"A]"},"start":0,"endExclusive":3},{"topic":{"topic_id":"2","topic_name":"坏"},"start":4,"endExclusive":6},{"topic":{"topic_id":"3","topic_name":"坏"},"start":4,"endExclusive":6},{"topic":{"topic_id":"-1","topic_name":"坏"},"start":7,"endExclusive":9},{}]}"""
        val draft = checkNotNull(readLocalPinDraft(raw))
        val link = "[#A\\]](https://www.zhihu.com/topic/1)"
        assertEquals("$link$PIN_TOPIC_SEPARATOR#坏 #坏", draft.content)
        assertEquals(draft.content.length, draft.contentSelectionStart)
        assertEquals(3, draft.contentSelectionEnd)
        val saved = ZhihuJson.json.encodeToString(draft)
        assertContains(saved, "\"schema\":2")
        assertFalse("\"topics\"" in saved)
        assertEquals(draft, readLocalPinDraft(saved))
        val unowned = checkNotNull(readLocalPinDraft(raw.replace("\"start\":0", "\"hasTrailingSpace\":false,\"start\":0")))
        assertEquals("$link #坏 #坏", unowned.content)
        assertEquals(emptyList(), compilePinMarkdown(checkNotNull(readLocalPinDraft("""{"content":"[#普通](https://www.zhihu.com/topic/1)"}""")).content).topics)
    }
}
