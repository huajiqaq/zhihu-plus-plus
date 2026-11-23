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
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.rememberTextMeasurer
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.editor.ZhihuImageUploadSource
import com.github.zly2006.zhihu.editor.compileMdToZhihuHtml
import com.github.zly2006.zhihu.editor.isImagePickerSupported
import com.github.zly2006.zhihu.editor.rememberImagePickerLauncher
import com.github.zly2006.zhihu.editor.uploadZhihuImage
import com.github.zly2006.zhihu.editor.zhihuHtmlToEditorMarkdown
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.WriteArticle
import com.github.zly2006.zhihu.platform.rememberPlainTextClipboard
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.RichEditorState
import com.github.zly2006.zhihu.ui.components.WriteContentPreviewSheet
import com.github.zly2006.zhihu.ui.components.isRichEditorSupported
import com.github.zly2006.zhihu.ui.components.replaceSelection
import com.github.zly2006.zhihu.util.json
import com.github.zly2006.zhihu.util.raiseForStatus
import com.github.zly2006.zhihu.util.signZhihuFetchRequest
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

const val WRITE_ARTICLE_BACK_BUTTON_TAG = "write_article_back_button"
const val WRITE_ARTICLE_TITLE_TAG = "write_article_title"
const val WRITE_ARTICLE_CONTENT_TAG = "write_article_content"
const val WRITE_ARTICLE_PREVIEW_TAG = "write_article_preview"
const val WRITE_ARTICLE_IMAGE_TAG = "write_article_image"
const val WRITE_ARTICLE_SAVE_TAG = "write_article_save"
const val WRITE_ARTICLE_PUBLISH_TAG = "write_article_publish"

@Serializable
private data class LocalArticleDraft(
    val title: String = "",
    val markdown: String = "",
    val articleId: Long? = null,
    val html: String = "",
    val richMode: Boolean = true,
)

@Serializable
private data class ArticleEditorDraft(
    val id: Long,
    val title: String = "",
    val content: String = "",
)

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun WriteArticleScreen(destination: WriteArticle) {
    val navigator = LocalNavigator.current
    val userMessages = rememberUserMessageSink()
    val coroutineScope = rememberCoroutineScope()
    val environment = rememberPaginationEnvironment(false)
    val account = rememberZhihuAccountStore()
    val accountClient = account.client
    val settings = rememberSettingsStore()
    val clipboard = rememberPlainTextClipboard()
    val draftKey = "creation.article.${account.session.profile?.id.orEmpty()}.draft"
    val initialDraft = remember(draftKey) {
        runCatching {
            ZhihuJson.json.decodeFromString<LocalArticleDraft>(settings.getString(draftKey, ""))
        }.getOrDefault(LocalArticleDraft())
    }
    val title = key(draftKey) { rememberTextFieldState(initialDraft.title) }
    val content = remember(draftKey) {
        TextFieldState(
            initialText = if (!isRichEditorSupported && initialDraft.richMode && initialDraft.html.isNotBlank()) {
                zhihuHtmlToEditorMarkdown(initialDraft.html)
            } else {
                initialDraft.markdown
            },
            initialSelection = TextRange.Zero,
        )
    }
    var articleId by remember(draftKey) { mutableStateOf(initialDraft.articleId) }
    var richMode by remember(draftKey) {
        mutableStateOf(isRichEditorSupported && initialDraft.richMode && (initialDraft.html.isNotBlank() || initialDraft.markdown.isBlank()))
    }
    val richEditor = remember(draftKey) { RichEditorState(initialDraft.html) }
    var isEditorConverting by remember(accountClient) { mutableStateOf(false) }
    val hasContent by remember(draftKey) { derivedStateOf { if (richMode) richEditor.hasContent else content.text.toString().isNotBlank() } }
    val editorAvailable by remember(draftKey) { derivedStateOf { !isEditorConverting && (!richMode || richEditor.isReady) } }
    var isSaving by remember(accountClient) { mutableStateOf(false) }
    var isSubmitting by remember(accountClient) { mutableStateOf(false) }
    var isUploadingImage by remember(accountClient) { mutableStateOf(false) }
    var errorMessage by remember(accountClient) { mutableStateOf<String?>(null) }
    var saveStatus by remember(accountClient) { mutableStateOf("草稿保存在本机") }
    var lastSaved by remember(draftKey) { mutableStateOf<Pair<String, String>?>(null) }
    var showPreview by remember(accountClient) { mutableStateOf(false) }
    var previewMarkdown by remember(accountClient) { mutableStateOf("") }
    var previewHtml by remember(accountClient) { mutableStateOf<String?>(null) }
    val previewSheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val saveMutex = remember(accountClient) { Mutex() }
    var draftPublished by remember(draftKey) { mutableStateOf(false) }

    fun persistDraft() {
        if (draftPublished) return
        val draft = LocalArticleDraft(title.text.toString(), content.text.toString(), articleId, richEditor.html, richMode)
        if (draft == initialDraft && settings.getStringOrNull(draftKey) == null) return
        settings.putString(draftKey, ZhihuJson.json.encodeToString(draft))
    }

    /** 退出或切换账号时仍写入原草稿键，不依赖下一次状态流调度。 */
    DisposableEffect(draftKey, accountClient) {
        onDispose { persistDraft() }
    }

    suspend fun submit(publish: Boolean, silent: Boolean = false) {
        saveMutex.withLock {
            if (isSubmitting || isUploadingImage || !editorAvailable) return@withLock
            val currentContent = if (richMode) richEditor.html else content.text.toString()
            val snapshot = title.text.toString() to currentContent
            if (publish && snapshot.first.isBlank()) {
                userMessages.showShortMessage("请填写文章标题")
                return@withLock
            }
            if (silent && snapshot == lastSaved) return@withLock
            if (account.client !== accountClient) return@withLock
            persistDraft()
            if (accountClient.load().cookies["z_c0"].isNullOrBlank()) {
                if (!silent) errorMessage = "保存文章需要登录；当前内容已保存在本机。"
                return@withLock
            }
            isSaving = !publish
            isSubmitting = publish
            saveStatus = if (publish) "发布中" else "正在保存草稿"
            try {
                val html = if (richMode) richEditor.snapshotHtml() else compileMdToZhihuHtml(snapshot.second)
                val hasBody = writeArticleScreenHasBody(html)
                if (!hasBody && (publish || snapshot.first.isBlank())) {
                    saveStatus = "草稿保存在本机"
                    if (publish) userMessages.showShortMessage("文章内容为空")
                    return@withLock
                }
                currentCoroutineContext().ensureActive()
                if (account.client !== accountClient) return@withLock
                accountClient.withAuthenticatedClient { client, cookies ->
                    val currentId = articleId
                    val url = if (currentId == null) {
                        "https://api.zhihu.com/articles/write"
                    } else {
                        "https://api.zhihu.com/articles/$currentId/draft"
                    }
                    val body = writeArticleScreenDraftRequestBody(snapshot.first, html, currentId)

                    /** /articles/write 对 Web UA 返回 403，草稿请求需保留已验证的 Android 编辑器环境。 */
                    val saved = if (currentId != null && snapshot == lastSaved && publish) {
                        ArticleEditorDraft(currentId, snapshot.first, html)
                    } else {
                        client
                            .post(url) {
                                header(HttpHeaders.UserAgent, "com.zhihu.android/Futureve/11.12.0 Mozilla/5.0 (Linux; Android 15)")
                                header("x-api-version", "3.0.89")
                                cookies["_xsrf"]?.let { header("x-xsrftoken", it) }
                                contentType(ContentType.Application.FormUrlEncoded)
                                setBody(body)
                                signZhihuFetchRequest(cookies)
                            }.raiseForStatus()
                            .json<ArticleEditorDraft>()
                    }
                    currentCoroutineContext().ensureActive()
                    if (account.client !== accountClient) return@withAuthenticatedClient
                    articleId = saved.id
                    lastSaved = snapshot.first to if (richMode) html else snapshot.second
                    persistDraft()
                    saveStatus = "草稿已保存"
                    if (!publish) {
                        if (!silent) userMessages.showShortMessage("已保存草稿")
                    } else {
                        val published = client
                            .put("https://api.zhihu.com/articles/${saved.id}/draft") {
                                header(HttpHeaders.UserAgent, "com.zhihu.android/Futureve/11.12.0 Mozilla/5.0 (Linux; Android 15)")
                                header("x-api-version", "3.0.89")
                                cookies["_xsrf"]?.let { header("x-xsrftoken", it) }
                                contentType(ContentType.Application.FormUrlEncoded)
                                setBody(
                                    writeArticleScreenPublishRequestBody(snapshot.first, html),
                                )
                                signZhihuFetchRequest(cookies)
                            }.raiseForStatus()
                            .json<ArticleEditorDraft>()
                        currentCoroutineContext().ensureActive()
                        if (account.client !== accountClient) return@withAuthenticatedClient
                        draftPublished = true
                        settings.remove(draftKey)
                        userMessages.showShortMessage("文章已发布")
                        navigator.onNavigateBack()
                        navigator.onNavigate(Article(title = snapshot.first, type = ArticleType.Article, id = published.id))
                    }
                }
            } catch (error: kotlin.coroutines.cancellation.CancellationException) {
                throw error
            } catch (error: Exception) {
                if (account.client !== accountClient) return@withLock
                saveStatus = "云端保存失败，内容仍保存在本机"
                if (!silent || publish) errorMessage = buildWriteOperationErrorMessage(if (publish) "发布文章失败" else "保存文章草稿失败", error)
            } finally {
                isSaving = false
                isSubmitting = false
            }
        }
    }

    LaunchedEffect(draftKey, accountClient) {
        snapshotFlow { Triple(title.text.toString(), if (richMode) richEditor.html else content.text.toString(), editorAvailable) }
            .debounce(3_000)
            .collect { snapshot ->
                if (snapshot.third && (snapshot.first.isNotBlank() || hasContent)) submit(publish = false, silent = true)
            }
    }

    LaunchedEffect(draftKey, accountClient) {
        snapshotFlow { LocalArticleDraft(title.text.toString(), content.text.toString(), articleId, richEditor.html, richMode) }
            .collect { if (account.client === accountClient) persistDraft() }
    }

    val picker = if (isImagePickerSupported) {
        rememberImagePickerLauncher { picked ->
            if (account.client !== accountClient || isSubmitting || isSaving || isUploadingImage) return@rememberImagePickerLauncher
            isUploadingImage = true
            coroutineScope.launch {
                try {
                    val image = uploadZhihuImage(environment, picked.bytes, picked.mimeType, picked.fileName, ZhihuImageUploadSource.Article)
                    currentCoroutineContext().ensureActive()
                    if (account.client !== accountClient) return@launch
                    val snippet = writeArticleScreenImageSnippet(image)
                    if (richMode) {
                        richEditor.insertImage(image.url, image.rawWidth, image.rawHeight)
                    } else {
                        content.edit { replaceSelection(snippet, cursorOffsetInInsert = snippet.length) }
                    }
                    persistDraft()
                } catch (error: kotlin.coroutines.cancellation.CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (account.client !== accountClient) return@launch
                    errorMessage = buildWriteOperationErrorMessage("插入图片失败", error)
                } finally {
                    isUploadingImage = false
                }
            }
        }
    } else {
        null
    }

    AppScaffold(
        sampleContent = !richMode,
        topBar = {
            val textMeasurer = rememberTextMeasurer()
            WriteArticleScreenTopBar(
                textMeasurer = textMeasurer,
                isSubmitting = isSubmitting,
                publishEnabled = !isSubmitting && !isSaving && !isUploadingImage && editorAvailable && title.text.isNotBlank() && hasContent,
                onBack = {
                    persistDraft()
                    navigator.onNavigateBack()
                },
                onPublish = { coroutineScope.launch { submit(publish = true) } },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .testTag("write_article_page")
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space12),
        ) {
            WriteArticleScreenEditor(
                title = title,
                richMode = richMode,
                isSubmitting = isSubmitting,
                isSaving = isSaving,
                isUploadingImage = isUploadingImage,
                editorAvailable = editorAvailable,
                hasContent = hasContent,
                saveStatus = saveStatus,
                content = content,
                richEditor = richEditor,
                isEditorConverting = isEditorConverting,
                onModeSelect = { index ->
                    val nextRich = index == 0
                    if (nextRich != richMode && !isEditorConverting && !isSaving && !isSubmitting) {
                        coroutineScope.launch {
                            isEditorConverting = true
                            try {
                                if (nextRich) {
                                    richEditor.loadHtml(compileMdToZhihuHtml(content.text.toString()))
                                } else {
                                    val markdown = zhihuHtmlToEditorMarkdown(richEditor.snapshotHtml())
                                    if (account.client !== accountClient) return@launch
                                    content.edit {
                                        replace(0, length, markdown)
                                        selection = TextRange.Zero
                                    }
                                }
                                if (account.client !== accountClient) return@launch
                                richMode = nextRich
                                persistDraft()
                            } catch (error: kotlin.coroutines.cancellation.CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                if (account.client !== accountClient) return@launch
                                errorMessage = buildWriteOperationErrorMessage("切换编辑模式失败", error)
                            } finally {
                                isEditorConverting = false
                            }
                        }
                    }
                },
                onPreview = {
                    coroutineScope.launch {
                        try {
                            val html = if (richMode) richEditor.snapshotHtml() else null
                            if (account.client !== accountClient) return@launch
                            previewHtml = html
                            previewMarkdown = content.text.toString()
                            showPreview = true
                        } catch (error: kotlin.coroutines.cancellation.CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            if (account.client !== accountClient) return@launch
                            errorMessage = buildWriteOperationErrorMessage("生成预览失败", error)
                        }
                    }
                },
                onImage = { picker?.launch() },
                onSave = { coroutineScope.launch { submit(publish = false) } },
            )
        }
    }
    if (showPreview) {
        WriteContentPreviewSheet(
            sheetState = previewSheetState,
            useWebView = false,
            isLoading = false,
            html = previewHtml,
            markdown = previewMarkdown,
            onDismissRequest = { showPreview = false },
        )
    }
    WriteOperationErrorDialog(
        message = errorMessage,
        onDismissRequest = { errorMessage = null },
        onCopy = { clipboard("write-article-error", it) },
    )
}
