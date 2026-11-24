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

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownShortcutTest {
    @Test
    fun headingAtTheStartOfAnEmptyFirstLineDoesNotModifyTheNextLine() {
        val result = TextFieldState("\na", TextRange(0))
        result.edit { applyMarkdownShortcut(MarkdownShortcut.H1) }

        assertEquals("# \na", result.text.toString())
        assertEquals(TextRange(2), result.selection)
    }

    @Test
    fun codeBlocksKeepFirstLineBoundariesAndSelections() {
        for ((input, unwrappedText) in arrayOf(
            TextFieldState("", TextRange(0)) to "\n",
            TextFieldState("a", TextRange(0)) to "a\n",
            TextFieldState("a", TextRange(1)) to "a\n",
            TextFieldState("\na", TextRange(0)) to "\na",
            TextFieldState("\na", TextRange(1)) to "\na\n",
            TextFieldState("\na", TextRange(2)) to "\na\n",
            TextFieldState("\na", TextRange(2, 1)) to "\na\n",
            TextFieldState("a\nb", TextRange(2)) to "a\nb\n",
        )) {
            val initialSelection = input.selection
            input.edit { applyMarkdownShortcut(MarkdownShortcut.CodeBlock) }
            input.edit { applyMarkdownShortcut(MarkdownShortcut.CodeBlock) }
            assertEquals(unwrappedText, input.text.toString())
            assertEquals(initialSelection, input.selection)
        }
        val firstLine = TextFieldState("\na", TextRange(0))
        firstLine.edit { applyMarkdownShortcut(MarkdownShortcut.CodeBlock) }
        assertEquals("```\n\n```\na", firstLine.text.toString())
        val lastLine = TextFieldState("\na", TextRange(2))
        lastLine.edit { applyMarkdownShortcut(MarkdownShortcut.CodeBlock) }
        assertEquals("\n```\na\n```\n", lastLine.text.toString())
    }

    @Test
    fun quoteAtTheStartOfAnEmptyFirstLineDoesNotModifyTheNextLine() {
        val result = TextFieldState("\na", TextRange(0))
        result.edit { applyMarkdownShortcut(MarkdownShortcut.Quote) }

        assertEquals("> \na", result.text.toString())
        assertEquals(TextRange(2), result.selection)
    }
}
