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

import com.github.zly2006.zhihu.account.InvalidRefreshTokenException
import com.github.zly2006.zhihu.account.ZhihuAccountClient
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.account.ZhihuMobileSessionProtocol
import com.github.zly2006.zhihu.account.canRenewCredentials
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CopyableThrowable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** 启动、显式续期和过期票据重试共用所属账号槽位的一把锁。 */
internal class ZhihuCredentialRefresher(
    private val accountStore: ZhihuAccountStore,
    private val accountId: String?,
) {
    private val mutex = Mutex()
    private var lastAttempt: TimeMark? = null

    suspend fun refresh(owner: ZhihuAccountClient, force: Boolean, rejectedCookie: String? = null): Boolean = mutex.withLock {
        val current = accountStore.renewalSession(owner, accountId, this)?.takeIf { it.canRenewCredentials } ?: return@withLock false
        val refreshToken = checkNotNull(current.mobileRefreshToken)
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
        } catch (rejected: InvalidRefreshTokenException) {
            if (!owner.reportSessionExpired(rejected, refreshToken)) return@withLock false
            throw rejected
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // 网络或解码异常可能带响应片段，不向调用方传递其 message 或 cause。
            throw TemporaryCredentialRefreshException()
        } finally {
            protocol.close()
            lastAttempt = TimeSource.Monotonic.markNow()
        }
        check(token.accountId == current.profile?.id) { "刷新响应的账号与当前登录账号不一致" }
        accountStore.saveRenewedSession(accountId, this, refreshToken, token)
    }
}

/** 协程栈恢复不能通过复制异常重新引入 cause，保持脱敏失败的无 cause 契约。 */
@OptIn(ExperimentalCoroutinesApi::class)
private class TemporaryCredentialRefreshException :
    IllegalStateException("未能刷新登录状态，请稍后重试；若多次失败，请重新登录"),
    CopyableThrowable<TemporaryCredentialRefreshException> {
    override fun createCopy(): TemporaryCredentialRefreshException? = null
}
