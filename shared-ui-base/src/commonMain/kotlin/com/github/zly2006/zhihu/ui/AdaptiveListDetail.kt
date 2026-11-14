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
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.CommentHolder
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import org.jetbrains.compose.resources.painterResource
import zhihu.uicomponents.generated.resources.Res
import zhihu.uicomponents.generated.resources.ic_launcher_foreground

const val LANDSCAPE_LIST_PANE_RATIO_KEY = "landscape_list_pane_ratio"
const val DEFAULT_LIST_PANE_RATIO = 1f / 3f
val MIN_LIST_PANE_WIDTH = 320.dp
val MIN_DETAIL_PANE_WIDTH = 480.dp
val LIST_DETAIL_DIVIDER_WIDTH = 16.dp

fun NavDestination.isDetailPaneDestination(): Boolean = when (this) {
    is Article, is Pin -> true
    is CommentHolder -> article.isDetailPaneDestination()
    else -> false
}

fun canShowLandscapeListDetail(
    enabled: Boolean,
    deviceSupportsListDetail: Boolean,
    preferenceEnabled: Boolean,
    width: Dp,
    height: Dp,
): Boolean = enabled &&
    deviceSupportsListDetail &&
    preferenceEnabled &&
    width >= 840.dp &&
    width > height

fun normalizedListPaneWidth(
    availableWidth: Dp,
    requestedRatio: Float,
): Dp {
    val usableWidth = availableWidth - LIST_DETAIL_DIVIDER_WIDTH
    return (usableWidth * requestedRatio.coerceIn(0f, 1f)).coerceIn(
        minimumValue = MIN_LIST_PANE_WIDTH,
        maximumValue = usableWidth - MIN_DETAIL_PANE_WIDTH,
    )
}

@Composable
fun ListDetailDivider(
    onDrag: (Float) -> Unit,
    onAdjustBy: (Float) -> Unit,
    onDragStopped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .then(if (settingSkinIsMiuix()) Modifier.background(LocalAppSkinTokens.current.surface) else Modifier)
            .width(LIST_DETAIL_DIVIDER_WIDTH)
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
            }.draggable(
                state = rememberDraggableState(onDelta = onDrag),
                orientation = Orientation.Horizontal,
                onDragStopped = { onDragStopped() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

@Composable
fun EmptyDetailPane(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .testTag("detail_pane_empty"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(32.dp),
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(96.dp).alpha(0.72f),
            )
            Text(
                text = "选择左侧内容",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
