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

package com.github.zly2006.zhihu.viewmodel.comment

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.fleeksoft.ksoup.Ksoup
import com.github.zly2006.zhihu.account.ZhihuAccountClient
import com.github.zly2006.zhihu.data.CommentSortOrder
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.ZhihuPaging
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.CommentHolder
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.SegmentCommentHolder
import com.github.zly2006.zhihu.util.AppLog
import com.github.zly2006.zhihu.util.DisplayFailure
import com.github.zly2006.zhihu.util.SegmentHighlightSpan
import com.github.zly2006.zhihu.viewmodel.CommentItem
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class RootCommentViewModel(
    content: NavDestination,
    private val initialCommentId: String? = null,
    private val sentenceOnly: Boolean = false,
    /** 精确片段索引与正文快照同源，不是评论接口的引用 map。 */
    var commentSegments: () -> Map<String, SegmentHighlightSpan> = { emptyMap() },
) : BaseCommentViewModel(content) {
    @Serializable
    private data class Counts(
        val totalCounts: Int? = null,
        val segmentCommentCounts: Int? = null,
    )

    @Serializable
    private data class SegmentQuote(
        val content: String = "",
    )

    @Serializable
    private data class PageMetadata(
        val counts: Counts? = null,
        val segmentInfos: Map<String, SegmentQuote>? = null,
    )

    private var countClient: HttpClient? = null
    private var countAccount: ZhihuAccountClient? = null
    private var countRequest: Job? = null
    private var confirmedTotalCount by mutableStateOf<Int?>(null)
    private var confirmedSentenceCount by mutableStateOf<Int?>(null)

    var rootPageReady by mutableStateOf(false)
        private set

    val totalCommentCount: Int?
        get() = confirmedTotalCount.takeUnless { countAccount?.isCurrent == false }

    /** 只向当前会话提供官方计数；null 是未知，不是服务端的 0。 */
    val sentenceCommentCount: Int?
        get() = confirmedSentenceCount.takeUnless { countAccount?.isCurrent == false }

    var sentenceQuotes by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    override val supportsSorting: Boolean get() = !sentenceOnly && super.supportsSorting

    /** 评论 API 可能不给句评数；正文同源片段的正数可证明有句评，不能拿普通总数或仅有划线代替。 */
    val hasKnownSentenceComments: Boolean get() = knownCommentedSegments().isNotEmpty()

    private fun knownCommentedSegments(): Map<String, SegmentHighlightSpan> {
        val target = article as? Article ?: return emptyMap()
        return commentSegments().filter { (id, span) ->
            id.isNotBlank() &&
                id in span.meta.segIds &&
                span.meta.commentCount > 0 &&
                span.contentId == target.id.toString() &&
                span.contentType?.removeSuffix("s") == target.type.toString()
        }
    }

    /** null 为全回答列表；MAX 仅表示正文已知片段子集读完。 */
    private var segmentFallbackCursor by mutableStateOf<Int?>(null)
    val isUsingSegmentFallback: Boolean
        get() = segmentFallbackCursor != null
    private var segmentFallbackIds: Set<String> = emptySet()

    override val isEnd: Boolean
        get() = commentClosedMessage != null ||
            if (isUsingSegmentFallback) {
                segmentFallbackCursor == Int.MAX_VALUE
            } else {
                super.isEnd
            }

    internal override fun resetForRefresh() {
        super.resetForRefresh()
        segmentFallbackCursor = null
        segmentFallbackIds = emptySet()
        countRequest?.cancel()
        countRequest = null
        countClient = null
        countAccount = null
        confirmedTotalCount = null
        confirmedSentenceCount = null
    }

    /** 全回答终止空页仅回退正文已知片段，不推断服务端原因或全量。 */
    private suspend fun fetchFallbackSegments(environment: PaginationEnvironment) {
        var cursor = segmentFallbackCursor ?: return
        val generation = pagingGeneration
        val client = environment.httpClient()
        val visited = mutableSetOf<String>()
        while (cursor < segmentFallbackIds.size) {
            val segId = segmentFallbackIds.elementAt(cursor)
            val url = lastPaging?.next ?: (
                article.rootCommentUrl.removeSuffix("/root_comment") +
                    "/segment/root_comment?segment_id=${segId.encodeURLParameter()}&limit=20&offset=&order_by=$orderBy"
            )
            if (!visited.add(url)) throw DisplayFailure { DomainStrings.segmentStalled }
            val json = environment.fetchJson(url, include) ?: throw DisplayFailure { DomainStrings.segmentCommentsFailed }
            currentCoroutineContext().ensureActive()
            if (generation != pagingGeneration || environment.httpClient() !== client) return
            val data = json["data"] as? JsonArray ?: throw DisplayFailure { DomainStrings.segmentMissingData }
            val rawPaging = json["paging"] as? JsonObject ?: throw DisplayFailure { DomainStrings.segmentMissingPaging }
            val terminal = rawPaging["is_end"] == JsonPrimitive(true)
            val paging = ZhihuJson.decodeJson<ZhihuPaging>(
                if (terminal && "next" !in rawPaging) JsonObject(rawPaging + ("next" to JsonPrimitive(""))) else rawPaging,
            )
            if (!paging.isEnd && (paging.next.isBlank() || paging.next == url)) throw DisplayFailure { DomainStrings.segmentStalled }
            val decoded = decodePage(environment, data)
            val previousCount = if (isRefreshPending) 0 else allData.size
            currentCoroutineContext().ensureActive()
            if (generation != pagingGeneration || environment.httpClient() !== client) return
            commitPage(environment, decoded, data, metadata = json) {
                if (paging.isEnd) cursor++
                lastPaging = paging.takeUnless { it.isEnd }
                segmentFallbackCursor = if (cursor >= segmentFallbackIds.size) Int.MAX_VALUE else cursor
            }
            if (allData.size > previousCount || isEnd) return
        }
        segmentFallbackCursor = Int.MAX_VALUE
    }

    override fun preparePageMetadata(json: JsonObject): () -> Unit {
        val metadata = ZhihuJson.decodeJson<PageMetadata>(json)
        val quotes = metadata.segmentInfos?.mapValues { Ksoup.parseBodyFragment(it.value.content).text() }
        val commit = super.preparePageMetadata(json)
        return {
            commit()
            if (!sentenceOnly) metadata.counts?.totalCounts?.takeIf { it >= 0 }?.let { confirmedTotalCount = it }
            if (!sentenceOnly && article is Article) {
                metadata.counts?.segmentCommentCounts?.takeIf { it >= 0 }?.let {
                    countRequest?.cancel()
                    confirmedSentenceCount = it
                }
            }
            if (quotes != null) sentenceQuotes = sentenceQuotes + quotes
        }
    }

    override suspend fun prepareResponse(environment: PaginationEnvironment, data: List<DataHolder.Comment>, rawData: JsonArray): () -> Unit {
        val commit = super.prepareResponse(environment, data, rawData)
        return {
            if (isRefreshPending) sentenceQuotes = emptyMap()
            commit()
        }
    }

    private var initialCommentLoaded = false
    var initialChildComment by mutableStateOf<DataHolder.Comment?>(null)
        private set

    internal data class ResolvedCommentAnchor(
        val target: DataHolder.Comment,
        val root: DataHolder.Comment,
    )

    companion object {
        val NavDestination.submitCommentUrl: String
            get() = when (this) {
                is Article -> {
                    when (type) {
                        ArticleType.Answer -> "https://www.zhihu.com/api/v4/comment_v5/answers/$id/comment"
                        ArticleType.Article -> "https://www.zhihu.com/api/v4/comment_v5/articles/$id/comment"
                    }
                }

                is Pin -> {
                    "https://www.zhihu.com/api/v4/comment_v5/pins/$id/comment"
                }

                is Question -> {
                    "https://www.zhihu.com/api/v4/comment_v5/questions/$questionId/comment"
                }

                is SegmentCommentHolder -> {
                    "https://www.zhihu.com/api/v4/comment_v5/${normalizedContentType}s/$contentId/segment/comment"
                }

                else -> ""
            }

        val NavDestination.rootCommentUrl: String
            get() = when (this) {
                is Article -> {
                    when (type) {
                        ArticleType.Answer -> "https://www.zhihu.com/api/v4/comment_v5/answers/$id/root_comment"
                        ArticleType.Article -> "https://www.zhihu.com/api/v4/comment_v5/articles/$id/root_comment"
                    }
                }

                is Pin -> {
                    "https://www.zhihu.com/api/v4/comment_v5/pins/$id/root_comment"
                }

                is Question -> {
                    "https://www.zhihu.com/api/v4/comment_v5/questions/$questionId/root_comment"
                }

                is SegmentCommentHolder -> {
                    "https://www.zhihu.com/api/v4/comment_v5/${normalizedContentType}s/$contentId/segment/root_comment?segment_id=$segmentId&limit=20&offset="
                }

                else -> ""
            }

        private val SegmentCommentHolder.normalizedContentType: String
            get() = contentType.removeSuffix("s")
    }

    private val orderBy: String
        get() = if (!supportsSorting || sortOrder == CommentSortOrder.SCORE) "score" else "ts"

    private fun multiSegmentUrl(limit: Int): String = article.rootCommentUrl.removeSuffix("/root_comment") +
        "/segment_comment?order_by=score&limit=$limit&offset="

    override val initialUrl: String
        get() {
            if (sentenceOnly) return multiSegmentUrl(20)
            val baseUrl = article.rootCommentUrl
            val separator = if ('?' in baseUrl) "&" else "?"
            return "$baseUrl${separator}order_by=$orderBy"
        }

    override fun loadMore(environment: PaginationEnvironment) {
        if (!sentenceOnly) {
            val client = environment.httpClient()
            if (countClient != null && countClient !== client) {
                rootPageReady = false
                resetForRefresh()
            }
            countClient = client
            countAccount = environment.accountClient
        }
        super.loadMore(environment)
    }

    /**
     * Web 普通根列表优先取 counts.segment_comment_counts（0 也有效）。缺字段时，按知乎 Android 11.12.0
     * 的 Amadeus 评论模块读取同一内容的 multiSegment 根列表（segment_comment）：此模式的 counts.total_counts 即句评数。
     * 普通或单段列表的 total_counts、paging.totals、返回行数及正文划线不能替代它。两处均未给出计数时保持未知；
     * 补读只取计数，不消费句评列表、引用或游标。
     */
    private suspend fun fetchMultiSegmentCount(environment: PaginationEnvironment): Int? {
        val response = environment.get(multiSegmentUrl(limit = 1))
        if (!response.status.isSuccess()) return null
        val json = response.body<JsonObject>()
        if (json["error"] != null && json["error"] != JsonNull) return null
        val counts = json["counts"] as? JsonObject ?: return null
        return ZhihuJson.decodeJson<Counts>(counts).totalCounts?.takeIf { it >= 0 }
    }

    override suspend fun fetchFeeds(environment: PaginationEnvironment) {
        if (sentenceOnly) {
            if (isUsingSegmentFallback) {
                fetchFallbackSegments(environment)
                return
            }
            val generation = pagingGeneration
            val client = environment.httpClient()
            val url = resolvePageUrl()
            val json = environment.fetchJson(url, include) ?: throw DisplayFailure { DomainStrings.segmentCommentsFailed }
            currentCoroutineContext().ensureActive()
            if (generation != pagingGeneration || environment.httpClient() !== client) return
            /** 全回答终止空态使用 data=null。 */
            val data = when (val value = json["data"]) {
                JsonNull -> JsonArray(emptyList())
                is JsonArray -> value
                else -> throw DisplayFailure { DomainStrings.segmentMissingData }
            }
            val rawPaging = json["paging"] as? JsonObject ?: throw DisplayFailure { DomainStrings.segmentMissingPaging }
            val terminal = rawPaging["is_end"] == JsonPrimitive(true)
            val paging = ZhihuJson.decodeJson<ZhihuPaging>(
                if (terminal && "next" !in rawPaging) JsonObject(rawPaging + ("next" to JsonPrimitive(""))) else rawPaging,
            )
            if (!paging.isEnd && (paging.next.isBlank() || paging.next == url)) throw DisplayFailure { DomainStrings.segmentStalled }
            val knownIds = knownCommentedSegments().keys
            if (data.isEmpty() && paging.isEnd && lastPaging == null && (!hasLoaded || isRefreshPending) && knownIds.isNotEmpty()) {
                segmentFallbackIds = knownIds
                segmentFallbackCursor = 0
                fetchFallbackSegments(environment)
                return
            }
            val decoded = decodePage(environment, data)
            currentCoroutineContext().ensureActive()
            if (generation != pagingGeneration || environment.httpClient() !== client) return
            commitPage(environment, decoded, data, paging, json)
            return
        }
        val firstPage = lastPaging == null
        val generation = pagingGeneration
        val client = environment.httpClient()
        val account = environment.accountClient

        fun isCurrentPage() = generation == pagingGeneration && account?.isCurrent != false && environment.httpClient() === client
        if (article is Article && countClient !== client) return
        if (!initialCommentLoaded && initialCommentId != null) {
            initialCommentLoaded = true
            try {
                /** 列表忽略 anchor_comment_id，先由详情定位根评论；https://github.com/zly2006/zhihu-plus-plus/issues/569 */
                val resolvedAnchor = resolveCommentAnchor(initialCommentId, environment)
                currentCoroutineContext().ensureActive()
                if (!isCurrentPage()) return
                initialChildComment = resolvedAnchor.target.takeIf { it.id != resolvedAnchor.root.id }
                commitPage(
                    environment = environment,
                    data = listOf(resolvedAnchor.root),
                    rawData = JsonArray(emptyList()),
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                AppLog.e("RootCommentViewModel", "Failed to resolve comment anchor", error)
            }
        }
        currentCoroutineContext().ensureActive()
        if (!isCurrentPage()) return
        super.fetchFeeds(environment)
        if (firstPage && hasLoaded && !isLoading && errorMessage == null && isCurrentPage()) rootPageReady = true
        if (firstPage &&
            article is Article &&
            confirmedSentenceCount == null &&
            !hasKnownSentenceComments &&
            countRequest == null &&
            hasLoaded &&
            errorMessage == null &&
            commentClosedMessage == null &&
            isCurrentPage()
        ) {
            /** 普通分页不等待补读；每轮最多一次，新一轮或普通页的明确计数会取消它。 */
            countRequest = viewModelScope.launch {
                if (!isCurrentPage()) return@launch
                val count = try {
                    fetchMultiSegmentCount(environment)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    return@launch
                }
                currentCoroutineContext().ensureActive()
                if (isCurrentPage() && confirmedSentenceCount == null) confirmedSentenceCount = count
            }
        }
    }

    internal suspend fun resolveCommentAnchor(
        commentId: String,
        environment: ZhihuApiEnvironment,
    ): ResolvedCommentAnchor {
        val target = environment
            .fetchJson(
                "https://www.zhihu.com/api/v4/comment_v5/comment/$commentId",
                "",
            )?.let { ZhihuJson.decodeJson<DataHolder.Comment>(it) }
            ?: error("Comment $commentId was not found")
        val rootCommentId = target.replyRootCommentId
            ?.takeIf { it.isNotBlank() && it != target.id }
            ?: return ResolvedCommentAnchor(target = target, root = target)
        val root = environment
            .fetchJson(
                "https://www.zhihu.com/api/v4/comment_v5/comment/$rootCommentId",
                "",
            )?.let { ZhihuJson.decodeJson<DataHolder.Comment>(it) }
            ?: error("Root comment $rootCommentId was not found")
        return ResolvedCommentAnchor(target = target, root = root)
    }

    override fun createCommentItem(comment: DataHolder.Comment, article: NavDestination): CommentItem {
        val segmentId = comment.resourceId.takeIf { comment.resourceType == "segment" }
        val span = segmentId?.let { commentSegments()[it] }
        val target = if (span != null &&
            span.contentId != null &&
            span.contentType != null &&
            span.paragraphId != null &&
            span.startOffset != null &&
            span.endOffset != null
        ) {
            SegmentCommentHolder(
                contentId = span.contentId,
                contentType = span.contentType,
                segmentId = segmentId,
                segmentContent = span.text,
                paragraphId = span.paragraphId,
                startOffset = span.startOffset,
                endOffset = span.endOffset,
            )
        } else {
            article
        }
        val clickTarget = CommentHolder(comment.id, target)
        val commentItem = CommentItem(comment, clickTarget)
        return commentItem
    }
}

data class CommentImageAttachment(
    val url: String,
    val width: Int,
    val height: Int,
    val isGif: Boolean = false,
)

data class CommentMention(
    val userId: String,
    val name: String,
    val urlToken: String,
)

internal fun NavDestination.buildSubmitCommentBody(
    commentText: String,
    replyToCommentId: String?,
    images: List<CommentImageAttachment> = emptyList(),
    stickers: List<CommentSticker> = emptyList(),
    mentions: List<CommentMention> = emptyList(),
    syncToPin: Boolean = false,
    voiceId: String? = null,
): JsonObject {
    var escapedText = commentText.escapeCommentHtml()
    mentions.forEach { mention ->
        val display = "@${mention.name} ".escapeCommentHtml()
        val mentionHtml = "<a data-hash=\"${mention.userId.escapeCommentHtml()}\" " +
            "href=\"/people/${mention.urlToken.escapeCommentHtml()}\" " +
            "class=\"member_mention\">$display</a>"
        escapedText = escapedText.replaceFirst(display, mentionHtml)
    }
    val contentHtml = buildString {
        if (escapedText.isNotBlank()) append("<p>$escapedText</p>")
        images.forEach { image ->
            val safeUrl = image.url.replace("\"", "&quot;")
            val imageClass = if (image.isGif) "comment_gif" else "comment_img"
            val imageLabel = if (image.isGif) "查看动图" else "查看图片"
            append(
                "<a href=\"$safeUrl\" class=\"$imageClass\" " +
                    "data-width=\"${image.width}\" data-height=\"${image.height}\">$imageLabel</a>",
            )
        }
        stickers.forEach { sticker ->
            append(commentStickerHtml(sticker))
        }
    }
    return buildJsonObject {
        val isSegment = this@buildSubmitCommentBody is SegmentCommentHolder
        put("comment_id", "")
        put("content", contentHtml)
        put("extra_params", "")
        put("has_img", images.isNotEmpty())
        put("reply_comment_id", replyToCommentId.orEmpty())
        put("score", 0)
        if (!isSegment) {
            putJsonArray("selected_settings") {
                if (syncToPin) add(JsonPrimitive("publish_sync_pin"))
            }
            put("unfriendly_check", "strict")
        }
        val stickerType = stickers.lastOrNull()?.let { if (it.isVipSticker) "vip" else "normal" }
        put("sticker_type", stickerType?.let { JsonArray(listOf(JsonPrimitive(it))) } ?: JsonNull)
        put("voice_info", voiceId?.let { id -> buildJsonObject { put("voice_id", id) } } ?: JsonNull)
        if (isSegment) {
            put(
                "segment",
                buildJsonObject {
                    put("segment_id", segmentId)
                    if (segmentId.isEmpty()) {
                        if (segmentContent.length < 5) throw DisplayFailure { DomainStrings.segmentTooShort }
                        put("content", segmentContent)
                        put(
                            "position",
                            buildJsonObject {
                                put(
                                    "start",
                                    buildJsonObject {
                                        put("offset", startOffset)
                                        put("paragraph_id", paragraphId)
                                    },
                                )
                                put(
                                    "end",
                                    buildJsonObject {
                                        put("offset", endOffset)
                                        put("paragraph_id", paragraphId)
                                    },
                                )
                            },
                        )
                    }
                },
            )
        } else {
            put("segment", JsonNull)
        }
    }
}

internal fun String.escapeCommentHtml(): String =
    buildString(length) {
        for (char in this@escapeCommentHtml) {
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(char)
            }
        }
    }
