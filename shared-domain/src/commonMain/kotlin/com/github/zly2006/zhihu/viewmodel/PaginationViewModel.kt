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
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.zly2006.zhihu.account.InvalidRefreshTokenException
import com.github.zly2006.zhihu.account.ZhihuAccountClient
import com.github.zly2006.zhihu.account.ZhihuIdentityApiException
import com.github.zly2006.zhihu.account.ZhihuLoginRequiredException
import com.github.zly2006.zhihu.account.ZhihuRequestProfile
import com.github.zly2006.zhihu.data.AigcVoteVoter
import com.github.zly2006.zhihu.data.ContentDetailCache
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.HISTORY_RECORDING_ENABLED_KEY
import com.github.zly2006.zhihu.data.HttpFailureKind
import com.github.zly2006.zhihu.data.HttpFailureRecord
import com.github.zly2006.zhihu.data.QualityFilterSettings
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.ZhihuJson.decodeJson
import com.github.zly2006.zhihu.data.ZhihuPaging
import com.github.zly2006.zhihu.data.classifyHttpFailure
import com.github.zly2006.zhihu.data.fetchZhihuContentDetail
import com.github.zly2006.zhihu.data.findQualityFilterReason
import com.github.zly2006.zhihu.data.isZhihuRequestHost
import com.github.zly2006.zhihu.data.redactDiagnosticText
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.isJvm
import com.github.zly2006.zhihu.platform.isNative
import com.github.zly2006.zhihu.platform.platformName
import com.github.zly2006.zhihu.platform.platformSettingsStore
import com.github.zly2006.zhihu.util.AppLog
import com.github.zly2006.zhihu.util.buildArticleExportFileName
import com.github.zly2006.zhihu.util.buildArticleExportHtml
import com.github.zly2006.zhihu.util.buildOfflineArticleExportHtml
import com.github.zly2006.zhihu.util.DisplayFailure
import com.github.zly2006.zhihu.util.HttpStatusException
import com.github.zly2006.zhihu.util.raiseForStatus
import com.github.zly2006.zhihu.util.signZhihuFetchRequest
import com.github.zly2006.zhihu.viewmodel.filter.FilterableContent
import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import kotlin.reflect.KType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
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

abstract class PaginationViewModel<T : Any>(
    val dataType: KType,
) : ViewModel() {
    val allData = mutableStateListOf<T>()
    val debugData: MutableList<JsonElement> = mutableListOf()
    var isLoading: Boolean by mutableStateOf(false)
        protected set
    var errorText by mutableStateOf<(() -> String)?>(null)
        protected set
    val errorMessage: String? get() = errorText?.invoke()
    var allowGuestAccess = false
    protected var lastPaging: ZhihuPaging? by mutableStateOf(null)
    protected var isRefreshPending by mutableStateOf(false)
        protected set
    open val isEnd: Boolean get() = lastPaging?.isEnd == true
    open val hasNextPage: Boolean get() = lastPaging?.let { !it.isEnd && it.next.isNotBlank() } == true
    val pageGeneration: Int get() = pagingGeneration
    protected abstract val initialUrl: String
    private var currentJob: Job? = null
    protected var pagingGeneration = 0
        private set
    protected open val shouldLogDecodeFailures: Boolean = true

    protected open fun resolvePageUrl(): String = lastPaging?.next ?: initialUrl

    /** 请求的通用附加字段；子类可覆盖以包含接口所需字段。 */
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
        errorText = null
        isRefreshPending = true
        lastPaging = null
    }

    /** 准备阶段允许失败或挂起；返回的动作只能提交已经准备好的值。 */
    protected open fun preparePageMetadata(json: JsonObject): () -> Unit = {}

    protected open suspend fun prepareResponse(environment: PaginationEnvironment, data: List<T>, rawData: JsonArray): () -> Unit = {
        if (isRefreshPending) {
            debugData.clear()
            allData.clear()
            isRefreshPending = false
        }
        debugData.addAll(rawData)
        allData.addAll(data)
    }

    /** 候选只用于显示；结束回调不提交分页，错误仍由原请求处理。 */
    protected open fun previewPage(environment: PaginationEnvironment, data: List<T>, rawData: JsonArray): ((Throwable?) -> Unit)? = null

    protected suspend fun commitPage(
        environment: PaginationEnvironment,
        data: List<T>,
        rawData: JsonArray,
        paging: ZhihuPaging? = null,
        metadata: JsonObject? = null,
        onCommit: () -> Unit = {},
    ) {
        val generation = pagingGeneration
        val client = environment.httpClient()
        currentCoroutineContext().ensureActive()
        if (environment.accountClient?.isCurrent == false) return
        val finishPreview = previewPage(environment, data, rawData)
        try {
            val commit = prepareResponse(environment, data, rawData)
            val commitMetadata = metadata?.let(::preparePageMetadata)
            currentCoroutineContext().ensureActive()
            if (generation != pagingGeneration || environment.accountClient?.isCurrent == false || environment.httpClient() !== client) {
                finishPreview?.invoke(CancellationException("分页请求已失效"))
                return
            }
            Snapshot.withMutableSnapshot {
                commit()
                if (paging != null) lastPaging = paging
                commitMetadata?.invoke()
                onCommit()
                finishPreview?.invoke(null)
            }
        } catch (error: Throwable) {
            finishPreview?.invoke(error)
            throw error
        }
    }

    protected open suspend fun decodePage(
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
                ?: throw PageFormatException { DomainStrings.emptyResponse }
            currentCoroutineContext().ensureActive()
            if (generation != pagingGeneration || environment.httpClient() !== client) return

            val jsonArray = json["data"] as? JsonArray
                ?: throw PageFormatException { DomainStrings.missingList }
            val paging = (json["paging"] as? JsonObject)?.let { value ->
                val terminal = (value["is_end"] ?: value["isEnd"])?.jsonPrimitive?.booleanOrNull == true
                decodeJson<ZhihuPaging>(
                    if (terminal && "next" !in value) JsonObject(value + ("next" to JsonPrimitive(""))) else value,
                )
            }
            val data = decodePage(environment, jsonArray)
            currentCoroutineContext().ensureActive()
            if (generation != pagingGeneration || environment.accountClient?.isCurrent == false || environment.httpClient() !== client) return
            commitPage(environment, data, jsonArray, paging, json)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (generation == pagingGeneration && environment.accountClient?.isCurrent != false && environment.httpClient() === client) {
                environment.handleFetchFailure(this::class.simpleName, e)
                if (generation == pagingGeneration && environment.accountClient?.isCurrent != false && environment.httpClient() === client) errorHandle(e)
            }
        } finally {
            if (generation == pagingGeneration && environment.accountClient?.isCurrent != false && environment.httpClient() === client) isLoading = false
        }
    }

    open fun loadMore(environment: PaginationEnvironment) {
        launchPageRequest(environment)
    }

    /** 下拉、刷新按钮和追加共用同一个任务，刷新可以取消整条旧分页链。 */
    protected fun launchPageRequest(environment: PaginationEnvironment): Job? {
        if (isLoading || isEnd) return null
        errorText = null
        isLoading = true
        val generation = pagingGeneration
        val client = environment.httpClient()
        currentJob = viewModelScope.launch {
            try {
                fetchFeeds(environment)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == pagingGeneration && environment.accountClient?.isCurrent != false && environment.httpClient() === client) errorHandle(e)
            } finally {
                if (generation == pagingGeneration && environment.accountClient?.isCurrent != false && environment.httpClient() === client) isLoading = false
            }
        }
        return currentJob
    }

    open fun retry(environment: PaginationEnvironment) {
        if (errorText == null) return
        loadMore(environment)
    }

    protected fun errorHandle(e: Exception) {
        if (e !is CancellationException) {
            errorText = zhihuFetchFailureText(e)
            isLoading = false
        }
    }
}

interface PreparedArticleExportContent

interface ArticleImageExportRenderer {
    suspend fun prepareExportWebView(htmlContent: String, timeoutMs: Long): PreparedArticleExportContent

    suspend fun captureExportBitmap(preparedWebView: PreparedArticleExportContent): Any

    suspend fun destroyExportWebView(preparedWebView: PreparedArticleExportContent)

    fun recycleExportBitmap(bitmap: Any)
}

fun zhihuFetchFailureMessage(error: Exception): String = zhihuFetchFailureText(error)()

private class PageFormatException(
    val displayMessage: () -> String,
) : SerializationException() {
    override val message: String get() = displayMessage()
}

internal fun zhihuFetchFailureText(error: Exception): () -> String = when (error) {
    is PageFormatException -> error.displayMessage
    is DisplayFailure -> error.displayMessage
    is InvalidRefreshTokenException, is ZhihuIdentityApiException -> ({ redactDiagnosticText(error.message.orEmpty()) })
    is ZhihuLoginRequiredException -> DomainStrings::loginRequired
    is HttpStatusException -> {
        val status = error.status
        val failure = classifyHttpFailure(
            HttpFailureRecord(
                id = "display",
                timestampMillis = 0,
                method = error.requestMethod?.value ?: "GET",
                url = error.requestUrl.toString(),
                status = error.status.value,
                headers = emptyMap(),
                loggedIn = null,
                authenticatedRequest = false,
                kind = HttpFailureKind.FORBIDDEN,
            ),
            null,
            error.bodyText,
        )
        val kind = failure.kind
        val render = {
            when {
                status == HttpStatusCode.NotFound -> DomainStrings.httpNotFound
                kind == HttpFailureKind.SESSION_EXPIRED -> DomainStrings.sessionExpiredHttp(status.value)
                kind == HttpFailureKind.HUMAN_VERIFICATION -> DomainStrings.verificationHttp(status.value)
                kind == HttpFailureKind.LOGIN_REQUIRED -> DomainStrings.loginRequiredHttp(status.value)
                else -> DomainStrings.httpFailed(status.value, status.description)
            }
        }
        render
    }
    is SerializationException, is NoTransformationFoundException -> DomainStrings::invalidResponse
    else ->
        error.message
            ?.takeIf(String::isNotBlank)
            ?.let(::redactDiagnosticText)
            ?.let { detail -> { detail } } ?: DomainStrings::networkFailed
}

/** 只有这个无查询参数的移动端想法详情已映射为网页桥的只读操作。 */
private val browserGuestPinUrl = Regex("https://api[.]zhihu[.]com/v2/pins/[1-9][0-9]{0,18}")

interface ZhihuApiEnvironment {
    val accountClient: ZhihuAccountClient?
        get() = null

    /** 页面能力开关，与是否携带登录 Cookie 无关。 */
    val allowGuestAccess: Boolean
        get() = false

    val isLoggedIn: Boolean
        get() = accountClient?.load()?.login ?: authenticatedCookies()["z_c0"]?.isNotBlank() == true

    fun httpClient(): HttpClient = accountClient?.let { it.httpClient(useCookies = it.load().login) } ?: error("匿名环境必须提供 HTTP 客户端")

    fun httpClient(profile: ZhihuRequestProfile): HttpClient =
        accountClient?.let { it.httpClient(profile, useCookies = it.load().login) } ?: if (profile == ZhihuRequestProfile.Mobile && this is MobileHomeFeedEnvironment) mobileHomeFeedHttpClient() else httpClient()

    fun authenticatedCookies(): Map<String, String> = accountClient
        ?.load()
        ?.takeIf { it.login }
        ?.cookies
        .orEmpty()

    suspend fun <T> withAuthenticatedClient(
        block: suspend (client: HttpClient, cookies: Map<String, String>) -> T,
    ): T = accountClient?.withAuthenticatedClient(block) ?: block(httpClient(), authenticatedCookies())

    suspend fun request(url: String, method: HttpMethod = HttpMethod.Get, profile: ZhihuRequestProfile = ZhihuRequestProfile.Web, block: suspend HttpRequestBuilder.() -> Unit = {}): HttpResponse {
        val owner = accountClient
        if (owner != null && !owner.load().login) {
            val guestProfileAllowed = profile != ZhihuRequestProfile.Mobile ||
                (owner.accountStore.usesBrowserSession && method == HttpMethod.Get && browserGuestPinUrl.matches(url))
            if (!allowGuestAccess || !guestProfileAllowed) throw ZhihuLoginRequiredException()
        }
        return if (owner != null) {
            owner.request(url, method, profile, useCookies = owner.load().login, block = block)
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
        response.raiseForStatus()
        if (response.status == HttpStatusCode.NoContent) return null
        val body = response.bodyAsText()
        val failure = classifyHttpFailure(
            HttpFailureRecord(
                id = "display",
                timestampMillis = 0,
                method = response.request.method.value,
                url = response.request.url.toString(),
                status = response.status.value,
                headers = emptyMap(),
                loggedIn = accountClient?.load()?.login,
                authenticatedRequest = false,
                kind = HttpFailureKind.FORBIDDEN,
            ),
            response.headers[HttpHeaders.Location],
            body,
        )
        if (failure.kind != HttpFailureKind.FORBIDDEN) {
            throw HttpStatusException(response.status, response.request.url, body, response.request.method)
        }
        val status = response.status.value
        return try {
            ZhihuJson.json.parseToJsonElement(body) as? JsonObject
        } catch (_: SerializationException) {
            throw PageFormatException { DomainStrings.invalidJsonHttp(status) }
        } ?: throw PageFormatException { DomainStrings.notJsonObjectHttp(status) }
    }

    suspend fun signedGetText(url: String): String {
        val response = get(url).raiseForStatus()
        val body = response.bodyAsText()
        val failure = classifyHttpFailure(
            HttpFailureRecord(
                id = "display",
                timestampMillis = 0,
                method = response.request.method.value,
                url = response.request.url.toString(),
                status = response.status.value,
                headers = emptyMap(),
                loggedIn = accountClient?.load()?.login,
                authenticatedRequest = false,
                kind = HttpFailureKind.FORBIDDEN,
            ),
            response.headers[HttpHeaders.Location],
            body,
        )
        if (failure.kind != HttpFailureKind.FORBIDDEN) {
            throw HttpStatusException(response.status, response.request.url, body, response.request.method)
        }
        return body
    }

    suspend fun handleFetchFailure(
        tag: String?,
        error: Exception,
    )

    fun logDecodeFailure(
        tag: String?,
        item: JsonElement,
        error: Exception,
    ) {
        AppLog.e(tag ?: "PaginationViewModel", "Failed to decode item: $item", error)
    }
}

suspend fun ZhihuApiEnvironment.fetchContentDetail(destination: NavDestination): DataHolder.Content? =
    runCatching {
        fetchZhihuContentDetail(destination) { url, include ->
            fetchJson(url, include)
        }
    }.getOrElse { error ->
        if (error is CancellationException) throw error
        AppLog.e("ZhihuApiEnvironment", "Failed to fetch content detail for $destination", error)
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
        AppLog.e("ZhihuApiEnvironment", "Failed to fetch content detail for $destination", error)
        null
    }

suspend fun ZhihuApiEnvironment.addReadHistory(
    contentToken: String,
    contentTypeName: String,
) {
    // Web 由桥的独立历史写授权控制；未授权会拒绝，而不是因页面无 Cookie 永久跳过。
    val browserSession = accountClient?.accountStore?.usesBrowserSession == true
    if (!platformSettingsStore().getBoolean(HISTORY_RECORDING_ENABLED_KEY, true) || (!browserSession && authenticatedCookies()["d_c0"] == null)) return
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
}

interface AigcVoteEnvironment {
    val accountClient: ZhihuAccountClient?
        get() = null

    fun isAigcVoteEnabled(): Boolean = accountClient?.aigcVoteEnvironment?.isAigcVoteEnabled() == true

    fun aigcVoteHttpClient(): HttpClient = accountClient?.aigcVoteEnvironment?.aigcVoteHttpClient() ?: error("AIGC 内容标记客户端不可用")

    fun aigcVoteBaseUrl(): String = accountClient?.aigcVoteEnvironment?.aigcVoteBaseUrl().orEmpty()

    fun aigcVoteClientId(): String = accountClient?.aigcVoteEnvironment?.aigcVoteClientId().orEmpty()

    fun aigcVoteVoter(): AigcVoteVoter? = accountClient?.aigcVoteEnvironment?.aigcVoteVoter()
}

interface ContentBlocklistEnvironment {
    suspend fun isUserBlocked(userId: String): Boolean = false

    suspend fun isQuestionAuthorBlocked(userId: String): Boolean = false

    suspend fun blockedUserIds(): Set<String> = emptySet()

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

/** 用户关闭系统交付面板不是协程取消；完成文案由真正交付产物的平台提供。 */
sealed interface ArticleExportResult {
    data class Completed(val completionMessage: String) : ArticleExportResult {
        init {
            require(completionMessage.isNotBlank())
        }
    }

    data object Cancelled : ArticleExportResult
}

enum class ArticleExportPermission {
    Granted,
    RequestStarted,
    Denied,
}

interface ArticleExportEnvironment {
    fun hasImageExportPermission(): Boolean = false

    fun requiresHtmlExportPermission(): Boolean = false

    fun requestImageExportPermission() = Unit

    /** Android 请求后由既有宿主接收结果；原生平台可以挂起等待系统授权。 */
    suspend fun ensureImageExportPermission(): ArticleExportPermission = withContext(Dispatchers.Main) {
        if (hasImageExportPermission()) {
            ArticleExportPermission.Granted
        } else {
            requestImageExportPermission()
            ArticleExportPermission.RequestStarted
        }
    }

    fun loadExportAssetText(fileName: String): String = error(DomainStrings.exportAssetsUnsupported(platformName))

    fun saveHtmlToDownloads(
        displayName: String,
        htmlContent: String,
    ): String = error(DomainStrings.htmlExportUnsupported(platformName))

    fun saveImageToMediaStore(
        displayName: String,
        bitmap: Any,
    ): Unit = error(DomainStrings.exportSaveUnsupported(platformName))

    fun articleImageExportRenderer(): ArticleImageExportRenderer =
        error(DomainStrings.imageExportUnsupported(platformName))
}

interface ArticleExportContentEnvironment :
    ArticleExportEnvironment,
    ZhihuApiEnvironment {
    /** 使用平台的文本 PDF 渲染和文件交付，不将整页截图包装为 PDF。 */
    suspend fun exportPdf(content: DataHolder.Content, includeAppAttribution: Boolean): ArticleExportResult =
        error(DomainStrings.pdfExportUnsupported(platformName))

    /** 平台负责资源读取与交付；浏览器只能确认下载已请求，不能确认文件已保存。 */
    suspend fun exportHtml(content: DataHolder.Content, includeAppAttribution: Boolean): ArticleExportResult {
        val html = buildOfflineArticleExportHtml(
            loadAssetText = ::loadExportAssetText,
            content = content,
            includeAppAttribution = includeAppAttribution,
            httpClient = httpClient(),
            useOriginalOnImageFetchFailure = isJvm,
        )
        ensureExportAccountActive()
        val location = saveHtmlToDownloads(buildArticleExportFileName(content, "html"), html)
        return ArticleExportResult.Completed(DomainStrings.htmlSavedTo(location))
    }

    /** 平台可按自己的编码、内存及系统交付约束输出文件，默认保留 Android 的位图流程。 */
    suspend fun exportImage(
        content: DataHolder.Content,
        includeAppAttribution: Boolean,
        extraSectionsHtml: String,
        includeComments: Boolean,
    ): ArticleExportResult {
        val renderer = articleImageExportRenderer()
        var prepared: PreparedArticleExportContent? = null
        var bitmap: Any? = null
        var primaryError: Throwable? = null
        var cleanupWarning = false
        try {
            prepared = renderer.prepareExportWebView(
                htmlContent = buildArticleExportHtml(
                    loadAssetText = ::loadExportAssetText,
                    content = content,
                    includeAppAttribution = includeAppAttribution,
                    extraSectionsHtml = extraSectionsHtml,
                ),
                timeoutMs = if (includeComments) 18_000L else 15_000L,
            )
            val captured = renderer.captureExportBitmap(prepared)
            bitmap = captured
            withContext(Dispatchers.Default) {
                ensureExportAccountActive()
                saveImageToMediaStore(buildArticleExportFileName(content, "jpg"), captured)
            }
        } catch (error: Throwable) {
            primaryError = error
            throw error
        } finally {
            withContext(NonCancellable) {
                val recycleError = runCatching { bitmap?.let(renderer::recycleExportBitmap) }.exceptionOrNull()
                val destroyError = runCatching { prepared?.let { renderer.destroyExportWebView(it) } }.exceptionOrNull()
                val primary = primaryError ?: recycleError ?: destroyError
                for (error in arrayOf(recycleError, destroyError)) {
                    if (primary != null && error != null && error !== primary && primary.suppressedExceptions.none { it === error }) {
                        primary.addSuppressed(error)
                    }
                }
                if (primaryError == null && primary != null) {
                    AppLog.e("ArticleExport", "Image delivered but renderer cleanup failed", primary)
                    cleanupWarning = true
                }
            }
        }
        val saved = if (includeComments) DomainStrings.imageCommentsSaved else DomainStrings.imageSaved
        return ArticleExportResult.Completed(if (cleanupWarning) "$saved\n${DomainStrings.exportCleanupFailed}" else saved)
    }
}

interface ContentLoadEnvironment :
    ZhihuApiEnvironment,
    ContentOpenEnvironment,
    AigcVoteEnvironment {
    override val accountClient: ZhihuAccountClient?
        get() = null
}

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
    ArticleExportContentEnvironment {
    fun showNlpBlockedMessage(blocked: Collection<FilterableContent>) = Unit
}

data class FeedDisplaySettings(
    val qualityFilterMode: QualityFilterMode = QualityFilterMode.RULES,
    val qualityFilter: QualityFilterSettings = QualityFilterSettings(),
    val reverseBlock: Boolean = false,
) {
    constructor(settings: SettingsStore) : this(
        qualityFilterMode = QualityFilterMode.entries.firstOrNull {
            it.name == settings.getString(QUALITY_FILTER_MODE_PREFERENCE_KEY, if (isJvm || isNative) QualityFilterMode.OFF.name else QualityFilterMode.RULES.name)
        } ?: QualityFilterMode.RULES,
        qualityFilter = QualityFilterSettings(
            answerVoteupCount = settings.getInt(ANSWER_VOTEUP_THRESHOLD_PREFERENCE_KEY, 10).coerceAtLeast(0),
            articleVoteupCount = settings.getInt(ARTICLE_VOTEUP_THRESHOLD_PREFERENCE_KEY, 20).coerceAtLeast(0),
            articleFollowersCount = settings.getInt(ARTICLE_FOLLOWERS_THRESHOLD_PREFERENCE_KEY, 50).coerceAtLeast(0),
            videoVoteCount = settings.getInt(VIDEO_VOTE_THRESHOLD_PREFERENCE_KEY, 20).coerceAtLeast(0),
            videoFollowersCount = settings.getInt(VIDEO_FOLLOWERS_THRESHOLD_PREFERENCE_KEY, 50).coerceAtLeast(0),
            questionAnswerCount = settings.getInt(QUESTION_ANSWER_THRESHOLD_PREFERENCE_KEY, 0).coerceAtLeast(0),
            questionFollowersCount = settings.getInt(QUESTION_FOLLOWERS_THRESHOLD_PREFERENCE_KEY, 50).coerceAtLeast(0),
        ),
        reverseBlock = settings.getBoolean("reverseBlock", false),
    )

    fun applyQualityFilter(item: FeedDisplayItem): FeedDisplayItem? {
        val reason = if (qualityFilterMode == QualityFilterMode.OFF || reverseBlock) null else item.findQualityFilterReason(qualityFilter)
        if (reason != null && qualityFilterMode == QualityFilterMode.HIDE) return null
        return item.copy(
            isFiltered = if (item.isQualityFiltered) reason != null else item.isFiltered || reason != null,
            isQualityFiltered = reason != null,
            qualityFilterReason = reason,
            /** 旧版本把规则写进 content；不能再将这段说明当作用户正文。 */
            content = item.content.takeUnless { item.isQualityFiltered && item.qualityFilterReason == null },
        )
    }
}

enum class QualityFilterMode {
    OFF,
    RULES,
    HIDE,
    ;

    val label: String
        get() = when (this) {
            OFF -> DomainStrings.qualityNoBlock
            RULES -> DomainStrings.qualityShowRules
            HIDE -> DomainStrings.qualityHide
        }
}

const val QUALITY_FILTER_MODE_PREFERENCE_KEY = "qualityFilterMode"
const val ANSWER_VOTEUP_THRESHOLD_PREFERENCE_KEY = "answerVoteupThreshold"
const val ARTICLE_VOTEUP_THRESHOLD_PREFERENCE_KEY = "articleVoteupThreshold"
const val ARTICLE_FOLLOWERS_THRESHOLD_PREFERENCE_KEY = "articleFollowersThreshold"
const val VIDEO_VOTE_THRESHOLD_PREFERENCE_KEY = "videoVoteThreshold"
const val VIDEO_FOLLOWERS_THRESHOLD_PREFERENCE_KEY = "videoFollowersThreshold"
const val QUESTION_ANSWER_THRESHOLD_PREFERENCE_KEY = "questionAnswerThreshold"
const val QUESTION_FOLLOWERS_THRESHOLD_PREFERENCE_KEY = "questionFollowersThreshold"

@Composable
expect fun rememberPaginationEnvironment(allowGuestAccess: Boolean): PaginationEnvironment
