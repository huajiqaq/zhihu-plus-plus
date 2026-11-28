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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens

/** Miuix 0.9.4 子项固定为 Tab 且无逐项 tag/enabled，需容器定位与回调禁用门控。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    testTags: List<String> = emptyList(),
    enabled: Boolean = true,
) {
    if (!settingSkinIsMiuix()) {
        BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
            val measurer = rememberTextMeasurer()
            val style = MaterialTheme.typography.labelLarge
            val padding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight)
            val direction = LocalLayoutDirection.current
            val count = options.size.coerceAtLeast(1)
            val buttonWidth = (maxWidth - ButtonGroupDefaults.ConnectedSpaceBetween * (count - 1)) / count
            val textBudget = buttonWidth - padding.calculateStartPadding(direction) - padding.calculateEndPadding(direction)
            val budgetPx = with(LocalDensity.current) { textBudget.roundToPx() }
            val fits = options.all { measurer.measure(it, style, softWrap = false).size.width <= budgetPx }
            if (!fits) {
                AppChoiceColumn(options, selectedIndex, onSelect, testTags = testTags, enabled = enabled)
                return@BoxWithConstraints
            }
            Row(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                options.forEachIndexed { index, option ->
                    ToggleButton(
                        checked = index == selectedIndex,
                        enabled = enabled,
                        onCheckedChange = { checked -> if (checked) onSelect(index) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = AppDimens.TouchTarget)
                            .then(testTags.getOrNull(index)?.let { Modifier.testTag(it) } ?: Modifier)
                            .clearAndSetSemantics {
                                text = AnnotatedString(option)
                                role = Role.RadioButton
                                selected = index == selectedIndex
                                if (!enabled) disabled()
                                onClick {
                                    if (enabled) {
                                        onSelect(index)
                                        true
                                    } else {
                                        false
                                    }
                                }
                            },
                        shapes = when {
                            options.size == 1 -> ToggleButtonDefaults.shapesFor(ToggleButtonDefaults.size)
                            index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            index == options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        contentPadding = padding,
                        colors = if (index == selectedIndex && !enabled) {
                            ToggleButtonDefaults.colors(
                                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f),
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        } else {
                            ToggleButtonDefaults.colors()
                        },
                    ) {
                        /** String 快速路径的语义重建可能扩大段宽；标签测量、绘制和选项语义须共用真实段落布局。 */
                        Text(
                            text = AnnotatedString(option),
                            style = style,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        return
    }

    /** Miuix 胶囊固定单行且溢出省略，需测量后回退列表以保留完整选项。 */
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val measurer = rememberTextMeasurer()
        val style = LocalAppSkinTokens.current.body2.copy(fontWeight = FontWeight.Bold)
        val count = options.size.coerceAtLeast(1)
        val tabWidth = (maxWidth - AppDimens.Space10 - AppDimens.Space4 * (count - 1)) / count
        val textBudget = (tabWidth - AppDimens.Space8).coerceAtLeast(AppDimens.Space4)
        val density = LocalDensity.current
        val budgetPx = with(density) { textBudget.roundToPx() }
        val fits = remember(options, style, budgetPx, density.fontScale) {
            options.all { option -> measurer.measure(option, style, maxLines = 1).size.width <= budgetPx }
        }
        if (fits) {
            MiuixAppSegmentedControl(
                options = options,
                selectedIndex = selectedIndex,
                onSelect = onSelect,
                modifier = Modifier,
                enabled = enabled,
            )
        } else {
            AppChoiceColumn(options, selectedIndex, onSelect, testTags = testTags, enabled = enabled)
        }
    }
}

@Composable
fun AppChoiceColumn(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    testTags: List<String> = emptyList(),
    enabled: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth().selectableGroup()) {
        options.forEachIndexed { index, option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AppDimens.TouchTarget)
                    .selectable(
                        selected = index == selectedIndex,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(index) },
                    ).then(testTags.getOrNull(index)?.let { Modifier.testTag(it) } ?: Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppRadioButton(
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    enabled = enabled,
                    modifier = Modifier.clearAndSetSemantics { },
                )
                Spacer(Modifier.width(AppDimens.Space8))
                Text(
                    text = option,
                    modifier = Modifier.weight(1f),
                    style = if (settingSkinIsMiuix()) LocalAppSkinTokens.current.main else MaterialTheme.typography.bodyLarge,
                    color = if (settingSkinIsMiuix() && !enabled) LocalAppSkinTokens.current.disabledOnSurface else Color.Unspecified,
                )
            }
        }
    }
}

@Composable
internal expect fun MiuixAppSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
)

@Composable
fun AppEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = AppDimens.EmptyStateHorizontalPadding,
                vertical = AppDimens.EmptyStateVerticalPadding,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(
                        if (settingSkinIsMiuix()) {
                            AppDimens.EmptyStateIconSizeMiuix
                        } else {
                            AppDimens.EmptyStateIconSizeM3e
                        },
                    ).background(
                        color = if (settingSkinIsMiuix()) {
                            LocalAppSkinTokens.current.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        shape = if (settingSkinIsMiuix()) {
                            RoundedCornerShape(AppDimens.EmptyStateIconCornerMiuix)
                        } else {
                            CircleShape
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (settingSkinIsMiuix()) {
                        LocalAppSkinTokens.current.onSurfaceVariantActions
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                )
            }
            Spacer(Modifier.height(AppDimens.EmptyStateTitleSpacing))
        }
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = if (settingSkinIsMiuix()) {
                LocalAppSkinTokens.current.title2
            } else {
                MaterialTheme.typography.titleLarge
            },
            color = if (settingSkinIsMiuix()) {
                LocalAppSkinTokens.current.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.Center,
        )
        description?.let {
            Spacer(Modifier.height(AppDimens.EmptyStateDescriptionSpacing))
            Text(
                text = it,
                style = if (settingSkinIsMiuix()) {
                    LocalAppSkinTokens.current.footnote2
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = if (settingSkinIsMiuix()) {
                    LocalAppSkinTokens.current.onSurfaceVariantSummary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
            )
        }
        action?.let {
            Spacer(Modifier.height(AppDimens.EmptyStateActionSpacing))
            it()
        }
    }
}

@Composable
fun AppPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showTitle: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val isMiuix = settingSkinIsMiuix()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (isMiuix) AppDimens.PageHeaderMiuixHorizontalPadding else AppDimens.PageHeaderM3eHorizontalPadding,
                end = if (isMiuix) AppDimens.PageHeaderMiuixHorizontalPadding else AppDimens.PageHeaderM3eHorizontalPadding,
                top = if (isMiuix) AppDimens.PageHeaderTopPaddingMiuix else AppDimens.PageHeaderTopPaddingM3e,
                bottom = AppDimens.PageHeaderBottomPadding,
            ),
    ) {
        AnimatedVisibility(
            visible = showTitle,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.HeaderActionSpacing),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        modifier = Modifier.semantics { heading() },
                        style = (
                            if (isMiuix) LocalAppSkinTokens.current.title2 else MaterialTheme.typography.headlineMedium
                        ).copy(fontWeight = FontWeight.SemiBold),
                        color = if (isMiuix) LocalAppSkinTokens.current.onSurface else MaterialTheme.colorScheme.onBackground,
                    )
                    subtitle?.let {
                        Spacer(
                            Modifier.height(
                                if (isMiuix) AppDimens.PageHeaderSubtitleSpacing else AppDimens.PageHeaderContentSpacing,
                            ),
                        )
                        Text(
                            text = it,
                            style = if (isMiuix) LocalAppSkinTokens.current.footnote2 else MaterialTheme.typography.bodyLarge,
                            color = if (isMiuix) {
                                LocalAppSkinTokens.current.onSurfaceVariantSummary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                actions()
            }
        }
        content()
    }
}

@Composable
fun AppSearchBar(
    hint: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    if (settingSkinIsMiuix()) {
        MiuixPageRenderer.SearchBar(hint, onClick, modifier, trailing)
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AppDimens.SearchBarHeightM3e)
            /** clip 在 clickable 前，避免方形水波纹。 */
            .clip(RoundedCornerShape(AppDimens.SheetCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClickLabel = hint, onClick = onClick)
            .padding(
                start = AppDimens.SearchBarStartPadding,
                end = if (trailing == null) {
                    AppDimens.SearchBarStartPadding
                } else {
                    AppDimens.SearchBarTrailingPadding
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(AppDimens.SearchBarIconSpacing))
        Text(
            text = hint,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        trailing?.invoke()
    }
}

@Composable
fun AppSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Space4, vertical = AppDimens.Space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = if (settingSkinIsMiuix()) {
                LocalAppSkinTokens.current.footnote2
            } else {
                MaterialTheme.typography.labelLarge
            },
            color = if (settingSkinIsMiuix()) {
                LocalAppSkinTokens.current.onSurfaceVariantSummary
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
        trailing?.invoke()
    }
}
