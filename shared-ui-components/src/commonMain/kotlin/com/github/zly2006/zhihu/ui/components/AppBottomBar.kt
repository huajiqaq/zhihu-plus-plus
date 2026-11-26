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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarDefaults
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.ui.rememberObservedSetting
import com.github.zly2006.zhihu.ui.subscreens.FLOATING_BOTTOM_BAR_STYLE_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

/** 导航项只携带展示与当前动作，目的地映射由调用方决定。 */
class BottomBarItem(
    val label: String,
    val icon: ImageVector,
    val testTag: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
fun AppBottomBar(
    items: List<BottomBarItem>,
    modifier: Modifier = Modifier,
    floating: Boolean = false,
    bottomPadding: Dp = 0.dp,
) {
    if (items.isEmpty()) return
    val isMiuix = settingSkinIsMiuix()
    val navigation: @Composable (Modifier) -> Unit = { barModifier ->
        if (isMiuix) {
            MiuixBottomBar(items = items, modifier = barModifier, floating = floating)
        } else {
            MaterialBottomBar(items, barModifier, floating)
        }
    }
    if (floating) {
        val settings = rememberSettingsStore()
        val floatingStyle by rememberObservedSetting(settings, FLOATING_BOTTOM_BAR_STYLE_PREFERENCE_KEY) {
            getString(FLOATING_BOTTOM_BAR_STYLE_PREFERENCE_KEY, "default")
        }
        val shape = RoundedCornerShape(50)
        val surface = if (isMiuix) LocalAppSkinTokens.current.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerHigh
        val outline = if (isMiuix) LocalAppSkinTokens.current.outline else MaterialTheme.colorScheme.outlineVariant
        Box(
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(bottom = bottomPadding + 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                val width = minOf(76.dp * items.size + 8.dp, maxWidth)
                if (floatingStyle == "apple") {
                    AppleBottomBar(items, Modifier.align(Alignment.Center).width(width))
                } else {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .width(width)
                            .appFloatingGlassSurface(
                                shape = shape,
                                fallbackContainer = surface,
                                tint = surface.copy(alpha = 0.35f),
                                borderColor = outline.copy(alpha = 0.2f),
                                isDark = ThemeManager.isDarkTheme(),
                            ).clip(shape),
                    ) {
                        navigation(Modifier)
                    }
                }
            }
        }
    } else {
        navigation(modifier)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MaterialBottomBar(items: List<BottomBarItem>, modifier: Modifier, floating: Boolean) {
    val container = if (floating) Color.Transparent else ShortNavigationBarDefaults.containerColor
    val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
    BottomBarDragStrip(items, if (floating) modifier else modifier.background(container).windowInsetsPadding(insets)) { _, preview ->
        ShortNavigationBar(
            modifier = Modifier.wrapContentWidth(),
            containerColor = container,
            arrangement = ShortNavigationBarArrangement.EqualWeight,
            windowInsets = WindowInsets(0, 0, 0, 0),
        ) {
            val colors = ShortNavigationBarItemDefaults.colors()
            items.forEachIndexed { index, item ->
                key(item.testTag) {
                    val weight = preview?.tabWeight(index)
                    ShortNavigationBarItem(
                        selected = weight?.let { it > 0f } ?: item.selected,
                        onClick = item.onClick,
                        label = { Text(item.label) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        modifier = Modifier.bottomBarTab(item),
                        colors = if (weight == null) {
                            colors
                        } else {
                            colors.copy(
                                selectedIconColor = lerp(colors.unselectedIconColor, colors.selectedIconColor, weight),
                                selectedTextColorTopIconPosition = lerp(colors.unselectedTextColor, colors.selectedTextColorTopIconPosition, weight),
                                selectedIndicatorColor = colors.selectedIndicatorColor.copy(alpha = colors.selectedIndicatorColor.alpha * weight),
                            )
                        },
                    )
                }
            }
        }
    }
}
