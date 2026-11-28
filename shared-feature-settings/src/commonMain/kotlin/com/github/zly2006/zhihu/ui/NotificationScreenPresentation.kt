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

package com.github.zly2006.zhihu.ui

import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.fleeksoft.ksoup.Ksoup
import com.github.zly2006.zhihu.data.MobileNotificationTimelineItem
import com.github.zly2006.zhihu.i18n.NotificationsStrings
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Notification
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.theme.LocalAppSkinTokens
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.settingSkinIsMiuix

@Composable
internal fun notificationHorizontalPadding() = if (settingSkinIsMiuix()) {
    AppDimens.PageHorizontalPadding
} else {
    AppDimens.PageHeaderM3eHorizontalPadding
}

internal fun Modifier.openNotificationTarget(destination: NavDestination?, navigate: (NavDestination) -> Unit, label: String): Modifier =
    if (destination == null) {
        this
    } else {
        // 将最小热区留在本区域内，避免旁边已删除的内容被判为点击另一目标。
        clickable(role = Role.Button, onClickLabel = label) { navigate(destination) }.minimumInteractiveComponentSize()
    }

@Composable
internal fun NotificationPreviewText(
    html: String,
    messageId: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 3,
    style: TextStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
) {
    val uriHandler = rememberUpdatedState(LocalUriHandler.current)
    val linkColor = LocalAppSkinTokens.current.onSurface
    val emojisUsed = remember(messageId, html) { mutableSetOf<String>() }
    val displayText = remember(messageId, html, linkColor) {
        emojisUsed.clear()
        buildAnnotatedString {
            dfsSimple(
                node = Ksoup.parseBodyFragment(html).body(),
                onOpenLink = { uriHandler.value.openUri(it) },
                linkColor = linkColor,
                componentUsed = emojisUsed,
            )
        }
    }
    Text(
        text = displayText,
        modifier = modifier,
        inlineContent = rememberCommentEmojiInlineContent(emojisUsed),
        style = style,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

internal fun MobileNotificationTimelineItem.displayTitle(): String =
    content
        ?.title
        ?.takeIf { it.isNotBlank() }
        ?: detailTitle.takeIf { it.isNotBlank() }
        ?: target?.name?.takeIf { it.isNotBlank() }
        ?: NotificationsStrings.notification

internal fun MobileNotificationTimelineItem.avatarUrl(): String =
    head?.avatarUrl?.takeIf { it.isNotBlank() }
        ?: head?.author?.avatarUrl?.takeIf { it.isNotBlank() }
        ?: target?.avatarUrl?.takeIf { it.isNotBlank() }
        ?: content?.subIcon?.takeIf { it.isNotBlank() }
        ?: ""

/** 头像只打开可确认的单一作者；不能把整行内容、聚合头像或系统会话当作个人主页。 */
internal fun MobileNotificationTimelineItem.avatarDestination(): Person? {
    if (type == "empty" ||
        cardType in setOf("noti_invite_card", "noti_invite_card_dsl", "noti_guide_card", "noti_interval_card", "noti_time_slice") ||
        cardType.startsWith("noti_aggregated_card") ||
        (head?.avatarUrls?.count(String::isNotBlank) ?: 0) > 1
    ) {
        return null
    }
    val actorLink = headDestination
    if (listOf(bodyDestination, quotedDestination, sourceDestination, actorLink).any {
            it is Notification.Entry || (it is Notification.Message && it.peerId.startsWith("-"))
        } ||
        head?.author?.id?.startsWith("-") == true
    ) {
        return null
    }
    head?.author?.takeIf { !it.id.isNullOrBlank() || it.urlToken.isNotBlank() }?.let { author ->
        return Person(
            id = author.id?.takeIf(String::isNotBlank) ?: Person.EMPTY_ID,
            urlToken = author.urlToken,
            name = author.name.ifBlank { NotificationsStrings.user },
        )
    }
    if (actorLink is Person) {
        return actorLink
    }
    return target
        ?.takeIf {
            it.type == "people" &&
                (it.urlToken.isNotBlank() || it.id.isNotBlank()) &&
                (head?.avatarUrl.isNullOrBlank() || head?.avatarUrl == it.avatarUrl)
        }?.let {
            Person(id = it.id.ifBlank { Person.EMPTY_ID }, urlToken = it.urlToken, name = it.name.ifBlank { NotificationsStrings.user })
        }
}

internal fun MobileNotificationTimelineItem.navDestination(): NavDestination? {
    if (content?.isDeleted == true || (bodyPreview.isBlank() && content?.isSubDeleted == true)) return null
    val destination = listDestination
    return if (destination is Notification.Message) {
        destination.copy(
            name = destination.name.ifBlank { displayTitle() },
            avatarUrl = avatarUrl(),
        )
    } else {
        destination ?: target
            ?.takeIf { it.type == "people" && (it.urlToken.isNotBlank() || it.id.isNotBlank()) }
            ?.let {
                Person(
                    id = it.id.ifBlank { Person.EMPTY_ID },
                    urlToken = it.urlToken,
                    name = it.name.ifBlank { NotificationsStrings.loading },
                )
            }
    }
}

internal fun formatUnreadCount(count: Int): String = if (count > 99) "99+" else count.toString()
