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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconToggleButtonColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize

internal actual object MiuixPrimitives {
    private fun unsupported(): Nothing = error("Miuix theme primitives are not supported by the lite variant")

    @Composable actual fun iconButton(onClick: () -> Unit, modifier: Modifier, enabled: Boolean, colors: IconButtonColors, content: @Composable () -> Unit): Unit = unsupported()

    @Composable actual fun selectionDropdown(options: List<String>, selectedIndex: Int, summaries: List<String?>?, enabled: Boolean, expanded: Boolean, onDismissRequest: () -> Unit, onSelect: (Int) -> Unit, renderInRootScaffold: Boolean): Unit = unsupported()

    @Composable actual fun floatingActionButton(onClick: () -> Unit, modifier: Modifier, shape: Shape?, containerColor: Color?, contentColor: Color?, shadowElevation: Dp?, content: @Composable () -> Unit): Unit = unsupported()

    @Composable actual fun filledTonalIconButton(onClick: () -> Unit, modifier: Modifier, enabled: Boolean, colors: IconButtonColors, content: @Composable () -> Unit): Unit = unsupported()

    @Composable actual fun iconToggleButton(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier, enabled: Boolean, colors: IconToggleButtonColors, content: @Composable () -> Unit): Unit = unsupported()

    @Composable actual fun textButton(text: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean, contentColor: Color): Unit = unsupported()

    @Composable actual fun textButton(onClick: () -> Unit, modifier: Modifier, enabled: Boolean, contentColor: Color, contentPadding: PaddingValues?, content: @Composable RowScope.() -> Unit): Unit = unsupported()

    @Composable actual fun button(text: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean): Unit = unsupported()

    @Composable actual fun button(onClick: () -> Unit, modifier: Modifier, enabled: Boolean, containerColor: Color?, contentColor: Color?, shape: Shape?, contentPadding: PaddingValues?, content: @Composable RowScope.() -> Unit): Unit = unsupported()

    @Composable actual fun outlinedButton(text: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean): Unit = unsupported()

    @Composable actual fun outlinedButton(onClick: () -> Unit, modifier: Modifier, enabled: Boolean, containerColor: Color?, contentColor: Color?, shape: Shape?, content: @Composable RowScope.() -> Unit): Unit = unsupported()

    @Composable actual fun chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier, enabled: Boolean): Unit = unsupported()

    @Composable actual fun chip(label: @Composable () -> Unit, selected: Boolean, onClick: () -> Unit, modifier: Modifier, enabled: Boolean, leadingIcon: (@Composable () -> Unit)?, trailingIcon: (@Composable () -> Unit)?): Unit = unsupported()

    @Composable actual fun checkbox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier, enabled: Boolean): Unit = unsupported()

    @Composable actual fun radioButton(selected: Boolean, onClick: () -> Unit, modifier: Modifier, enabled: Boolean): Unit = unsupported()

    @Composable actual fun slider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier, enabled: Boolean, valueRange: ClosedFloatingPointRange<Float>, steps: Int, onValueChangeFinished: (() -> Unit)?): Unit = unsupported()

    @Composable actual fun pullToRefresh(isRefreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier, contentPadding: PaddingValues, content: @Composable BoxScope.() -> Unit): Unit = unsupported()

    @Composable actual fun card(modifier: Modifier, onClick: (() -> Unit)?, shape: Shape?, containerColor: Color?, contentColor: Color?, content: @Composable ColumnScope.() -> Unit): Unit = unsupported()

    @Composable actual fun spinner(modifier: Modifier, size: Dp?, color: Color?): Unit = unsupported()

    @Composable actual fun linearProgress(progress: Float?, modifier: Modifier, color: Color?, trackColor: Color?): Unit = unsupported()

    @Composable actual fun dialog(onDismissRequest: () -> Unit, confirmText: String, onConfirm: () -> Unit, title: String?, modifier: Modifier, summary: String?, confirmEnabled: Boolean, dismissText: String?, dismissAction: () -> Unit, destructive: Boolean): Unit = unsupported()

    @Composable actual fun alertDialog(onDismissRequest: () -> Unit, modifier: Modifier, title: (@Composable () -> Unit)?, text: (@Composable () -> Unit)?, confirmButton: @Composable () -> Unit, dismissButton: (@Composable () -> Unit)?): Unit = unsupported()

    @Composable actual fun textField(value: String, onValueChange: (String) -> Unit, modifier: Modifier, placeholder: String, labelText: String, enabled: Boolean, readOnly: Boolean, singleLine: Boolean, minLines: Int, maxLines: Int, keyboardOptions: KeyboardOptions, keyboardActions: KeyboardActions, visualTransformation: VisualTransformation, miuixCornerRadius: Dp?, miuixInsideMargin: DpSize?, leadingIcon: @Composable (() -> Unit)?, trailingIcon: @Composable (() -> Unit)?): Unit = unsupported()

    @Composable actual fun searchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier, placeholder: String, keyboardOptions: KeyboardOptions, keyboardActions: KeyboardActions, leadingIcon: @Composable (() -> Unit)?, trailingIcon: @Composable (() -> Unit)?): Unit = unsupported()
}
