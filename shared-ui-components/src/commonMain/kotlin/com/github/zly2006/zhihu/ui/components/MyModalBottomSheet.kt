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

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.github.zly2006.zhihu.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue.Hidden
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.internal.Strings
import androidx.compose.material3.internal.getString
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.platform.exportTestTagsForUiAutomation
import com.github.zly2006.zhihu.platform.platformName
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import kotlinx.coroutines.launch
import androidx.compose.material3.BottomSheet as MaterialBottomSheet
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

const val DISABLE_BOTTOM_SHEET_ROUNDED_CORNERS_PREFERENCE_KEY = "disableBottomSheetRoundedCorners"

/** 正文评论必须留在页面组合树内，根评论与回复才能共用返回链和保存状态。 */
@Composable
@ExperimentalMaterial3Api
fun MyModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    sheetGesturesEnabled: Boolean = true,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    usePlatformWindow: Boolean = true,
    onClose: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!rememberNavEntryOnTop()) return
    RegisterOverlayBlur()
    val miuix = settingSkinIsMiuix()
    val settings = rememberSettingsStore()
    val resolvedShape = if (settings.getBoolean(DISABLE_BOTTOM_SHEET_ROUNDED_CORNERS_PREFERENCE_KEY, false)) {
        RectangleShape
    } else if (miuix) {
        RoundedCornerShape(topStart = AppDimens.SheetCornerRadius, topEnd = AppDimens.SheetCornerRadius)
    } else {
        shape
    }
    val resolvedContainerColor = if (miuix) LocalAppSkinTokens.current.surface else containerColor
    val resolvedContentColor = if (miuix) LocalAppSkinTokens.current.onSurface else contentColor
    val resolvedDragHandle: @Composable (() -> Unit)? = when {
        !miuix -> dragHandle
        dragHandle == null -> null
        else -> {
            { MiuixSheetDragHandle(onClose) }
        }
    }
    val sheet: @Composable () -> Unit = {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier.exportTestTagsForUiAutomation(),
            sheetState = sheetState,
            sheetMaxWidth = sheetMaxWidth,
            sheetGesturesEnabled = sheetGesturesEnabled,
            shape = resolvedShape,
            containerColor = resolvedContainerColor,
            contentColor = resolvedContentColor,
            tonalElevation = tonalElevation,
            scrimColor = scrimColor,
            dragHandle = resolvedDragHandle,
            contentWindowInsets = contentWindowInsets,
            properties = properties,
            content = content,
        )
    }

    if (!usePlatformWindow) {
        val scope = rememberCoroutineScope()
        val dismiss: () -> Unit = {
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                if (!sheetState.isVisible) onDismissRequest()
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .semantics { isTraversalGroup = true }
                .exportTestTagsForUiAutomation(),
        ) {
            MyBottomSheetScrim(
                color = scrimColor,
                onDismissRequest = dismiss,
                visible = sheetState.targetValue != Hidden,
                dismissEnabled = properties.shouldDismissOnClickOutside,
            )
            val topInset = runCatching { sheetState.requireOffset().toInt().coerceAtLeast(0) }.getOrDefault(0)
            MaterialBottomSheet(
                modifier = modifier
                    .align(Alignment.TopCenter)
                    .consumeWindowInsets(WindowInsets(top = topInset)),
                state = sheetState,
                onDismissRequest = onDismissRequest,
                maxWidth = sheetMaxWidth,
                gesturesEnabled = sheetGesturesEnabled,
                backHandlerEnabled = properties.shouldDismissOnBackPress,
                shape = resolvedShape,
                containerColor = resolvedContainerColor,
                contentColor = resolvedContentColor,
                tonalElevation = tonalElevation,
                dragHandle = dragHandle,
                contentWindowInsets = contentWindowInsets,
                content = content,
            )
        }
        if (sheetState.hasExpandedState) {
            LaunchedEffect(sheetState) { sheetState.show() }
        }
        return
    }

    if (platformName == "macOS") {
        MacosModalBottomSheetDialog(
            properties = properties,
            onDismissRequest = onDismissRequest,
            content = sheet,
        )
    } else {
        sheet()
    }
}

@Composable
private fun MiuixSheetDragHandle(onClose: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.DialogActionSpacing),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(AppDimens.ControlHeightCompact)
                .height(AppDimens.Space4)
                .background(
                    color = LocalAppSkinTokens.current.onSurfaceVariantActions.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(AppDimens.Space2),
                ),
        )
        if (onClose != null) {
            AppIconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "关闭")
            }
        }
    }
}

@Composable
@ExperimentalMaterial3Api
internal expect fun MacosModalBottomSheetDialog(
    onDismissRequest: () -> Unit,
    properties: ModalBottomSheetProperties,
    content: @Composable () -> Unit,
)

@Composable
private fun MyBottomSheetScrim(
    color: Color,
    onDismissRequest: () -> Unit,
    visible: Boolean,
    dismissEnabled: Boolean,
) {
    if (color.isSpecified) {
        val alpha by
            animateFloatAsState(
                targetValue = if (visible) 1f else 0f,
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "ScrimAlphaAnimation",
            )
        val closeSheet = getString(Strings.CloseSheet)
        val dismissSheet =
            if (dismissEnabled) {
                Modifier
                    .pointerInput(onDismissRequest) { detectTapGestures { onDismissRequest() } }
                    .semantics(mergeDescendants = true) {
                        traversalIndex = 1f
                        contentDescription = closeSheet
                        onClick {
                            onDismissRequest()
                            true
                        }
                    }
            } else {
                Modifier
            }
        Canvas(Modifier.fillMaxSize().then(dismissSheet)) {
            drawRect(color = color, alpha = alpha.coerceIn(0f, 1f))
        }
    }
}
