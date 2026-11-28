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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.zly2006.zhihu.editor.UploadedZhihuImage
import com.github.zly2006.zhihu.i18n.ContentStrings
import com.github.zly2006.zhihu.platform.isSoftwareKeyboardVisible
import com.github.zly2006.zhihu.platform.rememberPlainTextClipboard
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.MarkdownShortcut
import com.github.zly2006.zhihu.ui.components.MarkdownShortcutToolbar
import com.github.zly2006.zhihu.ui.components.applyMarkdownShortcut

@Composable
internal fun WritePinEditorFrame(
    innerPadding: PaddingValues,
    localDraftSaved: Boolean,
    header: @Composable (Dp) -> Unit,
    content: @Composable ColumnScope.(Dp) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .padding(horizontal = AppDimens.PageHorizontalPadding),
    ) {
        val availableHeight = maxHeight
        Column(modifier = Modifier.fillMaxSize()) {
            header(availableHeight)
            if (localDraftSaved) {
                Text(
                    text = ContentStrings.draftSavedLocally,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = AppDimens.Space4),
                )
            }
            content(availableHeight)
        }
    }
}

@Composable
internal fun WritePinHeader(
    title: TextFieldState,
    images: List<UploadedZhihuImage>,
    imageListBounds: Rect,
    floatingActionBounds: Rect,
    layoutDirection: LayoutDirection,
    density: Density,
    isSubmitting: Boolean,
    onImageListBoundsChange: (Rect) -> Unit,
    onRemoveImage: (Int) -> Unit,
) {
    WritePinTitleField(
        title = title,
        isSubmitting = isSubmitting,
    )
    if (images.isNotEmpty()) {
        WritePinImageList(
            images = images,
            imageListBounds = imageListBounds,
            floatingActionBounds = floatingActionBounds,
            layoutDirection = layoutDirection,
            density = density,
            isSubmitting = isSubmitting,
            onBoundsChanged = onImageListBoundsChange,
            onRemoveImage = onRemoveImage,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WritePinTitleField(
    title: TextFieldState,
    isSubmitting: Boolean,
) {
    BasicTextField(
        state = title,
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(WRITE_PIN_TITLE_TAG)
                .semantics { contentDescription = ContentStrings.optionalPinTitle },
        enabled = !isSubmitting,
        textStyle =
            MaterialTheme.typography.titleLarge.copy(
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorator = { innerTextField ->
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 12.dp),
            ) {
                if (title.text.isEmpty()) {
                    Text(
                        text = ContentStrings.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                innerTextField()
            }
        },
    )
    HorizontalDivider(
        modifier = Modifier.padding(bottom = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ColumnScope.WritePinMarkdownEditor(
    state: TextFieldState,
    topics: PinTopicEditing,
    typedTopicStart: Int?,
    isSubmitting: Boolean,
    onAddTopic: () -> Unit,
) {
    val color = MaterialTheme.colorScheme.primary
    val copy = rememberPlainTextClipboard()
    Column(Modifier.fillMaxWidth().weight(1f)) {
        BasicTextField(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag(WRITE_PIN_CONTENT_TAG)
                .semantics { contentDescription = ContentStrings.pinPlaceholder },
            enabled = !isSubmitting,
            inputTransformation = topics,
            outputTransformation = remember(topics, color) { topics.output(SpanStyle(color = color)) },
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
                            text = ContentStrings.pinPlaceholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (topics.selectedMarkdown(state) != null) {
            AppTextButton(text = ContentStrings.copyMarkdown, onClick = {
                topics.selectedMarkdown(state)?.let { copy("pin-markdown", it) }
            })
        }
        if (!isSoftwareKeyboardVisible()) {
            MarkdownShortcutToolbar(
                onApplyShortcut = { shortcut ->
                    if (shortcut == MarkdownShortcut.Topic) {
                        onAddTopic()
                    } else {
                        state.edit {
                            selection = topics.selection(asCharSequence(), selection)
                            applyMarkdownShortcut(shortcut)
                        }
                    }
                },
                shortcuts = listOf(MarkdownShortcut.Topic) + MarkdownShortcut.entries.filterNot { it == MarkdownShortcut.Topic },
                enabled = !isSubmitting,
            )
        }
    }
    if (typedTopicStart != null && !isSubmitting) {
        AppTextButton(
            text = ContentStrings.addTopicToHash,
            onClick = onAddTopic,
            modifier = Modifier.padding(bottom = AppDimens.Space12),
        )
    }
}
