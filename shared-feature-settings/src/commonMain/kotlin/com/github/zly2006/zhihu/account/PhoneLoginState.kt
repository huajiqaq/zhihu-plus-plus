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

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.byValue
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.github.zly2006.zhihu.data.HttpFailureRecord
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.toCookieHeaderString
import com.github.zly2006.zhihu.i18n.AccountStrings
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
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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

internal val PhoneLoginCountry.displayName: String
    get() = if (this == DEFAULT_COUNTRY) AccountStrings.china else name

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
    val phoneNumber = TextFieldState(phoneNumber)
    internal var country by mutableStateOf(country)
        private set
    internal var countries by mutableStateOf<List<PhoneLoginCountry>?>(null)
        private set
    internal var isLoadingCountries by mutableStateOf(false)
        private set
    private var countryLoadErrorText by mutableStateOf<(() -> String)?>(null)
    internal val countryLoadError: String? get() = countryLoadErrorText?.invoke()
    var agreementAccepted by mutableStateOf(agreementAccepted)
    val digits = TextFieldState()
    val captchaInput = TextFieldState()
    private var requestedPhone by mutableStateOf<String?>(null)
    private val currentRequestPhone: String?
        get() = requestedPhone?.takeIf { phoneNumber.text.contentEquals(it) }
    private var needsCaptcha by mutableStateOf(false)
    val captchaRequired: Boolean
        get() = needsCaptcha && currentRequestPhone != null
    var captchaImageBase64 by mutableStateOf<String?>(null)
        private set
    private var smsSent by mutableStateOf(false)
    val hasRequestedDigits: Boolean
        get() = smsSent && currentRequestPhone != null
    var isSendingDigits by mutableStateOf(false)
        private set
    var isLoggingIn by mutableStateOf(false)
        private set
    var resendSeconds by mutableIntStateOf(0)
        private set
    private var errorText by mutableStateOf<(() -> String)?>(null)
    val errorMessage: String? get() = errorText?.invoke()

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
        get() = normalizePhoneNumber(phoneNumber.text.toString(), country.code) != null

    val canSendDigits: Boolean
        get() = agreementAccepted && isPhoneNumberValid && resendSeconds == 0 && !isBusy

    val canRefreshCaptcha: Boolean
        get() = agreementAccepted && captchaRequired && !isBusy

    val canVerifyCaptcha: Boolean
        get() = agreementAccepted && captchaRequired && captchaInput.text.isNotBlank() && !isBusy

    val canSignIn: Boolean
        get() = agreementAccepted &&
            hasRequestedDigits &&
            isPhoneNumberValid &&
            digits.text.length == SMS_CODE_LENGTH &&
            !isBusy

    internal suspend fun loadCountries(httpClient: HttpClient) {
        if (isLoadingCountries || countries != null) return
        isLoadingCountries = true
        countryLoadErrorText = null
        try {
            val response = httpClient.get(SUPPORTED_COUNTRIES_URL)
            check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
            val result = ZhihuJson.decodeJson<SupportedCountriesResponse>(
                ZhihuJson.json.parseToJsonElement(response.bodyAsText()),
            )
            if (result.data.isEmpty()) throw LoginMessageException { AccountStrings.countriesMissing }
            countries = result.data
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            countryLoadErrorText = { AccountStrings.countriesFailed(error.message ?: AccountStrings.checkNetwork) }
        } finally {
            isLoadingCountries = false
        }
    }

    internal fun selectCountry(value: PhoneLoginCountry) {
        if (country == value) return
        country = value
        invalidateDigits()
    }

    internal val phoneNumberTransformation = InputTransformation.byValue { _, proposed ->
        proposed.filter { it in '0'..'9' }.take(MAX_PHONE_NUMBER_DIGITS)
    }

    internal val digitsTransformation = InputTransformation.byValue { _, proposed ->
        proposed.filter(Char::isDigit).take(SMS_CODE_LENGTH)
    }

    internal suspend fun clearVerificationOnPhoneChanges() {
        snapshotFlow { phoneNumber.text.toString() }.drop(1).collect {
            /** 同一帧若已为新号码发起请求，不再清它的验证码或结果。 */
            if (currentRequestPhone == null) invalidateDigits()
        }
    }

    private fun invalidateDigits() {
        digits.clearText()
        digits.undoState.clearHistory()
        requestedPhone = null
        smsSent = false
        needsCaptcha = false
        captchaImageBase64 = null
        captchaInput.clearText()
        captchaInput.undoState.clearHistory()
        errorText = null
    }

    suspend fun requestDigits() {
        if (isBusy) return
        val number = phoneNumber.text.toString()
        if (requestedPhone != number) {
            invalidateDigits()
            requestedPhone = number
        }
        errorText = null
        isSendingDigits = true
        try {
            val username = normalizePhoneNumber(number, country.code) ?: throw LoginMessageException {
                if (country.code == "+86") INVALID_PHONE_NUMBER_MESSAGE else INVALID_INTERNATIONAL_PHONE_NUMBER_MESSAGE
            }
            ensureGuestToken()
            if (currentRequestPhone != number) return
            val captchaState = requestCaptchaState()
            if (currentRequestPhone != number) return
            val result = if (captchaState.showCaptcha) {
                ZhihuPhoneDigitsResult.CaptchaRequired(loadCaptchaImage())
            } else {
                sendDigits(username, recheckCaptchaWhenRequested = true) ?: return
            }
            if (currentRequestPhone != number) return
            when (result) {
                ZhihuPhoneDigitsResult.Sent -> {
                    smsSent = true
                    needsCaptcha = false
                    captchaImageBase64 = null
                    captchaInput.clearText()
                    captchaInput.undoState.clearHistory()
                    resendSeconds = RESEND_COOLDOWN_SECONDS
                }

                is ZhihuPhoneDigitsResult.CaptchaRequired -> {
                    needsCaptcha = true
                    captchaImageBase64 = result.imageBase64
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (currentRequestPhone == number) errorText = { error.message ?: AccountStrings.smsFailed }
        } finally {
            isSendingDigits = false
        }
    }

    suspend fun refreshCaptcha() {
        val number = currentRequestPhone ?: return
        if (isBusy || !needsCaptcha) return
        errorText = null
        isSendingDigits = true
        try {
            ensureGuestToken()
            if (currentRequestPhone != number) return
            val image = loadCaptchaImage()
            if (currentRequestPhone == number) captchaImageBase64 = image
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (currentRequestPhone == number) errorText = { error.message ?: AccountStrings.captchaRefreshFailed }
        } finally {
            isSendingDigits = false
        }
    }

    suspend fun verifyCaptchaAndRequestDigits() {
        val number = currentRequestPhone ?: return
        if (isBusy || !needsCaptcha) return
        errorText = null
        isSendingDigits = true
        val verified = try {
            val captcha = captchaInput.text.toString().trim()
            if (captcha.isEmpty()) throw LoginMessageException { AccountStrings.captchaRequired }
            ensureGuestToken()
            if (currentRequestPhone != number) return
            val response = loginHttpClient.value.post("$PHONE_LOGIN_API_BASE_URL$CAPTCHA_PATH") {
                applyMobileHeaders()
                setEncryptedForm("input_text" to captcha)
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) throw LoginMessageException { AccountStrings.captchaVerifyHttp(response.status.value) }
            ZhihuJson.decodeJson<CaptchaVerificationResponse>(ZhihuJson.json.parseToJsonElement(body)).success
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (currentRequestPhone == number) errorText = { error.message ?: AccountStrings.captchaVerifyFailed }
            return
        } finally {
            isSendingDigits = false
        }
        if (currentRequestPhone != number) return
        if (verified) {
            requestDigits()
        } else {
            errorText = { AccountStrings.captchaIncorrect }
        }
    }

    suspend fun signIn(accountStore: ZhihuAccountStore, owner: ZhihuAccountClient): String? {
        if (!owner.isCurrent || accountStore.client !== owner) return null
        val number = currentRequestPhone ?: return null
        if (isBusy || !smsSent) return null
        errorText = null
        isLoggingIn = true
        try {
            val username = normalizePhoneNumber(number, country.code) ?: throw LoginMessageException {
                if (country.code == "+86") INVALID_PHONE_NUMBER_MESSAGE else INVALID_INTERNATIONAL_PHONE_NUMBER_MESSAGE
            }
            val code = digits.text.toString().trim()
            if (code.isEmpty()) throw LoginMessageException { AccountStrings.smsRequired }
            ensureGuestToken()
            if (currentRequestPhone != number) return null
            val timestamp = Clock.System.now().epochSeconds
            val signature = hmacSha1Hex(MOBILE_CLIENT_SECRET, "$DIGITS_GRANT_TYPE$MOBILE_CLIENT_ID$MOBILE_SOURCE$timestamp")
            val response = loginHttpClient.value.post("$PHONE_LOGIN_API_BASE_URL$SIGN_IN_PATH") {
                applyMobileHeaders()
                setEncryptedForm(
                    "client_id" to MOBILE_CLIENT_ID,
                    "digits" to code,
                    "grant_type" to DIGITS_GRANT_TYPE,
                    "signature" to signature,
                    "source" to MOBILE_SOURCE,
                    "timestamp" to timestamp.toString(),
                    "username" to username,
                )
            }
            val body = response.bodyAsText()
            if (currentRequestPhone != number) return null
            if (!response.status.isSuccess()) {
                val message = runCatching {
                    val root = ZhihuJson.json.parseToJsonElement(body).jsonObject
                    (root["error"]?.jsonObject ?: root)["message"]?.jsonPrimitive?.content
                }.getOrNull()
                throw LoginMessageException { message?.let { AccountStrings.phoneSignInFailedDetail(it) } ?: AccountStrings.phoneSignInFailedHttp(response.status.value) }
            }
            val responseToken = ZhihuJson.decodeJson<TokenResponse>(ZhihuJson.json.parseToJsonElement(body))
            if (responseToken.accessToken.isBlank()) throw LoginMessageException { AccountStrings.loginTokenMissing }
            cookies.putAll(responseToken.cookie.filterValues(String::isNotBlank))
            if (cookies["d_c0"].isNullOrBlank()) cookies["d_c0"] = webDeviceCookie ?: throw LoginMessageException { AccountStrings.deviceCookieMissing }
            val token = ZhihuMobileLoginToken(
                accessToken = responseToken.accessToken,
                refreshToken = responseToken.refreshToken,
                tokenType = responseToken.tokenType,
                expiresAt = responseToken.expiresIn?.let(timestamp::plus),
                cookies = cookies.toMap(),
            )
            if (accountStore.login(owner, token)) {
                return accountStore.session.username
            }
            errorText = { AccountStrings.loginTokenInvalid }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (currentRequestPhone == number) errorText = { error.message ?: AccountStrings.loginFailed }
        } finally {
            isLoggingIn = false
        }
        return null
    }

    private suspend fun sendDigits(
        username: String,
        recheckCaptchaWhenRequested: Boolean,
    ): ZhihuPhoneDigitsResult? {
        val number = currentRequestPhone ?: return null
        val response = loginHttpClient.value.post("$PHONE_LOGIN_API_BASE_URL$AUTH_DIGITS_PATH") {
            applyMobileHeaders()
            setEncryptedForm(
                "username" to username,
                "client_id" to MOBILE_CLIENT_ID,
            )
        }
        val body = response.bodyAsText()
        if (currentRequestPhone != number) return null
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
            if (currentRequestPhone != number) return null
            return if (captchaState.showCaptcha) {
                ZhihuPhoneDigitsResult.CaptchaRequired(loadCaptchaImage())
            } else {
                sendDigits(username, recheckCaptchaWhenRequested = false)
            }
        }
        val errorMessage = error?.get("message")?.jsonPrimitive?.content
        throw LoginMessageException {
            errorMessage?.let { AccountStrings.smsFailedDetail(it) }
                ?: AccountStrings.smsFailedHttp(response.status.value)
        }
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
            if (!response.status.isSuccess()) throw LoginMessageException { AccountStrings.webDeviceFailedHttp(response.status.value) }
            webDeviceCookie = fetchedDeviceCookie ?: throw LoginMessageException { AccountStrings.deviceCookieMissing }
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
            throw LoginMessageException {
                message?.let { AccountStrings.phoneInitFailedDetail(it) }
                    ?: AccountStrings.phoneInitFailedHttp(response.status.value)
            }
        }
        val initialization = ZhihuJson.decodeJson<DeviceGuestInitializationResponse>(ZhihuJson.json.parseToJsonElement(body))
        val guest = initialization.guest
        if (guest.accessToken.isBlank()) throw LoginMessageException { AccountStrings.guestTokenMissing }
        if (initialization.udid.isBlank()) throw LoginMessageException { AccountStrings.deviceTokenMissing }
        cookies.putAll(guest.cookie.filterValues(String::isNotBlank))
        deviceId = initialization.udid
        authorization = "${guest.tokenType.ifBlank { "bearer" }} ${guest.accessToken}"
    }

    private suspend fun requestCaptchaState(): CaptchaResponse {
        val response = loginHttpClient.value.get("$PHONE_LOGIN_API_BASE_URL$CAPTCHA_PATH") {
            applyMobileHeaders()
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) throw LoginMessageException { AccountStrings.captchaCheckHttp(response.status.value) }
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
        if (!response.status.isSuccess()) throw LoginMessageException { AccountStrings.captchaFetchHttp(response.status.value) }
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
            save = { listOf(it.phoneNumber.text.toString(), it.agreementAccepted, it.country.code, it.country.name, it.country.abbr) },
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
    LaunchedEffect(state) { state.clearVerificationOnPhoneChanges() }
    DisposableEffect(state) {
        onDispose(state::close)
    }
    return state
}

internal expect val phoneLoginDeviceInfo: ZhihuPhoneLoginDeviceInfo

internal const val MAINLAND_PHONE_NUMBER_LENGTH = 11
internal const val MAX_PHONE_NUMBER_DIGITS = 15
internal val INVALID_PHONE_NUMBER_MESSAGE: String get() = AccountStrings.invalidMainlandPhone
internal val INVALID_INTERNATIONAL_PHONE_NUMBER_MESSAGE: String get() = AccountStrings.invalidInternationalPhone

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

/** 保留本地错误的语义，语言切换时重新解析，不重建登录表单或重发请求。 */
internal class LoginMessageException(
    private val text: () -> String,
) : IllegalStateException() {
    override val message: String get() = text()
}
