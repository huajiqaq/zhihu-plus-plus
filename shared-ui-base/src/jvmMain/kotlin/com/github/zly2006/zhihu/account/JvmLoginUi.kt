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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.github.zly2006.zhihu.platform.platformName
import com.github.zly2006.zhihu.ui.components.DesktopWebviewComp
import com.github.zly2006.zhihu.util.isZhihuLoginRiskControl
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.coroutines.launch
import netscape.javascript.JSObject
import org.jetbrains.skia.Image
import java.util.Base64
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

actual val supportedLoginMethods: List<LoginMethod> = listOf(
    LoginMethod.Phone,
    LoginMethod.Qr,
    LoginMethod.Web,
)

actual val isLoginRiskControlSupported: Boolean = true

actual val phoneLoginDeviceInfo: ZhihuPhoneLoginDeviceInfo
    get() {
        val runtime = Runtime.getRuntime()
        return ZhihuPhoneLoginDeviceInfo(
            timezoneOffsetSeconds = TimeZone.getDefault().rawOffset / 1_000L,
            appInstallTimeMillis = 0,
            notificationEnabled = false,
            bluetoothAvailable = false,
            phoneBrand = platformName,
            phoneModel = System.getProperty("os.arch").orEmpty(),
            androidRelease = "12",
            cpuType = System.getProperty("os.arch").orEmpty(),
            cpuCount = runtime.availableProcessors(),
            cpuUsage = "0.0",
            totalMemoryMegabytes = (runtime.maxMemory() / 1_048_576L).toInt(),
            freeMemoryMegabytes = (runtime.freeMemory() / 1_048_576L).toInt(),
            totalStorageMegabytes = 0,
            freeStorageMegabytes = 0,
        )
    }

actual fun decodePhoneLoginCaptchaImage(content: String) = runCatching {
    val encoded = content.substringAfter("base64,", content)
    Image.makeFromEncoded(Base64.getDecoder().decode(encoded)).toComposeImageBitmap()
}.getOrNull()

@Composable
actual fun QrLoginPane(onLoginSuccess: (String) -> Unit) {
    val store = rememberZhihuAccountStore()
    val owner = store.client
    val cookies = remember(owner) { (store.session.cookies + store.accountsState.value.guestCookies).toMutableMap() }
    val client = rememberLoginHttpClient(cookies)
    SharedQrLoginPane(
        client = client,
        cookies = cookies,
        onRiskControl = { url, candidate -> owner.reportHumanVerification(url, candidate) },
        onLoginSuccess = { cookies ->
            if (store.login(cookies.toMutableMap())) {
                onLoginSuccess(store.session.username)
                true
            } else {
                false
            }
        },
    )
}

@Composable
actual fun WebLoginPane(onLoginSuccess: (String) -> Unit) {
    val store = rememberZhihuAccountStore()
    val owner = store.client
    val currentOwner = rememberUpdatedState(owner)
    val loginSuccess = rememberUpdatedState(onLoginSuccess)
    key(owner) {
        val scope = rememberCoroutineScope()
        val initialCookies = remember { store.session.cookies + store.accountsState.value.guestCookies }
        val candidateCookies = remember { AtomicReference(initialCookies) }
        var lastVerifiedCookies by remember { mutableStateOf<Map<String, String>?>(null) }
        var isVerifying by remember { mutableStateOf(false) }

        DesktopWebviewComp(
            url = ZHIHU_SIGNIN_URL,
            modifier = Modifier.fillMaxSize(),
            initialCookies = initialCookies,
            onCookiesChanged = { cookies ->
                if (store.client === owner && currentOwner.value === owner) {
                    candidateCookies.set(cookies.toMap())
                    if (owner.verificationState.value.phase == HumanVerificationPhase.ACTIVE &&
                        !cookies["z_c0"].isNullOrBlank() &&
                        cookies != lastVerifiedCookies &&
                        !isVerifying
                    ) {
                        lastVerifiedCookies = cookies
                        isVerifying = true
                        scope.launch {
                            try {
                                if (store.login(cookies.toMutableMap())) {
                                    loginSuccess.value(store.session.username)
                                }
                            } finally {
                                isVerifying = false
                            }
                        }
                    }
                }
            },
            onNavigation = { location ->
                if (store.client === owner &&
                    currentOwner.value === owner &&
                    location.trustedVerificationUrl()?.isZhihuLoginRiskControl() == true
                ) {
                    owner.reportHumanVerification(location, candidateCookies.get())
                }
            },
        )
    }
}

@Composable
actual fun LoginRiskControlPane(
    url: String,
    cookies: Map<String, String>,
    onCookiesChanged: (Map<String, String>) -> Unit,
    onVerificationComplete: () -> Unit,
) {
    val store = rememberZhihuAccountStore()
    val owner = store.client
    val currentOwner = rememberUpdatedState(owner)
    val cookiesChanged = rememberUpdatedState(onCookiesChanged)
    val verificationComplete = rememberUpdatedState(onVerificationComplete)
    val completed = remember(owner, url) { AtomicBoolean(false) }
    val active = remember(owner, url) { AtomicBoolean(true) }
    // JavaFX 只弱引用 JSObject 中的 Java 对象，必须持有当前文档的桥，直到页面销毁。
    val bridgeRef = remember(owner, url) { AtomicReference<JvmVerificationBridge?>() }
    DisposableEffect(owner, url) {
        onDispose {
            active.set(false)
            bridgeRef.set(null)
        }
    }
    key(owner, url) {
        DesktopWebviewComp(
            url = url,
            modifier = Modifier.fillMaxSize(),
            initialCookies = cookies,
            onCookiesChanged = { updated ->
                if (active.get() && store.client === owner && currentOwner.value === owner) cookiesChanged.value(updated)
            },
            onDocumentReady = { engine, onJavaScriptMessage ->
                val document = engine.document
                if (active.get() &&
                    document != null &&
                    engine.location.trustedVerificationUrl()?.isZhihuLoginRiskControl() == true &&
                    document.documentURI.trustedVerificationUrl()?.isZhihuLoginRiskControl() == true
                ) {
                    val callback = verificationComplete.value
                    lateinit var bridge: JvmVerificationBridge
                    bridge = JvmVerificationBridge(
                        isCurrent = {
                            active.get() &&
                                bridgeRef.get() === bridge &&
                                store.client === owner &&
                                currentOwner.value === owner &&
                                verificationComplete.value === callback &&
                                engine.document === document &&
                                engine.location.trustedVerificationUrl() != null &&
                                document.documentURI.trustedVerificationUrl() != null
                        },
                        onJavaScriptMessage = onJavaScriptMessage,
                        completed = completed,
                        onVerificationComplete = callback,
                    )
                    bridgeRef.set(bridge)
                    runCatching {
                        (engine.executeScript("window") as JSObject).setMember("ZhihuVerification", bridge)
                        // JavaFX 仅在文档可用后注入 observer，初始化请求可能漏检；仍可手动确认。
                        engine.executeScript(verificationResponseObserver)
                    }.onFailure { bridgeRef.compareAndSet(bridge, null) }
                } else {
                    bridgeRef.set(null)
                }
            },
        )
    }
}

internal actual fun readLoginRiskControlCookies(url: String): Map<String, String> = buildMap {
    val handler = java.net.CookieHandler.getDefault() ?: return@buildMap
    for (origin in arrayOf(ZHIHU_HOME_URL, url)) {
        handler.get(java.net.URI(origin), emptyMap()).forEach { (name, values) ->
            if (name.equals("Cookie", ignoreCase = true)) {
                values.forEach { putAll(parseCookieAssignments(it)) }
            }
        }
    }
}

/** JavaFX 通过反射调用桥，因此类和 postMessage 方法必须公开并保留方法名。 */
class JvmVerificationBridge internal constructor(
    private val isCurrent: () -> Boolean,
    private val onJavaScriptMessage: (() -> Unit) -> Unit,
    private val completed: AtomicBoolean,
    private val onVerificationComplete: () -> Unit,
) {
    fun postMessage(message: String) {
        if (message != "verified" || completed.get() || !isCurrent()) return
        onJavaScriptMessage {
            if (isCurrent() && completed.compareAndSet(false, true)) onVerificationComplete()
        }
    }
}

private fun String?.trustedVerificationUrl(): Url? = this
    ?.let {
        runCatching { Url(it) }.getOrNull()
    }?.takeIf {
        it.protocol == URLProtocol.HTTPS && it.host == "www.zhihu.com" && it.port == 443 && it.user == null && it.password == null
    }
