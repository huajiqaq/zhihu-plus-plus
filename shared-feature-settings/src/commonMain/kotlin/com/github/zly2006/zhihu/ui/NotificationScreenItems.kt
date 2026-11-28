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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.data.MobileNotificationTimelineItem
import com.github.zly2006.zhihu.i18n.NotificationsStrings
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.ui.components.AppDimens
import com.github.zly2006.zhihu.ui.components.AppIcon
import com.github.zly2006.zhihu.util.formatRelativeTime
import com.github.zly2006.zhihu.viewmodel.MobileNotificationCategory

@Composable
fun NotificationItemView(
    notification: MobileNotificationTimelineItem,
    entryName: String,
    showUnreadBadge: Boolean,
) {
    val navigate = LocalNavigator.current.onNavigate
    val splitTargets = entryName == MobileNotificationCategory.Comment.entryName || entryName == MobileNotificationCategory.Like.entryName
    val authorDestination = notification.avatarDestination()?.let { author ->
        if (splitTargets) (notification.headDestination as? Person) ?: author else author
    }
    val primary = if (splitTargets || notification.content?.isDeleted == true) notification.bodyDestination else notification.navDestination()
    val primaryClick = Modifier.openNotificationTarget(primary, navigate, NotificationsStrings.openBody)
    val headingClick = if (splitTargets && authorDestination != null) {
        Modifier.openNotificationTarget(authorDestination, navigate, NotificationsStrings.openProfile)
    } else {
        primaryClick
    }
    val unread = showUnreadBadge && notification.isUnread
    val backgroundColor = if (unread) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f) else Color.Transparent

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("notification_item_${notification.stableId}")
            .semantics { if (showUnreadBadge) stateDescription = if (unread) NotificationsStrings.unread else NotificationsStrings.read },
        color = backgroundColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = notificationHorizontalPadding(),
                    vertical = AppDimens.Space12,
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .padding(top = 10.dp, end = 4.dp),
                ) {
                    if (unread) {
                        Surface(
                            color = MaterialTheme.colorScheme.error,
                            shape = CircleShape,
                            modifier = Modifier.size(6.dp),
                        ) {}
                    }
                }
                val avatarUrl = notification.avatarUrl()
                if (avatarUrl.isNotBlank() || authorDestination != null) {
                    val modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("notification_avatar_${notification.stableId}")
                        .openNotificationTarget(authorDestination, navigate, NotificationsStrings.openProfile)
                    val description = authorDestination?.let { NotificationsStrings.avatarDescription(it.name) }
                    if (avatarUrl.isNotBlank()) {
                        AsyncImage(model = avatarUrl, contentDescription = description, modifier = modifier)
                    } else {
                        Box(modifier, contentAlignment = Alignment.Center) { AppIcon(Icons.Outlined.Notifications, description) }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Column(Modifier.fillMaxWidth().then(headingClick), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = notification.displayTitle(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        notification.displaySubtitle().takeIf(String::isNotBlank)?.let { subtitle ->
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Column(Modifier.fillMaxWidth().then(primaryClick)) {
                        notification.bodyPreview.takeIf(String::isNotBlank)?.let { body ->
                            Spacer(modifier = Modifier.height(4.dp))
                            NotificationPreviewText(html = body, messageId = notification.stableId, modifier = Modifier.fillMaxWidth())
                        }
                        Text(
                            text = formatRelativeTime(notification.created),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = AppDimens.Space4),
                        )
                    }
                }
            }

            NotificationItemQuotedContent(notification, navigate)
        }
    }
}

@Composable
internal fun NotificationItemQuotedContent(
    notification: MobileNotificationTimelineItem,
    navigate: (NavDestination) -> Unit,
) {
    val quotedPreview = notification.quotedPreview
    val sourceText = notification.sourceText()
    val source = notification.targetSource
    val image = source?.previewImage
    if (quotedPreview.isNotBlank() || sourceText.isNotBlank() || image != null) {
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (quotedPreview.isNotBlank()) {
                    NotificationPreviewText(
                        html = quotedPreview,
                        messageId = notification.stableId,
                        modifier = Modifier.fillMaxWidth().openNotificationTarget(notification.quotedDestination, navigate, NotificationsStrings.openQuote),
                    )
                }
                if (sourceText.isNotBlank() || image != null) {
                    Row(
                        Modifier.fillMaxWidth().openNotificationTarget(notification.sourceDestination, navigate, NotificationsStrings.openSource),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (sourceText.isNotBlank()) {
                            NotificationPreviewText(
                                html = sourceText,
                                messageId = notification.stableId,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (image != null) {
                            Box(contentAlignment = Alignment.BottomEnd) {
                                AsyncImage(
                                    model = image,
                                    contentDescription = if (source.hasVideo) NotificationsStrings.sourceVideoCover else NotificationsStrings.sourceImage,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.small),
                                )
                                if (source.hasVideo) {
                                    Text(NotificationsStrings.video, Modifier.background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 4.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun MobileNotificationTimelineItem.displaySubtitle(): String {
    val subtitle = content?.subTitle?.takeIf { it.isNotBlank() }.orEmpty()
    if ((!subtitle.endsWith("：") && subtitle.startsWith("评论了")) ||
        subtitle.startsWith("赞同了") ||
        subtitle.startsWith("喜欢了")
    ) {
        return "$subtitle："
    }
    return subtitle
}

private fun MobileNotificationTimelineItem.sourceText(): String =
    if (targetSource?.isDeleted == true) {
        NotificationsStrings.contentDeleted
    } else {
        listOfNotNull(
            targetSource?.text?.takeIf { it.isNotBlank() },
            targetSource?.subText?.takeIf { it.isNotBlank() },
        ).joinToString("<br>")
    }
