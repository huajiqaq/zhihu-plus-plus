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

package com.github.zly2006.zhihu.markdown

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalUriHandler
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.SegmentInfoMeta
import com.github.zly2006.zhihu.navigation.SegmentCommentHolder
import com.github.zly2006.zhihu.platform.rememberImageGalleryOpener
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.ui.components.AppDialog
import com.github.zly2006.zhihu.ui.components.CommentScreenComponent
import com.github.zly2006.zhihu.ui.components.LocalOnRequestContentRefresh
import com.github.zly2006.zhihu.ui.components.LocalOnSegmentHighlightClick
import com.github.zly2006.zhihu.ui.components.LocalSegmentActionSheetHost
import com.github.zly2006.zhihu.ui.components.LocalSegmentCommentHost
import com.github.zly2006.zhihu.ui.components.LocalSegmentMetaStates
import com.github.zly2006.zhihu.ui.components.LocalSegmentRemovedHighlights
import com.github.zly2006.zhihu.ui.components.LocalSegmentSelectionTarget
import com.github.zly2006.zhihu.ui.components.SegmentActionSheet
import com.github.zly2006.zhihu.ui.components.SegmentActionSheetContent
import com.github.zly2006.zhihu.ui.components.SegmentActionSheetState
import com.github.zly2006.zhihu.ui.components.SegmentCreationRange
import com.github.zly2006.zhihu.ui.components.SegmentHighlightInteractionHost
import com.github.zly2006.zhihu.ui.components.createSegmentReactions
import com.github.zly2006.zhihu.ui.components.findContainingSegmentForSelection
import com.github.zly2006.zhihu.ui.components.threadKey
import com.github.zly2006.zhihu.ui.components.toSegmentCommentHolder
import com.github.zly2006.zhihu.ui.components.toSegmentHighlightSpan
import com.github.zly2006.zhihu.ui.components.toggleSegmentLike
import com.github.zly2006.zhihu.ui.rememberObservedSetting
import com.github.zly2006.zhihu.ui.subscreens.PREF_BLOCK_SPACING
import com.github.zly2006.zhihu.ui.subscreens.PREF_FONT_SIZE
import com.github.zly2006.zhihu.ui.subscreens.PREF_LINE_HEIGHT
import com.github.zly2006.zhihu.util.SegmentHighlightSpan
import com.github.zly2006.zhihu.viewmodel.comment.SegmentCommentEvents
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import com.hrm.markdown.parser.ast.Document
import com.hrm.markdown.renderer.InlineElementKinds
import com.hrm.markdown.renderer.MarkdownInlineElementClickEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** 阅读宿主已统一限制标题、正文和操作栏宽度时，渲染器不再二次缩窄。 */
val LocalMarkdownWidthManagedByHost = staticCompositionLocalOf { false }

@Composable
fun RenderMarkdown(
    html: String,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    selectable: Boolean = true,
    enableScroll: Boolean = true,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    useTiqianRenderer: Boolean = false,
    searchState: MarkdownSearchState? = null,
    sourceHtml: String? = html,
    segmentCommentSnapshot: SegmentCommentEvents.Snapshot? = null,
) {
    val document = remember(html) { htmlToMdAst(html) }
    RenderMarkdownDocument(
        document = document,
        sourceMarkdown = null,
        sourceHtml = sourceHtml,
        segmentCommentSnapshot = segmentCommentSnapshot,
        modifier = modifier,
        scrollState = scrollState,
        selectable = selectable,
        enableScroll = enableScroll,
        header = header,
        footer = footer,
        useTiqianRenderer = useTiqianRenderer,
        searchState = searchState,
    )
}

@Composable
fun RenderMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    selectable: Boolean = true,
    enableScroll: Boolean = true,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    useTiqianRenderer: Boolean = false,
    searchState: MarkdownSearchState? = null,
) {
    val document = remember(markdown) { markdownToMdAst(markdown) }
    RenderMarkdownDocument(
        document = document,
        sourceMarkdown = markdown,
        sourceHtml = null,
        segmentCommentSnapshot = null,
        modifier = modifier,
        scrollState = scrollState,
        selectable = selectable,
        enableScroll = enableScroll,
        header = header,
        footer = footer,
        useTiqianRenderer = useTiqianRenderer,
        searchState = searchState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenderMarkdownDocument(
    document: Document,
    sourceMarkdown: String?,
    sourceHtml: String?,
    segmentCommentSnapshot: SegmentCommentEvents.Snapshot?,
    modifier: Modifier,
    scrollState: ScrollState,
    selectable: Boolean,
    enableScroll: Boolean,
    header: (@Composable () -> Unit)?,
    footer: (@Composable () -> Unit)?,
    useTiqianRenderer: Boolean,
    searchState: MarkdownSearchState?,
) {
    val previewImageUrls = remember(document) { document.previewImageUrls() }
    val uriHandler = LocalUriHandler.current
    val openImageGallery = rememberImageGalleryOpener()
    val settings = rememberSettingsStore()
    val accountStore = rememberZhihuAccountStore()
    val fontSize by rememberObservedSetting(settings, PREF_FONT_SIZE) { getInt(PREF_FONT_SIZE, 100) }
    val lineHeight by rememberObservedSetting(settings, PREF_LINE_HEIGHT) { getInt(PREF_LINE_HEIGHT, 160) }
    val blockSpacing by rememberObservedSetting(settings, PREF_BLOCK_SPACING) { getInt(PREF_BLOCK_SPACING, 100) }
    val creationClient = accountStore.client
    val creationTarget = LocalSegmentSelectionTarget.current

    /** 菜单关闭和高亮刷新不取消请求；换号、换正文才释放旧请求及反馈。 */
    val creationScope = key(creationClient, creationTarget, sourceHtml, sourceMarkdown) { rememberCoroutineScope() }
    var segmentCommentTarget by remember(creationScope) { mutableStateOf<SegmentCommentHolder?>(null) }
    var segmentActionSheetState by remember(creationScope) { mutableStateOf<SegmentActionSheetState?>(null) }
    val segmentMetaStates: SnapshotStateMap<String, SegmentInfoMeta> = remember(creationScope) { mutableStateMapOf() }
    val removedSegmentHighlights: SnapshotStateMap<String, Boolean> = remember(creationScope) { mutableStateMapOf() }
    LaunchedEffect(document, creationScope) {
        val activeMetas = renderMarkdownActiveSegmentMetas(document)
        /** 服务端可能回传旧正文；取消标记保留到划线从响应消失。 */
        removedSegmentHighlights.keys.filter { it !in activeMetas }.forEach(removedSegmentHighlights::remove)
        segmentMetaStates.keys
            .filter { key ->
                val server = activeMetas[key]
                val local = segmentMetaStates[key]
                server == null || (server.isLike == local?.isLike && server.likeCount == local.likeCount)
            }.forEach(segmentMetaStates::remove)
    }
    val requestContentRefresh = LocalOnRequestContentRefresh.current

    /** 在下方上下文提供者内消费评论目标，才能读取本正文的评论宿主。 */
    var pendingCommentTarget by remember(creationScope) { mutableStateOf<SegmentCommentHolder?>(null) }
    val creationEnvironment = rememberPaginationEnvironment(allowGuestAccess = false)
    var segmentError by remember(creationScope) { mutableStateOf<String?>(null) }
    var processingSegment by remember(creationScope) { mutableStateOf(false) }

    suspend fun ensureSegmentRequestIsCurrent() {
        currentCoroutineContext().ensureActive()
        if (accountStore.client !== creationClient) throw CancellationException("段评操作所属账号已切换")
    }

    val onCreateSegment: (SegmentCreationRange?, Boolean) -> Unit = { range, openCommentEditor ->
        val target = creationTarget
        val existing = range
            ?.let { findContainingSegmentForSelection(it, document, segmentMetaStates, removedSegmentHighlights) }
            ?.takeIf { it.contentId == target?.contentId && it.contentType == target?.contentType }
        when {
            processingSegment -> segmentError = "正在处理段评，请稍候"
            target == null -> segmentError = "当前内容不支持段评"
            sourceHtml == null -> segmentError = "当前内容缺少原始正文，无法定位划线，请刷新后重试"
            range == null || range.text.isBlank() -> segmentError = "选区跨越了不同原始段落、包含非纯文本，或无法准确定位，请重新选择。已有跨段划线可直接点击操作"
            (existing == null || openCommentEditor) && range.text.length < 5 -> segmentError = "请至少选择5个字后再喜欢或评论"
            !accountStore.session.login -> segmentError = "请先登录后操作段评"
            existing != null && openCommentEditor -> {
                pendingCommentTarget = existing.toSegmentCommentHolder()
                if (pendingCommentTarget == null) segmentError = "缺少段落定位，无法打开段评"
            }
            existing?.meta?.isLike == true -> segmentError = "这段划线已喜欢，可选择取消喜欢"
            else -> {
                segmentError = null
                processingSegment = true
                creationScope.launch {
                    try {
                        ensureSegmentRequestIsCurrent()
                        if (existing != null) {
                            val updated = toggleSegmentLike(creationEnvironment, existing)
                            ensureSegmentRequestIsCurrent()
                            val existingKey = existing.threadKey()
                            segmentMetaStates[existingKey] = updated
                            removedSegmentHighlights.remove(existingKey)
                            requestContentRefresh?.invoke()
                            return@launch
                        }
                        val (_, segId) = createSegmentReactions(creationEnvironment, target, listOf(range)).single()
                        ensureSegmentRequestIsCurrent()
                        segmentError = null
                        val createdKey = listOf(target.contentType, target.contentId, segId).joinToString("|")
                        removedSegmentHighlights.remove(createdKey)
                        segmentMetaStates.remove(createdKey)
                        if (openCommentEditor) {
                            pendingCommentTarget = SegmentCommentHolder(
                                contentId = target.contentId,
                                contentType = target.contentType,
                                segmentId = segId,
                                segmentContent = range.text,
                                paragraphId = range.paragraphId,
                                startOffset = range.startOffset,
                                endOffset = range.endOffset,
                            )
                        }
                        requestContentRefresh?.invoke()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        ensureSegmentRequestIsCurrent()
                        segmentError = "段评操作失败：${error.message?.takeIf(String::isNotBlank) ?: "请重试"}"
                    } finally {
                        processingSegment = false
                    }
                }
            }
        }
    }
    val onCancelSegmentLike: (SegmentCreationRange?) -> Unit = { range ->
        val liked = range?.let { findContainingSegmentForSelection(it, document, segmentMetaStates, removedSegmentHighlights) }?.takeIf { it.meta.isLike }
        when {
            processingSegment -> segmentError = "正在处理段评，请稍候"
            !accountStore.session.login -> segmentError = "请先登录后操作段评"
            range == null -> segmentError = "无法定位选中的划线，请重新选择后重试"
            liked == null -> segmentError = "选中的划线已变化，请重新选择后重试"
            else -> {
                segmentError = null
                processingSegment = true
                creationScope.launch {
                    try {
                        ensureSegmentRequestIsCurrent()
                        val updated = toggleSegmentLike(creationEnvironment, liked)
                        ensureSegmentRequestIsCurrent()
                        segmentError = null
                        val key = liked.threadKey()
                        segmentMetaStates[key] = updated
                        val commentDelta = segmentCommentSnapshot?.effectiveDelta(liked.meta.segIds) ?: 0
                        if (updated.likeCount == 0 && updated.commentCount + commentDelta <= 0) {
                            removedSegmentHighlights[key] = true
                        }
                        requestContentRefresh?.invoke()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        ensureSegmentRequestIsCurrent()
                        segmentError = "取消喜欢失败：${error.message?.takeIf(String::isNotBlank) ?: "请重试"}"
                    } finally {
                        processingSegment = false
                    }
                }
            }
        }
    }
    val containingSegmentProvider: (SegmentCreationRange) -> SegmentHighlightSpan? = { range ->
        findContainingSegmentForSelection(range, document, segmentMetaStates, removedSegmentHighlights)
    }
    CompositionLocalProvider(
        LocalSegmentCommentHost provides { target ->
            segmentActionSheetState?.onDismiss?.invoke()
            segmentCommentTarget = target
        },
        LocalSegmentActionSheetHost provides { state ->
            if (state == null && segmentActionSheetState != null) segmentCommentTarget = null
            segmentActionSheetState = state
        },
        LocalSegmentMetaStates provides segmentMetaStates,
        LocalSegmentRemovedHighlights provides removedSegmentHighlights,
    ) {
        val openSegmentComments = LocalSegmentCommentHost.current
        LaunchedEffect(pendingCommentTarget) {
            pendingCommentTarget?.let { target ->
                pendingCommentTarget = null
                openSegmentComments(target)
            }
        }
        SegmentHighlightInteractionHost(
            document,
            segmentMetaStates,
            segmentCommentSnapshot,
            onError = { segmentError = it },
            onOpenQuotedComments = { target ->
                if (segmentCommentTarget != target) segmentCommentTarget = target
            },
        ) {
            Box(
                modifier = modifier,
            ) {
                NoDoubleClickSelectionScope {
                    val onSegmentClick = LocalOnSegmentHighlightClick.current
                    val onInlineElementClick: (MarkdownInlineElementClickEvent) -> Unit = { event ->
                        if (event.kind == InlineElementKinds.Highlight) {
                            /** 非跨段划线缺少 display-text 属性，事件文本用于补全引文。 */
                            event.attributes
                                .toSegmentHighlightSpan(fallbackText = event.text)
                                ?.let { onSegmentClick?.invoke(it) }
                        }
                    }
                    val segmentUnderlineColor = MaterialTheme.colorScheme.outlineVariant
                    val segmentCommentColor = MaterialTheme.colorScheme.primary
                    val segmentCommentPainter = rememberVectorPainter(Icons.AutoMirrored.Filled.Comment)
                    RenderMarkdownContent(
                        document = document,
                        sourceMarkdown = sourceMarkdown,
                        sourceHtml = sourceHtml,
                        segmentCommentSnapshot = segmentCommentSnapshot,
                        scrollState = scrollState,
                        selectable = selectable,
                        enableScroll = enableScroll,
                        header = header,
                        footer = footer,
                        useTiqianRenderer = useTiqianRenderer,
                        searchState = searchState,
                        previewImageUrls = previewImageUrls,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        blockSpacing = blockSpacing,
                        settings = settings,
                        onLinkClick = uriHandler::openUri,
                        onInlineElementClick = onInlineElementClick,
                        segmentUnderlineColor = segmentUnderlineColor,
                        segmentCommentColor = segmentCommentColor,
                        segmentCommentPainter = segmentCommentPainter,
                        openImageGallery = openImageGallery,
                        onCreateSegment = onCreateSegment,
                        containingSegmentProvider = containingSegmentProvider,
                        onCancelSegmentLike = onCancelSegmentLike,
                        removedSegmentHighlights = removedSegmentHighlights,
                        segmentMetaStates = segmentMetaStates,
                    )
                }
            }
        }
    }
    CommentScreenComponent(
        showComments = segmentCommentTarget != null,
        onDismiss = {
            segmentActionSheetState?.onDismiss?.invoke()
            segmentCommentTarget = null
        },
        content = segmentCommentTarget ?: SegmentCommentHolder("dummy", "dummy", "dummy", "", "", 0, 0),
        rootHeader = segmentActionSheetState?.let { state -> { SegmentActionSheetContent(state) } },
    )
    if (segmentCommentTarget == null) {
        segmentActionSheetState?.let { state -> SegmentActionSheet(state) }
    }
    segmentError?.let { message ->
        AppDialog(
            title = "段评提示",
            summary = message,
            confirmText = "知道了",
            onDismissRequest = { segmentError = null },
            onConfirm = { segmentError = null },
        )
    }
}
