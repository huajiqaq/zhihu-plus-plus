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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.github.zly2006.zhihu.account

import io.ktor.client.request.HttpRequestData
import io.ktor.client.utils.EmptyContent
import io.ktor.http.content.OutgoingContent
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.suspendCancellableCoroutine

/** 方法、主机与路径在两端分别映射；不向油猴暴露 URL 或请求头代理。 */
internal data class WebBusinessOperation(val name: String, val id: String = "")

internal fun webBusinessOperation(data: HttpRequestData): WebBusinessOperation? {
    val method = data.method.value
    val path = data.url.encodedPath
    val host = data.url.host
    fun route(verb: String, pattern: String, op: String): WebBusinessOperation? {
        if (method !in verb.split('|')) return null
        val match = Regex(pattern).matchEntire(path) ?: return null
        return WebBusinessOperation(op + if (verb.contains('|')) "-$method" else "", match.groupValues.drop(1).joinToString("/"))
    }
    val number = "([1-9][0-9]{0,18})"
    val person = "([A-Za-z0-9_-]{1,128})"
    return when (host) {
        "www.zhihu.com" -> route("POST", "/api/v4/(answers|articles)/$number/voters", "vote")
            ?: route("POST|DELETE", "/api/v4/pins/$number/voters/up", "pinVote")
            ?: route("POST|DELETE", "/api/v4/(members|questions|topics)/$person/followers", "follow")
            ?: route("POST|DELETE", "/api/v4/members/$person/actions/block", "block")
            ?: route("POST|DELETE", "/api/v4/comments/$number/like", "commentLike")
            ?: route("POST", "/api/v4/comment_v5/(answers|articles|questions|pins)/$number/(comment|segment/comment)", "commentSend")
            ?: route("DELETE", "/api/v4/comment_v5/comment/$number", "commentDelete")
            ?: route("POST", "/api/v4/collections", "collectionCreate")
            ?: route("DELETE", "/api/v4/collections/$number", "collectionDelete")
            ?: route("POST", "/api/v4/polls/$number", "pollVote")
            ?: route("GET|POST", "/api/v4/questions/$number/draft", "answerDraft")
            ?: route("POST", "/api/v4/content/publish", "publish")
            ?: route("POST", "/api/v4/read_history/add", "historyAdd")
            ?: route("GET", "/api/v4/collections/$number(?:/(items))?", "collectionRead")
            ?: route("GET", "/api/v4/people/$person/collections", "peopleCollections")
            ?: route("GET", "/api/v4/members/$person/(answers|articles|followees|favlists|questions|column-contributions|following-favlists|following-questions|following-topic-contributions|following-columns)", "memberList")
            ?: route("GET", "/api/v4/v2/pins/$person/moments", "memberPins")
            ?: route("GET", "/api/v3/moments(?:/$person/activities)?", "moments")
            ?: route("GET", "/api/v4/(answers|pins)/$number/upvoters", "voters")
            ?: route("GET", "/api/v4/answers/$number/relationship", "answerRelationship")
            ?: route("GET", "/api/v4/me/sticker-groups", "stickerGroups")
            ?: route("GET", "/api/v4/sticker-groups/$person", "stickerGroup")
            ?: route("GET", "/api/v3/feed/topstory/recommend", "topstoryRead")
            ?: route("GET", "/api/v4/questions/$number/feeds", "questionFeedsRead")
            ?: route("GET", "/api/v4/(answers|articles|questions|pins)/$number", "contentDetail")
            ?: route("GET", "/api/v4/search/(hot_search|suggest)", "searchAssist")
            ?: route("GET", "/api/v5[.]1/topics/$number(?:/feeds/(top_activity/v2|essence/v2|timeline_activity/v2|top_question/v2|pin-hot|pin-new))?", "topicRead")
        "api.zhihu.com" -> route("GET|PUT", "/collections/contents/(answer|article)/$number", "contentCollections")
            ?: route("POST", "/comment_v5/(answers|articles|questions|pins)/$number/(comment|segment/comment)", "voiceCommentSend")
            ?: route("POST|DELETE", "/reaction/comments/$number/like", "notificationLike")
            ?: route("POST", "/emoji/(collect|user/remove|upload)", "emojiWrite")
            ?: route("GET", "/emoji/(user|library)/list", "emojiList")
            ?: route("GET", "/people/ats", "mentions")
            ?: route("GET", "/content/publish/parse_url", "parseLinks")
            ?: route("POST", "/content/publish/topics/recommend", "pinTopicSuggest")
            ?: route("POST", "/content/drafts", "pinDraft")
            ?: route("POST", "/questions", "questionCreate")
            ?: route("GET", "/questions/$number", "questionRelationship")
            ?: route("POST", "/articles/write", "articleCreate")
            ?: route("POST|PUT", "/articles/$number/draft", "articleDraft")
            ?: route("GET", "/people/$person(?:/(followers|profile/detail))?", "personRead")
            ?: route("GET", "/moments_v3", "mobileMoments")
            ?: route("GET", "/moments/recent", "recentFollow")
            ?: route("POST", "/moments/recent/read", "recentFollowRead")
            ?: route("GET", "/notifications/v3/message/v3", "notificationMessages")
            ?: route("GET", "/notifications/v3/timeline/entry/(like|comment|invite|favlist_me|follow)", "notificationTimeline")
            ?: route("POST", "/notifications/v3/timeline/entry/(like|comment|invite|favlist_me|follow)/actions/readall", "notificationReadAll")
            ?: route("GET", "/messages(?:/user/$person)?", "messagesRead")
            ?: route("GET", "/creators/question_route/notification_related/(recommended|hot)", "answerSuggestions")
            ?: route("POST", "/read_history/(set_switch|batch_del)", "historyWrite")
        else -> null
    }
}

internal fun webBusinessBody(data: HttpRequestData): String = when (val body = data.body) {
    EmptyContent -> ""
    is OutgoingContent.ByteArrayContent -> body.bytes().also {
        require(it.size <= 1024 * 1024) { "浏览器桥单次文本请求上限为 1 MiB" }
    }.decodeToString(throwOnInvalidSequence = true)
    else -> throw BrowserBridgeException(BrowserSessionStatus.Denied)
}

/** bytes 只用于完整上传操作；临时上传密钥、Cookie 与签名永远不经过此端口。 */
suspend fun ZhihuAccountClient.requestBrowserBusiness(
    operation: String,
    id: String = "",
    query: String = "",
    body: String = "",
    bytes: ByteArray? = null,
): Pair<Int, String> = coroutineScope {
    if (!accountStore.usesBrowserSession || !isCurrent) throw BrowserBridgeException(BrowserSessionStatus.Denied)
    val owner = load().profile?.id ?: throw BrowserBridgeException(BrowserSessionStatus.Guest)
    require(body.encodeToByteArray().size <= 1024 * 1024) { "浏览器桥单次文本请求上限为 1 MiB" }
    require(bytes == null || bytes.size in 1..32 * 1024 * 1024) { "浏览器桥单次上传上限为 32 MiB" }
    val pending = coroutineContext.job
    val subscription = httpClient().coroutineContext.job.invokeOnCompletion { pending.cancel() }
    try {
        suspendCancellableCoroutine { continuation ->
            val binary = bytes?.let { data -> makeBusinessBytes(data.size) { data[it].toInt() and 255 } }
            val handle = startBrowserBridge("business", operation, owner, id, query, body, binary) { kind, code, text ->
                if (!continuation.isActive) return@startBrowserBridge
                if (!isCurrent) {
                    continuation.cancel(CancellationException("账号客户端已关闭"))
                } else if (kind == "http") {
                    continuation.resume(code to text)
                } else if (kind == "uncertain") {
                    continuation.resumeWithException(BrowserMutationUncertain())
                } else if (kind == "challenge") {
                    this@requestBrowserBusiness.ownerVerificationForBusiness()
                    continuation.resumeWithException(BrowserVerificationRequired())
                } else if (kind == "cancelled") {
                    continuation.cancel(CancellationException("浏览器请求已取消；已提交写入不会自动重试"))
                } else {
                    val status = when (kind) {
                        "guest" -> BrowserSessionStatus.Guest
                        "expired" -> BrowserSessionStatus.Expired
                        "missing" -> BrowserSessionStatus.MissingBridge
                        "denied" -> BrowserSessionStatus.Denied
                        else -> BrowserSessionStatus.Unavailable
                    }
                    continuation.resumeWithException(BrowserBusinessRequestRejected(BrowserBridgeException(status).message.orEmpty()))
                }
            }
            continuation.invokeOnCancellation { handle.abort() }
        }
    } finally {
        subscription.dispose()
    }
}

@JsFun("(size, at) => { const bytes = new Uint8Array(size); for (let i = 0; i < size; i++) bytes[i] = at(i); return bytes.buffer; }")
private external fun makeBusinessBytes(size: Int, at: (Int) -> Int): BrowserBridgeBytes
