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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import com.github.zly2006.zhihu.account.accountIdentityKey
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.editor.PIN_TOPIC_SEPARATOR
import com.github.zly2006.zhihu.editor.PinContentTopicItem
import com.github.zly2006.zhihu.editor.PinTopicSuggestion
import com.github.zly2006.zhihu.editor.PinTopicSuggestionRequest
import com.github.zly2006.zhihu.editor.PinTopicSuggestionResponse
import com.github.zly2006.zhihu.editor.PublishPinRequest
import com.github.zly2006.zhihu.editor.SavePinDraftRequest
import com.github.zly2006.zhihu.editor.UnknownImageFormatException
import com.github.zly2006.zhihu.editor.ZhihuImageUploadSource
import com.github.zly2006.zhihu.editor.buildPinContentPayload
import com.github.zly2006.zhihu.editor.compilePinMarkdown
import com.github.zly2006.zhihu.editor.isImagePickerSupported
import com.github.zly2006.zhihu.editor.parsePublishContentId
import com.github.zly2006.zhihu.editor.rememberImagePickerLauncher
import com.github.zly2006.zhihu.editor.toPinMarkdown
import com.github.zly2006.zhihu.editor.uploadZhihuImage
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.WritePin
import com.github.zly2006.zhihu.platform.rememberPlainTextClipboard
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.util.json
import com.github.zly2006.zhihu.util.raiseForStatus
import com.github.zly2006.zhihu.viewmodel.postSigned
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.serialization.encodeToString
import kotlin.time.Duration.Companion.milliseconds

const val WRITE_PIN_BACK_BUTTON_TAG = "write_pin_back_button"

const val WRITE_PIN_TITLE_TAG = "WritePinTitle"
const val WRITE_PIN_CONTENT_TAG = "WritePinContent"
const val WRITE_PIN_FAB_PREVIEW_TAG = "WritePinFabPreview"
const val WRITE_PIN_FAB_IMAGE_TAG = "WritePinFabImage"
const val WRITE_PIN_FAB_SAVE_TAG = "WritePinFabSave"
const val WRITE_PIN_IMAGE_LIST_TAG = "WritePinImageList"
const val WRITE_PIN_TOPIC_SUGGESTIONS_TAG = "WritePinTopicSuggestions"

private data class PinTopicTarget(
    val source: String,
    val selection: TextRange,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WritePinScreen(destination: WritePin = WritePin()) {
    val navigator = LocalNavigator.current
    val userMessages = rememberUserMessageSink()
    val copyToClipboard = rememberPlainTextClipboard()
    val settings = rememberSettingsStore()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val environment = rememberPaginationEnvironment(false)
    val account = rememberZhihuAccountStore()
    val accountClient = account.client
    val accounts by account.accountsState.collectAsState()
    val accountId = accounts.session.accountIdentityKey() ?: accounts.activeAccountId ?: "guest"
    val draftKey = "creation.pin.${accountId.encodeURLParameter()}.${destination.publishTopicId.encodeURLParameter()}.draft"
    val coroutineScope = key(draftKey, accountClient) { rememberCoroutineScope() }
    val saved = remember(settings, draftKey) { settings.getStringOrNull(draftKey) }
    val restored = remember(saved) { saved?.let(::readLocalPinDraft) }
    val initialDraft = remember(restored, destination) {
        restored ?: LocalPinDraft(
            content = PinContentTopicItem(destination.publishTopicId, destination.topicName.removeSurrounding("#"))
                .toPinMarkdown()
                ?.let { it + PIN_TOPIC_SEPARATOR }
                .orEmpty(),
        )
    }
    val content = remember(draftKey) {
        TextFieldState(initialDraft.content, TextRange(initialDraft.contentSelectionStart, initialDraft.contentSelectionEnd))
    }
    val title = remember(draftKey) {
        TextFieldState(initialDraft.title, TextRange(initialDraft.titleSelectionStart, initialDraft.titleSelectionEnd))
    }
    val topics = remember(content) { PinTopicEditing() }
    var images by remember(draftKey) { mutableStateOf(initialDraft.images) }
    var localDraftSaved by remember(draftKey) { mutableStateOf(restored != null) }
    var typedTopicStart by remember(draftKey, accountClient) { mutableStateOf<Int?>(null) }
    var topicTarget by remember(draftKey, accountClient) { mutableStateOf<PinTopicTarget?>(null) }
    val topicQuery = remember(topicTarget, draftKey, accountClient) { TextFieldState() }
    var topicRetry by remember(topicTarget, draftKey, accountClient) { mutableIntStateOf(0) }
    val searchTerm = topicQuery.text.trim().toString()
    var topicSuggestions by remember(topicTarget, searchTerm, topicRetry, draftKey, accountClient) {
        mutableStateOf<List<PinTopicSuggestion>>(emptyList())
    }
    var topicSuggestionError by remember(topicTarget, searchTerm, topicRetry, draftKey, accountClient) { mutableStateOf<String?>(null) }
    var isSubmitting by remember(accountClient) { mutableStateOf(false) }
    var isUploadingImage by remember(accountClient) { mutableStateOf(false) }
    var floatingActionBounds by remember { mutableStateOf(Rect.Zero) }
    var imageListBounds by remember { mutableStateOf(Rect.Zero) }
    var errorDialogMessage by remember(draftKey, accountClient) {
        mutableStateOf(if (saved != null && restored == null) "此本机草稿无法读取，原草稿不会被覆盖。" else null)
    }
    var showPreviewSheet by remember(accountClient) { mutableStateOf(false) }
    val previewSheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    var isPreviewLoading by remember(accountClient) { mutableStateOf(false) }
    var previewHtml by remember(accountClient) { mutableStateOf<String?>(null) }
    var previewUseWebView by remember(accountClient) { mutableStateOf(false) }
    var draftPublished by remember(draftKey) { mutableStateOf(false) }

    fun currentDraft() = LocalPinDraft(
        title = title.text.toString(),
        content = content.text.toString(),
        images = images,
        titleSelectionStart = title.selection.start,
        titleSelectionEnd = title.selection.end,
        contentSelectionStart = content.selection.start,
        contentSelectionEnd = content.selection.end,
    )

    fun persistDraft(draft: LocalPinDraft = currentDraft()) {
        if (draftPublished || saved != null && restored == null) return
        if (!localDraftSaved && draft == initialDraft) return
        settings.putString(draftKey, ZhihuJson.json.encodeToString(draft))
        localDraftSaved = true
    }

    /** 退出或切换账号时仍写入原草稿键，不依赖下一次状态流调度。 */
    DisposableEffect(draftKey, accountClient) {
        onDispose { persistDraft() }
    }
    LaunchedEffect(draftKey, accountClient) {
        var previous = content.text.toString() to content.selection
        snapshotFlow { currentDraft() }.collect { draft ->
            if (account.client !== accountClient) return@collect
            val current = draft.content to TextRange(draft.contentSelectionStart, draft.contentSelectionEnd)
            if (current != previous) {
                typedTopicStart = typedPinTopicStart(previous, current)
                if (topicTarget?.source != current.first) topicTarget = null
            }
            previous = current
            persistDraft(draft)
        }
    }

    fun openTopics() {
        if (topicTarget != null || account.client !== accountClient) return
        val body = content.text.toString()
        val hash = typedTopicStart?.takeIf { body.getOrNull(it) == '#' && content.selection == TextRange(it + 1) }
        topicTarget = PinTopicTarget(body, hash?.let { TextRange(it, it + 1) } ?: content.selection)
        typedTopicStart = null
    }

    LaunchedEffect(topicTarget, searchTerm, topicRetry, draftKey, accountClient) {
        val target = topicTarget ?: return@LaunchedEffect
        if (searchTerm.isBlank()) return@LaunchedEffect
        delay(180.milliseconds)
        if (account.client !== accountClient || target.source != content.text.toString()) return@LaunchedEffect
        try {
            val request = PinTopicSuggestionRequest(title.text.toString(), compilePinMarkdown(target.source).html)
            val response = environment
                .postSigned(
                    "https://api.zhihu.com/content/publish/topics/recommend?" +
                        "recommend_type=pin&key_word=${searchTerm.encodeURLParameter(spaceToPlus = true)}",
                ) {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.raiseForStatus()
                .json<PinTopicSuggestionResponse>()
            if (account.client === accountClient && topicTarget == target && topicQuery.text.trim().toString() == searchTerm) topicSuggestions = response.data.list
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (account.client === accountClient && topicTarget == target && topicQuery.text.trim().toString() == searchTerm) {
                topicSuggestionError = error.message ?: error::class.simpleName ?: "未知错误"
            }
        }
    }

    fun showPreview() {
        if (account.client !== accountClient || isSubmitting || content.text.isBlank()) return
        val useWebView = settings.getBoolean(ARTICLE_USE_WEBVIEW_PREFERENCE_KEY, false)
        val markdownSnapshot = content.text.toString()
        coroutineScope.launch {
            if (account.client !== accountClient) return@launch
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            yield()
            if (account.client !== accountClient) return@launch
            previewUseWebView = useWebView
            previewHtml = null
            showPreviewSheet = true
            isPreviewLoading = true
            runCatching {
                compilePinMarkdown(markdownSnapshot).html
            }.onSuccess { html ->
                if (account.client === accountClient) previewHtml = html
            }.onFailure { e ->
                if (e is CancellationException) throw e
                if (account.client !== accountClient) return@onFailure
                errorDialogMessage = buildWriteOperationErrorMessage("生成预览失败", e)
                showPreviewSheet = false
            }
            if (account.client === accountClient) isPreviewLoading = false
        }
    }

    fun submitPin(publish: Boolean) {
        if (account.client !== accountClient || isSubmitting) return
        val draft = currentDraft()
        persistDraft(draft)
        if (draft.content.isBlank() && draft.images.isEmpty()) {
            userMessages.showShortMessage("想法内容为空")
            return
        }
        isSubmitting = true
        coroutineScope.launch {
            if (account.client !== accountClient) return@launch
            runCatching {
                val payload = buildPinContentPayload(draft.title.trim(), compilePinMarkdown(draft.content), draft.images)
                val xsrf = environment.authenticatedCookies()["_xsrf"]
                    ?: error("缺少 _xsrf Cookie，无法${if (publish) "发布" else "保存"}想法；请先确保已登录。")
                if (account.client !== accountClient) return@launch
                if (publish) {
                    val response = environment
                        .postSigned("https://www.zhihu.com/api/v4/content/publish") {
                            contentType(ContentType.Application.Json)
                            header(HttpHeaders.Referrer, "https://www.zhihu.com/")
                            header("x-xsrftoken", xsrf)
                            setBody(PublishPinRequest(data = payload))
                        }.raiseForStatus()
                        .json<DataHolder.ContentPublishResponse>()
                    if (response.message != "success") {
                        error("发布失败: ${response.message ?: "unknown"}\n$response")
                    }
                    parsePublishContentId(response.data?.result ?: error("发布成功但返回缺少 data.result"))
                        ?: error("发布成功但无法解析 publish.id")
                } else {
                    environment
                        .postSigned("https://api.zhihu.com/content/drafts") {
                            contentType(ContentType.Application.Json)
                            header(HttpHeaders.Referrer, "https://www.zhihu.com/")
                            header("x-xsrftoken", xsrf)
                            setBody(SavePinDraftRequest(data = payload))
                        }.raiseForStatus()
                    null
                }
            }.onSuccess { pinId ->
                if (account.client !== accountClient) return@onSuccess
                if (publish) {
                    draftPublished = true
                    if (saved == null || restored != null) settings.remove(draftKey)
                    localDraftSaved = false
                    userMessages.showShortMessage("发布成功")
                    navigator.onNavigateBack()
                    navigator.onNavigate(Pin(pinId ?: return@onSuccess))
                } else {
                    userMessages.showShortMessage("已保存草稿")
                }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                if (account.client !== accountClient) return@onFailure
                errorDialogMessage = buildWriteOperationErrorMessage(
                    title = if (publish) "发布失败" else "保存草稿失败",
                    throwable = e,
                )
            }
            if (account.client === accountClient) isSubmitting = false
        }
    }

    val launchImagePicker = if (isImagePickerSupported) {
        key(draftKey, accountClient) {
            rememberImagePickerLauncher { picked ->
                if (account.client !== accountClient || isSubmitting || isUploadingImage) return@rememberImagePickerLauncher
                if (images.size >= PIN_IMAGE_LIMIT) {
                    userMessages.showShortMessage("图片最多添加 $PIN_IMAGE_LIMIT 张")
                    return@rememberImagePickerLauncher
                }
                isUploadingImage = true
                coroutineScope.launch {
                    if (account.client !== accountClient) return@launch
                    runCatching {
                        uploadZhihuImage(
                            environment,
                            picked.bytes,
                            picked.mimeType,
                            picked.fileName,
                            ZhihuImageUploadSource.Pin,
                        )
                    }.onSuccess { uploaded ->
                        if (account.client !== accountClient) return@onSuccess
                        images = images + uploaded
                        persistDraft()
                        userMessages.showShortMessage("图片已添加")
                    }.onFailure { e ->
                        if (e is CancellationException) throw e
                        if (account.client !== accountClient) return@onFailure
                        if (e is UnknownImageFormatException) {
                            userMessages.showShortMessage(e.message ?: "无法识别图片格式，已取消上传")
                        } else {
                            errorDialogMessage = buildWriteOperationErrorMessage("插入图片失败", e)
                        }
                    }
                    if (account.client === accountClient) isUploadingImage = false
                }
            }
        }
    } else {
        null
    }

    WritePinScaffold(
        onBack = {
            persistDraft()
            navigator.onNavigateBack()
        },
        onPublish = { submitPin(publish = true) },
        isSubmitting = isSubmitting,
        isUploadingImage = isUploadingImage,
        onBoundsChanged = { floatingActionBounds = it },
        onPreview = ::showPreview,
        contentText = content.text.toString(),
        onAddImage = { launchImagePicker?.launch() },
        onSaveDraft = { submitPin(publish = false) },
    ) { innerPadding ->
        WritePinEditorFrame(
            innerPadding = innerPadding,
            localDraftSaved = localDraftSaved,
            header = { availableHeight ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = availableHeight / 2)
                        .verticalScroll(rememberScrollState()),
                ) {
                    WritePinHeader(
                        title = title,
                        images = images,
                        imageListBounds = imageListBounds,
                        floatingActionBounds = floatingActionBounds,
                        layoutDirection = layoutDirection,
                        density = density,
                        isSubmitting = isSubmitting,
                        onImageListBoundsChange = { imageListBounds = it },
                        onRemoveImage = { index ->
                            images = images.filterIndexed { itemIndex, _ -> itemIndex != index }
                            persistDraft()
                        },
                    )
                }
            },
            content = { availableHeight ->
                WritePinMarkdownEditor(
                    state = content,
                    topics = topics,
                    typedTopicStart = typedTopicStart,
                    isSubmitting = isSubmitting,
                    onAddTopic = ::openTopics,
                )
                topicTarget?.takeIf { it.source == content.text.toString() }?.let { target ->
                    val searchFocus = remember { FocusRequester() }
                    WritePinTopicSuggestions(
                        searchFocus = searchFocus,
                        availableHeight = availableHeight,
                        topicQuery = topicQuery,
                        topicSuggestionError = topicSuggestionError,
                        onRetry = { topicRetry++ },
                        topicSuggestions = topicSuggestions,
                        isSubmitting = isSubmitting,
                        onCancel = { topicTarget = null },
                        onSelectTopic = { topic ->
                            if (account.client === accountClient && !isSubmitting && topicTarget == target && target.source == content.text.toString()) {
                                val selected = PinContentTopicItem(topic.topicId.toString(), topic.name.removeSurrounding("#"))
                                if (topics.insert(content, target.selection, selected)) {
                                    topicTarget = null
                                    persistDraft()
                                } else {
                                    userMessages.showShortMessage("此话题无法插入")
                                }
                            }
                        },
                    )
                    LaunchedEffect(target) { searchFocus.requestFocus() }
                }
            },
        )
    }

    WritePinOverlays(
        showPreviewSheet = showPreviewSheet,
        previewSheetState = previewSheetState,
        previewUseWebView = previewUseWebView,
        isPreviewLoading = isPreviewLoading,
        previewHtml = previewHtml,
        onDismissPreview = {
            showPreviewSheet = false
            isPreviewLoading = false
        },
        errorDialogMessage = errorDialogMessage,
        onDismissError = { errorDialogMessage = null },
        onCopyError = { message ->
            copyToClipboard("write-pin-error", message)
            userMessages.showShortMessage("已复制错误信息")
        },
    )
}
