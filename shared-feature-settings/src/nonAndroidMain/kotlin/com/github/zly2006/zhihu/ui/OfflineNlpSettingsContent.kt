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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.github.zly2006.zhihu.filter.asListLoadState
import com.github.zly2006.zhihu.i18n.AccountStrings
import com.github.zly2006.zhihu.i18n.SettingsStrings
import com.github.zly2006.zhihu.i18n.WidgetsStrings
import com.github.zly2006.zhihu.nlp.OfflineNlpController
import com.github.zly2006.zhihu.nlp.OfflineNlpErrorCode
import com.github.zly2006.zhihu.nlp.OfflineNlpFailure
import com.github.zly2006.zhihu.nlp.toOfflineNlpFailure
import com.github.zly2006.zhihu.nlp.OfflineNlpModelState
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.ui.components.AppAlertDialog
import com.github.zly2006.zhihu.ui.components.AppBottomSheet
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppIcon
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppSlider
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.AppTextField
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingItemWithSwitch
import com.github.zly2006.zhihu.ui.components.SettingsContent
import com.github.zly2006.zhihu.util.AppLog
import com.github.zly2006.zhihu.util.formatFullDateTime
import com.github.zly2006.zhihu.viewmodel.LoadState
import com.github.zly2006.zhihu.viewmodel.filter.BlockedKeyword
import com.github.zly2006.zhihu.viewmodel.filter.KeywordScope
import com.github.zly2006.zhihu.viewmodel.filter.KeywordType
import com.github.zly2006.zhihu.viewmodel.filter.MatchedKeywordInfo
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import com.github.zly2006.zhihu.viewmodel.readyValue
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** 与 Full 共用屏蔽规则、阈值与匹配记录，只替换非 Android 的模型宿主。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun PlatformNlpSettingsContent() {
    val database = remember { getContentFilterDatabase() }
    val settings = rememberSettingsStore()
    val messages = rememberUserMessageSink()
    val scope = rememberCoroutineScope()
    val modelState by OfflineNlpController.state.collectAsState()
    val ready = modelState == OfflineNlpModelState.Ready
    var keywordScope by rememberSaveable { mutableStateOf(KeywordScope.ALL) }
    var reload by remember { mutableIntStateOf(0) }
    val phrases by remember(reload, keywordScope) {
        database.blockedKeywordDao().observeKeywordsByType(KeywordType.NLP_SEMANTIC.name, keywordScope.name).asListLoadState()
    }.collectAsState(LoadState.Loading)
    val records by remember(reload) { database.blockedContentRecordDao().observeRecentRecords().asListLoadState() }
        .collectAsState(LoadState.Loading)
    var enabled by rememberSetting(settings, "enableNLPBlocking", true)
    var threshold by rememberSetting(settings, "nlpSimilarityThreshold", 0.8f)
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showRecords by rememberSaveable { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testJob by remember { mutableStateOf<Job?>(null) }
    var testResult by remember { mutableStateOf<SemanticRuleTestResult?>(null) }
    var testError by remember { mutableStateOf<(() -> String)?>(null) }
    val testInput = rememberTextFieldState()
    val ruleTexts = phrases.readyValue?.map { it.keyword }.orEmpty()
    var modelActionRunning by remember { mutableStateOf(false) }
    var loadJob by remember { mutableStateOf<Job?>(null) }
    var modelError by remember { mutableStateOf<OfflineNlpFailure?>(null) }

    LaunchedEffect(testInput.text.toString(), keywordScope, threshold, ruleTexts) {
        testJob?.cancel()
        testResult = null
        testError = null
    }
    LaunchedEffect(modelState) {
        if (!ready) testResult = null
    }

    fun modelAction(loading: Boolean = false, action: suspend () -> Unit) {
        if (modelActionRunning || testing) return
        modelActionRunning = true
        modelError = null
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("OfflineNlpSettings", "Model operation failed", e)
                modelError = e.toOfflineNlpFailure()
            } finally {
                modelActionRunning = false
                loadJob = null
            }
        }
        if (loading) loadJob = job
        job.start()
    }

    fun writeRule(action: suspend () -> Unit, onSuccess: () -> Unit = {}) {
        if (submitting) return
        submitting = true
        scope.launch {
            try {
                action()
                currentCoroutineContext().ensureActive()
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("OfflineNlpSettings", "Rule operation failed", e)
                messages.showShortMessage(e.toOfflineNlpFailure(OfflineNlpErrorCode.RULE_FAILED).displayMessage)
            } finally {
                submitting = false
            }
        }
    }

    BlocklistItemList(
        state = phrases,
        onRetry = { reload++ },
        description = SettingsStrings.nlpPhrases,
        emptyTitle = SettingsStrings.nlpPhrasesEmpty,
        clearTitle = { SettingsStrings.nlpPhrasesClear },
        clearSummary = SettingsStrings.nlpPhrasesClearHint,
        onClearAll = {
            val scopeToClear = keywordScope.name
            writeRule({ database.blockedKeywordDao().deleteKeywordsByType(KeywordType.NLP_SEMANTIC.name, scopeToClear) })
        },
        itemKey = { it.id },
        contentPadding = PaddingValues(vertical = AppDimens.Space16),
        listTag = "nlpSettings:phrases",
        clearTag = "nlpSettings:clearPhrases",
        headerActions = {
            AppTextButton(
                text = AccountStrings.add,
                onClick = { showAdd = true },
                enabled = !submitting,
                modifier = Modifier.testTag("nlpSettings:addPhrase"),
            )
        },
        leadingItems = {
            item {
                SettingsContent {
                    OfflineNlpModelSection(
                        state = modelState,
                        busy = modelActionRunning || testing,
                        loading = loadJob != null,
                        operationError = modelError,
                        onLoad = { modelAction(loading = true) { OfflineNlpController.load() } },
                        onCancel = { loadJob?.cancel() },
                        onUnload = { modelAction { OfflineNlpController.unload() } },
                        onDelete = { modelAction { OfflineNlpController.deleteModel() } },
                    )
                    SettingItemWithSwitch(
                        title = { Text(SettingsStrings.nlpBlockingEnabled) },
                        description = { Text(if (ready) SettingsStrings.nlpRuleHint else SettingsStrings.nlpInactive) },
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            if (it && !ready) messages.showLongMessage(SettingsStrings.nlpInactive)
                        },
                        settingKey = "enableNLPBlocking",
                    )
                    KeywordScopeSettingItem(keywordScope, { keywordScope = it }, !submitting, "nlpKeywords:scope")
                    SettingItem(
                        title = { Text(SettingsStrings.nlpThreshold(semanticScoreText(threshold.toDouble()))) },
                        description = { Text(SettingsStrings.nlpThresholdHint) },
                        bottomAction = {
                            val slider = rememberSliderState(value = threshold, steps = 15, trackRange = 0.1f..0.9f)
                            slider.value = threshold
                            AppSlider(state = slider, onValueChange = { threshold = it })
                        },
                        settingKey = "nlpSimilarityThreshold",
                    )
                    SettingItem(
                        title = { Text(SettingsStrings.nlpTest) },
                        description = { Text(SettingsStrings.nlpTestHint) },
                        bottomAction = {
                            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Space8)) {
                                AppTextField(
                                    state = testInput,
                                    lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3, maxHeightInLines = 6),
                                    labelText = SettingsStrings.nlpTest,
                                    modifier = Modifier.fillMaxWidth().testTag("nlpSettings:testInput"),
                                )
                                if (!ready) Text(SettingsStrings.nlpInactive, color = MaterialTheme.colorScheme.error)
                                AppButton(
                                    text = if (testing) WidgetsStrings.loading else SettingsStrings.nlpTest,
                                    enabled = ready && !modelActionRunning && !testing && testInput.text.isNotBlank() && ruleTexts.isNotEmpty(),
                                    modifier = Modifier.testTag("nlpSettings:test"),
                                    onClick = {
                                        if (testing || modelActionRunning || OfflineNlpController.state.value != OfflineNlpModelState.Ready) return@AppButton
                                        val input = testInput.text.toString()
                                        val testScope = keywordScope
                                        val testThreshold = threshold
                                        val testPhrases = ruleTexts.toList()
                                        testing = true
                                        testResult = null
                                        testError = null
                                        testJob = scope.launch(start = CoroutineStart.LAZY) {
                                            try {
                                                val matches = OfflineNlpController.checkBlockedPhrases(input, testPhrases, testThreshold.toDouble())
                                                currentCoroutineContext().ensureActive()
                                                when (val resultState = OfflineNlpController.state.value) {
                                                    OfflineNlpModelState.Ready -> {
                                                        testResult = SemanticRuleTestResult(input, testScope, testThreshold, testPhrases, matches)
                                                    }
                                                    is OfflineNlpModelState.Error -> {
                                                        testError = { SettingsStrings.nlpOperationFailed(resultState.failure.displayMessage) }
                                                    }
                                                    else -> testError = { SettingsStrings.nlpInactive }
                                                }
                                            } catch (e: CancellationException) {
                                                throw e
                                            } catch (e: Exception) {
                                                AppLog.e("OfflineNlpSettings", "Semantic rule test failed", e)
                                                val failure = e.toOfflineNlpFailure(OfflineNlpErrorCode.INFERENCE_FAILED)
                                                testError = { SettingsStrings.nlpOperationFailed(failure.displayMessage) }
                                            } finally {
                                                testing = false
                                                testJob = null
                                            }
                                        }
                                        testJob?.start()
                                    },
                                )
                                testError?.let { Text(it(), color = MaterialTheme.colorScheme.error) }
                                testResult?.takeIf {
                                    ready && it.text == testInput.text.toString() && it.scope == keywordScope && it.threshold == threshold && it.phrases == ruleTexts
                                }?.let { result ->
                                    SelectionContainer {
                                        Text(
                                            if (result.matches.isEmpty()) SettingsStrings.nlpTestNoMatch else SettingsStrings.nlpTestMatches(
                                                result.matches.joinToString("\n") { (phrase, score) -> "$phrase · ${semanticScoreText(score)}" },
                                            ),
                                            modifier = Modifier.testTag("nlpSettings:testResult"),
                                        )
                                    }
                                }
                            }
                        },
                    )
                    SettingItem(
                        title = { Text(SettingsStrings.nlpRecords) },
                        description = { Text(SettingsStrings.nlpRecordsHint) },
                        onClick = { showRecords = true },
                    )
                }
            }
        },
    ) { phrase ->
        SettingItem(
            title = { Text(phrase.keyword) },
            description = { Text(formatFullDateTime(phrase.createdTime / 1_000)) },
            endAction = {
                Row {
                    AppIconButton(onClick = { editingId = phrase.id }, enabled = !submitting) {
                        AppIcon(Icons.Default.Edit, contentDescription = SettingsStrings.nlpEditPhrase)
                    }
                    AppIconButton(
                        onClick = {
                            writeRule({ database.blockedKeywordDao().deleteKeywordById(phrase.id, KeywordType.NLP_SEMANTIC.name, phrase.scope) })
                        },
                        enabled = !submitting,
                    ) {
                        AppIcon(Icons.Default.Delete, contentDescription = AccountStrings.keywordDeleteDescription(phrase.keyword))
                    }
                }
            },
        )
    }
    val editing = phrases.readyValue?.firstOrNull { it.id == editingId }
    if (showAdd || editing != null) {
        key(editing?.id) {
            val text = rememberTextFieldState(editing?.keyword.orEmpty())
            AppAlertDialog(
                title = { Text(if (editing == null) SettingsStrings.nlpPhrases else SettingsStrings.nlpEditPhrase) },
                onDismissRequest = {
                    if (!submitting) {
                        showAdd = false
                        editingId = null
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Space8)) {
                        AppTextField(
                            state = text,
                            labelText = SettingsStrings.nlpPhrases,
                            lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2),
                            enabled = !submitting,
                            modifier = Modifier.fillMaxWidth().testTag("nlpSettings:phraseInput"),
                        )
                        Text(SettingsStrings.nlpRuleHint)
                    }
                },
                confirmButton = {
                    AppButton(
                        text = AccountStrings.confirm,
                        enabled = !submitting && text.text.isNotBlank(),
                        onClick = {
                            val value = text.text.toString().trim()
                            if (value.isNotEmpty()) {
                                val updated = editing?.copy(keyword = value)
                                    ?: BlockedKeyword(keyword = value, keywordType = KeywordType.NLP_SEMANTIC.name, scope = keywordScope.name)
                                writeRule(
                                    { database.blockedKeywordDao().insertKeyword(updated) },
                                    onSuccess = {
                                        showAdd = false
                                        editingId = null
                                        messages.showShortMessage(SettingsStrings.nlpPhraseSaved)
                                    },
                                )
                            }
                        },
                    )
                },
                dismissButton = {
                    AppTextButton(
                        text = AccountStrings.cancel,
                        enabled = !submitting,
                        onClick = {
                            showAdd = false
                            editingId = null
                        },
                    )
                },
            )
        }
    }
    if (showRecords) {
        AppBottomSheet(onDismissRequest = { showRecords = false }) {
            BlocklistItemList(
                state = records,
                onRetry = { reload++ },
                description = SettingsStrings.nlpRecordsHint,
                emptyTitle = SettingsStrings.nlpRecordsEmpty,
                clearTitle = { SettingsStrings.nlpRecordsClear },
                clearSummary = SettingsStrings.nlpRecordsClearHint,
                onClearAll = { writeRule({ database.blockedContentRecordDao().clearAllRecords() }) },
                itemKey = { it.id },
                modifier = Modifier.fillMaxHeight(0.8f),
            ) { record ->
                val matches = remember(record.matchedKeywords) {
                    runCatching { Json.decodeFromString<List<MatchedKeywordInfo>>(record.matchedKeywords) }
                        .onFailure { AppLog.e("OfflineNlpSettings", "Could not read matched phrases", it) }
                        .getOrNull()
                }
                BlocklistItemRow(
                    title = record.title,
                    description = listOfNotNull(
                        record.authorName,
                        formatFullDateTime(record.blockedTime / 1_000),
                        record.excerpt?.takeIf { it.isNotBlank() },
                        matches?.joinToString("\n") { "${it.keyword} · ${semanticScoreText(it.similarity)}" } ?: SettingsStrings.readRecordsFailed,
                    ).joinToString("\n"),
                    deleteDescription = SettingsStrings.deleteBlockedRecord,
                    onDelete = { writeRule({ database.blockedContentRecordDao().deleteRecord(record.id) }) },
                )
            }
        }
    }
}

private data class SemanticRuleTestResult(
    val text: String,
    val scope: KeywordScope,
    val threshold: Float,
    val phrases: List<String>,
    val matches: List<Pair<String, Double>>,
)

private fun semanticScoreText(value: Double): String {
    val hundredths = (value * 100).roundToInt()
    return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
}
