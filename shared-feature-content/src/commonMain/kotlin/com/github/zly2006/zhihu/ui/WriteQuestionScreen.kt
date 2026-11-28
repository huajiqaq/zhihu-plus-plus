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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import com.github.zly2006.zhihu.account.accountIdentityKey
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.i18n.ContentStrings
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.WriteQuestion
import com.github.zly2006.zhihu.platform.rememberPlainTextClipboard
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppCompactTopBar
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.AppTextField
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import com.github.zly2006.zhihu.util.json
import com.github.zly2006.zhihu.util.raiseForStatus
import com.github.zly2006.zhihu.viewmodel.postSigned
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.formUrlEncode
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

const val WRITE_QUESTION_BACK_BUTTON_TAG = "write_question_back_button"
const val WRITE_QUESTION_TITLE_TAG = "write_question_title"
const val WRITE_QUESTION_DETAIL_TAG = "write_question_detail"
const val WRITE_QUESTION_PUBLISH_TAG = "write_question_publish"

@Serializable
private data class LocalQuestionDraft(
    val title: String = "",
    val detail: String = "",
)

@Serializable
private data class CreatedQuestionResponse(
    val id: Long,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WriteQuestionScreen(destination: WriteQuestion) {
    val navigator = LocalNavigator.current
    val userMessages = rememberUserMessageSink()
    val clipboard = rememberPlainTextClipboard()
    val coroutineScope = rememberCoroutineScope()
    val environment = rememberPaginationEnvironment(false)
    val settings = rememberSettingsStore()
    val account = rememberZhihuAccountStore()
    val accountClient = account.client
    val accounts by account.accountsState.collectAsState()
    val accountId = accounts.session.accountIdentityKey() ?: accounts.activeAccountId ?: "guest"
    val draftKey = "creation.question.${accountId.encodeURLParameter()}.draft"
    val initialDraft = remember(settings, draftKey) {
        runCatching { ZhihuJson.json.decodeFromString<LocalQuestionDraft>(settings.getString(draftKey, "")) }
            .getOrDefault(LocalQuestionDraft())
    }

    val title = key(draftKey) { rememberTextFieldState(initialDraft.title) }
    val detail = key(draftKey) { rememberTextFieldState(initialDraft.detail) }
    var localDraftSaved by remember(draftKey) { mutableStateOf(settings.getStringOrNull(draftKey) != null) }
    var draftPublished by remember(draftKey) { mutableStateOf(false) }
    var isSubmitting by remember(accountClient) { mutableStateOf(false) }
    var errorMessage by remember(accountClient) { mutableStateOf<String?>(null) }
    val skin = LocalAppSkinTokens.current

    fun currentDraft() = LocalQuestionDraft(title.text.toString(), detail.text.toString())

    fun persistDraft(draft: LocalQuestionDraft) {
        if (draftPublished) return
        settings.putString(draftKey, ZhihuJson.json.encodeToString(draft))
        localDraftSaved = true
    }

    /** 退出或换号时，仍按原草稿键补写状态流尚未看到的最后一次编辑。 */
    DisposableEffect(draftKey, settings) {
        var lastWritten = initialDraft

        fun saveChanges(draft: LocalQuestionDraft) {
            if (draft != lastWritten) {
                persistDraft(draft)
                lastWritten = draft
            }
        }
        val writer = coroutineScope.launch { snapshotFlow { currentDraft() }.collect { saveChanges(it) } }
        onDispose {
            writer.cancel()
            saveChanges(currentDraft())
        }
    }

    fun submit() {
        if (account.client !== accountClient || isSubmitting) return
        val draft = currentDraft()
        persistDraft(draft)
        val trimmedTitle = draft.title.trim()
        if (trimmedTitle.length < 5) {
            userMessages.showShortMessage(ContentStrings.questionTitleMinLength)
            return
        }
        val submittedTitle = trimmedTitle.let {
            if (it.endsWith('?') || it.endsWith('？')) it else "$it？"
        }
        val submittedDetail = draft.detail.trim()
        isSubmitting = true
        coroutineScope.launch {
            runCatching {
                environment
                    .postSigned("https://api.zhihu.com/questions") {
                        /** 11.12.0 QuestionEditorService / p0 要求表单 detail 而非 JSON content，空话题省略。 */
                        contentType(ContentType.Application.FormUrlEncoded)
                        setBody(
                            Parameters
                                .build {
                                    append("title", submittedTitle)
                                    append("detail", submittedDetail)
                                    append("is_anonymous", "false")
                                }.formUrlEncode(),
                        )
                    }.raiseForStatus()
                    .json<CreatedQuestionResponse>()
                    .id
            }.onSuccess { questionId ->
                if (account.client !== accountClient) return@onSuccess
                draftPublished = true
                settings.remove(draftKey)
                localDraftSaved = false
                userMessages.showShortMessage(ContentStrings.questionPublished)
                navigator.onNavigateBack()
                navigator.onNavigate(
                    Question(questionId, submittedTitle),
                )
            }.onFailure { e ->
                if (e is kotlin.coroutines.cancellation.CancellationException) throw e
                if (account.client !== accountClient) return@onFailure
                errorMessage = buildWriteOperationErrorMessage(ContentStrings.questionPublishFailed, e)
            }
            isSubmitting = false
        }
    }

    AppScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            val textMeasurer = rememberTextMeasurer()
            val titleStyle = if (settingSkinIsMiuix()) skin.title3 else MaterialTheme.typography.titleLarge
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val requiredWidth = with(LocalDensity.current) {
                    (
                        textMeasurer.measure(ContentStrings.askQuestion, style = titleStyle, softWrap = false).size.width +
                            textMeasurer.measure(ContentStrings.publishQuestion, style = if (settingSkinIsMiuix()) skin.main else MaterialTheme.typography.labelLarge, softWrap = false).size.width
                    ).toDp()
                } + AppDimens.TouchTarget + AppDimens.PageHorizontalPadding * 2 + AppDimens.Space24 * 2
                val separateAction = maxWidth < requiredWidth
                Column {
                    AppCompactTopBar(
                        title = ContentStrings.askQuestion,
                        onBack = navigator.onNavigateBack,
                        backTag = WRITE_QUESTION_BACK_BUTTON_TAG,
                        actions = {
                            if (!separateAction) {
                                AppButton(
                                    text = if (isSubmitting) ContentStrings.publishingEllipsis else ContentStrings.publishQuestion,
                                    onClick = { submit() },
                                    enabled = !isSubmitting,
                                    modifier = Modifier.testTag(WRITE_QUESTION_PUBLISH_TAG),
                                )
                            }
                        },
                    )
                    if (separateAction) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space4), horizontalArrangement = Arrangement.End) {
                            AppButton(
                                text = if (isSubmitting) ContentStrings.publishingEllipsis else ContentStrings.publishQuestion,
                                onClick = { submit() },
                                enabled = !isSubmitting,
                                modifier = Modifier.testTag(WRITE_QUESTION_PUBLISH_TAG),
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space12),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Space12),
        ) {
            AppTextField(
                state = title,
                enabled = !isSubmitting,
                placeholder = ContentStrings.questionTitleHint,
                labelText = ContentStrings.questionTitle,
                modifier = Modifier.fillMaxWidth().testTag(WRITE_QUESTION_TITLE_TAG),
            )
            if (localDraftSaved) {
                Text(ContentStrings.draftSavedLocally, style = skin.footnote2, color = skin.onSurfaceVariantSummary)
            }
            Text(
                ContentStrings.optionalDetails,
                style = skin.body2,
                color = skin.onSurfaceVariantSummary,
            )
            AppTextField(
                state = detail,
                enabled = !isSubmitting,
                placeholder = ContentStrings.questionDetailsHint,
                lineLimits = TextFieldLineLimits.MultiLine(),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .testTag(WRITE_QUESTION_DETAIL_TAG)
                    .semantics { contentDescription = ContentStrings.optionalDetails },
            )
        }
    }
    WriteOperationErrorDialog(
        message = errorMessage,
        onDismissRequest = { errorMessage = null },
        onCopy = { clipboard("write-question-error", it) },
    )
}
