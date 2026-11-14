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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.account.accountIdentityKey
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.editor.PinContentTopicItem
import com.github.zly2006.zhihu.editor.PinContentTopicMarker
import com.github.zly2006.zhihu.editor.PinTopicSuggestion
import com.github.zly2006.zhihu.editor.PinTopicSuggestionRequest
import com.github.zly2006.zhihu.editor.PinTopicSuggestionResponse
import com.github.zly2006.zhihu.editor.PublishPinRequest
import com.github.zly2006.zhihu.editor.SavePinDraftRequest
import com.github.zly2006.zhihu.editor.UnknownImageFormatException
import com.github.zly2006.zhihu.editor.UploadedZhihuImage
import com.github.zly2006.zhihu.editor.ZhihuImageUploadSource
import com.github.zly2006.zhihu.editor.buildPinContentPayload
import com.github.zly2006.zhihu.editor.calculatePinHtmlTextLength
import com.github.zly2006.zhihu.editor.compilePinMarkdownToZhihuHtml
import com.github.zly2006.zhihu.editor.isImagePickerSupported
import com.github.zly2006.zhihu.editor.parsePublishContentId
import com.github.zly2006.zhihu.editor.rememberImagePickerLauncher
import com.github.zly2006.zhihu.editor.uploadZhihuImage
import com.github.zly2006.zhihu.markdown.rememberMarkdownImageModel
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.WritePin
import com.github.zly2006.zhihu.platform.rememberPlainTextClipboard
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppCard
import com.github.zly2006.zhihu.ui.components.AppCompactTopBar
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppFloatingToolbar
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.MarkdownShortcut
import com.github.zly2006.zhihu.ui.components.WriteContentMarkdownEditor
import com.github.zly2006.zhihu.ui.components.WriteContentPreviewSheet
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlin.time.Duration.Companion.milliseconds
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

const val WRITE_PIN_BACK_BUTTON_TAG = "write_pin_back_button"

const val WRITE_PIN_TITLE_TAG = "WritePinTitle"
const val WRITE_PIN_CONTENT_TAG = "WritePinContent"
const val WRITE_PIN_FAB_PREVIEW_TAG = "WritePinFabPreview"
const val WRITE_PIN_FAB_IMAGE_TAG = "WritePinFabImage"
const val WRITE_PIN_FAB_SAVE_TAG = "WritePinFabSave"
const val WRITE_PIN_IMAGE_LIST_TAG = "WritePinImageList"
const val WRITE_PIN_TOPIC_SUGGESTIONS_TAG = "WritePinTopicSuggestions"

private const val PIN_IMAGE_LIMIT = 9

@Serializable
private data class LocalPinDraft(
    val title: String = "",
    val content: String = "",
    val images: List<UploadedZhihuImage> = emptyList(),
    val topics: List<PinContentTopicMarker> = emptyList(),
    val titleSelectionStart: Int = 0,
    val titleSelectionEnd: Int = 0,
    val contentSelectionStart: Int = 0,
    val contentSelectionEnd: Int = 0,
)

internal data class ActivePinTopicQuery(
    val start: Int,
    val endExclusive: Int,
    val query: String,
)

internal fun activePinTopicQuery(
    value: TextFieldValue,
    selectedTopics: List<PinContentTopicMarker> = emptyList(),
): ActivePinTopicQuery? {
    if (value.selection.start != value.selection.end) return null
    val cursor = value.selection.end
    if (cursor !in 1..value.text.length) return null
    var hash = value.text.lastIndexOf('#', startIndex = cursor - 1)
    while (hash > 0 && !value.text[hash - 1].isWhitespace()) {
        hash = value.text.lastIndexOf('#', startIndex = hash - 1)
    }
    if (hash < 0 || hash >= cursor || value.text.substring(hash, cursor).contains('\n')) return null
    val query = value.text.substring(hash + 1, cursor)
    if (query.length > 50) return null
    if (selectedTopics.any { marker -> hash == marker.start }) {
        return null
    }
    return ActivePinTopicQuery(hash, cursor, query)
}

internal fun insertPinTopic(
    value: TextFieldValue,
    query: ActivePinTopicQuery,
    topic: PinTopicSuggestion,
): TextFieldValue {
    val insertion = "#${topic.name} "
    val text = value.text.replaceRange(query.start, query.endExclusive, insertion)
    return value.copy(
        text = text,
        selection = androidx.compose.ui.text
            .TextRange(query.start + insertion.length),
        composition = null,
    )
}

internal fun updatePinTopicMarkers(
    oldText: String,
    newText: String,
    markers: List<PinContentTopicMarker>,
): List<PinContentTopicMarker> {
    val commonPrefix = oldText.zip(newText).takeWhile { (old, new) -> old == new }.size
    val maxSuffix = minOf(oldText.length - commonPrefix, newText.length - commonPrefix)
    var commonSuffix = 0
    while (commonSuffix < maxSuffix &&
        oldText[oldText.lastIndex - commonSuffix] == newText[newText.lastIndex - commonSuffix]
    ) {
        commonSuffix++
    }
    val oldChangedEnd = oldText.length - commonSuffix
    val delta = newText.length - oldText.length
    return markers.mapNotNull { marker ->
        when {
            marker.endExclusive <= commonPrefix -> marker
            marker.start >= oldChangedEnd -> marker.copy(start = marker.start + delta, endExclusive = marker.endExclusive + delta)
            else -> null
        }
    }
}

private class PinTopicVisualTransformation(
    private val topics: List<PinContentTopicMarker>,
    private val color: androidx.compose.ui.graphics.Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styled = AnnotatedString.Builder(text)
        topics.forEach { marker ->
            if (marker.endExclusive <= text.length) {
                styled.addStyle(SpanStyle(color = color), marker.start, marker.endExclusive)
            }
        }
        return TransformedText(styled.toAnnotatedString(), OffsetMapping.Identity)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WritePinScreen(destination: WritePin = WritePin()) {
    val navigator = LocalNavigator.current
    val userMessages = rememberUserMessageSink()
    val coroutineScope = rememberCoroutineScope()
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

    val initialTopic = remember(destination.publishTopicId, destination.topicName) {
        destination.publishTopicId
            .takeIf(String::isNotBlank)
            ?.let { PinContentTopicItem(it, destination.topicName) }
    }
    val initialDraft = remember(settings, draftKey) {
        runCatching { ZhihuJson.json.decodeFromString<LocalPinDraft>(settings.getString(draftKey, "")) }
            .getOrNull()
            ?: LocalPinDraft(
                content = initialTopic?.let { "#${it.displayName} " }.orEmpty(),
                topics = initialTopic?.let { listOf(PinContentTopicMarker(it, 0, it.inlineMarker.length)) }.orEmpty(),
            )
    }
    var content by remember(draftKey) {
        mutableStateOf(
            TextFieldValue(
                initialDraft.content,
                selection = androidx.compose.ui.text
                    .TextRange(initialDraft.contentSelectionStart, initialDraft.contentSelectionEnd),
            ),
        )
    }
    var title by remember(draftKey) {
        mutableStateOf(
            TextFieldValue(
                initialDraft.title,
                selection = androidx.compose.ui.text
                    .TextRange(initialDraft.titleSelectionStart, initialDraft.titleSelectionEnd),
            ),
        )
    }
    var images by remember(draftKey) { mutableStateOf(initialDraft.images) }
    var selectedTopics by remember(draftKey) { mutableStateOf(initialDraft.topics) }
    var localDraftSaved by remember(draftKey) { mutableStateOf(settings.getStringOrNull(draftKey) != null) }
    var topicSuggestions by remember(draftKey, accountClient) { mutableStateOf<List<PinTopicSuggestion>>(emptyList()) }
    var topicSuggestionError by remember(draftKey, accountClient) { mutableStateOf<String?>(null) }
    var topicSearchJob by remember(draftKey, accountClient) { mutableStateOf<Job?>(null) }
    var isSubmitting by remember(accountClient) { mutableStateOf(false) }
    var isUploadingImage by remember(accountClient) { mutableStateOf(false) }
    var floatingActionBounds by remember { mutableStateOf(Rect.Zero) }
    var imageListBounds by remember { mutableStateOf(Rect.Zero) }
    var errorDialogMessage by remember(accountClient) { mutableStateOf<String?>(null) }
    var showPreviewSheet by remember(accountClient) { mutableStateOf(false) }
    val previewSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isPreviewLoading by remember(accountClient) { mutableStateOf(false) }
    var previewHtml by remember(accountClient) { mutableStateOf<String?>(null) }
    var previewMarkdown by remember(accountClient) { mutableStateOf<String?>(null) }
    var previewUseWebView by remember(accountClient) { mutableStateOf(false) }

    fun persistDraft() {
        if (account.client !== accountClient) return
        settings.putString(
            draftKey,
            ZhihuJson.json.encodeToString(
                LocalPinDraft(
                    title = title.text,
                    content = content.text,
                    images = images,
                    topics = selectedTopics,
                    titleSelectionStart = title.selection.start,
                    titleSelectionEnd = title.selection.end,
                    contentSelectionStart = content.selection.start,
                    contentSelectionEnd = content.selection.end,
                ),
            ),
        )
        localDraftSaved = true
    }

    DisposableEffect(draftKey, accountClient) {
        onDispose { topicSearchJob?.cancel() }
    }

    fun updateContent(newValue: TextFieldValue) {
        selectedTopics = updatePinTopicMarkers(content.text, newValue.text, selectedTopics)
        content = newValue
        persistDraft()
        topicSearchJob?.cancel()
        val query = activePinTopicQuery(newValue, selectedTopics)
        if (query == null || query.query.isBlank()) {
            topicSuggestions = emptyList()
            topicSuggestionError = null
            return
        }
        topicSearchJob = coroutineScope.launch {
            delay(180.milliseconds)
            if (account.client !== accountClient) return@launch
            val contentHtml = compilePinMarkdownToZhihuHtml(newValue.text, selectedTopics)
            topicSuggestionError = null
            try {
                val pinTopicSuggestion = environment
                    .postSigned(
                        "https://api.zhihu.com/content/publish/topics/recommend?" +
                            "recommend_type=pin&key_word=${query.query.encodeURLParameter(spaceToPlus = true)}",
                    ) {
                        contentType(ContentType.Application.Json)
                        setBody(PinTopicSuggestionRequest(title = title.text, content = contentHtml))
                    }.raiseForStatus()
                    .json<PinTopicSuggestionResponse>()
                if (account.client === accountClient && activePinTopicQuery(content, selectedTopics) == query) {
                    topicSuggestions = pinTopicSuggestion.data.list
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (account.client === accountClient && activePinTopicQuery(content, selectedTopics) == query) {
                    topicSuggestions = emptyList()
                    topicSuggestionError = error.message ?: error::class.simpleName ?: "未知错误"
                }
            }
        }
    }

    fun showPreview() {
        if (isSubmitting || content.text.isBlank()) return
        val useWebView = settings.getBoolean(ARTICLE_USE_WEBVIEW_PREFERENCE_KEY, false)
        val markdownSnapshot = content.text
        val topicsSnapshot = selectedTopics
        coroutineScope.launch {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            yield()
            previewUseWebView = useWebView
            previewMarkdown = markdownSnapshot
            previewHtml = null
            showPreviewSheet = true
            if (!useWebView) {
                isPreviewLoading = false
                return@launch
            }
            isPreviewLoading = true
            runCatching {
                compilePinMarkdownToZhihuHtml(markdownSnapshot, topicsSnapshot)
            }.onSuccess { html ->
                previewHtml = html
            }.onFailure { e ->
                if (e is CancellationException) throw e
                if (account.client !== accountClient) return@onFailure
                errorDialogMessage = buildWriteOperationErrorMessage("生成预览失败", e)
                showPreviewSheet = false
            }
            isPreviewLoading = false
        }
    }

    fun submitPin(publish: Boolean) {
        persistDraft()
        val markdownSnapshot = content.text
        val imagesSnapshot = images
        val topicsSnapshot = selectedTopics
        if (markdownSnapshot.isBlank() && imagesSnapshot.isEmpty()) {
            userMessages.showShortMessage("想法内容为空")
            return
        }
        if (account.client !== accountClient || isSubmitting) return
        isSubmitting = true
        coroutineScope.launch {
            runCatching {
                val html = compilePinMarkdownToZhihuHtml(markdownSnapshot, topicsSnapshot)
                val textLength = calculatePinHtmlTextLength(html)
                val topics = topicsSnapshot.map(PinContentTopicMarker::topic).distinctBy(PinContentTopicItem::topicId)
                val payload = buildPinContentPayload(
                    title = title.text.trim(),
                    html = html,
                    textLength = textLength,
                    images = imagesSnapshot,
                    topics = topics,
                )
                val xsrf = environment.authenticatedCookies()["_xsrf"]
                    ?: error("缺少 _xsrf Cookie，无法${if (publish) "发布" else "保存"}想法；请先确保已登录。")
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
                    settings.remove(draftKey)
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
            isSubmitting = false
        }
    }

    val launchImagePicker = if (isImagePickerSupported) {
        rememberImagePickerLauncher { picked ->
            if (account.client !== accountClient || isSubmitting || isUploadingImage) return@rememberImagePickerLauncher
            if (images.size >= PIN_IMAGE_LIMIT) {
                userMessages.showShortMessage("图片最多添加 $PIN_IMAGE_LIMIT 张")
                return@rememberImagePickerLauncher
            }
            isUploadingImage = true
            coroutineScope.launch {
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
                isUploadingImage = false
            }
        }
    } else {
        null
    }

    AppScaffold(
        topBar = {
            val textMeasurer = rememberTextMeasurer()
            val titleStyle = if (settingSkinIsMiuix()) LocalAppSkinTokens.current.title3 else MaterialTheme.typography.titleLarge
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val requiredWidth = with(LocalDensity.current) {
                    (
                        textMeasurer.measure("发想法", style = titleStyle, softWrap = false).size.width +
                            textMeasurer.measure("发布", style = if (settingSkinIsMiuix()) LocalAppSkinTokens.current.main else MaterialTheme.typography.labelLarge, softWrap = false).size.width
                    ).toDp()
                } + AppDimens.TouchTarget + AppDimens.PageHorizontalPadding * 2 + AppDimens.Space24 * 2
                val separateAction = maxWidth < requiredWidth
                Column {
                    AppCompactTopBar(
                        title = "发想法",
                        onBack = navigator.onNavigateBack,
                        backTag = WRITE_PIN_BACK_BUTTON_TAG,
                        actions = {
                            if (!separateAction) {
                                AppButton(onClick = { submitPin(publish = true) }, enabled = !isSubmitting && !isUploadingImage) {
                                    if (isSubmitting) AppSpinner(size = AppDimens.IconSizeCompact) else Text("发布")
                                }
                            }
                        },
                    )
                    if (separateAction) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space4), horizontalArrangement = Arrangement.End) {
                            AppButton(onClick = { submitPin(publish = true) }, enabled = !isSubmitting && !isUploadingImage) {
                                if (isSubmitting) AppSpinner(size = AppDimens.IconSizeCompact) else Text("发布")
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(vertical = AppDimens.Space8)
                    .onGloballyPositioned { floatingActionBounds = it.boundsInRoot() },
                contentAlignment = Alignment.Center,
            ) {
                AppFloatingToolbar(modifier = Modifier.testTag("write_pin_action_toolbar")) {
                    AppIconButton(
                        onClick = ::showPreview,
                        enabled = !isSubmitting && content.text.isNotBlank(),
                        modifier = Modifier.size(AppDimens.TouchTarget).testTag(WRITE_PIN_FAB_PREVIEW_TAG),
                    ) {
                        Icon(Icons.Filled.Visibility, contentDescription = "预览")
                    }
                    if (isImagePickerSupported) {
                        AppIconButton(
                            onClick = { launchImagePicker?.launch() },
                            enabled = !isSubmitting && !isUploadingImage,
                            modifier = Modifier
                                .size(AppDimens.TouchTarget)
                                .testTag(WRITE_PIN_FAB_IMAGE_TAG)
                                .semantics { contentDescription = "插入图片" },
                        ) {
                            if (isUploadingImage) {
                                AppSpinner(modifier = Modifier, size = AppDimens.IconSizeCompact)
                            } else {
                                Icon(Icons.Filled.Image, contentDescription = "插入图片")
                            }
                        }
                    }
                    AppTextButton(
                        text = "草稿",
                        onClick = { submitPin(publish = false) },
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .widthIn(min = AppDimens.TouchTarget)
                            .testTag(WRITE_PIN_FAB_SAVE_TAG)
                            .semantics { contentDescription = "保存草稿" },
                    )
                }
            }
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .imePadding()
                .padding(horizontal = AppDimens.PageHorizontalPadding),
        ) {
            val availableHeight = maxHeight
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = availableHeight / 2)
                        .verticalScroll(rememberScrollState()),
                ) {
                    BasicTextField(
                        value = title,
                        onValueChange = { newValue ->
                            title = newValue
                            persistDraft()
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(WRITE_PIN_TITLE_TAG)
                                .semantics { contentDescription = "想法标题（可选）" },
                        enabled = !isSubmitting,
                        textStyle =
                            MaterialTheme.typography.titleLarge.copy(
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp, bottom = 12.dp),
                            ) {
                                if (title.text.isEmpty()) {
                                    Text(
                                        text = "标题",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )
                    if (images.isNotEmpty()) {
                        val attachmentEndPadding = if (
                            imageListBounds.top < floatingActionBounds.bottom && imageListBounds.bottom > floatingActionBounds.top
                        ) {
                            val overlapWidth = if (layoutDirection == LayoutDirection.Ltr) {
                                imageListBounds.right - floatingActionBounds.left
                            } else {
                                floatingActionBounds.right - imageListBounds.left
                            }
                            with(density) { overlapWidth.coerceAtLeast(0f).toDp() } + AppDimens.Space8
                        } else {
                            AppDimens.ElevationNone
                        }
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = AppDimens.Space8)
                                    .onGloballyPositioned { imageListBounds = it.boundsInRoot() }
                                    .testTag(WRITE_PIN_IMAGE_LIST_TAG),
                            verticalArrangement = Arrangement.spacedBy(AppDimens.Space8),
                        ) {
                            Text(
                                text = "图片 ${images.size}/$PIN_IMAGE_LIMIT",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(end = attachmentEndPadding)
                                        .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8),
                            ) {
                                images.forEachIndexed { index, image ->
                                    AppCard(modifier = Modifier.width(AppDimens.Space120)) {
                                        AsyncImage(
                                            model = rememberMarkdownImageModel(image.url),
                                            contentDescription = "想法图片 ${index + 1}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxWidth().height(AppDimens.Space64),
                                        )
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = AppDimens.Space8, vertical = AppDimens.Space4),
                                            verticalArrangement = Arrangement.spacedBy(AppDimens.Space4),
                                        ) {
                                            Text(
                                                text = "图片 ${index + 1}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            Text(
                                                text = "${image.rawWidth} x ${image.rawHeight}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        AppTextButton(
                                            text = "删除图片 ${index + 1}",
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = {
                                                images = images.filterIndexed { itemIndex, _ -> itemIndex != index }
                                                persistDraft()
                                            },
                                            enabled = !isSubmitting,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (localDraftSaved) {
                    Text(
                        text = "草稿保存在本机",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = AppDimens.Space4),
                    )
                }
                WriteContentMarkdownEditor(
                    value = content,
                    onValueChange = ::updateContent,
                    placeholder = "分享你此刻的想法... 输入 # 添加话题",
                    contentTag = WRITE_PIN_CONTENT_TAG,
                    enabled = !isSubmitting,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    topPadding = AppDimens.Space4,
                    bottomPadding = with(density) { floatingActionBounds.height.toDp() } + AppDimens.Space8,
                    visualTransformation = PinTopicVisualTransformation(selectedTopics, MaterialTheme.colorScheme.primary),
                    extraShortcuts = listOf(MarkdownShortcut.Topic),
                )
                if (topicSuggestions.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().testTag(WRITE_PIN_TOPIC_SUGGESTIONS_TAG),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 0.dp,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(
                            Modifier
                                .heightIn(max = availableHeight / 3)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            topicSuggestions.forEach { topic ->
                                AppTextButton(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .testTag("write_pin_topic_suggestion_${topic.topicId}"),
                                    onClick = {
                                        val query = activePinTopicQuery(content) ?: return@AppTextButton
                                        val previousContent = content
                                        val insertedContent = insertPinTopic(previousContent, query, topic)
                                        val shiftedTopics =
                                            updatePinTopicMarkers(
                                                previousContent.text,
                                                insertedContent.text,
                                                selectedTopics,
                                            )
                                        content = insertedContent
                                        val selected = PinContentTopicItem(topic.topicId.toString(), topic.name)
                                        val start = query.start
                                        val marker = PinContentTopicMarker(selected, start, start + selected.inlineMarker.length)
                                        if (shiftedTopics.none { it.start == marker.start }) {
                                            selectedTopics = shiftedTopics + marker
                                        }
                                        persistDraft()
                                        topicSuggestions = emptyList()
                                    },
                                ) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text("#${topic.name}")
                                        if (topic.discussCount.isNotBlank()) {
                                            Text(topic.discussCount, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                topicSuggestionError?.let { message ->
                    AppTextButton(
                        text = "话题推荐加载失败：$message，点击重试",
                        onClick = { updateContent(content) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (showPreviewSheet) {
        WriteContentPreviewSheet(
            sheetState = previewSheetState,
            useWebView = previewUseWebView,
            isLoading = isPreviewLoading,
            html = previewHtml,
            markdown = previewMarkdown,
            onDismissRequest = {
                showPreviewSheet = false
                isPreviewLoading = false
            },
        )
    }

    WriteOperationErrorDialog(
        message = errorDialogMessage,
        onDismissRequest = { errorDialogMessage = null },
        onCopy = { message ->
            copyToClipboard("write-pin-error", message)
            userMessages.showShortMessage("已复制错误信息")
        },
    )
}
