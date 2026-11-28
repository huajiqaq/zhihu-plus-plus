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
import com.github.zly2006.zhihu.ui.ReaderDisplayFailure
import com.github.zly2006.zhihu.util.HttpStatusException
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine

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

/** 更新请求只借用账户 store 的 engine；独立客户端不安装 Cookie 存储，用完即关。 */
suspend fun <T> withUpdateHttpClient(engine: HttpClientEngine, block: suspend (HttpClient) -> T): T {
    val client = HttpClient(engine) {
        installZhihuCommonClientConfig(mutableMapOf(), DEFAULT_ZHIHU_USER_AGENT, enableCookies = false)
    }
    try {
        return block(client)
    } finally {
        client.close()
    }
}
