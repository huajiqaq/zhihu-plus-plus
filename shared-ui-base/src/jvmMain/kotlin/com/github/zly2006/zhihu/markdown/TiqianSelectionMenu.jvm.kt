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

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuRepresentation
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.rememberPopupPositionProviderAtPosition
import com.github.zly2006.zhihu.ui.components.SegmentCreationRange
import com.github.zly2006.zhihu.ui.components.SegmentSelectionMenu
import com.github.zly2006.zhihu.ui.components.buildSegmentSelectionMenuActions
import org.tiqian.compose.CjkSelectionState

/** 通过 LocalContextMenuRepresentation 显示段评选区动作。 */
@Composable
fun TiqianSelectionMenuHost(
    selectionState: CjkSelectionState,
    selectionRange: () -> SegmentCreationRange?,
    liked: (SegmentCreationRange) -> Boolean,
    onLike: ((SegmentCreationRange?) -> Unit)?,
    onComment: ((SegmentCreationRange?) -> Unit)?,
    onCancelLike: ((SegmentCreationRange?) -> Unit)?,
    onMenuShown: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val representation = remember(selectionState, selectionRange, liked, onLike, onComment, onCancelLike, onMenuShown) {
        SegmentTextContextMenuRepresentation(selectionState, selectionRange, liked, onLike, onComment, onCancelLike, onMenuShown)
    }
    CompositionLocalProvider(LocalContextMenuRepresentation provides representation) {
        content()
    }
}

private class SegmentTextContextMenuRepresentation(
    private val selectionState: CjkSelectionState,
    private val selectionRange: () -> SegmentCreationRange?,
    private val liked: (SegmentCreationRange) -> Boolean,
    private val onLike: ((SegmentCreationRange?) -> Unit)?,
    private val onComment: ((SegmentCreationRange?) -> Unit)?,
    private val onCancelLike: ((SegmentCreationRange?) -> Unit)?,
    private val onMenuShown: () -> Unit,
) : ContextMenuRepresentation {
    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    override fun Representation(state: ContextMenuState, items: () -> List<ContextMenuItem>) {
        val status = state.status as? ContextMenuState.Status.Open ?: return
        LaunchedEffect(status, selectionState.selectedRanges) { onMenuShown() }
        val selected = selectionState.selectedText?.text.orEmpty()
        val range = selectionRange()
        val close = { state.status = ContextMenuState.Status.Closed }
        Popup(
            popupPositionProvider = rememberPopupPositionProviderAtPosition(status.rect.center),
            onDismissRequest = close,
            properties = PopupProperties(focusable = true),
        ) {
            SegmentSelectionMenu(
                actions = buildSegmentSelectionMenuActions(
                    selectedText = selected,
                    liked = range != null && liked(range),
                    onCopy = {
                        if (selectionState.copySelection()) selectionState.clearSelection()
                        close()
                    },
                    onSelectAll = { selectionState.selectAll() },
                    onLike = onLike?.let { callback ->
                        { _ ->
                            val selectedRange = selectionRange()
                            close()
                            callback(selectedRange)
                        }
                    },
                    onComment = onComment?.let { callback ->
                        { _ ->
                            val selectedRange = selectionRange()
                            close()
                            callback(selectedRange)
                        }
                    },
                    onCancelLike = onCancelLike?.let { callback ->
                        { _ ->
                            val selectedRange = selectionRange()
                            close()
                            callback(selectedRange)
                        }
                    },
                ),
            )
        }
    }
}
