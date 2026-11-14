/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 */

package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.theme.ThemeManager
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon
import top.yukonga.miuix.kmp.basic.Text as MiuixText

@Composable
internal actual fun MiuixSettingSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier,
    enabled: Boolean,
) {
    Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)
}

internal actual object MiuixSettingsRenderer {
    @Composable
    actual fun group(
        modifier: Modifier,
        title: String?,
        header: (@Composable () -> Unit)?,
        footer: (@Composable () -> Unit)?,
        content: @Composable () -> Unit,
    ) {
        Column(
            modifier.padding(
                start = AppDimens.SettingGroupHorizontalMarginMiuix,
                end = AppDimens.SettingGroupHorizontalMarginMiuix,
                bottom = AppDimens.SettingGroupBottomMargin,
            ),
        ) {
            title?.let {
                SmallTitle(
                    text = it,
                    textColor = LocalAppSkinTokens.current.onSurfaceVariantSummary,
                    insideMargin = PaddingValues(
                        start = AppDimens.SettingRowHorizontalPadding,
                        top = AppDimens.Space16,
                        bottom = AppDimens.SectionLabelBottomPadding,
                    ),
                )
            }
            header?.invoke()
            Card(
                insideMargin = PaddingValues(AppDimens.ElevationNone),
                colors = CardDefaults.defaultColors(
                    color = if (ThemeManager.isDarkTheme()) {
                        MiuixTheme.colorScheme.surfaceContainerHighest
                    } else {
                        MiuixTheme.colorScheme.surfaceContainer
                    },
                    contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
                ),
            ) { content() }
            footer?.let {
                Box(
                    Modifier.fillMaxWidth().padding(
                        horizontal = AppDimens.SettingGroupHorizontalMargin,
                        vertical = AppDimens.Space8,
                    ),
                ) {
                    ProvideTextStyle(LocalAppSkinTokens.current.footnote2) { it() }
                }
            }
        }
    }

    @Composable
    actual fun row(
        modifier: Modifier,
        title: @Composable () -> Unit,
        description: (@Composable () -> Unit)?,
        icon: (@Composable () -> Unit)?,
        onClick: (() -> Unit)?,
        endAction: (@Composable () -> Unit)?,
        bottomAction: (@Composable () -> Unit)?,
        rowModifier: Modifier,
        enabled: Boolean,
        iconShape: Shape,
    ) {
        val tokens = LocalAppSkinTokens.current
        BasicComponent(
            modifier = modifier.then(rowModifier).semantics { if (!enabled) disabled() },
            startAction = icon?.let { content -> { settingIcon(content, enabled, iconShape) } },
            endActions = when {
                endAction != null -> {
                    { endAction() }
                }
                onClick != null -> {
                    { settingChevron(enabled) }
                }
                else -> null
            },
            bottomAction = bottomAction?.let { action ->
                { Box(Modifier.fillMaxWidth()) { action() } }
            },
            enabled = enabled,
            role = if (onClick != null) Role.Button else null,
            onClick = onClick,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides if (enabled) tokens.onSurface else tokens.disabledOnSurface,
                top.yukonga.miuix.kmp.theme.LocalContentColor provides if (enabled) tokens.onSurface else tokens.disabledOnSurface,
            ) {
                ProvideTextStyle(tokens.main, content = title)
            }
            if (description != null) {
                Spacer(Modifier.height(AppDimens.SettingTitleDescriptionSpacing))
                CompositionLocalProvider(
                    LocalContentColor provides if (enabled) tokens.onSurfaceVariantSummary else tokens.disabledOnSurface,
                    top.yukonga.miuix.kmp.theme.LocalContentColor provides if (enabled) tokens.onSurfaceVariantSummary else tokens.disabledOnSurface,
                ) {
                    ProvideTextStyle(tokens.footnote1, content = description)
                }
            }
        }
    }

    @Composable
    actual fun select(
        modifier: Modifier,
        title: @Composable () -> Unit,
        description: (@Composable () -> Unit)?,
        icon: (@Composable () -> Unit)?,
        options: List<String>,
        selectedIndex: Int,
        summaries: List<String?>?,
        enabled: Boolean,
        fieldModifier: Modifier,
        settingKey: String?,
        highlightedKey: String,
        onPositioned: ((Int) -> Unit)?,
        bringIntoViewRequester: BringIntoViewRequester?,
        expanded: Boolean,
        onExpandedChange: (Boolean) -> Unit,
        onSelect: (Int) -> Unit,
    ) {
        val currentEnabled by rememberUpdatedState(enabled)
        Box {
            SettingItem(
                modifier = modifier,
                title = title,
                description = description,
                icon = icon,
                onClick = if (enabled) ({ onExpandedChange(true) }) else null,
                endAction = {
                    selectValue(options.getOrElse(selectedIndex) { "" }, enabled, fieldModifier)
                },
                enabled = enabled,
                settingKey = settingKey,
                highlightedKey = highlightedKey,
                onPositioned = onPositioned,
                bringIntoViewRequester = bringIntoViewRequester,
            )
            MiuixPrimitives.selectionDropdown(
                options = options,
                selectedIndex = selectedIndex,
                summaries = summaries,
                enabled = enabled,
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
                onSelect = {
                    onExpandedChange(false)
                    if (currentEnabled) onSelect(it)
                },
                renderInRootScaffold = true,
            )
        }
    }

    @Composable
    private fun selectValue(text: String, enabled: Boolean, modifier: Modifier) {
        val tokens = LocalAppSkinTokens.current
        val actionColor = if (enabled) {
            tokens.onSurfaceVariantActions
        } else {
            tokens.disabledOnSecondaryVariant
        }
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            MiuixText(
                text = text,
                modifier = Modifier.weight(1f, fill = false).padding(end = AppDimens.Space8),
                fontSize = tokens.body2.fontSize,
                color = if (enabled) tokens.onSurfaceVariantSummary else tokens.disabledOnSecondaryVariant,
            )
            DropdownArrowEndAction(actionColor = actionColor)
        }
    }

    @Composable
    private fun settingIcon(icon: @Composable () -> Unit, enabled: Boolean, shape: Shape) {
        Box(
            modifier = Modifier
                .size(AppDimens.SettingRowIconSize)
                .clip(shape)
                // secondaryVariant 在亮/暗两种卡片底色上都有可见的层次；surfaceVariant 会与卡片同色。
                .background(LocalAppSkinTokens.current.secondaryVariant),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides if (enabled) {
                    LocalAppSkinTokens.current.onSecondaryVariant
                } else {
                    LocalAppSkinTokens.current.disabledOnSecondaryVariant
                },
                content = icon,
            )
        }
    }

    @Composable
    private fun settingChevron(enabled: Boolean) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(AppDimens.SettingChevronSize),
            tint = if (enabled) {
                LocalAppSkinTokens.current.onSurfaceVariantActions
            } else {
                LocalAppSkinTokens.current.disabledOnSurface
            },
        )
    }
}
