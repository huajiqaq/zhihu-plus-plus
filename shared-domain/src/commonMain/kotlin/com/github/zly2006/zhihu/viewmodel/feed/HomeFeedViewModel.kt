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

import androidx.lifecycle.viewModelScope
import com.github.zly2006.zhihu.data.AdvertisementFeed
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.flattenFeeds
import com.github.zly2006.zhihu.data.navDestination
import com.github.zly2006.zhihu.data.questionAuthor
import com.github.zly2006.zhihu.data.target
import com.github.zly2006.zhihu.filter.RemoteHistorySync
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.platform.platformSettingsStore
import com.github.zly2006.zhihu.util.Log
import com.github.zly2006.zhihu.viewmodel.FeedDisplaySettings
import com.github.zly2006.zhihu.viewmodel.HomeFeedFilterResult
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.QualityFilterMode
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import com.github.zly2006.zhihu.viewmodel.filter.ContentDetailProvider
import com.github.zly2006.zhihu.viewmodel.filter.ContentType
import com.github.zly2006.zhihu.viewmodel.filter.ContentViewRecord
import com.github.zly2006.zhihu.viewmodel.filter.FeedDisplayFilterPipeline
import com.github.zly2006.zhihu.viewmodel.filter.ForegroundReadFilterPipeline
import com.github.zly2006.zhihu.viewmodel.filter.extractTopicIds
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import com.github.zly2006.zhihu.viewmodel.filter.toFeedFilterSettings
import com.github.zly2006.zhihu.viewmodel.getOrFetchContentDetail
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray

suspend fun resolveFeedBlockAuthorInfo(
    feedItem: FeedDisplayItem,
    contentDetailProvider: ContentDetailProvider?,
): Pair<String, String>? {
    feedItem.feed?.target?.author?.let { author ->
        return Pair(author.id, author.name)
    }

    return when (val content = resolveFeedBlockContentDetail(feedItem, contentDetailProvider)) {
        is DataHolder.Answer -> content.author.let { Pair(it.id, it.name) }
        is DataHolder.Article -> content.author.let { Pair(it.id, it.name) }
        is DataHolder.Question -> content.author.let { Pair(it.id, it.name) }
        is DataHolder.Pin -> content.author.let { Pair(it.id, it.name) }
        else -> null
    }
}

suspend fun resolveFeedQuestionAuthorInfo(
    feedItem: FeedDisplayItem,
    contentDetailProvider: ContentDetailProvider?,
): Pair<String, String>? {
    feedItem.feed?.target?.questionAuthor?.let { author ->
        return Pair(author.id, author.name)
    }

    val questionDestination = when (val raw = feedItem.raw) {
        is DataHolder.Question -> return raw.author.let { Pair(it.id, it.name) }
        is DataHolder.Answer -> {
            raw.question.author?.let { return Pair(it.id, it.name) }
            Question(questionId = raw.question.id, title = raw.question.title)
        }
        else -> when (val target = feedItem.feed?.target) {
            is Feed.AnswerTarget -> Question(questionId = target.question.id, title = target.question.title)
            is Feed.QuestionTarget -> Question(questionId = target.id, title = target.title)
            else -> null
        }
    } ?: return null

    val questionDetail = try {
        contentDetailProvider?.get(questionDestination) as? DataHolder.Question
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.e("HomeFeedViewModel", "Failed to resolve feed question author from question detail", error)
        null
    }
    return questionDetail
        ?.author
        ?.let { author ->
            Pair(author.id, author.name)
        }
}

suspend fun resolveFeedKeywordBlockingContent(
    feedItem: FeedDisplayItem,
    contentDetailProvider: ContentDetailProvider?,
): Triple<String, String, String?>? {
    val title = feedItem.title
    val summary = feedItem.summary ?: feedItem.feed?.target?.excerpt ?: ""
    val content = feedItem.content ?: when (val fullContent = resolveFeedBlockContentDetail(feedItem, contentDetailProvider)) {
        is DataHolder.Answer -> fullContent.content
        is DataHolder.Article -> fullContent.content
        is DataHolder.Question -> fullContent.detail
        is DataHolder.Pin -> fullContent.contentHtml
        else -> null
    }

    return if (title.isNotEmpty() || summary.isNotEmpty() || content != null) {
        Triple(title, summary, content)
    } else {
        null
    }
}

fun removeFeedItemsByBlockedTopic(
    viewModel: BaseFeedViewModel,
    topicId: String,
) {
    viewModel.displayItems.removeAll { item ->
        val raw = item.raw
        raw != null && extractTopicIds(raw)?.any { topic -> topic == topicId } == true
    }
}

private suspend fun resolveFeedBlockContentDetail(
    feedItem: FeedDisplayItem,
    contentDetailProvider: ContentDetailProvider?,
): DataHolder.Content? {
    val raw = feedItem.raw
    if (raw != null && raw !is DataHolder.DummyContent) {
        return raw
    }

    val navDestination = feedItem.navDestination ?: return null
    return contentDetailProvider?.get(navDestination)
}

interface HomeFeedInteractionViewModel {
    fun onUiContentClick(environment: ZhihuApiEnvironment, feed: Feed)
}

class HomeFeedViewModel(
    val remoteHistory: RemoteHistorySync = RemoteHistorySync(),
) : BaseFeedViewModel(),
    HomeFeedInteractionViewModel {
    private val reportedTouchedItems = hashSetOf<Pair<String, String>>()
    private var filterJob: Job? = null

    override val initialUrl: String
        get() = "https://api.zhihu.com/topstory/recommend"

    init {
        allowGuestAccess = true
    }

    public override suspend fun fetchFeeds(environment: PaginationEnvironment) {
        val generation = pagingGeneration
        val client = environment.httpClient()
        markItemsAsTouched(environment)
        if (generation != pagingGeneration || environment.httpClient() !== client) return
        super.fetchFeeds(environment)
        if (generation != pagingGeneration || environment.httpClient() !== client) return
        val pendingFilter = filterJob
        if (pendingFilter?.isActive == true) {
            isLoading = true
            try {
                pendingFilter.join()
            } finally {
                if (generation == pagingGeneration && environment.httpClient() === client && filterJob === pendingFilter) isLoading = false
            }
        }
    }

    override fun processResponse(environment: PaginationEnvironment, data: List<Feed>, rawData: JsonArray) {
        val generation = pagingGeneration
        val client = environment.httpClient()
        if (isRefreshPending) {
            allData.clear()
            debugData.clear()
            isRefreshPending = false
        }
        allData.addAll(data)
        debugData.addAll(rawData)

        filterJob = viewModelScope.launch {
            try {
                val displaySettings = FeedDisplaySettings(platformSettingsStore())
                val filterSettings = platformSettingsStore().toFeedFilterSettings()
                val loadedItems = data
                    .flattenFeeds()
                    .map { feed -> createDisplayItem(displaySettings, feed) }
                val newItems = loadedItems.filterNot {
                    (it.feed is AdvertisementFeed && !displaySettings.reverseBlock) ||
                        (it.isQualityFiltered && displaySettings.qualityFilterMode == QualityFilterMode.HIDE)
                }

                val reverseBlock = displaySettings.reverseBlock
                remoteHistory.awaitRecentPage()
                if (generation != pagingGeneration || environment.httpClient() !== client) return@launch
                val foregroundItems = ForegroundReadFilterPipeline(filterSettings).filter(newItems)
                if (generation != pagingGeneration || environment.httpClient() !== client) return@launch
                if (!reverseBlock) {
                    withContext(Dispatchers.Main) {
                        if (generation == pagingGeneration && environment.httpClient() === client) addDisplayItems(foregroundItems)
                    }
                }

                val filteredItems = FeedDisplayFilterPipeline(
                    settings = filterSettings,
                    contentDetailProvider = ContentDetailProvider(environment::getOrFetchContentDetail),
                ).filter(foregroundItems)
                if (generation != pagingGeneration || environment.httpClient() !== client) return@launch
                if (reverseBlock) {
                    addDisplayItems(filteredItems)
                }

                val filterResult = HomeFeedFilterResult(
                    foregroundItems = foregroundItems,
                    filteredItems = filteredItems,
                    reverseBlock = reverseBlock,
                )

                // 移除被过滤的条目，并更新已保留条目的 raw 内容
                withContext(Dispatchers.Main) {
                    if (generation == pagingGeneration && environment.httpClient() === client) {
                        displayItems.replaceHomeFeedItemsWithFilteredResult(filterResult)
                        latestLoadedDisplayItems.value = filterResult.filteredItems
                        completedPageCount++
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == pagingGeneration && environment.httpClient() === client) errorHandle(e)
            }
        }
    }

    override fun onUiContentClick(environment: ZhihuApiEnvironment, feed: Feed) {
        viewModelScope.launch(Dispatchers.Default) {
            if (environment.authenticatedCookies()["d_c0"] != null) {
                val payloadItem = when (val target = feed.target) {
                    is Feed.AnswerTarget -> listOf("answer", target.id.toString(), "read")
                    is Feed.ArticleTarget -> listOf("article", target.id.toString(), "read")
                    is Feed.PinTarget -> listOf("pin", target.id.toString(), "read")
                    else -> null
                }
                if (payloadItem != null) {
                    environment.post("https://www.zhihu.com/lastread/touch") {
                        header("x-requested-with", "fetch")
                        setBody(
                            MultiPartFormDataContent(
                                formData {
                                    append("items", ZhihuJson.json.encodeToString(listOf(payloadItem)))
                                },
                            ),
                        )
                    }
                }
            }
            try {
                if (platformSettingsStore().getBoolean("enableContentFilter", true)) {
                    val target = feed.target ?: return@launch
                    val (type, id) = when (target) {
                        is Feed.AnswerTarget -> ContentType.ANSWER to target.id.toString()
                        is Feed.ArticleTarget -> ContentType.ARTICLE to target.id.toString()
                        is Feed.QuestionTarget -> ContentType.QUESTION to target.id.toString()
                        is Feed.PinTarget -> ContentType.PIN to target.id.toString()
                        else -> return@launch
                    }
                    getContentFilterDatabase().contentFilterDao().markAsInteracted(ContentViewRecord.generateId(type, id))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                environment.handleFetchFailure("HomeFeedViewModel", e)
            }
        }
    }

    private suspend fun markItemsAsTouched(
        environment: ZhihuApiEnvironment,
    ) {
        try {
            if (environment.authenticatedCookies()["d_c0"] == null) return
            val currentTouchItems = displayItems
                .asSequence()
                .filterNot { it.isFiltered }
                .mapNotNull { it.feed?.target }
                .mapNotNull { target ->
                    when (target) {
                        is Feed.AnswerTarget -> "answer" to target.id.toString()
                        is Feed.ArticleTarget -> "article" to target.id.toString()
                        is Feed.PinTarget -> "pin" to target.id.toString()
                        else -> null
                    }
                }.toList()
            val untouchedItemSet = currentTouchItems - reportedTouchedItems

            if (untouchedItemSet.isNotEmpty()) {
                val payload = untouchedItemSet.map { (type, id) -> listOf(type, id, "touch") }
                val response = environment.post("https://www.zhihu.com/lastread/touch") {
                    header("x-requested-with", "fetch")
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("items", ZhihuJson.json.encodeToString(payload))
                            },
                        ),
                    )
                }
                if (response.status.isSuccess()) {
                    reportedTouchedItems.addAll(untouchedItemSet)
                } else {
                    Log.e("Browse-Touch", response.bodyAsText())
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            environment.handleFetchFailure("FeedViewModel", e)
        }
    }

    internal override fun resetForRefresh() {
        // 前一轮的异步过滤不能在新一轮刷新后重新写回旧卡片。
        filterJob?.cancel()
        filterJob = null
        reportedTouchedItems.clear()
        super.resetForRefresh()
    }
}
