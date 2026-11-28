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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class RichEditorStateTest {
    @Test
    fun remountPreservesDraftAndSelectionWithoutConvertingToMarkdown() {
        val html = "<p><strong>正文</strong><img src=\"https://pic.example/image.png\" data-rawwidth=\"800\"></p>"
        val state = RichEditorState(html)
        state.setReady(true)
        state.updateSelection(2, 1)
        state.setReady(false)
        assertEquals(html, state.html)
        assertEquals(2, state.selectionIndex)
        assertEquals(1, state.selectionLength)
        assertTrue(state.hasContent)
        state.setReady(true)
        assertEquals(html, state.html)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun closedEditorFailsPendingSnapshotInsteadOfWaitingForever() = runTest {
        val state = RichEditorState("<p>保留草稿</p>")
        state.setReady(true)
        val snapshot = async { runCatching { state.snapshotHtml() } }
        runCurrent()
        state.setReady(false)
        assertTrue(snapshot.await().exceptionOrNull() is IllegalStateException)
        assertEquals("<p>保留草稿</p>", state.html)
        assertFailsWith<IllegalStateException> { state.snapshotHtml() }
    }
}
