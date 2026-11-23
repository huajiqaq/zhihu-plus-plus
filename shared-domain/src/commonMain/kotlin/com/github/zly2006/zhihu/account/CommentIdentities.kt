/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.github.zly2006.zhihu.account

import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.installZhihuCommonClientConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

data class CommentSender(
    val sourceId: String,
    val profile: ZhihuAccountProfileSnapshot,
)

/** 身份切换会撤销来源凭据。普通请求可并行，切换、发送、切回独占同一来源账号。 */
class CommentIdentities internal constructor(
    private val store: ZhihuAccountStore,
) {
    private class Lane {
        val turn = Mutex()
        var readers = 0
        var drained = CompletableDeferred(Unit)
        var rotating = false
        var unsaved: Pair<ZhihuAccountSession, ZhihuAccountSession>? = null
    }

    private class ReadLease(
        val lane: Lane,
    ) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<ReadLease>
    }

    private val lanes = mutableMapOf<String, Lane>()

    private fun lane(id: String) = store.withSessionLock { lanes.getOrPut(id, ::Lane) }

    internal fun checkCanChangeAccounts() {
        check(lanes.values.none { it.rotating }) { "正在恢复评论身份，请稍后再切换账号" }
    }

    /** 原始 HttpClient、已绑定 Cookie 的调用和续期可嵌套；同一操作不重复申请读锁。 */
    internal suspend fun <T> withOriginal(id: String?, block: suspend () -> T): T {
        if (id == null) return block()
        val lane = lane(id)
        if (currentCoroutineContext()[ReadLease]?.lane === lane) return block()
        lane.turn.withLock {
            if (pending(id, lane) != null) {
                store.withSessionLock { lane.drained }.await()
                rotate(lane) { restore(id, lane) }
            }
            store.withSessionLock {
                if (lane.readers++ == 0) lane.drained = CompletableDeferred()
            }
        }
        try {
            return withContext(ReadLease(lane)) { block() }
        } finally {
            store.withSessionLock { if (--lane.readers == 0) lane.drained.complete(Unit) }
        }
    }

    internal fun bind(client: HttpClient, id: String?, owner: ZhihuAccountClient) {
        if (id == null) return
        client.requestPipeline.intercept(HttpRequestPipeline.Before) {
            withOriginal(id) {
                check(owner.isCurrent) { "账号已切换，请重新操作" }
                proceed()
            }
        }
    }

    private fun source(id: String): ZhihuSavedAccount = store.accounts.firstOrNull { it.id == id && it.session.login }
        ?: error("该账号已退出登录，请重新选择")

    private fun checkOwner(owner: ZhihuAccountClient) {
        check(owner.isCurrent && owner.load().login && !owner.verificationState.value.blocksSessionChange) {
            "账号已变更或需要先完成安全验证"
        }
    }

    suspend fun list(owner: ZhihuAccountClient, sourceId: String): List<CommentSender> = withOriginal(sourceId) {
        val session = store.withSessionLock {
            checkOwner(owner)
            source(sourceId).session
        }
        val accounts = createAccountHttpClient(session.cookies.toMutableMap(), session.userAgent, store.engine, store.http403Log::record).use { client ->
            val body = client.get("https://api.zhihu.com/people/account/list") { applyIdentityHeaders(session) }.identitySuccessBody("加载评论账号")
            ZhihuJson.decodeJson<ZhihuIdentityAccountListResponse>(ZhihuJson.json.parseToJsonElement(body)).data
        }
        accounts.filter { it.id.isNotBlank() && it.name.isNotBlank() }.map {
            CommentSender(sourceId, ZhihuAccountProfileSnapshot(it.id, it.name, it.urlToken, avatarUrl = it.avatarUrl))
        }
    }

    internal suspend fun <T> sendAs(
        owner: ZhihuAccountClient,
        sender: CommentSender,
        send: suspend (HttpClient, Map<String, String>) -> T,
    ): T {
        val id = sender.sourceId
        val lane = lane(id)
        return lane.turn.withLock {
            store.withSessionLock { lane.drained }.await()
            rotate(lane) {
                restore(id, lane)
                val sourceSession = store.withSessionLock {
                    checkOwner(owner)
                    source(id).session
                }
                val original = requireNotNull(sourceSession.profile) { "账号资料不完整，请重新登录" }
                require(original.id.isNotBlank() && sender.profile.id.isNotBlank()) { "账号身份不完整" }
                currentCoroutineContext().ensureActive()
                try {
                    val selected = if (original.id == sender.profile.id) {
                        sourceSession
                    } else {
                        withContext(NonCancellable) {
                            switch(sourceSession, sender.profile).also { issued ->
                                store.withSessionLock {
                                    lane.unsaved = sourceSession to issued
                                    store.accountState.updateAccount(source(id).copy(pendingCommentIdentity = issued))
                                    lane.unsaved = null
                                }
                            }
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    checkOwner(owner)
                    val cookies = selected.cookies.toMutableMap()
                    HttpClient(store.engine) {
                        installZhihuCommonClientConfig(
                            cookies,
                            selected.userAgent,
                            cookieLock = store.sessionLock,
                            loggedIn = { true },
                            onFailure = store.http403Log::record,
                            onCookieChanged = {
                                val saved = source(id)
                                val updated = selected.copy(cookies = cookies.toMutableMap())
                                if (selected === sourceSession) {
                                    store.accountState.updateAccount(saved.copy(session = updated))
                                    if (store.accountsState.value.activeAccountId == id) store.client.save(updated)
                                } else {
                                    lane.unsaved = saved.session to updated
                                    store.accountState.updateAccount(saved.copy(pendingCommentIdentity = updated))
                                    lane.unsaved = null
                                }
                            },
                        )
                    }.use { send(it, cookies.toMap()) }
                } finally {
                    withContext(NonCancellable) { restore(id, lane) }
                }
            }
        }
    }

    private suspend fun <T> rotate(lane: Lane, block: suspend () -> T): T {
        store.withSessionLock { lane.rotating = true }
        try {
            return block()
        } finally {
            store.withSessionLock { lane.rotating = false }
        }
    }

    private fun pending(id: String, lane: Lane) = store.withSessionLock {
        val saved = store.accounts.firstOrNull { it.id == id }
        lane.unsaved?.takeIf { it.first === saved?.session }?.second ?: saved?.pendingCommentIdentity
    }

    private suspend fun restore(id: String, lane: Lane) {
        val pending = pending(id, lane) ?: return
        val original = store.withSessionLock { requireNotNull(source(id).session.profile) }
        withContext(NonCancellable) {
            val restored = if (pending.profile?.id == original.id) pending else switch(pending, original)
            store.withSessionLock {
                val saved = source(id)
                lane.unsaved = saved.session to restored
                check(saved.session.profile?.id == original.id) { "来源账号已变更" }
                val updated = saved.session.copy(
                    cookies = restored.cookies,
                    mobileAccessToken = restored.mobileAccessToken,
                    mobileRefreshToken = restored.mobileRefreshToken,
                    mobileTokenType = restored.mobileTokenType,
                    mobileTokenExpiresAt = restored.mobileTokenExpiresAt,
                )
                store.accountState.updateAccount(saved.copy(session = updated, pendingCommentIdentity = null))
                if (store.accountsState.value.activeAccountId == id && store.client.isCurrent) store.client.save(updated)
                lane.unsaved = null
            }
        }
    }

    private suspend fun switch(source: ZhihuAccountSession, target: ZhihuAccountProfileSnapshot): ZhihuAccountSession {
        val cookies = source.cookies.toMutableMap()
        val body = createAccountHttpClient(cookies, source.userAgent, store.engine, store.http403Log::record).use { client ->
            client
                .post("https://api.zhihu.com/account/switch") {
                    applyIdentityHeaders(source)
                    contentType(ContentType.Application.Json)
                    setBody(SwitchAccountRequest(target.id))
                }.identitySuccessBody("切换评论身份")
        }
        val json = ZhihuJson.json.parseToJsonElement(body).jsonObject
        check(json["verification"] == null || json["verification"] == JsonNull) { "请在身份管理中完成知乎验证" }
        val token = ZhihuJson.decodeJson<ZhihuIdentityToken>(json)
        check(token.uid == target.id && token.accessToken.isNotBlank() && !token.cookie["z_c0"].isNullOrBlank()) { "服务器未返回所选身份的有效凭据" }
        cookies.putAll(token.cookie)
        return source.copy(
            username = target.name,
            profile = target,
            self = null,
            cookies = cookies,
            mobileAccessToken = token.accessToken,
            mobileRefreshToken = token.refreshToken,
            mobileTokenType = token.tokenType,
            mobileTokenExpiresAt = token.expiresAt,
        )
    }
}
