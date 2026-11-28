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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.platform.testTag
import com.github.zly2006.zhihu.i18n.AccountStrings
import com.github.zly2006.zhihu.platform.rememberSystemUrlOpener
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppOutlinedButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
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
    var generation by remember(client) { mutableIntStateOf(0) }
    var polling by remember(client) { mutableStateOf<Job?>(null) }

    fun requestCode() {
        val attempt = ++generation
        polling?.cancel()
        bitmap = null
        stage = QrStage.Loading
        failure = null
        // 保留点击激活：二维码获取不能从 LaunchedEffect 自动发起。
        polling = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val result = client.requestBrowserQrStep("loginQrStart")
                if (attempt != generation) return@launch
                when (result["state"]?.jsonPrimitive?.content) {
                    "official" -> stage = QrStage.Official
                    "cancelled" -> stage = QrStage.Idle
                    "ready" -> {
                        bitmap = generateQrLoginBitmap(result.getValue("link").jsonPrimitive.content)
                        stage = QrStage.Waiting
                        while (attempt == generation && (stage == QrStage.Waiting || stage == QrStage.Scanned)) {
                            delay(1_500)
                            val result = client.requestBrowserQrStep("loginQrPoll")
                            if (attempt != generation) return@launch
                            stage = when (result["state"]?.jsonPrimitive?.content) {
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
                throw cancelled
            } catch (error: Exception) {
                if (attempt == generation) {
                    failure = { browserBridgeFailureMessage(error) }
                    stage = QrStage.Failed
                }
            } finally {
                if (attempt == generation) bitmap = null
            }
        }
    }

    QrLoginContent(
        bitmap = bitmap,
        status = failure?.invoke() ?: stage.label(),
        loading = stage == QrStage.Loading || stage == QrStage.Verifying,
    ) {
        when (stage) {
            QrStage.Official, QrStage.CheckOfficialAccount -> AppButton(
                text = if (stage == QrStage.Official) AccountStrings.webOpenSignIn else AccountStrings.webQrCheckAccount,
                onClick = { open(ZHIHU_SIGNIN_REFERER_URL) },
                modifier = Modifier.testTag("qr_login_official"),
            )
            QrStage.Confirmed -> AppButton(
                text = AccountStrings.webQrCommit,
                onClick = {
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
                            if (verified && profile != null) {
                                stage = QrStage.SignedIn
                                store.reportLoginSuccess(store.client, profile.name)
                                onSuccess(profile.name)
                            } else {
                                stage = QrStage.CheckOfficialAccount
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            failure = { browserBridgeFailureMessage(error) }
                            stage = QrStage.CheckOfficialAccount
                        }
                    }
                },
                modifier = Modifier.testTag("qr_login_commit"),
            )
            QrStage.Idle, QrStage.Waiting, QrStage.Scanned, QrStage.Expired, QrStage.Failed -> AppOutlinedButton(
                text = if (stage == QrStage.Idle) AccountStrings.webQrStart else AccountStrings.qrRefresh,
                onClick = ::requestCode,
                modifier = Modifier.testTag(if (stage == QrStage.Idle) "qr_login_start" else "qr_login_retry"),
            )
            else -> Unit
        }
    }
}
