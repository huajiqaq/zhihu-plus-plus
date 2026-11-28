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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import com.github.zly2006.zhihu.data.AigcVoteFlagRequest
import com.github.zly2006.zhihu.data.AigcVoteFlagResponse
import com.github.zly2006.zhihu.data.AigcVoteFlagStatusResponse
import com.github.zly2006.zhihu.data.AigcVoteFlagSubmission
import com.github.zly2006.zhihu.data.AigcVoteNamedVoter
import com.github.zly2006.zhihu.data.AigcVoteReadEvent
import com.github.zly2006.zhihu.data.AigcVoteReadEventsRequest
import com.github.zly2006.zhihu.data.AigcVoteReadEventsResponse
import com.github.zly2006.zhihu.data.AigcVoteReadEvidence
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.platform.UserMessageSink
import com.github.zly2006.zhihu.platform.isAigcVoteSupported
import com.github.zly2006.zhihu.util.AppLog
import com.github.zly2006.zhihu.util.ZhihuFetchSignature
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.reflect.KMutableProperty0
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

internal class ArticleAigcController(
    private val article: Article,
    private val userMessages: UserMessageSink,
    private val scope: () -> CoroutineScope,
    private val titleValue: () -> String,
    private val contentValue: () -> String,
    private val updatedAtValue: () -> Long,
    private val createdAtValue: () -> Long,
    private val authorIdValue: () -> String,
    private val authorUrlTokenValue: () -> String,
    private val authorNameValue: () -> String,
    aigcVoteAvailableState: KMutableProperty0<Boolean>,
    aigcVoteLoadingState: KMutableProperty0<Boolean>,
    aigcVoteErrorState: KMutableProperty0<(() -> String)?>,
    aigcVoteCreditState: KMutableProperty0<Int>,
    aigcVoteProgressState: KMutableProperty0<Int>,
    aigcVoteCapState: KMutableProperty0<Int>,
    aigcCreditBypassAvailableState: KMutableProperty0<Boolean>,
    aigcVoterNameState: KMutableProperty0<String>,
    aigcNamedVotersState: KMutableProperty0<List<AigcVoteNamedVoter>>,
    aigcEffectiveFlagCountState: KMutableProperty0<Int>,
    aigcCurrentVersionFlagCountState: KMutableProperty0<Int>,
    zhihuaiAigcSupportVoterCountState: KMutableProperty0<Int>,
    aigcFlaggedState: KMutableProperty0<Boolean>,
) {
    private val title: String
        get() = titleValue()
    private val content: String
        get() = contentValue()
    private val updatedAt: Long
        get() = updatedAtValue()
    private val createdAt: Long
        get() = createdAtValue()
    private val authorId: String
        get() = authorIdValue()
    private val authorUrlToken: String
        get() = authorUrlTokenValue()
    private val authorName: String
        get() = authorNameValue()

    private var aigcVoteAvailable by aigcVoteAvailableState
    private var aigcVoteLoading by aigcVoteLoadingState
    private var aigcVoteError by aigcVoteErrorState
    private var aigcVoteCredit by aigcVoteCreditState
    private var aigcVoteProgress by aigcVoteProgressState
    private var aigcVoteCap by aigcVoteCapState
    private var aigcCreditBypassAvailable by aigcCreditBypassAvailableState
    private var aigcVoterName by aigcVoterNameState
    private var aigcNamedVoters by aigcNamedVotersState
    private var aigcEffectiveFlagCount by aigcEffectiveFlagCountState
    private var aigcCurrentVersionFlagCount by aigcCurrentVersionFlagCountState
    private var zhihuaiAigcSupportVoterCount by zhihuaiAigcSupportVoterCountState
    private var aigcFlagged by aigcFlaggedState

    private var aigcMaxScrollRatio by mutableFloatStateOf(0f)
    private var aigcReadSyncStarted = false
    private val openedAtEpochSeconds = Clock.System.now().epochSeconds

    fun updateAigcReadProgress(
        currentScroll: Int,
        maxScroll: Int,
    ) {
        val ratio = when {
            content.isBlank() -> 0f
            maxScroll <= 0 -> 1f
            else -> (currentScroll.toFloat() / maxScroll.toFloat()).coerceIn(0f, 1f)
        }
        if (ratio > aigcMaxScrollRatio) {
            aigcMaxScrollRatio = ratio
        }
    }

    fun isAigcFlagEvidenceReady(): Boolean = currentAigcReadEvidence().isEligibleForCredit()

    fun loadAigcFlagStatus(source: AigcVoteEnvironment) {
        val environment = source.accountClient?.aigcVoteEnvironment ?: source
        aigcVoteAvailable = isAigcVoteSupported && environment.isAigcVoteEnabled()
        val voter = environment.aigcVoteVoter()
        aigcVoterName = voter?.name.orEmpty()
        if (!aigcVoteAvailable) return
        val client = environment.aigcVoteHttpClient()

        scope().launch {
            aigcVoteLoading = true
            aigcVoteError = null
            try {
                val response = client
                    .get(
                        "${environment.aigcVoteBaseUrl().trimEnd('/')}/v1/contents/" +
                            "${aigcContentType()}/${article.id}/aigc-flag",
                    ) {
                        parameter("client_id", environment.aigcVoteClientId())
                        voter?.let {
                            parameter("voter_id", it.id)
                            parameter("voter_name", it.name)
                            it.urlToken?.takeIf(String::isNotBlank)?.let { token -> parameter("voter_url_token", token) }
                        }
                    }.requireAigcSuccess().body<AigcVoteFlagStatusResponse>()
                if (environment.accountClient?.isCurrent == false) return@launch
                aigcFlagged = response.myFlagged
                aigcVoteCredit = response.credit
                aigcVoteProgress = response.progress
                aigcVoteCap = response.cap
                aigcCreditBypassAvailable = response.creditBypassAvailable
                aigcEffectiveFlagCount = response.effectiveFlagCount
                aigcNamedVoters = response.voters
                zhihuaiAigcSupportVoterCount = response.externalSource?.voterCount ?: 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("ArticleViewModel", "Failed to load AIGC vote status", e)
                aigcVoteError = { e.message ?: DomainStrings.aigcStatusFailed }
            } finally {
                aigcVoteLoading = false
            }
        }
    }

    fun syncAigcReadEventIfEligible(source: AigcVoteEnvironment) {
        val environment = source.accountClient?.aigcVoteEnvironment ?: source
        aigcVoteAvailable = isAigcVoteSupported && environment.isAigcVoteEnabled()
        if (!aigcVoteAvailable || aigcReadSyncStarted || content.isBlank()) return
        val client = environment.aigcVoteHttpClient()

        val evidence = currentAigcReadEvidence()
        val contentUpdatedAt = currentContentUpdatedAt()
        if (!evidence.isEligibleForCredit() || contentUpdatedAt <= 0) return
        aigcReadSyncStarted = true

        scope().launch {
            try {
                val event = AigcVoteReadEvent(
                    contentType = aigcContentType(),
                    contentId = article.id.toString(),
                    title = title,
                    authorHash = currentAuthorHash(),
                    contentHtml = content,
                    contentUpdatedAt = contentUpdatedAt,
                    evidence = evidence,
                )
                val response = client
                    .post("${environment.aigcVoteBaseUrl().trimEnd('/')}/v1/read-events:batch") {
                        contentType(ContentType.Application.Json)
                        setBody(AigcVoteReadEventsRequest(environment.aigcVoteClientId(), listOf(event.toRequestEvent())))
                    }.requireAigcSuccess().body<AigcVoteReadEventsResponse>()
                if (environment.accountClient?.isCurrent == false) return@launch
                aigcVoteCredit = response.credit
                aigcVoteProgress = response.progress
                aigcVoteCap = response.cap
                aigcVoteError = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("ArticleViewModel", "Failed to sync AIGC read event", e)
                aigcVoteError = { e.message ?: DomainStrings.aigcCreditSyncFailed }
                aigcReadSyncStarted = false
            }
        }
    }

    fun submitAigcFlag(source: AigcVoteEnvironment) {
        val environment = source.accountClient?.aigcVoteEnvironment ?: source
        aigcVoteAvailable = isAigcVoteSupported && environment.isAigcVoteEnabled()
        if (!aigcVoteAvailable) {
            aigcVoteError = { DomainStrings.aigcNotConfigured }
            aigcVoteError?.let { userMessages.showShortMessage(it()) }
            return
        }
        val client = environment.aigcVoteHttpClient()
        if (content.isBlank()) {
            aigcVoteError = { DomainStrings.articleNotReady }
            aigcVoteError?.let { userMessages.showShortMessage(it()) }
            return
        }
        val voter = environment.aigcVoteVoter()
        aigcVoterName = voter?.name.orEmpty()
        if (voter == null) {
            aigcVoteError = { DomainStrings.aigcLoginNeeded }
            aigcVoteError?.let { userMessages.showShortMessage(it()) }
            return
        }
        if (aigcVoteCredit <= 0 && !aigcFlagged && !aigcCreditBypassAvailable) {
            aigcVoteError = { DomainStrings.aigcNoCredit }
            aigcVoteError?.let { userMessages.showShortMessage(it()) }
            return
        }
        if (!aigcCreditBypassAvailable && !isAigcFlagEvidenceReady()) {
            aigcVoteError = { DomainStrings.aigcReadMore }
            aigcVoteError?.let { userMessages.showShortMessage(it()) }
            return
        }

        scope().launch {
            aigcVoteLoading = true
            aigcVoteError = null
            try {
                val submission = AigcVoteFlagSubmission(
                    contentType = aigcContentType(),
                    contentId = article.id.toString(),
                    voter = voter,
                    title = title,
                    authorHash = currentAuthorHash(),
                    contentHtml = content,
                    contentUpdatedAt = currentContentUpdatedAt(),
                    evidence = currentAigcReadEvidence(),
                )
                val response = client
                    .post(
                        "${environment.aigcVoteBaseUrl().trimEnd('/')}/v1/contents/" +
                            "${submission.contentType}/${submission.contentId}/aigc-flag",
                    ) {
                        contentType(ContentType.Application.Json)
                        setBody(
                            AigcVoteFlagRequest(
                                clientId = environment.aigcVoteClientId(),
                                voter = submission.voter,
                                title = submission.title,
                                authorHash = submission.authorHash,
                                contentHtml = submission.contentHtml,
                                contentUpdatedAt = submission.contentUpdatedAt,
                                evidence = submission.evidence.toFlagEvidence(),
                            ),
                        )
                    }.requireAigcSuccess().body<AigcVoteFlagResponse>()
                if (environment.accountClient?.isCurrent == false) return@launch
                aigcFlagged = response.myFlagged
                aigcVoteCredit = response.credit
                aigcCreditBypassAvailable = response.creditBypassAvailable
                aigcEffectiveFlagCount = response.effectiveFlagCount
                aigcCurrentVersionFlagCount = response.currentVersionFlagCount
                aigcNamedVoters = response.voters
                zhihuaiAigcSupportVoterCount = response.externalSource?.voterCount ?: 0
                userMessages.showShortMessage(DomainStrings.aigcFlagSaved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("ArticleViewModel", "AIGC flag failed", e)
                aigcVoteError = { e.message ?: DomainStrings.aigcFlagFailed }
                userMessages.showShortMessage(DomainStrings.aigcFlagFailedDetail(e.message.orEmpty()))
            } finally {
                aigcVoteLoading = false
            }
        }
    }

    private fun currentAigcReadEvidence(): AigcVoteReadEvidence {
        val nowEpochSeconds = Clock.System.now().epochSeconds
        return AigcVoteReadEvidence(
            foregroundDurationMs = (nowEpochSeconds - openedAtEpochSeconds).coerceAtLeast(0) * 1_000,
            maxScrollRatio = aigcMaxScrollRatio.toDouble(),
            openedAtEpochSeconds = openedAtEpochSeconds,
        )
    }

    private fun currentContentUpdatedAt(): Long = when {
        updatedAt > 0L -> updatedAt
        createdAt > 0L -> createdAt
        else -> 0L
    }

    private fun currentAuthorHash(): String = ZhihuFetchSignature.md5Hex(
        authorId.ifBlank { authorUrlToken.ifBlank { authorName } },
    )

    private fun aigcContentType(): String = when (article.type) {
        ArticleType.Answer -> "answer"
        ArticleType.Article -> "article"
    }
}

@Serializable
private data class AigcVoteServiceError(val error: String)

/** 第三方服务失败不走知乎风控，保留 HTTP 状态及服务返回的业务原因。 */
private suspend fun HttpResponse.requireAigcSuccess(): HttpResponse {
    if (status.isSuccess()) return this
    val responseBody = bodyAsText()
    val detail = runCatching { ZhihuJson.json.decodeFromString<AigcVoteServiceError>(responseBody).error }.getOrNull()
    throw IllegalStateException("HTTP ${status.value} ${status.description}" + detail?.let { ": $it" }.orEmpty())
}
