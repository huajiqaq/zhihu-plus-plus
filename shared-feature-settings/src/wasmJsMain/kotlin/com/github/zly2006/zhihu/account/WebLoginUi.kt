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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.i18n.AccountStrings
import com.github.zly2006.zhihu.i18n.AppStrings
import com.github.zly2006.zhihu.platform.rememberSystemUrlOpener
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppDialog
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppTextButton
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.skia.Image

actual val supportedLoginMethods: List<LoginMethod> = listOf(LoginMethod.Phone, LoginMethod.Qr, LoginMethod.Web)
actual val isLoginRiskControlSupported = false

@Composable
actual fun WebLoginPane(onLoginSuccess: (String) -> Unit) {
    val store = rememberZhihuAccountStore()
    val owner = store.client
    val status by store.browserSessionStatus.collectAsState()
    val open = rememberSystemUrlOpener()
    val scope = rememberCoroutineScope()
    val onVerified by rememberUpdatedState(onLoginSuccess)
    val bridgeIssue = rememberBrowserLoginIssue()
    var refreshing by remember(store) { mutableStateOf(false) }
    var failure by remember(store) { mutableStateOf<(() -> String)?>(null) }
    var awaitingConfirmation by remember(owner) { mutableStateOf(false) }
    var businessPermissions by remember(owner) { mutableStateOf(emptySet<String>()) }
    val challenge by checkNotNull(owner.browserVerification).collectAsState()
    suspend fun refreshSession(completeLogin: Boolean) {
        when (store.refreshBrowserSession()) {
            BrowserSessionStatus.Authenticated -> {
                awaitingConfirmation = false
                if (completeLogin) {
                    store.reportLoginSuccess(store.client, store.session.username)
                    onVerified(store.session.username)
                }
            }
            BrowserSessionStatus.Guest -> failure = { AccountStrings.webSessionGuest }
            BrowserSessionStatus.Expired -> failure = { AccountStrings.webSessionExpired }
            BrowserSessionStatus.Denied -> failure = { AccountStrings.webSessionDenied }
            BrowserSessionStatus.MissingBridge -> failure = { AccountStrings.webSessionMissingBridge }
            else -> failure = { AccountStrings.webVerificationFailed }
        }
    }
    DisposableEffect(owner) {
        val subscription = observeBrowserBridgePermission {
            if (!refreshing && !owner.browserCommentRestoreRequired.value) scope.launch {
                refreshing = true
                try {
                    val bridge = owner.requestBrowserBridgeStatus()
                    businessPermissions = bridge.businessPermissions
                    val issue = bridge.readIssue ?: bridge.cookieIssue?.takeIf { it == WebBridgeIssue.SessionIsolationUnsupported }
                    if (issue != null) {
                        failure = { issue.message }
                    } else if (bridge.profilePermission != "checking") {
                        failure = null
                        refreshSession(completeLogin = awaitingConfirmation)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    failure = { browserBridgeFailureMessage(error) }
                } finally {
                    refreshing = false
                }
            }
        }
        onDispose { subscription.close() }
    }
    if (awaitingConfirmation && challenge == null) {
        AppDialog(
            title = AccountStrings.webLoginConfirmTitle,
            summary = bridgeIssue?.invoke() ?: failure?.invoke() ?: AccountStrings.webLoginConfirmSummary,
            confirmText = if (refreshing) AccountStrings.webVerifying else AccountStrings.webChallengeAuthorized,
            confirmEnabled = !refreshing && bridgeIssue == null,
            dismissText = AccountStrings.cancel,
            onDismissRequest = { if (!refreshing) awaitingConfirmation = false },
            onConfirm = {
                if (!refreshing) {
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        refreshing = true
                        failure = null
                        try {
                            if (!owner.requestBrowserLoginAction("authorizeProfile")) {
                                failure = { AccountStrings.webSessionDenied }
                            } else {
                                refreshSession(completeLogin = true)
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            failure = { browserBridgeFailureMessage(error) }
                        } finally {
                            refreshing = false
                        }
                    }
                }
            },
            modifier = Modifier.testTag("login_web_authorize_dialog"),
        )
    }
    Column(
        Modifier
            .widthIn(max = AppDimens.ContentMaxWidth)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(AppDimens.Space16),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Space12),
    ) {
        Text(
            bridgeIssue?.invoke() ?: failure?.invoke() ?: when (status) {
                BrowserSessionStatus.Unchecked -> AccountStrings.webSessionUnchecked
                BrowserSessionStatus.MissingBridge -> AccountStrings.webSessionMissingBridge
                BrowserSessionStatus.Guest -> AccountStrings.webSessionGuest
                BrowserSessionStatus.Authenticated -> AccountStrings.webSessionAuthenticated
                BrowserSessionStatus.Expired -> AccountStrings.webSessionExpired
                BrowserSessionStatus.Denied -> AccountStrings.webSessionDenied
                BrowserSessionStatus.Unavailable -> AccountStrings.webSessionUnavailable
            },
            Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("login_web_status"),
        )
        AppButton(
            text = AccountStrings.webOpenSignIn,
            onClick = {
                failure = null
                open(ZHIHU_SIGNIN_REFERER_URL)
                awaitingConfirmation = bridgeIssue == null
            },
            modifier = Modifier.testTag("login_web_official"),
        )
        AppButton(
            text = AccountStrings.webUseBrowserSession,
            enabled = !refreshing && bridgeIssue == null,
            onClick = {
                failure = null
                awaitingConfirmation = true
            },
            modifier = Modifier.testTag("login_web_refresh"),
        )
        if (status == BrowserSessionStatus.Authenticated) {
            Text(AccountStrings.webBusinessPermissionsSummary)
            listOf(
                "authorizePrivate" to AccountStrings.webAuthorizePrivate,
                "authorizeInteractions" to AccountStrings.webAuthorizeInteractions,
                "authorizeCreation" to AccountStrings.webAuthorizeCreation,
                "authorizeHistoryWrite" to AccountStrings.webAuthorizeHistoryWrite,
            ).forEach { (operation, label) ->
                val permission = when (operation) {
                    "authorizePrivate" -> "private"
                    "authorizeInteractions" -> "interact"
                    "authorizeCreation" -> "create"
                    else -> "history"
                }
                AppButton(
                    text = if (permission in businessPermissions) AccountStrings.webPermissionAuthorized(label) else label,
                    enabled = !refreshing && bridgeIssue == null,
                    onClick = {
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            refreshing = true
                            failure = null
                            try {
                                if (!owner.requestBrowserLoginAction(operation)) failure = { AccountStrings.webSessionDenied }
                                businessPermissions = owner.requestBrowserBridgeStatus().businessPermissions
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                failure = { browserBridgeFailureMessage(error) }
                            } finally {
                                refreshing = false
                            }
                        }
                    },
                    modifier = Modifier.testTag("login_web_$operation"),
                )
            }
        }
        AppTextButton(text = AppStrings.installBridge, onClick = { open("https://zly2006.github.io/zhihu-plus-plus/zhihu-web-bridge.user.js") })
    }
}

@Composable
internal actual fun LoginMethodReady(method: LoginMethod): Boolean {
    val issue = rememberBrowserLoginIssue()
    if (issue != null) {
        val open = rememberSystemUrlOpener()
        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Space12)) {
            Text(issue(), Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("login_bridge_status"))
            AppTextButton(text = AppStrings.installBridge, onClick = { open("https://zly2006.github.io/zhihu-plus-plus/zhihu-web-bridge.user.js") })
        }
    }
    return issue == null
}

/** bridgeStatus 仅检查管理器能力；未获用户授权前不探测 /me 或读取浏览器会话。 */
@Composable
private fun rememberBrowserLoginIssue(): (() -> String)? {
    val owner = rememberZhihuAccountStore().client
    val restoreRequired by owner.browserCommentRestoreRequired.collectAsState()
    var issue by remember(owner) { mutableStateOf<(() -> String)?>({ AccountStrings.webVerifying }) }
    val scope = rememberCoroutineScope()
    suspend fun checkCapabilities() {
        try {
            val status = owner.requestBrowserBridgeStatus()
            val blocked = status.readIssue ?: status.cookieIssue?.takeIf { it == WebBridgeIssue.SessionIsolationUnsupported }
            issue = blocked?.let { { it.message } }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            issue = { browserBridgeFailureMessage(error) }
        }
    }
    LaunchedEffect(owner) { checkCapabilities() }
    DisposableEffect(owner) {
        val subscription = observeBrowserBridgePermission { scope.launch { checkCapabilities() } }
        onDispose { subscription.close() }
    }
    return if (restoreRequired) ({ AccountStrings.webRestoreIdentitySummary }) else issue
}

internal actual fun createBrowserPhoneLoginBackend(store: ZhihuAccountStore, owner: ZhihuAccountClient): BrowserPhoneLoginBackend? =
    object : BrowserPhoneLoginBackend {
        private suspend fun step(operation: String, vararg fields: Pair<String, String>): JsonObject {
            try {
                val body = if (fields.isEmpty()) "" else JsonObject(fields.associate { it.first to JsonPrimitive(it.second) }).toString()
                return owner.requestBrowserPhoneAction(operation, body)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                throw LoginMessageException { browserBridgeFailureMessage(error) }
            }
        }

        private fun digitsResult(result: JsonObject): ZhihuPhoneDigitsResult = when (result["state"]?.jsonPrimitive?.content) {
            "sent" -> ZhihuPhoneDigitsResult.Sent
            "captcha" -> ZhihuPhoneDigitsResult.CaptchaRequired((result["image"] as? JsonPrimitive)?.takeIf { it.isString }?.content)
            else -> throw LoginMessageException { AccountStrings.smsFailed }
        }

        override suspend fun countries(): List<PhoneLoginCountry> =
            ZhihuJson.decodeJson(step("loginPhoneCountries").getValue("countries"))

        override suspend fun send(phone: String): ZhihuPhoneDigitsResult = digitsResult(step("loginPhoneSend", "phone" to phone))

        override suspend fun captcha(): String? = (digitsResult(step("loginPhoneCaptcha")) as? ZhihuPhoneDigitsResult.CaptchaRequired)?.imageBase64

        override suspend fun verifyCaptcha(input: String): ZhihuPhoneDigitsResult = digitsResult(step("loginPhoneVerifyCaptcha", "captcha" to input))

        override suspend fun signIn(phone: String, digits: String): String? {
            val result = step("loginPhoneCommit", "phone" to phone, "digits" to digits)
            if (result["authorized"] != JsonPrimitive(true)) return null
            return if (store.refreshBrowserSession() == BrowserSessionStatus.Authenticated) store.session.profile?.name else null
        }

        override fun close() {
            if (!owner.isCurrent) return
            owner.httpClient().launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    step("loginPhoneCancel")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // 取消不变更已验证账号；关闭客户端也会撤销旧事务。
                }
            }
        }
    }

private fun nativeLoginUnavailable(): Nothing = throw UnsupportedOperationException(AccountStrings.webOnlySignIn)

internal actual val phoneLoginDeviceInfo: ZhihuPhoneLoginDeviceInfo get() = nativeLoginUnavailable()

actual fun decodePhoneLoginCaptchaImage(content: String): ImageBitmap? = runCatching {
    val bytes = Base64.Default.decode(content.substringAfter("base64,", content).filterNot { it == '\r' || it == '\n' })
    Image.makeFromEncoded(bytes).toComposeImageBitmap()
}.getOrNull()

actual fun generateQrLoginBitmap(content: String): ImageBitmap = webQrLoginBitmap(content)

@Composable
actual fun QrLoginPane(onLoginSuccess: (String) -> Unit) = WebQrLoginPane(onLoginSuccess)

@Composable
internal actual fun AccountQrConfirmationPane(url: String, owner: ZhihuAccountClient, modifier: Modifier) =
    ExternalAccountQrConfirmationPane(url, modifier)

@Composable
actual fun LoginRiskControlPane(
    url: String,
    cookies: Map<String, String>,
    onCookiesChanged: (Map<String, String>) -> Unit,
    onCookieReaderReady: ((suspend () -> Map<String, String>)?) -> Unit,
    onVerificationComplete: () -> Unit,
): Unit = nativeLoginUnavailable()
