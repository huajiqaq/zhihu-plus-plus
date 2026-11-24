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

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.TabRowColors
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Badge as MiuixBadge
import top.yukonga.miuix.kmp.basic.BadgedBox as MiuixBadgedBox
import top.yukonga.miuix.kmp.basic.TabRowWithContour as MiuixTabRowWithContour
import top.yukonga.miuix.kmp.basic.Text as MiuixText

@Composable
internal actual fun MiuixAppBadgedBox(
    label: String?,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    MiuixBadgedBox(
        badge = { if (label != null) MiuixBadge { MiuixText(label) } },
        modifier = modifier,
        content = content,
    )
}

@Composable
internal actual fun MiuixAppTabRow(
    titles: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
    scrollable: Boolean,
    secondary: Boolean,
    enabled: Boolean,
) {
    /** 0.9.4 无 enabled 或逐项 modifier；禁用时隐藏可点击子项，tag 留在容器。 */
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val layouts = titles.map { title ->
        textMeasurer.measure(
            text = title,
            style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Bold),
            softWrap = false,
        )
    }
    val minTabWidth = with(density) { (layouts.maxOfOrNull { it.size.width } ?: 0).toDp() } + 24.dp
    val minTabHeight = with(density) { (layouts.maxOfOrNull { it.size.height } ?: 0).toDp() } + AppDimens.Space16
    /** 单行标签按最长文本预留原生内边距；空间不足由原生 LazyRow 滚动。 */
    val rowModifier = modifier
        .fillMaxWidth()
        .selectableGroup()
        .then(
            if (enabled) {
                Modifier
            } else {
                Modifier.clearAndSetSemantics {
                    disabled()
                    contentDescription = titles.getOrNull(selectedIndex).orEmpty()
                }
            },
        ).padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space8)
    MiuixTabRowWithContour(
        tabs = titles,
        selectedTabIndex = selectedIndex,
        onTabSelected = { index -> if (enabled) onSelect(index) },
        modifier = rowModifier,
        colors = miuixSelectionColors(enabled),
        minWidth = minTabWidth.coerceAtLeast(if (scrollable) TabRowDefaults.TabRowWithContourMinWidth else AppDimens.TouchTarget),
        maxWidth = minTabWidth.coerceAtLeast(TabRowDefaults.TabRowWithContourMaxWidth),
        height = minTabHeight.coerceAtLeast(AppDimens.TouchTarget) + AppDimens.Space10,
    )
}

@Composable
private fun miuixSelectionColors(enabled: Boolean): TabRowColors {
    val colors = MiuixTheme.colorScheme
    return TabRowDefaults.tabRowColors(
        backgroundColor = colors.surfaceContainerHigh,
        contentColor = if (enabled) colors.onSurfaceVariantSummary else colors.disabledOnSurface,
        selectedBackgroundColor = if (colors.surfaceContainer == colors.surfaceContainerHigh) {
            colors.surfaceContainerHighest
        } else {
            colors.surfaceContainer
        },
        selectedContentColor = if (enabled) colors.onBackground else colors.disabledOnSurface,
    )
}

@Composable
internal actual fun MiuixAppSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val textHeight = options.maxOfOrNull { option ->
        textMeasurer.measure(option, MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Bold)).size.height
    } ?: 0
    val itemHeight = with(density) { textHeight.toDp() } + AppDimens.Space8
    // 0.9.4 的公开胶囊选择器是 TabRowWithContour；子项固定为 Role.Tab，不能冒充 RadioButton。
    // 没有逐项 enabled/testTag 槽位，容器保留禁用语义与 tag，原生回调不能修改禁用的偏好。
    MiuixTabRowWithContour(
        tabs = options,
        selectedTabIndex = selectedIndex,
        onTabSelected = { index -> if (enabled) onSelect(index) },
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup()
            .then(
                if (enabled) {
                    Modifier
                } else {
                    Modifier.clearAndSetSemantics {
                        disabled()
                        contentDescription = options.getOrNull(selectedIndex).orEmpty()
                    }
                },
            ),
        colors = miuixSelectionColors(enabled),
        // 库内 contourPadding 上下各 5dp，外层高度要加 10dp，保证原生子项仍有 48dp 点击区。
        height = itemHeight.coerceAtLeast(AppDimens.TouchTarget) + AppDimens.Space10,
    )
}
