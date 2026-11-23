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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import org.jetbrains.compose.resources.painterResource
import zhihu.uicomponents.generated.resources.Res
import zhihu.uicomponents.generated.resources.ic_launcher_foreground

const val LANDSCAPE_LIST_PANE_RATIO_KEY = "landscape_list_pane_ratio"
const val DEFAULT_LIST_PANE_RATIO = 1f / 3f
val MIN_LIST_PANE_WIDTH = 320.dp
val MIN_DETAIL_PANE_WIDTH = 480.dp
val LIST_DETAIL_DIVIDER_WIDTH = AppDimens.DividerThickness
val LIST_DETAIL_DIVIDER_HIT_WIDTH = 16.dp

fun canShowLandscapeListDetail(
    enabled: Boolean,
    deviceSupportsListDetail: Boolean,
    preferenceEnabled: Boolean,
    width: Dp,
    height: Dp,
    minimumListWidth: Dp = MIN_LIST_PANE_WIDTH,
    minimumDetailWidth: Dp = MIN_DETAIL_PANE_WIDTH,
): Boolean = enabled &&
    deviceSupportsListDetail &&
    preferenceEnabled &&
    width >= minimumListWidth + LIST_DETAIL_DIVIDER_WIDTH + minimumDetailWidth &&
    width > height

fun normalizedListPaneWidth(
    availableWidth: Dp,
    requestedRatio: Float,
    minimumListWidth: Dp = MIN_LIST_PANE_WIDTH,
    minimumDetailWidth: Dp = MIN_DETAIL_PANE_WIDTH,
): Dp {
    val usableWidth = (availableWidth - LIST_DETAIL_DIVIDER_WIDTH).coerceAtLeast(0.dp)
    if (usableWidth < minimumListWidth + minimumDetailWidth) return availableWidth
    return (usableWidth * requestedRatio.coerceIn(0f, 1f)).coerceIn(
        minimumValue = minimumListWidth,
        maximumValue = usableWidth - minimumDetailWidth,
    )
}

@Composable
fun ListDetailDivider(
    onDrag: (Float) -> Unit,
    onAdjustBy: (Float) -> Unit,
    onDragStopped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val hitInset = with(LocalDensity.current) {
        ((LIST_DETAIL_DIVIDER_HIT_WIDTH.roundToPx() - LIST_DETAIL_DIVIDER_WIDTH.roundToPx()) / 2).toDp()
    }
    Box(
        modifier = modifier
            .offset(x = -hitInset)
            .width(LIST_DETAIL_DIVIDER_HIT_WIDTH)
            .fillMaxHeight()
            .testTag("list_detail_divider")
            .semantics {
                contentDescription = "调整列表和详情宽度"
                customActions = listOf(
                    CustomAccessibilityAction("增加列表宽度") {
                        onAdjustBy(0.05f)
                        onDragStopped()
                        true
                    },
                    CustomAccessibilityAction("减小列表宽度") {
                        onAdjustBy(-0.05f)
                        onDragStopped()
                        true
                    },
                )
            }.onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (event.key == Key.DirectionLeft || event.key == Key.DirectionRight)) {
                    onAdjustBy(if ((event.key == Key.DirectionRight) != isRtl) 0.05f else -0.05f)
                    onDragStopped()
                    true
                } else {
                    false
                }
            }.focusable()
            .then(ShareDividerPointerInput)
            .draggable(
                state = rememberDraggableState(onDelta = onDrag),
                orientation = Orientation.Horizontal,
                reverseDirection = isRtl,
                onDragStopped = { onDragStopped() },
            ),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = hitInset)
                .fillMaxHeight()
                .width(LIST_DETAIL_DIVIDER_WIDTH)
                .background(MaterialTheme.colorScheme.outlineVariant)
                .testTag("list_detail_divider_line"),
        )
    }
}

/** 透明拖拽层只抢占横向拖动；点击和竖向滚动仍须命中下方两栏。 */
private data object ShareDividerPointerInput : ModifierNodeElement<DividerPointerInputNode>() {
    override fun create() = DividerPointerInputNode()

    override fun update(node: DividerPointerInputNode) = Unit
}

private class DividerPointerInputNode :
    Modifier.Node(),
    PointerInputModifierNode {
    override fun sharePointerInputWithSiblings() = true

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) = Unit

    override fun onCancelPointerInput() = Unit
}

@Composable
fun EmptyDetailPane(modifier: Modifier = Modifier) {
    val tokens = LocalAppSkinTokens.current
    val miuix = settingSkinIsMiuix()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (miuix) tokens.surfaceContainer else MaterialTheme.colorScheme.surface)
            .testTag("detail_pane_empty"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppDimens.Space16),
            modifier = Modifier
                .widthIn(max = AppDimens.ContentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = AppDimens.Space32),
        ) {
            Box(
                modifier = Modifier
                    .size(AppDimens.EmptyStateIconSizeM3e)
                    .clip(if (miuix) RoundedCornerShape(AppDimens.EmptyStateIconCornerMiuix) else CircleShape)
                    .background(if (miuix) tokens.secondaryVariant else MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(Res.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(AppDimens.Space40),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppDimens.Space4),
            ) {
                Text(
                    text = "选择左侧内容",
                    style = if (miuix) tokens.title4 else MaterialTheme.typography.titleMedium,
                    color = tokens.onSurface,
                )
                Text(
                    text = "从列表打开回答、文章或问题，内容会显示在这里",
                    style = if (miuix) tokens.footnote1 else MaterialTheme.typography.bodySmall,
                    color = tokens.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
