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

import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.util.AppLog
import com.github.zly2006.zhihu.util.DisplayFailure
import com.github.zly2006.zhihu.util.ZhidaSummarySsePayload
import com.github.zly2006.zhihu.util.buildZhidaSummaryRequest
import com.github.zly2006.zhihu.util.decodeZhidaAnswerData
import com.github.zly2006.zhihu.util.decodeZhidaStreamErrorMessage
import com.github.zly2006.zhihu.util.mergeSummaryChunk
import com.github.zly2006.zhihu.util.parseZhidaSsePayload
import com.github.zly2006.zhihu.util.serializeZhidaSummaryRequest
import com.github.zly2006.zhihu.util.signZhihuFetchRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readLine
import kotlin.reflect.KMutableProperty0
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class ArticleAiSummaryController(
    private val article: Article,
    private val httpClient: HttpClient?,
    private val scope: () -> CoroutineScope,
    private val readTitle: () -> String,
    aiSummaryTextState: KMutableProperty0<String>,
    aiSummaryErrorState: KMutableProperty0<(() -> String)?>,
    aiSummaryLoadingState: KMutableProperty0<Boolean>,
) {
    private var aiSummaryText by aiSummaryTextState
    private var aiSummaryError by aiSummaryErrorState
    private var aiSummaryLoading by aiSummaryLoadingState
    private var aiSummaryJob: Job? = null

    fun requestAiSummary(environment: ZhihuApiEnvironment) {
        if (httpClient == null) {
            aiSummaryError = { DomainStrings.summaryNoClient }
            return
        }
        aiSummaryJob?.cancel()
        aiSummaryJob = scope().launch {
            aiSummaryLoading = true
            aiSummaryError = null
            aiSummaryText = ""
            try {
                val contentType = when (article.type) {
                    ArticleType.Answer -> "answer"
                    ArticleType.Article -> "article"
                }
                val request = buildZhidaSummaryRequest(
                    contentId = article.id,
                    contentType = contentType,
                    title = readTitle().ifBlank { "知乎内容" },
                )
                val owner = httpClient
                val response = environment.withAuthenticatedClient { client, cookies ->
                    if (client !== owner) throw CancellationException("总结期间账户已切换")
                    client.post("https://www.zhihu.com/ai_ingress/stream/completion") {
                        accept(ContentType.Text.EventStream)
                        contentType(ContentType.Application.Json)
                        header("x-xsrftoken", cookies["_xsrf"].orEmpty())
                        setBody(serializeZhidaSummaryRequest(request))
                        signZhihuFetchRequest(cookies)
                    }
                }
                currentCoroutineContext().ensureActive()
                if (environment.httpClient() !== owner) throw CancellationException("总结期间账户已切换")
                if (!response.status.isSuccess()) {
                    val errorBody = response.bodyAsText()
                    val status = response.status.value
                    val message = parseSummaryErrorMessage(errorBody)
                    throw DisplayFailure { message ?: DomainStrings.summaryHttpFailed(status) }
                }

                val channel = response.bodyAsChannel()
                var seenAnswerEvent = false
                var streamEnded = false
                var frameEvent: String? = null
                val frameDataLines = mutableListOf<String>()

                fun handlePayload(payload: ZhidaSummarySsePayload) {
                    when (payload.event.lowercase()) {
                        "answer" -> {
                            val answer = decodeZhidaAnswerData(payload.data) ?: return
                            if (answer.summary.isBlank()) return
                            seenAnswerEvent = true
                            aiSummaryText = if (answer.delta) {
                                mergeSummaryChunk(aiSummaryText, answer.summary)
                            } else {
                                answer.summary
                            }
                        }
                        "error" -> {
                            val message = decodeZhidaStreamErrorMessage(payload.data)
                            throw DisplayFailure { message ?: DomainStrings.summaryFailed }
                        }
                        "end" -> streamEnded = true
                        else -> Unit
                    }
                }

                fun flushFrame() {
                    if (frameDataLines.isEmpty()) return
                    val joinedData = frameDataLines.joinToString("\n")
                    frameDataLines.clear()
                    val payload = parseZhidaSsePayload(joinedData, frameEvent) ?: return
                    handlePayload(payload)
                }

                while (!streamEnded) {
                    val line = channel.readLine() ?: break
                    when {
                        line.startsWith("event:") -> {
                            frameEvent = line.substringAfter("event:").trim()
                        }
                        line.startsWith("data:") -> {
                            frameDataLines += line.substringAfter("data:")
                        }
                        line.isBlank() -> {
                            flushFrame()
                            frameEvent = null
                        }
                        line.startsWith(":") -> Unit
                        else -> Unit
                    }
                }

                if (!streamEnded) {
                    flushFrame()
                }

                if (!seenAnswerEvent || aiSummaryText.isBlank()) {
                    aiSummaryError = { DomainStrings.summaryEmpty }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("ArticleViewModel", "Failed to summarize article", e)
                aiSummaryError = { e.message ?: DomainStrings.summaryFailed }
            } finally {
                aiSummaryLoading = false
            }
        }
    }

    fun cancelAiSummary() {
        aiSummaryJob?.cancel()
        aiSummaryJob = null
        aiSummaryLoading = false
    }

    private fun parseSummaryErrorMessage(responseBody: String): String? = runCatching {
        val json = ZhihuJson.json.parseToJsonElement(responseBody).jsonObject
        json["error"]
            ?.jsonObject
            ?.get("message")
            ?.jsonPrimitive
            ?.contentOrNull
            ?: json["message"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()
}
