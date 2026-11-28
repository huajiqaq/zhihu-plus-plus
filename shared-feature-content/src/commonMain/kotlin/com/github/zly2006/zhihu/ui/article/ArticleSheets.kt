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

package com.github.zly2006.zhihu.ui.article

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.i18n.ContentStrings
import com.github.zly2006.zhihu.ui.components.AppBottomSheet
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArticleSummarySheet(
    showDialog: Boolean,
    summaryText: String,
    loading: Boolean,
    errorMessage: String?,
    onDismissRequest: () -> Unit,
    onRetryRequest: () -> Unit,
) {
    if (!showDialog) return
    val scrollState = rememberScrollState()
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    AppBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(scrollState),
        ) {
            Text(ContentStrings.summarizeContent, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(12.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                if (loading && summaryText.isBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppSpinner(size = 20.dp)
                        Text(ContentStrings.generatingSummary)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (summaryText.isNotBlank()) {
                    SelectionContainer { Text(summaryText) }
                }
                if (!errorMessage.isNullOrBlank()) {
                    if (summaryText.isNotBlank()) Spacer(modifier = Modifier.height(12.dp))
                    Text(errorMessage, color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                AppTextButton(
                    text = ContentStrings.close,
                    onClick = onDismissRequest,
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (!loading) {
                    AppTextButton(
                        text = ContentStrings.regenerateSummary,
                        onClick = onRetryRequest,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AigcFlagSheet(
    showDialog: Boolean,
    viewModel: ArticleViewModel,
    onDismissRequest: () -> Unit,
    onSubmitRequest: () -> Unit,
) {
    if (!showDialog) return
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    AppBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val canSubmitAigcFlag = viewModel.aigcVoteAvailable &&
                !viewModel.aigcVoteLoading &&
                !viewModel.aigcFlagged &&
                viewModel.aigcVoterName.isNotBlank() &&
                (
                    viewModel.aigcCreditBypassAvailable ||
                        (viewModel.aigcVoteCredit > 0 && viewModel.isAigcFlagEvidenceReady())
                )
            Text(
                text = ContentStrings.flagAiContent,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = ContentStrings.aiFlaggingHelp(viewModel.aigcVoteCap),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (!viewModel.aigcVoteAvailable) {
                    ContentStrings.aiFlaggingDisabled
                } else if (viewModel.aigcVoterName.isBlank()) {
                    ContentStrings.aiVoteLoginRequired
                } else {
                    ContentStrings.voterName(viewModel.aigcVoterName)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (viewModel.aigcCreditBypassAvailable) {
                    ContentStrings.creditsExempt(viewModel.aigcVoteCredit, viewModel.aigcVoteCap)
                } else {
                    ContentStrings.creditsProgress(viewModel.aigcVoteCredit, viewModel.aigcVoteCap, viewModel.aigcVoteProgress)
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (viewModel.aigcEffectiveFlagCount > 0) {
                    ContentStrings.effectiveFlagCount(viewModel.aigcEffectiveFlagCount)
                } else {
                    ContentStrings.noEffectiveFlags
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (viewModel.aigcNamedVoters.isNotEmpty()) {
                Text(
                    text = ContentStrings.namedVoters(
                        viewModel.aigcNamedVoters.joinToString(ContentStrings.listSeparator) { voter ->
                            if (voter.creditBypassed) ContentStrings.voterCreditExempt(voter.voterName) else voter.voterName
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            viewModel.aigcVoteError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppTextButton(
                    text = ContentStrings.close,
                    onClick = onDismissRequest,
                )
                Spacer(modifier = Modifier.width(8.dp))
                AppButton(
                    text = when {
                        !viewModel.aigcVoteAvailable -> ContentStrings.notEnabled
                        viewModel.aigcFlagged -> ContentStrings.flagged
                        viewModel.aigcVoteLoading -> ContentStrings.submitting
                        viewModel.aigcVoterName.isBlank() -> ContentStrings.loginRequired
                        viewModel.aigcCreditBypassAvailable -> ContentStrings.flagWithoutCredit
                        viewModel.aigcVoteCredit <= 0 -> ContentStrings.insufficientCredits
                        !viewModel.isAigcFlagEvidenceReady() -> ContentStrings.keepReading
                        else -> ContentStrings.flagWithCredit
                    },
                    onClick = onSubmitRequest,
                    enabled = canSubmitAigcFlag,
                )
            }
        }
    }
}
