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

package com.github.zly2006.zhihu.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.github.zly2006.zhihu.account.rememberZhihuAccountStore
import com.github.zly2006.zhihu.data.toCookieHeaderString
import io.ktor.http.Url

@Composable
fun rememberMarkdownImageModel(url: String): Any {
    val context = LocalPlatformContext.current
    val accountStore = rememberZhihuAccountStore()
    val accounts by accountStore.accountsState.collectAsState()
    val session = accounts.session
    val host = remember(url) { runCatching { Url(url).host.lowercase() }.getOrDefault("") }
    val cookieHeader = session.cookies.toCookieHeaderString().takeIf { host == "zhihu.com" || host.endsWith(".zhihu.com") }
    val headers = remember(cookieHeader, session.userAgent) {
        NetworkHeaders
            .Builder()
            .apply {
                /** Coil 显式请求头不经过 Ktor 的域名校验。 */
                cookieHeader?.let { set("Cookie", it) }
                set("User-Agent", session.userAgent)
            }.build()
    }
    return remember(context, url, headers) {
        ImageRequest
            .Builder(context)
            .data(url)
            .httpHeaders(headers)
            .build()
    }
}
