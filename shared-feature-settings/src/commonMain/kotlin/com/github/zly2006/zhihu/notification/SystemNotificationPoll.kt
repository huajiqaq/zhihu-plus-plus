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

package com.github.zly2006.zhihu.notification

import com.github.zly2006.zhihu.data.MobileNotificationTimelineItem
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.ZhihuPaging
import com.github.zly2006.zhihu.i18n.NotificationsStrings
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.CommentHolder
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.util.raiseForStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class SystemNotificationCheckpoint(
    val seenByEntry: Map<String, Set<String>> = emptyMap(),
    val baselinedTypes: Set<NotificationType> = emptySet(),
)

@Serializable
internal data class SystemNotificationEvent(
    val key: String,
    val type: NotificationType,
    val destination: NavDestination,
)

@Serializable
internal data class SystemNotificationPage(
    val data: List<MobileNotificationTimelineItem>,
    val paging: ZhihuPaging,
)

internal data class SystemNotificationPoll(
    val checkpoint: SystemNotificationCheckpoint,
    val events: List<SystemNotificationEvent>,
)

internal val NotificationType.timelineEntry: String
    get() = when (this) {
        NotificationType.LIKE_ANSWER, NotificationType.LIKE_COMMENT -> "like"
        NotificationType.REPLY_COMMENT -> "comment"
        NotificationType.INVITE_ANSWER -> "invite"
    }

/** 分页链接必须仍是同一个只读时间线，不能把账号客户端带到任意主机或写操作路径。 */
internal fun systemNotificationPageUrl(entry: String, candidate: String? = null): String {
    require(entry in setOf("like", "comment", "invite"))
    val path = "/notifications/v3/timeline/entry/$entry"
    val initial = "https://api.zhihu.com$path?" +
        (if (entry == "invite") "invite_with_time_slice=1&" else "") + "limit=20"
    val url = Url(candidate ?: initial)
    require(
        url.host == "api.zhihu.com" &&
            url.encodedPath == path &&
            url.user.isNullOrEmpty() &&
            url.password.isNullOrEmpty() &&
            url.fragment.isEmpty() &&
            ((url.protocol == URLProtocol.HTTPS && url.port == 443) || (url.protocol == URLProtocol.HTTP && url.port == 80)),
    ) { NotificationsStrings.pageUntrusted }
    return url.toString().replaceFirst("http://", "https://")
}

/** 只借用现有 Mobile transport；不刷新票据、不创建客户端、不调用会自动标读的页面 ViewModel。 */
internal suspend fun loadSystemNotificationPage(client: HttpClient, url: String): SystemNotificationPage =
    ZhihuJson.decodeJson(client.get(url).raiseForStatus().body<JsonObject>())

/** 所有已启用时间线连续同步成功后才返回新检查点；异常、循环分页和取消均不产生部分进度。 */
internal suspend fun pollSystemNotifications(
    checkpoint: SystemNotificationCheckpoint,
    enabledTypes: Set<NotificationType>,
    load: suspend (String) -> SystemNotificationPage,
): SystemNotificationPoll {
    val seen = checkpoint.seenByEntry.toMutableMap()
    val events = linkedMapOf<String, SystemNotificationEvent>()
    for (entry in enabledTypes.map { it.timelineEntry }.distinct()) {
        val known = checkpoint.seenByEntry[entry].orEmpty()
        val fetched = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        var url = systemNotificationPageUrl(entry)
        while (true) {
            currentCoroutineContext().ensureActive()
            check(visited.add(url)) { NotificationsStrings.pageBoundaryMissing }
            val page = load(url)
            currentCoroutineContext().ensureActive()
            var overlaps = false
            for (item in page.data) {
                val id = item.uniqueId.ifBlank { item.id }
                if (id.isBlank()) continue
                if (id in known) overlaps = true
                if (!fetched.add(id) || id in known || !item.isUnread) continue
                val event = systemNotificationEvent(entry, item) ?: continue
                if (event.type in enabledTypes && event.type in checkpoint.baselinedTypes) {
                    events[event.key] = event
                }
            }
            if (overlaps || page.paging.isEnd) break
            check(page.paging.next.isNotBlank()) { NotificationsStrings.pageNextMissing }
            url = systemNotificationPageUrl(entry, page.paging.next)
        }
        seen[entry] = known + fetched
    }
    return SystemNotificationPoll(
        checkpoint = SystemNotificationCheckpoint(seen, checkpoint.baselinedTypes + enabledTypes),
        events = events.values.toList(),
    )
}

/** 仅支持既有四种 opt-in；正文链接不借用引用、头像或来源链接作猜测性替代。 */
internal fun systemNotificationEvent(entry: String, item: MobileNotificationTimelineItem): SystemNotificationEvent? {
    val id = item.uniqueId.ifBlank { item.id }.takeIf(String::isNotBlank) ?: return null
    if (item.type == "message" || item.type == "empty" || item.content?.isDeleted == true) return null
    val type = if (entry == "invite") {
        NotificationType.INVITE_ANSWER
    } else {
        val content = item.content ?: return null
        listOf(content.subTitle, content.title, content.text).firstNotNullOfOrNull { matchNotificationType(it.trim()) }
            ?: return null
    }
    if (type.timelineEntry != entry) return null
    val destination = when (type) {
        NotificationType.INVITE_ANSWER -> {
            val target = item.target?.takeIf { it.type == "question" && item.targetSource?.isDeleted != true } ?: return null
            Question(target.id.toLongOrNull()?.takeIf { it > 0 } ?: return null)
        }
        NotificationType.LIKE_ANSWER -> {
            val answer = item.bodyDestination as? Article ?: return null
            answer.takeIf { it.type == ArticleType.Answer }?.notificationContent() ?: return null
        }
        NotificationType.LIKE_COMMENT, NotificationType.REPLY_COMMENT -> {
            val comment = item.bodyDestination as? CommentHolder ?: return null
            if (comment.commentId.toLongOrNull()?.let { it > 0 } != true) return null
            CommentHolder(comment.commentId, comment.article.notificationContent() ?: return null)
        }
    }
    return SystemNotificationEvent("$entry:$id", type, destination)
}

/** 持久化点击目标只保留导航所需 ID，不存通知正文、作者资料或外部 URL。 */
private fun NavDestination.notificationContent(): NavDestination? = when (this) {
    is Article if id > 0 -> Article(type = type, id = id)
    is Pin if id > 0 -> Pin(id)
    is Question if questionId > 0 -> Question(questionId)
    else -> null
}
