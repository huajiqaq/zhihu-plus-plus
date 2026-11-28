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

package com.github.zly2006.zhihu.viewmodel.feed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.zly2006.zhihu.data.CommonFeed
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.SEARCH_RESULT_VERB
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.decodeSearchText
import com.github.zly2006.zhihu.data.target
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.platform.platformSettingsStore
import com.github.zly2006.zhihu.util.DisplayFailure
import com.github.zly2006.zhihu.util.raiseForStatus
import com.github.zly2006.zhihu.viewmodel.FeedDisplaySettings
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

const val ZHIHU_HOT_SEARCH_URL = "https://www.zhihu.com/api/v4/search/hot_search"
const val ZHIHU_SEARCH_SUGGEST_URL = "https://www.zhihu.com/api/v4/search/suggest"
private const val SEARCH_VERTICAL_INFO = "0,0,0,0,0,0,0,0,0,0,0,0"

open class SearchViewModel(
    val searchQuery: String,
    private val restrictedMemberHashId: String = "",
) : BaseFeedViewModel() {
    private val committedEntities = mutableStateListOf<SearchEntity>()
    val entities: List<SearchEntity>
        get() {
            val preview = previewDisplayItems?.let(::createPageEntities)?.takeIf { it.isNotEmpty() } ?: return committedEntities
            return ((if (isRefreshPending) emptyList() else committedEntities) + preview).distinctBy(SearchEntity::id)
        }
    override val previewBeforeFiltering: Boolean get() = searchTab == SearchTab.General
    override val hasCommittedPageItems: Boolean get() = committedEntities.isNotEmpty()
    private var pendingGeneralEntities = emptyList<PendingGeneralEntity>()
    private var pendingTabEntities = emptyList<SearchEntity>()
    val changingTopicIds = mutableStateListOf<String>()
    var filters by mutableStateOf(SearchFilters())
        private set
    var searchTab by mutableStateOf(SearchTab.General)
        private set

    override val initialUrl: String
        get() = zhihuSearchUrl(searchQuery, searchTab, filters, restrictedMemberHashId)

    override val include = "data[*].highlight,object,type"

    fun selectTab(
        environment: PaginationEnvironment,
        tab: SearchTab,
    ) {
        if (searchTab == tab) return
        searchTab = tab
        refresh(environment)
    }

    fun updateFilters(
        environment: PaginationEnvironment,
        newFilters: SearchFilters,
    ) {
        if (filters == newFilters) return
        filters = newFilters
        refresh(environment)
    }

    suspend fun setTopicFollowing(
        environment: PaginationEnvironment,
        topicId: String,
        following: Boolean,
    ): Result<Unit> {
        val index = committedEntities.indexOfFirst { it is SearchEntity.Topic && it.id == topicId }
        val previous = committedEntities.getOrNull(index) as? SearchEntity.Topic
            ?: return Result.success(Unit)
        if (topicId in changingTopicIds || previous.isFollowing == following) return Result.success(Unit)

        changingTopicIds += topicId
        committedEntities[index] = previous.copy(isFollowing = following)
        return runCatching {
            val endpoint = "https://www.zhihu.com/api/v4/topics/$topicId/followers"
            val response = if (following) environment.post(endpoint) else environment.delete(endpoint)
            response.raiseForStatus()
            Unit
        }.onFailure {
            val currentIndex = committedEntities.indexOfFirst { it.id == topicId }
            if (currentIndex >= 0) committedEntities[currentIndex] = previous
        }.also { result ->
            changingTopicIds -= topicId
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
        }
    }

    override fun refresh(environment: PaginationEnvironment) {
        pendingGeneralEntities = emptyList()
        pendingTabEntities = emptyList()
        super.refresh(environment)
    }

    override suspend fun fetchFeeds(environment: PaginationEnvironment) {
        // 首屏过滤后没有可见结果时，由同一搜索请求所有者沿真实 next 继续，错误/结束/重复游标立即停止。
        val generation = pagingGeneration
        val client = environment.httpClient()
        val visitedUrls = mutableSetOf<String>()
        do {
            val requestUrl = resolvePageUrl()
            if (!visitedUrls.add(requestUrl)) return
            isLoading = true
            super.fetchFeeds(environment)
            if (generation != pagingGeneration || environment.httpClient() !== client) return
        } while (committedEntities.isEmpty() && errorMessage == null && !isEnd && resolvePageUrl() !in visitedUrls)
    }

    override suspend fun decodePage(
        environment: PaginationEnvironment,
        rawData: JsonArray,
    ): List<Feed> {
        val existingIds = if (isRefreshPending) mutableSetOf() else committedEntities.mapTo(mutableSetOf(), SearchEntity::id)
        val decodedEntities = mutableListOf<SearchEntity>()
        var decodedTopicCount = 0
        val indexedEntries = rawData.withIndex().sortedBy { (responseOrder, element) ->
            (element as? JsonObject)
                ?.get("index")
                ?.jsonPrimitive
                ?.content
                ?.toIntOrNull() ?: responseOrder
        }
        if (searchTab == SearchTab.General) {
            pendingGeneralEntities = indexedEntries.mapNotNull { (_, element) ->
                val entry = element as? JsonObject ?: return@mapNotNull null
                if (entry["type"]?.jsonPrimitive?.content != "search_result") return@mapNotNull null
                val content = entry["object"] as? JsonObject ?: return@mapNotNull null
                try {
                    if (content["type"]?.jsonPrimitive?.content == "people") {
                        PendingGeneralEntity.Person(ZhihuJson.decodeJson<DataHolder.People>(content))
                    } else {
                        PendingGeneralEntity.Content(
                            CommonFeed(
                                id = content["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                                verb = SEARCH_RESULT_VERB,
                                target = ZhihuJson.decodeJson<Feed.Target>(content),
                            ),
                        )
                    }
                } catch (e: Exception) {
                    environment.logDecodeFailure("SearchViewModel", element, e)
                    null
                }
            }
            return pendingGeneralEntities.mapNotNull { (it as? PendingGeneralEntity.Content)?.feed }
        }
        val feeds = indexedEntries.mapNotNull { (_, element) ->
            val entry = element as? JsonObject ?: return@mapNotNull null
            if (entry["type"]?.jsonPrimitive?.content != "search_result") return@mapNotNull null
            val content = entry["object"] as? JsonObject ?: return@mapNotNull null
            try {
                when (searchTab) {
                    SearchTab.General -> error("General 搜索已在保序分支解码")
                    SearchTab.People -> {
                        val person = ZhihuJson.decodeJson<DataHolder.People>(content)
                        if (existingIds.add(person.id)) decodedEntities += SearchEntity.Person(person)
                        null
                    }
                    SearchTab.Topic -> {
                        val topic = ZhihuJson.decodeJson<TopicSearchObject>(content)
                        if (topic.type != "topic") return@mapNotNull null
                        decodedTopicCount++
                        if (existingIds.add(topic.id)) {
                            decodedEntities += SearchEntity.Topic(
                                topic = DataHolder.Topic(
                                    id = topic.id,
                                    type = topic.type,
                                    url = topic.url,
                                    name = topic.name.decodeSearchText(),
                                    avatarUrl = topic.avatarUrl,
                                    topicType = topic.topicType,
                                ),
                                excerpt = topic.excerpt.decodeSearchText(),
                                visitCount = topic.visitCount,
                                discussCount = topic.topAnswerCount,
                                isFollowing = topic.isFollowing,
                            )
                        }
                        null
                    }
                }
            } catch (e: Exception) {
                environment.logDecodeFailure("SearchViewModel", element, e)
                null
            }
        }
        if (searchTab == SearchTab.Topic && rawData.isNotEmpty() && decodedTopicCount == 0) {
            val count = rawData.size
            throw DisplayFailure { DomainStrings.unreadableTopicResults(count) }
        }
        pendingTabEntities = decodedEntities
        return feeds
    }

    override suspend fun prepareResponse(environment: PaginationEnvironment, data: List<Feed>, rawData: JsonArray): () -> Unit {
        val blockedUserIds = if (searchTab == SearchTab.General && data.isNotEmpty()) environment.blockedUserIds() else emptySet()
        val filteredData = data.filterNot { it.target?.author?.id in blockedUserIds }
        val settings = FeedDisplaySettings(platformSettingsStore())
        val items = createDisplayItems(settings, filteredData)
        val newEntities = createPageEntities(items)
        return {
            if (isRefreshPending) committedEntities.clear()
            commitDisplayPage(items, filteredData, rawData)
            val ids = committedEntities.mapTo(hashSetOf(), SearchEntity::id)
            committedEntities.addAll(newEntities.filter { ids.add(it.id) })
            pendingTabEntities = emptyList()
            pendingGeneralEntities = emptyList()
        }
    }

    private fun createPageEntities(items: List<FeedDisplayItem>): List<SearchEntity> {
        if (searchTab != SearchTab.General) return pendingTabEntities
        val loadedContent = items.associateBy(FeedDisplayItem::stableKey)
        val settings = FeedDisplaySettings(platformSettingsStore())
        return pendingGeneralEntities.mapNotNull { pending ->
            when (pending) {
                is PendingGeneralEntity.Person -> SearchEntity.Person(pending.person)
                is PendingGeneralEntity.Content -> loadedContent[createDisplayItem(settings, pending.feed).stableKey]?.let { SearchEntity.Content(it) }
            }
        }
    }
}

/**
 * 搜索建议词，来自 `ZHIHU_SEARCH_SUGGEST_URL`。
 *
 * `label == "hot"` 的建议带热搜图标（[iconUrl] 非空），普通建议 [label] 为空字符串。
 * 该接口只需要设备 cookie（d_c0），不要求登录令牌和 zse96 签名，可容忍单次失败静默降级。
 */
@Serializable
data class SearchSuggestItem(
    val query: String,
    val label: String = "",
    val iconUrl: String = "",
)

/** 建议请求失败时保留热搜与历史入口。 */
suspend fun fetchSearchSuggest(
    environment: PaginationEnvironment,
    query: String,
): List<SearchSuggestItem> {
    val json = runCatching {
        environment.fetchJson("$ZHIHU_SEARCH_SUGGEST_URL?q=${query.encodeURLParameter()}", "")
    }.getOrElse { error ->
        if (error is CancellationException) throw error
        null
    } ?: return emptyList()
    val suggest = json["suggest"] as? JsonArray ?: return emptyList()
    return suggest.mapNotNull { element ->
        try {
            ZhihuJson.decodeJson<SearchSuggestItem>(element)
        } catch (e: Exception) {
            environment.logDecodeFailure("SearchSuggest", element, e)
            null
        }
    }
}

private sealed interface PendingGeneralEntity {
    data class Person(
        val person: DataHolder.People,
    ) : PendingGeneralEntity

    data class Content(
        val feed: Feed,
    ) : PendingGeneralEntity
}

sealed interface SearchEntity {
    val id: String

    data class Person(
        val person: DataHolder.People,
    ) : SearchEntity {
        override val id = person.id
    }

    data class Content(
        val item: FeedDisplayItem,
    ) : SearchEntity {
        override val id = item.stableKey
    }

    data class Topic(
        val topic: DataHolder.Topic,
        val excerpt: String,
        val visitCount: Long,
        val discussCount: Long,
        val isFollowing: Boolean,
    ) : SearchEntity {
        override val id = topic.id
    }
}

@Serializable
private data class TopicSearchObject(
    val id: String,
    val type: String,
    val url: String,
    val name: String,
    val avatarUrl: String? = null,
    val topicType: String? = null,
    val excerpt: String = "",
    val visitCount: Long = 0,
    val topAnswerCount: Long = 0,
    val isFollowing: Boolean = false,
)

data class SearchFilters(
    val sort: SearchSortOption = SearchSortOption.Default,
    val contentType: SearchContentType = SearchContentType.All,
    val timeRange: SearchTimeRange = SearchTimeRange.All,
)

enum class SearchSortOption(
    val parameter: String,
) {
    Default(""),
    Latest("created_time"),
    MostVoted("upvoted_count"),
    ;

    val label: String
        get() = when (this) {
            Default -> DomainStrings.searchRelevance
            Latest -> DomainStrings.searchLatest
            MostVoted -> DomainStrings.searchUpvotes
        }
}

enum class SearchContentType(
    val parameter: String,
) {
    All(""),
    Answer("answer"),
    Article("article"),
    Video("zvideo"),
    ;

    val label: String
        get() = when (this) {
            All -> DomainStrings.searchAllContent
            Answer -> DomainStrings.searchAnswers
            Article -> DomainStrings.searchArticles
            Video -> DomainStrings.searchVideos
        }
}

enum class SearchTab(
    val parameter: String,
) {
    General("general"),
    People("people"),
    Topic("topic"),
    ;

    val label: String
        get() = when (this) {
            General -> DomainStrings.searchEverything
            People -> DomainStrings.searchPeople
            Topic -> DomainStrings.searchTopics
        }
}

enum class SearchTimeRange(
    val parameter: String,
) {
    All(""),
    Day("a_day"),
    Week("a_week"),
    Month("a_month"),
    ThreeMonths("three_months"),
    HalfYear("half_a_year"),
    Year("a_year"),
    ;

    val label: String
        get() = when (this) {
            All -> DomainStrings.searchAnyTime
            Day -> DomainStrings.searchDay
            Week -> DomainStrings.searchWeek
            Month -> DomainStrings.searchMonth
            ThreeMonths -> DomainStrings.searchThreeMonths
            HalfYear -> DomainStrings.searchHalfYear
            Year -> DomainStrings.searchYear
        }
}

fun zhihuSearchUrl(
    query: String,
    searchTab: SearchTab = SearchTab.General,
    filters: SearchFilters = SearchFilters(),
    restrictedMemberHashId: String = "",
): String {
    val activeFilters = if (searchTab == SearchTab.General) filters else SearchFilters()
    val params = buildList {
        add("gk_version" to "gz-gaokao")
        add("t" to searchTab.parameter)
        add("q" to query)
        add("correction" to "1")
        add("offset" to "0")
        add("limit" to "20")
        add("search_source" to if (activeFilters == SearchFilters()) "Normal" else "Filter")
        add("show_all_topics" to if (searchTab == SearchTab.Topic) "1" else "0")
        if (restrictedMemberHashId.isNotBlank()) {
            add("filter_fields" to "")
            add("lc_idx" to "0")
            add("restricted_scene" to "member")
            add("restricted_field" to "member_hash_id")
            add("restricted_value" to restrictedMemberHashId)
        }
        activeFilters.contentType.parameter.takeIf(String::isNotEmpty)?.let {
            add("vertical" to it)
            add("vertical_info" to SEARCH_VERTICAL_INFO)
        }
        activeFilters.sort.parameter
            .takeIf(String::isNotEmpty)
            ?.let { add("sort" to it) }
        activeFilters.timeRange.parameter
            .takeIf(String::isNotEmpty)
            ?.let { add("time_interval" to it) }
    }.joinToString("&") { (key, value) ->
        "$key=${value.encodeURLParameter(spaceToPlus = true)}"
    }
    return "https://www.zhihu.com/api/v4/search_v3?$params"
}
