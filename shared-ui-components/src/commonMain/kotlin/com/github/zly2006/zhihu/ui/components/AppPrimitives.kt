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

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingActionButtonElevation
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.IconToggleButtonColors
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import kotlinx.coroutines.flow.merge
import androidx.compose.material3.ButtonDefaults as M3ButtonDefaults

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    content: @Composable () -> Unit,
) {
    if (settingSkinIsMiuix()) {
        MiuixPrimitives.iconButton(onClick, modifier, enabled, colors, content)
        return
    }
    IconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    renderInRootScaffold: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (settingSkinIsMiuix()) {
        MiuixPrimitives.dropdownMenu(expanded, onDismissRequest, modifier, offset, renderInRootScaffold, content)
        return
    }
    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        popupPositionProvider = MenuDefaults.rememberDropdownMenuPopupPositionProvider(MenuAnchorPosition.Below, offset),
    ) {
        AppDropdownMenuContent(modifier, content)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppDropdownMenuContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (settingSkinIsMiuix()) {
        Surface(
            shape = RoundedCornerShape(AppDimens.DropdownMenuCornerRadius),
            color = LocalAppSkinTokens.current.surface,
            tonalElevation = AppDimens.ElevationNone,
            shadowElevation = AppDimens.DropdownMenuElevation,
        ) {
            MaterialTheme(typography = MaterialTheme.typography.copy(labelLarge = LocalAppSkinTokens.current.main)) {
                Column(
                    modifier = modifier.verticalScroll(rememberScrollState()).padding(vertical = AppDimens.Space8),
                    content = content,
                )
            }
        }
    } else {
        DropdownMenuGroup(
            shapes = MenuDefaults.groupShape(index = 0, count = 1),
            modifier = modifier.verticalScroll(rememberScrollState()),
            content = content,
        )
    }
}

data class AppSelectionOption(
    val label: String,
    val testTag: String? = null,
)

@Composable
fun AppSelectionDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    options: List<AppSelectionOption>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    renderInRootScaffold: Boolean = true,
) {
    if (!rememberNavEntryOnTop()) return
    if (settingSkinIsMiuix()) {
        MiuixPrimitives.selectionDropdown(options.map { it.label }, selectedIndex, null, true, expanded, onDismissRequest, onSelect, renderInRootScaffold)
    } else {
        AppDropdownMenu(expanded, onDismissRequest, modifier, offset, renderInRootScaffold) {
            options.forEachIndexed { index, option ->
                AppDropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = { onSelect(index) },
                    modifier = (option.testTag?.let { Modifier.testTag(it) } ?: Modifier)
                        .semantics {
                            selected = index == selectedIndex
                            role = Role.RadioButton
                        },
                    trailingIcon = if (index == selectedIndex) {
                        { AppIcon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    if (settingSkinIsMiuix()) {
        MiuixPrimitives.dropdownMenuItem(text, onClick, modifier, leadingIcon, trailingIcon, enabled)
    } else {
        DropdownMenuItem(
            onClick = onClick,
            text = text,
            shape = MenuDefaults.standaloneItemShape,
            modifier = modifier,
            leadingIcon = leadingIcon,
            trailingContent = trailingIcon,
            enabled = enabled,
        )
    }
}

/** Material elevation 无公开数值；跨皮肤阴影须用 [shadowElevation]。 */
@Composable
fun AppFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    containerColor: Color? = null,
    contentColor: Color? = null,
    elevation: FloatingActionButtonElevation? = null,
    shadowElevation: Dp? = null,
    content: @Composable () -> Unit,
) {
    require(elevation == null || shadowElevation == null) { "Specify either Material elevation or shared shadowElevation, not both" }
    if (settingSkinIsMiuix()) {
        require(elevation == null) { "Material FAB elevation is not supported by Miuix; use shadowElevation" }
        MiuixPrimitives.floatingActionButton(onClick, modifier, shape, containerColor, contentColor, shadowElevation, content)
        return
    }
    val container = containerColor ?: FloatingActionButtonDefaults.containerColor
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = shape ?: FloatingActionButtonDefaults.shape,
        containerColor = container,
        contentColor = contentColor ?: contentColorFor(container),
        elevation = elevation ?: shadowElevation?.let { FloatingActionButtonDefaults.elevation(it, it, it, it) }
            ?: FloatingActionButtonDefaults.elevation(),
        content = content,
    )
}

@Composable
fun AppFilledTonalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    content: @Composable () -> Unit,
) {
    if (settingSkinIsMiuix()) {
        MiuixPrimitives.filledTonalIconButton(onClick, modifier, enabled, colors, content)
        return
    }
    FilledTonalIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppIconToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconToggleButtonColors = IconButtonDefaults.iconToggleButtonColors(),
    content: @Composable () -> Unit,
) {
    if (settingSkinIsMiuix()) {
        MiuixPrimitives.iconToggleButton(checked, onCheckedChange, modifier, enabled, colors, content)
        return
    }
    IconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = IconButtonDefaults.toggleableShapes(),
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = Color.Unspecified,
) {
    if (settingSkinIsMiuix()) {
        MiuixPrimitives.textButton(text, onClick, modifier, enabled, contentColor)
        return
    }
    TextButton(
        onClick = onClick,
        shapes = M3ButtonDefaults.shapes(),
        modifier = modifier,
        enabled = enabled,
        colors = M3ButtonDefaults.textButtonColors(contentColor = contentColor),
    ) {
        Text(text = text)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = Color.Unspecified,
    contentPadding: PaddingValues? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (settingSkinIsMiuix()) {
        MiuixPrimitives.textButton(onClick, modifier, enabled, contentColor, contentPadding, content)
        return
    }
    TextButton(
        onClick = onClick,
        shapes = M3ButtonDefaults.shapes(),
        modifier = modifier,
        enabled = enabled,
        contentPadding = contentPadding ?: M3ButtonDefaults.TextButtonContentPadding,
        colors = if (contentColor == Color.Unspecified) {
            M3ButtonDefaults.textButtonColors()
        } else {
            M3ButtonDefaults.textButtonColors(contentColor = contentColor)
        },
        content = content,
    )
}

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (settingSkinIsMiuix()) {
        MiuixPrimitives.button(text, onClick, modifier, enabled)
        return
    }
    AppButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) {
        Text(text)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color? = null,
    contentColor: Color? = null,
    shape: Shape? = null,
    contentPadding: PaddingValues? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (settingSkinIsMiuix()) {
        MiuixPrimitives.button(onClick, modifier, enabled, containerColor, contentColor, shape, contentPadding, content)
        return
    }
    val colors = if (containerColor == null && contentColor == null) {
        M3ButtonDefaults.buttonColors()
    } else {
        val container = containerColor ?: MaterialTheme.colorScheme.primary
        M3ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = contentColor ?: contentColorFor(container),
        )
    }
    if (shape == null) {
        Button(
            onClick = onClick,
            shapes = M3ButtonDefaults.shapes(),
            modifier = modifier,
            enabled = enabled,
            contentPadding = contentPadding ?: M3ButtonDefaults.ContentPadding,
            colors = colors,
            content = content,
        )
    } else {
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            contentPadding = contentPadding ?: M3ButtonDefaults.ContentPadding,
            colors = colors,
            content = content,
        )
    }
}

@Composable
fun AppOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (settingSkinIsMiuix()) {
        MiuixPrimitives.outlinedButton(text, onClick, modifier, enabled)
        return
    }
    AppOutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) {
        Text(text)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color? = null,
    contentColor: Color? = null,
    shape: Shape? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (settingSkinIsMiuix()) {
        MiuixPrimitives.outlinedButton(onClick, modifier, enabled, containerColor, contentColor, shape, content)
        return
    }
    val colors = if (containerColor == null && contentColor == null) {
        M3ButtonDefaults.outlinedButtonColors()
    } else {
        M3ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor ?: Color.Transparent,
            contentColor = contentColor ?: MaterialTheme.colorScheme.primary,
        )
    }
    if (shape == null) {
        OutlinedButton(
            onClick = onClick,
            shapes = M3ButtonDefaults.shapes(),
            modifier = modifier,
            enabled = enabled,
            colors = colors,
            content = content,
        )
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            colors = colors,
            content = content,
        )
    }
}

@Composable
fun AppChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (settingSkinIsMiuix()) {
        MiuixPrimitives.chip(label, selected, onClick, modifier, enabled)
        return
    }
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        label = { Text(label) },
    )
}

@Composable
fun AppChip(
    label: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    if (settingSkinIsMiuix()) {
        MiuixPrimitives.chip(label, selected, onClick, modifier, enabled, leadingIcon, trailingIcon)
        return
    }
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        label = label,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
    )
}

@Composable
fun AppCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (settingSkinIsMiuix()) {
        MiuixPrimitives.checkbox(checked, onCheckedChange, modifier, enabled)
        return
    }
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
fun AppRadioButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (settingSkinIsMiuix()) {
        MiuixPrimitives.radioButton(selected, onClick, modifier, enabled)
        return
    }
    RadioButton(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
fun AppSlider(
    state: SliderState,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    if (settingSkinIsMiuix()) {
        MiuixPrimitives.slider(state, onValueChange, modifier, enabled, onValueChangeFinished)
        return
    }
    Slider(
        state = state,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
    )
}

/** 下拉刷新可见时，分页尾部不重复显示加载指示。 */
val LocalPullToRefreshIndicatorVisible = compositionLocalOf { false }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    if (settingSkinIsMiuix()) {
        MiuixPrimitives.pullToRefresh(isRefreshing, onRefresh, modifier, contentPadding, content)
        return
    }
    val state = rememberPullToRefreshState()
    val haptic = LocalHapticFeedback.current
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onRefresh()
        },
        modifier = modifier,
        state = state,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter).padding(contentPadding),
            )
        },
    ) {
        CompositionLocalProvider(LocalPullToRefreshIndicatorVisible provides (isRefreshing || state.distanceFraction > 0f)) {
            content()
        }
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape? = null,
    containerColor: Color? = null,
    contentColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (settingSkinIsMiuix()) {
        MiuixPrimitives.card(modifier, onClick, shape, containerColor, contentColor, content)
        return
    }
    val container = containerColor ?: MaterialTheme.colorScheme.surfaceContainerLow
    val colors = CardDefaults.cardColors(containerColor = container, contentColor = contentColor ?: contentColorFor(container))
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape ?: CardDefaults.shape,
            colors = colors,
        ) {
            content()
        }
    } else {
        Card(
            modifier = modifier,
            shape = shape ?: CardDefaults.shape,
            colors = colors,
        ) {
            content()
        }
    }
}

/** size 为 null 时使用各皮肤默认尺寸。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppSpinner(
    modifier: Modifier = Modifier,
    size: Dp? = AppDimens.SpinnerSize,
    color: Color? = null,
) {
    if (settingSkinIsMiuix()) {
        MiuixPrimitives.spinner(modifier, size, color)
    } else {
        LoadingIndicator(modifier = modifier.size(size ?: AppDimens.SpinnerSize), color = color ?: LoadingIndicatorDefaults.indicatorColor)
    }
}

@Composable
fun AppLinearProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color? = null,
    trackColor: Color? = null,
) {
    if (settingSkinIsMiuix()) {
        MiuixPrimitives.linearProgress(progress, modifier, color, trackColor)
    } else if (color == null && trackColor == null) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = modifier,
        )
    } else {
        LinearProgressIndicator(
            progress = { progress },
            modifier = modifier,
            color = color ?: MaterialTheme.colorScheme.primary,
            trackColor = trackColor ?: MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
fun AppLinearProgressIndeterminate(
    modifier: Modifier = Modifier,
) {
    if (settingSkinIsMiuix()) {
        MiuixPrimitives.linearProgress(null, modifier, null, null)
    } else {
        LinearProgressIndicator(modifier = modifier)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    title: String? = null,
    modifier: Modifier = Modifier,
    summary: String? = null,
    confirmEnabled: Boolean = true,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
    destructive: Boolean = false,
) {
    if (!rememberNavEntryOnTop()) return
    if (!settingSkinIsMiuix()) {
        RegisterOverlayBlur()
    }
    val dismissAction = onDismiss ?: onDismissRequest
    if (settingSkinIsMiuix()) {
        MiuixPrimitives.dialog(
            onDismissRequest,
            confirmText,
            onConfirm,
            title,
            modifier,
            summary,
            confirmEnabled,
            dismissText,
            dismissAction,
            destructive,
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        title = if (title == null) {
            null
        } else {
            @Composable { Text(title) }
        },
        text = if (summary == null) {
            null
        } else {
            @Composable { Text(summary, modifier = Modifier.verticalScroll(rememberScrollState())) }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                shapes = M3ButtonDefaults.shapes(),
                enabled = confirmEnabled,
                colors = if (destructive) {
                    M3ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    M3ButtonDefaults.textButtonColors()
                },
            ) {
                Text(confirmText)
            }
        },
        dismissButton = if (dismissText == null) {
            null
        } else {
            @Composable {
                TextButton(onClick = dismissAction, shapes = M3ButtonDefaults.shapes()) {
                    Text(dismissText)
                }
            }
        },
    )
}

@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    if (!rememberNavEntryOnTop()) return
    if (!settingSkinIsMiuix()) {
        RegisterOverlayBlur()
    }
    if (settingSkinIsMiuix()) {
        MiuixPrimitives.alertDialog(
            onDismissRequest,
            modifier,
            title,
            text,
            confirmButton,
            dismissButton,
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        title = title,
        text = text?.let { body ->
            {
                Column(Modifier.verticalScroll(rememberScrollState())) { body() }
            }
        },
        confirmButton = confirmButton,
        dismissButton = dismissButton,
    )
}

/** 使用窗口生命周期区分页面覆盖与窗口失焦。 */
val LocalNavHostLifecycleOwner = compositionLocalOf<LifecycleOwner?> { null }

/** 窗口活跃时按条目 RESUMED 状态显示；失焦时保留浮层，避免中断选图和权限回调。 */
@Composable
fun rememberNavEntryOnTop(): Boolean {
    val host = LocalNavHostLifecycleOwner.current?.lifecycle ?: return true
    val entry = LocalLifecycleOwner.current.lifecycle
    val onTop = remember(entry, host) {
        mutableStateOf(host.currentState != Lifecycle.State.RESUMED || entry.currentState == Lifecycle.State.RESUMED)
    }
    LaunchedEffect(entry, host) {
        merge(entry.currentStateFlow, host.currentStateFlow).collect {
            if (host.currentState == Lifecycle.State.RESUMED) {
                onTop.value = entry.currentState == Lifecycle.State.RESUMED
            }
        }
    }
    return onTop.value
}

@Composable
fun AppTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    labelText: String = "",
    enabled: Boolean = true,
    readOnly: Boolean = false,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.SingleLine,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    inputTransformation: InputTransformation? = null,
    outputTransformation: OutputTransformation? = null,
    shape: Shape? = null,
    miuixCornerRadius: Dp? = null,
    miuixInsideMargin: DpSize? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    if (settingSkinIsMiuix()) {
        MiuixPrimitives.textField(
            state,
            modifier,
            placeholder,
            labelText,
            enabled,
            readOnly,
            lineLimits,
            keyboardOptions,
            onKeyboardAction,
            inputTransformation,
            outputTransformation,
            miuixCornerRadius,
            miuixInsideMargin,
            leadingIcon,
            trailingIcon,
        )
        return
    }
    OutlinedTextField(
        state = state,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        lineLimits = lineLimits,
        label = if (labelText.isEmpty()) null else ({ Text(labelText) }),
        placeholder = if (placeholder.isEmpty()) null else ({ Text(placeholder) }),
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        inputTransformation = inputTransformation,
        outputTransformation = outputTransformation,
        labelPosition = TextFieldLabelPosition.Inside(),
        shape = shape ?: OutlinedTextFieldDefaults.roundedShape,
        colors = OutlinedTextFieldDefaults.tonalColors(),
    )
}

/** 安全输入保持同一编辑状态；由原生安全文本框禁止复制和剪切，显隐不重建输入。 */
@Composable
fun AppSecureTextField(
    state: TextFieldState,
    labelText: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    textObfuscationMode: TextObfuscationMode = TextObfuscationMode.Hidden,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    if (settingSkinIsMiuix()) {
        MiuixPrimitives.secureTextField(state, labelText, modifier, placeholder, enabled, textObfuscationMode, trailingIcon)
        return
    }
    OutlinedSecureTextField(
        state = state,
        modifier = modifier,
        enabled = enabled,
        label = { Text(labelText) },
        placeholder = if (placeholder.isEmpty()) null else ({ Text(placeholder) }),
        trailingIcon = trailingIcon,
        textObfuscationMode = textObfuscationMode,
        labelPosition = TextFieldLabelPosition.Inside(),
        shape = OutlinedTextFieldDefaults.roundedShape,
        colors = OutlinedTextFieldDefaults.tonalColors(),
    )
}

@Composable
fun AppSearchField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    inputTransformation: InputTransformation? = null,
    outputTransformation: OutputTransformation? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    if (settingSkinIsMiuix()) {
        MiuixPrimitives.searchField(
            state,
            modifier,
            placeholder,
            keyboardOptions,
            onKeyboardAction,
            inputTransformation,
            outputTransformation,
            leadingIcon,
            trailingIcon,
        )
        return
    }
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    val colors = TextFieldDefaults.colors(
        focusedContainerColor = container,
        unfocusedContainerColor = container,
        disabledContainerColor = container,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
    )
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val textStyle = MaterialTheme.typography.bodyLarge
    val textMeasurer = rememberTextMeasurer()
    val textHeight = with(LocalDensity.current) {
        textMeasurer
            .measure(state.text.toString().ifEmpty { " " }, textStyle, softWrap = false, maxLines = 1)
            .size.height
            .toDp()
    }
    val decorator = TextFieldDefaults.decorator(
        state = state,
        enabled = true,
        lineLimits = TextFieldLineLimits.SingleLine,
        outputTransformation = outputTransformation,
        interactionSource = interactionSource,
        placeholder = if (placeholder.isEmpty()) null else ({ Text(placeholder, style = textStyle) }),
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        colors = colors,
        container = {
            TextFieldDefaults.Container(
                enabled = true,
                isError = false,
                interactionSource = interactionSource,
                shape = CircleShape,
                colors = colors,
            )
        },
    )
    CompositionLocalProvider(LocalTextSelectionColors provides colors.textSelectionColors) {
        BasicTextField(
            state = state,
            modifier = modifier.defaultMinSize(minWidth = TextFieldDefaults.MinWidth, minHeight = TextFieldDefaults.MinHeight),
            lineLimits = TextFieldLineLimits.SingleLine,
            textStyle = textStyle.copy(color = textStyle.color.takeOrElse { if (focused) colors.focusedTextColor else colors.unfocusedTextColor }),
            keyboardOptions = keyboardOptions,
            onKeyboardAction = onKeyboardAction,
            inputTransformation = inputTransformation,
            outputTransformation = outputTransformation,
            cursorBrush = SolidColor(colors.cursorColor),
            interactionSource = interactionSource,
            decorator = { innerTextField ->
                with(decorator) {
                    Decoration {
                        /** Skia 单行字形需要完整的最小高度，不能仅增高外层顶栏。 */
                        Box(Modifier.heightIn(min = textHeight), propagateMinConstraints = true) { innerTextField() }
                    }
                }
            },
        )
    }
}
