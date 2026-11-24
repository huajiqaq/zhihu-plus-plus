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

import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.NavigationBarDefaults
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRailDefaults
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationRail as MiuixNavigationRailComponent
import top.yukonga.miuix.kmp.basic.Text as MiuixText

@Composable
internal actual fun MiuixBottomBar(items: List<BottomBarItem>, modifier: Modifier, floating: Boolean) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MiuixTheme.textStyles.main.copy(
        fontSize = NavigationBarDefaults.LabelFontSize,
        fontWeight = FontWeight.Bold,
    )
    val labels = items.map { it.label }
    val foreground = MiuixTheme.colorScheme.onSurfaceContainer
    MiuixNavigationBar(
        modifier = modifier,
        color = if (floating) Color.Transparent else MiuixTheme.colorScheme.surface,
        showDivider = !floating,
        defaultWindowInsetsPadding = !floating,
    ) {
        BottomBarDragStrip(items) { itemWidth, preview ->
            val contentHeight = remember(labels, labelStyle, density, itemWidth, textMeasurer) {
                val available = Constraints(maxWidth = with(density) { itemWidth.toPx().toInt().coerceAtLeast(1) })
                val height = labels.maxOf { textMeasurer.measure(it, labelStyle, constraints = available).size.height }
                NavigationBarDefaults.IconTopPadding + NavigationBarDefaults.IconSize + with(density) { height.toDp() } + NavigationBarDefaults.BottomPadding
            }
            Row(Modifier.fillMaxWidth()) {
                items.forEachIndexed { index, item ->
                    key(item.testTag) {
                        val weight = preview?.tabWeight(index)
                        val alpha = NavigationBarDefaults.UnselectedAlpha
                        NavigationBarItem(
                            selected = weight?.let { it > 0f } ?: item.selected,
                            onClick = item.onClick,
                            icon = appIconVector(item.icon),
                            label = item.label,
                            modifier = Modifier.heightIn(min = contentHeight.coerceAtLeast(NavigationBarDefaults.ItemHeight)).bottomBarTab(item),
                            colors = NavigationBarDefaults.navigationBarItemColors(
                                selectedContentColor = if (weight == null) foreground else foreground.copy(alpha = foreground.alpha * (alpha + (1f - alpha) * weight)),
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal actual fun MiuixNavigationRail(items: List<BottomBarItem>, modifier: Modifier) {
    MiuixNavigationRailComponent(modifier = modifier.fillMaxHeight()) {
        items.forEach { item ->
            MiuixRailItem(
                item = item,
                modifier = Modifier.testTag(item.testTag),
            )
        }
    }
}

@Composable
private fun MiuixRailItem(item: BottomBarItem, modifier: Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = item.selected,
                onClick = item.onClick,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
            ).padding(vertical = NavigationRailDefaults.ItemVerticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(NavigationRailDefaults.ExpandedItemCornerRadius))
                .then(
                    if (item.selected) {
                        Modifier.squircleBackground(
                            color = MiuixTheme.colorScheme.surfaceContainerHigh,
                            cornerRadius = NavigationRailDefaults.ExpandedItemCornerRadius,
                        )
                    } else {
                        Modifier
                    },
                ).indication(interactionSource, LocalIndication.current)
                .padding(
                    horizontal = NavigationRailDefaults.ExpandedItemContentHorizontalPadding,
                    vertical = NavigationRailDefaults.CollapsedIndicatorVerticalPadding,
                ),
        ) {
            Image(
                imageVector = appIconVector(item.icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurfaceContainer),
                modifier = Modifier.size(NavigationRailDefaults.IconSize),
            )
        }
        Spacer(Modifier.height(NavigationRailDefaults.IconTextSpacing))
        MiuixText(
            text = item.label,
            color = MiuixTheme.colorScheme.onSurfaceContainer,
            textAlign = TextAlign.Center,
            fontSize = NavigationRailDefaults.LabelFontSize,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal actual fun MiuixDraggableRefreshButton(
    onClick: () -> Unit,
    modifier: Modifier,
    opacityFraction: Float,
    compact: Boolean,
    content: @Composable () -> Unit,
) {
    top.yukonga.miuix.kmp.basic.FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = if (compact) RoundedCornerShape(16.dp) else androidx.compose.foundation.shape.CircleShape,
        containerColor = MiuixTheme.colorScheme.primary.copy(alpha = opacityFraction),
        minWidth = if (compact) 48.dp else top.yukonga.miuix.kmp.basic.FloatingActionButtonDefaults.MinWidth,
        minHeight = if (compact) 48.dp else top.yukonga.miuix.kmp.basic.FloatingActionButtonDefaults.MinHeight,
    ) {
        val foreground = MiuixTheme.colorScheme.onPrimary.copy(alpha = opacityFraction)
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides foreground,
            top.yukonga.miuix.kmp.theme.LocalContentColor provides foreground,
        ) {
            content()
        }
    }
}
