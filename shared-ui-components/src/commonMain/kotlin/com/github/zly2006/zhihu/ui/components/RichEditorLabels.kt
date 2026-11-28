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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.zly2006.zhihu.ui.components

import com.github.zly2006.zhihu.i18n.AppLocale
import com.github.zly2006.zhihu.i18n.WidgetsStrings

fun richEditorUiLabels(): Map<String, String> = mapOf(
    "lang" to AppLocale.languageTag,
    "undo" to WidgetsStrings.editorUndo,
    "redo" to WidgetsStrings.editorRedo,
    "headingLevel" to WidgetsStrings.editorHeadingLevel,
    "paragraph" to WidgetsStrings.editorParagraph,
    "heading1" to WidgetsStrings.editorHeadingOne,
    "heading2" to WidgetsStrings.editorHeadingTwo,
    "heading3" to WidgetsStrings.editorHeadingThree,
    "heading4" to WidgetsStrings.editorHeadingFour,
    "bold" to WidgetsStrings.editorBold,
    "italic" to WidgetsStrings.editorItalic,
    "underline" to WidgetsStrings.editorUnderline,
    "ordered" to WidgetsStrings.editorOrderedList,
    "bullet" to WidgetsStrings.editorBulletList,
    "blockquote" to WidgetsStrings.editorQuote,
    "code" to WidgetsStrings.editorInlineCode,
    "code-block" to WidgetsStrings.editorCodeBlock,
    "link" to WidgetsStrings.editorInsertLink,
    "clean" to WidgetsStrings.editorClearFormatting,
    "visitLink" to WidgetsStrings.editorVisitLink,
    "enterLink" to WidgetsStrings.editorEnterLink,
    "editLink" to WidgetsStrings.editorEditLink,
    "saveLink" to WidgetsStrings.editorSaveLink,
    "removeLink" to WidgetsStrings.editorRemoveLink,
)
