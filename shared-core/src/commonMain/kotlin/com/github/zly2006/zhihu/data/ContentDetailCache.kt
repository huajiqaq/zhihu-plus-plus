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

package com.github.zly2006.zhihu.data

import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.util.AppLog
import io.ktor.client.HttpClient
import kotlin.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** 详情含账号关系和权限，缓存随客户端身份隔离。 */
object ContentDetailCache {
    private data class CacheEntry(
        val content: DataHolder.Content,
        val timestamp: Long,
    )

    private data class CacheKey(
        val owner: HttpClient,
        val contentType: String,
        val contentId: String,
    )

    private val cache = mutableMapOf<CacheKey, CacheEntry>()
    private val mutex = Mutex()
    private const val CACHE_EXPIRY_MS = 10 * 60 * 1000L
    private const val MAX_CACHE_SIZE = 100

    suspend fun getOrFetch(
        owner: HttpClient,
        navDestination: NavDestination,
        fetcher: suspend (NavDestination) -> DataHolder.Content?,
    ): DataHolder.Content? {
        val (contentType, contentId) = extractContentInfo(navDestination) ?: return null
        val key = CacheKey(owner, contentType, contentId)

        mutex.withLock {
            cache[key]?.let { entry ->
                if (Clock.System.now().toEpochMilliseconds() - entry.timestamp < CACHE_EXPIRY_MS) {
                    AppLog.d("ContentDetailCache", "Cache hit for $contentType:$contentId")
                    return entry.content
                } else {
                    cache.remove(key)
                }
            }
        }

        AppLog.d("ContentDetailCache", "Cache miss for $contentType:$contentId, fetching...")
        val content = fetcher(navDestination) ?: return null

        mutex.withLock {
            if (cache.size >= MAX_CACHE_SIZE) {
                val oldestKey = cache.entries.minByOrNull { it.value.timestamp }?.key
                oldestKey?.let { cache.remove(it) }
            }
            cache[key] = CacheEntry(content, Clock.System.now().toEpochMilliseconds())
        }

        return content
    }

    private fun extractContentInfo(navDestination: NavDestination): Pair<String, String>? = when (navDestination) {
        is Article -> {
            val type = when (navDestination.type) {
                ArticleType.Answer -> "answer"
                ArticleType.Article -> "article"
            }
            Pair(type, navDestination.id.toString())
        }
        is Question -> {
            Pair("question", navDestination.questionId.toString())
        }
        is Pin -> {
            Pair("pin", navDestination.id.toString())
        }
        else -> null
    }
}

fun zhihuContentDetailUrl(destination: NavDestination): String? = when (destination) {
    is Article -> when (destination.type) {
        ArticleType.Article -> "https://www.zhihu.com/api/v4/articles/${destination.id}"
        ArticleType.Answer -> "https://www.zhihu.com/api/v4/answers/${destination.id}"
    }

    is Question -> "https://www.zhihu.com/api/v4/questions/${destination.questionId}"
    is Pin -> "https://www.zhihu.com/api/v4/pins/${destination.id}"
    else -> null
}

fun zhihuContentDetailInclude(destination: NavDestination): String = when (destination) {
    is Article -> when (destination.type) {
        ArticleType.Article -> "content,topics,paid_info,can_comment,excerpt,thanks_count,voteup_count,comment_count,visited_count,relationship,ip_info,relationship.vote,author.badge_v2"
        ArticleType.Answer -> ".settings,content,editable_content,paid_info,can_comment,excerpt,thanks_count,voteup_count,comment_count,visited_count,attachment,reaction,ip_info,pagination_info,endorsements,question.topics,question.author,reaction.relation.voting,author.badge_v2,settings.table_of_contents.enabled"
    }

    is Question -> "read_count,visit_count,answer_count,voteup_count,comment_count,follower_count,detail,excerpt,author,relationship.is_following,topics"
    is Pin -> "topics"
    else -> ""
}

suspend fun fetchZhihuContentDetail(
    destination: NavDestination,
    fetchJson: suspend (String, String) -> JsonObject?,
): DataHolder.Content? {
    val url = zhihuContentDetailUrl(destination) ?: return null
    val include = zhihuContentDetailInclude(destination)
    val json = fetchJson(url, include) ?: return null

    return when (destination) {
        is Article -> decodeArticleContentDetail(destination, json)
        is Question -> decodeQuestionContentDetail(json)
        is Pin -> decodePinContentDetail(json)
        else -> null
    }
}

fun decodeArticleContentDetail(
    article: Article,
    json: JsonObject,
): DataHolder.Content {
    val normalizedJson = normalizeLongIdContentDetailJson(json)
    return when (article.type) {
        ArticleType.Answer -> ZhihuJson.decodeJson<DataHolder.Answer>(normalizedJson)
        ArticleType.Article -> ZhihuJson.decodeJson<DataHolder.Article>(normalizedJson)
    }
}

fun decodeQuestionContentDetail(json: JsonObject): DataHolder.Question =
    ZhihuJson.decodeJson(normalizeLongIdContentDetailJson(json))

fun decodePinContentDetail(json: JsonObject): DataHolder.Pin =
    ZhihuJson.decodeJson(json)

private fun normalizeLongIdContentDetailJson(jo: JsonObject): JsonObject = buildJsonObject {
    jo.entries.forEach { (key, value) ->
        if (key == "id") {
            put(key, JsonPrimitive(value.jsonPrimitive.long))
        } else {
            put(key, value)
        }
    }
}
