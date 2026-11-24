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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.zly2006.zhihu.data.AigcVoteNamedVoter
import com.github.zly2006.zhihu.data.Collection
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.LocalHistoryStorage
import com.github.zly2006.zhihu.data.OfficialBadge
import com.github.zly2006.zhihu.data.VoteUpState
import com.github.zly2006.zhihu.data.fetchZhihuContentDetail
import com.github.zly2006.zhihu.data.officialBadge
import com.github.zly2006.zhihu.filter.ContentOpenEventSupport
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.CollectionAnswerNavigator
import com.github.zly2006.zhihu.navigation.PaginationInfoNavigator
import com.github.zly2006.zhihu.navigation.QuestionAnswerNavigator
import com.github.zly2006.zhihu.platform.UserMessageSink
import com.github.zly2006.zhihu.util.Log
import com.github.zly2006.zhihu.util.applySegmentInfosToHtml
import com.github.zly2006.zhihu.util.raiseForStatus
import com.github.zly2006.zhihu.util.signZhihuFetchRequest
import com.github.zly2006.zhihu.viewmodel.comment.SegmentCommentEvents
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement

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
    var authorId by mutableStateOf("")
    var authorUrlToken by mutableStateOf("")
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
    var questionId by mutableLongStateOf(0L)
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
    private var exportSourceContent: DataHolder.Content? = null

    private val exportController = ArticleExportController(
        article = article,
        httpClient = httpClient,
        userMessages = userMessages,
        exportSourceContent = { exportSourceContent },
        onPermissionRequested = { permissionRequestCount++ },
        titleValue = { title },
        contentValue = { content },
        authorNameValue = { authorName },
        authorBioValue = { authorBio },
    )

    // Keep the public Compose delegates and their private setters on this facade.
    private val aiSummaryController = ArticleAiSummaryController(
        article = article,
        httpClient = httpClient,
        scope = { viewModelScope },
        readTitle = { title },
        aiSummaryTextState = ::aiSummaryText,
        aiSummaryErrorState = ::aiSummaryError,
        aiSummaryLoadingState = ::aiSummaryLoading,
    )

    private val aigcController = ArticleAigcController(
        article = article,
        userMessages = userMessages,
        scope = { viewModelScope },
        titleValue = { title },
        contentValue = { content },
        updatedAtValue = { updatedAt },
        createdAtValue = { createdAt },
        authorIdValue = { authorId },
        authorUrlTokenValue = { authorUrlToken },
        authorNameValue = { authorName },
        aigcVoteAvailableState = ::aigcVoteAvailable,
        aigcVoteLoadingState = ::aigcVoteLoading,
        aigcVoteErrorState = ::aigcVoteError,
        aigcVoteCreditState = ::aigcVoteCredit,
        aigcVoteProgressState = ::aigcVoteProgress,
        aigcVoteCapState = ::aigcVoteCap,
        aigcCreditBypassAvailableState = ::aigcCreditBypassAvailable,
        aigcVoterNameState = ::aigcVoterName,
        aigcNamedVotersState = ::aigcNamedVoters,
        aigcEffectiveFlagCountState = ::aigcEffectiveFlagCount,
        aigcCurrentVersionFlagCountState = ::aigcCurrentVersionFlagCount,
        zhihuaiAigcSupportVoterCountState = ::zhihuaiAigcSupportVoterCount,
        aigcFlaggedState = ::aigcFlagged,
    )

    private val collectionsController = ArticleCollectionsController(
        article = article,
        httpClient = httpClient,
        userMessages = userMessages,
        scope = { viewModelScope },
        collectionsValue = { collections },
    )

    private val voteController = ArticleVoteController(
        article = article,
        userMessages = userMessages,
        scope = { viewModelScope },
        voters = voters,
        voteUpStateState = ::voteUpState,
        voteUpCountState = ::voteUpCount,
        votersTotalState = ::votersTotal,
        votersNextUrlState = ::votersNextUrl,
        votersLoadingState = ::votersLoading,
        votersErrorState = ::votersError,
        votersSocialTextState = ::votersSocialText,
    )

    private val authorFollowController = ArticleAuthorFollowController(
        httpClient = httpClient,
        userMessages = userMessages,
        scope = { viewModelScope },
        canFollowAuthor = { canFollowAuthor },
        authorUrlTokenValue = { authorUrlToken },
        authorIsFollowingState = ::authorIsFollowing,
        authorFollowChangingState = ::authorFollowChanging,
    )

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
        /** 为空时由详情元数据推导；明确 false 保留全文已加载后的状态。 */
        val canLoadFullContent: Boolean? = null,
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
        canLoadFullContent = canLoadFullContent,
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
                canLoadFullContent = (cached.canLoadFullContent ?: (raw.paidInfo != null || raw.contentNeedTruncated)) && fullContentAnchor != null
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
                    val (loadedContent, commentSnapshot) = fetchContentDetail(environment)
                    if (article.type == ArticleType.Answer) {
                        val answer = loadedContent as? DataHolder.Answer
                        if (answer != null) {
                            applyLoadedAnswer(environment, answer, commentSnapshot)
                        } else {
                            error("暂时无法获取内容，请重试")
                        }
                    } else if (article.type == ArticleType.Article) {
                        val loadedArticle = loadedContent as? DataHolder.Article
                        if (loadedArticle != null) {
                            applyLoadedArticle(loadedArticle, commentSnapshot)
                        } else {
                            error("暂时无法获取内容，请重试")
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ArticleViewModel", "Failed to load content", e)
                loadError = if (e is com.github.zly2006.zhihu.account.ZhihuLoginRequiredException || e is com.github.zly2006.zhihu.util.HttpStatusException) {
                    zhihuFetchFailureMessage(e)
                } else {
                    "暂时无法获取内容，请重试"
                }
            } finally {
                isLoading = false
            }
        }
    }

    /** 拉取详情并捕获段落评论水位；加载期间账号切换或返回内容与当前页面不符时中止。 */
    private suspend fun fetchContentDetail(
        environment: ArticleLoadEnvironment,
    ): Pair<DataHolder.Content, SegmentCommentEvents.Snapshot> {
        val contentType = when (article.type) {
            ArticleType.Answer -> "answer"
            ArticleType.Article -> "article"
        }
        if (environment.accountClient?.load()?.login == false && !environment.allowGuestAccess) {
            environment.accountClient?.reportLoginRequired()
            throw com.github.zly2006.zhihu.account
                .ZhihuLoginRequiredException()
        }
        val owner = httpClient
        return environment.withAuthenticatedClient { client, cookies ->
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
    }

    /** 把回答详情写入页面状态、记入浏览历史、接上回答切换导航，再拉社交文案和赞同者。 */
    private suspend fun applyLoadedAnswer(
        environment: ArticleLoadEnvironment,
        answer: DataHolder.Answer,
        commentSnapshot: SegmentCommentEvents.Snapshot,
    ) {
        val sharedData = sharedArticleAnswerSwitchState
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
    }

    /** 把专栏文章详情写入页面状态并记入浏览历史。 */
    private suspend fun applyLoadedArticle(
        article: DataHolder.Article,
        commentSnapshot: SegmentCommentEvents.Snapshot,
    ) {
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
    }

    internal fun fullContentAfterAnchor(html: String, anchor: String): String? = paidContentAfterAnchor(html, anchor)

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
                    response.raiseForStatus()
                    val html = response.bodyAsText()
                    val failure = com.github.zly2006.zhihu.data.classifyHttpFailure(
                        com.github.zly2006.zhihu.data
                            .HttpFailureRecord("display", 0, "GET", url, response.status.value, emptyMap(), environment.isLoggedIn, true, com.github.zly2006.zhihu.data.HttpFailureKind.FORBIDDEN),
                        response.headers[HttpHeaders.Location],
                        html,
                    )
                    if (failure.kind == com.github.zly2006.zhihu.data.HttpFailureKind.LOGIN_REQUIRED) {
                        throw com.github.zly2006.zhihu.account
                            .ZhihuLoginRequiredException()
                    }
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
                userMessages.showShortMessage(zhihuFetchFailureMessage(e))
            } finally {
                isLoadingFullContent = false
            }
        }
    }

    fun toggleFavorite(collectionId: String, remove: Boolean, environment: ZhihuApiEnvironment) {
        collectionsController.toggleFavorite(collectionId, remove, environment)
    }

    fun requestAiSummary(environment: ZhihuApiEnvironment) {
        aiSummaryController.requestAiSummary(environment)
    }

    fun cancelAiSummary() {
        aiSummaryController.cancelAiSummary()
    }

    fun loadCollections(environment: ZhihuApiEnvironment) {
        collectionsController.loadCollections(environment)
    }

    fun createNewCollection(
        environment: ZhihuApiEnvironment,
        title: String,
        description: String = "",
        isPublic: Boolean = false,
    ) {
        collectionsController.createNewCollection(environment, title, description, isPublic)
    }

    fun toggleAuthorFollow(environment: ZhihuApiEnvironment) {
        authorFollowController.toggleAuthorFollow(environment)
    }

    fun toggleVoteUp(environment: ZhihuApiEnvironment, newState: VoteUpState) {
        voteController.toggleVoteUp(environment, newState)
    }

    fun updateAigcReadProgress(
        currentScroll: Int,
        maxScroll: Int,
    ) {
        aigcController.updateAigcReadProgress(currentScroll, maxScroll)
    }

    fun isAigcFlagEvidenceReady(): Boolean = aigcController.isAigcFlagEvidenceReady()

    fun loadAigcFlagStatus(environment: AigcVoteEnvironment) {
        aigcController.loadAigcFlagStatus(environment)
    }

    fun syncAigcReadEventIfEligible(environment: AigcVoteEnvironment) {
        aigcController.syncAigcReadEventIfEligible(environment)
    }

    fun submitAigcFlag(environment: AigcVoteEnvironment) {
        aigcController.submitAigcFlag(environment)
    }

    fun loadMoreVoters(environment: ZhihuApiEnvironment, reset: Boolean = false) {
        voteController.loadMoreVoters(environment, reset)
    }

    fun loadAnswerRelationshipEndorsement(environment: ZhihuApiEnvironment) {
        voteController.loadAnswerRelationshipEndorsement(environment)
    }

    suspend fun exportToImage(
        environment: ArticleExportContentEnvironment,
        includeAppAttribution: Boolean,
        onComplete: (Boolean) -> Unit,
    ) {
        exportController.exportToImage(environment, includeAppAttribution, onComplete)
    }

    suspend fun exportToImageWithComments(
        environment: ArticleExportContentEnvironment,
        commentCount: Int,
        includeAppAttribution: Boolean,
        onComplete: (Boolean) -> Unit,
    ) {
        exportController.exportToImageWithComments(environment, commentCount, includeAppAttribution, onComplete)
    }

    suspend fun exportToHtml(
        environment: ArticleExportContentEnvironment,
        includeAppAttribution: Boolean,
        onComplete: (Boolean) -> Unit,
    ) {
        exportController.exportToHtml(environment, includeAppAttribution, onComplete)
    }

    fun convertToMarkdown(): String = exportController.convertToMarkdown()

    fun exportToClipboard(environment: ClipboardEnvironment) {
        exportController.exportToClipboard(environment)
    }
}
