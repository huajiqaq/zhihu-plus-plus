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
import com.github.zly2006.zhihu.i18n.AccountStrings
import com.github.zly2006.zhihu.i18n.AndroidNlpStrings
import com.github.zly2006.zhihu.i18n.ContentStrings
import com.github.zly2006.zhihu.i18n.SettingsStrings
import com.github.zly2006.zhihu.i18n.WidgetsStrings
import com.github.zly2006.zhihu.nlp.NLPService
import com.github.zly2006.zhihu.nlp.SentenceEmbeddingManager
import com.github.zly2006.zhihu.nlp.nlpDisplayMessage
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

    fun launchAction(failurePrefix: () -> String, successMessage: (() -> String)? = null, action: suspend () -> Unit) {
        coroutineScope.launch {
            try {
                withContext(Dispatchers.IO) { action() }
                currentCoroutineContext().ensureActive()
                successMessage?.let { toast(it()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(NLP_KEYWORD_MANAGEMENT_TAG, failurePrefix(), e)
                toast(SettingsStrings.failureWithReason(failurePrefix(), e.nlpDisplayMessage { WidgetsStrings.unknownError }))
            }
        }
    }

    // 确认按钮的禁用要等下一帧重组才生效，同一帧里的第二次点击只能靠这里拦住。
    fun submitPhrase(successMessage: () -> String, onSuccess: () -> Unit, write: suspend () -> Unit) {
        if (submitting) return
        submitting = true
        coroutineScope.launch {
            try {
                withContext(Dispatchers.IO) { write() }
                currentCoroutineContext().ensureActive()
                toast(successMessage())
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(NLP_KEYWORD_MANAGEMENT_TAG, "Failed to save NLP phrase", e)
                toast(SettingsStrings.failureWithReason(AndroidNlpStrings.saveFailed, e.nlpDisplayMessage { WidgetsStrings.unknownError }))
            } finally {
                submitting = false
            }
        }
    }

    fun extractKeywords() {
        if (isExtracting) return
        val sourceText = inputText.text.toString()
        if (sourceText.isBlank()) {
            toast(AndroidNlpStrings.enterText)
            return
        }
        isExtracting = true
        coroutineScope.launch {
            try {
                val extracted = NLPService.extractKeywords(sourceText, 10)
                currentCoroutineContext().ensureActive()
                if (!inputText.text.contentEquals(sourceText)) return@launch
                extractedKeywords = extracted
                if (extractedKeywords.isEmpty()) toast(AndroidNlpStrings.noKeywordsExtracted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(NLP_KEYWORD_MANAGEMENT_TAG, "Extract NLP keywords failed", e)
                toast(SettingsStrings.failureWithReason(AndroidNlpStrings.extractionFailed, e.nlpDisplayMessage { WidgetsStrings.unknownError }))
            } finally {
                isExtracting = false
            }
        }
    }

    BlocklistItemList(
        state = phrases,
        onRetry = { reload++ },
        description = AndroidNlpStrings.blockedPhrasesScope(keywordScopeDescription(keywordScope)),
        emptyTitle = AndroidNlpStrings.phrasesEmpty,
        emptyDescription = AndroidNlpStrings.phrasesEmptyHint,
        clearTitle = { count -> AndroidNlpStrings.clearPhrasesTitle(count) },
        clearSummary = AndroidNlpStrings.clearPhrasesHint,
        clearTag = "nlpKeywords:clear",
        onClearAll = {
            val scopeToClear = keywordScope.name
            launchAction({ SettingsStrings.clearFailed }) {
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
                                toast(AndroidNlpStrings.modelLoaded)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e(NLP_KEYWORD_MANAGEMENT_TAG, "Load NLP model failed", e)
                                toast(SettingsStrings.failureWithReason(AndroidNlpStrings.modelLoadFailed, e.nlpDisplayMessage { AndroidNlpStrings.modelLoadFailed }))
                            }
                        }
                    },
                    onUnload = {
                        coroutineScope.launch {
                            SentenceEmbeddingManager.unload()
                            toast(AndroidNlpStrings.modelUnloaded)
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
                            submitPhrase({ AndroidNlpStrings.phraseAddedDetail(phrase) }, onSuccess = { extractedKeywords = emptyList() }) {
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
                launchAction({ SettingsStrings.deleteFailed }, { AccountStrings.deleted }) {
                    database.blockedKeywordDao().deleteKeywordById(keyword.id, KeywordType.NLP_SEMANTIC.name, keywordScope.name)
                }
            },
        )
    }

    if (showAddDialog) {
        PhraseDialog(
            title = AndroidNlpStrings.addPhraseTitle,
            confirmText = AccountStrings.add,
            initialPhrase = "",
            submitting = submitting,
            onDismiss = { if (!submitting) showAddDialog = false },
            onConfirm = { phrase ->
                submitPhrase({ AndroidNlpStrings.phraseAdded }, onSuccess = { showAddDialog = false }) {
                    database.blockedKeywordDao().insertKeyword(
                        BlockedKeyword(keyword = phrase.trim(), keywordType = KeywordType.NLP_SEMANTIC.name, scope = keywordScope.name),
                    )
                }
            },
        )
    }

    phrases.readyValue?.firstOrNull { it.id == editingKeywordId }?.let { keyword ->
        PhraseDialog(
            title = SettingsStrings.nlpEditPhrase,
            confirmText = AndroidNlpStrings.save,
            initialPhrase = keyword.keyword,
            submitting = submitting,
            onDismiss = { if (!submitting) editingKeywordId = null },
            onConfirm = { phrase ->
                submitPhrase({ AndroidNlpStrings.phraseUpdated }, onSuccess = { editingKeywordId = null }) {
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
            onClearAll = { launchAction({ SettingsStrings.clearFailed }) { database.blockedContentRecordDao().clearAllRecords() } },
        ) { record ->
            BlockedRecordItem(
                record = record,
                onDelete = { launchAction({ SettingsStrings.deleteFailed }) { database.blockedContentRecordDao().deleteRecord(record.id) } },
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
                        "${record.contentType} · ${record.authorName ?: ContentStrings.unknownAuthor} · ${
                            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(record.blockedTime))
                        }",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AppIconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) SettingsStrings.collapse else SettingsStrings.expand,
                    )
                }
            }

            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = AppDimens.Space8))
                val excerpt = record.excerpt
                if (!excerpt.isNullOrBlank()) {
                    Text(AndroidNlpStrings.excerpt, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(AppDimens.Space4))
                    SelectionContainer {
                        Text(excerpt, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(AppDimens.Space12))
                }
                Text(AndroidNlpStrings.topMatchedKeywords, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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
                    text = AndroidNlpStrings.deleteRecord,
                    onClick = onDelete,
                    modifier = Modifier.align(Alignment.End),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
