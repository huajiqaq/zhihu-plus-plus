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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.github.zly2006.zhihu.ui.components.SegmentCreationRange
import com.github.zly2006.zhihu.util.SegmentHighlightSpan
import com.github.zly2006.zhihu.viewmodel.comment.SegmentCommentEvents
import com.hrm.markdown.parser.ast.Document

actual val isTiqianMarkdownRendererAvailable: Boolean = false

@Composable
internal actual fun rememberTiqianMarkdownSearchState(): MarkdownSearchState =
    error("Tiqian Markdown search is unavailable on Native")

@Suppress("UNUSED_PARAMETER")
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
    error("Tiqian Markdown renderer is unavailable on Native")
}

@Composable
actual fun TiqianBrandTitle(prefix: String, suffix: String) {
    Text("${prefix}提椠$suffix")
}
