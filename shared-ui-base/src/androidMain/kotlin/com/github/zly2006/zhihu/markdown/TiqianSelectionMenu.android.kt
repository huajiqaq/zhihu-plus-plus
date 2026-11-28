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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.github.zly2006.zhihu.ui.components.SegmentCreationRange
import com.github.zly2006.zhihu.ui.components.SegmentSelectionMenu
import com.github.zly2006.zhihu.ui.components.SegmentToolbarPositionProvider
import com.github.zly2006.zhihu.ui.components.buildSegmentSelectionMenuActions
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import org.tiqian.compose.CjkSelectionState

/** 显示菜单时挂起、关闭时恢复；不获取焦点的弹窗保留拖拽手柄的选区焦点。 */
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
    val provider = remember { ZhihuTextContextMenuProvider() }
    var anchor by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val request = provider.request
    LaunchedEffect(request, selectionState.selectedRanges) {
        if (request != null) onMenuShown()
    }
    // 正文点击会清掉选区，此时菜单必须跟着消失。
    LaunchedEffect(request, selectionState.selectedText != null) {
        if (request != null && selectionState.selectedText == null) provider.close()
    }
    CompositionLocalProvider(LocalTextContextMenuToolbarProvider provides provider) {
        Box(Modifier.onGloballyPositioned { anchor = it }) {
            content()
            val coordinates = anchor
            if (request != null && coordinates != null) {
                val local = request.dataProvider.contentBounds(coordinates)
                val origin = coordinates.positionInWindow()
                SelectionMenuPopup(
                    windowBounds = Rect(
                        left = origin.x + local.left,
                        top = origin.y + local.top,
                        right = origin.x + local.right,
                        bottom = origin.y + local.bottom,
                    ),
                    selectionState = selectionState,
                    selectionRange = selectionRange,
                    liked = liked,
                    onLike = onLike,
                    onComment = onComment,
                    onCancelLike = onCancelLike,
                    onClose = provider::close,
                )
            }
        }
    }
}

@Composable
private fun SelectionMenuPopup(
    windowBounds: Rect,
    selectionState: CjkSelectionState,
    selectionRange: () -> SegmentCreationRange?,
    liked: (SegmentCreationRange) -> Boolean,
    onLike: ((SegmentCreationRange?) -> Unit)?,
    onComment: ((SegmentCreationRange?) -> Unit)?,
    onCancelLike: ((SegmentCreationRange?) -> Unit)?,
    onClose: () -> Unit,
) {
    val selected = selectionState.selectedText?.text.orEmpty()
    val range = selectionRange()
    Popup(
        popupPositionProvider = remember(windowBounds) { SegmentToolbarPositionProvider(windowBounds) },
        onDismissRequest = onClose,
        properties = PopupProperties(focusable = false),
    ) {
        SegmentSelectionMenu(
            actions = buildSegmentSelectionMenuActions(
                selectedText = selected,
                liked = range != null && liked(range),
                onCopy = {
                    if (selectionState.copySelection()) selectionState.clearSelection()
                    onClose()
                },
                onSelectAll = { selectionState.selectAll() },
                onLike = onLike?.let { callback ->
                    { _ ->
                        val selectedRange = selectionRange()
                        onClose()
                        callback(selectedRange)
                    }
                },
                onComment = onComment?.let { callback ->
                    { _ ->
                        val selectedRange = selectionRange()
                        onClose()
                        callback(selectedRange)
                    }
                },
                onCancelLike = onCancelLike?.let { callback ->
                    { _ ->
                        val selectedRange = selectionRange()
                        onClose()
                        callback(selectedRange)
                    }
                },
            ),
        )
    }
}

private class SelectionMenuRequest(
    val dataProvider: TextContextMenuDataProvider,
    val continuation: CancellableContinuation<Unit>,
)

private class ZhihuTextContextMenuProvider : TextContextMenuProvider {
    var request by mutableStateOf<SelectionMenuRequest?>(null)
        private set

    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
        suspendCancellableCoroutine { continuation ->
            request = SelectionMenuRequest(dataProvider, continuation)
            continuation.invokeOnCancellation { request = null }
        }
    }

    fun close() {
        val current = request ?: return
        request = null
        if (current.continuation.isActive) current.continuation.resume(Unit)
    }
}
