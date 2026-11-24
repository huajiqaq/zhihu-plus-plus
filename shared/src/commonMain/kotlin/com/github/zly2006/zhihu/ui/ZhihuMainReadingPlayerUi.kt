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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.reading.ReadingPlayerController
import com.github.zly2006.zhihu.reading.ReadingPlayerState
import com.github.zly2006.zhihu.reading.saveReadingPlaybackSpeed

/** 朗读播放器浮层跨重组保留的界面状态。 */
@Stable
internal class ZhihuMainReadingPlayerUiState(
    initialItemKey: String?,
) {
    var showQueue by mutableStateOf(false)
    var isExpandedByUser by mutableStateOf(false)
    var heightPx by mutableIntStateOf(0)
    val overlayOffsetState = ReadingPlayerOverlayOffsetState()
    var previousItemKey by mutableStateOf(initialItemKey)
}

@Composable
internal fun rememberZhihuMainReadingPlayerUiState(readingPlayerState: ReadingPlayerState): ZhihuMainReadingPlayerUiState =
    remember { ZhihuMainReadingPlayerUiState(readingPlayerState.currentItem?.key) }

/** 会话结束、队列切到另一项、换页时同步浮层状态。 */
@Composable
internal fun ZhihuMainReadingPlayerEffects(
    ui: ZhihuMainReadingPlayerUiState,
    readingPlayer: ReadingPlayerController,
    currentDestination: NavDestination?,
    isOnArticle: Boolean,
    onOpenReadingDestination: (NavDestination) -> Unit,
) {
    val readingPlayerState by readingPlayer.state
    LaunchedEffect(readingPlayerState.hasSession) {
        if (!readingPlayerState.hasSession) {
            ui.showQueue = false
            ui.isExpandedByUser = false
            ui.overlayOffsetState.resetOffset()
        }
    }
    val currentOpenReadingDestination by rememberUpdatedState(onOpenReadingDestination)
    LaunchedEffect(readingPlayerState.currentItem?.key) {
        val currentItem = readingPlayerState.currentItem
        val currentItemKey = currentItem?.key
        if (currentItem != null && ui.previousItemKey != null && ui.previousItemKey != currentItemKey) {
            /** 第一帧回调先于重组和约束测量；跨完该帧后再用最新导航回调，避免改宽与切队列同时发生时按旧窄屏清栈。 */
            withFrameNanos { }
            withFrameNanos { }
        }
        val itemChanged = ui.previousItemKey != null && ui.previousItemKey != currentItemKey
        ui.previousItemKey = currentItemKey
        if (itemChanged && currentItem != null) {
            currentOpenReadingDestination(currentItem.toDestination(readingPlayerState.sourceId))
        }
    }

    LaunchedEffect(currentDestination) {
        ui.isExpandedByUser = false
        if (!isOnArticle) ui.overlayOffsetState.clearRoute()
    }
    var wasOnArticle by remember { mutableStateOf(false) }
    if (!isOnArticle && wasOnArticle) {
        LeaveImmersiveModeCleanup()
    }
    SideEffect {
        wasOnArticle = isOnArticle
    }
}

/** 播放条和收起后的悬浮按钮；摆放位置由调用方的 [modifier] 决定。 */
@Composable
internal fun ZhihuMainReadingPlayerBar(
    modifier: Modifier,
    ui: ZhihuMainReadingPlayerUiState,
    readingPlayer: ReadingPlayerController,
    readingPlayerState: ReadingPlayerState,
    settings: SettingsStore,
    isExpanded: Boolean,
    isOnReadingDetail: Boolean,
) {
    ZhihuMainReadingPlayerOverlay(
        modifier = modifier,
        isReadingPlayerExpanded = isExpanded,
        readingPlayerState = readingPlayerState,
        onPrevious = readingPlayer::playPrevious,
        onTogglePlayPause = readingPlayer::togglePlayPause,
        onNext = readingPlayer::playNext,
        onStop = readingPlayer::stop,
        onOpenQueue = { ui.showQueue = true },
        onPlaybackSpeedChange = { speed ->
            saveReadingPlaybackSpeed(settings, speed)
            readingPlayer.setPlaybackSpeed(speed)
        },
        onBackgroundInteraction = {
            if (!isOnReadingDetail) ui.isExpandedByUser = false
        },
        onHeightChange = { ui.heightPx = it },
        readingPlayerOverlayOffsetState = ui.overlayOffsetState,
        onExpand = { ui.isExpandedByUser = true },
    )
}

/** 朗读队列面板；点某一项时切到它并打开对应内容。 */
@Composable
internal fun ZhihuMainReadingQueueSheet(
    ui: ZhihuMainReadingPlayerUiState,
    readingPlayer: ReadingPlayerController,
    readingPlayerState: ReadingPlayerState,
    isOnReadingDetail: Boolean,
    onOpenDestination: (NavDestination) -> Unit,
    onOpenSettings: () -> Unit,
) {
    ZhihuMainReadingQueue(
        visible = ui.showQueue,
        readingPlayerState = readingPlayerState,
        onDismissRequest = {
            ui.showQueue = false
            if (!isOnReadingDetail) ui.isExpandedByUser = false
        },
        onItemClick = { index, item ->
            ui.previousItemKey = item.key
            if (index != readingPlayer.state.value.currentIndex) {
                readingPlayer.playAt(index)
            }
            ui.showQueue = false
            onOpenDestination(item.toDestination(readingPlayer.state.value.sourceId))
        },
        onOpenSettings = {
            ui.showQueue = false
            ui.isExpandedByUser = false
            onOpenSettings()
        },
    )
}
