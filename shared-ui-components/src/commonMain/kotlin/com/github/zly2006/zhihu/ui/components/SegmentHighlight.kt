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

package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.SegmentInfoMeta
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.i18n.WidgetsStrings
import com.github.zly2006.zhihu.markdown.SEGMENT_SELECTION_BLOCK_ATTRIBUTE
import com.github.zly2006.zhihu.markdown.SegmentSelectionRange
import com.github.zly2006.zhihu.markdown.expandPlainTextSegmentSelection
import com.github.zly2006.zhihu.markdown.validatePlainTextSegmentSelection
import com.github.zly2006.zhihu.navigation.SegmentCommentHolder
import com.github.zly2006.zhihu.platform.rememberPlainTextClipboard
import com.github.zly2006.zhihu.util.SegmentHighlightSpan
import com.github.zly2006.zhihu.util.raiseForStatus
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.comment.SegmentCommentEvents
import com.github.zly2006.zhihu.viewmodel.deleteSigned
import com.github.zly2006.zhihu.viewmodel.postSigned
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import com.hrm.markdown.parser.ast.ContainerNode
import com.hrm.markdown.parser.ast.Document
import com.hrm.markdown.parser.ast.Node
import com.hrm.markdown.parser.ast.Paragraph
import com.hrm.markdown.renderer.MarkdownSelectionActions
import com.hrm.markdown.renderer.MarkdownSelectionInfo
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** 弹层须在选区子树外承载，避免跨窗口换算选择坐标。 */
val LocalSegmentCommentHost = staticCompositionLocalOf<(SegmentCommentHolder) -> Unit> {
    error("LocalSegmentCommentHost is not provided")
}

data class SegmentActionSheetState(
    val highlight: SegmentHighlightSpan,
    val onDismiss: () -> Unit,
    val onLikeClick: () -> Unit,
    val onCommentClick: () -> Unit,
    val onCopyClick: () -> Unit,
)

val LocalSegmentActionSheetHost = staticCompositionLocalOf<(SegmentActionSheetState?) -> Unit> {
    error("LocalSegmentActionSheetHost is not provided")
}

val LocalOnSegmentHighlightClick = staticCompositionLocalOf<((SegmentHighlightSpan) -> Unit)?> { null }

val LocalOnRequestContentRefresh = staticCompositionLocalOf<(() -> Unit)?> { null }

data class SegmentSelectionTarget(
    val contentId: String,
    val contentType: String,
)

val LocalSegmentSelectionTarget = staticCompositionLocalOf<SegmentSelectionTarget?> { null }

val LocalSegmentMetaStates = staticCompositionLocalOf<Map<String, SegmentInfoMeta>> { emptyMap() }

val LocalSegmentRemovedHighlights = staticCompositionLocalOf<SnapshotStateMap<String, Boolean>> {
    mutableStateMapOf()
}

fun buildSegmentUnlikeBody(highlight: SegmentHighlightSpan): String = buildJsonObject {
    put("seg_ids", highlight.meta.segIds.joinToString(","))
}.toString()

fun buildSegmentLikeBody(highlight: SegmentHighlightSpan): String = buildJsonObject {
    if (highlight.meta.segIds.isNotEmpty()) {
        put("seg_id", highlight.meta.segIds.joinToString(","))
        if (highlight.meta.isSpan) return@buildJsonObject
    }
    put("content", highlight.text)
    put(
        "position",
        buildJsonObject {
            put(
                "start",
                buildJsonObject {
                    put("paragraph_id", highlight.paragraphId.orEmpty())
                    put("offset", highlight.startOffset ?: 0)
                },
            )
            put(
                "end",
                buildJsonObject {
                    put("paragraph_id", highlight.paragraphId.orEmpty())
                    put("offset", highlight.endOffset ?: 0)
                },
            )
        },
    )
}.toString()

fun updateSegmentMetaAfterUnlike(highlight: SegmentHighlightSpan): SegmentInfoMeta = highlight.meta.copy(
    isLike = false,
    likeCount = (highlight.meta.likeCount - 1).coerceAtLeast(0),
)

fun updateSegmentMetaAfterLike(
    highlight: SegmentHighlightSpan,
    response: JsonObject?,
): SegmentInfoMeta {
    val segId = response
        ?.get("payload")
        ?.jsonObject
        ?.get("segId")
        ?.jsonPrimitive
        ?.content
        ?.split(',')
        ?.filter(String::isNotEmpty)
        ?: highlight.meta.segIds
    return highlight.meta.copy(
        segIds = segId,
        isLike = true,
        likeCount = highlight.meta.likeCount + 1,
    )
}

/** 偏移沿用原始段落的 UTF-16 半开区间。 */
data class SegmentCreationRange(
    val paragraphId: String,
    val startOffset: Int,
    val endOffset: Int,
    val text: String,
)

enum class SegmentReactionFailure {
    LOGIN_REQUIRED,
    HTTP,
    MISSING_DATA,
    NETWORK,
}

class SegmentReactionException(
    val failure: SegmentReactionFailure,
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

/** 新建划线不传 seg_id。 */
suspend fun createSegmentReactions(
    environment: PaginationEnvironment,
    target: SegmentSelectionTarget,
    ranges: List<SegmentCreationRange>,
): List<Pair<SegmentCreationRange, String>> {
    if (environment.authenticatedCookies()["d_c0"].isNullOrBlank()) {
        throw SegmentReactionException(SegmentReactionFailure.LOGIN_REQUIRED, WidgetsStrings.segmentLoginRequired)
    }
    if (ranges.size > 1) {
        throw SegmentReactionException(SegmentReactionFailure.MISSING_DATA, WidgetsStrings.singleParagraphOnly)
    }
    if (ranges.isEmpty()) return emptyList()
    if (target.contentId.isBlank() || target.contentType.isBlank()) {
        throw SegmentReactionException(SegmentReactionFailure.MISSING_DATA, WidgetsStrings.missingContentReference)
    }
    val range = ranges.single()
    if (range.text.length < 5) {
        throw SegmentReactionException(SegmentReactionFailure.MISSING_DATA, WidgetsStrings.selectionTooShort)
    }
    val url = "https://www.zhihu.com/api/v4/reaction/${target.contentType}s/${target.contentId}/segment_reaction"
    try {
        val response = environment.postSigned(url) {
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("content", range.text)
                    put(
                        "position",
                        buildJsonObject {
                            put(
                                "start",
                                buildJsonObject {
                                    put("paragraph_id", range.paragraphId)
                                    put("offset", range.startOffset)
                                },
                            )
                            put(
                                "end",
                                buildJsonObject {
                                    put("paragraph_id", range.paragraphId)
                                    put("offset", range.endOffset)
                                },
                            )
                        },
                    )
                }.toString(),
            )
        }
        if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.Created) {
            throw SegmentReactionException(
                SegmentReactionFailure.HTTP,
                WidgetsStrings.highlightHttpError(response.status.value),
                statusCode = response.status.value,
            )
        }
        val bodyText = response.bodyAsText()
        val obj = try {
            ZhihuJson.json.parseToJsonElement(bodyText) as? JsonObject
        } catch (_: IllegalArgumentException) {
            null
        }
        val value = obj?.get("seg_id") ?: (obj?.get("payload") as? JsonObject)?.get("segId")
        val segId = (value as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.contentOrNull
            ?.split(',')
            ?.firstOrNull { it.isNotBlank() }
            ?: throw SegmentReactionException(SegmentReactionFailure.MISSING_DATA, WidgetsStrings.highlightMissingId)
        return listOf(range to segId)
    } catch (error: CancellationException) {
        throw error
    } catch (error: ResponseException) {
        throw SegmentReactionException(
            SegmentReactionFailure.HTTP,
            WidgetsStrings.highlightHttpError(error.response.status.value),
            statusCode = error.response.status.value,
        )
    } catch (error: IOException) {
        throw SegmentReactionException(SegmentReactionFailure.NETWORK, WidgetsStrings.highlightNetworkError, cause = error)
    }
}

/** 选区被已有划线包住时，使用包含它的最大完整范围，不用重复文字猜偏移。 */
fun findContainingSegmentForSelection(
    range: SegmentCreationRange,
    document: Document,
    metaStates: Map<String, SegmentInfoMeta> = emptyMap(),
    removedHighlights: Map<String, Boolean> = emptyMap(),
): SegmentHighlightSpan? {
    val matches = mutableListOf<SegmentHighlightSpan>()

    fun visit(node: Node) {
        if (node is com.hrm.markdown.parser.ast.Highlight) {
            val nodeText = node.children.readablePlainText()
            val original = node.attributes.toSegmentHighlightSpan(fallbackText = nodeText)
            val span = original?.let {
                val live = metaStates[it.threadKey()]
                it.copy(meta = it.meta.copy(isLike = live?.isLike ?: it.meta.isLike, likeCount = live?.likeCount ?: it.meta.likeCount))
            }
            val start = span?.startOffset
            val end = span?.endOffset
            if (span != null &&
                removedHighlights[span.threadKey()] != true &&
                span.paragraphId == range.paragraphId &&
                start != null &&
                end != null &&
                start <= range.startOffset &&
                end >= range.endOffset
            ) {
                matches += span
            }
        }
        if (node is ContainerNode) node.children.forEach(::visit)
    }
    document.children.forEach(::visit)
    val containing = matches
        .distinctBy(SegmentHighlightSpan::threadKey)
        .maxByOrNull { (it.endOffset ?: 0) - (it.startOffset ?: 0) } ?: return null
    return if (containing.meta.isSpan) containing.withCompleteText(document.segmentHighlights()) else containing
}

internal expect val hrmSelectionToolbarPopupProperties: PopupProperties

@Composable
fun SegmentSelectionToolbar(
    info: MarkdownSelectionInfo,
    actions: MarkdownSelectionActions,
    document: Document,
    sourceHtml: String?,
    onCreateSegment: (SegmentCreationRange?, Boolean) -> Unit,
    containingSegmentOf: ((SegmentCreationRange) -> SegmentHighlightSpan?)? = null,
    onCancelLike: ((SegmentCreationRange?) -> Unit)? = null,
) {
    val selection = remember(info, document) {
        val paragraphs = mutableListOf<Paragraph>()

        fun collect(node: Node) {
            if (node is Paragraph) paragraphs += node
            if (node is ContainerNode) node.children.forEach(::collect)
        }
        collect(document)
        info.ranges.map { selected ->
            val block = selected.blockAttributes[SEGMENT_SELECTION_BLOCK_ATTRIBUTE]
            SegmentSelectionRange(
                paragraph = paragraphs.singleOrNull {
                    block != null &&
                        it.blockAttributes[SEGMENT_SELECTION_BLOCK_ATTRIBUTE] == block &&
                        it.blockAttributes["data-pid"] == selected.blockAttributes["data-pid"]
                },
                blockText = selected.blockText,
                startOffset = selected.startInBlock,
                endOffset = selected.endInBlock,
            )
        }
    }
    val range = remember(info, document, sourceHtml, selection) {
        sourceHtml?.let { validatePlainTextSegmentSelection(it, document, info.text, selection) }?.let {
            SegmentCreationRange(it.paragraphId, it.startOffset, it.endOffset, it.text)
        }
    }

    val containing = range?.let { containingSegmentOf?.invoke(it) }
    val selectedBlock = info.ranges.singleOrNull()
    /** 仅在工具栏初次出现时对齐已有划线；拖动中再次 selectRange 会结束 HRM 正在处理的手柄手势。 */
    LaunchedEffect(actions) {
        val start = containing?.startOffset
        val end = containing?.endOffset
        val selected = selection.singleOrNull()
        if (range != null &&
            selectedBlock != null &&
            selected != null &&
            sourceHtml != null &&
            start != null &&
            end != null &&
            (start != range.startOffset || end != range.endOffset)
        ) {
            expandPlainTextSegmentSelection(sourceHtml, document, selected, start, end)?.let {
                actions.selectRange(selectedBlock.blockStableId, it.startOffset, it.endOffset)
            }
        }
    }

    fun createAndFinish(openCommentEditor: Boolean) {
        actions.dismiss()
        onCreateSegment(range, openCommentEditor)
    }

    /** 向下弹出时，行底以下仍是手柄的触摸区，不属于菜单。 */
    val targetHeight = LocalViewConfiguration.current.minimumTouchTargetSize.height
    val bounds = info.boundsInWindow
    val boundsWithHandles = bounds.copy(bottom = bounds.bottom + with(LocalDensity.current) { targetHeight.toPx() / 2f })
    Popup(
        popupPositionProvider = remember(boundsWithHandles) { SegmentToolbarPositionProvider(boundsWithHandles) },
        onDismissRequest = actions::dismiss,
        properties = hrmSelectionToolbarPopupProperties,
    ) {
        SegmentSelectionMenu(
            actions = buildSegmentSelectionMenuActions(
                selectedText = info.text,
                liked = containing?.meta?.isLike == true,
                onCopy = actions::copySelection,
                onSelectAll = actions::selectAll,
                onLike = { createAndFinish(openCommentEditor = false) },
                onComment = { createAndFinish(openCommentEditor = true) },
                onCancelLike = onCancelLike?.let { cancel ->
                    { _ ->
                        actions.dismiss()
                        cancel(range)
                    }
                },
            ),
        )
    }
}

class SegmentSelectionMenuItem(
    val label: String,
    val onClick: () -> Unit,
)

data class SegmentDecorationState(
    val visible: Boolean,
    val mine: Boolean,
    val hasComments: Boolean,
)

fun resolveSegmentDecorationState(
    meta: SegmentInfoMeta?,
    removed: Boolean,
    localCommentDelta: Int = 0,
    localOwnCommentDelta: Int = 0,
): SegmentDecorationState {
    if (removed) return SegmentDecorationState(visible = false, mine = false, hasComments = false)
    return SegmentDecorationState(
        visible = true,
        mine = meta != null && (meta.isLike || (meta.commentCount + localCommentDelta > 0 && meta.myCommentCount + localOwnCommentDelta > 0)),
        hasComments = meta != null && meta.commentCount + localCommentDelta > 0,
    )
}

/** 调用方负责关闭菜单。 */
fun buildSegmentSelectionMenuActions(
    selectedText: String,
    liked: Boolean,
    onCopy: () -> Unit,
    onSelectAll: () -> Unit,
    onLike: ((String) -> Unit)?,
    onComment: ((String) -> Unit)?,
    onCancelLike: ((String) -> Unit)?,
): List<SegmentSelectionMenuItem> = buildList {
    add(SegmentSelectionMenuItem(WidgetsStrings.copy, onCopy))
    add(SegmentSelectionMenuItem(WidgetsStrings.selectAll, onSelectAll))
    if (liked && onCancelLike != null) {
        add(SegmentSelectionMenuItem(WidgetsStrings.unlike) { onCancelLike(selectedText) })
    } else if (onLike != null) {
        add(SegmentSelectionMenuItem(WidgetsStrings.like) { onLike(selectedText) })
    }
    if (onComment != null) {
        add(SegmentSelectionMenuItem(WidgetsStrings.comment) { onComment(selectedText) })
    }
}

@Composable
fun SegmentSelectionMenu(actions: List<SegmentSelectionMenuItem>) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (action in actions) {
                TextButton(onClick = action.onClick) {
                    Text(
                        text = action.label,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

class SegmentToolbarPositionProvider(
    private val boundsInWindow: Rect,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val margin = 8f
        val gap = 12f
        val x = (boundsInWindow.center.x - popupContentSize.width / 2f)
            .coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
        val above = (boundsInWindow.top - popupContentSize.height - gap).coerceAtLeast(margin)
        val y = if (above <= margin + gap &&
            boundsInWindow.bottom + gap + popupContentSize.height < windowSize.height - margin
        ) {
            boundsInWindow.bottom + gap
        } else {
            above
        }
        return IntOffset(x.roundToInt(), y.roundToInt())
    }
}

@Composable
fun SegmentHighlightInteractionHost(
    document: Document,
    metaStates: SnapshotStateMap<String, SegmentInfoMeta> = remember { mutableStateMapOf() },
    commentSnapshot: SegmentCommentEvents.Snapshot? = null,
    onError: (String) -> Unit = {},
    onOpenQuotedComments: ((SegmentCommentHolder) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val environment = rememberPaginationEnvironment(allowGuestAccess = false)
    val accountStore = rememberZhihuAccountStore()
    val copyPlainText = rememberPlainTextClipboard()
    val coroutineScope = rememberCoroutineScope()
    val removedHighlights = LocalSegmentRemovedHighlights.current
    val requestContentRefresh = LocalOnRequestContentRefresh.current
    var selectedHighlight by remember(document, commentSnapshot) { mutableStateOf<Pair<String, SegmentHighlightSpan>?>(null) }
    val openSegmentComments = LocalSegmentCommentHost.current
    val showSegmentActionSheet = LocalSegmentActionSheetHost.current
    val highlights = remember(document) { document.segmentHighlights() }
    val onSegmentHighlightClick: (SegmentHighlightSpan) -> Unit = remember(highlights, commentSnapshot, removedHighlights) {
        { span ->
            val key = span.threadKey()
            val fragments = highlights.filter { it.threadKey() == key }

            /** 点击事件可能只含单个视觉 run，请求必须沿原始属性取完整片段。 */
            val original = fragments.firstOrNull {
                it.paragraphId == span.paragraphId && it.startOffset == span.startOffset && it.endOffset == span.endOffset
            } ?: span
            if (removedHighlights[key] != true) selectedHighlight = key to original.withCompleteText(fragments)
        }
    }

    CompositionLocalProvider(
        LocalOnSegmentHighlightClick provides onSegmentHighlightClick,
        content = content,
    )

    val selectedKey = selectedHighlight?.first
    val selected = selectedHighlight?.second
    val baseMeta = selected?.meta?.let { original ->
        selectedKey?.let(metaStates::get)?.let { updated ->
            original.copy(isLike = updated.isLike, likeCount = updated.likeCount)
        } ?: original
    }
    val localDelta = selected?.meta?.segIds?.let { commentSnapshot?.effectiveDelta(it) } ?: 0
    val ownDelta = selected?.meta?.segIds?.let { commentSnapshot?.effectiveDelta(it, ownComments = true) } ?: 0
    val selectedMeta = baseMeta?.copy(
        commentCount = (baseMeta.commentCount + localDelta).coerceAtLeast(0),
        myCommentCount = (baseMeta.myCommentCount + ownDelta).coerceAtLeast(0),
    )
    LaunchedEffect(selected, selectedMeta) {
        if (selected == null || selectedKey == null || selectedMeta == null) {
            showSegmentActionSheet(null)
            return@LaunchedEffect
        }
        val commentTarget = selected.copy(meta = selectedMeta).toSegmentCommentHolder()
        if (selectedMeta.commentCount > 0 && commentTarget != null && onOpenQuotedComments == null) {
            selectedHighlight = null
            showSegmentActionSheet(null)
            openSegmentComments(commentTarget)
            return@LaunchedEffect
        }
        showSegmentActionSheet(
            SegmentActionSheetState(
                highlight = selected.copy(meta = selectedMeta),
                onDismiss = {
                    selectedHighlight = null
                    showSegmentActionSheet(null)
                },
                onLikeClick = {
                    if (!accountStore.session.login) {
                        onError(WidgetsStrings.segmentLoginRequired)
                    } else {
                        coroutineScope.launch {
                            try {
                                val updatedMeta = toggleSegmentLike(environment, selected.copy(meta = baseMeta))
                                metaStates[selectedKey] = updatedMeta
                                if (updatedMeta.isLike) removedHighlights.remove(selectedKey)
                                if (!updatedMeta.isLike &&
                                    updatedMeta.likeCount == 0 &&
                                    updatedMeta.commentCount + localDelta <= 0
                                ) {
                                    removedHighlights[selectedKey] = true
                                    requestContentRefresh?.invoke()
                                }
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                onError(WidgetsStrings.segmentActionFailed(error.message ?: WidgetsStrings.tryAgain))
                            }
                        }
                    }
                },
                onCommentClick = {
                    when {
                        commentTarget == null -> onError(WidgetsStrings.missingParagraphLocation)
                        onOpenQuotedComments != null -> onOpenQuotedComments(commentTarget)
                        else -> {
                            selectedHighlight = null
                            showSegmentActionSheet(null)
                            openSegmentComments(commentTarget)
                        }
                    }
                },
                onCopyClick = {
                    copyPlainText(WidgetsStrings.highlightedPassage, selected.displayText)
                    selectedHighlight = null
                    showSegmentActionSheet(null)
                },
            ),
        )
        if (selectedMeta.commentCount > 0 && commentTarget != null) {
            onOpenQuotedComments?.invoke(commentTarget)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            showSegmentActionSheet(null)
        }
    }
}

suspend fun toggleSegmentLike(
    environment: PaginationEnvironment,
    highlight: SegmentHighlightSpan,
): SegmentInfoMeta {
    check(!environment.authenticatedCookies()["d_c0"].isNullOrBlank()) { WidgetsStrings.segmentLoginRequired }
    val contentId = checkNotNull(highlight.contentId?.takeIf(String::isNotBlank)) { WidgetsStrings.missingContentId }
    val targetType = checkNotNull(highlight.contentType?.takeIf(String::isNotBlank)) { WidgetsStrings.missingContentType }
    val url = "https://www.zhihu.com/api/v4/reaction/${targetType}s/$contentId/segment_reaction"

    return if (highlight.meta.isLike) {
        val response = environment
            .deleteSigned(url) {
                contentType(ContentType.Application.Json)
                setBody(buildSegmentUnlikeBody(highlight))
            }.raiseForStatus()
        check(response.status.value in 200..299) { WidgetsStrings.segmentUnlikeHttpError(response.status.value) }
        updateSegmentMetaAfterUnlike(highlight)
    } else {
        val response = environment
            .postSigned(url) {
                contentType(ContentType.Application.Json)
                setBody(buildSegmentLikeBody(highlight))
            }.raiseForStatus()
        check(response.status.value in 200..299) { WidgetsStrings.segmentLikeHttpError(response.status.value) }
        updateSegmentMetaAfterLike(
            highlight,
            if (response.status == HttpStatusCode.NoContent) null else response.body<JsonElement>() as? JsonObject,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentActionSheet(state: SegmentActionSheetState) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.PartiallyExpanded, SheetValue.Expanded),
    )
    AppBottomSheet(onDismissRequest = state.onDismiss, sheetState = sheetState) {
        SegmentActionSheetContent(state, sheetState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentActionSheetContent(state: SegmentActionSheetState, sheetState: SheetState? = null) {
    val highlight = state.highlight
    val textScrollState = rememberScrollState()
    val overflowTolerance = with(LocalDensity.current) { 1.dp.roundToPx() }
    val hasMeasuredOverflow = textScrollState.viewportSize > 0 &&
        textScrollState.maxValue > overflowTolerance
    val showTopDivider = hasMeasuredOverflow && textScrollState.value > overflowTolerance
    val showBottomDivider = hasMeasuredOverflow &&
        textScrollState.value < textScrollState.maxValue - overflowTolerance
    Layout(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        content = {
            Text(
                text = WidgetsStrings.highlightedPassage,
                modifier = Modifier.layoutId("title"),
                style = MaterialTheme.typography.titleMedium,
            )
            if (showTopDivider) {
                HorizontalDivider(
                    modifier = Modifier
                        .layoutId("topDivider")
                        .testTag("segment_action_sheet_top_divider"),
                )
            }
            Text(
                text = "“${highlight.displayText}”",
                modifier = Modifier
                    .layoutId("text")
                    .verticalScroll(textScrollState),
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 24.sp,
            )
            if (showBottomDivider) {
                HorizontalDivider(
                    modifier = Modifier
                        .layoutId("bottomDivider")
                        .testTag("segment_action_sheet_bottom_divider"),
                )
            }
            Row(
                modifier = Modifier
                    .layoutId("actions")
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val actionColors = ButtonDefaults.filledTonalButtonColors()
                AppButton(
                    onClick = state.onLikeClick,
                    modifier = Modifier.weight(1f),
                    containerColor = actionColors.containerColor,
                    contentColor = actionColors.contentColor,
                ) {
                    AppIcon(
                        imageVector = if (highlight.meta.isLike) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = if (highlight.meta.isLike) WidgetsStrings.unlike else WidgetsStrings.like,
                    )
                    Text(
                        text = highlight.meta.likeCount.toString(),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                AppButton(
                    onClick = state.onCommentClick,
                    modifier = Modifier.weight(1f),
                    containerColor = actionColors.containerColor,
                    contentColor = actionColors.contentColor,
                ) {
                    AppIcon(
                        imageVector = Icons.AutoMirrored.Outlined.Comment,
                        contentDescription = WidgetsStrings.comment,
                    )
                    Text(
                        text = highlight.meta.commentCount.toString(),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                AppIconButton(onClick = state.onCopyClick) {
                    AppIcon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = WidgetsStrings.copyContent,
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val spacing = 12.dp.roundToPx()
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val title = measurables.first { it.layoutId == "title" }.measure(looseConstraints)
        val textMeasurable = measurables.first { it.layoutId == "text" }
        val topDivider = measurables
            .firstOrNull { it.layoutId == "topDivider" }
            ?.measure(looseConstraints)
        val bottomDivider = measurables
            .firstOrNull { it.layoutId == "bottomDivider" }
            ?.measure(looseConstraints)
        val actions = measurables.first { it.layoutId == "actions" }.measure(looseConstraints)
        val naturalTextHeight = textMeasurable
            .maxIntrinsicHeight(constraints.maxWidth)
            .coerceAtMost(constraints.maxHeight)
        val naturalHeight = title.height + naturalTextHeight + actions.height + spacing * 2
        val needsPartialExpansion = sheetState != null && naturalHeight > constraints.maxHeight / 2
        val layoutHeight = if (needsPartialExpansion) constraints.maxHeight else naturalHeight.coerceAtMost(constraints.maxHeight)
        val visibleHeight = if (needsPartialExpansion) {
            val sheetOffset = runCatching { sheetState.requireOffset() }
                .getOrDefault(constraints.maxHeight.toFloat())
            (constraints.maxHeight - sheetOffset.roundToInt()).coerceIn(
                minimumValue = title.height + actions.height + spacing * 2,
                maximumValue = constraints.maxHeight,
            )
        } else {
            layoutHeight
        }
        val textTop = title.height + spacing
        val actionsTop = visibleHeight - actions.height
        val textBottom = actionsTop - spacing
        val textHeight = (textBottom - textTop).coerceAtLeast(0)
        val text = textMeasurable.measure(
            looseConstraints.copy(maxHeight = textHeight),
        )

        layout(constraints.maxWidth, layoutHeight) {
            title.placeRelative(0, 0)
            text.placeRelative(0, textTop)
            topDivider?.placeRelative(0, textTop - topDivider.height)
            bottomDivider?.placeRelative(0, textBottom - bottomDivider.height)
            actions.placeRelative(0, actionsTop)
        }
    }
}

fun Map<String, String>.toSegmentHighlightSpan(fallbackText: String? = null): SegmentHighlightSpan? {
    val segIds = get("data-highlight-id")
        .orEmpty()
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
    if (segIds.isEmpty()) return null
    val text = get("data-highlight-display-text")?.takeIf(String::isNotBlank) ?: fallbackText.orEmpty()
    return SegmentHighlightSpan(
        text = text,
        displayText = text,
        meta = SegmentInfoMeta(
            segIds = segIds,
            isLike = get("data-highlight-is-like")?.toBoolean() ?: false,
            likeCount = get("data-highlight-like-count")?.toIntOrNull() ?: 0,
            commentCount = get("data-highlight-comment-count")?.toIntOrNull() ?: 0,
            myCommentCount = get("data-highlight-my-comment-count")?.toIntOrNull() ?: 0,
            isSpan = get("data-highlight-is-span")?.toBoolean() ?: false,
        ),
        sourceUrl = get("data-highlight-source-url"),
        contentId = get("data-highlight-content-id"),
        contentType = get("data-highlight-content-type"),
        paragraphId = get("data-highlight-pid"),
        startOffset = get("data-highlight-start-offset")?.toIntOrNull(),
        endOffset = get("data-highlight-end-offset")?.toIntOrNull(),
    )
}

fun SegmentHighlightSpan.threadKey(): String = listOf(
    contentType.orEmpty(),
    contentId.orEmpty(),
    meta.segIds.joinToString(","),
).joinToString("|")

private fun List<Node>.readablePlainText(): String = joinToString("") { it.readableText() }

private fun Node.readableText(): String = when (this) {
    is com.hrm.markdown.parser.ast.Text -> literal
    is com.hrm.markdown.parser.ast.SoftLineBreak -> " "
    is com.hrm.markdown.parser.ast.HardLineBreak -> "\n"
    is ContainerNode -> children.readablePlainText()
    is com.hrm.markdown.parser.ast.LeafNode -> literal
}

private fun Document.segmentHighlights(): List<SegmentHighlightSpan> {
    val fragments = mutableListOf<SegmentHighlightSpan>()

    fun visit(node: Node) {
        if (node is com.hrm.markdown.parser.ast.Highlight) {
            val text = node.children.readablePlainText()
            node.attributes.toSegmentHighlightSpan(fallbackText = text)?.let {
                fragments += it.copy(text = text)
            }
        }
        if (node is ContainerNode) node.children.forEach(::visit)
    }
    visit(this)
    return fragments
}

/** 跨段既有划线按原切片连接；段间换行仅用于引文展示，不充当创建坐标。 */
private fun SegmentHighlightSpan.withCompleteText(highlights: List<SegmentHighlightSpan>): SegmentHighlightSpan {
    val fragments = highlights.filter { it.threadKey() == threadKey() }
    if (fragments.isEmpty()) return this
    val display = fragments.firstOrNull { it.displayText != it.text }?.displayText ?: buildString {
        var previousParagraph: String? = null
        fragments.forEach { fragment ->
            if (isNotEmpty() && previousParagraph != fragment.paragraphId) append("\n\n")
            append(fragment.text)
            previousParagraph = fragment.paragraphId
        }
    }
    return copy(text = fragments.joinToString("") { it.text }, displayText = display)
}

fun SegmentHighlightSpan.toSegmentCommentHolder(): SegmentCommentHolder? {
    val contentId = contentId ?: return null
    val contentType = contentType ?: return null
    val segmentId = meta.segIds.joinToString(",").takeIf { it.isNotBlank() } ?: return null
    return SegmentCommentHolder(
        contentId = contentId,
        contentType = contentType,
        segmentId = segmentId,
        segmentContent = displayText,
        paragraphId = paragraphId ?: return null,
        startOffset = startOffset ?: return null,
        endOffset = endOffset ?: return null,
    )
}
