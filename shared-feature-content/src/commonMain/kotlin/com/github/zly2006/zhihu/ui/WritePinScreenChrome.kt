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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import com.github.zly2006.zhihu.editor.isImagePickerSupported
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppCompactTopBar
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppFloatingToolbar
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.WriteContentPreviewSheet
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WritePinTopBar(
    onBack: () -> Unit,
    onPublish: () -> Unit,
    isSubmitting: Boolean,
    isUploadingImage: Boolean,
) {
    val textMeasurer = rememberTextMeasurer()
    val titleStyle = if (settingSkinIsMiuix()) LocalAppSkinTokens.current.title3 else MaterialTheme.typography.titleLarge
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val requiredWidth = with(LocalDensity.current) {
            (
                textMeasurer.measure("发想法", style = titleStyle, softWrap = false).size.width +
                    textMeasurer.measure("发布", style = if (settingSkinIsMiuix()) LocalAppSkinTokens.current.main else MaterialTheme.typography.labelLarge, softWrap = false).size.width
            ).toDp()
        } + AppDimens.TouchTarget + AppDimens.PageHorizontalPadding * 2 + AppDimens.Space24 * 2
        val separateAction = maxWidth < requiredWidth
        Column {
            AppCompactTopBar(
                title = "发想法",
                onBack = onBack,
                backTag = WRITE_PIN_BACK_BUTTON_TAG,
                actions = {
                    if (!separateAction) {
                        AppButton(onClick = onPublish, enabled = !isSubmitting && !isUploadingImage) {
                            if (isSubmitting) AppSpinner(size = AppDimens.IconSizeCompact) else Text("发布")
                        }
                    }
                },
            )
            if (separateAction) {
                Row(Modifier.fillMaxWidth().padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space4), horizontalArrangement = Arrangement.End) {
                    AppButton(onClick = onPublish, enabled = !isSubmitting && !isUploadingImage) {
                        if (isSubmitting) AppSpinner(size = AppDimens.IconSizeCompact) else Text("发布")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WritePinBottomBar(
    onBoundsChanged: (Rect) -> Unit,
    onPreview: () -> Unit,
    contentText: String,
    isSubmitting: Boolean,
    isUploadingImage: Boolean,
    onAddImage: () -> Unit,
    onSaveDraft: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(vertical = AppDimens.Space8)
            .onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) },
        contentAlignment = Alignment.Center,
    ) {
        AppFloatingToolbar(modifier = Modifier.testTag("write_pin_action_toolbar")) {
            AppIconButton(
                onClick = onPreview,
                enabled = !isSubmitting && contentText.isNotBlank(),
                modifier = Modifier.size(AppDimens.TouchTarget).testTag(WRITE_PIN_FAB_PREVIEW_TAG),
            ) {
                Icon(Icons.Filled.Visibility, contentDescription = "预览")
            }
            if (isImagePickerSupported) {
                AppIconButton(
                    onClick = onAddImage,
                    enabled = !isSubmitting && !isUploadingImage,
                    modifier = Modifier
                        .size(AppDimens.TouchTarget)
                        .testTag(WRITE_PIN_FAB_IMAGE_TAG)
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
                enabled = !isSubmitting,
                modifier = Modifier
                    .widthIn(min = AppDimens.TouchTarget)
                    .testTag(WRITE_PIN_FAB_SAVE_TAG)
                    .semantics { contentDescription = "保存草稿" },
            )
        }
    }
}

@Composable
internal fun WritePinScaffold(
    onBack: () -> Unit,
    onPublish: () -> Unit,
    isSubmitting: Boolean,
    isUploadingImage: Boolean,
    onBoundsChanged: (Rect) -> Unit,
    onPreview: () -> Unit,
    contentText: String,
    onAddImage: () -> Unit,
    onSaveDraft: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    AppScaffold(
        topBar = {
            WritePinTopBar(
                onBack = onBack,
                onPublish = onPublish,
                isSubmitting = isSubmitting,
                isUploadingImage = isUploadingImage,
            )
        },
        bottomBar = {
            WritePinBottomBar(
                onBoundsChanged = onBoundsChanged,
                onPreview = onPreview,
                contentText = contentText,
                isSubmitting = isSubmitting,
                isUploadingImage = isUploadingImage,
                onAddImage = onAddImage,
                onSaveDraft = onSaveDraft,
            )
        },
    ) { innerPadding ->
        content(innerPadding)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WritePinOverlays(
    showPreviewSheet: Boolean,
    previewSheetState: SheetState,
    previewUseWebView: Boolean,
    isPreviewLoading: Boolean,
    previewHtml: String?,
    onDismissPreview: () -> Unit,
    errorDialogMessage: String?,
    onDismissError: () -> Unit,
    onCopyError: (String) -> Unit,
) {
    if (showPreviewSheet) {
        WriteContentPreviewSheet(
            sheetState = previewSheetState,
            useWebView = previewUseWebView,
            isLoading = isPreviewLoading,
            html = previewHtml,
            markdown = null,
            onDismissRequest = onDismissPreview,
        )
    }

    WriteOperationErrorDialog(
        message = errorDialogMessage,
        onDismissRequest = onDismissError,
        onCopy = onCopyError,
    )
}
