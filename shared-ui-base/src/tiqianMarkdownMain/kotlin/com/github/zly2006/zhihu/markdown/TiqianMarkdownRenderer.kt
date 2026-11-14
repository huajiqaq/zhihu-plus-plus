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
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.platform.rememberImageSaver
import com.github.zly2006.zhihu.platform.rememberImageSharer
import com.github.zly2006.zhihu.ui.components.LocalOnSegmentHighlightClick
import com.github.zly2006.zhihu.ui.components.LocalSegmentMetaStates
import com.github.zly2006.zhihu.ui.components.LocalSegmentRemovedHighlights
import com.github.zly2006.zhihu.ui.components.SegmentCreationRange
import com.github.zly2006.zhihu.ui.components.resolveSegmentDecorationState
import com.github.zly2006.zhihu.ui.components.threadKey
import com.github.zly2006.zhihu.ui.components.toSegmentHighlightSpan
import com.github.zly2006.zhihu.util.SegmentHighlightSpan
import com.github.zly2006.zhihu.viewmodel.comment.SegmentCommentEvents
import com.hrm.markdown.parser.ast.ContainerNode
import com.hrm.markdown.parser.ast.Document
import com.hrm.markdown.parser.ast.Node
import com.hrm.markdown.parser.ast.Paragraph
import com.hrm.markdown.renderer.MarkdownImageData
import org.tiqian.compose.material3.CjkText
import org.tiqian.compose.rememberCjkSelectionState
import org.tiqian.compose.ruby
import org.tiqian.markdown.MarkdownBlock
import org.tiqian.markdown.MarkdownBlockQuote
import org.tiqian.markdown.MarkdownContainerBlock
import org.tiqian.markdown.MarkdownCustomBlock
import org.tiqian.markdown.MarkdownFootnoteDefinition
import org.tiqian.markdown.MarkdownImageBlock
import org.tiqian.markdown.MarkdownList
import org.tiqian.markdown.MarkdownNodeKey
import org.tiqian.markdown.MarkdownParagraph
import org.tiqian.markdown.MarkdownTextMark
import org.tiqian.markdown.compose.MarkdownBlockSlots
import org.tiqian.markdown.compose.MarkdownCustomInlinePresentation
import org.tiqian.markdown.compose.MarkdownImageContent
import org.tiqian.markdown.compose.MarkdownImageLoadState
import org.tiqian.markdown.compose.MarkdownImageProvider
import org.tiqian.markdown.compose.MarkdownInlineContent
import org.tiqian.markdown.compose.MarkdownInlineDecoration
import org.tiqian.markdown.compose.MarkdownInlineEndMarker
import org.tiqian.markdown.compose.MarkdownInlineMetrics
import org.tiqian.markdown.compose.MarkdownInlineSlots
import org.tiqian.markdown.compose.MarkdownMathFont
import org.tiqian.markdown.compose.MarkdownSelectionKey
import org.tiqian.markdown.compose.MarkdownStyle
import org.tiqian.markdown.compose.MarkdownTextSearchState
import org.tiqian.markdown.compose.TiqianMarkdownSurface
import org.tiqian.markdown.compose.rememberMarkdownStyle
import org.tiqian.markdown.compose.rememberMarkdownTextSearchBringIntoViewSpec
import org.tiqian.markdown.compose.rememberMarkdownTextSearchState
import org.tiqian.markdown.compose.withBlockSpacingScale
import org.tiqian.markdown.compose.withMarkdownReadingScale

actual val isTiqianMarkdownRendererAvailable: Boolean = true

@Composable
internal actual fun rememberTiqianMarkdownSearchState(): MarkdownSearchState {
    val state = rememberMarkdownTextSearchState()
    return remember(state) { TiqianMarkdownSearchState(state) }
}

private class TiqianMarkdownSearchState(
    val rendererState: MarkdownTextSearchState,
) : MarkdownSearchState {
    init {
        rendererState.highlightAllMatches = true
        rendererState.allMatchHighlightColor = MarkdownSearchMatchColor
        rendererState.highlightColor = MarkdownSearchActiveMatchColor
    }

    override val matchCount: Int get() = rendererState.matches.size

    override fun setQuery(query: String) = rendererState.setQuery(query)

    override suspend fun reveal(index: Int): Boolean = rendererState.matches.getOrNull(index)?.let { rendererState.reveal(it) } ?: false

    override fun clear() = rendererState.clear()

    @Composable
    override fun rememberBringIntoViewSpec(obscuredTopPx: Float, obscuredBottomPx: Float, fallback: BringIntoViewSpec): BringIntoViewSpec =
        rememberMarkdownTextSearchBringIntoViewSpec(rendererState, obscuredTopPx, obscuredBottomPx, fallback)
}

@Composable
actual fun PlatformTiqianMarkdown(
    document: Document,
    sourceMarkdown: String?,
    sourceHtml: String?,
    segmentCommentSnapshot: SegmentCommentEvents.Snapshot?,
    imageUrls: List<String>,
    scrollState: ScrollState,
    selectable: Boolean,
    enableScroll: Boolean,
    fontSizeScale: Float,
    lineHeightFromFontSize: Float,
    blockSpacingScale: Float,
    mathFontFamilyId: String?,
    onLinkClick: (String) -> Unit,
    header: (@Composable () -> Unit)?,
    footer: (@Composable () -> Unit)?,
    onCreateSegment: ((SegmentCreationRange?, Boolean) -> Unit)?,
    containingSegmentOf: ((SegmentCreationRange) -> SegmentHighlightSpan?)?,
    onCancelSegmentLike: ((SegmentCreationRange) -> Unit)?,
    searchState: MarkdownSearchState?,
) {
    val compiled = remember(document, sourceMarkdown) {
        TiqianRenderDocumentCompiler().compile(document, sourceMarkdown)
    }
    val style = rememberZhihuMarkdownStyle(
        fontSizeScale = fontSizeScale,
        lineHeightFromFontSize = lineHeightFromFontSize,
        blockSpacingScale = blockSpacingScale,
        mathFontFamilyId = mathFontFamilyId,
    )
    val inlineSlots = rememberZhihuInlineSlots(imageUrls, segmentCommentSnapshot)
    val onSegmentClick by rememberUpdatedState(LocalOnSegmentHighlightClick.current)
    val removedHighlights = LocalSegmentRemovedHighlights.current
    val imageProvider: MarkdownImageProvider = { block ->
        ZhihuMarkdownImageContent(block)
    }
    val saveImage = rememberImageSaver()
    val shareImage = rememberImageSharer()
    val slots = MarkdownBlockSlots(
        customBlock = { block, _ ->
            if (block.kind == "zhihu-video") {
                val videoId = block.attributes["id"]?.toLongOrNull()
                if (videoId != null) {
                    RenderVideoBox(
                        videoId = videoId,
                        thumbnailUrl = block.attributes["thumb"]?.takeIf(String::isNotBlank),
                    )
                }
            }
        },
    )
    val selectionState = rememberCjkSelectionState()
    val paragraphsByKey = remember(compiled) {
        val paragraphs = mutableMapOf<MarkdownNodeKey, MarkdownParagraph>()

        fun collect(blocks: List<MarkdownBlock>) {
            blocks.forEach { block ->
                when (block) {
                    is MarkdownParagraph -> paragraphs[block.metadata.key] = block
                    is MarkdownBlockQuote -> collect(block.blocks)
                    is MarkdownList -> block.items.forEach { collect(it.blocks) }
                    is MarkdownFootnoteDefinition -> collect(block.blocks)
                    is MarkdownContainerBlock -> collect(block.blocks)
                    is MarkdownCustomBlock -> collect(block.blocks)
                    else -> Unit
                }
            }
        }
        collect(compiled.blocks)
        paragraphs
    }
    val selectionRange: () -> SegmentCreationRange? = {
        val range = selectionState.selectedRanges?.singleOrNull()
        val key = range?.fragmentKey as? MarkdownSelectionKey
        var node: Node? = document
        key?.node?.path?.forEach { index ->
            node = (node as? ContainerNode)?.children?.getOrNull(index)
        }
        val paragraph = (node as? Paragraph)?.takeIf {
            key?.role == "text" && it.stableKey == key.node.parserStableKey
        }
        val rendered = key?.node?.let(paragraphsByKey::get)
        val offsetsAreSafe = range != null &&
            rendered != null &&
            rendered.text.value == range.fragmentText.text &&
            rendered.text.issues.isEmpty() &&
            rendered.text.spans.none { span ->
                span.mark is MarkdownTextMark.InlineImage ||
                    span.mark is MarkdownTextMark.InlineMath ||
                    span.mark is MarkdownTextMark.Ruby
            }
        if (range == null || sourceHtml == null || !offsetsAreSafe) {
            null
        } else {
            validatePlainTextSegmentSelection(
                sourceHtml = sourceHtml,
                document = document,
                selectedText = selectionState.selectedText?.text.orEmpty(),
                ranges = listOf(SegmentSelectionRange(paragraph, range.fragmentText.text, range.start, range.end)),
            )?.let {
                SegmentCreationRange(it.paragraphId, it.startOffset, it.endOffset, it.text)
            }
        }
    }
    TiqianSelectionMenuHost(
        selectionState = selectionState,
        selectionRange = selectionRange,
        liked = { containingSegmentOf?.invoke(it)?.meta?.isLike == true },
        onMenuShown = {
            val selected = selectionState.selectedRanges?.singleOrNull()
            val range = selectionRange()
            val containing = range?.let { containingSegmentOf?.invoke(it) }
            val start = containing?.startOffset
            val end = containing?.endOffset
            if (selected != null &&
                range != null &&
                start != null &&
                end != null &&
                (start != range.startOffset || end != range.endOffset)
            ) {
                selectionState.selectRange(selected.fragmentKey, start, end)
            }
        },
        onLike = onCreateSegment?.let { create -> { range -> create(range, false) } },
        onComment = onCreateSegment?.let { create -> { range -> create(range, true) } },
        onCancelLike = onCancelSegmentLike,
    ) {
        TiqianMarkdownSurface(
            document = compiled,
            modifier = Modifier.testTag("tiqian_markdown_content"),
            style = style,
            slots = slots,
            inlineSlots = inlineSlots,
            scrollState = scrollState,
            selectable = selectable,
            selectionState = selectionState,
            searchState = (searchState as? TiqianMarkdownSearchState)?.rendererState,
            enableScroll = enableScroll,
            imageProvider = imageProvider,
            imageViewerActions = { image ->
                ZhihuImageViewerActionButton(onClick = { saveImage(image.destination) }) {
                    Icon(
                        imageVector = Icons.Outlined.FileDownload,
                        contentDescription = "下载图片",
                        tint = Color.White,
                    )
                }
                Spacer(Modifier.width(4.dp))
                ZhihuImageViewerActionButton(onClick = { shareImage(image.destination) }) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "分享图片",
                        tint = Color.White,
                    )
                }
            },
            onNodeClick = { event ->
                if (event.nodeType == "Highlight") {
                    val span = event.attributes.toSegmentHighlightSpan(fallbackText = event.text)
                    val segmentClick = onSegmentClick
                    /** 点击须读取实时取消态，不能使用布局装饰的旧快照。 */
                    if (span != null && removedHighlights[span.threadKey()] != true && segmentClick != null) {
                        segmentClick(span)
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            },
            onLinkClick = onLinkClick,
            header = header,
            footer = footer,
        )
    }
}

@Composable
private fun rememberZhihuMarkdownStyle(
    fontSizeScale: Float,
    lineHeightFromFontSize: Float,
    blockSpacingScale: Float,
    mathFontFamilyId: String?,
): MarkdownStyle =
    rememberMarkdownStyle(
        defaultStyle = MarkdownStyle()
            .let { base ->
                base.copy(
                    proseMeasure = base.proseMeasure.copy(enabled = !LocalMarkdownWidthManagedByHost.current),
                    body = base.body.withMarkdownReadingScale(
                        fontSizeScale = fontSizeScale,
                        lineHeightFromFontSize = lineHeightFromFontSize,
                    ),
                )
            }.let { base ->
                base.copy(
                    math = base.math.copy(
                        font = mathFontFamilyId?.let(MarkdownMathFont::Packaged)
                            ?: MarkdownMathFont.LeteSansMath,
                        displayScrollHostInset = 16.dp,
                    ),
                )
            }.withBlockSpacingScale(blockSpacingScale),
    )

@Composable
private fun ZhihuImageViewerActionButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.24f),
        shape = CircleShape,
    ) {
        IconButton(onClick = onClick, content = content)
    }
}

@Composable
private fun rememberZhihuInlineSlots(
    imageUrls: List<String>,
    commentSnapshot: SegmentCommentEvents.Snapshot?,
): MarkdownInlineSlots {
    val onSegmentHighlightClick = LocalOnSegmentHighlightClick.current
    val segmentUnderlineColor = MaterialTheme.colorScheme.outlineVariant
    val segmentCommentColor = MaterialTheme.colorScheme.primary
    val segmentCommentPainter = rememberVectorPainter(Icons.AutoMirrored.Filled.Comment)
    val density = LocalDensity.current
    val metaStates = LocalSegmentMetaStates.current
    val removedHighlights = LocalSegmentRemovedHighlights.current

    /** 装饰回调运行于布局阶段，状态须在组合期读取才能订阅重组。 */
    val metaSnapshot = metaStates.toMap()
    val removedSnapshot = removedHighlights.toMap()
    val commentDeltas = commentSnapshot?.asMap().orEmpty()
    val ownCommentDeltas = commentSnapshot?.asMap(ownComments = true).orEmpty()

    return MarkdownInlineSlots(
        image = { mark, _, _ ->
            val width = (mark.widthPixels?.toFloat() ?: 200f).sp
            val height = (mark.heightPixels?.toFloat() ?: 150f).sp
            val widthPx = with(density) { width.toPx() }
            val heightPx = with(density) { height.toPx() }
            MarkdownInlineContent(
                alternateText = "",
                placeholder = Placeholder(
                    width = width,
                    height = height,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                ),
                /** 行内图片没有文字基线，使用已知盒子底边。 */
                metrics = MarkdownInlineMetrics(
                    widthPx = widthPx,
                    heightPx = heightPx,
                    baselineFromTopPx = heightPx,
                ),
            ) {
                Box(Modifier.testTag("tiqian_inline_image")) {
                    RenderImage(
                        data = MarkdownImageData(
                            url = mark.destination,
                            altText = mark.description,
                            title = mark.title,
                            width = mark.widthPixels,
                            height = mark.heightPixels,
                            attributes = mark.attributes,
                        ),
                        modifier = Modifier,
                        imageUrls = imageUrls,
                    )
                }
            }
        },
        custom = { mark, _, _ ->
            if (mark.kind != ZHIHU_SEGMENT_HIGHLIGHT_KIND) {
                null
            } else {
                val span = mark.attributes.toSegmentHighlightSpan()
                val meta = span?.let { segmentMeta ->
                    val live = metaSnapshot[segmentMeta.threadKey()]
                    segmentMeta.meta.copy(isLike = live?.isLike ?: segmentMeta.meta.isLike, likeCount = live?.likeCount ?: segmentMeta.meta.likeCount)
                }
                val localDelta = span
                    ?.meta
                    ?.segIds
                    ?.map(String::trim)
                    ?.distinct()
                    ?.sumOf { commentDeltas[it] ?: 0 } ?: 0
                val state = resolveSegmentDecorationState(
                    meta = meta,
                    removed = span != null && removedSnapshot[span.threadKey()] == true,
                    localCommentDelta = localDelta,
                    localOwnCommentDelta = span
                        ?.meta
                        ?.segIds
                        ?.map(String::trim)
                        ?.distinct()
                        ?.sumOf { ownCommentDeltas[it] ?: 0 } ?: 0,
                )
                if (!state.visible) {
                    null
                } else {
                    val underlineColor = if (state.hasComments) segmentCommentColor else segmentUnderlineColor
                    MarkdownCustomInlinePresentation(
                        decoration = if (state.mine) {
                            /** 上游虚线要求正 gap，长 dash 表达实线。 */
                            MarkdownInlineDecoration.DashedUnderline(
                                color = underlineColor,
                                strokeWidth = if (state.hasComments) 1.5.dp else 1.dp,
                                dashWidth = 10_000.dp,
                                gapWidth = 0.5.dp,
                            )
                        } else {
                            MarkdownInlineDecoration.DottedUnderline(
                                color = underlineColor,
                                dotDiameter = 2.dp,
                                gapWidth = 3.dp,
                            )
                        },
                        onClick = onSegmentHighlightClick?.let { callback ->
                            { text -> mark.attributes.toSegmentHighlightSpan(fallbackText = text)?.let(callback) }
                        },
                        accessibilityLabel = { text -> "打开划线片段：$text" },
                        endMarker = if (state.hasComments) {
                            MarkdownInlineEndMarker(painter = segmentCommentPainter, color = segmentCommentColor)
                        } else {
                            null
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun ZhihuMarkdownImageContent(block: MarkdownImageBlock): MarkdownImageContent {
    val widthPixels = block.widthPixels
    val heightPixels = block.heightPixels
    val retained = retainedImageOutcomes[block.destination]
    var intrinsicSize by remember(block.destination) {
        mutableStateOf(
            retained?.intrinsicSize
                ?: if (widthPixels != null && heightPixels != null) {
                    IntSize(widthPixels, heightPixels)
                } else {
                    null
                },
        )
    }
    var loadState by remember(block.destination) {
        mutableStateOf(retained?.loadState ?: MarkdownImageLoadState.Loading)
    }
    val model = rememberMarkdownImageModel(block.destination)
    val description = block.description.ifBlank { null }
    return MarkdownImageContent(
        intrinsicSize = intrinsicSize,
        loadState = loadState,
    ) { modifier ->
        AsyncImage(
            model = model,
            contentDescription = description,
            modifier = modifier,
            onLoading = {
                /** 已完成的图片重入不能退回加载高度，避免改变阅读位置。 */
                if (retainedImageOutcomes[block.destination] == null) {
                    loadState = MarkdownImageLoadState.Loading
                }
            },
            onSuccess = { state ->
                val image = state.result.image
                if (image.width > 0 && image.height > 0) {
                    intrinsicSize = IntSize(image.width, image.height)
                }
                loadState = MarkdownImageLoadState.Success
                retainedImageOutcomes[block.destination] =
                    RetainedImageOutcome(MarkdownImageLoadState.Success, intrinsicSize)
            },
            onError = {
                loadState = MarkdownImageLoadState.Error
                retainedImageOutcomes[block.destination] =
                    RetainedImageOutcome(MarkdownImageLoadState.Error, intrinsicSize)
            },
            contentScale = ContentScale.Fit,
        )
    }
}

/** URL 完成态保留至进程结束，回收后重入不回退图片几何。 */
private data class RetainedImageOutcome(
    val loadState: MarkdownImageLoadState,
    val intrinsicSize: IntSize?,
)

private val retainedImageOutcomes = mutableMapOf<String, RetainedImageOutcome>()

@Composable
actual fun TiqianBrandTitle(prefix: String, suffix: String) {
    CjkText(
        text = buildAnnotatedString {
            append(prefix)
            ruby("提", "tí")
            ruby("椠", "qiàn")
            append(suffix)
        },
    )
}
