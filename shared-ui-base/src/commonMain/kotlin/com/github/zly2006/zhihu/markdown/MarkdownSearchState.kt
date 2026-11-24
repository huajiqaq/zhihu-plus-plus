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

package com.github.zly2006.zhihu.markdown

import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.hrm.markdown.renderer.MarkdownTextSearchState
import com.hrm.markdown.renderer.rememberMarkdownTextSearchBringIntoViewSpec
import com.hrm.markdown.renderer.rememberMarkdownTextSearchState

interface MarkdownSearchState {
    val matchCount: Int

    fun setQuery(query: String)

    suspend fun reveal(index: Int): Boolean

    fun clear()

    @Composable
    fun rememberBringIntoViewSpec(obscuredTopPx: Float, obscuredBottomPx: Float, fallback: BringIntoViewSpec): BringIntoViewSpec
}

@Composable
fun rememberMarkdownSearchState(useTiqian: Boolean, contentKey: String): MarkdownSearchState = key(contentKey) {
    if (useTiqian && isTiqianMarkdownRendererAvailable) {
        rememberTiqianMarkdownSearchState()
    } else {
        val state = rememberMarkdownTextSearchState()
        remember(state) { ComposeMarkdownSearchState(state) }
    }
}

internal val MarkdownSearchMatchColor = Color(0xFFFFEB3B).copy(alpha = 0.35f)
internal val MarkdownSearchActiveMatchColor = Color(0xFFFFC107).copy(alpha = 0.7f)

internal class ComposeMarkdownSearchState(
    val rendererState: MarkdownTextSearchState,
) : MarkdownSearchState {
    init {
        rendererState.highlightAllMatches = true
        rendererState.allMatchHighlightColor = MarkdownSearchMatchColor
        rendererState.highlightColor = MarkdownSearchActiveMatchColor
    }

    override val matchCount: Int get() = rendererState.matches.size

    override fun setQuery(query: String) = rendererState.setQuery(query)

    override suspend fun reveal(index: Int): Boolean = rendererState.matches.getOrNull(index)?.let { rendererState.reveal(it) } ?: false

    override fun clear() = rendererState.clear()

    @Composable
    override fun rememberBringIntoViewSpec(obscuredTopPx: Float, obscuredBottomPx: Float, fallback: BringIntoViewSpec): BringIntoViewSpec =
        rememberMarkdownTextSearchBringIntoViewSpec(rendererState, obscuredTopPx, obscuredBottomPx, fallback)
}

@Composable
internal expect fun rememberTiqianMarkdownSearchState(): MarkdownSearchState
