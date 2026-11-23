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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.markdown.rememberMarkdownImageModel
import com.github.zly2006.zhihu.platform.rememberImagePreviewOpener
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppAlertDialog
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.AppTextField
import com.github.zly2006.zhihu.ui.components.RecordedVoiceComment
import com.github.zly2006.zhihu.ui.components.VoiceCommentPlayer
import com.github.zly2006.zhihu.ui.components.VoiceCommentRecorder
import com.github.zly2006.zhihu.viewmodel.CommentItem
import com.github.zly2006.zhihu.viewmodel.comment.CommentImageAttachment
import com.github.zly2006.zhihu.viewmodel.comment.CommentSticker
import com.github.zly2006.zhihu.viewmodel.comment.MentionCandidate
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CommentScreenTools(
    voiceRecorder: VoiceCommentRecorder,
    recordingElapsedMs: Long,
    isSending: Boolean,
    isUploadingImage: Boolean,
    isUploadingVoice: Boolean,
    commentInputBarColor: Color,
    uploadedVoiceId: String?,
    stopVoiceRecording: () -> Unit,
    requestVoicePermission: () -> Unit,
    launchImagePicker: (() -> Unit)?,
    onMentionClick: () -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space4)
            .testTag(COMMENT_TOOLS_ROW_TAG),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Space4),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (voiceRecorder.isSupported) {
            if (voiceRecorder.isRecording) {
                AppIconButton(
                    onClick = { stopVoiceRecording() },
                    modifier = Modifier
                        .size(AppDimens.TouchTarget)
                        .testTag(COMMENT_VOICE_RECORD_BUTTON_TAG),
                ) {
                    Icon(
                        Icons.Filled.Stop,
                        contentDescription = "停止录音",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    text = "录音中 ${(recordingElapsedMs / 1000).coerceAtLeast(0)}″",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                AppButton(
                    onClick = { requestVoicePermission() },
                    enabled = !isSending && !isUploadingImage && !isUploadingVoice,
                    modifier = Modifier
                        .heightIn(min = AppDimens.TouchTarget)
                        .testTag(COMMENT_VOICE_BUTTON_TAG),
                    shape = MaterialTheme.shapes.medium,
                    containerColor = commentInputBarColor,
                    contentColor = if (uploadedVoiceId != null) LocalAppSkinTokens.current.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    contentPadding = PaddingValues(horizontal = AppDimens.Space12, vertical = AppDimens.Space8),
                ) {
                    Icon(Icons.Outlined.Mic, contentDescription = "录制语音评论")
                    Spacer(modifier = Modifier.width(AppDimens.Space4))
                    Text("语音", style = LocalAppSkinTokens.current.footnote2)
                }
            }
        }
        if (launchImagePicker != null) {
            AppButton(
                onClick = launchImagePicker,
                enabled = !isUploadingImage && !isSending,
                modifier = Modifier
                    .heightIn(min = AppDimens.TouchTarget)
                    .testTag(COMMENT_IMAGE_BUTTON_TAG),
                shape = MaterialTheme.shapes.medium,
                containerColor = commentInputBarColor,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                contentPadding = PaddingValues(horizontal = AppDimens.Space12, vertical = AppDimens.Space8),
            ) {
                if (isUploadingImage) {
                    AppSpinner(size = AppDimens.IconSizeCompact)
                } else {
                    Icon(Icons.Outlined.Image, contentDescription = "添加图片")
                }
                Spacer(modifier = Modifier.width(AppDimens.Space4))
                Text("图片", style = LocalAppSkinTokens.current.footnote2)
            }
        }
        if (!voiceRecorder.isSupported || launchImagePicker == null) {
            Text(
                "当前平台暂不支持" + listOfNotNull(
                    "录音".takeUnless { voiceRecorder.isSupported },
                    "选图".takeIf { launchImagePicker == null },
                ).joinToString("、"),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        AppButton(
            onClick = onMentionClick,
            enabled = !isSending,
            modifier = Modifier
                .heightIn(min = AppDimens.TouchTarget)
                .testTag(COMMENT_MENTION_BUTTON_TAG),
            shape = MaterialTheme.shapes.medium,
            containerColor = commentInputBarColor,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            contentPadding = PaddingValues(horizontal = AppDimens.Space12, vertical = AppDimens.Space8),
        ) {
            Icon(Icons.Outlined.AlternateEmail, contentDescription = "提及用户")
            Spacer(modifier = Modifier.width(AppDimens.Space4))
            Text("提及", style = LocalAppSkinTokens.current.footnote2)
        }
    }
}

@Composable
internal fun CommentScreenVoicePreview(
    voice: RecordedVoiceComment,
    voicePreviewPlayer: VoiceCommentPlayer,
    isUploadingVoice: Boolean,
    uploadedVoiceId: String?,
    voiceUploadError: String?,
    isSending: Boolean,
    uploadRecordedVoice: () -> Unit,
    discardRecordedVoice: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconButton(
            onClick = { voicePreviewPlayer.toggle(voice.filePath) },
            modifier = Modifier
                .size(32.dp)
                .testTag(COMMENT_VOICE_PREVIEW_TAG),
        ) {
            Icon(
                imageVector = if (voicePreviewPlayer.playingUrl == voice.filePath) {
                    Icons.Filled.Pause
                } else {
                    Icons.Filled.PlayArrow
                },
                contentDescription = if (voicePreviewPlayer.playingUrl == voice.filePath) {
                    "暂停语音"
                } else {
                    "播放语音"
                },
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = "${(voice.durationMs / 1000).coerceAtLeast(0)}″",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.width(8.dp))
        when {
            isUploadingVoice -> {
                AppSpinner(size = 14.dp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "语音上传中…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            uploadedVoiceId != null -> Text(
                text = "语音已就绪",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )

            voiceUploadError != null -> AppTextButton(
                text = "重试上传",
                onClick = { uploadRecordedVoice() },
                enabled = !isSending,
                modifier = Modifier
                    .testTag(COMMENT_VOICE_UPLOAD_RETRY_TAG)
                    .semantics {
                        stateDescription = voiceUploadError.orEmpty()
                        liveRegion = LiveRegionMode.Polite
                    },
            )

            else -> Text(
                text = "等待上传",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        AppIconButton(
            onClick = { discardRecordedVoice() },
            enabled = !isUploadingVoice,
            modifier = Modifier
                .size(32.dp)
                .testTag(COMMENT_VOICE_DELETE_TAG),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "移除语音",
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
internal fun CommentScreenPendingAttachments(
    pendingImages: List<CommentImageAttachment>,
    pendingStickers: List<CommentSticker>,
    onRemoveImage: (CommentImageAttachment) -> Unit,
    onRemoveSticker: (CommentSticker) -> Unit,
) {
    val openPendingPreview = rememberImagePreviewOpener()

    if (pendingImages.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            pendingImages.forEach { image ->
                Box {
                    AsyncImage(
                        model = rememberMarkdownImageModel(image.url),
                        contentDescription = "待发送图片",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .combinedClickable(
                                onClick = { openPendingPreview(image.url) },
                                onLongClick = { openPendingPreview(image.url) },
                            ),
                        contentScale = ContentScale.Crop,
                    )
                    AppIconButton(
                        onClick = { onRemoveImage(image) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(20.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "移除图片",
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }

    if (pendingStickers.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            pendingStickers.forEach { sticker ->
                Box {
                    AsyncImage(
                        model = sticker.commentUrl,
                        contentDescription = sticker.title.ifBlank { "待发送贴纸" },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .combinedClickable(
                                onClick = { openPendingPreview(sticker.commentUrl) },
                                onLongClick = { openPendingPreview(sticker.commentUrl) },
                            ),
                        contentScale = ContentScale.Fit,
                    )
                    AppIconButton(
                        onClick = { onRemoveSticker(sticker) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(20.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "移除贴纸",
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CommentScreenMentionPicker(
    mentionQuery: TextFieldState,
    mentionLoading: Boolean,
    mentionResults: List<MentionCandidate>,
    onSelect: (MentionCandidate) -> Unit,
    onDismiss: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("提及用户") },
        text = {
            Column {
                AppTextField(
                    state = mentionQuery,
                    labelText = "搜索提及的用户",
                    placeholder = "搜索用户",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AppDimens.TouchTarget)
                        .testTag(COMMENT_MENTION_SEARCH_TAG),
                )
                Spacer(modifier = Modifier.height(8.dp))
                when {
                    mentionLoading -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AppSpinner(size = 20.dp)
                        }
                    }

                    mentionResults.isEmpty() -> {
                        Text(
                            text = "没有找到用户",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            mentionResults.take(8).forEach { user ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = AppDimens.TouchTarget)
                                        .clickable { onSelect(user) }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (!user.avatarUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = user.avatarUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(user.name)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            AppTextButton(
                text = "关闭",
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = AppDimens.TouchTarget),
            )
        },
    )
}

@Composable
internal fun ColumnScope.CommentScreenReplyBanner(
    replyToComment: CommentItem?,
    onCancelReply: () -> Unit,
) {
    AnimatedVisibility(
        visible = replyToComment != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(COMMENT_REPLY_BANNER_TAG),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Reply,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "回复 ${replyToComment?.item?.author?.name ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
                AppIconButton(
                    onClick = onCancelReply,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag(COMMENT_CANCEL_REPLY_TAG),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "取消回复",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}
