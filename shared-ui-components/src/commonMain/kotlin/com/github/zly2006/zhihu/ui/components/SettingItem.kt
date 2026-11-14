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

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.github.zly2006.zhihu.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.CardColors
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.DropdownMenuPopupPositionProvider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.github.zly2006.zhihu.util.ProvideContentColorTextStyle
import kotlinx.coroutines.delay
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

/** 注册深链定位并绘制短暂高亮；无骨架宿主时自行滚动到目标。 */
fun Modifier.highlightSetting(
    settingKey: String?,
    highlightedKey: String,
    onPositioned: ((Int) -> Unit)? = null,
    bringIntoViewRequester: BringIntoViewRequester? = null,
    shape: Shape = RectangleShape,
    color: Color? = null,
): Modifier = composed {
    var modifier = this
    val deepLink = LocalSettingsDeepLink.current
    val effectiveHighlightedKey = highlightedKey.ifEmpty { deepLink?.highlightedKey.orEmpty() }
    val actualBringIntoViewRequester = remember(settingKey, bringIntoViewRequester, deepLink) {
        bringIntoViewRequester
            ?: settingKey?.let { key -> deepLink?.requesterFor(key) ?: BringIntoViewRequester() }
    }

    if (actualBringIntoViewRequester != null) {
        modifier = modifier.bringIntoViewRequester(actualBringIntoViewRequester)
    }

    if (onPositioned != null) {
        modifier = modifier.onGloballyPositioned { coords ->
            onPositioned(coords.positionInRoot().y.toInt())
        }
    }

    if (settingKey != null) {
        val isTarget = settingKey.isNotEmpty() && settingKey == effectiveHighlightedKey
        val highlightAlpha = remember { Animatable(0f) }
        val highlightColor = color ?: MaterialTheme.colorScheme.primaryContainer

        LaunchedEffect(isTarget) {
            if (isTarget) {
                if (deepLink == null) {
                    actualBringIntoViewRequester?.let {
                        delay(200)
                        it.bringIntoView()
                    }
                }
                repeat(2) {
                    highlightAlpha.animateTo(0.4f, tween(200, easing = LinearEasing))
                    highlightAlpha.animateTo(0.1f, tween(200, easing = LinearEasing))
                }
                highlightAlpha.animateTo(0.4f, tween(200))
                delay(2000)
                highlightAlpha.animateTo(0f, tween(1000, easing = FastOutSlowInEasing))
            } else {
                highlightAlpha.animateTo(0f, tween(500))
            }
        }

        modifier = modifier.drawWithContent {
            drawContent()
            if (highlightAlpha.value > 0f) {
                drawOutline(
                    outline = shape.createOutline(size, layoutDirection, this),
                    color = highlightColor.copy(alpha = highlightAlpha.value),
                )
            }
        }
    }

    modifier
}

/** 隐藏项缩小时连同前置间距收缩，避免折叠后留空。 */
@Composable
fun SettingItemGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    settingKey: String? = null,
    highlightedKey: String = "",
    onPositioned: ((rootY: Int) -> Unit)? = null,
    bringIntoViewRequester: BringIntoViewRequester? = null,
    content: @Composable () -> Unit,
) {
    if (settingSkinIsMiuix()) {
        MiuixSettingsRenderer.group(
            modifier = modifier.highlightSetting(
                settingKey = settingKey,
                highlightedKey = highlightedKey,
                onPositioned = onPositioned,
                bringIntoViewRequester = bringIntoViewRequester,
            ),
            title = title,
            header = header,
            footer = footer,
            content = content,
        )
        return
    }
    Column(
        Modifier
            .highlightSetting(
                settingKey = settingKey,
                highlightedKey = highlightedKey,
                onPositioned = onPositioned,
                bringIntoViewRequester = bringIntoViewRequester,
            ).padding(horizontal = AppDimens.SettingGroupHeaderHorizontalPadding)
            .padding(bottom = AppDimens.SettingGroupHeaderHorizontalPadding)
            .then(modifier),
    ) {
        title?.let {
            Text(
                text = title,
                modifier = Modifier
                    .padding(
                        start = AppDimens.SettingRowHorizontalPadding,
                        top = AppDimens.ElevationNone,
                        end = AppDimens.SettingRowHorizontalPadding,
                        bottom = AppDimens.SectionLabelBottomPadding,
                    ).semantics { heading() },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        header?.let { it() }

        Layout(
            content = content,
            modifier = Modifier.clip(MaterialTheme.shapes.large),
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints) }
            val spacing = ListItemDefaults.SegmentedGap.roundToPx()
            val baseItemHeight = AppDimens.TouchTarget.toPx()

            var yPosition = 0
            val positions = mutableListOf<Int>()
            var hasVisibleBefore = false

            for (i in placeables.indices) {
                val placeable = placeables[i]
                val scale = (placeable.height / baseItemHeight).coerceIn(0f, 1f)

                // 非首个可见元素前按比例补间距；元素缩小时，它前面的间距也同步收缩。
                if (hasVisibleBefore && scale > 0f) {
                    yPosition += (spacing * scale).toInt()
                }

                positions.add(yPosition)
                yPosition += placeable.height

                if (scale > 0f) {
                    hasVisibleBefore = true
                }
            }

            layout(constraints.maxWidth, yPosition) {
                for (i in placeables.indices) {
                    placeables[i].placeRelative(0, positions[i])
                }
            }
        }

        footer?.let {
            Column(
                modifier = Modifier.padding(
                    start = AppDimens.SettingGroupFooterPadding,
                    top = AppDimens.Space16,
                    end = AppDimens.SettingGroupFooterPadding,
                    bottom = AppDimens.SettingGroupFooterPadding,
                ),
            ) {
                ProvideContentColorTextStyle(
                    textStyle = MaterialTheme.typography.bodyMedium,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    it()
                }
            }
        }
    }
}

/** 仅限制内容宽度，外部滚动与翻页仍使用整屏视口。 */
@Composable
fun SettingsContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = AppDimens.SettingsContentMaxWidth)
                .fillMaxWidth(),
            content = content,
        )
    }
}

/** 整行打开选择菜单，当前值只展示；选择生效前等待菜单退场。 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingItemSelect(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    description: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    summaries: List<String?>? = null,
    enabled: Boolean = true,
    fieldModifier: Modifier = Modifier,
    settingKey: String? = null,
    highlightedKey: String = "",
    onPositioned: ((rootY: Int) -> Unit)? = null,
    bringIntoViewRequester: BringIntoViewRequester? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var pendingSelection by remember { mutableStateOf<Int?>(null) }
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOptions by rememberUpdatedState(options)
    val currentOnSelect by rememberUpdatedState(onSelect)
    LaunchedEffect(enabled) {
        if (!enabled) {
            expanded = false
            pendingSelection = null
        }
    }
    // 先等菜单退场再执行可能切换主题的动作，避免 handler 引用已销毁的 dispatcher。
    LaunchedEffect(pendingSelection, enabled) {
        val index = pendingSelection ?: return@LaunchedEffect
        delay(150)
        if (currentEnabled && index in currentOptions.indices) currentOnSelect(index)
        pendingSelection = null
    }
    val current = options.getOrElse(selectedIndex) { "" }

    if (settingSkinIsMiuix()) {
        MiuixSettingsRenderer.select(
            modifier = modifier,
            title = title,
            description = description,
            icon = icon,
            options = options,
            selectedIndex = selectedIndex,
            summaries = summaries,
            enabled = enabled,
            fieldModifier = fieldModifier,
            settingKey = settingKey,
            highlightedKey = highlightedKey,
            onPositioned = onPositioned,
            bringIntoViewRequester = bringIntoViewRequester,
            expanded = expanded,
            onExpandedChange = { if (!it || currentEnabled) expanded = it },
            onSelect = { if (currentEnabled) pendingSelection = it },
        )
        return
    }

    // 设置行不是文本输入框，使用锚定整行右端的 Expressive Popup；保持整行可点，不伪装成 menuAnchor 输入框。
    Box(modifier = modifier) {
        SettingItem(
            title = title,
            description = description,
            icon = icon,
            onClick = if (enabled) {
                { expanded = true }
            } else {
                null
            },
            endAction = {},
            bottomAction = {
                SettingSelectValue(
                    text = current,
                    enabled = enabled,
                    modifier = fieldModifier.fillMaxWidth(),
                )
            },
            enabled = enabled,
            settingKey = settingKey,
            highlightedKey = highlightedKey,
            onPositioned = onPositioned,
            bringIntoViewRequester = bringIntoViewRequester,
        )
        Box(modifier = Modifier.align(Alignment.BottomEnd)) {
            DropdownMenuPopup(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false },
                popupPositionProvider = EndAnchoredMenuPositionProvider,
            ) {
                DropdownMenuGroup(
                    shapes = MenuDefaults.groupShape(index = 0, count = 1),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    options.forEachIndexed { index, label ->
                        val summary = summaries?.getOrNull(index)
                        DropdownMenuItem(
                            selected = index == selectedIndex,
                            enabled = enabled,
                            onClick = {
                                expanded = false
                                if (currentEnabled) pendingSelection = index
                            },
                            text = {
                                Column {
                                    Text(label)
                                    if (summary != null) {
                                        Text(
                                            text = summary,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                            shapes = MenuDefaults.itemShape(index = index, count = options.size),
                            selectedLeadingIcon = {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 菜单右缘对齐锚点右缘（设置行右端）、自上而下展开；放不下时整体收进窗口内。 */
private object EndAnchoredMenuPositionProvider : DropdownMenuPopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(
        x = (anchorBounds.right - popupContentSize.width)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
        y = anchorBounds.bottom
            .coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
    )

    override val transformOrigin: TransformOrigin
        get() = TransformOrigin(pivotFractionX = 1f, pivotFractionY = 0f)
}

/** 选择行右侧的当前值；只展示，整行负责打开菜单。 */
@Composable
private fun SettingSelectValue(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        textAlign = TextAlign.End,
        style = MaterialTheme.typography.bodyMedium,
        color = if (enabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier,
    )
}

/** 整行切换；内嵌 Switch 不创建独立点击或语义节点。 */
@Composable
fun SettingItemWithSwitch(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    description: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    settingKey: String? = null,
    highlightedKey: String = "",
    onPositioned: ((rootY: Int) -> Unit)? = null,
    bringIntoViewRequester: BringIntoViewRequester? = null,
) {
    SettingItem(
        modifier = modifier.semantics {
            role = Role.Switch
            toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
        },
        title = title,
        description = description,
        icon = icon,
        checked = checked,
        onCheckedChange = onCheckedChange,
        endAction = {
            if (settingSkinIsMiuix()) {
                MiuixSettingSwitch(checked, null, Modifier.clearAndSetSemantics { }, enabled)
            } else {
                SwitchWithIcon(
                    checked = checked,
                    onCheckedChange = null,
                    modifier = Modifier.clearAndSetSemantics { },
                    enabled = enabled,
                )
            }
        },
        enabled = enabled,
        settingKey = settingKey,
        highlightedKey = highlightedKey,
        onPositioned = onPositioned,
        bringIntoViewRequester = bringIntoViewRequester,
    )
}

@Composable
fun SwitchWithIcon(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        thumbContent = {
            if (checked) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            }
        },
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
    )
}

@Composable
fun SettingItem(
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    description: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    endAction: (@Composable () -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    shape: Shape = ListItemDefaults.shapes().shape,
    iconShape: Shape = RoundedCornerShape(AppDimens.SettingIconCornerRadius),
    colors: CardColors = CardColors(
        containerColor = appGroupedCardColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        disabledContainerColor = ListItemDefaults.segmentedColors().disabledContainerColor,
        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    ),
    enabled: Boolean = true,
    settingKey: String? = null,
    highlightedKey: String = "",
    onPositioned: ((rootY: Int) -> Unit)? = null,
    bringIntoViewRequester: BringIntoViewRequester? = null,
) {
    if (settingSkinIsMiuix()) {
        MiuixSettingsRenderer.row(
            modifier = modifier
                .then(
                    if (checked != null && onCheckedChange != null) {
                        Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                    } else {
                        Modifier
                    },
                ).highlightSetting(
                    settingKey = settingKey,
                    highlightedKey = highlightedKey,
                    onPositioned = onPositioned,
                    bringIntoViewRequester = bringIntoViewRequester,
                ),
            title = title,
            description = description,
            icon = icon,
            onClick = onClick,
            endAction = endAction,
            bottomAction = bottomAction,
            rowModifier = contentModifier,
            enabled = enabled,
            iconShape = iconShape,
        )
        return
    }
    val leadingContent: (@Composable () -> Unit)? = icon?.let { content ->
        {
            Surface(
                modifier = Modifier.size(AppDimens.SettingRowIconSize),
                shape = iconShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    colors.disabledContentColor
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    content()
                }
            }
        }
    }
    val trailingContent: (@Composable () -> Unit)? = when {
        endAction != null -> endAction
        onClick != null -> {
            {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(AppDimens.SettingChevronSize),
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        colors.disabledContentColor
                    },
                )
            }
        }
        else -> null
    }
    if (checked != null && onCheckedChange != null && bottomAction == null && contentModifier == Modifier) {
        SegmentedListItem(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier.highlightSetting(
                settingKey = settingKey,
                highlightedKey = highlightedKey,
                onPositioned = onPositioned,
                bringIntoViewRequester = bringIntoViewRequester,
                shape = shape,
            ),
            enabled = enabled,
            shapes = ListItemDefaults.shapes(shape = shape, selectedShape = shape),
            colors = ListItemDefaults.segmentedColors(
                containerColor = colors.containerColor,
                contentColor = colors.contentColor,
                supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledContainerColor = colors.disabledContainerColor,
                disabledContentColor = colors.disabledContentColor,
                disabledSupportingContentColor = colors.disabledContentColor,
                disabledTrailingContentColor = colors.disabledContentColor,
                selectedContainerColor = colors.containerColor,
                selectedContentColor = colors.contentColor,
                selectedSupportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            contentPadding = ListItemDefaults.ContentPadding,
            leadingContent = leadingContent,
            supportingContent = description,
            trailingContent = trailingContent,
            content = title,
        )
        return
    }
    // 底部滑杆/输入框必须保留整行宽度；有整行动作且没有底部控件时直接交给官方分段列表项。
    if (onClick != null && bottomAction == null && contentModifier == Modifier) {
        SegmentedListItem(
            onClick = onClick,
            modifier = modifier.highlightSetting(
                settingKey = settingKey,
                highlightedKey = highlightedKey,
                onPositioned = onPositioned,
                bringIntoViewRequester = bringIntoViewRequester,
                shape = shape,
            ),
            enabled = enabled,
            shapes = ListItemDefaults.shapes(shape = shape),
            colors = ListItemDefaults.segmentedColors(
                containerColor = colors.containerColor,
                contentColor = colors.contentColor,
                supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledContainerColor = colors.disabledContainerColor,
                disabledContentColor = colors.disabledContentColor,
                disabledSupportingContentColor = colors.disabledContentColor,
                disabledTrailingContentColor = colors.disabledContentColor,
            ),
            contentPadding = ListItemDefaults.ContentPadding,
            leadingContent = leadingContent,
            supportingContent = description,
            trailingContent = trailingContent?.let { content ->
                { ProvideTextStyle(MaterialTheme.typography.bodyLarge, content) }
            },
            content = title,
        )
        return
    }
    val compoundModifier = if (checked != null && onCheckedChange != null) {
        modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
    } else {
        modifier
    }
    Surface(
        modifier = if (onClick != null) {
            compoundModifier
                .clip(shape)
                .highlightSetting(
                    settingKey = settingKey,
                    highlightedKey = highlightedKey,
                    onPositioned = onPositioned,
                    bringIntoViewRequester = bringIntoViewRequester,
                    shape = shape,
                ).clickable(
                    enabled = enabled,
                    onClick = onClick,
                )
        } else {
            compoundModifier
                .clip(shape)
                .highlightSetting(
                    settingKey = settingKey,
                    highlightedKey = highlightedKey,
                    onPositioned = onPositioned,
                    bringIntoViewRequester = bringIntoViewRequester,
                    shape = shape,
                )
        },
        shape = shape,
        color = if (enabled) colors.containerColor else colors.disabledContainerColor,
        contentColor = if (enabled) {
            colors.contentColor
        } else {
            colors.disabledContentColor
        },
    ) {
        Column(
            contentModifier.padding(
                horizontal = AppDimens.SettingRowHorizontalPadding,
                vertical = AppDimens.FeedCardContentTopPadding,
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SettingRowIconSpacing),
            ) {
                leadingContent?.invoke()
                Column(
                    modifier = Modifier
                        .heightIn(min = AppDimens.TouchTarget)
                        .weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    ProvideContentColorTextStyle(
                        textStyle = MaterialTheme.typography.bodyLarge,
                        contentColor = if (enabled) {
                            colors.contentColor
                        } else {
                            colors.disabledContentColor
                        },
                    ) {
                        title()
                    }
                    description?.let {
                        ProvideContentColorTextStyle(
                            textStyle = MaterialTheme.typography.bodyMedium,
                            contentColor = if (enabled) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                colors.disabledContentColor
                            },
                        ) {
                            description()
                        }
                    }
                }
                trailingContent?.invoke()
            }
            bottomAction?.let { action ->
                Box(Modifier.fillMaxWidth()) { action() }
            }
        }
    }
}
