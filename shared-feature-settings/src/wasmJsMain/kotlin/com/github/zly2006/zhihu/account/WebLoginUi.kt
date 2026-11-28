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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.github.zly2006.zhihu.i18n.AccountStrings
import com.github.zly2006.zhihu.i18n.AppStrings
import com.github.zly2006.zhihu.platform.rememberSystemUrlOpener
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppDialog
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppTextButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

actual val supportedLoginMethods: List<LoginMethod> = listOf(LoginMethod.Qr, LoginMethod.Web)
actual val isLoginRiskControlSupported = false

@Composable
actual fun WebLoginPane(onLoginSuccess: (String) -> Unit, onBrowserSessionVerified: () -> Unit) {
    val store = rememberZhihuAccountStore()
    val owner = store.client
    val status by store.browserSessionStatus.collectAsState()
    val open = rememberSystemUrlOpener()
    val scope = rememberCoroutineScope()
    val onVerified by rememberUpdatedState(onBrowserSessionVerified)
    var refreshing by remember(store) { mutableStateOf(false) }
    var failure by remember(store) { mutableStateOf<(() -> String)?>(null) }
    var awaitingConfirmation by remember(owner) { mutableStateOf(false) }
    var businessPermissions by remember(owner) { mutableStateOf(emptySet<String>()) }
    val challenge by checkNotNull(owner.browserVerification).collectAsState()
    suspend fun refreshSession() {
        when (store.refreshBrowserSession()) {
            BrowserSessionStatus.Authenticated -> {
                awaitingConfirmation = false
                onVerified()
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
            if (!refreshing) scope.launch {
                refreshing = true
                try {
                    val bridge = owner.requestBrowserBridgeStatus()
                    businessPermissions = bridge.businessPermissions
                    val issue = bridge.readIssue
                    if (issue != null) {
                        failure = { issue.message }
                    } else if (bridge.profilePermission != "checking") {
                        failure = null
                        refreshSession()
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
            summary = failure?.invoke() ?: AccountStrings.webLoginConfirmSummary,
            confirmText = if (refreshing) AccountStrings.webVerifying else AccountStrings.webChallengeAuthorized,
            confirmEnabled = !refreshing,
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
                                refreshSession()
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
            failure?.invoke() ?: when (status) {
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
                awaitingConfirmation = true
            },
            modifier = Modifier.testTag("login_web_official"),
        )
        AppButton(
            text = AccountStrings.webUseBrowserSession,
            enabled = !refreshing,
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
                    enabled = !refreshing,
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

private fun nativeLoginUnavailable(): Nothing = throw UnsupportedOperationException(AccountStrings.webOnlySignIn)

internal actual val phoneLoginDeviceInfo: ZhihuPhoneLoginDeviceInfo get() = nativeLoginUnavailable()

actual fun decodePhoneLoginCaptchaImage(content: String): ImageBitmap? = nativeLoginUnavailable()

actual fun generateQrLoginBitmap(content: String): ImageBitmap = webQrLoginBitmap(content)

internal actual fun readLoginRiskControlCookies(url: String): Map<String, String> = nativeLoginUnavailable()

@Composable
actual fun QrLoginPane(onLoginSuccess: (String) -> Unit) = WebQrLoginPane(onLoginSuccess)

@Composable
actual fun LoginRiskControlPane(
    url: String,
    cookies: Map<String, String>,
    onCookiesChanged: (Map<String, String>) -> Unit,
    onVerificationComplete: () -> Unit,
): Unit = nativeLoginUnavailable()
