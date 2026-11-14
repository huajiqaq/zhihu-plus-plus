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

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WriteContentMarkdownEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    contentTag: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    topPadding: Dp = 16.dp,
    bottomPadding: Dp = 160.dp,
    onVerticalScroll: ((Float) -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    extraShortcuts: List<MarkdownShortcut> = emptyList(),
) {
    val scrollState = rememberScrollState()
    val currentOnVerticalScroll by rememberUpdatedState(onVerticalScroll)
    val scrollDirectionObserver = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            var previousPosition = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            ).position
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.firstOrNull()?.let { change ->
                    val delta = change.position.y - previousPosition.y
                    if (delta != 0f) currentOnVerticalScroll?.invoke(delta)
                    previousPosition = change.position
                }
            } while (event.changes.any { it.pressed })
        }
    }

    Box(modifier = modifier.then(if (onVerticalScroll == null) Modifier else scrollDirectionObserver)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier =
                Modifier
                    .fillMaxSize()
                    .testTag(contentTag)
                    .semantics { contentDescription = placeholder },
            enabled = enabled,
            visualTransformation = visualTransformation,
            textStyle =
                TextStyle(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 17.sp,
                    lineHeight = 26.sp,
                ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(top = topPadding, bottom = bottomPadding),
                ) {
                    if (value.text.isEmpty()) {
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
        MarkdownShortcutToolbar(
            onApplyShortcut = { shortcut ->
                onValueChange(value.applyMarkdownShortcut(shortcut))
            },
            shortcuts = extraShortcuts + MarkdownShortcut.entries.filterNot { it == MarkdownShortcut.Topic },
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 10.dp),
            enabled = enabled,
        )
    }
}
