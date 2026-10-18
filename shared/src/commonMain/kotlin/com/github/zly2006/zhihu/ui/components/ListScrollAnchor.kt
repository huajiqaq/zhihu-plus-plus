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

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 让带动态头部的列表在返回时保持滚动位置。
 *
 * `LazyListState` 只按 index + offset 恢复；如果头部高度还在动画、数据还没加载完，
 * 第一帧恢复的 offset 会被按错误的头部高度重新解释。这里等 [contentReady] 为 true
 * （头部高度已定型、列表已有数据）后再恢复一次，并且恢复完成前不记录锚点，
 * 避免返回瞬间的瞬态位置覆盖保存值。
 *
 * @param contentReady 头部几何已经稳定，可以按最终高度解释 offset。
 * @param itemCount 列表数据条数；为 0 时等待数据。
 * @param saveKey 区分不同页面的保存键。
 */
@Composable
fun rememberListScrollAnchor(
    listState: LazyListState,
    contentReady: Boolean,
    itemCount: Int,
    saveKey: Any? = null,
) {
    var anchorIndex by rememberSaveable(saveKey) { mutableIntStateOf(0) }
    var anchorOffset by rememberSaveable(saveKey) { mutableIntStateOf(0) }
    var restored by remember(saveKey) { mutableStateOf(false) }

    LaunchedEffect(listState, contentReady, restored) {
        if (!contentReady || !restored) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                anchorIndex = index
                anchorOffset = offset
            }
    }

    LaunchedEffect(contentReady, itemCount, restored) {
        if (!contentReady || itemCount <= 0 || restored) return@LaunchedEffect
        val totalItems = listState.layoutInfo.totalItemsCount
        val targetIndex = if (totalItems > 0) anchorIndex.coerceIn(0, totalItems - 1) else anchorIndex
        listState.scrollToItem(targetIndex, anchorOffset)
        restored = true
    }
}
