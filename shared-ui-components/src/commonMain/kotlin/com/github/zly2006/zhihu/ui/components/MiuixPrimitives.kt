/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 only.
 */

package com.github.zly2006.zhihu.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconToggleButtonColors
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize

internal expect object MiuixPrimitives {
    @Composable fun iconButton(onClick: () -> Unit, modifier: Modifier, enabled: Boolean, colors: IconButtonColors, content: @Composable () -> Unit)

    @Composable fun dropdownMenu(expanded: Boolean, onDismissRequest: () -> Unit, modifier: Modifier, offset: DpOffset, renderInRootScaffold: Boolean, content: @Composable ColumnScope.() -> Unit)

    @Composable fun dropdownMenuItem(text: @Composable () -> Unit, onClick: () -> Unit, modifier: Modifier, leadingIcon: (@Composable () -> Unit)?, trailingIcon: (@Composable () -> Unit)?, enabled: Boolean)

    @Composable fun selectionDropdown(options: List<String>, selectedIndex: Int, summaries: List<String?>?, enabled: Boolean, expanded: Boolean, onDismissRequest: () -> Unit, onSelect: (Int) -> Unit, renderInRootScaffold: Boolean)

    @Composable fun floatingActionButton(onClick: () -> Unit, modifier: Modifier, shape: Shape?, containerColor: Color?, contentColor: Color?, shadowElevation: Dp?, content: @Composable () -> Unit)

    @Composable fun filledTonalIconButton(onClick: () -> Unit, modifier: Modifier, enabled: Boolean, colors: IconButtonColors, content: @Composable () -> Unit)

    @Composable fun iconToggleButton(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier, enabled: Boolean, colors: IconToggleButtonColors, content: @Composable () -> Unit)

    @Composable fun textButton(text: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean, contentColor: Color)

    @Composable fun textButton(onClick: () -> Unit, modifier: Modifier, enabled: Boolean, contentColor: Color, contentPadding: PaddingValues?, content: @Composable RowScope.() -> Unit)

    @Composable fun button(text: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean)

    @Composable fun button(onClick: () -> Unit, modifier: Modifier, enabled: Boolean, containerColor: Color?, contentColor: Color?, shape: Shape?, contentPadding: PaddingValues?, content: @Composable RowScope.() -> Unit)

    @Composable fun outlinedButton(text: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean)

    @Composable fun outlinedButton(onClick: () -> Unit, modifier: Modifier, enabled: Boolean, containerColor: Color?, contentColor: Color?, shape: Shape?, content: @Composable RowScope.() -> Unit)

    @Composable fun chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier, enabled: Boolean)

    @Composable fun chip(label: @Composable () -> Unit, selected: Boolean, onClick: () -> Unit, modifier: Modifier, enabled: Boolean, leadingIcon: (@Composable () -> Unit)?, trailingIcon: (@Composable () -> Unit)?)

    @Composable fun checkbox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier, enabled: Boolean)

    @Composable fun radioButton(selected: Boolean, onClick: () -> Unit, modifier: Modifier, enabled: Boolean)

    @Composable fun slider(state: SliderState, onValueChange: (Float) -> Unit, modifier: Modifier, enabled: Boolean, onValueChangeFinished: (() -> Unit)?)

    @Composable fun pullToRefresh(isRefreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier, contentPadding: PaddingValues, content: @Composable BoxScope.() -> Unit)

    @Composable fun card(modifier: Modifier, onClick: (() -> Unit)?, shape: Shape?, containerColor: Color?, contentColor: Color?, content: @Composable ColumnScope.() -> Unit)

    @Composable fun spinner(modifier: Modifier, size: Dp?, color: Color?)

    @Composable fun linearProgress(progress: Float?, modifier: Modifier, color: Color?, trackColor: Color?)

    @Composable fun dialog(onDismissRequest: () -> Unit, confirmText: String, onConfirm: () -> Unit, title: String?, modifier: Modifier, summary: String?, confirmEnabled: Boolean, dismissText: String?, dismissAction: () -> Unit, destructive: Boolean)

    @Composable fun alertDialog(onDismissRequest: () -> Unit, modifier: Modifier, title: (@Composable () -> Unit)?, text: (@Composable () -> Unit)?, confirmButton: @Composable () -> Unit, dismissButton: (@Composable () -> Unit)?)

    @Composable fun textField(state: TextFieldState, modifier: Modifier, placeholder: String, labelText: String, enabled: Boolean, readOnly: Boolean, lineLimits: TextFieldLineLimits, keyboardOptions: KeyboardOptions, onKeyboardAction: KeyboardActionHandler?, inputTransformation: InputTransformation?, outputTransformation: OutputTransformation?, miuixCornerRadius: Dp?, miuixInsideMargin: DpSize?, leadingIcon: @Composable (() -> Unit)?, trailingIcon: @Composable (() -> Unit)?)

    @Composable fun secureTextField(state: TextFieldState, labelText: String, modifier: Modifier, placeholder: String, enabled: Boolean, textObfuscationMode: TextObfuscationMode, trailingIcon: @Composable (() -> Unit)?)

    @Composable fun searchField(state: TextFieldState, modifier: Modifier, placeholder: String, keyboardOptions: KeyboardOptions, onKeyboardAction: KeyboardActionHandler?, inputTransformation: InputTransformation?, outputTransformation: OutputTransformation?, leadingIcon: @Composable (() -> Unit)?, trailingIcon: @Composable (() -> Unit)?)
}
