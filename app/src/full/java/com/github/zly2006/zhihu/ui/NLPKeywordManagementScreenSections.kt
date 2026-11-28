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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.github.zly2006.zhihu.i18n.AccountStrings
import com.github.zly2006.zhihu.i18n.AndroidNlpStrings
import com.github.zly2006.zhihu.i18n.SettingsStrings
import com.github.zly2006.zhihu.i18n.WidgetsStrings
import com.github.zly2006.zhihu.nlp.ModelState
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppCard
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppSlider
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.AppTextField
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingsContent
import com.github.zly2006.zhihu.viewmodel.filter.BlockedKeyword
import com.github.zly2006.zhihu.viewmodel.filter.KeywordScope

@Composable
internal fun NLPKeywordManagementModelSection(
    modelState: ModelState,
    isExtracting: Boolean,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
) {
    NlpCard {
        Column(
            modifier = Modifier.padding(AppDimens.Space16),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Space8),
        ) {
            Text(AndroidNlpStrings.transformerModel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            val statusText = when (val state = modelState) {
                ModelState.Uninitialized -> AndroidNlpStrings.modelStatusUnloaded
                ModelState.Loading -> AndroidNlpStrings.modelStatusLoading
                ModelState.Ready -> AndroidNlpStrings.modelStatusReady
                is ModelState.Error -> AndroidNlpStrings.modelStatusFailed
                is ModelState.Downloading -> AndroidNlpStrings.modelStatusDownloading(state.progress)
            }
            Text(statusText, style = MaterialTheme.typography.bodyMedium)
            (modelState as? ModelState.Error)?.let { error ->
                Text(error.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (isExtracting) {
                Text(
                    AndroidNlpStrings.processingUnloadUnavailable,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12)) {
                AppButton(
                    onClick = onLoad,
                    enabled = modelState !is ModelState.Loading && modelState !is ModelState.Ready,
                ) {
                    Text(if (modelState is ModelState.Loading) WidgetsStrings.loading else AndroidNlpStrings.loadModel)
                }
                AppTextButton(
                    onClick = onUnload,
                    enabled = modelState is ModelState.Ready && !isExtracting,
                ) {
                    Text(SettingsStrings.nlpUnload)
                }
            }
        }
    }
}

@Composable
internal fun NLPKeywordManagementInfoSection() {
    NlpCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(modifier = Modifier.padding(AppDimens.Space16)) {
            Text(AccountStrings.nlpBlocking, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(AppDimens.Space8))
            Text(
                AndroidNlpStrings.aboutBlocking,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun NLPKeywordManagementThresholdSection(
    similarityThreshold: Float,
    onSimilarityThresholdChange: (Float) -> Unit,
) {
    NlpCard {
        Column(modifier = Modifier.padding(AppDimens.Space16)) {
            Text(SettingsStrings.nlpThreshold(String.format("%.2f", similarityThreshold)), style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(AppDimens.Space8))
            val slider = rememberSliderState(value = similarityThreshold, steps = 15, trackRange = 0.1f..0.9f)
            slider.value = similarityThreshold
            AppSlider(
                state = slider,
                onValueChange = onSimilarityThresholdChange,
            )
            Text(
                AndroidNlpStrings.thresholdHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun NLPKeywordManagementExtractionSection(
    inputText: TextFieldState,
    isExtracting: Boolean,
    onExtract: () -> Unit,
) {
    NlpCard {
        Column(modifier = Modifier.padding(AppDimens.Space16)) {
            Text(AndroidNlpStrings.extractKeywords, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(AppDimens.Space8))
            AppTextField(
                state = inputText,
                labelText = AndroidNlpStrings.inputText,
                lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3, maxHeightInLines = 5),
                placeholder = AndroidNlpStrings.inputPlaceholder,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(AppDimens.Space8))
            AppButton(
                onClick = onExtract,
                enabled = inputText.text.isNotBlank() && !isExtracting,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(if (isExtracting) AndroidNlpStrings.extracting else AndroidNlpStrings.extractKeywords)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NLPKeywordManagementExtractedKeywordsSection(
    extractedKeywords: List<String>,
    submitting: Boolean,
    onAdd: () -> Unit,
) {
    NlpCard {
        Column(modifier = Modifier.padding(AppDimens.Space16)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(AndroidNlpStrings.extractedKeywords, style = MaterialTheme.typography.titleSmall)
                AppTextButton(
                    onClick = onAdd,
                    enabled = !submitting,
                ) {
                    Text(AndroidNlpStrings.combineAndAdd)
                }
            }
            Spacer(modifier = Modifier.height(AppDimens.Space8))
            Text(
                AndroidNlpStrings.combineHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(AppDimens.Space8))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Space8),
            ) {
                extractedKeywords.forEach { keyword ->
                    Text(
                        text = keyword,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small)
                            .padding(horizontal = AppDimens.Space12, vertical = AppDimens.Space8),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
internal fun NLPKeywordManagementRecordsEntry(
    recordCount: Int?,
    onClick: () -> Unit,
) {
    SettingsContent {
        SettingItem(
            modifier = Modifier.padding(horizontal = AppDimens.Space16).padding(bottom = AppDimens.Space16),
            title = { Text(SettingsStrings.nlpRecords) },
            description = {
                Text(if (recordCount != null) AndroidNlpStrings.recordsCount(recordCount) else AndroidNlpStrings.recordsHint)
            },
            onClick = onClick,
        )
    }
}

@Composable
internal fun NLPKeywordManagementKeywordItem(
    keyword: BlockedKeyword,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    SettingItem(
        title = { Text(keyword.keyword, fontWeight = FontWeight.Medium) },
        description = {
            Text(
                AndroidNlpStrings.phraseDetails(
                    keyword.getNLPKeywords().size,
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(keyword.createdTime)),
                ),
            )
        },
        endAction = {
            Row {
                AppIconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = AndroidNlpStrings.editPhraseAccessibility(keyword.keyword), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AppIconButton(
                    onClick = onDelete,
                ) {
                    Icon(Icons.Default.Delete, contentDescription = AndroidNlpStrings.deletePhraseAccessibility(keyword.keyword), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    )
}

@Composable
internal fun NLPKeywordManagementScopeSection(
    keywordScope: KeywordScope,
    submitting: Boolean,
    onKeywordScopeChange: (KeywordScope) -> Unit,
) {
    SettingsContent {
        KeywordScopeSettingItem(
            scope = keywordScope,
            onSelect = onKeywordScopeChange,
            enabled = !submitting,
            testTagPrefix = "nlpKeywords:scope",
        )
    }
}

@Composable
internal fun NLPKeywordManagementAddAction(
    onClick: () -> Unit,
) {
    AppTextButton(onClick = onClick) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(modifier = Modifier.width(AppDimens.Space4))
        Text(AndroidNlpStrings.addManually)
    }
}

@Composable
private fun NlpCard(
    containerColor: Color? = null,
    content: @Composable () -> Unit,
) {
    SettingsContent {
        AppCard(
            modifier = Modifier.padding(start = AppDimens.Space16, end = AppDimens.Space16, bottom = AppDimens.Space16),
            containerColor = containerColor,
        ) {
            content()
        }
    }
}
