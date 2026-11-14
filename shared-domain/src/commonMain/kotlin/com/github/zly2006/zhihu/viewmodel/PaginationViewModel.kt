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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.zly2006.zhihu.account.ZhihuAccountClient
import com.github.zly2006.zhihu.account.ZhihuRequestProfile
import com.github.zly2006.zhihu.data.AigcVoteVoter
import com.github.zly2006.zhihu.data.ContentDetailCache
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.HISTORY_RECORDING_ENABLED_KEY
import com.github.zly2006.zhihu.data.QualityFilterSettings
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.ZhihuJson.decodeJson
import com.github.zly2006.zhihu.data.ZhihuPaging
import com.github.zly2006.zhihu.data.fetchZhihuContentDetail
import com.github.zly2006.zhihu.data.isZhihuRequestHost
import com.github.zly2006.zhihu.navigation.AnswerNavigator
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.isJvm
import com.github.zly2006.zhihu.platform.isNative
import com.github.zly2006.zhihu.platform.platformName
import com.github.zly2006.zhihu.platform.platformSettingsStore
import com.github.zly2006.zhihu.ui.ArticleAnswerSwitchState
import com.github.zly2006.zhihu.ui.ArticleAnswerTransitionDirection
import com.github.zly2006.zhihu.util.Log
import com.github.zly2006.zhihu.util.signZhihuFetchRequest
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel.CachedAnswerContent
import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer
import kotlin.reflect.KType

abstract class PaginationViewModel<T : Any>(
    val dataType: KType,
) : ViewModel() {
    val allData = mutableStateListOf<T>()
    val debugData: MutableList<JsonElement> = mutableListOf()
    var isLoading: Boolean by mutableStateOf(false)
        protected set
    var errorMessage: String? by mutableStateOf(null)
        protected set
    var allowGuestAccess = false
    protected var lastPaging: ZhihuPaging? by mutableStateOf(null)
    protected var isRefreshPending = false
        protected set
    open val isEnd: Boolean get() = lastPaging?.isEnd == true
    protected abstract val initialUrl: String
    private var currentJob: Job? = null
    protected var pagingGeneration = 0
        private set
    protected open val shouldLogDecodeFailures: Boolean = true

    protected open fun resolvePageUrl(): String = lastPaging?.next ?: initialUrl

    /**
     * Generally used fields to include in the API request.
     * This can be overridden in subclasses to include more specific fields.
     */
    open val include = "data[*].content,excerpt,headline,target.author.badge_v2"

    open fun refresh(environment: PaginationEnvironment) {
        resetForRefresh()
        loadMore(environment)
    }

    /** 重置一轮分页但不发请求；混合推荐需要同时重置两个来源后再并行加载。 */
    internal open fun resetForRefresh() {
        pagingGeneration++
        currentJob?.cancel()
        currentJob = null
        isLoading = false
        errorMessage = null
        isRefreshPending = true
        lastPaging = null
    }

    protected open fun handlePageMetadata(json: JsonObject) = Unit

    protected open fun processResponse(environment: PaginationEnvironment, data: List<T>, rawData: JsonArray) {
        if (isRefreshPending) {
            debugData.clear()
            allData.clear()
            isRefreshPending = false
        }
        debugData.addAll(rawData) // 保存原始JSON
        allData.addAll(data) // 保存未flatten的数据
    }

    protected open fun decodePage(
        environment: PaginationEnvironment,
        rawData: JsonArray,
    ): List<T> = rawData.mapNotNull {
        if ("type" in it.jsonObject &&
            it.jsonObject["type"]?.jsonPrimitive?.content in listOf(
                "invited_answer",
                "tab_list",
                "feed_item_index_group",
            )
        ) {
            return@mapNotNull null
        }
        try {
            @Suppress("UNCHECKED_CAST")
            decodeJson(serializer(dataType) as KSerializer<T>, it)
        } catch (e: Exception) {
            if (shouldLogDecodeFailures) {
                environment.logDecodeFailure(this::class.simpleName, it, e)
            }
            null
        }
    }

    protected open suspend fun fetchFeeds(environment: PaginationEnvironment) {
        val generation = pagingGeneration
        val client = environment.httpClient()
        try {
            val url = resolvePageUrl()

            @Suppress("HttpUrlsUsage")
            val json = environment.fetchJson(url.replace("http://", "https://"), include)
                ?: throw RuntimeException("您可能已被风控，请重新登录。", Exception("cause: not json object."))
            currentCoroutineContext().ensureActive()
            if (generation != pagingGeneration || environment.httpClient() !== client) return

            val jsonArray = json["data"] as? JsonArray
                ?: throw RuntimeException("您可能已被风控，请重新登录。", Exception("cause: no $.data"))
            val paging = (json["paging"] as? JsonObject)?.let { value ->
                val terminal = (value["is_end"] ?: value["isEnd"])?.jsonPrimitive?.booleanOrNull == true
                decodeJson<ZhihuPaging>(
                    if (terminal && "next" !in value) JsonObject(value + ("next" to JsonPrimitive(""))) else value,
                )
            }
            val data = decodePage(environment, jsonArray)
            processResponse(environment, data, jsonArray)
            if (paging != null) lastPaging = paging
            handlePageMetadata(json)
        } catch (e: CancellationException) {
            throw e
        } catch (_: NoTransformationFoundException) {
            if (generation == pagingGeneration && environment.httpClient() === client) {
                throw RuntimeException("您可能已被风控，请重新登录。")
            }
        } catch (e: Exception) {
            if (generation == pagingGeneration && environment.httpClient() === client) {
                environment.handleFetchFailure(this::class.simpleName, e)
                if (generation == pagingGeneration && environment.httpClient() === client) errorHandle(e)
            }
        } finally {
            if (generation == pagingGeneration && environment.httpClient() === client) isLoading = false
        }
    }

    open fun loadMore(environment: PaginationEnvironment) {
        if (isLoading || isEnd) return
        errorMessage = null
        isLoading = true
        val generation = pagingGeneration
        val client = environment.httpClient()
        currentJob = viewModelScope.launch {
            try {
                fetchFeeds(environment)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == pagingGeneration && environment.httpClient() === client) errorHandle(e)
            } finally {
                if (generation == pagingGeneration && environment.httpClient() === client) isLoading = false
            }
        }
    }

    open fun retry(environment: PaginationEnvironment) {
        if (errorMessage == null) return
        loadMore(environment)
    }

    protected fun errorHandle(e: Exception) {
        if (e !is CancellationException) {
            errorMessage = e.message?.takeIf { it.isNotBlank() } ?: "加载失败，请重试"
            isLoading = false
        }
    }
}

open class ArticleAnswerSwitchData :
    ViewModel(),
    ArticleAnswerSwitchState {
    /** 活跃的导航器：管理来源、历史记录和预取 */
    override var navigator: AnswerNavigator? by mutableStateOf(null)

    /**
     * 导航前由来源界面设置（如 CollectionContentScreen）。
     * [reset] 时会将其应用到 [navigator]。
     */
    override var pendingNavigator: AnswerNavigator? = null

    // 用于消除切换闪动：导航前设置，新页面用它初始化
    override var pendingInitialContent: CachedAnswerContent? = null

    // 标记是否从回答切换导航进入（避免被 LaunchedEffect 重置方向后误判）
    @kotlin.concurrent.Volatile
    override var navigatingFromAnswerSwitch = false

    // 由 DisposableEffect.onDispose 消费，不受 LaunchedEffect 时序影响
    override var answerSwitchDisposeInProgress = false

    // 导航动画方向
    override var answerTransitionDirection = ArticleAnswerTransitionDirection.DEFAULT

    // 沉浸式阅读模式
    override var isImmersiveMode by mutableStateOf(false)

    override fun reset() {
        navigator = pendingNavigator
        pendingNavigator = null
        pendingInitialContent = null
        navigatingFromAnswerSwitch = false
        isImmersiveMode = false
    }

    override fun promoteForNavigation(direction: ArticleAnswerTransitionDirection) = Unit
}

val sharedArticleAnswerSwitchState = ArticleAnswerSwitchData()

interface PreparedArticleExportContent

interface ArticleImageExportRenderer {
    suspend fun prepareExportWebView(htmlContent: String, timeoutMs: Long): PreparedArticleExportContent

    suspend fun captureExportBitmap(preparedWebView: PreparedArticleExportContent): Any

    suspend fun destroyExportWebView(preparedWebView: PreparedArticleExportContent)

    fun recycleExportBitmap(bitmap: Any)
}

interface ZhihuApiEnvironment {
    val accountClient: ZhihuAccountClient?
        get() = null

    fun httpClient(): HttpClient = accountClient?.httpClient() ?: error("匿名环境必须提供 HTTP 客户端")

    fun httpClient(profile: ZhihuRequestProfile): HttpClient =
        accountClient?.httpClient(profile) ?: if (profile == ZhihuRequestProfile.Mobile && this is MobileHomeFeedEnvironment) mobileHomeFeedHttpClient() else httpClient()

    fun authenticatedCookies(): Map<String, String> = accountClient?.load()?.cookies.orEmpty()

    suspend fun <T> withAuthenticatedClient(
        block: suspend (client: HttpClient, cookies: Map<String, String>) -> T,
    ): T = block(httpClient(), authenticatedCookies())

    suspend fun request(url: String, method: HttpMethod = HttpMethod.Get, profile: ZhihuRequestProfile = ZhihuRequestProfile.Web, block: suspend HttpRequestBuilder.() -> Unit = {}): HttpResponse {
        val owner = accountClient
        return if (owner != null) {
            owner.request(url, method, profile, block = block)
        } else {
            httpClient(profile).request(url) {
                this.method = method
                block()
                if (profile == ZhihuRequestProfile.Web && isZhihuRequestHost(this.url.host)) {
                    val cookies = authenticatedCookies()
                    cookies["_xsrf"]?.takeIf(String::isNotBlank)?.let { headers["x-xsrftoken"] = it }
                    signZhihuFetchRequest(cookies)
                }
            }
        }
    }

    suspend fun get(url: String, profile: ZhihuRequestProfile = ZhihuRequestProfile.Web, block: suspend HttpRequestBuilder.() -> Unit = {}): HttpResponse = request(url, HttpMethod.Get, profile, block)

    suspend fun post(url: String, profile: ZhihuRequestProfile = ZhihuRequestProfile.Web, block: suspend HttpRequestBuilder.() -> Unit = {}): HttpResponse = request(url, HttpMethod.Post, profile, block)

    suspend fun put(url: String, profile: ZhihuRequestProfile = ZhihuRequestProfile.Web, block: suspend HttpRequestBuilder.() -> Unit = {}): HttpResponse = request(url, HttpMethod.Put, profile, block)

    suspend fun patch(url: String, profile: ZhihuRequestProfile = ZhihuRequestProfile.Web, block: suspend HttpRequestBuilder.() -> Unit = {}): HttpResponse = request(url, HttpMethod.Patch, profile, block)

    suspend fun delete(url: String, profile: ZhihuRequestProfile = ZhihuRequestProfile.Web, block: suspend HttpRequestBuilder.() -> Unit = {}): HttpResponse = request(url, HttpMethod.Delete, profile, block)

    suspend fun fetchJson(url: String, include: String): JsonObject? {
        val response = get(url) {
            url {
                protocol = URLProtocol.HTTPS
                if (include.isNotEmpty()) parameters["include"] = include
            }
        }
        if (response.status == HttpStatusCode.NoContent) return null
        val body = response.bodyAsText()
        if (!response.status.isSuccess() || body.trimStart().firstOrNull() != '{') {
            Log.e("ZhihuApiEnvironment", "Non-JSON response: HTTP ${response.status.value}")
            return null
        }
        return ZhihuJson.json.parseToJsonElement(body) as? JsonObject
    }

    suspend fun signedGetText(url: String): String = get(url).bodyAsText()

    suspend fun handleFetchFailure(
        tag: String?,
        error: Exception,
    )

    fun logDecodeFailure(
        tag: String?,
        item: JsonElement,
        error: Exception,
    ) {
        Log.e(tag ?: "PaginationViewModel", "Failed to decode item: $item", error)
    }
}

suspend fun ZhihuApiEnvironment.fetchContentDetail(destination: NavDestination): DataHolder.Content? =
    runCatching {
        fetchZhihuContentDetail(destination) { url, include ->
            fetchJson(url, include)
        }
    }.getOrElse { error ->
        if (error is CancellationException) throw error
        Log.e("ZhihuApiEnvironment", "Failed to fetch content detail for $destination", error)
        null
    }

suspend fun ZhihuApiEnvironment.getOrFetchContentDetail(destination: NavDestination): DataHolder.Content? =
    runCatching {
        val owner = httpClient()
        ContentDetailCache
            .getOrFetch(owner, destination) { target ->
                if (httpClient() !== owner) throw CancellationException("加载期间账户已切换")
                fetchZhihuContentDetail(target) { url, include -> fetchJson(url, include) }
            }.also {
                if (httpClient() !== owner) throw CancellationException("加载期间账户已切换")
            }
    }.getOrElse { error ->
        if (error is CancellationException) throw error
        Log.e("ZhihuApiEnvironment", "Failed to fetch content detail for $destination", error)
        null
    }

suspend fun ZhihuApiEnvironment.addReadHistory(
    contentToken: String,
    contentTypeName: String,
) {
    if (!platformSettingsStore().getBoolean(HISTORY_RECORDING_ENABLED_KEY, true) || authenticatedCookies()["d_c0"] == null) return
    runCatching {
        postSigned("https://www.zhihu.com/api/v4/read_history/add") {
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("content_token", contentToken)
                    put("content_type", contentTypeName)
                }.toString(),
            )
        }
    }
}

suspend fun ZhihuApiEnvironment.postSigned(
    url: String,
    block: HttpRequestBuilder.() -> Unit = {},
): HttpResponse = post(url) { block() }

suspend fun ZhihuApiEnvironment.deleteSigned(
    url: String,
    block: HttpRequestBuilder.() -> Unit = {},
): HttpResponse = delete(url) { block() }

interface MobileHomeFeedEnvironment : ZhihuApiEnvironment {
    fun mobileHomeFeedHttpClient(): HttpClient = accountClient?.httpClient(ZhihuRequestProfile.Mobile) ?: httpClient()

    suspend fun handleMobileHomeFeedFailure(error: Exception) {
        handleFetchFailure("AndroidHomeFeedViewModel", error)
    }
}

interface ContentOpenEnvironment {
    suspend fun recordContentOpenEvent(
        destination: NavDestination,
        questionId: Long? = null,
        openFrom: String = "",
    ) = Unit

    suspend fun recordOpenEvent(
        destination: Article,
        questionId: Long?,
    ) = Unit
}

interface AigcVoteEnvironment {
    fun isAigcVoteEnabled(): Boolean = false

    fun aigcVoteHttpClient(): HttpClient = error("AIGC 内容标记客户端不可用")

    fun aigcVoteBaseUrl(): String = ""

    fun aigcVoteClientId(): String = ""

    fun aigcVoteVoter(): AigcVoteVoter? = null
}

interface ContentBlocklistEnvironment {
    suspend fun isUserBlocked(userId: String): Boolean = false

    suspend fun isQuestionAuthorBlocked(userId: String): Boolean = false

    fun blockedUserIds(): Set<String> = emptySet()

    suspend fun addBlockedUser(
        userId: String,
        userName: String,
        urlToken: String? = null,
        avatarUrl: String? = null,
    ) = Unit

    suspend fun addBlockedQuestionAuthor(
        userId: String,
        userName: String,
        urlToken: String? = null,
        avatarUrl: String? = null,
    ) = Unit

    suspend fun removeBlockedUser(userId: String) = Unit

    suspend fun removeBlockedQuestionAuthor(userId: String) = Unit
}

interface LocalRecommendationEnvironment : ZhihuApiEnvironment {
    suspend fun showLocalRecommendationDatabaseError() = Unit
}

interface ClipboardEnvironment {
    fun setPlainTextClipboard(
        label: String,
        text: String,
    ) = Unit
}

interface ArticleExportEnvironment {
    fun hasImageExportPermission(): Boolean = false

    fun requiresHtmlExportPermission(): Boolean = false

    fun requestImageExportPermission() = Unit

    fun loadExportAssetText(fileName: String): String = error("$platformName 暂不支持文章导出资源读取")

    fun saveHtmlToDownloads(
        displayName: String,
        htmlContent: String,
    ): String = error("$platformName 暂不支持文章 HTML 导出")

    fun saveImageToMediaStore(
        displayName: String,
        bitmap: Any,
    ): Unit = error("$platformName 暂不支持文章图片保存")

    fun articleImageExportRenderer(): ArticleImageExportRenderer =
        error("$platformName 暂不支持文章图片导出")
}

interface ArticleExportContentEnvironment :
    ArticleExportEnvironment,
    ZhihuApiEnvironment

interface ContentLoadEnvironment :
    ZhihuApiEnvironment,
    ContentOpenEnvironment,
    AigcVoteEnvironment

interface ProfileLoadEnvironment :
    ContentLoadEnvironment,
    ContentBlocklistEnvironment

interface ArticleLoadEnvironment :
    ZhihuApiEnvironment,
    ContentLoadEnvironment

interface PaginationEnvironment :
    ZhihuApiEnvironment,
    MobileHomeFeedEnvironment,
    LocalRecommendationEnvironment,
    ClipboardEnvironment,
    ProfileLoadEnvironment,
    ArticleLoadEnvironment,
    ArticleExportContentEnvironment

data class FeedDisplaySettings(
    val qualityFilterMode: QualityFilterMode = QualityFilterMode.RULES,
    val qualityFilter: QualityFilterSettings = QualityFilterSettings(),
    val reverseBlock: Boolean = false,
) {
    constructor(settings: SettingsStore) : this(
        qualityFilterMode = if (isJvm || isNative) {
            QualityFilterMode.OFF
        } else {
            QualityFilterMode.entries.firstOrNull {
                it.name == settings.getString(QUALITY_FILTER_MODE_PREFERENCE_KEY, QualityFilterMode.RULES.name)
            } ?: QualityFilterMode.RULES
        },
        qualityFilter = if (isJvm || isNative) {
            QualityFilterSettings()
        } else {
            QualityFilterSettings(
                answerVoteupCount = settings.getInt(ANSWER_VOTEUP_THRESHOLD_PREFERENCE_KEY, 10).coerceAtLeast(0),
                articleVoteupCount = settings.getInt(ARTICLE_VOTEUP_THRESHOLD_PREFERENCE_KEY, 20).coerceAtLeast(0),
                articleFollowersCount = settings.getInt(ARTICLE_FOLLOWERS_THRESHOLD_PREFERENCE_KEY, 50).coerceAtLeast(0),
                videoVoteCount = settings.getInt(VIDEO_VOTE_THRESHOLD_PREFERENCE_KEY, 20).coerceAtLeast(0),
                videoFollowersCount = settings.getInt(VIDEO_FOLLOWERS_THRESHOLD_PREFERENCE_KEY, 50).coerceAtLeast(0),
                questionAnswerCount = settings.getInt(QUESTION_ANSWER_THRESHOLD_PREFERENCE_KEY, 0).coerceAtLeast(0),
                questionFollowersCount = settings.getInt(QUESTION_FOLLOWERS_THRESHOLD_PREFERENCE_KEY, 50).coerceAtLeast(0),
            )
        },
        reverseBlock = settings.getBoolean("reverseBlock", false),
    )
}

enum class QualityFilterMode {
    OFF,
    RULES,
    HIDE,
}

const val QUALITY_FILTER_MODE_PREFERENCE_KEY = "qualityFilterMode"
const val ANSWER_VOTEUP_THRESHOLD_PREFERENCE_KEY = "answerVoteupThreshold"
const val ARTICLE_VOTEUP_THRESHOLD_PREFERENCE_KEY = "articleVoteupThreshold"
const val ARTICLE_FOLLOWERS_THRESHOLD_PREFERENCE_KEY = "articleFollowersThreshold"
const val VIDEO_VOTE_THRESHOLD_PREFERENCE_KEY = "videoVoteThreshold"
const val VIDEO_FOLLOWERS_THRESHOLD_PREFERENCE_KEY = "videoFollowersThreshold"
const val QUESTION_ANSWER_THRESHOLD_PREFERENCE_KEY = "questionAnswerThreshold"
const val QUESTION_FOLLOWERS_THRESHOLD_PREFERENCE_KEY = "questionFollowersThreshold"

data class HomeFeedFilterResult(
    val foregroundItems: List<FeedDisplayItem>,
    val filteredItems: List<FeedDisplayItem>,
    val reverseBlock: Boolean,
)

@Composable
expect fun rememberPaginationEnvironment(allowGuestAccess: Boolean): PaginationEnvironment
