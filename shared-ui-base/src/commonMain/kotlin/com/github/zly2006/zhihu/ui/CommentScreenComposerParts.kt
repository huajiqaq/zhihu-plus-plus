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
import com.github.zly2006.zhihu.i18n.CommentsStrings
import com.github.zly2006.zhihu.markdown.rememberMarkdownImageModel
import com.github.zly2006.zhihu.platform.rememberImagePreviewOpener
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppAlertDialog
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppIcon
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
            if (voiceRecorder.isBusy) {
                AppSpinner(size = AppDimens.IconSizeCompact)
                AppIconButton(onClick = voiceRecorder::cancel, modifier = Modifier.size(AppDimens.TouchTarget)) {
                    AppIcon(Icons.Default.Close, contentDescription = CommentsStrings.removeVoice)
                }
            } else if (voiceRecorder.isRecording) {
                AppIconButton(
                    onClick = { stopVoiceRecording() },
                    enabled = !voiceRecorder.isBusy,
                    modifier = Modifier
                        .size(AppDimens.TouchTarget)
                        .testTag(COMMENT_VOICE_RECORD_BUTTON_TAG),
                ) {
                    AppIcon(
                        Icons.Filled.Stop,
                        contentDescription = CommentsStrings.stopRecording,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                AppIconButton(onClick = voiceRecorder::cancel, modifier = Modifier.size(AppDimens.TouchTarget)) {
                    AppIcon(Icons.Default.Close, contentDescription = CommentsStrings.removeVoice)
                }
                Text(
                    text = CommentsStrings.recordingSeconds((recordingElapsedMs / 1000).coerceAtLeast(0).toInt()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                AppButton(
                    onClick = { requestVoicePermission() },
                    enabled = !isSending && !isUploadingImage && !isUploadingVoice && !voiceRecorder.isBusy,
                    modifier = Modifier
                        .heightIn(min = AppDimens.TouchTarget)
                        .testTag(COMMENT_VOICE_BUTTON_TAG),
                    shape = MaterialTheme.shapes.medium,
                    containerColor = commentInputBarColor,
                    contentColor = if (uploadedVoiceId != null) LocalAppSkinTokens.current.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    contentPadding = PaddingValues(horizontal = AppDimens.Space12, vertical = AppDimens.Space8),
                ) {
                    AppIcon(Icons.Outlined.Mic, contentDescription = CommentsStrings.recordVoiceComment)
                    Spacer(modifier = Modifier.width(AppDimens.Space4))
                    Text(CommentsStrings.voice, style = LocalAppSkinTokens.current.footnote2)
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
                    AppIcon(Icons.Outlined.Image, contentDescription = CommentsStrings.addImage)
                }
                Spacer(modifier = Modifier.width(AppDimens.Space4))
                Text(CommentsStrings.image, style = LocalAppSkinTokens.current.footnote2)
            }
        }
        if (!voiceRecorder.isSupported || launchImagePicker == null) {
            Text(
                when {
                    !voiceRecorder.isSupported && launchImagePicker == null -> CommentsStrings.recordingAndImagesUnavailable
                    !voiceRecorder.isSupported -> CommentsStrings.recordingUnavailable
                    else -> CommentsStrings.imagePickerUnavailable
                },
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
            AppIcon(Icons.Outlined.AlternateEmail, contentDescription = CommentsStrings.mentionUser)
            Spacer(modifier = Modifier.width(AppDimens.Space4))
            Text(CommentsStrings.mention, style = LocalAppSkinTokens.current.footnote2)
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
            AppIcon(
                imageVector = if (voicePreviewPlayer.playingUrl == voice.filePath) {
                    Icons.Filled.Pause
                } else {
                    Icons.Filled.PlayArrow
                },
                contentDescription = if (voicePreviewPlayer.playingUrl == voice.filePath) {
                    CommentsStrings.pauseVoice
                } else {
                    CommentsStrings.playVoice
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
                    text = CommentsStrings.voiceUploading,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            uploadedVoiceId != null -> Text(
                text = CommentsStrings.voiceReady,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )

            voiceUploadError != null -> AppTextButton(
                text = CommentsStrings.retryUpload,
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
                text = CommentsStrings.waitingToUpload,
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
            AppIcon(
                Icons.Default.Close,
                contentDescription = CommentsStrings.removeVoice,
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
                        contentDescription = CommentsStrings.pendingImage,
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
                        AppIcon(
                            Icons.Default.Close,
                            contentDescription = CommentsStrings.removeImage,
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
                        contentDescription = sticker.title.ifBlank { CommentsStrings.pendingSticker },
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
                        AppIcon(
                            Icons.Default.Close,
                            contentDescription = CommentsStrings.removeSticker,
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
        title = { Text(CommentsStrings.mentionUser) },
        text = {
            Column {
                AppTextField(
                    state = mentionQuery,
                    labelText = CommentsStrings.searchMentionUsers,
                    placeholder = CommentsStrings.searchUsers,
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
                            text = CommentsStrings.noUsersFound,
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
                text = CommentsStrings.close,
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
                AppIcon(
                    Icons.AutoMirrored.Filled.Reply,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = CommentsStrings.replyToAuthor(
                        replyToComment
                            ?.item
                            ?.author
                            ?.name
                            .orEmpty(),
                    ),
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
                    AppIcon(
                        Icons.Default.Close,
                        contentDescription = CommentsStrings.cancelReply,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}
