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
import com.github.zly2006.zhihu.nlp.KeywordWithWeight
import com.github.zly2006.zhihu.viewmodel.filter.KeywordScope
import kotlin.math.roundToInt
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

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
            title = { Text("按关键词屏蔽") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("作用范围", style = MaterialTheme.typography.titleSmall)
                    AppSegmentedControl(
                        options = listOf("全部内容", "问题", "回答"),
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
                            Text("正在提取关键词...")
                        }
                    } else if (extractedKeywords.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "未能提取到关键词",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else {
                        Text(
                            "从内容中提取到以下关键词，选择要屏蔽的关键词：",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "提示：选中的关键词将用空格串联成一个短语进行NLP语义匹配",
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
                                        "短语预览",
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
                            "提示：基于NLP语义相似度，即使用词不同，主题相似的内容也会被过滤",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AppTextButton(
                            onClick = { showDetailDialog = true },
                            enabled = keywordInfoList.isNotEmpty() && !isLoading,
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                            Text("查看关键词权重")
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
                        Text("屏蔽 (${selectedKeywords.size})")
                    }
                }
            },
            dismissButton = {
                AppTextButton(
                    onClick = onDismiss,
                    enabled = !isAdding,
                ) {
                    Text("取消")
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
        title = { Text("关键词详细信息") },
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
                            "内容预览",
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
                    "提取的关键词（按重要性排序）",
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
                    "说明：权重值由TextRank算法计算得出，数值越高表示该关键词在内容中越重要",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            AppTextButton(onClick = onDismiss) {
                Text("关闭")
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
