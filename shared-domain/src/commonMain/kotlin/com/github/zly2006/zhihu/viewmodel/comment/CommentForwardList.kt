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

package com.github.zly2006.zhihu.viewmodel.comment

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.zly2006.zhihu.account.ZhihuAccountClient
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.i18n.DomainStrings
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.util.DisplayFailure
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.request
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.util.AttributeKey
import io.ktor.util.collections.ConcurrentMap
import io.ktor.util.toMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class CommentForward(
    val id: String,
    val router: String? = null,
    val title: String? = null,
    val summary: String? = null,
    val author: Author? = null,
    val contentEndInfo: EndInfo? = null,
    val images: List<Image>? = null,
    val video: Video? = null,
    val comments: Comments? = null,
    val reaction: Reaction? = null,
) {
    @Serializable
    data class Author(
        val name: String? = null,
        val avatarUrl: String? = null,
        val id: String? = null,
        val urlToken: String? = null,
    ) {
        val image: String?
            get() = avatarUrl?.let { value ->
                if (value.isNotEmpty() && value.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' }) {
                    "https://picx.zhimg.com/${value}_l.jpg"
                } else {
                    mediaUrl(value)
                }
            }
    }

    @Serializable
    data class EndInfo(
        val publishTime: Long? = null,
        val ipInfo: String? = null,
    )

    @Serializable
    data class Image(
        val url: String? = null,
        val width: Int? = null,
        val height: Int? = null,
    ) {
        val imageUrl: String? get() = url?.let(::mediaUrl)
    }

    @Serializable
    data class Video(
        val coverUrl: String? = null,
        val width: Int? = null,
        val height: Int? = null,
    ) {
        val imageUrl: String? get() = coverUrl?.let(::mediaUrl)
    }

    @Serializable
    data class Reaction(
        val statistics: Statistics? = null,
        val relation: Relation? = null,
    ) {
        @Serializable
        data class Statistics(
            val upVoteCount: Int? = null,
        )

        @Serializable
        data class Relation(
            val vote: String? = null,
        )
    }

    @Serializable
    data class Comments(
        val count: Int? = null,
        val list: List<Preview>? = null,
    )

    @Serializable
    data class Preview(
        val id: String? = null,
        val content: String? = null,
        val author: Author? = null,
        val authorTag: JsonElement? = null,
        val commentTag: List<Tag>? = null,
        val createdTime: Long? = null,
        val liked: Boolean? = null,
        val likeCount: Int? = null,
    ) {
        /** 转发预览的作者标签既可能是单个对象，也可能是数组。 */
        val authorLabels: List<String>
            get() = (authorTag as? JsonArray ?: listOfNotNull(authorTag)).mapNotNull {
                ((it as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
            }

        @Serializable
        data class Tag(
            val type: String? = null,
            val text: String? = null,
        )
    }

    /** 回复始终归属于条目 ID，不能用详情 router 中的 ID 替代。 */
    val commentPin: Pin? get() = pinId(id)?.let { Pin(it) }

    /** 仅已见证的想法链接可进入原生页；条目 ID 与导航目标彼此独立。 */
    val destination: Pin?
        get() {
            val prefix = "https://www.zhihu.com/pin/"
            if (router == null || !router.startsWith(prefix)) return null
            return pinId(router.substring(prefix.length))?.let { Pin(it) }
        }

    companion object {
        private fun pinId(token: String) = token.takeIf { it.all { char -> char in '0'..'9' } }?.toLongOrNull()?.takeIf { it > 0 }

        /** 图片不借用账号凭据；只接受无用户信息的知乎图片 CDN 地址。 */
        private fun mediaUrl(value: String): String? {
            if (!value.startsWith("https://") && !value.startsWith("http://")) return null
            if (value.any { it.isWhitespace() || it.isISOControl() || it == '\\' }) return null
            val url = runCatching { Url(value) }.getOrNull() ?: return null
            return value.takeIf {
                url.port == url.protocol.defaultPort &&
                    url.user == null &&
                    url.password == null &&
                    (url.host == "zhimg.com" || url.host.endsWith(".zhimg.com"))
            }
        }
    }
}

@Serializable
private data class ForwardPage(
    val data: List<CommentForward>,
    val paging: Paging,
) {
    @Serializable
    data class Paging(
        val isEnd: Boolean,
        val next: String? = null,
    )
}

@Serializable
private data class ForwardTabResponse(
    val data: ForwardTab,
)

@Serializable
data class ForwardTab(
    val isShowTab: Boolean,
    val count: Int,
)

private const val FORWARD_PATH = "/unify-consumption/forward/list"

/** 缓存归当前客户端；服务端独立提供页签可见性和总数，不用列表行数推断。 */
class CommentForwardList private constructor(
    private val account: ZhihuAccountClient,
    private val contentType: String,
    private val contentToken: String,
) {
    private val firstUrl = "https://api.zhihu.com$FORWARD_PATH?content_type=$contentType&content_token=$contentToken"
    private var nextUrl: String? by mutableStateOf(firstUrl)
    private val pages = Mutex()
    private val tabRequest = Mutex()
    var tab by mutableStateOf<ForwardTab?>(null)
        private set
    private var tabFailure by mutableStateOf<(() -> String)?>(null)
    val tabError: String? get() = tabFailure?.invoke()
    private val visited = mutableSetOf<Map<String, List<String>>>()
    private val ids = mutableSetOf<String>()

    var items by mutableStateOf(emptyList<CommentForward>())
        private set
    var isLoading by mutableStateOf(false)
        private set
    private var failure by mutableStateOf<(() -> String)?>(null)
    val errorMessage: String? get() = failure?.invoke()
    val isEnd: Boolean get() = nextUrl == null

    /** 普通面板只读计数；isShowTab=true 且 count=0 仍是有效的空转发页签。 */
    suspend fun loadTab(refresh: Boolean = false) = tabRequest.withLock {
        if (!account.isCurrent || (!refresh && (tab != null || tabFailure != null))) return@withLock
        tabFailure = null
        try {
            val url = "https://api.zhihu.com/unify-consumption/forward/count?content_type=$contentType&content_token=$contentToken"
            val result = ZhihuJson.decodeJson<ForwardTabResponse>(fetch(url)).data
            currentCoroutineContext().ensureActive()
            require(result.count >= 0)
            if (tab != result) {
                pages.withLock {
                    if (account.isCurrent) {
                        ids.clear()
                        visited.clear()
                        items = emptyList()
                        nextUrl = firstUrl
                        failure = null
                        tab = result
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (account.isCurrent) {
                tab = null
                tabFailure = { (error as? DisplayFailure)?.message ?: DomainStrings.forwardUnreadable }
            }
        }
    }

    suspend fun loadFirstPage() = pages.withLock {
        if (tab?.isShowTab == true && items.isEmpty() && errorMessage == null) readPage()
    }

    suspend fun loadMore() {
        val requested = nextUrl ?: return
        pages.withLock { if (nextUrl == requested) readPage() }
    }

    /** 空的非终页不能证明没有转发；在面板协程内续读到首条或终页，取消不算失败。 */
    private suspend fun readPage() {
        if (isEnd || tab?.isShowTab != true || !account.isCurrent) return
        isLoading = true
        failure = null
        try {
            while (account.isCurrent) {
                currentCoroutineContext().ensureActive()
                val url = nextUrl ?: return
                val json = fetch(url)
                currentCoroutineContext().ensureActive()
                if (!account.isCurrent) return
                val page = ZhihuJson.decodeJson<ForwardPage>(json)
                require(page.data.all { it.id.isNotBlank() })
                val next = page.paging.next.takeUnless { page.paging.isEnd }
                if (!page.paging.isEnd) {
                    if (next.isNullOrBlank()) throw DisplayFailure { DomainStrings.forwardMissingNext }
                    val parsed = Url(next)
                    val query = parsed.parameters.toMap()
                    val validPage =
                        next.startsWith("https://") &&
                            parsed.protocol == URLProtocol.HTTPS &&
                            parsed.host == "api.zhihu.com" &&
                            parsed.port == 443 &&
                            parsed.user == null &&
                            parsed.password == null &&
                            parsed.encodedPath == FORWARD_PATH &&
                            '#' !in next &&
                            next.none { it.isWhitespace() || it.isISOControl() || it == '\\' } &&
                            query["content_type"] == listOf(contentType) &&
                            query["content_token"] == listOf(contentToken) &&
                            query["cursor"]?.size == 1 &&
                            query["delivered_count"]?.size == 1
                    if (!validPage) throw DisplayFailure { DomainStrings.forwardInvalidNext }
                    /** 游标是不透明的；参数换序不是前进，也不能从 delivered_count 推断数量关系。 */
                    if (!visited.add(query)) throw DisplayFailure { DomainStrings.forwardStalled }
                }
                val added = page.data.filter { ids.add(it.id) }
                nextUrl = next
                if (added.isNotEmpty()) {
                    items = items + added
                    return
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (account.isCurrent) failure = { (error as? IllegalStateException)?.message ?: DomainStrings.forwardUnreadable }
        } finally {
            isLoading = false
        }
    }

    private suspend fun fetch(url: String) = account.withAuthenticatedClient { client, _ ->
        val response = client.get(url)
        val status = response.status
        if (!status.isSuccess()) throw DisplayFailure { DomainStrings.forwardLoadHttp(status.value) }
        if (response.request.url != Url(url)) throw DisplayFailure { DomainStrings.forwardTargetMismatch }
        response.body<JsonObject>().also { json ->
            if (json["error"] != null && json["error"] != JsonNull) throw DisplayFailure { DomainStrings.forwardLoadFailed }
        }
    }

    companion object {
        private val lists = AttributeKey<ConcurrentMap<Pair<String, String>, CommentForwardList>>("ZhihuContentForwardLists")

        fun forContent(content: NavDestination, account: ZhihuAccountClient): CommentForwardList? {
            val (type, id) = when (content) {
                is Article -> content.type.toString() to content.id
                is Pin -> "pin" to content.id
                else -> return null
            }
            if (id <= 0) return null
            val token = id.toString()
            return account.accountStore.withSessionLock {
                if (!account.isCurrent) return@withSessionLock null
                val client = account.httpClient()
                client.attributes
                    .computeIfAbsent(lists) { ConcurrentMap() }
                    .computeIfAbsent(type to token) { CommentForwardList(account, type, token) }
            }
        }
    }
}
