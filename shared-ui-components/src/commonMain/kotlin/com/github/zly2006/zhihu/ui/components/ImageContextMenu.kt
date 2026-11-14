/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 */

package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.DropdownMenuPopupPositionProvider
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

/** 图片操作菜单只接收当前窗口的像素坐标；无指针位置时靠近图片可见边界的中心。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppImageContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    windowImageBounds: Rect?,
    windowPressPoint: Offset? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacingPx = with(LocalDensity.current) { AppDimens.Space8.roundToPx() }
    val positionProvider = remember(windowPressPoint, windowImageBounds, spacingPx) {
        ImageContextMenuPositionProvider(windowPressPoint, windowImageBounds, spacingPx, spacingPx)
    }
    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        popupPositionProvider = positionProvider,
    ) {
        AppDropdownMenuContent(modifier = modifier.testTag("image_context_menu"), content = content)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal class ImageContextMenuPositionProvider(
    private val windowPressPoint: Offset?,
    private val windowImageBounds: Rect?,
    private val gapPx: Int,
    private val edgeMarginPx: Int,
) : DropdownMenuPopupPositionProvider {
    override var transformOrigin by mutableStateOf(TransformOrigin.Center)
        private set

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val point = windowPressPoint?.takeIf { it.x.isFinite() && it.y.isFinite() }
            ?: windowImageBounds?.center
            ?: Offset(anchorBounds.left + anchorBounds.width / 2f, anchorBounds.top + anchorBounds.height / 2f)
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        val marginX = edgeMarginPx.coerceAtMost(maxX / 2)
        val marginY = edgeMarginPx.coerceAtMost(maxY / 2)
        val x = if (layoutDirection == LayoutDirection.Ltr) {
            point.x.roundToInt()
        } else {
            point.x.roundToInt() - popupContentSize.width
        }
        val below = point.y.roundToInt() + gapPx
        val y = if (below <= maxY - marginY) below else point.y.roundToInt() - popupContentSize.height - gapPx
        val position = IntOffset(x.coerceIn(marginX, maxX - marginX), y.coerceIn(marginY, maxY - marginY))
        transformOrigin = TransformOrigin(
            pivotFractionX = ((point.x - position.x) / popupContentSize.width.coerceAtLeast(1)).coerceIn(0f, 1f),
            pivotFractionY = ((point.y - position.y) / popupContentSize.height.coerceAtLeast(1)).coerceIn(0f, 1f),
        )
        return position
    }
}
