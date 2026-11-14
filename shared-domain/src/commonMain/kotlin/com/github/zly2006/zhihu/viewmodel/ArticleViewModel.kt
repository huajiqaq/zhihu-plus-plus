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

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fleeksoft.ksoup.Ksoup
import com.github.zly2006.zhihu.data.AigcVoteFlagRequest
import com.github.zly2006.zhihu.data.AigcVoteFlagResponse
import com.github.zly2006.zhihu.data.AigcVoteFlagStatusResponse
import com.github.zly2006.zhihu.data.AigcVoteFlagSubmission
import com.github.zly2006.zhihu.data.AigcVoteNamedVoter
import com.github.zly2006.zhihu.data.AigcVoteReadEvent
import com.github.zly2006.zhihu.data.AigcVoteReadEventsRequest
import com.github.zly2006.zhihu.data.AigcVoteReadEventsResponse
import com.github.zly2006.zhihu.data.AigcVoteReadEvidence
import com.github.zly2006.zhihu.data.ArticleContentPreview
import com.github.zly2006.zhihu.data.Collection
import com.github.zly2006.zhihu.data.CollectionResponse
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.LocalHistoryStorage
import com.github.zly2006.zhihu.data.OfficialBadge
import com.github.zly2006.zhihu.data.VoteUpState
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.decodeZhihuCommentData
import com.github.zly2006.zhihu.data.fetchZhihuContentDetail
import com.github.zly2006.zhihu.data.officialBadge
import com.github.zly2006.zhihu.filter.ContentOpenEventSupport
import com.github.zly2006.zhihu.markdown.htmlToMdAst
import com.github.zly2006.zhihu.markdown.toMarkdown
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.CollectionAnswerNavigator
import com.github.zly2006.zhihu.navigation.PaginationInfoNavigator
import com.github.zly2006.zhihu.navigation.QuestionAnswerNavigator
import com.github.zly2006.zhihu.platform.UserMessageSink
import com.github.zly2006.zhihu.platform.isAigcVoteSupported
import com.github.zly2006.zhihu.platform.isArticleHtmlExportSupported
import com.github.zly2006.zhihu.platform.isArticleImageExportSupported
import com.github.zly2006.zhihu.platform.isJvm
import com.github.zly2006.zhihu.util.ArticleExportComment
import com.github.zly2006.zhihu.util.Log
import com.github.zly2006.zhihu.util.ZhidaSummarySsePayload
import com.github.zly2006.zhihu.util.ZhihuFetchSignature
import com.github.zly2006.zhihu.util.applySegmentInfosToHtml
import com.github.zly2006.zhihu.util.buildArticleExportCommentsHtml
import com.github.zly2006.zhihu.util.buildArticleExportFileName
import com.github.zly2006.zhihu.util.buildArticleExportHtml
import com.github.zly2006.zhihu.util.buildOfflineArticleExportHtml
import com.github.zly2006.zhihu.util.buildZhidaSummaryRequest
import com.github.zly2006.zhihu.util.decodeZhidaAnswerData
import com.github.zly2006.zhihu.util.decodeZhidaStreamErrorMessage
import com.github.zly2006.zhihu.util.formatFullDateTime
import com.github.zly2006.zhihu.util.jsonObject
import com.github.zly2006.zhihu.util.mergeSummaryChunk
import com.github.zly2006.zhihu.util.parseZhidaSsePayload
import com.github.zly2006.zhihu.util.prepareArticleExportComment
import com.github.zly2006.zhihu.util.raiseForStatus
import com.github.zly2006.zhihu.util.serializeZhidaSummaryRequest
import com.github.zly2006.zhihu.util.signZhihuFetchRequest
import com.github.zly2006.zhihu.viewmodel.comment.SegmentCommentEvents
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Clock

class ArticleViewModel(
    private val article: Article,
    val httpClient: HttpClient?,
    private val userMessages: UserMessageSink = object : UserMessageSink {
        override fun showShortMessage(message: String) = Unit
    },
    registerOnPause: (((() -> Unit) -> Unit))? = null,
) : ViewModel() {
    var permissionRequestCount by mutableIntStateOf(0)
    var title by mutableStateOf(article.title.takeUnless { it == "loading..." }.orEmpty())
    var authorId by mutableStateOf(article.authorId)
    var authorUrlToken by mutableStateOf(article.authorUrlToken)
    var authorName by mutableStateOf(article.authorName.takeUnless { it == "loading..." }.orEmpty())
    var authorBio by mutableStateOf(article.authorBio.takeUnless { it == "loading..." }.orEmpty())
    var authorAvatarSrc by mutableStateOf(article.avatarSrc.orEmpty())
    var authorBadge by mutableStateOf<OfficialBadge?>(null)
    var authorIsFollowing by mutableStateOf(false)
    var authorIsSelf by mutableStateOf(false)
    var authorFollowChanging by mutableStateOf(false)
        private set
    val canFollowAuthor: Boolean
        get() = hasLoaded && httpClient != null && authorUrlToken.isNotBlank() && !authorIsSelf

    /** 列表预览不写入正文源、引用快照、回答缓存、导出或朗读数据。 */
    val previewExcerpt = article.excerpt.orEmpty()
    var previewContent by mutableStateOf(ArticleContentPreview.take(article).orEmpty())
        private set
    var content by mutableStateOf("")
    var sourceContent by mutableStateOf<String?>(null)
        private set
    var segmentCommentSnapshot by mutableStateOf<SegmentCommentEvents.Snapshot?>(null)
        private set

    /** 引用索引来自同一响应元数据，不能从跳过富格式段落的注入 HTML 反推。 */
    var segmentInfos by mutableStateOf<kotlin.collections.Collection<com.github.zly2006.zhihu.data.SegmentInfoParagraph>>(emptySet())
        private set

    val commentSegments by derivedStateOf {
        segmentInfos
            .flatMap { paragraph ->
                com.github.zly2006.zhihu.util
                    .buildSegmentTextParts(
                        text = paragraph.text,
                        marks = paragraph.marks,
                        contentId = article.id.toString(),
                        contentType = article.type.toString(),
                        paragraphId = paragraph.pid,
                    ).mapNotNull { it.highlight }
            }.flatMap { span ->
                span.meta.segIds
                    .filter(String::isNotBlank)
                    .map { it to span }
            }.groupBy({ it.first }, { it.second })
            .mapValues { (_, fragments) ->
                fragments.first().copy(
                    displayText = buildString {
                        var previousParagraphId: String? = null
                        fragments.forEach { span ->
                            if (isNotEmpty() && span.paragraphId != previousParagraphId) append("\n\n")
                            append(span.text)
                            previousParagraphId = span.paragraphId
                        }
                    },
                )
            }
    }
    var attachment by mutableStateOf<JsonElement?>(null)
    var isLoading by mutableStateOf(false)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var hasLoaded by mutableStateOf(httpClient == null)
        private set
    private var canLoadFullContent by mutableStateOf(false)
    val paidContentActionVisible: Boolean
        get() = canLoadFullContent && article.type == ArticleType.Answer
    var isLoadingFullContent by mutableStateOf(false)
        private set
    private var fullContentAnchor: String? = null
    var voteUpCount by mutableIntStateOf(0)
    var commentCount by mutableIntStateOf(0)
    var voteUpState by mutableStateOf(VoteUpState.Neutral)
    var votersTotal by mutableIntStateOf(0)
        private set
    var votersNextUrl by mutableStateOf<String?>(null)
        private set
    var votersLoading by mutableStateOf(false)
        private set
    var votersError by mutableStateOf<String?>(null)
        private set
    var votersSocialText by mutableStateOf("")
        private set
    val voters = mutableStateListOf<DataHolder.Author>()
    var questionId by mutableLongStateOf(article.questionId.takeIf { article.type == ArticleType.Answer && it > 0 } ?: 0L)
    var answerNextIds by mutableStateOf<List<Long>>(emptyList())
        private set
    var collections = mutableStateListOf<Collection>()
    var updatedAt by mutableLongStateOf(0L)
    var createdAt by mutableLongStateOf(0L)
    var ipInfo by mutableStateOf<String?>(null)
    var endorsements by mutableStateOf<List<DataHolder.AnswerEndorsementDisplay>>(emptyList())
    var topics by mutableStateOf<List<DataHolder.Topic>>(emptyList())
    var endorsementTexts: List<String>
        get() = endorsements.map { endorsement -> endorsement.text }
        set(value) {
            endorsements = value.map { text -> DataHolder.AnswerEndorsementDisplay(text = text) }
        }
    var aiSummaryText by mutableStateOf("")
        private set
    var aiSummaryError by mutableStateOf<String?>(null)
        private set
    var aiSummaryLoading by mutableStateOf(false)
        private set
    var aigcVoteAvailable by mutableStateOf(false)
        private set
    var aigcVoteLoading by mutableStateOf(false)
        private set
    var aigcVoteError by mutableStateOf<String?>(null)
        private set
    var aigcVoteCredit by mutableIntStateOf(0)
        private set
    var aigcVoteProgress by mutableIntStateOf(0)
        private set
    var aigcVoteCap by mutableIntStateOf(5)
        private set
    var aigcCreditBypassAvailable by mutableStateOf(false)
        private set
    var aigcVoterName by mutableStateOf("")
        private set
    var aigcNamedVoters by mutableStateOf<List<AigcVoteNamedVoter>>(emptyList())
        private set

    /** 自家后端支持人数：在 Zhihu++ 服务中把该内容标记为 AIGC 的有效用户数。 */
    var aigcEffectiveFlagCount by mutableIntStateOf(0)
        private set
    var aigcCurrentVersionFlagCount by mutableIntStateOf(0)
        private set

    /** 外部来源支持人数：仅来自 zhihuai.sx349.xyz 的支持票投票人数。 */
    var zhihuaiAigcSupportVoterCount by mutableIntStateOf(0)
        private set

    /** 展示用 AIGC 支持总人数：自家后端支持人数 + zhihuai 外部支持人数。 */
    val aigcSupportVoterCount: Int
        get() = aigcEffectiveFlagCount + zhihuaiAigcSupportVoterCount
    var aigcFlagged by mutableStateOf(false)
        private set
    private var aigcMaxScrollRatio by mutableFloatStateOf(0f)
    private var aigcReadSyncStarted = false
    private val openedAtEpochSeconds = Clock.System.now().epochSeconds
    private var aiSummaryJob: Job? = null
    private var exportSourceContent: DataHolder.Content? = null

    var rememberedScrollY by mutableIntStateOf(0)
    var rememberedScrollYSync = true

    data class CachedAnswerContent(
        val article: Article,
        val title: String,
        val authorName: String,
        val authorBio: String,
        val authorAvatarUrl: String,
        val authorId: String = "",
        val authorUrlToken: String = "",
        val authorIsFollowing: Boolean = false,
        val authorIsSelf: Boolean = false,
        val exportSourceContent: DataHolder.Content? = null,
        val authorBadge: OfficialBadge? = null,
        val content: String,
        val voteUpCount: Int,
        val commentCount: Int,
        val createdAt: Long = 0L,
        val updatedAt: Long = 0L,
        val ipInfo: String? = null,
        val endorsements: List<DataHolder.AnswerEndorsementDisplay> = emptyList(),
        val sourceLabel: String = "此问题",
        val sourceContent: String? = content,
        val segmentCommentSnapshot: SegmentCommentEvents.Snapshot? = null,
        val segmentInfos: kotlin.collections.Collection<com.github.zly2006.zhihu.data.SegmentInfoParagraph> = emptySet(),
    ) {
        val endorsementTexts: List<String>
            get() = endorsements.map { endorsement -> endorsement.text }
    }

    fun toCachedContent(sourceLabel: String = "此问题"): CachedAnswerContent = CachedAnswerContent(
        article = article,
        title = title,
        authorName = authorName,
        authorBio = authorBio,
        authorAvatarUrl = authorAvatarSrc,
        authorId = authorId,
        authorUrlToken = authorUrlToken,
        authorIsFollowing = authorIsFollowing,
        authorIsSelf = authorIsSelf,
        exportSourceContent = exportSourceContent,
        authorBadge = authorBadge,
        content = content,
        voteUpCount = voteUpCount,
        commentCount = commentCount,
        createdAt = createdAt,
        updatedAt = updatedAt,
        ipInfo = ipInfo,
        endorsements = endorsements,
        sourceLabel = sourceLabel,
        sourceContent = sourceContent,
        segmentCommentSnapshot = segmentCommentSnapshot,
        segmentInfos = segmentInfos,
    )

    fun seedFromCached(cached: CachedAnswerContent) {
        title = cached.title
        authorName = cached.authorName
        authorBio = cached.authorBio
        authorAvatarSrc = cached.authorAvatarUrl
        authorId = cached.authorId
        authorUrlToken = cached.authorUrlToken
        authorIsFollowing = cached.authorIsFollowing
        authorIsSelf = cached.authorIsSelf
        exportSourceContent = cached.exportSourceContent
        when (val raw = cached.exportSourceContent) {
            is DataHolder.Answer -> {
                questionId = raw.question.id
                attachment = raw.attachment
                answerNextIds = raw.paginationInfo?.nextAnswerIds.orEmpty()
                voteUpState = VoteUpState.from(raw.reaction?.relation?.vote)
                fullContentAnchor = truncatedContentAnchor(cached.sourceContent ?: stripAppViewPrompt(raw.content))
                canLoadFullContent = (raw.paidInfo != null || raw.contentNeedTruncated) && fullContentAnchor != null
            }
            is DataHolder.Article -> {
                topics = raw.topics.orEmpty()
                voteUpState = VoteUpState.from(raw.reaction?.relation?.vote)
            }
            else -> Unit
        }
        authorBadge = cached.authorBadge
        Snapshot.withMutableSnapshot {
            sourceContent = cached.sourceContent
            content = cached.content
            if (cached.content.isNotBlank() || attachment != null) previewContent = ""
            segmentCommentSnapshot = cached.segmentCommentSnapshot
            segmentInfos = cached.segmentInfos
        }
        voteUpCount = cached.voteUpCount
        commentCount = cached.commentCount
        createdAt = cached.createdAt
        updatedAt = cached.updatedAt
        ipInfo = cached.ipInfo
        endorsements = cached.endorsements
        hasLoaded = httpClient == null || cached.exportSourceContent != null || cached.content.isNotBlank()
    }

    init {
        Log.i("zhihu-scroll", "me is $this")
        registerOnPause?.invoke {
            rememberedScrollYSync = false
        }
    }

    val isFavorited: Boolean
        get() = collections.any { it.isFavorited }

    @OptIn(ExperimentalStdlibApi::class)
    fun loadArticle(environment: ArticleLoadEnvironment) {
        if (httpClient == null || isLoading) return
        isLoading = true
        loadError = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) {
                    val contentType = when (article.type) {
                        ArticleType.Answer -> "answer"
                        ArticleType.Article -> "article"
                    }
                    val owner = httpClient
                    val (loadedContent, commentSnapshot) = environment.withAuthenticatedClient { client, cookies ->
                        if (client !== owner) throw CancellationException("加载期间账户已切换")
                        val snapshot = withContext(Dispatchers.Main.immediate) {
                            SegmentCommentEvents.capture(client, contentType, article.id.toString())
                        }
                        val detail = fetchZhihuContentDetail(article) { url, include ->
                            if (environment.httpClient() !== client) throw CancellationException("加载期间账户已切换")
                            environment.fetchJson(url, include)
                        } ?: throw IllegalStateException("暂时无法获取内容，请重试")
                        if (environment.httpClient() !== client) {
                            throw CancellationException("加载期间账户已切换")
                        }
                        check(
                            when (detail) {
                                is DataHolder.Answer -> article.type == ArticleType.Answer && detail.id == article.id
                                is DataHolder.Article -> article.type == ArticleType.Article && detail.id == article.id
                                else -> false
                            },
                        ) { "暂时无法获取内容，请重试" }
                        detail to snapshot
                    }
                    if (article.type == ArticleType.Answer) {
                        val sharedData = sharedArticleAnswerSwitchState
                        val answer = loadedContent as? DataHolder.Answer
                        if (answer != null) {
                            exportSourceContent = answer
                            title = answer.question.title
                            authorName = answer.author.name
                            authorId = answer.author.id
                            authorUrlToken = answer.author.urlToken
                            authorIsFollowing = answer.author.isFollowing
                            authorIsSelf = answer.isMine
                            val baseContent = stripAppViewPrompt(answer.content)
                            val renderedContent = preparePaidContent(answer.copy(content = baseContent))
                            Snapshot.withMutableSnapshot {
                                sourceContent = baseContent
                                content = renderedContent
                                previewContent = ""
                                segmentCommentSnapshot = commentSnapshot
                                segmentInfos = answer.segmentInfos
                                hasLoaded = true
                            }
                            attachment = answer.attachment
                            authorBio = answer.author.headline
                            authorAvatarSrc = answer.author.avatarUrl
                            authorBadge = answer.author.badgeV2.officialBadge()
                            voteUpCount = answer.voteupCount
                            votersTotal = answer.voteupCount
                            commentCount = answer.commentCount
                            questionId = answer.question.id
                            answerNextIds = answer.paginationInfo?.nextAnswerIds.orEmpty()
                            voteUpState = VoteUpState.from(answer.reaction?.relation?.vote)
                            updatedAt = answer.updatedTime
                            createdAt = answer.createdTime
                            ipInfo = answer.ipInfo
                            endorsements = answer.endorsementItems
                            topics = emptyList()

                            withContext(Dispatchers.Main.immediate) {
                                LocalHistoryStorage().add(
                                    Article(
                                        id = answer.id,
                                        type = ArticleType.Answer,
                                        title = answer.question.title,
                                        authorName = answer.author.name,
                                        authorBio = answer.author.headline,
                                        avatarSrc = answer.author.avatarUrl,
                                        excerpt = answer.excerpt,
                                        authorId = answer.author.id,
                                        authorUrlToken = answer.author.urlToken,
                                        questionId = answer.question.id,
                                    ),
                                )
                            }
                            ContentOpenEventSupport.recordOpenEvent(getContentFilterDatabase(), article, answer.question.id)
                            withContext(Dispatchers.Main.immediate) {
                                if (sharedData.navigator !is CollectionAnswerNavigator) {
                                    val existingNav = sharedData.navigator
                                    val isSameQuestion = when (existingNav) {
                                        is QuestionAnswerNavigator -> existingNav.questionId == questionId
                                        is PaginationInfoNavigator -> existingNav.questionId == questionId
                                        else -> false
                                    }
                                    if (!isSameQuestion) {
                                        sharedData.navigator = QuestionAnswerNavigator(
                                            questionId = questionId,
                                            environment = environment,
                                        )
                                    }
                                }
                                sharedData.navigator?.pushAnswer(
                                    toCachedContent(sourceLabel = sharedData.navigator?.sourceName ?: "此问题"),
                                )
                            }
                            loadAnswerRelationshipEndorsement(environment)
                            loadMoreVoters(environment, reset = true)

                            withContext(Dispatchers.Main.immediate) {
                                sharedData.navigator?.let { nav ->
                                    if (nav.currentAnswerIndex >= nav.answerHistory.size - 1) {
                                        nav.prefetchNext(article.id)
                                    }
                                    nav.prefetchPrevious(article.id)
                                }
                            }
                        } else {
                            error("暂时无法获取内容，请重试")
                        }
                    } else if (article.type == ArticleType.Article) {
                        val article = loadedContent as? DataHolder.Article
                        if (article != null) {
                            endorsements = emptyList()
                            exportSourceContent = article
                            title = article.title
                            val renderedContent = applySegmentInfosToHtml(
                                content = article.content,
                                segmentInfos = article.segmentInfos,
                                sourceUrl = "https://zhuanlan.zhihu.com/p/${article.id}",
                                contentId = article.id.toString(),
                                contentType = "article",
                            )
                            Snapshot.withMutableSnapshot {
                                sourceContent = article.content
                                content = renderedContent
                                previewContent = ""
                                segmentCommentSnapshot = commentSnapshot
                                segmentInfos = article.segmentInfos
                                hasLoaded = true
                            }
                            voteUpCount = article.voteupCount
                            votersTotal = article.voteupCount
                            commentCount = article.commentCount
                            authorId = article.author.id
                            authorUrlToken = article.author.urlToken
                            authorIsFollowing = article.author.isFollowing
                            authorIsSelf = article.isMine
                            authorName = article.author.name
                            authorBio = article.author.headline
                            authorAvatarSrc = article.author.avatarUrl
                            authorBadge = article.author.badgeV2.officialBadge()
                            voteUpState = VoteUpState.from(article.reaction?.relation?.vote)
                            updatedAt = article.updated
                            createdAt = article.created
                            ipInfo = article.ipInfo
                            topics = article.topics.orEmpty()

                            withContext(Dispatchers.Main.immediate) {
                                LocalHistoryStorage().add(
                                    Article(
                                        id = article.id,
                                        type = ArticleType.Article,
                                        title = article.title,
                                        authorName = article.author.name,
                                        authorBio = article.author.headline,
                                        avatarSrc = article.author.avatarUrl,
                                        excerpt = article.excerpt,
                                        authorId = article.author.id,
                                        authorUrlToken = article.author.urlToken,
                                    ),
                                )
                            }
                            ContentOpenEventSupport.recordOpenEvent(getContentFilterDatabase(), this@ArticleViewModel.article)
                        } else {
                            error("暂时无法获取内容，请重试")
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ArticleViewModel", "Failed to load content", e)
                loadError = "暂时无法获取内容，请重试"
            } finally {
                isLoading = false
            }
        }
    }

    private fun stripAppViewPrompt(html: String): String {
        val body = Ksoup.parseBodyFragment(html).body()
        val last = body.children().lastOrNull() ?: return html
        val text = last.text()
        if (!text.contains("App", ignoreCase = true) || !text.contains("查看")) return html
        last.remove()
        return body.html()
    }

    private fun truncatedContentAnchor(html: String): String? =
        Ksoup
            .parseBodyFragment(html)
            .body()
            .children()
            .lastOrNull { it.text().isNotBlank() }
            ?.text()
            ?.trim()

    internal fun fullContentAfterAnchor(html: String, anchor: String): String? {
        val richText = Ksoup.parse(html).selectFirst(".RichText") ?: return null
        val nodes = richText.children()
        val anchorTexts = nodes.map { it.text().trim() }
        val occurrence = anchorTexts.count { it == anchor }
        if (occurrence == 0) return null
        val anchorIndex = anchorTexts.indices.filter { anchorTexts[it] == anchor }[occurrence - 1]
        if (nodes.lastOrNull()?.text()?.trim() == anchor) return null
        return nodes.drop(anchorIndex + 1).joinToString("") { it.outerHtml() }.ifBlank { null }
    }

    internal fun preparePaidContent(content: DataHolder.Answer): String {
        val baseContent = stripAppViewPrompt(content.content)
        fullContentAnchor = truncatedContentAnchor(baseContent)
        canLoadFullContent = (content.paidInfo != null || content.contentNeedTruncated) && fullContentAnchor != null
        return applySegmentInfosToHtml(
            content = baseContent,
            segmentInfos = content.segmentInfos,
            sourceUrl = "https://www.zhihu.com/question/${content.question.id}/answer/${content.id}",
            contentId = content.id.toString(),
            contentType = "answer",
        )
    }

    internal fun appendPaidContent(extraHtml: String) {
        val renderedExtra = applySegmentInfosToHtml(
            content = extraHtml,
            segmentInfos = segmentInfos.toList(),
            sourceUrl = "https://www.zhihu.com/question/$questionId/answer/${article.id}",
            contentId = article.id.toString(),
            contentType = "answer",
        )
        Snapshot.withMutableSnapshot {
            sourceContent = sourceContent?.plus(extraHtml)
            exportSourceContent = (exportSourceContent as? DataHolder.Answer)?.copy(content = sourceContent.orEmpty())
            content += renderedExtra
            canLoadFullContent = false
        }
    }

    fun loadFullContent(environment: ZhihuApiEnvironment) {
        val anchor = fullContentAnchor ?: return
        if (httpClient == null || isLoadingFullContent || !canLoadFullContent || article.type != ArticleType.Answer) return
        isLoadingFullContent = true
        viewModelScope.launch {
            try {
                val owner = environment.httpClient()
                val extraHtml = environment.withAuthenticatedClient { client, cookies ->
                    if (client !== owner) throw CancellationException("加载期间账户已切换")
                    val url = "https://www.zhihu.com/appview/v2/answer/${article.id}"
                    val response = client.get(url) { signZhihuFetchRequest(cookies) }
                    if (!response.status.isSuccess()) throw IllegalStateException("HTTP ${response.status}")
                    val html = response.bodyAsText()
                    fullContentAfterAnchor(html, anchor)
                }
                currentCoroutineContext().ensureActive()
                if (environment.httpClient() !== owner) throw CancellationException("加载期间账户已切换")
                if (extraHtml == null) {
                    userMessages.showShortMessage("获取失败，请检查是否开通会员或是否购买")
                    return@launch
                }
                appendPaidContent(extraHtml)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ArticleViewModel", "Failed to load full content", e)
                userMessages.showShortMessage("加载失败")
            } finally {
                isLoadingFullContent = false
            }
        }
    }

    fun toggleFavorite(collectionId: String, remove: Boolean, environment: ZhihuApiEnvironment) {
        if (httpClient == null) return
        viewModelScope.launch {
            try {
                val contentType = when (article.type) {
                    ArticleType.Answer -> "answer"
                    ArticleType.Article -> "article"
                }
                val action = if (remove) "remove" else "add"
                val url = "https://api.zhihu.com/collections/contents/$contentType/${article.id}"
                val body = "${action}_collections=$collectionId"

                val response = httpClient.put(url) {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(body)
                }

                if (response.status.isSuccess()) {
                    loadCollections(environment)
                    userMessages.showShortMessage(if (remove) "取消收藏成功" else "收藏成功")
                } else {
                    userMessages.showShortMessage("收藏操作失败")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ArticleViewModel", "Favorite toggle failed", e)
                userMessages.showShortMessage("收藏操作失败: ${e.message}")
            }
        }
    }

    fun requestAiSummary(environment: ZhihuApiEnvironment) {
        if (httpClient == null) {
            aiSummaryError = "未初始化网络客户端"
            return
        }
        aiSummaryJob?.cancel()
        aiSummaryJob = viewModelScope.launch {
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
                    title = title.ifBlank { "知乎内容" },
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
                    throw IllegalStateException(message ?: "总结请求失败（HTTP $status）")
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
                            val message = decodeZhidaStreamErrorMessage(payload.data) ?: "总结失败"
                            throw IllegalStateException(message)
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
                    aiSummaryError = "未返回可显示的总结内容"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ArticleViewModel", "Failed to summarize article", e)
                aiSummaryError = e.message ?: "总结失败"
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

    private val collectionOrder = mutableListOf<String>()

    fun loadCollections(environment: ZhihuApiEnvironment) {
        if (httpClient == null) return
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                try {
                    val contentType = when (article.type) {
                        ArticleType.Answer -> "answer"
                        ArticleType.Article -> "article"
                    }
                    val collectionsUrl = "https://api.zhihu.com/collections/contents/$contentType/${article.id}?limit=50"
                    val jojo = environment.fetchJson(collectionsUrl, "") ?: return@withContext
                    val collectionsData = ZhihuJson.decodeJson<CollectionResponse>(jojo)
                    collections.clear()
                    collections.addAll(
                        collectionsData.data
                            .sortedWith { a, b ->
                                val indexA = collectionOrder.indexOf(a.id)
                                val indexB = collectionOrder.indexOf(b.id)
                                when {
                                    indexA == -1 && indexB == -1 -> 0
                                    indexA == -1 -> -1
                                    indexB == -1 -> 1
                                    else -> indexA.compareTo(indexB)
                                }
                            },
                    )
                    collectionOrder.clear()
                    collectionOrder.addAll(collections.map { it.id })
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("ArticleViewModel", "Failed to load collections", e)
                }
            }
        }
    }

    fun createNewCollection(
        environment: ZhihuApiEnvironment,
        title: String,
        description: String = "",
        isPublic: Boolean = false,
    ) {
        if (httpClient == null) return
        viewModelScope.launch {
            environment.post("https://www.zhihu.com/api/v4/collections") {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("title", title)
                        put("description", description)
                        put("is_public", isPublic)
                    },
                )
            }
            loadCollections(environment)
        }
    }

    fun toggleAuthorFollow(environment: ZhihuApiEnvironment) {
        val owner = httpClient ?: return
        val token = authorUrlToken
        if (!canFollowAuthor || authorFollowChanging) return
        val following = !authorIsFollowing
        authorFollowChanging = true
        viewModelScope.launch {
            try {
                environment.withAuthenticatedClient { client, cookies ->
                    if (client !== owner) throw CancellationException("操作期间账户已切换")
                    client
                        .request("https://www.zhihu.com/api/v4/members/$token/followers") {
                            method = if (following) HttpMethod.Post else HttpMethod.Delete
                            signZhihuFetchRequest(cookies)
                        }.raiseForStatus()
                }
                currentCoroutineContext().ensureActive()
                if (environment.httpClient() !== owner) throw CancellationException("操作期间账户已切换")
                if (authorUrlToken == token) authorIsFollowing = following
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("ArticleViewModel", "Toggle author follow failed", error)
                userMessages.showShortMessage(if (following) "关注失败，请重试" else "取消关注失败，请重试")
            } finally {
                authorFollowChanging = false
            }
        }
    }

    fun toggleVoteUp(environment: ZhihuApiEnvironment, newState: VoteUpState) {
        viewModelScope.launch {
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

    fun loadAigcFlagStatus(environment: AigcVoteEnvironment) {
        aigcVoteAvailable = isAigcVoteSupported && environment.isAigcVoteEnabled()
        val voter = environment.aigcVoteVoter()
        aigcVoterName = voter?.name.orEmpty()
        if (!aigcVoteAvailable) return
        val client = environment.aigcVoteHttpClient()

        viewModelScope.launch {
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
                    }.body<AigcVoteFlagStatusResponse>()
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
                Log.e("ArticleViewModel", "Failed to load AIGC vote status", e)
                aigcVoteError = e.message ?: "AIGC 投票状态加载失败"
            } finally {
                aigcVoteLoading = false
            }
        }
    }

    fun syncAigcReadEventIfEligible(environment: AigcVoteEnvironment) {
        aigcVoteAvailable = isAigcVoteSupported && environment.isAigcVoteEnabled()
        if (!aigcVoteAvailable || aigcReadSyncStarted || content.isBlank()) return
        val client = environment.aigcVoteHttpClient()

        val evidence = currentAigcReadEvidence()
        val contentUpdatedAt = currentContentUpdatedAt()
        if (!evidence.isEligibleForCredit() || contentUpdatedAt <= 0) return
        aigcReadSyncStarted = true

        viewModelScope.launch {
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
                    }.body<AigcVoteReadEventsResponse>()
                aigcVoteCredit = response.credit
                aigcVoteProgress = response.progress
                aigcVoteCap = response.cap
                aigcVoteError = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ArticleViewModel", "Failed to sync AIGC read event", e)
                aigcVoteError = e.message ?: "AIGC 阅读积分同步失败"
                aigcReadSyncStarted = false
            }
        }
    }

    fun submitAigcFlag(environment: AigcVoteEnvironment) {
        aigcVoteAvailable = isAigcVoteSupported && environment.isAigcVoteEnabled()
        if (!aigcVoteAvailable) {
            aigcVoteError = "未配置 AIGC 投票服务"
            userMessages.showShortMessage(aigcVoteError!!)
            return
        }
        val client = environment.aigcVoteHttpClient()
        if (content.isBlank()) {
            aigcVoteError = "正文尚未加载完成"
            userMessages.showShortMessage(aigcVoteError!!)
            return
        }
        val voter = environment.aigcVoteVoter()
        aigcVoterName = voter?.name.orEmpty()
        if (voter == null) {
            aigcVoteError = "需要登录后才能记名投票"
            userMessages.showShortMessage(aigcVoteError!!)
            return
        }
        if (aigcVoteCredit <= 0 && !aigcFlagged && !aigcCreditBypassAvailable) {
            aigcVoteError = "投票积分不足"
            userMessages.showShortMessage(aigcVoteError!!)
            return
        }
        if (!aigcCreditBypassAvailable && !isAigcFlagEvidenceReady()) {
            aigcVoteError = "需要继续阅读后才能标记"
            userMessages.showShortMessage(aigcVoteError!!)
            return
        }

        viewModelScope.launch {
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
                    }.body<AigcVoteFlagResponse>()
                aigcFlagged = response.myFlagged
                aigcVoteCredit = response.credit
                aigcCreditBypassAvailable = response.creditBypassAvailable
                aigcEffectiveFlagCount = response.effectiveFlagCount
                aigcCurrentVersionFlagCount = response.currentVersionFlagCount
                aigcNamedVoters = response.voters
                zhihuaiAigcSupportVoterCount = response.externalSource?.voterCount ?: 0
                userMessages.showShortMessage("已标记疑似 AIGC")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ArticleViewModel", "AIGC flag failed", e)
                aigcVoteError = e.message ?: "AIGC 标记失败"
                userMessages.showShortMessage("AIGC 标记失败: ${e.message}")
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

    fun loadMoreVoters(environment: ZhihuApiEnvironment, reset: Boolean = false) {
        if (article.type != ArticleType.Answer || votersLoading) return
        viewModelScope.launch {
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
        viewModelScope.launch {
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

    suspend fun exportToImage(
        environment: ArticleExportContentEnvironment,
        includeAppAttribution: Boolean,
        onComplete: (Boolean) -> Unit,
    ) {
        exportToImageInternal(
            environment = environment,
            includeComments = false,
            commentCount = 0,
            includeAppAttribution = includeAppAttribution,
            successMessage = "图片已保存到相册",
            onComplete = onComplete,
        )
    }

    suspend fun exportToImageWithComments(
        environment: ArticleExportContentEnvironment,
        commentCount: Int,
        includeAppAttribution: Boolean,
        onComplete: (Boolean) -> Unit,
    ) {
        exportToImageInternal(
            environment = environment,
            includeComments = true,
            commentCount = commentCount,
            includeAppAttribution = includeAppAttribution,
            successMessage = "带评论图片已保存到相册",
            onComplete = onComplete,
        )
    }

    suspend fun exportToHtml(
        environment: ArticleExportContentEnvironment,
        includeAppAttribution: Boolean,
        onComplete: (Boolean) -> Unit,
    ) {
        if (!isArticleHtmlExportSupported) {
            withContext(Dispatchers.Main) { onComplete(false) }
            return
        }
        runCatching {
            check(httpClient == null || environment.httpClient() === httpClient) { "账户已切换，请重新打开内容" }
            requireExportSourceContent()
        }.onFailure { error ->
            withContext(Dispatchers.Main) {
                userMessages.showShortMessage(error.message ?: "内容未加载完成")
                onComplete(false)
            }
            return
        }

        if (environment.requiresHtmlExportPermission() && !environment.hasImageExportPermission()) {
            withContext(Dispatchers.Main) {
                environment.requestImageExportPermission()
                permissionRequestCount++
                userMessages.showShortMessage("需要存储权限才能导出 HTML，正在请求权限")
                onComplete(false)
            }
            return
        }

        try {
            val htmlContent = withContext(Dispatchers.Default) {
                buildOfflineArticleExportHtml(
                    loadAssetText = environment::loadExportAssetText,
                    content = requireExportSourceContent(),
                    includeAppAttribution = includeAppAttribution,
                    httpClient = environment.httpClient(),
                    useOriginalOnImageFetchFailure = isJvm,
                )
            }
            val savedLocation = withContext(Dispatchers.Default) {
                environment.saveHtmlToDownloads(
                    displayName = buildArticleExportFileName(
                        content = requireExportSourceContent(),
                        extension = "html",
                    ),
                    htmlContent = htmlContent,
                )
            }
            withContext(Dispatchers.Main) {
                userMessages.showLongMessage("HTML 已保存到 $savedLocation")
                onComplete(true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ArticleViewModel", "HTML export failed", e)
            withContext(Dispatchers.Main) {
                userMessages.showShortMessage("HTML 导出失败: ${e.message}")
                onComplete(false)
            }
        }
    }

    private suspend fun exportToImageInternal(
        environment: ArticleExportContentEnvironment,
        includeComments: Boolean,
        commentCount: Int,
        includeAppAttribution: Boolean,
        successMessage: String,
        onComplete: (Boolean) -> Unit,
    ) {
        if (!isArticleImageExportSupported) {
            withContext(Dispatchers.Main) { onComplete(false) }
            return
        }
        runCatching {
            check(httpClient == null || environment.httpClient() === httpClient) { "账户已切换，请重新打开内容" }
            requireExportSourceContent()
        }.onFailure { error ->
            withContext(Dispatchers.Main) {
                userMessages.showShortMessage(error.message ?: "内容未加载完成")
                onComplete(false)
            }
            return
        }

        if (!environment.hasImageExportPermission()) {
            withContext(Dispatchers.Main) {
                environment.requestImageExportPermission()
                permissionRequestCount++
                userMessages.showShortMessage("需要存储权限才能导出图片，正在请求权限")
                onComplete(false)
            }
            return
        }

        var preparedWebView: PreparedArticleExportContent? = null
        var bitmap: Any? = null
        val renderer = environment.articleImageExportRenderer()
        var exportError: Throwable? = null
        try {
            try {
                val commentsHtml = if (includeComments && commentCount > 0) {
                    buildArticleExportCommentsHtml(
                        comments = fetchExportComments(environment, commentCount),
                        requestedCount = commentCount,
                    )
                } else {
                    ""
                }
                preparedWebView = renderer.prepareExportWebView(
                    htmlContent = buildArticleExportHtml(
                        loadAssetText = environment::loadExportAssetText,
                        content = requireExportSourceContent(),
                        includeAppAttribution = includeAppAttribution,
                        extraSectionsHtml = commentsHtml,
                    ),
                    timeoutMs = if (includeComments) 18_000L else 15_000L,
                )
                val capturedBitmap = renderer.captureExportBitmap(preparedWebView)
                bitmap = capturedBitmap
                withContext(Dispatchers.Default) {
                    environment.saveImageToMediaStore(
                        displayName = buildArticleExportFileName(
                            content = requireExportSourceContent(),
                            extension = "jpg",
                        ),
                        bitmap = capturedBitmap,
                    )
                }
                withContext(Dispatchers.Main) {
                    userMessages.showLongMessage(successMessage)
                    onComplete(true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ArticleViewModel", "Image export failed", e)
                val errorPrefix = if (includeComments) "带评论图片导出失败" else "图片导出失败"
                withContext(Dispatchers.Main) {
                    userMessages.showShortMessage("$errorPrefix: ${e.message}")
                    onComplete(false)
                }
            }
        } catch (error: Throwable) {
            exportError = error
            throw error
        } finally {
            withContext(NonCancellable) {
                val recycleError = runCatching { bitmap?.let { renderer.recycleExportBitmap(it) } }.exceptionOrNull()
                val destroyError = runCatching { preparedWebView?.let { renderer.destroyExportWebView(it) } }.exceptionOrNull()
                val primary = exportError ?: recycleError ?: destroyError
                for (error in arrayOf(recycleError, destroyError)) {
                    if (primary != null && error != null && error !== primary && primary.suppressedExceptions.none { it === error }) {
                        primary.addSuppressed(error)
                    }
                }
                if (exportError == null && primary != null) throw primary
            }
        }
    }

    private suspend fun fetchExportComments(
        environment: ArticleExportContentEnvironment,
        requestedCount: Int,
    ): List<ArticleExportComment> {
        val safeRequestedCount = requestedCount.coerceAtLeast(0)
        if (safeRequestedCount == 0) return emptyList()

        val url = when (article.type) {
            ArticleType.Answer -> "https://www.zhihu.com/api/v4/comment_v5/answers/${article.id}/root_comment"
            ArticleType.Article -> "https://www.zhihu.com/api/v4/comment_v5/articles/${article.id}/root_comment"
        }
        val json = environment.fetchJson(
            url = "$url?order=score&limit=${safeRequestedCount.coerceAtMost(20)}",
            include = "data[*].content,excerpt,headline",
        ) ?: return emptyList()
        return decodeZhihuCommentData(json, safeRequestedCount)
            .map { comment ->
                prepareArticleExportComment(
                    authorName = comment.author.name,
                    content = comment.content,
                    createdTimeText = formatFullDateTime(comment.createdTime).dropLast(3),
                )
            }
    }

    private fun requireExportSourceContent(): DataHolder.Content = exportSourceContent
        ?: throw IllegalStateException("内容未加载完成")

    fun convertToMarkdown(): String {
        val sb = StringBuilder()

        sb.append("# $title\n\n")

        sb.append("**作者**: $authorName\n\n")
        if (authorBio.isNotEmpty()) {
            sb.append("**简介**: $authorBio\n\n")
        }

        sb.append("---\n\n")
        sb.append(htmlToMdAst(content, noNativeBlock = true).toMarkdown())

        return sb.toString()
    }

    fun exportToClipboard(environment: ClipboardEnvironment) {
        val markdown = convertToMarkdown()

        environment.setPlainTextClipboard("Zhihu Article", markdown)

        userMessages.showShortMessage("文章已复制到剪贴板")
    }
}

@Serializable
private data class AnswerRelationshipEndorsement(
    val type: String = "",
    val text: String = "",
)
