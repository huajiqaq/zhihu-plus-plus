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

import androidx.lifecycle.viewModelScope
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.navigation.CommentHolder
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.SegmentCommentHolder
import com.github.zly2006.zhihu.util.json
import com.github.zly2006.zhihu.util.signZhihuFetchRequest
import com.github.zly2006.zhihu.viewmodel.CommentItem
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import com.github.zly2006.zhihu.viewmodel.comment.RootCommentViewModel.Companion.submitCommentUrl
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray

/** 子评论弹层自行持有生命周期，不能交给 viewModel() 保存。 */
class ChildCommentViewModel(
    content: NavDestination,
    private val initialComment: DataHolder.Comment? = null,
) : BaseCommentViewModel(content) {
    private var initialCommentLoaded = false

    override val initialUrl: String = when (content) {
        is CommentHolder -> "https://www.zhihu.com/api/v4/comment_v5/comment/${content.commentId}/child_comment"

        else -> ""
    }

    override suspend fun fetchFeeds(environment: PaginationEnvironment) {
        if (!initialCommentLoaded && initialComment != null) {
            initialCommentLoaded = true
            processResponse(
                environment = environment,
                data = listOf(initialComment),
                rawData = JsonArray(emptyList()),
            )
        }
        super.fetchFeeds(environment)
    }

    override fun createCommentItem(comment: DataHolder.Comment, article: NavDestination): CommentItem {
        val commentItem = CommentItem(comment, null)
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
        val commentHolder = content as CommentHolder
        if (commentText.isBlank() && images.isEmpty() && stickers.isEmpty() && voiceId.isNullOrBlank()) {
            onFinished()
            return
        }

        errorMessage = null
        viewModelScope.launch {
            try {
                val requestBody = commentHolder.article.buildSubmitCommentBody(
                    commentText = commentText,
                    replyToCommentId = replyToCommentId ?: commentHolder.commentId,
                    images = images,
                    stickers = stickers,
                    mentions = mentions,
                    syncToPin = syncToPin,
                    voiceId = voiceId,
                )

                val hasVoice = !voiceId.isNullOrBlank()
                val submitUrl = if (hasVoice) {
                    commentHolder.article.submitCommentUrl.replace("https://www.zhihu.com/api/v4/", "https://api.zhihu.com/")
                } else {
                    commentHolder.article.submitCommentUrl
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
                    val model = response.json<DataHolder.Comment>()
                    allData.add(0, model)
                    (commentHolder.article as? SegmentCommentHolder)?.let { segment ->
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
