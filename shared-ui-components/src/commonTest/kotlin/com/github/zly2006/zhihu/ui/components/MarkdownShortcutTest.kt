package com.github.zly2006.zhihu.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownShortcutTest {
    @Test
    fun headingAtTheStartOfAnEmptyFirstLineDoesNotModifyTheNextLine() {
        val result = TextFieldValue("\na", TextRange(0)).applyMarkdownShortcut(MarkdownShortcut.H1)

        assertEquals("# \na", result.text)
        assertEquals(TextRange(2), result.selection)
    }

    @Test
    fun codeBlocksKeepFirstLineBoundariesAndSelections() {
        for ((input, unwrappedText) in arrayOf(
            TextFieldValue("", TextRange(0)) to "\n",
            TextFieldValue("a", TextRange(0)) to "a\n",
            TextFieldValue("a", TextRange(1)) to "a\n",
            TextFieldValue("\na", TextRange(0)) to "\na",
            TextFieldValue("\na", TextRange(1)) to "\na\n",
            TextFieldValue("\na", TextRange(2)) to "\na\n",
            TextFieldValue("\na", TextRange(2, 1)) to "\na\n",
            TextFieldValue("a\nb", TextRange(2)) to "a\nb\n",
        )) {
            val wrapped = input.applyMarkdownShortcut(MarkdownShortcut.CodeBlock)
            val unwrapped = wrapped.applyMarkdownShortcut(MarkdownShortcut.CodeBlock)
            assertEquals(unwrappedText, unwrapped.text)
            assertEquals(input.selection, unwrapped.selection)
        }
        assertEquals("```\n\n```\na", TextFieldValue("\na", TextRange(0)).applyMarkdownShortcut(MarkdownShortcut.CodeBlock).text)
        assertEquals("\n```\na\n```\n", TextFieldValue("\na", TextRange(2)).applyMarkdownShortcut(MarkdownShortcut.CodeBlock).text)
    }

    @Test
    fun quoteAtTheStartOfAnEmptyFirstLineDoesNotModifyTheNextLine() {
        val result = TextFieldValue("\na", TextRange(0)).applyMarkdownShortcut(MarkdownShortcut.Quote)

        assertEquals("> \na", result.text)
        assertEquals(TextRange(2), result.selection)
    }
}
