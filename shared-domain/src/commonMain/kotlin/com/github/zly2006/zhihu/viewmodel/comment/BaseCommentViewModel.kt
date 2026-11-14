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
import com.github.zly2006.zhihu.navigation.CommentHolder
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.SegmentCommentHolder
import com.github.zly2006.zhihu.util.signZhihuFetchRequest
import com.github.zly2006.zhihu.viewmodel.CommentItem
import com.github.zly2006.zhihu.viewmodel.ContentBlocklistEnvironment
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.PaginationViewModel
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import io.ktor.client.request.delete
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.reflect.typeOf

abstract class BaseCommentViewModel(
    val article: NavDestination,
) : PaginationViewModel<DataHolder.Comment>(typeOf<DataHolder.Comment>()) {
    protected val commentsMap = mutableMapOf<String, CommentItem>()
    var sortOrder by mutableStateOf(CommentSortOrder.SCORE)
    var commentClosedMessage by mutableStateOf<String?>(null)
        private set

    var hasLoaded by mutableStateOf(false)
        private set

    val isRefreshing: Boolean
        get() = isLoading && isRefreshPending

    override val isEnd: Boolean
        get() = commentClosedMessage != null || super.isEnd

    internal override fun resetForRefresh() {
        super.resetForRefresh()
        commentClosedMessage = null
    }

    override fun handlePageMetadata(json: JsonObject) {
        val status = json["comment_status"] as? JsonObject ?: return
        if (status["type"]?.jsonPrimitive?.intOrNull != 1) return
        /** 关闭评论仍返回非终止空页，必须停止跟随 next；https://github.com/zly2006/zhihu-plus-plus/issues/758 */
        commentClosedMessage = status["text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: "评论区已关闭"
    }

    override fun processResponse(environment: PaginationEnvironment, data: List<DataHolder.Comment>, rawData: JsonArray) {
        if (isRefreshPending) commentsMap.clear()
        super.processResponse(environment, emptyList(), rawData)
        filterBlockedComments(environment, data).forEach { comment ->
            if (allData.none { it.id == comment.id }) {
                allData.add(comment)
            }
            val commentItem = createCommentItem(comment, article)
            commentsMap[comment.id] = commentItem
            comment.childComments.forEach {
                val childCommentItem = createCommentItem(it, commentItem.clickTarget?.article ?: article)
                commentsMap[it.id] = childCommentItem
            }
        }
        hasLoaded = true
    }

    private fun filterBlockedComments(
        environment: ContentBlocklistEnvironment,
        comments: List<DataHolder.Comment>,
    ): List<DataHolder.Comment> {
        val blockedUserIds = environment.blockedUserIds()
        if (blockedUserIds.isEmpty()) return comments
        return comments.mapNotNull { comment ->
            if (comment.author.id in blockedUserIds) {
                null
            } else {
                comment.copy(
                    childComments = comment.childComments.filterNot { it.author.id in blockedUserIds },
                )
            }
        }
    }

    abstract fun createCommentItem(comment: DataHolder.Comment, article: NavDestination): CommentItem

    fun changeSortOrder(newSortOrder: CommentSortOrder, environment: PaginationEnvironment) {
        if (sortOrder != newSortOrder) {
            sortOrder = newSortOrder
            refresh(environment)
        }
    }

    abstract fun submitComment(
        content: NavDestination,
        commentText: String,
        environment: ZhihuApiEnvironment,
        replyToCommentId: String? = null,
        images: List<CommentImageAttachment> = emptyList(),
        stickers: List<CommentSticker> = emptyList(),
        mentions: List<CommentMention> = emptyList(),
        syncToPin: Boolean = false,
        voiceId: String? = null,
        onFinished: () -> Unit = {},
        onSuccess: () -> Unit,
    )

    var isLikeLoading by mutableStateOf(false)

    fun toggleLikeComment(
        commentData: DataHolder.Comment,
        environment: ZhihuApiEnvironment,
        onSuccess: () -> Unit,
    ) {
        if (isLikeLoading) return
        isLikeLoading = true

        val commentId = commentData.id
        val newLikeState = !commentData.liked
        viewModelScope.launch {
            try {
                val likeUrl = "https://www.zhihu.com/api/v4/comments/$commentId/like"
                val response = if (newLikeState) {
                    environment.post(likeUrl)
                } else {
                    environment.delete(likeUrl)
                }

                if (response.status.isSuccess()) {
                    onSuccess()
                } else {
                    errorMessage = "操作失败：${response.status}"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = "操作失败：${e.message}"
            } finally {
                isLikeLoading = false
            }
        }
    }

    fun deleteComment(
        commentData: DataHolder.Comment,
        environment: ZhihuApiEnvironment,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
        ownComment: Boolean,
    ) {
        if (!commentData.canDelete) {
            onFailure("当前账号无权删除这条评论")
            return
        }

        viewModelScope.launch {
            try {
                val (requestClient, response) = environment.withAuthenticatedClient { client, cookies ->
                    client to client.delete("https://www.zhihu.com/api/v4/comment_v5/comment/${commentData.id}") {
                        signZhihuFetchRequest(cookies)
                    }
                }
                if (response.status.isSuccess()) {
                    allData
                        .firstOrNull { it.id == commentData.id }
                        ?.childComments
                        ?.forEach { commentsMap.remove(it.id) }
                    allData.removeAll { it.id == commentData.id }
                    allData.indices.forEach { index ->
                        val parent = allData[index]
                        val remainingChildren = parent.childComments.filterNot { it.id == commentData.id }
                        if (remainingChildren.size != parent.childComments.size) {
                            allData[index] = parent.copy(
                                childComments = remainingChildren,
                                childCommentCount = (parent.childCommentCount - 1).coerceAtLeast(remainingChildren.size),
                            )
                        }
                    }
                    val segment = commentsMap.remove(commentData.id)?.clickTarget?.article as? SegmentCommentHolder
                        ?: article as? SegmentCommentHolder
                        ?: (article as? CommentHolder)?.article as? SegmentCommentHolder
                    segment?.let { SegmentCommentEvents.notifyChanged(requestClient, it, -1, ownComment) }
                    onSuccess()
                } else {
                    onFailure("删除失败：${response.status}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onFailure("删除失败：${e.message}")
            }
        }
    }
}
