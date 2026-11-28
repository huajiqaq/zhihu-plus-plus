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

package com.github.zly2006.zhihu.updater

import com.github.zly2006.zhihu.account.DEFAULT_ZHIHU_USER_AGENT
import com.github.zly2006.zhihu.data.installZhihuCommonClientConfig
import com.github.zly2006.zhihu.i18n.ReaderStrings
import com.github.zly2006.zhihu.i18n.SettingsStrings
import com.github.zly2006.zhihu.ui.ReaderDisplayFailure
import com.github.zly2006.zhihu.util.HttpStatusException
import com.github.zly2006.zhihu.util.raiseForStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal const val SYSTEM_UPDATE_AUTO_CHECK_INTERVAL_MILLIS = 3 * 60 * 60 * 1000L

enum class UpdateStep {
    Check,
    Download,
    Install,
}

/** 给用户看的失败原因：HTTP 错误只保留状态码和可以采取的动作，不显示整段响应体。 */
fun describeUpdateFailure(error: Throwable): String = when (error) {
    is ReaderDisplayFailure -> error.displayMessage()
    is HttpStatusException -> when (error.status.value) {
        401 -> ReaderStrings.githubTokenInvalid
        403, 429 -> ReaderStrings.githubAccessLimited(error.status.value)
        else -> ReaderStrings.serverHttpError(error.status.value, error.status.description)
    }
    else -> error.message?.takeIf { it.isNotBlank() } ?: error::class.simpleName ?: ReaderStrings.unknownError
}

/** 固定官方发布资产，流式写入独占临时文件；元数据缺长度时仍限制上限和响应声明长度。 */
internal suspend fun downloadReleaseAsset(client: HttpClient, asset: GithubAsset, write: (ByteArray, Int) -> Unit) {
    require(asset.hasValidDownloadMetadata()) { SettingsStrings.updateFileInvalid }
    client.prepareGet(asset.browserDownloadUrl).execute { response ->
        response.raiseForStatus()
        check(response.status.value == 200 &&
            !response.headers[HttpHeaders.ContentType].orEmpty().startsWith("text/", ignoreCase = true)
        ) { SettingsStrings.updateFileInvalid }
        val responseBytes = response.headers[HttpHeaders.ContentLength]?.let { header ->
            val length = header.toLongOrNull()
            check(length != null && length in 1..MAX_UPDATE_DOWNLOAD_BYTES) { SettingsStrings.updateFileInvalid }
            check(asset.size == 0L || length == asset.size) { SettingsStrings.updateFileInvalid }
            length
        }
        val limit = asset.size.takeIf { it > 0 } ?: responseBytes ?: MAX_UPDATE_DOWNLOAD_BYTES
        val channel = response.bodyAsChannel()
        val buffer = ByteArray(64 * 1024)
        var received = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = channel.readAvailable(buffer)
            if (count == -1) break
            received += count
            check(received <= limit) { SettingsStrings.updateFileInvalid }
            write(buffer, count)
        }
        check(received > 0 && (asset.size == 0L || received == asset.size) &&
            (responseBytes == null || received == responseBytes)
        ) { SettingsStrings.updateFileInvalid }
    }
}

/** 更新请求只借用账户 store 的 engine；独立客户端不安装 Cookie 存储，用完即关。 */
suspend fun <T> withUpdateHttpClient(engine: HttpClientEngine, block: suspend (HttpClient) -> T): T {
    val client = HttpClient(engine) {
        installZhihuCommonClientConfig(mutableMapOf(), DEFAULT_ZHIHU_USER_AGENT, enableCookies = false)
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
            requestTimeoutMillis = 15 * 60_000L
        }
    }
    try {
        return block(client)
    } finally {
        client.close()
    }
}
