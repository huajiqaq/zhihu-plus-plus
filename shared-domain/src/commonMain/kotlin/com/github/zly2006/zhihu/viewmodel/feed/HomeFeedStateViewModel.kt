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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.viewModelScope
import com.github.zly2006.zhihu.data.AdvertisementFeed
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.navDestination
import com.github.zly2006.zhihu.filter.RemoteHistorySync
import com.github.zly2006.zhihu.platform.platformSemanticRuntimeGeneration
import com.github.zly2006.zhihu.platform.platformSettingsStore
import com.github.zly2006.zhihu.util.AppLog
import com.github.zly2006.zhihu.viewmodel.FeedDisplaySettings
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.filter.ContentDetailProvider
import com.github.zly2006.zhihu.viewmodel.filter.ContentFilterManager
import com.github.zly2006.zhihu.viewmodel.filter.FeedDisplayFilterPipeline
import com.github.zly2006.zhihu.viewmodel.filter.ForegroundReadFilterPipeline
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import com.github.zly2006.zhihu.viewmodel.filter.readHomeFeedFilterPolicy
import com.github.zly2006.zhihu.viewmodel.filter.recheckHomeFeedItems
import com.github.zly2006.zhihu.viewmodel.filter.toFeedFilterSettings
import com.github.zly2006.zhihu.viewmodel.getOrFetchContentDetail
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray

/** 首页列表、规则版本和快照只由一个所有者提交；UI 只提供规则变化与可见边界。 */
abstract class HomeFeedStateViewModel(
    val remoteHistory: RemoteHistorySync = RemoteHistorySync(),
) : BaseFeedViewModel() {
    private val changes = Mutex()
    var filterPolicyRevision by mutableIntStateOf(0)
        private set
    private var checkedFingerprint: String? = null
    var snapshotRevision by mutableIntStateOf(0)
        private set
    var startupAttempted = false
    private val database get() = getContentFilterDatabase()

    init {
        var semanticGeneration = platformSemanticRuntimeGeneration.value
        viewModelScope.launch {
            platformSemanticRuntimeGeneration.collect { generation ->
                if (generation != semanticGeneration) {
                    semanticGeneration = generation
                    invalidateFilterPolicy()
                }
            }
        }
    }

    fun invalidateFilterPolicy() {
        filterPolicyRevision++
    }

    final override fun onFilterPolicyChanged() = invalidateFilterPolicy()

    final override fun onDisplayItemsChanged() {
        snapshotRevision++
    }

    protected fun requestIsCurrent(environment: PaginationEnvironment, generation: Int, client: HttpClient): Boolean =
        generation == pageGeneration && environment.accountClient?.isCurrent != false && environment.httpClient() === client

    /** 候选只参与显示；全部过滤成功后才一起提交确认列表、原始页和游标。 */
    protected suspend fun publishHomePage(
        environment: PaginationEnvironment,
        generation: Int,
        client: HttpClient,
        items: List<FeedDisplayItem>,
        data: List<Feed> = emptyList(),
        rawData: JsonArray = JsonArray(emptyList()),
        foreground: Boolean = false,
        detailProvider: ContentDetailProvider? = null,
        preview: (List<FeedDisplayItem>, Int) -> Unit = { _, _ -> },
        commitCursor: () -> Unit = {},
    ) {
        currentCoroutineContext().ensureActive()
        if (!requestIsCurrent(environment, generation, client)) return
        val contentItems = items.filter { it.feed != null || it.navDestination != null || it.filterFacts != null }
        if (foreground) {
            // 空屏不能排在另一来源的慢过滤、历史或规则数据库之后。
            if (committedDisplayItems.isEmpty()) {
                val displaySettings = FeedDisplaySettings(platformSettingsStore())
                if (!displaySettings.reverseBlock) {
                    preview(contentItems.filterNot { it.feed is AdvertisementFeed }.mapNotNull(displaySettings::applyQualityFilter), filterPolicyRevision)
                }
            }
            remoteHistory.awaitRecentPage()
        }
        changes.withLock {
            while (requestIsCurrent(environment, generation, client)) {
                val revision = filterPolicyRevision
                val settings = platformSettingsStore()
                val displaySettings = FeedDisplaySettings(settings)
                val filterSettings = settings.toFeedFilterSettings()
                val candidates = if (foreground) {
                    ForegroundReadFilterPipeline(filterSettings, database).filter(contentItems.mapNotNull(displaySettings::applyQualityFilter))
                } else contentItems
                currentCoroutineContext().ensureActive()
                if (!requestIsCurrent(environment, generation, client)) return@withLock
                if (revision != filterPolicyRevision) continue
                if (foreground) {
                    preview(if (displaySettings.reverseBlock) emptyList() else candidates.filterNot { it.feed is AdvertisementFeed }, revision)
                }
                val policy = readHomeFeedFilterPolicy(settings, database)
                val checked = FeedDisplayFilterPipeline(
                    settings = policy.filterSettings,
                    displaySettings = policy.displaySettings,
                    rules = policy.rules,
                    database = database,
                    onNlpBlocked = { blocked ->
                        if (requestIsCurrent(environment, generation, client) && revision == filterPolicyRevision) {
                            environment.showNlpBlockedMessage(blocked)
                        }
                    },
                    contentDetailProvider = detailProvider ?: ContentDetailProvider { destination ->
                        if (environment.isLoggedIn) environment.getOrFetchContentDetail(destination) else null
                    },
                ).filter(candidates)
                val contentKeys = contentItems.mapTo(hashSetOf()) { it.stableKey }
                val checkedByKey = checked.associateBy { it.stableKey }
                val filtered = items.mapNotNull { if (it.stableKey in contentKeys) checkedByKey[it.stableKey] else it }
                val original = committedDisplayItems.toList()
                val rechecked = if (!isRefreshPending && checkedFingerprint != policy.fingerprint) {
                    recheckHomeFeedItems(original, policy, database) { blocked ->
                        if (requestIsCurrent(environment, generation, client) && revision == filterPolicyRevision) {
                            environment.showNlpBlockedMessage(blocked)
                        }
                    }
                } else null
                currentCoroutineContext().ensureActive()
                if (!requestIsCurrent(environment, generation, client)) return@withLock
                if (revision != filterPolicyRevision || policy.semanticRuntimeGeneration != platformSemanticRuntimeGeneration.value) continue
                val existingKeys = if (isRefreshPending) emptySet() else original.mapTo(hashSetOf()) { it.stableKey }
                val newItems = filtered.distinctBy { it.stableKey }.filterNot { it.stableKey in existingKeys }
                Snapshot.withMutableSnapshot {
                    if (rechecked != null) applyRecheck(original, rechecked)
                    commitDisplayPage(filtered, data, rawData)
                    commitCursor()
                    if (checkedFingerprint != policy.fingerprint) snapshotRevision++
                    checkedFingerprint = policy.fingerprint
                }
                // 曝光只登记确认内容身份，重试、另一来源及缓存复检不能重复登记。
                if (foreground && policy.filterSettings.enableContentFilter && !policy.filterSettings.reverseBlock) {
                    val manager = ContentFilterManager(database.contentFilterDao())
                    for (item in newItems) {
                        if (!requestIsCurrent(environment, generation, client)) break
                        val identity = item.contentIdentity ?: continue
                        try {
                            manager.recordContentView(identity.type, identity.id)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            AppLog.e("HomeFeedViewModel", "Failed to record committed feed exposure", error)
                        }
                    }
                }
                return@withLock
            }
        }
    }

    /** 本地预热提示不是内容，不查询规则数据库，也不登记曝光。 */
    protected suspend fun publishHomeNotice(environment: PaginationEnvironment, generation: Int, client: HttpClient, items: List<FeedDisplayItem>) = changes.withLock {
        currentCoroutineContext().ensureActive()
        if (!requestIsCurrent(environment, generation, client)) return@withLock
        Snapshot.withMutableSnapshot {
            commitDisplayPage(items)
            checkedFingerprint = null
        }
    }

    suspend fun recheckFilterPolicy(environment: PaginationEnvironment) = changes.withLock {
        if (committedDisplayItems.isEmpty()) return@withLock
        val generation = pageGeneration
        val client = environment.httpClient()
        while (requestIsCurrent(environment, generation, client)) {
            val revision = filterPolicyRevision
            val policy = readHomeFeedFilterPolicy(platformSettingsStore(), database)
            if (checkedFingerprint == policy.fingerprint) return@withLock
            val original = committedDisplayItems.toList()
            val filtered = recheckHomeFeedItems(original, policy, database) { blocked ->
                if (requestIsCurrent(environment, generation, client) && revision == filterPolicyRevision) {
                    environment.showNlpBlockedMessage(blocked)
                }
            }
            currentCoroutineContext().ensureActive()
            if (!requestIsCurrent(environment, generation, client)) return@withLock
            if (revision != filterPolicyRevision || policy.semanticRuntimeGeneration != platformSemanticRuntimeGeneration.value) continue
            Snapshot.withMutableSnapshot {
                applyRecheck(original, filtered)
                checkedFingerprint = policy.fingerprint
                if (completedPageCount > 0 || original.isNotEmpty()) snapshotRevision++
            }
            return@withLock
        }
    }

    private fun applyRecheck(original: List<FeedDisplayItem>, filtered: List<FeedDisplayItem>) {
        val originalByKey = original.associateBy { it.stableKey }
        val filteredByKey = filtered.associateBy { it.stableKey }
        replaceDisplayItems(committedDisplayItems.mapNotNull { item ->
            if (originalByKey[item.stableKey] !== item) item else filteredByKey[item.stableKey]
        })
    }

    fun restoreStartupItems(items: List<FeedDisplayItem>, fingerprint: String) {
        if (isLoading || completedPageCount != 0 || displayItems.isNotEmpty()) return
        checkedFingerprint = fingerprint
        addDisplayItems(items)
    }

    fun startupSnapshot(): Pair<List<FeedDisplayItem>, String>? = checkedFingerprint?.let {
        committedDisplayItems.toList() to it
    }

    suspend fun removeRemoteReads(environment: PaginationEnvironment, visibleKeys: () -> Set<Any>) = changes.withLock {
        val generation = pageGeneration
        val client = environment.httpClient()
        val items = committedDisplayItems.toList()
        val lastVisible = items.indexOfLast { it.stableKey in visibleKeys() }
        val candidates = items.drop(lastVisible + 1)
        val keptKeys = remoteHistory.filterRemoteReads(candidates).mapTo(hashSetOf()) { it.stableKey }
        val removedKeys = candidates.filterNot { it.stableKey in keptKeys }.mapTo(hashSetOf()) { it.stableKey }
        currentCoroutineContext().ensureActive()
        if (!requestIsCurrent(environment, generation, client)) return@withLock
        val protectedEnd = committedDisplayItems.indexOfLast { it.stableKey in visibleKeys() }
        replaceDisplayItems(committedDisplayItems.filterIndexed { index, item ->
            index <= protectedEnd || item.stableKey !in removedKeys
        })
    }
}
