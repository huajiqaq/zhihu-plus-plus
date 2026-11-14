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

import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.util.ZhihuMessageBodyEncryptor
import com.github.zly2006.zhihu.util.hmacSha1Hex
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock

/** 借用账号 store 的 engine；续期 Cookie 与活动账号隔离，验证后由 store 原子提交。 */
internal class ZhihuMobileSessionProtocol(
    engine: HttpClientEngine,
) : AutoCloseable {
    private val cookies = mutableMapOf<String, String>()
    private val httpClient = createAccountHttpClient(cookies, MOBILE_USER_AGENT, engine)

    suspend fun refresh(refreshToken: String): ZhihuMobileLoginToken {
        require(refreshToken.isNotBlank()) { "缺少移动端刷新凭据，请重新登录" }
        val timestamp = Clock.System.now().epochSeconds
        val signature = hmacSha1Hex(MOBILE_CLIENT_SECRET, "refresh_token$MOBILE_CLIENT_ID$MOBILE_SOURCE$timestamp")
        val response = httpClient.post("https://api.zhihu.com/api/account/prod/sign_in") {
            accept(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "oauth $MOBILE_CLIENT_ID")
            header("x-api-version", "3.0.93")
            header("x-app-version", "11.4.0")
            header("x-app-build", "release")
            header("x-app-bundleid", MOBILE_SOURCE)
            header("x-app-flavor", "honor")
            header("x-app-za", "OS=Android&Release=12&Model=Android+SDK+built+for+arm64&VersionName=11.4.0&VersionCode=40408&Product=com.zhihu.android&Width=1080&Height=2274&Installer=%E8%8D%A3%E8%80%80%E5%95%86%E5%BA%97&DeviceType=AndroidPhone&Brand=Android")
            header("x-network-type", "3G")
            header("x-page-id", "44")
            header("x-zse-93", "101_1_1.0")
            contentType(ContentType.Application.FormUrlEncoded)
            val form = Parameters
                .build {
                    append("client_id", MOBILE_CLIENT_ID)
                    append("grant_type", "refresh_token")
                    append("signature", signature)
                    append("source", MOBILE_SOURCE)
                    append("timestamp", timestamp.toString())
                    append("refresh_token", refreshToken)
                }.formUrlEncode()
            setBody(ZhihuMessageBodyEncryptor.encrypt(form))
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val message = runCatching {
                val root = ZhihuJson.json.parseToJsonElement(body).jsonObject
                (root["error"]?.jsonObject ?: root)["message"]?.jsonPrimitive?.content
            }.getOrNull()
            error(message?.let { "刷新登录状态失败：$it" } ?: "刷新登录状态失败（HTTP ${response.status.value}）")
        }
        val token = ZhihuJson.decodeJson<MobileRefreshResponse>(ZhihuJson.json.parseToJsonElement(body))
        check(token.accessToken.isNotBlank()) { "服务器未返回登录凭证" }
        check(token.verification == null || token.verification == JsonNull) { "需要完成知乎身份验证，请重新登录" }
        cookies.putAll(token.cookie.filterValues(String::isNotBlank))
        check(!token.refreshToken.isNullOrBlank() && !cookies["z_c0"].isNullOrBlank()) { "刷新响应缺少必要登录凭据" }
        check(token.tokenType.isNotBlank()) { "刷新响应缺少令牌类型" }
        val expiresAt = token.expiresAt ?: token.expiresIn?.let(timestamp::plus)
        check(expiresAt != null && expiresAt > timestamp) { "刷新响应缺少有效的到期时间" }
        return ZhihuMobileLoginToken(token.accessToken, token.refreshToken, token.tokenType, expiresAt, cookies.toMap(), token.uid)
    }

    override fun close() = httpClient.close()
}

private const val MOBILE_CLIENT_ID = "8d5227e0aaaa4797a763ac64e0c3b8"
private const val MOBILE_CLIENT_SECRET = "ecbefbf6b17e47ecb9035107866380"
private const val MOBILE_SOURCE = "com.zhihu.android"
private const val MOBILE_USER_AGENT = "com.zhihu.android/Futureve/11.4.0 Mozilla/5.0 (Linux; Android 12; Android SDK built for arm64 Build/SE1A.220621.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/57.0.1000.10 Mobile Safari/537.36"

@Serializable
private data class MobileRefreshResponse(
    val accessToken: String,
    val refreshToken: String? = null,
    val tokenType: String = "bearer",
    val expiresIn: Long? = null,
    val expiresAt: Long? = null,
    val uid: String? = null,
    val verification: JsonElement? = null,
    val cookie: Map<String, String> = emptyMap(),
)
