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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.github.zly2006.zhihu.filter.asListLoadState
import com.github.zly2006.zhihu.nlp.NLPService
import com.github.zly2006.zhihu.nlp.SentenceEmbeddingManager
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.ui.components.AppCard
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.viewmodel.LoadState
import com.github.zly2006.zhihu.viewmodel.filter.BlockedContentRecord
import com.github.zly2006.zhihu.viewmodel.filter.BlockedKeyword
import com.github.zly2006.zhihu.viewmodel.filter.KeywordScope
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

    var keywordScope by rememberSaveable { mutableStateOf(KeywordScope.ALL) }
    var reload by remember { mutableIntStateOf(0) }
    val phrases by remember(reload, keywordScope) {
        database.blockedKeywordDao().observeKeywordsByType(KeywordType.NLP_SEMANTIC.name, keywordScope.name).asListLoadState()
    }.collectAsState(LoadState.Loading)
    val records by remember(reload) { database.blockedContentRecordDao().observeRecentRecords().asListLoadState() }
        .collectAsState(LoadState.Loading)

    var isExtracting by remember { mutableStateOf(false) }
    var extractedKeywords by remember { mutableStateOf<List<String>>(emptyList()) }
    val inputText = rememberTextFieldState()
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
        val sourceText = inputText.text.toString()
        if (sourceText.isBlank()) {
            toast("请输入文本")
            return
        }
        isExtracting = true
        coroutineScope.launch {
            try {
                val extracted = NLPService.extractKeywords(sourceText, 10)
                currentCoroutineContext().ensureActive()
                if (!inputText.text.contentEquals(sourceText)) return@launch
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
        description = "已屏蔽短语：${keywordScopeDescription(keywordScope)}",
        emptyTitle = "暂无 NLP 屏蔽短语",
        emptyDescription = "在上方提取关键词，或点“手动添加”。",
        clearTitle = { count -> "清空当前范围的 $count 个 NLP 屏蔽短语？" },
        clearSummary = "仅清空当前作用范围的 NLP 短语，不影响其他范围或精确关键词。清空后无法撤销，如需保留请先导出规则。",
        clearTag = "nlpKeywords:clear",
        onClearAll = {
            val scopeToClear = keywordScope.name
            launchAction("清空失败") {
                database.blockedKeywordDao().deleteKeywordsByType(KeywordType.NLP_SEMANTIC.name, scopeToClear)
            }
        },
        itemKey = { it.id },
        contentPadding = PaddingValues(
            top = AppDimens.Space16 + innerPadding.calculateTopPadding(),
            bottom = AppDimens.Space16 + innerPadding.calculateBottomPadding(),
        ),
        headerActions = {
            NLPKeywordManagementAddAction(onClick = { showAddDialog = true })
        },
        leadingItems = {
            item {
                NLPKeywordManagementScopeSection(
                    keywordScope = keywordScope,
                    submitting = submitting,
                    onKeywordScopeChange = { keywordScope = it },
                )
            }
            item {
                NLPKeywordManagementModelSection(
                    modelState = modelState,
                    isExtracting = isExtracting,
                    onLoad = {
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
                    onUnload = {
                        coroutineScope.launch {
                            SentenceEmbeddingManager.unload()
                            toast("已卸载模型")
                        }
                    },
                )
            }
            item {
                NLPKeywordManagementInfoSection()
            }
            item {
                NLPKeywordManagementThresholdSection(
                    similarityThreshold = similarityThreshold,
                    onSimilarityThresholdChange = { similarityThreshold = it },
                )
            }
            item {
                NLPKeywordManagementExtractionSection(
                    inputText = inputText,
                    isExtracting = isExtracting,
                    onExtract = { extractKeywords() },
                )
            }
            if (extractedKeywords.isNotEmpty()) {
                item {
                    NLPKeywordManagementExtractedKeywordsSection(
                        extractedKeywords = extractedKeywords,
                        submitting = submitting,
                        onAdd = {
                            val phrase = extractedKeywords.joinToString(" ")
                            submitPhrase("已添加短语：$phrase", onSuccess = { extractedKeywords = emptyList() }) {
                                database.blockedKeywordDao().insertKeyword(
                                    BlockedKeyword(keyword = phrase.trim(), keywordType = KeywordType.NLP_SEMANTIC.name, scope = keywordScope.name),
                                )
                            }
                        },
                    )
                }
            }
            item {
                NLPKeywordManagementRecordsEntry(
                    recordCount = records.readyValue?.size,
                    onClick = { showRecordsSheet = true },
                )
            }
        },
    ) { keyword ->
        NLPKeywordManagementKeywordItem(
            keyword = keyword,
            onEdit = { editingKeywordId = keyword.id },
            onDelete = {
                launchAction("删除失败", "已删除") {
                    database.blockedKeywordDao().deleteKeywordById(keyword.id, KeywordType.NLP_SEMANTIC.name, keywordScope.name)
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
                        BlockedKeyword(keyword = phrase.trim(), keywordType = KeywordType.NLP_SEMANTIC.name, scope = keywordScope.name),
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
        NLPKeywordManagementRecordsSheet(
            records = records,
            onDismiss = { showRecordsSheet = false },
            onRetry = { reload++ },
            onClearAll = { launchAction("清空失败") { database.blockedContentRecordDao().clearAllRecords() } },
        ) { record ->
            BlockedRecordItem(
                record = record,
                onDelete = { launchAction("删除失败") { database.blockedContentRecordDao().deleteRecord(record.id) } },
            )
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
