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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppSegmentedControl
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.RichEditorState
import com.github.zly2006.zhihu.ui.components.WriteContentMarkdownEditor
import com.github.zly2006.zhihu.ui.components.WriteContentRichEditor

@Composable
internal fun WriteAnswerQuestionSection(
    questionTitle: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 12.dp),
    ) {
        Text(
            text = "正在回答",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = questionTitle,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        )
    }
}

@Composable
internal fun WriteAnswerModeSelector(
    richMode: Boolean,
    enabled: Boolean,
    onModeChange: (Int) -> Unit,
) {
    AppSegmentedControl(
        options = listOf("图文", "Markdown"),
        selectedIndex = if (richMode) 0 else 1,
        onSelect = onModeChange,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun WriteAnswerEditorSection(
    draftSaveStatus: String,
    initialContentLoaded: Boolean,
    isDetecting: Boolean,
    richMode: Boolean,
    richEditor: RichEditorState,
    content: TextFieldState,
    isSubmitting: Boolean,
    isEditorConverting: Boolean,
    onRetryDraftLoad: () -> Unit,
) {
    if (draftSaveStatus.isNotBlank()) {
        Text(
            draftSaveStatus,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = AppDimens.Space8).semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    if (!initialContentLoaded && !isDetecting) {
        AppTextButton(
            text = "重试加载草稿",
            onClick = onRetryDraftLoad,
        )
    }
    if (richMode) {
        WriteContentRichEditor(
            state = richEditor,
            placeholder = "请输入图文回答内容……",
            enabled = !isSubmitting && !isEditorConverting,
            modifier = Modifier.testTag(WRITE_ANSWER_CONTENT_TAG),
        )
    } else {
        WriteContentMarkdownEditor(
            state = content,
            placeholder = "请输入图文回答内容……",
            contentTag = WRITE_ANSWER_CONTENT_TAG,
            enabled = !isSubmitting && !isEditorConverting,
        )
    }
}
