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

import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.VoteUpState
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.platform.UserMessageSink
import com.github.zly2006.zhihu.util.Log
import com.github.zly2006.zhihu.util.jsonObject
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.reflect.KMutableProperty0

/**
 * 文章页的点赞与赞同者。点赞成功后票态、数量、赞同者总数一起更新，回答还要重拉社交文案和赞同者列表，
 * 所以这几项放在同一个类里。状态仍声明在 [ArticleViewModel] 上。
 */
internal class ArticleVoteController(
    private val article: Article,
    private val userMessages: UserMessageSink,
    private val scope: () -> CoroutineScope,
    private val voters: MutableList<DataHolder.Author>,
    voteUpStateState: KMutableProperty0<VoteUpState>,
    voteUpCountState: KMutableProperty0<Int>,
    votersTotalState: KMutableProperty0<Int>,
    votersNextUrlState: KMutableProperty0<String?>,
    votersLoadingState: KMutableProperty0<Boolean>,
    votersErrorState: KMutableProperty0<String?>,
    votersSocialTextState: KMutableProperty0<String>,
) {
    private var voteUpState by voteUpStateState
    private var voteUpCount by voteUpCountState
    private var votersTotal by votersTotalState
    private var votersNextUrl by votersNextUrlState
    private var votersLoading by votersLoadingState
    private var votersError by votersErrorState
    private var votersSocialText by votersSocialTextState

    fun toggleVoteUp(environment: ZhihuApiEnvironment, newState: VoteUpState) {
        scope().launch {
            try {
                val endpoint = when (article.type) {
                    ArticleType.Answer -> "https://www.zhihu.com/api/v4/answers/${article.id}/voters"
                    ArticleType.Article -> "https://www.zhihu.com/api/v4/articles/${article.id}/voters"
                }

                val response = environment
                    .post(endpoint) {
                        when (article.type) {
                            ArticleType.Answer -> setBody(mapOf("type" to newState.key))
                            ArticleType.Article -> setBody(mapOf("voting" to if (newState == VoteUpState.Up) 1 else 0))
                        }
                        contentType(ContentType.Application.Json)
                    }.jsonObject()

                voteUpState = newState
                voteUpCount = response["voteup_count"]!!.jsonPrimitive.int
                votersTotal = voteUpCount
                if (article.type == ArticleType.Answer) {
                    loadAnswerRelationshipEndorsement(environment)
                    loadMoreVoters(environment, reset = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ArticleViewModel", "Vote up failed", e)
                userMessages.showShortMessage("点赞失败: ${e.message}")
            }
        }
    }

    fun loadMoreVoters(environment: ZhihuApiEnvironment, reset: Boolean) {
        if (article.type != ArticleType.Answer || votersLoading) return
        scope().launch {
            votersLoading = true
            votersError = null
            try {
                val page = loadVotersPage(
                    environment = environment,
                    initialUrl = "https://www.zhihu.com/api/v4/answers/${article.id}/upvoters?limit=10&offset=0",
                    nextUrl = votersNextUrl,
                    reset = reset,
                )
                voters.replaceOrAppendUniqueVoters(page.data, reset)
                votersTotal = page.paging.totals.takeIf { it > 0 } ?: voteUpCount
                votersNextUrl = page.nextUrlOrNull()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ArticleViewModel", "Failed to load answer voters", e)
                votersError = e.message ?: "加载赞同者失败"
            } finally {
                votersLoading = false
            }
        }
    }

    fun loadAnswerRelationshipEndorsement(environment: ZhihuApiEnvironment) {
        if (article.type != ArticleType.Answer) return
        scope().launch {
            try {
                val response = environment.fetchJson("https://www.zhihu.com/api/v4/answers/${article.id}/relationship?desktop=true", "")
                    ?: return@launch
                val endorsement = ZhihuJson.decodeJson<AnswerRelationshipEndorsement>(response)
                votersSocialText = endorsement.text
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ArticleViewModel", "Failed to load answer relationship endorsement", e)
                votersSocialText = ""
            }
        }
    }
}

@Serializable
private data class AnswerRelationshipEndorsement(
    val type: String = "",
    val text: String = "",
)
