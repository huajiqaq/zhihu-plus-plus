/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 only.
 */

package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconToggleButtonColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.RefreshState
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Checkbox as MiuixCheckbox
import top.yukonga.miuix.kmp.basic.FloatingActionButton as MiuixFloatingActionButton
import top.yukonga.miuix.kmp.basic.FloatingActionButtonDefaults as MiuixFabDefaults
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator as MiuixInfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator as MiuixLinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults as MiuixProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.PullToRefresh as MiuixPullToRefresh
import top.yukonga.miuix.kmp.basic.RadioButton as MiuixRadioButton
import top.yukonga.miuix.kmp.basic.Slider as MiuixSlider
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults as MiuixTextFieldDefaults
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState as rememberMiuixPullToRefreshState
import top.yukonga.miuix.kmp.theme.LocalContentColor as MiuixContentColor

internal actual object MiuixPrimitives {
    @Composable
    actual fun iconButton(onClick: () -> Unit, modifier: Modifier, enabled: Boolean, colors: IconButtonColors, content: @Composable () -> Unit) {
        MiuixIconButton(
            onClick,
            modifier.semantics(mergeDescendants = true) {
                role = Role.Button
                if (!enabled) disabled()
            },
            enabled,
            backgroundColor = if (enabled) colors.containerColor else colors.disabledContainerColor,
            minHeight = AppDimens.IconButtonMinSize,
            minWidth = AppDimens.IconButtonMinSize,
        ) {
            val foreground = if (enabled) colors.contentColor else colors.disabledContentColor
            CompositionLocalProvider(LocalContentColor provides foreground, MiuixContentColor provides foreground) { content() }
        }
    }

    @Composable
    actual fun selectionDropdown(options: List<String>, selectedIndex: Int, summaries: List<String?>?, enabled: Boolean, expanded: Boolean, onDismissRequest: () -> Unit, onSelect: (Int) -> Unit, renderInRootScaffold: Boolean) {
        if (!rememberNavEntryOnTop()) return
        val currentEnabled by rememberUpdatedState(enabled)
        OverlayDropdownPopup(
            entry = DropdownEntry(
                items = options.mapIndexed { index, label ->
                    DropdownItem(
                        text = label,
                        enabled = enabled,
                        summary = summaries?.getOrNull(index),
                        selected = index == selectedIndex,
                        onClick = { if (currentEnabled) onSelect(index) },
                    )
                },
            ),
            show = expanded && enabled,
            onDismiss = onDismissRequest,
            onDismissFinished = {},
            maxHeight = null,
            dropdownColors = DropdownDefaults.dropdownColors(),
            renderInRootScaffold = renderInRootScaffold,
            collapseOnSelection = true,
        )
    }

    @Composable
    actual fun floatingActionButton(onClick: () -> Unit, modifier: Modifier, shape: Shape?, containerColor: Color?, contentColor: Color?, shadowElevation: Dp?, content: @Composable () -> Unit) {
        val container = containerColor ?: MiuixTheme.colorScheme.primary
        val foreground = contentColor ?: if (containerColor == null) MiuixTheme.colorScheme.onPrimary else contentColorFor(container)
        MiuixFloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            shape = shape ?: CircleShape,
            containerColor = container,
            shadowElevation = shadowElevation ?: MiuixFabDefaults.ShadowElevation,
        ) {
            CompositionLocalProvider(LocalContentColor provides foreground, MiuixContentColor provides foreground) { content() }
        }
    }

    @Composable
    actual fun filledTonalIconButton(onClick: () -> Unit, modifier: Modifier, enabled: Boolean, colors: IconButtonColors, content: @Composable () -> Unit) {
        MiuixIconButton(
            onClick,
            modifier.semantics(mergeDescendants = true) {
                role = Role.Button
                if (!enabled) disabled()
            },
            enabled,
            backgroundColor = if (enabled) colors.containerColor else colors.disabledContainerColor,
            minHeight = AppDimens.IconButtonMinSize,
            minWidth = AppDimens.IconButtonMinSize,
        ) {
            CompositionLocalProvider(LocalContentColor provides if (enabled) colors.contentColor else colors.disabledContentColor) { content() }
        }
    }

    @Composable
    actual fun iconToggleButton(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier, enabled: Boolean, colors: IconToggleButtonColors, content: @Composable () -> Unit) {
        MiuixIconButton(
            { onCheckedChange(!checked) },
            modifier.semantics(mergeDescendants = true) {
                role = Role.Checkbox
                toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                if (!enabled) disabled()
            },
            enabled,
            backgroundColor = when {
                !enabled -> colors.disabledContainerColor
                checked -> colors.checkedContainerColor
                else -> colors.containerColor
            },
            minHeight = AppDimens.IconButtonMinSize,
            minWidth = AppDimens.IconButtonMinSize,
        ) {
            val foreground = when {
                !enabled -> colors.disabledContentColor
                checked -> colors.checkedContentColor
                else -> colors.contentColor
            }
            CompositionLocalProvider(LocalContentColor provides foreground, MiuixContentColor provides foreground) { content() }
        }
    }

    @Composable
    actual fun textButton(text: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean, contentColor: Color) {
        MiuixTextButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = ButtonDefaults.textButtonColors(
                color = Color.Transparent,
                disabledColor = Color.Transparent,
                textColor = if (contentColor == Color.Unspecified) MiuixTheme.colorScheme.primary else contentColor,
            ),
        )
    }

    @Composable
    actual fun textButton(onClick: () -> Unit, modifier: Modifier, enabled: Boolean, contentColor: Color, contentPadding: PaddingValues?, content: @Composable RowScope.() -> Unit) {
        MiuixButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            insideMargin = contentPadding ?: ButtonDefaults.InsideMargin,
            colors = ButtonDefaults.buttonColors(
                color = Color.Transparent,
                disabledColor = Color.Transparent,
                contentColor = if (contentColor == Color.Unspecified) MiuixTheme.colorScheme.primary else contentColor,
            ),
        ) {
            CompositionLocalProvider(LocalContentColor provides MiuixContentColor.current) { content() }
        }
    }

    @Composable
    actual fun button(text: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean) {
        /** 默认次级底色难以区分禁用态，主操作使用 primary。 */
        MiuixButton(onClick, modifier, enabled, colors = ButtonDefaults.buttonColorsPrimary()) { MiuixText(text) }
    }

    @Composable
    actual fun button(onClick: () -> Unit, modifier: Modifier, enabled: Boolean, containerColor: Color?, contentColor: Color?, shape: Shape?, contentPadding: PaddingValues?, content: @Composable RowScope.() -> Unit) {
        val container = containerColor ?: MiuixTheme.colorScheme.primary
        val foreground = contentColor ?: if (containerColor == null) MiuixTheme.colorScheme.onPrimary else contentColorFor(container)
        /** 原生 Button 仅支持统一圆角，自定义 Shape 由外层裁切。 */
        MiuixButton(
            onClick = onClick,
            modifier = if (shape == null) modifier else modifier.clip(shape),
            enabled = enabled,
            cornerRadius = if (shape == null) ButtonDefaults.CornerRadius else 0.dp,
            insideMargin = contentPadding ?: ButtonDefaults.InsideMargin,
            colors = ButtonDefaults.buttonColorsPrimary(color = container, contentColor = foreground),
        ) {
            CompositionLocalProvider(LocalContentColor provides MiuixContentColor.current) { content() }
        }
    }

    @Composable
    actual fun outlinedButton(text: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean) {
        MiuixTextButton(text, onClick, modifier, enabled = enabled)
    }

    @Composable
    actual fun outlinedButton(onClick: () -> Unit, modifier: Modifier, enabled: Boolean, containerColor: Color?, contentColor: Color?, shape: Shape?, content: @Composable RowScope.() -> Unit) {
        val container = containerColor ?: MiuixTheme.colorScheme.secondaryVariant
        val foreground = contentColor ?: if (containerColor == null) MiuixTheme.colorScheme.onSecondaryVariant else contentColorFor(container)
        MiuixButton(
            onClick = onClick,
            modifier = if (shape == null) modifier else modifier.clip(shape),
            enabled = enabled,
            cornerRadius = if (shape == null) ButtonDefaults.CornerRadius else 0.dp,
            colors = ButtonDefaults.buttonColors(color = container, contentColor = foreground),
        ) {
            CompositionLocalProvider(LocalContentColor provides MiuixContentColor.current) { content() }
        }
    }

    @Composable
    actual fun chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier, enabled: Boolean) {
        chip({ MiuixText(label, style = LocalAppSkinTokens.current.footnote1) }, selected, onClick, modifier, enabled, null, null)
    }

    @Composable
    actual fun chip(label: @Composable () -> Unit, selected: Boolean, onClick: () -> Unit, modifier: Modifier, enabled: Boolean, leadingIcon: (@Composable () -> Unit)?, trailingIcon: (@Composable () -> Unit)?) {
        MiuixButton(
            onClick = onClick,
            modifier = modifier.semantics { this.selected = selected },
            enabled = enabled,
            minWidth = AppDimens.ChipMinWidth,
            minHeight = AppDimens.ChipHeight,
            cornerRadius = AppDimens.CompactCornerRadius,
            insideMargin = PaddingValues(horizontal = AppDimens.ChipHorizontalPadding),
            colors = ButtonDefaults.buttonColors(
                color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.secondaryContainer,
                contentColor = if (selected) LocalAppSkinTokens.current.onPrimary else LocalAppSkinTokens.current.onSurface,
            ),
        ) {
            CompositionLocalProvider(LocalContentColor provides MiuixContentColor.current) {
                ProvideTextStyle(LocalAppSkinTokens.current.footnote1) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        leadingIcon?.invoke()
                        label()
                        trailingIcon?.invoke()
                    }
                }
            }
        }
    }

    @Composable
    actual fun checkbox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier, enabled: Boolean) {
        MiuixCheckbox(if (checked) ToggleableState.On else ToggleableState.Off, onClick = onCheckedChange?.let { { it(!checked) } }, modifier = modifier, enabled = enabled)
    }

    @Composable
    actual fun radioButton(selected: Boolean, onClick: () -> Unit, modifier: Modifier, enabled: Boolean) {
        MiuixRadioButton(
            selected = selected,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        )
    }

    @Composable
    actual fun slider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier, enabled: Boolean, valueRange: ClosedFloatingPointRange<Float>, steps: Int, onValueChangeFinished: (() -> Unit)?) {
        MiuixSlider(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.padding(horizontal = AppDimens.Space8).then(
                if (enabled) {
                    Modifier
                } else {
                    Modifier.clearAndSetSemantics {
                        disabled()
                        progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange, steps)
                    }
                },
            ),
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
        )
    }

    @Composable
    actual fun pullToRefresh(isRefreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier, contentPadding: PaddingValues, content: @Composable BoxScope.() -> Unit) {
        val state = rememberMiuixPullToRefreshState()
        CompositionLocalProvider(LocalPullToRefreshIndicatorVisible provides (state.refreshState != RefreshState.Idle)) {
            MiuixPullToRefresh(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = modifier,
                pullToRefreshState = state,
                contentPadding = contentPadding,
                refreshTexts = listOf("下拉刷新", "松开刷新", "正在刷新…", "刷新完成"),
            ) {
                Box(Modifier.fillMaxSize()) { content() }
            }
        }
    }

    @Composable
    actual fun card(modifier: Modifier, onClick: (() -> Unit)?, shape: Shape?, containerColor: Color?, contentColor: Color?, content: @Composable ColumnScope.() -> Unit) {
        val container = containerColor ?: MiuixTheme.colorScheme.surfaceContainer
        val foreground = contentColor ?: if (containerColor == null) MiuixTheme.colorScheme.onSurfaceContainer else contentColorFor(container)
        val colors = CardDefaults.defaultColors(color = container, contentColor = foreground)
        val contentSlot: @Composable ColumnScope.() -> Unit = {
            CompositionLocalProvider(LocalContentColor provides MiuixContentColor.current) { content() }
        }
        /** 原生 Card 不支持任意 Shape，外层裁切保留该契约。 */
        val cardModifier = if (shape == null) modifier else modifier.clip(shape)
        val radius = if (shape == null) CardDefaults.CornerRadius else 0.dp
        if (onClick == null) {
            MiuixCard(modifier = cardModifier, cornerRadius = radius, colors = colors, content = contentSlot)
        } else {
            MiuixCard(modifier = cardModifier, cornerRadius = radius, colors = colors, onClick = onClick, pressFeedbackType = PressFeedbackType.Sink, content = contentSlot)
        }
    }

    @Composable
    actual fun spinner(modifier: Modifier, size: Dp?, color: Color?) {
        val indicatorModifier = modifier.semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }
        val indicatorColor = color ?: LocalContentColor.current
        if (size == null) {
            MiuixInfiniteProgressIndicator(modifier = indicatorModifier, color = indicatorColor)
        } else {
            MiuixInfiniteProgressIndicator(modifier = indicatorModifier, size = size, color = indicatorColor)
        }
    }

    @Composable
    actual fun linearProgress(progress: Float?, modifier: Modifier, color: Color?, trackColor: Color?) {
        MiuixLinearProgressIndicator(
            modifier = modifier,
            progress = progress,
            colors = MiuixProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = color ?: MiuixTheme.colorScheme.primary,
                backgroundColor = trackColor ?: MiuixTheme.colorScheme.secondaryContainer,
            ),
        )
    }

    @Composable
    actual fun dialog(
        onDismissRequest: () -> Unit,
        confirmText: String,
        onConfirm: () -> Unit,
        title: String?,
        modifier: Modifier,
        summary: String?,
        confirmEnabled: Boolean,
        dismissText: String?,
        dismissAction: () -> Unit,
        destructive: Boolean,
    ) {
        /** 确认窗必须盖住当前 Sheet，不能投递到下层 Scaffold。 */
        WindowDialog(
            show = true,
            modifier = modifier,
            onDismissRequest = onDismissRequest,
        ) {
            RegisterOverlayBlur()
            AppModalContent(
                title = title?.let { value -> { MiuixText(value) } },
                text = summary?.let { value -> { MiuixText(value) } },
                confirmButton = {
                    MiuixTextButton(
                        confirmText,
                        onConfirm,
                        enabled = confirmEnabled,
                        colors = if (destructive) {
                            ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error)
                        } else {
                            ButtonDefaults.textButtonColors()
                        },
                    )
                },
                dismissButton = dismissText?.let { value -> { MiuixTextButton(value, dismissAction) } },
            )
        }
    }

    @Composable
    actual fun alertDialog(
        onDismissRequest: () -> Unit,
        modifier: Modifier,
        title: (@Composable () -> Unit)?,
        text: (@Composable () -> Unit)?,
        confirmButton: @Composable () -> Unit,
        dismissButton: (@Composable () -> Unit)?,
    ) {
        WindowDialog(show = true, modifier = modifier, onDismissRequest = onDismissRequest) {
            RegisterOverlayBlur()
            AppModalContent(title, text, confirmButton, dismissButton)
        }
    }

    @Composable
    actual fun textField(
        value: String,
        onValueChange: (String) -> Unit,
        modifier: Modifier,
        placeholder: String,
        labelText: String,
        enabled: Boolean,
        readOnly: Boolean,
        singleLine: Boolean,
        minLines: Int,
        maxLines: Int,
        keyboardOptions: KeyboardOptions,
        keyboardActions: KeyboardActions,
        visualTransformation: VisualTransformation,
        miuixCornerRadius: Dp?,
        miuixInsideMargin: DpSize?,
        leadingIcon: @Composable (() -> Unit)?,
        trailingIcon: @Composable (() -> Unit)?,
    ) {
        MiuixTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            label = labelText.ifEmpty { placeholder },
            useLabelAsPlaceholder = labelText.isEmpty(),
            enabled = enabled,
            readOnly = readOnly,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            insideMargin = miuixInsideMargin ?: MiuixTextFieldDefaults.InsideMargin,
            cornerRadius = miuixCornerRadius ?: MiuixTextFieldDefaults.CornerRadius,
            colors = MiuixTextFieldDefaults.textFieldColors(
                labelColor = if (enabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.disabledOnSurface,
            ),
            visualTransformation = visualTransformation,
            leadingIcon = leadingIcon?.let { icon -> { Box(Modifier.padding(end = 8.dp)) { icon() } } },
            trailingIcon = trailingIcon,
        )
    }

    @Composable
    actual fun searchField(
        value: String,
        onValueChange: (String) -> Unit,
        modifier: Modifier,
        placeholder: String,
        keyboardOptions: KeyboardOptions,
        keyboardActions: KeyboardActions,
        leadingIcon: @Composable (() -> Unit)?,
        trailingIcon: @Composable (() -> Unit)?,
    ) {
        MiuixTextField(
            value = value,
            onValueChange = onValueChange,
            /** Miuix 0.9.4 会动画边框底色，透明内层避免获焦时闪边。 */
            modifier = modifier.squircleBackground(MiuixTheme.colorScheme.surfaceContainerHigh, AppDimens.SearchBarHeightMiuix),
            label = placeholder,
            useLabelAsPlaceholder = true,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = true,
            insideMargin = MiuixTextFieldDefaults.InsideMargin,
            cornerRadius = AppDimens.SearchBarHeightMiuix,
            textStyle = MiuixTheme.textStyles.main.copy(fontWeight = FontWeight.Normal),
            colors = MiuixTextFieldDefaults.textFieldColors(
                backgroundColor = Color.Transparent,
                labelColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                borderColor = Color.Transparent,
            ),
            cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
            leadingIcon = leadingIcon?.let { icon -> { Box(Modifier.padding(end = 8.dp)) { icon() } } },
            trailingIcon = trailingIcon,
        )
    }
}
