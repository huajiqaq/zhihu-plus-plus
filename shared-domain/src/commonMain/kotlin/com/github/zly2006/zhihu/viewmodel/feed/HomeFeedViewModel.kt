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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.RecommendationMode
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.ZhihuPaging
import com.github.zly2006.zhihu.data.displayExcerpt
import com.github.zly2006.zhihu.data.navDestination
import com.github.zly2006.zhihu.data.questionAuthor
import com.github.zly2006.zhihu.data.target
import com.github.zly2006.zhihu.filter.RemoteHistorySync
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.platform.platformSettingsStore
import com.github.zly2006.zhihu.util.AppLog
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import com.github.zly2006.zhihu.viewmodel.filter.ContentDetailProvider
import com.github.zly2006.zhihu.viewmodel.filter.ContentType
import com.github.zly2006.zhihu.viewmodel.filter.ContentViewRecord
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.encodeToString

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
        AppLog.e("HomeFeedViewModel", "Failed to resolve feed question author from question detail", error)
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
    val summary = feedItem.summary ?: feedItem.feed?.target?.displayExcerpt ?: ""
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
    val mode: RecommendationMode = RecommendationMode.WEB,
    remoteHistory: RemoteHistorySync = RemoteHistorySync(),
) : HomeFeedStateViewModel(remoteHistory), HomeFeedInteractionViewModel {
    private class PendingPage(val page: HomeFeedPage, val generation: Int, val client: HttpClient, val environment: PaginationEnvironment) {
        var preview by mutableStateOf<List<FeedDisplayItem>>(emptyList())
        var previewRevision by mutableIntStateOf(-1)
    }

    private class SourceState(val source: HomeFeedSource) {
        var paging: ZhihuPaging? by mutableStateOf(null)
        var failure: Exception? by mutableStateOf(null)
        var pending: PendingPage? by mutableStateOf(null)
    }

    private val sources = when (mode) {
        RecommendationMode.WEB -> listOf(SourceState(WebHomeFeedSource))
        RecommendationMode.ANDROID -> listOf(SourceState(MobileHomeFeedSource))
        RecommendationMode.MIXED -> listOf(SourceState(MobileHomeFeedSource), SourceState(WebHomeFeedSource))
        RecommendationMode.LOCAL -> error("Local recommendations have their own loader")
    }
    private val reportedTouchedItems = hashSetOf<Pair<String, String>>()
    private var sourceClient: HttpClient? = null
    private var publicationOrder by mutableStateOf<List<String>>(emptyList())

    // 候选是来源请求的临时投影；确认列表、快照和分页仍只有 Base 的单一所有者。
    override val displayItems: List<FeedDisplayItem>
        get() {
            val previews = sources.filter {
                !isRefreshPending || it.failure == null || committedDisplayItems.isEmpty()
            }.mapNotNull { it.pending }.filter {
                it.previewRevision == filterPolicyRevision && requestIsCurrent(it.environment, it.generation, it.client)
            }.flatMap { it.preview }
            if (isRefreshPending && previews.isEmpty()) return committedDisplayItems
            val confirmed = if (isRefreshPending) emptyList() else committedDisplayItems
            return inPublicationOrder(confirmed + previews)
        }

    private fun inPublicationOrder(items: List<FeedDisplayItem>): List<FeedDisplayItem> {
        val remaining = items.distinctBy { it.stableKey }.associateBy { it.stableKey }.toMutableMap()
        return buildList {
            publicationOrder.forEach { key -> remaining.remove(key)?.let { add(it) } }
            addAll(remaining.values)
        }
    }

    override val initialUrl: String get() = sources.first().source.initialUrl
    override val isEnd: Boolean get() = sources.all { it.paging?.isEnd == true }
    override val hasNextPage: Boolean get() = sources.any { it.paging?.let { page -> !page.isEnd && page.next.isNotBlank() } == true }

    init { allowGuestAccess = mode == RecommendationMode.WEB }

    override fun loadMore(environment: PaginationEnvironment) {
        val client = environment.httpClient()
        if (sourceClient != null && sourceClient !== client) resetForRefresh()
        sourceClient = client
        super.loadMore(environment)
    }

    override suspend fun fetchFeeds(environment: PaginationEnvironment) {
        val generation = pageGeneration
        val client = environment.httpClient()
        sourceClient = client
        if (mode != RecommendationMode.ANDROID) markItemsAsTouched(environment)
        if (!requestIsCurrent(environment, generation, client)) return
        val failedSources = sources.filter { it.failure != null }
        val requestedSources = failedSources.ifEmpty { sources.filter { it.paging?.isEnd != true } }
        if (failedSources.isEmpty() || publicationOrder.isEmpty()) {
            publicationOrder = if (isRefreshPending) emptyList() else committedDisplayItems.map { it.stableKey }
        }
        // 每个来源的页保留到过滤成功；重试不能推进已经成功的另一来源。
        supervisorScope {
            requestedSources.map { state ->
                async {
                    try {
                        val pending = state.pending ?: run {
                            val page = state.source.load(environment, state.paging?.next ?: state.source.initialUrl)
                            currentCoroutineContext().ensureActive()
                            if (!requestIsCurrent(environment, generation, client)) return@async
                            PendingPage(page, generation, client, environment).also { state.pending = it }
                        }
                        val page = pending.page
                        state.failure = null
                        publishHomePage(
                            environment, generation, client, page.items, page.data, page.rawData,
                            foreground = true,
                            preview = { items, revision ->
                                if (state.pending === pending && requestIsCurrent(environment, generation, client)) {
                                    publicationOrder = (publicationOrder + items.map { it.stableKey }).distinct()
                                    pending.preview = items
                                    pending.previewRevision = revision
                                }
                            },
                        ) {
                            // 确认只精化首次显示的位置，不能把先完成过滤的来源移到前面。
                            replaceDisplayItems(inPublicationOrder(committedDisplayItems))
                            publicationOrder = (publicationOrder + committedDisplayItems.map { it.stableKey }).distinct()
                            state.paging = page.paging
                            state.failure = null
                            state.pending = null
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        if (requestIsCurrent(environment, generation, client)) {
                            state.failure = error
                            try {
                                if (state.source === MobileHomeFeedSource) environment.handleMobileHomeFeedFailure(error)
                                else environment.handleFetchFailure("HomeFeedViewModel", error)
                            } catch (reportError: CancellationException) {
                                throw reportError
                            } catch (reportError: Exception) {
                                AppLog.e("HomeFeedViewModel", "Failed to report source failure", reportError)
                            }
                        }
                    }
                }
            }.awaitAll()
        }
        if (requestIsCurrent(environment, generation, client)) {
            if (sources.all { it.pending == null }) publicationOrder = emptyList()
            sources.firstNotNullOfOrNull { it.failure }?.let(::errorHandle)
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
            val currentTouchItems = committedDisplayItems
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
                    AppLog.e("Browse-Touch", response.bodyAsText())
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            environment.handleFetchFailure("FeedViewModel", e)
        }
    }

    override fun resetForRefresh() {
        super.resetForRefresh()
        sources.forEach { it.paging = null; it.failure = null; it.pending = null }
        publicationOrder = emptyList()
        reportedTouchedItems.clear()
    }
}
