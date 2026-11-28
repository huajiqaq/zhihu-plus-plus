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

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Base64
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationManagerCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.github.zly2006.zhihu.data.ZHIHU_ANDROID_USER_AGENT
import com.github.zly2006.zhihu.data.currentAndroidApplicationContext
import com.github.zly2006.zhihu.data.isZhihuRequestHost
import com.github.zly2006.zhihu.ui.components.setupUpWebviewClient
import com.github.zly2006.zhihu.util.isZhihuLoginRiskControl
import com.github.zly2006.zhihu.util.telemetry
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import java.util.TimeZone
import kotlinx.coroutines.launch

actual val supportedLoginMethods: List<LoginMethod> = listOf(
    LoginMethod.Phone,
    LoginMethod.Qr,
    LoginMethod.Web,
)

actual val isLoginRiskControlSupported: Boolean = true

actual val phoneLoginDeviceInfo: ZhihuPhoneLoginDeviceInfo
    get() = currentAndroidApplicationContext().phoneLoginDeviceInfo()

actual fun decodePhoneLoginCaptchaImage(content: String) = runCatching {
    val encoded = content.substringAfter("base64,", content)
    val bytes = Base64.decode(encoded, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()

@Composable
actual fun QrLoginPane(onLoginSuccess: (String) -> Unit) {
    val context = LocalContext.current
    AccountQrLoginPane { username ->
        telemetry(context, "login")
        onLoginSuccess(username)
    }
}

@Composable
actual fun WebLoginPane(onLoginSuccess: (String) -> Unit, onBrowserSessionVerified: () -> Unit) {
    val context = LocalContext.current
    val accountStore = rememberZhihuAccountStore()
    val accountClient = accountStore.client
    val scope = rememberCoroutineScope()
    val failureObserver = remember(accountClient) { accountClient.webViewFailureObserver { readWebViewCookies(ZHIHU_HOME_URL) } }
    var isVerifying by remember { mutableStateOf(false) }

    val loginSuccess = rememberUpdatedState(onLoginSuccess)
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                configureWebLogin(this, accountStore.session.cookies + accountStore.accountsState.value.guestCookies, failureObserver, { url, cookies ->
                    accountClient.reportHumanVerification(url, cookies)
                }) { cookies ->
                    if (accountStore.client === accountClient &&
                        accountClient.verificationState.value.phase == HumanVerificationPhase.ACTIVE &&
                        !isVerifying &&
                        !cookies["z_c0"].isNullOrBlank()
                    ) {
                        isVerifying = true
                        scope.launch {
                            try {
                                if (accountStore.login(accountClient, cookies.toMutableMap())) {
                                    telemetry(context, "login")
                                    loginSuccess.value(accountStore.session.username)
                                }
                            } finally {
                                isVerifying = false
                            }
                        }
                    }
                }
            }
        },
        onRelease = {
            it.stopLoading()
            it.destroy()
        },
    )
}

@Composable
actual fun LoginRiskControlPane(
    url: String,
    cookies: Map<String, String>,
    onCookiesChanged: (Map<String, String>) -> Unit,
    onVerificationComplete: () -> Unit,
) {
    val cookiesChanged = rememberUpdatedState(onCookiesChanged)
    val verificationComplete = rememberUpdatedState(onVerificationComplete)
    val accountClient = rememberZhihuAccountStore().client
    val failureObserver = remember(accountClient) { accountClient.webViewFailureObserver() }
    // 登录页需要固定视口内滚动；正文 WebviewComp 会按 HTML 高度撑开并在重组时重新配置。
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                configureRiskControlWebView(
                    webView = this,
                    url = url,
                    cookies = cookies,
                    onCookiesChanged = { cookiesChanged.value(it) },
                    onVerificationComplete = { verificationComplete.value() },
                    onHttpError = failureObserver,
                )
            }
        },
        onRelease = {
            it.stopLoading()
            it.destroy()
        },
    )
}

@SuppressLint("SetJavaScriptEnabled")
private fun configureWebLogin(
    webView: WebView,
    cookies: Map<String, String>,
    onHttpError: (WebResourceRequest, WebResourceResponse) -> Unit,
    onRiskControl: (String, Map<String, String>) -> Unit,
    onCookiesReady: (Map<String, String>) -> Unit,
) {
    webView.setupUpWebviewClient()
    webView.settings.javaScriptEnabled = true
    webView.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest,
        ): Boolean {
            if (request.url.toString() == ZHIHU_HOME_URL) {
                webView.settings.userAgentString = ZHIHU_ANDROID_USER_AGENT
            }
            return request.url?.scheme == "zhihu"
        }

        override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest, errorResponse: WebResourceResponse) {
            onHttpError(request, errorResponse)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            val target = url?.let { runCatching { Url(it) }.getOrNull() } ?: return
            if (target.isZhihuLoginRiskControl()) {
                onRiskControl(target.toString(), readWebViewCookies(ZHIHU_HOME_URL) + readWebViewCookies(url))
                return
            }
            if (target.protocol == URLProtocol.HTTPS &&
                isZhihuRequestHost(target.host) &&
                target.encodedPath != "/signin" &&
                !target.isZhihuLoginRiskControl()
            ) {
                onCookiesReady(readWebViewCookies(ZHIHU_HOME_URL) + readWebViewCookies(url))
            }
        }
    }
    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptThirdPartyCookies(webView, true)
    cookies.forEach { (name, value) ->
        cookieManager.setCookie(ZHIHU_HOME_URL, "$name=$value; Domain=.zhihu.com; Path=/")
    }
    cookieManager.flush()
    webView.loadUrl(ZHIHU_SIGNIN_URL)
}

@SuppressLint("SetJavaScriptEnabled")
private fun configureRiskControlWebView(
    webView: WebView,
    url: String,
    cookies: Map<String, String>,
    onCookiesChanged: (Map<String, String>) -> Unit,
    onVerificationComplete: () -> Unit,
    onHttpError: (WebResourceRequest, WebResourceResponse) -> Unit,
) {
    webView.settings.javaScriptEnabled = true
    webView.settings.domStorageEnabled = true
    webView.settings.userAgentString = ZHIHU_DESKTOP_USER_AGENT
    // WebView 110 的此验证码页硬件合成会跳帧，使用软件层；不影响正文 WebView。
    webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptThirdPartyCookies(webView, true)
    cookies.forEach { (name, value) ->
        cookieManager.setCookie(ZHIHU_HOME_URL, "$name=$value; Domain=.zhihu.com; Path=/")
    }
    cookieManager.flush()
    if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
        WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    ) {
        val origin = setOf("https://www.zhihu.com")
        var completed = false
        WebViewCompat.addWebMessageListener(webView, "ZhihuVerification", origin) { view, message, source, isMainFrame, _ ->
            if (!completed && isMainFrame && source.scheme == "https" && source.host == "www.zhihu.com" && message.data == "verified") {
                completed = true
                cookieManager.flush()
                onCookiesChanged(readWebViewCookies(ZHIHU_HOME_URL) + readWebViewCookies(view.url ?: url))
                onVerificationComplete()
            }
        }
        WebViewCompat.addDocumentStartJavaScript(webView, verificationResponseObserver, origin)
    }
    webView.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest,
        ): Boolean = request.url?.scheme == "zhihu"

        override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest, errorResponse: WebResourceResponse) {
            onHttpError(request, errorResponse)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            cookieManager.flush()
            onCookiesChanged(readWebViewCookies(ZHIHU_HOME_URL) + readWebViewCookies(url))
        }
    }
    webView.loadUrl(url)
}

internal actual fun readLoginRiskControlCookies(url: String): Map<String, String> {
    CookieManager.getInstance().flush()
    return readWebViewCookies(ZHIHU_HOME_URL) + readWebViewCookies(url)
}

private fun readWebViewCookies(url: String?): Map<String, String> =
    parseCookieAssignments(
        CookieManager.getInstance().getCookie(url ?: ZHIHU_HOME_URL).orEmpty(),
    )

private fun Context.phoneLoginDeviceInfo(): ZhihuPhoneLoginDeviceInfo {
    val runtime = Runtime.getRuntime()
    val storage = StatFs(Environment.getDataDirectory().path)
    val installTime = runCatching {
        packageManager.getPackageInfo(packageName, 0).firstInstallTime
    }.getOrDefault(0L)
    return ZhihuPhoneLoginDeviceInfo(
        timezoneOffsetSeconds = TimeZone.getDefault().rawOffset / 1_000L,
        appInstallTimeMillis = installTime,
        notificationEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled(),
        bluetoothAvailable = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH),
        phoneBrand = Build.BRAND,
        phoneModel = Build.MODEL,
        androidRelease = Build.VERSION.RELEASE,
        cpuType = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
        cpuCount = runtime.availableProcessors(),
        cpuUsage = "0.0",
        totalMemoryMegabytes = (runtime.totalMemory() / 1_048_576L).toInt(),
        freeMemoryMegabytes = (runtime.freeMemory() / 1_048_576L).toInt(),
        totalStorageMegabytes = (storage.totalBytes / 1_048_576L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        freeStorageMegabytes = (storage.availableBytes / 1_048_576L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
    )
}
