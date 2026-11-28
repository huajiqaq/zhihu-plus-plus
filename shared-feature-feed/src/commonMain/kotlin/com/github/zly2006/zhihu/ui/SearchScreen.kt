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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.decodeSearchText
import com.github.zly2006.zhihu.i18n.FeedStrings
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.navigation.Search
import com.github.zly2006.zhihu.navigation.Topic
import com.github.zly2006.zhihu.platform.UserMessageDuration
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.reading.RegisterReadingQueueSource
import com.github.zly2006.zhihu.ui.components.AppScaffold
import com.github.zly2006.zhihu.ui.components.FeedAuthorBlockConfirmDialog
import com.github.zly2006.zhihu.ui.components.FeedAuthorBlockRequest
import com.github.zly2006.zhihu.ui.components.FeedAuthorBlockType
import com.github.zly2006.zhihu.viewmodel.feed.SearchFilters
import com.github.zly2006.zhihu.viewmodel.feed.SearchSuggestItem
import com.github.zly2006.zhihu.viewmodel.feed.SearchTab
import com.github.zly2006.zhihu.viewmodel.feed.SearchViewModel
import com.github.zly2006.zhihu.viewmodel.feed.ZHIHU_HOT_SEARCH_URL
import com.github.zly2006.zhihu.viewmodel.feed.fetchSearchSuggest
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray

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
    val searchText = key(search) { rememberTextFieldState(search.query) }
    val coroutineScope = rememberCoroutineScope()
    val peopleListState = rememberLazyListState()
    val topicListState = rememberLazyListState()
    val generalListState = rememberLazyListState()
    val suggestionScrollState = rememberScrollState()
    val isMemberSearch = search.isRestrictedToMember
    val memberSearchName = search.restrictedMemberName.ifBlank { FeedStrings.unnamedMember }
    val searchPlaceholder = if (isMemberSearch) FeedStrings.searchMemberPosts(memberSearchName) else FeedStrings.searchContent
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
                val history = settings
                    .getStringOrNull(SEARCH_HISTORY_KEY)
                    ?.let { json ->
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
            settings.putString(SEARCH_HISTORY_KEY, ZhihuJson.json.encodeToString(searchHistoryItems.toList()))
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

    LaunchedEffect(viewModel.searchQuery, isMemberSearch, searchText) {
        if (viewModel.searchQuery.isNotEmpty() || isMemberSearch) return@LaunchedEffect
        snapshotFlow { searchText.text.toString() }
            .debounce(300)
            .mapLatest { text ->
                if (text.isBlank()) emptyList() else fetchSearchSuggest(paginationEnvironment, text.trim())
            }.collectLatest { items ->
                suggestItems.clear()
                suggestItems.addAll(items)
            }
    }

    val historyHeader: @Composable (Boolean) -> Unit = { showClearAction ->
        SearchScreenHistoryHeader(
            showClearAction = showClearAction,
            historyMoreMenuExpanded = historyMoreMenuExpanded,
            onHistoryMoreMenuExpandedChange = { historyMoreMenuExpanded = it },
            showClearHistoryConfirm = showClearHistoryConfirm,
            onShowClearHistoryConfirmChange = { showClearHistoryConfirm = it },
            historyCount = searchHistoryItems.size,
            onConfirmClearHistory = {
                searchHistoryItems.clear()
                settings.putString(SEARCH_HISTORY_KEY, ZhihuJson.json.encodeToString(searchHistoryItems.toList()))
            },
            onDisableHistory = {
                showSearchHistory.value = false
                settings.putBoolean("showSearchHistory", false)
                userMessages.showShortMessage(FeedStrings.searchHistoryDisabled)
            },
        )
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

    LaunchedEffect(viewModel.errorText) {
        viewModel.errorMessage?.let {
            userMessages.showMessage(it, UserMessageDuration.Long)
        }
    }

    AppScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SearchScreenTopBar(
                searchText = searchText,
                searchInputFocusRequester = searchInputFocusRequester,
                searchPlaceholder = searchPlaceholder,
                onSearch = {
                    keyboardController?.hide()
                    submitSearch(searchText.text.toString())
                },
                onBack = navigator.onNavigateBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (search.query.isNotEmpty() && !isMemberSearch) {
                SearchScreenTabs(
                    searchTab = viewModel.searchTab,
                    onSelect = { index ->
                        filterDraft = null
                        viewModel.selectTab(paginationEnvironment, SearchTab.entries[index])
                    },
                )
            }
            if (viewModel.displayItems.isEmpty() && !viewModel.isLoading && viewModel.searchQuery.isEmpty()) {
                SearchScreenLandingContent(
                    suggestItems = suggestItems,
                    showSearchHistory = showSearchHistory.value,
                    searchHistoryItems = searchHistoryItems,
                    showHotSearch = showHotSearch.value,
                    hotSearchItems = hotSearchItems,
                    suggestionScrollState = suggestionScrollState,
                    historyMoreMenuExpanded = historyMoreMenuExpanded,
                    hotSearchMoreMenuExpanded = hotSearchMoreMenuExpanded,
                    historyHeader = historyHeader,
                    isMemberSearch = isMemberSearch,
                    memberSearchName = memberSearchName,
                    onHotSearchMoreMenuExpandedChange = { hotSearchMoreMenuExpanded = it },
                    onSelect = { query ->
                        keyboardController?.hide()
                        submitSearch(query)
                    },
                    onRefreshHotSearch = { coroutineScope.launch { runCatching { fetchHotSearch() }.onFailure { if (it is CancellationException) throw it } } },
                    onDisableHotSearch = {
                        showHotSearch.value = false
                        settings.putBoolean("showSearchHotSearch", false)
                        userMessages.showShortMessage(FeedStrings.trendingSearchesDisabled)
                    },
                )
            } else if (viewModel.entities.isEmpty() && viewModel.errorMessage == null && !viewModel.isLoading && viewModel.isEnd) {
                SearchScreenEmptyResults(
                    searchTab = viewModel.searchTab,
                    generalListState = generalListState,
                    filters = viewModel.filters,
                    onFilterClick = {
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        filterDraft = viewModel.filters
                    },
                    onModifyKeyword = {
                        searchInputFocusRequester.requestFocus()
                        keyboardController?.show()
                    },
                )
            } else if (viewModel.searchTab != SearchTab.General) {
                SearchScreenEntityResults(
                    viewModel = viewModel,
                    paginationEnvironment = paginationEnvironment,
                    topicListState = topicListState,
                    peopleListState = peopleListState,
                    coroutineScope = coroutineScope,
                    onNavigateTopic = { result -> navigator.onNavigate(Topic(result.topic.id, result.topic.name)) },
                    onNavigatePerson = { person ->
                        navigator.onNavigate(Person(person.id, person.urlToken.orEmpty(), person.name.decodeSearchText()))
                    },
                    onFollowFailure = { userMessages.showShortMessage(FeedStrings.followFailed(it.message.orEmpty())) },
                )
            } else {
                SearchScreenGeneralResults(
                    viewModel = viewModel,
                    paginationEnvironment = paginationEnvironment,
                    generalListState = generalListState,
                    filterDraft = filterDraft,
                    feedAuthorBlockRequest = feedAuthorBlockRequest,
                    isMemberSearch = isMemberSearch,
                    memberSearchName = memberSearchName,
                    readingQueueSourceId = readingQueueSourceId,
                    onFilterClick = {
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        filterDraft = viewModel.filters
                    },
                    onBlockUser = { result, dismissMenu ->
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
                    onNavigatePerson = { person ->
                        navigator.onNavigate(
                            Person(
                                person.id,
                                person.urlToken.orEmpty(),
                                person.name.decodeSearchText(),
                            ),
                        )
                    },
                    refreshFab = {
                        val showRefreshFab = remember { settings.getBoolean("showRefreshFab", true) }
                        SearchScreenRefreshFab(
                            showRefreshFab = showRefreshFab,
                            isLoading = viewModel.isLoading,
                            onRefresh = {
                                viewModel.refresh(paginationEnvironment)
                            },
                        )
                    },
                )
            }
        }
    }

    SearchScreenOverlays(
        filterDraft = filterDraft,
        onFilterDraftChange = { filterDraft = it },
        onDismissFilter = { filterDraft = null },
        onApplyFilter = { draft ->
            filterDraft = null
            if (draft != viewModel.filters) {
                coroutineScope.launch { generalListState.scrollToItem(0) }
                viewModel.updateFilters(paginationEnvironment, draft)
            }
        },
        feedAuthorBlockRequest = feedAuthorBlockRequest,
        viewModel = viewModel,
        onDismissBlock = { feedAuthorBlockRequest = null },
        onConfirmBlock = {
            viewModel.refresh(paginationEnvironment)
            feedAuthorBlockRequest = null
        },
    )
}

@Composable
private fun SearchScreenOverlays(
    filterDraft: SearchFilters?,
    onFilterDraftChange: (SearchFilters) -> Unit,
    onDismissFilter: () -> Unit,
    onApplyFilter: (SearchFilters) -> Unit,
    feedAuthorBlockRequest: FeedAuthorBlockRequest?,
    viewModel: SearchViewModel,
    onDismissBlock: () -> Unit,
    onConfirmBlock: () -> Unit,
) {
    filterDraft?.let { draft ->
        SearchFilterMenu(
            filters = draft,
            onFiltersChange = onFilterDraftChange,
            onDismissRequest = onDismissFilter,
            onApply = { onApplyFilter(draft) },
        )
    }
    FeedAuthorBlockConfirmDialog(
        request = feedAuthorBlockRequest,
        viewModel = viewModel,
        onDismiss = onDismissBlock,
        onConfirm = onConfirmBlock,
    )
}
