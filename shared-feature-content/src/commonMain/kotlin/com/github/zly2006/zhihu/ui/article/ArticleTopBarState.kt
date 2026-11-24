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

package com.github.zly2006.zhihu.ui.article

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest

@Stable
internal class ArticleTopBarState(
    initialHiddenFraction: Float = 0f,
) {
    val hiddenFraction = Animatable(initialHiddenFraction)
    var heightPx by mutableFloatStateOf(0f)
    internal var isSnapping by mutableStateOf(false)

    companion object {
        val Saver = Saver<ArticleTopBarState, Float>(save = { it.hiddenFraction.value }, restore = ::ArticleTopBarState)
    }
}

@Composable
internal fun rememberArticleTopBarState(
    scrollState: ScrollState,
    autoHide: Boolean,
): ArticleTopBarState {
    val state = rememberSaveable(saver = ArticleTopBarState.Saver) { ArticleTopBarState() }

    LaunchedEffect(autoHide) {
        if (!autoHide) state.hiddenFraction.snapTo(0f)
    }
    LaunchedEffect(scrollState, autoHide) {
        /** 恢复的正文位置是基线，不是一次新滚动。 */
        var previousScroll = scrollState.value
        snapshotFlow { scrollState.value }.collectLatest { currentScroll ->
            val delta = currentScroll - previousScroll
            previousScroll = currentScroll
            if (!state.isSnapping) {
                val height = state.heightPx
                if (currentScroll == 0) {
                    state.hiddenFraction.snapTo(0f)
                } else if (autoHide && height > 0f && delta != 0) {
                    val distanceFromBottom = (scrollState.maxValue - currentScroll).coerceAtLeast(0)
                    val hidden = state.hiddenFraction.value + delta / height
                    state.hiddenFraction.snapTo(hidden.coerceIn(0f, (distanceFromBottom / height).coerceAtMost(1f)))
                }
            }
        }
    }

    LaunchedEffect(scrollState, autoHide) {
        snapshotFlow { scrollState.isScrollInProgress }.collectLatest { isScrollInProgress ->
            if (isScrollInProgress || !autoHide || state.heightPx <= 0f) return@collectLatest
            val target = if (state.hiddenFraction.value > 0.5f) 1f else 0f
            if (target != state.hiddenFraction.value) {
                try {
                    state.isSnapping = true
                    state.hiddenFraction.animateTo(target, tween(150))
                } finally {
                    state.isSnapping = false
                }
            }
        }
    }

    return state
}
