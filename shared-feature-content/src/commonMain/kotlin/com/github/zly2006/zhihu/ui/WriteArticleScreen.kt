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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import com.fleeksoft.ksoup.Ksoup
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
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppCompactTopBar
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppFloatingToolbar
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.AppSegmentedControl
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.AppTextField
import com.github.zly2006.zhihu.ui.components.RichEditorState
import com.github.zly2006.zhihu.ui.components.WriteContentMarkdownEditor
import com.github.zly2006.zhihu.ui.components.WriteContentPreviewSheet
import com.github.zly2006.zhihu.ui.components.WriteContentRichEditor
import com.github.zly2006.zhihu.ui.components.isRichEditorSupported
import com.github.zly2006.zhihu.ui.components.replaceSelection
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
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
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

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
    val density = LocalDensity.current
    var actionToolbarHeight by remember { mutableStateOf(AppDimens.ElevationNone) }
    val draftKey = "creation.article.${account.session.profile?.id.orEmpty()}.draft"
    val initialDraft = remember(draftKey) {
        runCatching {
            ZhihuJson.json.decodeFromString<LocalArticleDraft>(settings.getString(draftKey, ""))
        }.getOrDefault(LocalArticleDraft())
    }
    var title by remember(draftKey) { mutableStateOf(initialDraft.title) }
    var content by remember(draftKey) {
        mutableStateOf(
            TextFieldValue(
                if (!isRichEditorSupported && initialDraft.richMode && initialDraft.html.isNotBlank()) {
                    zhihuHtmlToEditorMarkdown(initialDraft.html)
                } else {
                    initialDraft.markdown
                },
            ),
        )
    }
    var articleId by remember(draftKey) { mutableStateOf(initialDraft.articleId) }
    var richMode by remember(draftKey) {
        mutableStateOf(isRichEditorSupported && initialDraft.richMode && (initialDraft.html.isNotBlank() || initialDraft.markdown.isBlank()))
    }
    val richEditor = remember(draftKey) { RichEditorState(initialDraft.html) }
    var isEditorConverting by remember(accountClient) { mutableStateOf(false) }
    val hasContent by remember(draftKey) { derivedStateOf { if (richMode) richEditor.hasContent else content.text.isNotBlank() } }
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
    val previewSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val saveMutex = remember(accountClient) { Mutex() }

    fun persistDraft() {
        if (account.client !== accountClient) return
        settings.putString(draftKey, ZhihuJson.json.encodeToString(LocalArticleDraft(title, content.text, articleId, richEditor.html, richMode)))
    }

    suspend fun submit(publish: Boolean, silent: Boolean = false) {
        saveMutex.withLock {
            if (isSubmitting || isUploadingImage || !editorAvailable) return@withLock
            val currentContent = if (richMode) richEditor.html else content.text
            val snapshot = title to currentContent
            if (publish && title.isBlank()) {
                userMessages.showShortMessage("请填写文章标题")
                return@withLock
            }
            if (silent && snapshot == lastSaved) return@withLock
            if (account.client !== accountClient) return@withLock
            if (accountClient.load().cookies["z_c0"].isNullOrBlank()) {
                if (!silent) errorMessage = "保存文章需要登录；当前内容已保存在本机。"
                return@withLock
            }
            isSaving = !publish
            isSubmitting = publish
            saveStatus = if (publish) "发布中" else "正在保存草稿"
            try {
                val html = if (richMode) richEditor.snapshotHtml() else compileMdToZhihuHtml(snapshot.second)
                val documentBody = Ksoup.parseBodyFragment(html).body()
                val hasBody = documentBody.text().isNotBlank() || documentBody.select("img,video,iframe").isNotEmpty()
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
                    val body = Parameters
                        .build {
                            append("title", snapshot.first)
                            append("content", html)
                            append("copyright", if (currentId == null) "public" else "need_review")
                            if (currentId != null) {
                                append("delta_time", "30000")
                                append("topic_ids", "")
                            }
                        }.formUrlEncode()
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
                                    Parameters
                                        .build {
                                            append("title", snapshot.first)
                                            append("content", html)
                                            append("copyright_permission", "need_review")
                                        }.formUrlEncode(),
                                )
                                signZhihuFetchRequest(cookies)
                            }.raiseForStatus()
                            .json<ArticleEditorDraft>()
                        currentCoroutineContext().ensureActive()
                        if (account.client !== accountClient) return@withAuthenticatedClient
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
        snapshotFlow { Triple(title, if (richMode) richEditor.html else content.text, editorAvailable) }
            .debounce(3_000)
            .collect { snapshot ->
                if (snapshot.third && (snapshot.first.isNotBlank() || hasContent)) submit(publish = false, silent = true)
            }
    }

    LaunchedEffect(draftKey, richEditor) {
        snapshotFlow { richEditor.html }.collect { if (richMode) persistDraft() }
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
                    val imageMetadata = buildString {
                        append("zhimg:w=${image.rawWidth};h=${image.rawHeight}")
                        image.watermark?.let { append(";wm=${if (it) 1 else 0}") }
                        if (image.watermark == true) image.watermarkUrl?.let { append(";wmsrc=$it") }
                    }
                    val snippet = "![](${image.url} \"$imageMetadata\")"
                    if (richMode) {
                        richEditor.insertImage(image.url, image.rawWidth, image.rawHeight)
                    } else {
                        content = content.replaceSelection(snippet, cursorOffsetInInsert = snippet.length)
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
        topBar = {
            val textMeasurer = rememberTextMeasurer()
            val titleStyle = if (settingSkinIsMiuix()) LocalAppSkinTokens.current.title3 else MaterialTheme.typography.titleLarge
            val actionStyle = if (settingSkinIsMiuix()) LocalAppSkinTokens.current.main else MaterialTheme.typography.labelLarge
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val requiredWidth = with(LocalDensity.current) {
                    (
                        textMeasurer.measure("写文章", style = titleStyle, softWrap = false).size.width +
                            textMeasurer.measure("发布中", style = actionStyle, softWrap = false).size.width
                    ).toDp()
                } + AppDimens.TouchTarget + AppDimens.PageHorizontalPadding * 2 + AppDimens.Space24 * 2
                val separateAction = maxWidth < requiredWidth
                Column {
                    AppCompactTopBar(
                        title = "写文章",
                        onBack = {
                            persistDraft()
                            navigator.onNavigateBack()
                        },
                        backTag = WRITE_ARTICLE_BACK_BUTTON_TAG,
                        actions = {
                            if (!separateAction) {
                                AppButton(
                                    text = if (isSubmitting) "发布中" else "发布",
                                    enabled = !isSubmitting && !isSaving && !isUploadingImage && editorAvailable && title.isNotBlank() && hasContent,
                                    onClick = { coroutineScope.launch { submit(publish = true) } },
                                    modifier = Modifier.testTag(WRITE_ARTICLE_PUBLISH_TAG),
                                )
                            }
                        },
                    )
                    if (separateAction) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space4), horizontalArrangement = Arrangement.End) {
                            AppButton(
                                text = if (isSubmitting) "发布中" else "发布",
                                enabled = !isSubmitting && !isSaving && !isUploadingImage && editorAvailable && title.isNotBlank() && hasContent,
                                onClick = { coroutineScope.launch { submit(publish = true) } },
                                modifier = Modifier.testTag(WRITE_ARTICLE_PUBLISH_TAG),
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(vertical = AppDimens.Space8)
                    .onSizeChanged { actionToolbarHeight = with(density) { it.height.toDp() } },
                contentAlignment = Alignment.Center,
            ) {
                AppFloatingToolbar(modifier = Modifier.testTag("write_article_action_toolbar")) {
                    AppIconButton(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    val html = if (richMode) richEditor.snapshotHtml() else null
                                    if (account.client !== accountClient) return@launch
                                    previewHtml = html
                                    previewMarkdown = content.text
                                    showPreview = true
                                } catch (error: kotlin.coroutines.cancellation.CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    if (account.client !== accountClient) return@launch
                                    errorMessage = buildWriteOperationErrorMessage("生成预览失败", error)
                                }
                            }
                        },
                        enabled = hasContent && !isSubmitting && editorAvailable,
                        modifier = Modifier.size(AppDimens.TouchTarget).testTag(WRITE_ARTICLE_PREVIEW_TAG),
                    ) {
                        Icon(Icons.Filled.Visibility, contentDescription = "预览")
                    }
                    if (isImagePickerSupported) {
                        AppIconButton(
                            onClick = { picker?.launch() },
                            enabled = !isSubmitting && !isSaving && !isUploadingImage && editorAvailable,
                            modifier = Modifier
                                .size(AppDimens.TouchTarget)
                                .testTag(WRITE_ARTICLE_IMAGE_TAG)
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
                        onClick = { coroutineScope.launch { submit(publish = false) } },
                        enabled = !isSubmitting && !isSaving && !isUploadingImage && editorAvailable,
                        modifier = Modifier
                            .widthIn(min = AppDimens.TouchTarget)
                            .testTag(WRITE_ARTICLE_SAVE_TAG)
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
                .imePadding()
                .padding(horizontal = AppDimens.PageHorizontalPadding),
        ) {
            val availableHeight = maxHeight
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = availableHeight / 2)
                        .verticalScroll(rememberScrollState()),
                ) {
                    AppTextField(
                        value = title,
                        onValueChange = {
                            title = it
                            persistDraft()
                        },
                        placeholder = "文章标题",
                        labelText = "标题",
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth().testTag(WRITE_ARTICLE_TITLE_TAG),
                    )
                    if (isRichEditorSupported) {
                        AppSegmentedControl(
                            options = listOf("图文", "Markdown"),
                            selectedIndex = if (richMode) 0 else 1,
                            onSelect = { index ->
                                val nextRich = index == 0
                                if (nextRich != richMode && !isEditorConverting && !isSaving && !isSubmitting) {
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
                            enabled = !isSubmitting && !isSaving && !isUploadingImage && editorAvailable,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        saveStatus,
                        modifier = Modifier
                            .padding(vertical = AppDimens.Space12)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                if (richMode) {
                    WriteContentRichEditor(
                        state = richEditor,
                        placeholder = "开始写作……",
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth().weight(1f).testTag(WRITE_ARTICLE_CONTENT_TAG),
                    )
                } else {
                    WriteContentMarkdownEditor(
                        value = content,
                        onValueChange = {
                            content = it
                            persistDraft()
                        },
                        placeholder = "开始写作……",
                        contentTag = WRITE_ARTICLE_CONTENT_TAG,
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        bottomPadding = actionToolbarHeight + AppDimens.Space8,
                    )
                }
            }
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
