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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.github.zly2006.zhihu.i18n.WidgetsStrings
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.viewmodel.LoadState

@Composable
fun AppMetadataLoadState(
    isLoading: Boolean,
    errorMessage: String?,
    hasContent: Boolean,
    onRetry: () -> Unit,
    loadingTag: String,
    errorTag: String,
    retryTag: String,
) {
    val tokens = LocalAppSkinTokens.current
    when {
        errorMessage != null -> Column(
            modifier = Modifier.fillMaxWidth().padding(AppDimens.Space12),
        ) {
            androidx.compose.material3.Text(
                text = if (hasContent) WidgetsStrings.updateFailedWithContent(errorMessage) else WidgetsStrings.loadFailedDetail(errorMessage),
                color = tokens.error,
                style = tokens.body2,
                modifier = Modifier.testTag(errorTag),
            )
            AppTextButton(WidgetsStrings.retry, onRetry, modifier = Modifier.testTag(retryTag))
        }
        isLoading -> Row(
            modifier = Modifier.fillMaxWidth().padding(AppDimens.Space12).testTag(loadingTag),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppSpinner(size = AppDimens.InlineSpinnerSize)
            androidx.compose.material3.Text(
                text = if (hasContent) WidgetsStrings.updating else WidgetsStrings.loading,
                color = tokens.onSurfaceVariantSummary,
                style = tokens.body2,
            )
        }
    }
}

const val APP_LOAD_STATE_LOADING_TAG = "app_load_state_loading"
const val APP_LOAD_STATE_FAILED_TAG = "app_load_state_failed"
const val APP_LOAD_STATE_EMPTY_TAG = "app_load_state_empty"

@Composable
fun <T> AppLoadStateContent(
    state: LoadState<T>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    failedTitle: String = WidgetsStrings.loadFailed,
    isEmpty: (T) -> Boolean = { false },
    empty: @Composable () -> Unit = {},
    content: @Composable (T) -> Unit,
) {
    when (state) {
        LoadState.Loading -> Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = AppDimens.Space32)
                .testTag(APP_LOAD_STATE_LOADING_TAG),
            contentAlignment = Alignment.Center,
        ) {
            AppSpinner()
        }

        is LoadState.Failed -> AppEmptyState(
            title = failedTitle,
            description = state.message,
            modifier = modifier.testTag(APP_LOAD_STATE_FAILED_TAG),
            action = {
                AppTextButton(
                    text = WidgetsStrings.retry,
                    onClick = onRetry,
                    contentColor = if (settingSkinIsMiuix()) LocalAppSkinTokens.current.onSurface else Color.Unspecified,
                )
            },
        )

        is LoadState.Ready -> if (isEmpty(state.value)) {
            Box(modifier = modifier.testTag(APP_LOAD_STATE_EMPTY_TAG)) { empty() }
        } else {
            content(state.value)
        }
    }
}
