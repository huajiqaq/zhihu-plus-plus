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

package com.github.zly2006.zhihu.viewmodel

import com.github.zly2006.zhihu.util.raiseForStatus
import com.github.zly2006.zhihu.util.signZhihuFetchRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.request
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 作者关注共用签名协议；请求前后均校验客户端，切号后的结果不能回写关系。 */
suspend fun ZhihuApiEnvironment.setAuthorFollowing(
    owner: HttpClient,
    authorUrlToken: String,
    following: Boolean,
) {
    require(authorUrlToken.isNotBlank())
    withAuthenticatedClient { client, cookies ->
        if (client !== owner) throw CancellationException("操作期间账户已切换")
        client
            .request("https://www.zhihu.com/api/v4/members/$authorUrlToken/followers") {
                method = if (following) HttpMethod.Post else HttpMethod.Delete
                signZhihuFetchRequest(cookies)
            }.raiseForStatus()
    }
    currentCoroutineContext().ensureActive()
    if (httpClient() !== owner) throw CancellationException("操作期间账户已切换")
}
