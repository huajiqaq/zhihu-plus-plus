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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.github.zly2006.zhihu.i18n.AccountStrings
import com.github.zly2006.zhihu.platform.rememberSystemUrlOpener
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppSpinner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive

private val displayLink = Regex("https://www[.]zhihu[.]com/account/scan/login/[A-Za-z0-9_-]{1,256}")

/** 仅在内存中绘制官方扫码链接，保留二维码四周四个模块的静区。 */
internal fun webQrLoginBitmap(content: String): ImageBitmap {
    require(displayLink.matches(content))
    val matrix = qrcode(0, "M")
    matrix.addData(content)
    matrix.make()
    val modules = matrix.getModuleCount()
    val size = modules + 8
    val bitmap = ImageBitmap(size, size)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply {
        color = Color.White
        isAntiAlias = false
    }
    canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
    paint.color = Color.Black
    for (y in 0 until modules) {
        for (x in 0 until modules) {
            if (matrix.isDark(y, x)) canvas.drawRect(x + 4f, y + 4f, x + 5f, y + 5f, paint)
        }
    }
    return bitmap
}

private enum class QrStage(
    val label: () -> String,
) {
    Idle({ AccountStrings.webQrIdle }),
    Loading({ AccountStrings.qrLoading }),
    Waiting({ AccountStrings.qrScanHint }),
    Scanned({ AccountStrings.qrConfirmInApp }),
    Confirmed({ AccountStrings.webQrConfirmed }),
    Verifying({ AccountStrings.qrVerifying }),
    Expired({ AccountStrings.qrExpired }),
    Official({ AccountStrings.webQrOfficial }),
    Failed({ AccountStrings.webQrStartFailed }),
    CheckOfficialAccount({ AccountStrings.webQrCommitFailed }),
    SignedIn({ AccountStrings.loginSuccess }),
}

@Composable
internal fun WebQrLoginPane(onLoginSuccess: (String) -> Unit) {
    val store = rememberZhihuAccountStore()
    val client = store.client
    val scope = rememberCoroutineScope()
    val onSuccess by rememberUpdatedState(onLoginSuccess)
    val open = rememberSystemUrlOpener()
    var stage by remember(client) { mutableStateOf(QrStage.Idle) }
    var bitmap by remember(client) { mutableStateOf<ImageBitmap?>(null) }
    var failure by remember(client) { mutableStateOf<(() -> String)?>(null) }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(AppDimens.Space16)
            .testTag("qr_login_content"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppDimens.Space12),
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = AccountStrings.qrImageDescription,
                modifier = Modifier
                    .widthIn(max = AppDimens.QrCodeSize)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .testTag("qr_login_image"),
                filterQuality = FilterQuality.None,
            )
        }
        if (stage == QrStage.Loading || stage == QrStage.Verifying) AppSpinner(Modifier.testTag("qr_login_loading"))
        Text(failure?.invoke() ?: stage.label(), Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("qr_login_status"), textAlign = TextAlign.Center)
        when (stage) {
            QrStage.Official, QrStage.CheckOfficialAccount -> AppButton(
                text = if (stage == QrStage.Official) AccountStrings.webOpenSignIn else AccountStrings.webQrCheckAccount,
                onClick = { open(ZHIHU_SIGNIN_REFERER_URL) },
                modifier = Modifier.testTag("qr_login_official"),
            )
            QrStage.Confirmed -> AppButton(AccountStrings.webQrCommit, {
                if (stage != QrStage.Confirmed) return@AppButton
                stage = QrStage.Verifying
                failure = null
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        if (!client.requestBrowserLoginAction("cookieSignIn")) {
                            stage = QrStage.Idle
                            return@launch
                        }
                        val verified = store.refreshBrowserSession() == BrowserSessionStatus.Authenticated
                        val profile = store.session.profile
                        if (verified && store.session.login && profile != null && profile.id.isNotBlank() && profile.name.isNotBlank()) {
                            stage = QrStage.SignedIn
                            onSuccess(profile.name)
                        } else {
                            stage = QrStage.CheckOfficialAccount
                        }
                    } catch (cancelled: CancellationException) {
                        stage = QrStage.CheckOfficialAccount
                        throw cancelled
                    } catch (error: Exception) {
                        failure = { browserBridgeFailureMessage(error) }
                        stage = QrStage.CheckOfficialAccount
                    }
                }
            }, Modifier.testTag("qr_login_commit"))
            QrStage.Idle, QrStage.Expired, QrStage.Failed -> AppButton(
                text = if (stage == QrStage.Idle) AccountStrings.webQrStart else AccountStrings.qrRefresh,
                onClick = {
                    if (stage == QrStage.Loading) return@AppButton
                    stage = QrStage.Loading
                    failure = null
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        try {
                            val result = client.requestBrowserQrStep("loginQrStart")
                            when (result["state"]?.jsonPrimitive?.content) {
                                "official" -> stage = QrStage.Official
                                "cancelled" -> stage = QrStage.Idle
                                "ready" -> {
                                    bitmap = generateQrLoginBitmap(result.getValue("link").jsonPrimitive.content)
                                    stage = QrStage.Waiting
                                    while (stage == QrStage.Waiting || stage == QrStage.Scanned) {
                                        delay(1_500)
                                        stage = when (client.requestBrowserQrStep("loginQrPoll")["state"]?.jsonPrimitive?.content) {
                                            "waiting" -> QrStage.Waiting
                                            "scanned" -> QrStage.Scanned
                                            "confirmed" -> QrStage.Confirmed
                                            "expired" -> QrStage.Expired
                                            else -> QrStage.Failed
                                        }
                                    }
                                }
                                else -> stage = QrStage.Failed
                            }
                        } catch (cancelled: CancellationException) {
                            stage = QrStage.Idle
                            throw cancelled
                        } catch (error: Exception) {
                            failure = { browserBridgeFailureMessage(error) }
                            stage = QrStage.Failed
                        } finally {
                            bitmap = null
                        }
                    }
                },
                modifier = Modifier.testTag("qr_login_start"),
            )
            else -> Unit
        }
    }
}
