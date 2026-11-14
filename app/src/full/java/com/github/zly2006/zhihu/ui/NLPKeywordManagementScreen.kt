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

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.github.zly2006.zhihu.filter.asListLoadState
import com.github.zly2006.zhihu.nlp.ModelState
import com.github.zly2006.zhihu.nlp.NLPService
import com.github.zly2006.zhihu.nlp.SentenceEmbeddingManager
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.ui.components.AppAlertDialog
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppCard
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppSlider
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.AppTextField
import com.github.zly2006.zhihu.ui.components.MyModalBottomSheet
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingsContent
import com.github.zly2006.zhihu.viewmodel.LoadState
import com.github.zly2006.zhihu.viewmodel.filter.BlockedContentRecord
import com.github.zly2006.zhihu.viewmodel.filter.BlockedKeyword
import com.github.zly2006.zhihu.viewmodel.filter.KeywordType
import com.github.zly2006.zhihu.viewmodel.filter.MatchedKeywordInfo
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import com.github.zly2006.zhihu.viewmodel.readyValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private const val NLP_KEYWORD_MANAGEMENT_TAG = "NLPKeywordManagement"

/** NLP 匹配记录按短语记录，与首页屏蔽记录不是同一数据源，不能合并或相互清除。 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NLPKeywordManagementScreen(
    innerPadding: PaddingValues,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val database = remember(context) { getContentFilterDatabase(context) }
    val settings = rememberSettingsStore()
    val modelState by SentenceEmbeddingManager.state.collectAsState()

    LaunchedEffect(context) {
        SentenceEmbeddingManager.setDefaultContext(context.applicationContext)
    }

    var reload by remember { mutableIntStateOf(0) }
    val phrases by remember(reload) {
        database.blockedKeywordDao().observeKeywordsByType(KeywordType.NLP_SEMANTIC.name).asListLoadState()
    }.collectAsState(LoadState.Loading)
    val records by remember(reload) { database.blockedContentRecordDao().observeRecentRecords().asListLoadState() }
        .collectAsState(LoadState.Loading)

    var isExtracting by remember { mutableStateOf(false) }
    var extractedKeywords by remember { mutableStateOf<List<String>>(emptyList()) }
    var inputText by rememberSaveable { mutableStateOf("") }
    var similarityThreshold by rememberSetting(settings, "nlpSimilarityThreshold", 0.8f)
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var editingKeywordId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showRecordsSheet by rememberSaveable { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    fun launchAction(failurePrefix: String, successMessage: String? = null, action: suspend () -> Unit) {
        coroutineScope.launch {
            try {
                withContext(Dispatchers.IO) { action() }
                currentCoroutineContext().ensureActive()
                successMessage?.let(::toast)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(NLP_KEYWORD_MANAGEMENT_TAG, failurePrefix, e)
                toast("$failurePrefix：${e.message}")
            }
        }
    }

    // 确认按钮的禁用要等下一帧重组才生效，同一帧里的第二次点击只能靠这里拦住。
    fun submitPhrase(successMessage: String, onSuccess: () -> Unit, write: suspend () -> Unit) {
        if (submitting) return
        submitting = true
        coroutineScope.launch {
            try {
                withContext(Dispatchers.IO) { write() }
                currentCoroutineContext().ensureActive()
                toast(successMessage)
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(NLP_KEYWORD_MANAGEMENT_TAG, "Failed to save NLP phrase", e)
                toast("保存失败：${e.message}")
            } finally {
                submitting = false
            }
        }
    }

    fun extractKeywords() {
        if (isExtracting) return
        if (inputText.isBlank()) {
            toast("请输入文本")
            return
        }
        isExtracting = true
        coroutineScope.launch {
            try {
                val sourceText = inputText
                val extracted = NLPService.extractKeywords(sourceText, 10)
                currentCoroutineContext().ensureActive()
                if (inputText != sourceText) return@launch
                extractedKeywords = extracted
                if (extractedKeywords.isEmpty()) toast("未能提取到关键词")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(NLP_KEYWORD_MANAGEMENT_TAG, "Extract NLP keywords failed", e)
                toast("提取失败：${e.message}")
            } finally {
                isExtracting = false
            }
        }
    }

    BlocklistItemList(
        state = phrases,
        onRetry = { reload++ },
        description = "已屏蔽短语：语义与短语相近的内容不会出现在首页。",
        emptyTitle = "暂无 NLP 屏蔽短语",
        emptyDescription = "在上方提取关键词，或点“手动添加”。",
        clearTitle = { count -> "清空 $count 个 NLP 屏蔽短语？" },
        clearSummary = "首页将不再按这些短语做语义屏蔽。清空后无法撤销，如需保留请先导出规则。",
        onClearAll = {
            launchAction("清空失败") {
                database.blockedKeywordDao().deleteKeywordsByType(KeywordType.NLP_SEMANTIC.name)
            }
        },
        itemKey = { it.id },
        contentPadding = PaddingValues(
            top = AppDimens.Space16 + innerPadding.calculateTopPadding(),
            bottom = AppDimens.Space16 + innerPadding.calculateBottomPadding(),
        ),
        headerActions = {
            AppTextButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(AppDimens.Space4))
                Text("手动添加")
            }
        },
        leadingItems = {
            item {
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
                                onClick = {
                                    coroutineScope.launch {
                                        try {
                                            SentenceEmbeddingManager.ensureModel(context)
                                            toast("模型已加载")
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            Log.e(NLP_KEYWORD_MANAGEMENT_TAG, "Load NLP model failed", e)
                                            toast("模型加载失败：${e.message}")
                                        }
                                    }
                                },
                                enabled = modelState !is ModelState.Loading && modelState !is ModelState.Ready,
                            ) {
                                Text(if (modelState is ModelState.Loading) "加载中..." else "加载模型")
                            }
                            AppTextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        SentenceEmbeddingManager.unload()
                                        toast("已卸载模型")
                                    }
                                },
                                enabled = modelState is ModelState.Ready && !isExtracting,
                            ) {
                                Text("卸载模型")
                            }
                        }
                    }
                }
            }
            item {
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
            item {
                NlpCard {
                    Column(modifier = Modifier.padding(AppDimens.Space16)) {
                        Text("相似度阈值: ${String.format("%.2f", similarityThreshold)}", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(AppDimens.Space8))
                        AppSlider(
                            value = similarityThreshold,
                            onValueChange = { similarityThreshold = it },
                            valueRange = 0.1f..0.9f,
                            steps = 15,
                        )
                        Text(
                            "阈值越高，匹配越严格；阈值越低，匹配越宽松",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                NlpCard {
                    Column(modifier = Modifier.padding(AppDimens.Space16)) {
                        Text("提取关键词", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(AppDimens.Space8))
                        AppTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            labelText = "输入文本",
                            singleLine = false,
                            placeholder = "粘贴Feed内容或输入任意文本",
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 5,
                        )
                        Spacer(modifier = Modifier.height(AppDimens.Space8))
                        AppButton(
                            onClick = { extractKeywords() },
                            enabled = inputText.isNotBlank() && !isExtracting,
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text(if (isExtracting) "提取中..." else "提取关键词")
                        }
                    }
                }
            }
            if (extractedKeywords.isNotEmpty()) {
                item {
                    NlpCard {
                        Column(modifier = Modifier.padding(AppDimens.Space16)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("提取的关键词", style = MaterialTheme.typography.titleSmall)
                                AppTextButton(
                                    onClick = {
                                        val phrase = extractedKeywords.joinToString(" ")
                                        submitPhrase("已添加短语：$phrase", onSuccess = { extractedKeywords = emptyList() }) {
                                            database.blockedKeywordDao().insertKeyword(
                                                BlockedKeyword(keyword = phrase.trim(), keywordType = KeywordType.NLP_SEMANTIC.name),
                                            )
                                        }
                                    },
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
            }
            item {
                SettingsContent {
                    val recordCount = records.readyValue?.size
                    SettingItem(
                        modifier = Modifier.padding(horizontal = AppDimens.Space16).padding(bottom = AppDimens.Space16),
                        title = { Text("NLP 匹配记录") },
                        description = {
                            Text(if (recordCount != null) "最近 $recordCount 条被语义屏蔽的内容与匹配的短语" else "被语义屏蔽的内容与匹配的短语")
                        },
                        onClick = { showRecordsSheet = true },
                    )
                }
            }
        },
    ) { keyword ->
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
                    AppIconButton(onClick = { editingKeywordId = keyword.id }) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑短语“${keyword.keyword}”", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AppIconButton(
                        onClick = { launchAction("删除失败", "已删除") { database.blockedKeywordDao().deleteKeyword(keyword) } },
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "删除短语“${keyword.keyword}”", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
        )
    }

    if (showAddDialog) {
        PhraseDialog(
            title = "手动添加NLP短语",
            confirmText = "添加",
            initialPhrase = "",
            submitting = submitting,
            onDismiss = { if (!submitting) showAddDialog = false },
            onConfirm = { phrase ->
                submitPhrase("已添加短语", onSuccess = { showAddDialog = false }) {
                    database.blockedKeywordDao().insertKeyword(
                        BlockedKeyword(keyword = phrase.trim(), keywordType = KeywordType.NLP_SEMANTIC.name),
                    )
                }
            },
        )
    }

    phrases.readyValue?.firstOrNull { it.id == editingKeywordId }?.let { keyword ->
        PhraseDialog(
            title = "编辑NLP短语",
            confirmText = "保存",
            initialPhrase = keyword.keyword,
            submitting = submitting,
            onDismiss = { if (!submitting) editingKeywordId = null },
            onConfirm = { phrase ->
                submitPhrase("已更新短语", onSuccess = { editingKeywordId = null }) {
                    database.blockedKeywordDao().insertKeyword(keyword.copy(keyword = phrase.trim()))
                }
            },
        )
    }

    if (showRecordsSheet) {
        MyModalBottomSheet(onDismissRequest = { showRecordsSheet = false }) {
            BlocklistItemList(
                state = records,
                onRetry = { reload++ },
                description = "最近 ${BlockedContentRecord.MAX_RECORDS} 条被 NLP 语义屏蔽的内容，点开可查看匹配的短语。",
                emptyTitle = "暂无 NLP 匹配记录",
                emptyDescription = "首页内容命中 NLP 屏蔽短语时会记录在这里。",
                clearTitle = { count -> "清空 $count 条 NLP 匹配记录？" },
                clearSummary = "只删除这些记录，不影响屏蔽短语。清空后无法撤销。",
                onClearAll = { launchAction("清空失败") { database.blockedContentRecordDao().clearAllRecords() } },
                itemKey = { it.id },
                modifier = Modifier.fillMaxHeight(0.8f),
                contentPadding = PaddingValues(bottom = AppDimens.Space16),
            ) { record ->
                BlockedRecordItem(
                    record = record,
                    onDelete = { launchAction("删除失败") { database.blockedContentRecordDao().deleteRecord(record.id) } },
                )
            }
        }
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

@Composable
private fun BlockedRecordItem(
    record: BlockedContentRecord,
    onDelete: () -> Unit,
) {
    var expanded by rememberSaveable(record.id) { mutableStateOf(false) }
    val matchedKeywords = remember(record.matchedKeywords) {
        runCatching {
            Json.decodeFromString(ListSerializer(MatchedKeywordInfo.serializer()), record.matchedKeywords)
        }.getOrElse { e ->
            Log.e(NLP_KEYWORD_MANAGEMENT_TAG, "Failed to parse matched keywords", e)
            emptyList()
        }
    }

    AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = AppDimens.Space16, vertical = AppDimens.Space4)) {
        Column(modifier = Modifier.padding(AppDimens.Space16)) {
            Row(
                modifier = Modifier
                    .clickable { expanded = !expanded }
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(record.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(AppDimens.Space4))
                    Text(
                        "${record.contentType} · ${record.authorName ?: "未知作者"} · ${
                            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(record.blockedTime))
                        }",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AppIconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "收起" else "展开",
                    )
                }
            }

            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = AppDimens.Space8))
                val excerpt = record.excerpt
                if (!excerpt.isNullOrBlank()) {
                    Text("摘要", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(AppDimens.Space4))
                    SelectionContainer {
                        Text(excerpt, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(AppDimens.Space12))
                }
                Text("匹配的关键词（前3个）", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(AppDimens.Space4))
                matchedKeywords.forEach { match ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = AppDimens.Space4),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        SelectionContainer(modifier = Modifier.weight(1f)) {
                            Text(match.keyword, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            String.format("%.2f%%", match.similarity * 100),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(AppDimens.Space8))
                AppTextButton(
                    text = "删除记录",
                    onClick = onDelete,
                    modifier = Modifier.align(Alignment.End),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PhraseDialog(
    title: String,
    confirmText: String,
    initialPhrase: String,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var phrase by rememberSaveable(initialPhrase) { mutableStateOf(initialPhrase) }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                AppTextField(
                    value = phrase,
                    onValueChange = { phrase = it },
                    labelText = "短语",
                    singleLine = false,
                    placeholder = "用空格分隔多个关键词，如：政治 敏感",
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Spacer(modifier = Modifier.height(AppDimens.Space8))
                Text(
                    "提示：用空格分隔多个关键词来表达与逻辑。例如：\"政治 敏感\" 将匹配同时包含这两个主题的内容。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            AppButton(
                onClick = { if (phrase.isNotBlank()) onConfirm(phrase) },
                enabled = phrase.isNotBlank() && !submitting,
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(AppDimens.Space4))
                Text(confirmText)
            }
        },
        dismissButton = {
            AppTextButton(text = "取消", onClick = onDismiss, enabled = !submitting)
        },
    )
}
