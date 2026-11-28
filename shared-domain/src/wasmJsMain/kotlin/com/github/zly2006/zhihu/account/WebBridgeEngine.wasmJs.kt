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

@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.github.zly2006.zhihu.account

import com.github.zly2006.zhihu.i18n.AccountStrings
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.util.DisplayFailure
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.callContext
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.utils.EmptyContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.headersOf
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.JsAny
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

fun createWebAccountStore(): ZhihuAccountStore = ZhihuAccountStore(null, accountHttpClientEngineFactory.create())

internal actual val accountHttpClientEngineFactory: HttpClientEngineFactory<*> = object : HttpClientEngineFactory<HttpClientEngineConfig> {
    override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine = WebBridgeEngine(HttpClientEngineConfig().apply(block))
}

private val detailPath = Regex("/api/v4/(answer|article|question|pin)s/([1-9][0-9]{0,18})")
private val commentsPath = Regex("/api/v4/comment_v5/(answer|article|question|pin)s/([1-9][0-9]{0,18})/(root_comment|segment_comment|segment/root_comment)")
private val singleCommentPath = Regex("/api/v4/comment_v5/comment/([1-9][0-9]{0,18})(/child_comment)?")
private val questionFeedsPath = Regex("/api/v4/questions/([1-9][0-9]{0,18})/feeds")
private val pinPath = Regex("/v2/pins/([1-9][0-9]{0,18})")

private class WebBridgeEngine(
    override val config: HttpClientEngineConfig,
) : HttpClientEngineBase("ZhihuWebBridge"),
    BrowserAccountEngine {
    override suspend fun sendMessage(owner: ZhihuAccountClient, receiverId: String, content: String): String {
        val body = JsonObject(mapOf("receiver_id" to JsonPrimitive(receiverId), "content" to JsonPrimitive(content))).toString()
        val (status, response) = owner.requestBrowserBusiness("messageSend", body = body)
        check(status in 200..299) { "私信发送失败（HTTP $status），不会自动重发" }
        return response
    }

    @OptIn(InternalAPI::class)
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val url = data.url

        fun denied(): Nothing = throw BrowserBridgeException(BrowserSessionStatus.Denied)
        val business = webBusinessOperation(data)
        val guestCompatible = business?.name in setOf("contentDetail", "topstoryRead", "questionFeedsRead") &&
            (data.attributes.contains(anonymousBrowserRead) || data.attributes.getOrNull(browserRequestOwner)?.load()?.login != true)
        if (business != null && !guestCompatible) {
            if (url.protocol != URLProtocol.HTTPS || url.port != 443 || url.user != null || url.password != null ||
                url.fragment.isNotEmpty() || url.toString().any(Char::isISOControl) || data.attributes.contains(anonymousBrowserRead)
            ) denied()
            // 共享 consumer 的签名/CSRF 不出页面；油猴从自己的账号快照重新生成。
            val consumerHeaders = setOf("accept", "content-type", "referer", "user-agent", "x-api-version", "x-app-version", "x-app-za", "x-xsrftoken", "x-zse-93", "x-zse-96", "x-requested-with")
            if (data.headers.entries().any { (name, values) -> name.lowercase() !in consumerHeaders || values.any { it.length > 4096 || it.any(Char::isISOControl) } }) denied()
            val owner = data.attributes.getOrNull(browserRequestOwner) ?: denied()
            val started = GMTDate()
            val (status, body) = owner.requestBrowserBusiness(business.name, business.id, url.encodedQuery, webBusinessBody(data))
            return HttpResponseData(
                HttpStatusCode.fromValue(status), started,
                headersOf(HttpHeaders.ContentType, "application/json; charset=utf-8"),
                HttpProtocolVersion.HTTP_1_1, ByteReadChannel(body.encodeToByteArray()), callContext(),
            )
        }
        if (data.method != HttpMethod.Get ||
            data.body !== EmptyContent ||
            url.protocol != URLProtocol.HTTPS ||
            url.port != 443 ||
            url.user != null ||
            url.password != null ||
            url.fragment.isNotEmpty() ||
            url.toString().any(Char::isISOControl)
        ) {
            denied()
        }
        // Ktor 的默认转换器/JSON 插件会补 Accept。只识别它们的固定值，不把任何头送出页面。
        if (data.headers.entries().any { (name, values) ->
                !name.equals(HttpHeaders.Accept, ignoreCase = true) || values.any { it != "*/*" && it != "application/json" }
            }
        ) {
            denied()
        }
        var id = ""
        val op = when (url.host) {
            "www.zhihu.com" -> when (url.encodedPath) {
                "/api/v4/me" -> if (data.attributes.contains(anonymousBrowserRead)) denied() else "profile"
                "/api/v3/feed/topstory/hot-lists/total" -> "hotList"
                "/api/v3/feed/topstory/recommend" -> "recommend"
                "/api/v4/search_v3" -> "search"
                else -> {
                    val detail = detailPath.matchEntire(url.encodedPath)
                    val comments = commentsPath.matchEntire(url.encodedPath)
                    val single = singleCommentPath.matchEntire(url.encodedPath)
                    val feeds = questionFeedsPath.matchEntire(url.encodedPath)
                    when {
                        detail != null -> {
                            id = detail.groupValues[2]
                            detail.groupValues[1]
                        }
                        comments != null -> {
                            id = comments.groupValues[2]
                            val kind = when (comments.groupValues[3]) {
                                "root_comment" -> "root"
                                "segment_comment" -> "segments"
                                else -> "segment"
                            }
                            "$kind-${comments.groupValues[1]}"
                        }
                        single != null -> {
                            id = single.groupValues[1]
                            if (single.groupValues[2].isEmpty()) "comment" else "childComments"
                        }
                        feeds != null -> {
                            id = feeds.groupValues[1]
                            "questionFeeds"
                        }
                        else -> denied()
                    }
                }
            }
            "api.zhihu.com" -> when (url.encodedPath) {
                "/unify-consumption/forward/count" -> "forwardCount"
                "/unify-consumption/forward/list" -> "forwardList"
                "/unify-consumption/read_history" -> "history"
                "/read_history/total" -> "historySetting"
                "/topstory/recommend" -> "homeFeed"
                else -> {
                    id = pinPath.matchEntire(url.encodedPath)?.groupValues?.get(1) ?: denied()
                    "pinV2"
                }
            }
            else -> denied()
        }
        val history = op == "history" || op == "historySetting"
        if (history) {
            if (data.attributes.contains(anonymousBrowserRead)) denied()
            id = data.attributes.getOrNull(browserHistoryOwner) ?: denied()
        }
        val owner = data.attributes.getOrNull(browserRequestOwner) ?: denied()
        val authenticatedRead = owner.load().login && op !in setOf("profile", "history", "historySetting") && !data.attributes.contains(anonymousBrowserRead)
        if (data.attributes.contains(mobileBrowserRead) && op != "pinV2" && !authenticatedRead) denied()
        val started = GMTDate()
        val (status, body) = if (authenticatedRead) {
            owner.requestBrowserBusiness("existingRead", "$op/$id", url.encodedQuery)
        } else {
            owner.browserRead(op, id, url.encodedQuery)
        }
        return HttpResponseData(
            HttpStatusCode.fromValue(status),
            started,
            headersOf(HttpHeaders.ContentType, "application/json; charset=utf-8"),
            HttpProtocolVersion.HTTP_1_1,
            ByteReadChannel(body.encodeToByteArray()),
            callContext(),
        )
    }
}

/** 仅报告脚本能力，不接触账号、Cookie 或网络。 */
data class WebBridgeStatus(
    val version: String,
    val profilePermission: String,
    val readIssue: WebBridgeIssue?,
    val cookieIssue: WebBridgeIssue?,
    val businessPermissions: Set<String>,
)

enum class WebBridgeIssue(
    internal val wire: String,
    private val label: () -> String,
) {
    ManagerInfoMissing("manager-info-missing", { DomainStrings.bridgeManagerInfoMissing }),
    ManagerUnsupported("manager-unsupported", { DomainStrings.bridgeManagerUnsupported }),
    ManagerVersionUnknown("manager-version-unknown", { DomainStrings.bridgeManagerVersionUnknown }),
    RedirectUnsupported("redirect-unsupported", { DomainStrings.bridgeRedirectUnsupported }),
    RequestApiMissing("request-api-missing", { DomainStrings.bridgeRequestApiMissing }),
    CookieApiMissing("cookie-api-missing", { DomainStrings.bridgeCookieApiMissing }),
    WebLocksMissing("web-locks-missing", { DomainStrings.bridgeWebLocksMissing }),
    SecureRandomMissing("secure-random-missing", { DomainStrings.bridgeSecureRandomMissing }),
    CookieAttributesUnsupported("cookie-attributes-unsupported", { DomainStrings.bridgeCookieAttributesUnsupported }),
    GestureRequired("gesture-required", { DomainStrings.bridgeGestureRequired }),
    ;

    val message: String get() = label()
}

class WebBridgeCapabilityException(val issue: WebBridgeIssue) : IllegalStateException() {
    override val message: String get() = issue.message
}

/** 不把管理器/服务端任意错误字符串直接展示给用户。 */
fun browserBridgeFailureMessage(error: Exception): String = when (error) {
    is WebBridgeCapabilityException -> error.issue.message
    is DisplayFailure -> error.displayMessage()
    else -> AccountStrings.webBridgeActionFailed
}

suspend fun ZhihuAccountClient.requestBrowserBridgeStatus(): WebBridgeStatus = browserAction("bridgeStatus") { status, body ->
    fun invalid(): Nothing = throw BrowserBridgeException(BrowserSessionStatus.Unavailable)
    val data = if (status == 200 && body.length <= 1024) {
        runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()
    } else {
        null
    } ?: invalid()
    if (data.keys != setOf("version", "capabilities", "permission")) invalid()
    val version = (data["version"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: invalid()
    if (!Regex("[0-9]+[.][0-9]+[.][0-9]+").matches(version)) invalid()
    val capabilities = data["capabilities"] as? JsonObject ?: invalid()
    val permission = data["permission"] as? JsonObject ?: invalid()
    if (capabilities.keys != setOf("read", "cookie", "readIssue", "cookieIssue") || permission.keys != setOf("profile", "business")) invalid()
    val business = (permission["business"] as? JsonArray ?: invalid()).map {
        (it as? JsonPrimitive)?.takeIf { value -> value.isString && value.content in setOf("private", "interact", "create", "history") }?.content ?: invalid()
    }.toSet()
    val profile = (permission["profile"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: invalid()
    if (profile !in setOf("denied", "checking", "granted")) invalid()
    fun issue(name: String): WebBridgeIssue? {
        val reason = capabilities["${name}Issue"]
        val value = if (reason == JsonNull) null else {
            WebBridgeIssue.entries.find { reason == JsonPrimitive(it.wire) } ?: invalid()
        }
        if (capabilities[name] != JsonPrimitive(value == null)) invalid()
        return value
    }
    WebBridgeStatus(version, profile, issue("read"), issue("cookie"), business)
}

/** 通知只是重新核对的信号；不能据此宣告已授权或已登录。 */
fun observeBrowserBridgePermission(onChange: () -> Unit): AutoCloseable {
    val handle = watchBrowserBridgePermission(onChange)
    return AutoCloseable { handle.abort() }
}

@JsFun(
    """(changed) => {
        const notify = () => changed();
        window.addEventListener('zhihu++/permission/changed/1', notify);
        return {abort() { window.removeEventListener('zhihu++/permission/changed/1', notify); }};
    }""",
)
private external fun watchBrowserBridgePermission(onChange: () -> Unit): BrowserBridgeHandle

/** 登录交互不接收来自页面的 URL 或凭据；用户取消返回 false，取消端口仍传播协程取消。 */
suspend fun ZhihuAccountClient.requestBrowserLoginAction(op: String): Boolean {
    if (op !in setOf("authorizeProfile", "cookieSignIn", "authorizePrivate", "authorizeInteractions", "authorizeCreation", "authorizeHistoryWrite")) throw BrowserBridgeException(BrowserSessionStatus.Denied)
    return browserAction(op) { status, body -> browserActionFlag(status, body, "authorized") }
}

/** 扫码只收本次本标签页的展示投影，不提供传入 URL 或轮询票据的参数。 */
suspend fun ZhihuAccountClient.requestBrowserQrStep(op: String): JsonObject {
    if (op != "loginQrStart" && op != "loginQrPoll") throw BrowserBridgeException(BrowserSessionStatus.Denied)
    return browserAction(op) { status, body -> browserQrProjection(op, status, body) }
}

/** 独立交互不经过 Ktor 的 callContext，也须在发起它的 client 关闭时取消端口。 */
private suspend fun <T> ZhihuAccountClient.browserAction(op: String, decode: (Int, String) -> T): T = coroutineScope {
    if (browserVerification == null) throw BrowserBridgeException(BrowserSessionStatus.Denied)
    if (!isCurrent) throw CancellationException("账号客户端已关闭")
    val action = coroutineContext.job
    val subscription = httpClient().coroutineContext.job.invokeOnCompletion { action.cancel() }
    try {
        val (status, body) = browserRead(op, "", "")
        if (!isCurrent) throw CancellationException("账号客户端已关闭")
        decode(status, body)
    } finally {
        subscription.dispose()
    }
}

private suspend fun ZhihuAccountClient.browserRead(op: String, id: String, query: String): Pair<Int, String> {
    currentCoroutineContext().ensureActive()
    if (!isCurrent) throw CancellationException("账号客户端已关闭")
    val history = op == "history" || op == "historySetting"
    return suspendCancellableCoroutine { continuation ->
        val request = startBrowserBridge("read", op, if (history) id else "", if (history) "account-bound" else id, query, "", null) { kind, code, text ->
            if (!continuation.isActive) return@startBrowserBridge
            if (!isCurrent) {
                continuation.cancel(CancellationException("账号客户端已关闭"))
                return@startBrowserBridge
            }
            when (kind) {
                "http" -> continuation.resume(code to text)
                "cancelled" -> continuation.cancel(CancellationException("浏览器请求已取消"))
                "unsupported" -> {
                    val issue = WebBridgeIssue.entries.find { it.wire == text }
                    continuation.resumeWithException(
                        issue?.let(::WebBridgeCapabilityException) ?: BrowserBridgeException(BrowserSessionStatus.Unavailable),
                    )
                }
                "challenge" -> {
                    reportBrowserVerification({ openBrowserVerification() }) {
                        browserAction("verificationCheck") { status, body -> browserActionFlag(status, body, "verified") }
                    }
                    continuation.resumeWithException(BrowserVerificationRequired())
                }
                else -> {
                    val failure = when (kind) {
                        "missing" -> BrowserSessionStatus.MissingBridge
                        "guest" -> BrowserSessionStatus.Guest
                        "expired" -> BrowserSessionStatus.Expired
                        "denied" -> BrowserSessionStatus.Denied
                        else -> BrowserSessionStatus.Unavailable
                    }
                    continuation.resumeWithException(BrowserBridgeException(failure, historyGrantExpired = history && failure == BrowserSessionStatus.Expired))
                }
            }
        }
        continuation.invokeOnCancellation { request.abort() }
    }
}

internal fun ZhihuAccountClient.ownerVerificationForBusiness() {
    reportBrowserVerification({ openBrowserVerification() }) {
        browserAction("verificationCheck") { status, body -> browserActionFlag(status, body, "verified") }
    }
}

/** 同步分发到油猴，保持点击的用户激活；只有确实打开官方页的监听器才会取消事件。 */
@JsFun(
    """() => {
        const page = new URL(location.href);
        const allowed = (page.origin === 'http://127.0.0.1:8080' && ['/', '/index.html'].includes(page.pathname)) ||
            (page.origin === 'https://zly2006.github.io' && ['/zhihu-plus-plus/', '/zhihu-plus-plus/index.html'].includes(page.pathname));
        if (window.top !== window || !allowed || page.username || page.password ||
            !navigator.userActivation?.isActive) return false;
        return !window.dispatchEvent(new Event('zhihu++/verification/open/1', {cancelable: true}));
    }""",
)
private external fun openBrowserVerification(): Boolean

internal external interface BrowserBridgeBytes : JsAny

internal external interface BrowserBridgeHandle : JsAny {
    fun abort()
}

/** 每次操作独占端口；私有参数仅在接单后传输，取消已接单写入不能宣称未提交。 */
@JsFun(
    """(channel, op, owner, id, query, body, bytes, done) => {
        const page = new URL(location.href);
        const trustedPage = () => {
            const current = new URL(location.href);
            return window.top === window && !current.username && !current.password &&
                current.origin === page.origin && current.pathname === page.pathname &&
                ((current.origin === 'http://127.0.0.1:8080' && ['/', '/index.html'].includes(current.pathname)) ||
                    (current.origin === 'https://zly2006.github.io' && ['/zhihu-plus-plus/', '/zhihu-plus-plus/index.html'].includes(current.pathname)));
        };
        const business = channel === 'business';
        const loginAction = !business && ['authorizeProfile', 'cookieSignIn', 'loginQrStart', 'authorizePrivate', 'authorizeInteractions', 'authorizeCreation', 'authorizeHistoryWrite'].includes(op);
        const gesture = loginAction || (!business && op === 'verificationCheck');
        const qr = !business && (op === 'loginQrStart' || op === 'loginQrPoll');
        const status = !business && op === 'bridgeStatus';
        const history = !business && (op === 'history' || op === 'historySetting');
        const bodyLimit = status ? 1024 : qr && op === 'loginQrStart' ? 576 : (qr || gesture) ? 64 : 4194304;
        if (!trustedPage() || !['read', 'business'].includes(channel) ||
            (!business && (body !== '' || bytes !== null || (history ? id !== 'account-bound' : owner !== '') ||
                ((qr || gesture || status) && (id !== '' || query !== ''))))) {
            done('denied', 0, '');
            return {abort() {}};
        }
        if (gesture && !navigator.userActivation?.isActive) {
            done('unsupported', 0, 'gesture-required');
            return {abort() {}};
        }
        // 同步进入脚本的监听器，由脚本亲自核对激活并取得同意；端口消息不传授权声明。
        const gestureHandled = !gesture || !window.dispatchEvent(new Event('zhihu++/gesture/' + op + '/1', {cancelable: true}));
        const {port1, port2} = new MessageChannel();
        const mutation = business && (['vote', 'commentSend', 'voiceCommentSend', 'commentDelete', 'collectionCreate', 'collectionDelete', 'pollVote', 'emojiWrite',
            'publish', 'pinDraft', 'pinTopicSuggest', 'questionCreate', 'articleCreate', 'historyWrite', 'historyAdd', 'recentFollowRead',
            'notificationReadAll', 'messageSend', 'uploadImage', 'uploadVoice'].includes(op) || /-(POST|PUT|DELETE)$/.test(op));
        let settled = false, accepted = false, submitted = false, timer;
        const finish = (kind, status = 0, body = '') => {
            if (settled) return;
            settled = true;
            if (!trustedPage()) { kind = submitted ? 'uncertain' : 'denied'; status = 0; body = ''; }
            if (submitted && kind !== 'http') { kind = 'uncertain'; status = 0; body = ''; }
            clearTimeout(timer);
            window.removeEventListener('pagehide', cancel);
            port1.onmessage = port1.onmessageerror = null;
            port1.close();
            done(kind, status, body);
        };
        const stop = (kind, status = 0, body = '') => {
            if (!settled) {
                port1.postMessage('cancel');
                finish(kind, status, body);
            }
        };
        const cancel = () => {
            // 端口断开时可能来不及收到已提交信号；仅写请求保守报告未知。
            if (accepted && mutation) submitted = true;
            stop(submitted ? 'uncertain' : 'cancelled');
        };
        window.addEventListener('pagehide', cancel);
        timer = setTimeout(() => stop('missing'), 1200);
        port1.onmessageerror = business ? cancel : () => stop('denied');
        port1.onmessage = ({data}) => {
            const signal = data !== null && typeof data === 'object' && Object.keys(data).length === 1;
            const response = data !== null && typeof data === 'object' && Object.keys(data).length === 3;
            // 提交信号可能在 pathname 改变前已排队；先记录它，再处理入口失信。
            if (business && accepted && signal && data.kind === 'submitted') submitted = true;
            if (!trustedPage()) return business ? cancel() : stop('denied');
            if (!accepted && signal && data.kind === 'unavailable') {
                finish('unavailable');
            } else if (!accepted && signal && data.kind === 'accepted') {
                accepted = true;
                clearTimeout(timer);
                timer = setTimeout(business ? cancel : () => stop('unavailable'), business ? (bytes ? 180000 : 65000) : (loginAction ? 65000 : 17000));
                if (!gestureHandled) return stop('unsupported', 0, 'gesture-required');
                try {
                    port1.postMessage({channel, op, owner, id, query, body, bytes}, bytes ? [bytes] : []);
                } catch { if (business) cancel(); else stop('unavailable'); }
            } else if (business && accepted && signal && data.kind === 'submitted') {
                submitted = true;
            } else if (accepted && signal && data.kind === 'challenge' && (business || op !== 'verificationCheck')) {
                finish('challenge');
            } else if (accepted && response && data.kind === 'http' && Number.isInteger(data.status) && data.status >= 200 &&
                data.status <= 599 && !(data.status >= 300 && data.status < 400) &&
                typeof data.body === 'string' && data.body.length <= bodyLimit) {
                finish('http', data.status, data.body);
            } else if (accepted && response && data.kind === 'unsupported' && data.status === 0 &&
                typeof data.body === 'string' && /^[a-z-]{1,64}$/.test(data.body)) {
                finish('unsupported', 0, data.body);
            } else if (accepted && signal && (['denied', 'guest', 'expired', 'unavailable', 'cancelled'].includes(data.kind) || (business && data.kind === 'uncertain'))) {
                finish(data.kind);
            } else if (business) cancel();
            else stop('denied');
        };
        try {
            window.postMessage({type: 'zhihu++/bridge/1'}, location.origin, [port2]);
        }
        catch { port2.close(); finish('unavailable'); }
        return {abort: cancel};
    }""",
)
internal external fun startBrowserBridge(
    channel: String,
    op: String,
    owner: String,
    id: String,
    query: String,
    body: String,
    bytes: BrowserBridgeBytes?,
    done: (String, Int, String) -> Unit,
): BrowserBridgeHandle
