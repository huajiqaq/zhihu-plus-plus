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

package com.github.zly2006.zhihu.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppFloatingActionButton
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.DraggableFabColumn
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import com.github.zly2006.zhihu.viewmodel.feed.BaseFeedViewModel
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

@Composable
internal fun BoxScope.HomeScreenFloatingActions(
    createFabVisible: Boolean,
    utilityFabsVisible: Boolean,
    fabGraphicsLayer: GraphicsLayerScope.() -> Unit,
    innerPadding: PaddingValues,
    readingPlayerOverlayPadding: Dp,
    onCreateClick: () -> Unit,
    onCreateFabPositioned: (LayoutCoordinates) -> Unit,
    onUtilityFabPositioned: (LayoutCoordinates) -> Unit,
    utilityBottomAvoidance: Dp,
    isDebuggable: Boolean,
    onDebugClick: () -> Unit,
    onRefreshClick: () -> Unit,
    viewModel: BaseFeedViewModel,
) {
    if (createFabVisible) {
        AppFloatingActionButton(
            onClick = onCreateClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .graphicsLayer(fabGraphicsLayer)
                .padding(
                    end = AppDimens.Space16,
                    bottom = innerPadding.calculateBottomPadding() +
                        readingPlayerOverlayPadding +
                        AppDimens.Space8,
                ).onGloballyPositioned(onCreateFabPositioned)
                .testTag(HOME_CREATE_FAB_TAG),
            containerColor = if (settingSkinIsMiuix()) null else FloatingActionButtonDefaults.containerColor,
        ) {
            Icon(Icons.Default.Add, contentDescription = "创作")
        }
    }
    if (utilityFabsVisible) {
        DraggableFabColumn(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = AppDimens.Space16)
                .graphicsLayer(fabGraphicsLayer)
                .onGloballyPositioned(onUtilityFabPositioned),
            preferenceName = "homeUtilityFabs",
            /** 皮肤 FAB 尺寸不同，避让需使用实测 root bounds（Miuix 为 60dp）。 */
            bottomAvoidance = utilityBottomAvoidance,
        ) {
            if (isDebuggable) {
                AppFloatingActionButton(
                    onClick = onDebugClick,
                    containerColor = FloatingActionButtonDefaults.containerColor,
                ) {
                    Icon(Icons.Default.CopyAll, contentDescription = "复制")
                }
            }
            AppFloatingActionButton(
                onClick = onRefreshClick,
                modifier = Modifier.testTag(HOME_REFRESH_BUTTON_TAG),
                containerColor = FloatingActionButtonDefaults.containerColor,
            ) {
                if (viewModel.isLoading && !viewModel.isPullToRefresh) {
                    AppSpinner(modifier = Modifier.testTag("home_refresh_loading"), size = if (settingSkinIsMiuix()) null else AppDimens.IconSizeLarge)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
        }
    }
}
