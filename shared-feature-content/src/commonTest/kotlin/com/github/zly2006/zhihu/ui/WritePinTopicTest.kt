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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.encodeToString

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
    fun currentDraftPreservesTopicIdentityAndSelectionWithoutRewritingLinks() {
        val topic = PinContentTopicItem("1354", "机器 学习")
        val content = "${checkNotNull(topic.toPinMarkdown())}$PIN_TOPIC_SEPARATOR[普通](https://www.zhihu.com/topic/2) [#普通](<https://www.zhihu.com/topic/3>)"
        val draft = LocalPinDraft(
            title = "保留",
            content = content,
            titleSelectionStart = 2,
            titleSelectionEnd = 0,
            contentSelectionStart = content.length,
            contentSelectionEnd = 1,
        )
        val saved = ZhihuJson.json.encodeToString(draft)
        val restored = checkNotNull(readLocalPinDraft(saved))
        assertEquals(draft, restored)
        assertEquals(listOf(topic), compilePinMarkdown(restored.content).topics)
        val outOfBounds = draft.copy(titleSelectionStart = 99, titleSelectionEnd = -1, contentSelectionStart = Int.MAX_VALUE, contentSelectionEnd = -1)
        assertEquals(
            draft.copy(contentSelectionEnd = 0),
            readLocalPinDraft(ZhihuJson.json.encodeToString(outOfBounds)),
        )
    }

    @Test
    fun nonCurrentDraftsAreNotRestored() {
        assertNull(readLocalPinDraft("""{"content":"[#普通](https://www.zhihu.com/topic/1)"}"""))
        assertNull(readLocalPinDraft("""{"schema":1,"content":"[#普通](https://www.zhihu.com/topic/1)","topics":[]}"""))
        assertNull(readLocalPinDraft("""{"schema":3,"content":"保留"}"""))
        assertNull(readLocalPinDraft("""{"schema":2,"content":null}"""))
    }
}
