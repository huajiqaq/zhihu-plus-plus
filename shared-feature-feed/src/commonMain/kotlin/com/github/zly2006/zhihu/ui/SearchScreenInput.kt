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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.i18n.FeedStrings
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppIcon
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppSearchField
import com.github.zly2006.zhihu.ui.components.AppTabRow
import com.github.zly2006.zhihu.ui.components.AppTopBar
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.viewmodel.feed.SearchSuggestItem
import com.github.zly2006.zhihu.viewmodel.feed.SearchTab

@Composable
internal fun SearchScreenTopBar(
    searchText: TextFieldState,
    searchInputFocusRequester: FocusRequester,
    searchPlaceholder: String,
    onSearch: () -> Unit,
    onBack: () -> Unit,
) {
    AppTopBar(
        title = {
            AppSearchField(
                state = searchText,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AppDimens.TouchTarget)
                    .focusRequester(searchInputFocusRequester)
                    .testTag("search_input")
                    .semantics { contentDescription = FeedStrings.searchContent },
                placeholder = searchPlaceholder,
                leadingIcon = {
                    Box(
                        modifier = Modifier.size(AppDimens.TouchTarget),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(
                            Icons.Default.Search,
                            contentDescription = FeedStrings.search,
                            tint = LocalAppSkinTokens.current.onSurfaceVariantActions,
                            modifier = Modifier.size(AppDimens.Space20),
                        )
                    }
                },
                trailingIcon = if (searchText.text.isNotEmpty()) {
                    {
                        AppIconButton(
                            onClick = searchText::clearText,
                            modifier = Modifier
                                .size(AppDimens.TouchTarget)
                                .testTag("search_clear_button"),
                        ) {
                            AppIcon(
                                Icons.Default.Clear,
                                contentDescription = FeedStrings.clearInput,
                                tint = LocalAppSkinTokens.current.onSurfaceVariantActions,
                                modifier = Modifier.size(AppDimens.Space20),
                            )
                        }
                    }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                onKeyboardAction = { onSearch() },
            )
        },
        onBack = onBack,
        backTag = SEARCH_SCREEN_BACK_BUTTON_TAG,
    )
}

@Composable
internal fun SearchScreenTabs(
    searchTab: SearchTab,
    onSelect: (Int) -> Unit,
) {
    AppTabRow(
        titles = SearchTab.entries.map { it.label },
        selectedIndex = searchTab.ordinal,
        onSelect = onSelect,
        testTags = SearchTab.entries.map { "search_tab_${it.name}" },
    )
}

@Composable
internal fun SearchScreenSuggestions(
    suggestItems: List<SearchSuggestItem>,
    onSelect: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("search_suggest_list"),
    ) {
        /** 响应可能包含重复建议词，以索引区分 key。 */
        itemsIndexed(suggestItems, key = { index, item -> "$index:${item.query}" }) { _, item ->
            SettingItem(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.query, style = LocalAppSkinTokens.current.main, modifier = Modifier.weight(1f, fill = false))
                        if (item.iconUrl.isNotEmpty()) {
                            Spacer(Modifier.width(AppDimens.Space4))
                            AsyncImage(
                                model = item.iconUrl,
                                contentDescription = item.label,
                                modifier = Modifier.height(AppDimens.Space14),
                            )
                        }
                    }
                },
                icon = { AppIcon(Icons.Default.Search, contentDescription = null) },
                endAction = {},
                onClick = { onSelect(item.query) },
                modifier = Modifier.testTag("search_suggest_${item.query}"),
            )
        }
    }
}
