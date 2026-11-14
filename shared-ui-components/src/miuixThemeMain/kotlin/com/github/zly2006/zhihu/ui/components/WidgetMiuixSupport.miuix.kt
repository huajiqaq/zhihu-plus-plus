/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 */

package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.NavigationBarDefaults
import top.yukonga.miuix.kmp.basic.NavigationRailDefaults
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationRail as MiuixNavigationRailComponent
import top.yukonga.miuix.kmp.basic.Text as MiuixText

@Composable
internal actual fun MiuixBottomBar(items: List<BottomBarItem>, modifier: Modifier) {
    BoxWithConstraints {
        val itemWidth = maxWidth / items.size.coerceAtLeast(1)
        MiuixNavigationBar(
            modifier = modifier.appGlassBlur(
                shape = androidx.compose.ui.graphics.RectangleShape,
                tint = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.35f),
            ),
            color = appGlassContainerColor(MiuixTheme.colorScheme.surface),
        ) {
            items.forEach { item ->
                MiuixFloatingNavigationItem(item, Modifier.testTag(item.testTag), itemWidth)
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
            )
            .padding(vertical = NavigationRailDefaults.ItemVerticalPadding),
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
                )
                .indication(interactionSource, LocalIndication.current)
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
internal actual fun RowScope.MiuixFloatingNavigationItem(
    item: BottomBarItem,
    modifier: Modifier,
    itemWidth: Dp,
    highlightSelected: Boolean,
    contentColor: Color?,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MiuixTheme.textStyles.main.copy(
        fontSize = NavigationBarDefaults.LabelFontSize,
        fontWeight = FontWeight.Normal,
    )
    val labelLayout = textMeasurer.measure(
        text = item.label,
        style = labelStyle,
        constraints = Constraints(maxWidth = with(density) { itemWidth.roundToPx() }.coerceAtLeast(1)),
    )
    val contentHeight = NavigationBarDefaults.IconTopPadding + NavigationBarDefaults.IconSize +
        with(density) { labelLayout.size.height.toDp() } + NavigationBarDefaults.BottomPadding
    // 0.9.4 原生项固定乘未选中 alpha；图标与标签共同消费胶囊的连续跟手色。
    val foreground = contentColor ?: if (item.selected && highlightSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Column(
        modifier = modifier
            .weight(1f)
            .height(contentHeight.coerceAtLeast(NavigationBarDefaults.ItemHeight))
            .selectable(
                selected = item.selected,
                onClick = item.onClick,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            imageVector = appIconVector(item.icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(
                if (isPressed) {
                    val alpha = if (item.selected) NavigationBarDefaults.SelectedPressedAlpha else NavigationBarDefaults.UnselectedPressedAlpha
                    foreground.copy(alpha = foreground.alpha * alpha)
                } else {
                    foreground
                },
            ),
            modifier = Modifier.padding(top = NavigationBarDefaults.IconTopPadding).size(NavigationBarDefaults.IconSize),
        )
        MiuixText(
            text = item.label,
            color = foreground,
            textAlign = TextAlign.Center,
            style = labelStyle,
            modifier = Modifier.padding(bottom = NavigationBarDefaults.BottomPadding),
        )
    }
}
