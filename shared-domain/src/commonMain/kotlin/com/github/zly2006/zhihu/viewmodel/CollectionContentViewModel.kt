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

package com.github.zly2006.zhihu.viewmodel

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.ZhihuCollection
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.ZhihuPaging
import com.github.zly2006.zhihu.data.displayExcerpt
import com.github.zly2006.zhihu.data.navDestination
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.util.DisplayFailure
import kotlin.random.Random
import kotlin.reflect.typeOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray

suspend fun ZhihuApiEnvironment.fetchCollection(collectionId: String): ZhihuCollection {
    val json = fetchJson("https://www.zhihu.com/api/v4/collections/$collectionId", "") ?: throw DisplayFailure { DomainStrings.collectionMissingInfo }
    return ZhihuJson.decodeJson<ZhihuCollection>(json["collection"] ?: throw DisplayFailure { DomainStrings.collectionMissingInfo })
}

class CollectionContentViewModel(
    val collectionId: String,
) : PaginationViewModel<CollectionItem>(typeOf<CollectionItem>()) {
    private var randomPageOffsets: List<Int>? = null
    private var randomPageCursor = 0
    private var lastRandomFirstOffset: Int? = null
    private var activeRandomSeed: Int? = null
    private var activeRandomItemCount: Int? = null
    private val randomDisplayOrderKeys = mutableListOf<String>()
    val retainedRandomOrderKeys: List<String>
        get() = randomDisplayOrderKeys
    val isRandomMode: Boolean
        get() = randomPageOffsets != null
    val displayItems = mutableStateListOf<FeedDisplayItem>()
    var collection by mutableStateOf<ZhihuCollection?>(null)
    val title by derivedStateOf {
        collection?.title ?: DomainStrings.collectionTitle
    }
    var exportDialogState by mutableStateOf<CollectionHtmlExportDialogState?>(null)
        private set

    override val initialUrl: String
        get() = "https://www.zhihu.com/api/v4/collections/$collectionId/items"

    override val isEnd: Boolean
        get() = randomPageOffsets?.let { randomPageCursor >= it.size } ?: super.isEnd

    val nextPageUrl: String
        get() = lastPaging?.next.orEmpty()

    override suspend fun prepareResponse(environment: PaginationEnvironment, data: List<CollectionItem>, rawData: JsonArray): () -> Unit {
        val items = data.map(::createDisplayItem)
        val commit = super.prepareResponse(environment, data, rawData)
        return {
            if (isRefreshPending) displayItems.clear()
            commit()
            displayItems.addAll(items)
            if (randomPageOffsets != null) randomPageCursor++
        }
    }

    override fun resolvePageUrl(): String {
        val offset = randomPageOffsets?.getOrNull(randomPageCursor) ?: return super.resolvePageUrl()
        return "https://www.zhihu.com/api/v4/collections/$collectionId/items?offset=$offset&limit=$COLLECTION_PAGE_SIZE"
    }

    private fun createDisplayItem(item: CollectionItem): FeedDisplayItem = FeedDisplayItem(
        title = item.content.title,
        summary = item.content.displayExcerpt,
        details = item.content.displayDetails,
        destination = item.content.navDestination,
        feed = null,
        authorName = item.content.author?.name,
        avatarSrc = when (item.content) {
            is Feed.AnswerTarget -> item.content.author?.avatarUrl
            is Feed.ArticleTarget -> item.content.author.avatarUrl
            is Feed.QuestionTarget -> item.content.author?.avatarUrl
            else -> null
        },
    )

    override fun refresh(environment: PaginationEnvironment) {
        activeRandomSeed = null
        activeRandomItemCount = null
        randomDisplayOrderKeys.clear()
        randomPageOffsets = null
        randomPageCursor = 0
        super.refresh(environment)
    }

    fun refreshRandom(
        environment: PaginationEnvironment,
        itemCount: Int,
        randomSeed: Int,
    ) {
        if (
            shouldReuseCollectionRandomSession(
                activeRandomSeed = activeRandomSeed,
                activeRandomItemCount = activeRandomItemCount,
                requestedRandomSeed = randomSeed,
                requestedItemCount = itemCount,
                hasLoadedItems = displayItems.isNotEmpty(),
                isLoading = isLoading,
                isEnd = isEnd,
            )
        ) {
            return
        }

        activeRandomSeed = randomSeed
        activeRandomItemCount = itemCount
        randomDisplayOrderKeys.clear()
        val offsets = collectionRandomPageOffsets(
            itemCount = itemCount,
            randomSeed = randomSeed,
            previousFirstOffset = lastRandomFirstOffset ?: 0,
        )
        randomPageOffsets = offsets
        randomPageCursor = 0
        lastRandomFirstOffset = offsets.firstOrNull()
        super.refresh(environment)
    }

    fun retainRandomDisplayOrder(keys: List<String>) {
        randomDisplayOrderKeys.clear()
        randomDisplayOrderKeys.addAll(keys)
    }

    override suspend fun fetchFeeds(environment: PaginationEnvironment) {
        val generation = pagingGeneration
        val client = environment.httpClient()
        if (isRefreshPending || collection == null) {
            try {
                val loadedCollection = environment.fetchCollection(collectionId)
                currentCoroutineContext().ensureActive()
                if (generation != pagingGeneration || environment.httpClient() !== client) return
                collection = loadedCollection
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == pagingGeneration && environment.httpClient() === client) {
                    environment.handleFetchFailure(this::class.simpleName, e)
                    if (generation == pagingGeneration && environment.httpClient() === client) errorHandle(e)
                }
                return
            }
        }
        super.fetchFeeds(environment)
    }

    fun exportAllToHtmlZip(
        environment: CollectionContentEnvironment,
        includeImages: Boolean,
    ) {
        if (exportDialogState?.isCompleted == false) return

        viewModelScope.launch {
            exportDialogState = CollectionHtmlExportDialogState(
                phase = CollectionExportPhase.Loading,
                totalCount = 0,
                processedCount = 0,
                successCount = 0,
                skippedCount = 0,
                failedCount = 0,
                isIndeterminate = true,
            )

            try {
                val result = withExportAccount(environment) {
                    val items = ensureAllCollectionItemsLoaded(environment)
                    if (items.isEmpty()) {
                        exportDialogState = CollectionHtmlExportDialogState(
                            phase = CollectionExportPhase.Empty,
                            totalCount = 0,
                            processedCount = 0,
                            successCount = 0,
                            skippedCount = 0,
                            failedCount = 0,
                            isCompleted = true,
                        )
                        return@withExportAccount null
                    }
                    ensureExportAccountActive()
                    environment.exportCollectionItemsToHtmlZip(
                        collectionTitle = title,
                        items = items,
                        includeImages = includeImages,
                        onProgress = { progress ->
                            exportDialogState = CollectionHtmlExportDialogState(
                                phase = CollectionExportPhase.Exporting,
                                totalCount = progress.totalCount,
                                processedCount = progress.processedCount,
                                successCount = progress.successCount,
                                skippedCount = progress.skippedCount,
                                failedCount = progress.failedCount,
                                currentTitle = progress.currentTitle,
                            )
                        },
                    )
                } ?: return@launch
                if (result.delivery == ArticleExportResult.Cancelled) {
                    exportDialogState = null
                    return@launch
                }

                exportDialogState = CollectionHtmlExportDialogState(
                    phase = CollectionExportPhase.Complete,
                    totalCount = result.totalCount,
                    processedCount = result.totalCount,
                    successCount = result.successCount,
                    skippedCount = result.skippedCount,
                    failedCount = result.failedCount,
                    currentTitle = "",
                    isCompleted = true,
                    zipFilePath = result.zipFilePath,
                    delivery = result.delivery,
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                exportDialogState = CollectionHtmlExportDialogState(
                    phase = CollectionExportPhase.Failed,
                    totalCount = exportDialogState?.totalCount ?: 0,
                    processedCount = exportDialogState?.processedCount ?: 0,
                    successCount = exportDialogState?.successCount ?: 0,
                    skippedCount = exportDialogState?.skippedCount ?: 0,
                    failedCount = exportDialogState?.failedCount ?: 0,
                    currentTitle = exportDialogState?.currentTitle.orEmpty(),
                    isCompleted = true,
                    failure = e,
                )
                environment.handleCollectionExportFailure(e)
            }
        }
    }

    fun dismissExportDialog() {
        exportDialogState = null
    }

    private suspend fun ensureAllCollectionItemsLoaded(environment: CollectionContentEnvironment): List<CollectionItem> {
        if (collection == null) {
            collection = environment.fetchCollection(collectionId)
        }

        while (allData.isEmpty() || !isEnd) {
            val beforeCount = allData.size
            val beforePaging = lastPaging
            isLoading = true
            fetchFeeds(environment)

            val pagingAdvanced = hasPagingProgress(beforePaging, lastPaging)
            val itemCountAdvanced = allData.size > beforeCount
            check(isEnd || pagingAdvanced || itemCountAdvanced) { errorText?.invoke() ?: DomainStrings.exportFailed }
            if (isEnd) break
        }

        return allData.toList()
    }

    private fun hasPagingProgress(
        beforePaging: ZhihuPaging?,
        afterPaging: ZhihuPaging?,
    ): Boolean {
        if (afterPaging == null) return false
        if (beforePaging == null) return true
        return beforePaging.next != afterPaging.next || beforePaging.page != afterPaging.page || beforePaging.isEnd != afterPaging.isEnd
    }
}

fun collectionRandomPageOffsets(
    itemCount: Int,
    randomSeed: Int,
    previousFirstOffset: Int? = null,
    pageSize: Int = COLLECTION_PAGE_SIZE,
): List<Int> {
    require(pageSize > 0)
    val pageCount = ((itemCount.coerceAtLeast(1) + pageSize - 1) / pageSize)
    val offsets = (0 until pageCount)
        .map { page -> page * pageSize }
        .shuffled(Random(randomSeed))
        .toMutableList()
    if (offsets.size > 1 && offsets.first() == previousFirstOffset) {
        val replacementIndex = offsets.indexOfFirst { it != previousFirstOffset }
        val first = offsets.first()
        offsets[0] = offsets[replacementIndex]
        offsets[replacementIndex] = first
    }
    return offsets
}

fun shouldReuseCollectionRandomSession(
    activeRandomSeed: Int?,
    activeRandomItemCount: Int?,
    requestedRandomSeed: Int,
    requestedItemCount: Int,
    hasLoadedItems: Boolean,
    isLoading: Boolean,
    isEnd: Boolean,
): Boolean =
    activeRandomSeed == requestedRandomSeed &&
        activeRandomItemCount == requestedItemCount &&
        (hasLoadedItems || isLoading || isEnd)

private const val COLLECTION_PAGE_SIZE = 20

enum class CollectionExportPhase { Loading, Exporting, Empty, Complete, Failed }

@Stable
data class CollectionHtmlExportDialogState(
    val phase: CollectionExportPhase,
    val totalCount: Int,
    val processedCount: Int,
    val successCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val currentTitle: String = "",
    val isIndeterminate: Boolean = false,
    val isCompleted: Boolean = false,
    private val failure: Exception? = null,
    val zipFilePath: String? = null,
    val delivery: ArticleExportResult? = null,
) {
    val phaseText: String
        get() = when (phase) {
            CollectionExportPhase.Loading -> DomainStrings.exportLoading
            CollectionExportPhase.Exporting -> DomainStrings.exportProgress(processedCount, totalCount)
            CollectionExportPhase.Empty -> DomainStrings.exportEmpty
            CollectionExportPhase.Complete -> DomainStrings.exportComplete
            CollectionExportPhase.Failed -> DomainStrings.exportFailed
        }
    val resultMessage: String?
        get() = when (phase) {
            CollectionExportPhase.Empty -> DomainStrings.exportEmptyHint
            CollectionExportPhase.Failed -> failure?.message ?: DomainStrings.unknownError
            CollectionExportPhase.Complete -> when (val delivered = delivery) {
                is ArticleExportResult.Completed -> "${DomainStrings.exportTotals(successCount, skippedCount, failedCount)}\n${delivered.completionMessage}"
                ArticleExportResult.Cancelled -> null
                null -> if (zipFilePath != null) {
                    DomainStrings.exportTotals(successCount, skippedCount, failedCount)
                } else {
                    DomainStrings.exportNoArticles(skippedCount, failedCount)
                }
            }
            else -> null
        }
    val progress: Float
        get() = if (totalCount <= 0) 0f else (processedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
}
