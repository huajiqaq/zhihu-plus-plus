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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.SegmentInfoMeta
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.SegmentCommentHolder
import com.github.zly2006.zhihu.navigation.resolveContent
import com.github.zly2006.zhihu.platform.rememberExternalUrlOpener
import com.github.zly2006.zhihu.platform.rememberImageGalleryOpener
import com.github.zly2006.zhihu.platform.rememberImageSaver
import com.github.zly2006.zhihu.platform.rememberImageSharer
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.ui.components.AppDialog
import com.github.zly2006.zhihu.ui.components.AppDropdownMenuItem
import com.github.zly2006.zhihu.ui.components.AppImageContextMenu
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
import com.github.zly2006.zhihu.ui.components.SegmentSelectionToolbar
import com.github.zly2006.zhihu.ui.components.createSegmentReactions
import com.github.zly2006.zhihu.ui.components.findContainingSegmentForSelection
import com.github.zly2006.zhihu.ui.components.resolveSegmentDecorationState
import com.github.zly2006.zhihu.ui.components.threadKey
import com.github.zly2006.zhihu.ui.components.toSegmentHighlightSpan
import com.github.zly2006.zhihu.ui.components.toggleSegmentLike
import com.github.zly2006.zhihu.ui.rememberObservedSetting
import com.github.zly2006.zhihu.ui.subscreens.DUO3_TIQIAN_MATH_FONT_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.PREF_BLOCK_SPACING
import com.github.zly2006.zhihu.ui.subscreens.PREF_FONT_SIZE
import com.github.zly2006.zhihu.ui.subscreens.PREF_LINE_HEIGHT
import com.github.zly2006.zhihu.util.SegmentHighlightSpan
import com.github.zly2006.zhihu.viewmodel.comment.SegmentCommentEvents
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import com.hrm.latex.renderer.model.LatexConfig
import com.hrm.latex.renderer.model.LatexTheme
import com.hrm.markdown.parser.ast.Document
import com.hrm.markdown.renderer.InlineElementKinds
import com.hrm.markdown.renderer.Markdown
import com.hrm.markdown.renderer.MarkdownComponents
import com.hrm.markdown.renderer.MarkdownDefaults
import com.hrm.markdown.renderer.MarkdownImageData
import com.hrm.markdown.renderer.MarkdownInlineDecorationRequest
import com.hrm.markdown.renderer.MarkdownInlineElementClickEvent
import com.hrm.markdown.renderer.MarkdownInlineEndMarker
import com.hrm.markdown.renderer.MarkdownInlineUnderline
import com.hrm.markdown.renderer.MarkdownInteractions
import com.hrm.markdown.runtime.MarkdownBlockDirectiveRenderer
import com.hrm.markdown.runtime.MarkdownDirectivePlugin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 阅读宿主已统一限制标题、正文和操作栏宽度时，渲染器不再二次缩窄。 */
val LocalMarkdownWidthManagedByHost = staticCompositionLocalOf { false }

@Composable
fun RenderImage(
    data: MarkdownImageData,
    modifier: Modifier,
    imageUrls: List<String> = listOf(data.url),
) {
    val openImageGallery = rememberImageGalleryOpener()
    val openExternalUrl = rememberExternalUrlOpener()
    val saveImage = rememberImageSaver()
    val shareImage = rememberImageSharer()
    var expanded by remember(data.url) { mutableStateOf(false) }
    var windowPressPoint by remember(data.url) { mutableStateOf<Offset?>(null) }
    var windowImageBounds by remember(data.url) { mutableStateOf<Rect?>(null) }
    var imageCoordinates by remember(data.url) { mutableStateOf<LayoutCoordinates?>(null) }
    val hapticFeedback = LocalHapticFeedback.current
    val previewUrls = remember(imageUrls, data.url) {
        imageUrls.ifEmpty { listOf(data.url) }
    }
    val imageWidth = data.width
    val imageHeight = data.height
    val imageAspectRatio =
        if (imageWidth != null && imageHeight != null && imageWidth > 0 && imageHeight > 0) {
            imageWidth.toFloat() / imageHeight
        } else {
            null
        }

    fun openGallery() {
        val initialIndex = previewUrls.indexOf(data.url).takeIf { it >= 0 } ?: 0
        openImageGallery(previewUrls, initialIndex)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = rememberMarkdownImageModel(data.url),
            contentDescription = data.altText,
            modifier = modifier
                .fillMaxWidth(0.8f)
                .then(
                    if (imageAspectRatio != null) {
                        Modifier.aspectRatio(imageAspectRatio)
                    } else {
                        Modifier
                    },
                ).onGloballyPositioned { coordinates ->
                    imageCoordinates = coordinates
                    windowImageBounds = coordinates.boundsInWindow()
                }.pointerInput(data.url, hapticFeedback) {
                    detectTapGestures(
                        onTap = {
                            openGallery()
                        },
                        onLongPress = { offset ->
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            imageCoordinates?.takeIf { it.isAttached }?.let { coordinates ->
                                windowImageBounds = coordinates.boundsInWindow()
                                windowPressPoint = coordinates.localToWindow(offset)
                            } ?: run { windowPressPoint = null }
                            expanded = true
                        },
                    )
                }.semantics {
                    onClick(label = "查看原图") {
                        openGallery()
                        true
                    }
                    onLongClick(label = "图片操作") {
                        windowPressPoint = null
                        windowImageBounds = imageCoordinates?.takeIf { it.isAttached }?.boundsInWindow()
                        expanded = true
                        true
                    }
                },
        )

        /** Popup 文字不能注册到父级选区，否则跨窗口坐标排序会失败。 */
        DisableSelection {
            AppImageContextMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                windowImageBounds = windowImageBounds,
                windowPressPoint = windowPressPoint,
            ) {
                AppDropdownMenuItem(
                    text = { Text("查看图片") },
                    onClick = {
                        expanded = false
                        openGallery()
                    },
                )
                AppDropdownMenuItem(
                    text = { Text("在浏览器中打开") },
                    onClick = {
                        expanded = false
                        openExternalUrl(data.url)
                    },
                )
                AppDropdownMenuItem(
                    text = { Text("保存图片") },
                    onClick = {
                        expanded = false
                        saveImage(data.url)
                    },
                )
                AppDropdownMenuItem(
                    text = { Text("分享图片") },
                    onClick = {
                        expanded = false
                        shareImage(data.url)
                    },
                )
            }
        }
    }
}

/** 正文双击交给页面动作，不触发系统双击选词。 */
@Composable
private fun NoDoubleClickSelectionScope(content: @Composable () -> Unit) {
    val current = LocalViewConfiguration.current
    val patched =
        remember(current) {
            object : ViewConfiguration by current {
                override val doubleTapTimeoutMillis: Long = 0L
            }
        }

    CompositionLocalProvider(LocalViewConfiguration provides patched) {
        content()
    }
}

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
    val navigator = LocalNavigator.current
    val openExternalUrl = rememberExternalUrlOpener()
    val openImageGallery = rememberImageGalleryOpener()
    val settings = rememberSettingsStore()
    val accountStore = rememberZhihuAccountStore()
    val fontSize by rememberObservedSetting(settings, PREF_FONT_SIZE) { getInt(PREF_FONT_SIZE, 100) }
    val lineHeight by rememberObservedSetting(settings, PREF_LINE_HEIGHT) { getInt(PREF_LINE_HEIGHT, 160) }
    val blockSpacing by rememberObservedSetting(settings, PREF_BLOCK_SPACING) { getInt(PREF_BLOCK_SPACING, 100) }
    var segmentCommentTarget by remember { mutableStateOf<SegmentCommentHolder?>(null) }
    var segmentActionSheetState by remember { mutableStateOf<SegmentActionSheetState?>(null) }
    val segmentMetaStates: SnapshotStateMap<String, SegmentInfoMeta> = remember(accountStore.client) { mutableStateMapOf() }
    val removedSegmentHighlights: SnapshotStateMap<String, Boolean> = remember(accountStore.client) { mutableStateMapOf() }
    LaunchedEffect(document) {
        val activeMetas = mutableMapOf<String, SegmentInfoMeta>()

        fun visit(node: com.hrm.markdown.parser.ast.Node) {
            if (node is com.hrm.markdown.parser.ast.Highlight) {
                node.attributes.toSegmentHighlightSpan()?.let { activeMetas[it.threadKey()] = it.meta }
            }
            if (node is com.hrm.markdown.parser.ast.ContainerNode) node.children.forEach(::visit)
        }
        visit(document)
        /** 服务端可能回传旧正文；取消标记保留到划线从响应消失。 */
        removedSegmentHighlights.keys.filter { it !in activeMetas }.forEach(removedSegmentHighlights::remove)
        segmentMetaStates.keys
            .filter { key ->
                val server = activeMetas[key]
                val local = segmentMetaStates[key]
                server == null || (server.isLike == local?.isLike && server.likeCount == local.likeCount)
            }.forEach(segmentMetaStates::remove)
    }
    val creationTarget = LocalSegmentSelectionTarget.current
    val requestContentRefresh = LocalOnRequestContentRefresh.current

    /** 在下方 provider 内消费评论目标，才能读取本正文的评论宿主。 */
    var pendingCommentTarget by remember { mutableStateOf<SegmentCommentHolder?>(null) }
    val creationEnvironment = rememberPaginationEnvironment(allowGuestAccess = false)
    var segmentError by remember { mutableStateOf<String?>(null) }
    val creationScope = rememberCoroutineScope()
    var creatingSegment by remember { mutableStateOf(false) }
    val onCreateSegment: (SegmentCreationRange?, Boolean) -> Unit = { range, openCommentEditor ->
        val target = creationTarget
        when {
            creatingSegment -> segmentError = "正在处理段评，请稍候"
            target == null -> segmentError = "当前内容不支持段评"
            range == null || range.text.isBlank() -> segmentError = "只能选择同一段内的纯文本划线"
            !accountStore.session.login -> segmentError = "请先登录后操作段评"
            else -> {
                creatingSegment = true
                creationScope.launch {
                    try {
                        val (_, segId) = createSegmentReactions(creationEnvironment, target, listOf(range)).single()
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
                        segmentError = "段评操作失败：${error.message ?: "请重试"}"
                    } finally {
                        creatingSegment = false
                    }
                }
            }
        }
    }
    val onCancelSegmentLike: (SegmentCreationRange) -> Unit = { range ->
        val liked = findContainingSegmentForSelection(range, document, segmentMetaStates, removedSegmentHighlights)?.takeIf { it.meta.isLike }
        if (!accountStore.session.login) {
            segmentError = "请先登录后操作段评"
        } else if (liked != null) {
            creationScope.launch {
                try {
                    val updated = toggleSegmentLike(creationEnvironment, liked)
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
                    segmentError = "取消喜欢失败：${error.message ?: "请重试"}"
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
                    val onLinkClick: (String) -> Unit = { url ->
                        resolveContent(url)?.let { navigator.onNavigate(it) }
                            ?: openExternalUrl(url)
                    }
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
                    val removedHighlightsSnapshot = removedSegmentHighlights.toMap()
                    val segmentMetaSnapshot = segmentMetaStates.toMap()
                    val commentDeltas = segmentCommentSnapshot?.asMap().orEmpty()
                    val ownCommentDeltas = segmentCommentSnapshot?.asMap(ownComments = true).orEmpty()
                    val inlineDecoration: (MarkdownInlineDecorationRequest) -> MarkdownInlineUnderline? =
                        { request ->
                            if (request.kind == InlineElementKinds.Highlight) {
                                request.attributes.toSegmentHighlightSpan()?.let { span ->
                                    val live = segmentMetaSnapshot[span.threadKey()]
                                    val meta = span.meta.copy(isLike = live?.isLike ?: span.meta.isLike, likeCount = live?.likeCount ?: span.meta.likeCount)
                                    val ids = span.meta.segIds
                                        .map(String::trim)
                                        .distinct()
                                    val localDelta = ids.sumOf { commentDeltas[it] ?: 0 }
                                    val state = resolveSegmentDecorationState(
                                        meta = meta,
                                        removed = removedHighlightsSnapshot[span.threadKey()] == true,
                                        localCommentDelta = localDelta,
                                        localOwnCommentDelta = ids.sumOf { ownCommentDeltas[it] ?: 0 },
                                    )
                                    if (!state.visible) return@let null
                                    MarkdownInlineUnderline(
                                        color = if (state.hasComments) segmentCommentColor else segmentUnderlineColor,
                                        strokeWidth = if (state.hasComments) 1.5.dp else 1.dp,
                                        dotDiameter = if (state.mine) null else 2.dp,
                                        dotGap = 3.dp,
                                        endMarker = if (state.hasComments) {
                                            MarkdownInlineEndMarker(color = segmentCommentColor, diameter = 8.dp, painter = segmentCommentPainter)
                                        } else {
                                            null
                                        },
                                    )
                                }
                            } else {
                                null
                            }
                        }
                    if (useTiqianRenderer && isTiqianMarkdownRendererAvailable) {
                        PlatformTiqianMarkdown(
                            document = document,
                            sourceMarkdown = sourceMarkdown,
                            sourceHtml = sourceHtml,
                            segmentCommentSnapshot = segmentCommentSnapshot,
                            imageUrls = previewImageUrls,
                            scrollState = scrollState,
                            selectable = selectable,
                            enableScroll = enableScroll,
                            fontSizeScale = fontSize / 100f,
                            lineHeightFromFontSize = lineHeight / 100f,
                            blockSpacingScale = blockSpacing / 100f,
                            mathFontFamilyId = settings
                                .getString(DUO3_TIQIAN_MATH_FONT_PREFERENCE_KEY, "lete")
                                .takeIf { it == "stix" },
                            onLinkClick = onLinkClick,
                            header = header,
                            footer = footer,
                            onCreateSegment = onCreateSegment,
                            containingSegmentOf = containingSegmentProvider,
                            onCancelSegmentLike = onCancelSegmentLike,
                            searchState = searchState,
                        )
                    } else {
                        val latexFontFamilies = rememberMarkdownLatexFontFamilies()
                        val defaultTypography = MarkdownDefaults.typography()
                        val defaultLayout = MarkdownDefaults.layout()
                        val scaledFontSize = 16.sp * fontSize / 100
                        val typography = defaultTypography.copy(
                            text = defaultTypography.text.copy(
                                fontSize = scaledFontSize,
                                lineHeight = scaledFontSize * lineHeight / 100,
                            ),
                        )
                        val layout = defaultLayout.copy(
                            blockSpacing = defaultLayout.blockSpacing * (blockSpacing / 100f),
                        )
                        val mathFontSize = 18.sp * fontSize / 100
                        val latexTheme = LatexTheme.material3(MaterialTheme.colorScheme)
                        val latexConfig = if (latexFontFamilies == null) {
                            LatexConfig(fontSize = mathFontSize, theme = latexTheme)
                        } else {
                            LatexConfig(
                                fontSize = mathFontSize,
                                fontFamilies = latexFontFamilies,
                                theme = latexTheme,
                            )
                        }
                        Markdown(
                            document = document,
                            enableScroll = enableScroll,
                            externalScrollState = scrollState.takeUnless { enableScroll },
                            enableSelection = selectable,
                            textSearchState = (searchState as? ComposeMarkdownSearchState)?.rendererState,
                            /** 默认 colors() 不投影当前 Material 色表，划线背景由装饰槽替代。 */
                            colors = MarkdownDefaults
                                .material3Colors()
                                .copy(highlight = Color.Transparent),
                            typography = typography,
                            layout = layout,
                            latexConfig = latexConfig,
                            components = MarkdownComponents(
                                header = header,
                                footer = footer,
                                selectionToolbar = { info, actions ->
                                    SegmentSelectionToolbar(
                                        info = info,
                                        actions = actions,
                                        document = document,
                                        sourceHtml = sourceHtml,
                                        onCreateSegment = onCreateSegment,
                                        onDismiss = actions::dismiss,
                                        containingSegmentOf = containingSegmentProvider,
                                        onCancelLike = onCancelSegmentLike,
                                    )
                                },
                                image = { data, imageModifier ->
                                    RenderImage(
                                        data = data,
                                        modifier = imageModifier,
                                        imageUrls = previewImageUrls,
                                    )
                                },
                            ),
                            interactions = MarkdownInteractions(
                                onLinkClick = onLinkClick,
                                onImageClick = { event ->
                                    openImageGallery(event.gallery.images.map { it.image.url }, event.gallery.initialIndex)
                                },
                                onInlineElementClick = onInlineElementClick,
                                inlineDecoration = inlineDecoration,
                            ),
                            directivePlugins = remember { listOf(ZhihuVideoDirectivePlugin) },
                        )
                    }
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

private object ZhihuVideoDirectivePlugin : MarkdownDirectivePlugin {
    override val id: String = "zhihu-video"
    override val blockDirectiveRenderers: Map<String, MarkdownBlockDirectiveRenderer> = mapOf(
        "zhihu-video" to { scope ->
            val videoId = scope.directive.args["id"]?.toLongOrNull()
            val thumbnailUrl = scope.directive.args["thumb"]?.takeIf(String::isNotBlank)
            if (videoId != null) {
                RenderVideoBox(videoId = videoId, thumbnailUrl = thumbnailUrl)
            }
        },
    )
}
