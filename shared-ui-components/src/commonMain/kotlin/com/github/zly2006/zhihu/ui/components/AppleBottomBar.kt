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

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

/** 共用的液态玻璃导航。 */
@Composable
internal fun AppleBottomBar(items: List<BottomBarItem>, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    val tokens = LocalAppSkinTokens.current
    val dark = ThemeManager.isDarkTheme()
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labels = items.map { it.label }
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val interactions = items.map { item ->
        key(item.testTag) { remember { MutableInteractionSource() } }
    }
    val pressed = interactions.map { it.collectIsPressedAsState().value }
    val selected = items.indexOfFirst { it.selected }
    val shape = RoundedCornerShape(50)

    BoxWithConstraints(modifier) {
        val viewportWidth = (maxWidth - 8.dp).coerceAtLeast(0.dp)
        val itemWidth = (viewportWidth / items.size).coerceAtLeast(AppDimens.TouchTarget)
        val scroll = rememberScrollState()
        LaunchedEffect(selected, itemWidth, viewportWidth, scroll.maxValue) {
            if (selected >= 0) {
                val target = with(density) { (itemWidth * selected - (viewportWidth - itemWidth) / 2).roundToPx() }
                scroll.animateScrollTo(target.coerceIn(0, scroll.maxValue))
            }
        }
        val labelHeight = remember(labels, labelStyle, density, itemWidth, measurer) {
            val available = with(density) { (itemWidth - 12.dp).roundToPx().coerceAtLeast(1) }
            val pixels = labels.maxOf { label ->
                measurer.measure(label, labelStyle, constraints = Constraints(maxWidth = available)).size.height
            }
            with(density) { pixels.toDp() }
        }
        val height = maxOf(64.dp, labelHeight + 52.dp)
        val targetOffset = itemWidth * selected.coerceAtLeast(0)
        val selectionOffset by animateDpAsState(targetOffset, spring(dampingRatio = 0.85f, stiffness = 450f), label = "appleTabOffset")
        val selectionScale by animateFloatAsState(
            if (selected >= 0 && pressed[selected]) 0.96f else 1f,
            spring(dampingRatio = 0.8f, stiffness = 500f),
            label = "appleTabPress",
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .appAppleGlassSurface(shape, tokens.surfaceContainerHigh, dark)
                .clip(shape)
                .padding(4.dp),
        ) {
            Box(Modifier.horizontalScroll(scroll).width(itemWidth * items.size).height(height - 8.dp)) {
                if (selected >= 0) {
                    Box(
                        Modifier
                            .offset(x = selectionOffset)
                            .width(itemWidth)
                            .height(height - 8.dp)
                            .graphicsLayer {
                                scaleX = selectionScale
                                scaleY = selectionScale
                            }
                            .background(tokens.onSurface.copy(alpha = if (dark) 0.12f else 0.06f), shape)
                            .border(0.5.dp, Color.White.copy(alpha = if (dark) 0.14f else 0.5f), shape),
                    )
                }
                Row(Modifier.fillMaxSize().selectableGroup()) {
                    items.forEachIndexed { index, item ->
                        key(item.testTag) {
                            val scale by animateFloatAsState(
                                if (pressed[index]) 0.94f else 1f,
                                spring(dampingRatio = 0.8f, stiffness = 500f),
                                label = "appleTabContent",
                            )
                            val foreground = if (item.selected) tokens.primary else tokens.onSurface
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(height - 8.dp)
                                    .clip(shape)
                                    .selectable(
                                        selected = item.selected,
                                        onClick = item.onClick,
                                        role = Role.Tab,
                                        interactionSource = interactions[index],
                                        indication = null,
                                    )
                                    .testTag(item.testTag),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp, vertical = 8.dp)
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(item.icon, null, Modifier.size(24.dp), tint = foreground)
                                    Text(
                                        item.label,
                                        Modifier.fillMaxWidth().padding(top = 4.dp),
                                        color = foreground,
                                        style = labelStyle,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
