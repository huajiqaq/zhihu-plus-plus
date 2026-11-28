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

import com.github.zly2006.zhihu.account.ZhihuAccountClient
import com.github.zly2006.zhihu.account.createDirectHttpClient
import com.github.zly2006.zhihu.data.AIGC_MARKING_ENABLED_PREFERENCE_KEY
import com.github.zly2006.zhihu.data.AigcVoteVoter
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.platform.platformSettingsStore
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.job

private const val AIGC_VOTE_CLIENT_ID_KEY = "aigcVoteClientId"
internal const val AIGC_VOTE_SERVER_URL_KEY = "aigcVoteServerUrl"
private const val DEFAULT_AIGC_VOTE_SERVER_URL = "https://aigc-vote.ai.fintechedu.cn"

/** 第三方标记只读取当前账号公开身份；独立客户端随账号所有者关闭，不带知乎凭据。 */
internal class AccountAigcVoteEnvironment(override val accountClient: ZhihuAccountClient) : AigcVoteEnvironment, AutoCloseable {
    private val settings by lazy { platformSettingsStore() }
    private val client = lazy {
        createDirectHttpClient {
            install(ContentNegotiation) { json(ZhihuJson.json) }
        }
    }

    override fun isAigcVoteEnabled(): Boolean = accountClient.isCurrent && settings.getBoolean(AIGC_MARKING_ENABLED_PREFERENCE_KEY, false)

    override fun aigcVoteHttpClient(): HttpClient = accountClient.accountStore.withSessionLock {
        if (!accountClient.isCurrent) throw CancellationException("AIGC account client is no longer current")
        client.value
    }

    override fun aigcVoteBaseUrl(): String = settings.getString(AIGC_VOTE_SERVER_URL_KEY, DEFAULT_AIGC_VOTE_SERVER_URL)
        .ifBlank { DEFAULT_AIGC_VOTE_SERVER_URL }

    override fun aigcVoteClientId(): String = accountClient.accountStore.withSessionLock {
        if (!accountClient.isCurrent) throw CancellationException("AIGC account client is no longer current")
        settings.getStringOrNull(AIGC_VOTE_CLIENT_ID_KEY)?.takeIf(String::isNotBlank) ?: Uuid.random().toString().also {
            settings.putString(AIGC_VOTE_CLIENT_ID_KEY, it)
        }
    }

    override fun aigcVoteVoter(): AigcVoteVoter? = accountClient.accountStore.withSessionLock {
        if (!accountClient.isCurrent) return@withSessionLock null
        accountClient.load().takeIf { it.login }?.profile?.takeIf { it.id.isNotBlank() }?.let { profile ->
            AigcVoteVoter(id = profile.id, name = profile.name, urlToken = profile.urlToken, avatarUrl = profile.avatarUrl)
        }
    }

    override fun close() {
        if (client.isInitialized()) {
            client.value.coroutineContext.job.cancel()
            client.value.close()
        }
    }
}
