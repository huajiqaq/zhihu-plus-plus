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
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.fetchVerifiedZhihuSession
import com.github.zly2006.zhihu.data.installZhihuCommonClientConfig
import com.github.zly2006.zhihu.data.installZhihuMobileClientProfile
import com.github.zly2006.zhihu.data.isZhihuRequestHost
import com.github.zly2006.zhihu.data.resolveVerificationUrl
import com.github.zly2006.zhihu.data.toCookieHeaderString
import com.github.zly2006.zhihu.util.ZHIHU_WEB_ZSE93
import com.github.zly2006.zhihu.util.ZhihuCredentialRefresher
import com.github.zly2006.zhihu.util.ZhihuFetchSignature
import com.github.zly2006.zhihu.util.isZhihuLoginRiskControl
import com.github.zly2006.zhihu.util.raiseForStatus
import com.github.zly2006.zhihu.util.signZhihuFetchRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.api.SendingRequest
import io.ktor.client.plugins.api.SetupRequest
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
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
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.io.files.Path
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class ZhihuRequestProfile { Web, Mobile }

enum class SessionExpiryPrompt { ACTIVE, SHOWING, DISMISSED }

data class SessionExpiryState(
    val phase: SessionExpiryPrompt = SessionExpiryPrompt.ACTIVE,
    val serverMessage: String? = null,
)

class ZhihuLoginRequiredException : IllegalStateException("此功能需要登录，请先登录")

internal val requestCookiesKey = io.ktor.util.AttributeKey<Map<String, String>>("ZhihuSignedRequestCookies")

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

    suspend fun refreshCredentials(force: Boolean = false): Boolean = accountStore.commentIdentities.withOriginal(accountId) {
        credentialRefresher.refresh(this, force)
    }

    private val mutableVerificationState = MutableStateFlow(HumanVerificationState())
    val verificationState: StateFlow<HumanVerificationState> = mutableVerificationState.asStateFlow()

    fun reportHumanVerification(url: String?, candidateCookies: Map<String, String>? = null) {
        if (closed.value) return
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
        if ((url.protocol != io.ktor.http.URLProtocol.HTTP && url.protocol != io.ktor.http.URLProtocol.HTTPS) ||
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
    private val activeRequests = mutableSetOf<kotlinx.coroutines.Job>()
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
            check(signin.status.isSuccess()) { "匿名网页初始化拒绝重定向（HTTP ${signin.status.value}）" }
            if (signin.request.url.isZhihuLoginRiskControl()) error("匿名访问需要完成知乎安全验证")
            val device = transport
                .post("https://www.zhihu.com/udid") {
                    header(HttpHeaders.Referrer, "https://www.zhihu.com/signin")
                    header(HttpHeaders.Origin, "https://www.zhihu.com")
                }.raiseForStatus()
            check(device.status.isSuccess()) { "匿名网页初始化拒绝重定向（HTTP ${device.status.value}）" }
            currentCoroutineContext().ensureActive()
            if (!isCurrent) throw CancellationException("匿名客户端已关闭")
            if (device.request.url.isZhihuLoginRiskControl()) error("匿名访问需要完成知乎安全验证")
            check(anonymousCookieStorage.get(Url("https://www.zhihu.com/")).any { it.name == "d_c0" && it.value.isNotBlank() }) {
                "匿名网页初始化失败：知乎未返回设备 Cookie，请重试"
            }
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
        if (load() != session) accountState.saveCurrent(session.copy(cookies = updatedCookies.toMutableMap()))
        cookies.clear()
        cookies.putAll(updatedCookies)
    }

    fun httpClient(useCookies: Boolean = true): HttpClient = httpClient(ZhihuRequestProfile.Web, useCookies)

    fun httpClient(profile: ZhihuRequestProfile, useCookies: Boolean = true): HttpClient = sessionLock.withLock {
        check(isCurrent) { "ZhihuAccountClient 已关闭" }
        if (!load().login && !useCookies) {
            if (profile == ZhihuRequestProfile.Mobile) throw ZhihuLoginRequiredException()
            anonymousWebClient.value
        } else if (profile == ZhihuRequestProfile.Mobile) {
            if (useCookies) mobileClient.value else guestMobileClient.value
        } else {
            if (useCookies) client else guestClient.value
        }
    }

    fun mobileHomeFeedHttpClient(useCookies: Boolean = true): HttpClient = httpClient(ZhihuRequestProfile.Mobile, useCookies)

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
        val anonymous = !load().login && !useCookies
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
        mutableExpiryPrompt.value = SessionExpiryState(SessionExpiryPrompt.DISMISSED)
        mutableLoginPrompt.value = SessionExpiryPrompt.DISMISSED
        mutableVerificationState.value = HumanVerificationState(HumanVerificationPhase.DISMISSED)
        anonymousInitialization?.cancel(CancellationException("匿名客户端已关闭"))
        activeRequests.toList().forEach { it.cancel(CancellationException("请求期间账号已切换")) }
        activeRequests.clear()
        anonymousCookies.clear()
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
    repository: ZhihuAccountRepository,
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
    internal val accountState = try {
        ZhihuAccountState(repository)
    } catch (error: Throwable) {
        runCatching { engine?.close() }.exceptionOrNull()?.let { if (it !== error) error.addSuppressed(it) }
        throw error
    }

    // 传入engine即移交所有权；会话只借用，换账号不关闭，store统一释放。
    val engine: HttpClientEngine = engine ?: accountHttpClientEngineFactory.create()
    val http403Log = Http403Log(Path(repository.directory, "network-diagnostics", "http403.json"))
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

    fun save(session: ZhihuAccountSession) = sessionLock.withLock {
        check(!closed.value) { "ZhihuAccountStore 已关闭" }
        if (this.session.hasSameIdentityAs(session)) {
            mutableClient.save(session)
        } else {
            replaceSession(session)
        }
    }

    fun replaceSession(session: ZhihuAccountSession) = sessionLock.withLock {
        replaceClient {
            accountState.saveCurrent(session, reauthenticated = true)
            credentialRefreshers.remove(accountState.value.activeAccountId)
        }
    }

    fun clear() = sessionLock.withLock {
        val removedId = accountState.value.activeAccountId
        replaceClient { accountState.removeCurrent() }
        credentialRefreshers.remove(removedId)
        Unit
    }

    suspend fun switchAccount(id: String): Boolean {
        val owner = mutableClient
        if (owner.verificationState.value.blocksSessionChange) return false
        if (id == accountState.value.activeAccountId) return true
        val (savedSession, verified) = commentIdentities.withOriginal(id) {
            val saved = accountState.value.accounts
                .firstOrNull { it.id == id }
                ?.session ?: return@withOriginal null
            val verified = verifyCandidateSession(saved.cookies.toMutableMap(), saved.userAgent) ?: return@withOriginal null
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

    suspend fun login(cookies: MutableMap<String, String>): Boolean {
        val owner = mutableClient
        if (owner.verificationState.value.blocksSessionChange) return false
        val session = verifyCandidateSession(cookies) ?: return false
        currentCoroutineContext().ensureActive()
        return sessionLock.withLock {
            if (closed.value || mutableClient !== owner || owner.verificationState.value.blocksSessionChange) return@withLock false
            replaceClient {
                accountState.selectOrAdd(session)
                credentialRefreshers.remove(accountState.value.activeAccountId)
            }
            true
        }
    }

    suspend fun login(token: ZhihuMobileLoginToken): Boolean {
        val owner = mutableClient
        if (owner.verificationState.value.blocksSessionChange) return false
        val session = verifyCandidateSession(token.cookies.toMutableMap())?.copy(
            mobileAccessToken = token.accessToken,
            mobileRefreshToken = token.refreshToken,
            mobileTokenType = token.tokenType,
            mobileTokenExpiresAt = token.expiresAt,
        ) ?: return false
        currentCoroutineContext().ensureActive()
        return sessionLock.withLock {
            if (closed.value || mutableClient !== owner || owner.verificationState.value.blocksSessionChange) return@withLock false
            replaceClient {
                accountState.selectOrAdd(session)
                credentialRefreshers.remove(accountState.value.activeAccountId)
            }
            true
        }
    }

    suspend fun completeIdentityChange(
        owner: ZhihuAccountClient,
        oldSession: ZhihuAccountSession,
        body: String,
        expectedAccountId: String? = null,
    ): ZhihuIdentityChangeResult {
        sessionLock.withLock {
            check(client === owner && owner.isCurrent && !owner.verificationState.value.blocksSessionChange) { "账号已变更或需要先完成安全验证" }
        }
        val token = ZhihuJson.decodeJson<ZhihuIdentityToken>(ZhihuJson.json.parseToJsonElement(body))
        check(token.accessToken.isNotBlank()) { "服务器未返回新账号凭证" }
        check(token.cookie["z_c0"].isNullOrBlank().not()) { "服务器未返回新账号 Cookie" }
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
                    response.identitySuccessBody("初始化新账号"),
                ).jsonObject
            val profile = ZhihuJson.decodeJson<ZhihuIdentityProfile>(rawProfile)
            check(profile.id.isNotBlank() && profile.name.isNotBlank()) { "服务器返回的账号资料不完整" }
            check(expectedAccountId == null || profile.id == expectedAccountId) { "服务器返回的账号与目标账号不一致" }
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
                check(this.client === owner && owner.isCurrent && !owner.verificationState.value.blocksSessionChange) { "账号已变更或需要先完成安全验证" }
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
        cookies: MutableMap<String, String>,
        userAgent: String = session.userAgent,
    ): ZhihuAccountSession? {
        val owner = mutableClient
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
        check(!mutableClient.verificationState.value.blocksSessionChange) { "请先完成安全验证并确认重启提示" }
        commentIdentities.checkCanChangeAccounts()
        // 先使旧 client 失效，再发布新会话；否则关闭前完成的旧响应可能写入新账号。
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
    private val repository: ZhihuAccountRepository,
) {
    val state = MutableStateFlow(repository.loadAccounts())
    val value: ZhihuAccounts
        get() = state.value

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
        check(current.accounts.any { it.id == account.id }) { "该账号已退出登录" }
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
        require(value.accounts.any { it.id == id }) { "找不到要切换的登录账号" }
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
): HttpClient = HttpClient(engine) {
    installZhihuCommonClientConfig(
        cookies,
        userAgent,
        onFailure = onFailure,
    )
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
expect fun rememberZhihuAccountStore(): ZhihuAccountStore
