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
            Text("Transformer 模型", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            val statusText = when (val state = modelState) {
                ModelState.Uninitialized -> "当前状态：未加载"
                ModelState.Loading -> "当前状态：正在加载"
                ModelState.Ready -> "当前状态：已就绪"
                is ModelState.Error -> "当前状态：加载失败"
                is ModelState.Downloading -> "当前状态：下载中 ${state.progress}%"
            }
            Text(statusText, style = MaterialTheme.typography.bodyMedium)
            (modelState as? ModelState.Error)?.let { error ->
                Text(error.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (isExtracting) {
                Text(
                    "提示：正在进行关键词任务，卸载暂不可用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12)) {
                AppButton(
                    onClick = onLoad,
                    enabled = modelState !is ModelState.Loading && modelState !is ModelState.Ready,
                ) {
                    Text(if (modelState is ModelState.Loading) "加载中..." else "加载模型")
                }
                AppTextButton(
                    onClick = onUnload,
                    enabled = modelState is ModelState.Ready && !isExtracting,
                ) {
                    Text("卸载模型")
                }
            }
        }
    }
}

@Composable
internal fun NLPKeywordManagementInfoSection() {
    NlpCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(modifier = Modifier.padding(AppDimens.Space16)) {
            Text("NLP智能屏蔽", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(AppDimens.Space8))
            Text(
                "基于自然语言处理技术，通过组合多个关键词成短语来表达与逻辑。例如：\"政治 敏感\" 将匹配同时包含这两个主题的内容。",
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
            Text("相似度阈值: ${String.format("%.2f", similarityThreshold)}", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(AppDimens.Space8))
            val slider = rememberSliderState(value = similarityThreshold, steps = 15, trackRange = 0.1f..0.9f)
            slider.value = similarityThreshold
            AppSlider(
                state = slider,
                onValueChange = onSimilarityThresholdChange,
            )
            Text(
                "阈值越高，匹配越严格；阈值越低，匹配越宽松",
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
            Text("提取关键词", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(AppDimens.Space8))
            AppTextField(
                state = inputText,
                labelText = "输入文本",
                lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3, maxHeightInLines = 5),
                placeholder = "粘贴Feed内容或输入任意文本",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(AppDimens.Space8))
            AppButton(
                onClick = onExtract,
                enabled = inputText.text.isNotBlank() && !isExtracting,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(if (isExtracting) "提取中..." else "提取关键词")
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
                Text("提取的关键词", style = MaterialTheme.typography.titleSmall)
                AppTextButton(
                    onClick = onAdd,
                    enabled = !submitting,
                ) {
                    Text("组合为短语并添加")
                }
            }
            Spacer(modifier = Modifier.height(AppDimens.Space8))
            Text(
                "提示：所有关键词将用空格连接成一个短语",
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
            title = { Text("NLP 匹配记录") },
            description = {
                Text(if (recordCount != null) "最近 $recordCount 条被语义屏蔽的内容与匹配的短语" else "被语义屏蔽的内容与匹配的短语")
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
                "包含 ${keyword.getNLPKeywords().size} 个关键词 · 添加于 ${
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(keyword.createdTime))
                }",
            )
        },
        endAction = {
            Row {
                AppIconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑短语“${keyword.keyword}”", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AppIconButton(
                    onClick = onDelete,
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "删除短语“${keyword.keyword}”", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Text("手动添加")
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
