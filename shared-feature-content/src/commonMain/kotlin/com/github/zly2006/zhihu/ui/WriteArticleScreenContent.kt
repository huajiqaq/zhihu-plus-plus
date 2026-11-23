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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import com.github.zly2006.zhihu.editor.isImagePickerSupported
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppCompactTopBar
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppSegmentedControl
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.AppTextField
import com.github.zly2006.zhihu.ui.components.RichEditorState
import com.github.zly2006.zhihu.ui.components.WriteContentMarkdownEditor
import com.github.zly2006.zhihu.ui.components.WriteContentRichEditor
import com.github.zly2006.zhihu.ui.components.isRichEditorSupported
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

@Composable
internal fun WriteArticleScreenTopBar(
    textMeasurer: TextMeasurer,
    isSubmitting: Boolean,
    publishEnabled: Boolean,
    onBack: () -> Unit,
    onPublish: () -> Unit,
) {
    val titleStyle = if (settingSkinIsMiuix()) LocalAppSkinTokens.current.title3 else MaterialTheme.typography.titleLarge
    val actionStyle = if (settingSkinIsMiuix()) LocalAppSkinTokens.current.main else MaterialTheme.typography.labelLarge
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val requiredWidth = with(LocalDensity.current) {
            (
                textMeasurer.measure("写文章", style = titleStyle, softWrap = false).size.width +
                    textMeasurer.measure("发布中", style = actionStyle, softWrap = false).size.width
            ).toDp()
        } + AppDimens.TouchTarget + AppDimens.PageHorizontalPadding * 2 + AppDimens.Space24 * 2
        val separateAction = maxWidth < requiredWidth
        Column {
            AppCompactTopBar(
                title = "写文章",
                onBack = onBack,
                backTag = WRITE_ARTICLE_BACK_BUTTON_TAG,
                actions = {
                    if (!separateAction) {
                        AppButton(
                            text = if (isSubmitting) "发布中" else "发布",
                            enabled = publishEnabled,
                            onClick = onPublish,
                            modifier = Modifier.testTag(WRITE_ARTICLE_PUBLISH_TAG),
                        )
                    }
                },
            )
            if (separateAction) {
                Row(Modifier.fillMaxWidth().padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space4), horizontalArrangement = Arrangement.End) {
                    AppButton(
                        text = if (isSubmitting) "发布中" else "发布",
                        enabled = publishEnabled,
                        onClick = onPublish,
                        modifier = Modifier.testTag(WRITE_ARTICLE_PUBLISH_TAG),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ColumnScope.WriteArticleScreenEditor(
    title: TextFieldState,
    richMode: Boolean,
    isSubmitting: Boolean,
    isSaving: Boolean,
    isUploadingImage: Boolean,
    editorAvailable: Boolean,
    hasContent: Boolean,
    saveStatus: String,
    content: TextFieldState,
    richEditor: RichEditorState,
    isEditorConverting: Boolean,
    onModeSelect: (Int) -> Unit,
    onPreview: () -> Unit,
    onImage: () -> Unit,
    onSave: () -> Unit,
) {
    AppTextField(
        state = title,
        placeholder = "文章标题",
        labelText = "标题",
        enabled = !isSubmitting,
        modifier = Modifier.fillMaxWidth().testTag(WRITE_ARTICLE_TITLE_TAG),
    )
    if (isRichEditorSupported) {
        AppSegmentedControl(
            options = listOf("图文", "Markdown"),
            selectedIndex = if (richMode) 0 else 1,
            onSelect = onModeSelect,
            enabled = !isSubmitting && !isSaving && !isUploadingImage && editorAvailable,
            modifier = Modifier.fillMaxWidth().padding(top = AppDimens.Space12),
        )
    }
    Text(
        saveStatus,
        modifier = Modifier
            .padding(vertical = AppDimens.Space12)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
    if (richMode) {
        WriteContentRichEditor(
            state = richEditor,
            placeholder = "开始写作……",
            enabled = !isSubmitting && !isEditorConverting,
            modifier = Modifier.testTag(WRITE_ARTICLE_CONTENT_TAG),
        )
    } else {
        WriteContentMarkdownEditor(
            state = content,
            placeholder = "开始写作……",
            contentTag = WRITE_ARTICLE_CONTENT_TAG,
            enabled = !isSubmitting && !isEditorConverting,
        )
    }
    Row(
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(vertical = AppDimens.Space8)
            .testTag("write_article_action_toolbar"),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconButton(
            onClick = onPreview,
            enabled = hasContent && !isSubmitting && editorAvailable,
            modifier = Modifier.size(AppDimens.TouchTarget).testTag(WRITE_ARTICLE_PREVIEW_TAG),
        ) {
            Icon(Icons.Filled.Visibility, contentDescription = "预览")
        }
        if (isImagePickerSupported) {
            AppIconButton(
                onClick = onImage,
                enabled = !isSubmitting && !isSaving && !isUploadingImage && editorAvailable,
                modifier = Modifier
                    .size(AppDimens.TouchTarget)
                    .testTag(WRITE_ARTICLE_IMAGE_TAG)
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
            onClick = onSave,
            enabled = !isSubmitting && !isSaving && !isUploadingImage && editorAvailable,
            modifier = Modifier
                .widthIn(min = AppDimens.TouchTarget)
                .testTag(WRITE_ARTICLE_SAVE_TAG)
                .semantics { contentDescription = "保存草稿" },
        )
    }
}
