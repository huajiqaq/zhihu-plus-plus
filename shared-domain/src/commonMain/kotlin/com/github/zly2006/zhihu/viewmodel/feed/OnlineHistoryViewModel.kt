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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.HistoryType
import com.github.zly2006.zhihu.data.LocalHistoryStorage
import com.github.zly2006.zhihu.data.OnlineHistoryDeletePair
import com.github.zly2006.zhihu.data.OnlineHistoryItem
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.ZhihuPaging
import com.github.zly2006.zhihu.data.toFeedDisplayItemNavDestinationJson
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.resolveContent
import com.github.zly2006.zhihu.util.raiseForStatus
import com.github.zly2006.zhihu.util.signZhihuFetchRequest
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

class OnlineHistoryViewModel : BaseFeedViewModel() {
    @Serializable
    private data class HistoryTotal(
        val disabled: Boolean? = null,
    )

    override val initialUrl = "https://api.zhihu.com/unify-consumption/read_history?offset=0&limit=10"
    override val shouldLogDecodeFailures = false
    private val deletionPairs = mutableMapOf<String, OnlineHistoryDeletePair>()
    private val localHistory by lazy { LocalHistoryStorage() }
    private var loaded = false
    private var accountClient: HttpClient? = null
    private var accountGeneration = 0
    private var recordingGeneration = 0

    var accountId: String? = null
        private set
    var recordingEnabled by mutableStateOf(true)
        private set
    var isChangingRecording by mutableStateOf(false)
        private set

    fun loadFor(environment: PaginationEnvironment, accountId: String) {
        val client = environment.httpClient()
        val changedAccount = this.accountId != accountId || accountClient !== client
        if (changedAccount) {
            this.accountId = accountId
            accountClient = client
            accountGeneration++
            recordingEnabled = true
            isChangingRecording = false
            clearLoadedItems()
            loaded = false
        }
        if (changedAccount || !loaded && displayItems.isEmpty() && !isLoading) refresh(environment)
    }

    fun clearForGuest() {
        accountId = null
        accountClient = null
        accountGeneration++
        loaded = false
        recordingEnabled = true
        isChangingRecording = false
        resetForRefresh()
        clearLoadedItems()
    }

    private fun markCleared() {
        resetForRefresh()
        clearLoadedItems()
        pendingRefresh.value = false
        loaded = true
        lastPaging = ZhihuPaging(isEnd = true, next = "")
    }

    private fun clearLoadedItems() {
        displayItems.clear()
        deletionPairs.clear()
    }

    /** 同一账号重登录会替换 client；A→B→A 的迟到响应不能仅凭账号字符串识别。 */
    private fun isCurrentAccount(environment: PaginationEnvironment, generation: Int, client: HttpClient): Boolean =
        generation == accountGeneration && accountId != null && accountClient === client && environment.httpClient() === client

    suspend fun loadRecordingSetting(environment: PaginationEnvironment, accountId: String) {
        val client = accountClient ?: return
        val generation = accountGeneration
        if (this.accountId != accountId || !isCurrentAccount(environment, generation, client) || isChangingRecording) return
        val settingGeneration = ++recordingGeneration
        try {
            val json = environment.withAuthenticatedClient { requestClient, cookies ->
                if (requestClient !== client || !isCurrentAccount(environment, generation, client)) return@withAuthenticatedClient null
                val response = environment.get("https://api.zhihu.com/read_history/total").raiseForStatus()
                ZhihuJson.json.parseToJsonElement(response.bodyAsText()).jsonObject
            } ?: return
            currentCoroutineContext().ensureActive()
            if (!isCurrentAccount(environment, generation, client) || settingGeneration != recordingGeneration) return
            val disabled = ZhihuJson.decodeJson<HistoryTotal>(json).disabled ?: return
            val wasEnabled = recordingEnabled
            if (disabled) localHistory.setRecordingEnabled(false)
            recordingEnabled = !disabled
            if (disabled) {
                markCleared()
            } else if (!wasEnabled) {
                refresh(environment)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isCurrentAccount(environment, generation, client) && settingGeneration == recordingGeneration) throw e
        }
    }

    suspend fun setRecordingEnabled(environment: PaginationEnvironment, enabled: Boolean): Boolean {
        val client = accountClient ?: return false
        val generation = accountGeneration
        if (!isCurrentAccount(environment, generation, client) || isChangingRecording || recordingEnabled == enabled) return false
        isChangingRecording = true
        val settingGeneration = ++recordingGeneration
        try {
            val response = environment.withAuthenticatedClient { requestClient, cookies ->
                if (requestClient !== client || !isCurrentAccount(environment, generation, client)) return@withAuthenticatedClient null
                client.post("https://api.zhihu.com/read_history/set_switch") {
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject { put("enabled", enabled) }.toString())
                    signZhihuFetchRequest(cookies)
                }
            } ?: return false
            currentCoroutineContext().ensureActive()
            if (!isCurrentAccount(environment, generation, client)) return false
            check(response.status.isSuccess()) { "设置浏览记录失败: ${response.status}" }
            if (!enabled) localHistory.clearAndSave()
            localHistory.setRecordingEnabled(enabled)
            recordingEnabled = enabled
            if (enabled) refresh(environment) else markCleared()
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!isCurrentAccount(environment, generation, client)) return false
            throw e
        } finally {
            if (generation == accountGeneration && settingGeneration == recordingGeneration) isChangingRecording = false
        }
    }

    override fun processResponse(environment: PaginationEnvironment, data: List<Feed>, rawData: JsonArray) {
        if (!recordingEnabled) return
        val localEntries = localHistory.history
        if (pendingRefresh.value) {
            clearLoadedItems()
            pendingRefresh.value = false
        }
        for (element in rawData) {
            val item = runCatching { ZhihuJson.decodeJson<OnlineHistoryItem>(element) }.getOrNull() ?: continue
            val nav = resolveContent(item.data.action.url)
            val type = nav?.let(HistoryType::from)?.takeUnless { it == HistoryType.OTHER }
                ?: HistoryType.fromContentType(item.data.extra.contentType)
            val pair = OnlineHistoryDeletePair(item.data.extra.contentToken, item.data.extra.contentType)
            val matchedItem = localEntries.firstOrNull { it == nav }
            val destination = if (nav is Article) {
                val cached = matchedItem as? Article ?: nav
                cached.copy(
                    title = item.data.header.title
                        .ifBlank { cached.title },
                    authorName = item.data.content
                        ?.authorName
                        ?.takeIf(String::isNotBlank) ?: cached.authorName,
                    excerpt = item.data.content
                        ?.summary
                        ?.takeIf(String::isNotBlank) ?: cached.excerpt,
                    questionId = if (nav.type == ArticleType.Answer) {
                        item.data.extra.questionToken
                            .toLongOrNull()
                            ?.takeIf { it > 0 } ?: cached.questionId
                    } else {
                        cached.questionId
                    },
                )
            } else {
                nav
            }
            val key = "online:${pair.contentType}:${pair.contentToken}"
            deletionPairs[key] = pair
            if (displayItems.any { it.stableKey == key }) continue
            displayItems += FeedDisplayItem(
                title = item.data.header.title,
                summary = item.data.content
                    ?.summary
                    .orEmpty(),
                details = item.data.matrix
                    ?.firstOrNull()
                    ?.data
                    ?.text
                    ?.takeIf(String::isNotBlank) ?: type,
                feed = null,
                navDestinationJson = destination?.toFeedDisplayItemNavDestinationJson(),
                avatarSrc = (matchedItem as? Article)?.avatarSrc,
                authorName = item.data.content?.authorName,
                historyType = type,
                titleBadges = item.data.header.badges,
                historyKey = key,
            )
        }
        loaded = true
    }

    suspend fun deleteItems(environment: PaginationEnvironment, keys: Set<String>): Boolean {
        val client = accountClient ?: return false
        val generation = accountGeneration
        if (keys.isEmpty() || !isCurrentAccount(environment, generation, client)) return false
        val pairs = keys.mapTo(linkedSetOf()) { key -> checkNotNull(deletionPairs[key]) { "历史记录已改变，请重新选择" } }
        try {
            val response = environment.withAuthenticatedClient { requestClient, cookies ->
                if (requestClient !== client || !isCurrentAccount(environment, generation, client)) return@withAuthenticatedClient null
                client.post("https://api.zhihu.com/read_history/batch_del") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        buildJsonObject {
                            put(
                                "pairs",
                                buildJsonArray {
                                    for (pair in pairs) {
                                        add(
                                            buildJsonObject {
                                                put("content_token", pair.contentToken)
                                                put("content_type", pair.contentType)
                                            },
                                        )
                                    }
                                },
                            )
                            put("clear", false)
                        }.toString(),
                    )
                    signZhihuFetchRequest(cookies)
                }
            } ?: return false
            currentCoroutineContext().ensureActive()
            if (!isCurrentAccount(environment, generation, client)) return false
            check(response.status.isSuccess()) { "删除在线历史记录失败: ${response.status}" }
            displayItems.removeAll { it.stableKey in keys }
            for (key in keys) deletionPairs.remove(key)
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!isCurrentAccount(environment, generation, client)) return false
            throw e
        }
    }

    suspend fun clearAllHistory(environment: PaginationEnvironment): Boolean {
        val client = accountClient ?: return false
        val generation = accountGeneration
        if (!isCurrentAccount(environment, generation, client)) return false
        try {
            val response = environment.withAuthenticatedClient { requestClient, cookies ->
                if (requestClient !== client || !isCurrentAccount(environment, generation, client)) return@withAuthenticatedClient null
                client.post("https://api.zhihu.com/read_history/batch_del") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        buildJsonObject {
                            put("pairs", buildJsonArray {})
                            put("clear", true)
                        }.toString(),
                    )
                    signZhihuFetchRequest(cookies)
                }
            } ?: return false
            currentCoroutineContext().ensureActive()
            if (!isCurrentAccount(environment, generation, client)) return false
            check(response.status.isSuccess()) { "清除在线历史记录失败: ${response.status}" }
            localHistory.clearAndSave()
            markCleared()
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!isCurrentAccount(environment, generation, client)) return false
            throw e
        }
    }
}
