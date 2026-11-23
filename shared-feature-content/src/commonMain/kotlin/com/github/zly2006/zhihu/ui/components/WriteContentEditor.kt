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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

@Composable
fun WriteContentMarkdownEditor(
    state: TextFieldState,
    placeholder: String,
    contentTag: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    scrollState: ScrollState? = null,
) {
    Column(modifier) {
        BasicTextField(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (scrollState == null) {
                        Modifier.heightIn(min = AppDimens.Space160)
                    } else {
                        Modifier.weight(1f)
                    },
                ).testTag(contentTag)
                .semantics { contentDescription = placeholder },
            enabled = enabled,
            scrollState = scrollState ?: rememberScrollState(),
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 17.sp,
                lineHeight = 26.sp,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorator = { innerTextField ->
                Box(Modifier.fillMaxWidth().padding(vertical = AppDimens.Space12)) {
                    if (state.text.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (WindowInsets.ime.getBottom(LocalDensity.current) == 0) {
            MarkdownShortcutToolbar(
                onApplyShortcut = { shortcut -> state.edit { applyMarkdownShortcut(shortcut) } },
                shortcuts = MarkdownShortcut.entries.filterNot { it == MarkdownShortcut.Topic },
                enabled = enabled,
            )
        }
    }
}
