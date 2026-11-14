/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 only.
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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastRoundToInt
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.theme.ThemeManager
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.sign
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

/** 不支持 RuntimeShader 时保留实心容器和拖动反馈。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppFloatingBar(
    items: List<BottomBarItem>,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val isMiuix = settingSkinIsMiuix()
    val isDark = ThemeManager.isDarkTheme()
    val shape = CircleShape

    val fallbackContainer = if (isMiuix) {
        LocalAppSkinTokens.current.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val tint = if (isMiuix) {
        LocalAppSkinTokens.current.surfaceContainer.copy(alpha = 0.30f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.32f)
    }
    val indicatorColor = if (isMiuix) {
        LocalAppSkinTokens.current.primary.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    }
    val borderColor = if (isMiuix) {
        LocalAppSkinTokens.current.outline.copy(alpha = 0.10f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    }
    val selectedColor = if (isMiuix) {
        LocalAppSkinTokens.current.primary
    } else {
        MaterialTheme.colorScheme.primary
    }
    val unselectedColor = if (isMiuix) {
        LocalAppSkinTokens.current.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val selectedIndex = items.indexOfFirst { it.selected }.coerceAtLeast(0)
    val layoutDirection = LocalLayoutDirection.current
    val lastIndex = items.size - 1
    val density = LocalDensity.current
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentItems by rememberUpdatedState(items)
    val indicator = remember { Animatable(selectedIndex.toFloat()) }
    val panelOffset = remember { Animatable(0f) }
    var pressing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val settleSpring = spring<Float>(dampingRatio = 0.7f, stiffness = 380f)
    val settlePanelSpring = spring<Float>(dampingRatio = 1f, stiffness = 300f)
    val rubberBandPx = with(density) { 4.dp.toPx() }

    LaunchedEffect(selectedIndex) {
        indicator.animateTo(selectedIndex.toFloat(), settleSpring)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = bottomPadding + 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            val barWidth = minOf(76.dp * items.size + 8.dp, maxWidth)
            val tabWidth = (barWidth - 8.dp) / items.size
            val tabWidthPx = with(density) { tabWidth.toPx() }
            val contentWidthPx = with(density) { (barWidth - 8.dp).toPx() }

            fun logicalIndexAt(localX: Float): Float {
                if (tabWidthPx <= 0f) return 0f
                val logicalX = if (layoutDirection == LayoutDirection.Ltr) localX else contentWidthPx - localX
                return (logicalX / tabWidthPx - 0.5f).fastCoerceIn(0f, lastIndex.toFloat())
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(barWidth)
                    .heightIn(min = 64.dp)
                    .graphicsLayer {
                        translationX = panelOffset.value *
                            if (layoutDirection == LayoutDirection.Ltr) 1f else -1f
                    }.appFloatingGlassSurface(
                        shape = shape,
                        fallbackContainer = fallbackContainer,
                        tint = tint,
                        borderColor = borderColor,
                        isDark = isDark,
                    )
                    // 图标行、胶囊和手势坐标共用 barWidth - 8dp 的内容区。
                    .padding(horizontal = AppDimens.FloatingBottomBarInnerInset)
                    .pointerInput(items.size, tabWidthPx, contentWidthPx, layoutDirection) {
                        // 只追踪按下→水平拖动→抬起这一段手势；不用 tap/detect 事件族，
                        // 避免与子项的原生点击反馈叠成两层波纹。
                        awaitEachGesture {
                            val down = awaitFirstDown(pass = PointerEventPass.Initial)
                            val startX = down.position.x
                            val startY = down.position.y
                            pressing = true
                            scope.launch { indicator.snapTo(logicalIndexAt(startX)) }
                            var dragMoved = false
                            var upChange: PointerInputChange? = null
                            while (upChange == null) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.fastFirstOrNull { it.id == down.id }
                                if (change == null) {
                                    if (event.changes.none { it.pressed }) {
                                        break
                                    }
                                    continue
                                }
                                if (change.changedToUpIgnoreConsumed()) {
                                    upChange = change
                                    break
                                }
                                val dragging = abs(change.position.x - startX) > 6f * density.density &&
                                    abs(change.position.y - startY) < abs(change.position.x - startX)
                                if (dragging) {
                                    dragMoved = true
                                    change.consume()
                                    val nextIndicator = logicalIndexAt(change.position.x)
                                    val logical = if (layoutDirection == LayoutDirection.Ltr) {
                                        change.position.x
                                    } else {
                                        contentWidthPx - change.position.x
                                    }
                                    val overshoot = logical.coerceIn(0f, contentWidthPx) - logical
                                    val nextPanel = (panelOffset.value - overshoot * sign(logical) * EaseOut.transform(abs(overshoot) / tabWidthPx))
                                        .fastCoerceIn(-rubberBandPx, rubberBandPx)
                                    scope.launch {
                                        indicator.snapTo(nextIndicator)
                                        panelOffset.snapTo(nextPanel)
                                    }
                                }
                            }
                            val target = indicator.value.fastRoundToInt().fastCoerceIn(0, lastIndex)
                            pressing = false
                            // 纯点按（未形成拖动）交给子项原生 onClick，保持既有重选语义；
                            // 只有真的拖到别的入口才由这里提交一次。
                            if (dragMoved && target != currentSelectedIndex) {
                                currentItems[target].onClick()
                            }
                            scope.launch {
                                indicator.animateTo(
                                    if (dragMoved) target.toFloat() else currentSelectedIndex.toFloat(),
                                    settleSpring,
                                )
                                panelOffset.animateTo(0f, settlePanelSpring)
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.matchParentSize()) {
                    Box(
                        modifier = Modifier
                            .width(tabWidth)
                            .fillMaxHeight()
                            .padding(vertical = 4.dp)
                            .graphicsLayer {
                                translationX = tabWidthPx * indicator.value *
                                    if (layoutDirection == LayoutDirection.Ltr) 1f else -1f
                            }.clip(CircleShape)
                            .background(indicatorColor),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEachIndexed { index, item ->
                        val followColor by remember(index, selectedColor, unselectedColor) {
                            derivedStateOf {
                                val proximity = 1f - (indicator.value - index).absoluteValue.coerceIn(0f, 1f)
                                lerp(unselectedColor, selectedColor, proximity)
                            }
                        }
                        FloatingBarItem(
                            item = item,
                            isMiuix = isMiuix,
                            itemWidth = tabWidth,
                            contentColor = followColor,
                            pressing = pressing,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RowScope.FloatingBarItem(
    item: BottomBarItem,
    isMiuix: Boolean,
    itemWidth: Dp,
    contentColor: Color,
    pressing: Boolean,
    modifier: Modifier = Modifier,
) {
    val taggedModifier = modifier.testTag(item.testTag)
    if (isMiuix) {
        MiuixFloatingNavigationItem(item, taggedModifier, itemWidth, contentColor = contentColor)
    } else {
        // 选中反馈由外层跟手胶囊承担；按住（含点按瞬间）不再叠加原生圆形波纹底。
        CompositionLocalProvider(LocalRippleConfiguration provides if (pressing) null else LocalRippleConfiguration.current) {
            ShortNavigationBarItem(
                selected = item.selected,
                onClick = item.onClick,
                modifier = taggedModifier,
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(item.label) },
                colors = ShortNavigationBarItemDefaults.colors(
                    selectedIconColor = contentColor,
                    selectedTextColor = contentColor,
                    selectedIndicatorColor = Color.Transparent,
                    unselectedIconColor = contentColor,
                    unselectedTextColor = contentColor,
                ),
            )
        }
    }
}
