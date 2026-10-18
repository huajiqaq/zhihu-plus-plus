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
import com.github.zly2006.zhihu.viewmodel.CommentItem
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import com.github.zly2006.zhihu.viewmodel.postSigned
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.time.Clock

class RootCommentViewModel(
    content: NavDestination,
    private val initialCommentId: String? = null,
) : BaseCommentViewModel(content) {
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

        /**
         * 打开文章时并行预取评论第一页，打开评论面板时直接使用，
         * 省掉一次网络往返（#450）。
         */
        suspend fun prefetchFirstPage(
            environment: ZhihuApiEnvironment,
            article: NavDestination,
        ) {
            val key = commentCacheKey(article) ?: return
            val url = article.rootCommentUrl
            if (url.isBlank()) return
            val json = runCatching {
                environment.fetchJson("$url?order_by=score", "")
            }.getOrNull() ?: return
            CommentFirstPageCache.put(key, json)
        }
    }

    override val initialUrl: String
        get() {
            val baseUrl = article.rootCommentUrl
            // 添加排序参数
            val orderParam = when (sortOrder) {
                CommentSortOrder.SCORE -> "score"
                CommentSortOrder.TIME -> "ts"
            }
            val separator = if ('?' in baseUrl) "&" else "?"
            return "$baseUrl${separator}order_by=$orderParam"
        }

    override suspend fun fetchFeeds(environment: PaginationEnvironment) {
        if (lastPaging == null && initialCommentId == null && sortOrder == CommentSortOrder.SCORE) {
            val key = commentCacheKey(article)
            val cached = key?.let { CommentFirstPageCache.take(it) }
            val data = cached?.get("data") as? JsonArray
            if (cached != null && data != null) {
                processResponse(environment, decodePage(environment, data), data)
                if ("paging" in cached) lastPaging = ZhihuJson.decodeJson(cached["paging"]!!)
                return
            }
        }
        if (!initialCommentLoaded && initialCommentId != null) {
            initialCommentLoaded = true
            try {
                // 根评论列表接口会忽略通知 deep link 的 anchor_comment_id，只能先通过详情解析根评论并置于列表顶部。
                // https://github.com/zly2006/zhihu-plus-plus/issues/569
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
        val clickTarget = CommentHolder(comment.id, article)

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
        onSuccess: () -> Unit,
    ) {
        if (commentText.isBlank() && images.isEmpty() && stickers.isEmpty() && voiceId.isNullOrBlank()) return

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

                val response = environment.postSigned(content.submitCommentUrl) {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }

                if (response.status.isSuccess()) {
                    // 评论成功后，把它添加到第一个。
                    val model = ZhihuJson.decodeJson<DataHolder.Comment>(response.jsonObject())
                    allData.add(0, model)
                    onSuccess()
                } else {
                    errorMessage = "评论发送失败: ${response.status}"
                }
            } catch (e: Exception) {
                errorMessage = "评论发送异常: ${e.message}"
            }
        }
    }
}

data class CommentImageAttachment(
    val url: String,
    val width: Int,
    val height: Int,
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
            append(
                "<a href=\"$safeUrl\" class=\"comment_img\" " +
                    "data-width=\"${image.width}\" data-height=\"${image.height}\">查看图片</a>",
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
            // `unfriendly_check` 和 `selected_settings` 均为可选字段，发布段评时无需提交。
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

private const val COMMENT_PREFETCH_TTL_MS = 5 * 60 * 1000L
private const val COMMENT_PREFETCH_MAX_ENTRIES = 4

private data class CachedCommentPage(
    val json: JsonObject,
    val createdAt: Long,
)

private object CommentFirstPageCache {
    private val mutex = Mutex()
    private val entries = mutableMapOf<String, CachedCommentPage>()

    suspend fun put(key: String, json: JsonObject) {
        mutex.withLock {
            val now = Clock.System.now().toEpochMilliseconds()
            entries.entries.removeAll { now - it.value.createdAt > COMMENT_PREFETCH_TTL_MS }
            if (entries.size >= COMMENT_PREFETCH_MAX_ENTRIES) {
                entries.minByOrNull { it.value.createdAt }?.let { entries.remove(it.key) }
            }
            entries[key] = CachedCommentPage(json, now)
        }
    }

    suspend fun take(key: String): JsonObject? = mutex.withLock {
        val entry = entries.remove(key) ?: return@withLock null
        val now = Clock.System.now().toEpochMilliseconds()
        if (now - entry.createdAt > COMMENT_PREFETCH_TTL_MS) null else entry.json
    }
}

private fun commentCacheKey(article: NavDestination): String? = when (article) {
    is Article -> "${article.type}:${article.id}"
    else -> null
}
