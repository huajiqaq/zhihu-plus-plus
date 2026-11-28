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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.i18n.ContentStrings
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppEmptyState
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.CommentScreenComponent
import com.github.zly2006.zhihu.ui.components.ShareDialog
import com.github.zly2006.zhihu.ui.components.VotersSheet
import com.github.zly2006.zhihu.ui.components.getShareText
import com.github.zly2006.zhihu.util.formatCompactCount

@Composable
internal fun PinScreenBody(
    innerPadding: PaddingValues,
    isLoading: Boolean,
    errorMessage: String?,
    hasContent: Boolean,
    errorContent: @Composable () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        when {
            isLoading && !hasContent -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(PIN_SCREEN_LOADING_TAG),
                    contentAlignment = Alignment.Center,
                ) {
                    AppSpinner()
                }
            }

            errorMessage != null && !hasContent -> {
                errorContent()
            }

            hasContent -> {
                content()
            }
        }
    }
}

@Composable
internal fun PinScreenErrorContent(
    errorMessage: String?,
    scrollState: ScrollState,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
        verticalArrangement = Arrangement.Center,
    ) {
        AppEmptyState(
            title = ContentStrings.pinLoadFailed,
            description = errorMessage,
            modifier = Modifier.testTag(PIN_SCREEN_ERROR_TAG),
            action = {
                AppButton(
                    text = ContentStrings.retry,
                    onClick = onRetry,
                    modifier = Modifier.testTag(PIN_SCREEN_RETRY_TAG),
                )
            },
        )
    }
}

@Composable
internal fun PinScreenOverlays(
    pin: Pin,
    showComments: Boolean,
    isZhPlusAuthorContent: Boolean,
    onCommentsDismiss: () -> Unit,
    showShareDialog: Boolean,
    onShareDismiss: () -> Unit,
    showVoters: Boolean,
    likeCount: Int,
    voters: List<DataHolder.Author>,
    votersLoading: Boolean,
    votersError: String?,
    canLoadMoreVoters: Boolean,
    onVotersDismiss: () -> Unit,
    onLoadMoreVoters: () -> Unit,
    onRetryVoters: () -> Unit,
    onNavigateVoter: (Person) -> Unit,
) {
    CommentScreenComponent(
        showComments = showComments,
        onDismiss = onCommentsDismiss,
        content = pin,
        isZhPlusAuthorContent = isZhPlusAuthorContent,
    )

    val shareText = getShareText(pin)
    if (shareText != null) {
        ShareDialog(
            content = pin,
            shareText = shareText,
            showDialog = showShareDialog,
            onDismissRequest = onShareDismiss,
        )
    }

    VotersSheet(
        show = showVoters,
        title = ContentStrings.pinLikes(likeCount, formatCompactCount(likeCount)),
        voters = voters,
        isLoading = votersLoading,
        errorMessage = votersError,
        canLoadMore = canLoadMoreVoters,
        onDismissRequest = onVotersDismiss,
        onLoadMore = onLoadMoreVoters,
        onRetry = onRetryVoters,
        onNavigate = onNavigateVoter,
    )
}
