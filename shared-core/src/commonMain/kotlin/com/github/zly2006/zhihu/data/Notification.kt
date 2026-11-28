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

package com.github.zly2006.zhihu.data

import com.github.zly2006.zhihu.i18n.CoreStrings
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.CommentHolder
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Notification
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.WebPage
import com.github.zly2006.zhihu.navigation.resolveNavigationUrl
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.serialization.Serializable

@Serializable
data class MobileNotificationMessageOverview(
    val head: List<MobileNotificationHeadEntry> = emptyList(),
    val columnHead: List<MobileNotificationColumnHead> = emptyList(),
    val data: List<MobileNotificationTimelineItem> = emptyList(),
    val paging: ZhihuPaging? = null,
    val unread: MobileNotificationUnread = MobileNotificationUnread(),
) {
    val totalUnreadCount: Int
        get() = unread.message.count
}

const val MOBILE_NOTIFICATION_MESSAGE_URL = "https://api.zhihu.com/notifications/v3/message/v3"

@Serializable
data class MobileNotificationUnread(
    val message: MobileNotificationUnreadCount = MobileNotificationUnreadCount(),
)

@Serializable
data class MobileNotificationUnreadCount(
    val count: Int = 0,
    val showCount: Boolean = false,
)

@Serializable
data class MobileNotificationHeadEntry(
    val detailTitle: String = "",
    val unreadCount: Int = 0,
    val id: String = "",
    val content: MobileNotificationContent? = null,
)

@Serializable
data class MobileNotificationColumnHead(
    val id: String = "",
    val title: String = "",
    val textPrefix: String = "",
    val text: String = "",
    val targetLink: String = "",
    val avatarUrls: List<MobileNotificationImage> = emptyList(),
    val images: List<String> = emptyList(),
    val unreadCount: Int = 0,
    val created: Long = 0,
)

@Serializable
data class MobileNotificationImage(
    val url: String = "",
    val nightUrl: String = "",
)

@Serializable
data class MobileNotificationTimelineItem(
    val id: String = "",
    val uniqueId: String = "",
    val type: String = "",
    val cardType: String = "",
    val detailTitle: String = "",
    val isRead: Boolean = true,
    val unreadCount: Int = 0,
    val created: Long = 0,
    val createdStr: String = "",
    val head: MobileNotificationHead? = null,
    val content: MobileNotificationContent? = null,
    val targetSource: MobileNotificationTargetSource? = null,
    val target: MobileNotificationTarget? = null,
    val emptyInfo: MobileNotificationEmptyInfo? = null,
    val additionalInfo: List<MobileNotificationAdditionalInfo> = emptyList(),
    val extraAction: MobileNotificationExtraAction? = null,
) {
    val stableId: String
        get() = uniqueId.ifBlank { id.ifBlank { "$created-$cardType-$detailTitle" } }

    val isUnread: Boolean get() = !isRead || unreadCount > 0

    fun asRead(): MobileNotificationTimelineItem = if (isUnread) copy(isRead = true, unreadCount = 0) else this

    val bodyDestination: NavDestination? get() = content?.takeUnless { it.isDeleted }?.targetLink.destination()
    val quotedDestination: NavDestination? get() = content?.takeUnless { it.isSubDeleted }?.subTargetLink.destination()
    val sourceDestination: NavDestination? get() = targetSource?.takeUnless { it.isDeleted }?.targetLink.destination()
    val headDestination: NavDestination? get() = head?.targetLink.destination()

    /** 写操作必须同时核对动作载荷及卡片锚点，不能用通知 ID、引用评论或来源 ID 代替。 */
    val commentActionTarget: CommentHolder?
        get() {
            val extra = extraAction ?: return null
            val data = extra.data ?: return null
            val anchor = bodyDestination as? CommentHolder ?: return null
            if (data.type != "comment" || data.id != anchor.commentId || data.isDelete || data.collapsed || targetSource?.isDeleted == true) return null
            val matchesResource = when (val parent = anchor.article) {
                is Article -> data.resourceType == parent.type.toString() && extra.resourceId == parent.id.toString()
                is Pin -> data.resourceType == "pin" && extra.resourceId == parent.id.toString()
                is Question -> data.resourceType == "question" && extra.resourceId == parent.questionId.toString()
                else -> false
            }
            return anchor.takeIf { matchesResource && anchor.commentId != "0" }
        }

    /** 消息主页整行优先进入会话；分类卡的各个区域则只能打开自己的链接。 */
    val listDestination: NavDestination?
        get() {
            val targets = listOfNotNull(bodyDestination, quotedDestination, sourceDestination, headDestination)
            return targets.minByOrNull {
                when (it) {
                    is Notification.Message -> 0
                    is WebPage -> 2
                    is Person -> 3
                    else -> 1
                }
            }
        }

    /** 官方 Android 11.12.0 的 CommentAndForwardViewHolder/LikeNotiViewHolder 以 abstractText 渲染正文，subText 为引用评论。 */
    val bodyPreview: String
        get() = content
            ?.let {
                if (it.isDeleted) {
                    CoreStrings.deletedContent
                } else {
                    it.abstractText.takeIf(String::isNotBlank) ?: it.text
                }
            }.orEmpty()

    /** 引用内容与正文分别保留；字段缺失时不猜测正文，也不追加详情请求。 */
    val quotedPreview: String
        get() = content
            ?.let {
                if (it.isSubDeleted) CoreStrings.deletedContent else it.subText
            }.orEmpty()
}

private fun String?.destination(): NavDestination? = this?.takeIf(String::isNotBlank)?.let(::resolveNavigationUrl)

@Serializable
data class MobileNotificationHead(
    val author: MobileNotificationAuthor? = null,
    val avatarUrl: String = "",
    val targetLink: String = "",
    val avatarUrls: List<String> = emptyList(),
    val labels: List<MobileNotificationLabel> = emptyList(),
)

@Serializable
data class MobileNotificationLabel(
    val text: String = "",
)

@Serializable
data class MobileNotificationAuthor(
    val id: String? = null,
    val name: String = "",
    val urlToken: String = "",
    val headline: String = "",
    val avatarUrl: String = "",
    val messageUserType: String = "",
    val messageUserSubType: String = "",
    val isForbidden: Boolean = false,
    val muteStatus: Boolean = false,
)

@Serializable
data class MobileNotificationContent(
    val title: String = "",
    val subTitle: String = "",
    val text: String = "",
    val subText: String = "",
    val abstractText: String = "",
    val targetLink: String = "",
    val subTargetLink: String = "",
    val subIcon: String = "",
    val isDeleted: Boolean = false,
    val isSubDeleted: Boolean = false,
    val hasVideo: Boolean = false,
)

@Serializable
data class MobileNotificationTargetSource(
    val topText: String = "",
    val subText: String = "",
    val text: String = "",
    val fullText: String = "",
    val targetLink: String = "",
    val isDeleted: Boolean = false,
    val hasVideo: Boolean = false,
    val image: String = "",
    val objectId: String = "",
    val subTextIsLink: Boolean = false,
) {
    /** 仅自动加载官方 HTTPS 图片，不把通知中的任意 URI 交给图片加载器。 */
    val previewImage: String?
        get() = image.takeUnless { isDeleted || it.isBlank() }?.takeIf {
            val url = runCatching { Url(it) }.getOrNull()
            url != null && url.protocol == URLProtocol.HTTPS && (url.host == "zhimg.com" || url.host.endsWith(".zhimg.com"))
        }
}

@Serializable
data class MobileNotificationTarget(
    val id: String = "",
    val type: String = "",
    val name: String = "",
    val title: String = "",
    val urlToken: String = "",
    val headline: String = "",
    val avatarUrl: String = "",
    val url: String = "",
    val myAnswerUrl: String = "",
    val hasAnswer: Boolean = false,
    val followNum: Int = 0,
)

@Serializable
data class MobileNotificationEmptyInfo(
    val text: String = "",
    val number: Int = 0,
)

@Serializable
data class MobileNotificationExtraAction(
    val resourceId: String? = null,
    val authorName: String? = null,
    val data: MobileNotificationActionData? = null,
)

@Serializable
data class MobileNotificationActionData(
    val type: String? = null,
    val id: String? = null,
    val resourceType: String? = null,
    val voting: Boolean? = null,
    val isDelete: Boolean = false,
    val collapsed: Boolean = false,
)

@Serializable
data class MobileNotificationAdditionalInfo(
    val id: Int = 0,
    val text: String = "",
    val icon: String = "",
)

@Serializable
data class ZhihuPrivateMessagePage(
    val data: List<ZhihuPrivateMessage> = emptyList(),
    val paging: ZhihuPaging,
)

@Serializable
data class ZhihuPrivateMessage(
    val id: String = "",
    val type: String = "",
    val contentType: Int = 0,
    val content: String = "",
    val createdTime: Long = 0,
    val sender: MobileNotificationAuthor? = null,
    val receiver: MobileNotificationAuthor? = null,
    val plugin: ZhihuPrivateMessagePlugin? = null,
) {
    val stableId: String
        get() = id.ifBlank { "$createdTime-${sender?.id}-${receiver?.id}" }
}

@Serializable
data class ZhihuPrivateMessagePlugin(
    val pluginType: String = "",
    val pluginContent: String = "",
    val excerpt: String = "",
)
