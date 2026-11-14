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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.github.zly2006.zhihu.data.ZHIHU_ME_URL
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppOutlinedButton
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import com.github.zly2006.zhihu.util.ZHIHU_WEB_ZSE93
import com.github.zly2006.zhihu.util.isZhihuLoginRiskControl
import com.github.zly2006.zhihu.util.signZhihuFetchRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

const val ZHIHU_HOME_URL = "https://www.zhihu.com/"
const val ZHIHU_SIGNIN_URL = "https://www.zhihu.com/signin?next=%2F"
const val ZHIHU_SIGNIN_REFERER_URL = "https://www.zhihu.com/signin"
const val ZHIHU_RISK_CONTROL_URL = "https://www.zhihu.com/account/risk_control/"

private const val UDID_URL = "https://www.zhihu.com/udid"
private const val CAPTCHA_V2_URL = "https://www.zhihu.com/api/v3/oauth/captcha/v2?type=captcha_sign_in"
private const val QRCODE_URL = "https://www.zhihu.com/api/v3/account/api/login/qrcode"
private const val DESKTOP_SEC_CH_UA = "\"Not:A-Brand\";v=\"99\", \"Google Chrome\";v=\"145\", \"Chromium\";v=\"145\""
private const val DESKTOP_SEC_CH_UA_MOBILE = "?0"
private const val DESKTOP_SEC_CH_UA_PLATFORM = "\"Windows\""
private const val DEFAULT_QR_LOGIN_DEADLINE_MILLIS = 120_000L
private const val MAX_QR_LOGIN_TTL_SECONDS = 86_400L
private const val MAX_QR_LOGIN_TTL_MILLIS = MAX_QR_LOGIN_TTL_SECONDS * 1000
private val QR_LOGIN_REQUEST_TIMEOUT = 10.seconds

@Serializable
data class ZhihuQrCodeResponse(
    val expiresAt: Long? = null,
    val link: String? = null,
    val token: String? = null,
    val qrcodeToken: String? = null,
)

@Serializable
data class ZhihuQrScanError(
    val needLogin: Boolean? = null,
    val redirect: String? = null,
    val code: Int? = null,
    val message: String? = null,
)

@Serializable
data class ZhihuQrScanInfo(
    val status: Int? = null,
    val cookie: String? = null,
    val cookies: String? = null,
    val zC0: String? = null,
    val userId: String? = null,
    val accessToken: String? = null,
    val success: Boolean? = null,
    val loggedIn: Boolean? = null,
    val loginStatus: String? = null,
    val error: ZhihuQrScanError? = null,
)

internal class QrLoginRiskControlException(
    message: String,
    val redirectUrl: String,
) : IllegalStateException(message)

private class QrLoginRequestException(
    message: String,
) : IllegalStateException(message)

private suspend fun HttpResponse.checkQrLoginResponse(): JsonElement? {
    val requestUrl = call.request.url
    val redirectedUrl = headers[HttpHeaders.Location]?.let { location ->
        runCatching { URLBuilder(requestUrl).apply { parameters.clear() }.takeFrom(location).build() }.getOrNull()
    }
    val challengeUrl = redirectedUrl?.takeIf { it.isZhihuLoginRiskControl() }
        ?: requestUrl.takeIf { it.isZhihuLoginRiskControl() }
    /** Ktor 默认跟随 302；unhuman 的最终 200 HTML 仍是风控，不是登录成功。 */
    if (challengeUrl != null) {
        throw QrLoginRiskControlException("知乎需要验证当前网络环境，请先完成安全验证。", challengeUrl.toString())
    }
    val body = bodyAsText()
    val json = runCatching { ZhihuJson.json.parseToJsonElement(body) }.getOrNull()
    val error = (json as? JsonObject)?.get("error")?.let {
        runCatching { ZhihuJson.decodeJson(ZhihuQrScanError.serializer(), it) }.getOrNull()
    }
    val errorRedirect = error?.redirect?.let { location ->
        runCatching { URLBuilder(requestUrl).apply { parameters.clear() }.takeFrom(location).build() }
            .getOrNull()
            ?.takeIf { it.isZhihuLoginRiskControl() }
    }
    if (status == HttpStatusCode.Forbidden || errorRedirect != null) {
        throw QrLoginRiskControlException(
            error?.message?.takeIf { it.isNotBlank() } ?: "知乎限制了当前登录请求，请先完成安全验证。",
            errorRedirect?.toString() ?: ZHIHU_RISK_CONTROL_URL,
        )
    }
    if (status.value >= 400 || error?.code != null) {
        throw QrLoginRequestException(
            error?.message?.takeIf { it.isNotBlank() } ?: "知乎登录请求失败：${status.value}",
        )
    }
    return json
}

suspend fun prefetchQrLoginContext(
    client: HttpClient,
    cookies: Map<String, String>,
) {
    client
        .get(ZHIHU_SIGNIN_URL) {
            createDesktopHeaders(ZHIHU_HOME_URL).forEach { (key, value) ->
                header(key, value)
            }
        }.checkQrLoginResponse()

    for ((url, method) in listOf(UDID_URL to HttpMethod.Post, CAPTCHA_V2_URL to HttpMethod.Get)) {
        try {
            client
                .request(url) {
                    this.method = method
                    createZhihuLoginHeaders(cookies, ZHIHU_SIGNIN_REFERER_URL).forEach { (key, value) ->
                        header(key, value)
                    }
                    if (method == HttpMethod.Post) signZhihuFetchRequest(cookies)
                }.checkQrLoginResponse()
        } catch (e: CancellationException) {
            throw e
        } catch (e: QrLoginRiskControlException) {
            throw e
        } catch (e: QrLoginRequestException) {
            throw e
        } catch (_: Exception) {
            /** 预热网络失败可继续；服务端拒绝和协程取消必须中止。 */
        }
    }
}

suspend fun requestQrCode(
    client: HttpClient,
    cookies: Map<String, String>,
): ZhihuQrCodeResponse {
    val response = client.post(QRCODE_URL) {
        createZhihuLoginHeaders(cookies, ZHIHU_SIGNIN_REFERER_URL).forEach { (key, value) ->
            header(key, value)
        }
        signZhihuFetchRequest(cookies)
    }
    val json = response.checkQrLoginResponse()
        ?: throw QrLoginRequestException("二维码获取失败：知乎返回了无效响应")
    val result = ZhihuJson.decodeJson(ZhihuQrCodeResponse.serializer(), json)
    val token = result.token ?: result.qrcodeToken
    if (token.isNullOrBlank() || result.link.isNullOrBlank()) {
        throw QrLoginRequestException("二维码获取失败：${response.status.value}")
    }
    return result.copy(token = token)
}

suspend fun pollQrCodeLogin(
    client: HttpClient,
    cookies: MutableMap<String, String>,
    token: String,
    deadline: Long,
    onScanned: () -> Unit,
    onRiskControl: (String?, String?) -> Unit,
): Boolean {
    var hasPromptedConfirm = false

    while (currentEpochMillis() <= deadline) {
        currentCoroutineContext().ensureActive()

        try {
            val response = withTimeoutOrNull(QR_LOGIN_REQUEST_TIMEOUT) {
                client.get("$QRCODE_URL/$token/scan_info") {
                    createZhihuLoginHeaders(cookies, ZHIHU_SIGNIN_URL, isPolling = true).forEach { (key, value) ->
                        header(key, value)
                    }
                }
            } ?: throw IllegalStateException("二维码登录轮询超时")
            val json = response.checkQrLoginResponse()
                ?: throw QrLoginRequestException("二维码登录轮询返回了无效响应")
            val scanInfo = ZhihuJson.decodeJson(ZhihuQrScanInfo.serializer(), json)
            syncCookiesFromScanInfo(cookies, scanInfo)

            if (scanInfo.status == 1 && !hasPromptedConfirm) {
                hasPromptedConfirm = true
                onScanned()
            }

            if (isQrLoginExpired(scanInfo)) {
                return false
            }

            val loginSucceeded = isQrLoginSuccessful(scanInfo)
            if (loginSucceeded || cookies.containsKey("z_c0")) {
                if (!cookies.containsKey("z_c0")) {
                    runCatching {
                        client.get(ZHIHU_ME_URL) {
                            createZhihuLoginHeaders(cookies, ZHIHU_SIGNIN_URL, isPolling = true).forEach { (key, value) ->
                                header(key, value)
                            }
                        }
                    }
                }
                if (loginSucceeded || cookies.containsKey("z_c0")) {
                    return true
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: QrLoginRiskControlException) {
            onRiskControl(e.message, e.redirectUrl)
            return false
        } catch (e: QrLoginRequestException) {
            throw e
        } catch (e: SerializationException) {
            throw e
        } catch (_: Exception) {
            /** 临时网络抖动可继续轮询。 */
        }

        delay(500)
    }

    return false
}

fun createDesktopHeaders(referer: String? = null): MutableMap<String, String> {
    val headers = mutableMapOf(
        "Accept" to "application/json, text/plain, */*",
        "Accept-Language" to "en-US,en;q=0.9",
        "User-Agent" to ZHIHU_DESKTOP_USER_AGENT,
        "sec-ch-ua" to DESKTOP_SEC_CH_UA,
        "sec-ch-ua-mobile" to DESKTOP_SEC_CH_UA_MOBILE,
        "sec-ch-ua-platform" to DESKTOP_SEC_CH_UA_PLATFORM,
    )
    if (!referer.isNullOrBlank()) {
        headers["Referer"] = referer
    }
    return headers
}

fun createZhihuLoginHeaders(
    cookies: Map<String, String>,
    referer: String,
    isPolling: Boolean = false,
): Map<String, String> {
    val headers = createDesktopHeaders(referer)
    headers["Origin"] = ZHIHU_HOME_URL.removeSuffix("/")
    headers["x-requested-with"] = "fetch"
    if (isPolling) {
        headers["Accept"] = "*/*"
        headers["sec-fetch-dest"] = "empty"
        headers["sec-fetch-mode"] = "cors"
        headers["sec-fetch-site"] = "same-origin"
        headers["x-zse-93"] = ZHIHU_WEB_ZSE93
    }
    cookies["_xsrf"]?.let {
        headers["x-xsrftoken"] = it
    }
    return headers
}

fun syncCookiesFromScanInfo(
    cookies: MutableMap<String, String>,
    scanInfo: ZhihuQrScanInfo,
) {
    val rawCookie = listOf(scanInfo.cookie, scanInfo.cookies)
        .filterIsInstance<String>()
        .joinToString(";")
    cookies.putAll(parseCookieAssignments(rawCookie))
    scanInfo.zC0
        ?.takeIf { it.isNotBlank() }
        ?.let { cookies["z_c0"] = it }
}

fun parseCookieAssignments(rawCookie: String): Map<String, String> {
    val skipCookieAttributes = setOf("Domain", "Path", "Expires", "Max-Age", "HttpOnly", "Secure", "SameSite")
    return rawCookie
        .split(";")
        .mapNotNull { item ->
            val trimmed = item.trim()
            if (!trimmed.contains("=")) {
                return@mapNotNull null
            }
            val name = trimmed.substringBefore("=").trim()
            if (name.isBlank() || skipCookieAttributes.contains(name)) {
                return@mapNotNull null
            }
            val value = trimmed.substringAfter("=").trim()
            if (value.isBlank()) {
                return@mapNotNull null
            }
            name to value
        }.toMap()
}

fun isQrLoginSuccessful(scanInfo: ZhihuQrScanInfo): Boolean {
    if (scanInfo.userId != null || !scanInfo.accessToken.isNullOrBlank() || scanInfo.success == true || scanInfo.loggedIn == true) {
        return true
    }
    val loginStatus = scanInfo.loginStatus.orEmpty().uppercase()
    return loginStatus in setOf("CONFIRMED", "LOGIN_SUCCESS", "SUCCESS", "OK", "LOGGED_IN")
}

fun isQrLoginExpired(scanInfo: ZhihuQrScanInfo): Boolean {
    val loginStatus = scanInfo.loginStatus.orEmpty().uppercase()
    return scanInfo.status == 2 || loginStatus in setOf("EXPIRED", "QR_CODE_EXPIRED", "LOGIN_EXPIRED")
}

fun normalizeDeadline(expiresAt: Long?): Long {
    val now = currentEpochMillis()
    if (expiresAt == null || expiresAt <= 0) {
        return now + DEFAULT_QR_LOGIN_DEADLINE_MILLIS
    }
    val nowSeconds = now / 1000
    return if (expiresAt < nowSeconds) {
        /** expires_at 小值表示 TTL 而非绝对时间；秒与毫秒分别限于一天。 */
        when {
            expiresAt <= MAX_QR_LOGIN_TTL_SECONDS -> now + expiresAt * 1000
            expiresAt <= MAX_QR_LOGIN_TTL_MILLIS -> now + expiresAt
            else -> now + DEFAULT_QR_LOGIN_DEADLINE_MILLIS
        }
    } else if (expiresAt < 10_000_000_000L) {
        expiresAt * 1000
    } else {
        expiresAt
    }
}

private fun currentEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()

@Composable
fun SharedQrLoginPane(
    client: HttpClient,
    cookies: MutableMap<String, String>,
    onLoginSuccess: suspend (Map<String, String>) -> Boolean,
    modifier: Modifier = Modifier,
    generateQrBitmap: (String) -> ImageBitmap = ::generateQrLoginBitmap,
    qrReadyMessage: String = "使用知乎扫描",
    onQrReady: () -> Unit = {},
    onRiskControl: (String, Map<String, String>) -> Unit,
) {
    var refreshKey by rememberSaveable { mutableIntStateOf(0) }
    var qrBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var statusText by remember { mutableStateOf("正在获取二维码") }
    var riskControlUrl by remember { mutableStateOf<String?>(null) }
    var riskControlMessage by remember { mutableStateOf<String?>(null) }
    var isWorking by remember { mutableStateOf(true) }
    var isVerifying by remember { mutableStateOf(false) }
    LaunchedEffect(refreshKey) {
        qrBitmap = null
        statusText = "正在获取二维码"
        riskControlUrl = null
        isWorking = true

        try {
            withTimeoutOrNull(QR_LOGIN_REQUEST_TIMEOUT) {
                prefetchQrLoginContext(client, cookies)
            } ?: throw IllegalStateException("二维码登录初始化超时，请重试")
            val qrCode = withTimeoutOrNull(QR_LOGIN_REQUEST_TIMEOUT) {
                requestQrCode(client, cookies)
            } ?: throw IllegalStateException("二维码获取超时，请重试")
            val qrLink = qrCode.link ?: throw IllegalStateException("知乎没有返回二维码链接")
            val qrToken = qrCode.token ?: qrCode.qrcodeToken ?: throw IllegalStateException("知乎没有返回二维码 token")
            qrBitmap = generateQrBitmap(qrLink)
            statusText = qrReadyMessage
            onQrReady()

            val success = pollQrCodeLogin(
                client = client,
                cookies = cookies,
                token = qrToken,
                deadline = normalizeDeadline(qrCode.expiresAt),
                onScanned = {
                    statusText = "请在知乎 App 上确认登录"
                },
                onRiskControl = { message, redirectUrl ->
                    riskControlMessage = message ?: "知乎需要验证当前网络环境"
                    riskControlUrl = redirectUrl ?: ZHIHU_RISK_CONTROL_URL
                    statusText = riskControlMessage ?: "知乎需要验证当前网络环境"
                },
            )

            if (success) {
                statusText = "正在验证登录"
                isVerifying = true
                statusText = if (onLoginSuccess(cookies)) {
                    "登录成功"
                } else {
                    "登录结果验证失败，请重试"
                }
                isWorking = false
            } else if (!riskControlUrl.isNullOrBlank()) {
                isWorking = false
            } else {
                statusText = "二维码已过期，请重试"
                isWorking = false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: QrLoginRiskControlException) {
            riskControlMessage = e.message
            riskControlUrl = e.redirectUrl
            statusText = e.message.orEmpty()
            isWorking = false
        } catch (e: SerializationException) {
            statusText = "知乎登录响应格式异常，请重试"
            isWorking = false
        } catch (e: Exception) {
            statusText = e.message ?: "二维码获取失败，请重试"
            isWorking = false
        } finally {
            isVerifying = false
        }
    }

    val currentRiskControlUrl = riskControlUrl
    LaunchedEffect(currentRiskControlUrl) {
        if (!currentRiskControlUrl.isNullOrBlank()) onRiskControl(currentRiskControlUrl, cookies)
    }
    if (!currentRiskControlUrl.isNullOrBlank()) {
        Text(
            text = "请完成安全验证并重启应用后再扫码。",
            modifier = modifier.fillMaxWidth().testTag("qr_risk_control_content"),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .testTag("qr_login_content"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (qrBitmap != null) {
                Surface(
                    shape = RoundedCornerShape(AppDimens.CardCornerRadius),
                    color = Color.White,
                ) {
                    Image(
                        bitmap = qrBitmap!!,
                        contentDescription = "知乎登录二维码",
                        modifier = Modifier
                            .widthIn(max = AppDimens.QrCodeSize + AppDimens.Space16 + AppDimens.Space16)
                            .fillMaxWidth()
                            .padding(AppDimens.Space16)
                            .aspectRatio(1f)
                            .testTag("qr_login_image"),
                    )
                }
                Spacer(modifier = Modifier.size(AppDimens.IconSizeCompact))
            } else if (isWorking) {
                AppSpinner(
                    modifier = Modifier.testTag("qr_login_loading"),
                    size = AppDimens.ProgressIndicatorSize,
                )
                Spacer(modifier = Modifier.size(AppDimens.IconSizeCompact))
            }

            Text(
                text = statusText,
                style = if (settingSkinIsMiuix()) {
                    LocalAppSkinTokens.current.main
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                color = if (settingSkinIsMiuix()) {
                    LocalAppSkinTokens.current.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.PageHeaderM3eHorizontalPadding)
                    .testTag("qr_login_status")
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )

            if (!riskControlMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.size(AppDimens.Space12))
                Text(
                    text = riskControlMessage.orEmpty(),
                    style = if (settingSkinIsMiuix()) {
                        LocalAppSkinTokens.current.footnote2
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = if (settingSkinIsMiuix()) {
                        LocalAppSkinTokens.current.error
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimens.PageHeaderM3eHorizontalPadding),
                )
            }

            Spacer(modifier = Modifier.size(AppDimens.Space20))

            Row(
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12),
            ) {
                AppOutlinedButton(
                    onClick = {
                        qrBitmap = null
                        statusText = "正在刷新二维码"
                        isWorking = true
                        riskControlUrl = null
                        riskControlMessage = null
                        refreshKey += 1
                    },
                    modifier = Modifier.testTag("qr_login_retry"),
                    enabled = !isVerifying,
                ) {
                    Text("刷新二维码")
                }
            }
        }
    }
}

expect fun generateQrLoginBitmap(content: String): ImageBitmap

expect val isLoginRiskControlSupported: Boolean

/** 手动确认时读取验证页最新 Cookie，包含页面 AJAX 更新的凭据。 */
internal expect fun readLoginRiskControlCookies(url: String): Map<String, String>

@Composable
expect fun LoginRiskControlPane(
    url: String,
    cookies: Map<String, String>,
    onCookiesChanged: (Map<String, String>) -> Unit,
    onVerificationComplete: () -> Unit,
)
