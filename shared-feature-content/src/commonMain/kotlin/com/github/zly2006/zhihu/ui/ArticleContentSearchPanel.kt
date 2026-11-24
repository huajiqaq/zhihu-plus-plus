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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.article.ArticleContentSearchState
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppSearchField
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

@Composable
internal fun ArticleContentSearchPanel(state: ArticleContentSearchState) {
    val skin = LocalAppSkinTokens.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.visible) {
        if (state.visible) focusRequester.requestFocus()
    }
    Box(
        modifier = Modifier
            .widthIn(max = AppDimens.ArticleReadingMaxWidth)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space8)
            .testTag("article_search_panel"),
    ) {
        Column(Modifier.padding(horizontal = AppDimens.Space12, vertical = AppDimens.Space8)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppSearchField(
                    state = state.query,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .testTag("article_search_field"),
                    placeholder = "搜索正文",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    onKeyboardAction = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        state.retry()
                    },
                )
                Text(
                    text = if (state.matchCount == 0) "0/0" else "${state.current + 1}/${state.matchCount}",
                    style = skin.footnote1,
                    color = skin.onSurfaceVariantSummary,
                    modifier = Modifier
                        .padding(horizontal = AppDimens.Space8)
                        .testTag("article_search_status"),
                )
                AppIconButton(
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        state.previous()
                    },
                    enabled = state.matchCount > 0,
                    modifier = Modifier.testTag("article_search_previous"),
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上一处", tint = skin.onSurfaceVariantActions)
                }
                AppIconButton(
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        state.next()
                    },
                    enabled = state.matchCount > 0,
                    modifier = Modifier.testTag("article_search_next"),
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下一处", tint = skin.onSurfaceVariantActions)
                }
                AppIconButton(
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        state.hide()
                    },
                    modifier = Modifier.testTag("article_search_close"),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "关闭搜索", tint = skin.onSurfaceVariantActions)
                }
            }
            val status = when {
                state.locating -> "正在定位…"
                state.failed -> "未能定位此处，请重试"
                state.matchCount == 0 && state.query.text.isNotBlank() -> "未找到匹配内容"
                else -> null
            }
            Text(
                text = status.orEmpty(),
                style = skin.footnote2,
                color = if (state.failed) skin.error else skin.onSurfaceVariantSummary,
                modifier = Modifier
                    .padding(top = AppDimens.Space4)
                    .clickable(enabled = state.failed, onClick = state::retry)
                    .testTag("article_search_location_status"),
            )
        }
    }
}
