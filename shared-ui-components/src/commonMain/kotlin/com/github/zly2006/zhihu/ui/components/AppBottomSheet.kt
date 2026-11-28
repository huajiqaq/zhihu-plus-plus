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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import com.github.zly2006.zhihu.i18n.WidgetsStrings
import com.github.zly2006.zhihu.platform.exportTestTagsForUiAutomation
import com.github.zly2006.zhihu.platform.platformName
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import kotlinx.coroutines.launch

const val DISABLE_BOTTOM_SHEET_ROUNDED_CORNERS_PREFERENCE_KEY = "disableBottomSheetRoundedCorners"

/** 页面被覆盖只卸载窗口，不触发关闭；需要跨隐藏保留的状态由调用方持有。 */
@Composable
@ExperimentalMaterial3Api
fun AppBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.PartiallyExpanded, SheetValue.Expanded),
    ),
    sheetGesturesEnabled: Boolean = true,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.modalWindowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    showMiuixCloseButton: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!rememberNavEntryOnTop()) return
    RegisterOverlayBlur()
    val scope = rememberCoroutineScope()
    val miuix = settingSkinIsMiuix()
    val settings = rememberSettingsStore()
    val shape = when {
        settings.getBoolean(DISABLE_BOTTOM_SHEET_ROUNDED_CORNERS_PREFERENCE_KEY, false) -> RectangleShape
        miuix -> RoundedCornerShape(topStart = AppDimens.SheetCornerRadius, topEnd = AppDimens.SheetCornerRadius)
        else -> BottomSheetDefaults.ExpandedShape
    }
    val resolvedContainerColor = if (miuix) LocalAppSkinTokens.current.surface else containerColor
    val contentColor = if (miuix) LocalAppSkinTokens.current.onSurface else contentColorFor(containerColor)

    /** Material 的窗口返回回调在 hide 被否决时也可能执行。 */
    fun dismissIfHidden() {
        if (!sheetState.isVisible) onDismissRequest()
    }

    fun hide() {
        scope.launch {
            sheetState.hide()
            dismissIfHidden()
        }
    }

    val sheet: @Composable () -> Unit = {
        ModalBottomSheet(
            onDismissRequest = ::dismissIfHidden,
            modifier = modifier.exportTestTagsForUiAutomation(),
            sheetState = sheetState,
            sheetGesturesEnabled = sheetGesturesEnabled,
            shape = shape,
            containerColor = resolvedContainerColor,
            contentColor = contentColor,
            dragHandle = {
                if (miuix) {
                    MiuixSheetDragHandle(if (showMiuixCloseButton) ::hide else null)
                } else {
                    BottomSheetDefaults.DragHandle()
                }
            },
            contentWindowInsets = contentWindowInsets,
            properties = properties,
            content = content,
        )
    }

    if (platformName == "macOS") {
        MacosModalBottomSheetDialog(
            properties = properties,
            onDismissRequest = ::hide,
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
                AppIcon(Icons.Filled.Close, contentDescription = WidgetsStrings.close)
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
