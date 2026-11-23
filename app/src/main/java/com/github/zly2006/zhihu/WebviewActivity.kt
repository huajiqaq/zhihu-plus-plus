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

package com.github.zly2006.zhihu

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.webkit.WebViewClientCompat
import com.github.zly2006.zhihu.account.AccountVerificationDialogs
import com.github.zly2006.zhihu.account.ZhihuAccountClient
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.account.webViewFailureObserver
import com.github.zly2006.zhihu.data.ZHIHU_ANDROID_USER_AGENT
import com.github.zly2006.zhihu.data.ZHIHU_LOGIN_REQUIRED_MESSAGE
import com.github.zly2006.zhihu.data.isZhihuRequestHost
import com.github.zly2006.zhihu.navigation.WebPage
import com.github.zly2006.zhihu.navigation.isWebPageUrl
import com.github.zly2006.zhihu.navigation.requestLoginNavigation
import com.github.zly2006.zhihu.navigation.resolveNavigationUrl
import com.github.zly2006.zhihu.platform.ACTION_OPEN_ZHIHU_ACCOUNT_WEB
import com.github.zly2006.zhihu.theme.ZhihuTheme
import com.github.zly2006.zhihu.ui.components.WebPageScreen
import com.github.zly2006.zhihu.util.enableEdgeToEdgeCompat

class WebviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.data?.toString() ?: run {
            finish()
            return
        }
        enableEdgeToEdgeCompat()
        setContent {
            ZhihuTheme {
                val account = rememberZhihuAccountStore().client
                if (intent.action == ACTION_OPEN_ZHIHU_ACCOUNT_WEB && intent.data?.isAccountPage() == true) {
                    AccountWebPage(url, account)
                } else {
                    WebPageScreen(url, onClose = ::finish)
                }
                AccountVerificationDialogs(
                    client = account,
                    onLogin = {
                        requestLoginNavigation()
                        finish()
                    },
                )
            }
        }
    }

    private fun Uri.isAccountPage(): Boolean = isWebPageUrl(toString()) &&
        scheme.equals("https", ignoreCase = true) &&
        userInfo == null &&
        (host.equals("zhihu.com", ignoreCase = true) || isZhihuRequestHost(host.orEmpty()))

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun AccountWebPage(url: String, account: ZhihuAccountClient) {
        val observeHttpFailure = remember(account) { account.webViewFailureObserver() }
        Surface(Modifier.fillMaxSize().systemBarsPadding()) {
            AndroidView(
                factory = { context ->
                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        account.load().cookies.forEach { (name, value) ->
                            setCookie("https://www.zhihu.com/", "$name=$value; Domain=.zhihu.com; Path=/; Secure")
                        }
                        flush()
                    }
                    WebView(context).apply {
                        settings.userAgentString = ZHIHU_ANDROID_USER_AGENT
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        webViewClient = object : WebViewClientCompat() {
                            private var navigation = 0

                            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                navigation++
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                if (view.url != url || !url.toUri().isAccountPage()) return
                                val visit = navigation
                                view.evaluateJavascript("(function(){if(!document.body)return false;var body=document.body.cloneNode(true);body.querySelectorAll('.RichText,.QuestionRichText,.RichContent-inner,script,style,blockquote').forEach(function(node){node.remove();});return body.textContent.includes('$ZHIHU_LOGIN_REQUIRED_MESSAGE');})()") { required ->
                                    if (required == "true" && visit == navigation && view.url == url) account.reportLoginRequired()
                                }
                            }

                            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) =
                                observeHttpFailure(request, errorResponse)

                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                if (!request.isForMainFrame) return false
                                val uri = request.url
                                if ((uri.isAccountPage() || uri.scheme == "zhihu") && uri.pathSegments.firstOrNull() == "done") {
                                    finish()
                                    return true
                                }
                                when (val destination = resolveNavigationUrl(uri.toString())) {
                                    is WebPage -> {
                                        if (uri.isAccountPage() && uri.host != "link.zhihu.com") return false
                                        startActivity(Intent(this@WebviewActivity, WebviewActivity::class.java).setData(destination.url.toUri()))
                                    }
                                    null -> return true
                                    else -> {
                                        startActivity(Intent(Intent.ACTION_VIEW, uri, this@WebviewActivity, MainActivity::class.java))
                                        finish()
                                    }
                                }
                                return true
                            }
                        }
                        loadUrl(url)
                    }
                },
                onReset = null,
                onRelease = {
                    it.stopLoading()
                    it.destroy()
                },
            )
        }
    }
}
