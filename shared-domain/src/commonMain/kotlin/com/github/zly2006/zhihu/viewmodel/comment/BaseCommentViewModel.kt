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
import com.github.zly2006.zhihu.account.CommentSender
import com.github.zly2006.zhihu.data.CommentSortOrder
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.navigation.CommentHolder
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.SegmentCommentHolder
import com.github.zly2006.zhihu.util.DisplayFailure
import com.github.zly2006.zhihu.util.json
import com.github.zly2006.zhihu.util.signZhihuFetchRequest
import com.github.zly2006.zhihu.viewmodel.CommentItem
import com.github.zly2006.zhihu.viewmodel.ContentBlocklistEnvironment
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.PaginationViewModel
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import com.github.zly2006.zhihu.viewmodel.comment.RootCommentViewModel.Companion.submitCommentUrl
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.reflect.typeOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

abstract class BaseCommentViewModel(
    val article: NavDestination,
) : PaginationViewModel<DataHolder.Comment>(typeOf<DataHolder.Comment>()) {
    protected val commentsMap = mutableMapOf<String, CommentItem>()
    open val supportsSorting: Boolean get() = article !is SegmentCommentHolder && article !is CommentHolder
    var sortOrder by mutableStateOf(CommentSortOrder.SCORE)
    private var closedReason by mutableStateOf<String?>(null)
    val commentClosedMessage: String? get() = closedReason?.ifBlank { DomainStrings.commentsClosed }

    var hasLoaded by mutableStateOf(false)
        private set

    val isRefreshing: Boolean
        get() = isLoading && isRefreshPending

    override val isEnd: Boolean
        get() = commentClosedMessage != null || super.isEnd

    internal override fun resetForRefresh() {
        super.resetForRefresh()
        closedReason = null
    }

    override fun preparePageMetadata(json: JsonObject): () -> Unit {
        val status = json["comment_status"] as? JsonObject ?: return {}
        if (status["type"]?.jsonPrimitive?.intOrNull != 1) return {}
        val reason = status["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
        /** 关闭评论仍返回非终止空页，必须停止跟随 next；https://github.com/zly2006/zhihu-plus-plus/issues/758 */
        return { closedReason = reason }
    }

    override suspend fun prepareResponse(environment: PaginationEnvironment, data: List<DataHolder.Comment>, rawData: JsonArray): () -> Unit {
        val filtered = filterBlockedComments(environment, data)
        val mapped = buildMap {
            filtered.forEach { comment ->
                val item = createCommentItem(comment, article)
                put(comment.id, item)
                comment.childComments.forEach { put(it.id, createCommentItem(it, item.clickTarget?.article ?: article)) }
            }
        }
        val commit = super.prepareResponse(environment, emptyList(), rawData)
        return {
            if (isRefreshPending) commentsMap.clear()
            commit()
            val ids = allData.mapTo(hashSetOf()) { it.id }
            allData.addAll(filtered.filter { ids.add(it.id) })
            commentsMap.putAll(mapped)
            hasLoaded = true
        }
    }

    private suspend fun filterBlockedComments(
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
        if (supportsSorting && sortOrder != newSortOrder) {
            sortOrder = newSortOrder
            refresh(environment)
        }
    }

    fun submitComment(
        content: NavDestination,
        commentText: String,
        environment: ZhihuApiEnvironment,
        replyToCommentId: String? = null,
        images: List<CommentImageAttachment> = emptyList(),
        stickers: List<CommentSticker> = emptyList(),
        mentions: List<CommentMention> = emptyList(),
        syncToPin: Boolean = false,
        voiceId: String? = null,
        sender: CommentSender? = null,
        onFinished: () -> Unit = {},
        onSuccess: () -> Unit,
    ) {
        if (commentText.isBlank() && images.isEmpty() && stickers.isEmpty() && voiceId.isNullOrBlank()) {
            onFinished()
            return
        }
        val owner = environment.accountClient
        val thread = content as? CommentHolder
        val target = thread?.article ?: content
        errorText = null
        viewModelScope.launch {
            var accepted = false
            var decoded = false
            try {
                if (sender != null && (images.isNotEmpty() || stickers.isNotEmpty() || !voiceId.isNullOrBlank())) {
                    throw DisplayFailure { DomainStrings.sendAsAttachmentOwner }
                }
                val body = target.buildSubmitCommentBody(commentText, replyToCommentId ?: thread?.commentId, images, stickers, mentions, syncToPin, voiceId)
                val hasVoice = !voiceId.isNullOrBlank()
                val url = if (hasVoice) target.submitCommentUrl.replace("https://www.zhihu.com/api/v4/", "https://api.zhihu.com/") else target.submitCommentUrl
                val viewer = if (sender != null) requireNotNull(owner).httpClient() else null
                val submit: suspend (HttpClient, Map<String, String>) -> Unit = { client, cookies ->
                    val response = client.post(url) {
                        contentType(ContentType.Application.Json)
                        if (hasVoice) {
                            header("User-Agent", "com.zhihu.android/Futureve/11.12.0 Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Mobile Safari/537.36")
                            header("x-api-version", "3.1.8")
                            header("x-app-version", "11.12.0")
                            header("x-app-za", "OS=Android&Release=15&VersionName=11.12.0&VersionCode=41208&Product=com.zhihu.android&DeviceType=AndroidPhone")
                        }
                        setBody(body)
                        signZhihuFetchRequest(cookies)
                    }
                    if (response.status.isSuccess()) {
                        val model = response.json<DataHolder.Comment>()
                        accepted = true
                        check(sender == null || model.author.id == sender.profile.id) { DomainStrings.commentAuthorMismatch }
                        if (sender == null || (!model.isVisibleOnlyToMyself && !model.reviewing)) {
                            /** 别的发送身份拥有的操作权限不能带入当前阅读账号。 */
                            allData.add(0, if (sender == null) model else model.copy(canDelete = false, canCollapse = false, liked = false, disliked = false))
                            if (target is SegmentCommentHolder) {
                                SegmentCommentEvents.notifyChanged(viewer ?: client, target, 1, ownComment = sender == null)
                            }
                        }
                        decoded = true
                    } else {
                        val status = response.status
                        errorText = { DomainStrings.commentSendStatus(status) }
                    }
                }
                if (sender == null) {
                    environment.withAuthenticatedClient(submit)
                } else {
                    requireNotNull(owner).accountStore.commentIdentities.sendAs(owner, sender, submit)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                errorText = {
                    when {
                        decoded -> DomainStrings.commentSentRestoreFailed
                        accepted -> DomainStrings.commentSentAuthorMismatch
                        else -> DomainStrings.commentSendError(error.message.orEmpty())
                    }
                }
            } finally {
                try {
                    if (accepted) onSuccess()
                } finally {
                    onFinished()
                }
            }
        }
    }

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
                    val status = response.status
                    errorText = { DomainStrings.operationFailed(status) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorText = { DomainStrings.operationFailed(e.message.orEmpty()) }
            } finally {
                isLikeLoading = false
            }
        }
    }

    fun deleteComment(
        commentData: DataHolder.Comment,
        environment: ZhihuApiEnvironment,
        onSuccess: () -> Unit,
        onFailure: (() -> String) -> Unit,
        ownComment: Boolean,
    ) {
        if (!commentData.canDelete) {
            onFailure { DomainStrings.commentDeleteDenied }
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
                    val status = response.status
                    onFailure { DomainStrings.deleteFailed(status) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onFailure { DomainStrings.deleteFailed(e.message.orEmpty()) }
            }
        }
    }
}
