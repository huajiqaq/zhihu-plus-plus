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

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

/** 导航项只携带展示与当前动作，目的地映射由调用方决定。 */
class BottomBarItem(
    val label: String,
    val icon: ImageVector,
    val testTag: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppBottomBar(
    items: List<BottomBarItem>,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
    floating: Boolean = false,
) {
    if (floating) {
        AppFloatingBar(
            items = items,
            bottomPadding = bottomPadding,
            modifier = modifier,
        )
        return
    }
    if (settingSkinIsMiuix()) {
        MiuixBottomBar(items = items, modifier = modifier)
        return
    }

    ShortNavigationBar(
        modifier = modifier
            .wrapContentWidth()
            .appGlassBlur(
                shape = androidx.compose.ui.graphics.RectangleShape,
                tint = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f),
            ),
        containerColor = appGlassContainerColor(MaterialTheme.colorScheme.surfaceContainerHigh),
        arrangement = ShortNavigationBarArrangement.EqualWeight,
        windowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
        ),
    ) {
        items.forEach { item ->
            ShortNavigationBarItem(
                selected = item.selected,
                onClick = item.onClick,
                label = { Text(item.label) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                modifier = Modifier.testTag(item.testTag),
            )
        }
    }
}
