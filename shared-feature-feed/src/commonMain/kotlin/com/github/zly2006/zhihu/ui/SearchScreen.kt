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

import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.officialBadge
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.navigation.Search
import com.github.zly2006.zhihu.navigation.Topic
import com.github.zly2006.zhihu.platform.UserMessageDuration
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.reading.RegisterReadingQueueSource
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppCard
import com.github.zly2006.zhihu.ui.components.AppChip
import com.github.zly2006.zhihu.ui.components.AppDialog
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppDropdownMenu
import com.github.zly2006.zhihu.ui.components.AppDropdownMenuItem
import com.github.zly2006.zhihu.ui.components.AppEmptyState
import com.github.zly2006.zhihu.ui.components.AppIconButton
import com.github.zly2006.zhihu.ui.components.AppOutlinedButton
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.AppSearchField
import com.github.zly2006.zhihu.ui.components.AppSectionTitle
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.AppTabRow
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.AppTopBar
import com.github.zly2006.zhihu.ui.components.AuthorBadge
import com.github.zly2006.zhihu.ui.components.DraggableRefreshButton
import com.github.zly2006.zhihu.ui.components.FeedAuthorBlockConfirmDialog
import com.github.zly2006.zhihu.ui.components.FeedAuthorBlockRequest
import com.github.zly2006.zhihu.ui.components.FeedAuthorBlockType
import com.github.zly2006.zhihu.ui.components.FeedCard
import com.github.zly2006.zhihu.ui.components.FeedPullToRefresh
import com.github.zly2006.zhihu.ui.components.MyModalBottomSheet
import com.github.zly2006.zhihu.ui.components.PaginatedList
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import com.github.zly2006.zhihu.util.parseEmphasizedHtmlTextWithTheme
import com.github.zly2006.zhihu.viewmodel.feed.SearchContentType
import com.github.zly2006.zhihu.viewmodel.feed.SearchEntity
import com.github.zly2006.zhihu.viewmodel.feed.SearchFilters
import com.github.zly2006.zhihu.viewmodel.feed.SearchSortOption
import com.github.zly2006.zhihu.viewmodel.feed.SearchSuggestItem
import com.github.zly2006.zhihu.viewmodel.feed.SearchTab
import com.github.zly2006.zhihu.viewmodel.feed.SearchTimeRange
import com.github.zly2006.zhihu.viewmodel.feed.SearchViewModel
import com.github.zly2006.zhihu.viewmodel.feed.ZHIHU_HOT_SEARCH_URL
import com.github.zly2006.zhihu.viewmodel.feed.fetchSearchSuggest
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import com.github.zly2006.zhihu.ui.components.AppIcon as Icon

@Serializable
private data class HotSearchItem(
    val query: String,
    val hotShow: String = "",
    val label: String = "",
)

const val SEARCH_SCREEN_BACK_BUTTON_TAG = "search_back_button"

private const val SEARCH_HISTORY_KEY = "searchHistoryQueries"
private const val SEARCH_HISTORY_MAX_SIZE = 20

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class, ExperimentalCoroutinesApi::class)
@Composable
fun SearchScreen(
    search: Search,
) {
    val navigator = LocalNavigator.current
    val userMessages = rememberUserMessageSink()
    val settings = rememberSettingsStore()
    val viewModel = viewModel { SearchViewModel(search.query, search.restrictedMemberHashId) }
    val readingQueueSourceId = buildString {
        append("search:")
        append(search.restrictedMemberHashId)
        append(':')
        append(viewModel.filters.sort.name)
        append(':')
        append(viewModel.filters.contentType.name)
        append(':')
        append(viewModel.searchTab.name)
        append(':')
        append(viewModel.filters.timeRange.name)
        append(':')
        append(search.query)
    }
    RegisterReadingQueueSource(
        sourceId = readingQueueSourceId,
        items = viewModel.displayItems,
    )
    val paginationEnvironment = rememberPaginationEnvironment(allowGuestAccess = false)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchInputFocusRequester = remember { FocusRequester() }
    var searchText by remember { mutableStateOf(search.query) }
    val coroutineScope = rememberCoroutineScope()
    val peopleListState = rememberLazyListState()
    val topicListState = rememberLazyListState()
    val generalListState = rememberLazyListState()
    val suggestionScrollState = rememberScrollState()
    val isMemberSearch = search.isRestrictedToMember
    val memberSearchName = search.restrictedMemberName.ifBlank { "TA" }
    val searchPlaceholder = if (isMemberSearch) "搜索 $memberSearchName 的创作" else "搜索内容"
    val shouldAutoFocusSearchInput = search.query.isBlank()

    val showHotSearch = remember { mutableStateOf(!isMemberSearch && settings.getBoolean("showSearchHotSearch", true)) }
    val hotSearchItems = remember { mutableStateListOf<HotSearchItem>() }
    var hotSearchMoreMenuExpanded by remember { mutableStateOf(false) }
    var historyMoreMenuExpanded by remember { mutableStateOf(false) }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }
    var filterDraft by remember { mutableStateOf<SearchFilters?>(null) }
    var feedAuthorBlockRequest by remember { mutableStateOf<FeedAuthorBlockRequest?>(null) }
    val showSearchHistory = remember { mutableStateOf(!isMemberSearch && settings.getBoolean("showSearchHistory", true)) }
    val searchHistoryItems = remember {
        mutableStateListOf<String>().apply {
            if (!isMemberSearch) {
                val history = settings.getStringOrNull(SEARCH_HISTORY_KEY)?.let { json ->
                    runCatching { ZhihuJson.json.decodeFromString<List<String>>(json) }.getOrNull()
                }.orEmpty()
                addAll(history)
            }
        }
    }

    fun submitSearch(query: String) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        if (showSearchHistory.value) {
            searchHistoryItems.remove(trimmedQuery)
            searchHistoryItems.add(0, trimmedQuery)
            while (searchHistoryItems.size > SEARCH_HISTORY_MAX_SIZE) {
                searchHistoryItems.removeAt(searchHistoryItems.lastIndex)
            }
            settings.putString(SEARCH_HISTORY_KEY, ZhihuJson.json.encodeToString(searchHistoryItems))
        }
        navigator.onNavigate(search.copy(query = trimmedQuery))
    }

    suspend fun fetchHotSearch() {
        val json = paginationEnvironment.fetchJson(ZHIHU_HOT_SEARCH_URL, "") ?: return
        val queries = json["hot_search_queries"] as? JsonArray ?: return
        hotSearchItems.clear()
        queries.take(15).forEach { item ->
            hotSearchItems.add(ZhihuJson.decodeJson(item))
        }
    }

    val suggestItems = remember { mutableStateListOf<SearchSuggestItem>() }

    LaunchedEffect(viewModel.searchQuery, isMemberSearch) {
        if (viewModel.searchQuery.isNotEmpty() || isMemberSearch) return@LaunchedEffect
        snapshotFlow { searchText }
            .debounce(300)
            .mapLatest { text ->
                if (text.isBlank()) emptyList() else fetchSearchSuggest(paginationEnvironment, text.trim())
            }.collectLatest { items ->
                suggestItems.clear()
                suggestItems.addAll(items)
            }
    }

    @Composable
    fun SearchHistoryHeader(showClearAction: Boolean) {
        AppSectionTitle(text = "搜索历史") {
            AppIconButton(
                onClick = { historyMoreMenuExpanded = true },
                modifier = Modifier
                    .size(AppDimens.TouchTarget)
                    .testTag("search_history_more_button"),
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = "更多", modifier = Modifier.size(18.dp))
                AppDropdownMenu(
                    expanded = historyMoreMenuExpanded,
                    onDismissRequest = { historyMoreMenuExpanded = false },
                ) {
                    if (showClearAction) {
                        AppDropdownMenuItem(
                            text = { Text("清空搜索历史") },
                            onClick = {
                                historyMoreMenuExpanded = false
                                showClearHistoryConfirm = true
                            },
                        )
                    }
                    AppDropdownMenuItem(
                        text = { Text("关闭搜索历史") },
                        onClick = {
                            historyMoreMenuExpanded = false
                            showSearchHistory.value = false
                            settings.putBoolean("showSearchHistory", false)
                            userMessages.showShortMessage("已关闭搜索历史，可在 导航与交互 › 搜索 中重新开启")
                        },
                    )
                }
            }
        }
        if (showClearHistoryConfirm) {
            AppDialog(
                title = "清空 ${searchHistoryItems.size} 条搜索历史？",
                summary = "清空后无法恢复。",
                confirmText = "清空",
                onConfirm = {
                    showClearHistoryConfirm = false
                    searchHistoryItems.clear()
                    settings.putString(SEARCH_HISTORY_KEY, ZhihuJson.json.encodeToString(searchHistoryItems))
                },
                dismissText = "取消",
                onDismissRequest = { showClearHistoryConfirm = false },
                destructive = true,
            )
        }
    }

    LaunchedEffect(search.query, showHotSearch.value, isMemberSearch) {
        if (search.query.isEmpty() && !isMemberSearch && showHotSearch.value) {
            runCatching { fetchHotSearch() }.onFailure { if (it is CancellationException) throw it }
        }
    }

    LaunchedEffect(search.query) {
        if (search.query.isNotEmpty() && viewModel.displayItems.isEmpty()) {
            viewModel.refresh(paginationEnvironment)
        }
    }

    LaunchedEffect(shouldAutoFocusSearchInput) {
        if (shouldAutoFocusSearchInput) {
            /** 导航切换先让出执行，再请求输入焦点。 */
            yield()
            searchInputFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(viewModel.errorMessage) {
        viewModel.errorMessage?.let {
            userMessages.showMessage(it, UserMessageDuration.Long)
        }
    }

    AppScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AppTopBar(
                title = {
                    AppSearchField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = AppDimens.TouchTarget)
                            .focusRequester(searchInputFocusRequester)
                            .testTag("search_input")
                            .semantics { contentDescription = "搜索内容" },
                        placeholder = searchPlaceholder,
                        leadingIcon = {
                            Box(
                                modifier = Modifier.size(AppDimens.TouchTarget),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "搜索",
                                    tint = LocalAppSkinTokens.current.onSurfaceVariantActions,
                                    modifier = Modifier.size(AppDimens.Space20),
                                )
                            }
                        },
                        trailingIcon = if (searchText.isNotEmpty()) {
                            {
                                AppIconButton(
                                    onClick = { searchText = "" },
                                    modifier = Modifier
                                        .size(AppDimens.TouchTarget)
                                        .testTag("search_clear_button"),
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "清除",
                                        tint = LocalAppSkinTokens.current.onSurfaceVariantActions,
                                        modifier = Modifier.size(AppDimens.Space20),
                                    )
                                }
                            }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                keyboardController?.hide()
                                submitSearch(searchText)
                            },
                        ),
                    )
                },
                onBack = navigator.onNavigateBack,
                backTag = SEARCH_SCREEN_BACK_BUTTON_TAG,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (search.query.isNotEmpty() && !isMemberSearch) {
                AppTabRow(
                    titles = SearchTab.entries.map { it.label },
                    selectedIndex = viewModel.searchTab.ordinal,
                    onSelect = { index ->
                        filterDraft = null
                        viewModel.selectTab(paginationEnvironment, SearchTab.entries[index])
                    },
                    testTags = SearchTab.entries.map { "search_tab_${it.name}" },
                )
            }
            if (viewModel.displayItems.isEmpty() && !viewModel.isLoading && viewModel.searchQuery.isEmpty()) {
                if (suggestItems.isNotEmpty()) {
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
                                icon = { Icon(Icons.Default.Search, contentDescription = null) },
                                endAction = {},
                                onClick = {
                                    keyboardController?.hide()
                                    submitSearch(item.query)
                                },
                                modifier = Modifier.testTag("search_suggest_${item.query}"),
                            )
                        }
                    }
                } else if (
                    showSearchHistory.value && searchHistoryItems.isNotEmpty() || showHotSearch.value && hotSearchItems.isNotEmpty()
                ) {
                    val shouldShowHistory = showSearchHistory.value && searchHistoryItems.isNotEmpty()
                    val shouldShowHotSearch = showHotSearch.value && hotSearchItems.isNotEmpty()
                    val pageTurnTarget = rememberPageTurnTarget(
                        scrollState = suggestionScrollState,
                        enabled = !historyMoreMenuExpanded && !hotSearchMoreMenuExpanded,
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pageTurnViewportWithGuide(pageTurnTarget)
                            .verticalScroll(suggestionScrollState)
                            .padding(16.dp)
                            .testTag("search_hot_list"),
                    ) {
                        if (shouldShowHistory) {
                            SearchHistoryHeader(showClearAction = true)
                            Spacer(modifier = Modifier.height(8.dp))
                            searchHistoryItems.forEach { query ->
                                SettingItem(
                                    title = { Text(query, style = LocalAppSkinTokens.current.main) },
                                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    onClick = {
                                        keyboardController?.hide()
                                        submitSearch(query)
                                    },
                                    endAction = {},
                                )
                            }
                            if (shouldShowHotSearch) {
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        if (shouldShowHotSearch) {
                            AppSectionTitle(text = "热搜") {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AppIconButton(
                                        onClick = {
                                            coroutineScope.launch { runCatching { fetchHotSearch() }.onFailure { if (it is CancellationException) throw it } }
                                        },
                                        modifier = Modifier
                                            .size(AppDimens.TouchTarget)
                                            .testTag("search_hot_refresh_button"),
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = "刷新热搜", modifier = Modifier.size(18.dp))
                                    }
                                    AppIconButton(
                                        onClick = { hotSearchMoreMenuExpanded = true },
                                        modifier = Modifier
                                            .size(AppDimens.TouchTarget)
                                            .testTag("search_hot_more_button"),
                                    ) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "更多", modifier = Modifier.size(18.dp))
                                        AppDropdownMenu(
                                            expanded = hotSearchMoreMenuExpanded,
                                            onDismissRequest = { hotSearchMoreMenuExpanded = false },
                                        ) {
                                            AppDropdownMenuItem(
                                                text = { Text("关闭热搜显示") },
                                                onClick = {
                                                    hotSearchMoreMenuExpanded = false
                                                    showHotSearch.value = false
                                                    settings.putBoolean("showSearchHotSearch", false)
                                                    userMessages.showShortMessage("已关闭热搜显示，可在 导航与交互 › 搜索 中重新开启")
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Column {
                                hotSearchItems.forEachIndexed { index, item ->
                                    SettingItem(
                                        title = { Text(item.query, style = LocalAppSkinTokens.current.main) },
                                        icon = {
                                            Text(
                                                "${index + 1}",
                                                style = LocalAppSkinTokens.current.footnote1,
                                                color = if (index < 3) LocalAppSkinTokens.current.primary else LocalAppSkinTokens.current.onSurfaceVariantSummary,
                                            )
                                        },
                                        onClick = {
                                            keyboardController?.hide()
                                            submitSearch(item.query)
                                        },
                                        endAction = {
                                            if (item.hotShow.isNotEmpty()) {
                                                Text(item.hotShow, style = LocalAppSkinTokens.current.footnote2, color = LocalAppSkinTokens.current.onSurfaceVariantSummary)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    if (showSearchHistory.value || isMemberSearch) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            if (showSearchHistory.value) {
                                SearchHistoryHeader(showClearAction = false)
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            Text(
                                text = if (isMemberSearch) {
                                    "输入关键词搜索 $memberSearchName 的创作"
                                } else {
                                    "暂无搜索历史，输入关键词搜索后会保存在这里"
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else {
                        Text(
                            text = "请输入搜索内容",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        )
                    }
                }
            } else if (viewModel.entities.isEmpty() && viewModel.errorMessage == null && !viewModel.isLoading && viewModel.isEnd) {
                val emptyContent: @Composable () -> Unit = {
                    AppEmptyState(
                        title = when (viewModel.searchTab) {
                            SearchTab.General -> "没有找到相关内容"
                            SearchTab.People -> "没有找到相关用户"
                            SearchTab.Topic -> "没有找到相关话题"
                        },
                        description = "试试其他关键词或调整筛选条件。",
                        modifier = Modifier.fillMaxSize().testTag("search_results_empty"),
                        action = {
                            AppTextButton("修改关键词", {
                                searchInputFocusRequester.requestFocus()
                                keyboardController?.show()
                            })
                        },
                    )
                }
                if (viewModel.searchTab == SearchTab.General) {
                    LazyColumn(state = generalListState, modifier = Modifier.fillMaxSize().testTag("search_general_results")) {
                        item(key = "search_filters") {
                            SearchFilterEntry(viewModel.filters) {
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                                filterDraft = viewModel.filters
                            }
                        }
                        item { emptyContent() }
                    }
                } else {
                    emptyContent()
                }
            } else if (viewModel.searchTab != SearchTab.General) {
                val resultListState = if (viewModel.searchTab == SearchTab.Topic) topicListState else peopleListState
                val pageTurnTarget = rememberPageTurnTarget(resultListState, enabled = true)
                FeedPullToRefresh(viewModel, paginationEnvironment) {
                    PaginatedList(
                        items = viewModel.entities,
                        listState = resultListState,
                        key = SearchEntity::id,
                        onLoadMore = { viewModel.loadMore(paginationEnvironment) },
                        isEnd = { viewModel.isEnd },
                        isLoading = { viewModel.isLoading },
                        loadError = { viewModel.errorMessage },
                        onRetry = { viewModel.retry(paginationEnvironment) },
                        contentPadding = androidx.compose.foundation.layout
                            .PaddingValues(bottom = LocalReadingPlayerOverlayPadding.current),
                        modifier = Modifier.fillMaxSize().pageTurnViewportWithGuide(pageTurnTarget),
                    ) { result ->
                        when (result) {
                            is SearchEntity.Topic -> {
                                val topic = result.topic
                                SettingItem(
                                    title = { Text(topic.name, style = LocalAppSkinTokens.current.title4) },
                                    description = {
                                        Column {
                                            result.excerpt.takeIf(String::isNotBlank)?.let {
                                                Text(it, style = LocalAppSkinTokens.current.body2, color = LocalAppSkinTokens.current.onSurfaceVariantSummary)
                                            }
                                            Text(
                                                "${formatTopicCount(result.visitCount.toString())} 浏览 · ${formatTopicCount(result.discussCount.toString())} 讨论",
                                                style = LocalAppSkinTokens.current.footnote2,
                                                color = LocalAppSkinTokens.current.onSurfaceVariantSummary,
                                            )
                                        }
                                    },
                                    icon = {
                                        AsyncImage(
                                            model = topic.avatarUrl,
                                            contentDescription = "${topic.name}的话题头像",
                                            modifier = Modifier.size(AppDimens.TouchTarget).clip(CircleShape),
                                        )
                                    },
                                    onClick = { navigator.onNavigate(Topic(topic.id, topic.name)) },
                                    endAction = {
                                        AppTextButton(
                                            text = if (result.isFollowing) "已关注" else "关注",
                                            onClick = {
                                                coroutineScope.launch {
                                                    viewModel
                                                        .setTopicFollowing(paginationEnvironment, topic.id, !result.isFollowing)
                                                        .onFailure { userMessages.showShortMessage("关注操作失败：${it.message}") }
                                                }
                                            },
                                            modifier = Modifier.testTag("search_topic_follow_${topic.id}"),
                                            enabled = topic.id !in viewModel.changingTopicIds,
                                        )
                                    },
                                    modifier = Modifier.testTag("search_topic_result_${topic.id}"),
                                )
                            }
                            is SearchEntity.Person -> {
                                val person = result.person
                                val plainName = person.name.replace("<em>", "").replace("</em>", "")
                                PersonSearchResultRow(person) {
                                    navigator.onNavigate(Person(person.id, person.urlToken.orEmpty(), plainName))
                                }
                            }
                            is SearchEntity.Content -> Unit
                        }
                    }
                }
            } else {
                val pageTurnTarget = rememberPageTurnTarget(
                    listState = generalListState,
                    enabled = filterDraft == null && feedAuthorBlockRequest == null,
                )
                FeedPullToRefresh(viewModel, paginationEnvironment) {
                    PaginatedList(
                        items = viewModel.entities,
                        listState = generalListState,
                        onLoadMore = { viewModel.loadMore(paginationEnvironment) },
                        modifier = Modifier
                            .pageTurnViewportWithGuide(pageTurnTarget)
                            .testTag("search_general_results"),
                        topContent = {
                            item(key = "search_filters") {
                                SearchFilterEntry(viewModel.filters) {
                                    focusManager.clearFocus(force = true)
                                    keyboardController?.hide()
                                    filterDraft = viewModel.filters
                                }
                                if (isMemberSearch) {
                                    Text(
                                        text = "以下结果来自 $memberSearchName 的创作",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space8),
                                    )
                                }
                            }
                        },
                        isEnd = { viewModel.isEnd },
                        isLoading = { viewModel.isLoading },
                        loadError = { viewModel.errorMessage },
                        onRetry = { viewModel.retry(paginationEnvironment) },
                        contentPadding = androidx.compose.foundation.layout
                            .PaddingValues(bottom = LocalReadingPlayerOverlayPadding.current),
                        key = SearchEntity::id,
                    ) { result ->
                        when (result) {
                            is SearchEntity.Content -> FeedCard(
                                item = result.item,
                                modifier = Modifier.testTag("search_general_content_${result.id}"),
                                readingQueueSourceId = readingQueueSourceId,
                                menuItems = { dismissMenu ->
                                    AppDropdownMenuItem(
                                        text = { Text("屏蔽用户") },
                                        onClick = {
                                            dismissMenu()
                                            viewModel.handleBlockUser(
                                                paginationEnvironment,
                                                userMessages,
                                                result.item,
                                            ) { authorInfo ->
                                                feedAuthorBlockRequest = FeedAuthorBlockRequest(
                                                    FeedAuthorBlockType.CONTENT_AUTHOR,
                                                    authorInfo.first,
                                                    authorInfo.second,
                                                )
                                            }
                                        },
                                    )
                                },
                            )
                            is SearchEntity.Person -> PersonSearchResultRow(result.person) {
                                val person = result.person
                                navigator.onNavigate(
                                    Person(
                                        person.id,
                                        person.urlToken.orEmpty(),
                                        person.name.replace("<em>", "").replace("</em>", ""),
                                    ),
                                )
                            }
                            is SearchEntity.Topic -> Unit
                        }
                    }

                    val showRefreshFab = remember { settings.getBoolean("showRefreshFab", true) }
                    if (showRefreshFab) {
                        DraggableRefreshButton(
                            preferenceName = "searchRefresh",
                            bottomAvoidance = LocalReadingPlayerOverlayPadding.current,
                            onClick = {
                                viewModel.refresh(paginationEnvironment)
                            },
                        ) {
                            if (viewModel.isLoading) {
                                AppSpinner(size = if (settingSkinIsMiuix()) null else AppDimens.IconSizeLarge)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "刷新")
                            }
                        }
                    }
                }
            }
        }
    }

    filterDraft?.let { draft ->
        SearchFilterMenu(
            filters = draft,
            onFiltersChange = { filterDraft = it },
            onDismissRequest = { filterDraft = null },
            onApply = {
                filterDraft = null
                if (draft != viewModel.filters) {
                    coroutineScope.launch { generalListState.scrollToItem(0) }
                    viewModel.updateFilters(paginationEnvironment, draft)
                }
            },
        )
    }

    FeedAuthorBlockConfirmDialog(
        request = feedAuthorBlockRequest,
        displayItems = viewModel.displayItems,
        onDismiss = { feedAuthorBlockRequest = null },
        onConfirm = {
            viewModel.refresh(paginationEnvironment)
            feedAuthorBlockRequest = null
        },
    )
}

@Composable
private fun PersonSearchResultRow(
    person: DataHolder.People,
    onClick: () -> Unit,
) {
    val skin = LocalAppSkinTokens.current
    val plainName = person.name.replace("<em>", "").replace("</em>", "")
    SettingItem(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = parseEmphasizedHtmlTextWithTheme(person.name),
                    style = skin.title4,
                    modifier = Modifier.weight(1f, fill = false),
                )
                val badge = person.badgeV2.officialBadge()
                if (badge?.isUsefulInList == true) {
                    Spacer(Modifier.width(AppDimens.Space4))
                    AuthorBadge(badge, compact = true)
                }
            }
        },
        description = {
            Column {
                person.headline.takeIf(String::isNotEmpty)?.let {
                    Text(it, style = skin.body2, color = skin.onSurfaceVariantSummary)
                }
                Text(
                    "${person.followerCount} 粉丝 · ${person.answerCount} 回答",
                    style = skin.footnote2,
                    color = skin.onSurfaceVariantSummary,
                )
            }
        },
        icon = {
            AsyncImage(
                model = person.avatarUrl,
                contentDescription = "${plainName}的头像",
                modifier = Modifier.size(AppDimens.TouchTarget).clip(CircleShape),
            )
        },
        onClick = onClick,
        endAction = {},
        modifier = Modifier.testTag("search_people_result_${person.id}"),
    )
}

@Composable
private fun SearchFilterEntry(filters: SearchFilters, onClick: () -> Unit) {
    val skin = LocalAppSkinTokens.current
    val activeCount = listOf(
        filters.sort != SearchSortOption.Default,
        filters.contentType != SearchContentType.All,
        filters.timeRange != SearchTimeRange.All,
    ).count { it }
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.PageHorizontalPadding, vertical = AppDimens.Space8)
            .testTag("search_filter_button"),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.Space12)
                .testTag("search_filter_context"),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.FilterList, contentDescription = null, tint = if (activeCount > 0) skin.primary else skin.onSurface)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppDimens.Space2)) {
                Text(if (activeCount > 0) "筛选 · $activeCount" else "筛选", style = skin.main)
                Text("${filters.sort.label} · ${filters.contentType.label} · ${filters.timeRange.label}", style = skin.footnote1, color = skin.onSurfaceVariantSummary)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = skin.onSurfaceVariantActions)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchFilterMenu(
    filters: SearchFilters,
    onFiltersChange: (SearchFilters) -> Unit,
    onDismissRequest: () -> Unit,
    onApply: () -> Unit,
) {
    val skin = LocalAppSkinTokens.current
    MyModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("search_filter_panel"),
        onClose = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.PageHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Space16),
        ) {
            Text("筛选搜索结果", style = skin.title3)
            SearchFilterGroup("排序") {
                SearchSortOption.entries.forEach { option ->
                    AppChip(option.label, filters.sort == option, { onFiltersChange(filters.copy(sort = option)) }, modifier = Modifier.heightIn(min = AppDimens.TouchTarget).testTag("search_filter_sort_${option.name}"))
                }
            }
            SearchFilterGroup("内容类型") {
                SearchContentType.entries.forEach { type ->
                    AppChip(type.label, filters.contentType == type, { onFiltersChange(filters.copy(contentType = type)) }, modifier = Modifier.heightIn(min = AppDimens.TouchTarget).testTag("search_filter_type_${type.name}"))
                }
            }
            SearchFilterGroup("时间范围") {
                SearchTimeRange.entries.forEach { range ->
                    AppChip(range.label, filters.timeRange == range, { onFiltersChange(filters.copy(timeRange = range)) }, modifier = Modifier.heightIn(min = AppDimens.TouchTarget).testTag("search_filter_time_${range.name}"))
                }
            }
            Spacer(Modifier.height(AppDimens.Space4))
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(AppDimens.PageHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Space8),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            AppTextButton("重置", { onFiltersChange(SearchFilters()) }, enabled = filters != SearchFilters(), modifier = Modifier.width(IntrinsicSize.Max).testTag("search_filter_reset"))
            AppOutlinedButton("取消", onDismissRequest, modifier = Modifier.width(IntrinsicSize.Max).testTag("search_filter_cancel"))
            AppButton("应用筛选", onApply, modifier = Modifier.width(IntrinsicSize.Max).testTag("search_filter_apply"))
        }
    }
}

@Composable
private fun SearchFilterGroup(title: String, content: @Composable FlowRowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Space8)) {
        Text(title, style = LocalAppSkinTokens.current.main)
        FlowRow(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Space8),
            content = content,
        )
    }
}
