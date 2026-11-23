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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.zly2006.zhihu.data.SegmentInfoMeta
import com.github.zly2006.zhihu.platform.ImageGalleryOpener
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.ui.components.SegmentCreationRange
import com.github.zly2006.zhihu.ui.components.SegmentSelectionToolbar
import com.github.zly2006.zhihu.ui.components.resolveSegmentDecorationState
import com.github.zly2006.zhihu.ui.components.threadKey
import com.github.zly2006.zhihu.ui.components.toSegmentHighlightSpan
import com.github.zly2006.zhihu.ui.subscreens.TIQIAN_MATH_FONT_PREFERENCE_KEY
import com.github.zly2006.zhihu.util.SegmentHighlightSpan
import com.github.zly2006.zhihu.viewmodel.comment.SegmentCommentEvents
import com.hrm.latex.renderer.model.LatexConfig
import com.hrm.latex.renderer.model.LatexTheme
import com.hrm.markdown.parser.ast.Document
import com.hrm.markdown.renderer.InlineElementKinds
import com.hrm.markdown.renderer.Markdown
import com.hrm.markdown.renderer.MarkdownComponents
import com.hrm.markdown.renderer.MarkdownDefaults
import com.hrm.markdown.renderer.MarkdownInlineDecorationRequest
import com.hrm.markdown.renderer.MarkdownInlineElementClickEvent
import com.hrm.markdown.renderer.MarkdownInlineEndMarker
import com.hrm.markdown.renderer.MarkdownInlineUnderline
import com.hrm.markdown.renderer.MarkdownInteractions
import com.hrm.markdown.runtime.MarkdownBlockDirectiveRenderer
import com.hrm.markdown.runtime.MarkdownDirectivePlugin

/** 正文双击交给页面动作，不触发系统双击选词。 */
@Composable
internal fun NoDoubleClickSelectionScope(content: @Composable () -> Unit) {
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

internal fun renderMarkdownActiveSegmentMetas(document: Document): Map<String, SegmentInfoMeta> {
    val activeMetas = mutableMapOf<String, SegmentInfoMeta>()

    fun visit(node: com.hrm.markdown.parser.ast.Node) {
        if (node is com.hrm.markdown.parser.ast.Highlight) {
            node.attributes.toSegmentHighlightSpan()?.let { activeMetas[it.threadKey()] = it.meta }
        }
        if (node is com.hrm.markdown.parser.ast.ContainerNode) node.children.forEach(::visit)
    }
    visit(document)
    return activeMetas
}

@Composable
internal fun RenderMarkdownContent(
    document: Document,
    sourceMarkdown: String?,
    sourceHtml: String?,
    segmentCommentSnapshot: SegmentCommentEvents.Snapshot?,
    scrollState: ScrollState,
    selectable: Boolean,
    enableScroll: Boolean,
    header: (@Composable () -> Unit)?,
    footer: (@Composable () -> Unit)?,
    useTiqianRenderer: Boolean,
    searchState: MarkdownSearchState?,
    previewImageUrls: List<String>,
    fontSize: Int,
    lineHeight: Int,
    blockSpacing: Int,
    settings: SettingsStore,
    onLinkClick: (String) -> Unit,
    onInlineElementClick: (MarkdownInlineElementClickEvent) -> Unit,
    segmentUnderlineColor: Color,
    segmentCommentColor: Color,
    segmentCommentPainter: Painter,
    openImageGallery: ImageGalleryOpener,
    onCreateSegment: (SegmentCreationRange?, Boolean) -> Unit,
    containingSegmentProvider: (SegmentCreationRange) -> SegmentHighlightSpan?,
    onCancelSegmentLike: (SegmentCreationRange?) -> Unit,
    removedSegmentHighlights: Map<String, Boolean>,
    segmentMetaStates: Map<String, SegmentInfoMeta>,
) {
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
                .getString(TIQIAN_MATH_FONT_PREFERENCE_KEY, "lete")
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
        val latexConfig = LatexConfig(fontSize = mathFontSize, theme = latexTheme)
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
