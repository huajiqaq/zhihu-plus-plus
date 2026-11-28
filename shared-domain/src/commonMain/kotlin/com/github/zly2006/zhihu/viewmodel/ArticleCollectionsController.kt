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

import androidx.compose.runtime.snapshots.SnapshotStateList
import com.github.zly2006.zhihu.data.ZhihuCollection
import com.github.zly2006.zhihu.data.ZhihuCollectionResponse
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.platform.UserMessageSink
import com.github.zly2006.zhihu.util.AppLog
import io.ktor.client.HttpClient
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 文章页的收藏夹请求：读取、收藏与取消、新建。列表本身仍由 [ArticleViewModel] 持有。 */
internal class ArticleCollectionsController(
    private val article: Article,
    private val httpClient: HttpClient?,
    private val userMessages: UserMessageSink,
    private val scope: () -> CoroutineScope,
    private val collectionsValue: () -> SnapshotStateList<ZhihuCollection>,
) {
    private val collections: SnapshotStateList<ZhihuCollection>
        get() = collectionsValue()

    /** 上一次展示的顺序：刷新后已有的收藏夹保持原位，新出现的排在最前。 */
    private val collectionOrder = mutableListOf<String>()

    fun toggleFavorite(collectionId: String, remove: Boolean, environment: ZhihuApiEnvironment) {
        if (httpClient == null) return
        scope().launch {
            try {
                val contentType = when (article.type) {
                    ArticleType.Answer -> "answer"
                    ArticleType.Article -> "article"
                }
                val action = if (remove) "remove" else "add"
                val url = "https://api.zhihu.com/collections/contents/$contentType/${article.id}"
                val body = "${action}_collections=$collectionId"

                val response = httpClient.put(url) {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(body)
                }

                if (response.status.isSuccess()) {
                    loadCollections(environment)
                    userMessages.showShortMessage(if (remove) DomainStrings.collectionItemRemoved else DomainStrings.collectionItemSaved)
                } else {
                    userMessages.showShortMessage(DomainStrings.collectionChangeFailed)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("ArticleViewModel", "Favorite toggle failed", e)
                userMessages.showShortMessage(DomainStrings.collectionChangeError(e.message.orEmpty()))
            }
        }
    }

    fun loadCollections(environment: ZhihuApiEnvironment) {
        if (httpClient == null) return
        scope().launch {
            withContext(Dispatchers.Default) {
                try {
                    val contentType = when (article.type) {
                        ArticleType.Answer -> "answer"
                        ArticleType.Article -> "article"
                    }
                    val collectionsUrl = "https://api.zhihu.com/collections/contents/$contentType/${article.id}?limit=50"
                    val jojo = environment.fetchJson(collectionsUrl, "") ?: return@withContext
                    val collectionsData = ZhihuJson.decodeJson<ZhihuCollectionResponse>(jojo)
                    collections.clear()
                    collections.addAll(
                        collectionsData.data
                            .sortedWith { a, b ->
                                val indexA = collectionOrder.indexOf(a.id)
                                val indexB = collectionOrder.indexOf(b.id)
                                when {
                                    indexA == -1 && indexB == -1 -> 0
                                    indexA == -1 -> -1
                                    indexB == -1 -> 1
                                    else -> indexA.compareTo(indexB)
                                }
                            },
                    )
                    collectionOrder.clear()
                    collectionOrder.addAll(collections.map { it.id })
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.e("ArticleViewModel", "Failed to load collections", e)
                }
            }
        }
    }

    fun createNewCollection(
        environment: ZhihuApiEnvironment,
        title: String,
        description: String,
        isPublic: Boolean,
    ) {
        if (httpClient == null) return
        scope().launch {
            environment.post("https://www.zhihu.com/api/v4/collections") {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("title", title)
                        put("description", description)
                        put("is_public", isPublic)
                    },
                )
            }
            loadCollections(environment)
        }
    }
}
