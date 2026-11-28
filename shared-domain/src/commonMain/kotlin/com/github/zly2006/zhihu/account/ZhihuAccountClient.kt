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

package com.github.zly2006.zhihu.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.github.zly2006.zhihu.data.Http403Log
import com.github.zly2006.zhihu.data.HttpFailureKind
import com.github.zly2006.zhihu.data.HttpFailureRecord
import com.github.zly2006.zhihu.data.RecoverableTicketRequest
import com.github.zly2006.zhihu.data.ZHIHU_ME_URL
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.fetchVerifiedZhihuSession
import com.github.zly2006.zhihu.data.installZhihuCommonClientConfig
import com.github.zly2006.zhihu.data.installZhihuMobileClientProfile
import com.github.zly2006.zhihu.data.isZhihuRequestHost
import com.github.zly2006.zhihu.data.resolveVerificationUrl
import com.github.zly2006.zhihu.data.toCookieHeaderString
import com.github.zly2006.zhihu.i18n.AccountStrings
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.util.DisplayFailure
import com.github.zly2006.zhihu.util.ZHIHU_WEB_ZSE93
import com.github.zly2006.zhihu.util.ZhihuCredentialRefresher
import com.github.zly2006.zhihu.util.ZhihuFetchSignature
import com.github.zly2006.zhihu.util.isZhihuLoginRiskControl
import com.github.zly2006.zhihu.util.raiseForStatus
import com.github.zly2006.zhihu.util.signZhihuFetchRequest
import com.github.zly2006.zhihu.viewmodel.AccountAigcVoteEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.api.SendingRequest
import io.ktor.client.plugins.api.SetupRequest
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.AttributeKey
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.files.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class ZhihuRequestProfile { Web, Mobile }

enum class BrowserSessionStatus { Unchecked, MissingBridge, Guest, Authenticated, Expired, Denied, Unavailable }

/** 只跨账号客户端重建保存一次登录反馈，不保留凭据或旧页面回调。 */
class LoginSuccessReceipt internal constructor(val username: String, internal val owner: ZhihuAccountClient)

data class BrowserCommentSendResult(val comment: JsonObject, val restored: Boolean)

internal interface BrowserAccountEngine : HttpClientEngine {
    suspend fun revokeSession(owner: ZhihuAccountClient): Unit = throw BrowserBridgeException(BrowserSessionStatus.Denied)

    suspend fun identityAccounts(owner: ZhihuAccountClient): List<ZhihuIdentityAccount> = throw BrowserBridgeException(BrowserSessionStatus.Denied)

    suspend fun changeIdentity(owner: ZhihuAccountClient, targetUserId: String?): Boolean = throw BrowserBridgeException(BrowserSessionStatus.Denied)

    suspend fun sendCommentAs(owner: ZhihuAccountClient, sender: CommentSender, url: String, body: JsonObject): BrowserCommentSendResult =
        throw BrowserBridgeException(BrowserSessionStatus.Denied)

    suspend fun restoreCommentIdentity(owner: ZhihuAccountClient): Boolean = throw BrowserBridgeException(BrowserSessionStatus.Denied)

    suspend fun sendMessage(owner: ZhihuAccountClient, receiverId: String, content: String): String =
        throw BrowserBridgeException(BrowserSessionStatus.Denied)
}

internal class BrowserBridgeException(
    val status: BrowserSessionStatus,
    historyGrantExpired: Boolean = false,
) : DisplayFailure({
        when (status) {
            BrowserSessionStatus.MissingBridge -> DomainStrings.bridgeMissing
            BrowserSessionStatus.Guest -> DomainStrings.bridgeGuest
            BrowserSessionStatus.Expired -> if (historyGrantExpired) DomainStrings.browserHistoryGrantExpired else DomainStrings.bridgeExpired
            BrowserSessionStatus.Denied -> DomainStrings.bridgeDenied
            else -> DomainStrings.bridgeUnavailable
        }
    })

/** 请求已交给浏览器网络层，但没有可确认的业务结果；调用方必须先核对，不能自动重发。 */
class BrowserMutationUncertain : IllegalStateException("请求可能已生效，但未收到可确认的结果。请先在知乎核对；不会自动重发。")

/** 业务桥在提交写入之前拒绝请求；区别于可能已生效的未知结果。 */
class BrowserBusinessRequestRejected(message: String) : IllegalStateException(message)

internal class BrowserVerificationRequired : DisplayFailure({ AccountStrings.verificationRequiredTitle })

internal val browserRequestOwner = AttributeKey<ZhihuAccountClient>("ZhihuBrowserRequestOwner")
internal val anonymousBrowserRead = AttributeKey<Unit>("ZhihuAnonymousBrowserRead")
internal val mobileBrowserRead = AttributeKey<Unit>("ZhihuMobileBrowserRead")
internal val browserHistoryOwner = AttributeKey<String>("ZhihuBrowserHistoryOwner")

private fun browserProjection(status: Int, body: String, limit: Int): JsonObject {
    if (status == 200 && body.length <= limit) {
        (runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject)?.let { return it }
    }
    throw BrowserBridgeException(BrowserSessionStatus.Denied)
}

/** 具名交互只接收脚本投影的单个布尔值，不把上游错误或网页提供的字符串当作授权。 */
internal fun browserActionFlag(status: Int, body: String, field: String): Boolean = when (browserProjection(status, body, 64).takeIf { it.size == 1 }?.get(field)) {
    JsonPrimitive(true) -> true
    JsonPrimitive(false) -> false
    else -> throw BrowserBridgeException(BrowserSessionStatus.Denied)
}

private val browserQrDisplayLink = Regex("""https://www\.zhihu\.com/account/scan/login/[A-Za-z0-9_-]{1,256}""")

/** 只交给 UI 可本地绘制的官方链接和状态；附带查询、原始票据或其他响应字段均不通过。 */
internal fun browserQrProjection(op: String, status: Int, body: String): JsonObject {
    val data = browserProjection(status, body, if (op == "loginQrStart") 576 else 64)
    val state = (data["state"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    val valid = when (op) {
        "loginQrStart" -> when (state) {
            "ready" -> {
                val link = data["link"] as? JsonPrimitive
                data.size == 2 && link?.isString == true && browserQrDisplayLink.matches(link.content)
            }
            "official", "cancelled" -> data.size == 1
            else -> false
        }
        "loginQrPoll" -> data.size == 1 && state in setOf("waiting", "scanned", "confirmed", "expired")
        else -> false
    }
    if (!valid) throw BrowserBridgeException(BrowserSessionStatus.Denied)
    return data
}

enum class SessionExpiryPrompt { ACTIVE, SHOWING, DISMISSED }

data class SessionExpiryState(
    val phase: SessionExpiryPrompt = SessionExpiryPrompt.ACTIVE,
    val serverMessage: String? = null,
)

class ZhihuLoginRequiredException : IllegalStateException() {
    override val message: String get() = DomainStrings.loginRequired
}

internal val requestCookiesKey = AttributeKey<Map<String, String>>("ZhihuSignedRequestCookies")

class ZhihuAccountClient internal constructor(
    internal val accountStore: ZhihuAccountStore,
    engine: HttpClientEngine,
    val http403Log: Http403Log,
) : AutoCloseable {
    private val accountState: ZhihuAccountState
        get() = accountStore.accountState
    private val accountId = accountState.value.activeAccountId
    private val sessionLock
        get() = accountStore.sessionLock
    private val closed = MutableStateFlow(false)
    val isCurrent: Boolean
        get() = !closed.value && accountState.value.activeAccountId == accountId
    private val credentialRefresher = accountStore.credentialRefresherFor(accountId)
    private val aigcVoteEnvironmentDelegate = lazy { AccountAigcVoteEnvironment(this) }
    internal val aigcVoteEnvironment: AccountAigcVoteEnvironment
        get() = sessionLock.withLock {
            check(isCurrent) { "账号客户端已失效" }
            aigcVoteEnvironmentDelegate.value
        }

    suspend fun refreshCredentials(force: Boolean = false): Boolean = !accountStore.usesBrowserSession &&
        accountStore.commentIdentities.withOriginal(accountId) {
            credentialRefresher.refresh(this, force)
        }

    private val pendingBrowserVerification = if (accountStore.usesBrowserSession) MutableStateFlow<BrowserVerification?>(null) else null
    val browserVerification = pendingBrowserVerification?.asStateFlow()
    private val pendingCommentRestore = MutableStateFlow(false)
    val browserCommentRestoreRequired: StateFlow<Boolean> = pendingCommentRestore.asStateFlow()

    internal fun reportBrowserCommentRestoreRequired() {
        if (isCurrent && accountStore.usesBrowserSession) pendingCommentRestore.value = true
    }

    internal fun clearBrowserCommentRestoreRequired() {
        if (isCurrent) pendingCommentRestore.value = false
    }

    /** 操作只属于发出 challenge 的 client 和这一轮验证；URL、session 均由本标签页脚本持有。 */
    inner class BrowserVerification internal constructor(
        private val openPage: () -> Boolean,
        private val checkPage: suspend () -> Boolean,
    ) {
        var opened by mutableStateOf(false)
            private set
        var checking by mutableStateOf(false)
            private set

        fun open(): Boolean = sessionLock.withLock {
            if (!isCurrent || pendingBrowserVerification?.value !== this || checking) return@withLock false
            openPage().also { if (it) opened = true }
        }

        suspend fun check(): Boolean {
            currentCoroutineContext().ensureActive()
            sessionLock.withLock {
                if (!isCurrent || pendingBrowserVerification?.value !== this || !opened || checking) return false
                checking = true
            }
            return try {
                val verified = checkPage()
                currentCoroutineContext().ensureActive()
                sessionLock.withLock { verified && isCurrent && pendingBrowserVerification?.compareAndSet(this, null) == true }
            } finally {
                sessionLock.withLock { checking = false }
            }
        }

        fun dismiss() {
            pendingBrowserVerification?.compareAndSet(this, null)
        }
    }

    internal fun reportBrowserVerification(openPage: () -> Boolean, checkPage: suspend () -> Boolean) = sessionLock.withLock {
        if (isCurrent) pendingBrowserVerification?.value = BrowserVerification(openPage, checkPage)
    }

    private fun HttpClientConfig<*>.installBrowserReads(anonymous: Boolean = false, mobile: Boolean = false, verifiedId: String? = null) {
        followRedirects = false
        install(ContentNegotiation) { json(ZhihuJson.json) }
        defaultRequest {
            attributes.put(browserRequestOwner, this@ZhihuAccountClient)
            if (anonymous) attributes.put(anonymousBrowserRead, Unit)
            if (mobile) attributes.put(mobileBrowserRead, Unit)
            if (verifiedId != null) attributes.put(browserHistoryOwner, verifiedId)
        }
    }

    private val mutableVerificationState = MutableStateFlow(HumanVerificationState())
    val verificationState: StateFlow<HumanVerificationState> = mutableVerificationState.asStateFlow()

    fun reportHumanVerification(url: String?, candidateCookies: Map<String, String>? = null) {
        if (closed.value || accountStore.usesBrowserSession) return
        val trusted = resolveVerificationUrl(Url("https://www.zhihu.com/account/unhuman"), url) ?: return
        mutableVerificationState.update {
            if (!closed.value && it.phase == HumanVerificationPhase.ACTIVE) {
                it.copy(phase = HumanVerificationPhase.PROMPT, url = trusted, candidateCookies = candidateCookies?.toMap())
            } else {
                it
            }
        }
    }

    fun beginHumanVerification() {
        if (closed.value) return
        mutableVerificationState.update {
            if (it.url != null && it.phase == HumanVerificationPhase.PROMPT) {
                it.copy(phase = HumanVerificationPhase.VERIFYING)
            } else {
                it
            }
        }
    }

    fun acknowledgeVerificationRestart() {
        mutableVerificationState.update {
            if (it.phase == HumanVerificationPhase.RESTART_REQUIRED) it.copy(phase = HumanVerificationPhase.DISMISSED) else it
        }
    }

    /** 只接受仍在验证的所属会话；保存成功后提示重启，不重放业务请求。 */
    suspend fun completeHumanVerification(cookies: Map<String, String>) {
        if (closed.value || mutableVerificationState.value.phase != HumanVerificationPhase.VERIFYING) return
        val candidate = mutableVerificationState.value.candidateCookies
        if (candidate == null) {
            val current = load()
            val merged = current.cookies.toMutableMap().apply { putAll(cookies) }
            save(current.copy(cookies = merged))
        } else {
            accountState.saveGuestVerificationCookies(candidate + cookies)
        }
        http403Log.clear(stopRecording = true)
        mutableVerificationState.update {
            if (!closed.value && it.phase == HumanVerificationPhase.VERIFYING) it.copy(phase = HumanVerificationPhase.RESTART_REQUIRED) else it
        }
    }

    /** 仅用户点击时检查官方解除状态；未通过不得用“我已完成”跳过验证。 */
    suspend fun confirmHumanVerification(updatedCookies: Map<String, String>): Boolean {
        val verification = mutableVerificationState.value
        if (closed.value || verification.phase != HumanVerificationPhase.VERIFYING) return false
        val url = verification.url ?: return false
        val baseline = (verification.candidateCookies ?: load().cookies).toMap()
        val verificationCookies = (baseline + updatedCookies).toMutableMap()
        val verifier = createAccountHttpClient(
            verificationCookies,
            ZHIHU_DESKTOP_USER_AGENT,
            client.engine,
            onFailure = { reportHttpFailure(it, verification.candidateCookies) },
        )
        return try {
            val response = verifier.get("https://www.zhihu.com/api/v4/anticrawl/new_captcha_appeal") {
                header(HttpHeaders.Referrer, url)
                Url(url).parameters["session"]?.let { parameter("session", it) }
            }
            if (!response.status.isSuccess()) return false
            val result = ZhihuJson.snakeCaseToCamelCase(ZhihuJson.json.parseToJsonElement(response.bodyAsText())) as? JsonObject
                ?: return false
            if (result["error"] != null && result["error"] != JsonNull) return false
            val blockLevel = result["blockLevel"]?.jsonPrimitive?.takeUnless { it.isString }?.intOrNull
            val code = result["code"]?.jsonPrimitive?.takeUnless { it.isString }?.intOrNull
            if (blockLevel != 0 && code != 0) return false
            currentCoroutineContext().ensureActive()
            if (closed.value || mutableVerificationState.value != verification) return false
            completeHumanVerification(verificationCookies.filter { (name, value) -> baseline[name] != value })
            true
        } finally {
            verifier.close()
        }
    }

    private val mutableExpiryPrompt = MutableStateFlow(SessionExpiryState())
    val expiryPrompt: StateFlow<SessionExpiryState> = mutableExpiryPrompt.asStateFlow()
    private val mutableLoginPrompt = MutableStateFlow(SessionExpiryPrompt.ACTIVE)
    val loginPrompt: StateFlow<SessionExpiryPrompt> = mutableLoginPrompt.asStateFlow()

    fun reportLoginRequired() {
        if (isCurrent) mutableLoginPrompt.compareAndSet(SessionExpiryPrompt.ACTIVE, SessionExpiryPrompt.SHOWING)
    }

    fun dismissLoginRequirement() {
        mutableLoginPrompt.compareAndSet(SessionExpiryPrompt.SHOWING, SessionExpiryPrompt.DISMISSED)
    }

    /** 只接受所属客户端当前凭据的结果；迟到的通用票据错误不能覆盖明确的拒刷原因。 */
    internal fun reportSessionExpired(rejection: InvalidRefreshTokenException? = null, expectedRefreshToken: String? = null): Boolean = sessionLock.withLock {
        if (!isCurrent || !load().login || (expectedRefreshToken != null && load().mobileRefreshToken != expectedRefreshToken)) return@withLock false
        val prompt = mutableExpiryPrompt.value
        if (prompt.phase != SessionExpiryPrompt.DISMISSED) {
            mutableExpiryPrompt.value = SessionExpiryState(SessionExpiryPrompt.SHOWING, rejection?.serverMessage ?: prompt.serverMessage)
        }
        true
    }

    fun dismissSessionExpiry() = sessionLock.withLock {
        if (mutableExpiryPrompt.value.phase == SessionExpiryPrompt.SHOWING) mutableExpiryPrompt.value = SessionExpiryState(SessionExpiryPrompt.DISMISSED)
    }

    fun clearExpiredSession(): Boolean = sessionLock.withLock {
        if (!isCurrent || mutableExpiryPrompt.value.phase != SessionExpiryPrompt.SHOWING || verificationState.value.blocksSessionChange) return@withLock false
        accountStore.clear()
        true
    }

    private val userAgent = accountState.value.session.userAgent
    private val cookies = accountState.value.session.cookies
        .toMutableMap()
    private val onCookieChanged: () -> Unit = {
        sessionLock.withLock {
            if (isCurrent) accountState.saveCurrent(load().copy(cookies = cookies.toMutableMap()))
        }
    }

    suspend fun reportHttpFailure(record: HttpFailureRecord, candidateCookies: Map<String, String>? = null) {
        val url = runCatching { Url(record.url) }.getOrNull() ?: return
        if ((url.protocol != URLProtocol.HTTP && url.protocol != URLProtocol.HTTPS) ||
            !isZhihuRequestHost(url.host)
        ) {
            return
        }
        if (!closed.value) {
            if (candidateCookies == null && record.kind == HttpFailureKind.SESSION_EXPIRED) reportSessionExpired()
            if (record.kind == HttpFailureKind.LOGIN_REQUIRED) reportLoginRequired()
            if (record.kind == HttpFailureKind.HUMAN_VERIFICATION) reportHumanVerification(record.verificationUrl, candidateCookies)
        }
        http403Log.record(record)
    }

    /** 签名与最终 Cookie 必须来自同一快照，不能被 HttpCookies 在发送阶段换成新值。 */
    private val requestCookieSnapshot = createClientPlugin("ZhihuSignedCookieSnapshot", { }) {
        on(SendingRequest) { request, _ ->
            request.attributes.getOrNull(requestCookiesKey)?.let { snapshot ->
                if (!isCurrent) throw CancellationException("请求期间账号已切换")
                request.headers.remove(HttpHeaders.Cookie)
                if (request.url.protocol == URLProtocol.HTTPS && isZhihuRequestHost(request.url.host)) {
                    snapshot.toCookieHeaderString().takeIf(String::isNotBlank)?.let { request.headers[HttpHeaders.Cookie] = it }
                } else {
                    request.headers.remove(HttpHeaders.Authorization)
                    request.headers.remove("x-xsrftoken")
                    request.headers.remove("x-zse-93")
                    request.headers.remove("x-zse-96")
                }
            }
        }
    }
    private val client = HttpClient(engine) {
        if (accountStore.usesBrowserSession) {
            installBrowserReads(verifiedId = accountId)
            return@HttpClient
        }
        installZhihuCommonClientConfig(
            cookies,
            userAgent,
            onCookieChanged,
            cookieLock = sessionLock,
            loggedIn = { load().login },
            onFailure = { reportHttpFailure(it) },
        )
        install(requestCookieSnapshot)
    }.also { accountStore.commentIdentities.bind(it, accountId, this) }

    /** 匿名设备 Cookie 只归当前 client 所有，绝不读取或写入登录 session。 */
    private data class AnonymousCookie(
        val cookie: Cookie,
        val hostOnly: Boolean,
    )

    private val anonymousCookies = mutableMapOf<Triple<String, String, String>, AnonymousCookie>()
    private var anonymousInitialization: CompletableDeferred<Unit>? = null
    private val activeRequests = mutableSetOf<Job>()
    private val anonymousCookieStorage = object : CookiesStorage {
        override suspend fun addCookie(requestUrl: Url, cookie: Cookie) = sessionLock.withLock {
            if (!isCurrent || requestUrl.protocol != URLProtocol.HTTPS || !isZhihuRequestHost(requestUrl.host)) return@withLock
            if (cookie.name !in setOf("d_c0", "_xsrf", "__zse_ck")) return@withLock
            val domain = cookie.domain?.trimStart('.')?.lowercase() ?: requestUrl.host.lowercase()
            if (domain != "zhihu.com" && !isZhihuRequestHost(domain)) return@withLock
            if (requestUrl.host.lowercase() != domain && !requestUrl.host.lowercase().endsWith(".$domain")) return@withLock
            val path = cookie.path ?: requestUrl.encodedPath.substringBeforeLast('/', "").ifEmpty { "/" }
            val identity = Triple(cookie.name, domain, path)
            if (cookie.value.isBlank() || cookie.maxAge?.let { it <= 0 } == true) {
                anonymousCookies.remove(identity)
            } else {
                anonymousCookies[identity] = AnonymousCookie(cookie.copy(domain = domain, path = path), hostOnly = cookie.domain == null)
            }
        }

        override suspend fun get(requestUrl: Url): List<Cookie> = sessionLock.withLock {
            if (!isCurrent || requestUrl.protocol != URLProtocol.HTTPS || !isZhihuRequestHost(requestUrl.host)) return@withLock emptyList()
            anonymousCookies.values
                .filter { stored ->
                    val cookie = stored.cookie
                    val domain = cookie.domain.orEmpty()
                    val path = cookie.path ?: "/"
                    (requestUrl.host.lowercase() == domain || (!stored.hostOnly && requestUrl.host.lowercase().endsWith(".$domain"))) &&
                        (requestUrl.encodedPath == path || requestUrl.encodedPath.startsWith(path.trimEnd('/') + "/"))
                }.map { it.cookie }
        }

        override fun close() = Unit
    }
    private val anonymousWebProfile = createClientPlugin("ZhihuAnonymousWebProfile", { }) {
        on(SetupRequest) { request ->
            if (request.url.protocol == URLProtocol.HTTPS && isZhihuRequestHost(request.url.host)) {
                val bootstrap = request.url.host == "www.zhihu.com" && request.url.build().encodedPath in setOf("/signin", "/udid")
                if (!bootstrap) initializeAnonymousWeb()
                val snapshot = anonymousCookieStorage.get(request.url.build()).associate { it.name to it.value }
                request.headers.remove(HttpHeaders.Authorization)
                request.headers.remove(HttpHeaders.Cookie)
                request.headers.remove("x-xsrftoken")
                request.attributes.put(requestCookiesKey, snapshot)
                if (!bootstrap || request.url.build().encodedPath == "/udid") {
                    snapshot["_xsrf"]?.let { request.headers["x-xsrftoken"] = it }
                    request.signZhihuFetchRequest(snapshot)
                }
            }
        }
    }
    private val anonymousWebClient = lazy {
        HttpClient(client.engine) {
            followRedirects = false
            installZhihuCommonClientConfig(
                mutableMapOf(),
                ZHIHU_DESKTOP_USER_AGENT,
                enableCookies = false,
                loggedIn = { false },
                onFailure = { reportHttpFailure(it) },
            )
            install(HttpCookies) { storage = anonymousCookieStorage }
            install(anonymousWebProfile)
            install(requestCookieSnapshot)
        }
    }

    private suspend fun initializeAnonymousWeb() {
        var leader = false
        val initialization = sessionLock.withLock {
            if (!isCurrent) throw CancellationException("匿名客户端已关闭")
            anonymousInitialization ?: CompletableDeferred<Unit>().also {
                anonymousInitialization = it
                leader = true
            }
        }
        if (!leader) return initialization.await()
        try {
            val transport = anonymousWebClient.value
            val signin = transport
                .get("https://www.zhihu.com/signin?next=%2F") {
                    header(HttpHeaders.Referrer, "https://www.zhihu.com/")
                }.raiseForStatus()
            if (!signin.status.isSuccess()) {
                val code = signin.status.value
                throw DisplayFailure { DomainStrings.guestRedirectHttp(code) }
            }
            if (signin.request.url.isZhihuLoginRiskControl()) throw DisplayFailure { DomainStrings.guestVerification }
            val device = transport
                .post("https://www.zhihu.com/udid") {
                    header(HttpHeaders.Referrer, "https://www.zhihu.com/signin")
                    header(HttpHeaders.Origin, "https://www.zhihu.com")
                }.raiseForStatus()
            if (!device.status.isSuccess()) {
                val code = device.status.value
                throw DisplayFailure { DomainStrings.guestRedirectHttp(code) }
            }
            currentCoroutineContext().ensureActive()
            if (!isCurrent) throw CancellationException("匿名客户端已关闭")
            if (device.request.url.isZhihuLoginRiskControl()) throw DisplayFailure { DomainStrings.guestVerification }
            if (anonymousCookieStorage.get(Url("https://www.zhihu.com/")).none { it.name == "d_c0" && it.value.isNotBlank() }) throw DisplayFailure { DomainStrings.guestNoDeviceCookie }
            initialization.complete(Unit)
        } catch (error: Throwable) {
            sessionLock.withLock {
                if (anonymousInitialization === initialization) {
                    anonymousCookies.clear()
                    anonymousInitialization = null
                }
                initialization.completeExceptionally(error)
            }
            throw error
        }
    }

    private val guestClient = lazy {
        HttpClient(client.engine) {
            if (accountStore.usesBrowserSession) {
                installBrowserReads(anonymous = true)
                return@HttpClient
            }
            installZhihuCommonClientConfig(
                cookies,
                userAgent,
                enableHttpCache = true,
                enableCookies = false,
                loggedIn = { load().login },
                onFailure = { reportHttpFailure(it) },
            )
        }
    }

    // 移动 GET 需要补 zse96；只捕获所属会话的 Cookie，旧连接不能改投新账号。
    private val mobileSignature = createClientPlugin("ZhihuPPZseSignature", { }) {
        onRequest { request, _ ->
            if (isZhihuRequestHost(request.url.host) && request.method == HttpMethod.Get && request.headers["x-zse-96"] == null) {
                val dc0 = sessionLock.withLock { cookies["d_c0"]?.takeIf { it.isNotBlank() } }
                if (dc0 != null) {
                    val zse93 = request.headers["x-zse-93"]?.takeIf { it.isNotBlank() } ?: ZHIHU_WEB_ZSE93
                    request.headers["x-zse-93"] = zse93
                    request.headers["x-zse-96"] =
                        ZhihuFetchSignature.createZse96Header(zse93, request.url.toString(), dc0)
                }
            }
        }
    }
    private val mobileClient = lazy {
        HttpClient(client.engine) {
            if (accountStore.usesBrowserSession) {
                installBrowserReads(mobile = true)
                return@HttpClient
            }
            installZhihuCommonClientConfig(
                cookies,
                userAgent.ifBlank { DEFAULT_ZHIHU_USER_AGENT },
                onCookieChanged,
                cookieLock = sessionLock,
                loggedIn = { load().login },
                onFailure = { reportHttpFailure(it) },
            )
            installZhihuMobileClientProfile()
            install(mobileSignature)
            install(requestCookieSnapshot)
        }.also { accountStore.commentIdentities.bind(it, accountId, this) }
    }
    private val guestMobileClient = lazy {
        HttpClient(client.engine) {
            if (accountStore.usesBrowserSession) {
                installBrowserReads(anonymous = true, mobile = true)
                return@HttpClient
            }
            installZhihuCommonClientConfig(
                cookies,
                userAgent.ifBlank { DEFAULT_ZHIHU_USER_AGENT },
                enableCookies = false,
                loggedIn = { load().login },
                onFailure = { reportHttpFailure(it) },
            )
            installZhihuMobileClientProfile()
            install(mobileSignature)
            install(requestCookieSnapshot)
        }
    }

    fun load(): ZhihuAccountSession = sessionLock.withLock {
        val accounts = accountState.value
        accounts.accounts.firstOrNull { it.id == accountId }?.session
            ?: accounts.session.takeIf { accountId == null && isCurrent }
            ?: error("账号客户端已失效")
    }

    fun save(session: ZhihuAccountSession) = sessionLock.withLock {
        accountStore.requireNativeAccount()
        check(isCurrent) { "ZhihuAccountClient 已关闭" }
        require(load().hasSameIdentityAs(session)) {
            "ZhihuAccountClient 只能刷新同一账号的 session；切换身份必须替换 client"
        }
        val current = load()
        val credentialsChanged = current.cookies["z_c0"] != session.cookies["z_c0"] || current.mobileRefreshToken != session.mobileRefreshToken
        persistSession(session)
        if (credentialsChanged) mutableExpiryPrompt.value = SessionExpiryState()
    }

    private fun persistSession(session: ZhihuAccountSession) = sessionLock.withLock {
        // HttpClient.close() 允许在途响应完成；已被退出/切换替换的 client 不得再把旧会话写回账户 store。
        if (!isCurrent) return@withLock
        val updatedCookies = session.cookies.toMap()
        accountState.saveCurrent(session.copy(cookies = updatedCookies.toMutableMap()))
        cookies.clear()
        cookies.putAll(updatedCookies)
    }

    fun httpClient(useCookies: Boolean = true): HttpClient = httpClient(ZhihuRequestProfile.Web, useCookies)

    fun httpClient(profile: ZhihuRequestProfile, useCookies: Boolean = true): HttpClient = sessionLock.withLock {
        check(isCurrent) { "ZhihuAccountClient 已关闭" }
        if (accountStore.usesBrowserSession) {
            if (profile == ZhihuRequestProfile.Mobile) {
                if (useCookies) mobileClient.value else guestMobileClient.value
            } else {
                if (useCookies) client else guestClient.value
            }
        } else if (!load().login && !useCookies) {
            if (profile == ZhihuRequestProfile.Mobile) throw ZhihuLoginRequiredException()
            anonymousWebClient.value
        } else if (profile == ZhihuRequestProfile.Mobile) {
            if (useCookies) mobileClient.value else guestMobileClient.value
        } else {
            if (useCookies) client else guestClient.value
        }
    }

    fun mobileHomeFeedHttpClient(useCookies: Boolean = true): HttpClient = httpClient(ZhihuRequestProfile.Mobile, useCookies)

    /** 浏览器私信只交结构化业务值；加密、签名和凭据归具名请求桥。 */
    suspend fun sendBrowserMessage(receiverId: String, content: String): String {
        check(isCurrent) { "账号客户端已失效" }
        val bridge = accountStore.engine as? BrowserAccountEngine ?: error("当前账号不是浏览器会话")
        return bridge.sendMessage(this, receiverId, content)
    }

    /** 导出等跨多个请求的操作属于当前 client；切号关闭时取消整条流水线。 */
    internal suspend fun <T> withCurrentOwnerOperation(block: suspend () -> T): T = coroutineScope {
        val operation = currentCoroutineContext().job
        sessionLock.withLock {
            if (!isCurrent) throw CancellationException(DomainStrings.accountChangedReopen)
            activeRequests += operation
        }
        try {
            coroutineScope { block() }
        } finally {
            sessionLock.withLock { activeRequests -= operation }
        }
    }

    /** POST 前捕获本机会话的标读观察者；仅服务端确认成功后调用返回的单次回调。 */
    fun beginNotificationReadAll(entryName: String): () -> Unit = accountStore.beginNotificationReadAll(this, entryName)

    suspend fun request(
        url: String,
        method: HttpMethod = HttpMethod.Get,
        profile: ZhihuRequestProfile = ZhihuRequestProfile.Web,
        useCookies: Boolean = true,
        block: suspend HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse = accountStore.commentIdentities.withOriginal(accountId) {
        requestBound(url, method, profile, useCookies, block)
    }

    private suspend fun requestBound(
        url: String,
        method: HttpMethod,
        profile: ZhihuRequestProfile,
        useCookies: Boolean,
        block: suspend HttpRequestBuilder.() -> Unit,
    ): HttpResponse {
        val anonymous = !accountStore.usesBrowserSession && !load().login && !useCookies
        val transport = httpClient(profile, useCookies)
        val job = currentCoroutineContext().job
        sessionLock.withLock {
            if (!isCurrent) throw CancellationException("账号客户端已关闭")
            activeRequests += job
        }
        try {
            if (anonymous) initializeAnonymousWeb()

            suspend fun send(firstAttempt: Boolean): HttpResponse = transport.request(url) {
                this.method = method
                block()
                currentCoroutineContext().ensureActive()
                if (accountStore.usesBrowserSession) return@request
                val snapshot = if (anonymous) {
                    anonymousCookieStorage.get(this.url.build()).associate { it.name to it.value }
                } else {
                    sessionLock.withLock {
                        if (!isCurrent) throw CancellationException("请求期间账号已切换")
                        if (firstAttempt && useCookies && method == HttpMethod.Get && this.method == HttpMethod.Get && load().canRenewCredentials) {
                            attributes.put(RecoverableTicketRequest, Unit)
                        }
                        if (useCookies) cookies.toMap() else emptyMap()
                    }
                }
                attributes.put(requestCookiesKey, snapshot)
                if (profile == ZhihuRequestProfile.Web && isZhihuRequestHost(this.url.host)) {
                    snapshot["_xsrf"]?.takeIf(String::isNotBlank)?.let { headers["x-xsrftoken"] = it }
                    signZhihuFetchRequest(snapshot)
                }
            }
            val response = send(firstAttempt = true)
            currentCoroutineContext().ensureActive()
            if (!isCurrent) throw CancellationException("请求期间账号已切换")
            if (!response.request.attributes.contains(RecoverableTicketRequest) ||
                response.request.method != HttpMethod.Get ||
                response.status != HttpStatusCode.Unauthorized ||
                !isZhihuRequestHost(response.request.url.host)
            ) {
                return response
            }
            if (!isCurrent) return response
            val body = response.bodyAsText()
            val ticketExpired = runCatching {
                val error = ZhihuJson.json
                    .parseToJsonElement(body)
                    .jsonObject["error"]
                    ?.jsonObject
                error?.get("code")?.jsonPrimitive?.intOrNull == 100 &&
                    error["message"]?.jsonPrimitive?.content in setOf("ERR_LOGIN_TICKET_EXPIRED", "ERR_TICKET_NOT_EXIST")
            }.getOrDefault(false)
            if (!ticketExpired) return response
            currentCoroutineContext().ensureActive()
            val rejectedCookie = response.request.attributes[requestCookiesKey]["z_c0"]
            if (!credentialRefresher.refresh(this, force = true, rejectedCookie = rejectedCookie)) {
                if (isCurrent && load().cookies["z_c0"] == rejectedCookie) reportSessionExpired()
                return response
            }
            if (!isCurrent) throw CancellationException("续期期间账号已切换")
            return send(firstAttempt = false).raiseForStatus()
        } finally {
            sessionLock.withLock { activeRequests -= job }
        }
    }

    suspend fun get(url: String, profile: ZhihuRequestProfile = ZhihuRequestProfile.Web, block: suspend HttpRequestBuilder.() -> Unit = {}): HttpResponse = request(url, HttpMethod.Get, profile, block = block)

    suspend fun post(url: String, profile: ZhihuRequestProfile = ZhihuRequestProfile.Web, block: suspend HttpRequestBuilder.() -> Unit = {}): HttpResponse = request(url, HttpMethod.Post, profile, block = block)

    suspend fun put(url: String, profile: ZhihuRequestProfile = ZhihuRequestProfile.Web, block: suspend HttpRequestBuilder.() -> Unit = {}): HttpResponse = request(url, HttpMethod.Put, profile, block = block)

    suspend fun patch(url: String, profile: ZhihuRequestProfile = ZhihuRequestProfile.Web, block: suspend HttpRequestBuilder.() -> Unit = {}): HttpResponse = request(url, HttpMethod.Patch, profile, block = block)

    suspend fun delete(url: String, profile: ZhihuRequestProfile = ZhihuRequestProfile.Web, block: suspend HttpRequestBuilder.() -> Unit = {}): HttpResponse = request(url, HttpMethod.Delete, profile, block = block)

    suspend fun refreshAndSaveProfile(): ZhihuAccountSession? {
        if (accountStore.usesBrowserSession) {
            if (!isCurrent) throw CancellationException("账号客户端已关闭")
            return if (accountStore.refreshBrowserSession() == BrowserSessionStatus.Authenticated) accountStore.session else null
        }
        val current = load()
        val refreshed = fetchVerifiedZhihuSession(client, current.cookies, current.userAgent) ?: return null
        return sessionLock.withLock {
            if (!isCurrent) return@withLock null
            val latest = load()
            val latestProfileId = latest.profile?.id
            if (latestProfileId != null && latestProfileId != refreshed.profile?.id) return@withLock null
            latest.copy(username = refreshed.username, profile = refreshed.profile, self = refreshed.self).also(::persistSession)
        }
    }

    suspend fun <T> withAuthenticatedClient(
        block: suspend (client: HttpClient, cookies: Map<String, String>) -> T,
    ): T = accountStore.commentIdentities.withOriginal(accountId) {
        val (boundClient, cookieSnapshot) = sessionLock.withLock {
            check(isCurrent) { "账号客户端已失效" }
            if (load().login) client to cookies.toMap() else guestClient.value to emptyMap()
        }
        block(boundClient, cookieSnapshot)
    }

    override fun close() = sessionLock.withLock {
        if (!closed.compareAndSet(false, true)) return@withLock
        pendingBrowserVerification?.value = null
        mutableExpiryPrompt.value = SessionExpiryState(SessionExpiryPrompt.DISMISSED)
        mutableLoginPrompt.value = SessionExpiryPrompt.DISMISSED
        mutableVerificationState.value = HumanVerificationState(HumanVerificationPhase.DISMISSED)
        anonymousInitialization?.cancel(CancellationException("匿名客户端已关闭"))
        activeRequests.toList().forEach { it.cancel(CancellationException("请求期间账号已切换")) }
        activeRequests.clear()
        anonymousCookies.clear()
        if (aigcVoteEnvironmentDelegate.isInitialized()) aigcVoteEnvironmentDelegate.value.close()
        if (accountStore.usesBrowserSession) {
            client.coroutineContext.job.cancel()
            if (guestClient.isInitialized()) {
                guestClient.value.coroutineContext.job
                    .cancel()
            }
            if (mobileClient.isInitialized()) {
                mobileClient.value.coroutineContext.job
                    .cancel()
            }
            if (guestMobileClient.isInitialized()) {
                guestMobileClient.value.coroutineContext.job
                    .cancel()
            }
        }
        if (anonymousWebClient.isInitialized()) {
            anonymousWebClient.value.coroutineContext.job
                .cancel(CancellationException("匿名客户端已关闭"))
            anonymousWebClient.value.close()
        }
        if (guestClient.isInitialized()) guestClient.value.close()
        if (mobileClient.isInitialized()) mobileClient.value.close()
        if (guestMobileClient.isInitialized()) guestMobileClient.value.close()
        client.close()
    }
}

class ZhihuAccountStore(
    repository: ZhihuAccountRepository?,
    engine: HttpClientEngine? = null,
) : AutoCloseable {
    internal val sessionLock = reentrantLock()

    /** 域外依赖账号状态的操作经这里与账号切换互斥，不另建第二把锁。 */
    fun <T> withSessionLock(block: () -> T): T = sessionLock.withLock(block)

    private val notificationReadAllObservers = mutableSetOf<(ZhihuAccountClient, String) -> (() -> Unit)?>()

    /** 观察者在请求开始时取快照，返回成功时的副作用；两个阶段都与账号切换互斥。 */
    fun observeNotificationReadAll(observer: (ZhihuAccountClient, String) -> (() -> Unit)?): AutoCloseable = sessionLock.withLock {
        check(!closed.value) { "ZhihuAccountStore 已关闭" }
        notificationReadAllObservers += observer
        object : AutoCloseable {
            override fun close() = sessionLock.withLock {
                notificationReadAllObservers -= observer
                Unit
            }
        }
    }

    internal fun beginNotificationReadAll(owner: ZhihuAccountClient, entryName: String): () -> Unit = sessionLock.withLock {
        check(!closed.value && mutableClient === owner && owner.isCurrent) { "账号已切换" }
        val confirmations = notificationReadAllObservers.toList().mapNotNull { observer -> observer(owner, entryName)?.let { observer to it } }
        var consumed = false
        return@withLock {
            sessionLock.withLock {
                if (!consumed) {
                    consumed = true
                    if (!closed.value && mutableClient === owner && owner.isCurrent) {
                        confirmations.forEach { (observer, confirm) ->
                            if (observer in notificationReadAllObservers) confirm()
                        }
                    }
                }
            }
        }
    }

    private val credentialRefreshers = mutableMapOf<String?, ZhihuCredentialRefresher>()
    val commentIdentities = CommentIdentities(this)

    // 传入 engine 即移交所有权；会话只借用，换账号不关闭，store 统一释放。
    val engine: HttpClientEngine = engine ?: accountHttpClientEngineFactory.create()
    val usesBrowserSession: Boolean = this.engine is BrowserAccountEngine
    internal val accountState = try {
        require((repository == null) == usesBrowserSession) { "浏览器会话不能绑定本地账号文件" }
        ZhihuAccountState(repository)
    } catch (error: Throwable) {
        runCatching { this.engine.close() }.exceptionOrNull()?.let { if (it !== error) error.addSuppressed(it) }
        throw error
    }

    val http403Log = Http403Log(repository?.let { Path(it.directory, "network-diagnostics", "http403.json") })
    private val browserProbe = Mutex()
    private val mutableBrowserStatus = MutableStateFlow(BrowserSessionStatus.Unchecked)
    val browserSessionStatus: StateFlow<BrowserSessionStatus> = mutableBrowserStatus.asStateFlow()
    private val pendingLoginSuccess = MutableStateFlow<LoginSuccessReceipt?>(null)
    val loginSuccessReceipt: StateFlow<LoginSuccessReceipt?> = pendingLoginSuccess.asStateFlow()

    fun reportLoginSuccess(expectedClient: ZhihuAccountClient, username: String): Boolean = sessionLock.withLock {
        if (closed.value || mutableClient !== expectedClient || !expectedClient.isCurrent || !session.login || session.username != username) return@withLock false
        pendingLoginSuccess.value = LoginSuccessReceipt(username, expectedClient)
        true
    }

    fun consumeLoginSuccess(receipt: LoginSuccessReceipt): Boolean = sessionLock.withLock {
        if (pendingLoginSuccess.value !== receipt || mutableClient !== receipt.owner || !receipt.owner.isCurrent) return@withLock false
        pendingLoginSuccess.value = null
        true
    }
    private val closed = MutableStateFlow(false)
    private var mutableClient by mutableStateOf(
        try {
            ZhihuAccountClient(this, this.engine, http403Log)
        } catch (error: Throwable) {
            runCatching { this.engine.close() }.exceptionOrNull()?.let { if (it !== error) error.addSuppressed(it) }
            throw error
        },
    )

    val accountsState: StateFlow<ZhihuAccounts> = accountState.state.asStateFlow()
    val session: ZhihuAccountSession
        get() = accountState.value.session
    val accounts: List<ZhihuSavedAccount>
        get() = accountState.value.accounts
    val client: ZhihuAccountClient
        get() = sessionLock.withLock { mutableClient }

    /** 只有经本标签页授权的 /me 能建立浏览器身份；账号文件、网页声明和旧会话均不能替代。 */
    suspend fun refreshBrowserSession(expectedAccountId: String? = null): BrowserSessionStatus = browserProbe.withLock {
        check(usesBrowserSession && !closed.value) { "没有可用的浏览器账号会话" }
        val owner = client
        var profile: ZhihuAccountProfileSnapshot? = null
        val result = try {
            val response = owner.httpClient().get(ZHIHU_ME_URL)
            if (response.status == HttpStatusCode.OK) {
                profile = ZhihuJson
                    .decodeJson<ZhihuAccountProfileSnapshot>(ZhihuJson.json.parseToJsonElement(response.bodyAsText()))
                    .takeIf { it.id.isNotBlank() && it.name.isNotBlank() && !it.id.all { c -> c == '0' } }
                if (profile != null && (expectedAccountId == null || profile.id == expectedAccountId)) BrowserSessionStatus.Authenticated else BrowserSessionStatus.Denied
            } else {
                BrowserSessionStatus.Unavailable
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: BrowserVerificationRequired) {
            currentCoroutineContext().ensureActive()
            if (!owner.isCurrent) throw CancellationException("浏览器账号状态检查已取消")
            // 验证不是 /me 身份结果：不宣布登录成功，也不把已验证账号换成访客。
            return@withLock BrowserSessionStatus.Unavailable
        } catch (e: BrowserBridgeException) {
            e.status
        } catch (_: Exception) {
            BrowserSessionStatus.Unavailable
        }
        currentCoroutineContext().ensureActive()
        sessionLock.withLock {
            if (!owner.isCurrent || closed.value) throw CancellationException("浏览器账号状态检查已取消")
            val status = if (result == BrowserSessionStatus.Guest && (session.login || mutableBrowserStatus.value == BrowserSessionStatus.Expired)) {
                BrowserSessionStatus.Expired
            } else {
                result
            }
            // 请求失败不等于退出登录；只有 /me 确认身份或明确失效才更换账号所有者。
            if (status == BrowserSessionStatus.Authenticated || status == BrowserSessionStatus.Guest || status == BrowserSessionStatus.Expired) {
                if (session.profile?.id != profile?.id) {
                    replaceClient { accountState.setBrowserProfile(profile) }
                } else if (session.profile != profile) {
                    accountState.setBrowserProfile(profile)
                }
            }
            mutableBrowserStatus.value = status
            status
        }
    }

    internal fun requireNativeAccount() {
        if (usesBrowserSession) throw BrowserBridgeException(BrowserSessionStatus.Denied)
    }

    fun save(session: ZhihuAccountSession) = sessionLock.withLock {
        check(!closed.value) { "ZhihuAccountStore 已关闭" }
        if (this.session.hasSameIdentityAs(session)) {
            mutableClient.save(session)
        } else {
            replaceSession(session)
        }
    }

    fun replaceSession(session: ZhihuAccountSession) = sessionLock.withLock {
        requireNativeAccount()
        replaceClient {
            accountState.saveCurrent(session, reauthenticated = true)
            credentialRefreshers.remove(accountState.value.activeAccountId)
        }
    }

    /** 浏览器只撤本应用授权；官网会话与其 Cookie 仍由浏览器管理。 */
    suspend fun signOut(owner: ZhihuAccountClient): Boolean {
        if (!owner.isCurrent || client !== owner || owner.verificationState.value.blocksSessionChange) return false
        if (!usesBrowserSession) return sessionLock.withLock {
            if (mutableClient !== owner || owner.verificationState.value.blocksSessionChange) return@withLock false
            clear()
            true
        }
        (engine as BrowserAccountEngine).revokeSession(owner)
        currentCoroutineContext().ensureActive()
        return sessionLock.withLock {
            if (closed.value || mutableClient !== owner) return@withLock false
            replaceClient { accountState.setBrowserProfile(null) }
            mutableBrowserStatus.value = BrowserSessionStatus.Guest
            true
        }
    }

    fun clear() = sessionLock.withLock {
        requireNativeAccount()
        val removedId = accountState.value.activeAccountId
        replaceClient { accountState.removeCurrent() }
        credentialRefreshers.remove(removedId)
        Unit
    }

    suspend fun switchAccount(id: String): Boolean {
        requireNativeAccount()
        val owner = mutableClient
        if (owner.verificationState.value.blocksSessionChange) return false
        if (id == accountState.value.activeAccountId) return true
        val (savedSession, verified) = commentIdentities.withOriginal(id) {
            val saved = accountState.value.accounts
                .firstOrNull { it.id == id }
                ?.session ?: return@withOriginal null
            val verified = verifyCandidateSession(owner, saved.cookies.toMutableMap(), saved.userAgent) ?: return@withOriginal null
            saved to verified
        } ?: return false
        currentCoroutineContext().ensureActive()
        return sessionLock.withLock {
            if (closed.value || mutableClient !== owner || owner.verificationState.value.blocksSessionChange) return@withLock false
            val latest = accountState.value.accounts
                .firstOrNull { it.id == id }
                ?.session ?: return@withLock false
            if (!latest.hasSameIdentityAs(verified)) return@withLock false
            val selected = latest.copy(
                username = verified.username,
                profile = verified.profile,
                self = verified.self,
                cookies = if (latest.cookies == savedSession.cookies) verified.cookies else latest.cookies,
            )
            replaceClient { accountState.select(id, selected) }
            true
        }
    }

    fun removeAccount(id: String) = sessionLock.withLock {
        requireNativeAccount()
        check(!closed.value) { "ZhihuAccountStore 已关闭" }
        commentIdentities.checkCanChangeAccounts()
        credentialRefreshers.remove(id)
        val activeAccountId = accountState.value.activeAccountId
        if (id == activeAccountId) {
            replaceClient { accountState.remove(id) }
        } else {
            accountState.remove(id)
        }
    }

    /** owner 必须来自登录交互开始时；迟到的扫码或网页回调不能借用新会话提交。 */
    suspend fun login(owner: ZhihuAccountClient, cookies: MutableMap<String, String>): Boolean {
        requireNativeAccount()
        val userAgent = sessionLock.withLock {
            if (!canCompleteLogin(owner)) return false
            session.userAgent
        }
        val session = verifyCandidateSession(owner, cookies, userAgent) ?: return false
        val requestContext = currentCoroutineContext()
        return sessionLock.withLock {
            requestContext.ensureActive()
            if (!canCompleteLogin(owner)) return@withLock false
            replaceClient {
                accountState.selectOrAdd(session)
                credentialRefreshers.remove(accountState.value.activeAccountId)
            }
            true
        }
    }

    suspend fun login(owner: ZhihuAccountClient, token: ZhihuMobileLoginToken): Boolean {
        requireNativeAccount()
        val userAgent = sessionLock.withLock {
            if (!canCompleteLogin(owner)) return false
            session.userAgent
        }
        val session = verifyCandidateSession(owner, token.cookies.toMutableMap(), userAgent)?.copy(
            mobileAccessToken = token.accessToken,
            mobileRefreshToken = token.refreshToken,
            mobileTokenType = token.tokenType,
            mobileTokenExpiresAt = token.expiresAt,
        ) ?: return false
        val requestContext = currentCoroutineContext()
        return sessionLock.withLock {
            requestContext.ensureActive()
            if (!canCompleteLogin(owner)) return@withLock false
            replaceClient {
                accountState.selectOrAdd(session)
                credentialRefreshers.remove(accountState.value.activeAccountId)
            }
            true
        }
    }

    private fun canCompleteLogin(owner: ZhihuAccountClient): Boolean = !closed.value &&
        mutableClient === owner && owner.isCurrent && !owner.verificationState.value.blocksSessionChange

    suspend fun sendBrowserCommentAs(owner: ZhihuAccountClient, sender: CommentSender, url: String, body: JsonObject): BrowserCommentSendResult {
        val bridge = engine as? BrowserAccountEngine ?: throw BrowserBridgeException(BrowserSessionStatus.Denied)
        if (client !== owner || !owner.isCurrent || sender.sourceId != accountsState.value.activeAccountId) {
            throw DisplayFailure { DomainStrings.accountChangedOrVerify }
        }
        if (owner.browserCommentRestoreRequired.value) throw DisplayFailure { DomainStrings.identityRestoreInProgress }
        val result = bridge.sendCommentAs(owner, sender, url, body)
        if (!result.restored) owner.reportBrowserCommentRestoreRequired()
        return result
    }

    suspend fun restoreBrowserCommentIdentity(owner: ZhihuAccountClient): Boolean {
        val bridge = engine as? BrowserAccountEngine ?: throw BrowserBridgeException(BrowserSessionStatus.Denied)
        if (client !== owner || !owner.isCurrent) return false
        if (!owner.browserCommentRestoreRequired.value) return true
        val restored = bridge.restoreCommentIdentity(owner)
        if (!restored || client !== owner || !owner.isCurrent) return false
        owner.clearBrowserCommentRestoreRequired()
        return true
    }

    suspend fun browserIdentityAccounts(owner: ZhihuAccountClient): List<ZhihuIdentityAccount> {
        val bridge = engine as? BrowserAccountEngine ?: throw BrowserBridgeException(BrowserSessionStatus.Denied)
        if (client !== owner || !owner.isCurrent) throw DisplayFailure { DomainStrings.accountChangedOrVerify }
        if (owner.browserCommentRestoreRequired.value) throw DisplayFailure { DomainStrings.identityRestoreInProgress }
        val accounts = bridge.identityAccounts(owner)
        if (client !== owner || !owner.isCurrent) throw DisplayFailure { DomainStrings.accountChangedOrVerify }
        return accounts
    }

    /** 桥内完成签发和双域核身；公共会话只接收经过 /me 复核的资料，不接触新凭据。 */
    suspend fun changeBrowserIdentity(owner: ZhihuAccountClient, targetUserId: String?): ZhihuIdentityChangeResult {
        val bridge = engine as? BrowserAccountEngine ?: throw BrowserBridgeException(BrowserSessionStatus.Denied)
        if (client !== owner || !owner.isCurrent) throw DisplayFailure { DomainStrings.accountChangedOrVerify }
        if (owner.browserCommentRestoreRequired.value) throw DisplayFailure { DomainStrings.identityRestoreInProgress }
        if (!bridge.changeIdentity(owner, targetUserId)) throw BrowserBridgeException(BrowserSessionStatus.Denied)
        if (client !== owner || !owner.isCurrent) throw DisplayFailure { DomainStrings.accountChangedOrVerify }
        if (refreshBrowserSession(targetUserId) != BrowserSessionStatus.Authenticated) throw DisplayFailure { DomainStrings.serverAccountMismatch }
        val verified = session
        val profile = verified.profile ?: throw DisplayFailure { DomainStrings.serverProfileIncomplete }
        return ZhihuIdentityChangeResult(
            ZhihuIdentityAccount(profile.id, profile.urlToken, profile.name, profile.avatarUrl, isActive = true),
            verified,
        )
    }

    suspend fun completeIdentityChange(
        owner: ZhihuAccountClient,
        oldSession: ZhihuAccountSession,
        body: String,
        expectedAccountId: String? = null,
    ): ZhihuIdentityChangeResult {
        requireNativeAccount()
        sessionLock.withLock {
            if (client !== owner || !owner.isCurrent || owner.verificationState.value.blocksSessionChange) throw DisplayFailure { DomainStrings.accountChangedOrVerify }
        }
        val token = ZhihuJson.decodeJson<ZhihuIdentityToken>(ZhihuJson.json.parseToJsonElement(body))
        if (token.accessToken.isBlank()) throw DisplayFailure { DomainStrings.newAccountNoToken }
        if (token.cookie["z_c0"].isNullOrBlank()) throw DisplayFailure { DomainStrings.newAccountNoCookie }
        val newCookies = oldSession.cookies.toMutableMap().apply { putAll(token.cookie) }
        val client = createAccountHttpClient(
            newCookies,
            oldSession.userAgent,
            engine,
            onFailure = { owner.reportHttpFailure(it, newCookies) },
        )
        return try {
            val response = client.get("https://api.zhihu.com/people/self") {
                applyIdentityHeaders(
                    oldSession.copy(
                        mobileAccessToken = token.accessToken,
                        mobileTokenType = token.tokenType,
                    ),
                )
            }
            val rawProfile = ZhihuJson.json
                .parseToJsonElement(
                    response.identitySuccessBody { DomainStrings.initializeAccount },
                ).jsonObject
            val profile = ZhihuJson.decodeJson<ZhihuIdentityProfile>(rawProfile)
            if (profile.id.isBlank() || profile.name.isBlank()) throw DisplayFailure { DomainStrings.serverProfileIncomplete }
            if (expectedAccountId != null && profile.id != expectedAccountId) throw DisplayFailure { DomainStrings.serverAccountMismatch }
            val nextSession = oldSession.copy(
                login = true,
                username = profile.name,
                cookies = newCookies,
                profile = ZhihuAccountProfileSnapshot(
                    id = profile.id,
                    name = profile.name,
                    urlToken = profile.urlToken,
                    userType = profile.userType,
                    avatarUrl = profile.avatarUrl,
                ),
                self = rawProfile,
                mobileAccessToken = token.accessToken,
                mobileRefreshToken = token.refreshToken,
                mobileTokenType = token.tokenType,
                mobileTokenExpiresAt = token.expiresAt,
            )
            currentCoroutineContext().ensureActive()
            sessionLock.withLock {
                if (this.client !== owner || !owner.isCurrent || owner.verificationState.value.blocksSessionChange) throw DisplayFailure { DomainStrings.accountChangedOrVerify }
                replaceSession(nextSession)
            }
            ZhihuIdentityChangeResult(
                account = ZhihuIdentityAccount(
                    id = profile.id,
                    urlToken = profile.urlToken,
                    name = profile.name,
                    avatarUrl = profile.avatarUrl,
                    isActive = true,
                    canCreateSubAccount = profile.canCreateSubAccount,
                    accountType = profile.accountType,
                    subAccountControlStatus = profile.subAccountControlStatus,
                ),
                session = nextSession,
            )
        } finally {
            client.close()
        }
    }

    internal fun credentialRefresherFor(accountId: String?): ZhihuCredentialRefresher = sessionLock.withLock {
        credentialRefreshers.getOrPut(accountId) { ZhihuCredentialRefresher(this, accountId) }
    }

    internal fun renewalSession(owner: ZhihuAccountClient, accountId: String?, refresher: ZhihuCredentialRefresher): ZhihuAccountSession? = sessionLock.withLock {
        if (closed.value ||
            mutableClient !== owner ||
            !owner.isCurrent ||
            accountState.value.activeAccountId != accountId ||
            credentialRefreshers[accountId] !== refresher ||
            mutableClient.verificationState.value.blocksSessionChange
        ) {
            return@withLock null
        }
        session
    }

    internal fun saveRenewedSession(accountId: String?, refresher: ZhihuCredentialRefresher, expectedRefreshToken: String, token: ZhihuMobileLoginToken): Boolean = sessionLock.withLock {
        if (closed.value || accountId == null || credentialRefreshers[accountId] !== refresher) return@withLock false
        val latest = accountState.value.accounts
            .firstOrNull { it.id == accountId }
            ?.session ?: return@withLock false
        if (!latest.login || latest.mobileRefreshToken != expectedRefreshToken || latest.profile?.id != token.accountId) return@withLock false
        val updated = latest.copy(
            cookies = (latest.cookies + token.cookies).toMutableMap(),
            mobileAccessToken = token.accessToken,
            mobileRefreshToken = token.refreshToken,
            mobileTokenType = token.tokenType,
            mobileTokenExpiresAt = token.expiresAt,
        )
        if (accountState.value.activeAccountId == accountId) {
            if (!mutableClient.isCurrent || mutableClient.verificationState.value.blocksSessionChange) return@withLock false
            mutableClient.save(updated)
        } else {
            accountState.saveAccount(accountId, updated)
        }
        true
    }

    private suspend fun verifyCandidateSession(
        owner: ZhihuAccountClient,
        cookies: MutableMap<String, String>,
        userAgent: String,
    ): ZhihuAccountSession? {
        val client = createAccountHttpClient(
            cookies,
            userAgent,
            engine,
            onFailure = { owner.reportHttpFailure(it, cookies) },
        )
        return try {
            fetchVerifiedZhihuSession(client, cookies, userAgent)
        } finally {
            client.close()
        }
    }

    private fun replaceClient(updateAccounts: () -> Unit) = sessionLock.withLock {
        check(!closed.value) { "ZhihuAccountStore 已关闭" }
        if (mutableClient.verificationState.value.blocksSessionChange) throw DisplayFailure { DomainStrings.verifyBeforeSwitch }
        commentIdentities.checkCanChangeAccounts()
        // 先使旧 client 失效，再发布新会话；否则关闭前完成的旧响应可能写入新账号。
        pendingLoginSuccess.value = null
        mutableClient.close()
        try {
            updateAccounts()
        } finally {
            // 持久化失败时 state 仍是原账号，也要恢复一个可用 client，不能留下已关闭实例。
            mutableClient = ZhihuAccountClient(this, engine, http403Log)
        }
    }

    override fun close() = sessionLock.withLock {
        if (!closed.compareAndSet(false, true)) return@withLock
        notificationReadAllObservers.clear()
        credentialRefreshers.clear()
        pendingLoginSuccess.value = null
        val clientError = runCatching { mutableClient.close() }.exceptionOrNull()
        val engineError = runCatching { engine.close() }.exceptionOrNull()
        val error = clientError ?: engineError
        if (error != null) {
            if (engineError != null && engineError !== error) error.addSuppressed(engineError)
            throw error
        }
    }
}

internal class ZhihuAccountState(
    private val repository: ZhihuAccountRepository?,
) {
    val state = MutableStateFlow(repository?.loadAccounts() ?: ZhihuAccounts())
    val value: ZhihuAccounts
        get() = state.value

    fun setBrowserProfile(profile: ZhihuAccountProfileSnapshot?) {
        check(repository == null)
        state.value = if (profile == null) {
            ZhihuAccounts()
        } else {
            val session = ZhihuAccountSession(login = true, username = profile.name, profile = profile, userAgent = "")
            ZhihuAccounts(profile.id, listOf(ZhihuSavedAccount(profile.id, session)))
        }
    }

    fun saveCurrent(session: ZhihuAccountSession, reauthenticated: Boolean = false) {
        val current = value
        val activeId = current.activeAccountId
        val updated = if (activeId == null) {
            if (!session.login) {
                current.copy(guestCookies = session.cookies.toMap())
            } else {
                val id = session.accountSlotId()
                ZhihuAccounts(id, listOf(ZhihuSavedAccount(id, session)))
            }
        } else {
            current.copy(
                accounts = current.accounts.map { account ->
                    if (account.id != activeId) {
                        account
                    } else {
                        account.copy(session = session, pendingCommentIdentity = account.pendingCommentIdentity.takeUnless { reauthenticated })
                    }
                },
            )
        }
        persist(updated)
    }

    fun saveAccount(id: String, session: ZhihuAccountSession) {
        val current = value
        persist(current.copy(accounts = current.accounts.map { if (it.id == id) it.copy(session = session) else it }))
    }

    fun updateAccount(account: ZhihuSavedAccount) {
        val current = value
        if (current.accounts.none { it.id == account.id }) throw DisplayFailure { DomainStrings.accountSignedOut }
        persist(current.copy(accounts = current.accounts.map { if (it.id == account.id) account else it }))
    }

    fun saveGuestVerificationCookies(cookies: Map<String, String>) {
        persist(value.copy(guestCookies = value.guestCookies + cookies.filterKeys { it != "z_c0" }))
    }

    fun selectOrAdd(session: ZhihuAccountSession) {
        val existing = value.accounts.firstOrNull { it.session.hasSameIdentityAs(session) }
        if (existing != null) {
            persist(
                value.copy(
                    activeAccountId = existing.id,
                    accounts = value.accounts.map { if (it.id == existing.id) it.copy(session = session, pendingCommentIdentity = null) else it },
                ),
            )
        } else {
            val id = session.accountSlotId()
            persist(ZhihuAccounts(id, value.accounts + ZhihuSavedAccount(id, session)))
        }
    }

    fun select(id: String, session: ZhihuAccountSession) {
        require(value.accounts.any { it.id == id }) { DomainStrings.switchAccountMissing }
        persist(
            value.copy(
                activeAccountId = id,
                accounts = value.accounts.map { if (it.id == id) it.copy(session = session) else it },
            ),
        )
    }

    fun remove(id: String) {
        val remaining = value.accounts.filterNot { it.id == id }
        val activeId = if (value.activeAccountId == id) remaining.firstOrNull()?.id else value.activeAccountId
        persist(ZhihuAccounts(activeId, remaining))
    }

    fun removeCurrent() {
        value.activeAccountId?.let(::remove) ?: persist(ZhihuAccounts())
    }

    private fun persist(accounts: ZhihuAccounts) {
        val repository = checkNotNull(repository) { "浏览器不保存本地账号" }
        if (accounts.accounts.isEmpty() && accounts.guestCookies.isEmpty()) {
            repository.clear()
        } else {
            repository.saveAccounts(accounts)
        }
        state.value = accounts
    }
}

fun createAccountHttpClient(
    cookies: MutableMap<String, String>,
    userAgent: String,
    engine: HttpClientEngine,
    onFailure: suspend (HttpFailureRecord) -> Unit = {},
): HttpClient {
    if (engine is BrowserAccountEngine) throw BrowserBridgeException(BrowserSessionStatus.Denied)
    return HttpClient(engine) {
        installZhihuCommonClientConfig(cookies, userAgent, onFailure = onFailure)
    }
}

internal val ZhihuAccountSession.canRenewCredentials: Boolean
    get() = login && !profile?.id.isNullOrBlank() && !mobileRefreshToken.isNullOrBlank()

private fun ZhihuAccountSession.hasSameIdentityAs(other: ZhihuAccountSession): Boolean {
    if (login != other.login) return false
    if (!login) return true
    val identity = accountIdentityKey()
    val otherIdentity = other.accountIdentityKey()
    return identity != null && identity == otherIdentity
}

internal expect val accountHttpClientEngineFactory: HttpClientEngineFactory<*>

val LocalZhihuAccountStore = staticCompositionLocalOf<ZhihuAccountStore?> { null }

@Composable
fun rememberZhihuAccountStore(): ZhihuAccountStore = checkNotNull(LocalZhihuAccountStore.current) {
    "宿主必须提供当前应用的账号 store"
}
