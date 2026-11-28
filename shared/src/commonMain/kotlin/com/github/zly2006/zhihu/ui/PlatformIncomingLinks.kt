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

package com.github.zly2006.zhihu.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.resolveContent
import io.ktor.http.Url
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

private val incomingLinks = Channel<NavDestination>(capacity = 16)

/** 只接受共享解析器支持的知乎路由，不接受任意网页或 URL 凭据；进入页面不提交写操作。 */
internal fun resolvePlatformIncomingUrl(value: String): NavDestination? {
    if (value.length > 8192) return null
    return runCatching {
        val input = value.trim()
        val url = Url(input)
        if (!url.user.isNullOrEmpty() || !url.password.isNullOrEmpty()) return null
        val target = if (url.protocol.name == "zhihu-plus-plus") {
            if (url.host != "open" || url.encodedPath !in setOf("", "/") || url.parameters.names() != setOf("url") || url.parameters.getAll("url")?.size != 1) return null
            url.parameters["url"] ?: return null
        } else input
        if (target.length > 8192) return null
        val contentUrl = Url(target)
        if (!contentUrl.user.isNullOrEmpty() || !contentUrl.password.isNullOrEmpty()) return null
        resolveContent(target)
    }.getOrNull()
}

/** Swift/AppKit/桌面 launcher 在 UI 未就绪时也可入队；只保留有界公开路由。 */
fun handlePlatformIncomingUrl(value: String): Boolean =
    resolvePlatformIncomingUrl(value)?.let { incomingLinks.trySend(it).isSuccess } ?: false

@Composable
internal fun PlatformIncomingLinksHost(onNavigate: (NavDestination) -> Unit) {
    val navigate by rememberUpdatedState(onNavigate)
    LaunchedEffect(Unit) { incomingLinks.receiveAsFlow().collect { navigate(it) } }
}
