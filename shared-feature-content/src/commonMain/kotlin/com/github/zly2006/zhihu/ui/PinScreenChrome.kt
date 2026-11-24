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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.reading.ReadingPlaybackStatus
import com.github.zly2006.zhihu.reading.ReadingPlayerController
import com.github.zly2006.zhihu.reading.ReadingPreferences
import com.github.zly2006.zhihu.reading.ReadingQueueItem
import com.github.zly2006.zhihu.reading.ReadingQueueSourceRegistry
import com.github.zly2006.zhihu.reading.ReadingStartRequest
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppOutlinedButton
import com.github.zly2006.zhihu.ui.components.AppPageTitleBar
import com.github.zly2006.zhihu.ui.components.AppPageTitleBarScrollState
import com.github.zly2006.zhihu.ui.components.AppSpinner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.ThumbUp as OutlinedThumbUp
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

@Composable
internal fun PinScreenTopBar(
    pin: Pin,
    pinContent: DataHolder.Pin?,
    scrollState: AppPageTitleBarScrollState,
    onBack: () -> Unit,
    readingItem: ReadingQueueItem?,
    isCurrentReadingItem: Boolean,
    readingEnabled: Boolean,
    readingStatus: ReadingPlaybackStatus,
    isActivelyPlaying: Boolean,
    readingPlayer: ReadingPlayerController,
    coroutineScope: CoroutineScope,
    readingPreferences: ReadingPreferences,
    readingPlaybackSpeed: Float,
    onShareClick: () -> Unit,
) {
    AppPageTitleBar(
        title = pinContent?.let { detail ->
            detail.content.firstNotNullOfOrNull { (it as? DataHolder.Pin.ContentText)?.title?.takeIf(String::isNotBlank) }
                ?: "${detail.author.name}的想法"
        } ?: "想法",
        scrollState = scrollState,
        onBack = onBack,
        backTag = PIN_SCREEN_BACK_BUTTON_TAG,
        actions = {
            PinScreenReadingButton(
                onClick = {
                    val item = readingItem ?: return@PinScreenReadingButton
                    if (isCurrentReadingItem) {
                        readingPlayer.togglePlayPause()
                    } else {
                        coroutineScope.launch {
                            readingPlayer.start(
                                ReadingStartRequest(
                                    queue = ReadingQueueSourceRegistry.queueStartingAt(
                                        current = item,
                                        sourceId = pin.readingQueueSourceId,
                                        limit = readingPreferences.queueLimit,
                                    ),
                                    preferences = readingPreferences,
                                    sourceId = pin.readingQueueSourceId,
                                    playbackSpeed = readingPlaybackSpeed,
                                ),
                            )
                        }
                    }
                },
                enabled = readingEnabled,
                isCurrentReadingItem = isCurrentReadingItem,
                readingStatus = readingStatus,
                isActivelyPlaying = isActivelyPlaying,
            )
            AppIconButton(
                onClick = onShareClick,
                modifier = Modifier.testTag(PIN_SCREEN_SHARE_BUTTON_TAG),
            ) {
                Icon(Icons.Default.Share, contentDescription = "分享")
            }
        },
    )
}

@Composable
private fun PinScreenReadingButton(
    onClick: () -> Unit,
    enabled: Boolean,
    isCurrentReadingItem: Boolean,
    readingStatus: ReadingPlaybackStatus,
    isActivelyPlaying: Boolean,
) {
    AppIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.testTag(PIN_SCREEN_READING_BUTTON_TAG),
    ) {
        when {
            isCurrentReadingItem &&
                readingStatus in setOf(
                    ReadingPlaybackStatus.Initializing,
                    ReadingPlaybackStatus.Loading,
                ) -> AppSpinner(modifier = Modifier, size = 22.dp)
            isCurrentReadingItem && isActivelyPlaying -> Icon(
                Icons.Default.Pause,
                contentDescription = "暂停朗读",
            )
            else -> Icon(
                Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = if (isCurrentReadingItem) "继续朗读" else "开始连续朗读",
            )
        }
    }
}

@Composable
internal fun PinActionBar(
    isLiked: Boolean,
    likeCount: Int,
    commentCount: Int,
    onLikeClick: () -> Unit,
    likeChanging: Boolean,
    onCommentClick: () -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.PageHorizontalPadding)
            .padding(
                top = 4.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Space8),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        AppOutlinedButton(
            onClick = onLikeClick,
            enabled = !likeChanging,
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .testTag(PIN_SCREEN_LIKE_BUTTON_TAG)
                .semantics {
                    stateDescription = if (isLiked) "已点赞" else "未点赞"
                    onClick(label = if (isLiked) "取消点赞" else "点赞", action = null)
                },
            containerColor = if (isLiked) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (isLiked) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ) {
            Icon(
                if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.OutlinedThumbUp,
                contentDescription = if (isLiked) "取消点赞" else "点赞",
                modifier = Modifier.size(AppDimens.Space20),
            )
            Spacer(Modifier.width(AppDimens.Space6))
            Text(
                if (likeChanging) "提交中…" else "点赞 · $likeCount",
                style = MaterialTheme.typography.labelLarge,
            )
        }
        AppOutlinedButton(
            onClick = onCommentClick,
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .testTag(PIN_SCREEN_COMMENT_BUTTON_TAG),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Comment,
                contentDescription = "评论",
                modifier = Modifier.size(AppDimens.Space20),
            )
            Spacer(Modifier.width(AppDimens.Space6))
            Text("评论 · $commentCount", style = MaterialTheme.typography.labelLarge)
        }
    }
}
