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

package com.github.zly2006.zhihu.util

import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.account.ZhihuMobileSessionProtocol
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** 启动、显式续期和过期票据重试共用所属账号槽位的一把锁。 */
class ZhihuCredentialRefresher internal constructor(
    private val accountStore: ZhihuAccountStore,
    private val accountId: String?,
) {
    private val mutex = Mutex()
    private var lastAttempt: TimeMark? = null

    suspend fun refresh(force: Boolean = false, rejectedCookie: String? = null): Boolean = mutex.withLock {
        val current = accountStore.renewalSession(accountId, this) ?: return@withLock false
        val personId = current.profile?.id?.takeIf(String::isNotBlank)
        val refreshToken = current.mobileRefreshToken?.takeIf(String::isNotBlank)
        if (!current.login || personId == null || refreshToken == null) return@withLock false
        if (rejectedCookie != null && !current.cookies["z_c0"].isNullOrBlank() && current.cookies["z_c0"] != rejectedCookie) return@withLock true
        if (!force) {
            val expiresAt = current.mobileTokenExpiresAt ?: return@withLock false
            if (expiresAt <= 0 || expiresAt > Clock.System.now().epochSeconds + 1.days.inWholeSeconds) return@withLock false
        }
        if (lastAttempt?.elapsedNow()?.let { it < 1.minutes } == true) return@withLock false
        lastAttempt = TimeSource.Monotonic.markNow()
        val protocol = ZhihuMobileSessionProtocol(accountStore.engine)
        val token = try {
            protocol.refresh(refreshToken)
        } finally {
            protocol.close()
            lastAttempt = TimeSource.Monotonic.markNow()
        }
        check(token.accountId == personId) { "刷新响应的账号与当前登录账号不一致" }
        accountStore.saveRenewedSession(accountId, this, refreshToken, token)
    }
}
