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

package com.github.zly2006.zhihu.viewmodel.feed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.LocalHistoryStorage
import com.github.zly2006.zhihu.data.decodeQuestionContentDetail
import com.github.zly2006.zhihu.data.navDestination
import com.github.zly2006.zhihu.data.target
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.QuestionAnswerNavigator
import com.github.zly2006.zhihu.navigation.zhihuQuestionFeedsUrl
import com.github.zly2006.zhihu.util.formatRelativeTime
import com.github.zly2006.zhihu.util.raiseForStatus
import com.github.zly2006.zhihu.viewmodel.ContentLoadEnvironment
import com.github.zly2006.zhihu.viewmodel.FeedDisplaySettings
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import com.github.zly2006.zhihu.viewmodel.addReadHistory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

open class QuestionFeedViewModel(
    private val questionId: Long,
) : BaseFeedViewModel() {
    var sortOrder by mutableStateOf("default")
        private set

    /** 保留问题详情以维持返回时的题头高度，避免列表恢复偏移落到后续回答。 */
    var questionDetail by mutableStateOf<DataHolder.Question?>(null)

    var isQuestionDetailLoading by mutableStateOf(false)
        private set
    var questionDetailError by mutableStateOf<String?>(null)
        private set
    private var detailLoadJob: Job? = null
    private var detailLoadGeneration = 0

    override fun onCleared() {
        detailLoadGeneration++
        detailLoadJob?.cancel()
        super.onCleared()
    }

    suspend fun loadQuestionDetail(environment: ContentLoadEnvironment, question: Question) {
        if (isQuestionDetailLoading) return
        val generation = ++detailLoadGeneration
        val client = environment.httpClient()
        detailLoadJob = currentCoroutineContext()[Job]
        isQuestionDetailLoading = true
        questionDetailError = null
        try {
            environment.addReadHistory(questionId.toString(), "question")
            currentCoroutineContext().ensureActive()
            if (generation != detailLoadGeneration || environment.httpClient() !== client) {
                throw CancellationException("加载已失效")
            }
            val include =
                "read_count,visit_count,answer_count,voteup_count,comment_count,follower_count,detail,excerpt,author,relationship.is_following,topics"
            val jsonObject = environment.fetchJson("https://www.zhihu.com/api/v4/questions/$questionId", include)
                ?: error("问题详情为空")
            val loadedQuestion = decodeQuestionContentDetail(jsonObject)
            currentCoroutineContext().ensureActive()
            if (generation != detailLoadGeneration || environment.httpClient() !== client) {
                throw CancellationException("加载已失效")
            }
            LocalHistoryStorage().add(Question(questionId, loadedQuestion.title))
            environment.recordContentOpenEvent(destination = question, questionId = questionId)
            currentCoroutineContext().ensureActive()
            if (generation != detailLoadGeneration || environment.httpClient() !== client) {
                throw CancellationException("加载已失效")
            }
            questionDetail = loadedQuestion
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (generation == detailLoadGeneration && environment.httpClient() === client) {
                questionDetailError = error.message?.takeIf(String::isNotBlank) ?: "问题详情加载失败"
            }
        } finally {
            if (generation == detailLoadGeneration) {
                isQuestionDetailLoading = false
                detailLoadJob = null
            }
        }
    }

    override val initialUrl: String
        get() = zhihuQuestionFeedsUrl(questionId, limit = 20, order = sortOrder)

    fun updateSortOrder(order: String) {
        if (sortOrder != order) {
            sortOrder = order
        }
    }

    override fun createDisplayItem(settings: FeedDisplaySettings, feed: Feed): FeedDisplayItem {
        val target = feed.target
        if (target is Feed.AnswerTarget) {
            return FeedDisplayItem(
                authorName = target.author?.name ?: "未知作者",
                avatarSrc = target.author?.avatarUrl,
                summary = target.excerpt,
                /** 旧 API 或广告数据可能缺少回答时间。 */
                details = if (target.createdTime > 0) {
                    "${target.detailsText} · ${formatRelativeTime(target.createdTime)}"
                } else {
                    target.detailsText
                },
                feed = feed,
                title = "",
            )
        }
        return super.createDisplayItem(settings, feed)
    }

    fun createAnswerNavigatorFor(
        item: FeedDisplayItem,
        environment: ZhihuApiEnvironment,
    ): QuestionAnswerNavigator? {
        val destination = item.navDestination as? Article ?: return null
        if (destination.type != ArticleType.Answer) return null
        val index = displayItems.indexOfFirst { it.stableKey == item.stableKey }
        if (index < 0) return null
        return QuestionAnswerNavigator(
            questionId = questionId,
            initialNextAnswers = displayItems
                .drop(index + 1)
                .mapNotNull { it.navDestination as? Article },
            initialPreviousAnswers = displayItems
                .take(index)
                .asReversed()
                .mapNotNull { it.navDestination as? Article },
            initialNextUrl = lastPaging?.next.orEmpty(),
            order = sortOrder,
            environment = environment,
        )
    }

    suspend fun followQuestion(environment: ZhihuApiEnvironment, follow: Boolean) {
        check(!environment.authenticatedCookies()["d_c0"].isNullOrBlank()) { "请先登录后再关注问题" }
        val url = "https://www.zhihu.com/api/v4/questions/$questionId/followers"
        if (follow) {
            environment.post(url).raiseForStatus()
        } else {
            environment.delete(url).raiseForStatus()
        }
    }

    override fun processResponse(environment: PaginationEnvironment, data: List<Feed>, rawData: kotlinx.serialization.json.JsonArray) {
        val blockedUserIds = environment.blockedUserIds()
        val filtered = if (blockedUserIds.isEmpty()) {
            data
        } else {
            data.filterNot { feed ->
                val target = feed.target
                target is Feed.AnswerTarget && target.author?.id in blockedUserIds
            }
        }
        super.processResponse(environment, filtered, rawData)
    }
}
