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
import com.github.zly2006.zhihu.data.CommentSortOrder
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.CommentHolder
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.SegmentCommentHolder
import com.github.zly2006.zhihu.util.Log
import com.github.zly2006.zhihu.util.jsonObject
import com.github.zly2006.zhihu.util.signZhihuFetchRequest
import com.github.zly2006.zhihu.viewmodel.CommentItem
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
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
    var commentSegments: () -> Map<String, com.github.zly2006.zhihu.util.SegmentHighlightSpan> = { emptyMap() },
) : BaseCommentViewModel(content) {
    @Serializable
    private data class Counts(
        val segmentCommentCounts: Int = 0,
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

    var sentenceCommentCount by mutableStateOf(0)
        private set
    var sentenceQuotes by mutableStateOf<Map<String, String>>(emptyMap())
        private set

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
    }

    /** 全回答终止空页仅回退正文已知片段，不推断服务端原因或全量。 */
    private suspend fun fetchFallbackSegments(environment: PaginationEnvironment) {
        var cursor = segmentFallbackCursor ?: return
        val generation = pagingGeneration
        val client = environment.httpClient()
        val visited = mutableSetOf<String>()
        while (cursor < segmentFallbackIds.size) {
            val segId = segmentFallbackIds.elementAt(cursor)
            val order = if (sortOrder == CommentSortOrder.SCORE) "score" else "ts"
            val url = lastPaging?.next ?: (
                article.rootCommentUrl.removeSuffix("/root_comment") +
                    "/segment/root_comment?segment_id=${segId.encodeURLParameter()}&limit=20&offset=&order_by=$order"
            )
            check(visited.add(url)) { "句子评论分页未前进，请重试" }
            val json = environment.fetchJson(url, include) ?: error("句子评论加载失败，请重试")
            currentCoroutineContext().ensureActive()
            if (generation != pagingGeneration || environment.httpClient() !== client) return
            val data = json["data"] as? JsonArray ?: error("句子评论响应缺少 data")
            val rawPaging = json["paging"] as? JsonObject ?: error("句子评论响应缺少 paging")
            val terminal = rawPaging["is_end"] == JsonPrimitive(true)
            val paging = ZhihuJson.decodeJson<com.github.zly2006.zhihu.data.ZhihuPaging>(
                if (terminal && "next" !in rawPaging) JsonObject(rawPaging + ("next" to JsonPrimitive(""))) else rawPaging,
            )
            check(paging.isEnd || (paging.next.isNotBlank() && paging.next != url)) { "句子评论分页未前进，请重试" }
            val decoded = decodePage(environment, data)
            ZhihuJson.decodeJson<PageMetadata>(json)
            val previousCount = if (isRefreshPending) 0 else allData.size
            processResponse(environment, decoded, data)
            handlePageMetadata(json)
            if (paging.isEnd) cursor++
            lastPaging = paging.takeUnless { it.isEnd }
            segmentFallbackCursor = if (cursor >= segmentFallbackIds.size) Int.MAX_VALUE else cursor
            if (allData.size > previousCount || isEnd) return
        }
        segmentFallbackCursor = Int.MAX_VALUE
    }

    override fun handlePageMetadata(json: JsonObject) {
        val metadata = ZhihuJson.decodeJson<PageMetadata>(json)
        super.handlePageMetadata(json)
        if (!sentenceOnly) metadata.counts?.let { sentenceCommentCount = it.segmentCommentCounts }
        metadata.segmentInfos?.let { quotes ->
            sentenceQuotes = sentenceQuotes + quotes.mapValues {
                com.fleeksoft.ksoup.Ksoup
                    .parseBodyFragment(it.value.content)
                    .text()
            }
        }
    }

    override fun processResponse(environment: PaginationEnvironment, data: List<DataHolder.Comment>, rawData: JsonArray) {
        if (isRefreshPending) sentenceQuotes = emptyMap()
        super.processResponse(environment, data, rawData)
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

    override val initialUrl: String
        get() {
            val baseUrl = if (sentenceOnly) {
                article.rootCommentUrl.removeSuffix("/root_comment") + "/segment_comment"
            } else {
                article.rootCommentUrl
            }
            val orderParam = when (sortOrder) {
                CommentSortOrder.SCORE -> "score"
                CommentSortOrder.TIME -> "ts"
            }
            val separator = if ('?' in baseUrl) "&" else "?"
            return "$baseUrl${separator}order_by=$orderParam" + if (sentenceOnly) "&limit=20&offset=" else ""
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
            val json = environment.fetchJson(url, include) ?: error("句子评论加载失败，请重试")
            currentCoroutineContext().ensureActive()
            if (generation != pagingGeneration || environment.httpClient() !== client) return
            /** 全回答终止空态使用 data=null。 */
            val data = when (val value = json["data"]) {
                JsonNull -> JsonArray(emptyList())
                is JsonArray -> value
                else -> error("句子评论响应缺少 data")
            }
            val rawPaging = json["paging"] as? JsonObject ?: error("句子评论响应缺少 paging")
            val terminal = rawPaging["is_end"] == JsonPrimitive(true)
            val paging = ZhihuJson.decodeJson<com.github.zly2006.zhihu.data.ZhihuPaging>(
                if (terminal && "next" !in rawPaging) JsonObject(rawPaging + ("next" to JsonPrimitive(""))) else rawPaging,
            )
            check(paging.isEnd || (paging.next.isNotBlank() && paging.next != url)) { "句子评论分页未前进，请重试" }
            val knownIds = commentSegments().filterValues { it.meta.commentCount > 0 }.keys
            if (data.isEmpty() && paging.isEnd && lastPaging == null && (!hasLoaded || isRefreshPending) && knownIds.isNotEmpty()) {
                segmentFallbackIds = knownIds
                segmentFallbackCursor = 0
                fetchFallbackSegments(environment)
                return
            }
            val decoded = decodePage(environment, data)
            ZhihuJson.decodeJson<PageMetadata>(json)
            processResponse(environment, decoded, data)
            handlePageMetadata(json)
            lastPaging = paging
            return
        }
        if (!initialCommentLoaded && initialCommentId != null) {
            initialCommentLoaded = true
            try {
                /** 列表忽略 anchor_comment_id，先由详情定位根评论；https://github.com/zly2006/zhihu-plus-plus/issues/569 */
                val resolvedAnchor = resolveCommentAnchor(initialCommentId, environment)
                initialChildComment = resolvedAnchor.target.takeIf { it.id != resolvedAnchor.root.id }
                processResponse(
                    environment = environment,
                    data = listOf(resolvedAnchor.root),
                    rawData = JsonArray(emptyList()),
                )
            } catch (error: Exception) {
                if (error is kotlin.coroutines.cancellation.CancellationException) throw error
                Log.e("RootCommentViewModel", "Failed to resolve comment anchor", error)
            }
        }
        super.fetchFeeds(environment)
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
        commentsMap[comment.id] = commentItem
        return commentItem
    }

    override fun submitComment(
        content: NavDestination,
        commentText: String,
        environment: ZhihuApiEnvironment,
        replyToCommentId: String?,
        images: List<CommentImageAttachment>,
        stickers: List<CommentSticker>,
        mentions: List<CommentMention>,
        syncToPin: Boolean,
        voiceId: String?,
        onFinished: () -> Unit,
        onSuccess: () -> Unit,
    ) {
        if (commentText.isBlank() && images.isEmpty() && stickers.isEmpty() && voiceId.isNullOrBlank()) {
            onFinished()
            return
        }

        errorMessage = null
        viewModelScope.launch {
            try {
                val requestBody = content.buildSubmitCommentBody(
                    commentText = commentText,
                    replyToCommentId = replyToCommentId,
                    images = images,
                    stickers = stickers,
                    mentions = mentions,
                    syncToPin = syncToPin,
                    voiceId = voiceId,
                )

                val hasVoice = !voiceId.isNullOrBlank()

                /** Web 评论接口不接受 voice_info，语音走移动端接口。 */
                val submitUrl = if (hasVoice) {
                    content.submitCommentUrl.replace("https://www.zhihu.com/api/v4/", "https://api.zhihu.com/")
                } else {
                    content.submitCommentUrl
                }
                val (requestClient, response) = environment.withAuthenticatedClient { client, cookies ->
                    client to client.post(submitUrl) {
                        contentType(ContentType.Application.Json)
                        if (hasVoice) {
                            header("User-Agent", "com.zhihu.android/Futureve/11.12.0 Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Mobile Safari/537.36")
                            header("x-api-version", "3.1.8")
                            header("x-app-version", "11.12.0")
                            header("x-app-za", "OS=Android&Release=15&VersionName=11.12.0&VersionCode=41208&Product=com.zhihu.android&DeviceType=AndroidPhone")
                        }
                        setBody(requestBody)
                        signZhihuFetchRequest(cookies)
                    }
                }

                if (response.status.isSuccess()) {
                    val model = ZhihuJson.decodeJson<DataHolder.Comment>(response.jsonObject())
                    allData.add(0, model)
                    (content as? SegmentCommentHolder)?.let { segment ->
                        SegmentCommentEvents.notifySubmitted(requestClient, segment)
                    }
                    onSuccess()
                } else {
                    errorMessage = "评论发送失败: ${response.status}"
                }
            } catch (e: Exception) {
                if (e is kotlin.coroutines.cancellation.CancellationException) throw e
                errorMessage = "评论发送异常: ${e.message}"
            } finally {
                onFinished()
            }
        }
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
