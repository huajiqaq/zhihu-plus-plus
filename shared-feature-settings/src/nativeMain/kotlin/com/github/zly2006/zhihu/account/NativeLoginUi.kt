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

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.github.zly2006.zhihu.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.github.zly2006.zhihu.data.isZhihuRequestHost
import com.github.zly2006.zhihu.i18n.AccountStrings
import com.github.zly2006.zhihu.platform.platformName
import com.github.zly2006.zhihu.ui.components.AppLinearProgressIndeterminate
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.AppleWebPageView
import com.github.zly2006.zhihu.util.isZhihuLoginRiskControl
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlin.coroutines.resume
import kotlin.io.encoding.Base64
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.skia.Image
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSError
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSHTTPCookieDomain
import platform.Foundation.NSHTTPCookieName
import platform.Foundation.NSHTTPCookiePath
import platform.Foundation.NSHTTPCookieSecure
import platform.Foundation.NSHTTPCookieValue
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURL
import platform.Foundation.NSURLErrorCancelled
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLRequest
import platform.WebKit.WKHTTPCookieStore
import platform.WebKit.WKHTTPCookieStoreObserverProtocol
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKNavigationResponse
import platform.WebKit.WKNavigationResponsePolicy
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUIDelegateProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.WebKit.WKWindowFeatures
import platform.darwin.NSObject

@Composable
internal actual fun LoginMethodReady(method: LoginMethod): Boolean = true

internal actual fun createBrowserPhoneLoginBackend(store: ZhihuAccountStore, owner: ZhihuAccountClient): BrowserPhoneLoginBackend? = null

actual val isLoginRiskControlSupported: Boolean = true

actual val phoneLoginDeviceInfo: ZhihuPhoneLoginDeviceInfo
    get() = ZhihuPhoneLoginDeviceInfo(
        timezoneOffsetSeconds = 0,
        appInstallTimeMillis = 0,
        notificationEnabled = false,
        bluetoothAvailable = false,
        phoneBrand = platformName,
        phoneModel = platformName,
        androidRelease = "12",
        cpuType = platformName,
        cpuCount = 1,
        cpuUsage = "0.0",
        totalMemoryMegabytes = 0,
        freeMemoryMegabytes = 0,
        totalStorageMegabytes = 0,
        freeStorageMegabytes = 0,
    )

actual fun decodePhoneLoginCaptchaImage(content: String) = runCatching {
    val encoded = content.substringAfter("base64,", content)
    Image.makeFromEncoded(Base64.Default.decode(encoded)).toComposeImageBitmap()
}.getOrNull()

@Composable
actual fun QrLoginPane(onLoginSuccess: (String) -> Unit) = AccountQrLoginPane(onLoginSuccess)

@Composable
actual fun WebLoginPane(onLoginSuccess: (String) -> Unit) {
    val store = rememberZhihuAccountStore()
    val owner = store.client
    val scope = rememberCoroutineScope()
    val onSuccess by rememberUpdatedState(onLoginSuccess)
    var verifying by remember(owner) { mutableStateOf(false) }
    var failure by remember(owner) { mutableStateOf(false) }
    var lastCandidate by remember(owner) { mutableStateOf<Map<String, String>?>(null) }
    val controller = remember(owner) {
        AppleLoginBrowser(owner, scope, ZHIHU_SIGNIN_URL) { url, cookies ->
            if (url.isZhihuLoginRiskControl()) {
                owner.reportHumanVerification(url.toString(), cookies)
            } else if (url.encodedPath != "/signin" && !cookies["z_c0"].isNullOrBlank() &&
                owner.verificationState.value.phase == HumanVerificationPhase.ACTIVE &&
                !verifying && cookies != lastCandidate
            ) {
                lastCandidate = cookies
                verifying = true
                failure = false
                scope.launch {
                    try {
                        if (store.login(owner, cookies.toMutableMap())) {
                            store.reportLoginSuccess(store.client, store.session.username)
                            onSuccess(store.session.username)
                        } else failure = true
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        failure = true
                    } finally {
                        verifying = false
                    }
                }
            }
        }
    }
    LaunchedEffect(controller) { controller.start(store.session.cookies + store.accountsState.value.guestCookies) }
    DisposableEffect(controller) { onDispose(controller::close) }
    Column(Modifier.fillMaxSize()) {
        if (verifying) AppLinearProgressIndeterminate(modifier = Modifier.fillMaxWidth())
        if (failure) {
            Text(AccountStrings.loginFailed)
            AppTextButton(text = AccountStrings.retry, onClick = {
                lastCandidate = null
                controller.refreshCookies()
            })
        }
        controller.Content(Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
internal actual fun AccountQrConfirmationPane(url: String, owner: ZhihuAccountClient, modifier: Modifier) {
    require(isAccountQrConfirmationUrl(url))
    val scope = rememberCoroutineScope()
    val controller = remember(owner, url) {
        AppleLoginBrowser(owner, scope, url) { location, cookies ->
            if (location.isZhihuLoginRiskControl()) owner.reportHumanVerification(location.toString(), cookies)
        }
    }
    LaunchedEffect(controller) { controller.start(owner.load().cookies) }
    DisposableEffect(controller) { onDispose(controller::close) }
    controller.Content(modifier)
}

@Composable
actual fun LoginRiskControlPane(
    url: String,
    cookies: Map<String, String>,
    onCookiesChanged: (Map<String, String>) -> Unit,
    onCookieReaderReady: ((suspend () -> Map<String, String>)?) -> Unit,
    onVerificationComplete: () -> Unit,
) {
    val owner = rememberZhihuAccountStore().client
    val scope = rememberCoroutineScope()
    val cookiesChanged by rememberUpdatedState(onCookiesChanged)
    val readerReady by rememberUpdatedState(onCookieReaderReady)
    val verified by rememberUpdatedState(onVerificationComplete)
    val controller = remember(owner, url) {
        AppleLoginBrowser(owner, scope, url, onVerified = { verified() }) { _, updated -> cookiesChanged(updated) }
    }
    LaunchedEffect(controller) {
        controller.start(cookies)
        readerReady { controller.readCookies() }
    }
    DisposableEffect(controller) {
        onDispose {
            readerReady(null)
            controller.close()
        }
    }
    controller.Content(Modifier.fillMaxSize())
}

/** 登录页使用独立 WK 会话，旧账号或其他网页的 Cookie 不能成为新登录候选。 */
private class AppleLoginBrowser(
    private val owner: ZhihuAccountClient,
    private val scope: CoroutineScope,
    private val initialUrl: String,
    private val onVerified: (() -> Unit)? = null,
    private val onCookies: (Url, Map<String, String>) -> Unit,
) : AutoCloseable {
    private val configuration = WKWebViewConfiguration().apply {
        websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore()
    }
    private val cookieStore = configuration.websiteDataStore.httpCookieStore
    private var closed = false
    private var ready = false
    private var verificationSent = false
    private var failure by mutableStateOf(false)
    private val current: Boolean get() = !closed && owner.isCurrent
    private val view = WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = configuration)
    private val factory: () -> WKWebView = { view }
    private val observer = object : NSObject(), WKHTTPCookieStoreObserverProtocol {
        override fun cookiesDidChangeInCookieStore(cookieStore: WKHTTPCookieStore) = refreshCookies()
    }
    private val messages = object : NSObject(), WKScriptMessageHandlerProtocol {
        override fun userContentController(userContentController: WKUserContentController, didReceiveScriptMessage: WKScriptMessage) {
            val frame = didReceiveScriptMessage.frameInfo
            val url = frame.request.URL?.absoluteString?.trustedLoginUrl()
            if (current && ready && !verificationSent && didReceiveScriptMessage.webView === view && frame.mainFrame &&
                url?.host == "www.zhihu.com" && didReceiveScriptMessage.body == "verified"
            ) {
                verificationSent = true
                onVerified?.invoke()
            }
        }
    }
    private val delegate = object : NSObject(), WKNavigationDelegateProtocol, WKUIDelegateProtocol {
        override fun webView(webView: WKWebView, decidePolicyForNavigationAction: WKNavigationAction, decisionHandler: (WKNavigationActionPolicy) -> Unit) {
            val action = decidePolicyForNavigationAction
            val permitted = current && (action.targetFrame?.mainFrame == false || action.request.URL?.absoluteString?.trustedLoginUrl() != null)
            decisionHandler(if (permitted) WKNavigationActionPolicy.WKNavigationActionPolicyAllow else WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
        }

        override fun webView(webView: WKWebView, createWebViewWithConfiguration: WKWebViewConfiguration, forNavigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures): WKWebView? {
            if (current && forNavigationAction.targetFrame == null && forNavigationAction.request.URL?.absoluteString?.trustedLoginUrl() != null) {
                view.loadRequest(forNavigationAction.request)
            }
            return null
        }

        override fun webView(webView: WKWebView, decidePolicyForNavigationResponse: WKNavigationResponse, decisionHandler: (WKNavigationResponsePolicy) -> Unit) {
            if (current && decidePolicyForNavigationResponse.forMainFrame &&
                ((decidePolicyForNavigationResponse.response as? NSHTTPURLResponse)?.statusCode ?: 200) >= 400
            ) failure = true
            decisionHandler(WKNavigationResponsePolicy.WKNavigationResponsePolicyAllow)
        }

        override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) = refreshCookies()

        @ObjCSignatureOverride
        override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) = failed(withError)

        @ObjCSignatureOverride
        override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) = failed(withError)

        override fun webViewWebContentProcessDidTerminate(webView: WKWebView) {
            if (current) failure = true
        }
    }

    init {
        require(initialUrl.trustedLoginUrl() != null)
        view.navigationDelegate = delegate
        view.UIDelegate = delegate
        view.customUserAgent = ZHIHU_DESKTOP_USER_AGENT
        cookieStore.addObserver(observer)
        if (onVerified != null) {
            configuration.userContentController.addScriptMessageHandler(messages, name = "ZhihuVerification")
            configuration.userContentController.addUserScript(
                WKUserScript(
                    source = "if (location.origin === 'https://www.zhihu.com') { window.ZhihuVerification = { postMessage: function(value) { window.webkit.messageHandlers.ZhihuVerification.postMessage(value); } };\n$verificationResponseObserver\n}",
                    injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                    forMainFrameOnly = true,
                ),
            )
        }
    }

    suspend fun start(cookies: Map<String, String>) {
        for ((name, value) in cookies) {
            if (!current) return
            val cookie = NSHTTPCookie.cookieWithProperties(mapOf(
                NSHTTPCookieName to name,
                NSHTTPCookieValue to value,
                NSHTTPCookieDomain to ".zhihu.com",
                NSHTTPCookiePath to "/",
                NSHTTPCookieSecure to "TRUE",
            )) ?: continue
            suspendCancellableCoroutine<Unit> { continuation ->
                cookieStore.setCookie(cookie) { if (continuation.isActive) continuation.resume(Unit) }
            }
        }
        if (!current) return
        ready = true
        view.loadRequest(NSURLRequest.requestWithURL(checkNotNull(NSURL.URLWithString(initialUrl))))
    }

    suspend fun readCookies(): Map<String, String> = suspendCancellableCoroutine { continuation ->
        cookieStore.getAllCookies { values ->
            if (!continuation.isActive) return@getAllCookies
            if (!current) {
                continuation.cancel()
            } else {
                continuation.resume(values.filterIsInstance<NSHTTPCookie>().filter {
                    isZhihuRequestHost(it.domain.removePrefix("."))
                }.associate { it.name to it.value })
            }
        }
    }

    fun refreshCookies() {
        if (!current || !ready) return
        val location = view.URL?.absoluteString ?: return
        val url = location.trustedLoginUrl() ?: return
        scope.launch {
            val cookies = readCookies()
            if (current && view.URL?.absoluteString == location) onCookies(url, cookies)
        }
    }

    private fun failed(error: NSError) {
        if (current && !(error.domain == NSURLErrorDomain && error.code == NSURLErrorCancelled)) failure = true
    }

    @Composable
    fun Content(modifier: Modifier) {
        Column(modifier) {
            if (failure) {
                Text(AccountStrings.webLoginLoadFailed)
                AppTextButton(text = AccountStrings.retry, onClick = {
                    failure = false
                    if (view.URL == null) view.loadRequest(NSURLRequest.requestWithURL(checkNotNull(NSURL.URLWithString(initialUrl)))) else view.reload()
                })
            }
            AppleWebPageView(Modifier.fillMaxWidth().weight(1f), factory)
        }
    }

    override fun close() {
        closed = true
        cookieStore.removeObserver(observer)
        configuration.userContentController.removeScriptMessageHandlerForName("ZhihuVerification")
        configuration.userContentController.removeAllUserScripts()
        view.navigationDelegate = null
        view.UIDelegate = null
        view.stopLoading()
    }
}

private fun String.trustedLoginUrl(): Url? = runCatching { Url(this) }.getOrNull()?.takeIf {
    it.protocol == URLProtocol.HTTPS && it.port == 443 && it.user == null && it.password == null && isZhihuRequestHost(it.host)
}
