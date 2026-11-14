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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.util.jsonObject
import com.github.zly2006.zhihu.editor.PatchDraftRequest
import com.github.zly2006.zhihu.editor.PatchDraftSettings
import com.github.zly2006.zhihu.editor.PublishAnswerData
import com.github.zly2006.zhihu.editor.PublishAnswerRequest
import com.github.zly2006.zhihu.editor.PublishContentsTables
import com.github.zly2006.zhihu.editor.PublishDraft
import com.github.zly2006.zhihu.editor.PublishExtraInfo
import com.github.zly2006.zhihu.editor.PublishHybrid
import com.github.zly2006.zhihu.editor.PublishTrace
import com.github.zly2006.zhihu.editor.UnknownImageFormatException
import com.github.zly2006.zhihu.editor.ZhihuImageUploadSource
import com.github.zly2006.zhihu.editor.buildPcBusinessParams
import com.github.zly2006.zhihu.editor.compileMdToZhihuHtml
import com.github.zly2006.zhihu.editor.isImagePickerSupported
import com.github.zly2006.zhihu.editor.newPublishTraceId
import com.github.zly2006.zhihu.editor.parsePublishContentId
import com.github.zly2006.zhihu.editor.rememberImagePickerLauncher
import com.github.zly2006.zhihu.editor.uploadZhihuImage
import com.github.zly2006.zhihu.editor.zhihuHtmlToEditorMarkdown
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.WriteAnswer
import com.github.zly2006.zhihu.platform.rememberPlainTextClipboard
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppFloatingToolbar
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.AppSegmentedControl
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.AppTopBar
import com.github.zly2006.zhihu.ui.components.MyModalBottomSheet
import com.github.zly2006.zhihu.ui.components.RichEditorState
import com.github.zly2006.zhihu.ui.components.SettingItemWithSwitch
import com.github.zly2006.zhihu.ui.components.WriteContentMarkdownEditor
import com.github.zly2006.zhihu.ui.components.WriteContentPreviewSheet
import com.github.zly2006.zhihu.ui.components.WriteContentRichEditor
import com.github.zly2006.zhihu.ui.components.isRichEditorSupported
import com.github.zly2006.zhihu.ui.components.replaceSelection
import com.github.zly2006.zhihu.util.json
import com.github.zly2006.zhihu.util.raiseForStatus
import com.github.zly2006.zhihu.util.signZhihuFetchRequest
import com.github.zly2006.zhihu.viewmodel.fetchContentDetail
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

const val WRITE_ANSWER_BACK_BUTTON_TAG = "write_answer_back_button"

const val WRITE_ANSWER_CONTENT_TAG = "WriteAnswerContent"
const val WRITE_ANSWER_FAB_PREVIEW_TAG = "WriteAnswerFabPreview"
const val WRITE_ANSWER_FAB_IMAGE_TAG = "WriteAnswerFabImage"
const val WRITE_ANSWER_FAB_SAVE_TAG = "WriteAnswerFabSave"

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun WriteAnswerScreen(
    destination: WriteAnswer,
) {
    val navigator = LocalNavigator.current
    val userMessages = rememberUserMessageSink()
    val environment = rememberPaginationEnvironment(false)
    val coroutineScope = rememberCoroutineScope()
    val copyToClipboard = rememberPlainTextClipboard()
    val settings = rememberSettingsStore()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var editorActionsVisible by remember { mutableStateOf(true) }
    val density = LocalDensity.current
    val actionVisibilityThreshold = with(density) { AppDimens.Space12.roundToPx() }
    var actionToolbarHeight by remember { mutableStateOf(AppDimens.ElevationNone) }
    var accumulatedEditorScroll by remember { mutableStateOf(0f) }

    val account = rememberZhihuAccountStore()
    val accountClient = account.client
    val draftKey = "creation.answer.${account.session.profile?.id.orEmpty()}.${destination.questionId}"
    var content by remember(draftKey) {
        val savedHtml = settings.getString("$draftKey.html", "")
        mutableStateOf(
            TextFieldValue(
                if (!isRichEditorSupported && savedHtml.isNotBlank() && settings.getBoolean("$draftKey.rich", true)) {
                    zhihuHtmlToEditorMarkdown(savedHtml)
                } else {
                    settings.getString(draftKey, "")
                },
            ),
        )
    }
    var tocEnabled by remember(draftKey) { mutableStateOf(settings.getBoolean("$draftKey.toc", false)) }
    var richMode by remember(draftKey) {
        mutableStateOf(
            isRichEditorSupported &&
                settings.getBoolean("$draftKey.rich", true) &&
                (settings.getString("$draftKey.html", "").isNotBlank() || content.text.isBlank()),
        )
    }
    val richEditor = remember(draftKey) { RichEditorState(settings.getString("$draftKey.html", "")) }
    var isEditorConverting by remember(accountClient) { mutableStateOf(false) }
    val hasContent by remember(draftKey) { derivedStateOf { if (richMode) richEditor.hasContent else content.text.isNotBlank() } }
    val editorAvailable by remember(draftKey) { derivedStateOf { !isEditorConverting && (!richMode || richEditor.isReady) } }
    var initialContentLoaded by remember(draftKey, accountClient) { mutableStateOf(false) }
    var draftLoadAttempt by remember(draftKey, accountClient) { mutableStateOf(0) }
    var lastAutoSavedText by remember(draftKey, accountClient) { mutableStateOf<Pair<String, Boolean>?>(null) }
    var remoteSettings by remember(draftKey, accountClient) { mutableStateOf<kotlinx.serialization.json.JsonObject?>(null) }
    var draftSaveStatus by remember(draftKey, accountClient) { mutableStateOf("") }
    var isSubmitting by remember(accountClient) { mutableStateOf(false) }
    var existingAnswerId by remember(accountClient) { mutableStateOf<Long?>(null) }
    var isDetecting by remember(accountClient) { mutableStateOf(false) }
    var isLoadingExistingAnswer by remember(accountClient) { mutableStateOf(false) }
    var isUploadingImage by remember(accountClient) { mutableStateOf(false) }
    var errorDialogMessage by remember(accountClient) { mutableStateOf<String?>(null) }
    var showSettingsSheet by remember(accountClient) { mutableStateOf(false) }
    var showPreviewSheet by remember(accountClient) { mutableStateOf(false) }
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val previewSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isPreviewLoading by remember(accountClient) { mutableStateOf(false) }
    var previewHtml by remember(accountClient) { mutableStateOf<String?>(null) }
    var previewMarkdown by remember(accountClient) { mutableStateOf<String?>(null) }
    var previewUseWebView by remember(accountClient) { mutableStateOf(false) }

    suspend fun ensureAnswerId(): Long? {
        val cached = existingAnswerId
        if (cached != null) return cached
        if (account.client !== accountClient) throw kotlinx.coroutines.CancellationException("账号已切换")
        val element = accountClient.withAuthenticatedClient { client, cookies ->
            check(!cookies["d_c0"].isNullOrBlank()) { "尚未登录，无法确认已有回答" }
            accountClient.get("https://api.zhihu.com/questions/${destination.questionId}") {
                url.parameters["include"] = "relationship,relationship.my_answer"
            }.jsonObject()
        } ?: error("无法确认已有回答，请重试加载草稿")
        currentCoroutineContext().ensureActive()
        if (account.client !== accountClient) throw kotlinx.coroutines.CancellationException("账号已切换")
        val relationship = ZhihuJson.decodeJson(DataHolder.QuestionRelationshipApiResponse.serializer(), element).relationship
            ?: error("已有回答状态缺失，请重试加载草稿")
        val answerId = relationship.myAnswer
            ?.takeUnless { it.isDeleted == true }
            ?.answerId
            ?.toLongOrNull()
        existingAnswerId = answerId
        return answerId
    }

    fun showPreview() {
        if (isSubmitting || !hasContent) return
        if (richMode) {
            coroutineScope.launch {
                try {
                    val html = richEditor.snapshotHtml()
                    if (account.client !== accountClient) return@launch
                    previewHtml = html
                    previewMarkdown = null
                    previewUseWebView = false
                    showPreviewSheet = true
                } catch (error: kotlin.coroutines.cancellation.CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (account.client !== accountClient) return@launch
                    errorDialogMessage = buildWriteOperationErrorMessage("生成预览失败", error)
                }
            }
            return
        }
        val useWebView = settings.getBoolean(ARTICLE_USE_WEBVIEW_PREFERENCE_KEY, false)
        val markdownSnapshot = content.text
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
                compileMdToZhihuHtml(markdown = markdownSnapshot)
            }.onSuccess { html ->
                previewHtml = html
            }.onFailure { e ->
                errorDialogMessage = buildWriteOperationErrorMessage("生成预览失败", e)
                showPreviewSheet = false
            }
            isPreviewLoading = false
        }
    }

    fun submitAnswer(publish: Boolean, silent: Boolean = false) {
        if (publish && !hasContent) {
            userMessages.showShortMessage("内容为空")
            return
        }
        if (account.client !== accountClient || isSubmitting || isUploadingImage || !editorAvailable || !initialContentLoaded) return
        val snapshot = (if (richMode) richEditor.html else content.text) to tocEnabled
        isSubmitting = true
        coroutineScope.launch {
            runCatching {
                val html = if (richMode) richEditor.snapshotHtml() else compileMdToZhihuHtml(markdown = snapshot.first)
                currentCoroutineContext().ensureActive()
                if (account.client !== accountClient) throw kotlinx.coroutines.CancellationException("账号已切换")
                val answerId = ensureAnswerId()
                val client = accountClient.httpClient()
                val cookies = accountClient.load().cookies
                val xsrf = cookies["_xsrf"]
                    ?: error("缺少 _xsrf Cookie，无法写入回答草稿；请先确保已登录。")
                client
                    .post("https://www.zhihu.com/api/v4/questions/${destination.questionId}/draft") {
                        contentType(ContentType.Application.Json)
                        header(
                            HttpHeaders.Referrer,
                            "https://www.zhihu.com/question/${destination.questionId}/answer/${answerId ?: ""}",
                        )
                        header("x-xsrftoken", xsrf)
                        val draftBody = ZhihuJson.json.encodeToJsonElement(
                            PatchDraftRequest.serializer(),
                            PatchDraftRequest(content = html, settings = PatchDraftSettings(tableOfContentsEnabled = snapshot.second)),
                        ) as JsonObject
                        val savedSettings = remoteSettings
                        setBody(
                            JsonObject(
                                draftBody + (
                                    "settings" to buildJsonObject {
                                        (savedSettings ?: draftBody.getValue("settings") as JsonObject).forEach { (key, value) -> put(key, value) }
                                        put("table_of_contents_enabled", snapshot.second)
                                    }
                                ),
                            ),
                        )
                        signZhihuFetchRequest(cookies)
                    }.raiseForStatus()
                currentCoroutineContext().ensureActive()
                if (account.client !== accountClient) throw kotlinx.coroutines.CancellationException("账号已切换")
                if (publish) {
                    val response = client
                        .post("https://www.zhihu.com/api/v4/content/publish") {
                            contentType(ContentType.Application.Json)
                            header("x-xsrftoken", xsrf)
                            setBody(
                                PublishAnswerRequest(
                                    data = PublishAnswerData(
                                        publish = PublishTrace(traceId = newPublishTraceId()),
                                        draft = PublishDraft(
                                            isPublished = answerId != null,
                                            contentId = answerId?.toString(),
                                        ),
                                        extraInfo = PublishExtraInfo(
                                            questionId = destination.questionId.toString(),
                                            pcBusinessParams = buildPcBusinessParams(snapshot.second),
                                        ),
                                        hybrid = PublishHybrid(html),
                                        contentsTables = PublishContentsTables(snapshot.second),
                                    ),
                                ),
                            )
                            signZhihuFetchRequest(cookies)
                        }.raiseForStatus()
                        .json<DataHolder.ContentPublishResponse>()
                    if (response.message != "success") {
                        if (response.code == 103003) {
                            error(response.message ?: "已回答过该问题，创建回答失败")
                        }
                        error("发布失败: ${response.message ?: "unknown"}\n$response")
                    }
                    parsePublishContentId(response.data?.result ?: error("发布成功但返回缺少 data.result"))
                        ?: error("发布成功但无法解析 publish.id")
                } else {
                    null
                }
            }.onSuccess { answerId ->
                currentCoroutineContext().ensureActive()
                if (account.client !== accountClient) return@onSuccess
                lastAutoSavedText = snapshot
                draftSaveStatus = "草稿已保存"
                if (publish) {
                    settings.remove(draftKey)
                    settings.remove("$draftKey.toc")
                    settings.remove("$draftKey.html")
                    settings.remove("$draftKey.rich")
                    userMessages.showShortMessage("发布成功")
                    navigator.onNavigateBack()
                    navigator.onNavigate(Article(type = ArticleType.Answer, id = answerId ?: return@onSuccess))
                } else if (!silent) {
                    userMessages.showShortMessage("已保存草稿")
                }
            }.onFailure { e ->
                if (e is kotlin.coroutines.cancellation.CancellationException) throw e
                if (account.client !== accountClient) return@onFailure
                draftSaveStatus = "云端保存失败，内容仍保存在本机"
                if (!silent) {
                    errorDialogMessage = buildWriteOperationErrorMessage(
                        title = if (publish) "发布失败" else "保存草稿失败",
                        throwable = e,
                    )
                }
            }
            isSubmitting = false
        }
    }

    val launchImagePicker = if (isImagePickerSupported) {
        rememberImagePickerLauncher { picked ->
            if (account.client !== accountClient || isSubmitting || isUploadingImage) return@rememberImagePickerLauncher
            isUploadingImage = true
            coroutineScope.launch {
                runCatching {
                    uploadZhihuImage(
                        environment,
                        picked.bytes,
                        picked.mimeType,
                        picked.fileName,
                        ZhihuImageUploadSource.Answer,
                    )
                }.onSuccess { uploaded ->
                    currentCoroutineContext().ensureActive()
                    if (account.client !== accountClient) return@onSuccess
                    val title = buildString {
                        append("zhimg:w=").append(uploaded.rawWidth)
                        append(";h=").append(uploaded.rawHeight)
                        uploaded.watermark?.let { append(";wm=").append(if (it) 1 else 0) }
                        if (uploaded.watermark == true) {
                            uploaded.watermarkUrl?.let { append(";wmsrc=").append(it) }
                        }
                    }
                    val alt = picked.fileName
                        ?.substringBeforeLast('.')
                        ?.takeIf { it.isNotBlank() }
                        .orEmpty()
                    val snippet = "![$alt](${uploaded.url} \"$title\")"
                    if (richMode) {
                        richEditor.insertImage(uploaded.url, uploaded.rawWidth, uploaded.rawHeight)
                    } else {
                        content = content.replaceSelection(snippet, cursorOffsetInInsert = snippet.length)
                    }
                    settings.putString(draftKey, content.text)
                    userMessages.showShortMessage("图片已插入")
                }.onFailure { e ->
                    if (e is kotlin.coroutines.cancellation.CancellationException) throw e
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

    LaunchedEffect(draftKey, accountClient, draftLoadAttempt) {
        if (account.client !== accountClient) return@LaunchedEffect
        initialContentLoaded = false
        isDetecting = true
        try {
            existingAnswerId = ensureAnswerId()
            val draft = accountClient.withAuthenticatedClient { client, cookies ->
                accountClient.get("https://www.zhihu.com/api/v4/questions/${destination.questionId}/draft").jsonObject()
            } ?: error("云端草稿未加载成功，请重试")
            currentCoroutineContext().ensureActive()
            if (account.client !== accountClient) return@LaunchedEffect
            val remoteHtml = draft["editable_content"]?.jsonPrimitive?.contentOrNull
                ?: draft["content"]?.jsonPrimitive?.contentOrNull
            remoteSettings = draft["settings"] as? JsonObject
            lastAutoSavedText = (if (richMode) remoteHtml.orEmpty() else zhihuHtmlToEditorMarkdown(remoteHtml.orEmpty())) to
                (remoteSettings?.get("table_of_contents_enabled")?.jsonPrimitive?.booleanOrNull ?: false)
            if (!hasContent) {
                tocEnabled = remoteSettings?.get("table_of_contents_enabled")?.jsonPrimitive?.booleanOrNull ?: false
                if (!remoteHtml.isNullOrBlank()) {
                    if (richMode) {
                        richEditor.loadHtml(remoteHtml)
                    } else {
                        content = TextFieldValue(zhihuHtmlToEditorMarkdown(remoteHtml))
                    }
                    draftSaveStatus = "已恢复云端草稿"
                } else {
                    draftSaveStatus = "云端草稿已加载"
                }
            }
            if (!hasContent) {
                existingAnswerId?.let { answerId ->
                    isLoadingExistingAnswer = true
                    val answer = environment.fetchContentDetail(Article(type = ArticleType.Answer, id = answerId)) as? DataHolder.Answer
                        ?: error("已有回答未加载成功，请重试")
                    currentCoroutineContext().ensureActive()
                    if (account.client !== accountClient) return@LaunchedEffect
                    if (!hasContent) {
                        tocEnabled = answer.settings?.tableOfContents?.enabled ?: false
                        if (richMode) {
                            richEditor.loadHtml(answer.editableContent ?: answer.content)
                        } else {
                            content = TextFieldValue(zhihuHtmlToEditorMarkdown(answer.editableContent ?: answer.content))
                        }
                    }
                }
            }
            initialContentLoaded = true
        } catch (error: kotlin.coroutines.cancellation.CancellationException) {
            throw error
        } catch (error: Exception) {
            if (account.client !== accountClient) return@LaunchedEffect
            draftSaveStatus = "云端草稿加载失败，本机内容仍可编辑；重试成功前不会写入云端"
        } finally {
            isLoadingExistingAnswer = false
            isDetecting = false
        }
    }

    LaunchedEffect(draftKey, accountClient) {
        snapshotFlow { Triple(if (richMode) richEditor.html else content.text, tocEnabled, initialContentLoaded && editorAvailable) }
            .debounce(8_000)
            .collect { (text, toc, loaded) ->
                val saved = lastAutoSavedText
                if (!loaded || text to toc == saved || (!hasContent && saved?.first?.isBlank() == true && toc == saved.second)) return@collect
                submitAnswer(publish = false, silent = true)
            }
    }

    LaunchedEffect(draftKey, accountClient, richEditor) {
        snapshotFlow { richEditor.html }.collect { if (account.client === accountClient && richMode) settings.putString("$draftKey.html", it) }
    }

    AppScaffold(
        topBar = {
            AppTopBar(
                title = {
                    Text(
                        text =
                            when {
                                isDetecting || isLoadingExistingAnswer -> "正在检测已有回答..."
                                existingAnswerId != null -> "编辑已有回答"
                                else -> "写回答"
                            },
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                },
                onBack = {
                    settings.putString(draftKey, content.text)
                    navigator.onNavigateBack()
                },
                backTag = WRITE_ANSWER_BACK_BUTTON_TAG,
                actions = {
                    AppIconButton(
                        onClick = { showSettingsSheet = true },
                        enabled = !isSubmitting,
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "回答设置")
                    }
                    AppButton(
                        onClick = { submitAnswer(publish = true) },
                        enabled = initialContentLoaded && !isSubmitting && !isUploadingImage && editorAvailable && hasContent,
                    ) {
                        if (isSubmitting) {
                            AppSpinner(modifier = Modifier, size = 18.dp)
                        } else {
                            Text("发布")
                        }
                    }
                },
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .heightIn(min = actionToolbarHeight + AppDimens.Space16)
                    .padding(vertical = AppDimens.Space8),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = editorActionsVisible,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                ) {
                    AppFloatingToolbar(
                        modifier = Modifier
                            .testTag("write_answer_action_toolbar")
                            .onSizeChanged { actionToolbarHeight = with(density) { it.height.toDp() } },
                    ) {
                        AppIconButton(
                            onClick = ::showPreview,
                            enabled = !isSubmitting && hasContent && editorAvailable,
                            modifier = Modifier.size(AppDimens.TouchTarget).testTag(WRITE_ANSWER_FAB_PREVIEW_TAG),
                        ) {
                            Icon(Icons.Filled.Visibility, contentDescription = "预览")
                        }
                        if (isImagePickerSupported) {
                            AppIconButton(
                                onClick = { launchImagePicker?.launch() },
                                enabled = !isSubmitting && !isUploadingImage && editorAvailable,
                                modifier = Modifier
                                    .size(AppDimens.TouchTarget)
                                    .testTag(WRITE_ANSWER_FAB_IMAGE_TAG)
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
                            onClick = { submitAnswer(publish = false) },
                            enabled = initialContentLoaded && !isSubmitting && !isUploadingImage && editorAvailable,
                            modifier = Modifier
                                .widthIn(min = AppDimens.TouchTarget)
                                .testTag(WRITE_ANSWER_FAB_SAVE_TAG)
                                .semantics { contentDescription = "保存草稿" },
                        )
                    }
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
                    destination.questionTitle.takeIf(String::isNotBlank)?.let { questionTitle ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 12.dp),
                        ) {
                            Text(
                                text = "正在回答",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = questionTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            )
                        }
                    }
                    if (isRichEditorSupported) {
                        AppSegmentedControl(
                            options = listOf("图文", "Markdown"),
                            selectedIndex = if (richMode) 0 else 1,
                            onSelect = { index ->
                                val nextRich = index == 0
                                if (nextRich != richMode && !isEditorConverting) {
                                    coroutineScope.launch {
                                        isEditorConverting = true
                                        try {
                                            if (nextRich) {
                                                richEditor.loadHtml(compileMdToZhihuHtml(content.text))
                                            } else {
                                                val markdown = zhihuHtmlToEditorMarkdown(richEditor.snapshotHtml())
                                                if (account.client !== accountClient) return@launch
                                                content = TextFieldValue(markdown)
                                            }
                                            if (account.client !== accountClient) return@launch
                                            richMode = nextRich
                                            settings.putBoolean("$draftKey.rich", richMode)
                                            settings.putString(draftKey, content.text)
                                        } catch (error: kotlin.coroutines.cancellation.CancellationException) {
                                            throw error
                                        } catch (error: Exception) {
                                            if (account.client !== accountClient) return@launch
                                            errorDialogMessage = buildWriteOperationErrorMessage("切换编辑模式失败", error)
                                        } finally {
                                            isEditorConverting = false
                                        }
                                    }
                                }
                            },
                            enabled = !isSubmitting && !isUploadingImage && editorAvailable,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (draftSaveStatus.isNotBlank()) {
                        Text(
                            draftSaveStatus,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = AppDimens.Space8).semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    if (!initialContentLoaded && !isDetecting) {
                        AppTextButton(
                            text = "重试加载草稿",
                            onClick = { draftLoadAttempt++ },
                        )
                    }
                }
                if (richMode) {
                    WriteContentRichEditor(
                        state = richEditor,
                        placeholder = "请输入图文回答内容……",
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth().weight(1f).testTag(WRITE_ANSWER_CONTENT_TAG),
                        onVerticalScroll = { delta -> editorActionsVisible = delta >= 0f },
                    )
                } else {
                    WriteContentMarkdownEditor(
                        value = content,
                        onValueChange = { newValue ->
                            content = newValue
                            settings.putString(draftKey, newValue.text)
                        },
                        placeholder = "请输入图文回答内容……",
                        contentTag = WRITE_ANSWER_CONTENT_TAG,
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        bottomPadding = actionToolbarHeight + AppDimens.Space8,
                        onVerticalScroll = { delta ->
                            if (accumulatedEditorScroll != 0f && (accumulatedEditorScroll > 0f) != (delta > 0f)) {
                                accumulatedEditorScroll = delta
                            } else {
                                accumulatedEditorScroll += delta
                            }
                            when {
                                accumulatedEditorScroll <= -actionVisibilityThreshold -> {
                                    editorActionsVisible = false
                                    accumulatedEditorScroll = 0f
                                }
                                accumulatedEditorScroll >= actionVisibilityThreshold -> {
                                    editorActionsVisible = true
                                    accumulatedEditorScroll = 0f
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (showSettingsSheet) {
        MyModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = settingsSheetState,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        ) {
            Text(
                text = "回答设置",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = AppDimens.PageHorizontalPadding, vertical = 8.dp),
            )
            Text(
                text = "生成目录选项对图文和 Markdown 两种编辑模式均有效。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = AppDimens.PageHorizontalPadding),
            )
            Spacer(Modifier.height(16.dp))
            SettingItemWithSwitch(
                title = { Text("生成目录") },
                description = {
                    Text("适合长回答，知乎会根据标题生成目录结构。")
                },
                checked = tocEnabled,
                onCheckedChange = {
                    tocEnabled = it
                    settings.putBoolean("$draftKey.toc", it)
                },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(20.dp))
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
            copyToClipboard("write-answer-error", message)
            userMessages.showShortMessage("已复制错误信息")
        },
    )
}
