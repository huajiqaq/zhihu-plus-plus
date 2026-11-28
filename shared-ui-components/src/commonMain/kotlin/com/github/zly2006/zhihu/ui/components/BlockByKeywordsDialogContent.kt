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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.i18n.WidgetsStrings
import com.github.zly2006.zhihu.nlp.KeywordWithWeight
import com.github.zly2006.zhihu.viewmodel.filter.KeywordScope
import kotlin.math.roundToInt

/**
 * 按关键词屏蔽的信息流辅助弹窗。
 *
 * 弹窗展示从标题和摘要中提取的关键词，默认选择前三个，允许用户组合成一个 NLP 屏蔽短语。它用于把 Feed 卡片上的一次性“屏蔽”
 * 操作转化为可维护的规则，因此需要同时展示加载态、添加态和关键词权重详情。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlockByKeywordsDialogContent(
    showDialog: Boolean,
    feedTitle: String,
    feedExcerpt: String?,
    extractedKeywords: List<String>,
    keywordInfoList: List<KeywordWithWeight>,
    isLoading: Boolean,
    isAdding: Boolean,
    onDismiss: () -> Unit,
    onConfirmPhrase: (String) -> Unit,
    scope: KeywordScope = KeywordScope.ALL,
    onScopeChange: (KeywordScope) -> Unit = {},
) {
    var selectedKeywords by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDetailDialog by remember { mutableStateOf(false) }

    LaunchedEffect(showDialog, extractedKeywords) {
        if (showDialog) {
            selectedKeywords = extractedKeywords.take(3).toSet()
        } else {
            selectedKeywords = emptySet()
            showDetailDialog = false
        }
    }

    if (showDialog) {
        AppAlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(WidgetsStrings.blockByKeyword) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(WidgetsStrings.keywordScope, style = MaterialTheme.typography.titleSmall)
                    AppSegmentedControl(
                        options = listOf(WidgetsStrings.scopeAll, WidgetsStrings.scopeQuestions, WidgetsStrings.scopeAnswers),
                        selectedIndex = scope.ordinal,
                        onSelect = { onScopeChange(KeywordScope.entries[it]) },
                        enabled = !isAdding,
                        testTags = KeywordScope.entries.map { "blockByKeywords:scope:${it.name}" },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isLoading) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AppSpinner()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(WidgetsStrings.keywordExtracting)
                        }
                    } else if (extractedKeywords.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                WidgetsStrings.noKeywordsFound,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else {
                        Text(
                            WidgetsStrings.chooseKeywords,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            WidgetsStrings.keywordPhraseHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            extractedKeywords.forEach { keyword ->
                                val isSelected = keyword in selectedKeywords
                                AppChip(
                                    label = keyword,
                                    selected = isSelected,
                                    onClick = {
                                        selectedKeywords = if (isSelected) {
                                            selectedKeywords - keyword
                                        } else {
                                            selectedKeywords + keyword
                                        }
                                    },
                                )
                            }
                        }

                        if (selectedKeywords.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            AppCard(
                                modifier = Modifier.fillMaxWidth(),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                ) {
                                    Text(
                                        WidgetsStrings.phrasePreview,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        selectedKeywords.sorted().joinToString(" "),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            WidgetsStrings.similarTopicsHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AppTextButton(
                            onClick = { showDetailDialog = true },
                            enabled = keywordInfoList.isNotEmpty() && !isLoading,
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            AppIcon(Icons.Default.Info, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                            Text(WidgetsStrings.showKeywordWeights)
                        }
                    }
                }
            },
            confirmButton = {
                AppButton(
                    onClick = {
                        if (selectedKeywords.isNotEmpty()) {
                            onConfirmPhrase(selectedKeywords.sorted().joinToString(" "))
                        }
                    },
                    enabled = selectedKeywords.isNotEmpty() && !isLoading && !isAdding,
                ) {
                    if (isAdding) {
                        AppSpinner(size = 16.dp)
                    } else {
                        Text(WidgetsStrings.blockSelectedCount(selectedKeywords.size))
                    }
                }
            },
            dismissButton = {
                AppTextButton(
                    onClick = onDismiss,
                    enabled = !isAdding,
                ) {
                    Text(WidgetsStrings.cancel)
                }
            },
        )
    }

    if (showDetailDialog) {
        KeywordDetailDialog(
            keywordInfoList = keywordInfoList,
            feedTitle = feedTitle,
            feedExcerpt = feedExcerpt,
            onDismiss = { showDetailDialog = false },
        )
    }
}

@Composable
fun KeywordDetailDialog(
    keywordInfoList: List<KeywordWithWeight>,
    feedTitle: String,
    feedExcerpt: String?,
    onDismiss: () -> Unit,
) {
    val maxWeight = keywordInfoList.maxOfOrNull { it.weight } ?: 1.0

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(WidgetsStrings.keywordDetails) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                    ) {
                        Text(
                            WidgetsStrings.contentPreview,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            feedTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        if (!feedExcerpt.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                feedExcerpt.take(100) + if (feedExcerpt.length > 100) "..." else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    WidgetsStrings.keywordRanking,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))

                keywordInfoList.forEachIndexed { index, keywordInfo ->
                    KeywordInfoItem(
                        index = index + 1,
                        keywordInfo = keywordInfo,
                        maxWeight = maxWeight,
                    )
                    if (index < keywordInfoList.size - 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    WidgetsStrings.keywordWeightExplanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            AppTextButton(onClick = onDismiss) {
                Text(WidgetsStrings.close)
            }
        },
    )
}

@Composable
fun KeywordInfoItem(
    index: Int,
    keywordInfo: KeywordWithWeight,
    maxWeight: Double,
) {
    val normalizedWeight = if (maxWeight > 0) (keywordInfo.weight / maxWeight).toFloat() else 0f

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "#$index",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(end = 8.dp),
                    )
                    Text(
                        keywordInfo.keyword,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Text(
                    formatKeywordWeight(keywordInfo.weight),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            AppLinearProgress(
                progress = normalizedWeight,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

private fun formatKeywordWeight(weight: Double): String {
    val scaled = (weight * 10_000).roundToInt()
    val sign = if (scaled < 0) "-" else ""
    val absolute = kotlin.math.abs(scaled)
    val integer = absolute / 10_000
    val fraction = (absolute % 10_000).toString().padStart(4, '0')
    return "$sign$integer.$fraction"
}
