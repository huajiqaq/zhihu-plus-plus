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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.zly2006.zhihu.reading

import com.fleeksoft.ksoup.Ksoup
import com.github.zly2006.zhihu.data.decodeZhihuCommentData
import com.github.zly2006.zhihu.i18n.ReadingStrings
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.getOrFetchContentDetail
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 借用宿主的账号环境，不另建客户端；一次加载不能跨账户拼接正文和评论。 */
internal class ReadingContentResolver(private val environment: PaginationEnvironment) {
    suspend fun resolve(item: ReadingQueueItem, preferences: ReadingPreferences): Pair<ReadingQueueItem, ResolvedReadingContent> {
        val owner = environment.accountClient
        fun requireOwner() {
            if (environment.accountClient !== owner || owner?.isCurrent == false) {
                throw CancellationException("Reading account changed")
            }
        }
        requireOwner()
        val resolved = if (item.bodyHtml != null) {
            item
        } else {
            val content = environment.getOrFetchContentDetail(item.toDestination()) ?: error(ReadingStrings.contentLoadFailed)
            content.toReadingQueueItem(item.toDestination()) ?: error(ReadingStrings.unsupportedContentType)
        }
        requireOwner()
        val comments = if (preferences.shouldLoadComments) fetchComments(resolved, preferences, ::requireOwner) else emptyList()
        requireOwner()
        return resolved to ResolvedReadingContent(
            contentType = resolved.contentType,
            title = resolved.title,
            author = resolved.author,
            body = htmlToReadingText(resolved.bodyHtml.orEmpty()),
            publishedAt = resolved.publishedAt,
            voteUpCount = resolved.voteUpCount,
            comments = comments,
            updatedAt = resolved.updatedAt,
        )
    }

    private suspend fun fetchComments(
        item: ReadingQueueItem,
        preferences: ReadingPreferences,
        requireOwner: () -> Unit,
    ): List<ReadingComment> {
        val resource = when (item.contentType) {
            ReadingContentType.Answer -> "answers"
            ReadingContentType.Article -> "articles"
            ReadingContentType.Pin -> "pins"
            ReadingContentType.Question -> "questions"
        }
        val baseUrl = "https://www.zhihu.com/api/v4/comment_v5/$resource/${item.id}/root_comment"
        val comments = mutableListOf<ReadingComment>()
        val blockedUserIds = environment.blockedUserIds()
        var nextUrl: String? = "$baseUrl?order_by=${preferences.commentOrder.apiValue}&limit=${minOf(20, preferences.commentCount)}"
        val visited = mutableSetOf<String>()
        while (nextUrl != null && comments.size < preferences.commentCount && visited.add(nextUrl)) {
            requireOwner()
            val response = environment.fetchJson(nextUrl, include = "data[*].content,author") ?: break
            requireOwner()
            comments += decodeZhihuCommentData(response, Int.MAX_VALUE)
                .filterNot { it.author.id in blockedUserIds }
                .map { ReadingComment(it.author.name, Ksoup.parse(it.content).text()) }
                .take(preferences.commentCount - comments.size)
            val paging = response["paging"]?.jsonObject
            nextUrl = if (paging?.get("is_end")?.jsonPrimitive?.booleanOrNull == true) null else paging?.get("next")?.jsonPrimitive?.contentOrNull
        }
        return comments.take(preferences.commentCount)
    }
}
