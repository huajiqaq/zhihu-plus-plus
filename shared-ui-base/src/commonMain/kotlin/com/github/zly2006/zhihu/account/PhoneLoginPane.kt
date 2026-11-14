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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpSize
import com.github.zly2006.zhihu.data.HttpFailureRecord
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.toCookieHeaderString
import com.github.zly2006.zhihu.platform.rememberExternalUrlOpener
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppAlertDialog
import com.github.zly2006.zhihu.ui.components.AppButton
import com.github.zly2006.zhihu.ui.components.AppCheckbox
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppOutlinedButton
import com.github.zly2006.zhihu.ui.components.AppSpinner
import com.github.zly2006.zhihu.ui.components.AppTextButton
import com.github.zly2006.zhihu.ui.components.AppTextField
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix
import com.github.zly2006.zhihu.util.ZhihuMessageBodyEncryptor
import com.github.zly2006.zhihu.util.hmacSha1Hex
import com.github.zly2006.zhihu.util.signZhihuFetchRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock

@Composable
fun rememberLoginHttpClient(cookies: MutableMap<String, String>): HttpClient {
    val owner = rememberZhihuAccountStore()
    val accountClient = owner.client
    val httpClient = remember(owner, accountClient, cookies) {
        createAccountHttpClient(
            cookies,
            owner.session.userAgent,
            owner.engine,
            onFailure = { accountClient.reportHttpFailure(it, cookies) },
        )
    }
    DisposableEffect(httpClient) {
        onDispose(httpClient::close)
    }
    return httpClient
}

expect fun decodePhoneLoginCaptchaImage(content: String): ImageBitmap?

private const val PHONE_LOGIN_API_BASE_URL = "https://api.zhihu.com"
private const val DEVICE_GUEST_INIT_PATH = "/api/account/prod/init/udid_guest"
private const val CAPTCHA_PATH = "/captcha"
private const val AUTH_DIGITS_PATH = "/api/account/prod/auth/digits"
private const val SIGN_IN_PATH = "/api/account/prod/sign_in"

private const val MOBILE_CLIENT_ID = "8d5227e0aaaa4797a763ac64e0c3b8"
private const val MOBILE_CLIENT_SECRET = "ecbefbf6b17e47ecb9035107866380"
private const val MOBILE_SOURCE = "com.zhihu.android"
private const val DIGITS_GRANT_TYPE = "digits"
private const val CLOUD_APP_ID = "1355"
private const val CLOUD_APP_SECRET = "dd49a835-56e7-4a0f-95b5-efd51ea5397f"
private const val CLOUD_SIGN_VERSION = "2"

const val ZHIHU_ANDROID_PHONE_LOGIN_USER_AGENT =
    "com.zhihu.android/Futureve/11.4.0 Mozilla/5.0 (Linux; Android 12; Android SDK built for arm64 " +
        "Build/SE1A.220621.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 " +
        "Chrome/57.0.1000.10 Mobile Safari/537.36"

data class ZhihuPhoneLoginDeviceInfo(
    val timezoneOffsetSeconds: Long,
    val appInstallTimeMillis: Long,
    val notificationEnabled: Boolean,
    val bluetoothAvailable: Boolean,
    val phoneBrand: String,
    val phoneModel: String,
    val androidRelease: String,
    val cpuType: String,
    val cpuCount: Int,
    val cpuUsage: String,
    val totalMemoryMegabytes: Int,
    val freeMemoryMegabytes: Int,
    val totalStorageMegabytes: Int,
    val freeStorageMegabytes: Int,
)

private sealed interface ZhihuPhoneDigitsResult {
    data object Sent : ZhihuPhoneDigitsResult

    data class CaptchaRequired(
        val imageBase64: String?,
    ) : ZhihuPhoneDigitsResult
}

private const val SMS_CODE_LENGTH = 6
private const val RESEND_COOLDOWN_SECONDS = 60
private const val SUPPORTED_COUNTRIES_URL = "https://www.zhihu.com/api/v3/oauth/sms/supported_countries"

@Serializable
internal data class PhoneLoginCountry(
    val code: String,
    val name: String,
    val abbr: String,
)

@Serializable
private data class SupportedCountriesResponse(
    val data: List<PhoneLoginCountry>,
)

private val DEFAULT_COUNTRY = PhoneLoginCountry(code = "+86", name = "中国", abbr = "CN")

/**
 * LoginScreen 持有表单与访客会话，切登录方式不重建。
 * 仅手机号、地区和同意状态随配置保存；验证码、访客凭据和冷却属于本次登录。
 */
@Stable
class PhoneLoginState internal constructor(
    private val engine: HttpClientEngine,
    phoneNumber: String = "",
    agreementAccepted: Boolean = false,
    country: PhoneLoginCountry = DEFAULT_COUNTRY,
    initialCookies: Map<String, String> = emptyMap(),
    onFailure: suspend (HttpFailureRecord, Map<String, String>) -> Unit = { _, _ -> },
) : AutoCloseable {
    var phoneNumber by mutableStateOf(phoneNumber)
        private set
    internal var country by mutableStateOf(country)
        private set
    internal var countries by mutableStateOf<List<PhoneLoginCountry>?>(null)
        private set
    internal var isLoadingCountries by mutableStateOf(false)
        private set
    internal var countryLoadError by mutableStateOf<String?>(null)
        private set
    var agreementAccepted by mutableStateOf(agreementAccepted)
    var digits by mutableStateOf("")
        private set
    var captchaInput by mutableStateOf("")
    var captchaRequired by mutableStateOf(false)
        private set
    var captchaImageBase64 by mutableStateOf<String?>(null)
        private set
    var hasRequestedDigits by mutableStateOf(false)
        private set
    var isSendingDigits by mutableStateOf(false)
        private set
    var isLoggingIn by mutableStateOf(false)
        private set
    var resendSeconds by mutableIntStateOf(0)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    private val cookies = initialCookies.toMutableMap()
    private val loginHttpClient = lazy {
        createAccountHttpClient(cookies, ZHIHU_ANDROID_PHONE_LOGIN_USER_AGENT, engine, onFailure = { onFailure(it, cookies) })
    }
    private val deviceInfo by lazy { phoneLoginDeviceInfo }
    private var authorization = "oauth $MOBILE_CLIENT_ID"
    private var deviceId: String? = null
    private var webDeviceCookie = cookies.remove("d_c0")?.takeIf(String::isNotBlank)

    /** 在途请求锁住输入与方式切换，避免提交值与界面不一致。 */
    val isBusy: Boolean
        get() = isSendingDigits || isLoggingIn

    val isPhoneNumberValid: Boolean
        get() = normalizePhoneNumber(phoneNumber, country.code) != null

    val canSendDigits: Boolean
        get() = agreementAccepted && isPhoneNumberValid && resendSeconds == 0 && !isBusy

    val canRefreshCaptcha: Boolean
        get() = agreementAccepted && !isBusy

    val canVerifyCaptcha: Boolean
        get() = agreementAccepted && captchaInput.isNotBlank() && !isBusy

    val canSignIn: Boolean
        get() = agreementAccepted &&
            hasRequestedDigits &&
            isPhoneNumberValid &&
            digits.length == SMS_CODE_LENGTH &&
            !isBusy

    internal suspend fun loadCountries(httpClient: HttpClient) {
        if (isLoadingCountries || countries != null) return
        isLoadingCountries = true
        countryLoadError = null
        try {
            val response = httpClient.get(SUPPORTED_COUNTRIES_URL)
            check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
            val result = ZhihuJson.decodeJson<SupportedCountriesResponse>(
                ZhihuJson.json.parseToJsonElement(response.bodyAsText()),
            )
            check(result.data.isNotEmpty()) { "服务器未返回地区列表" }
            countries = result.data
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            countryLoadError = "获取地区列表失败：${error.message ?: "请检查网络"}"
        } finally {
            isLoadingCountries = false
        }
    }

    internal fun selectCountry(value: PhoneLoginCountry) {
        if (country == value) return
        country = value
        invalidateDigits()
    }

    fun updatePhoneNumber(value: String) {
        val next = value.filter { it in '0'..'9' }.take(MAX_PHONE_NUMBER_DIGITS)
        if (phoneNumber == next) return
        phoneNumber = next
        invalidateDigits()
    }

    private fun invalidateDigits() {
        digits = ""
        hasRequestedDigits = false
        captchaRequired = false
        captchaImageBase64 = null
        captchaInput = ""
        errorMessage = null
    }

    fun updateDigits(value: String) {
        digits = value.filter(Char::isDigit).take(SMS_CODE_LENGTH)
    }

    suspend fun requestDigits() {
        errorMessage = null
        isSendingDigits = true
        try {
            val username = requireNotNull(normalizePhoneNumber(phoneNumber, country.code)) {
                if (country.code == "+86") INVALID_PHONE_NUMBER_MESSAGE else INVALID_INTERNATIONAL_PHONE_NUMBER_MESSAGE
            }
            ensureGuestToken()
            val captchaState = requestCaptchaState()
            val result = if (captchaState.showCaptcha) {
                ZhihuPhoneDigitsResult.CaptchaRequired(loadCaptchaImage())
            } else {
                sendDigits(username, recheckCaptchaWhenRequested = true)
            }
            when (result) {
                ZhihuPhoneDigitsResult.Sent -> {
                    hasRequestedDigits = true
                    captchaRequired = false
                    captchaImageBase64 = null
                    captchaInput = ""
                    resendSeconds = RESEND_COOLDOWN_SECONDS
                }

                is ZhihuPhoneDigitsResult.CaptchaRequired -> {
                    captchaRequired = true
                    captchaImageBase64 = result.imageBase64
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorMessage = error.message ?: "发送验证码失败"
        } finally {
            isSendingDigits = false
        }
    }

    suspend fun refreshCaptcha() {
        errorMessage = null
        isSendingDigits = true
        try {
            ensureGuestToken()
            captchaImageBase64 = loadCaptchaImage()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorMessage = error.message ?: "刷新图形验证码失败"
        } finally {
            isSendingDigits = false
        }
    }

    suspend fun verifyCaptchaAndRequestDigits() {
        errorMessage = null
        isSendingDigits = true
        val verified = try {
            require(captchaInput.isNotBlank()) { "图形验证码不能为空" }
            ensureGuestToken()
            val response = loginHttpClient.value.post("$PHONE_LOGIN_API_BASE_URL$CAPTCHA_PATH") {
                applyMobileHeaders()
                setEncryptedForm("input_text" to captchaInput.trim())
            }
            val body = response.bodyAsText()
            check(response.status.isSuccess()) { "验证图形验证码失败（HTTP ${response.status.value}）" }
            ZhihuJson.decodeJson<CaptchaVerificationResponse>(ZhihuJson.json.parseToJsonElement(body)).success
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorMessage = error.message ?: "验证图形验证码失败"
            return
        } finally {
            isSendingDigits = false
        }
        if (verified) {
            requestDigits()
        } else {
            errorMessage = "图形验证码不正确"
        }
    }

    suspend fun signIn(accountStore: ZhihuAccountStore): String? {
        errorMessage = null
        isLoggingIn = true
        try {
            val username = requireNotNull(normalizePhoneNumber(phoneNumber, country.code)) {
                if (country.code == "+86") INVALID_PHONE_NUMBER_MESSAGE else INVALID_INTERNATIONAL_PHONE_NUMBER_MESSAGE
            }
            require(digits.isNotBlank()) { "短信验证码不能为空" }
            ensureGuestToken()
            val timestamp = Clock.System.now().epochSeconds
            val signature = hmacSha1Hex(MOBILE_CLIENT_SECRET, "$DIGITS_GRANT_TYPE$MOBILE_CLIENT_ID$MOBILE_SOURCE$timestamp")
            val response = loginHttpClient.value.post("$PHONE_LOGIN_API_BASE_URL$SIGN_IN_PATH") {
                applyMobileHeaders()
                setEncryptedForm(
                    "client_id" to MOBILE_CLIENT_ID,
                    "digits" to digits.trim(),
                    "grant_type" to DIGITS_GRANT_TYPE,
                    "signature" to signature,
                    "source" to MOBILE_SOURCE,
                    "timestamp" to timestamp.toString(),
                    "username" to username,
                )
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                val message = runCatching {
                    val root = ZhihuJson.json.parseToJsonElement(body).jsonObject
                    (root["error"]?.jsonObject ?: root)["message"]?.jsonPrimitive?.content
                }.getOrNull()
                error(message?.let { "手机号登录失败：$it" } ?: "手机号登录失败（HTTP ${response.status.value}）")
            }
            val responseToken = ZhihuJson.decodeJson<TokenResponse>(ZhihuJson.json.parseToJsonElement(body))
            check(responseToken.accessToken.isNotBlank()) { "服务器未返回登录凭证" }
            cookies.putAll(responseToken.cookie.filterValues(String::isNotBlank))
            if (cookies["d_c0"].isNullOrBlank()) cookies["d_c0"] = checkNotNull(webDeviceCookie) { "服务器未返回必要的 Cookie d_c0" }
            val token = ZhihuMobileLoginToken(
                accessToken = responseToken.accessToken,
                refreshToken = responseToken.refreshToken,
                tokenType = responseToken.tokenType,
                expiresAt = responseToken.expiresIn?.let(timestamp::plus),
                cookies = cookies.toMap(),
            )
            if (accountStore.login(token)) {
                return accountStore.session.username
            }
            errorMessage = "登录凭证验证失败，请重试"
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorMessage = error.message ?: "登录失败"
        } finally {
            isLoggingIn = false
        }
        return null
    }

    private suspend fun sendDigits(
        username: String,
        recheckCaptchaWhenRequested: Boolean,
    ): ZhihuPhoneDigitsResult {
        val response = loginHttpClient.value.post("$PHONE_LOGIN_API_BASE_URL$AUTH_DIGITS_PATH") {
            applyMobileHeaders()
            setEncryptedForm(
                "username" to username,
                "client_id" to MOBILE_CLIENT_ID,
            )
        }
        val body = response.bodyAsText()
        if (response.status.isSuccess()) {
            return ZhihuPhoneDigitsResult.Sent
        }

        val error = runCatching {
            val root = ZhihuJson.json.parseToJsonElement(body).jsonObject
            root["error"]?.jsonObject ?: root
        }.getOrNull()
        val errorCode = error
            ?.get("code")
            ?.jsonPrimitive
            ?.content
            ?.toIntOrNull()
        if (errorCode in CAPTCHA_INVALID_ERROR_CODES) {
            return ZhihuPhoneDigitsResult.CaptchaRequired(loadCaptchaImage())
        }
        if (errorCode == CAPTCHA_NEEDED_ERROR_CODE && recheckCaptchaWhenRequested) {
            val captchaState = requestCaptchaState()
            return if (captchaState.showCaptcha) {
                ZhihuPhoneDigitsResult.CaptchaRequired(loadCaptchaImage())
            } else {
                sendDigits(username, recheckCaptchaWhenRequested = false)
            }
        }
        val errorMessage = error?.get("message")?.jsonPrimitive?.content
        error(
            errorMessage?.let { "发送短信验证码失败：$it" }
                ?: "发送短信验证码失败（HTTP ${response.status.value}）",
        )
    }

    private suspend fun ensureGuestToken() {
        if (!authorization.startsWith("oauth ")) return

        if (webDeviceCookie == null) {
            val mobileCookies = cookies.toMap()
            var fetchedDeviceCookie: String? = null
            val response = try {
                loginHttpClient.value
                    .post("https://www.zhihu.com/udid") {
                        signZhihuFetchRequest(cookies)
                    }.also {
                        fetchedDeviceCookie = cookies["d_c0"]?.takeIf(String::isNotBlank)
                    }
            } finally {
                /** 移动访客初始化携带网页 d_c0 会返回 500；该凭据只在登录时合并。 */
                cookies.clear()
                cookies.putAll(mobileCookies)
            }
            check(response.status.isSuccess()) { "初始化网页设备凭证失败（HTTP ${response.status.value}）" }
            webDeviceCookie = checkNotNull(fetchedDeviceCookie) { "服务器未返回必要的 Cookie d_c0" }
        }

        val timestamp = (Clock.System.now().epochSeconds).toString()
        val form = Parameters
            .build {
                append("app_build", "40408")
                append("app_install_time", deviceInfo.appInstallTimeMillis.toString())
                append("app_ticket", "interface is empty")
                append("app_version", "11.4.0")
                append("bt_ck", if (deviceInfo.bluetoothAvailable) "1" else "0")
                append("bundle_id", MOBILE_SOURCE)
                append("cp_ct", deviceInfo.cpuCount.toString())
                append("cp_tp", deviceInfo.cpuType)
                append("cp_us", deviceInfo.cpuUsage)
                append("d_n", deviceInfo.phoneModel)
                append("fr_mem", deviceInfo.freeMemoryMegabytes.toString())
                append("fr_st", deviceInfo.freeStorageMegabytes.toString())
                append("latitude", "0.0")
                append("longitude", "0.0")
                append("nt_st", if (deviceInfo.notificationEnabled) "1" else "0")
                append("ph_br", deviceInfo.phoneBrand)
                append("ph_md", deviceInfo.phoneModel)
                append("ph_os", "Android ${deviceInfo.androidRelease}")
                append("pre_install", "InterfaceIsNull")
                append("tt_mem", deviceInfo.totalMemoryMegabytes.toString())
                append("tt_st", deviceInfo.totalStorageMegabytes.toString())
                append("tz_of", deviceInfo.timezoneOffsetSeconds.toString())
                append("zx_expired", "0")
            }.formUrlEncode()
        val signature = hmacSha1Hex(
            CLOUD_APP_SECRET,
            CLOUD_APP_ID + CLOUD_SIGN_VERSION + form + timestamp,
        )
        val response = loginHttpClient.value.post("$PHONE_LOGIN_API_BASE_URL$DEVICE_GUEST_INIT_PATH") {
            applyMobileHeaders()
            header("x-app-id", CLOUD_APP_ID)
            header("x-sign-version", CLOUD_SIGN_VERSION)
            header("x-req-ts", timestamp)
            header("x-req-signature", signature)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(ZhihuMessageBodyEncryptor.encrypt(form))
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val message = runCatching {
                val root = ZhihuJson.json.parseToJsonElement(body).jsonObject
                (root["error"]?.jsonObject ?: root)["message"]?.jsonPrimitive?.content
            }.getOrNull()
            error(
                message?.let { "初始化手机号登录失败：$it" }
                    ?: "初始化手机号登录失败（HTTP ${response.status.value}）",
            )
        }
        val initialization = ZhihuJson.decodeJson<DeviceGuestInitializationResponse>(ZhihuJson.json.parseToJsonElement(body))
        val guest = initialization.guest
        check(guest.accessToken.isNotBlank()) { "服务器未返回访客凭证" }
        check(initialization.udid.isNotBlank()) { "服务器未返回设备凭证" }
        cookies.putAll(guest.cookie.filterValues(String::isNotBlank))
        deviceId = initialization.udid
        authorization = "${guest.tokenType.ifBlank { "bearer" }} ${guest.accessToken}"
    }

    private suspend fun requestCaptchaState(): CaptchaResponse {
        val response = loginHttpClient.value.get("$PHONE_LOGIN_API_BASE_URL$CAPTCHA_PATH") {
            applyMobileHeaders()
        }
        val body = response.bodyAsText()
        check(response.status.isSuccess()) { "检查图形验证码失败（HTTP ${response.status.value}）" }
        return ZhihuJson.decodeJson(ZhihuJson.json.parseToJsonElement(body))
    }

    private fun HttpRequestBuilder.applyMobileHeaders() {
        accept(ContentType.Application.Json)
        header(HttpHeaders.UserAgent, ZHIHU_ANDROID_PHONE_LOGIN_USER_AGENT)
        header(HttpHeaders.Authorization, authorization)
        cookies.toCookieHeaderString().takeIf(String::isNotBlank)?.let {
            header(HttpHeaders.Cookie, it)
        }
        deviceId?.let { header("x-udid", it) }
        header("x-api-version", "3.0.93")
        header("x-app-version", "11.4.0")
        header("x-app-build", "release")
        header("x-app-bundleid", MOBILE_SOURCE)
        header("x-app-flavor", "honor")
        header(
            "x-app-za",
            "OS=Android&Release=12&Model=Android+SDK+built+for+arm64&VersionName=11.4.0&VersionCode=40408&" +
                "Product=com.zhihu.android&Width=1080&Height=2274&Installer=%E8%8D%A3%E8%80%80%E5%95%86%E5%BA%97&" +
                "DeviceType=AndroidPhone&Brand=Android",
        )
        header("x-network-type", "3G")
        header("x-page-id", "44")
        header("x-zse-93", "101_1_1.0")
    }

    private fun HttpRequestBuilder.setEncryptedForm(vararg entries: Pair<String, String>) {
        contentType(ContentType.Application.FormUrlEncoded)
        val form = Parameters
            .build {
                entries.forEach { (name, value) -> append(name, value) }
            }.formUrlEncode()
        setBody(ZhihuMessageBodyEncryptor.encrypt(form))
    }

    private suspend fun loadCaptchaImage(): String? {
        val response = loginHttpClient.value.put("$PHONE_LOGIN_API_BASE_URL$CAPTCHA_PATH") {
            applyMobileHeaders()
        }
        val body = response.bodyAsText()
        check(response.status.isSuccess()) { "获取图形验证码失败（HTTP ${response.status.value}）" }
        return ZhihuJson.decodeJson<CaptchaResponse>(ZhihuJson.json.parseToJsonElement(body)).imgBase64
    }

    internal suspend fun runResendCooldown() {
        while (true) {
            snapshotFlow { resendSeconds }.first { it > 0 }
            while (resendSeconds > 0) {
                delay(1_000)
                resendSeconds--
            }
        }
    }

    override fun close() {
        if (loginHttpClient.isInitialized()) loginHttpClient.value.close()
    }
}

@Composable
fun rememberPhoneLoginState(): PhoneLoginState {
    val owner = rememberZhihuAccountStore()
    val client = owner.client
    val saver = remember(owner, client) {
        listSaver<PhoneLoginState, Any>(
            save = { listOf(it.phoneNumber, it.agreementAccepted, it.country.code, it.country.name, it.country.abbr) },
            restore = {
                PhoneLoginState(
                    engine = owner.engine,
                    initialCookies = owner.accountsState.value.guestCookies,
                    onFailure = { record, cookies -> client.reportHttpFailure(record, cookies) },
                    phoneNumber = it[0] as String,
                    agreementAccepted = it[1] as Boolean,
                    country = if (it.size >= 5) {
                        PhoneLoginCountry(code = it[2] as String, name = it[3] as String, abbr = it[4] as String)
                    } else {
                        DEFAULT_COUNTRY
                    },
                )
            },
        )
    }
    val state = rememberSaveable(owner, client, saver = saver) {
        PhoneLoginState(
            owner.engine,
            initialCookies = owner.accountsState.value.guestCookies,
            onFailure = { record, cookies -> client.reportHttpFailure(record, cookies) },
        )
    }
    LaunchedEffect(state) { state.runResendCooldown() }
    DisposableEffect(state) {
        onDispose(state::close)
    }
    return state
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhoneLoginPane(
    onLoginSuccess: (String) -> Unit,
    state: PhoneLoginState = rememberPhoneLoginState(),
) {
    val accountStore = rememberZhihuAccountStore()
    val openExternalUrl = rememberExternalUrlOpener()

    /** 地区接口用独立访客 Cookie，避免网页 Cookie 混入移动端初始化。 */
    val countryHttpClient = rememberLoginHttpClient(remember { mutableMapOf<String, String>() })
    val scope = rememberCoroutineScope()
    val fieldShape = RoundedCornerShape(AppDimens.LoginFieldCornerRadius)
    var countryPickerOpen by remember { mutableStateOf(false) }
    var countryQuery by remember { mutableStateOf("") }
    val verifyCaptcha: () -> Unit = {
        scope.launch { state.verifyCaptchaAndRequestDigits() }
    }

    LaunchedEffect(countryPickerOpen) {
        if (countryPickerOpen && state.countryLoadError == null) state.loadCountries(countryHttpClient)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = AppDimens.Space8),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Space8),
    ) {
        AppOutlinedButton(
            text = "国家或地区：${state.country.name} ${state.country.code} ▾",
            onClick = {
                countryQuery = ""
                countryPickerOpen = true
            },
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth().testTag("phone_login_country"),
        )
        AppTextField(
            value = state.phoneNumber,
            onValueChange = state::updatePhoneNumber,
            labelText = "手机号",
            enabled = !state.isBusy,
            singleLine = true,
            shape = fieldShape,
            miuixCornerRadius = AppDimens.LoginFieldCornerRadius,
            miuixInsideMargin = DpSize(AppDimens.Space16, AppDimens.Space16),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("phone_login_phone"),
        )
        val isMainland = state.country.code
            .replace(" ", "")
            .replace("-", "") == "+86"
        if (state.phoneNumber.isNotEmpty() &&
            !state.isPhoneNumberValid &&
            (!isMainland || state.phoneNumber.length >= MAINLAND_PHONE_NUMBER_LENGTH)
        ) {
            Text(
                text = if (isMainland) INVALID_PHONE_NUMBER_MESSAGE else INVALID_INTERNATIONAL_PHONE_NUMBER_MESSAGE,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .padding(horizontal = AppDimens.Space16)
                    .testTag("phone_login_phone_error")
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        if (state.captchaRequired) {
            CaptchaStep(
                state = state,
                fieldShape = fieldShape,
                onRefresh = { scope.launch { state.refreshCaptcha() } },
                onVerify = verifyCaptcha,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTextField(
                value = state.digits,
                onValueChange = state::updateDigits,
                labelText = "短信验证码",
                enabled = !state.isBusy,
                singleLine = true,
                shape = fieldShape,
                miuixCornerRadius = AppDimens.LoginFieldCornerRadius,
                miuixInsideMargin = DpSize(AppDimens.Space16, AppDimens.Space16),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("phone_login_digits"),
            )
            AppButton(
                onClick = { scope.launch { state.requestDigits() } },
                enabled = state.canSendDigits,
                modifier = Modifier.testTag("phone_login_send_digits"),
            ) {
                if (state.isSendingDigits) {
                    AppSpinner(modifier = Modifier, size = AppDimens.InlineSpinnerSize)
                } else {
                    Text(
                        if (state.resendSeconds > 0) {
                            "${state.resendSeconds}s"
                        } else {
                            "发送验证码"
                        },
                    )
                }
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = AppDimens.TouchTarget)
                    .clip(RoundedCornerShape(AppDimens.MenuCornerRadius))
                    .toggleable(
                        value = state.agreementAccepted,
                        enabled = !state.isBusy,
                        role = Role.Checkbox,
                        onValueChange = { state.agreementAccepted = it },
                    ).testTag("phone_login_agreement"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppCheckbox(
                    checked = state.agreementAccepted,
                    onCheckedChange = null,
                    enabled = !state.isBusy,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                Text(
                    text = "我已阅读并同意",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(end = AppDimens.Space4),
                )
            }
            AgreementLink(text = "《知乎协议》", onClick = { openExternalUrl(ZHIHU_TERMS_URL) })
            AgreementLink(text = "《个人信息保护指引》", onClick = { openExternalUrl(ZHIHU_PRIVACY_URL) })
        }

        AppButton(
            onClick = {
                scope.launch {
                    state.signIn(accountStore)?.let(onLoginSuccess)
                }
            },
            enabled = state.canSignIn,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("phone_login_submit"),
        ) {
            if (state.isLoggingIn) {
                AppSpinner(modifier = Modifier, size = AppDimens.InlineSpinnerSize)
            } else {
                Text("登录")
            }
        }

        state.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .testTag("phone_login_error")
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        Text(
            text = "使用知乎官方登录协议；验证码形式由知乎风控实时决定。",
            style = if (settingSkinIsMiuix()) {
                LocalAppSkinTokens.current.footnote2
            } else {
                MaterialTheme.typography.bodySmall
            },
            color = if (settingSkinIsMiuix()) {
                LocalAppSkinTokens.current.onSurfaceVariantSummary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(top = AppDimens.Space4),
        )
    }

    if (countryPickerOpen) {
        AppAlertDialog(
            onDismissRequest = { countryPickerOpen = false },
            title = { Text("选择国家或地区") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Space8)) {
                    when {
                        state.isLoadingCountries || (state.countries == null && state.countryLoadError == null) -> {
                            AppSpinner(size = AppDimens.InlineSpinnerSize)
                            Text("正在获取地区列表…")
                        }

                        state.countryLoadError != null -> {
                            Text(state.countryLoadError.orEmpty(), color = MaterialTheme.colorScheme.error)
                            AppTextButton(
                                text = "重试",
                                onClick = { scope.launch { state.loadCountries(countryHttpClient) } },
                                modifier = Modifier.testTag("phone_login_country_retry"),
                            )
                        }

                        else -> {
                            AppTextField(
                                value = countryQuery,
                                onValueChange = { countryQuery = it },
                                labelText = "搜索地区、缩写或区号",
                                modifier = Modifier.fillMaxWidth().testTag("phone_login_country_search"),
                            )
                            val query = countryQuery.trim()
                            val filtered = state.countries.orEmpty().filter {
                                it.name.contains(query, ignoreCase = true) ||
                                    it.abbr.contains(query, ignoreCase = true) ||
                                    it.code.contains(query, ignoreCase = true)
                            }
                            if (filtered.isEmpty()) {
                                Text("没有匹配的地区")
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = AppDimens.Space240)) {
                                    items(filtered) { country ->
                                        AppTextButton(
                                            onClick = {
                                                state.selectCountry(country)
                                                countryPickerOpen = false
                                            },
                                            modifier = Modifier.fillMaxWidth().testTag("phone_login_country_${country.abbr}"),
                                        ) {
                                            Text(
                                                text = "${country.name} ${country.code}${if (country == state.country) " ✓" else ""}",
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { AppTextButton(text = "关闭", onClick = { countryPickerOpen = false }) },
        )
    }
}

@Composable
private fun CaptchaStep(
    state: PhoneLoginState,
    fieldShape: RoundedCornerShape,
    onRefresh: () -> Unit,
    onVerify: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Space8)) {
        val imageBase64 = state.captchaImageBase64
        val captchaBitmap = remember(imageBase64) {
            imageBase64?.let(::decodePhoneLoginCaptchaImage)
        }
        if (captchaBitmap != null) {
            Image(
                bitmap = captchaBitmap,
                contentDescription = "图形验证码",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppDimens.LargeInputHeight)
                    .testTag("phone_login_captcha_image"),
            )
        } else {
            CaptchaUnavailable(
                reason = if (imageBase64 == null) {
                    "服务器没有返回图形验证码"
                } else {
                    "图形验证码图片无法解析"
                },
                retryEnabled = state.canRefreshCaptcha,
                onRetry = onRefresh,
            )
        }
        AppTextField(
            value = state.captchaInput,
            onValueChange = { state.captchaInput = it },
            labelText = "图形验证码",
            enabled = !state.isBusy,
            singleLine = true,
            shape = fieldShape,
            miuixCornerRadius = AppDimens.LoginFieldCornerRadius,
            miuixInsideMargin = DpSize(AppDimens.Space16, AppDimens.Space16),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { if (state.canVerifyCaptcha) onVerify() },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("phone_login_captcha_input"),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Space8),
        ) {
            AppOutlinedButton(
                onClick = onRefresh,
                enabled = state.canRefreshCaptcha,
                modifier = Modifier
                    .weight(1f)
                    .testTag("phone_login_refresh_captcha"),
            ) {
                Text("换一张")
            }
            AppButton(
                text = "验证并发送",
                onClick = onVerify,
                modifier = Modifier
                    .weight(1f)
                    .testTag("phone_login_verify_captcha"),
                enabled = state.canVerifyCaptcha,
            )
        }
    }
}

@Composable
private fun CaptchaUnavailable(
    reason: String,
    retryEnabled: Boolean,
    onRetry: () -> Unit,
) {
    val shape = RoundedCornerShape(AppDimens.LoginFieldCornerRadius)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AppDimens.LargeInputHeight)
            .border(AppDimens.DividerThickness, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = AppDimens.Space16, vertical = AppDimens.Space8)
            .testTag("phone_login_captcha_unavailable"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = reason,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        AppTextButton(
            text = "重新获取",
            onClick = onRetry,
            enabled = retryEnabled,
        )
    }
}

@Composable
private fun AgreementLink(
    text: String,
    onClick: () -> Unit,
) {
    AppTextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = AppDimens.Space4, vertical = AppDimens.Space8),
    ) {
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}

internal expect val phoneLoginDeviceInfo: ZhihuPhoneLoginDeviceInfo

internal const val MAINLAND_PHONE_NUMBER_LENGTH = 11
internal const val MAX_PHONE_NUMBER_DIGITS = 15
internal const val INVALID_PHONE_NUMBER_MESSAGE = "请输入正确的中国大陆手机号"
internal const val INVALID_INTERNATIONAL_PHONE_NUMBER_MESSAGE = "请输入正确的手机号，含区号最多 15 位数字"

/** 表单和请求共用归一化规则，非大陆号码只限制 E.164 的总长度与字符。 */
internal fun normalizePhoneNumber(phoneNumber: String, countryCode: String): String? {
    val prefix = countryCode.trim().replace(" ", "").replace("-", "")
    if (!prefix.startsWith('+') ||
        prefix.length < 2 ||
        prefix[1] !in '1'..'9' ||
        !prefix.drop(1).all { it in '0'..'9' }
    ) {
        return null
    }
    val compact = phoneNumber.trim().replace(" ", "").replace("-", "")
    val local = if (prefix == "+86") {
        when {
            compact.startsWith("+86") -> compact.removePrefix("+86")
            compact.startsWith("86") && compact.length == MAINLAND_PHONE_NUMBER_LENGTH + 2 -> compact.removePrefix("86")
            else -> compact
        }.takeIf { it.length == MAINLAND_PHONE_NUMBER_LENGTH && it.startsWith('1') }
    } else {
        compact
    } ?: return null
    if (local.isEmpty() || !local.all { it in '0'..'9' } || prefix.length - 1 + local.length > MAX_PHONE_NUMBER_DIGITS) {
        return null
    }
    return prefix + local
}

@Serializable
private data class GuestTokenResponse(
    val accessToken: String,
    val tokenType: String = "bearer",
    val cookie: Map<String, String> = emptyMap(),
)

@Serializable
private data class DeviceGuestInitializationResponse(
    val udid: String,
    val guest: GuestTokenResponse,
)

@Serializable
private data class CaptchaResponse(
    val showCaptcha: Boolean = false,
    val imgBase64: String? = null,
)

@Serializable
private data class CaptchaVerificationResponse(
    val success: Boolean = false,
)

@Serializable
private data class TokenResponse(
    val accessToken: String,
    val refreshToken: String? = null,
    val tokenType: String = "bearer",
    val expiresIn: Long? = null,
    val cookie: Map<String, String> = emptyMap(),
)

private val CAPTCHA_INVALID_ERROR_CODES = setOf(120001, 120002)
private const val CAPTCHA_NEEDED_ERROR_CODE = 120005
