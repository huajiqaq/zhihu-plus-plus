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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.editor.isImagePickerSupported
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.AppTopBar
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

@Composable
internal fun WriteAnswerTopBar(
    isDetecting: Boolean,
    isLoadingExistingAnswer: Boolean,
    existingAnswerId: Long?,
    isSubmitting: Boolean,
    publishEnabled: Boolean,
    onBack: () -> Unit,
    onShowSettings: () -> Unit,
    onPublish: () -> Unit,
) {
    AppTopBar(
        title = {
            Text(
                text =
                    when {
                        isDetecting || isLoadingExistingAnswer -> "正在检测已有回答..."
                        existingAnswerId != null -> "编辑回答"
                        else -> "写回答"
                    },
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        },
        onBack = onBack,
        backTag = WRITE_ANSWER_BACK_BUTTON_TAG,
        actions = {
            AppIconButton(
                onClick = onShowSettings,
                enabled = !isSubmitting,
            ) {
                Icon(Icons.Default.Settings, contentDescription = "回答设置")
            }
            AppButton(
                onClick = onPublish,
                enabled = publishEnabled,
            ) {
                if (isSubmitting) {
                    AppSpinner(modifier = Modifier, size = 18.dp)
                } else {
                    Text("发布")
                }
            }
        },
    )
}

@Composable
internal fun ColumnScope.WriteAnswerActionToolbar(
    isSubmitting: Boolean,
    hasContent: Boolean,
    editorAvailable: Boolean,
    isUploadingImage: Boolean,
    initialContentLoaded: Boolean,
    onShowPreview: () -> Unit,
    onInsertImage: () -> Unit,
    onSaveDraft: () -> Unit,
) {
    Row(
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(vertical = AppDimens.Space8)
            .testTag("write_answer_action_toolbar"),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconButton(
            onClick = onShowPreview,
            enabled = !isSubmitting && hasContent && editorAvailable,
            modifier = Modifier.size(AppDimens.TouchTarget).testTag(WRITE_ANSWER_FAB_PREVIEW_TAG),
        ) {
            Icon(Icons.Filled.Visibility, contentDescription = "预览")
        }
        if (isImagePickerSupported) {
            AppIconButton(
                onClick = onInsertImage,
                enabled = !isSubmitting && !isUploadingImage && editorAvailable,
                modifier = Modifier
                    .size(AppDimens.TouchTarget)
                    .testTag(WRITE_ANSWER_FAB_IMAGE_TAG)
                    .semantics { contentDescription = "插入图片" },
            ) {
                if (isUploadingImage) {
                    AppSpinner(modifier = Modifier, size = AppDimens.IconSizeCompact)
                } else {
                    Icon(Icons.Filled.Image, contentDescription = "插入图片")
                }
            }
        }
        AppTextButton(
            text = "草稿",
            onClick = onSaveDraft,
            enabled = initialContentLoaded && !isSubmitting && !isUploadingImage && editorAvailable,
            modifier = Modifier
                .widthIn(min = AppDimens.TouchTarget)
                .testTag(WRITE_ANSWER_FAB_SAVE_TAG)
                .semantics { contentDescription = "保存草稿" },
        )
    }
}
