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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.github.zly2006.zhihu.account.ZhihuRequestProfile
import com.github.zly2006.zhihu.data.MOBILE_NOTIFICATION_MESSAGE_URL
import com.github.zly2006.zhihu.data.MobileNotificationAuthor
import com.github.zly2006.zhihu.data.MobileNotificationColumnHead
import com.github.zly2006.zhihu.data.MobileNotificationMessageOverview
import com.github.zly2006.zhihu.data.MobileNotificationTimelineItem
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.ZhihuPaging
import com.github.zly2006.zhihu.data.ZhihuPrivateMessage
import com.github.zly2006.zhihu.data.ZhihuPrivateMessagePage
import com.github.zly2006.zhihu.notification.NotificationSettingsStore
import com.github.zly2006.zhihu.notification.matchNotificationType
import com.github.zly2006.zhihu.util.Log
import com.github.zly2006.zhihu.util.ZhihuMessageBodyEncryptor
import com.github.zly2006.zhihu.util.jsonObject
import com.github.zly2006.zhihu.util.signZhihuMobileRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KType
import kotlin.reflect.typeOf

interface NotificationSettingsEnvironment {
    val notificationSettingsStore: NotificationSettingsStore
}

interface NotificationEnvironment :
    PaginationEnvironment,
    NotificationSettingsEnvironment

enum class MobileNotificationCategory(
    val entryName: String,
    val detailTitle: String,
) {
    Comment("comment", "评论转发@"),
    Like("like", "赞同喜欢"),
    Favorite("favlist_me", "收藏了我"),
    Follow("follow", "关注订阅"),
    ;

    val readAllUrl: String
        get() = "$MOBILE_NOTIFICATION_TIMELINE_URL/$entryName/actions/readall"
}

abstract class NotificationPaginationViewModel<T : Any>(
    dataType: KType,
) : PaginationViewModel<T>(dataType) {
    private var accountClient: HttpClient? = null

    protected fun useAccount(environment: MobileHomeFeedEnvironment): HttpClient {
        val client = environment.mobileHomeFeedHttpClient()
        if (accountClient !== client) {
            val changed = accountClient != null
            accountClient = client
            if (changed) {
                resetForRefresh()
                allData.clear()
                debugData.clear()
                onAccountChanged()
            }
        }
        return client
    }

    protected open fun onAccountChanged() = Unit

    protected fun isCurrentAccount(environment: MobileHomeFeedEnvironment, client: HttpClient): Boolean =
        accountClient === client && runCatching { environment.mobileHomeFeedHttpClient() }.getOrNull() === client

    protected suspend fun requestForAccount(
        environment: MobileHomeFeedEnvironment,
        client: HttpClient,
        url: String,
        method: HttpMethod = HttpMethod.Get,
        block: suspend HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse {
        val owner = environment.accountClient
        check(isCurrentAccount(environment, client)) { "账号已切换" }
        if (owner != null) {
            check(owner.httpClient(ZhihuRequestProfile.Mobile) === client) { "账号已切换" }
            return owner.request(url, method, ZhihuRequestProfile.Mobile, block = block)
        }
        return client.request(url) {
            this.method = method
            block()
        }
    }

    override fun loadMore(environment: PaginationEnvironment) {
        useAccount(environment)
        super.loadMore(environment)
    }

    override fun refresh(environment: PaginationEnvironment) {
        useAccount(environment)
        super.refresh(environment)
    }

    fun loadFor(environment: PaginationEnvironment) {
        val changed = accountClient !== environment.mobileHomeFeedHttpClient()
        useAccount(environment)
        if (changed || allData.isEmpty()) refresh(environment)
    }
}

class NotificationViewModel :
    NotificationPaginationViewModel<MobileNotificationTimelineItem>(
        dataType = typeOf<MobileNotificationTimelineItem>(),
    ) {
    override val initialUrl = "$MOBILE_NOTIFICATION_MESSAGE_URL?limit=20"
    override val include = ""

    val categoryUnreadCounts = SnapshotStateMap<MobileNotificationCategory, Int>()

    var invitation: MobileNotificationColumnHead? by mutableStateOf(null)
        private set

    var unreadCount: Int by mutableIntStateOf(0)
        private set

    private var refreshingFirstPage = false

    init {
        MobileNotificationCategory.entries.forEach {
            categoryUnreadCounts[it] = 0
        }
    }

    override fun onAccountChanged() {
        categoryUnreadCounts.keys.forEach { categoryUnreadCounts[it] = 0 }
        invitation = null
        unreadCount = 0
        refreshingFirstPage = false
    }

    override fun refresh(environment: PaginationEnvironment) {
        useAccount(environment)
        if (isLoading) return
        errorMessage = null
        lastPaging = null
        refreshingFirstPage = true
        loadMore(environment)
    }

    override suspend fun fetchFeeds(environment: PaginationEnvironment) {
        val client = useAccount(environment)
        val generation = pagingGeneration
        try {
            val url = lastPaging?.next ?: initialUrl
            val json = requestForAccount(environment, client, url.replace("http://", "https://")).jsonObject()
            currentCoroutineContext().ensureActive()
            if (generation != pagingGeneration || !isCurrentAccount(environment, client)) return
            val page = ZhihuJson.decodeJson<MobileNotificationMessageOverview>(json)
            val rawData = json["data"]?.jsonArray ?: JsonArray(emptyList())

            if (lastPaging == null) {
                invitation = page.columnHead.firstOrNull()
                MobileNotificationCategory.entries.forEach { category ->
                    categoryUnreadCounts[category] = page.head
                        .firstOrNull { it.detailTitle == category.detailTitle }
                        ?.unreadCount ?: 0
                }
                unreadCount = categoryUnreadCounts.values.sum()
            }

            val pageData = page.data
                .filter { it.type != "empty" }
                .distinctBy { it.stableId }
            if (refreshingFirstPage) {
                val refreshedIds = pageData.mapTo(mutableSetOf()) { it.stableId }
                val retainedTail = allData.filter { it.stableId !in refreshedIds }
                allData.clear()
                allData.addAll(pageData)
                allData.addAll(retainedTail)
                debugData.clear()
                debugData.addAll(rawData)
                isRefreshPending = false
            } else {
                val existingIds = allData.mapTo(mutableSetOf()) { it.stableId }
                processResponse(
                    environment,
                    pageData.filter { existingIds.add(it.stableId) },
                    rawData,
                )
            }
            lastPaging = page.paging ?: ZhihuPaging(isEnd = true, next = "")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (generation == pagingGeneration && isCurrentAccount(environment, client)) {
                errorHandle(e)
                environment.handleFetchFailure(this::class.simpleName, e)
            }
        } finally {
            if (generation == pagingGeneration && isCurrentAccount(environment, client)) {
                refreshingFirstPage = false
                isLoading = false
            }
        }
    }

    suspend fun markAllAsRead(environment: MobileHomeFeedEnvironment): Boolean {
        val client = useAccount(environment)
        var succeeded = true
        for (category in MobileNotificationCategory.entries) {
            if (!isCurrentAccount(environment, client)) return false
            if ((categoryUnreadCounts[category] ?: 0) <= 0) continue
            try {
                val response = requestForAccount(environment, client, category.readAllUrl, HttpMethod.Post)
                currentCoroutineContext().ensureActive()
                if (!isCurrentAccount(environment, client)) return false
                if (!response.status.isSuccess()) {
                    Log.e("NotificationViewModel", "Failed to mark ${category.entryName} notifications as read: ${response.status}")
                    succeeded = false
                } else {
                    categoryUnreadCounts[category] = 0
                    unreadCount = categoryUnreadCounts.values.sum()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (!isCurrentAccount(environment, client)) return false
                Log.e("NotificationViewModel", "Failed to mark ${category.entryName} notifications as read", e)
                succeeded = false
            }
        }
        return succeeded
    }
}

class NotificationTimelineViewModel(
    val entryName: String,
) : NotificationPaginationViewModel<MobileNotificationTimelineItem>(
        dataType = typeOf<MobileNotificationTimelineItem>(),
    ) {
    override val initialUrl = buildString {
        append("$MOBILE_NOTIFICATION_TIMELINE_URL/$entryName?")
        if (entryName == INVITATION_ENTRY_NAME) {
            append("invite_with_time_slice=1&")
        }
        append("limit=20")
    }
    override val include = ""
    private var markedAsRead = false

    override fun onAccountChanged() {
        markedAsRead = false
    }

    val readAllUrl: String
        get() = "$MOBILE_NOTIFICATION_TIMELINE_URL/$entryName/actions/readall"

    override suspend fun fetchFeeds(environment: PaginationEnvironment) {
        val client = useAccount(environment)
        val generation = pagingGeneration
        try {
            val notificationEnvironment = environment as? NotificationEnvironment
                ?: error("NotificationSettingsStore is required for notification pagination")
            val url = lastPaging?.next ?: initialUrl
            val json = requestForAccount(environment, client, url.replace("http://", "https://")).jsonObject()
            currentCoroutineContext().ensureActive()
            if (generation != pagingGeneration || !isCurrentAccount(environment, client)) return
            val rawData = json["data"]?.jsonArray ?: JsonArray(emptyList())
            val data = rawData.mapNotNull {
                try {
                    ZhihuJson.decodeJson<MobileNotificationTimelineItem>(it)
                } catch (e: Exception) {
                    if (shouldLogDecodeFailures) {
                        environment.logDecodeFailure(this::class.simpleName, it, e)
                    }
                    null
                }
            }

            val existingIds = if (isRefreshPending) mutableSetOf() else allData.mapTo(mutableSetOf()) { it.stableId }
            processResponse(
                environment,
                data.filter { existingIds.add(it.stableId) },
                rawData,
            )
            lastPaging = json["paging"]
                ?.let { ZhihuJson.decodeJson<ZhihuPaging>(it) }
                ?: ZhihuPaging(isEnd = true, next = "")

            if (!markedAsRead && notificationEnvironment.notificationSettingsStore.getAutoMarkAsReadEnabled()) {
                try {
                    val response = requestForAccount(environment, client, readAllUrl, HttpMethod.Post)
                    currentCoroutineContext().ensureActive()
                    if (generation != pagingGeneration || !isCurrentAccount(environment, client)) return
                    markedAsRead = response.status.isSuccess()
                    if (!markedAsRead) Log.e("NotificationTimelineViewModel", "Failed to mark $entryName notifications as read: ${response.status}")
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    if (generation == pagingGeneration && isCurrentAccount(environment, client)) {
                        Log.e("NotificationTimelineViewModel", "Failed to mark $entryName notifications as read", e)
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (generation == pagingGeneration && isCurrentAccount(environment, client)) {
                errorHandle(e)
                environment.handleFetchFailure(this::class.simpleName, e)
            }
        }
    }

    fun shouldShowNotification(
        settingsStore: NotificationSettingsStore,
        notification: MobileNotificationTimelineItem,
    ): Boolean {
        val content = notification.content ?: return true
        val verb = listOf(content.title, content.subTitle, content.text)
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        val type = matchNotificationType(verb)
        return type == null || settingsStore.getDisplayInAppEnabled(type)
    }
}

class PrivateMessageViewModel(
    private val peerId: String,
) : NotificationPaginationViewModel<ZhihuPrivateMessage>(
        dataType = typeOf<ZhihuPrivateMessage>(),
    ) {
    override val initialUrl = "$MOBILE_PRIVATE_MESSAGE_URL?limit=20&sender_id=$peerId"
    override val include = ""

    var peer: MobileNotificationAuthor? by mutableStateOf(null)
        private set

    var isSending by mutableStateOf(false)
        private set

    private var isRefreshingLatest = false
    private var refreshGeneration = 0
    private var refreshingFirstPage = false
    private var retainedHistoryPaging: ZhihuPaging? = null

    // 发送确认只证明这一条消息存在，不能作为消息列表已连续同步的边界。
    private val pendingSentIds = mutableSetOf<String>()

    override fun onAccountChanged() {
        peer = null
        isSending = false
        isRefreshingLatest = false
        refreshGeneration++
        refreshingFirstPage = false
        retainedHistoryPaging = null
        pendingSentIds.clear()
    }

    override fun refresh(environment: PaginationEnvironment) {
        useAccount(environment)
        refreshGeneration += 1
        isRefreshingLatest = false
        if (!isRefreshPending) retainedHistoryPaging = lastPaging
        refreshingFirstPage = true
        super.refresh(environment)
    }

    suspend fun sendMessage(
        content: String,
        environment: MobileHomeFeedEnvironment,
    ): Boolean {
        val client = useAccount(environment)
        val cookies = environment.authenticatedCookies()
        if (content.isBlank() || isSending || !isCurrentAccount(environment, client)) return false

        isSending = true
        errorMessage = null
        return try {
            val encryptedBody = run {
                val form = Parameters
                    .build {
                        append("receiver_id", peerId)
                        append("content", content)
                        append("content_type", "0")
                        append("source_type", "message_list")
                    }.formUrlEncode()
                ZhihuMessageBodyEncryptor.encrypt(form)
            }
            val response = requestForAccount(environment, client, MOBILE_PRIVATE_MESSAGE_URL, HttpMethod.Post) {
                contentType(ContentType.Application.FormUrlEncoded)
                header("X-Zse-93", "101_1_1.0")
                signZhihuMobileRequest(
                    url = MOBILE_PRIVATE_MESSAGE_URL,
                    cookies = cookies,
                    body = encryptedBody,
                )
                setBody(encryptedBody)
            }
            currentCoroutineContext().ensureActive()
            if (!isCurrentAccount(environment, client)) return false
            if (!response.status.isSuccess()) {
                val responseText = response.bodyAsText()
                currentCoroutineContext().ensureActive()
                if (!isCurrentAccount(environment, client)) return false
                errorMessage = runCatching {
                    ZhihuJson.json
                        .parseToJsonElement(responseText)
                        .jsonObject["error"]
                        ?.jsonObject
                        ?.get("message")
                        ?.jsonPrimitive
                        ?.content
                }.getOrNull() ?: "发送失败（${response.status.value}）"
                Log.e("PrivateMessageViewModel", "Failed to send private message: ${response.status}, $errorMessage")
                false
            } else {
                val message = ZhihuJson.decodeJson<ZhihuPrivateMessage>(
                    ZhihuJson.json.parseToJsonElement(response.bodyAsText()),
                )
                currentCoroutineContext().ensureActive()
                if (!isCurrentAccount(environment, client)) return false
                require(message.id.isNotBlank()) { "发送未获服务端确认" }
                if (allData.none { it.stableId == message.stableId }) {
                    allData.add(0, message)
                    pendingSentIds.add(message.stableId)
                }
                true
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!isCurrentAccount(environment, client)) return false
            Log.e("PrivateMessageViewModel", "Failed to send private message", e)
            errorMessage = e.message ?: "发送失败"
            false
        } finally {
            if (isCurrentAccount(environment, client)) isSending = false
        }
    }

    /** 轮询保留历史分页，失败不打断当前会话。 */
    suspend fun refreshLatest(environment: MobileHomeFeedEnvironment) {
        val client = useAccount(environment)
        if (isLoading || isSending || isRefreshingLatest) return

        isRefreshingLatest = true
        val generation = refreshGeneration
        try {
            val knownIds = allData.mapTo(mutableSetOf()) { it.stableId } - pendingSentIds
            val incoming = mutableListOf<ZhihuPrivateMessage>()
            val visitedUrls = mutableSetOf<String>()
            var pageUrl = initialUrl
            var firstPagePaging: ZhihuPaging? = null

            while (true) {
                val json = requestForAccount(environment, client, pageUrl.replace("http://", "https://"))
                    .jsonObject()
                currentCoroutineContext().ensureActive()
                if (generation != refreshGeneration || !isCurrentAccount(environment, client)) return
                val page = ZhihuJson.decodeJson<ZhihuPrivateMessagePage>(json)
                if (firstPagePaging == null) firstPagePaging = page.paging
                incoming += page.data

                val overlapsKnownMessage = page.data.any { it.stableId in knownIds }
                val nextUrl = page.paging.next
                if (knownIds.isEmpty() || overlapsKnownMessage || page.paging.isEnd) {
                    break
                }
                if (nextUrl.isBlank() || !visitedUrls.add(nextUrl)) return
                pageUrl = nextUrl
            }

            var olderMessageId: String? = null
            incoming
                .distinctBy(ZhihuPrivateMessage::stableId)
                .asReversed()
                .forEach { message ->
                    val existingIndex = allData.indexOfFirst { it.stableId == message.stableId }
                    val anchorIndex = olderMessageId?.let { id -> allData.indexOfFirst { it.stableId == id } }
                    if (existingIndex >= 0 && (anchorIndex == null || existingIndex == anchorIndex - 1)) {
                        if (allData[existingIndex] != message) allData[existingIndex] = message
                    } else {
                        if (existingIndex >= 0) allData.removeAt(existingIndex)
                        // 同秒消息也按服务端顺序插到更旧的消息前，保留未出现在本轮响应中的发送消息。
                        val insertionIndex = olderMessageId?.let { id -> allData.indexOfFirst { it.stableId == id } }
                            ?: allData.indexOfFirst { it.createdTime < message.createdTime }.takeIf { it >= 0 }
                            ?: allData.size
                        allData.add(insertionIndex, message)
                    }
                    olderMessageId = message.stableId
                }
            pendingSentIds.removeAll(incoming.map { it.stableId }.toSet())

            if (knownIds.isEmpty()) {
                lastPaging = firstPagePaging
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (generation == refreshGeneration && isCurrentAccount(environment, client)) {
                Log.e("PrivateMessageViewModel", "Failed to refresh latest private messages", e)
            }
        } finally {
            if (generation == refreshGeneration && isCurrentAccount(environment, client)) isRefreshingLatest = false
        }
    }

    override suspend fun fetchFeeds(environment: PaginationEnvironment) {
        val client = useAccount(environment)
        val generation = pagingGeneration
        val synchronizedIds = if (refreshingFirstPage) {
            allData.mapTo(mutableSetOf()) { it.stableId } - pendingSentIds
        } else {
            emptySet()
        }
        try {
            coroutineScope {
                val peerRequest = if (peer == null) {
                    async {
                        requestForAccount(environment, client, "$MOBILE_PRIVATE_MESSAGE_USER_URL/$peerId")
                            .jsonObject()
                    }
                } else {
                    null
                }
                val pageRequest = async {
                    requestForAccount(environment, client, (lastPaging?.next ?: initialUrl).replace("http://", "https://"))
                        .jsonObject()
                }

                val json = pageRequest.await()
                currentCoroutineContext().ensureActive()
                if (generation != pagingGeneration || !isCurrentAccount(environment, client)) return@coroutineScope
                val page = ZhihuJson.decodeJson<ZhihuPrivateMessagePage>(json)
                val rawData = json["data"]?.jsonArray ?: JsonArray(emptyList())
                if (refreshingFirstPage) {
                    val refreshData = page.data.toMutableList()
                    val refreshRawData = rawData.toMutableList()
                    val visitedUrls = mutableSetOf(initialUrl)
                    var refreshPage = page
                    /** 补齐到旧连续边界后，刷新首页才可作为轮询的同步边界。 */
                    while ((synchronizedIds.isNotEmpty() || retainedHistoryPaging != null) &&
                        !refreshPage.paging.isEnd &&
                        refreshPage.data.none { it.stableId in synchronizedIds }
                    ) {
                        val nextUrl = refreshPage.paging.next
                        check(nextUrl.isNotBlank() && visitedUrls.add(nextUrl)) { "私信分页未补齐，请重试" }
                        val nextJson = requestForAccount(environment, client, nextUrl.replace("http://", "https://")).jsonObject()
                        currentCoroutineContext().ensureActive()
                        if (generation != pagingGeneration || !isCurrentAccount(environment, client)) return@coroutineScope
                        refreshPage = ZhihuJson.decodeJson<ZhihuPrivateMessagePage>(nextJson)
                        refreshData.addAll(refreshPage.data)
                        refreshRawData.addAll(nextJson["data"]?.jsonArray ?: JsonArray(emptyList()))
                    }
                    val pageData = refreshData.distinctBy { it.stableId }
                    val refreshedIds = pageData.mapTo(mutableSetOf()) { it.stableId }
                    val retainedTail = allData.filter { it.stableId !in refreshedIds }
                    val pendingMessages = retainedTail.filter { it.stableId in pendingSentIds }
                    allData.clear()
                    allData.addAll(pendingMessages)
                    allData.addAll(pageData)
                    allData.addAll(retainedTail.filter { it.stableId !in pendingSentIds })
                    debugData.clear()
                    debugData.addAll(refreshRawData)
                    isRefreshPending = false
                    lastPaging = retainedHistoryPaging ?: refreshPage.paging
                    retainedHistoryPaging = null
                    refreshingFirstPage = false
                    pendingSentIds.removeAll(refreshData.map { it.stableId }.toSet())
                } else {
                    val existingIds = allData.mapTo(mutableSetOf()) { it.stableId }
                    processResponse(environment, page.data.filter { existingIds.add(it.stableId) }, rawData)
                    lastPaging = page.paging
                    pendingSentIds.removeAll(page.data.map { it.stableId }.toSet())
                }

                peerRequest?.let { request ->
                    runCatching {
                        val response = request.await()
                        currentCoroutineContext().ensureActive()
                        if (generation == pagingGeneration && isCurrentAccount(environment, client)) {
                            peer = ZhihuJson.decodeJson<MobileNotificationAuthor>(response)
                        }
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        if (generation == pagingGeneration && isCurrentAccount(environment, client)) {
                            Log.e("PrivateMessageViewModel", "Failed to load private-message peer", error)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (generation == pagingGeneration && isCurrentAccount(environment, client)) {
                errorHandle(e)
                environment.handleFetchFailure(this::class.simpleName, e)
            }
        }
    }
}

private const val INVITATION_ENTRY_NAME = "invite"
private const val MOBILE_NOTIFICATION_TIMELINE_URL = "https://api.zhihu.com/notifications/v3/timeline/entry"
private const val MOBILE_PRIVATE_MESSAGE_URL = "https://api.zhihu.com/messages"
private const val MOBILE_PRIVATE_MESSAGE_USER_URL = "https://api.zhihu.com/messages/user"
