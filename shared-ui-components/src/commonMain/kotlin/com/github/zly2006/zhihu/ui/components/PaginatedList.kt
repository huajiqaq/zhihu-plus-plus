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

import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import com.github.zly2006.zhihu.ui.components.AppSpinner
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.ui.rememberObservedSetting
import com.github.zly2006.zhihu.ui.subscreens.DEFAULT_SHOW_CONTENT_END_MARKER
import com.github.zly2006.zhihu.ui.subscreens.PREF_SHOW_CONTENT_END_MARKER

val ProgressIndicatorFooter: @Composable (LazyListState) -> Unit = { state ->
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (LocalPullToRefreshViewModel.current?.isPullToRefresh != true) {
            AppSpinner(size = null)
        }
    }
}

private const val DUPLICATE_KEY_PREFIX = "PaginatedListDuplicateKey"

internal fun <T> uniquePaginatedListKeys(
    items: List<T>,
    key: (T) -> Any,
): List<Any> {
    val originalKeys = items.map(key)
    val reservedKeys = originalKeys.toSet()
    val usedKeys = mutableSetOf<Any>()
    val duplicateOccurrences = mutableMapOf<Any, Int>()

    return originalKeys.map { original ->
        if (usedKeys.add(original)) {
            original
        } else {
            val occurrence = duplicateOccurrences.getOrElse(original) { 1 }
            duplicateOccurrences[original] = occurrence + 1
            uniqueDuplicatePaginatedListKey(original, occurrence, reservedKeys, usedKeys)
        }
    }
}

private fun uniqueDuplicatePaginatedListKey(
    original: Any,
    occurrence: Int,
    reservedKeys: Set<Any>,
    usedKeys: MutableSet<Any>,
): Any {
    var collision = 0
    while (true) {
        val candidate = "$DUPLICATE_KEY_PREFIX:$occurrence:$collision:$original"
        if (candidate !in reservedKeys && usedKeys.add(candidate)) {
            return candidate
        }
        collision++
    }
}

@Composable
private fun <T> LazyItemScope.PaginatedListItem(
    item: T,
    animateItemPlacement: Boolean,
    itemContent: @Composable LazyItemScope.(T) -> Unit,
) {
    Box(
        modifier = Modifier.animateItem(
            fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
            fadeOutSpec = spring(stiffness = Spring.StiffnessMediumLow),
            placementSpec = if (animateItemPlacement) spring(stiffness = Spring.StiffnessMediumLow) else null,
        ),
    ) {
        itemContent(item)
    }
}

@Composable
fun <T> PaginatedList(
    items: List<T>,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    listState: LazyListState = rememberLazyListState(),
    reverseLayout: Boolean = false,
    animateItemPlacement: Boolean = true,
    isEnd: () -> Boolean,
    footer: @Composable ((LazyListState) -> Unit)? = null,
    key: ((T) -> Any)? = null,
    topContent: LazyListScope.() -> Unit = {},
    bottomContent: LazyListScope.() -> Unit = {},
    isLoading: () -> Boolean,
    loadError: () -> String?,
    onRetry: () -> Unit,
    itemContent: @Composable LazyItemScope.(T) -> Unit,
) {
    val displayedItems = items.toList()
    val settings = rememberSettingsStore()
    val showContentEndMarker by rememberObservedSetting(settings, PREF_SHOW_CONTENT_END_MARKER) {
        getBoolean(PREF_SHOW_CONTENT_END_MARKER, DEFAULT_SHOW_CONTENT_END_MARKER)
    }
    val shouldLoadMore by remember(listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            if (visibleItemsInfo.isEmpty()) {
                false
            } else {
                val lastVisibleItem = visibleItemsInfo.last()
                lastVisibleItem.index >= layoutInfo.totalItemsCount - 3
            }
        }
    }

    val loadMore by rememberUpdatedState(onLoadMore)
    val ended = isEnd()
    val loading = isLoading()
    val error = loadError()
    LaunchedEffect(shouldLoadMore, displayedItems.size, ended, loading, error) {
        if (shouldLoadMore && displayedItems.isNotEmpty() && !ended && !loading && error == null) {
            loadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
    ) {
        topContent(this)

        if (key != null) {
            val itemKeys = uniquePaginatedListKeys(displayedItems, key)
            itemsIndexed(displayedItems, key = { index, _ -> itemKeys[index] }) { _, item ->
                PaginatedListItem(item, animateItemPlacement, itemContent)
            }
        } else {
            items(displayedItems) { item ->
                PaginatedListItem(item, animateItemPlacement, itemContent)
            }
        }

        bottomContent(this)

        item {
            when {
                error != null -> {
                    AppEmptyState(
                        title = "加载失败",
                        description = error,
                        action = { AppTextButton("重试", onRetry) },
                    )
                }
                loading -> (footer ?: ProgressIndicatorFooter).invoke(listState)
                ended && displayedItems.isEmpty() -> {
                    AppEmptyState(
                        title = "暂无内容",
                        description = "这里还没有可显示的内容",
                        modifier = Modifier.padding(top = AppDimens.Space24),
                    )
                }
                ended && !showContentEndMarker -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppDimens.Space16),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "已经到底啦",
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        if (ended && !loading && error == null && displayedItems.isNotEmpty() && showContentEndMarker) {
            pageTurnContentEndMarker()
        }
    }
}
