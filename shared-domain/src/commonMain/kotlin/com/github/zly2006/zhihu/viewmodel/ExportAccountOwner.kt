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

import com.github.zly2006.zhihu.i18n.DomainStrings
import io.ktor.client.HttpClient
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private class ExportAccountOwner(val validate: () -> Boolean) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ExportAccountOwner>
}

/** 平台发布前调用；账号切换会取消整个操作，复核覆盖系统交付前最后一个挂起点。 */
suspend fun ensureExportAccountActive() {
    val context = currentCoroutineContext()
    context.ensureActive()
    if (context[ExportAccountOwner]?.validate?.invoke() == false) throw CancellationException(DomainStrings.accountChangedReopen)
}

internal suspend fun <T> withExportAccount(
    environment: ZhihuApiEnvironment,
    expectedHttpClient: HttpClient? = null,
    block: suspend () -> T,
): T {
    val owner = environment.accountClient
    val transport = environment.httpClient()
    check(expectedHttpClient == null || transport === expectedHttpClient) { DomainStrings.accountChangedReopen }
    val guard = ExportAccountOwner {
        owner?.isCurrent != false && environment.accountClient === owner && environment.httpClient() === transport
    }
    suspend fun execute(): T = withContext(guard) {
        ensureExportAccountActive()
        block()
    }
    return if (owner == null) execute() else owner.withCurrentOwnerOperation { execute() }
}
