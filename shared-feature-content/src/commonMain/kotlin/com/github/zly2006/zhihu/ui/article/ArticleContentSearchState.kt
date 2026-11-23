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

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.github.zly2006.zhihu.markdown.MarkdownSearchState

class ArticleContentSearchState(
    val renderer: MarkdownSearchState,
) {
    var visible by mutableStateOf(false)
        private set
    val query = TextFieldState()
    var current by mutableIntStateOf(0)
        private set
    var requestId by mutableIntStateOf(0)
        private set
    var locating by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set

    val matchCount: Int get() = renderer.matchCount

    fun hide() {
        visible = false
        requestId++
        renderer.clear()
        locating = false
        failed = false
    }

    fun show() {
        visible = true
        refreshQuery(query.text.toString())
    }

    internal suspend fun observeQuery() {
        snapshotFlow { query.text.toString() }.collect { if (visible) refreshQuery(it) }
    }

    private fun refreshQuery(text: String) {
        current = 0
        locating = false
        failed = false
        requestId++
        renderer.setQuery(text)
    }

    fun retry() {
        if (visible && matchCount > 0) requestId++
    }

    fun next() {
        if (matchCount > 0) {
            current = (current + 1) % matchCount
            requestId++
        }
    }

    fun previous() {
        if (matchCount > 0) {
            current = (current - 1 + matchCount) % matchCount
            requestId++
        }
    }

    suspend fun revealCurrent() {
        if (!visible || matchCount == 0) return
        val request = requestId
        locating = true
        failed = false
        try {
            val reached = renderer.reveal(current.coerceIn(0, matchCount - 1))
            if (request == requestId) failed = !reached
        } finally {
            if (request == requestId) locating = false
        }
    }
}
